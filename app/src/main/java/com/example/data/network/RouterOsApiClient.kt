package com.example.data.network

import com.example.data.model.AdminUser
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.HotspotServerInfo
import com.example.data.model.InterfaceStats
import com.example.data.model.IpBinding
import com.example.data.model.IpPoolInfo
import com.example.data.model.RouterBoardInfo
import com.example.data.model.RouterResource
import com.example.data.model.SystemLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

enum class CardTargetSystem {
    HOTSPOT,
    USER_MANAGER_V7,
    USER_MANAGER_V6
}

data class ResolvedCardTarget(
    val system: CardTargetSystem,
    val internalId: String,
    val name: String
)

class RouterOsApiClient {

    companion object {
        private const val TRANSACTION_SO_TIMEOUT_MS = 35_000
        private const val MAX_WORD_LENGTH_BYTES = 16 * 1024 * 1024
    }

    private val socketLock = Any()
    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private val cmdSocketLock = Any()
    private var cmdSocket: Socket? = null
    private var cmdInStream: InputStream? = null
    private var cmdOutStream: OutputStream? = null

    @Volatile
    private var lastHost: String = ""
    @Volatile
    private var lastPort: Int = 8728
    @Volatile
    private var lastUsername: String = ""
    @Volatile
    private var lastPassword: String = ""
    @Volatile
    private var lastUseTls: Boolean = false
    @Volatile
    private var isExplicitlyClosed: Boolean = false

    @Volatile
    private var lastConnectTimeoutMs: Int = 2500

    private val cachedProfiles = mutableListOf<HotspotProfile>()

