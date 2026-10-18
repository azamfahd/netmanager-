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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import kotlin.random.Random

/**
 * High-Performance, Asynchronous Non-Blocking MikroTik RouterOS API Client
 *
 * Implements native MikroTik binary protocol over TCP port 8728 (or 8729).
 * Features:
 * - Thread-safe, non-blocking execution using Kotlin Mutex & Coroutines (Dispatchers.IO)
 * - Automatic reconnection and auto-retry upon socket drops
 * - Complete hotspot user lifecycle: Add, Single/Batch Generation, Toggle Enable/Disable,
 *   Counters Reset, Editing, and Safe Deletion with active session termination
 * - Hotspot Profile lifecycle: Add, Edit, Delete with exact RouterOS ID resolution
 */
class RouterOsApiClient {

    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private var lastHost: String = ""
    private var lastPort: Int = 8728
    private var lastUsername: String = ""
    private var lastPassword: String = ""

    // Asynchronous non-blocking mutex for serializing socket operations safely across coroutines
    private val socketMutex = Mutex()

    suspend fun ensureConnected(): Boolean {
        return socketMutex.withLock {
            ensureConnectedLocked()
        }
    }

    private suspend fun ensureConnectedLocked(): Boolean {
        if (socket != null && socket?.isClosed == false && socket?.isConnected == true) {
            return true
        }
        if (lastHost.isNotBlank() && lastUsername.isNotBlank()) {
            return connectAndLoginInternal(lastHost, lastPort, lastUsername, lastPassword).isSuccess
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
        return socketMutex.withLock {
            connectAndLoginInternal(host, port, username, password, timeoutMs)
        }
    }

    private fun connectAndLoginInternal(
        host: String,
        port: Int = 8728,
        username: String,
        password: String,
        timeoutMs: Int = 6000
    ): Result<Boolean> {
        try {
            closeInternal()
            val cleanHost = host.trim()
            val cleanUser = username.trim()
            lastHost = cleanHost
            lastPort = port
            lastUsername = cleanUser
            lastPassword = password

            val sock = Socket()
            sock.connect(InetSocketAddress(cleanHost, port), timeoutMs)
            sock.soTimeout = 10000
            sock.tcpNoDelay = true
            socket = sock
            inStream = sock.getInputStream()
            outStream = sock.getOutputStream()

            // Attempt RouterOS v6.43+ and v7 login
            writeSentence(listOf("/login", "=name=$cleanUser", "=password=$password"))
            var response = readSentence()

            if (response.isEmpty()) {
                return Result.failure(Exception("لم يستجب الراوتر على منفذ API ($port)"))
            }

            val replyType = response.firstOrNull() ?: ""

            // Success (RouterOS 6.43+ & 7.x)
            if (replyType == "!done" && response.none { it.startsWith("=ret=") }) {
                return Result.success(true)
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
                    return Result.success(true)
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

            closeInternal()
            return Result.failure(Exception(userFriendlyError))
        } catch (e: Exception) {
            closeInternal()
            return Result.failure(e)
        }
    }

    /**
     * Resolves the real MikroTik internal ID (e.g. *1, *2) for a hotspot card by its username.
     * Guarantees accurate modifications even if card was generated locally.
     */
    private fun findHotspotUserIdByNameLocked(name: String): String? {
        if (name.isBlank()) return null
        return try {
            writeSentence(listOf("/ip/hotspot/user/print", "?name=$name"))
            val sentences = readAllSentencesUntilDone()
            for (s in sentences) {
                if (s.firstOrNull() == "!re") {
                    val map = parseSentenceToMap(s)
                    val id = map[".id"]
                    if (!id.isNullOrBlank()) return id
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves the real MikroTik internal ID for a profile by its name.
     */
    private fun findProfileIdByNameLocked(name: String): String? {
        if (name.isBlank()) return null
        return try {
            writeSentence(listOf("/ip/hotspot/user/profile/print", "?name=$name"))
            val sentences = readAllSentencesUntilDone()
            for (s in sentences) {
                if (s.firstOrNull() == "!re") {
                    val map = parseSentenceToMap(s)
                    val id = map[".id"]
                    if (!id.isNullOrBlank()) return id
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // Core Hotspot User Management
    // ==========================================

    suspend fun toggleCardDisabled(cardId: String, cardName: String = "", currentDisabled: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val targetId = if (cardId.startsWith("*")) cardId else findHotspotUserIdByNameLocked(cardName) ?: cardId
                    val newVal = if (currentDisabled) "no" else "yes"

                    writeSentence(listOf("/ip/hotspot/user/set", "=.id=$targetId", "=disabled=$newVal"))
                    val sentences = readAllSentencesUntilDone()
                    val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trapSentence != null) {
                        val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر تغيير حالة الكرت"
                        return@withContext Result.failure(Exception(trapMsg))
                    }
                    val isDone = sentences.any { it.firstOrNull() == "!done" }
                    if (!isDone) {
                        return@withContext Result.failure(Exception("لم يؤكد الراوتر تغيير حالة الكرت"))
                    }
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun deleteCard(cardId: String, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val targetId = if (cardId.startsWith("*")) cardId else findHotspotUserIdByNameLocked(cardName) ?: cardId

                    writeSentence(listOf("/ip/hotspot/user/remove", "=.id=$targetId"))
                    val sentences = readAllSentencesUntilDone()
                    val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trapSentence != null) {
                        val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر حذف الكرت"
                        return@withContext Result.failure(Exception(trapMsg))
                    }

                    // Also terminate active session if currently online
                    if (cardName.isNotBlank()) {
                        try {
                            writeSentence(listOf("/ip/hotspot/active/print", "?user=$cardName"))
                            val activeSentences = readAllSentencesUntilDone()
                            for (s in activeSentences) {
                                if (s.firstOrNull() == "!re") {
                                    val map = parseSentenceToMap(s)
                                    val activeId = map[".id"]
                                    if (!activeId.isNullOrBlank()) {
                                        writeSentence(listOf("/ip/hotspot/active/remove", "=.id=$activeId"))
                                        readAllSentencesUntilDone()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun updateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val targetId = if (card.id.startsWith("*")) card.id else findHotspotUserIdByNameLocked(card.name) ?: card.id
                    val words = mutableListOf("/ip/hotspot/user/set", "=.id=$targetId", "=name=${card.name}")
                    if (card.password.isNotBlank()) words.add("=password=${card.password}")
                    if (card.profile.isNotBlank()) words.add("=profile=${card.profile}")
                    if (card.limitUptime.isNotBlank()) words.add("=limit-uptime=${card.limitUptime}")
                    if (card.limitBytesTotal > 0) words.add("=limit-bytes-total=${card.limitBytesTotal}")
                    if (card.comment.isNotBlank()) words.add("=comment=${card.comment}")
                    words.add("=disabled=${if (card.disabled) "yes" else "no"}")

                    writeSentence(words)
                    val sentences = readAllSentencesUntilDone()
                    val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trapSentence != null) {
                        val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر تعديل بيانات الكرت"
                        return@withContext Result.failure(Exception(trapMsg))
                    }
                    val isDone = sentences.any { it.firstOrNull() == "!done" }
                    if (!isDone) {
                        return@withContext Result.failure(Exception("لم يؤكد الراوتر تعديل بيانات الكرت"))
                    }
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun reactivateCard(cardId: String, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val targetId = if (cardId.startsWith("*")) cardId else findHotspotUserIdByNameLocked(cardName) ?: cardId

                    // 1. Reset counters in RouterOS
                    writeSentence(listOf("/ip/hotspot/user/reset-counters", "=.id=$targetId"))
                    readAllSentencesUntilDone()

                    // 2. Enable card
                    writeSentence(listOf("/ip/hotspot/user/set", "=.id=$targetId", "=disabled=no"))
                    val sentences = readAllSentencesUntilDone()
                    val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trapSentence != null) {
                        val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر تفعيل الكرت"
                        return@withContext Result.failure(Exception(trapMsg))
                    }

                    // 3. Remove from active if logged in
                    if (cardName.isNotBlank()) {
                        try {
                            writeSentence(listOf("/ip/hotspot/active/print", "?user=$cardName"))
                            val activeSentences = readAllSentencesUntilDone()
                            for (s in activeSentences) {
                                if (s.firstOrNull() == "!re") {
                                    val map = parseSentenceToMap(s)
                                    val actId = map[".id"]
                                    if (!actId.isNullOrBlank()) {
                                        writeSentence(listOf("/ip/hotspot/active/remove", "=.id=$actId"))
                                        readAllSentencesUntilDone()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun addCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val words = mutableListOf("/ip/hotspot/user/add", "=name=${card.name}")
                    if (card.password.isNotBlank()) words.add("=password=${card.password}")
                    if (card.profile.isNotBlank()) words.add("=profile=${card.profile}")
                    if (card.limitUptime.isNotBlank()) words.add("=limit-uptime=${card.limitUptime}")
                    if (card.limitBytesTotal > 0) words.add("=limit-bytes-total=${card.limitBytesTotal}")
                    if (card.comment.isNotBlank()) words.add("=comment=${card.comment}")
                    if (card.disabled) words.add("=disabled=yes")

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
    }

    // ==========================================
    // Core Hotspot Profile Management
    // ==========================================

    suspend fun addProfile(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val words = mutableListOf("/ip/hotspot/user/profile/add", "=name=${profile.name}")
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
    }

    suspend fun updateProfile(profile: HotspotProfile): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val targetId = if (profile.id.startsWith("*")) profile.id else findProfileIdByNameLocked(profile.name) ?: profile.id
                    val words = mutableListOf("/ip/hotspot/user/profile/set", "=.id=$targetId")
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
                    val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trapSentence != null) {
                        val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر تعديل الباقة"
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
    }

    suspend fun deleteProfile(profileId: String, profileName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val targetId = if (profileId.startsWith("*")) profileId else findProfileIdByNameLocked(profileName) ?: profileId
                    writeSentence(listOf("/ip/hotspot/user/profile/remove", "=.id=$targetId"))
                    val sentences = readAllSentencesUntilDone()
                    val trapSentence = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trapSentence != null) {
                        val trapMsg = trapSentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر حذف الباقة"
                        return@withContext Result.failure(Exception(trapMsg))
                    }
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // ==========================================
    // Telemetry & Data Fetching Operations
    // ==========================================

    suspend fun getSystemResource(): Result<RouterResource> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
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
                        Result.failure(Exception("لم يتم استلام بيانات الموارد"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getActiveHotspotUsers(): Result<List<HotspotActiveUser>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/hotspot/active/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = mutableListOf<HotspotActiveUser>()
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val uptimeStr = map["uptime"] ?: "0s"
                            val uptimeSec = parseUptimeToSeconds(uptimeStr)
                            val bIn = map["bytes-in"]?.toLongOrNull() ?: 0L
                            val bOut = map["bytes-out"]?.toLongOrNull() ?: 0L
                            val speedKbps = if (uptimeSec > 0) ((bIn + bOut) * 8.0 / uptimeSec / 1024.0) else 0.0

                            list.add(
                                HotspotActiveUser(
                                    id = map[".id"] ?: "act_${list.size}",
                                    serverName = map["server"] ?: "hotspot1",
                                    user = map["user"] ?: "مستخدم",
                                    address = map["address"] ?: "0.0.0.0",
                                    macAddress = map["mac-address"] ?: "",
                                    uptime = uptimeStr,
                                    bytesIn = bIn,
                                    bytesOut = bOut,
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
    }

    suspend fun getHotspotUsers(): Result<List<HotspotCard>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
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
    }

    suspend fun getHotspotProfiles(): Result<List<HotspotProfile>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val list = mutableListOf<HotspotProfile>()

                    // 1. Fetch Hotspot User Profiles (/ip/hotspot/user/profile)
                    writeSentence(listOf("/ip/hotspot/user/profile/print"))
                    val sentences = readAllSentencesUntilDone()
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: "default"
                            val shared = map["shared-users"]?.toIntOrNull() ?: 1
                            val rateLimit = map["rate-limit"] ?: "غير محدد"
                            val comment = map["comment"] ?: ""
                            val price = extractPriceFromText(comment, name, "")
                            val (rxSpeed, txSpeed) = parseRateLimitSpeeds(rateLimit)

                            list.add(
                                HotspotProfile(
                                    id = map[".id"] ?: "p_${list.size}",
                                    name = name,
                                    sharedUsers = shared,
                                    rateLimit = rateLimit,
                                    sessionTimeout = map["session-timeout"] ?: "غير محدد",
                                    price = price,
                                    dataLimit = extractDataLimitFromText(comment),
                                    downloadLimit = rxSpeed,
                                    uploadLimit = txSpeed,
                                    comment = comment
                                )
                            )
                        }
                    }

                    // 2. Fetch User-Manager Profiles if available (/user-manager/profile)
                    try {
                        writeSentence(listOf("/user-manager/profile/print"))
                        val umSentences = readAllSentencesUntilDone()
                        for (s in umSentences) {
                            if (s.firstOrNull() == "!re") {
                                val map = parseSentenceToMap(s)
                                val name = map["name"] ?: continue
                                if (list.none { it.name.equals(name, ignoreCase = true) }) {
                                    val price = map["price"]?.let { "$it ر.ي" } ?: extractPriceFromText(map["comment"] ?: "", name, "")
                                    list.add(
                                        HotspotProfile(
                                            id = map[".id"] ?: "um_p_${list.size}",
                                            name = name,
                                            sharedUsers = 1,
                                            rateLimit = map["rate-limit"] ?: "غير محدد",
                                            sessionTimeout = map["validity"] ?: "غير محدد",
                                            price = price
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getDhcpLeases(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/dhcp-server/lease/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getHotspotHosts(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/hotspot/host/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getWirelessRegistrations(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/interface/wireless/registration-table/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getArpEntries(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/arp/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getBridgeHosts(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/interface/bridge/host/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getIpBindings(): Result<List<IpBinding>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/hotspot/ip-binding/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = mutableListOf<IpBinding>()
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            list.add(
                                IpBinding(
                                    id = map[".id"] ?: "bind_${list.size}",
                                    macAddress = map["mac-address"] ?: "",
                                    address = map["address"] ?: "",
                                    type = map["type"] ?: "bypassed",
                                    comment = map["comment"] ?: "",
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
    }

    suspend fun kickUser(userId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/hotspot/active/remove", "=.id=$userId"))
                    val sentences = readAllSentencesUntilDone()
                    val isDone = sentences.any { it.firstOrNull() == "!done" }
                    Result.success(isDone)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun rebootRouter(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/system/reboot"))
                    readAllSentencesUntilDone()
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getSystemIdentity(): Result<String> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/system/identity/print"))
                    val sentences = readAllSentencesUntilDone()
                    val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                    val name = re?.let { parseSentenceToMap(it)["name"] } ?: "MikroTik"
                    Result.success(name)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getNetworkInterfaces(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/interface/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getWirelessInterfaces(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/interface/wireless/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getRouterBoardInfo(): Result<RouterBoardInfo> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/system/routerboard/print"))
                    val sentences = readAllSentencesUntilDone()
                    val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                    if (re != null) {
                        val map = parseSentenceToMap(re)
                        Result.success(
                            RouterBoardInfo(
                                routerboard = map["routerboard"] == "yes" || map["routerboard"] == "true",
                                model = map["model"] ?: "MikroTik Router",
                                serialNumber = map["serial-number"] ?: "",
                                currentFirmware = map["current-firmware"] ?: "",
                                upgradeFirmware = map["upgrade-firmware"] ?: ""
                            )
                        )
                    } else {
                        Result.failure(Exception("لم يتم استلام معلومات لوحة الراوتر"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getSystemHealth(): Result<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/system/health/print"))
                    val sentences = readAllSentencesUntilDone()
                    val re = sentences.firstOrNull { it.firstOrNull() == "!re" }
                    val map = if (re != null) parseSentenceToMap(re) else emptyMap()
                    Result.success(map)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getSystemLogs(limit: Int = 30): Result<List<SystemLogEntry>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
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
    }

    suspend fun getHotspotServers(): Result<List<HotspotServerInfo>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
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
                                    interfaceName = map["interface"] ?: "bridge",
                                    profile = map["profile"] ?: "default",
                                    addressPool = map["address-pool"] ?: "none",
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
    }

    suspend fun getIpPools(): Result<List<IpPoolInfo>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/pool/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = mutableListOf<IpPoolInfo>()
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            list.add(
                                IpPoolInfo(
                                    id = map[".id"] ?: "pool_${list.size}",
                                    name = map["name"] ?: "dhcp_pool",
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
    }

    suspend fun getUserManagerUsers(): Result<List<HotspotCard>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val list = mutableListOf<HotspotCard>()
                    try {
                        writeSentence(listOf("/user-manager/user/print"))
                        val sentences = readAllSentencesUntilDone()
                        for (s in sentences) {
                            if (s.firstOrNull() == "!re") {
                                val map = parseSentenceToMap(s)
                                val name = map["name"] ?: map["username"] ?: ""
                                if (name.isNotBlank()) {
                                    val profile = map["actual-profile"] ?: map["profile"] ?: "default"
                                    val comment = map["comment"] ?: ""
                                    list.add(
                                        HotspotCard(
                                            id = map[".id"] ?: "um_${list.size}",
                                            name = name,
                                            password = map["password"] ?: "",
                                            profile = profile,
                                            uptime = map["uptime"] ?: "0s",
                                            bytesIn = map["download-used"]?.toLongOrNull() ?: 0L,
                                            bytesOut = map["upload-used"]?.toLongOrNull() ?: 0L,
                                            limitUptime = map["till-time"] ?: "",
                                            limitBytesTotal = map["download-limit"]?.toLongOrNull() ?: 0L,
                                            comment = comment.ifBlank { "User-Manager" },
                                            disabled = map["disabled"] == "true" || map["disabled"] == "yes",
                                            price = extractPriceFromText(comment, profile, name)
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getDiscoveredNeighbors(): Result<List<Map<String, String>>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/ip/neighbor/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = sentences.filter { it.firstOrNull() == "!re" }.map { parseSentenceToMap(it) }
                    Result.success(list)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getRouterUsers(): Result<List<AdminUser>> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    writeSentence(listOf("/user/print"))
                    val sentences = readAllSentencesUntilDone()
                    val list = mutableListOf<AdminUser>()
                    for (s in sentences) {
                        if (s.firstOrNull() == "!re") {
                            val map = parseSentenceToMap(s)
                            val name = map["name"] ?: ""
                            val group = map["group"] ?: "read"
                            val disabled = map["disabled"] == "true" || map["disabled"] == "yes"
                            val comment = map["comment"] ?: ""
                            if (name.isNotBlank()) {
                                val roleStr = when (group.lowercase()) {
                                    "full" -> "مدير عام (Full Admin)"
                                    "write" -> "مشرف كروت"
                                    else -> "مراقب"
                                }
                                val perms = when (group.lowercase()) {
                                    "full" -> listOf("full", "read", "write", "policy", "test")
                                    "write" -> listOf("read", "write", "policy")
                                    else -> listOf("read")
                                }
                                list.add(
                                    AdminUser(
                                        id = map[".id"] ?: "u_${list.size}",
                                        username = name,
                                        fullName = comment.ifBlank { name },
                                        role = roleStr,
                                        permissions = perms,
                                        lastActiveTime = map["last-logged-in"] ?: "غير محدد",
                                        ipAddress = "",
                                        isActive = !disabled
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
    }

    suspend fun addRouterUser(username: String, password: String, group: String, comment: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            socketMutex.withLock {
                try {
                    ensureConnectedLocked()
                    val words = mutableListOf("/user/add", "=name=$username", "=group=$group")
                    if (password.isNotBlank()) words.add("=password=$password")
                    if (comment.isNotBlank()) words.add("=comment=$comment")

                    writeSentence(words)
                    val sentences = readAllSentencesUntilDone()
                    val trap = sentences.firstOrNull { it.firstOrNull() == "!trap" }
                    if (trap != null) {
                        val msg = trap.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                            ?: "رفض الراوتر إضافة المستخدم"
                        return@withContext Result.failure(Exception(msg))
                    }
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // ==========================================
    // Low-Level Protocol Parsing & Socket I/O
    // ==========================================

    private fun parseSentenceToMap(sentence: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (word in sentence) {
            if (word.startsWith("=")) {
                val eqIdx = word.indexOf('=', 1)
                if (eqIdx > 1) {
                    val key = word.substring(1, eqIdx)
                    val value = word.substring(eqIdx + 1)
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun writeSentence(words: List<String>) {
        val out = outStream ?: throw IllegalStateException("Socket is not connected")
        for (word in words) {
            writeWord(out, word)
        }
        out.write(0x00) // End of sentence
        out.flush()
    }

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
        val firstByte = inStream.read()
        if (firstByte == -1 || firstByte == 0x00) {
            return ""
        }

        val len: Int = when {
            (firstByte and 0x80) == 0 -> firstByte
            (firstByte and 0xC0) == 0x80 -> {
                val b2 = inStream.read()
                ((firstByte and 0x3F) shl 8) or b2
            }
            (firstByte and 0xE0) == 0xC0 -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                ((firstByte and 0x1F) shl 16) or (b2 shl 8) or b3
            }
            (firstByte and 0xF0) == 0xE0 -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                val b4 = inStream.read()
                ((firstByte and 0x0F) shl 24) or (b2 shl 16) or (b3 shl 8) or b4
            }
            else -> {
                val b2 = inStream.read()
                val b3 = inStream.read()
                val b4 = inStream.read()
                val b5 = inStream.read()
                (b2 shl 24) or (b3 shl 16) or (b4 shl 8) or b5
            }
        }

        val buffer = ByteArray(len)
        var offset = 0
        while (offset < len) {
            val count = inStream.read(buffer, offset, len - offset)
            if (count == -1) break
            offset += count
        }
        return String(buffer, 0, offset, Charsets.UTF_8)
    }

    fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
    }

    private fun extractPriceFromText(comment: String, profileName: String, cardName: String): String {
        val priceRegex = Regex("""(\d+)\s*(ر\.ي|ريال|YER|YR)""", RegexOption.IGNORE_CASE)
        val match = priceRegex.find(comment) ?: priceRegex.find(profileName) ?: priceRegex.find(cardName)
        if (match != null) return match.value

        val numRegex = Regex("""(?:\b|_)(\d{2,5})(?:\b|_)""")
        val numMatch = numRegex.find(profileName) ?: numRegex.find(comment)
        if (numMatch != null) {
            val value = numMatch.groupValues[1]
            if (value in listOf("100", "200", "250", "300", "500", "1000", "1500", "2000", "3000", "5000")) {
                return "$value ر.ي"
            }
        }
        return "500 ر.ي"
    }

    private fun extractDataLimitFromText(comment: String): String {
        val mbRegex = Regex("""(\d+)\s*(MB|GB|ميجابايت|جيجابايت)""", RegexOption.IGNORE_CASE)
        val match = mbRegex.find(comment)
        return match?.value ?: "غير محدد"
    }

    private fun parseRateLimitSpeeds(rateLimit: String): Pair<String, String> {
        val parts = rateLimit.split("/")
        val rx = parts.getOrNull(0)?.trim()?.ifBlank { "غير محدد" } ?: "غير محدد"
        val tx = parts.getOrNull(1)?.trim()?.ifBlank { "غير محدد" } ?: "غير محدد"
        return rx to tx
    }

    private fun parseUptimeToSeconds(uptime: String): Long {
        var total = 0L
        val weekMatch = Regex("""(\d+)w""").find(uptime)
        val dayMatch = Regex("""(\d+)d""").find(uptime)
        val hourMatch = Regex("""(\d+)h""").find(uptime)
        val minMatch = Regex("""(\d+)m""").find(uptime)
        val secMatch = Regex("""(\d+)s""").find(uptime)

        weekMatch?.let { total += it.groupValues[1].toLong() * 7 * 24 * 3600 }
        dayMatch?.let { total += it.groupValues[1].toLong() * 24 * 3600 }
        hourMatch?.let { total += it.groupValues[1].toLong() * 3600 }
        minMatch?.let { total += it.groupValues[1].toLong() * 60 }
        secMatch?.let { total += it.groupValues[1].toLong() }
        return total
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
        val sb = java.lang.StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
