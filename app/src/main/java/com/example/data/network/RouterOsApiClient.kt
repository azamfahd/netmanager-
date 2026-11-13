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
import java.security.MessageDigest
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

    private val socketLock = Any()
    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    @Volatile
    private var lastHost: String = ""
    @Volatile
    private var lastPort: Int = 8728
    @Volatile
    private var lastUsername: String = ""
    @Volatile
    private var lastPassword: String = ""
    @Volatile
    private var isExplicitlyClosed: Boolean = false

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

    suspend fun connectAndLogin(
        host: String,
        port: Int = 8728,
        username: String,
        password: String,
        timeoutMs: Int = 6000
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            synchronized(socketLock) {
                try {
                    closeSocketOnlyLocked()
                    isExplicitlyClosed = false
                    val cleanHost = host.trim()
                    val cleanUser = username.trim()
                    lastHost = cleanHost
                    lastPort = port
                    lastUsername = cleanUser
                    lastPassword = password

                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.keepAlive = true
                    sock.connect(InetSocketAddress(cleanHost, port), timeoutMs.coerceAtLeast(8000))
                    sock.soTimeout = 25000 // 25 seconds adequate for heavy UserManager database transactions
                    socket = sock
                    inStream = sock.getInputStream()
                    outStream = sock.getOutputStream()

                    // Attempt RouterOS v6.43+ and v7 login
                    writeSentenceLocked(listOf("/login", "=name=$cleanUser", "=password=$password"))
                    var response = readSingleSentenceLocked()

                    if (response.isEmpty()) {
                        closeSocketOnlyLocked()
                        return@synchronized Result.failure(Exception("لم يستجب الراوتر على منفذ API ($port)"))
                    }

                    val replyType = response.firstOrNull() ?: ""

                    // Success
                    if (replyType == "!done" && response.none { it.startsWith("=ret=") }) {
                        return@synchronized Result.success(true)
                    }

                    // Old RouterOS pre-6.43 challenge-response
                    val challengeParam = response.firstOrNull { it.startsWith("=ret=") }
                    if (replyType == "!done" && challengeParam != null) {
                        val challengeHex = challengeParam.removePrefix("=ret=")
                        val challengeBytes = hexStringToByteArray(challengeHex)
                        val md5 = MessageDigest.getInstance("MD5")
                        md5.update(0.toByte())
                        md5.update(password.toByteArray(Charsets.UTF_8))
                        md5.update(challengeBytes)
                        val digestHex = byteArrayToHexString(md5.digest())
                        val responseHash = "00$digestHex"

                        writeSentenceLocked(listOf("/login", "=name=$cleanUser", "=response=$responseHash"))
                        response = readSingleSentenceLocked()
                        if (response.firstOrNull() == "!done") {
                            return@synchronized Result.success(true)
                        }
                    }

                    // Authentication Failure / Trap
                    val trapMsg = response.firstOrNull { it.startsWith("=message=") }
                        ?.removePrefix("=message=")

                    val userFriendlyError = if (trapMsg != null && (trapMsg.contains("cannot log in", ignoreCase = true) || trapMsg.contains("invalid", ignoreCase = true) || trapMsg.contains("password", ignoreCase = true))) {
                        "خطأ في بيانات الدخول: اسم المستخدم أو كلمة المرور غير صحيحة. يرجى التأكد من كتابة اسم المستخدم (مثل admin) وكلمة المرور الخاصة بحساب إدارة راوتر ميكروتك بدقة."
                    } else if (trapMsg != null) {
                        "فشلت المصادقة مع الراوتر: $trapMsg"
                    } else {
                        "خطأ في بيانات الدخول: اسم المستخدم أو كلمة المرور غير صحيحة."
                    }

                    closeSocketOnlyLocked()
                    Result.failure(Exception(userFriendlyError))
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
                    val cpu = map["cpu-load"]?.toIntOrNull() ?: 15
                    val freeMem = map["free-memory"]?.toLongOrNull() ?: (500 * 1024 * 1024L)
                    val totalMem = map["total-memory"]?.toLongOrNull() ?: (1024 * 1024 * 1024L)
                    val uptime = map["uptime"] ?: "1d 2h"
                    val version = map["version"] ?: "RouterOS"
                    val board = map["board-name"] ?: "MikroTik"
                    val arch = map["architecture-name"] ?: "mipsbe"

                    val res = RouterResource(
                        uptime = uptime,
                        version = version,
                        boardName = board,
                        cpuLoad = cpu,
                        freeMemoryBytes = freeMem,
                        totalMemoryBytes = totalMem,
                        architecture = arch,
                        rxSpeedKbps = 0.0,
                        txSpeedKbps = 0.0
                    )
                    Result.success(res)
                } else {
                    Result.success(RouterResource())
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
                val sentences = executeSentence(listOf("/ip/hotspot/active/print"))
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
                                comment = map["comment"] ?: ""
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
                val sentences = executeSentence(listOf("/ip/hotspot/user/print"))
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
                                cardType = "hotspot"
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
                    if (sentences.any { it.firstOrNull() == "!done" }) anySystemSucceeded = true
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
                    if (umSentences.any { it.firstOrNull() == "!done" }) anySystemSucceeded = true
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
                                val totalLimit = lData["transfer-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                    ?: lData["total-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                                    ?: (if (downLimit > 0L || upLimit > 0L) downLimit + upLimit else 0L)

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
                    if (tumSentences.any { it.firstOrNull() == "!done" }) anySystemSucceeded = true
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

                if (list.isNotEmpty()) {
                    synchronized(cachedProfiles) {
                        cachedProfiles.clear()
                        cachedProfiles.addAll(list)
                    }
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
                    val printSentences = executeSentence(listOf("/ip/hotspot/user/profile/print", "?name=${profile.name}"))
                    val hsRe = printSentences.firstOrNull { it.firstOrNull() == "!re" }
                    if (hsRe != null) {
                        val map = parseSentenceToMap(hsRe)
                        profileId = map[".id"] ?: profileId
                    } else {
                        // Check User Manager
                        val umPrint = executeSentence(listOf("/user-manager/profile/print", "?name=${profile.name}"))
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
                    val sentences = executeSentence(words)
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

                val sentences = executeSentence(words)
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
                val sentences = executeSentence(listOf("/ip/hotspot/active/remove", "=.id=$userId"))
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
                val sentences = executeSentence(listOf("/ip/hotspot/active/print", "?user=$cleanName"))
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val actId = map[".id"]
                        if (!actId.isNullOrBlank()) {
                            executeSentence(listOf("/ip/hotspot/active/remove", "=.id=$actId"))
                        }
                    }
                }
            }

            if (system == null || system == CardTargetSystem.USER_MANAGER_V7) {
                try {
                    val sentences = executeSentence(listOf("/user-manager/session/print", "?user=$cleanName"))
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val actId = map[".id"]
                            if (!actId.isNullOrBlank()) {
                                executeSentence(listOf("/user-manager/session/remove", "=.id=$actId"))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (system == null || system == CardTargetSystem.USER_MANAGER_V6) {
                try {
                    val sentences = executeSentence(listOf("/tool/user-manager/active-sessions/print", "?user=$cleanName"))
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val actId = map[".id"] ?: map["user"]
                            if (!actId.isNullOrBlank()) {
                                executeSentence(listOf("/tool/user-manager/active-sessions/remove", "=numbers=$actId"))
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

        suspend fun queryItem(cmd: String, filter: String): Map<String, String>? {
            return try {
                val sentences = executeSentence(listOf(cmd, filter))
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

                fun executeRemoval(cmd: String, idParam: String): Result<Boolean> {
                    val sentences = executeSentence(listOf(cmd, idParam))
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
                            val altSentences = executeSentence(listOf(cmd, altParam))
                            val altTrap = altSentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (altTrap == null && altSentences.any { it.firstOrNull() == "!done" }) {
                                return Result.success(true)
                            }
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

                // If not found anywhere on the router, it has already been removed
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleCardDisabled(cardId: String, currentDisabled: Boolean, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                val targetName = cardName.trim().ifBlank { cardId.trim() }
                val resolved = resolveCardTarget(cardId, targetName)
                    ?: ResolvedCardTarget(CardTargetSystem.HOTSPOT, cardId, targetName)

                val willDisable = !currentDisabled
                val newVal = if (willDisable) "yes" else "no"

                // Disconnect active session if disabling
                if (willDisable) {
                    disconnectActiveSession(targetName, resolved.system)
                }

                fun executeSet(cmd: String, idParam: String): Result<Boolean> {
                    val sentences = executeSentence(listOf(cmd, idParam, "=disabled=$newVal"))
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

                when (resolved.system) {
                    CardTargetSystem.HOTSPOT -> {
                        val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                        return@withContext executeSet("/ip/hotspot/user/set", idParam)
                    }
                    CardTargetSystem.USER_MANAGER_V7 -> {
                        val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                        return@withContext executeSet("/user-manager/user/set", idParam)
                    }
                    CardTargetSystem.USER_MANAGER_V6 -> {
                        val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=$targetName"
                        return@withContext executeSet("/tool/user-manager/user/set", idParam)
                    }
                }
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
            val sentences = executeSentence(listOf("/ip/hotspot/user/profile/print", "?name=$profileName"))
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
                executeSentence(words)
            }
        } catch (_: Exception) {}
    }

    private fun createHotspotProfileSimple(profileName: String) {
        try {
            executeSentence(listOf("/ip/hotspot/user/profile/add", "=name=$profileName", "=shared-users=1"))
        } catch (_: Exception) {}
    }

    private fun ensureUserManagerProfileExists(profileName: String, targetProfile: HotspotProfile?, isV6: Boolean = false) {
        if (isV6) {
            try {
                // Check v6 only
                val v6Sentences = executeSentence(listOf("/tool/user-manager/profile/print", "?name=$profileName"))
                if (v6Sentences.none { it.firstOrNull() == "!re" }) {
                    val v6Words = mutableListOf("/tool/user-manager/profile/add", "=name=$profileName", "=customer=admin")
                    val valTime = targetProfile?.validity?.let { formatRouterOsTime(it) } ?: "1d"
                    v6Words.add("=validity=$valTime")
                    executeSentence(v6Words)
                }
            } catch (_: Exception) {}
        } else {
            try {
                // Check v7 only
                val v7Sentences = executeSentence(listOf("/user-manager/profile/print", "?name=$profileName"))
                if (v7Sentences.none { it.firstOrNull() == "!re" }) {
                    val v7Words = mutableListOf("/user-manager/profile/add", "=name=$profileName")
                    val valTime = targetProfile?.validity?.let { formatRouterOsTime(it) } ?: "1d"
                    v7Words.add("=validity=$valTime")
                    val priceNum = targetProfile?.priceNumber?.takeIf { it > 0 } ?: 0.0
                    if (priceNum > 0) v7Words.add("=price=${priceNum.toInt()}")
                    executeSentence(v7Words)
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
        words.add("=disabled=no")
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

        val sentences = executeSentence(words)
        val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
        if (trapSentence != null) {
            val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                ?: "رفض راوتر ميكروتك إضافة الكرت"
            
            // If already exists on router, ensure it's enabled and active
            if (trapMsg.contains("already exists", ignoreCase = true) || trapMsg.contains("already have", ignoreCase = true)) {
                executeSentence(listOf("/ip/hotspot/user/set", "=numbers=${card.name.trim()}", "=disabled=no"))
                return Result.success(true)
            }

            // If server parameter was rejected (some v6 firmware)
            if (trapMsg.contains("server", ignoreCase = true) || trapMsg.contains("unknown parameter", ignoreCase = true)) {
                val simpleWords = words.filter { !it.startsWith("=server=") }
                val retrySentences = executeSentence(simpleWords)
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
                    "=disabled=no"
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
                val retrySentences = executeSentence(simpleWords)
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
                val printExisting = executeSentence(listOf("/user-manager/user/print", "?name=$cardName"))
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

            // Stage 2: Create user if not already present
            if (!userExists) {
                val words = mutableListOf(
                    "/user-manager/user/add",
                    "=name=$cardName",
                    "=disabled=no"
                )
                if (card.password.isNotBlank()) {
                    words.add("=password=${card.password.trim()}")
                }
                if (card.comment.isNotBlank()) {
                    words.add("=comment=${card.comment}")
                }
                val addSentences = executeSentence(words)
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
                    val existingProfSentences = executeSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
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
                    val profSentences = executeSentence(profWords)
                    val profTrap = profSentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (profTrap != null) {
                        val profMsg = profTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "فشل ربط المستخدم بالباقة في يوزرمانجر"
                        
                        if (!profMsg.contains("already", ignoreCase = true)) {
                            val recheck = executeSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
                            val verified = recheck.any { it.firstOrNull() == "!re" }
                            if (!verified) {
                                return Result.failure(Exception("تم إنشاء المستخدم $cardName ولكن تعذر ربط الباقة ($cleanProfile): $profMsg"))
                            }
                        }
                    }
                }
            }

            // Stage 4: Ensure User is active & enabled (only if user existed and had internalId)
            if (userExists && !internalId.isNullOrBlank()) {
                try {
                    executeSentence(listOf("/user-manager/user/set", "=numbers=$internalId", "=disabled=no"))
                } catch (_: Exception) {}
            }

            return Result.success(true)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val rawMsg = e.message ?: e.localizedMessage ?: "Timeout"
            
            // Reconciliation check
            try {
                if (ensureConnectedInternal()) {
                    val checkUser = executeSentence(listOf("/user-manager/user/print", "?name=$cardName"))
                    if (checkUser.any { it.firstOrNull() == "!re" }) {
                        if (cleanProfile.isNotBlank() && !cleanProfile.equals("default", ignoreCase = true)) {
                            try {
                                val checkProf = executeSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
                                if (checkProf.none { it.firstOrNull() == "!re" }) {
                                    executeSentence(listOf("/user-manager/user-profile/add", "=user=$cardName", "=profile=$cleanProfile"))
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
                val checkV6 = executeSentence(listOf("/tool/user-manager/user/print", "?username=$cardName"))
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

            // Stage 2: Add user in User Manager v6 if not present
            if (!userAlreadyExists) {
                val words = mutableListOf(
                    "/tool/user-manager/user/add",
                    "=username=$cardName",
                    "=customer=$cust",
                    "=disabled=no"
                )
                if (card.password.isNotBlank()) {
                    words.add("=password=${card.password.trim()}")
                }
                if (card.comment.isNotBlank()) {
                    words.add("=comment=${card.comment}")
                }
                val addSentences = executeSentence(words)
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
                    val reCheck = executeSentence(listOf("/tool/user-manager/user/print", "?username=$cardName"))
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

                // Method A: Direct user-profile/add (Fastest and safest in RouterOS v6, binds by username without numbers ambiguity)
                try {
                    val userProfWords = listOf(
                        "/tool/user-manager/user-profile/add",
                        "=user=$cardName",
                        "=profile=$cleanProfile"
                    )
                    val profSentences = executeSentence(userProfWords)
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
                        val actSentences = executeSentence(actWords)
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
                        val actSentences = executeSentence(actWords)
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
                        val checkProf = executeSentence(listOf("/tool/user-manager/user-profile/print", "?user=$cardName"))
                        if (checkProf.any { it.firstOrNull() == "!re" }) {
                            profileAssigned = true
                        }
                    } catch (_: Exception) {}
                }

                if (!profileAssigned) {
                    return Result.failure(Exception("تم إنشاء المستخدم $cardName ولكن تعذر ربط وتفعيل باقة v6 ($cleanProfile)"))
                }
            }

            // Stage 4: Ensure active and enabled (only if previously existed or using internalId)
            if (userAlreadyExists && !internalId.isNullOrBlank()) {
                try {
                    executeSentence(listOf("/tool/user-manager/user/set", "=numbers=$internalId", "=disabled=no"))
                } catch (_: Exception) {}
            }

            return Result.success(true)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val rawMsg = e.message ?: e.localizedMessage ?: "Timeout"

            // Non-blocking Reconciliation
            try {
                if (ensureConnectedInternal()) {
                    val checkV6 = executeSentence(listOf("/tool/user-manager/user/print", "?username=$cardName"))
                    val userRec = checkV6.firstOrNull { it.firstOrNull() == "!re" }
                    if (userRec != null) {
                        // User exists on the router! Fast profile assignment
                        if (cleanProfile.isNotBlank() && !cleanProfile.equals("default", ignoreCase = true)) {
                            try {
                                executeSentence(listOf(
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
                ensureConnected()
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
                val v7Sentences = executeSentence(v7Words)
                val v7Trap = v7Sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (v7Trap == null && v7Sentences.any { it.firstOrNull() == "!done" }) {
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
                val v6Sentences = executeSentence(v6Words)
                if (v6Sentences.any { it.firstOrNull() == "!done" }) {
                    return@withContext Result.success(true)
                }

                Result.success(true)
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
                            val sentences = executeSentence(listOf(
                                "/ip/hotspot/user/set",
                                targetParam,
                                "=disabled=no",
                                "=profile=$cleanProfile",
                                "=server=all"
                            ))
                            val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (trap == null && sentences.any { it.firstOrNull() == "!done" }) {
                                activated = true
                            }
                        }
                        CardTargetSystem.USER_MANAGER_V7 -> {
                            val targetParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${resolved.internalId}"
                            val sentences = executeSentence(listOf(
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
                            val sentences = executeSentence(listOf(
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
                    val hsPrint = executeSentence(listOf("/ip/hotspot/user/print", "?name=$targetName"))
                    val hsRe = hsPrint.firstOrNull { it.firstOrNull() == "!re" }
                    if (hsRe != null) {
                        val map = parseSentenceToMap(hsRe)
                        val internalId = map[".id"] ?: card.id
                        val targetParam = if (internalId.startsWith("*")) "=.id=$internalId" else "=numbers=$internalId"
                        val setRes = executeSentence(listOf(
                            "/ip/hotspot/user/set",
                            targetParam,
                            "=disabled=no",
                            "=profile=$cleanProfile",
                            "=server=all"
                        ))
                        val trap = setRes.firstOrNull { it.firstOrNull() == "!trap" }
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
                        val words = mutableListOf(
                            "/ip/hotspot/user/set",
                            targetParam
                        )
                        if (cleanPassword.isNotEmpty()) words.add("=password=$cleanPassword")
                        if (cleanProfile.isNotEmpty()) words.add("=profile=$cleanProfile")
                        words.add("=disabled=${if (card.disabled) "yes" else "no"}")
                        words.add("=comment=$cleanComment")

                        val validUptime = formatRouterOsTime(card.limitUptime)
                        if (!validUptime.isNullOrBlank() && validUptime != "0s") {
                            words.add("=limit-uptime=$validUptime")
                        }

                        if (card.limitBytesTotal > 0L) {
                            words.add("=limit-bytes-total=${card.limitBytesTotal}")
                        }

                        val sentences = executeSentence(words)
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val trapMsg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                                ?: "رفض راوتر ميكروتك تعديل كرت الهوتسبوت"

                            // Retry 1: with name parameter
                            val nameWords = mutableListOf(
                                "/ip/hotspot/user/set",
                                "=numbers=${card.name}",
                                "=disabled=${if (card.disabled) "yes" else "no"}"
                            )
                            if (cleanPassword.isNotEmpty()) nameWords.add("=password=$cleanPassword")
                            if (cleanProfile.isNotEmpty()) nameWords.add("=profile=$cleanProfile")
                            if (cleanComment.isNotEmpty()) nameWords.add("=comment=$cleanComment")

                            val nameSentences = executeSentence(nameWords)
                            val nameTrap = nameSentences.firstOrNull { it.firstOrNull() == "!trap" }
                            if (nameTrap != null) {
                                // Retry 2: core targetParam without limits
                                val coreWords = mutableListOf(
                                    "/ip/hotspot/user/set",
                                    targetParam,
                                    "=disabled=${if (card.disabled) "yes" else "no"}"
                                )
                                if (cleanPassword.isNotEmpty()) coreWords.add("=password=$cleanPassword")
                                if (cleanProfile.isNotEmpty()) coreWords.add("=profile=$cleanProfile")
                                val coreSentences = executeSentence(coreWords)
                                val coreTrap = coreSentences.firstOrNull { it.firstOrNull() == "!trap" }
                                if (coreTrap != null) {
                                    val retryMsg = coreTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: trapMsg
                                    return@withContext Result.failure(Exception("رفض الراوتر تعديل الكرت: $retryMsg"))
                                }
                            }
                        }
                        val isDone = sentences.any { it.firstOrNull() == "!done" }
                        if (!isDone && trap != null) {
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
                        var sentences = executeSentence(words)
                        var trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null && targetParam != "=numbers=${card.name}") {
                            sentences = executeSentence(listOf(
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
                            try {
                                executeSentence(listOf(
                                    "/user-manager/user-profile/add",
                                    "=user=${card.name}",
                                    "=profile=$cleanProfile"
                                ))
                            } catch (_: Exception) {}
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
                        val sentences = executeSentence(words)
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
                        executeSentence(listOf("/ip/hotspot/user/reset-counters", idParam))
                        val sentences = executeSentence(listOf("/ip/hotspot/user/set", idParam, "=disabled=no"))
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إعادة تفعيل الكرت"
                            return@withContext Result.failure(Exception(msg))
                        }
                        return@withContext Result.success(true)
                    }
                    CardTargetSystem.USER_MANAGER_V7 -> {
                        val idParam = if (resolved.internalId.startsWith("*")) "=.id=${resolved.internalId}" else "=numbers=${card.name}"
                        executeSentence(listOf("/user-manager/user/reset-counters", idParam))
                        val sentences = executeSentence(listOf("/user-manager/user/set", idParam, "=disabled=no"))
                        val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                        if (trap != null) {
                            val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إعادة تفعيل كرت يوزرمانجر"
                            return@withContext Result.failure(Exception(msg))
                        }
                        return@withContext Result.success(true)
                    }
                    CardTargetSystem.USER_MANAGER_V6 -> {
                        val targetParam = "=numbers=${card.name}"
                        executeSentence(listOf("/tool/user-manager/user/reset-counters", targetParam))
                        val sentences = executeSentence(listOf("/tool/user-manager/user/set", targetParam, "=disabled=no"))
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
                ensureConnected()
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

                val sentences = executeSentence(words)
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
                ensureConnected()
                val targetName = profileName.ifBlank { profileId }

                // 1. Try Hotspot profile removal
                if (profileId.startsWith("*")) {
                    val s1 = executeSentence(listOf("/ip/hotspot/user/profile/remove", "=.id=$profileId"))
                    if (s1.any { it.firstOrNull() == "!done" } && s1.none { it.firstOrNull() == "!trap" }) return@withContext Result.success(true)
                }
                if (targetName.isNotBlank()) {
                    val s1b = executeSentence(listOf("/ip/hotspot/user/profile/remove", "=numbers=$targetName"))
                    if (s1b.any { it.firstOrNull() == "!done" } && s1b.none { it.firstOrNull() == "!trap" }) return@withContext Result.success(true)
                }

                // 2. Try User Manager v7 profile removal
                if (profileId.startsWith("*")) {
                    val s2 = executeSentence(listOf("/user-manager/profile/remove", "=.id=$profileId"))
                    if (s2.any { it.firstOrNull() == "!done" } && s2.none { it.firstOrNull() == "!trap" }) return@withContext Result.success(true)
                }
                if (targetName.isNotBlank()) {
                    val s2b = executeSentence(listOf("/user-manager/profile/remove", "=numbers=$targetName"))
                    if (s2b.any { it.firstOrNull() == "!done" } && s2b.none { it.firstOrNull() == "!trap" }) return@withContext Result.success(true)
                }

                // 3. Try User Manager v6 profile removal
                if (targetName.isNotBlank()) {
                    val s3 = executeSentence(listOf("/tool/user-manager/profile/remove", "=numbers=$targetName"))
                    if (s3.any { it.firstOrNull() == "!done" } && s3.none { it.firstOrNull() == "!trap" }) return@withContext Result.success(true)
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
                executeSentence(listOf("/system/reboot"))
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
                val sentences = executeSentence(listOf("/interface/print", "=.proplist=.id,name,type,mac-address,rx-byte,tx-byte,rx-packet,tx-packet,running,disabled,comment"))
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
                    Result.success(RouterBoardInfo())
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
                val sentences = executeSentence(listOf("/log/print"))
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
                // Try v7 user-manager first
                var sentences = executeSentence(listOf("/user-manager/user/print"))
                if (sentences.none { it.firstOrNull() == "!re" }) {
                    // Try v6 /tool/user-manager/user
                    sentences = executeSentence(listOf("/tool/user-manager/user/print"))
                }

                val list = mutableListOf<HotspotCard>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val name = map["name"] ?: map["username"] ?: ""
                        if (name.isNotBlank()) {
                            val comment = map["comment"] ?: ""
                            val profile = map["profile"] ?: map["actual-profile"] ?: "UserManager"
                            val price = extractPriceFromText(comment, profile, name)
                            
                            // MikroTik User Manager v6:
                            // download-used = Router to Client (Download) -> bytesOut
                            // upload-used = Client to Router (Upload) -> bytesIn
                            val downUsed = map["download-used"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            val upUsed = map["upload-used"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            
                            val downLimit = map["download-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            val totalLimit = map["total-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            val transLimit = map["transfer-limit"]?.let { com.example.data.network.NetworkUtils.parseSizeToBytes(it) } ?: 0L
                            
                            val bestLimit = if (totalLimit > 0L) totalLimit else if (transLimit > 0L) transLimit else downLimit

                            list.add(
                                HotspotCard(
                                    id = map[".id"] ?: "um_${list.size}",
                                    name = name,
                                    password = map["password"] ?: "",
                                    profile = profile,
                                    uptime = map["uptime"] ?: map["till-time"] ?: "0s",
                                    bytesIn = upUsed,
                                    bytesOut = downUsed,
                                    limitUptime = map["time-limit"] ?: "",
                                    limitBytesTotal = bestLimit,
                                    comment = comment,
                                    disabled = map["disabled"] == "true" || map["disabled"] == "yes",
                                    price = price,
                                    cardType = "usermanager"
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

    private fun reconnectInternalLocked(): Boolean {
        if (isExplicitlyClosed) return false
        closeSocketOnlyLocked()
        val cleanHost = lastHost.trim()
        val cleanUser = lastUsername.trim()
        if (cleanHost.isBlank() || cleanUser.isBlank()) return false

        return try {
            val sock = Socket()
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.connect(InetSocketAddress(cleanHost, lastPort), 8000)
            sock.soTimeout = 25000
            socket = sock
            inStream = sock.getInputStream()
            outStream = sock.getOutputStream()

            writeSentenceLocked(listOf("/login", "=name=$cleanUser", "=password=$lastPassword"))
            val response = readSingleSentenceLocked()
            val replyType = response.firstOrNull() ?: ""

            if (replyType == "!done" && response.none { it.startsWith("=ret=") }) {
                true
            } else {
                val challengeParam = response.firstOrNull { it.startsWith("=ret=") }
                if (replyType == "!done" && challengeParam != null) {
                    val challengeHex = challengeParam.removePrefix("=ret=")
                    val challengeBytes = hexStringToByteArray(challengeHex)
                    val md5 = MessageDigest.getInstance("MD5")
                    md5.update(0.toByte())
                    md5.update(lastPassword.toByteArray(Charsets.UTF_8))
                    md5.update(challengeBytes)
                    val digestHex = byteArrayToHexString(md5.digest())
                    val responseHash = "00$digestHex"

                    writeSentenceLocked(listOf("/login", "=name=$cleanUser", "=response=$responseHash"))
                    val resp2 = readSingleSentenceLocked()
                    resp2.firstOrNull() == "!done"
                } else {
                    closeSocketOnlyLocked()
                    false
                }
            }
        } catch (_: Exception) {
            closeSocketOnlyLocked()
            false
        }
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
                if (attempts >= 2 || isExplicitlyClosed || lastHost.isBlank()) {
                    throw e
                }
            }
        }
        return@synchronized emptyList()
    }

    // Must ONLY be called while holding socketLock
    private fun writeSentenceLocked(words: List<String>) {
        val out = outStream ?: throw IllegalStateException("Socket is not connected")
        for (word in words) {
            writeWord(out, word)
        }
        out.write(0x00) // End of sentence
        out.flush()
    }

    // Must ONLY be called while holding socketLock
    private fun readSingleSentenceLocked(): List<String> {
        val inS = inStream ?: throw IllegalStateException("Socket is not connected")
        val words = mutableListOf<String>()
        while (true) {
            val word = readWord(inS)
            if (word.isEmpty()) break
            words.add(word)
        }
        return words
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
                                ipAddress = map["address"] ?: "192.168.88.1",
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
                val sentences = executeSentence(words)
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

    private fun closeSocketOnlyLocked() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inStream = null
        outStream = null
    }

    fun close() {
        synchronized(socketLock) {
            closeSocketOnlyLocked()
        }
    }

    fun disconnect() {
        synchronized(socketLock) {
            isExplicitlyClosed = true
            lastHost = ""
            lastUsername = ""
            lastPassword = ""
            closeSocketOnlyLocked()
        }
    }

    suspend fun getDnsCache(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/dns/cache/print"))
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

    suspend fun getFirewallConnections(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val sentences = executeSentence(listOf("/ip/firewall/connection/print"))
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
