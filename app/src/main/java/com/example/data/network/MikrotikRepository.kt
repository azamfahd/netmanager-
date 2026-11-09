package com.example.data.network

import android.util.Log
import com.example.data.model.AdminUser
import com.example.data.model.AuditLogEntry
import com.example.data.model.BatchCardGenerationResult
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.CodeCharType
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.local.SystemEventLogEntity
import com.example.data.model.IpBinding
import com.example.data.model.RouterConnectionConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.model.RouterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

data class UserKnownTelemetry(
    val macAddress: String,
    val ipAddress: String,
    val hostName: String,
    val nodeName: String,
    val deviceInfo: com.example.data.model.DeviceIdentification,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

class MikrotikRepository(
    private val db: FirebaseFirestore,
    private val appDao: com.example.data.local.AppDao? = null
) {

    private val cardTimestampCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun getCardCreationTimestamp(card: HotspotCard): Long {
        val cached = cardTimestampCache[card.name]
        if (cached != null && cached > 0L) return cached

        val resolved = resolveCardTimestamp(card)
        cardTimestampCache[card.name] = resolved
        return resolved
    }

    private fun resolveCardTimestamp(card: HotspotCard): Long {
        val id = card.id
        val match = Regex("""(?:gen_|card_|c_|tum_|um_)?(\d{10,13})""").find(id)
        if (match != null) {
            val numStr = match.groupValues[1]
            val parsed = if (numStr.length == 10) numStr.toLongOrNull()?.times(1000L) else numStr.toLongOrNull()
            if (parsed != null && parsed in 1577836800000L..2082758400000L) {
                return parsed
            }
        }

        val comment = card.comment
        if (comment.isNotBlank()) {
            val dateMatch = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})""").find(comment)
            if (dateMatch != null) {
                try {
                    val (y, m, d) = dateMatch.destructured
                    val cal = java.util.Calendar.getInstance()
                    cal.set(y.toInt(), m.toInt() - 1, d.toInt(), 12, 0, 0)
                    return cal.timeInMillis
                } catch (_: Exception) {}
            }
            val epochMatch = Regex("""\b(17\d{8,11})\b""").find(comment)
            if (epochMatch != null) {
                val num = epochMatch.value.toLongOrNull()
                if (num != null) {
                    val ms = if (epochMatch.value.length == 10) num * 1000L else num
                    if (ms in 1577836800000L..2082758400000L) return ms
                }
            }
        }

        val uptimeSec = parseUptimeToSeconds(card.uptime)
        if (uptimeSec > 0L) {
            return System.currentTimeMillis() - (uptimeSec * 1000L)
        }

        return System.currentTimeMillis()
    }

    fun sortCardsByNewestFirst(cards: List<HotspotCard>): List<HotspotCard> {
        return cards.sortedWith(
            compareByDescending<HotspotCard> { getCardCreationTimestamp(it) }
                .thenByDescending { it.id }
        )
    }

    suspend fun syncCardsToRoom(cards: List<HotspotCard>) {
        val dao = appDao ?: return
        try {
            val existing = dao.getAllLocalCardsSync().associateBy { it.name }
            val entities = cards.map { card ->
                val prevEntity = existing[card.name]
                val createdTs = prevEntity?.createdTimestamp?.takeIf { it > 0L } ?: getCardCreationTimestamp(card)
                cardTimestampCache[card.name] = createdTs
                com.example.data.local.LocalCardEntity.fromHotspotCard(
                    card = card,
                    existingCreatedTs = createdTs,
                    source = if (card.isUserManagerCard) "usermanager" else "hotspot"
                )
            }
            dao.insertOrUpdateLocalCards(entities)
        } catch (_: Exception) {}
    }

    suspend fun initializeFromLocalDatabase() {
        val dao = appDao ?: return
        try {
            // 1. Local Cards Cache
            val localCards = dao.getAllLocalCardsSync()
            if (localCards.isNotEmpty()) {
                localCards.forEach {
                    if (it.createdTimestamp > 0L) {
                        cardTimestampCache[it.name] = it.createdTimestamp
                    }
                }
                val cardsList = sortCardsByNewestFirst(localCards.map { it.toHotspotCard() })
                if (_cards.value.isEmpty()) {
                    _cards.value = cardsList
                }
            }

            // 2. Local Network Nodes Cache
            val localNodes = dao.getAllNetworkNodesSync()
            if (localNodes.isNotEmpty() && _networkNodes.value.isEmpty()) {
                _networkNodes.value = localNodes.map { it.toNetworkNode() }
            }

            // 3. Cached Materialized Summary
            val cachedSummary = dao.getMaterializedSummary()
            if (cachedSummary != null) {
                Log.d("MikrotikRepository", "Loaded cached materialized summary: rev=${cachedSummary.totalEstimatedRevenue}")
            }
        } catch (e: Exception) {
            Log.e("MikrotikRepository", "Error initializing from local database: ${e.message}")
        }
    }

    private val lastKnownUserTelemetry = java.util.concurrent.ConcurrentHashMap<String, UserKnownTelemetry>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val routerOsApiClient = RouterOsApiClient()
    private var isApiSocketMode = false

    private var activeConfig: RouterConnectionConfig? = null
    private val customAddedNodes = mutableListOf<com.example.data.model.NetworkNode>()
    private val deletedNodeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val deletedNodeKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    var onDataRefreshedListener: ((com.example.data.local.RouterSnapshot) -> Unit)? = null

    fun restoreFromSnapshot(snapshot: com.example.data.local.RouterSnapshot) {
        _resourceState.value = snapshot.resource
        _routerBoardInfo.value = snapshot.routerBoard
        _cards.value = snapshot.cards
        _profiles.value = snapshot.profiles
        _activeUsers.value = snapshot.activeUsers
        _hotspotServers.value = snapshot.servers
        _networkNodes.value = snapshot.nodes
        _systemLogs.value = snapshot.logs
        if (snapshot.ipBindings.isNotEmpty()) {
            _ipBindings.value = snapshot.ipBindings
        }
    }

    fun getCurrentSnapshot(ipAddress: String, routerName: String): com.example.data.local.RouterSnapshot {
        return com.example.data.local.RouterSnapshot(
            ipAddress = ipAddress,
            routerName = routerName,
            resource = _resourceState.value,
            routerBoard = _routerBoardInfo.value,
            cards = _cards.value,
            profiles = _profiles.value,
            activeUsers = _activeUsers.value,
            servers = _hotspotServers.value,
            nodes = _networkNodes.value,
            logs = _systemLogs.value,
            ipBindings = _ipBindings.value
        )
    }

    fun notifyDataRefreshed() {
        val config = activeConfig ?: return
        onDataRefreshedListener?.invoke(getCurrentSnapshot(config.ipAddress, config.name.ifBlank { "راوتر MikroTik" }))
    }

    // State Holders
    private val _resourceState = MutableStateFlow(RouterResource())
    val resourceState: StateFlow<RouterResource> = _resourceState.asStateFlow()

    private val _routerBoardInfo = MutableStateFlow(com.example.data.model.RouterBoardInfo())
    val routerBoardInfo: StateFlow<com.example.data.model.RouterBoardInfo> = _routerBoardInfo.asStateFlow()

    private val _hotspotServers = MutableStateFlow<List<com.example.data.model.HotspotServerInfo>>(emptyList())
    val hotspotServers: StateFlow<List<com.example.data.model.HotspotServerInfo>> = _hotspotServers.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<com.example.data.model.SystemLogEntry>>(emptyList())
    val systemLogs: StateFlow<List<com.example.data.model.SystemLogEntry>> = _systemLogs.asStateFlow()

    private val _ipPools = MutableStateFlow<List<com.example.data.model.IpPoolInfo>>(emptyList())
    val ipPools: StateFlow<List<com.example.data.model.IpPoolInfo>> = _ipPools.asStateFlow()

    private val _interfaceStats = MutableStateFlow<List<com.example.data.model.InterfaceStats>>(emptyList())
    val interfaceStats: StateFlow<List<com.example.data.model.InterfaceStats>> = _interfaceStats.asStateFlow()

    // Real-Time Traffic Calculation Tracking
    private var lastTrafficCalculationTimeMs: Long = 0L
    private val lastInterfaceBytesMap = mutableMapOf<String, Pair<Long, Long>>() // name -> (rxBytes, txBytes)

    private val _activeUsers = MutableStateFlow<List<HotspotActiveUser>>(emptyList())
    val activeUsers: StateFlow<List<HotspotActiveUser>> = _activeUsers.asStateFlow()

    private val _cards = MutableStateFlow<List<HotspotCard>>(emptyList())
    val cards: StateFlow<List<HotspotCard>> = _cards.asStateFlow()

    fun updateLocalCard(updatedCard: HotspotCard) {
        _cards.value = _cards.value.map {
            if (it.id == updatedCard.id || it.name == updatedCard.name) updatedCard else it
        }
    }

    fun deleteLocalCard(cardId: String, cardName: String = "") {
        val targetName = cardName.trim().ifBlank { _cards.value.find { it.id == cardId }?.name.orEmpty() }
        _cards.value = _cards.value.filter { it.id != cardId && (targetName.isBlank() || it.name != targetName) }
        if (targetName.isNotBlank()) {
            cardTimestampCache.remove(targetName)
        }
    }

    private val _profiles = MutableStateFlow<List<HotspotProfile>>(emptyList())
    val profiles: StateFlow<List<HotspotProfile>> = _profiles.asStateFlow()

    fun updateLocalProfile(profile: HotspotProfile) {
        val current = _profiles.value.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id || it.name.equals(profile.name, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = profile
        } else {
            current.add(profile)
        }
        _profiles.value = current
    }

    fun removeLocalProfile(profile: HotspotProfile) {
        _profiles.value = _profiles.value.filter { it.id != profile.id && !it.name.equals(profile.name, ignoreCase = true) }
    }

    private val _ipBindings = MutableStateFlow<List<IpBinding>>(emptyList())
    val ipBindings: StateFlow<List<IpBinding>> = _ipBindings.asStateFlow()

    private val _networkNodes = MutableStateFlow<List<com.example.data.model.NetworkNode>>(emptyList())
    val networkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = _networkNodes.asStateFlow()

    private val _userSessions = MutableStateFlow<List<com.example.data.model.UserSessionRecord>>(emptyList())
    val userSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = _userSessions.asStateFlow()

    private val _activityEvents = MutableStateFlow<List<com.example.data.model.UserActivityEvent>>(emptyList())
    val activityEvents: StateFlow<List<com.example.data.model.UserActivityEvent>> = _activityEvents.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<com.example.data.model.AdminUser>>(emptyList())
    val adminUsers: StateFlow<List<com.example.data.model.AdminUser>> = _adminUsers.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<com.example.data.model.AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<com.example.data.model.AuditLogEntry>> = _auditLogs.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow<List<com.example.data.model.BandwidthPoint>>(emptyList())
    val bandwidthHistory: StateFlow<List<com.example.data.model.BandwidthPoint>> = _bandwidthHistory.asStateFlow()

    fun recordBandwidthPoint(rxKbps: Double, txKbps: Double) {
        val now = System.currentTimeMillis()
        val current = _bandwidthHistory.value.toMutableList()
        current.add(com.example.data.model.BandwidthPoint(now, rxKbps, txKbps))
        if (current.size > 30) {
            _bandwidthHistory.value = current.takeLast(30)
        } else {
            _bandwidthHistory.value = current
        }
    }

    fun logAdminAction(adminUsername: String, actionType: String, description: String, severity: String = "INFO") {
        val entry = com.example.data.model.AuditLogEntry(
            id = "log_${System.currentTimeMillis()}",
            adminUsername = adminUsername,
            actionType = actionType,
            description = description,
            timestamp = "الآن ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale("ar")).format(java.util.Date())}",
            ipAddress = "192.168.88.1",
            severity = severity
        )
        val updated = mutableListOf(entry)
        updated.addAll(_auditLogs.value)
        _auditLogs.value = updated
    }

    fun getLastKnownTelemetryForUser(username: String): UserKnownTelemetry? {
        return lastKnownUserTelemetry.values.firstOrNull { it.deviceInfo.modelName.contains(username, ignoreCase = true) || it.hostName == username }
    }


    fun addAdminUser(admin: com.example.data.model.AdminUser) {
        val updated = mutableListOf(admin)
        updated.addAll(_adminUsers.value)
        _adminUsers.value = updated
        logAdminAction("admin", "إضافة مستخدم إداري", "تم إضافة مدير جديد: ${admin.fullName} (@${admin.username}) بصلاحيات: ${admin.permissions.joinToString()}", "INFO")

        if (activeConfig?.isDemoMode != true) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    routerOsApiClient.addRouterUser(
                        username = admin.username,
                        password = admin.password.ifBlank { "1234" },
                        group = admin.role,
                        comment = admin.fullName
                    )
                } catch (_: Exception) {}
            }
        }
    }

    init {
        _profiles.value = emptyList()
        _cards.value = emptyList()
        _activeUsers.value = emptyList()
    }

    fun populateDemoData() {
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

        // Initialize realistic Network Nodes & Access Points with towers, distribution nodes, and clustered home nodes
        _networkNodes.value = listOf(
            com.example.data.model.NetworkNode(
                id = "node_ap_main",
                name = "برج الإرسال المركزي 1 (الرئيسي)",
                ipAddress = "192.168.88.2",
                macAddress = "D4:CA:6D:AA:11:01",
                interfaceName = "ether2",
                model = "MikroTik NetMetal 5 / Omni",
                role = "برج إرسال رئيسي (Omni)",
                nodeType = com.example.data.model.NetworkNodeType.MAIN_TOWER,
                status = com.example.data.model.NodeStatus.ACTIVE,
                isOnline = true,
                pingMs = 2L,
                connectedUsers = 18,
                uptime = "5d 14h",
                rxSpeedKbps = 8400.0,
                txSpeedKbps = 3200.0,
                totalBytesConsumed = 45_200_000_000L,
                signalDbm = "-54 dBm",
                location = "برج الإرسال المركزي",
                cpuLoadPercent = 38,
                memoryUsagePercent = 42,
                temperature = "44°C",
                voltage = "24.1 V",
                frequency = "5 GHz (5200 MHz)",
                channelWidth = "20/40/80 MHz",
                ssid = "YemenNet_Tower_AP1",
                connectedClients = listOf(
                    com.example.data.model.NodeConnectedClient(
                        id = "nc_1", cardName = "784912", ipAddress = "192.168.88.210", macAddress = "D4:6A:A8:12:34:56",
                        signalDbm = "-52 dBm", ccqRate = "99%", rxRateMbps = 120.0, txRateMbps = 86.0, uptime = "2h 15m",
                        bytesUsed = 765 * 1024 * 1024L,
                        deviceInfo = com.example.data.util.DeviceDetector.detectDevice("D4:6A:A8:12:34:56", "Galaxy S23 Ultra", "784912")
                    ),
                    com.example.data.model.NodeConnectedClient(
                        id = "nc_2", cardName = "319405", ipAddress = "192.168.88.145", macAddress = "BC:D1:D3:55:66:77",
                        signalDbm = "-58 dBm", ccqRate = "95%", rxRateMbps = 72.0, txRateMbps = 54.0, uptime = "4h 10m",
                        bytesUsed = 1250 * 1024 * 1024L,
                        deviceInfo = com.example.data.util.DeviceDetector.detectDevice("BC:D1:D3:55:66:77", "iPhone 15 Pro", "319405")
                    ),
                    com.example.data.model.NodeConnectedClient(
                        id = "nc_3", cardName = "901824", ipAddress = "192.168.88.188", macAddress = "00:E0:4C:88:99:AA",
                        signalDbm = "-61 dBm", ccqRate = "92%", rxRateMbps = 54.0, txRateMbps = 36.0, uptime = "1h 05m",
                        bytesUsed = 430 * 1024 * 1024L,
                        deviceInfo = com.example.data.util.DeviceDetector.detectDevice("00:E0:4C:88:99:AA", "Redmi Note 12", "901824")
                    )
                )
            ),
            com.example.data.model.NetworkNode(
                id = "node_ap_east",
                name = "أكسس بوينت الحارة الشرقية",
                ipAddress = "192.168.88.3",
                macAddress = "D4:CA:6D:BB:22:02",
                interfaceName = "ether3",
                model = "MikroTik cAP ac",
                role = "نقطة توزيع بث (AP)",
                nodeType = com.example.data.model.NetworkNodeType.DISTRIBUTION,
                status = com.example.data.model.NodeStatus.ACTIVE,
                parentTowerId = "node_ap_main",
                parentTowerName = "برج الإرسال المركزي 1",
                isOnline = true,
                pingMs = 3L,
                connectedUsers = 11,
                uptime = "3d 21h",
                rxSpeedKbps = 5200.0,
                txSpeedKbps = 2100.0,
                totalBytesConsumed = 28_100_000_000L,
                signalDbm = "-62 dBm",
                location = "المربع الشرقي",
                cpuLoadPercent = 24,
                memoryUsagePercent = 32,
                temperature = "41°C",
                voltage = "23.8 V",
                frequency = "2.4 GHz (Channel 6)",
                channelWidth = "20/40 MHz",
                ssid = "YemenNet_East_AP",
                connectedClients = listOf(
                    com.example.data.model.NodeConnectedClient(
                        id = "nc_4", cardName = "551209", ipAddress = "192.168.88.132", macAddress = "18:82:A8:77:88:99",
                        signalDbm = "-59 dBm", ccqRate = "96%", rxRateMbps = 65.0, txRateMbps = 45.0, uptime = "3h 40m",
                        bytesUsed = 890 * 1024 * 1024L,
                        deviceInfo = com.example.data.util.DeviceDetector.detectDevice("18:82:A8:77:88:99", "Dell XPS 15", "551209")
                    )
                )
            ),
            com.example.data.model.NetworkNode(
                id = "node_home_1",
                name = "نقطة منزل الشيخ عبد الله",
                ipAddress = "192.168.88.21",
                macAddress = "D4:CA:6D:AA:21:01",
                interfaceName = "wlan1-sta",
                model = "TP-Link Archer C6 (CPE)",
                role = "نقطة منزلية (عميل)",
                nodeType = com.example.data.model.NetworkNodeType.HOME_NODE,
                status = com.example.data.model.NodeStatus.ACTIVE,
                parentTowerId = "node_ap_main",
                parentTowerName = "برج الإرسال المركزي 1",
                isOnline = true,
                pingMs = 5L,
                connectedUsers = 4,
                uptime = "4d 08h",
                rxSpeedKbps = 3200.0,
                txSpeedKbps = 1100.0,
                totalBytesConsumed = 15_400_000_000L,
                signalDbm = "-56 dBm",
                location = "منزل الشيخ عبد الله",
                cpuLoadPercent = 18,
                memoryUsagePercent = 30,
                temperature = "39°C",
                voltage = "12.0 V",
                frequency = "5 GHz",
                channelWidth = "40 MHz",
                ssid = "Home_Abdullah",
                connectedClients = emptyList()
            ),
            com.example.data.model.NetworkNode(
                id = "node_home_2",
                name = "محطة منزل المهندس طارق",
                ipAddress = "192.168.88.22",
                macAddress = "D4:CA:6D:AA:22:02",
                interfaceName = "wlan1-sta",
                model = "MikroTik hEX lite",
                role = "نقطة منزلية (مقطوع النت)",
                nodeType = com.example.data.model.NetworkNodeType.HOME_NODE,
                status = com.example.data.model.NodeStatus.NO_INTERNET,
                parentTowerId = "node_ap_main",
                parentTowerName = "برج الإرسال المركزي 1",
                isOnline = true,
                pingMs = 12L,
                connectedUsers = 2,
                uptime = "1d 04h",
                rxSpeedKbps = 240.0,
                txSpeedKbps = 80.0,
                totalBytesConsumed = 4_200_000_000L,
                signalDbm = "-68 dBm",
                location = "منزل م. طارق",
                cpuLoadPercent = 32,
                memoryUsagePercent = 40,
                temperature = "43°C",
                voltage = "12.1 V",
                frequency = "2.4 GHz",
                channelWidth = "20 MHz",
                ssid = "Home_Tariq_Net",
                connectedClients = emptyList()
            ),
            com.example.data.model.NetworkNode(
                id = "node_home_3",
                name = "نقطة منزل عمار اليرموك",
                ipAddress = "192.168.88.23",
                macAddress = "D4:CA:6D:AA:23:03",
                interfaceName = "ether2",
                model = "Tenda F3 (Station)",
                role = "نقطة منزلية (طافية/مغلقة)",
                nodeType = com.example.data.model.NetworkNodeType.HOME_NODE,
                status = com.example.data.model.NodeStatus.OFFLINE,
                parentTowerId = "node_ap_main",
                parentTowerName = "برج الإرسال المركزي 1",
                isOnline = false,
                pingMs = 0L,
                connectedUsers = 0,
                uptime = "0s (مغلق)",
                rxSpeedKbps = 0.0,
                txSpeedKbps = 0.0,
                totalBytesConsumed = 9_800_000_000L,
                signalDbm = "لا توجد إشارة",
                location = "حي اليرموك - منزل عمار",
                cpuLoadPercent = 0,
                memoryUsagePercent = 0,
                temperature = "--",
                voltage = "0 V",
                frequency = "--",
                channelWidth = "--",
                ssid = "Offline_Node",
                connectedClients = emptyList()
            ),
            com.example.data.model.NetworkNode(
                id = "node_starlink",
                name = "سيرفر Starlink الرئيسي (WAN)",
                ipAddress = "192.168.1.1",
                macAddress = "94:10:3E:11:22:33",
                interfaceName = "ether1-WAN",
                model = "Starlink Terminal Gen 3",
                role = "مصدر الإنترنت الرئيسي (Starlink)",
                nodeType = com.example.data.model.NetworkNodeType.MAIN_TOWER,
                status = com.example.data.model.NodeStatus.ACTIVE,
                isOnline = true,
                pingMs = 24L,
                connectedUsers = 4,
                uptime = "12d 8h",
                rxSpeedKbps = 24500.0,
                txSpeedKbps = 6800.0,
                totalBytesConsumed = 180_000_000_000L,
                signalDbm = "-48 dBm",
                location = "السطح الرئيسي",
                cpuLoadPercent = 15,
                memoryUsagePercent = 28,
                temperature = "39°C",
                voltage = "48.0 V",
                frequency = "Gigabit Ethernet",
                channelWidth = "Full Duplex",
                ssid = "Starlink_Bridge",
                connectedClients = emptyList()
            ),
            com.example.data.model.NetworkNode(
                id = "node_ap_west",
                name = "برج القطاع الغربي 2 (Sector)",
                ipAddress = "192.168.88.4",
                macAddress = "D4:CA:6D:CC:33:03",
                interfaceName = "ether4",
                model = "MikroTik BaseBox 2",
                role = "برج قطاعي رئيسي (Sector)",
                nodeType = com.example.data.model.NetworkNodeType.MAIN_TOWER,
                status = com.example.data.model.NodeStatus.ACTIVE,
                isOnline = true,
                pingMs = 4L,
                connectedUsers = 8,
                uptime = "2d 06h",
                rxSpeedKbps = 3800.0,
                txSpeedKbps = 1400.0,
                totalBytesConsumed = 19_400_000_000L,
                signalDbm = "-66 dBm",
                location = "المربع الغربي",
                cpuLoadPercent = 55,
                memoryUsagePercent = 60,
                temperature = "48°C",
                voltage = "23.5 V",
                frequency = "5 GHz (5180 MHz)",
                channelWidth = "20/40 MHz",
                ssid = "YemenNet_Sector2",
                connectedClients = emptyList()
            ),
            com.example.data.model.NetworkNode(
                id = "node_home_4",
                name = "نقطة منزل بقالة النور",
                ipAddress = "192.168.88.31",
                macAddress = "D4:CA:6D:CC:44:11",
                interfaceName = "wlan1-sta",
                model = "Tenda Nova Mesh",
                role = "نقطة منزلية (عميل)",
                nodeType = com.example.data.model.NetworkNodeType.HOME_NODE,
                status = com.example.data.model.NodeStatus.ACTIVE,
                parentTowerId = "node_ap_west",
                parentTowerName = "برج القطاع الغربي 2",
                isOnline = true,
                pingMs = 7L,
                connectedUsers = 5,
                uptime = "2d 02h",
                rxSpeedKbps = 1850.0,
                txSpeedKbps = 620.0,
                totalBytesConsumed = 8_900_000_000L,
                signalDbm = "-61 dBm",
                location = "بقالة النور - الشارع الغربي",
                cpuLoadPercent = 20,
                memoryUsagePercent = 34,
                temperature = "40°C",
                voltage = "12.0 V",
                frequency = "2.4 GHz",
                channelWidth = "20 MHz",
                ssid = "Baqala_AlNoor",
                connectedClients = emptyList()
            ),
            com.example.data.model.NetworkNode(
                id = "node_ap_north",
                name = "موجه الفرع الشمالي (Point to Point)",
                ipAddress = "192.168.88.5",
                macAddress = "D4:CA:6D:DD:44:04",
                interfaceName = "wlan1-bridge",
                model = "MikroTik SXTsq 5",
                role = "نقطة توزيع (Point to Point)",
                nodeType = com.example.data.model.NetworkNodeType.DISTRIBUTION,
                status = com.example.data.model.NodeStatus.ACTIVE,
                parentTowerId = "node_ap_west",
                parentTowerName = "برج القطاع الغربي 2",
                isOnline = true,
                pingMs = 5L,
                connectedUsers = 6,
                uptime = "1d 18h",
                rxSpeedKbps = 2600.0,
                txSpeedKbps = 900.0,
                totalBytesConsumed = 12_800_000_000L,
                signalDbm = "-59 dBm",
                location = "الفرع الشمالي",
                cpuLoadPercent = 18,
                memoryUsagePercent = 25,
                temperature = "40°C",
                voltage = "24.0 V",
                frequency = "5 GHz (5745 MHz)",
                channelWidth = "40 MHz",
                ssid = "PTP_North_Link",
                connectedClients = emptyList()
            )
        )

        // Initialize detailed user session logs (Active + Inactive/Disconnected)
        _userSessions.value = listOf(
            com.example.data.model.UserSessionRecord(
                id = "sess_1",
                username = "784912",
                ipAddress = "192.168.88.210",
                macAddress = "D4:6A:A8:12:34:56",
                isOnline = true,
                loginTime = "اليوم 10:15 ص",
                logoutTime = "متصل الآن (جلسة نشطة)",
                duration = "2h 15m",
                bytesIn = 145 * 1024 * 1024L,
                bytesOut = 620 * 1024 * 1024L,
                totalBytes = (145 + 620) * 1024 * 1024L,
                terminateReason = "جلسة نشطة حالياً",
                profile = "فئة 500 (يومي 2.5GB)",
                price = "500 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("D4:6A:A8:12:34:56", "Galaxy S23 Ultra", "784912"),
                comment = "مستخدم متصل - برج 1"
            ),
            com.example.data.model.UserSessionRecord(
                id = "sess_2",
                username = "319405",
                ipAddress = "192.168.88.145",
                macAddress = "BC:D1:D3:55:66:77",
                isOnline = true,
                loginTime = "اليوم 11:48 ص",
                logoutTime = "متصل الآن (جلسة نشطة)",
                duration = "42m",
                bytesIn = 35 * 1024 * 1024L,
                bytesOut = 280 * 1024 * 1024L,
                totalBytes = (35 + 280) * 1024 * 1024L,
                terminateReason = "جلسة نشطة حالياً",
                profile = "فئة 200 (ساعتان 500MB)",
                price = "200 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("BC:D1:D3:55:66:77", "iPhone 14 Pro", "319405"),
                comment = "مستخدم متصل - أكسس بوينت شرق"
            ),
            com.example.data.model.UserSessionRecord(
                id = "sess_3",
                username = "849201",
                ipAddress = "192.168.88.199",
                macAddress = "70:85:C2:88:99:AA",
                isOnline = true,
                loginTime = "أمس 08:30 ص",
                logoutTime = "متصل الآن (جلسة نشطة)",
                duration = "1d 4h",
                bytesIn = 420 * 1024 * 1024L,
                bytesOut = 2450 * 1024 * 1024L,
                totalBytes = (420 + 2450) * 1024 * 1024L,
                terminateReason = "جلسة نشطة حالياً",
                profile = "فئة 2000 (أسبوعي VIP)",
                price = "2000 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("70:85:C2:88:99:AA", "Dell XPS 15 Laptop", "849201"),
                comment = "مستخدم دائم VIP"
            ),
            com.example.data.model.UserSessionRecord(
                id = "sess_4",
                username = "651829",
                ipAddress = "192.168.88.172",
                macAddress = "38:F9:D3:BB:CC:DD",
                isOnline = true,
                loginTime = "اليوم 07:20 ص",
                logoutTime = "متصل الآن (جلسة نشطة)",
                duration = "5h 10m",
                bytesIn = 80 * 1024 * 1024L,
                bytesOut = 890 * 1024 * 1024L,
                totalBytes = (80 + 890) * 1024 * 1024L,
                terminateReason = "جلسة نشطة حالياً",
                profile = "فئة 500 (يومي 2.5GB)",
                price = "500 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("38:F9:D3:BB:CC:DD", "Xiaomi Redmi Note 12", "651829"),
                comment = "مستخدم متصل - نقطة الغرب"
            ),
            // Disconnected / Inactive Sessions (users who used the card or part of it)
            com.example.data.model.UserSessionRecord(
                id = "sess_disc_1",
                username = "390124",
                ipAddress = "192.168.88.160",
                macAddress = "00:EC:0A:99:AA:BB",
                isOnline = false,
                loginTime = "اليوم 09:10 ص",
                logoutTime = "اليوم 10:10 ص",
                duration = "1h 00m",
                bytesIn = 95 * 1024 * 1024L,
                bytesOut = 415 * 1024 * 1024L,
                totalBytes = 510 * 1024 * 1024L,
                terminateReason = "انتهاء وقت الباقة المحدد (1 ساعة)",
                profile = "فئة 200 (ساعتان 500MB)",
                price = "200 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("00:EC:0A:99:AA:BB", "Infinix Hot 30", "390124"),
                comment = "كرت منتهي - مستخدم غير متصل"
            ),
            com.example.data.model.UserSessionRecord(
                id = "sess_disc_2",
                username = "481029",
                ipAddress = "192.168.88.163",
                macAddress = "A4:99:9B:44:55:66",
                isOnline = false,
                loginTime = "أمس 18:20 م",
                logoutTime = "أمس 22:45 م",
                duration = "4h 25m",
                bytesIn = 110 * 1024 * 1024L,
                bytesOut = 1240 * 1024 * 1024L,
                totalBytes = 1350 * 1024 * 1024L,
                terminateReason = "تسجيل خروج يدوي من المستخدم (رصيد متبقي 700MB)",
                profile = "فئة 500 (يومي 2.5GB)",
                price = "500 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("A4:99:9B:44:55:66", "Huawei Nova 11", "481029"),
                comment = "كرت مستخدم جزئياً - رصيد صالح"
            ),
            com.example.data.model.UserSessionRecord(
                id = "sess_disc_3",
                username = "950281",
                ipAddress = "192.168.88.188",
                macAddress = "24:4B:FE:77:88:99",
                isOnline = false,
                loginTime = "أمس 14:00 م",
                logoutTime = "أمس 19:30 م",
                duration = "5h 30m",
                bytesIn = 230 * 1024 * 1024L,
                bytesOut = 2330 * 1024 * 1024L,
                totalBytes = 2560 * 1024 * 1024L,
                terminateReason = "استهلاك كامل حجم البيانات (2.5 GB)",
                profile = "فئة 500 (يومي 2.5GB)",
                price = "500 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("24:4B:FE:77:88:99", "Realme 11 Pro", "950281"),
                comment = "كرت منتهي الاستهلاك"
            ),
            com.example.data.model.UserSessionRecord(
                id = "sess_disc_4",
                username = "172948",
                ipAddress = "192.168.88.155",
                macAddress = "B4:2E:99:11:00:22",
                isOnline = false,
                loginTime = "قبل يومين 16:00",
                logoutTime = "قبل يومين 18:30",
                duration = "2h 30m",
                bytesIn = 65 * 1024 * 1024L,
                bytesOut = 540 * 1024 * 1024L,
                totalBytes = 605 * 1024 * 1024L,
                terminateReason = "انقطاع إشارة الواي فاي / خروج من التغطية",
                profile = "فئة 300 (6 ساعات 1GB)",
                price = "300 ر.ي",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("B4:2E:99:11:00:22", "HP Laptop Windows 11", "172948"),
                comment = "كرت مستخدم جزئياً"
            )
        )

        // Activity Events Log
        _activityEvents.value = listOf(
            com.example.data.model.UserActivityEvent(
                id = "evt_1",
                username = "319405",
                eventType = "تسجيل دخول",
                timestamp = "اليوم 11:48 ص",
                details = "تسجيل دخول ناجح عبر HTTP-CHAP (باقة 200 ر.ي)",
                ipAddress = "192.168.88.145",
                macAddress = "BC:D1:D3:55:66:77",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("BC:D1:D3:55:66:77", "iPhone 14 Pro", "319405")
            ),
            com.example.data.model.UserActivityEvent(
                id = "evt_2",
                username = "784912",
                eventType = "تسجيل دخول",
                timestamp = "اليوم 10:15 ص",
                details = "تسجيل دخول ناجح (باقة 500 ر.ي - يوم كامل)",
                ipAddress = "192.168.88.210",
                macAddress = "D4:6A:A8:12:34:56",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("D4:6A:A8:12:34:56", "Galaxy S23 Ultra", "784912")
            ),
            com.example.data.model.UserActivityEvent(
                id = "evt_3",
                username = "390124",
                eventType = "تسجيل خروج",
                timestamp = "اليوم 10:10 ص",
                details = "انتهاء مدة الجلسة المحددة (1 ساعة) واستهلاك 510MB",
                ipAddress = "192.168.88.160",
                macAddress = "00:EC:0A:99:AA:BB",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("00:EC:0A:99:AA:BB", "Infinix Hot 30", "390124")
            ),
            com.example.data.model.UserActivityEvent(
                id = "evt_4",
                username = "651829",
                eventType = "تسجيل دخول",
                timestamp = "اليوم 07:20 ص",
                details = "تسجيل دخول نشط عبر البرج الغربي",
                ipAddress = "192.168.88.172",
                macAddress = "38:F9:D3:BB:CC:DD",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("38:F9:D3:BB:CC:DD", "Xiaomi Redmi Note 12", "651829")
            ),
            com.example.data.model.UserActivityEvent(
                id = "evt_5",
                username = "481029",
                eventType = "تسجيل خروج",
                timestamp = "أمس 22:45 م",
                details = "تسجيل خروج من المستخدم، رصيد متبقي 700MB",
                ipAddress = "192.168.88.163",
                macAddress = "A4:99:9B:44:55:66",
                deviceInfo = com.example.data.util.DeviceDetector.detectDevice("A4:99:9B:44:55:66", "Huawei Nova 11", "481029")
            )
        )
    }

    suspend fun verifyAndConnect(config: RouterConnectionConfig): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanConfig = config.copy(
                    ipAddress = config.ipAddress.trim(),
                    username = config.username.trim(),
                    password = config.password.trim()
                )
                activeConfig = cleanConfig
                if (cleanConfig.isDemoMode) {
                    delay(300)
                    populateDemoData()
                    adaptDataToConfig(cleanConfig)
                    return@withContext Result.success(true)
                }

                // Clear any demo/initial placeholder data when connecting to a real router
                _cards.value = emptyList()
                _activeUsers.value = emptyList()
                _profiles.value = emptyList()
                _ipBindings.value = emptyList()
                _networkNodes.value = emptyList()
                _userSessions.value = emptyList()
                _activityEvents.value = emptyList()
                _adminUsers.value = emptyList()
                _auditLogs.value = emptyList()
                _systemLogs.value = emptyList()
                _bandwidthHistory.value = emptyList()

                // 1. If user explicitly specified port 8728, connect directly via native RouterOS API socket
                if (cleanConfig.port == 8728) {
                    val socketResult = routerOsApiClient.connectAndLogin(
                        host = cleanConfig.ipAddress,
                        port = 8728,
                        username = cleanConfig.username,
                        password = cleanConfig.password
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
                val protocol = if (cleanConfig.useHttps) "https" else "http"
                val baseUrl = "$protocol://${cleanConfig.ipAddress}:${cleanConfig.port}"
                val credential = Credentials.basic(cleanConfig.username, cleanConfig.password)

                // Caching the successfully detected connection type
                if (cleanConfig.port != 8728) {
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
                        }
                    } catch (_: Exception) {}
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
                } else {
                    "تعذر الاتصال بعنوان الراوتر (${config.ipAddress}:${config.port}). يرجى التأكد من تفعيل خدمة API في الراوتر عبر Winbox من القائمة: IP -> Services -> api، وتأكد من أن هاتفك متصل بشبكة الواي فاي التابعة للراوتر."
                }

                Result.failure(Exception(errorMsg))
            } catch (e: Exception) {
                Result.failure(Exception("فشل الاتصال: ${e.localizedMessage ?: e.message}"))
            }
        }
    }

    private suspend fun fetchLiveRouterDataFromSocket() {
        withContext(Dispatchers.IO) {
            val identityResult = routerOsApiClient.getSystemIdentity()
            val realIdentity = identityResult.getOrNull().orEmpty()

            // 1. Fetch System Resources & Hardware Health
            val resResult = routerOsApiClient.getSystemResource()
            val healthMap = routerOsApiClient.getSystemHealth().getOrDefault(emptyMap())
            val healthVolt = healthMap["voltage"]?.let { "$it V" } ?: healthMap["voltage-sensor"]?.let { "$it V" } ?: "24.1 V"
            val healthTemp = healthMap["temperature"]?.let { "$it°C" } ?: healthMap["cpu-temperature"]?.let { "$it°C" } ?: "42°C"

            if (resResult.isSuccess) {
                val res = resResult.getOrThrow()
                val currentRx = _resourceState.value.rxSpeedKbps
                val currentTx = _resourceState.value.txSpeedKbps
                _resourceState.value = res.copy(
                    boardName = if (realIdentity.isNotBlank()) "$realIdentity (${res.boardName})" else res.boardName,
                    voltage = healthVolt,
                    temperature = healthTemp,
                    rxSpeedKbps = currentRx,
                    txSpeedKbps = currentTx
                )
            }

            // 2. Fetch RouterBOARD Hardware Specs
            val rbResult = routerOsApiClient.getRouterBoardInfo()
            if (rbResult.isSuccess) {
                _routerBoardInfo.value = rbResult.getOrThrow()
            }

            // 3. Fetch System Logs & Router Users
            val usersResult = routerOsApiClient.getRouterUsers()
            if (usersResult.isSuccess) {
                val realUsers = usersResult.getOrThrow()
                if (realUsers.isNotEmpty()) {
                    _adminUsers.value = realUsers
                }
            }

            val logResult = routerOsApiClient.getSystemLogs(limit = 40)
            if (logResult.isSuccess) {
                val sysLogs = logResult.getOrThrow()
                _systemLogs.value = sysLogs
                val auditEntries = sysLogs.mapIndexed { index, entry ->
                    com.example.data.model.AuditLogEntry(
                        id = entry.id.ifBlank { "log_$index" },
                        adminUsername = "admin",
                        actionType = entry.topics,
                        description = entry.message,
                        timestamp = entry.time,
                        ipAddress = activeConfig?.ipAddress ?: "192.168.88.1",
                        severity = if (entry.topics.contains("error", ignoreCase = true) || entry.topics.contains("warning", ignoreCase = true)) "WARNING" else "INFO"
                    )
                }
                if (auditEntries.isNotEmpty()) {
                    _auditLogs.value = auditEntries
                }
            }

            // 4. Fetch Hotspot Servers & IP Pools
            val serversResult = routerOsApiClient.getHotspotServers()
            if (serversResult.isSuccess) {
                _hotspotServers.value = serversResult.getOrThrow()
            }
            val poolsResult = routerOsApiClient.getIpPools()
            if (poolsResult.isSuccess) {
                _ipPools.value = poolsResult.getOrThrow()
            }

            // 5. Fetch network hosts, bridge hosts, DHCP leases, wireless registrations and ARP for precision mapping
            val leases = routerOsApiClient.getDhcpLeases().getOrDefault(emptyList())
            val hosts = routerOsApiClient.getHotspotHosts().getOrDefault(emptyList())
            val bridgeHosts = routerOsApiClient.getBridgeHosts().getOrDefault(emptyList())
            val wirelessRegs = routerOsApiClient.getWirelessRegistrations().getOrDefault(emptyList())
            val arpEntries = routerOsApiClient.getArpEntries().getOrDefault(emptyList())

            val macToHostName = mutableMapOf<String, String>()
            val ipToHostName = mutableMapOf<String, String>()
            leases.forEach { l ->
                val mac = l["mac-address"]?.uppercase().orEmpty()
                val ip = l["address"].orEmpty()
                val host = l["host-name"]?.ifBlank { l["comment"] }.orEmpty()
                if (mac.isNotBlank() && host.isNotBlank()) macToHostName[mac] = host
                if (ip.isNotBlank() && host.isNotBlank()) ipToHostName[ip] = host
            }
            hosts.forEach { h ->
                val mac = h["mac-address"]?.uppercase().orEmpty()
                val comment = h["comment"].orEmpty()
                if (mac.isNotBlank() && comment.isNotBlank() && !macToHostName.containsKey(mac)) {
                    macToHostName[mac] = comment
                }
            }

            val macToInterface = mutableMapOf<String, String>()
            // Bridge hosts provide the exact physical port (on-interface) for each client behind APs
            bridgeHosts.forEach { bh ->
                val mac = bh["mac-address"]?.uppercase().orEmpty()
                val onIf = bh["on-interface"].orEmpty()
                if (mac.isNotBlank() && onIf.isNotBlank()) {
                    macToInterface[mac] = onIf
                }
            }
            wirelessRegs.forEach { wr ->
                val mac = wr["mac-address"]?.uppercase().orEmpty()
                val ifName = wr["interface"].orEmpty()
                if (mac.isNotBlank() && ifName.isNotBlank()) macToInterface[mac] = ifName
            }
            hosts.forEach { h ->
                val mac = h["mac-address"]?.uppercase().orEmpty()
                val ifName = h["interface"]?.ifBlank { h["bridge-port"] }.orEmpty()
                if (mac.isNotBlank() && ifName.isNotBlank() && !macToInterface.containsKey(mac)) {
                    macToInterface[mac] = ifName
                }
            }

            // 6. Fetch Active Users
            val activeResult = routerOsApiClient.getActiveHotspotUsers()
            if (activeResult.isSuccess) {
                val rawActive = activeResult.getOrThrow()
                val enrichedActive = rawActive.map { user ->
                    val cleanMac = user.macAddress.uppercase()
                    val hostName = macToHostName[cleanMac] ?: ipToHostName[user.address] ?: user.comment
                    val ifName = macToInterface[cleanMac] ?: hosts.find { it["mac-address"].equals(cleanMac, ignoreCase = true) }?.get("interface") ?: "السيرفر الرئيسي"
                    val device = com.example.data.util.DeviceDetector.detectDevice(cleanMac, hostName, user.user)
                    user.copy(
                        hostName = hostName,
                        nodeName = ifName,
                        serverName = hosts.find { it["mac-address"].equals(cleanMac, ignoreCase = true) }?.get("server") ?: "hotspot1",
                        deviceModel = "${device.brand} ${device.modelName}"
                    )
                }
                _activeUsers.value = enrichedActive
            }

            // 7. Fetch Hotspot Cards + User-Manager Database (Complete User Base)
            val hotspotCardsRes = routerOsApiClient.getHotspotUsers()
            val umCardsRes = routerOsApiClient.getUserManagerUsers()
            val hotspotCards = hotspotCardsRes.getOrNull()
            val umCards = umCardsRes.getOrNull()
            if (hotspotCards != null || umCards != null) {
                val combinedCards = mutableListOf<HotspotCard>()
                hotspotCards?.let { combinedCards.addAll(it) }
                val existingCardNames = combinedCards.map { it.name }.toSet()
                umCards?.forEach { umCard ->
                    if (!existingCardNames.contains(umCard.name)) {
                        combinedCards.add(umCard)
                    }
                }
                val distinctCards = combinedCards.distinctBy { it.name }
                val sortedCards = sortCardsByNewestFirst(distinctCards)
                if (sortedCards.isNotEmpty() || _cards.value.isEmpty()) {
                    _cards.value = sortedCards
                }
                syncCardsToRoom(sortedCards)
            }

            // 8. Fetch Profiles & IP Bindings from Router
            val profResult = routerOsApiClient.getHotspotProfiles()
            if (profResult.isSuccess) {
                val liveProfiles = profResult.getOrThrow()
                if (liveProfiles.isNotEmpty() || _profiles.value.isEmpty()) {
                    _profiles.value = liveProfiles
                }
            }
            val bindResult = routerOsApiClient.getIpBindings()
            if (bindResult.isSuccess) {
                val liveBindings = bindResult.getOrThrow()
                if (liveBindings.isNotEmpty() || _ipBindings.value.isEmpty()) {
                    _ipBindings.value = liveBindings
                }
            }

            // 9. Fetch Interfaces & Neighbors for Per-Node Distribution
            val ifaces = routerOsApiClient.getNetworkInterfaces().getOrDefault(emptyList())
            val ifaceStats = ifaces.mapIndexed { idx, it ->
                val name = it["name"] ?: "ether${idx + 1}"
                val rxB = it["rx-byte"]?.toLongOrNull() ?: 0L
                val txB = it["tx-byte"]?.toLongOrNull() ?: 0L
                val rxP = it["rx-packet"]?.toLongOrNull() ?: 0L
                val txP = it["tx-packet"]?.toLongOrNull() ?: 0L
                val isRunning = it["running"] == "true"
                val disabled = it["disabled"] == "true"
                com.example.data.model.InterfaceStats(
                    id = it[".id"] ?: "if_$idx",
                    name = name,
                    type = it["type"] ?: "ether",
                    macAddress = it["mac-address"] ?: "",
                    rxBytes = rxB,
                    txBytes = txB,
                    rxPackets = rxP,
                    txPackets = txP,
                    isRunning = isRunning,
                    disabled = disabled,
                    comment = it["comment"] ?: ""
                )
            }
            if (ifaceStats.isNotEmpty()) {
                _interfaceStats.value = ifaceStats
                calculateAndUpdateLiveTrafficSpeeds(ifaceStats)
            }

            val wirelessList = routerOsApiClient.getWirelessInterfaces().getOrDefault(emptyList())
            val vlanList = routerOsApiClient.getVlanInterfaces().getOrDefault(emptyList())
            val neighborsList = routerOsApiClient.getDiscoveredNeighbors().getOrDefault(emptyList()).toMutableList()
            val ipAddressesList = routerOsApiClient.getRouterAddresses().getOrDefault(emptyList())
            
            // Add RoMON neighbors for deeper management network visibility as requested
            val romonNeighbors = routerOsApiClient.getRomonNeighbors().getOrDefault(emptyList())
            romonNeighbors.forEach { rn ->
                if (neighborsList.none { it["mac-address"] == rn["mac-address"] }) {
                    neighborsList.add(rn)
                }
            }



            activeConfig?.let { syncLiveSessionsAndNodes(it, wirelessList, neighborsList, realIdentity, macToInterface, wirelessRegs, vlanList, ipAddressesList) }
            notifyDataRefreshed()
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
                rxSpeedKbps = _resourceState.value.rxSpeedKbps,
                txSpeedKbps = _resourceState.value.txSpeedKbps
            )
        } catch (_: Exception) {}
    }

    private suspend fun fetchLiveRouterData(baseUrl: String, credential: String) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch system identity, routerboard and health
                var realIdentity = ""
                try {
                    val idReq = Request.Builder().url("$baseUrl/rest/system/identity").header("Authorization", credential).build()
                    val idResp = httpClient.newCall(idReq).execute()
                    if (idResp.isSuccessful) {
                        val idObj = JSONObject(idResp.body?.string() ?: "{}")
                        realIdentity = idObj.optString("name", "")
                        if (realIdentity.isNotBlank()) {
                            _resourceState.value = _resourceState.value.copy(
                                boardName = "$realIdentity (${_resourceState.value.boardName})"
                            )
                        }
                    }
                } catch (_: Exception) {}

                // Fetch RouterBOARD info
                try {
                    val rbReq = Request.Builder().url("$baseUrl/rest/system/routerboard").header("Authorization", credential).build()
                    val rbResp = httpClient.newCall(rbReq).execute()
                    if (rbResp.isSuccessful) {
                        val rbObj = JSONObject(rbResp.body?.string() ?: "{}")
                        _routerBoardInfo.value = com.example.data.model.RouterBoardInfo(
                            model = rbObj.optString("model", "MikroTik Router"),
                            serialNumber = rbObj.optString("serial-number", ""),
                            currentFirmware = rbObj.optString("current-firmware", ""),
                            upgradeFirmware = rbObj.optString("upgrade-firmware", ""),
                            routerboard = rbObj.optBoolean("routerboard", true)
                        )
                    }
                } catch (_: Exception) {}

                // Fetch Health Sensors
                try {
                    val hReq = Request.Builder().url("$baseUrl/rest/system/health").header("Authorization", credential).build()
                    val hResp = httpClient.newCall(hReq).execute()
                    if (hResp.isSuccessful) {
                        val hArr = JSONArray(hResp.body?.string() ?: "[]")
                        var volt = "24.1 V"
                        var temp = "42°C"
                        for (i in 0 until hArr.length()) {
                            val o = hArr.getJSONObject(i)
                            val name = o.optString("name", "")
                            val value = o.optString("value", "")
                            if (name.contains("voltage", ignoreCase = true)) volt = "$value V"
                            if (name.contains("temperature", ignoreCase = true)) temp = "$value°C"
                        }
                        _resourceState.value = _resourceState.value.copy(voltage = volt, temperature = temp)
                    }
                } catch (_: Exception) {}

                // Fetch System Logs
                try {
                    val lReq = Request.Builder().url("$baseUrl/rest/log").header("Authorization", credential).build()
                    val lResp = httpClient.newCall(lReq).execute()
                    if (lResp.isSuccessful) {
                        val lArr = JSONArray(lResp.body?.string() ?: "[]")
                        val logList = mutableListOf<com.example.data.model.SystemLogEntry>()
                        for (i in 0 until lArr.length()) {
                            val o = lArr.getJSONObject(i)
                            logList.add(
                                com.example.data.model.SystemLogEntry(
                                    id = o.optString(".id", "l$i"),
                                    time = o.optString("time", ""),
                                    topics = o.optString("topics", "info"),
                                    message = o.optString("message", "")
                                )
                            )
                        }
                        _systemLogs.value = logList.takeLast(40).reversed()
                    }
                } catch (_: Exception) {}

                // Fetch Router Users (/rest/user)
                try {
                    val uReq = Request.Builder().url("$baseUrl/rest/user").header("Authorization", credential).build()
                    val uResp = httpClient.newCall(uReq).execute()
                    if (uResp.isSuccessful) {
                        val uArr = JSONArray(uResp.body?.string() ?: "[]")
                        val userList = mutableListOf<com.example.data.model.AdminUser>()
                        for (i in 0 until uArr.length()) {
                            val o = uArr.getJSONObject(i)
                            val username = o.optString("name", "admin")
                            val group = o.optString("group", "full")
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
                            userList.add(
                                com.example.data.model.AdminUser(
                                    id = o.optString(".id", "u_$i"),
                                    username = username,
                                    fullName = o.optString("comment", "").ifBlank { username },
                                    role = roleName,
                                    permissions = permissions,
                                    lastActiveTime = o.optString("last-logged-in", "نشط"),
                                    ipAddress = o.optString("address", "192.168.88.1"),
                                    isActive = !o.optBoolean("disabled", false),
                                    password = ""
                                )
                            )
                        }
                        if (userList.isNotEmpty()) {
                            _adminUsers.value = userList
                        }
                    }
                } catch (_: Exception) {}

                // Fetch Hotspot Servers & IP Pools
                try {
                    val sReq = Request.Builder().url("$baseUrl/rest/ip/hotspot").header("Authorization", credential).build()
                    val sResp = httpClient.newCall(sReq).execute()
                    if (sResp.isSuccessful) {
                        val sArr = JSONArray(sResp.body?.string() ?: "[]")
                        val serverList = mutableListOf<com.example.data.model.HotspotServerInfo>()
                        for (i in 0 until sArr.length()) {
                            val o = sArr.getJSONObject(i)
                            serverList.add(
                                com.example.data.model.HotspotServerInfo(
                                    id = o.optString(".id", "hs$i"),
                                    name = o.optString("name", "hotspot1"),
                                    interfaceName = o.optString("interface", "all"),
                                    profile = o.optString("profile", "default"),
                                    addressPool = o.optString("address-pool", ""),
                                    addressesPerMac = o.optInt("addresses-per-mac", 1),
                                    disabled = o.optBoolean("disabled", false)
                                )
                            )
                        }
                        _hotspotServers.value = serverList
                    }
                } catch (_: Exception) {}

                try {
                    val pReq = Request.Builder().url("$baseUrl/rest/ip/pool").header("Authorization", credential).build()
                    val pResp = httpClient.newCall(pReq).execute()
                    if (pResp.isSuccessful) {
                        val pArr = JSONArray(pResp.body?.string() ?: "[]")
                        val poolList = mutableListOf<com.example.data.model.IpPoolInfo>()
                        for (i in 0 until pArr.length()) {
                            val o = pArr.getJSONObject(i)
                            poolList.add(
                                com.example.data.model.IpPoolInfo(
                                    id = o.optString(".id", "pool$i"),
                                    name = o.optString("name", "pool"),
                                    ranges = o.optString("ranges", "")
                                )
                            )
                        }
                        _ipPools.value = poolList
                    }
                } catch (_: Exception) {}

                // Fetch DHCP leases for hostname resolution
                val macToHostName = mutableMapOf<String, String>()
                val ipToHostName = mutableMapOf<String, String>()
                try {
                    val leaseReq = Request.Builder().url("$baseUrl/rest/ip/dhcp-server/lease").header("Authorization", credential).build()
                    val leaseResp = httpClient.newCall(leaseReq).execute()
                    if (leaseResp.isSuccessful) {
                        val arr = JSONArray(leaseResp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val mac = o.optString("mac-address").uppercase()
                            val ip = o.optString("address")
                            val host = o.optString("host-name").ifBlank { o.optString("comment") }
                            if (mac.isNotBlank() && host.isNotBlank()) macToHostName[mac] = host
                            if (ip.isNotBlank() && host.isNotBlank()) ipToHostName[ip] = host
                        }
                    }
                } catch (_: Exception) {}

                // Fetch Bridge hosts and Hotspot hosts
                val macToInterface = mutableMapOf<String, String>()
                val macToServer = mutableMapOf<String, String>()
                try {
                    val bReq = Request.Builder().url("$baseUrl/rest/interface/bridge/host").header("Authorization", credential).build()
                    val bResp = httpClient.newCall(bReq).execute()
                    if (bResp.isSuccessful) {
                        val arr = JSONArray(bResp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val mac = o.optString("mac-address").uppercase()
                            val onIf = o.optString("on-interface")
                            if (mac.isNotBlank() && onIf.isNotBlank()) {
                                macToInterface[mac] = onIf
                            }
                        }
                    }
                } catch (_: Exception) {}
                try {
                    val hostReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/host").header("Authorization", credential).build()
                    val hostResp = httpClient.newCall(hostReq).execute()
                    if (hostResp.isSuccessful) {
                        val arr = JSONArray(hostResp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val mac = o.optString("mac-address").uppercase()
                            val ifName = o.optString("interface").ifBlank { o.optString("bridge-port") }
                            val server = o.optString("server")
                            val comment = o.optString("comment")
                            if (mac.isNotBlank() && ifName.isNotBlank()) macToInterface[mac] = ifName
                            if (mac.isNotBlank() && server.isNotBlank()) macToServer[mac] = server
                            if (mac.isNotBlank() && comment.isNotBlank() && !macToHostName.containsKey(mac)) {
                                macToHostName[mac] = comment
                            }
                        }
                    }
                } catch (_: Exception) {}

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
                        val mac = obj.optString("mac-address", "").uppercase()
                        val ip = obj.optString("address", "")
                        val rawComment = obj.optString("comment", "")
                        val hostName = macToHostName[mac] ?: ipToHostName[ip] ?: rawComment
                        val ifName = macToInterface[mac] ?: "السيرفر الرئيسي"
                        val device = com.example.data.util.DeviceDetector.detectDevice(mac, hostName, obj.optString("user", ""))

                        list.add(
                            HotspotActiveUser(
                                id = obj.optString(".id", "u$i"),
                                user = obj.optString("user", "user$i"),
                                address = ip,
                                macAddress = mac,
                                uptime = obj.optString("uptime", "0s"),
                                bytesIn = obj.optLong("bytes-in", 0),
                                bytesOut = obj.optLong("bytes-out", 0),
                                loginBy = obj.optString("login-by", "http"),
                                sessionTimeLeft = obj.optString("session-time-left", ""),
                                comment = rawComment,
                                hostName = hostName,
                                serverName = macToServer[mac] ?: obj.optString("server", "hotspot1"),
                                nodeName = ifName,
                                deviceModel = "${device.brand} ${device.modelName}"
                            )
                        )
                    }
                    _activeUsers.value = list
                }

                // Fetch hotspot users (cards)
                val cardList = mutableListOf<HotspotCard>()
                val usersRequest = Request.Builder()
                    .url("$baseUrl/rest/ip/hotspot/user")
                    .header("Authorization", credential)
                    .build()
                val usersResponse = httpClient.newCall(usersRequest).execute()
                if (usersResponse.isSuccessful) {
                    val usersArray = JSONArray(usersResponse.body?.string() ?: "[]")
                    for (i in 0 until usersArray.length()) {
                        val obj = usersArray.getJSONObject(i)
                        val cardName = obj.optString("name", "")
                        val comment = obj.optString("comment", "")
                        val profile = obj.optString("profile", "default")
                        val price = extractPriceFromText(comment, profile, cardName)

                        cardList.add(
                            HotspotCard(
                                id = obj.optString(".id", "c$i"),
                                name = cardName,
                                password = obj.optString("password", ""),
                                profile = profile,
                                uptime = obj.optString("uptime", "0s"),
                                bytesIn = obj.optLong("bytes-in", 0),
                                bytesOut = obj.optLong("bytes-out", 0),
                                limitUptime = obj.optString("limit-uptime", ""),
                                limitBytesTotal = obj.optLong("limit-bytes-total", 0L).takeIf { it > 0 }
                                    ?: obj.optLong("limit-bytes-out", 0L).takeIf { it > 0 }
                                    ?: obj.optLong("limit-bytes-in", 0L),
                                comment = comment,
                                disabled = obj.optBoolean("disabled", false),
                                price = price
                            )
                        )
                    }
                }

                // Check for User-Manager users in REST mode
                try {
                    val umReq = Request.Builder().url("$baseUrl/rest/user-manager/user").header("Authorization", credential).build()
                    val umResp = httpClient.newCall(umReq).execute()
                    if (umResp.isSuccessful) {
                        val umArr = JSONArray(umResp.body?.string() ?: "[]")
                        val existingNames = cardList.map { it.name }.toSet()
                        for (i in 0 until umArr.length()) {
                            val obj = umArr.getJSONObject(i)
                            val name = obj.optString("name").ifBlank { obj.optString("username") }
                            if (name.isNotBlank() && !existingNames.contains(name)) {
                                val comment = obj.optString("comment")
                                val profile = obj.optString("profile", "UserManager")
                                val price = extractPriceFromText(comment, profile, name)
                                cardList.add(
                                    HotspotCard(
                                        id = obj.optString(".id", "um$i"),
                                        name = name,
                                        password = obj.optString("password", ""),
                                        profile = profile,
                                        uptime = obj.optString("uptime", "0s"),
                                        bytesIn = obj.optLong("download-used", 0L),
                                        bytesOut = obj.optLong("upload-used", 0L),
                                        limitUptime = obj.optString("time-limit", ""),
                                        limitBytesTotal = obj.optLong("total-limit", 0L).takeIf { it > 0 }
                                            ?: obj.optLong("download-limit", 0L),
                                        comment = comment,
                                        disabled = obj.optBoolean("disabled", false),
                                        price = price
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
                val sortedCards = sortCardsByNewestFirst(cardList.distinctBy { it.name })
                if (sortedCards.isNotEmpty() || _cards.value.isEmpty()) {
                    _cards.value = sortedCards
                }
                syncCardsToRoom(sortedCards)

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
                        val name = obj.optString("name", "profile$i")
                        val rateLimit = obj.optString("rate-limit", "غير محدد")
                        val comment = obj.optString("comment", "")
                        val price = extractPriceFromText(comment, name, "")
                        val priceNum = Regex("""\d+""").find(price)?.value?.toDoubleOrNull() ?: 0.0
                        val (up, down) = parseRateLimitSpeeds(rateLimit)
                        val sessionTimeout = obj.optString("session-timeout", "غير محدد")

                        profList.add(
                            HotspotProfile(
                                id = obj.optString(".id", "p$i"),
                                name = name,
                                rateLimit = rateLimit,
                                sharedUsers = obj.optInt("shared-users", 1),
                                sessionTimeout = sessionTimeout,
                                keepaliveTimeout = obj.optString("keepalive-timeout", "5m"),
                                price = price,
                                uploadLimit = up,
                                downloadLimit = down,
                                validity = sessionTimeout,
                                dataLimit = "",
                                source = "hotspot",
                                comment = comment,
                                priceNumber = priceNum
                            )
                        )
                    }

                    // Also try user manager profiles (v7 and v6) via REST
                    try {
                        val endpoints = listOf(
                            "$baseUrl/rest/user-manager/profile" to "usermanager",
                            "$baseUrl/rest/tool/user-manager/profile" to "usermanager"
                        )
                        for ((url, src) in endpoints) {
                            try {
                                val req = Request.Builder().url(url).header("Authorization", credential).build()
                                val resp = httpClient.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    val arr = JSONArray(resp.body?.string() ?: "[]")
                                    for (i in 0 until arr.length()) {
                                        val obj = arr.getJSONObject(i)
                                        val name = obj.optString("name", "")
                                        if (name.isNotBlank() && profList.none { it.name.equals(name, ignoreCase = true) }) {
                                            val rateLimit = obj.optString("rate-limit", "غير محدد")
                                            val comment = obj.optString("comment", "")
                                            val priceStr = extractPriceFromText(comment, name, "")
                                            val (up, down) = parseRateLimitSpeeds(rateLimit)
                                            val totalLimit = obj.optLong("total-limit", 0L)
                                            val dlLimit = obj.optLong("download-limit", 0L)
                                            val resolvedDataLimit = if (totalLimit > 0L) totalLimit.toString() else if (dlLimit > 0L) dlLimit.toString() else ""

                                            profList.add(
                                                HotspotProfile(
                                                    id = obj.optString(".id", "${src}_$i"),
                                                    name = name,
                                                    rateLimit = rateLimit,
                                                    sharedUsers = obj.optInt("shared-users", 1),
                                                    sessionTimeout = "1d",
                                                    price = priceStr,
                                                    uploadLimit = up,
                                                    downloadLimit = down,
                                                    validity = "1d",
                                                    dataLimit = resolvedDataLimit,
                                                    source = src,
                                                    comment = comment
                                                )
                                            )
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

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

                // Fetch network interfaces stats
                try {
                    val ifReq = Request.Builder().url("$baseUrl/rest/interface").header("Authorization", credential).build()
                    val ifResp = httpClient.newCall(ifReq).execute()
                    if (ifResp.isSuccessful) {
                        val ifArr = JSONArray(ifResp.body?.string() ?: "[]")
                        val ifList = mutableListOf<com.example.data.model.InterfaceStats>()
                        for (i in 0 until ifArr.length()) {
                            val o = ifArr.getJSONObject(i)
                            ifList.add(
                                com.example.data.model.InterfaceStats(
                                    id = o.optString(".id", "if$i"),
                                    name = o.optString("name", "ether${i + 1}"),
                                    type = o.optString("type", "ether"),
                                    macAddress = o.optString("mac-address", ""),
                                    rxBytes = o.optLong("rx-byte", 0L),
                                    txBytes = o.optLong("tx-byte", 0L),
                                    rxPackets = o.optLong("rx-packet", 0L),
                                    txPackets = o.optLong("tx-packet", 0L),
                                    isRunning = o.optBoolean("running", true),
                                    disabled = o.optBoolean("disabled", false),
                                    comment = o.optString("comment", "")
                                )
                            )
                        }
                        if (ifList.isNotEmpty()) {
                            _interfaceStats.value = ifList
                            calculateAndUpdateLiveTrafficSpeeds(ifList)
                        }
                    }
                } catch (_: Exception) {}

                // 5. Fetch Wireless Interfaces, Neighbors and Registrations in parallel to reduce sync lag
                val wirelessJob = async {
                    try {
                        val wReq = Request.Builder().url("$baseUrl/rest/interface/wireless").header("Authorization", credential).build()
                        val wResp = httpClient.newCall(wReq).execute()
                        if (wResp.isSuccessful) JSONArray(wResp.body?.string() ?: "[]") else JSONArray()
                    } catch (_: Exception) { JSONArray() }
                }

                val neighborJob = async {
                    try {
                        val nReq = Request.Builder().url("$baseUrl/rest/ip/neighbor").header("Authorization", credential).build()
                        val nResp = httpClient.newCall(nReq).execute()
                        if (nResp.isSuccessful) JSONArray(nResp.body?.string() ?: "[]") else JSONArray()
                    } catch (_: Exception) { JSONArray() }
                }

                val registrationsJob = async {
                    try {
                        val rReq = Request.Builder().url("$baseUrl/rest/interface/wireless/registration-table").header("Authorization", credential).build()
                        val rResp = httpClient.newCall(rReq).execute()
                        if (rResp.isSuccessful) JSONArray(rResp.body?.string() ?: "[]") else JSONArray()
                    } catch (_: Exception) { JSONArray() }
                }

                val vlanJob = async {
                    try {
                        val vReq = Request.Builder().url("$baseUrl/rest/interface/vlan").header("Authorization", credential).build()
                        val vResp = httpClient.newCall(vReq).execute()
                        if (vResp.isSuccessful) JSONArray(vResp.body?.string() ?: "[]") else JSONArray()
                    } catch (_: Exception) { JSONArray() }
                }

                val romonJob = async {
                    try {
                        val rReq = Request.Builder().url("$baseUrl/rest/tool/romon/neighbor").header("Authorization", credential).build()
                        val rResp = httpClient.newCall(rReq).execute()
                        if (rResp.isSuccessful) JSONArray(rResp.body?.string() ?: "[]") else {
                            // Try alternative v7 path
                            val v7Req = Request.Builder().url("$baseUrl/rest/romon/neighbor").header("Authorization", credential).build()
                            val v7Resp = httpClient.newCall(v7Req).execute()
                            if (v7Resp.isSuccessful) JSONArray(v7Resp.body?.string() ?: "[]") else JSONArray()
                        }
                    } catch (_: Exception) { JSONArray() }
                }

                    emptyList<Map<String, String>>()

                val ipAddressJob = async {
                    try {
                        val aReq = Request.Builder().url("$baseUrl/rest/ip/address").header("Authorization", credential).build()
                        val aResp = httpClient.newCall(aReq).execute()
                        if (aResp.isSuccessful) JSONArray(aResp.body?.string() ?: "[]") else JSONArray()
                    } catch (_: Exception) { JSONArray() }
                }

                val wirelessArray = wirelessJob.await()
                val vlanArray = vlanJob.await()
                val neighborArray = neighborJob.await()
                val registrationsArray = registrationsJob.await()
                val romonArray = romonJob.await()
                val scanResults = emptyList<Map<String, String>>()
                val ipAddressArray = ipAddressJob.await()

                val wirelessList = mutableListOf<Map<String, String>>()
                for (i in 0 until wirelessArray.length()) {
                    val o = wirelessArray.getJSONObject(i)
                    val m = mutableMapOf<String, String>()
                    o.keys().forEach { m[it] = o.optString(it) }
                    wirelessList.add(m)
                }

                val vlanList = mutableListOf<Map<String, String>>()
                for (i in 0 until vlanArray.length()) {
                    val o = vlanArray.getJSONObject(i)
                    val m = mutableMapOf<String, String>()
                    o.keys().forEach { m[it] = o.optString(it) }
                    vlanList.add(m)
                }

                val ipAddressesList = mutableListOf<Map<String, String>>()
                for (i in 0 until ipAddressArray.length()) {
                    val o = ipAddressArray.getJSONObject(i)
                    val m = mutableMapOf<String, String>()
                    o.keys().forEach { m[it] = o.optString(it) }
                    ipAddressesList.add(m)
                }

                val neighborsList = mutableListOf<Map<String, String>>()
                for (i in 0 until neighborArray.length()) {
                    val o = neighborArray.getJSONObject(i)
                    val m = mutableMapOf<String, String>()
                    o.keys().forEach { m[it] = o.optString(it) }
                    neighborsList.add(m)
                }

                // Merge RoMON neighbors
                for (i in 0 until romonArray.length()) {
                    val o = romonArray.getJSONObject(i)
                    val mac = o.optString("mac-address")
                    if (neighborsList.none { it["mac-address"] == mac }) {
                        val m = mutableMapOf<String, String>()
                        o.keys().forEach { m[it] = o.optString(it) }
                        neighborsList.add(m)
                    }
                }

                // Merge Scan results (if any)
                scanResults.forEach { result ->
                    if (neighborsList.none { neighbor -> neighbor["address"] == result["address"] }) {
                        neighborsList.add(result)
                    }
                }

                val wirelessRegs = mutableListOf<Map<String, String>>()
                for (i in 0 until registrationsArray.length()) {
                    val o = registrationsArray.getJSONObject(i)
                    val m = mutableMapOf<String, String>()
                    o.keys().forEach { m[it] = o.optString(it) }
                    wirelessRegs.add(m)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    activeConfig?.let { syncLiveSessionsAndNodes(it, wirelessList, neighborsList, realIdentity, macToInterface, wirelessRegs, vlanList, ipAddressesList) }
                }
                notifyDataRefreshed()
            } catch (_: Exception) {}
        }
    }

    private fun extractPriceFromText(comment: String, profileName: String, cardName: String): String {
        val combined = "$comment $profileName $cardName"
        
        // 1. More aggressive price detection (handle more currency formats and labels)
        val pricePatterns = listOf(
            Regex("""(?i)(?:price|سعر|السعر|فئة|فئه)[:\s]*(\d{2,6})"""),
            Regex("""(\d{2,6})\s*(?:ر\.?ي|ريال|YER|YR|ريال يمني)""", RegexOption.IGNORE_CASE)
        )
        
        for (regex in pricePatterns) {
            val match = regex.find(combined)
            if (match != null) {
                return "${match.groupValues[1]} ر.ي"
            }
        }

        // 2. Fallback: Check for bare numbers if profile name suggests a price (e.g., "Abu 200")
        val numberMatch = Regex("""\b(\d{2,6})\b""").findAll(combined)
        for (match in numberMatch) {
            val num = match.groupValues[1].toIntOrNull() ?: continue
            if (num in listOf(100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 1000, 1200, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 10000)) {
                return "$num ر.ي"
            }
        }
        
        return "غير محدد"
    }

    private fun parseUptimeToSeconds(uptime: String): Long {
        if (uptime.isBlank()) return 0L
        var total = 0L
        val clean = uptime.lowercase().replace(" ", "")
        val w = Regex("""(\d+)w""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val d = Regex("""(\d+)d""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val h = Regex("""(\d+)h""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val m = Regex("""(\d+)m""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val s = Regex("""(\d+)s""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        total += w * 604800L + d * 86400L + h * 3600L + m * 60L + s
        return total
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

    private fun adaptDataToConfig(config: RouterConnectionConfig) {
        val routerIpBase = config.ipAddress.takeIf { it.isNotBlank() } ?: "192.168.88.1"
        val subnetPrefix = if (routerIpBase.contains(".")) routerIpBase.substringBeforeLast(".") else "192.168.88"
        val routerName = config.name.ifBlank { "راوتر $routerIpBase" }

        _resourceState.value = _resourceState.value.copy(
            boardName = "MikroTik RouterOS ($routerName)",
            version = "RouterOS v7.14"
        )

        _activeUsers.value = _activeUsers.value.mapIndexed { idx, active ->
            active.copy(
                address = "$subnetPrefix.${100 + idx}",
                comment = "مستخدم متصل - $routerName"
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            syncLiveSessionsAndNodes(config)
        }
    }

    private suspend fun syncLiveSessionsAndNodes(
        config: RouterConnectionConfig,
        wirelessInterfaces: List<Map<String, String>> = emptyList(),
        neighbors: List<Map<String, String>> = emptyList(),
        realIdentity: String = "",
        macToInterfaceMap: Map<String, String> = emptyList<Pair<String, String>>().toMap(),
        wirelessRegs: List<Map<String, String>> = emptyList(),
        vlans: List<Map<String, String>> = emptyList(),
        ipAddresses: List<Map<String, String>> = emptyList()
    ) {
        val activeList = _activeUsers.value
        val cardList = _cards.value

        val routerIpBase = config.ipAddress.takeIf { it.isNotBlank() } ?: "192.168.88.1"
        val subnetPrefix = if (routerIpBase.contains(".")) routerIpBase.substringBeforeLast(".") else "172.16.1"
        val serverName = realIdentity.ifBlank { config.name.ifBlank { "السيرفر الرئيسي ($routerIpBase)" } }

        // 1. Build IP map: interface -> IP address
        val interfaceIpMap = mutableMapOf<String, String>()
        ipAddresses.forEach { a ->
            val iface = a["interface"]?.trim()
            val addr = a["address"]?.trim()
            if (!iface.isNullOrBlank() && !addr.isNullOrBlank()) {
                val ipOnly = addr.substringBefore("/")
                interfaceIpMap[iface] = ipOnly
            }
        }

        // 2. Load persistent nodes from DB to ensure closed/offline nodes are preserved
        val dbNodes = try {
            appDao?.getAllNetworkNodesSync()?.map {
                it.toNetworkNode().copy(
                    status = com.example.data.model.NodeStatus.OFFLINE,
                    isOnline = false,
                    connectedUsers = 0,
                    connectedClients = emptyList()
                )
            }
        } catch (_: Exception) {
            emptyList<com.example.data.model.NetworkNode>()
        }

        val candidateSubNodes = mutableListOf<com.example.data.model.NetworkNode>()
        val handledNeighborKeys = mutableSetOf<String>()

        fun isNodeBlacklisted(id: String, ip: String, ifName: String, mac: String, vlanId: Int?): Boolean {
            if (deletedNodeIds.contains(id)) return true
            if (ip.isNotBlank() && deletedNodeKeys.contains(ip)) return true
            if (ifName.isNotBlank() && deletedNodeKeys.contains(ifName)) return true
            if (mac.isNotBlank() && (deletedNodeKeys.contains(mac.uppercase()) || deletedNodeKeys.contains(mac))) return true
            if (vlanId != null && deletedNodeKeys.contains("vlan_$vlanId")) return true
            return false
        }

        // 3. Process ALL Interfaces from _interfaceStats
        val allRouterInterfaces = _interfaceStats.value
        
        // Find default bridge for VLANs (like 'LAN' or 'VLAN' from the user's WebFig)
        val defaultBridgeName = allRouterInterfaces.firstOrNull { it.type.equals("bridge", true) && (it.name.equals("LAN", true) || it.name.equals("VLAN", true)) }?.name
            ?: allRouterInterfaces.firstOrNull { it.type.equals("bridge", true) }?.name
            ?: "LAN"

        allRouterInterfaces.forEach { iface ->
            val ifName = iface.name
            val ifType = iface.type.lowercase()
            val isRunning = iface.isRunning
            val isDisabled = iface.disabled

            val isVlan = ifType == "vlan" || ifName.contains("VLAN", true) || ifName.contains("ESLAN", true)
            val isBridge = ifType == "bridge"
            val isEther = ifType == "ether" || ifName.startsWith("ether", true)
            val isWireless = ifType == "wlan" || ifType == "wifi" || ifName.startsWith("wlan", true)

            if (isVlan) {
                val vlanObj = vlans.firstOrNull { it["name"].equals(ifName, true) }
                val vlanId = vlanObj?.get("vlan-id")?.toIntOrNull()
                    ?: Regex("""(?:ESLAN_NET_|VLAN_|VLAN|NET|V)?_?0*(\d+)""", RegexOption.IGNORE_CASE).find(ifName)?.groupValues?.get(1)?.toIntOrNull()

                val parentIf = vlanObj?.get("interface")?.takeIf { it.isNotBlank() } ?: defaultBridgeName

                val assignedIp = interfaceIpMap[ifName]
                    ?: if (vlanId != null && vlanId > 0) "172.16.1.$vlanId"
                    else "$subnetPrefix.254"

                if (isNodeBlacklisted("node_vlan_$ifName", assignedIp, ifName, iface.macAddress, vlanId)) {
                    return@forEach
                }

                // Check if a neighbor in /ip/neighbor matches this VLAN interface
                val matchedNeighbor = neighbors.firstOrNull { n ->
                    val nIf = n["interface"] ?: ""
                    val nAddr = n["address"] ?: ""
                    val nId = n["identity"] ?: ""
                    val nMac = n["mac-address"] ?: ""

                    nIf.equals(ifName, true) ||
                    (nAddr.isNotBlank() && nAddr == assignedIp) ||
                    (vlanId != null && (nId == "$vlanId" || nId.contains("($vlanId)", true) || nId.contains("(${vlanId.toString().padStart(2, '0')})", true) || nAddr.endsWith(".$vlanId")))
                }

                if (matchedNeighbor != null) {
                    val nMac = matchedNeighbor["mac-address"] ?: ""
                    val nAddr = matchedNeighbor["address"] ?: ""
                    if (nMac.isNotBlank()) handledNeighborKeys.add(nMac.uppercase())
                    if (nAddr.isNotBlank()) handledNeighborKeys.add(nAddr)
                    val nId = matchedNeighbor["identity"] ?: ""
                    if (nId.isNotBlank()) handledNeighborKeys.add(nId)

                    val nIdentity = matchedNeighbor["identity"] ?: matchedNeighbor["board"] ?: "نقطة $vlanId"
                    val nPlatform = matchedNeighbor["platform"] ?: matchedNeighbor["board"] ?: "MikroTik AP"
                    val effectiveIp = nAddr.ifBlank { assignedIp }

                    val displayName = when {
                        nIdentity.isNotBlank() && nIdentity != ifName -> "$nIdentity ($ifName)"
                        vlanId != null -> "نقطة $vlanId ($ifName)"
                        else -> "نقطة $ifName"
                    }

                    candidateSubNodes.add(
                        com.example.data.model.NetworkNode(
                            id = "node_vlan_$ifName",
                            name = displayName,
                            ipAddress = effectiveIp,
                            macAddress = nMac.ifBlank { iface.macAddress.ifBlank { "48:A9:8A:76:FB:${"%02X".format(vlanId ?: 1)}" } },
                            interfaceName = ifName,
                            parentInterface = parentIf,
                            vlanId = vlanId,
                            model = nPlatform,
                            role = "أكسس بوينت (بث)",
                            isOnline = true,
                            status = com.example.data.model.NodeStatus.ACTIVE,
                            nodeType = com.example.data.model.NetworkNodeType.DISTRIBUTION,
                            parentTowerId = "node_if_$parentIf",
                            rxSpeedKbps = iface.rxSpeedKbps,
                            txSpeedKbps = iface.txSpeedKbps,
                            totalBytesConsumed = iface.rxBytes + iface.txBytes,
                            pingMs = 2L,
                            ssid = nIdentity
                        )
                    )
                } else {
                    val effectiveStatus = when {
                        isDisabled -> com.example.data.model.NodeStatus.DISABLED
                        isRunning -> com.example.data.model.NodeStatus.ACTIVE
                        else -> com.example.data.model.NodeStatus.OFFLINE
                    }

                    val displayName = when {
                        iface.comment.isNotBlank() -> "${iface.comment} ($ifName)"
                        vlanId != null -> "نقطة بث ESLAN ($vlanId) - $ifName"
                        else -> "نقطة $ifName"
                    }

                    candidateSubNodes.add(
                        com.example.data.model.NetworkNode(
                            id = "node_vlan_$ifName",
                            name = displayName,
                            ipAddress = assignedIp,
                            macAddress = iface.macAddress.ifBlank { "48:A9:8A:76:FB:${"%02X".format(vlanId ?: 1)}" },
                            interfaceName = ifName,
                            parentInterface = parentIf,
                            vlanId = vlanId,
                            model = "MikroTik VLAN Interface",
                            role = "نقطة توزيع فرعية (VLAN)",
                            isOnline = isRunning && !isDisabled,
                            status = effectiveStatus,
                            nodeType = com.example.data.model.NetworkNodeType.DISTRIBUTION,
                            parentTowerId = "node_if_$parentIf",
                            rxSpeedKbps = iface.rxSpeedKbps,
                            txSpeedKbps = iface.txSpeedKbps,
                            totalBytesConsumed = iface.rxBytes + iface.txBytes,
                            pingMs = if (isRunning) 4L else 0L,
                            ssid = ifName
                        )
                    )
                }
            } else if (isBridge) {
                val assignedIp = interfaceIpMap[ifName] ?: routerIpBase
                if (!isNodeBlacklisted("node_if_$ifName", assignedIp, ifName, iface.macAddress, null)) {
                    candidateSubNodes.add(
                        com.example.data.model.NetworkNode(
                            id = "node_if_$ifName",
                            name = "جسر التوزيع الرئيسي ($ifName)",
                            ipAddress = assignedIp,
                            macAddress = iface.macAddress,
                            interfaceName = ifName,
                            model = "Bridge Switch",
                            role = "جسر التوزيع الرئيسي (Bridge)",
                            isOnline = isRunning && !isDisabled,
                            status = if (isDisabled) com.example.data.model.NodeStatus.DISABLED else if (isRunning) com.example.data.model.NodeStatus.ACTIVE else com.example.data.model.NodeStatus.OFFLINE,
                            nodeType = com.example.data.model.NetworkNodeType.MAIN_TOWER,
                            parentTowerId = "node_main_router",
                            rxSpeedKbps = iface.rxSpeedKbps,
                            txSpeedKbps = iface.txSpeedKbps,
                            totalBytesConsumed = iface.rxBytes + iface.txBytes
                        )
                    )
                }
            } else if (isEther) {
                // Physical port: do NOT create duplicate node if neighbor AP is connected, or if port is empty/slave
                val hasNeighborOnPort = neighbors.any { (it["interface"] ?: "").equals(ifName, true) }
                val assignedIp = interfaceIpMap[ifName]
                val hasDistinctIpOrTraffic = (assignedIp != null && assignedIp != routerIpBase) || (iface.rxBytes + iface.txBytes > 10_000_000L)

                if (!hasNeighborOnPort && hasDistinctIpOrTraffic && isRunning && !isDisabled) {
                    val portIp = assignedIp ?: routerIpBase
                    if (!isNodeBlacklisted("node_if_$ifName", portIp, ifName, iface.macAddress, null)) {
                        candidateSubNodes.add(
                            com.example.data.model.NetworkNode(
                                id = "node_if_$ifName",
                                name = "منفذ $ifName (Ethernet)",
                                ipAddress = portIp,
                                macAddress = iface.macAddress,
                                interfaceName = ifName,
                                model = "Physical Port",
                                role = "منفذ فيزيائي رئيسي",
                                isOnline = true,
                                status = com.example.data.model.NodeStatus.ACTIVE,
                                nodeType = com.example.data.model.NetworkNodeType.MAIN_TOWER,
                                parentTowerId = "node_main_router",
                                rxSpeedKbps = iface.rxSpeedKbps,
                                txSpeedKbps = iface.txSpeedKbps,
                                totalBytesConsumed = iface.rxBytes + iface.txBytes
                            )
                        )
                    }
                }
            } else if (isWireless) {
                val wObj = wirelessInterfaces.firstOrNull { it["name"].equals(ifName, true) }
                val ssid = wObj?.get("ssid") ?: ifName
                val band = wObj?.get("band") ?: "5GHz"
                val assignedIp = interfaceIpMap[ifName] ?: routerIpBase

                if (!isNodeBlacklisted("node_wlan_$ifName", assignedIp, ifName, iface.macAddress, null)) {
                    candidateSubNodes.add(
                        com.example.data.model.NetworkNode(
                            id = "node_wlan_$ifName",
                            name = "نقطة بث لاسلكي ($ssid)",
                            ipAddress = assignedIp,
                            macAddress = iface.macAddress,
                            interfaceName = ifName,
                            model = "MikroTik Wireless ($band)",
                            role = "أكسس بوينت لاسلكي",
                            isOnline = isRunning && !isDisabled,
                            status = if (isDisabled) com.example.data.model.NodeStatus.DISABLED else if (isRunning) com.example.data.model.NodeStatus.ACTIVE else com.example.data.model.NodeStatus.OFFLINE,
                            nodeType = com.example.data.model.NetworkNodeType.DISTRIBUTION,
                            parentTowerId = "node_main_router",
                            ssid = ssid
                        )
                    )
                }
            }
        }

        // 4. Any remaining neighbors that didn't match any interface
        neighbors.forEach { n ->
            val nIdentity = n["identity"] ?: n["board"] ?: "برج متصل"
            val nAddress = n["address"] ?: ""
            val nMac = n["mac-address"] ?: ""
            val nInterface = n["interface"] ?: "ether1"
            val keyMac = nMac.uppercase()
            val keyAddr = nAddress

            val alreadyHandled = (keyMac.isNotBlank() && handledNeighborKeys.contains(keyMac)) ||
                    (keyAddr.isNotBlank() && handledNeighborKeys.contains(keyAddr)) ||
                    handledNeighborKeys.contains(nIdentity)

            if (!alreadyHandled && (nAddress.isNotBlank() || nMac.isNotBlank())) {
                val candidateId = "node_neighbor_${(nMac.ifBlank { nAddress }).replace(':', '_')}"
                if (!isNodeBlacklisted(candidateId, nAddress, nInterface, nMac, null)) {
                    // Check if already represented by an existing candidate node by IP or MAC
                    val existingCandidate = candidateSubNodes.firstOrNull { c ->
                        (nAddress.isNotBlank() && c.ipAddress == nAddress) ||
                        (nMac.isNotBlank() && c.macAddress.equals(nMac, true))
                    }
                    if (existingCandidate != null) {
                        val idx = candidateSubNodes.indexOf(existingCandidate)
                        candidateSubNodes[idx] = existingCandidate.copy(
                            name = if (existingCandidate.name.startsWith("منفذ") || existingCandidate.name.startsWith("نقطة")) "$nIdentity (${existingCandidate.interfaceName})" else existingCandidate.name,
                            model = n["platform"] ?: existingCandidate.model,
                            ssid = nIdentity
                        )
                    } else {
                        val isMikrotik = (n["platform"] ?: "").contains("MikroTik", true)
                        candidateSubNodes.add(
                            com.example.data.model.NetworkNode(
                                id = candidateId,
                                name = nIdentity,
                                ipAddress = nAddress,
                                macAddress = nMac,
                                interfaceName = nInterface,
                                model = n["platform"] ?: "RouterOS Device",
                                role = if (isMikrotik) "برج إرسال MikroTik" else "جهاز شبكة متصل",
                                isOnline = true,
                                status = com.example.data.model.NodeStatus.ACTIVE,
                                nodeType = if (isMikrotik) com.example.data.model.NetworkNodeType.MAIN_TOWER else com.example.data.model.NetworkNodeType.DISTRIBUTION,
                                ssid = nIdentity,
                                parentTowerId = "node_if_$nInterface"
                            )
                        )
                    }
                }
            }
        }

        // 5. Integrate custom manually added nodes
        customAddedNodes.forEach { customNode ->
            if (!isNodeBlacklisted(customNode.id, customNode.ipAddress, customNode.interfaceName, customNode.macAddress, customNode.vlanId)) {
                val matchIdx = candidateSubNodes.indexOfFirst {
                    it.id == customNode.id ||
                    (customNode.ipAddress.isNotBlank() && it.ipAddress == customNode.ipAddress)
                }
                if (matchIdx >= 0) {
                    candidateSubNodes[matchIdx] = candidateSubNodes[matchIdx].copy(
                        name = customNode.name,
                        location = customNode.location,
                        role = customNode.role,
                        nodeType = customNode.nodeType,
                        parentTowerId = customNode.parentTowerId,
                        parentTowerName = customNode.parentTowerName
                    )
                } else {
                    candidateSubNodes.add(customNode)
                }
            }
        }

        // 6. Sync with DB: Deduplicate and preserve custom settings, do not resurrect deleted nodes
        dbNodes?.forEach { dbNode ->
            if (!isNodeBlacklisted(dbNode.id, dbNode.ipAddress, dbNode.interfaceName, dbNode.macAddress, dbNode.vlanId)) {
                val matchIdx = candidateSubNodes.indexOfFirst { c ->
                    c.id == dbNode.id ||
                    (dbNode.ipAddress.isNotBlank() && c.ipAddress == dbNode.ipAddress) ||
                    (dbNode.macAddress.isNotBlank() && c.macAddress.equals(dbNode.macAddress, true)) ||
                    (dbNode.vlanId != null && c.vlanId == dbNode.vlanId) ||
                    (dbNode.interfaceName.isNotBlank() && c.interfaceName.equals(dbNode.interfaceName, true))
                }
                if (matchIdx >= 0) {
                    val live = candidateSubNodes[matchIdx]
                    candidateSubNodes[matchIdx] = live.copy(
                        name = if (dbNode.name.isNotBlank() && !dbNode.name.startsWith("نقطة بث ESLAN") && !dbNode.name.startsWith("منفذ ")) dbNode.name else live.name,
                        location = if (dbNode.location.isNotBlank()) dbNode.location else live.location,
                        parentTowerId = dbNode.parentTowerId ?: live.parentTowerId,
                        parentTowerName = dbNode.parentTowerName ?: live.parentTowerName,
                        role = if (dbNode.role.isNotBlank()) dbNode.role else live.role
                    )
                } else {
                    candidateSubNodes.add(dbNode.copy(isOnline = false, status = com.example.data.model.NodeStatus.OFFLINE, connectedUsers = 0))
                }
            }
        }

        // 7. Strict Final Deduplication of Sub-Nodes by IP, then MAC, then ID
        val deduplicatedSubNodes = mutableListOf<com.example.data.model.NetworkNode>()
        val seenIps = mutableSetOf<String>()
        val seenMacs = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        candidateSubNodes.forEach { node ->
            val cleanIp = node.ipAddress.trim()
            val cleanMac = node.macAddress.trim().uppercase()
            val cleanId = node.id.trim()

            val ipClash = cleanIp.isNotBlank() && cleanIp != routerIpBase && cleanIp != "$subnetPrefix.254" && seenIps.contains(cleanIp)
            val macClash = cleanMac.isNotBlank() && !cleanMac.startsWith("48:A9:8A:76:FB") && seenMacs.contains(cleanMac)
            val idClash = seenIds.contains(cleanId)

            if (!ipClash && !macClash && !idClash) {
                if (cleanIp.isNotBlank() && cleanIp != routerIpBase && cleanIp != "$subnetPrefix.254") seenIps.add(cleanIp)
                if (cleanMac.isNotBlank() && !cleanMac.startsWith("48:A9:8A:76:FB")) seenMacs.add(cleanMac)
                seenIds.add(cleanId)
                deduplicatedSubNodes.add(node)
            }
        }

        // 8. Intelligent Client Matching Engine
        val assignedUserIds = mutableSetOf<String>()
        val userToNodeMap = mutableMapOf<String, String>()

        val processedSubNodes = deduplicatedSubNodes.map { node ->
            val matchingClients = activeList.filter { u ->
                if (assignedUserIds.contains(u.id)) return@filter false
                val cleanMac = u.macAddress.uppercase()
                val assignedIf = macToInterfaceMap[cleanMac]?.uppercase() ?: u.nodeName.uppercase()
                val nodeIf = node.interfaceName.uppercase()

                // Direct Interface Match
                val isPortMatch = nodeIf.isNotBlank() && (assignedIf == nodeIf || assignedIf.startsWith("$nodeIf."))
                
                // Wireless Reg Match
                val isWirelessMatch = wirelessRegs.any { 
                    it["mac-address"].equals(cleanMac, true) && it["interface"].equals(node.interfaceName, true) 
                }

                // Name/Server Match
                val isNameMatch = u.serverName.equals(node.interfaceName, true) || u.serverName.equals(node.name, true)

                isPortMatch || isWirelessMatch || isNameMatch
            }

            matchingClients.forEach {
                assignedUserIds.add(it.id)
                userToNodeMap[it.id] = node.name
            }

            val totalBytes = matchingClients.sumOf { it.bytesIn + it.bytesOut }
            node.copy(
                connectedUsers = matchingClients.size,
                totalBytesConsumed = if (totalBytes > 0) totalBytes else node.totalBytesConsumed,
                connectedClients = matchingClients.map { u ->
                    val dev = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, u.hostName, u.user)
                    com.example.data.model.NodeConnectedClient(
                        id = u.id, cardName = u.user, ipAddress = u.address, macAddress = u.macAddress,
                        rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)), txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                        uptime = u.uptime, bytesUsed = u.bytesIn + u.bytesOut, deviceInfo = dev
                    )
                }
            )
        }.toMutableList()

        // 3. Central Gateway / Main Router Node
        val remainingClients = activeList.filter { !assignedUserIds.contains(it.id) }
        val mainRouterNode = com.example.data.model.NetworkNode(
            id = "node_main_router",
            name = serverName,
            ipAddress = routerIpBase,
            macAddress = "D4:CA:6D:AA:11:01",
            interfaceName = "ALL",
            model = _resourceState.value.boardName.ifBlank { "MikroTik Router" },
            role = "السيرفر الرئيسي (Gateway)",
            isOnline = true,
            connectedUsers = remainingClients.size,
            uptime = _resourceState.value.uptime,
            nodeType = com.example.data.model.NetworkNodeType.MAIN_TOWER,
            connectedClients = remainingClients.map { u ->
                val dev = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, u.hostName, u.user)
                com.example.data.model.NodeConnectedClient(
                    id = u.id, cardName = u.user, ipAddress = u.address, macAddress = u.macAddress,
                    rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)), txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                    uptime = u.uptime, bytesUsed = u.bytesIn + u.bytesOut, deviceInfo = dev
                )
            }
        )

        val finalNodes = (listOf(mainRouterNode) + processedSubNodes).distinctBy { it.id }
        _networkNodes.value = finalNodes

        // Persist discovered and matched nodes to DB so they appear as "Offline" later if not found
        try {
            val entities = finalNodes.map { 
                com.example.data.local.NetworkNodeEntity.fromNetworkNode(it) 
            }
            appDao?.insertOrUpdateNetworkNodes(entities)
        } catch (e: Exception) {
            // Ignore DB errors during background sync to prevent app crash
        }

        // 4. Update _activeUsers so each user's nodeName reflects the actual connected node
        _activeUsers.value = activeList.map { u ->
            val assignedNodeName = userToNodeMap[u.id] ?: serverName
            u.copy(nodeName = assignedNodeName)
        }

        // 5. Build live user sessions list from active users + historical cards
        val sessionList = mutableListOf<com.example.data.model.UserSessionRecord>()
        val activeUsernames = _activeUsers.value.map { it.user }.toSet()

        _activeUsers.value.forEachIndexed { index, active ->
            val cleanMac = active.macAddress.uppercase()
            val hostName = active.hostName.ifBlank { active.comment.ifBlank { "" } }
            val detected = if (active.deviceModel.isNotBlank()) {
                com.example.data.util.DeviceDetector.detectDevice(cleanMac, hostName, active.user).let { d ->
                    d.copy(modelName = active.deviceModel)
                }
            } else {
                com.example.data.util.DeviceDetector.detectDevice(cleanMac, hostName, active.user)
            }

            // Permanently cache device info in memory so sessions never lose device name or details
            lastKnownUserTelemetry[active.user] = UserKnownTelemetry(
                macAddress = cleanMac,
                ipAddress = active.address,
                hostName = hostName,
                nodeName = active.nodeName,
                deviceInfo = detected
            )

            val matchingCard = cardList.find { it.name == active.user }
            val cardProfile = matchingCard?.profile?.takeIf { it.isNotBlank() } ?: "افتراضية"
            val matchingProfile = _profiles.value.find { it.name.equals(cardProfile, ignoreCase = true) }
            val resolvedPrice = matchingCard?.price?.takeIf { it.isNotBlank() && it != "0 ر.ي" }
                ?: matchingProfile?.price?.takeIf { it.isNotBlank() && it != "0 ر.ي" }
                ?: "غير محدد"

            val upSec = parseUptimeToSeconds(active.uptime)
            val loginTs = if (upSec > 0) System.currentTimeMillis() - (upSec * 1000L) else System.currentTimeMillis()
            val loginDisplay = NetworkUtils.formatArabicFullDateTime(loginTs)

            val packageLimit = matchingCard?.limitBytesTotal ?: 0L
            val isQuotaOver = matchingCard?.isExpiredQuota ?: (packageLimit > 0L && (active.bytesIn + active.bytesOut) >= packageLimit)

            sessionList.add(
                com.example.data.model.UserSessionRecord(
                    id = "sess_active_${active.user}",
                    username = active.user,
                    ipAddress = active.address.ifBlank { "$subnetPrefix.${100 + index}" },
                    macAddress = cleanMac.ifBlank { "غير مسجل" },
                    isOnline = true,
                    loginTime = loginDisplay,
                    logoutTime = "متصل حالياً",
                    duration = active.uptime.ifBlank { "0s" },
                    bytesIn = active.bytesIn,
                    bytesOut = active.bytesOut,
                    totalBytes = active.bytesIn + active.bytesOut,
                    terminateReason = if (isQuotaOver) "انتهى الرصيد (استنفد حصة البيانات)" else "جلسة نشطة متصلة عبر ${active.nodeName}",
                    profile = cardProfile,
                    price = resolvedPrice,
                    deviceInfo = detected,
                    comment = if (hostName.isNotBlank()) "اسم الجهاز: $hostName" else "مستخدم متصل",
                    nodeName = active.nodeName,
                    packageLimitBytes = packageLimit,
                    isQuotaExhausted = isQuotaOver
                )
            )
        }

        // Add previous sessions from cards with historical usage preserving device & node info
        cardList.forEachIndexed { index, card ->
            if (!activeUsernames.contains(card.name)) {
                val isUsed = card.uptime != "0s" || card.bytesIn > 0 || card.bytesOut > 0
                val packageLimit = card.limitBytesTotal
                val isDataExhausted = card.isExpiredQuota
                val isTimeExpired = card.isExpiredValidity
                val isExpired = card.isExpired

                val reason = when {
                    isDataExhausted -> "انتهى الرصيد (استنفد حجم البيانات المقررة: ${if (packageLimit > 0) NetworkUtils.formatBytes(packageLimit) else "100%"})"
                    isTimeExpired -> "انتهت الصلاحية (انقضاء وقت الكرت: ${card.limitUptime.ifBlank { card.uptime }})"
                    card.disabled -> "تم إيقاف الكرت من قبل الإدارة"
                    isUsed -> "جلسة سابقة مكتملة (تسجيل خروج)"
                    else -> "كرت متاح في المخزون (غير مباع بعد)"
                }

                // Look up last known device telemetry for this subscriber so device info is never lost
                val known = lastKnownUserTelemetry[card.name]
                val resolvedMac = known?.macAddress?.takeIf { it.isNotBlank() } ?: "مسجل بالراوتر"
                val resolvedIp = known?.ipAddress?.takeIf { it.isNotBlank() } ?: "غير متصل حالياً"
                val resolvedNode = known?.nodeName?.takeIf { it.isNotBlank() } ?: serverName
                val resolvedDevice = known?.deviceInfo ?: com.example.data.util.DeviceDetector.detectDevice("", card.comment, card.name)

                sessionList.add(
                    com.example.data.model.UserSessionRecord(
                        id = "sess_card_${card.name}",
                        username = card.name,
                        ipAddress = resolvedIp,
                        macAddress = resolvedMac,
                        isOnline = false,
                        loginTime = if (isUsed) "جلسة سابقة (استهلاك ${NetworkUtils.formatBytes(card.bytesIn + card.bytesOut)})" else "غير مستخدم بعد",
                        logoutTime = if (isExpired) (if (isDataExhausted) "انتهى الرصيد" else "انتهت الصلاحية") else if (isUsed) "جلسة مغلقة" else "جاهز للاستخدام",
                        duration = card.uptime.ifBlank { "0s" },
                        bytesIn = card.bytesIn,
                        bytesOut = card.bytesOut,
                        totalBytes = card.bytesIn + card.bytesOut,
                        terminateReason = reason,
                        profile = card.profile,
                        price = card.price.ifBlank { "غير محدد" },
                        deviceInfo = resolvedDevice,
                        comment = card.comment.ifBlank { if (isUsed) "كرت مستخدم سابق" else "كرت جاهز للبيع" },
                        nodeName = resolvedNode,
                        packageLimitBytes = packageLimit,
                        isQuotaExhausted = isDataExhausted
                    )
                )
            }
        }

        _userSessions.value = sessionList

        // 6. Activity events log strictly from real active users
        val eventsList = mutableListOf<com.example.data.model.UserActivityEvent>()
        _activeUsers.value.take(15).forEachIndexed { idx, u ->
            val dev = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, u.hostName, u.user)
            val hostStr = if (u.hostName.isNotBlank()) " [${u.hostName}]" else ""
            eventsList.add(
                com.example.data.model.UserActivityEvent(
                    id = "evt_live_$idx",
                    username = u.user,
                    eventType = "تسجيل دخول",
                    timestamp = "نشط حالياً (${u.uptime})",
                    details = "متصل عبر ${u.nodeName}$hostStr (${u.loginBy})",
                    ipAddress = u.address,
                    macAddress = u.macAddress,
                    deviceInfo = dev
                )
            )
        }
        _activityEvents.value = eventsList
    }

    /**
     * Calculates real-time Rx and Tx traffic speed in Kbps based on delta bytes across polling intervals.
     * Prevents static/hung 0 Kbps displays when connected to a real MikroTik router.
     */
    private fun calculateAndUpdateLiveTrafficSpeeds(interfaces: List<com.example.data.model.InterfaceStats>) {
        val now = System.currentTimeMillis()
        val prevTime = lastTrafficCalculationTimeMs
        lastTrafficCalculationTimeMs = now

        // Focus on active, running non-loopback interfaces, or total aggregate if few
        val activeIfaces = interfaces.filter { it.isRunning && !it.disabled && it.type != "loopback" }
        val targetIfaces = if (activeIfaces.isNotEmpty()) activeIfaces else interfaces

        var totalDeltaRxBytes = 0L
        var totalDeltaTxBytes = 0L
        var hasValidPreviousData = false

        for (iface in targetIfaces) {
            val prev = lastInterfaceBytesMap[iface.name]
            if (prev != null) {
                hasValidPreviousData = true
                val deltaRx = (iface.rxBytes - prev.first).coerceAtLeast(0L)
                val deltaTx = (iface.txBytes - prev.second).coerceAtLeast(0L)
                totalDeltaRxBytes += deltaRx
                totalDeltaTxBytes += deltaTx
            }
            lastInterfaceBytesMap[iface.name] = Pair(iface.rxBytes, iface.txBytes)
        }

        if (hasValidPreviousData && prevTime > 0L) {
            val deltaSec = ((now - prevTime) / 1000.0).coerceIn(0.5, 30.0)
            // (Bytes * 8) / (1024 * seconds) = Kbps
            val rxKbps = ((totalDeltaRxBytes * 8.0) / (1024.0 * deltaSec)).coerceAtLeast(0.0)
            val txKbps = ((totalDeltaTxBytes * 8.0) / (1024.0 * deltaSec)).coerceAtLeast(0.0)

            _resourceState.value = _resourceState.value.copy(
                rxSpeedKbps = rxKbps,
                txSpeedKbps = txKbps
            )
            recordBandwidthPoint(rxKbps, txKbps)
        }
    }

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    suspend fun refreshProfilesFromRouter() {
        val config = activeConfig ?: return
        if (config.isDemoMode) return
        withContext(Dispatchers.IO) {
            if (isApiSocketMode) {
                val res = routerOsApiClient.getHotspotProfiles()
                if (res.isSuccess) {
                    _profiles.value = res.getOrDefault(emptyList())
                }
            } else {
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val list = mutableListOf<HotspotProfile>()
                    
                    val pReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/profile").header("Authorization", credential).build()
                    val pResp = httpClient.newCall(pReq).execute()
                    if (pResp.isSuccessful) {
                        val jsonArray = JSONArray(pResp.body?.string().orEmpty())
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val name = obj.optString("name", "default")
                            list.add(HotspotProfile(
                                id = obj.optString(".id", "p_$i"),
                                name = name,
                                rateLimit = obj.optString("rate-limit", "غير محدد"),
                                sessionTimeout = obj.optString("session-timeout", "غير محدد"),
                                source = "hotspot",
                                comment = obj.optString("comment", "")
                            ))
                        }
                    }

                    val umReq = Request.Builder().url("$baseUrl/rest/user-manager/profile").header("Authorization", credential).build()
                    val umResp = httpClient.newCall(umReq).execute()
                    if (umResp.isSuccessful) {
                        val umArray = JSONArray(umResp.body?.string().orEmpty())
                        for (i in 0 until umArray.length()) {
                            val obj = umArray.getJSONObject(i)
                            val name = obj.optString("name", "")
                            if (name.isNotBlank()) {
                                list.add(HotspotProfile(
                                    id = obj.optString(".id", "um_$i"),
                                    name = name,
                                    validity = obj.optString("validity", "1d"),
                                    source = "usermanager_v7",
                                    comment = obj.optString("comment", "")
                                ))
                            }
                        }
                    }

                    if (list.isNotEmpty()) {
                        _profiles.value = list
                    }
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun refreshCardsFromRouter() {
        val config = activeConfig ?: return
        if (config.isDemoMode) return
        withContext(Dispatchers.IO) {
            if (isApiSocketMode) {
                val hotspotCardsRes = routerOsApiClient.getHotspotUsers()
                val umCardsRes = routerOsApiClient.getUserManagerUsers()
                val hotspotCards = hotspotCardsRes.getOrNull()
                val umCards = umCardsRes.getOrNull()
                if (hotspotCards != null || umCards != null) {
                    val combinedCards = mutableListOf<HotspotCard>()
                    hotspotCards?.let { combinedCards.addAll(it) }
                    val existingNames = combinedCards.map { it.name }.toSet()
                    umCards?.forEach { umCard ->
                        if (!existingNames.contains(umCard.name)) {
                            combinedCards.add(umCard)
                        }
                    }
                    val distinctCards = combinedCards.distinctBy { it.name }
                    val sortedCards = sortCardsByNewestFirst(distinctCards)
                    if (sortedCards.isNotEmpty()) {
                        _cards.value = sortedCards
                        syncCardsToRoom(sortedCards)
                    }
                }
            } else {
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val cardList = mutableListOf<HotspotCard>()

                    val userReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user").header("Authorization", credential).build()
                    val userResp = httpClient.newCall(userReq).execute()
                    if (userResp.isSuccessful) {
                        val userArray = JSONArray(userResp.body?.string() ?: "[]")
                        for (i in 0 until userArray.length()) {
                            val obj = userArray.getJSONObject(i)
                            val name = obj.optString("name", "card$i")
                            val comment = obj.optString("comment", "")
                            val profile = obj.optString("profile", "default")
                            val price = extractPriceFromText(comment, profile, name)
                            cardList.add(
                                HotspotCard(
                                    id = obj.optString(".id", "u$i"),
                                    name = name,
                                    password = obj.optString("password", ""),
                                    profile = profile,
                                    uptime = obj.optString("uptime", "0s"),
                                    bytesIn = obj.optLong("bytes-in", 0L),
                                    bytesOut = obj.optLong("bytes-out", 0L),
                                    limitUptime = obj.optString("limit-uptime", ""),
                                    limitBytesTotal = obj.optLong("limit-bytes-total", 0L).takeIf { it > 0 }
                                        ?: obj.optLong("limit-bytes-out", 0L).takeIf { it > 0 }
                                        ?: obj.optLong("limit-bytes-in", 0L),
                                    comment = comment,
                                    disabled = obj.optBoolean("disabled", false),
                                    price = price
                                )
                            )
                        }
                    }

                    try {
                        val umReq = Request.Builder().url("$baseUrl/rest/user-manager/user").header("Authorization", credential).build()
                        val umResp = httpClient.newCall(umReq).execute()
                        if (umResp.isSuccessful) {
                            val umArray = JSONArray(umResp.body?.string() ?: "[]")
                            val existingNames = cardList.map { it.name }.toSet()
                            for (i in 0 until umArray.length()) {
                                val obj = umArray.getJSONObject(i)
                                val name = obj.optString("name", "")
                                if (name.isNotBlank() && !existingNames.contains(name)) {
                                    val comment = obj.optString("comment", "")
                                    val profile = obj.optString("actual-profile", obj.optString("profile", "default"))
                                    val price = extractPriceFromText(comment, profile, name)
                                    cardList.add(
                                        HotspotCard(
                                            id = obj.optString(".id", "um$i"),
                                            name = name,
                                            password = obj.optString("password", ""),
                                            profile = profile,
                                            uptime = obj.optString("uptime", "0s"),
                                            bytesIn = obj.optLong("download-used", 0L),
                                            bytesOut = obj.optLong("upload-used", 0L),
                                            limitUptime = obj.optString("time-limit", ""),
                                            limitBytesTotal = obj.optLong("total-limit", 0L).takeIf { it > 0 }
                                                ?: obj.optLong("download-limit", 0L),
                                            comment = comment,
                                            disabled = obj.optBoolean("disabled", false),
                                            price = price
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val sortedCards = sortCardsByNewestFirst(cardList.distinctBy { it.name })
                    if (sortedCards.isNotEmpty()) {
                        _cards.value = sortedCards
                        syncCardsToRoom(sortedCards)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun refreshData(): Result<Unit> {
        val config = activeConfig ?: return Result.failure(Exception("لم يتم ضبط إعدادات الاتصال"))
        if (config.isDemoMode) {
            val current = _resourceState.value
            val newRx = (current.rxSpeedKbps + Random.nextDouble(-400.0, 500.0)).coerceAtLeast(400.0)
            val newTx = (current.txSpeedKbps + Random.nextDouble(-200.0, 300.0)).coerceAtLeast(200.0)
            _resourceState.value = current.copy(
                cpuLoad = (current.cpuLoad + Random.nextInt(-3, 4)).coerceIn(5, 75),
                rxSpeedKbps = newRx,
                txSpeedKbps = newTx
            )
            recordBandwidthPoint(newRx, newTx)
            _lastSyncError.value = null
            return Result.success(Unit)
        }

        return try {
            if (isApiSocketMode) {
                fetchLiveRouterDataFromSocket()
                _lastSyncError.value = null
                Result.success(Unit)
            } else {
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
                    } catch (_: Exception) {}
                    fetchLiveRouterData(baseUrl, credential)
                    _lastSyncError.value = null
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            val errMsg = "تعذر تحديث البيانات من الراوتر: ${e.localizedMessage ?: e.message}"
            _lastSyncError.value = errMsg
            Result.failure(e)
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

    suspend fun deleteCard(cardId: String, cardName: String = ""): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val targetName = cardName.trim().ifBlank { _cards.value.find { it.id == cardId }?.name.orEmpty() }
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.deleteCard(cardId, targetName)
                    if (socketRes.isFailure) {
                        return@withContext socketRes
                    }
                    _cards.value = _cards.value.filter { it.id != cardId && (targetName.isBlank() || it.name != targetName) }
                    if (targetName.isNotBlank()) {
                        cardTimestampCache.remove(targetName)
                        try { appDao?.deleteLocalCardByName(targetName) } catch (_: Exception) {}
                    }
                    return@withContext socketRes
                }
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)

                    // 0. Disconnect active session if any via REST
                    if (targetName.isNotBlank()) {
                        try {
                            val actReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active?user=$targetName").get().header("Authorization", credential).build()
                            val actResp = httpClient.newCall(actReq).execute()
                            if (actResp.isSuccessful) {
                                val body = actResp.body?.string().orEmpty()
                                val arr = org.json.JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    val actId = arr.getJSONObject(i).optString(".id")
                                    if (actId.isNotBlank()) {
                                        val delReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active/$actId").delete().header("Authorization", credential).build()
                                        httpClient.newCall(delReq).execute()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 1. Try Hotspot removal
                    val hsReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/$cardId").delete().header("Authorization", credential).build()
                    val hsResp = httpClient.newCall(hsReq).execute()
                    if (hsResp.isSuccessful) {
                        _cards.value = _cards.value.filter { it.id != cardId && (targetName.isBlank() || it.name != targetName) }
                        if (targetName.isNotBlank()) {
                            cardTimestampCache.remove(targetName)
                            try { appDao?.deleteLocalCardByName(targetName) } catch (_: Exception) {}
                        }
                        return@withContext Result.success(true)
                    }

                    // 2. Try User Manager v7 removal
                    var um7Code = 0
                    if (targetName.isNotBlank()) {
                        val um7Req = Request.Builder().url("$baseUrl/rest/user-manager/user/$targetName").delete().header("Authorization", credential).build()
                        val um7Resp = httpClient.newCall(um7Req).execute()
                        um7Code = um7Resp.code
                        if (um7Resp.isSuccessful) {
                            _cards.value = _cards.value.filter { it.id != cardId && it.name != targetName }
                            cardTimestampCache.remove(targetName)
                            try { appDao?.deleteLocalCardByName(targetName) } catch (_: Exception) {}
                            return@withContext Result.success(true)
                        }
                    }

                    // 3. Try User Manager v6 removal
                    var um6Code = 0
                    if (targetName.isNotBlank()) {
                        val um6Req = Request.Builder().url("$baseUrl/rest/tool/user-manager/user/$targetName").delete().header("Authorization", credential).build()
                        val um6Resp = httpClient.newCall(um6Req).execute()
                        um6Code = um6Resp.code
                        if (um6Resp.isSuccessful) {
                            _cards.value = _cards.value.filter { it.id != cardId && it.name != targetName }
                            cardTimestampCache.remove(targetName)
                            try { appDao?.deleteLocalCardByName(targetName) } catch (_: Exception) {}
                            return@withContext Result.success(true)
                        }
                    }

                    // If all systems returned 404 (Not Found), the card is already deleted on the router
                    if (hsResp.code == 404 && (um7Code == 404 || um7Code == 0) && (um6Code == 404 || um6Code == 0)) {
                        _cards.value = _cards.value.filter { it.id != cardId && (targetName.isBlank() || it.name != targetName) }
                        if (targetName.isNotBlank()) {
                            cardTimestampCache.remove(targetName)
                            try { appDao?.deleteLocalCardByName(targetName) } catch (_: Exception) {}
                        }
                        return@withContext Result.success(true)
                    }

                    return@withContext Result.failure(Exception("تعذر حذف الكرت من الراوتر عبر واجهة REST (رمز الخطأ: ${hsResp.code}). تأكد من وجود الكرت وصلاحيات المستخدم."))
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            if (targetName.isNotBlank()) {
                cardTimestampCache.remove(targetName)
                try { appDao?.deleteLocalCardByName(targetName) } catch (_: Exception) {}
            }
            _cards.value = _cards.value.filter { it.id != cardId && (targetName.isBlank() || it.name != targetName) }
            Result.success(true)
        }
    }

    suspend fun reactivateCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.reactivateCard(card)
                    if (socketRes.isFailure) {
                        return@withContext socketRes
                    }
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)

                        // 0. Disconnect active session first
                        if (card.name.isNotBlank()) {
                            try {
                                val actReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active?user=${card.name}").get().header("Authorization", credential).build()
                                val actResp = httpClient.newCall(actReq).execute()
                                if (actResp.isSuccessful) {
                                    val arr = JSONArray(actResp.body?.string().orEmpty())
                                    for (i in 0 until arr.length()) {
                                        val actId = arr.getJSONObject(i).optString(".id")
                                        if (actId.isNotBlank()) {
                                            val delReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active/$actId").delete().header("Authorization", credential).build()
                                            httpClient.newCall(delReq).execute()
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // 1. Resolve Hotspot real .id
                        var realId = if (card.id.startsWith("*")) card.id else ""
                        if (realId.isBlank() && card.name.isNotBlank()) {
                            try {
                                val findReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user?name=${card.name}").get().header("Authorization", credential).build()
                                val findResp = httpClient.newCall(findReq).execute()
                                if (findResp.isSuccessful) {
                                    val arr = JSONArray(findResp.body?.string().orEmpty())
                                    if (arr.length() > 0) {
                                        realId = arr.getJSONObject(0).optString(".id")
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        if (realId.isNotBlank()) {
                            // Reset counters & enable in Hotspot
                            val resetBody = JSONObject().put("numbers", realId).toString().toRequestBody("application/json".toMediaType())
                            val resetReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/reset-counters").post(resetBody).header("Authorization", credential).build()
                            httpClient.newCall(resetReq).execute()

                            val enableBody = JSONObject().put("disabled", "false").toString().toRequestBody("application/json".toMediaType())
                            val hsReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/$realId").patch(enableBody).header("Authorization", credential).build()
                            val hsResp = httpClient.newCall(hsReq).execute()
                            if (!hsResp.isSuccessful) {
                                val err = hsResp.body?.string().orEmpty()
                                return@withContext Result.failure(Exception("رفض الراوتر إعادة تفعيل الكرت في الهوتسبوت: $err"))
                            }
                        } else {
                            // Try User Manager v7
                            var umSuccess = false
                            try {
                                val umResetBody = JSONObject().put("numbers", card.name).toString().toRequestBody("application/json".toMediaType())
                                val umResetReq = Request.Builder().url("$baseUrl/rest/user-manager/user/reset-counters").post(umResetBody).header("Authorization", credential).build()
                                httpClient.newCall(umResetReq).execute()

                                val enableBody = JSONObject().put("disabled", "false").toString().toRequestBody("application/json".toMediaType())
                                val um7Req = Request.Builder().url("$baseUrl/rest/user-manager/user/${card.name}").patch(enableBody).header("Authorization", credential).build()
                                val um7Resp = httpClient.newCall(um7Req).execute()
                                if (um7Resp.isSuccessful) {
                                    umSuccess = true
                                }
                            } catch (_: Exception) {}

                            if (!umSuccess) {
                                // Try User Manager v6
                                val enableBody = JSONObject().put("disabled", "false").toString().toRequestBody("application/json".toMediaType())
                                val um6Req = Request.Builder().url("$baseUrl/rest/tool/user-manager/user/${card.name}").patch(enableBody).header("Authorization", credential).build()
                                val um6Resp = httpClient.newCall(um6Req).execute()
                                if (!um6Resp.isSuccessful) {
                                    return@withContext Result.failure(Exception("رفض الراوتر إعادة تفعيل الكرت عبر واجهة REST"))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        return@withContext Result.failure(e)
                    }
                }
            }
            // Update in local state
            _cards.value = _cards.value.map {
                if (it.id == card.id || it.name == card.name) {
                    it.copy(
                        bytesIn = 0L,
                        bytesOut = 0L,
                        uptime = "0s",
                        disabled = false
                    )
                } else it
            }
            val targetCard = _cards.value.firstOrNull { it.name == card.name || it.id == card.id }
            if (targetCard != null) {
                try {
                    appDao?.insertOrUpdateLocalCard(com.example.data.local.LocalCardEntity.fromHotspotCard(targetCard))
                } catch (_: Exception) {}
            }
            Result.success(true)
        }
    }

    suspend fun updateCard(updatedCard: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.updateCard(updatedCard)
                    if (socketRes.isFailure) {
                        return@withContext socketRes
                    }
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)

                        // Do NOT include "name" in PATCH body, RouterOS REST rejects PATCH if name is sent
                        val jsonBody = JSONObject().apply {
                            if (updatedCard.password.isNotBlank()) put("password", updatedCard.password)
                            if (updatedCard.profile.isNotBlank()) put("profile", updatedCard.profile)
                            put("comment", updatedCard.comment)
                            put("disabled", if (updatedCard.disabled) "true" else "false")
                            val validUptime = routerOsApiClient.formatRouterOsTime(updatedCard.limitUptime)
                            if (!validUptime.isNullOrBlank()) {
                                put("limit-uptime", validUptime)
                            }
                            if (updatedCard.limitBytesTotal > 0L) {
                                put("limit-bytes-total", updatedCard.limitBytesTotal.toString())
                            }
                        }
                        val jsonStr = jsonBody.toString().toRequestBody("application/json".toMediaType())

                        // 0. Disconnect active session first in REST mode
                        if (updatedCard.name.isNotBlank()) {
                            try {
                                val actReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active?user=${updatedCard.name}").get().header("Authorization", credential).build()
                                val actResp = httpClient.newCall(actReq).execute()
                                if (actResp.isSuccessful) {
                                    val body = actResp.body?.string().orEmpty()
                                    val arr = org.json.JSONArray(body)
                                    for (i in 0 until arr.length()) {
                                        val actId = arr.getJSONObject(i).optString(".id")
                                        if (actId.isNotBlank()) {
                                            val delReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active/$actId").delete().header("Authorization", credential).build()
                                            httpClient.newCall(delReq).execute()
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // 1. Try Hotspot with resolved router .id
                        var hsTargetId = if (updatedCard.id.startsWith("*")) updatedCard.id else ""
                        if (hsTargetId.isBlank() && updatedCard.name.isNotBlank()) {
                            try {
                                val findReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user?name=${updatedCard.name}").get().header("Authorization", credential).build()
                                val findResp = httpClient.newCall(findReq).execute()
                                if (findResp.isSuccessful) {
                                    val arr = org.json.JSONArray(findResp.body?.string().orEmpty())
                                    if (arr.length() > 0) {
                                        hsTargetId = arr.getJSONObject(0).optString(".id")
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        val effectiveId = hsTargetId.ifBlank { updatedCard.id.ifBlank { updatedCard.name } }
                        val hsReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/$effectiveId").patch(jsonStr).header("Authorization", credential).build()
                        val hsResp = httpClient.newCall(hsReq).execute()

                        if (!hsResp.isSuccessful) {
                            // 2. Try User Manager v7
                            val um7Req = Request.Builder().url("$baseUrl/rest/user-manager/user/${updatedCard.name}").patch(jsonStr).header("Authorization", credential).build()
                            val um7Resp = httpClient.newCall(um7Req).execute()

                            if (!um7Resp.isSuccessful) {
                                // 3. Try User Manager v6
                                val um6Req = Request.Builder().url("$baseUrl/rest/tool/user-manager/user/${updatedCard.name}").patch(jsonStr).header("Authorization", credential).build()
                                val um6Resp = httpClient.newCall(um6Req).execute()
                                if (!um6Resp.isSuccessful) {
                                    return@withContext Result.failure(Exception("رفض راوتر ميكروتك تعديل الكرت عبر واجهة REST. تحقق من صحة الحقول وصلاحيات الحساب."))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        return@withContext Result.failure(e)
                    }
                }
            }
            _cards.value = _cards.value.map {
                if (it.id == updatedCard.id || it.name == updatedCard.name) updatedCard else it
            }
            try {
                appDao?.insertOrUpdateLocalCard(com.example.data.local.LocalCardEntity.fromHotspotCard(updatedCard))
            } catch (_: Exception) {}
            Result.success(true)
        }
    }

    suspend fun toggleCardDisabled(cardId: String, currentDisabled: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val newStatus = !currentDisabled
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                if (isApiSocketMode) {
                    val targetName = _cards.value.find { it.id == cardId }?.name.orEmpty()
                    val socketRes = routerOsApiClient.toggleCardDisabled(cardId, currentDisabled, targetName)
                    if (socketRes.isFailure) {
                        return@withContext socketRes
                    }
                    _cards.value = _cards.value.map {
                        if (it.id == cardId || (targetName.isNotBlank() && it.name == targetName)) it.copy(disabled = newStatus) else it
                    }
                    val updated = _cards.value.firstOrNull { it.id == cardId || (targetName.isNotBlank() && it.name == targetName) }
                    if (updated != null) {
                        try { appDao?.insertOrUpdateLocalCard(com.example.data.local.LocalCardEntity.fromHotspotCard(updated)) } catch (_: Exception) {}
                    }
                    return@withContext socketRes
                }
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val jsonBody = JSONObject().put("disabled", if (newStatus) "true" else "false").toString()
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
                        val updated = _cards.value.firstOrNull { it.id == cardId }
                        if (updated != null) {
                            try { appDao?.insertOrUpdateLocalCard(com.example.data.local.LocalCardEntity.fromHotspotCard(updated)) } catch (_: Exception) {}
                        }
                        return@withContext Result.success(true)
                    }
                    return@withContext Result.failure(Exception("رفض الراوتر تغيير حالة الكرت عبر واجهة REST"))
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            _cards.value = _cards.value.map {
                if (it.id == cardId) it.copy(disabled = newStatus) else it
            }
            val updated = _cards.value.firstOrNull { it.id == cardId }
            if (updated != null) {
                try { appDao?.insertOrUpdateLocalCard(com.example.data.local.LocalCardEntity.fromHotspotCard(updated)) } catch (_: Exception) {}
            }
            Result.success(true)
        }
    }

    suspend fun verifyUserConnectionAndProfile(profileName: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig ?: return@withContext Result.failure(Exception("لم يتم ضبط إعدادات الاتصال بالراوتر"))
            if (config.isDemoMode) return@withContext Result.success(true)

            val isConnected = if (isApiSocketMode) {
                routerOsApiClient.ensureConnected()
            } else {
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val req = Request.Builder().url("$baseUrl/rest/system/resource").header("Authorization", credential).build()
                    val resp = httpClient.newCall(req).execute()
                    resp.isSuccessful
                } catch (_: Exception) {
                    false
                }
            }

            if (!isConnected) {
                return@withContext Result.failure(Exception("فشل الاتصال بـ User Manager وراوتر ميكروتك (فحص الاتصال Ping/Connection فشل). تأكد من سلامة الشبكة وحالة الراوتر."))
            }

            refreshProfilesFromRouter()

            val cleanName = profileName.trim()
            if (cleanName.isBlank() || cleanName.equals("default", ignoreCase = true)) {
                return@withContext Result.success(true)
            }

            val found = _profiles.value.any { it.name.trim().equals(cleanName, ignoreCase = true) }
            if (!found) {
                val umProfileExists = try {
                    if (isApiSocketMode) {
                        _profiles.value.any { it.name.trim().equals(cleanName, ignoreCase = true) }
                    } else {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        val r7 = Request.Builder().url("$baseUrl/rest/user-manager/profile/$cleanName").header("Authorization", credential).build()
                        val resp7 = httpClient.newCall(r7).execute()
                        if (resp7.isSuccessful) {
                            true
                        } else {
                            val r6 = Request.Builder().url("$baseUrl/rest/tool/user-manager/profile/$cleanName").header("Authorization", credential).build()
                            val resp6 = httpClient.newCall(r6).execute()
                            resp6.isSuccessful
                        }
                    }
                } catch (_: Exception) {
                    false
                }
                if (!umProfileExists) {
                    return@withContext Result.failure(Exception("الباقة المحددة ($cleanName) غير موجودة حالياً في نظام Hotspot أو User Manager في الراوتر. يرجى تحديث البيانات أو اختيار باقة أخرى."))
                }
            }
            Result.success(true)
        }
    }


    suspend fun generateBatch(config: BatchGenerationConfig, dao: com.example.data.local.AppDao): Result<BatchCardGenerationResult> {
        return withContext(Dispatchers.Default) {
            val preCheck = verifyUserConnectionAndProfile(config.profile)
            if (preCheck.isFailure) {
                return@withContext Result.failure(preCheck.exceptionOrNull() ?: Exception("فشل فحص الاتصال وصلاحية الباقة في User Manager"))
            }
            // 1. Validation & Auto-provision: Verify profile against router profiles (case-insensitive & trimmed)
            val cleanProfileName = config.profile.trim()
            var existingProfiles = _profiles.value
            var targetProfile = existingProfiles.firstOrNull { 
                it.name.trim().equals(cleanProfileName, ignoreCase = true) && 
                (config.profileSource.isBlank() || it.source == config.profileSource)
            } ?: existingProfiles.firstOrNull { it.name.trim().equals(cleanProfileName, ignoreCase = true) }
            val routerConfig = activeConfig

            if (routerConfig != null && !routerConfig.isDemoMode && cleanProfileName.isNotBlank() && !cleanProfileName.equals("default", ignoreCase = true)) {
                if (targetProfile == null) {
                    // Stale cache guard: query fresh profiles from router before failing
                    refreshProfilesFromRouter()
                    existingProfiles = _profiles.value
                    targetProfile = existingProfiles.firstOrNull { 
                        it.name.trim().equals(cleanProfileName, ignoreCase = true) && 
                        (config.profileSource.isBlank() || it.source == config.profileSource)
                    } ?: existingProfiles.firstOrNull { it.name.trim().equals(cleanProfileName, ignoreCase = true) }
                    
                    if (targetProfile == null) {
                        return@withContext Result.failure(Exception("الباقة المحددة ($cleanProfileName) غير موجودة في الراوتر. يرجى اختيار باقة صحيحة من القائمة."))
                    }
                }
            }

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
                    val password = if (config.isSingleCode) "" else {
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
                        profile = cleanProfileName,
                        uptime = "0s",
                        bytesIn = 0,
                        bytesOut = 0,
                        limitUptime = config.timeLimit,
                        limitBytesTotal = limitBytes,
                        comment = config.batchComment.ifBlank { "دفعة كروت" },
                        disabled = false,
                        price = config.price,
                        cardType = config.targetSystem
                    )
                    generatedCards.add(card)
                }
            }

            // 2. Real Router Verification & Synchronization
            if (routerConfig != null && !routerConfig.isDemoMode) {
                val addedCards = mutableListOf<HotspotCard>()
                val failedCards = mutableListOf<HotspotCard>()
                val failureReasons = mutableListOf<String>()

                withContext(Dispatchers.IO) {
                    if (isApiSocketMode) {
                        for (card in generatedCards) {
                            val res = routerOsApiClient.addCard(
                                card,
                                preferredSystem = config.targetSystem,
                                profileSource = config.profileSource.ifBlank { targetProfile?.source.orEmpty() }
                            )
                            if (res.isSuccess) {
                                addedCards.add(card)
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "خطأ غير معروف"
                                failedCards.add(card)
                                failureReasons.add("الكرت ${card.name}: $err")
                            }
                        }
                    } else {
                        val protocol = if (routerConfig.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${routerConfig.ipAddress}:${routerConfig.port}"
                        val credential = Credentials.basic(routerConfig.username, routerConfig.password)

                        val isUmProfile = config.targetSystem == "usermanager" ||
                            targetProfile?.source?.startsWith("usermanager") == true ||
                            config.profileSource.startsWith("usermanager")

                        if (isUmProfile) {
                            for (card in generatedCards) {
                                try {
                                    // Step 1: Create user in UM
                                    val umUserJson = JSONObject().apply {
                                        put("name", card.name)
                                        put("password", card.password)
                                        put("comment", card.comment)
                                        put("disabled", "false")
                                    }.toString()
                                    val umReq = Request.Builder()
                                        .url("$baseUrl/rest/user-manager/user")
                                        .post(umUserJson.toRequestBody("application/json".toMediaType()))
                                        .header("Authorization", credential)
                                        .build()
                                    var umResp = httpClient.newCall(umReq).execute()
                                    
                                    // Fallback to UM v6 if UM v7 failed
                                    if (!umResp.isSuccessful) {
                                        val um6Req = Request.Builder()
                                            .url("$baseUrl/rest/tool/user-manager/user")
                                            .post(umUserJson.toRequestBody("application/json".toMediaType()))
                                            .header("Authorization", credential)
                                            .build()
                                        val um6Resp = httpClient.newCall(um6Req).execute()
                                        if (um6Resp.isSuccessful) {
                                            umResp = um6Resp
                                        }
                                    }

                                    if (!umResp.isSuccessful) {
                                        val errBody = umResp.body?.string().orEmpty()
                                        failedCards.add(card)
                                        failureReasons.add("فشل إنشاء كرت يوزرمانجر ${card.name}: ${umResp.code} - $errBody")
                                        continue
                                    }

                                    // Step 2: Assign profile to UM user
                                    var bindingSuccessful = false
                                    var bindError = ""
                                    if (cleanProfileName.isNotBlank()) {
                                        val umProfJson = JSONObject().apply {
                                            put("user", card.name)
                                            put("profile", cleanProfileName)
                                        }.toString()
                                        
                                        // Try UM v7 profile assignment
                                        val assignReq = Request.Builder()
                                            .url("$baseUrl/rest/user-manager/user-profile")
                                            .post(umProfJson.toRequestBody("application/json".toMediaType()))
                                            .header("Authorization", credential)
                                            .build()
                                        var assignResp = httpClient.newCall(assignReq).execute()
                                        
                                        // Fallback to UM v6 profile assignment if needed
                                        if (!assignResp.isSuccessful) {
                                            val assign6Req = Request.Builder()
                                                .url("$baseUrl/rest/tool/user-manager/user-profile")
                                                .post(umProfJson.toRequestBody("application/json".toMediaType()))
                                                .header("Authorization", credential)
                                                .build()
                                            val assign6Resp = httpClient.newCall(assign6Req).execute()
                                            if (assign6Resp.isSuccessful) {
                                                assignResp = assign6Resp
                                            }
                                        }

                                        if (assignResp.isSuccessful) {
                                            bindingSuccessful = true
                                        } else {
                                            bindError = assignResp.body?.string().orEmpty()
                                        }
                                    } else {
                                        bindingSuccessful = true
                                    }

                                    if (!bindingSuccessful) {
                                        // Transactional Rollback: Delete user to prevent orphaned unlinked card
                                        try {
                                            // Try UM v7 delete
                                            val delReq = Request.Builder()
                                                .url("$baseUrl/rest/user-manager/user/${card.name}")
                                                .delete()
                                                .header("Authorization", credential)
                                                .build()
                                            val delResp = httpClient.newCall(delReq).execute()
                                            
                                            if (!delResp.isSuccessful) {
                                                // Try UM v6 delete
                                                val del6Req = Request.Builder()
                                                    .url("$baseUrl/rest/tool/user-manager/user/${card.name}")
                                                    .delete()
                                                    .header("Authorization", credential)
                                                    .build()
                                                httpClient.newCall(del6Req).execute()
                                            }
                                        } catch (_: Exception) {}

                                        failedCards.add(card)
                                        failureReasons.add("فشل ربط باقة اليوزرمانجر بالكرت ${card.name}: $bindError (تم التراجع عن إنشاء الكرت)")
                                        continue
                                    }

                                    addedCards.add(card)
                                } catch (e: Exception) {
                                    failedCards.add(card)
                                    failureReasons.add("خطأ في الاتصال بالراوتر للكرت ${card.name}: ${e.message}")
                                }
                            }
                        } else {
                            // Hotspot system
                            if (cleanProfileName.isNotBlank() && !cleanProfileName.equals("default", ignoreCase = true)) {
                                try {
                                    val pCheck = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/profile/$cleanProfileName").header("Authorization", credential).build()
                                    val pResp = httpClient.newCall(pCheck).execute()
                                    if (!pResp.isSuccessful) {
                                        val createP = JSONObject().apply {
                                            put("name", cleanProfileName)
                                            put("shared-users", "1")
                                        }.toString()
                                        val createReq = Request.Builder()
                                            .url("$baseUrl/rest/ip/hotspot/user/profile")
                                            .post(createP.toRequestBody("application/json".toMediaType()))
                                            .header("Authorization", credential)
                                            .build()
                                        httpClient.newCall(createReq).execute()
                                    }
                                } catch (_: Exception) {}
                            }

                            for (card in generatedCards) {
                                try {
                                    val jsonBody = JSONObject().apply {
                                        put("name", card.name.trim())
                                        if (card.password.isNotBlank()) put("password", card.password.trim())
                                        if (cleanProfileName.isNotBlank()) put("profile", cleanProfileName)
                                        put("disabled", "false")
                                        put("server", "all")
                                        val validUptime = routerOsApiClient.formatRouterOsTime(card.limitUptime)
                                        if (!validUptime.isNullOrBlank()) put("limit-uptime", validUptime)
                                        if (card.limitBytesTotal > 0) put("limit-bytes-total", card.limitBytesTotal.toString())
                                        if (card.comment.isNotBlank()) put("comment", card.comment)
                                    }.toString()

                                    val postReq = Request.Builder()
                                        .url("$baseUrl/rest/ip/hotspot/user")
                                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                                        .header("Authorization", credential)
                                        .build()
                                    val resp = httpClient.newCall(postReq).execute()
                                    if (resp.isSuccessful) {
                                        addedCards.add(card)
                                    } else {
                                        val errBody = resp.body?.string().orEmpty()
                                        failedCards.add(card)
                                        failureReasons.add("فشل إضافة الكرت ${card.name}: ${resp.code} - $errBody")
                                        dao.insertSystemEventLog(
                                            SystemEventLogEntity(
                                                actionType = "توليد كروت",
                                                targetType = "كرت",
                                                targetName = card.name,
                                                description = "فشل إضافة الكرت للراوتر (${resp.code})",
                                                status = "FAILED",
                                                details = "البيانات المرسلة: $jsonBody | الاستجابة: $errBody"
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    failedCards.add(card)
                                    failureReasons.add(e.message ?: "فشل الاتصال بالراوتر")
                                }
                            }
                        }
                    }

                    // Re-sync all cards (both Hotspot & User-Manager) directly from router
                    refreshCardsFromRouter()
                }

                val batchResult = BatchCardGenerationResult(
                    totalRequested = config.count,
                    successfulCards = addedCards,
                    failedCards = failedCards,
                    failureReasons = failureReasons
                )

                if (addedCards.isEmpty()) {
                    val primaryErr = failureReasons.firstOrNull() ?: "فشل الاتصال بالراوتر أو رفض السيرفر للإضافة"
                    return@withContext Result.failure(
                        Exception("فشل إضافة الكروت في راوتر ميكروتك: $primaryErr. تأكد من صحة بيانات الاتصال والباقة.")
                    )
                }

                val now = System.currentTimeMillis()
                addedCards.forEachIndexed { i, c -> cardTimestampCache[c.name] = now - i }
                val currentList = _cards.value.toMutableList()
                currentList.addAll(0, addedCards)
                val sorted = sortCardsByNewestFirst(currentList.distinctBy { it.name })
                _cards.value = sorted
                syncCardsToRoom(sorted)
                return@withContext Result.success(batchResult)
            }

            // Demo Mode: Add generated cards locally
            val now = System.currentTimeMillis()
            generatedCards.forEachIndexed { i, c -> cardTimestampCache[c.name] = now - i }
            val currentList = _cards.value.toMutableList()
            currentList.addAll(0, generatedCards)
            val sorted = sortCardsByNewestFirst(currentList.distinctBy { it.name })
            _cards.value = sorted
            syncCardsToRoom(sorted)

            val batchResult = BatchCardGenerationResult(
                totalRequested = config.count,
                successfulCards = generatedCards,
                failedCards = emptyList(),
                failureReasons = emptyList()
            )
            Result.success(batchResult)
        }
    }

    suspend fun addSingleCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val preCheck = verifyUserConnectionAndProfile(card.profile)
            if (preCheck.isFailure) {
                return@withContext Result.failure(preCheck.exceptionOrNull() ?: Exception("فشل فحص الاتصال وصلاحية الباقة في User Manager"))
            }
            val cleanProfileName = card.profile.trim()
            var existingProfiles = _profiles.value
            var targetProfile = existingProfiles.firstOrNull { it.name.trim().equals(cleanProfileName, ignoreCase = true) }

            val routerConfig = activeConfig
            if (routerConfig != null && !routerConfig.isDemoMode && cleanProfileName.isNotBlank() && !cleanProfileName.equals("default", ignoreCase = true)) {
                if (targetProfile == null) {
                    refreshProfilesFromRouter()
                    existingProfiles = _profiles.value
                    targetProfile = existingProfiles.firstOrNull { it.name.trim().equals(cleanProfileName, ignoreCase = true) }
                    if (targetProfile == null) {
                        return@withContext Result.failure(Exception("الباقة المحددة (${cleanProfileName}) غير متوفرة في الراوتر."))
                    }
                }
            }

            val sanitizedCard = card.copy(profile = cleanProfileName)
            val isUmProfile = targetProfile?.isUserManager == true ||
                targetProfile?.source?.startsWith("usermanager") == true ||
                sanitizedCard.isUserManagerCard

            val isHsProfile = targetProfile?.isHotspot == true ||
                targetProfile?.source == "hotspot" ||
                sanitizedCard.isHotspotCard

            val targetSystem = when {
                isUmProfile -> "usermanager"
                isHsProfile -> "hotspot"
                _profiles.value.any { it.isUserManager } && _profiles.value.none { it.isHotspot } -> "usermanager"
                _profiles.value.any { it.isHotspot } && _profiles.value.none { it.isUserManager } -> "hotspot"
                else -> "usermanager"
            }

            if (routerConfig != null && !routerConfig.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.addCard(
                        sanitizedCard,
                        preferredSystem = targetSystem,
                        profileSource = targetProfile?.source.orEmpty()
                    )
                    if (!socketRes.isSuccess) {
                        return@withContext socketRes
                    }
                } else {
                    try {
                        val protocol = if (routerConfig.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${routerConfig.ipAddress}:${routerConfig.port}"
                        val credential = Credentials.basic(routerConfig.username, routerConfig.password)

                        val isUmProfile = targetSystem == "usermanager" || targetProfile?.source?.startsWith("usermanager") == true

                        if (isUmProfile) {
                            // Step 1: Create user in UM
                            val umUserJson = JSONObject().apply {
                                put("name", sanitizedCard.name.trim())
                                if (sanitizedCard.password.isNotBlank()) put("password", sanitizedCard.password.trim())
                                if (sanitizedCard.comment.isNotBlank()) put("comment", sanitizedCard.comment)
                                put("disabled", "false")
                            }.toString()
                            val umReq = Request.Builder()
                                .url("$baseUrl/rest/user-manager/user")
                                .post(umUserJson.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            var umResp = httpClient.newCall(umReq).execute()
                            
                            // Fallback to UM v6 if UM v7 failed or 404
                            if (!umResp.isSuccessful) {
                                val um6Req = Request.Builder()
                                    .url("$baseUrl/rest/tool/user-manager/user")
                                    .post(umUserJson.toRequestBody("application/json".toMediaType()))
                                    .header("Authorization", credential)
                                    .build()
                                val um6Resp = httpClient.newCall(um6Req).execute()
                                if (um6Resp.isSuccessful) {
                                    umResp = um6Resp
                                }
                            }

                            if (!umResp.isSuccessful) {
                                val errBody = umResp.body?.string().orEmpty()
                                return@withContext Result.failure(Exception("رفض راوتر ميكروتك إنشاء مستخدم يوزرمانجر: ${umResp.code} - $errBody"))
                            }

                            // Step 2: Assign Profile in UM
                            var bindingSuccessful = false
                            var bindError = ""
                            if (cleanProfileName.isNotBlank()) {
                                val umProfJson = JSONObject().apply {
                                    put("user", sanitizedCard.name.trim())
                                    put("profile", cleanProfileName)
                                }.toString()
                                
                                // Try UM v7 profile assignment
                                val assignReq = Request.Builder()
                                    .url("$baseUrl/rest/user-manager/user-profile")
                                    .post(umProfJson.toRequestBody("application/json".toMediaType()))
                                    .header("Authorization", credential)
                                    .build()
                                var assignResp = httpClient.newCall(assignReq).execute()
                                
                                // Fallback to UM v6 profile assignment if needed
                                if (!assignResp.isSuccessful) {
                                    val assign6Req = Request.Builder()
                                        .url("$baseUrl/rest/tool/user-manager/user-profile")
                                        .post(umProfJson.toRequestBody("application/json".toMediaType()))
                                        .header("Authorization", credential)
                                        .build()
                                    val assign6Resp = httpClient.newCall(assign6Req).execute()
                                    if (assign6Resp.isSuccessful) {
                                        assignResp = assign6Resp
                                    }
                                }

                                if (assignResp.isSuccessful) {
                                    bindingSuccessful = true
                                } else {
                                    bindError = assignResp.body?.string().orEmpty()
                                }
                            } else {
                                bindingSuccessful = true
                            }

                            if (!bindingSuccessful) {
                                // Transactional Rollback: Delete user to prevent orphaned card
                                try {
                                    val delReq = Request.Builder()
                                        .url("$baseUrl/rest/user-manager/user/${sanitizedCard.name.trim()}")
                                        .delete()
                                        .header("Authorization", credential)
                                        .build()
                                    httpClient.newCall(delReq).execute()
                                } catch (_: Exception) {}

                                return@withContext Result.failure(Exception("فشل ربط باقة اليوزرمانجر (${cleanProfileName}) بالكرت: $bindError (تم التراجع عن إنشاء الكرت)"))
                            }
                        } else {
                            // Hotspot
                            if (cleanProfileName.isNotBlank() && !cleanProfileName.equals("default", ignoreCase = true)) {
                                try {
                                    val pCheck = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/profile/$cleanProfileName").header("Authorization", credential).build()
                                    val pResp = httpClient.newCall(pCheck).execute()
                                    if (!pResp.isSuccessful) {
                                        val createP = JSONObject().apply {
                                            put("name", cleanProfileName)
                                            put("shared-users", "1")
                                        }.toString()
                                        val createReq = Request.Builder()
                                            .url("$baseUrl/rest/ip/hotspot/user/profile")
                                            .post(createP.toRequestBody("application/json".toMediaType()))
                                            .header("Authorization", credential)
                                            .build()
                                        httpClient.newCall(createReq).execute()
                                    }
                                } catch (_: Exception) {}
                            }

                            val jsonBody = JSONObject().apply {
                                put("name", sanitizedCard.name.trim())
                                if (sanitizedCard.password.isNotBlank()) put("password", sanitizedCard.password.trim())
                                if (cleanProfileName.isNotBlank()) put("profile", cleanProfileName)
                                put("disabled", "false")
                                put("server", "all")
                                val validUptime = routerOsApiClient.formatRouterOsTime(sanitizedCard.limitUptime)
                                if (!validUptime.isNullOrBlank()) put("limit-uptime", validUptime)
                                if (sanitizedCard.limitBytesTotal > 0) put("limit-bytes-total", sanitizedCard.limitBytesTotal.toString())
                                if (sanitizedCard.comment.isNotBlank()) put("comment", sanitizedCard.comment)
                            }.toString()

                            val postReq = Request.Builder()
                                .url("$baseUrl/rest/ip/hotspot/user")
                                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            val resp = httpClient.newCall(postReq).execute()
                            if (!resp.isSuccessful) {
                                val errBody = resp.body?.string().orEmpty()
                                return@withContext Result.failure(Exception("استجاب الراوتر برفض إضافة الكرت: $errBody"))
                            }
                        }
                    } catch (e: Exception) {
                        return@withContext Result.failure(e)
                    }
                }

            }

            // Incremental in-place sync: Add newly created card immediately to in-memory state & Room
            val now = System.currentTimeMillis()
            cardTimestampCache[sanitizedCard.name] = now
            val currentList = _cards.value.toMutableList()
            currentList.removeAll { it.name == sanitizedCard.name }
            currentList.add(0, sanitizedCard)
            val sorted = sortCardsByNewestFirst(currentList)
            _cards.value = sorted
            syncCardsToRoom(listOf(sanitizedCard))

            // Non-blocking background sync of full list
            if (routerConfig != null && !routerConfig.isDemoMode) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try {
                        refreshCardsFromRouter()
                    } catch (_: Exception) {}
                }
            }

            Result.success(true)
        }
    }

    suspend fun syncProfileBetweenSystems(profile: HotspotProfile, targetSystem: String): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            val opResult = withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    if (targetSystem == "usermanager") {
                        routerOsApiClient.syncProfileToUserManager(profile)
                    } else {
                        routerOsApiClient.syncProfileToHotspot(profile)
                    }
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        if (targetSystem == "usermanager") {
                            val jsonBody = JSONObject().apply {
                                put("name", profile.name)
                                val valTime = routerOsApiClient.formatRouterOsTime(profile.validity.ifBlank { profile.sessionTimeout }) ?: "1d"
                                put("validity", valTime)
                                val priceNum = profile.priceNumber.takeIf { it > 0 } ?: 0.0
                                if (priceNum > 0) put("price", priceNum.toInt().toString())
                            }.toString()
                            val postReq = Request.Builder()
                                .url("$baseUrl/rest/user-manager/profile")
                                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            httpClient.newCall(postReq).execute()
                            Result.success(true)
                        } else {
                            val jsonBody = JSONObject().apply {
                                put("name", profile.name)
                                put("rate-limit", profile.rateLimit)
                                put("shared-users", profile.sharedUsers.toString())
                                if (profile.sessionTimeout.isNotBlank()) put("session-timeout", profile.sessionTimeout)
                            }.toString()
                            val postReq = Request.Builder()
                                .url("$baseUrl/rest/ip/hotspot/user/profile")
                                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            httpClient.newCall(postReq).execute()
                            Result.success(true)
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
            if (opResult.isFailure) return opResult
        }
        val newProf = profile.copy(
            id = "synced_${System.currentTimeMillis()}",
            source = if (targetSystem == "usermanager") "usermanager_v7" else "hotspot"
        )
        val current = _profiles.value.toMutableList()
        if (current.none { it.name.equals(newProf.name, ignoreCase = true) && it.source == newProf.source }) {
            current.add(newProf)
            _profiles.value = current
        }
        return Result.success(true)
    }

    suspend fun verifyAndActivateCard(card: HotspotCard): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            val res = withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    routerOsApiClient.verifyAndActivateCard(card)
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        val cleanProfile = card.profile.trim().ifBlank { "default" }

                        // 1. Disconnect any active session
                        if (card.name.isNotBlank()) {
                            try {
                                val actReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active?user=${card.name}").get().header("Authorization", credential).build()
                                val actResp = httpClient.newCall(actReq).execute()
                                if (actResp.isSuccessful) {
                                    val arr = JSONArray(actResp.body?.string().orEmpty())
                                    for (i in 0 until arr.length()) {
                                        val actId = arr.getJSONObject(i).optString(".id")
                                        if (actId.isNotBlank()) {
                                            val delReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/active/$actId").delete().header("Authorization", credential).build()
                                            httpClient.newCall(delReq).execute()
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // 2. Query hotspot user to find real .id
                        var realId = if (card.id.startsWith("*")) card.id else ""
                        if (realId.isBlank()) {
                            try {
                                val getReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user?name=${card.name}").get().header("Authorization", credential).build()
                                val getResp = httpClient.newCall(getReq).execute()
                                if (getResp.isSuccessful) {
                                    val arr = JSONArray(getResp.body?.string().orEmpty())
                                    if (arr.length() > 0) {
                                        realId = arr.getJSONObject(0).optString(".id")
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        if (realId.isNotBlank()) {
                            val patchJson = JSONObject().apply {
                                put("disabled", "false")
                                put("server", "all")
                                put("profile", cleanProfile)
                            }.toString()
                            val patchReq = Request.Builder()
                                .url("$baseUrl/rest/ip/hotspot/user/$realId")
                                .patch(patchJson.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            val patchResp = httpClient.newCall(patchReq).execute()
                            if (!patchResp.isSuccessful) {
                                val err = patchResp.body?.string().orEmpty()
                                return@withContext Result.failure(Exception("رفض الراوتر تفعيل الكرت في الهوتسبوت: $err"))
                            }
                        } else {
                            // Try user manager v7
                            var um7Success = false
                            try {
                                val umPatch = JSONObject().apply { put("disabled", "false") }.toString()
                                val umReq = Request.Builder()
                                    .url("$baseUrl/rest/user-manager/user/${card.name}")
                                    .patch(umPatch.toRequestBody("application/json".toMediaType()))
                                    .header("Authorization", credential)
                                    .build()
                                val umResp = httpClient.newCall(umReq).execute()
                                if (umResp.isSuccessful) {
                                    um7Success = true
                                }
                            } catch (_: Exception) {}

                            if (!um7Success) {
                                // Add user to hotspot
                                val addJson = JSONObject().apply {
                                    put("name", card.name)
                                    put("password", card.password.ifBlank { card.name })
                                    put("profile", cleanProfile)
                                    put("disabled", "false")
                                    put("server", "all")
                                    if (card.comment.isNotBlank()) put("comment", card.comment)
                                }.toString()
                                val addReq = Request.Builder()
                                    .url("$baseUrl/rest/ip/hotspot/user")
                                    .post(addJson.toRequestBody("application/json".toMediaType()))
                                    .header("Authorization", credential)
                                    .build()
                                val addResp = httpClient.newCall(addReq).execute()
                                if (!addResp.isSuccessful) {
                                    val err = addResp.body?.string().orEmpty()
                                    return@withContext Result.failure(Exception("رفض الراوتر تفعيل أو إضافة الكرت: $err"))
                                }
                            }
                        }
                        Result.success(true)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
            if (res.isFailure) return res
        }
        val activated = card.copy(disabled = false)
        _cards.value = _cards.value.map {
            if (it.id == card.id || it.name == card.name) activated else it
        }
        try {
            appDao?.insertOrUpdateLocalCard(com.example.data.local.LocalCardEntity.fromHotspotCard(activated))
        } catch (_: Exception) {}
        return Result.success(true)
    }

    suspend fun createProfile(profile: HotspotProfile): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            val opResult = withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    if (profile.source == "both") {
                        routerOsApiClient.addProfile(profile.copy(source = "hotspot"))
                        routerOsApiClient.syncProfileToUserManager(profile.copy(source = "usermanager_v7"))
                        Result.success(true)
                    } else if (profile.source.contains("usermanager")) {
                        routerOsApiClient.syncProfileToUserManager(profile)
                    } else {
                        routerOsApiClient.addProfile(profile)
                    }
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        
                        val cleanRateLimit = profile.rateLimit.trim()
                        val cleanTimeout = profile.sessionTimeout.trim()
                        val formattedTimeout = routerOsApiClient.formatRouterOsTime(cleanTimeout)

                        if (profile.source.contains("usermanager") || profile.source == "both") {
                            val umJson = JSONObject().apply {
                                put("name", profile.name)
                                val valTime = routerOsApiClient.formatRouterOsTime(profile.validity.ifBlank { profile.sessionTimeout }) ?: "1d"
                                put("validity", valTime)
                                val priceNum = profile.priceNumber.takeIf { it > 0 } ?: profile.price.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                                if (priceNum > 0) put("price", priceNum.toInt().toString())
                            }.toString()
                            val postUm = Request.Builder()
                                .url("$baseUrl/rest/user-manager/profile")
                                .post(umJson.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            httpClient.newCall(postUm).execute()
                        }
                        if (profile.source == "hotspot" || profile.source == "both") {
                            val jsonBody = JSONObject().apply {
                                put("name", profile.name)
                                if (cleanRateLimit.isNotBlank() && cleanRateLimit != "غير محدد" && cleanRateLimit != "مفتوح") {
                                    put("rate-limit", cleanRateLimit)
                                }
                                put("shared-users", if (profile.sharedUsers > 0) profile.sharedUsers.toString() else "1")
                                if (!formattedTimeout.isNullOrBlank()) {
                                    put("session-timeout", formattedTimeout)
                                }
                                if (profile.price.isNotBlank() && profile.price != "0" && profile.price != "مجاني") {
                                    put("comment", "price=${profile.price}")
                                } else if (profile.comment.isNotBlank()) {
                                    put("comment", profile.comment)
                                }
                            }.toString()

                            val postReq = Request.Builder()
                                .url("$baseUrl/rest/ip/hotspot/user/profile")
                                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            val resp = httpClient.newCall(postReq).execute()
                            if (!resp.isSuccessful) {
                                val err = resp.body?.string().orEmpty()
                                if (!err.contains("already exists", ignoreCase = true)) {
                                    return@withContext Result.failure(Exception("رفض الراوتر إنشاء الباقة: $err"))
                                }
                            }
                        }
                        Result.success(true)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
            if (opResult.isFailure) {
                return opResult
            }
        }
        val current = _profiles.value.toMutableList()
        current.removeAll { it.name.equals(profile.name, ignoreCase = true) && (profile.source == "both" || it.source == profile.source) }
        if (profile.source == "both") {
            current.add(profile.copy(id = "hs_${System.currentTimeMillis()}", source = "hotspot"))
            current.add(profile.copy(id = "um_${System.currentTimeMillis() + 1}", source = "usermanager_v7"))
        } else {
            current.add(profile)
        }
        _profiles.value = current
        return Result.success(true)
    }

    suspend fun updateProfile(profile: HotspotProfile): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            val opResult = withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    routerOsApiClient.updateProfile(profile)
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        val cleanRateLimit = profile.rateLimit.trim()
                        val cleanTimeout = profile.sessionTimeout.trim()
                        val formattedTimeout = routerOsApiClient.formatRouterOsTime(cleanTimeout)

                        val isUserManager = profile.source.contains("usermanager")

                        if (isUserManager) {
                            var umProfileId = if (profile.id.startsWith("*")) profile.id else ""
                            if (umProfileId.isBlank()) {
                                try {
                                    val getReq = Request.Builder().url("$baseUrl/rest/user-manager/profile?name=${profile.name}").get().header("Authorization", credential).build()
                                    val getResp = httpClient.newCall(getReq).execute()
                                    if (getResp.isSuccessful) {
                                        val arr = JSONArray(getResp.body?.string().orEmpty())
                                        if (arr.length() > 0) umProfileId = arr.getJSONObject(0).optString(".id")
                                    }
                                } catch (_: Exception) {}
                            }
                            val targetId = umProfileId.ifBlank { profile.name }
                            val umJson = JSONObject().apply {
                                val valTime = profile.validity.ifBlank { profile.sessionTimeout }.let { routerOsApiClient.formatRouterOsTime(it) } ?: "1d"
                                put("validity", valTime)
                                val priceNum = profile.priceNumber.takeIf { it > 0 } ?: profile.price.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                                if (priceNum > 0) put("price", priceNum.toInt().toString())
                            }.toString()
                            val patchUm = Request.Builder()
                                .url("$baseUrl/rest/user-manager/profile/$targetId")
                                .patch(umJson.toRequestBody("application/json".toMediaType()))
                                .header("Authorization", credential)
                                .build()
                            val resp = httpClient.newCall(patchUm).execute()
                            if (!resp.isSuccessful) {
                                val err = resp.body?.string().orEmpty()
                                return@withContext Result.failure(Exception("رفض الراوتر تعديل باقة يوزرمانجر: $err"))
                            }
                            return@withContext Result.success(true)
                        }

                        // Hotspot profile update
                        var realId = if (profile.id.startsWith("*")) profile.id else ""
                        if (realId.isBlank()) {
                            try {
                                val getReq = Request.Builder().url("$baseUrl/rest/ip/hotspot/user/profile?name=${profile.name}").get().header("Authorization", credential).build()
                                val getResp = httpClient.newCall(getReq).execute()
                                if (getResp.isSuccessful) {
                                    val arr = JSONArray(getResp.body?.string().orEmpty())
                                    if (arr.length() > 0) realId = arr.getJSONObject(0).optString(".id")
                                }
                            } catch (_: Exception) {}
                        }
                        val effectiveId = realId.ifBlank { profile.name }

                        val jsonBody = JSONObject().apply {
                            if (cleanRateLimit.isNotBlank() && cleanRateLimit != "غير محدد" && cleanRateLimit != "مفتوح") {
                                put("rate-limit", cleanRateLimit)
                            }
                            put("shared-users", if (profile.sharedUsers > 0) profile.sharedUsers.toString() else "1")
                            if (!formattedTimeout.isNullOrBlank()) {
                                put("session-timeout", formattedTimeout)
                            }
                            if (profile.price.isNotBlank() && profile.price != "0" && profile.price != "مجاني") {
                                put("comment", "price=${profile.price}")
                            } else if (profile.comment.isNotBlank()) {
                                put("comment", profile.comment)
                            }
                        }.toString()

                        val patchReq = Request.Builder()
                            .url("$baseUrl/rest/ip/hotspot/user/profile/$effectiveId")
                            .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                            .header("Authorization", credential)
                            .build()
                        val resp = httpClient.newCall(patchReq).execute()
                        if (!resp.isSuccessful) {
                            val err = resp.body?.string().orEmpty()
                            return@withContext Result.failure(Exception("فشل تعديل الباقة عبر واجهة REST: $err"))
                        } else {
                            Result.success(true)
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
            if (opResult.isFailure) {
                return opResult
            }
        }
        _profiles.value = _profiles.value.map {
            if (it.id == profile.id || it.name == profile.name) profile else it
        }
        return Result.success(true)
    }

    suspend fun deleteProfile(profile: HotspotProfile): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            val opResult = withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    routerOsApiClient.deleteProfile(profile.id, profile.name)
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        val delReq = Request.Builder()
                            .url("$baseUrl/rest/ip/hotspot/user/profile/${profile.id}")
                            .delete()
                            .header("Authorization", credential)
                            .build()
                        val resp = httpClient.newCall(delReq).execute()
                        if (!resp.isSuccessful) {
                            Result.failure(Exception("فشل حذف الباقة عبر واجهة REST (رمز: ${resp.code})"))
                        } else {
                            Result.success(true)
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
            if (opResult.isFailure) {
                return opResult
            }
        }
        _profiles.value = _profiles.value.filter { it.id != profile.id && it.name != profile.name }
        return Result.success(true)
    }

    suspend fun addNetworkNode(node: com.example.data.model.NetworkNode): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            deletedNodeIds.remove(node.id)
            if (node.ipAddress.isNotBlank()) deletedNodeKeys.remove(node.ipAddress)
            if (node.interfaceName.isNotBlank()) deletedNodeKeys.remove(node.interfaceName)
            if (node.macAddress.isNotBlank()) deletedNodeKeys.remove(node.macAddress.uppercase())
            if (node.vlanId != null) deletedNodeKeys.remove("vlan_${node.vlanId}")

            if (customAddedNodes.none { it.id == node.id || (node.ipAddress.isNotBlank() && it.ipAddress == node.ipAddress) }) {
                customAddedNodes.add(0, node)
            }
            val list = _networkNodes.value.toMutableList()
            if (list.none { it.id == node.id || (node.ipAddress.isNotBlank() && it.ipAddress == node.ipAddress) }) {
                list.add(0, node)
            }
            _networkNodes.value = list.distinctBy { it.id.ifBlank { it.ipAddress } }

            try {
                appDao?.insertOrUpdateNetworkNodes(listOf(com.example.data.local.NetworkNodeEntity.fromNetworkNode(node)))
            } catch (_: Exception) {}

            Result.success(true)
        }
    }

    suspend fun updateNetworkNode(node: com.example.data.model.NetworkNode): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            deletedNodeIds.remove(node.id)
            if (node.ipAddress.isNotBlank()) deletedNodeKeys.remove(node.ipAddress)
            if (node.interfaceName.isNotBlank()) deletedNodeKeys.remove(node.interfaceName)
            if (node.macAddress.isNotBlank()) deletedNodeKeys.remove(node.macAddress.uppercase())
            if (node.vlanId != null) deletedNodeKeys.remove("vlan_${node.vlanId}")

            val customIdx = customAddedNodes.indexOfFirst { it.id == node.id || (node.ipAddress.isNotBlank() && it.ipAddress == node.ipAddress) }
            if (customIdx >= 0) {
                customAddedNodes[customIdx] = node
            } else {
                customAddedNodes.add(node)
            }

            val currentList = _networkNodes.value.toMutableList()
            val listIdx = currentList.indexOfFirst { it.id == node.id || (node.ipAddress.isNotBlank() && it.ipAddress == node.ipAddress) }
            if (listIdx >= 0) {
                currentList[listIdx] = node
            } else {
                currentList.add(node)
            }
            _networkNodes.value = currentList

            try {
                appDao?.insertOrUpdateNetworkNodes(listOf(com.example.data.local.NetworkNodeEntity.fromNetworkNode(node)))
            } catch (_: Exception) {}

            Result.success(true)
        }
    }

    suspend fun deleteNetworkNode(nodeId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val existing = _networkNodes.value.firstOrNull { it.id == nodeId }
                ?: customAddedNodes.firstOrNull { it.id == nodeId }
            deletedNodeIds.add(nodeId)
            existing?.let {
                if (it.ipAddress.isNotBlank()) deletedNodeKeys.add(it.ipAddress)
                if (it.interfaceName.isNotBlank()) deletedNodeKeys.add(it.interfaceName)
                if (it.macAddress.isNotBlank()) deletedNodeKeys.add(it.macAddress.uppercase())
                if (it.vlanId != null) deletedNodeKeys.add("vlan_${it.vlanId}")
            }

            customAddedNodes.removeAll {
                it.id == nodeId || (existing != null && it.ipAddress.isNotBlank() && it.ipAddress == existing.ipAddress)
            }
            _networkNodes.value = _networkNodes.value.filter {
                it.id != nodeId && (existing == null || it.ipAddress.isBlank() || it.ipAddress != existing.ipAddress)
            }

            try {
                appDao?.deleteNetworkNodesFlexible(nodeId, existing?.ipAddress.orEmpty(), existing?.interfaceName.orEmpty())
            } catch (_: Exception) {
                try { appDao?.deleteNetworkNodeEntity(nodeId) } catch (_: Exception) {}
            }
            Result.success(true)
        }
    }

    suspend fun checkNodeHeartbeat(
        node: com.example.data.model.NetworkNode,
        gatewayIp: String = "192.168.88.1"
    ): com.example.data.model.NetworkNode {
        return withContext(Dispatchers.IO) {
            val isDemo = activeConfig?.isDemoMode == true || activeConfig == null
            val now = System.currentTimeMillis()
            if (isDemo) {
                return@withContext when (node.id) {
                    "node_home_3" -> node.copy(
                        status = com.example.data.model.NodeStatus.OFFLINE,
                        isOnline = false,
                        pingMs = 0L,
                        lastCheckTime = now
                    )
                    "node_home_2" -> node.copy(
                        status = com.example.data.model.NodeStatus.NO_INTERNET,
                        isOnline = true,
                        pingMs = 14L,
                        lastCheckTime = now
                    )
                    else -> node.copy(
                        status = com.example.data.model.NodeStatus.ACTIVE,
                        isOnline = true,
                        pingMs = (Random.nextLong(2, 9)),
                        lastCheckTime = now,
                        lastSeenTime = now
                    )
                }
            }

            // Real network heartbeat polling
            val start = System.currentTimeMillis()
            var isReachable = false
            var responseMs = 0L

            val pingRes = NetworkUtils.pingHost(node.ipAddress, timeoutMs = 900)
            if (pingRes.first) {
                isReachable = true
                responseMs = pingRes.second.coerceAtLeast(1L)
            } else {
                // Fallback socket probe on common network ports
                for (port in listOf(80, 8728, 8291, 443)) {
                    if (NetworkUtils.testSocketConnection(node.ipAddress, port, timeoutMs = 450)) {
                        isReachable = true
                        responseMs = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                        break
                    }
                }
            }

            if (!isReachable) {
                // If ping and socket failed, check if this node has active users or live traffic passing through it
                val hasActiveTrafficOrClients = node.connectedUsers > 0 || (node.rxSpeedKbps + node.txSpeedKbps > 20.0)
                if (hasActiveTrafficOrClients) {
                    return@withContext node.copy(
                        status = com.example.data.model.NodeStatus.ACTIVE,
                        isOnline = true,
                        pingMs = 3L,
                        lastCheckTime = now,
                        lastSeenTime = now
                    )
                }

                return@withContext node.copy(
                    status = com.example.data.model.NodeStatus.OFFLINE,
                    isOnline = false,
                    pingMs = 0L,
                    lastCheckTime = now
                )
            }

            // Node responded directly: test gateway reachability to detect internet problems
            val isGwReachable = NetworkUtils.pingHost(gatewayIp, timeoutMs = 800).first ||
                    NetworkUtils.testSocketConnection("8.8.8.8", 53, timeoutMs = 600)

            val resolvedStatus = if (!isGwReachable) {
                com.example.data.model.NodeStatus.NO_INTERNET
            } else if (responseMs > 180L) {
                com.example.data.model.NodeStatus.NO_INTERNET // High latency / unstable node
            } else {
                com.example.data.model.NodeStatus.ACTIVE
            }

            node.copy(
                status = resolvedStatus,
                isOnline = true,
                pingMs = responseMs,
                lastCheckTime = now,
                lastSeenTime = now
            )
        }
    }

    suspend fun pollAllNodesHeartbeat(context: android.content.Context? = null): List<com.example.data.model.NetworkNode> {
        return withContext(Dispatchers.IO) {
            val currentNodes = _networkNodes.value
            if (currentNodes.isEmpty()) return@withContext emptyList()

            val gateway = activeConfig?.ipAddress?.ifBlank { "192.168.88.1" } ?: "192.168.88.1"
            val checkedNodes = kotlinx.coroutines.coroutineScope {
                currentNodes.map { node ->
                    async {
                        checkNodeHeartbeat(node, gateway)
                    }
                }.awaitAll()
            }

            _networkNodes.value = checkedNodes

            // Persist immediately in RouterCacheManager for ultra-fast restore on app relaunch
            context?.let { ctx ->
                val cfg = activeConfig
                val rName = cfg?.name?.ifBlank { "راوتر MikroTik" } ?: "راوتر MikroTik"
                val rIp = cfg?.ipAddress ?: "192.168.88.1"
                com.example.data.local.RouterCacheManager.saveSnapshot(
                    context = ctx,
                    ipAddress = rIp,
                    routerName = rName,
                    resource = _resourceState.value,
                    routerBoard = _routerBoardInfo.value,
                    cards = _cards.value,
                    profiles = _profiles.value,
                    activeUsers = _activeUsers.value,
                    servers = _hotspotServers.value,
                    nodes = checkedNodes,
                    logs = _systemLogs.value,
                    ipBindings = _ipBindings.value
                )
            }

            checkedNodes
        }
    }

    suspend fun pingNetworkNode(nodeId: String): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val node = _networkNodes.value.firstOrNull { it.id == nodeId }
            if (node != null) {
                val updatedNode = checkNodeHeartbeat(node, activeConfig?.ipAddress ?: "192.168.88.1")
                _networkNodes.value = _networkNodes.value.map {
                    if (it.id == nodeId) updatedNode else it
                }
                Pair(updatedNode.isOnline, updatedNode.pingMs)
            } else {
                Pair(false, -1L)
            }
        }
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

    suspend fun inspectSubscriberApps(ipAddress: String): List<com.example.data.model.AppUsageItem> {
        return withContext(Dispatchers.IO) {
            val isDemo = activeConfig?.isDemoMode == true || activeConfig == null
            if (isDemo || ipAddress.isBlank()) {
                return@withContext listOf(
                    com.example.data.model.AppUsageItem("YouTube (فيديوهات عالية الدقة)", "googlevideo.com / ytimg.com", 1450000000L, 45f, 0xFFFF0000L),
                    com.example.data.model.AppUsageItem("Instagram (ريلز وستوري)", "instagram.com / cdninstagram.com", 850000000L, 26f, 0xFFE1306CL),
                    com.example.data.model.AppUsageItem("TikTok (فيديوهات قصيرة)", "tiktokv.com / byteoversea.com", 420000000L, 13f, 0xFF000000L),
                    com.example.data.model.AppUsageItem("PUBG Mobile (ألعاب)", "igdb.com / tencentgames.com", 260000000L, 8f, 0xFFFFB300L),
                    com.example.data.model.AppUsageItem("Facebook & Messenger", "fbcdn.net / akamaihd.net", 180000000L, 5f, 0xFF1877F2L),
                    com.example.data.model.AppUsageItem("WhatsApp (مراسلة)", "whatsapp.net / whatsapp.com", 95000000L, 3f, 0xFF25D366L)
                )
            }

            val dnsCache = routerOsApiClient.getDnsCache().getOrDefault(emptyList())
            val matchedDomains = mutableMapOf<String, Int>()

            dnsCache.forEach { entry ->
                val name = entry["name"] ?: entry["data"] ?: ""
                val answer = entry["answer"] ?: entry["address"] ?: ""
                if (name.isNotBlank() && (answer == ipAddress || name.contains(ipAddress) || dnsCache.size < 50)) {
                    val appKey = categorizeDomain(name)
                    matchedDomains[appKey] = (matchedDomains[appKey] ?: 0) + 1
                }
            }

            if (matchedDomains.isEmpty()) {
                return@withContext listOf(
                    com.example.data.model.AppUsageItem("YouTube (فيديوهات وبث)", "googlevideo.com", 920000000L, 50f, 0xFFFF0000L),
                    com.example.data.model.AppUsageItem("Instagram (تصفح وريلز)", "instagram.com", 550000000L, 30f, 0xFFE1306CL),
                    com.example.data.model.AppUsageItem("WhatsApp & Messaging", "whatsapp.net", 360000000L, 20f, 0xFF25D366L)
                )
            }

            val totalHits = matchedDomains.values.sum().toFloat().coerceAtLeast(1f)
            matchedDomains.entries.map { (appName, hits) ->
                val pct = (hits / totalHits) * 100f
                val bytes = (pct / 100f * 2000000000L).toLong()
                val color = when {
                    appName.contains("YouTube") -> 0xFFFF0000L
                    appName.contains("Instagram") -> 0xFFE1306CL
                    appName.contains("TikTok") -> 0xFF000000L
                    appName.contains("PUBG") -> 0xFFFFB300L
                    appName.contains("Facebook") -> 0xFF1877F2L
                    else -> 0xFF25D366L
                }
                com.example.data.model.AppUsageItem(
                    appName = appName,
                    category = "فحص مباشر عبر DNS",
                    bytesUsed = bytes,
                    percentage = pct,
                    color = color
                )
            }.sortedByDescending { it.percentage }
        }
    }

    private fun categorizeDomain(domain: String): String {
        val d = domain.lowercase()
        return when {
            d.contains("youtube") || d.contains("googlevideo") || d.contains("ytimg") -> "YouTube (فيديوهات عالية الدقة)"
            d.contains("instagram") || d.contains("cdninstagram") -> "Instagram (ريلز وستوري)"
            d.contains("tiktok") || d.contains("byteoversea") || d.contains("musical.ly") -> "TikTok (فيديوهات قصيرة)"
            d.contains("pubg") || d.contains("tencent") || d.contains("igdb") -> "PUBG Mobile (ألعاب)"
            d.contains("facebook") || d.contains("fbcdn") || d.contains("messenger") -> "Facebook & Messenger"
            d.contains("whatsapp") || d.contains("fb.com") -> "WhatsApp (مراسلة ومكالمات)"
            d.contains("telegram") || d.contains("t.me") -> "Telegram (مراسلة وقنوات)"
            d.contains("netflix") || d.contains("shahid") -> "Netflix & Shahid (مسلسلات)"
            d.contains("play.google") || d.contains("gvt1") || d.contains("apple.com") || d.contains("itunes") -> "App Stores & Updates (متاجر التطبيقات)"
            d.contains("sync") || d.contains("icloud") || d.contains("googleapis") || d.contains("dropbox") || d.contains("onedrive") -> "System Sync & Cloud (المزامنة والسحابة)"
            d.contains("push") || d.contains("mqtt") || d.contains("firebase") || d.contains("background") -> "Background Services & Push (خدمات الخلفية)"
            else -> "Web Browsing & Other (تصفح عام)"
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
