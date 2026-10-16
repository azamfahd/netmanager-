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

    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private var lastHost: String = ""
    private var lastPort: Int = 8728
    private var lastUsername: String = ""
    private var lastPassword: String = ""

    suspend fun ensureConnected(): Boolean {
        if (socket != null && socket?.isClosed == false && socket?.isConnected == true) {
            return true
        }
        if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
            return connectAndLogin(lastHost, lastPort, lastUsername, lastPassword).isSuccess
        }
        return false
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
                ensureConnected()
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
                                price = parsedPrice
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
                                    dataLimit = extractDataLimitFromText(comment),
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
                            if (list.any { it.name.equals(name, ignoreCase = true) }) continue

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
                                    source = "usermanager",
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
                            if (list.any { it.name.equals(name, ignoreCase = true) }) continue
                            val rawPrice = map["price"]?.takeIf { it.isNotBlank() && it != "0" }
                            val priceStr = if (rawPrice != null) "$rawPrice ر.ي" else extractPriceFromText(map["comment"].orEmpty(), name, "")
                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "tum_${list.size}",
                                    name = name,
                                    rateLimit = map["rate-limit"] ?: "مفتوح",
                                    sharedUsers = 1,
                                    sessionTimeout = map["validity"] ?: "1d",
                                    price = priceStr,
                                    source = "usermanager",
                                    comment = map["comment"].orEmpty()
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}

                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProfile(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val words = mutableListOf("/ip/hotspot/user/profile/set", "=.id=${profile.id}")
                if (profile.rateLimit.isNotBlank() && profile.rateLimit != "غير محدد") {
                    words.add("=rate-limit=${profile.rateLimit}")
                }
                words.add("=shared-users=${profile.sharedUsers}")
                if (profile.sessionTimeout.isNotBlank() && profile.sessionTimeout != "غير محدد") {
                    words.add("=session-timeout=${profile.sessionTimeout}")
                }
                if (profile.price.isNotBlank()) {
                    words.add("=comment=price=${profile.price}")
                }
                writeSentence(words)
                val sentences = readAllSentencesUntilDone()
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                Result.success(isDone)
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

    private fun extractDataLimitFromText(comment: String): String {
        if (comment.isBlank()) return ""
        val match = Regex("""(?i)(\d+(?:\.\d+)?)\s*(GB|MB|GI|MI|G|M)""").find(comment)
        return match?.value?.uppercase() ?: ""
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

    suspend fun deleteCard(cardId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                writeSentence(listOf("/ip/hotspot/user/remove", "=.id=$cardId"))
                val sentences = readAllSentencesUntilDone()
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                Result.success(isDone)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleCardDisabled(cardId: String, currentDisabled: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val newVal = if (currentDisabled) "no" else "yes"
                writeSentence(listOf("/ip/hotspot/user/set", "=.id=$cardId", "=disabled=$newVal"))
                val sentences = readAllSentencesUntilDone()
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                Result.success(isDone)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val words = mutableListOf("/ip/hotspot/user/add", "=name=${card.name}", "=password=${card.password}")
                if (card.profile.isNotBlank()) words.add("=profile=${card.profile}")
                if (card.limitUptime.isNotBlank()) words.add("=limit-uptime=${card.limitUptime}")
                if (card.limitBytesTotal > 0) words.add("=limit-bytes-total=${card.limitBytesTotal}")
                if (card.comment.isNotBlank()) words.add("=comment=${card.comment}")

                writeSentence(words)
                val sentences = readAllSentencesUntilDone()
                val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trapSentence != null) {
                    val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "رفض راوتر ميكروتك إضافة الكرت"
                    return@withContext Result.failure(Exception(trapMsg))
                }
                val isDone = sentences.any { it.firstOrNull() == "!done" }
                if (!isDone) {
                    return@withContext Result.failure(Exception("لم يؤكد الراوتر إضافة الكرت"))
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addProfile(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val words = mutableListOf("/ip/hotspot/user/profile/add", "=name=${profile.name}")
                if (profile.rateLimit.isNotBlank() && profile.rateLimit != "غير محدد") {
                    words.add("=rate-limit=${profile.rateLimit}")
                }
                words.add("=shared-users=${profile.sharedUsers}")
                if (profile.sessionTimeout.isNotBlank() && profile.sessionTimeout != "غير محدد") {
                    words.add("=session-timeout=${profile.sessionTimeout}")
                }

                writeSentence(words)
                val sentences = readAllSentencesUntilDone()
                val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                if (trapSentence != null) {
                    val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "رفض راوتر ميكروتك إنشاء الباقة"
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
                                    price = price
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

    @Synchronized
    private fun writeSentence(words: List<String>) {
        val out = outStream ?: throw IllegalStateException("Socket is not connected")
        for (word in words) {
            writeWord(out, word)
        }
        out.write(0x00) // End of sentence
        out.flush()
    }

    @Synchronized
    private fun readSentence(): List<String> {
        val inS = inStream ?: throw IllegalStateException("Socket is not connected")
        val words = mutableListOf<String>()
        while (true) {
            val word = readWord(inS)
            if (word.isEmpty()) break
            words.add(word)
        }
        return words
    }

    @Synchronized
    private fun readAllSentencesUntilDone(): List<List<String>> {
        val sentences = mutableListOf<List<String>>()
        while (true) {
            val sentence = readSentence()
            if (sentence.isEmpty()) break
            sentences.add(sentence)
            val first = sentence.firstOrNull() ?: ""
            if (first == "!done" || first == "!fatal") {
                break
            }
        }
        return sentences
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
