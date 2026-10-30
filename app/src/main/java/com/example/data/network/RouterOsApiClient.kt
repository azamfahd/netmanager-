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

class RouterOsApiClient {

    private val socketLock = Any()
    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private var lastHost: String = ""
    private var lastPort: Int = 8728
    private var lastUsername: String = ""
    private var lastPassword: String = ""

    private var lastExecutionSentences: List<List<String>> = emptyList()

    private val cachedProfiles = mutableListOf<HotspotProfile>()

    fun getCachedProfiles(): List<HotspotProfile> = synchronized(cachedProfiles) { cachedProfiles.toList() }

    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        synchronized(socketLock) {
            if (socket != null && socket?.isClosed == false && socket?.isConnected == true) {
                return@synchronized true
            }
            if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
                return@synchronized reconnectInternal()
            }
            false
        }
    }

    suspend fun connectAndLogin(
        host: String,
        port: Int = 8728,
        username: String,
        password: String,
        timeoutMs: Int = 6000
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                close()
                val cleanHost = host.trim()
                val cleanUser = username.trim()
                lastHost = cleanHost
                lastPort = port
                lastUsername = cleanUser
                lastPassword = password

                val sock = Socket()
                sock.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                sock.soTimeout = 8000
                socket = sock
                inStream = sock.getInputStream()
                outStream = sock.getOutputStream()

                // Attempt RouterOS v6.43+ and v7 login
                writeSentence(listOf("/login", "=name=$cleanUser", "=password=$password"))
                var response = readSentence()

                if (response.isEmpty()) {
                    return@withContext Result.failure(Exception("لم يستجب الراوتر على منفذ API ($port)"))
                }

                val replyType = response.firstOrNull() ?: ""

                // Success
                if (replyType == "!done" && response.none { it.startsWith("=ret=") }) {
                    return@withContext Result.success(true)
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

                    writeSentence(listOf("/login", "=name=$cleanUser", "=response=$responseHash"))
                    response = readSentence()
                    if (response.firstOrNull() == "!done") {
                        return@withContext Result.success(true)
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

                close()
                Result.failure(Exception(userFriendlyError))
            } catch (e: Exception) {
                close()
                Result.failure(e)
            }
        }
    }

    suspend fun getSystemResource(): Result<RouterResource> {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureConnected()) {
                    return@withContext Result.failure(Exception("غير متصل بالراوتر عبر منفذ API ($lastPort)"))
                }
                writeSentence(listOf("/system/resource/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/hotspot/active/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/hotspot/user/print"))
                val sentences = readAllSentencesUntilDone()
                val list = mutableListOf<HotspotCard>()
                for (s in sentences) {
                    if (s.firstOrNull() == "!re") {
                        val map = parseSentenceToMap(s)
                        val name = map["name"] ?: ""
                        val comment = map["comment"] ?: ""
                        val profile = map["profile"] ?: "default"
                        val parsedPrice = extractPriceFromText(comment, profile, name)
                        list.add(
                            HotspotCard(
                                id = map[".id"] ?: "c_${list.size}",
                                name = name,
                                password = map["password"] ?: "",
                                profile = profile,
                                uptime = map["uptime"] ?: "0s",
                                bytesIn = map["bytes-in"]?.toLongOrNull() ?: 0L,
                                bytesOut = map["bytes-out"]?.toLongOrNull() ?: 0L,
                                limitUptime = map["limit-uptime"] ?: "",
                                limitBytesTotal = map["limit-bytes-total"]?.toLongOrNull() ?: 0L,
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

                // 1. Fetch Hotspot User Profiles (/ip/hotspot/user/profile)
                try {
                    writeSentence(listOf("/ip/hotspot/user/profile/print"))
                    val sentences = readAllSentencesUntilDone()
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
                } catch (_: Exception) {}

                // 2. Fetch User-Manager Profiles if available (/user-manager/profile)
                try {
                    writeSentence(listOf("/user-manager/profile/print"))
                    val umSentences = readAllSentencesUntilDone()
                    for (s in umSentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: continue
                            if (list.any { it.name.equals(name, ignoreCase = true) && it.source == "usermanager_v7" }) continue

                            val rawPrice = map["price"]?.takeIf { it.isNotBlank() && it != "0" }
                            val priceStr = if (rawPrice != null) "$rawPrice ر.ي" else extractPriceFromText(map["comment"].orEmpty(), name, "")
                            val priceNum = rawPrice?.toDoubleOrNull() ?: (Regex("""\d+""").find(priceStr)?.value?.toDoubleOrNull() ?: 0.0)
                            val validity = map["validity"] ?: map["session-timeout"] ?: "1d"

                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "um_${list.size}",
                                    name = name,
                                    rateLimit = map["rate-limit"] ?: "مفتوح",
                                    sharedUsers = 1,
                                    sessionTimeout = validity,
                                    keepaliveTimeout = "5m",
                                    price = priceStr,
                                    uploadLimit = "",
                                    downloadLimit = "",
                                    validity = validity,
                                    dataLimit = "",
                                    source = "usermanager_v7",
                                    comment = map["comment"].orEmpty(),
                                    priceNumber = priceNum
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}

                // 3. Fetch Tool User-Manager Profiles if available (/tool/user-manager/profile)
                try {
                    writeSentence(listOf("/tool/user-manager/profile/print"))
                    val tumSentences = readAllSentencesUntilDone()
                    for (s in tumSentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: continue
                            if (list.any { it.name.equals(name, ignoreCase = true) && it.source.startsWith("usermanager") }) continue
                            val rawPrice = map["price"]?.takeIf { it.isNotBlank() && it != "0" }
                            val priceStr = if (rawPrice != null) "$rawPrice ر.ي" else extractPriceFromText(map["comment"].orEmpty(), name, "")
                            val customer = map["customer"]?.takeIf { it.isNotBlank() } ?: "admin"
                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "tum_${list.size}",
                                    name = name,
                                    rateLimit = map["rate-limit"] ?: "مفتوح",
                                    sharedUsers = 1,
                                    sessionTimeout = map["validity"] ?: "1d",
                                    price = priceStr,
                                    source = "usermanager_v6",
                                    comment = customer
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}

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
                ensureConnected()
                var profileId = profile.id
                if (!profileId.startsWith("*")) {
                    writeSentence(listOf("/ip/hotspot/user/profile/print"))
                    val sentences = readAllSentencesUntilDone()
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            if (map["name"]?.trim().equals(profile.name.trim(), ignoreCase = true)) {
                                profileId = map[".id"] ?: profileId
                                break
                            }
                        }
                    }
                }

                val target = if (profileId.startsWith("*")) "=.id=$profileId" else "=name=${profile.name}"
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
                    return@withContext Result.failure(Exception(trapMsg))
                }
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                if (!isDone) {
                    return@withContext Result.failure(Exception("لم يؤكد الراوتر تعديل الباقة"))
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
                writeSentence(listOf("/ip/dhcp-server/lease/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/hotspot/host/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/interface/wireless/registration-table/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/arp/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/interface/bridge/host/print"))
                val sentences = readAllSentencesUntilDone()
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

    private fun extractDataLimitFromText(name: String, comment: String): String {
        val combined = "$name $comment"
        if (combined.isBlank()) return "غير محدد"
        val match = Regex("""(?i)(\d+(?:\.\d+)?)\s*(GB|MB|GI|MI|G|M|جيجا|ميجا)""").find(combined)
        if (match != null && match.groupValues.size > 1) {
            val num = match.groupValues[1]
            val unit = match.groupValues[2].uppercase()
            val normalizedUnit = when {
                unit.startsWith("G") || unit.contains("جيجا") -> "GB"
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
                writeSentence(listOf("/ip/hotspot/ip-binding/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/hotspot/active/remove", "=.id=$userId"))
                val sentences = readAllSentencesUntilDone()
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                Result.success(isDone)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteCard(cardId: String, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                ensureConnected()
                
                // Helper to execute removal and check for trap
                fun executeRemoval(words: List<String>): Result<Boolean> {
                    writeSentence(words)
                    val sentences = readAllSentencesUntilDone()
                    val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trap != null) {
                        val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل حذف الكرت"
                        return Result.failure(Exception(msg))
                    }
                    if (sentences.any { it.firstOrNull() == "!done" }) return Result.success(true)
                    return Result.failure(Exception("لم يؤكد الراوتر حذف الكرت"))
                }

                val targetName = cardName.ifBlank { cardId }

                // 0. Remove active hotspot session first if any so Mikrotik doesn't keep/recreate the user
                if (targetName.isNotBlank()) {
                    try {
                        writeSentence(listOf("/ip/hotspot/active/print", "?user=$targetName"))
                        val activeSentences = readAllSentencesUntilDone()
                        for (s in activeSentences) {
                            if (s.firstOrNull() == "!re") {
                                val activeMap = parseSentenceToMap(s)
                                val activeId = activeMap[".id"]
                                if (!activeId.isNullOrBlank()) {
                                    writeSentence(listOf("/ip/hotspot/active/remove", "=.id=$activeId"))
                                    readAllSentencesUntilDone()
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Find .id by name first if cardId doesn't start with *
                var actualId = cardId
                if (!actualId.startsWith("*") && targetName.isNotBlank()) {
                    try {
                        writeSentence(listOf("/ip/hotspot/user/print", "?name=$targetName"))
                        val userSentences = readAllSentencesUntilDone()
                        for (s in userSentences) {
                            if (s.firstOrNull() == "!re") {
                                val userMap = parseSentenceToMap(s)
                                if (userMap["name"]?.trim().equals(targetName.trim(), ignoreCase = true)) {
                                    actualId = userMap[".id"] ?: actualId
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 1. Try Hotspot removal
                if (actualId.startsWith("*")) {
                    val hsRes = executeRemoval(listOf("/ip/hotspot/user/remove", "=.id=$actualId"))
                    if (hsRes.isSuccess) return@withContext hsRes
                }
                if (targetName.isNotBlank()) {
                    val hsNameRes = executeRemoval(listOf("/ip/hotspot/user/remove", "=numbers=$targetName"))
                    if (hsNameRes.isSuccess) return@withContext hsNameRes
                }

                // 2. Try User Manager v7
                if (actualId.startsWith("*")) {
                    val um7Res = executeRemoval(listOf("/user-manager/user/remove", "=.id=$actualId"))
                    if (um7Res.isSuccess) return@withContext um7Res
                }
                if (targetName.isNotBlank()) {
                    val um7NameRes = executeRemoval(listOf("/user-manager/user/remove", "=numbers=$targetName"))
                    if (um7NameRes.isSuccess) return@withContext um7NameRes
                }

                // 3. Try User Manager v6
                if (targetName.isNotBlank()) {
                    val um6Res = executeRemoval(listOf("/tool/user-manager/user/remove", "=numbers=$targetName"))
                    if (um6Res.isSuccess) return@withContext um6Res
                }

                // If all failed, return failure
                Result.failure(Exception("فشل حذف الكرت من كافة المسارات بالراوتر ($targetName)"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleCardDisabled(cardId: String, currentDisabled: Boolean, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                ensureConnected()
                val newVal = if (currentDisabled) "no" else "yes"
                // 1. Try Hotspot
                writeSentence(listOf("/ip/hotspot/user/set", "=.id=$cardId", "=disabled=$newVal"))
                val sentences = readAllSentencesUntilDone()
                if (sentences.any { it.firstOrNull() == "!done" }) return@withContext Result.success(true)

                // 2. Try User Manager v7
                writeSentence(listOf("/user-manager/user/set", "=.id=$cardId", "=disabled=$newVal"))
                val um7Sentences = readAllSentencesUntilDone()
                if (um7Sentences.any { it.firstOrNull() == "!done" }) return@withContext Result.success(true)

                // 3. Try User Manager v6
                val targetName = cardName.ifBlank { cardId }
                writeSentence(listOf("/tool/user-manager/user/set", "=numbers=$targetName", "=disabled=$newVal"))
                val um6Sentences = readAllSentencesUntilDone()
                if (um6Sentences.any { it.firstOrNull() == "!done" }) return@withContext Result.success(true)

                Result.success(true)
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

                val shouldBeUserManager = preferredSystem == "usermanager" ||
                    (card.isUserManagerCard && preferredSystem != "hotspot") ||
                    (targetProfile?.isUserManager == true && preferredSystem != "hotspot") ||
                    (profileSource.startsWith("usermanager") && preferredSystem != "hotspot")

                // 1. If destination is User Manager: NEVER fallback silently to Hotspot!
                if (shouldBeUserManager) {
                    // Make sure profile exists in User Manager
                    if (profileName.isNotBlank() && !profileName.equals("default", ignoreCase = true)) {
                        ensureUserManagerProfileExists(profileName, targetProfile)
                    }

                    val isV6 = targetProfile?.source == "usermanager_v6" || profileSource == "usermanager_v6"
                    val umResult = if (isV6) {
                        val customer = targetProfile?.comment?.takeIf { it.isNotBlank() } ?: "admin"
                        addUserManagerV6User(card, customer)
                    } else {
                        addUserManagerV7User(card)
                    }

                    // Strict boundary: User Manager cards must succeed or fail in User Manager with explicit error
                    if (umResult.isSuccess) {
                        return@withContext umResult
                    } else {
                        val umError = umResult.exceptionOrNull()?.message ?: "فشل إنشاء كرت يوزرمانجر"
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
            writeSentence(listOf("/ip/hotspot/user/profile/print", "?name=$profileName"))
            val sentences = readAllSentencesUntilDone()
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
                writeSentence(words)
                readAllSentencesUntilDone()
            }
        } catch (_: Exception) {}
    }

    private fun createHotspotProfileSimple(profileName: String) {
        try {
            writeSentence(listOf("/ip/hotspot/user/profile/add", "=name=$profileName", "=shared-users=1"))
            readAllSentencesUntilDone()
        } catch (_: Exception) {}
    }

    private fun ensureUserManagerProfileExists(profileName: String, targetProfile: HotspotProfile?) {
        try {
            // Check v7
            writeSentence(listOf("/user-manager/profile/print", "?name=$profileName"))
            val v7Sentences = readAllSentencesUntilDone()
            if (v7Sentences.none { it.firstOrNull() == "!re" }) {
                val v7Words = mutableListOf("/user-manager/profile/add", "=name=$profileName")
                val valTime = targetProfile?.validity?.let { formatRouterOsTime(it) } ?: "1d"
                v7Words.add("=validity=$valTime")
                val priceNum = targetProfile?.priceNumber?.takeIf { it > 0 } ?: 0.0
                if (priceNum > 0) v7Words.add("=price=${priceNum.toInt()}")
                writeSentence(v7Words)
                readAllSentencesUntilDone()
            }
        } catch (_: Exception) {}
        try {
            // Check v6
            writeSentence(listOf("/tool/user-manager/profile/print", "?name=$profileName"))
            val v6Sentences = readAllSentencesUntilDone()
            if (v6Sentences.none { it.firstOrNull() == "!re" }) {
                val v6Words = mutableListOf("/tool/user-manager/profile/add", "=name=$profileName", "=customer=admin")
                val valTime = targetProfile?.validity?.let { formatRouterOsTime(it) } ?: "1d"
                v6Words.add("=validity=$valTime")
                writeSentence(v6Words)
                readAllSentencesUntilDone()
            }
        } catch (_: Exception) {}
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

        writeSentence(words)
        val sentences = readAllSentencesUntilDone()
        val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
        if (trapSentence != null) {
            val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                ?: "رفض راوتر ميكروتك إضافة الكرت"
            
            // If already exists on router, ensure it's enabled and active
            if (trapMsg.contains("already exists", ignoreCase = true) || trapMsg.contains("already have", ignoreCase = true)) {
                writeSentence(listOf("/ip/hotspot/user/set", "=numbers=${card.name.trim()}", "=disabled=no"))
                readAllSentencesUntilDone()
                return Result.success(true)
            }

            // If server parameter was rejected (some v6 firmware)
            if (trapMsg.contains("server", ignoreCase = true) || trapMsg.contains("unknown parameter", ignoreCase = true)) {
                val simpleWords = words.filter { !it.startsWith("=server=") }
                writeSentence(simpleWords)
                val retrySentences = readAllSentencesUntilDone()
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
                writeSentence(simpleWords)
                val retrySentences = readAllSentencesUntilDone()
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
        try {
            // Phase 1: Create user in User Manager v7
            val words = mutableListOf(
                "/user-manager/user/add",
                "=name=$cardName",
                "=disabled=no"
            )
            if (card.password.isNotBlank()) {
                words.add("=password=${card.password.trim()}")
            }
            if (card.comment.isNotBlank()) words.add("=comment=${card.comment}")
            writeSentence(words)
            val sentences = readAllSentencesUntilDone()
            val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
            if (trap != null) {
                val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إضافة مستخدم يوزرمانجر"
                if (!msg.contains("already exists", ignoreCase = true)) {
                    return Result.failure(Exception(msg))
                }
            }

            // Phase 2: Bind profile transactionally
            if (cleanProfile.isNotBlank()) {
                val profWords = listOf(
                    "/user-manager/user-profile/add",
                    "=user=$cardName",
                    "=profile=$cleanProfile"
                )
                writeSentence(profWords)
                val profSentences = readAllSentencesUntilDone()
                val profTrap = profSentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (profTrap != null) {
                    val profMsg = profTrap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "فشل ربط المستخدم بالباقة في يوزرمانجر"
                    
                    // Transactional Rollback: Remove the user if profile assignment failed
                    try {
                        writeSentence(listOf("/user-manager/user/remove", "=numbers=$cardName"))
                        readAllSentencesUntilDone()
                    } catch (_: Exception) {}

                    return Result.failure(Exception("فشل تفعيل باقة اليوزرمانجر ($cleanProfile) للمستخدم $cardName: $profMsg (تم التراجع عن إنشاء الكرت)"))
                }
            }

            // Phase 3: Ensure enabled in UM v7
            writeSentence(listOf("/user-manager/user/set", "=numbers=$cardName", "=disabled=no"))
            readAllSentencesUntilDone()

            // Phase 4: Post-creation verification
            if (cleanProfile.isNotBlank()) {
                writeSentence(listOf("/user-manager/user-profile/print", "?user=$cardName"))
                val verifySentences = readAllSentencesUntilDone()
                val isProfileAssigned = verifySentences.any { it.firstOrNull() == "!re" }
                if (!isProfileAssigned) {
                    // Check if user itself exists
                    writeSentence(listOf("/user-manager/user/print", "?name=$cardName"))
                    val userCheck = readAllSentencesUntilDone()
                    if (userCheck.none { it.firstOrNull() == "!re" }) {
                        return Result.failure(Exception("لم يؤكد الراوتر حفظ الكرت $cardName في يوزرمانجر"))
                    }
                }
            }

            return Result.success(true)
        } catch (e: Exception) {
            // Rollback on unexpected exception
            try {
                writeSentence(listOf("/user-manager/user/remove", "=numbers=$cardName"))
                readAllSentencesUntilDone()
            } catch (_: Exception) {}
            return Result.failure(e)
        }
    }

    private fun addUserManagerV6User(card: HotspotCard, customer: String): Result<Boolean> {
        val cardName = card.name.trim()
        val cleanProfile = card.profile.trim()
        val cust = if (customer.isBlank()) "admin" else customer
        try {
            // Phase 1: Add user in User Manager v6
            val words = mutableListOf(
                "/tool/user-manager/user/add",
                "=username=$cardName",
                "=customer=$cust",
                "=disabled=no"
            )
            if (card.password.isNotBlank()) {
                words.add("=password=${card.password.trim()}")
            }
            if (card.comment.isNotBlank()) words.add("=comment=${card.comment}")
            writeSentence(words)
            val sentences = readAllSentencesUntilDone()
            val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
            if (trap != null) {
                val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "فشل إضافة كرت يوزرمانجر"
                if (!msg.contains("already exists", ignoreCase = true)) {
                    return Result.failure(Exception(msg))
                }
            }

            // Phase 2: Assign & activate profile
            if (cleanProfile.isNotBlank()) {
                val actWords = listOf(
                    "/tool/user-manager/user/create-and-activate-profile",
                    "=numbers=$cardName",
                    "=profile=$cleanProfile",
                    "=customer=$cust"
                )
                writeSentence(actWords)
                val actSentences = readAllSentencesUntilDone()
                val actTrap = actSentences.firstOrNull { it.firstOrNull() == "!trap" }
                val actDone = actSentences.any { it.firstOrNull() == "!done" }
                var profileAssigned = (actTrap == null && actDone)

                if (!profileAssigned) {
                    writeSentence(listOf(
                        "/tool/user-manager/user/set",
                        "=numbers=$cardName",
                        "=actual-profile=$cleanProfile",
                        "=disabled=no"
                    ))
                    val setSentences = readAllSentencesUntilDone()
                    val setTrap = setSentences.firstOrNull { it.firstOrNull() == "!trap" }
                    val setDone = setSentences.any { it.firstOrNull() == "!done" }
                    profileAssigned = (setTrap == null && setDone)
                }

                if (!profileAssigned) {
                    val errMsg = actTrap?.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "فشل تعيين وتفعيل الباقة في User Manager v6"
                    
                    // Transactional Rollback: Remove the user if profile assignment failed
                    try {
                        writeSentence(listOf("/tool/user-manager/user/remove", "=numbers=$cardName"))
                        readAllSentencesUntilDone()
                    } catch (_: Exception) {}

                    return Result.failure(Exception("فشل تفعيل باقة User Manager v6 ($cleanProfile): $errMsg (تم التراجع عن إنشاء الكرت)"))
                }
            }
            return Result.success(true)
        } catch (e: Exception) {
            // Rollback on unexpected exception
            try {
                writeSentence(listOf("/tool/user-manager/user/remove", "=numbers=$cardName"))
                readAllSentencesUntilDone()
            } catch (_: Exception) {}
            return Result.failure(e)
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
                writeSentence(v7Words)
                val v7Sentences = readAllSentencesUntilDone()
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
                writeSentence(v6Words)
                val v6Sentences = readAllSentencesUntilDone()
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
                ensureConnected()
                val targetName = card.name.trim()
                if (targetName.isBlank()) return@withContext Result.failure(Exception("اسم الكرت فارغ"))

                val cleanProfile = card.profile.trim().ifBlank { "default" }
                if (!cleanProfile.equals("default", ignoreCase = true)) {
                    ensureHotspotProfileExists(cleanProfile, null)
                }

                // Ensure Hotspot user is present and active
                writeSentence(listOf("/ip/hotspot/user/print", "?name=$targetName"))
                val hsSentences = readAllSentencesUntilDone()
                val hsExists = hsSentences.any { it.firstOrNull() == "!re" }

                if (hsExists) {
                    writeSentence(listOf(
                        "/ip/hotspot/user/set",
                        "=numbers=$targetName",
                        "=disabled=no",
                        "=profile=$cleanProfile",
                        "=server=all"
                    ))
                    readAllSentencesUntilDone()
                } else {
                    addHotspotNativeUser(card.copy(profile = cleanProfile))
                }

                // Also check and enable in User Manager if user exists there
                try {
                    writeSentence(listOf("/user-manager/user/set", "=numbers=$targetName", "=disabled=no"))
                    readAllSentencesUntilDone()
                } catch (_: Exception) {}
                try {
                    writeSentence(listOf("/tool/user-manager/user/set", "=numbers=$targetName", "=disabled=no"))
                    readAllSentencesUntilDone()
                } catch (_: Exception) {}

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                ensureConnected()
                val targetProfile = synchronized(cachedProfiles) {
                    cachedProfiles.firstOrNull { it.name.trim().equals(card.profile.trim(), ignoreCase = true) }
                }
                val source = targetProfile?.source ?: ""

                if (source == "usermanager_v6") {
                    writeSentence(listOf(
                        "/tool/user-manager/user/set",
                        "=numbers=${card.name}",
                        "=password=${card.password}",
                        "=actual-profile=${card.profile}",
                        "=comment=${card.comment}"
                    ))
                    readAllSentencesUntilDone()
                    return@withContext Result.success(true)
                }

                if (source == "usermanager_v7") {
                    writeSentence(listOf(
                        "/user-manager/user/set",
                        "=.id=${card.id}",
                        "=password=${card.password}",
                        "=comment=${card.comment}"
                    ))
                    readAllSentencesUntilDone()
                    if (card.profile.isNotBlank()) {
                        writeSentence(listOf(
                            "/user-manager/user-profile/add",
                            "=user=${card.name}",
                            "=profile=${card.profile}"
                        ))
                        readAllSentencesUntilDone()
                    }
                    return@withContext Result.success(true)
                }

                val target = if (card.id.startsWith("*")) "=.id=${card.id}" else "=numbers=${card.name}"
                val cleanProfile = card.profile.trim()
                val words = mutableListOf(
                    "/ip/hotspot/user/set",
                    target,
                    "=name=${card.name}",
                    "=password=${card.password}",
                    "=disabled=no"
                )
                if (cleanProfile.isNotBlank()) words.add("=profile=$cleanProfile")
                if (card.comment.isNotBlank()) words.add("=comment=${card.comment}")
                val validUptime = formatRouterOsTime(card.limitUptime)
                if (!validUptime.isNullOrBlank()) {
                    words.add("=limit-uptime=$validUptime")
                }
                if (card.limitBytesTotal > 0) {
                    words.add("=limit-bytes-total=${card.limitBytesTotal}")
                }
                writeSentence(words)
                val sentences = readAllSentencesUntilDone()
                val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trap != null && !card.id.startsWith("*")) {
                    // Try with pure profile and disabled=no
                    val retryWords = mutableListOf(
                        "/ip/hotspot/user/set",
                        "=numbers=${card.name}",
                        "=password=${card.password}",
                        "=disabled=no"
                    )
                    if (cleanProfile.isNotBlank()) retryWords.add("=profile=$cleanProfile")
                    if (card.comment.isNotBlank()) retryWords.add("=comment=${card.comment}")
                    writeSentence(retryWords)
                    readAllSentencesUntilDone()
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reactivateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                ensureConnected()
                // 1. Hotspot reset counters & enable
                writeSentence(listOf("/ip/hotspot/user/reset-counters", "=.id=${card.id}"))
                val s1 = readAllSentencesUntilDone()
                if (s1.none { it.firstOrNull() == "!done" }) {
                    writeSentence(listOf("/ip/hotspot/user/reset-counters", "=numbers=${card.name}"))
                    readAllSentencesUntilDone()
                }
                writeSentence(listOf("/ip/hotspot/user/set", "=.id=${card.id}", "=disabled=no"))
                readAllSentencesUntilDone()

                // 2. User Manager v7 reset counters & enable
                writeSentence(listOf("/user-manager/user/reset-counters", "=.id=${card.id}"))
                readAllSentencesUntilDone()
                writeSentence(listOf("/user-manager/user/set", "=.id=${card.id}", "=disabled=no"))
                readAllSentencesUntilDone()

                // 3. User Manager v6 reset counters & enable
                writeSentence(listOf("/tool/user-manager/user/reset-counters", "=numbers=${card.name}"))
                readAllSentencesUntilDone()
                writeSentence(listOf("/tool/user-manager/user/set", "=numbers=${card.name}", "=disabled=no"))
                readAllSentencesUntilDone()

                Result.success(true)
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
                writeSentence(listOf("/system/reboot"))
                readAllSentencesUntilDone()
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSystemIdentity(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                writeSentence(listOf("/system/identity/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/interface/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/interface/wireless/print"))
                val sentences = readAllSentencesUntilDone()
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

    suspend fun getRouterBoardInfo(): Result<RouterBoardInfo> {
        return withContext(Dispatchers.IO) {
            try {
                writeSentence(listOf("/system/routerboard/print"))
                val sentences = readAllSentencesUntilDone()
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
                ensureConnected()
                writeSentence(listOf("/system/health/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/log/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/hotspot/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/ip/pool/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(listOf("/user-manager/user/print"))
                var sentences = readAllSentencesUntilDone()
                if (sentences.none { it.firstOrNull() == "!re" }) {
                    // Try v6 /tool/user-manager/user
                    writeSentence(listOf("/tool/user-manager/user/print"))
                    sentences = readAllSentencesUntilDone()
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
                            list.add(
                                HotspotCard(
                                    id = map[".id"] ?: "um_${list.size}",
                                    name = name,
                                    password = map["password"] ?: "",
                                    profile = profile,
                                    uptime = map["uptime"] ?: map["till-time"] ?: "0s",
                                    bytesIn = map["download-used"]?.toLongOrNull() ?: 0L,
                                    bytesOut = map["upload-used"]?.toLongOrNull() ?: 0L,
                                    limitUptime = map["time-limit"] ?: "",
                                    limitBytesTotal = map["download-limit"]?.toLongOrNull() ?: 0L,
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
                writeSentence(listOf("/ip/neighbor/print"))
                val sentences = readAllSentencesUntilDone()
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

    private fun reconnectInternal(): Boolean {
        close()
        val cleanHost = lastHost.trim()
        val cleanUser = lastUsername.trim()
        if (cleanHost.isBlank() || cleanUser.isBlank()) return false

        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(cleanHost, lastPort), 5000)
            sock.soTimeout = 8000
            socket = sock
            inStream = sock.getInputStream()
            outStream = sock.getOutputStream()

            writeSentenceWithoutRetry(listOf("/login", "=name=$cleanUser", "=password=$lastPassword"))
            val response = readSentenceWithoutRetry()
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

                    writeSentenceWithoutRetry(listOf("/login", "=name=$cleanUser", "=response=$responseHash"))
                    val resp2 = readSentenceWithoutRetry()
                    resp2.firstOrNull() == "!done"
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            close()
            false
        }
    }

    fun executeSentence(words: List<String>): List<List<String>> = synchronized(socketLock) {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            try {
                if (socket == null || socket?.isClosed == true || socket?.isConnected != true) {
                    if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
                        val reconnected = reconnectInternal()
                        if (!reconnected) {
                            throw IllegalStateException("تعذر إعادة الاتصال براوتر ميكروتك عبر منفذ API ($lastPort)")
                        }
                    }
                }
                val out = outStream ?: throw IllegalStateException("Socket is not connected")
                val inS = inStream ?: throw IllegalStateException("Socket is not connected")

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
                lastExecutionSentences = sentences
                return sentences
            } catch (e: Exception) {
                close()
                if (attempts >= 2 || lastHost.isBlank()) {
                    throw e
                }
            }
        }
        return emptyList()
    }

    @Synchronized
    private fun writeSentenceWithoutRetry(words: List<String>) {
        val out = outStream ?: throw IllegalStateException("Socket is not connected")
        for (word in words) {
            writeWord(out, word)
        }
        out.write(0x00) // End of sentence
        out.flush()
    }

    @Synchronized
    private fun readSentenceWithoutRetry(): List<String> {
        val inS = inStream ?: throw IllegalStateException("Socket is not connected")
        val words = mutableListOf<String>()
        while (true) {
            val word = readWord(inS)
            if (word.isEmpty()) break
            words.add(word)
        }
        return words
    }

    private fun writeSentence(words: List<String>): List<List<String>> = synchronized(socketLock) {
        executeSentence(words)
    }

    private fun readSentence(): List<String> = synchronized(socketLock) {
        if (lastExecutionSentences.isNotEmpty()) {
            val s = lastExecutionSentences.first()
            lastExecutionSentences = lastExecutionSentences.drop(1)
            s
        } else {
            emptyList()
        }
    }

    private fun readAllSentencesUntilDone(): List<List<String>> = synchronized(socketLock) {
        val res = lastExecutionSentences
        lastExecutionSentences = emptyList()
        res
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
        if (b1 == -1) return ""
        val len = when {
            (b1 and 0x80) == 0 -> b1
            (b1 and 0xC0) == 0x80 -> {
                val b2 = inStream.read()
                if (b2 == -1) return ""
                ((b1 and 0x3F) shl 8) or b2
            }
            (b1 and 0xE0) == 0xC0 -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                if (b2 == -1 || b3 == -1) return ""
                ((b1 and 0x1F) shl 16) or (b2 shl 8) or b3
            }
            else -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                val b4 = inStream.read()
                if (b2 == -1 || b3 == -1 || b4 == -1) return ""
                ((b1 and 0x0F) shl 24) or (b2 shl 16) or (b3 shl 8) or b4
            }
        }
        if (len == 0) return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val count = inStream.read(buf, read, len - read)
            if (count == -1) break
            read += count
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    suspend fun getRouterUsers(): Result<List<AdminUser>> {
        return withContext(Dispatchers.IO) {
            try {
                writeSentence(listOf("/user/print"))
                val sentences = readAllSentencesUntilDone()
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
                writeSentence(words)
                val sentences = readAllSentencesUntilDone()
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

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inStream = null
        outStream = null
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