    fun getCachedProfiles(): List<HotspotProfile> = synchronized(cachedProfiles) { cachedProfiles.toList() }

    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        ensureConnectedInternal()
    }

    private fun ensureConnectedInternal(): Boolean = synchronized(socketLock) {
        if (isExplicitlyClosed) return@synchronized false
        val s = socket
        if (s != null && !s.isClosed && s.isConnected && !s.isInputShutdown && !s.isOutputShutdown) {
            return@synchronized true
        }
        if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
            return@synchronized reconnectInternalLocked()
        }
        false
    }

    private fun resolveSocketAddress(host: String, port: Int): InetSocketAddress {
        val clean = host.trim()
        if (RouterConnectionParser.isIpv4(clean)) {
            val parts = clean.split('.')
            if (parts.size == 4) {
                val bytes = ByteArray(4) { idx -> parts[idx].toInt().toByte() }
                val addr = java.net.InetAddress.getByAddress(clean, bytes)
                return InetSocketAddress(addr, port)
            }
        }
        return InetSocketAddress(clean, port)
    }

    private val routerSslSocketFactory: SSLSocketFactory by lazy {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            }
        )
        SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }.socketFactory
    }

    private fun createRouterSocket(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        handshakeTimeoutMs: Int,
        useTls: Boolean
    ): Socket {
        val effectiveConnectTimeout = connectTimeoutMs.coerceIn(500, 15000)
        val effectiveHandshakeTimeout = handshakeTimeoutMs.coerceIn(1000, 10000)
        val tcpSocket = NetworkUtils.createRoutedSocket(host)
        tcpSocket.tcpNoDelay = true
        tcpSocket.keepAlive = true
        tcpSocket.connect(resolveSocketAddress(host, port), effectiveConnectTimeout)
        // Use tight read timeout during TLS handshake & /login so dead/non-API ports fail fast instead of stalling 25s
        tcpSocket.soTimeout = effectiveHandshakeTimeout
        if (!useTls) {
            return tcpSocket
        }
        val sslSocket = routerSslSocketFactory.createSocket(tcpSocket, host, port, true) as SSLSocket
        try {
            val supported = sslSocket.supportedCipherSuites
            val enabled = sslSocket.enabledCipherSuites.toMutableList()
            supported.filter { it.contains("anon", ignoreCase = true) || it.contains("ADH", ignoreCase = true) }
                .forEach { if (it !in enabled) enabled.add(it) }
            if (enabled.isNotEmpty()) {
                sslSocket.enabledCipherSuites = enabled.toTypedArray()
            }
        } catch (_: Exception) {}
        sslSocket.soTimeout = effectiveHandshakeTimeout
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun readLoginSentencesUntilDone(inS: InputStream): List<List<String>> {
        val sentences = mutableListOf<List<String>>()
        while (true) {
            val sentence = readSingleSentenceFromStream(inS)
            if (sentence.isEmpty()) break
            sentences.add(sentence)
            val first = sentence.firstOrNull() ?: ""
            if (first == "!done" || first == "!fatal") break
        }
        return sentences
    }

    private fun performLoginHandshake(
        sock: Socket,
        inS: InputStream,
        outS: OutputStream,
        cleanUser: String,
        password: String
    ): Pair<Boolean, String?> {
        // 1. Attempt modern RouterOS (v6.43+ and v7) plain /login
        writeSentenceToStream(outS, listOf("/login", "=name=$cleanUser", "=password=$password"))
        val firstReply = readLoginSentencesUntilDone(inS)
        if (firstReply.isEmpty()) {
            return false to "لم يستجب الراوتر على منفذ API"
        }

        if (firstReply.any { it.firstOrNull() == "!fatal" }) {
            val fatalMsg = firstReply.firstOrNull { it.firstOrNull() == "!fatal" }
                ?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                ?: firstReply.firstOrNull { it.firstOrNull() == "!fatal" }?.drop(1)?.firstOrNull()
            return false to (fatalMsg ?: "أغلق راوتر ميكروتك جلسة الدخول")
        }

        val firstTrap = firstReply.firstOrNull { it.firstOrNull() == "!trap" }
        val firstDone = firstReply.lastOrNull { it.firstOrNull() == "!done" }
        val firstChallenge = firstReply.flatten().firstOrNull { it.startsWith("=ret=") }

        if (firstTrap == null && firstDone != null && firstChallenge == null) {
            runCatching { sock.soTimeout = TRANSACTION_SO_TIMEOUT_MS }
            return true to null
        }

        var challengeParam = firstChallenge
        var trapMsg = firstTrap?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")

        // 2. Pre-6.43 legacy RouterOS: initial /login =name= =password= triggers !trap; bare /login returns =ret=<challenge>
        if (firstTrap != null && firstDone != null) {
            try {
                writeSentenceToStream(outS, listOf("/login"))
                val bareReply = readLoginSentencesUntilDone(inS)
                val bareChallenge = bareReply.flatten().firstOrNull { it.startsWith("=ret=") }
                if (bareChallenge != null && bareReply.none { it.firstOrNull() == "!trap" || it.firstOrNull() == "!fatal" }) {
                    challengeParam = bareChallenge
                }
            } catch (_: Exception) {
                // Keep original trapMsg if bare /login probe failed
            }
        }

        // 3. Complete MD5 challenge-response if =ret= was issued
        if (challengeParam != null) {
            val challengeHex = challengeParam.removePrefix("=ret=").trim()
            if (challengeHex.length >= 2 && challengeHex.length % 2 == 0) {
                val challengeBytes = hexStringToByteArray(challengeHex)
                val md5 = MessageDigest.getInstance("MD5")
                md5.update(0.toByte())
                md5.update(password.toByteArray(Charsets.UTF_8))
                md5.update(challengeBytes)
                val digestHex = byteArrayToHexString(md5.digest())
                val responseHash = "00$digestHex"

                writeSentenceToStream(outS, listOf("/login", "=name=$cleanUser", "=response=$responseHash"))
                val resp2 = readLoginSentencesUntilDone(inS)
                val trap2 = resp2.firstOrNull { it.firstOrNull() == "!trap" }
                val fatal2 = resp2.firstOrNull { it.firstOrNull() == "!fatal" }
                if (trap2 == null && fatal2 == null && resp2.any { it.firstOrNull() == "!done" }) {
                    runCatching { sock.soTimeout = TRANSACTION_SO_TIMEOUT_MS }
                    return true to null
                }
                trapMsg = trap2?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: trapMsg
            }
        }

        return false to trapMsg
    }

    suspend fun connectAndLogin(
        host: String,
        port: Int = 8728,
        username: String,
        password: String,
        timeoutMs: Int = 2500,
        useTls: Boolean = (port == 8729)
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            synchronized(socketLock) {
                var phase = "TCP_CONNECT"
                val parsed = RouterConnectionParser.normalizeHostInput(host)
                val cleanHost = parsed.host.ifBlank { host.trim() }
                val effectiveConnectMs = timeoutMs.coerceIn(500, 15000)
                val effectiveHandshakeMs = (effectiveConnectMs + 800).coerceIn(1200, 6000)
                try {
                    closeSocketOnlyLocked()
                    synchronized(cmdSocketLock) { closeCmdSocketOnlyLocked() }
                    synchronized(cachedProfiles) { cachedProfiles.clear() }
                    isExplicitlyClosed = false
                    val cleanUser = username.trim()
                    val effectiveUseTls = useTls || port == 8729
                    lastHost = cleanHost
                    lastPort = port
                    lastUsername = cleanUser
                    lastPassword = password
                    lastUseTls = effectiveUseTls
                    lastConnectTimeoutMs = effectiveConnectMs

                    val sock = createRouterSocket(
                        host = cleanHost,
                        port = port,
                        connectTimeoutMs = effectiveConnectMs,
                        handshakeTimeoutMs = effectiveHandshakeMs,
                        useTls = effectiveUseTls
                    )
                    socket = sock
                    inStream = sock.getInputStream()
                    outStream = sock.getOutputStream()

                    phase = "LOGIN_HANDSHAKE"
                    val (ok, trapMsg) = performLoginHandshake(
                        sock = sock,
                        inS = sock.getInputStream(),
                        outS = sock.getOutputStream(),
                        cleanUser = cleanUser,
                        password = password
                    )
                    if (ok) {
                        return@synchronized Result.success(true)
                    }

                    val userFriendlyError = if (trapMsg != null && (trapMsg.contains("cannot log in", ignoreCase = true) || trapMsg.contains("invalid", ignoreCase = true) || trapMsg.contains("password", ignoreCase = true))) {
                        "خطأ في بيانات الدخول: اسم المستخدم أو كلمة المرور غير صحيحة. يرجى التأكد من كتابة اسم المستخدم وكلمة المرور الخاصة بحساب إدارة راوتر ميكروتك بدقة."
                    } else if (trapMsg != null) {
                        "فشلت المصادقة مع الراوتر: $trapMsg"
                    } else {
                        "خطأ في بيانات الدخول: اسم المستخدم أو كلمة المرور غير صحيحة."
                    }

                    closeSocketOnlyLocked()
                    Result.failure(Exception(userFriendlyError))
                } catch (e: UnknownHostException) {
                    closeSocketOnlyLocked()
                    Result.failure(e)
                } catch (e: SSLException) {
                    closeSocketOnlyLocked()
                    Result.failure(Exception("فشل التفاوض الأمني SSL على المنفذ ($port): ${e.localizedMessage ?: "شهادة غير موثوقة أو غير صالحة"}", e))
                } catch (e: java.net.SocketTimeoutException) {
                    closeSocketOnlyLocked()
                    val reason = if (phase == "TCP_CONNECT") {
                        "انتهى وقت محاولة الوصول (${effectiveConnectMs}ms) إلى الراوتر ($cleanHost:$port)."
                    } else {
                        "اتصل الجهاز بالمنفذ ($port) لكن انتهى وقت انتظار رد المصادقة (${effectiveHandshakeMs}ms) — تأكد أن المنفذ مخصص لـ RouterOS API."
                    }
                    Result.failure(java.net.SocketTimeoutException(reason).apply { initCause(e) })
                } catch (e: java.net.ConnectException) {
                    closeSocketOnlyLocked()
                    Result.failure(java.net.ConnectException("رفض الراوتر ($cleanHost) الاتصال المباشر على المنفذ ($port) — تأكد من تفعيل خدمة api من IP -> Services.").apply { initCause(e) })
                } catch (e: Exception) {
                    closeSocketOnlyLocked()
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getSystemResource(): Result<RouterResource> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val sentences = executeSentence(listOf("/system/resource/print"))
                val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                if (re != null) {
                    val map = parseSentenceToMap(re)
                    val cpu = map["cpu-load"]?.toIntOrNull() ?: 0
                    val freeMem = map["free-memory"]?.toLongOrNull() ?: 0L
                    val totalMem = map["total-memory"]?.toLongOrNull() ?: 0L
                    val uptime = map["uptime"] ?: "0s"
                    val version = map["version"] ?: ""
                    val board = map["board-name"] ?: ""
                    val arch = map["architecture-name"] ?: ""
                    val cpuFreqRaw = map["cpu-frequency"].orEmpty()
                    val cpuFreq = when {
                        cpuFreqRaw.isBlank() -> ""
                        cpuFreqRaw.contains("MHz", ignoreCase = true) -> cpuFreqRaw
                        else -> "$cpuFreqRaw MHz"
                    }
                    val hddFree = map["free-hdd-space"]?.toLongOrNull() ?: 0L

                    val res = RouterResource(
                        uptime = uptime,
                        version = version,
                        boardName = board,
                        cpuLoad = cpu,
                        freeMemoryBytes = freeMem,
                        totalMemoryBytes = totalMem,
                        architecture = arch,
                        rxSpeedKbps = 0.0,
                        txSpeedKbps = 0.0,
                        cpuFrequency = cpuFreq,
                        hddFreeBytes = hddFree
                    )
                    Result.success(res)
                } else {
                    val trapMsg = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        ?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                    Result.failure(Exception(trapMsg?.let { "تعذر قراءة بيانات النظام: $it" } ?: "لم يُرجع الراوتر بيانات النظام"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getActiveHotspotUsers(): Result<List<HotspotActiveUser>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val sentences = executeQueryWithProplist(
                    command = "/ip/hotspot/active/print",
                    proplist = ".id,user,address,mac-address,uptime,bytes-in,bytes-out,login-by,session-time-left,comment,server"
                )
                extractTrapMessageIfNoRe(sentences)?.let { trapMsg ->
                    return@withContext Result.failure(Exception("تعذر جلب الجلسات النشطة: $trapMsg"))
                }
                val list = mutableListOf<HotspotActiveUser>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        list.add(
                            HotspotActiveUser(
                                id = map[".id"] ?: "u_${list.size}",
                                user = map["user"] ?: "unknown",
                                address = map["address"] ?: "",
                                macAddress = map["mac-address"] ?: "",
                                uptime = map["uptime"] ?: "0s",
                                bytesIn = map["bytes-in"]?.toLongOrNull() ?: 0L,
                                bytesOut = map["bytes-out"]?.toLongOrNull() ?: 0L,
                                loginBy = map["login-by"] ?: "http",
                                sessionTimeLeft = map["session-time-left"] ?: "",
                                comment = map["comment"] ?: "",
                                serverName = map["server"]?.ifBlank { "hotspot1" } ?: "hotspot1"
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getHotspotUsers(): Result<List<HotspotCard>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val sentences = executeQueryWithProplist(
                    command = "/ip/hotspot/user/print",
                    proplist = ".id,name,password,profile,uptime,bytes-in,bytes-out,limit-uptime,limit-bytes-total,limit-bytes-out,limit-bytes-in,comment,disabled"
                )
                extractTrapMessageIfNoRe(sentences)?.let { trapMsg ->
                    return@withContext Result.failure(Exception("تعذر جلب كروت الهوتسبوت: $trapMsg"))
                }
                val list = mutableListOf<HotspotCard>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val name = map["name"] ?: ""
                        val comment = map["comment"] ?: ""
                        val profile = map["profile"] ?: "default"
                        val parsedPrice = extractPriceFromText(comment, profile, name)
                        val upUsed = map["bytes-in"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                        val downUsed = map["bytes-out"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                        val totalLimit = map["limit-bytes-total"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                        val outLimit = map["limit-bytes-out"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                        val inLimit = map["limit-bytes-in"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                        val effectiveLimit = if (totalLimit > 0L) totalLimit else if (outLimit > 0L) outLimit else inLimit
                        val dateHint = com.example.data.network.NetworkUtils.parseRouterDateToEpoch(comment) ?: 0L

                        list.add(
                            HotspotCard(
                                id = map[".id"] ?: "c_${list.size}",
                                name = name,
                                password = map["password"] ?: "",
                                profile = profile,
                                uptime = map["uptime"] ?: "0s",
                                bytesIn = upUsed,
                                bytesOut = downUsed,
                                limitUptime = map["limit-uptime"] ?: "",
                                limitBytesTotal = effectiveLimit,
                                comment = comment,
                                disabled = map["disabled"] == "true" || map["disabled"] == "yes",
                                price = parsedPrice,
                                cardType = "hotspot",
                                createdAtHint = dateHint
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getHotspotProfiles(): Result<List<HotspotProfile>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("فشل الاتصال براوتر ميكروتك عبر منفذ API ($lastPort)"))
                }
                val list = mutableListOf<HotspotProfile>()
                var anySystemSucceeded = false
                var lastSysError: Exception? = null

                // 1. Fetch Hotspot User Profiles (/ip/hotspot/user/profile)
                try {
                    val sentences = executeSentence(listOf("/ip/hotspot/user/profile/print"))
                    val trapMsg = extractTrapMessageIfNoRe(sentences)
                    if (trapMsg != null) {
                        lastSysError = Exception("تعذر جلب باقات الهوتسبوت: $trapMsg")
                    } else if (sentences.any { it.firstOrNull() == "!done" }) {
                        anySystemSucceeded = true
                    }
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: "default"
                            val rateLimit = map["rate-limit"] ?: "غير محدد"
                            val comment = map["comment"] ?: ""
                            val sessionTimeout = map["session-timeout"] ?: "غير محدد"
                            val keepaliveTimeout = map["keepalive-timeout"] ?: "5m"
                            val parsedPrice = extractPriceFromText(comment, name, "")
                            val priceNum = Regex("""\d+""").find(parsedPrice)?.value?.toDoubleOrNull() ?: 0.0
                            val (up, down) = parseRateLimitSpeeds(rateLimit)

                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "p_${list.size}",
                                    name = name,
                                    rateLimit = rateLimit,
                                    sharedUsers = map["shared-users"]?.toIntOrNull() ?: 1,
                                    sessionTimeout = sessionTimeout,
                                    keepaliveTimeout = keepaliveTimeout,
                                    price = parsedPrice,
                                    uploadLimit = up,
                                    downloadLimit = down,
                                    validity = sessionTimeout,
                                    dataLimit = extractDataLimitFromText(name, comment),
                                    source = "hotspot",
                                    comment = comment,
                                    priceNumber = priceNum
                                )
                            )
                        }
                    }
                } catch (e: Exception) { lastSysError = e }

                // 2. Fetch User-Manager Profiles & Linked Limitations (/user-manager/profile & /user-manager/limitation)
                try {
                    // Pre-fetch limitations in v7 if available
                    val v7Limitations = mutableMapOf<String, Map<String, String>>()
                    try {
                        val limitSentences = executeSentence(listOf("/user-manager/limitation/print"))
                        for (ls in limitSentences) {
                            if (ls.firstOrNull() == "!re") {
                                val lMap = parseSentenceToMap(ls)
                                val lName = lMap["name"] ?: continue
                                v7Limitations[lName] = lMap
                                val lId = lMap[".id"]
                                if (lId != null) v7Limitations[lId] = lMap
                            }
                        }
                    } catch (_: Exception) {}

                    // Pre-fetch profile-limitation bindings in v7 (mapping profile -> limitation)
                    val v7ProfileToLimit = mutableMapOf<String, MutableList<String>>()
                    try {
                        val plSentences = executeSentence(listOf("/user-manager/profile-limitation/print"))
                        for (pls in plSentences) {
                            if (pls.firstOrNull() == "!re") {
                                val plMap = parseSentenceToMap(pls)
                                val pName = plMap["profile"] ?: continue
                                val lName = plMap["limitation"] ?: continue
                                v7ProfileToLimit.getOrPut(pName) { mutableListOf() }.add(lName)
                            }
                        }
                    } catch (_: Exception) {}

                    val umSentences = executeSentence(listOf("/user-manager/profile/print"))
                    val umTrap = extractTrapMessageIfNoRe(umSentences)
                    if (umTrap != null) {
                        if (lastSysError == null) lastSysError = Exception("تعذر جلب باقات يوزرمانجر: $umTrap")
                    } else if (umSentences.any { it.firstOrNull() == "!done" }) {
                        anySystemSucceeded = true
                    }
                    for (s in umSentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: continue
                            if (list.any { it.name.equals(name, ignoreCase = true) && it.source == "usermanager_v7" }) continue

                            val rawPrice = map["price"]?.takeIf { it.isNotBlank() && it != "0" }
                            val priceStr = if (rawPrice != null) "$rawPrice ر.ي" else extractPriceFromText(map["comment"].orEmpty(), name, "")
                            val priceNum = rawPrice?.toDoubleOrNull() ?: (Regex("""\d+""").find(priceStr)?.value?.toDoubleOrNull() ?: 0.0)
                            val validity = map["validity"] ?: map["session-timeout"] ?: "1d"

                            // Resolve exact data limit from linked limitation
                            var resolvedDataLimit = ""
                            var resolvedRateLimit = map["rate-limit"] ?: ""

                            val boundLimits = v7ProfileToLimit[name] ?: emptyList()
                            for (bLimitName in boundLimits) {
                                val lData = v7Limitations[bLimitName] ?: continue
                                val downLimit = lData["download-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                val upLimit = lData["upload-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                val transferLimit = lData["transfer-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) }?.takeIf { it > 0L }
                                val explicitTotal = lData["total-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) }?.takeIf { it > 0L }
                                val totalLimit = transferLimit
                                    ?: explicitTotal
                                    ?: if (downLimit > 0L || upLimit > 0L) downLimit + upLimit else 0L

                                if (totalLimit > 0L) {
                                    resolvedDataLimit = formatBytes(totalLimit)
                                    break
                                } else if (downLimit > 0L) {
                                    resolvedDataLimit = formatBytes(downLimit)
                                    break
                                }

                                val limitRate = lData["rate-limit"] ?: ""
                                if (limitRate.isNotBlank() && resolvedRateLimit.isBlank()) {
                                    resolvedRateLimit = limitRate
                                }
                            }

                            // Fallback to text extraction from name or comment if limitation wasn't bound
                            if (resolvedDataLimit.isBlank()) {
                                resolvedDataLimit = extractDataLimitFromText(name, map["comment"].orEmpty())
                            }

                            val (upSpeed, downSpeed) = parseRateLimitSpeeds(resolvedRateLimit)

                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "um_${list.size}",
                                    name = name,
                                    rateLimit = if (resolvedRateLimit.isNotBlank()) resolvedRateLimit else "مفتوح",
                                    sharedUsers = 1,
                                    sessionTimeout = validity,
                                    keepaliveTimeout = "5m",
                                    price = priceStr,
                                    uploadLimit = upSpeed,
                                    downloadLimit = downSpeed,
                                    validity = validity,
                                    dataLimit = resolvedDataLimit,
                                    source = "usermanager_v7",
                                    comment = map["comment"].orEmpty(),
                                    priceNumber = priceNum
                                )
                            )
                        }
                    }
                } catch (e: Exception) { lastSysError = e }

                // 3. Fetch Tool User-Manager Profiles & Limitations for RouterOS v6
                try {
                    val v6Limitations = mutableMapOf<String, Map<String, String>>()
                    try {
                        val limitSentences = executeSentence(listOf("/tool/user-manager/limitation/print"))
                        for (ls in limitSentences) {
                            if (ls.firstOrNull() == "!re") {
                                val lMap = parseSentenceToMap(ls)
                                val lName = lMap["name"] ?: continue
                                v6Limitations[lName] = lMap
                                val lId = lMap[".id"]
                                if (lId != null) v6Limitations[lId] = lMap
                            }
                        }
                    } catch (_: Exception) {}

                    val v6ProfileToLimit = mutableMapOf<String, MutableList<String>>()
                    try {
                        val plSentences = executeSentence(listOf("/tool/user-manager/profile-limitation/print"))
                        for (pls in plSentences) {
                            if (pls.firstOrNull() == "!re") {
                                val plMap = parseSentenceToMap(pls)
                                val pName = plMap["profile"] ?: continue
                                val lName = plMap["limitation"] ?: continue
                                v6ProfileToLimit.getOrPut(pName) { mutableListOf() }.add(lName)
                            }
                        }
                    } catch (_: Exception) {}

                    val tumSentences = executeSentence(listOf("/tool/user-manager/profile/print"))
                    val tumTrap = extractTrapMessageIfNoRe(tumSentences)
                    if (tumTrap != null) {
                        if (lastSysError == null) lastSysError = Exception("تعذر جلب باقات يوزرمانجر: $tumTrap")
                    } else if (tumSentences.any { it.firstOrNull() == "!done" }) {
                        anySystemSucceeded = true
                    }
                    for (s in tumSentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: continue
                            if (list.any { it.name.equals(name, ignoreCase = true) && it.source.startsWith("usermanager") }) continue
                            val rawPrice = map["price"]?.takeIf { it.isNotBlank() && it != "0" }
                            val priceStr = if (rawPrice != null) "$rawPrice ر.ي" else extractPriceFromText(map["comment"].orEmpty(), name, "")
                            val customer = map["customer"]?.takeIf { it.isNotBlank() } ?: "admin"

                            var v6DataLimit = ""
                            var v6Rate = map["rate-limit"] ?: ""

                            val boundLimits = v6ProfileToLimit[name] ?: emptyList()
                            for (bLimitName in boundLimits) {
                                val lData = v6Limitations[bLimitName] ?: continue
                                val downLimit = lData["download-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                val upLimit = lData["upload-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                val transferLimit = lData["transfer-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                val total = if (transferLimit > 0L) transferLimit else (downLimit + upLimit)
                                if (total > 0L) {
                                    v6DataLimit = formatBytes(total)
                                    break
                                }
                                val limitRate = lData["rate-limit"] ?: ""
                                if (limitRate.isNotBlank() && v6Rate.isBlank()) {
                                    v6Rate = limitRate
                                }
                            }

                            if (v6DataLimit.isBlank()) {
                                v6DataLimit = extractDataLimitFromText(name, map["comment"].orEmpty())
                            }

                            val (upSpeed, downSpeed) = parseRateLimitSpeeds(v6Rate)

                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "tum_${list.size}",
                                    name = name,
                                    rateLimit = if (v6Rate.isNotBlank()) v6Rate else "مفتوح",
                                    sharedUsers = 1,
                                    sessionTimeout = map["validity"] ?: "1d",
                                    price = priceStr,
                                    uploadLimit = upSpeed,
                                    downloadLimit = downSpeed,
                                    validity = map["validity"] ?: "1d",
                                    dataLimit = v6DataLimit,
                                    source = "usermanager_v6",
                                    comment = customer
                                )
                            )
                        }
                    }
                } catch (e: Exception) { lastSysError = e }

                if (!anySystemSucceeded && lastSysError != null) {
                    return@withContext Result.failure(lastSysError!!)
                }

                synchronized(cachedProfiles) {
                    cachedProfiles.clear()
                    cachedProfiles.addAll(list)
                }

                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProfile(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                var profileId = profile.id
                var isUserManager = profile.source.contains("usermanager")

                if (!profileId.startsWith("*")) {
                    val printSentences = executeCommandSentence(listOf("/ip/hotspot/user/profile/print", "?name=${profile.name}"))
                    val hsRe = printSentences.firstOrNull { it.firstOrNull() == "!re" }
                    if (hsRe != null) {
                        val map = parseSentenceToMap(hsRe)
                        profileId = map[".id"] ?: profileId
                    } else {
                        // Check User Manager
                        val umPrint = executeCommandSentence(listOf("/user-manager/profile/print", "?name=${profile.name}"))
                        val umRe = umPrint.firstOrNull { it.firstOrNull() == "!re" }
                        if (umRe != null) {
                            val map = parseSentenceToMap(umRe)
                            profileId = map[".id"] ?: profileId
                            isUserManager = true
                        }
                    }
                }

                if (isUserManager) {
                    val target = if (profileId.startsWith("*")) "=.id=$profileId" else "=numbers=${profile.name}"
                    val words = mutableListOf("/user-manager/profile/set", target)
                    val valTime = profile.validity.ifBlank { profile.sessionTimeout }.let { formatRouterOsTime(it) } ?: "1d"
                    words.add("=validity=$valTime")
                    val priceNum = profile.priceNumber.takeIf { it > 0 } ?: profile.price.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                    if (priceNum > 0) words.add("=price=${priceNum.toInt()}")
                    val sentences = executeCommandSentence(words)
                    val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trap != null) {
                        val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل تحديث باقة يوزرمانجر"
                        return@withContext Result.failure(Exception(msg))
                    }
                    return@withContext Result.success(true)
                }

                val target = if (profileId.startsWith("*")) "=.id=$profileId" else "=numbers=${profile.name}"
                val words = mutableListOf("/ip/hotspot/user/profile/set", target)
                val cleanRateLimit = profile.rateLimit.trim()
                if (cleanRateLimit.isNotBlank() && cleanRateLimit != "غير محدد" && cleanRateLimit != "مفتوح") {
                    words.add("=rate-limit=$cleanRateLimit")
                }
                words.add("=shared-users=${if (profile.sharedUsers > 0) profile.sharedUsers else 1}")
                val cleanTimeout = profile.sessionTimeout.trim()
                if (cleanTimeout.isNotBlank() && cleanTimeout != "غير محدد") {
                    val formattedTimeout = formatRouterOsTime(cleanTimeout)
                    if (!formattedTimeout.isNullOrBlank()) {
                        words.add("=session-timeout=$formattedTimeout")
                    }
                }
                if (profile.price.isNotBlank() && profile.price != "0" && profile.price != "مجاني") {
                    words.add("=comment=price=${profile.price}")
                } else if (profile.comment.isNotBlank()) {
                    words.add("=comment=${profile.comment}")
                }

                val sentences = executeCommandSentence(words)
                val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trapSentence != null) {
                    val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "فشل تحديث الباقة في راوتر ميكروتك"
                    // If target wasn't found by .id, retry once by adding profile
                    val addRes = addProfile(profile)
                    if (addRes.isSuccess) {
                        return@withContext Result.success(true)
                    }
                    return@withContext Result.failure(Exception(trapMsg))
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDhcpLeases(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/dhcp-server/lease/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getHotspotHosts(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/hotspot/host/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWirelessRegistrations(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/interface/wireless/registration-table/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getArpEntries(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/arp/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getBridgeHosts(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/interface/bridge/host/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun extractPriceFromText(comment: String, profileName: String, cardName: String): String {
        val combined = "$comment $profileName $cardName"
        // 1. Check explicit price tags: price=500, سعر: 200, 300ريال, 1000 YER
        val explicitRegex = Regex("""(?i)(?:price=|سعر=|السعر=|فئة\s*|فئه\s*)(\d{2,6})""")
        val explicitMatch = explicitRegex.find(combined)
        if (explicitMatch != null && explicitMatch.groupValues.size > 1) {
            return "${explicitMatch.groupValues[1]} ر.ي"
        }

        // 2. Check currency suffixed numbers: 500 ر.ي, 200ريال, 1000YER
        val currencyRegex = Regex("""(\d{2,6})\s*(?:ر\.?ي|ريال|YER|YR)""", RegexOption.IGNORE_CASE)
        val currencyMatch = currencyRegex.find(combined)
        if (currencyMatch != null && currencyMatch.groupValues.size > 1) {
            return "${currencyMatch.groupValues[1]} ر.ي"
        }

        // 3. Check standalone standard voucher prices in comment or profileName
        val commonPrices = listOf(100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 1000, 1200, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 10000)
        for (price in commonPrices) {
            if (Regex("""\b$price\b""").containsMatchIn(comment) || Regex("""\b$price\b""").containsMatchIn(profileName)) {
                return "$price ر.ي"
            }
        }

        return "غير محدد"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f GB", gb).replace(".0 GB", " GB")
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.0f MB", mb)
        }
    }

    private fun extractDataLimitFromText(name: String, comment: String): String {
        val combined = "$name $comment".lowercase()
        if (combined.isBlank()) return "غير محدد"
        
        // Match patterns like: 1024MB, 1GB, 500 ميجا, 2 جيجا, 1000m, 500mb
        val regex = Regex("""(\d+(?:\.\d+)?)\s*(gb|mb|gi|mi|g|m|جيجا|ميجا)""")
        val match = regex.find(combined)
        
        if (match != null && match.groupValues.size > 2) {
            val num = match.groupValues[1]
            val unit = match.groupValues[2]
            val normalizedUnit = when {
                unit.startsWith("g") || unit.contains("جيجا") -> "GB"
                else -> "MB"
            }
            return "$num $normalizedUnit"
        }
        return "غير محدد"
    }

    private fun parseRateLimitSpeeds(rateLimit: String): Pair<String, String> {
        if (rateLimit.isBlank() || rateLimit == "غير محدد") {
            return Pair("غير محدد", "غير محدد")
        }
        val parts = rateLimit.split("/")
        if (parts.size >= 2) {
            val up = parts[0].trim().replace("M", " Mbps").replace("k", " Kbps")
            val down = parts[1].split(" ")[0].trim().replace("M", " Mbps").replace("k", " Kbps")
            return Pair(up, down)
        }
        return Pair(rateLimit, rateLimit)
    }

    suspend fun getIpBindings(): Result<List<IpBinding>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/hotspot/ip-binding/print"))
                val list = mutableListOf<IpBinding>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        list.add(
                            IpBinding(
                                id = map[".id"] ?: "b_${list.size}",
                                macAddress = map["mac-address"] ?: "",
                                address = map["address"] ?: "",
                                type = map["type"] ?: "bypassed",
                                comment = map["comment"] ?: "جهاز معفى",
                                disabled = map["disabled"] == "true" || map["disabled"] == "yes"
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun kickUser(userId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeCommandSentence(listOf("/ip/hotspot/active/remove", "=.id=$userId"))
                val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trap != null) {
                    val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "تعذر فصل المشترك"
                    if (msg.contains("no such item", true) || msg.contains("not found", true)) {
                        return@withContext Result.success(true)
                    }
                    return@withContext Result.failure(Exception(msg))
                }
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                Result.success(isDone)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun disconnectActiveSession(cardName: String, system: CardTargetSystem? = null) {
        val cleanName = cardName.trim()
        if (cleanName.isBlank()) return
        try {
            if (system == null || system == CardTargetSystem.HOTSPOT) {
                val sentences = executeCommandSentence(listOf("/ip/hotspot/active/print", "?user=$cleanName"))
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val actId = map[".id"]
                        if (!actId.isNullOrBlank()) {
                            executeCommandSentence(listOf("/ip/hotspot/active/remove", "=.id=$actId"))
                        }
                    }
                }
            }

            if (system == null || system == CardTargetSystem.USER_MANAGER_V7) {
                try {
                    val sentences = executeCommandSentence(listOf("/user-manager/session/print", "?user=$cleanName"))
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val actId = map[".id"]
                            if (!actId.isNullOrBlank()) {
                                executeCommandSentence(listOf("/user-manager/session/remove", "=.id=$actId"))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (system == null || system == CardTargetSystem.USER_MANAGER_V6) {
                try {
                    val sentences = executeCommandSentence(listOf("/tool/user-manager/active-sessions/print", "?user=$cleanName"))
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val actId = map[".id"] ?: map["user"]
                            if (!actId.isNullOrBlank()) {
                                executeCommandSentence(listOf("/tool/user-manager/active-sessions/remove", "=numbers=$actId"))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    suspend fun resolveCardTarget(cardId: String, cardName: String, hintType: String = ""): ResolvedCardTarget? {
        val targetName = cardName.trim().ifBlank { cardId.trim() }
        if (targetName.isBlank()) return null

        fun queryItem(cmd: String, filter: String): Map<String, String>? {
            return try {
                val sentences = executeCommandSentence(listOf(cmd, filter))
                val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                if (re != null) parseSentenceToMap(re) else null
            } catch (_: Exception) {
                null
            }
        }

        // 1. Check Hotspot first if hinted or default
        if (hintType.equals("hotspot", ignoreCase = true) || hintType.isBlank()) {
            val hsByName = queryItem("/ip/hotspot/user/print", "?name=$targetName")
            if (hsByName != null) {
                val id = hsByName[".id"] ?: cardId
                return ResolvedCardTarget(CardTargetSystem.HOTSPOT, id, hsByName["name"] ?: targetName)
            }
            if (cardId.startsWith("*")) {
                val hsById = queryItem("/ip/hotspot/user/print", "?.id=$cardId")
                if (hsById != null) {
                    return ResolvedCardTarget(CardTargetSystem.HOTSPOT, cardId, hsById["name"] ?: targetName)
                }
            }
        }

        // 2. Check User Manager v7
        if (hintType.equals("usermanager", ignoreCase = true) || hintType.isBlank()) {
            val um7ByName = queryItem("/user-manager/user/print", "?name=$targetName")
            if (um7ByName != null) {
                val id = um7ByName[".id"] ?: cardId
                return ResolvedCardTarget(CardTargetSystem.USER_MANAGER_V7, id, um7ByName["name"] ?: targetName)
            }
            if (cardId.startsWith("*")) {
                val um7ById = queryItem("/user-manager/user/print", "?.id=$cardId")
                if (um7ById != null) {
                    return ResolvedCardTarget(CardTargetSystem.USER_MANAGER_V7, cardId, um7ById["name"] ?: targetName)
                }
            }
        }

        // 3. Check User Manager v6
        val um6ByName = queryItem("/tool/user-manager/user/print", "?username=$targetName")
            ?: queryItem("/tool/user-manager/user/print", "?name=$targetName")
        if (um6ByName != null) {
            val id = um6ByName[".id"] ?: targetName
            return ResolvedCardTarget(CardTargetSystem.USER_MANAGER_V6, id, targetName)
        }

        // 4. Fallback check Hotspot if was hinted usermanager
        if (hintType.equals("usermanager", ignoreCase = true)) {
            val hsByName = queryItem("/ip/hotspot/user/print", "?name=$targetName")
            if (hsByName != null) {
                val id = hsByName[".id"] ?: cardId
                return ResolvedCardTarget(CardTargetSystem.HOTSPOT, id, hsByName["name"] ?: targetName)
            }
        }

        // 5. If cardId starts with *, assume Hotspot ID
        if (cardId.startsWith("*")) {
            return ResolvedCardTarget(CardTargetSystem.HOTSPOT, cardId, targetName)
        }

        return null
    }

    suspend fun deleteCard(cardId: String, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val targetName = cardName.trim().ifBlank { cardId.trim() }

                // 1. Auto-detect card system and exact router .id
                val resolved = resolveCardTarget(cardId, targetName)

                // 2. Disconnect active session first immediately
                disconnectActiveSession(targetName, resolved?.system)

                var realTrapError: String? = null
                fun executeRemoval(cmd: String, idParam: String): Result<Boolean> {
                    val sentences = executeCommandSentence(listOf(cmd, idParam))
                    val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trap != null) {
                        val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "رفض الراوتر حذف الكرت"
                        // If item is not found or already deleted on router, treat as successful idempotent removal
                        if (msg.contains("no such item", true) || msg.contains("not found", true) || msg.contains("nothing to do", true) || msg.contains("already", true)) {
                            return Result.success(true)
                        }
                        // Fallback: If .id was rejected as unknown parameter, retry with numbers
                        if (idParam.startsWith("=.id=") && (msg.contains("unknown parameter", true) || msg.contains("syntax", true))) {
                            val altParam = "=numbers=" + idParam.removePrefix("=.id=")
                            val altSentences = executeCommandSentence(listOf(cmd, altParam))
                            val altTrap = altSentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (altTrap == null && altSentences.any { it.firstOrNull() == "!done" }) {
                                return Result.success(true)
                            }
                        }
                        if (!msg.contains("no such command", true) && !msg.contains("unknown command", true)) {
                            realTrapError = msg
                        }
                        return Result.failure(Exception(msg))
                    }
                    if (sentences.any { it.firstOrNull() == "!done" }) {
                        return Result.success(true)
                    }
                    return Result.failure(Exception("لم يؤكد الراوتر حذف الكرت"))
                }

                if (resolved != null) {
                    val res = when (resolved.system) {
                        CardTargetSystem.HOTSPOT -> {
                            val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                            val primary = executeRemoval("/ip/hotspot/user/remove", idParam)
                            if (primary.isSuccess) primary else executeRemoval("/ip/hotspot/user/remove", "=numbers=$targetName")
                        }
                        CardTargetSystem.USER_MANAGER_V7 -> {
                            val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                            val primary = executeRemoval("/user-manager/user/remove", idParam)
                            if (primary.isSuccess) primary else executeRemoval("/user-manager/user/remove", "=numbers=$targetName")
                        }
                        CardTargetSystem.USER_MANAGER_V6 -> {
                            val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                            val primary = executeRemoval("/tool/user-manager/user/remove", idParam)
                            if (primary.isSuccess) primary else executeRemoval("/tool/user-manager/user/remove", "=numbers=$targetName")
                        }
                    }
                    if (res.isSuccess) return@withContext res
                    if (realTrapError != null) return@withContext Result.failure(Exception(realTrapError!!))
                }

                // Fallback removal across systems
                val actualId = if (cardId.startsWith("*")) cardId else ""
                if (actualId.isNotBlank()) {
                    val hsRes = executeRemoval("/ip/hotspot/user/remove", "=.id=$actualId")
                    if (hsRes.isSuccess) return@withContext hsRes
                    val hsNumRes = executeRemoval("/ip/hotspot/user/remove", "=numbers=$actualId")
                    if (hsNumRes.isSuccess) return@withContext hsNumRes
                    val um7Res = executeRemoval("/user-manager/user/remove", "=.id=$actualId")
                    if (um7Res.isSuccess) return@withContext um7Res
                    val um7NumRes = executeRemoval("/user-manager/user/remove", "=numbers=$actualId")
                    if (um7NumRes.isSuccess) return@withContext um7NumRes
                }
                if (targetName.isNotBlank()) {
                    val hsNameRes = executeRemoval("/ip/hotspot/user/remove", "=numbers=$targetName")
                    if (hsNameRes.isSuccess) return@withContext hsNameRes
                    val um7NameRes = executeRemoval("/user-manager/user/remove", "=numbers=$targetName")
                    if (um7NameRes.isSuccess) return@withContext um7NameRes
                    val um6Res = executeRemoval("/tool/user-manager/user/remove", "=numbers=$targetName")
                    if (um6Res.isSuccess) return@withContext um6Res
                }

                if (realTrapError != null) {
                    return@withContext Result.failure(Exception(realTrapError!!))
                }
                // If not found anywhere on the router, it has already been removed
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleCardDisabled(cardId: String, currentDisabled: Boolean, cardName: String = ""): Result<Boolean> {
        return setCardDisabled(cardId, targetDisabled = !currentDisabled, cardName = cardName)
    }

    suspend fun setCardDisabled(cardId: String, targetDisabled: Boolean, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val targetName = cardName.trim().ifBlank { cardId.trim() }
                val resolved = resolveCardTarget(cardId, targetName)

                val willDisable = targetDisabled
                val newVal = if (willDisable) "yes" else "no"

                // Disconnect active session if disabling
                if (willDisable) {
                    disconnectActiveSession(targetName, resolved?.system)
                }

                fun isMissingItemTrap(msg: String): Boolean {
                    return msg.contains("no such item", ignoreCase = true) ||
                        msg.contains("not found", ignoreCase = true) ||
                        msg.contains("no such command", ignoreCase = true) ||
                        msg.contains("unknown command", ignoreCase = true)
                }

                fun executeSet(cmd: String, idParam: String): Result<Boolean> {
                    val sentences = executeCommandSentence(listOf(cmd, idParam, "=disabled=$newVal"))
                    val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trap != null) {
                        val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل تغيير حالة الكرت"
                        return Result.failure(Exception(msg))
                    }
                    if (sentences.any { it.firstOrNull() == "!done" }) {
                        return Result.success(true)
                    }
                    return Result.failure(Exception("لم يؤكد الراوتر تغيير حالة الكرت"))
                }

                fun commandFor(sys: CardTargetSystem): String = when (sys) {
                    CardTargetSystem.HOTSPOT -> "/ip/hotspot/user/set"
                    CardTargetSystem.USER_MANAGER_V7 -> "/user-manager/user/set"
                    CardTargetSystem.USER_MANAGER_V6 -> "/tool/user-manager/user/set"
                }

                if (resolved != null) {
                    val cmd = commandFor(resolved.system)
                    val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                    val primary = executeSet(cmd, idParam)
                    if (primary.isSuccess) return@withContext primary
                    val errMsg = primary.exceptionOrNull()?.message.orEmpty()
                    if (!isMissingItemTrap(errMsg)) {
                        return@withContext primary
                    }
                }

                val candidateSystems = listOf(
                    CardTargetSystem.HOTSPOT,
                    CardTargetSystem.USER_MANAGER_V7,
                    CardTargetSystem.USER_MANAGER_V6
                ).filter { it != resolved?.system }

                var lastFailure: Result<Boolean>? = null
                for (sys in candidateSystems) {
                    val cmd = commandFor(sys)
                    if (sys == CardTargetSystem.HOTSPOT && cardId.startsWith("*")) {
                        val byId = executeSet(cmd, "=.id=$cardId")
                        if (byId.isSuccess) return@withContext byId
                        val byIdErr = byId.exceptionOrNull()?.message.orEmpty()
                        if (!isMissingItemTrap(byIdErr)) return@withContext byId
                        lastFailure = byId
                    }
                    if (targetName.isNotBlank()) {
                        val byName = executeSet(cmd, "=numbers=$targetName")
                        if (byName.isSuccess) return@withContext byName
                        val byNameErr = byName.exceptionOrNull()?.message.orEmpty()
                        if (!isMissingItemTrap(byNameErr)) return@withContext byName
                        lastFailure = byName
                    }
                }

                lastFailure ?: Result.failure(Exception("لم يتم العثور على الكرت ($targetName) في الراوتر"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addCard(
        card: HotspotCard,
        preferredSystem: String = "",
        profileSource: String = ""
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("فشل الاتصال براوتر ميكروتك عبر منفذ API ($lastPort)"))
                }
                val profileName = card.profile.trim()

                // Precise profile lookup with source disambiguation
                val targetProfile = synchronized(cachedProfiles) {
                    if (profileSource.isNotBlank()) {
                        cachedProfiles.firstOrNull { it.name.trim().equals(profileName, ignoreCase = true) && it.source == profileSource }
                    } else if (preferredSystem == "usermanager" || card.isUserManagerCard) {
                        cachedProfiles.firstOrNull { it.name.trim().equals(profileName, ignoreCase = true) && it.isUserManager }
                    } else if (preferredSystem == "hotspot" || card.isHotspotCard) {
                        cachedProfiles.firstOrNull { it.name.trim().equals(profileName, ignoreCase = true) && it.isHotspot }
                    } else {
                        null
                    } ?: cachedProfiles.firstOrNull { it.name.trim().equals(profileName, ignoreCase = true) }
                }

                val isUmProfile = targetProfile?.isUserManager == true ||
                    targetProfile?.source?.startsWith("usermanager") == true ||
                    profileSource.startsWith("usermanager")

                val isHsProfile = targetProfile?.isHotspot == true ||
                    targetProfile?.source == "hotspot" ||
                    profileSource == "hotspot"

                val shouldBeUserManager = when {
                    isUmProfile -> true
                    isHsProfile -> false
                    preferredSystem == "usermanager" || card.isUserManagerCard -> true
                    preferredSystem == "hotspot" || card.isHotspotCard -> false
                    else -> synchronized(cachedProfiles) {
                        if (cachedProfiles.any { it.isUserManager } && cachedProfiles.none { it.isHotspot }) true
                        else if (cachedProfiles.any { it.isHotspot } && cachedProfiles.none { it.isUserManager }) false
                        else false
                    }
                }

                // 1. User Manager system
                if (shouldBeUserManager) {
                    val isV6 = targetProfile?.source == "usermanager_v6" || profileSource == "usermanager_v6"
                    val profileAlreadyExists = targetProfile != null && targetProfile.name.trim().equals(profileName, ignoreCase = true)
                    if (!profileAlreadyExists && profileName.isNotBlank() && !profileName.equals("default", ignoreCase = true)) {
                        ensureUserManagerProfileExists(profileName, targetProfile, isV6 = isV6)
                    }

                    val umResult = if (isV6) {
                        val customer = targetProfile?.comment?.takeIf { it.isNotBlank() } ?: "admin"
                        addUserManagerV6User(card, customer)
                    } else {
                        addUserManagerV7User(card)
                    }

                    if (umResult.isSuccess) {
                        return@withContext umResult
                    } else {
                        val umError = umResult.exceptionOrNull()?.message ?: "فشل إنشاء كرت يوزرمانجر"
                        // Intelligent fallback: If router does not have user-manager package installed ("no such command") or user preferred hotspot
                        if (umError.contains("no such command", ignoreCase = true) || umError.contains("no such", ignoreCase = true) ||
                            (preferredSystem == "hotspot" || card.isHotspotCard)) {
                            if (profileName.isNotBlank() && !profileName.equals("default", ignoreCase = true)) {
                                ensureHotspotProfileExists(profileName, targetProfile)
                            }
                            val hsFallback = addHotspotNativeUser(card)
                            if (hsFallback.isSuccess) {
                                return@withContext hsFallback
                            }
                        }
                        return@withContext Result.failure(Exception("خطأ في نظام يوزرمانجر: $umError"))
                    }
                }

                // 2. Native Hotspot (/ip/hotspot/user/add)
                if (profileName.isNotBlank() && !profileName.equals("default", ignoreCase = true)) {
                    ensureHotspotProfileExists(profileName, targetProfile)
                }

                val hsResult = addHotspotNativeUser(card)
                if (hsResult.isSuccess) {
                    return@withContext hsResult
                }

                val errMsg = hsResult.exceptionOrNull()?.message.orEmpty()
                // Intelligent fallback: If router does not have Hotspot package ("no such command"), try User Manager
                if (errMsg.contains("no such command", ignoreCase = true) || errMsg.contains("no such", ignoreCase = true)) {
                    val umFallback = addUserManagerV7User(card)
                    if (umFallback.isSuccess) return@withContext umFallback
                }

                // If failed because profile was not found on the router, auto-create it now and retry
                if (errMsg.contains("profile", ignoreCase = true) || errMsg.contains("not found", ignoreCase = true)) {
                    createHotspotProfileSimple(profileName)
                    val retryResult = addHotspotNativeUser(card)
                    if (retryResult.isSuccess) return@withContext retryResult
                }

                return@withContext hsResult
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun ensureHotspotProfileExists(profileName: String, targetProfile: HotspotProfile?) {
        try {
            val sentences = executeCommandSentence(listOf("/ip/hotspot/user/profile/print", "?name=$profileName"))
            val exists = sentences.any { it.firstOrNull() == "!re" }
            if (!exists) {
                val words = mutableListOf(
                    "/ip/hotspot/user/profile/add",
                    "=name=$profileName",
                    "=shared-users=${targetProfile?.sharedUsers?.coerceAtLeast(1) ?: 1}"
                )
                val rate = targetProfile?.rateLimit?.takeIf { it.isNotBlank() && it != "غير محدد" && it != "مفتوح" }
                if (rate != null) words.add("=rate-limit=$rate")
                val timeout = targetProfile?.sessionTimeout?.let { formatRouterOsTime(it) }
                if (timeout != null) words.add("=session-timeout=$timeout")
                if (!targetProfile?.price.isNullOrBlank()) words.add("=comment=price=${targetProfile.price}")
                executeCommandSentence(words)
            }
        } catch (_: Exception) {}
    }

    private fun createHotspotProfileSimple(profileName: String) {
        try {
            executeCommandSentence(listOf("/ip/hotspot/user/profile/add", "=name=$profileName", "=shared-users=1"))
        } catch (_: Exception) {}
    }

    private fun ensureUserManagerProfileExists(profileName: String, targetProfile: HotspotProfile?, isV6: Boolean = false) {
        if (isV6) {
            try {
                // Check v6 only
                val v6Sentences = executeCommandSentence(listOf("/tool/user-manager/profile/print", "?name=$profileName"))
                if (v6Sentences.none { it.firstOrNull() == "!re" }) {
                    val v6Words = mutableListOf("/tool/user-manager/profile/add", "=name=$profileName", "=customer=admin")
                    val valTime = targetProfile?.validity?.let { formatRouterOsTime(it) } ?: "1d"
                    v6Words.add("=validity=$valTime")
                    executeCommandSentence(v6Words)
                }
            } catch (_: Exception) {}
        } else {
            try {
                // Check v7 only
                val v7Sentences = executeCommandSentence(listOf("/user-manager/profile/print", "?name=$profileName"))
                if (v7Sentences.none { it.firstOrNull() == "!re" }) {
                    val v7Words = mutableListOf("/user-manager/profile/add", "=name=$profileName")
                    val valTime = targetProfile?.validity?.let { formatRouterOsTime(it) } ?: "1d"
                    v7Words.add("=validity=$valTime")
                    val priceNum = targetProfile?.priceNumber?.takeIf { it > 0 } ?: 0.0
                    if (priceNum > 0) v7Words.add("=price=${priceNum.toInt()}")
                    executeCommandSentence(v7Words)
                }
            } catch (_: Exception) {}
        }
    }

    private fun addHotspotNativeUser(card: HotspotCard): Result<Boolean> {
        val cleanProfile = card.profile.trim()
        val words = mutableListOf(
            "/ip/hotspot/user/add",
            "=name=${card.name.trim()}"
        )
        if (card.password.isNotBlank()) {
            words.add("=password=${card.password.trim()}")
        }
        if (cleanProfile.isNotBlank()) {
            words.add("=profile=$cleanProfile")
        }
        val disabledFlag = if (card.disabled) "yes" else "no"
        words.add("=disabled=$disabledFlag")
        words.add("=server=all")

        val validUptime = formatRouterOsTime(card.limitUptime)
        if (!validUptime.isNullOrBlank()) {
            words.add("=limit-uptime=$validUptime")
        }
        if (card.limitBytesTotal > 0) {
            words.add("=limit-bytes-total=${card.limitBytesTotal}")
        }
        if (card.comment.isNotBlank()) {
            words.add("=comment=${card.comment}")
        }

        val sentences = executeCommandSentence(words)
        val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
        if (trapSentence != null) {
            val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                ?: "رفض راوتر ميكروتك إضافة الكرت"

            // If already exists on router, sync its requested disabled flag
            if (trapMsg.contains("already exists", ignoreCase = true) || trapMsg.contains("already have", ignoreCase = true)) {
                executeCommandSentence(listOf("/ip/hotspot/user/set", "=numbers=${card.name.trim()}", "=disabled=$disabledFlag"))
                return Result.success(true)
            }

            // If server parameter was rejected (some v6 firmware / setups)
            if (trapMsg.contains("server", ignoreCase = true) || trapMsg.contains("unknown parameter", ignoreCase = true)) {
                val simpleWords = words.filter { !it.startsWith("=server=") }
                val retrySentences = executeCommandSentence(simpleWords)
                if (retrySentences.any { it.firstOrNull() == "!done" } && retrySentences.none { it.firstOrNull() == "!trap" }) {
                    return Result.success(true)
                }
            }

            val isParamOrVersionTrap = trapMsg.contains("argument", ignoreCase = true) ||
                trapMsg.contains("parameter", ignoreCase = true) ||
                trapMsg.contains("unknown", ignoreCase = true) ||
                trapMsg.contains("limit", ignoreCase = true) ||
                trapMsg.contains("invalid", ignoreCase = true) ||
                trapMsg.contains("value", ignoreCase = true) ||
                trapMsg.contains("profile", ignoreCase = true)

            if (isParamOrVersionTrap) {
                // Retry with clean minimal command: name, password, profile, disabled=no
                val simpleWords = mutableListOf(
                    "/ip/hotspot/user/add",
                    "=name=${card.name.trim()}",
                    "=disabled=$disabledFlag"
                )
                if (card.password.isNotBlank()) {
                    simpleWords.add("=password=${card.password.trim()}")
                }
                if (cleanProfile.isNotBlank()) {
                    simpleWords.add("=profile=$cleanProfile")
                }
                if (card.comment.isNotBlank()) {
                    simpleWords.add("=comment=${card.comment}")
                }
                val retrySentences = executeCommandSentence(simpleWords)
                val retryTrap = retrySentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (retryTrap == null && retrySentences.any { it.firstOrNull() == "!done" }) {
                    return Result.success(true)
                }
            }
            return Result.failure(Exception(trapMsg))
        }
        val isDone = sentences.any { it.firstOrNull() == "!done" }
        if (!isDone) {
            return Result.failure(Exception("لم يؤكد الراوتر إضافة الكرت"))
        }
        return Result.success(true)
    }

    private fun addUserManagerV7User(card: HotspotCard): Result<Boolean> {
        val cardName = card.name.trim()
        val cleanProfile = card.profile.trim()
        val startTime = System.currentTimeMillis()

        try {
            // Stage 1: Pre-check if user already exists on UM v7 & extract internalId
            var internalId: String? = null
            var userExists = false
            try {
                val printExisting = executeCommandSentence(listOf("/user-manager/user/print", "?name=$cardName"))
                for (s in printExisting) {
                    if (s.firstOrNull() == "!re") {
                        userExists = true
                        val idVal = s.firstOrNull { it.startsWith("=.id=") }?.removePrefix("=.id=")
                        if (!idVal.isNullOrBlank()) {
                            internalId = idVal
                            break
                        }
                    }
                }
            } catch (_: Exception) {}

            val disabledFlag = if (card.disabled) "yes" else "no"
            // Stage 2: Create user if not already present
            if (!userExists) {
                val words = mutableListOf(
                    "/user-manager/user/add",
                    "=name=$cardName",
                    "=disabled=$disabledFlag"
                )
                if (card.password.isNotBlank()) {
                    words.add("=password=${card.password.trim()}")
                }
                if (card.comment.isNotBlank()) {
                    words.add("=comment=${card.comment}")
                }
                val addSentences = executeCommandSentence(words)
                val trap = addSentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trap != null) {
                    val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إضافة مستخدم يوزرمانجر"
                    if (msg.contains("no such command", ignoreCase = true) || msg.contains("no such", ignoreCase = true)) {
                        return addUserManagerV6User(card, "admin")
                    }
                    if (!msg.contains("already exists", ignoreCase = true) && !msg.contains("already have", ignoreCase = true)) {
                        return Result.failure(Exception("خطأ الراوتر في إنشاء المستخدم: $msg"))
                    }
                }
                val doneSentence = addSentences.firstOrNull { it.firstOrNull() == "!done" }
                val retId = doneSentence?.firstOrNull { it.startsWith("=ret=") }?.removePrefix("=ret=")
                if (!retId.isNullOrBlank()) {
                    internalId = retId
                }
            }

            // Stage 3: Bind and verify User-Profile in UM v7
            if (cleanProfile.isNotBlank() && !cleanProfile.equals("default", ignoreCase = true)) {
                var profileAlreadyBound = false
                try {
                    val existingProfSentences = executeCommandSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
                    profileAlreadyBound = existingProfSentences.any { s ->
                        s.firstOrNull() == "!re" && s.any { it.startsWith("=profile=") && it.removePrefix("=profile=").trim().equals(cleanProfile, ignoreCase = true) }
                    }
                } catch (_: Exception) {}

                if (!profileAlreadyBound) {
                    val profWords = listOf(
                        "/user-manager/user-profile/add",
                        "=user=$cardName",
                        "=profile=$cleanProfile"
                    )
                    val profSentences = executeCommandSentence(profWords)
                    val profTrap = profSentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (profTrap != null) {
                        val profMsg = profTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "فشل ربط المستخدم بالباقة في يوزرمانجر"

                        if (!profMsg.contains("already", ignoreCase = true)) {
                            val recheck = executeCommandSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
                            val verified = recheck.any { it.firstOrNull() == "!re" }
                            if (!verified) {
                                return Result.failure(Exception("تم إنشاء المستخدم $cardName ولكن تعذر ربط الباقة ($cleanProfile): $profMsg"))
                            }
                        }
                    }
                }
            }

            // Stage 4: Ensure requested disabled state (only if user existed and had internalId)
            if (userExists && !internalId.isNullOrBlank()) {
                try {
                    executeCommandSentence(listOf("/user-manager/user/set", "=numbers=$internalId", "=disabled=$disabledFlag"))
                } catch (_: Exception) {}
            }

            return Result.success(true)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val rawMsg = e.message ?: e.localizedMessage ?: "Timeout"

            // Reconciliation check
            try {
                if (!isExplicitlyClosed && lastHost.isNotBlank()) {
                    val checkUser = executeCommandSentence(listOf("/user-manager/user/print", "?name=$cardName"))
                    if (checkUser.any { it.firstOrNull() == "!re" }) {
                        if (cleanProfile.isNotBlank() && !cleanProfile.equals("default", ignoreCase = true)) {
                            try {
                                val checkProf = executeCommandSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
                                if (checkProf.none { it.firstOrNull() == "!re" }) {
                                    executeCommandSentence(listOf("/user-manager/user-profile/add", "=user=$cardName", "=profile=$cleanProfile"))
                                }
                            } catch (_: Exception) {}
                        }
                        return Result.success(true)
                    }
                }
            } catch (_: Exception) {}

            return Result.failure(Exception("استغرقت العملية ($duration ms) مع الراوتر: $rawMsg."))
        }
    }

    private fun addUserManagerV6User(card: HotspotCard, customer: String): Result<Boolean> {
        val cardName = card.name.trim()
        val cleanProfile = card.profile.trim()
        val cust = if (customer.isBlank()) "admin" else customer
        val startTime = System.currentTimeMillis()

        try {
            // Stage 1: Pre-check if user already exists on UM v6 & extract internalId (.id)
            var internalId: String? = null
            var userAlreadyExists = false
            try {
                val checkV6 = executeCommandSentence(listOf("/tool/user-manager/user/print", "?username=$cardName"))
                for (s in checkV6) {
                    if (s.firstOrNull() == "!re") {
                        userAlreadyExists = true
                        val idVal = s.firstOrNull { it.startsWith("=.id=") }?.removePrefix("=.id=")
                        if (!idVal.isNullOrBlank()) {
                            internalId = idVal
                            break
                        }
                    }
                }
            } catch (_: Exception) {}

            val disabledFlag = if (card.disabled) "yes" else "no"
            // Stage 2: Add user in User Manager v6 if not present
            if (!userAlreadyExists) {
                val words = mutableListOf(
                    "/tool/user-manager/user/add",
                    "=username=$cardName",
                    "=customer=$cust",
                    "=disabled=$disabledFlag"
                )
                if (card.password.isNotBlank()) {
                    words.add("=password=${card.password.trim()}")
                }
                if (card.comment.isNotBlank()) {
                    words.add("=comment=${card.comment}")
                }
                val addSentences = executeCommandSentence(words)
                val trap = addSentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trap != null) {
                    val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إضافة كرت يوزرمانجر v6"
                    if (!msg.contains("already exists", ignoreCase = true)) {
                        return Result.failure(Exception("خطأ الراوتر في إنشاء المستخدم: $msg"))
                    }
                }
                val doneSentence = addSentences.firstOrNull { it.firstOrNull() == "!done" }
                val retId = doneSentence?.firstOrNull { it.startsWith("=ret=") }?.removePrefix("=ret=")
                if (!retId.isNullOrBlank()) {
                    internalId = retId
                }
            }

            // Ensure we have the internalId if missing
            if (internalId.isNullOrBlank()) {
                try {
                    val reCheck = executeCommandSentence(listOf("/tool/user-manager/user/print", "?username=$cardName"))
                    for (s in reCheck) {
                        if (s.firstOrNull() == "!re") {
                            val idVal = s.firstOrNull { it.startsWith("=.id=") }?.removePrefix("=.id=")
                            if (!idVal.isNullOrBlank()) {
                                internalId = idVal
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Stage 3: Assign & activate profile in UM v6
            if (cleanProfile.isNotBlank() && !cleanProfile.equals("default", ignoreCase = true)) {
                var profileAssigned = false

                // Method A: Direct user-profile/add
                try {
                    val userProfWords = listOf(
                        "/tool/user-manager/user-profile/add",
                        "=user=$cardName",
                        "=profile=$cleanProfile"
                    )
                    val profSentences = executeCommandSentence(userProfWords)
                    val profTrap = profSentences.firstOrNull { it.firstOrNull() == "!trap" }
                    val profDone = profSentences.any { it.firstOrNull() == "!done" }
                    if (profDone && profTrap == null) {
                        profileAssigned = true
                    }
                } catch (_: Exception) {}

                // Method B: create-and-activate-profile using internal .id pointer (e.g. *1F)
                if (!profileAssigned && !internalId.isNullOrBlank()) {
                    try {
                        val actWords = mutableListOf(
                            "/tool/user-manager/user/create-and-activate-profile",
                            "=numbers=$internalId",
                            "=profile=$cleanProfile"
                        )
                        if (cust.isNotBlank()) {
                            actWords.add("=customer=$cust")
                        }
                        val actSentences = executeCommandSentence(actWords)
                        val actTrap = actSentences.firstOrNull { it.firstOrNull() == "!trap" }
                        val actDone = actSentences.any { it.firstOrNull() == "!done" }
                        if (actDone && actTrap == null) {
                            profileAssigned = true
                        }
                    } catch (_: Exception) {}
                }

                // Method C: create-and-activate-profile without customer argument
                if (!profileAssigned && !internalId.isNullOrBlank()) {
                    try {
                        val actWords = listOf(
                            "/tool/user-manager/user/create-and-activate-profile",
                            "=numbers=$internalId",
                            "=profile=$cleanProfile"
                        )
                        val actSentences = executeCommandSentence(actWords)
                        val actTrap = actSentences.firstOrNull { it.firstOrNull() == "!trap" }
                        val actDone = actSentences.any { it.firstOrNull() == "!done" }
                        if (actDone && actTrap == null) {
                            profileAssigned = true
                        }
                    } catch (_: Exception) {}
                }

                // Method D: Verify if profile is bound or active
                if (!profileAssigned) {
                    try {
                        val checkProf = executeCommandSentence(listOf("/tool/user-manager/user-profile/print", "?user=$cardName"))
                        if (checkProf.any { it.firstOrNull() == "!re" }) {
                            profileAssigned = true
                        }
                    } catch (_: Exception) {}
                }

                if (!profileAssigned) {
                    return Result.failure(Exception("تم إنشاء المستخدم $cardName ولكن تعذر ربط وتفعيل باقة v6 ($cleanProfile)"))
                }
            }

            // Stage 4: Ensure requested disabled state (only if previously existed or using internalId)
            if (userAlreadyExists && !internalId.isNullOrBlank()) {
                try {
                    executeCommandSentence(listOf("/tool/user-manager/user/set", "=numbers=$internalId", "=disabled=$disabledFlag"))
                } catch (_: Exception) {}
            }

            return Result.success(true)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val rawMsg = e.message ?: e.localizedMessage ?: "Timeout"

            // Non-blocking Reconciliation
            try {
                if (!isExplicitlyClosed && lastHost.isNotBlank()) {
                    val checkV6 = executeCommandSentence(listOf("/tool/user-manager/user/print", "?username=$cardName"))
                    val userRec = checkV6.firstOrNull { it.firstOrNull() == "!re" }
                    if (userRec != null) {
                        if (cleanProfile.isNotBlank() && !cleanProfile.equals("default", ignoreCase = true)) {
                            try {
                                executeCommandSentence(listOf(
                                    "/tool/user-manager/user-profile/add",
                                    "=user=$cardName",
                                    "=profile=$cleanProfile"
                                ))
                            } catch (_: Exception) {}
                        }
                        return Result.success(true)
                    }
                }
            } catch (_: Exception) {}

            return Result.failure(Exception("استغرقت العملية ($duration ms) مع راوتر v6: $rawMsg."))
        }
    }

    suspend fun syncProfileToUserManager(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val cleanName = profile.name.trim()
                if (cleanName.isBlank()) return@withContext Result.failure(Exception("اسم الباقة فارغ"))

                // Try v7 user-manager profile add
                val v7Words = mutableListOf(
                    "/user-manager/profile/add",
                    "=name=$cleanName"
                )
                if (profile.validity.isNotBlank() && profile.validity != "غير محدد") {
                    val validTime = formatRouterOsTime(profile.validity)
                    if (validTime != null) v7Words.add("=validity=$validTime")
                }
                val priceNum = profile.priceNumber.takeIf { it > 0 } ?: Regex("""\d+""").find(profile.price)?.value?.toDoubleOrNull()
                if (priceNum != null && priceNum > 0) {
                    v7Words.add("=price=${priceNum.toInt()}")
                }
                val v7Sentences = executeCommandSentence(v7Words)
                val v7Trap = v7Sentences.firstOrNull { it.firstOrNull() == "!trap" }
                val v7TrapMsg = v7Trap?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                if (v7Trap == null && v7Sentences.any { it.firstOrNull() == "!done" }) {
                    return@withContext Result.success(true)
                }
                if (v7TrapMsg != null && v7TrapMsg.contains("already", ignoreCase = true)) {
                    return@withContext Result.success(true)
                }

                // Try v6 tool user-manager profile add
                val v6Words = mutableListOf(
                    "/tool/user-manager/profile/add",
                    "=name=$cleanName",
                    "=customer=admin"
                )
                if (priceNum != null && priceNum > 0) {
                    v6Words.add("=price=${priceNum.toInt()}")
                }
                if (profile.validity.isNotBlank() && profile.validity != "غير محدد") {
                    val validTime = formatRouterOsTime(profile.validity)
                    if (validTime != null) v6Words.add("=validity=$validTime")
                }
                val v6Sentences = executeCommandSentence(v6Words)
                val v6Trap = v6Sentences.firstOrNull { it.firstOrNull() == "!trap" }
                val v6TrapMsg = v6Trap?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                if (v6Trap == null && v6Sentences.any { it.firstOrNull() == "!done" }) {
                    return@withContext Result.success(true)
                }
                if (v6TrapMsg != null && v6TrapMsg.contains("already", ignoreCase = true)) {
                    return@withContext Result.success(true)
                }

                val realErr = listOfNotNull(v7TrapMsg, v6TrapMsg)
                    .firstOrNull { !it.contains("no such command", ignoreCase = true) && !it.contains("unknown command", ignoreCase = true) }
                    ?: "تعذر مزامنة الباقة مع User Manager"
                Result.failure(Exception(realErr))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncProfileToHotspot(profile: HotspotProfile): Result<Boolean> {
        return addProfile(profile)
    }

    suspend fun verifyAndActivateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val targetName = card.name.trim()
                if (targetName.isBlank()) return@withContext Result.failure(Exception("اسم الكرت فارغ"))

                // 1. Disconnect any active sessions first
                disconnectActiveSession(targetName, null)

                val cleanProfile = card.profile.trim().ifBlank { "default" }
                if (!cleanProfile.equals("default", ignoreCase = true)) {
                    ensureHotspotProfileExists(cleanProfile, null)
                }

                var activated = false

                // 2. Resolve target using resolveCardTarget
                val resolved = resolveCardTarget(card.id, targetName, card.cardType)
                if (resolved != null) {
                    when (resolved.system) {
                        CardTargetSystem.HOTSPOT -> {
                            val targetParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${resolved.internalId}"
                            var sentences = executeCommandSentence(listOf(
                                "/ip/hotspot/user/set",
                                targetParam,
                                "=disabled=no",
                                "=profile=$cleanProfile",
                                "=server=all"
                            ))
                            var trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (trap != null) {
                                val trapMsg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=").orEmpty()
                                if (trapMsg.contains("server", ignoreCase = true) || trapMsg.contains("unknown parameter", ignoreCase = true)) {
                                    sentences = executeCommandSentence(listOf(
                                        "/ip/hotspot/user/set",
                                        targetParam,
                                        "=disabled=no",
                                        "=profile=$cleanProfile"
                                    ))
                                    trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                                }
                            }
                            if (trap == null && sentences.any { it.firstOrNull() == "!done" }) {
                                activated = true
                            }
                        }
                        CardTargetSystem.USER_MANAGER_V7 -> {
                            val targetParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${resolved.internalId}"
                            val sentences = executeCommandSentence(listOf(
                                "/user-manager/user/set",
                                targetParam,
                                "=disabled=no"
                            ))
                            val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (trap == null && sentences.any { it.firstOrNull() == "!done" }) {
                                activated = true
                            }
                        }
                        CardTargetSystem.USER_MANAGER_V6 -> {
                            val sentences = executeCommandSentence(listOf(
                                "/tool/user-manager/user/set",
                                "=numbers=$targetName",
                                "=disabled=no"
                            ))
                            val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (trap == null && sentences.any { it.firstOrNull() == "!done" }) {
                                activated = true
                            }
                        }
                    }
                }

                // 3. Fallback: Query Hotspot print by name
                if (!activated) {
                    val hsPrint = executeCommandSentence(listOf("/ip/hotspot/user/print", "?name=$targetName"))
                    val hsRe = hsPrint.firstOrNull { it.firstOrNull() == "!re" }
                    if (hsRe != null) {
                        val map = parseSentenceToMap(hsRe)
                        val internalId = map[".id"] ?: card.id
                        val targetParam = if (internalId.startsWith("*")) "=.id=$internalId" else "=numbers=$internalId"
                        var setRes = executeCommandSentence(listOf(
                            "/ip/hotspot/user/set",
                            targetParam,
                            "=disabled=no",
                            "=profile=$cleanProfile",
                            "=server=all"
                        ))
                        var trap = setRes.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val tMsg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=").orEmpty()
                            if (tMsg.contains("server", ignoreCase = true) || tMsg.contains("unknown parameter", ignoreCase = true)) {
                                setRes = executeCommandSentence(listOf(
                                    "/ip/hotspot/user/set",
                                    targetParam,
                                    "=disabled=no",
                                    "=profile=$cleanProfile"
                                ))
                                trap = setRes.firstOrNull { it.firstOrNull() == "!trap" }
                            }
                        }
                        if (trap != null) {
                            val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "رفض الراوتر تفعيل الكرت"
                            return@withContext Result.failure(Exception(msg))
                        }
                        activated = true
                    } else {
                        // Card does not exist in RouterOS, create it immediately as active in Hotspot
                        val addRes = addHotspotNativeUser(card.copy(profile = cleanProfile, disabled = false))
                        if (addRes.isFailure) {
                            return@withContext addRes
                        }
                        activated = true
                    }
                }

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }

                // 1. Resolve card target system and exact router .id
                val resolved = resolveCardTarget(card.id, card.name, card.cardType)
                    ?: ResolvedCardTarget(CardTargetSystem.HOTSPOT, card.id, card.name)

                // 2. Disconnect active session immediately as requested
                disconnectActiveSession(card.name, resolved.system)

                val cleanProfile = card.profile.trim()
                val cleanPassword = card.password.trim()
                val cleanComment = card.comment.trim()

                when (resolved.system) {
                    CardTargetSystem.HOTSPOT -> {
                        val targetParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${card.name}"
                        val validUptime = formatRouterOsTime(card.limitUptime)
                        val words = mutableListOf(
                            "/ip/hotspot/user/set",
                            targetParam
                        )
                        if (cleanPassword.isNotEmpty()) words.add("=password=$cleanPassword")
                        if (cleanProfile.isNotEmpty()) words.add("=profile=$cleanProfile")
                        words.add("=disabled=${if (card.disabled) "yes" else "no"}")
                        words.add("=comment=$cleanComment")
                        if (!validUptime.isNullOrBlank() && validUptime != "0s") {
                            words.add("=limit-uptime=$validUptime")
                        }
                        if (card.limitBytesTotal > 0L) {
                            words.add("=limit-bytes-total=${card.limitBytesTotal}")
                        }

                        var finalSentences = executeCommandSentence(words)
                        var finalTrap = finalSentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (finalTrap != null) {
                            val trapMsg = finalTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                                ?: "رفض راوتر ميكروتك تعديل كرت الهوتسبوت"

                            // Retry 1: with name parameter, preserving limits & profile
                            val nameWords = mutableListOf(
                                "/ip/hotspot/user/set",
                                "=numbers=${card.name}",
                                "=disabled=${if (card.disabled) "yes" else "no"}"
                            )
                            if (cleanPassword.isNotEmpty()) nameWords.add("=password=$cleanPassword")
                            if (cleanProfile.isNotEmpty()) nameWords.add("=profile=$cleanProfile")
                            if (cleanComment.isNotEmpty()) nameWords.add("=comment=$cleanComment")
                            if (!validUptime.isNullOrBlank() && validUptime != "0s") {
                                nameWords.add("=limit-uptime=$validUptime")
                            }
                            if (card.limitBytesTotal > 0L) {
                                nameWords.add("=limit-bytes-total=${card.limitBytesTotal}")
                            }

                            finalSentences = executeCommandSentence(nameWords)
                            finalTrap = finalSentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (finalTrap != null) {
                                // Retry 2: core targetParam without limits if router rejected limit syntax
                                val coreWords = mutableListOf(
                                    "/ip/hotspot/user/set",
                                    targetParam,
                                    "=disabled=${if (card.disabled) "yes" else "no"}"
                                )
                                if (cleanPassword.isNotEmpty()) coreWords.add("=password=$cleanPassword")
                                if (cleanProfile.isNotEmpty()) coreWords.add("=profile=$cleanProfile")
                                finalSentences = executeCommandSentence(coreWords)
                                finalTrap = finalSentences.firstOrNull { it.firstOrNull() == "!trap" }
                                if (finalTrap != null) {
                                    val retryMsg = finalTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: trapMsg
                                    return@withContext Result.failure(Exception("رفض الراوتر تعديل الكرت: $retryMsg"))
                                }
                            }
                        }
                        val isDone = finalSentences.any { it.firstOrNull() == "!done" }
                        if (!isDone || finalTrap != null) {
                            return@withContext Result.failure(Exception("لم يؤكد الراوتر تعديل الكرت"))
                        }
                        return@withContext Result.success(true)
                    }

                    CardTargetSystem.USER_MANAGER_V7 -> {
                        val targetParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${card.name}"
                        val words = mutableListOf(
                            "/user-manager/user/set",
                            targetParam,
                            "=password=$cleanPassword",
                            "=comment=$cleanComment",
                            "=disabled=${if (card.disabled) "yes" else "no"}"
                        )
                        var sentences = executeCommandSentence(words)
                        var trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null && targetParam != "=numbers=${card.name}") {
                            sentences = executeCommandSentence(listOf(
                                "/user-manager/user/set",
                                "=numbers=${card.name}",
                                "=password=$cleanPassword",
                                "=comment=$cleanComment",
                                "=disabled=${if (card.disabled) "yes" else "no"}"
                            ))
                            trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        }
                        if (trap != null) {
                            val trapMsg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "رفض راوتر ميكروتك تعديل كرت يوزرمانجر"
                            return@withContext Result.failure(Exception("فشل تعديل كرت يوزرمانجر v7: $trapMsg"))
                        }

                        if (cleanProfile.isNotEmpty()) {
                            var targetAlreadyBound = false
                            try {
                                val existingProfSentences = executeCommandSentence(
                                    listOf("/user-manager/user-profile/print", "?user=${card.name}")
                                )
                                for (ps in existingProfSentences) {
                                    if (ps.firstOrNull() == "!re") {
                                        val pMap = parseSentenceToMap(ps)
                                        val boundProf = pMap["profile"].orEmpty().trim()
                                        val boundId = pMap[".id"].orEmpty()
                                        if (boundProf.equals(cleanProfile, ignoreCase = true)) {
                                            targetAlreadyBound = true
                                        } else if (boundId.isNotBlank()) {
                                            executeCommandSentence(
                                                listOf("/user-manager/user-profile/remove", "=.id=$boundId")
                                            )
                                        }
                                    }
                                }
                            } catch (_: Exception) {}

                            if (!targetAlreadyBound) {
                                val addProfRes = executeCommandSentence(listOf(
                                    "/user-manager/user-profile/add",
                                    "=user=${card.name}",
                                    "=profile=$cleanProfile"
                                ))
                                val addTrap = addProfRes.firstOrNull { it.firstOrNull() == "!trap" }
                                if (addTrap != null) {
                                    val msg = addTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=").orEmpty()
                                    if (!msg.contains("already", ignoreCase = true)) {
                                        return@withContext Result.failure(Exception("تم تعديل الكرت لكن فشل ربط الباقة الجديدة ($cleanProfile): $msg"))
                                    }
                                }
                            }
                        }
                        return@withContext Result.success(true)
                    }

                    CardTargetSystem.USER_MANAGER_V6 -> {
                        val targetParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${card.name}"
                        val words = mutableListOf(
                            "/tool/user-manager/user/set",
                            targetParam,
                            "=password=$cleanPassword",
                            "=actual-profile=$cleanProfile",
                            "=comment=$cleanComment",
                            "=disabled=${if (card.disabled) "yes" else "no"}"
                        )
                        val sentences = executeCommandSentence(words)
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val trapMsg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                                ?: "رفض راوتر ميكروتك تعديل كرت يوزرمانجر v6"
                            return@withContext Result.failure(Exception("فشل تعديل كرت يوزرمانجر v6: $trapMsg"))
                        }
                        return@withContext Result.success(true)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reactivateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val resolved = resolveCardTarget(card.id, card.name, card.cardType)
                    ?: ResolvedCardTarget(CardTargetSystem.HOTSPOT, card.id, card.name)

                // 1. Disconnect active session first
                disconnectActiveSession(card.name, resolved.system)

                when (resolved.system) {
                    CardTargetSystem.HOTSPOT -> {
                        val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${card.name}"
                        executeCommandSentence(listOf("/ip/hotspot/user/reset-counters", idParam))
                        val sentences = executeCommandSentence(listOf("/ip/hotspot/user/set", idParam, "=disabled=no"))
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إعادة تفعيل الكرت"
                            return@withContext Result.failure(Exception(msg))
                        }
                        return@withContext Result.success(true)
                    }
                    CardTargetSystem.USER_MANAGER_V7 -> {
                        val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${card.name}"
                        executeCommandSentence(listOf("/user-manager/user/reset-counters", idParam))
                        val sentences = executeCommandSentence(listOf("/user-manager/user/set", idParam, "=disabled=no"))
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إعادة تفعيل كرت يوزرمانجر"
                            return@withContext Result.failure(Exception(msg))
                        }
                        return@withContext Result.success(true)
                    }
                    CardTargetSystem.USER_MANAGER_V6 -> {
                        val targetParam = "=numbers=${card.name}"
                        executeCommandSentence(listOf("/tool/user-manager/user/reset-counters", targetParam))
                        val sentences = executeCommandSentence(listOf("/tool/user-manager/user/set", targetParam, "=disabled=no"))
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إعادة تفعيل الكرت"
                            return@withContext Result.failure(Exception(msg))
                        }
                        return@withContext Result.success(true)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun formatRouterOsTime(raw: String): String? {
        val clean = raw.trim().lowercase()
        if (clean.isBlank() || clean == "غير محدد" || clean == "مفتوح" || clean == "0" || clean == "0s") {
            return null
        }
        val digits = Regex("""\d+""").find(clean)?.value?.toIntOrNull() ?: 1
        if (clean.contains("ساعة") || clean.contains("ساعات")) {
            return "${digits}h"
        }
        if (clean.contains("يوم") || clean.contains("أيام") || clean.contains("ايام")) {
            return "${digits}d"
        }
        if (clean.contains("أسبوع") || clean.contains("اسبوع")) {
            return "${digits * 7}d"
        }
        if (clean.contains("شهر")) {
            return "${digits * 30}d"
        }
        if (clean.contains("دقيقة") || clean.contains("دقائق")) {
            return "${digits}m"
        }
        if (Regex("""^(\d+[wdhms])+$""").matches(clean) || Regex("""^\d{1,2}:\d{2}(:\d{2})?$""").matches(clean)) {
            return clean
        }
        val match = Regex("""(\d+)\s*([wdhms])""").find(clean)
        if (match != null) {
            return "${match.groupValues[1]}${match.groupValues[2]}"
        }
        return null
    }

    suspend fun addProfile(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val cleanName = profile.name.trim()
                if (cleanName.isBlank()) return@withContext Result.failure(Exception("اسم الباقة فارغ"))

                val words = mutableListOf("/ip/hotspot/user/profile/add", "=name=$cleanName")
                val cleanRateLimit = profile.rateLimit.trim()
                if (cleanRateLimit.isNotBlank() && cleanRateLimit != "غير محدد" && cleanRateLimit != "مفتوح") {
                    words.add("=rate-limit=$cleanRateLimit")
                }
                words.add("=shared-users=${if (profile.sharedUsers > 0) profile.sharedUsers else 1}")
                val cleanTimeout = profile.sessionTimeout.trim()
                if (cleanTimeout.isNotBlank() && cleanTimeout != "غير محدد") {
                    val formattedTimeout = formatRouterOsTime(cleanTimeout)
                    if (!formattedTimeout.isNullOrBlank()) {
                        words.add("=session-timeout=$formattedTimeout")
                    }
                }
                if (profile.price.isNotBlank() && profile.price != "0" && profile.price != "مجاني") {
                    words.add("=comment=price=${profile.price}")
                } else if (profile.comment.isNotBlank()) {
                    words.add("=comment=${profile.comment}")
                }

                val sentences = executeCommandSentence(words)
                val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trapSentence != null) {
                    val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "رفض راوتر ميكروتك إنشاء الباقة"
                    if (trapMsg.contains("already exists", ignoreCase = true) ||
                        trapMsg.contains("already have", ignoreCase = true) ||
                        trapMsg.contains("already", ignoreCase = true)) {
                        return@withContext Result.success(true)
                    }
                    return@withContext Result.failure(Exception(trapMsg))
                }
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                if (!isDone) {
                    return@withContext Result.failure(Exception("لم يؤكد الراوتر إنشاء الباقة"))
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteProfile(profileId: String, profileName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val targetName = profileName.ifBlank { profileId }
                var nonMissingError: String? = null

                fun tryRemove(words: List<String>): Boolean {
                    val s = executeCommandSentence(words)
                    val trap = s.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trap == null && s.any { it.firstOrNull() == "!done" }) return true
                    val msg = trap?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=").orEmpty()
                    if (msg.isNotBlank() &&
                        !msg.contains("no such item", true) &&
                        !msg.contains("not found", true) &&
                        !msg.contains("no such command", true) &&
                        !msg.contains("unknown command", true) &&
                        !msg.contains("nothing to do", true)
                    ) {
                        nonMissingError = msg
                    }
                    return false
                }

                // 1. Try Hotspot profile removal
                if (profileId.startsWith("*") && tryRemove(listOf("/ip/hotspot/user/profile/remove", "=.id=$profileId"))) {
                    return@withContext Result.success(true)
                }
                if (targetName.isNotBlank() && tryRemove(listOf("/ip/hotspot/user/profile/remove", "=numbers=$targetName"))) {
                    return@withContext Result.success(true)
                }

                // 2. Try User Manager v7 profile removal
                if (profileId.startsWith("*") && tryRemove(listOf("/user-manager/profile/remove", "=.id=$profileId"))) {
                    return@withContext Result.success(true)
                }
                if (targetName.isNotBlank() && tryRemove(listOf("/user-manager/profile/remove", "=numbers=$targetName"))) {
                    return@withContext Result.success(true)
                }

                // 3. Try User Manager v6 profile removal
                if (targetName.isNotBlank() && tryRemove(listOf("/tool/user-manager/profile/remove", "=numbers=$targetName"))) {
                    return@withContext Result.success(true)
                }

                if (nonMissingError != null) {
                    return@withContext Result.failure(Exception(nonMissingError!!))
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun rebootRouter(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                executeCommandSentence(listOf("/system/reboot"))
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSystemIdentity(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/system/identity/print"))
                val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                val name = if (re != null) parseSentenceToMap(re)["name"].orEmpty() else ""
                Result.success(name)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getNetworkInterfaces(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/interface/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWirelessInterfaces(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/interface/wireless/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getVlanInterfaces(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/interface/vlan/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getInterfaceStats(): Result<List<InterfaceStats>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                var sentences = executeSentence(listOf("/interface/print", "=.proplist=.id,name,type,mac-address,rx-byte,tx-byte,rx-packet,tx-packet,running,disabled,comment"))
                if (sentences.none { it.firstOrNull() == "!re" }) {
                    sentences = executeSentence(listOf("/interface/print"))
                }
                extractTrapMessageIfNoRe(sentences)?.let { trapMsg ->
                    return@withContext Result.failure(Exception("تعذر جلب إحصائيات المنافذ: $trapMsg"))
                }
                val list = mutableListOf<InterfaceStats>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val name = map["name"] ?: "unknown"
                        val rxBytes = map["rx-byte"]?.toLongOrNull() ?: 0L
                        val txBytes = map["tx-byte"]?.toLongOrNull() ?: 0L
                        val rxPackets = map["rx-packet"]?.toLongOrNull() ?: 0L
                        val txPackets = map["tx-packet"]?.toLongOrNull() ?: 0L
                        val isRunning = map["running"] == "true" || map["running"] == "yes"
                        val isDisabled = map["disabled"] == "true" || map["disabled"] == "yes"
                        val comment = map["comment"] ?: ""

                        list.add(
                            InterfaceStats(
                                id = map[".id"] ?: "if_${list.size}",
                                name = name,
                                type = map["type"] ?: "ether",
                                macAddress = map["mac-address"] ?: "",
                                rxBytes = rxBytes,
                                txBytes = txBytes,
                                rxPackets = rxPackets,
                                txPackets = txPackets,
                                isRunning = isRunning,
                                disabled = isDisabled,
                                comment = comment
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRouterBoardInfo(): Result<RouterBoardInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/system/routerboard/print"))
                val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                if (re != null) {
                    val map = parseSentenceToMap(re)
                    val info = RouterBoardInfo(
                        model = map["model"] ?: "MikroTik Router",
                        serialNumber = map["serial-number"] ?: "",
                        currentFirmware = map["current-firmware"] ?: "",
                        upgradeFirmware = map["upgrade-firmware"] ?: "",
                        routerboard = map["routerboard"] == "true" || map["routerboard"] == "yes"
                    )
                    Result.success(info)
                } else {
                    Result.failure(Exception("لا تتوفر بيانات RouterBOARD"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSystemHealth(): Result<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val sentences = executeSentence(listOf("/system/health/print"))
                val map = mutableMapOf<String, String>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val itemMap = parseSentenceToMap(s)
                        val name = itemMap["name"]?.lowercase()
                        val value = itemMap["value"]
                        if (name != null && value != null) {
                            map[name] = value
                        }
                        itemMap.forEach { (k, v) ->
                            map[k.lowercase()] = v
                        }
                    }
                }
                Result.success(map)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSystemLogs(limit: Int = 30): Result<List<SystemLogEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeQueryWithProplist(
                    command = "/log/print",
                    proplist = ".id,time,topics,message"
                )
                val list = mutableListOf<SystemLogEntry>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        list.add(
                            SystemLogEntry(
                                id = map[".id"] ?: "l_${list.size}",
                                time = map["time"] ?: "",
                                topics = map["topics"] ?: "info",
                                message = map["message"] ?: ""
                            )
                        )
                    }
                }
                Result.success(list.takeLast(limit).reversed())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getHotspotServers(): Result<List<HotspotServerInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/hotspot/print"))
                val list = mutableListOf<HotspotServerInfo>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        list.add(
                            HotspotServerInfo(
                                id = map[".id"] ?: "hs_${list.size}",
                                name = map["name"] ?: "hotspot1",
                                interfaceName = map["interface"] ?: "all",
                                profile = map["profile"] ?: "default",
                                addressPool = map["address-pool"] ?: "",
                                addressesPerMac = map["addresses-per-mac"]?.toIntOrNull() ?: 1,
                                disabled = map["disabled"] == "true" || map["disabled"] == "yes"
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getIpPools(): Result<List<IpPoolInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/pool/print"))
                val list = mutableListOf<IpPoolInfo>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        list.add(
                            IpPoolInfo(
                                id = map[".id"] ?: "pool_${list.size}",
                                name = map["name"] ?: "pool",
                                ranges = map["ranges"] ?: ""
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserManagerUsers(): Result<List<HotspotCard>> {
        return withContext(Dispatchers.IO) {
            try {
                val umProplist = ".id,name,username,password,actual-profile,profile,uptime,download-used,upload-used,download-limit,total-limit,transfer-limit,time-limit,creation-time,start-time,started,last-seen,last-logged-in,till-time,end-time,comment,disabled"
                // Try v7 user-manager first
                val v7Sentences = executeQueryWithProplist("/user-manager/user/print", umProplist)
                val isV7Um = v7Sentences.any { it.firstOrNull() == "!re" }
                val v7Trap = extractTrapMessageIfNoRe(v7Sentences)
                val sentences = if (isV7Um) {
                    v7Sentences
                } else {
                    // Try v6 /tool/user-manager/user
                    val v6Sentences = executeQueryWithProplist("/tool/user-manager/user/print", umProplist)
                    val v6Trap = extractTrapMessageIfNoRe(v6Sentences)
                    if (v6Sentences.none { it.firstOrNull() == "!re" } && v7Trap != null && v6Trap != null) {
                        return@withContext Result.failure(Exception("تعذر جلب كروت يوزرمانجر: $v7Trap"))
                    }
                    v6Sentences
                }

                // In RouterOS v7, active/assigned profiles & subscription timestamps often live in /user-manager/user-profile
                val userProfileMap = mutableMapOf<String, Pair<String, String>>()
                try {
                    val upSentences = executeQueryWithProplist(
                        command = if (isV7Um) "/user-manager/user-profile/print" else "/tool/user-manager/user-profile/print",
                        proplist = "user,username,profile,end-time,till-time,state"
                    )
                    for (ups in upSentences) {
                        if (ups.firstOrNull() == "!re") {
                            val upMap = parseSentenceToMap(ups)
                            val uName = (upMap["user"] ?: upMap["username"] ?: "").trim()
                            val pName = (upMap["profile"] ?: "").trim()
                            val endRaw = (upMap["end-time"] ?: upMap["till-time"] ?: "").trim()
                            val state = (upMap["state"] ?: "").lowercase()
                            if (uName.isNotBlank() && pName.isNotBlank()) {
                                val existing = userProfileMap[uName.lowercase()]
                                if (existing == null || state == "running-active" || state == "running") {
                                    userProfileMap[uName.lowercase()] = pName to endRaw
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                val list = mutableListOf<HotspotCard>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val name = map["name"] ?: map["username"] ?: ""
                        if (name.isNotBlank()) {
                            val comment = map["comment"] ?: ""
                            val upEntry = userProfileMap[name.trim().lowercase()]
                            val profile = map["actual-profile"]?.takeIf { it.isNotBlank() }
                                ?: map["profile"]?.takeIf { it.isNotBlank() }
                                ?: upEntry?.first?.takeIf { it.isNotBlank() }
                                ?: "UserManager"
                            val price = extractPriceFromText(comment, profile, name)

                            // MikroTik User Manager v6/v7:
                            // download-used = Router to Client (Download) -> bytesOut
                            // upload-used = Client to Router (Upload) -> bytesIn
                            val downUsed = map["download-used"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            val upUsed = map["upload-used"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L

                            val downLimit = map["download-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            val totalLimit = map["total-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            val transLimit = map["transfer-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L

                            val bestLimit = if (totalLimit > 0L) totalLimit else if (transLimit > 0L) transLimit else downLimit
                            val dateHint = listOfNotNull(
                                map["creation-time"],
                                map["start-time"],
                                map["started"],
                                comment.takeIf { it.isNotBlank() },
                                map["last-seen"],
                                map["last-logged-in"],
                                map["till-time"],
                                map["end-time"],
                                upEntry?.second?.takeIf { it.isNotBlank() }
                            ).firstNotNullOfOrNull { raw ->
                                com.example.data.network.NetworkUtils.parseRouterDateToEpoch(raw)
                            } ?: 0L

                            list.add(
                                HotspotCard(
                                    id = map[".id"] ?: "um_${list.size}",
                                    name = name,
                                    password = map["password"] ?: "",
                                    profile = profile,
                                    uptime = map["uptime"] ?: "0s",
                                    bytesIn = upUsed,
                                    bytesOut = downUsed,
                                    limitUptime = map["time-limit"] ?: "",
                                    limitBytesTotal = bestLimit,
                                    comment = comment,
                                    disabled = map["disabled"] == "true" || map["disabled"] == "yes",
                                    price = price,
                                    cardType = "usermanager",
                                    createdAtHint = dateHint
                                )
                            )
                        }
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserManagerSessions(): Result<List<com.example.data.local.SessionArchiveEntity>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر"))
                }
                val sessProplist = ".id,user,username,from-time,started,start-time,till-time,ended,end-time,active,download,download-used,bytes-out,upload,upload-used,bytes-in,uptime,terminate-cause,status,profile,actual-profile,nas-ip-address,user-ip,ip-address,calling-station-id,mac-address"
                var sentences = executeQueryWithProplist("/user-manager/session/print", sessProplist)
                if (sentences.none { it.firstOrNull() == "!re" }) {
                    sentences = executeQueryWithProplist("/tool/user-manager/session/print", sessProplist)
                }
                val list = mutableListOf<com.example.data.local.SessionArchiveEntity>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val username = (map["user"] ?: map["username"] ?: "").trim()
                        if (username.isBlank()) continue
                        val fromRaw = map["from-time"] ?: map["started"] ?: map["start-time"] ?: ""
                        val tillRaw = map["till-time"] ?: map["ended"] ?: map["end-time"] ?: ""
                        val isActive = map["active"] == "true" || map["active"] == "yes"
                        val parsedTs = com.example.data.network.NetworkUtils.parseRouterDateToEpoch(fromRaw)
                            ?: com.example.data.network.NetworkUtils.parseRouterDateToEpoch(tillRaw)
                            ?: if (isActive) System.currentTimeMillis() else 0L
                        if (parsedTs <= 0L) continue

                        val downUsed = com.example.data.network.NetworkUtils.parseSizeToBytes(
                            map["download"] ?: map["download-used"] ?: map["bytes-out"] ?: "0"
                        )
                        val upUsed = com.example.data.network.NetworkUtils.parseSizeToBytes(
                            map["upload"] ?: map["upload-used"] ?: map["bytes-in"] ?: "0"
                        )
                        val rawId = map[".id"] ?: "${fromRaw}_${tillRaw}"
                        val sessionId = "um_sess_${username}_${rawId}"
                        list.add(
                            com.example.data.local.SessionArchiveEntity(
                                sessionId = sessionId,
                                username = username,
                                ipAddress = map["user-ip"] ?: map["ip-address"] ?: "",
                                macAddress = (map["calling-station-id"] ?: map["mac-address"] ?: "").uppercase(),
                                isOnline = isActive,
                                loginTime = if (fromRaw.isNotBlank()) {
                                    com.example.data.network.NetworkUtils.formatArabicFullDateTimeFromString(fromRaw, parsedTs)
                                } else {
                                    com.example.data.network.NetworkUtils.formatArabicFullDateTime(parsedTs)
                                },
                                logoutTime = when {
                                    isActive -> "متصل حالياً"
                                    tillRaw.isNotBlank() -> com.example.data.network.NetworkUtils.formatArabicFullDateTimeFromString(tillRaw, parsedTs)
                                    else -> "جلسة مكتملة"
                                },
                                duration = map["uptime"] ?: "0s",
                                bytesIn = upUsed,
                                bytesOut = downUsed,
                                totalBytes = upUsed + downUsed,
                                terminateReason = map["terminate-cause"] ?: map["status"] ?: if (isActive) "جلسة نشطة" else "جلسة يوزرمانجر",
                                profile = map["profile"] ?: map["actual-profile"] ?: "",
                                nodeName = map["nas-ip-address"] ?: "UserManager",
                                timestamp = parsedTs
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDiscoveredNeighbors(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/neighbor/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRouterAddresses(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/address/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRomonNeighbors(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/tool/romon/neighbor/print"))
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                if (list.isEmpty()) {
                    // Try alternative path for RouterOS v7 romon
                    val altSentences = executeSentence(listOf("/romon/neighbor/print"))
                    for (s in altSentences) {
                        if (s.firstOrNull() == "!re") {
                            list.add(parseSentenceToMap(s))
                        }
                    }
                }
                Result.success(list)
            } catch (_: Exception) {
                Result.success(emptyList())
            }
        }
    }

    private fun parseSentenceToMap(sentence: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (word in sentence) {
            if (word.startsWith("=")) {
                val clean = word.substring(1)
                val idx = clean.indexOf('=')
                if (idx != -1) {
                    val key = clean.substring(0, idx)
                    val value = clean.substring(idx + 1)
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun authenticateSocket(
        sock: Socket,
        inS: InputStream,
        outS: OutputStream,
        cleanUser: String,
        password: String
    ): Boolean {
        return performLoginHandshake(sock, inS, outS, cleanUser, password).first
    }

    private fun reconnectInternalLocked(): Boolean {
        if (isExplicitlyClosed) return false
        closeSocketOnlyLocked()
        val cleanHost = lastHost.trim()
        val cleanUser = lastUsername.trim()
        if (cleanHost.isBlank() || cleanUser.isBlank()) return false

        val isLocal = RouterConnectionParser.isPrivateOrLocalIpv4(cleanHost)
        val reconnectConnectMs = lastConnectTimeoutMs.coerceIn(800, if (isLocal) 2200 else 3200)
        val reconnectHandshakeMs = (reconnectConnectMs + 900).coerceIn(1500, 4500)

        return try {
            val sock = createRouterSocket(
                host = cleanHost,
                port = lastPort,
                connectTimeoutMs = reconnectConnectMs,
                handshakeTimeoutMs = reconnectHandshakeMs,
                useTls = lastUseTls
            )
            val inS = sock.getInputStream()
            val outS = sock.getOutputStream()
            socket = sock
            inStream = inS
            outStream = outS
            if (authenticateSocket(sock, inS, outS, cleanUser, lastPassword)) {
                true
            } else {
                closeSocketOnlyLocked()
                false
            }
        } catch (_: Exception) {
            closeSocketOnlyLocked()
            false
        }
    }

    private fun reconnectCmdInternalLocked(): Boolean {
        if (isExplicitlyClosed) return false
        closeCmdSocketOnlyLocked()
        val cleanHost = lastHost.trim()
        val cleanUser = lastUsername.trim()
        if (cleanHost.isBlank() || cleanUser.isBlank()) return false

        val isLocal = RouterConnectionParser.isPrivateOrLocalIpv4(cleanHost)
        val reconnectConnectMs = lastConnectTimeoutMs.coerceIn(800, if (isLocal) 2200 else 3200)
        val reconnectHandshakeMs = (reconnectConnectMs + 900).coerceIn(1500, 4500)

        return try {
            val sock = createRouterSocket(
                host = cleanHost,
                port = lastPort,
                connectTimeoutMs = reconnectConnectMs,
                handshakeTimeoutMs = reconnectHandshakeMs,
                useTls = lastUseTls
            )
            val inS = sock.getInputStream()
            val outS = sock.getOutputStream()
            cmdSocket = sock
            cmdInStream = inS
            cmdOutStream = outS
            if (authenticateSocket(sock, inS, outS, cleanUser, lastPassword)) {
                true
            } else {
                closeCmdSocketOnlyLocked()
                false
            }
        } catch (_: Exception) {
            closeCmdSocketOnlyLocked()
            false
        }
    }

    private fun executeQueryWithProplist(
        command: String,
        proplist: String,
        extraArgs: List<String> = emptyList()
    ): List<List<String>> {
        val withProps = mutableListOf(command, "=.proplist=$proplist")
        withProps.addAll(extraArgs)
        val sentences = executeSentence(withProps)
        val hasTrap = sentences.any { it.firstOrNull() == "!trap" }
        val hasRe = sentences.any { it.firstOrNull() == "!re" }
        if (hasRe && !hasTrap) return sentences
        if (!hasTrap && sentences.any { it.firstOrNull() == "!done" }) return sentences
        val fallback = mutableListOf(command)
        fallback.addAll(extraArgs)
        return executeSentence(fallback)
    }

    fun executeCommandSentence(words: List<String>): List<List<String>> = synchronized(cmdSocketLock) {
        if (isExplicitlyClosed) {
            throw IllegalStateException("تم إغلاق الاتصال بالراوتر")
        }
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val s = cmdSocket
                if (s == null || s.isClosed || !s.isConnected || s.isInputShutdown || s.isOutputShutdown) {
                    if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
                        val reconnected = reconnectCmdInternalLocked()
                        if (!reconnected) {
                            throw IllegalStateException("تعذر فتح قناة الأوامر مع راوتر ميكروتك عبر منفذ API ($lastPort)")
                        }
                    } else {
                        throw IllegalStateException("لا توجد جلسة اتصال نشطة بالراوتر")
                    }
                }
                val out = cmdOutStream ?: throw IllegalStateException("Command socket output stream is null")
                val inS = cmdInStream ?: throw IllegalStateException("Command socket input stream is null")
                val sentences = performSentenceExchange(out, inS, words)
                val hasFatal = sentences.any { it.firstOrNull() == "!fatal" }
                if (hasFatal) {
                    closeCmdSocketOnlyLocked()
                    if (attempts < 2 && !isExplicitlyClosed && lastHost.isNotBlank()) continue
                    throw java.io.IOException("أغلق راوتر ميكروتك جلسة API (!fatal)")
                }
                val hasDone = sentences.any { it.firstOrNull() == "!done" }
                if (sentences.isEmpty() || !hasDone) {
                    closeCmdSocketOnlyLocked()
                    if (attempts < 2 && !isExplicitlyClosed && lastHost.isNotBlank()) continue
                    throw java.io.EOFException("انقطعت استجابة راوتر ميكروتك قبل اكتمالها")
                }
                return@synchronized sentences
            } catch (e: Exception) {
                closeCmdSocketOnlyLocked()
                val shouldRetry = attempts < 2 &&
                    !isExplicitlyClosed &&
                    lastHost.isNotBlank() &&
                    (e is java.io.EOFException ||
                        (e is java.net.SocketException && e !is java.net.ConnectException) ||
                        (e is java.io.IOException &&
                            e !is java.net.SocketTimeoutException &&
                            e !is java.net.ConnectException &&
                            e !is java.net.UnknownHostException &&
                            e !is java.net.NoRouteToHostException &&
                            e !is javax.net.ssl.SSLException))
                if (!shouldRetry) throw e
            }
        }
        return@synchronized emptyList()
    }

    fun executeSentence(words: List<String>): List<List<String>> = synchronized(socketLock) {
        if (isExplicitlyClosed) {
            throw IllegalStateException("تم إغلاق الاتصال بالراوتر")
        }
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                val s = socket
                if (s == null || s.isClosed || !s.isConnected || s.isInputShutdown || s.isOutputShutdown) {
                    if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
                        val reconnected = reconnectInternalLocked()
                        if (!reconnected) {
                            throw IllegalStateException("تعذر إعادة الاتصال براوتر ميكروتك عبر منفذ API ($lastPort)")
                        }
                    } else {
                        throw IllegalStateException("لا توجد جلسة اتصال نشطة بالراوتر")
                    }
                }
                val out = outStream ?: throw IllegalStateException("Socket output stream is null")
                val inS = inStream ?: throw IllegalStateException("Socket input stream is null")

                val sentences = performSentenceExchange(out, inS, words)
                val hasFatal = sentences.any { it.firstOrNull() == "!fatal" }
                if (hasFatal) {
                    closeSocketOnlyLocked()
                    if (attempts < 2 && !isExplicitlyClosed && lastHost.isNotBlank()) {
                        continue
                    }
                    throw java.io.IOException("أغلق راوتر ميكروتك جلسة API (!fatal)")
                }
                val hasDone = sentences.any { it.firstOrNull() == "!done" }
                if (sentences.isEmpty() || !hasDone) {
                    closeSocketOnlyLocked()
                    if (attempts < 2 && !isExplicitlyClosed && lastHost.isNotBlank()) {
                        continue
                    }
                    throw java.io.EOFException("انقطعت استجابة راوتر ميكروتك قبل اكتمالها")
                }
                return@synchronized sentences
            } catch (e: Exception) {
                closeSocketOnlyLocked()
                val shouldRetry = attempts < 2 &&
                    !isExplicitlyClosed &&
                    lastHost.isNotBlank() &&
                    (e is java.io.EOFException ||
                        (e is java.net.SocketException && e !is java.net.ConnectException) ||
                        (e is java.io.IOException &&
                            e !is java.net.SocketTimeoutException &&
                            e !is java.net.ConnectException &&
                            e !is java.net.UnknownHostException &&
                            e !is java.net.NoRouteToHostException &&
                            e !is javax.net.ssl.SSLException))
                if (!shouldRetry) {
                    throw e
                }
            }
        }
        return@synchronized emptyList()
    }

    private fun performSentenceExchange(out: OutputStream, inS: InputStream, words: List<String>): List<List<String>> {
        for (word in words) {
            writeWord(out, word)
        }
        out.write(0x00) // End of sentence
        out.flush()

        val sentences = mutableListOf<List<String>>()
        while (true) {
            val sentence = mutableListOf<String>()
            while (true) {
                val word = readWord(inS)
                if (word.isEmpty()) break
                sentence.add(word)
            }
            if (sentence.isEmpty()) break
            sentences.add(sentence)
            val first = sentence.firstOrNull() ?: ""
            if (first == "!done" || first == "!fatal") break
        }
        return sentences
    }

    private fun extractTrapMessageIfNoRe(sentences: List<List<String>>): String? {
        if (sentences.any { it.firstOrNull() == "!re" }) return null
        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" } ?: return null
        return trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")?.ifBlank { null }
            ?: "رفض الراوتر الاستعلام (!trap)"
    }

    private fun writeSentenceToStream(out: OutputStream, words: List<String>) {
        for (word in words) {
            writeWord(out, word)
        }
        out.write(0x00)
        out.flush()
    }

    private fun readSingleSentenceFromStream(inS: InputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val word = readWord(inS)
            if (word.isEmpty()) break
            words.add(word)
        }
        return words
    }

    // Must ONLY be called while holding socketLock
    private fun writeSentenceLocked(words: List<String>) {
        val out = outStream ?: throw IllegalStateException("Socket is not connected")
        writeSentenceToStream(out, words)
    }

    // Must ONLY be called while holding socketLock
    private fun readSingleSentenceLocked(): List<String> {
        val inS = inStream ?: throw IllegalStateException("Socket is not connected")
        return readSingleSentenceFromStream(inS)
    }

    private fun writeWord(out: OutputStream, word: String) {
        val bytes = word.toByteArray(Charsets.UTF_8)
        val len = bytes.size
        when {
            len < 0x80 -> out.write(len)
            len < 0x4000 -> {
                out.write((len shr 8) or 0x80)
                out.write(len and 0xFF)
            }
            len < 0x200000 -> {
                out.write((len shr 16) or 0xC0)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write((len shr 24) or 0xE0)
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(bytes)
    }

    private fun readWord(inStream: InputStream): String {
        val b1 = inStream.read()
        if (b1 == -1) throw java.io.EOFException("تم إغلاق الاتصال من راوتر ميكروتك")
        val len = when {
            (b1 and 0x80) == 0 -> b1
            (b1 and 0xC0) == 0x80 -> {
                val b2 = inStream.read()
                if (b2 == -1) throw java.io.EOFException("انقطع تدفق البيانات من راوتر ميكروتك أثناء قراءة طول الحزمة")
                ((b1 and 0x3F) shl 8) or b2
            }
            (b1 and 0xE0) == 0xC0 -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                if (b2 == -1 || b3 == -1) throw java.io.EOFException("انقطع تدفق البيانات من راوتر ميكروتك أثناء قراءة طول الحزمة")
                ((b1 and 0x1F) shl 16) or (b2 shl 8) or b3
            }
            else -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                val b4 = inStream.read()
                if (b2 == -1 || b3 == -1 || b4 == -1) throw java.io.EOFException("انقطع تدفق البيانات من راوتر ميكروتك أثناء قراءة طول الحزمة")
                ((b1 and 0x0F) shl 24) or (b2 shl 16) or (b3 shl 8) or b4
            }
        }
        if (len == 0) return ""
        if (len < 0 || len > MAX_WORD_LENGTH_BYTES) {
            throw java.io.IOException("استجابة غير متوافقة مع بروتوكول RouterOS API ($len بايت)")
        }
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val count = inStream.read(buf, read, len - read)
            if (count == -1) throw java.io.EOFException("تم إغلاق الاتصال قبل اكتمال قراءة الكلمة من راوتر ميكروتك")
            read += count
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    suspend fun getRouterUsers(): Result<List<AdminUser>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/user/print"))
                val list = mutableListOf<AdminUser>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val username = map["name"] ?: "admin"
                        val group = map["group"] ?: "full"
                        val roleName = when (group.lowercase()) {
                            "full" -> "مدير عام (Full Admin)"
                            "write" -> "مشرف كروت"
                            "read" -> "مراقب"
                            else -> group
                        }
                        val permissions = if (group.lowercase() == "full") {
                            listOf("إدارة كاملة", "توليد كروت", "تعديل الأسعار", "إدارة السيرفرات", "تعديل الصلاحيات")
                        } else {
                            listOf("عرض الجلسات", "مراقبة الاستهلاك")
                        }
                        list.add(
                            AdminUser(
                                id = map[".id"] ?: "u_${list.size}",
                                username = username,
                                fullName = map["comment"]?.ifBlank { username } ?: username,
                                role = roleName,
                                permissions = permissions,
                                lastActiveTime = map["last-logged-in"] ?: "نشط",
                                ipAddress = map["address"].orEmpty(),
                                isActive = true,
                                password = ""
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addRouterUser(username: String, password: String, group: String, comment: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val rosGroup = when {
                    group.contains("عام") || group.contains("Full") -> "full"
                    group.contains("كروت") -> "write"
                    else -> "read"
                }
                val words = mutableListOf("/user/add", "=name=$username", "=password=$password", "=group=$rosGroup")
                if (comment.isNotBlank()) {
                    words.add("=comment=$comment")
                }
                val sentences = executeCommandSentence(words)
                val hasTrap = sentences.any { it.firstOrNull() == "!trap" }
                if (hasTrap) {
                    val trapMap = parseSentenceToMap(sentences.first { it.firstOrNull() == "!trap" })
                    Result.failure(Exception(trapMap["message"] ?: "فشل إضافة المستخدم في الراوتر"))
                } else {
                    Result.success(true)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun pingFromRouter(targetIp: String, count: Int = 2): Result<Pair<Boolean, Long>> {
        return withContext(Dispatchers.IO) {
            val cleanIp = targetIp.trim()
            if (cleanIp.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("عنوان IP فارغ"))
            }
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val safeCount = count.coerceIn(1, 5)
                val sentences = executeCommandSentence(
                    listOf("/ping", "=address=$cleanIp", "=count=$safeCount")
                )
                val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trap != null) {
                    val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل فحص ping من الراوتر"
                    return@withContext Result.failure(Exception(msg))
                }
                var receivedReplies = 0
                val rtts = mutableListOf<Long>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val recCount = map["received"]?.toIntOrNull() ?: 0
                        val timeRaw = map["time"].orEmpty().trim()
                        val avgRaw = map["avg-rtt"].orEmpty().trim()
                        val statusRaw = map["status"].orEmpty().lowercase()
                        if (recCount > 0 || (timeRaw.isNotBlank() && !statusRaw.contains("timeout"))) {
                            receivedReplies += maxOf(1, recCount)
                            parseRouterPingMs(timeRaw.ifBlank { avgRaw })?.let { rtts.add(it) }
                        } else if (avgRaw.isNotBlank() && recCount > 0) {
                            parseRouterPingMs(avgRaw)?.let { rtts.add(it) }
                        }
                    }
                }
                val avgRtt = if (rtts.isNotEmpty()) rtts.average().toLong().coerceAtLeast(1L) else 0L
                Result.success((receivedReplies > 0) to avgRtt)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseRouterPingMs(raw: String): Long? {
        val clean = raw.trim().lowercase()
        if (clean.isBlank()) return null
        Regex("""^(\d+)(?:\.\d+)?ms$""").find(clean)?.let {
            return it.groupValues[1].toLongOrNull()?.coerceAtLeast(1L)
        }
        Regex("""^(\d+)us$""").find(clean)?.let {
            val us = it.groupValues[1].toLongOrNull() ?: return null
            return maxOf(1L, us / 1000L)
        }
        Regex("""^(\d+)s$""").find(clean)?.let {
            val sec = it.groupValues[1].toLongOrNull() ?: return null
            return sec * 1000L
        }
        return clean.filter { it.isDigit() }.toLongOrNull()
    }

    private fun closeSocketOnlyLocked() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inStream = null
        outStream = null
    }

    private fun closeCmdSocketOnlyLocked() {
        try {
            cmdSocket?.close()
        } catch (_: Exception) {}
        cmdSocket = null
        cmdInStream = null
        cmdOutStream = null
    }

    fun close() {
        synchronized(socketLock) {
            closeSocketOnlyLocked()
        }
        synchronized(cmdSocketLock) {
            closeCmdSocketOnlyLocked()
        }
    }

    fun disconnect() {
        synchronized(socketLock) {
            isExplicitlyClosed = true
            lastHost = ""
            lastUsername = ""
            lastPassword = ""
            synchronized(cachedProfiles) { cachedProfiles.clear() }
            closeSocketOnlyLocked()
        }
        synchronized(cmdSocketLock) {
            closeCmdSocketOnlyLocked()
        }
    }

    suspend fun getDnsCache(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeQueryWithProplist(
                    command = "/ip/dns/cache/print",
                    proplist = "name,address,data,type,ttl"
                )
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getFirewallConnections(srcAddress: String? = null): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val extra = if (!srcAddress.isNullOrBlank()) listOf("?src-address=${srcAddress.trim()}") else emptyList()
                val sentences = executeQueryWithProplist(
                    command = "/ip/firewall/connection/print",
                    proplist = "protocol,src-address,dst-address,dst-port,connection-type",
                    extraArgs = extra
                )
                val list = mutableListOf<Map<String, String>>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        list.add(parseSentenceToMap(s))
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
