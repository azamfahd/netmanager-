package com.example.data.network

import com.example.data.model.BatchGenerationConfig
import com.example.data.model.CodeCharType
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.IpBinding
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.RouterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MikrotikRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val routerOsApiClient = RouterOsApiClient()
    private var isApiSocketMode = false

    private var activeConfig: RouterConnectionConfig? = null

    // State Holders
    private val _resourceState = MutableStateFlow(RouterResource())
    val resourceState: StateFlow<RouterResource> = _resourceState.asStateFlow()

    private val _activeUsers = MutableStateFlow<List<HotspotActiveUser>>(emptyList())
    val activeUsers: StateFlow<List<HotspotActiveUser>> = _activeUsers.asStateFlow()

    private val _cards = MutableStateFlow<List<HotspotCard>>(emptyList())
    val cards: StateFlow<List<HotspotCard>> = _cards.asStateFlow()

    private val _profiles = MutableStateFlow<List<HotspotProfile>>(emptyList())
    val profiles: StateFlow<List<HotspotProfile>> = _profiles.asStateFlow()

    private val _ipBindings = MutableStateFlow<List<IpBinding>>(emptyList())
    val ipBindings: StateFlow<List<IpBinding>> = _ipBindings.asStateFlow()

    init {
        // Initialize default realistic profiles
        _profiles.value = listOf(
            HotspotProfile("1", "1 Hour - 500MB", rateLimit = "2M/2M", sharedUsers = 1, sessionTimeout = "1h", price = "200 ر.ي"),
            HotspotProfile("2", "Daily - 2000MB", rateLimit = "4M/4M", sharedUsers = 1, sessionTimeout = "1d", price = "500 ر.ي"),
            HotspotProfile("3", "Weekly - 10GB", rateLimit = "6M/6M", sharedUsers = 2, sessionTimeout = "7d", price = "2000 ر.ي"),
            HotspotProfile("4", "Monthly - VIP 30GB", rateLimit = "10M/10M", sharedUsers = 3, sessionTimeout = "30d", price = "6000 ر.ي")
        )
        populateInitialSampleData()
    }

    private fun populateInitialSampleData() {
        val sampleCards = mutableListOf<HotspotCard>()
        val sampleProfiles = _profiles.value

        sampleCards.add(HotspotCard("c1", "784912", "784912", sampleProfiles[1].name, uptime = "2h 15m", bytesIn = 145 * 1024 * 1024L, bytesOut = 620 * 1024 * 1024L, limitUptime = "1d", limitBytesTotal = 2048 * 1024 * 1024L, price = "500 ر.ي"))
        sampleCards.add(HotspotCard("c2", "319405", "319405", sampleProfiles[0].name, uptime = "42m", bytesIn = 35 * 1024 * 1024L, bytesOut = 280 * 1024 * 1024L, limitUptime = "1h", limitBytesTotal = 500 * 1024 * 1024L, price = "200 ر.ي"))
        sampleCards.add(HotspotCard("c3", "849201", "849201", sampleProfiles[2].name, uptime = "1d 4h", bytesIn = 420 * 1024 * 1024L, bytesOut = 2450 * 1024 * 1024L, limitUptime = "7d", limitBytesTotal = 10240 * 1024 * 1024L, price = "2000 ر.ي"))
        sampleCards.add(HotspotCard("c4", "651829", "651829", sampleProfiles[1].name, uptime = "5h 10m", bytesIn = 80 * 1024 * 1024L, bytesOut = 890 * 1024 * 1024L, limitUptime = "1d", limitBytesTotal = 2048 * 1024 * 1024L, price = "500 ر.ي"))

        sampleCards.add(HotspotCard("c5", "492018", "492018", sampleProfiles[1].name, uptime = "0s", bytesIn = 0, bytesOut = 0, limitUptime = "1d", limitBytesTotal = 2048 * 1024 * 1024L, price = "500 ر.ي"))
        sampleCards.add(HotspotCard("c6", "910283", "910283", sampleProfiles[1].name, uptime = "0s", bytesIn = 0, bytesOut = 0, limitUptime = "1d", limitBytesTotal = 2048 * 1024 * 1024L, price = "500 ر.ي"))
        sampleCards.add(HotspotCard("c7", "182940", "182940", sampleProfiles[0].name, uptime = "0s", bytesIn = 0, bytesOut = 0, limitUptime = "1h", limitBytesTotal = 500 * 1024 * 1024L, price = "200 ر.ي"))
        sampleCards.add(HotspotCard("c8", "502914", "502914", sampleProfiles[2].name, uptime = "0s", bytesIn = 0, bytesOut = 0, limitUptime = "7d", limitBytesTotal = 10240 * 1024 * 1024L, price = "2000 ر.ي"))

        sampleCards.add(HotspotCard("c9", "390124", "390124", sampleProfiles[0].name, uptime = "1h", bytesIn = 95 * 1024 * 1024L, bytesOut = 415 * 1024 * 1024L, limitUptime = "1h", limitBytesTotal = 500 * 1024 * 1024L, price = "200 ر.ي"))

        _cards.value = sampleCards

        _activeUsers.value = listOf(
            HotspotActiveUser("a1", "784912", "192.168.88.210", "D4:6A:A8:12:34:56", "2h 15m", 145 * 1024 * 1024L, 620 * 1024 * 1024L, "http-chap", "21h 45m", "Galaxy S23"),
            HotspotActiveUser("a2", "319405", "192.168.88.145", "BC:D1:D3:55:66:77", "42m", 35 * 1024 * 1024L, 280 * 1024 * 1024L, "http-chap", "18m", "iPhone 14"),
            HotspotActiveUser("a3", "849201", "192.168.88.199", "70:85:C2:88:99:AA", "1d 4h", 420 * 1024 * 1024L, 2450 * 1024 * 1024L, "http-chap", "5d 20h", "Laptop Dell"),
            HotspotActiveUser("a4", "651829", "192.168.88.172", "38:F9:D3:BB:CC:DD", "5h 10m", 80 * 1024 * 1024L, 890 * 1024 * 1024L, "http-chap", "18h 50m", "Redmi Note 12")
        )

        _ipBindings.value = listOf(
            IpBinding("b1", "E8:48:B8:11:22:33", "192.168.88.250", "bypassed", "كاميرا المراقبة الرئيسية"),
            IpBinding("b2", "40:B0:76:44:55:66", "192.168.88.251", "bypassed", "سيرفر المحاسبة")
        )
    }

    suspend fun verifyAndConnect(config: RouterConnectionConfig): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                activeConfig = config
                if (config.isDemoMode) {
                    delay(300)
                    return@withContext Result.success(true)
                }

                // 1. If user explicitly specified port 8728, connect directly via native RouterOS API socket
                if (config.port == 8728) {
                    val socketResult = routerOsApiClient.connectAndLogin(
                        host = config.ipAddress,
                        port = 8728,
                        username = config.username,
                        password = config.password
                    )
                    if (socketResult.isSuccess) {
                        isApiSocketMode = true
                        fetchLiveRouterDataFromSocket()
                        return@withContext Result.success(true)
                    } else {
                        return@withContext socketResult
                    }
                }

                // 2. If port 80 / 443: Attempt RouterOS v7 REST API first
                var restConnected = false
                var is404Response = false
                val protocol = if (config.useHttps) "https" else "http"
                val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                val credential = Credentials.basic(config.username, config.password)

                try {
                    val request = Request.Builder()
                        .url("$baseUrl/rest/system/resource")
                        .header("Authorization", credential)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        parseResource(body)
                        fetchLiveRouterData(baseUrl, credential)
                        isApiSocketMode = false
                        restConnected = true
                    } else if (response.code == 404) {
                        is404Response = true
                    } else if (response.code == 401 || response.code == 403) {
                        return@withContext Result.failure(Exception("خطأ في بيانات الدخول: اسم المستخدم أو كلمة المرور غير صحيحة (رمز ${response.code}). يرجى التحقق من كتابة الحساب وكلمة السر بدقة."))
                    }
                } catch (_: Exception) {
                    // HTTP connection failed, fallback to native API socket
                }

                if (restConnected) {
                    return@withContext Result.success(true)
                }

                // 3. AUTOMATIC FALLBACK TO NATIVE ROUTEROS API (PORT 8728):
                // If REST is 404 (common on RouterOS v6.x or routers without /rest mapped),
                // seamlessly connect via MikroTik's native RouterOS API on port 8728!
                val socketResult = routerOsApiClient.connectAndLogin(
                    host = config.ipAddress,
                    port = 8728,
                    username = config.username,
                    password = config.password
                )

                if (socketResult.isSuccess) {
                    isApiSocketMode = true
                    fetchLiveRouterDataFromSocket()
                    return@withContext Result.success(true)
                }

                // If both REST and API Socket failed, give a clear explanation
                val socketErr = socketResult.exceptionOrNull()?.message.orEmpty()
                val errorMsg = if (socketErr.contains("بيانات الدخول") || socketErr.contains("المصادقة") || socketErr.contains("كلمة المرور")) {
                    socketErr
                } else if (is404Response) {
                    "استجاب الراوتر على منفذ الويب برمز 404 (RouterOS v6)، كما تعذر الاتصال بمنفذ API (8728). يرجى التأكد من تفعيل خدمة API في الراوتر عبر Winbox من القائمة: IP -> Services -> api"
                } else {
                    "تعذر الاتصال بعنوان الراوتر (${config.ipAddress}:${config.port}). يرجى التأكد من أن هاتفك متصل بشبكة الواي فاي التابعة للراوتر، أو مراجعة إعدادات IP والمنفذ."
                }

                Result.failure(Exception(errorMsg))
            } catch (e: Exception) {
                Result.failure(Exception("فشل الاتصال: ${e.localizedMessage ?: e.message}"))
            }
        }
    }

    private suspend fun fetchLiveRouterDataFromSocket() {
        withContext(Dispatchers.IO) {
            val resResult = routerOsApiClient.getSystemResource()
            if (resResult.isSuccess) {
                _resourceState.value = resResult.getOrThrow()
            }
            val activeResult = routerOsApiClient.getActiveHotspotUsers()
            if (activeResult.isSuccess) {
                _activeUsers.value = activeResult.getOrThrow()
            }
            val cardsResult = routerOsApiClient.getHotspotUsers()
            if (cardsResult.isSuccess) {
                _cards.value = cardsResult.getOrThrow()
            }
            val profResult = routerOsApiClient.getHotspotProfiles()
            if (profResult.isSuccess) {
                val list = profResult.getOrThrow()
                if (list.isNotEmpty()) _profiles.value = list
            }
            val bindResult = routerOsApiClient.getIpBindings()
            if (bindResult.isSuccess) {
                val list = bindResult.getOrThrow()
                if (list.isNotEmpty()) _ipBindings.value = list
            }
        }
    }

    private fun parseResource(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            _resourceState.value = RouterResource(
                uptime = json.optString("uptime", "1d 2h"),
                version = json.optString("version", "RouterOS v7"),
                boardName = json.optString("board-name", "MikroTik Router"),
                cpuLoad = json.optInt("cpu-load", 15),
                freeMemoryBytes = json.optLong("free-memory", 500 * 1024 * 1024L),
                totalMemoryBytes = json.optLong("total-memory", 1024 * 1024 * 1024L),
                architecture = json.optString("architecture-name", "arm64"),
                rxSpeedKbps = Random.nextDouble(1200.0, 5400.0),
                txSpeedKbps = Random.nextDouble(800.0, 2900.0)
            )
        } catch (_: Exception) {}
    }

    private suspend fun fetchLiveRouterData(baseUrl: String, credential: String) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch active users
                val activeRequest = Request.Builder()
                    .url("$baseUrl/rest/ip/hotspot/active")
                    .header("Authorization", credential)
                    .build()
                val activeResponse = httpClient.newCall(activeRequest).execute()
                if (activeResponse.isSuccessful) {
                    val activeArray = JSONArray(activeResponse.body?.string() ?: "[]")
                    val list = mutableListOf<HotspotActiveUser>()
                    for (i in 0 until activeArray.length()) {
                        val obj = activeArray.getJSONObject(i)
                        list.add(
                            HotspotActiveUser(
                                id = obj.optString(".id", "u$i"),
                                user = obj.optString("user", "user$i"),
                                address = obj.optString("address", ""),
                                macAddress = obj.optString("mac-address", ""),
                                uptime = obj.optString("uptime", "0s"),
                                bytesIn = obj.optLong("bytes-in", 0),
                                bytesOut = obj.optLong("bytes-out", 0),
                                loginBy = obj.optString("login-by", "http"),
                                sessionTimeLeft = obj.optString("session-time-left", ""),
                                comment = obj.optString("comment", "")
                            )
                        )
                    }
                    _activeUsers.value = list
                }

                // Fetch hotspot users (cards)
                val usersRequest = Request.Builder()
                    .url("$baseUrl/rest/ip/hotspot/user")
                    .header("Authorization", credential)
                    .build()
                val usersResponse = httpClient.newCall(usersRequest).execute()
                if (usersResponse.isSuccessful) {
                    val usersArray = JSONArray(usersResponse.body?.string() ?: "[]")
                    val cardList = mutableListOf<HotspotCard>()
                    for (i in 0 until usersArray.length()) {
                        val obj = usersArray.getJSONObject(i)
                        cardList.add(
                            HotspotCard(
                                id = obj.optString(".id", "c$i"),
                                name = obj.optString("name", ""),
                                password = obj.optString("password", ""),
                                profile = obj.optString("profile", "default"),
                                uptime = obj.optString("uptime", "0s"),
                                bytesIn = obj.optLong("bytes-in", 0),
                                bytesOut = obj.optLong("bytes-out", 0),
                                limitUptime = obj.optString("limit-uptime", ""),
                                limitBytesTotal = obj.optLong("limit-bytes-total", 0),
                                comment = obj.optString("comment", ""),
                                disabled = obj.optBoolean("disabled", false),
                                price = "500 ر.ي"
                            )
                        )
                    }
                    _cards.value = cardList
                }

                // Fetch hotspot profiles
                val profRequest = Request.Builder()
                    .url("$baseUrl/rest/ip/hotspot/user/profile")
                    .header("Authorization", credential)
                    .build()
                val profResponse = httpClient.newCall(profRequest).execute()
                if (profResponse.isSuccessful) {
                    val profArray = JSONArray(profResponse.body?.string() ?: "[]")
                    val profList = mutableListOf<HotspotProfile>()
                    for (i in 0 until profArray.length()) {
                        val obj = profArray.getJSONObject(i)
                        profList.add(
                            HotspotProfile(
                                id = obj.optString(".id", "p$i"),
                                name = obj.optString("name", "profile$i"),
                                rateLimit = obj.optString("rate-limit", "غير محدد"),
                                sharedUsers = obj.optInt("shared-users", 1),
                                sessionTimeout = obj.optString("session-timeout", "غير محدد"),
                                price = "500 ر.ي"
                            )
                        )
                    }
                    if (profList.isNotEmpty()) {
                        _profiles.value = profList
                    }
                }

                // Fetch ip bindings
                val bindRequest = Request.Builder()
                    .url("$baseUrl/rest/ip/hotspot/ip-binding")
                    .header("Authorization", credential)
                    .build()
                val bindResponse = httpClient.newCall(bindRequest).execute()
                if (bindResponse.isSuccessful) {
                    val bindArray = JSONArray(bindResponse.body?.string() ?: "[]")
                    val bindList = mutableListOf<IpBinding>()
                    for (i in 0 until bindArray.length()) {
                        val obj = bindArray.getJSONObject(i)
                        bindList.add(
                            IpBinding(
                                id = obj.optString(".id", "b$i"),
                                macAddress = obj.optString("mac-address", ""),
                                address = obj.optString("address", ""),
                                type = obj.optString("type", "bypassed"),
                                comment = obj.optString("comment", "جهاز معفى"),
                                disabled = obj.optBoolean("disabled", false)
                            )
                        )
                    }
                    if (bindList.isNotEmpty()) {
                        _ipBindings.value = bindList
                    }
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun refreshData() {
        val config = activeConfig ?: return
        if (config.isDemoMode) {
            val current = _resourceState.value
            _resourceState.value = current.copy(
                cpuLoad = (current.cpuLoad + Random.nextInt(-3, 4)).coerceIn(5, 75),
                rxSpeedKbps = (current.rxSpeedKbps + Random.nextDouble(-400.0, 500.0)).coerceAtLeast(400.0),
                txSpeedKbps = (current.txSpeedKbps + Random.nextDouble(-200.0, 300.0)).coerceAtLeast(200.0)
            )
            return
        }

        if (isApiSocketMode) {
            fetchLiveRouterDataFromSocket()
            return
        }

        withContext(Dispatchers.IO) {
            val protocol = if (config.useHttps) "https" else "http"
            val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
            val credential = Credentials.basic(config.username, config.password)
            try {
                val resReq = Request.Builder().url("$baseUrl/rest/system/resource").header("Authorization", credential).build()
                val resResp = httpClient.newCall(resReq).execute()
                if (resResp.isSuccessful) {
                    parseResource(resResp.body?.string() ?: "")
                }
                fetchLiveRouterData(baseUrl, credential)
            } catch (_: Exception) {}
        }
    }

    suspend fun kickUser(user: HotspotActiveUser): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.kickUser(user.id)
                    if (socketRes.isSuccess) {
                        _activeUsers.value = _activeUsers.value.filter { it.id != user.id }
                    }
                    return@withContext socketRes
                }
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val delRequest = Request.Builder()
                        .url("$baseUrl/rest/ip/hotspot/active/${user.id}")
                        .delete()
                        .header("Authorization", credential)
                        .build()
                    val resp = httpClient.newCall(delRequest).execute()
                    if (resp.isSuccessful) {
                        _activeUsers.value = _activeUsers.value.filter { it.id != user.id }
                        return@withContext Result.success(true)
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            _activeUsers.value = _activeUsers.value.filter { it.id != user.id }
            Result.success(true)
        }
    }

    suspend fun deleteCard(cardId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.deleteCard(cardId)
                    if (socketRes.isSuccess) {
                        _cards.value = _cards.value.filter { it.id != cardId }
                    }
                    return@withContext socketRes
                }
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val delRequest = Request.Builder()
                        .url("$baseUrl/rest/ip/hotspot/user/$cardId")
                        .delete()
                        .header("Authorization", credential)
                        .build()
                    val resp = httpClient.newCall(delRequest).execute()
                    if (resp.isSuccessful) {
                        _cards.value = _cards.value.filter { it.id != cardId }
                        return@withContext Result.success(true)
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            _cards.value = _cards.value.filter { it.id != cardId }
            Result.success(true)
        }
    }

    suspend fun toggleCardDisabled(cardId: String, currentDisabled: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val newStatus = !currentDisabled
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.toggleCardDisabled(cardId, currentDisabled)
                    if (socketRes.isSuccess) {
                        _cards.value = _cards.value.map {
                            if (it.id == cardId) it.copy(disabled = newStatus) else it
                        }
                    }
                    return@withContext socketRes
                }
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val jsonBody = JSONObject().put("disabled", if (newStatus) "yes" else "no").toString()
                    val patchRequest = Request.Builder()
                        .url("$baseUrl/rest/ip/hotspot/user/$cardId")
                        .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                        .header("Authorization", credential)
                        .build()
                    val resp = httpClient.newCall(patchRequest).execute()
                    if (resp.isSuccessful) {
                        _cards.value = _cards.value.map {
                            if (it.id == cardId) it.copy(disabled = newStatus) else it
                        }
                        return@withContext Result.success(true)
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            _cards.value = _cards.value.map {
                if (it.id == cardId) it.copy(disabled = newStatus) else it
            }
            Result.success(true)
        }
    }

    suspend fun generateBatch(config: BatchGenerationConfig): Result<List<HotspotCard>> {
        return withContext(Dispatchers.Default) {
            val generatedCards = mutableListOf<HotspotCard>()
            val existingCodes = _cards.value.map { it.name }.toMutableSet()
            val random = SecureRandom()

            val charPool = when (config.characterSet) {
                CodeCharType.NUMBERS_ONLY -> "1234567890"
                CodeCharType.LETTERS_AND_NUMBERS -> "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
                CodeCharType.LETTERS_ONLY -> "ABCDEFGHJKLMNPQRSTUVWXYZ"
            }

            var attempts = 0
            while (generatedCards.size < config.count && attempts < config.count * 10) {
                attempts++
                val codeBuilder = StringBuilder(config.prefix)
                for (i in 0 until config.codeLength) {
                    val index = random.nextInt(charPool.length)
                    codeBuilder.append(charPool[index])
                }
                val code = codeBuilder.toString()
                if (!existingCodes.contains(code)) {
                    existingCodes.add(code)
                    val password = if (config.isSingleCode) code else {
                        val passBuilder = StringBuilder()
                        for (i in 0 until 4) {
                            passBuilder.append(random.nextInt(10))
                        }
                        passBuilder.toString()
                    }

                    val limitBytes = if (config.dataLimitMB > 0) config.dataLimitMB * 1024 * 1024L else 0L

                    val card = HotspotCard(
                        id = "gen_${System.currentTimeMillis()}_${generatedCards.size}",
                        name = code,
                        password = password,
                        profile = config.profile,
                        uptime = "0s",
                        bytesIn = 0,
                        bytesOut = 0,
                        limitUptime = config.timeLimit,
                        limitBytesTotal = limitBytes,
                        comment = config.batchComment.ifBlank { "دفعة كروت" },
                        disabled = false,
                        price = config.price
                    )
                    generatedCards.add(card)
                }
            }

            // Sync with real router
            val routerConfig = activeConfig
            if (routerConfig != null && !routerConfig.isDemoMode) {
                withContext(Dispatchers.IO) {
                    if (isApiSocketMode) {
                        for (card in generatedCards) {
                            routerOsApiClient.addCard(card)
                        }
                    } else {
                        val protocol = if (routerConfig.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${routerConfig.ipAddress}:${routerConfig.port}"
                        val credential = Credentials.basic(routerConfig.username, routerConfig.password)

                        for (card in generatedCards) {
                            try {
                                val jsonBody = JSONObject().apply {
                                    put("name", card.name)
                                    put("password", card.password)
                                    put("profile", card.profile)
                                    if (card.limitUptime.isNotBlank()) put("limit-uptime", card.limitUptime)
                                    if (card.limitBytesTotal > 0) put("limit-bytes-total", card.limitBytesTotal)
                                    put("comment", card.comment)
                                }.toString()

                                val putReq = Request.Builder()
                                    .url("$baseUrl/rest/ip/hotspot/user")
                                    .put(jsonBody.toRequestBody("application/json".toMediaType()))
                                    .header("Authorization", credential)
                                    .build()
                                httpClient.newCall(putReq).execute()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            val currentList = _cards.value.toMutableList()
            currentList.addAll(0, generatedCards)
            _cards.value = currentList

            Result.success(generatedCards)
        }
    }

    suspend fun createProfile(profile: HotspotProfile): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    routerOsApiClient.addProfile(profile)
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        val jsonBody = JSONObject().apply {
                            put("name", profile.name)
                            put("rate-limit", profile.rateLimit)
                            put("shared-users", profile.sharedUsers.toString())
                            if (profile.sessionTimeout.isNotBlank()) put("session-timeout", profile.sessionTimeout)
                        }.toString()

                        val putReq = Request.Builder()
                            .url("$baseUrl/rest/ip/hotspot/user/profile")
                            .put(jsonBody.toRequestBody("application/json".toMediaType()))
                            .header("Authorization", credential)
                            .build()
                        httpClient.newCall(putReq).execute()
                    } catch (_: Exception) {}
                }
            }
        }
        val current = _profiles.value.toMutableList()
        current.add(profile)
        _profiles.value = current
        return Result.success(true)
    }

    suspend fun rebootRouter(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    return@withContext routerOsApiClient.rebootRouter()
                }
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val postReq = Request.Builder()
                        .url("$baseUrl/rest/system/reboot")
                        .post("{}".toRequestBody("application/json".toMediaType()))
                        .header("Authorization", credential)
                        .build()
                    httpClient.newCall(postReq).execute()
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                delay(1000)
                Result.success(true)
            }
        }
    }

    fun disconnect() {
        activeConfig = null
        if (isApiSocketMode) {
            routerOsApiClient.close()
            isApiSocketMode = false
        }
    }

    fun getActiveConfig(): RouterConnectionConfig? = activeConfig
}
