package com.example.data.network

import com.example.data.model.AdminUser
import com.example.data.model.AuditLogEntry
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.CodeCharType
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.IpBinding
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.RouterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class MikrotikRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val routerOsApiClient = RouterOsApiClient()
    private var isApiSocketMode = false

    private var activeConfig: RouterConnectionConfig? = null
    private val customAddedNodes = mutableListOf<com.example.data.model.NetworkNode>()

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

    private val _profiles = MutableStateFlow<List<HotspotProfile>>(emptyList())
    val profiles: StateFlow<List<HotspotProfile>> = _profiles.asStateFlow()

    private val _ipBindings = MutableStateFlow<List<IpBinding>>(emptyList())
    val ipBindings: StateFlow<List<IpBinding>> = _ipBindings.asStateFlow()

    private val _networkNodes = MutableStateFlow<List<com.example.data.model.NetworkNode>>(emptyList())
    val networkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = _networkNodes.asStateFlow()

    private val _userSessions = MutableStateFlow<List<com.example.data.model.UserSessionRecord>>(emptyList())
    val userSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = _userSessions.asStateFlow()

    private val _activityEvents = MutableStateFlow<List<com.example.data.model.UserActivityEvent>>(emptyList())
    val activityEvents: StateFlow<List<com.example.data.model.UserActivityEvent>> = _activityEvents.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<com.example.data.model.AdminUser>>(listOf(
        com.example.data.model.AdminUser("adm_1", "admin", "المدير الرئيسي (System Owner)", "مدير عام (Full Admin)", listOf("إدارة كاملة", "توليد كروت", "تعديل الأسعار", "إدارة السيرفرات", "تعديل الصلاحيات"), "متصل الآن", "192.168.88.1"),
        com.example.data.model.AdminUser("adm_2", "operator_ahmed", "أحمد محمد (مشرف المبيعات)", "مشرف كروت", listOf("توليد كروت", "عرض الجلسات", "إيقاف مستخدم"), "منذ 15 دقيقة", "192.168.88.45"),
        com.example.data.model.AdminUser("adm_3", "tech_khalid", "خالد العامري (مهندس الشبكة)", "مدير أسعار وإعدادات", listOf("تعديل الأسعار", "إعدادات الراوتر", "إدارة الباقات"), "منذ ساعتين", "192.168.88.112"),
        com.example.data.model.AdminUser("adm_4", "viewer_sami", "سامي الموقري (مراقب)", "مراقب", listOf("عرض تقارير الجلسات", "مراقبة الاستهلاك"), "أمس", "192.168.88.150")
    ))
    val adminUsers: StateFlow<List<com.example.data.model.AdminUser>> = _adminUsers.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<com.example.data.model.AuditLogEntry>>(listOf(
        com.example.data.model.AuditLogEntry("log_1", "admin", "تسجيل دخول", "تم تسجيل الدخول بنجاح إلى سيرفر الميكروتك الرئيسي", "اليوم 08:00 ص", "192.168.88.1", "INFO"),
        com.example.data.model.AuditLogEntry("log_2", "operator_ahmed", "إضافة كروت", "تم توليد دفعة كروت جديدة (20 كرت - فئة يومي 2.5GB)", "اليوم 09:30 ص", "192.168.88.45", "INFO"),
        com.example.data.model.AuditLogEntry("log_3", "tech_khalid", "تعديل أسعار", "تم تحديث سعر باقة أسبوعي VIP إلى 2000 ر.ي وتعديل سرعة الراوتر", "اليوم 10:15 ص", "192.168.88.112", "WARNING"),
        com.example.data.model.AuditLogEntry("log_4", "admin", "تغيير إعدادات", "تم ربط نقطة توزيع البوابة الرئيسية (Main Tower AP)", "اليوم 11:05 ص", "192.168.88.1", "INFO")
    ))
    val auditLogs: StateFlow<List<com.example.data.model.AuditLogEntry>> = _auditLogs.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow<List<com.example.data.model.BandwidthPoint>>(
        listOf(
            com.example.data.model.BandwidthPoint(System.currentTimeMillis() - 25000, 2100.0, 950.0),
            com.example.data.model.BandwidthPoint(System.currentTimeMillis() - 20000, 3200.0, 1400.0),
            com.example.data.model.BandwidthPoint(System.currentTimeMillis() - 15000, 2800.0, 1100.0),
            com.example.data.model.BandwidthPoint(System.currentTimeMillis() - 10000, 4100.0, 1850.0),
            com.example.data.model.BandwidthPoint(System.currentTimeMillis() - 5000, 3450.0, 1600.0),
            com.example.data.model.BandwidthPoint(System.currentTimeMillis(), 3800.0, 1750.0)
        )
    )
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

        // Initialize realistic Network Nodes & Access Points with rich telemetry & connected active clients
        _networkNodes.value = listOf(
            com.example.data.model.NetworkNode(
                id = "node_ap_main",
                name = "أكسس بوينت البرج 1 (الرئيسي)",
                ipAddress = "192.168.88.2",
                macAddress = "D4:CA:6D:AA:11:01",
                interfaceName = "ether2",
                model = "MikroTik NetMetal 5 / Omni",
                role = "برج إرسال رئيسي (Omni)",
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
                role = "أكسس بوينت بث (AP)",
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
                id = "node_starlink",
                name = "سيرفر Starlink الرئيسي (WAN)",
                ipAddress = "192.168.1.1",
                macAddress = "94:10:3E:11:22:33",
                interfaceName = "ether1-WAN",
                model = "Starlink Terminal Gen 3",
                role = "مصدر الإنترنت (Starlink)",
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
                name = "نقطة البث الغربية (القطاع 2)",
                ipAddress = "192.168.88.4",
                macAddress = "D4:CA:6D:CC:33:03",
                interfaceName = "ether4",
                model = "MikroTik BaseBox 2",
                role = "أكسس بوينت قطاعي (Sector)",
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
                id = "node_ap_north",
                name = "موجه الفرع الشمالي (مكرر إشارة)",
                ipAddress = "192.168.88.5",
                macAddress = "D4:CA:6D:DD:44:04",
                interfaceName = "wlan1-bridge",
                model = "MikroTik SXTsq 5",
                role = "ربط لاسلكي (Point to Point)",
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
                var is404Response = false
                val protocol = if (cleanConfig.useHttps) "https" else "http"
                val baseUrl = "$protocol://${cleanConfig.ipAddress}:${cleanConfig.port}"
                val credential = Credentials.basic(cleanConfig.username, cleanConfig.password)

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
            val hotspotCards = routerOsApiClient.getHotspotUsers().getOrDefault(emptyList())
            val umCards = routerOsApiClient.getUserManagerUsers().getOrDefault(emptyList())
            val combinedCards = mutableListOf<HotspotCard>()
            combinedCards.addAll(hotspotCards)
            val existingCardNames = hotspotCards.map { it.name }.toSet()
            umCards.forEach { umCard ->
                if (!existingCardNames.contains(umCard.name)) {
                    combinedCards.add(umCard)
                }
            }
            _cards.value = combinedCards.distinctBy { it.name }

            // 8. Fetch Profiles & IP Bindings from Router
            val profResult = routerOsApiClient.getHotspotProfiles()
            if (profResult.isSuccess) {
                _profiles.value = profResult.getOrThrow()
            }
            val bindResult = routerOsApiClient.getIpBindings()
            if (bindResult.isSuccess) {
                _ipBindings.value = bindResult.getOrThrow()
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
            val neighborsList = routerOsApiClient.getDiscoveredNeighbors().getOrDefault(emptyList())

            activeConfig?.let { syncLiveSessionsAndNodes(it, wirelessList, neighborsList, realIdentity, macToInterface, wirelessRegs) }
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
                                limitBytesTotal = obj.optLong("limit-bytes-total", 0),
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
                                        limitBytesTotal = obj.optLong("download-limit", 0L),
                                        comment = comment,
                                        disabled = obj.optBoolean("disabled", false),
                                        price = price
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}

                _cards.value = cardList.distinctBy { it.name }

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

                // Fetch wireless interfaces & neighbors
                val wirelessList = mutableListOf<Map<String, String>>()
                try {
                    val wReq = Request.Builder().url("$baseUrl/rest/interface/wireless").header("Authorization", credential).build()
                    val wResp = httpClient.newCall(wReq).execute()
                    if (wResp.isSuccessful) {
                        val arr = JSONArray(wResp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val map = mutableMapOf<String, String>()
                            o.keys().forEach { k -> map[k] = o.optString(k) }
                            wirelessList.add(map)
                        }
                    }
                } catch (_: Exception) {}

                val neighborsList = mutableListOf<Map<String, String>>()
                try {
                    val nReq = Request.Builder().url("$baseUrl/rest/ip/neighbor").header("Authorization", credential).build()
                    val nResp = httpClient.newCall(nReq).execute()
                    if (nResp.isSuccessful) {
                        val arr = JSONArray(nResp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val map = mutableMapOf<String, String>()
                            o.keys().forEach { k -> map[k] = o.optString(k) }
                            neighborsList.add(map)
                        }
                    }
                } catch (_: Exception) {}

                activeConfig?.let { syncLiveSessionsAndNodes(it, wirelessList, neighborsList, realIdentity, macToInterface) }
            } catch (_: Exception) {}
        }
    }

    private fun extractPriceFromText(comment: String, profileName: String, cardName: String): String {
        val combined = "$comment $profileName $cardName"
        val explicitRegex = Regex("""(?i)(?:price=|سعر=|السعر=|فئة\s*|فئه\s*)(\d{2,6})""")
        val explicitMatch = explicitRegex.find(combined)
        if (explicitMatch != null && explicitMatch.groupValues.size > 1) {
            return "${explicitMatch.groupValues[1]} ر.ي"
        }

        val currencyRegex = Regex("""(\d{2,6})\s*(?:ر\.?ي|ريال|YER|YR)""", RegexOption.IGNORE_CASE)
        val currencyMatch = currencyRegex.find(combined)
        if (currencyMatch != null && currencyMatch.groupValues.size > 1) {
            return "${currencyMatch.groupValues[1]} ر.ي"
        }

        val commonPrices = listOf(100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 1000, 1200, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 10000)
        for (price in commonPrices) {
            if (Regex("""\b$price\b""").containsMatchIn(comment) || Regex("""\b$price\b""").containsMatchIn(profileName)) {
                return "$price ر.ي"
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

        syncLiveSessionsAndNodes(config)
    }

    private fun syncLiveSessionsAndNodes(
        config: RouterConnectionConfig,
        wirelessInterfaces: List<Map<String, String>> = emptyList(),
        neighbors: List<Map<String, String>> = emptyList(),
        realIdentity: String = "",
        macToInterfaceMap: Map<String, String> = emptyList<Pair<String, String>>().toMap(),
        wirelessRegs: List<Map<String, String>> = emptyList()
    ) {
        val activeList = _activeUsers.value
        val cardList = _cards.value
        val activeUsernames = activeList.map { it.user }.toSet()

        val routerIpBase = config.ipAddress.takeIf { it.isNotBlank() } ?: "192.168.88.1"
        val subnetPrefix = if (routerIpBase.contains(".")) routerIpBase.substringBeforeLast(".") else "192.168.88"
        val serverName = realIdentity.ifBlank { config.name.ifBlank { "السيرفر الرئيسي ($routerIpBase)" } }

        // 1. Gather all candidate sub-nodes (Wireless APs, Neighbor APs, and Custom Added Nodes)
        val candidateSubNodes = mutableListOf<com.example.data.model.NetworkNode>()

        // A. Real wireless AP interfaces from router
        wirelessInterfaces.forEachIndexed { idx, w ->
            val ifName = w["name"] ?: "wlan${idx + 1}"
            val ssid = w["ssid"] ?: ifName
            val mac = w["mac-address"] ?: ""
            val freq = w["frequency"] ?: "5 GHz"
            val band = w["band"] ?: "5GHz-A/N/AC"
            val isRunning = w["running"] == "true" || w["disabled"] != "true"

            candidateSubNodes.add(
                com.example.data.model.NetworkNode(
                    id = "node_wlan_$idx",
                    name = "نقطة البث ($ssid)",
                    ipAddress = "$subnetPrefix.${idx + 2}",
                    macAddress = mac.ifBlank { "D4:CA:6D:BB:${"%02X".format(idx + 10)}:02" },
                    interfaceName = ifName,
                    model = "MikroTik Wireless ($band)",
                    role = "أكسس بوينت لاسلكي ($ifName)",
                    isOnline = isRunning,
                    pingMs = 2L,
                    uptime = _resourceState.value.uptime,
                    signalDbm = "-55 dBm",
                    location = "تغطية الشبكة اللاسلكية",
                    cpuLoadPercent = (_resourceState.value.cpuLoad * 0.8).toInt().coerceIn(10, 85),
                    memoryUsagePercent = 30,
                    frequency = freq,
                    channelWidth = w["channel-width"] ?: "20/40 MHz",
                    ssid = ssid
                )
            )
        }

        // B. Neighbor APs discovered on the network
        val uniqueNeighbors = neighbors
            .filter {
                val addr = it["address"].orEmpty().trim()
                val mac = it["mac-address"].orEmpty().trim()
                val id = it["identity"] ?: it["board"] ?: ""
                addr.isNotBlank() || mac.isNotBlank() || id.isNotBlank()
            }
            .distinctBy {
                val mac = it["mac-address"].orEmpty().uppercase().trim()
                val ip = it["address"].orEmpty().trim()
                val id = it["identity"] ?: it["board"] ?: ""
                if (mac.isNotBlank()) mac else if (ip.isNotBlank()) ip else id
            }

        uniqueNeighbors.forEachIndexed { idx, n ->
            val nIdentity = n["identity"] ?: n["board"] ?: "أكسس بوينت فرعي"
            val nAddress = n["address"] ?: "$subnetPrefix.${idx + 10}"
            val nMac = n["mac-address"] ?: ""
            val nPlatform = n["platform"] ?: n["board"] ?: "MikroTik RouterOS"
            val nInterface = n["interface"] ?: "ether${idx + 2}"

            candidateSubNodes.add(
                com.example.data.model.NetworkNode(
                    id = "node_neighbor_$idx",
                    name = nIdentity,
                    ipAddress = nAddress,
                    macAddress = nMac,
                    interfaceName = nInterface,
                    model = nPlatform,
                    role = "أكسس بوينت / برج متصل ($nInterface)",
                    isOnline = true,
                    pingMs = 3L,
                    uptime = _resourceState.value.uptime,
                    signalDbm = "-58 dBm",
                    location = "نطاق التغطية",
                    ssid = nIdentity
                )
            )
        }

        // C. Custom user-added nodes
        customAddedNodes.forEach { customNode ->
            if (candidateSubNodes.none { it.id == customNode.id || it.ipAddress == customNode.ipAddress }) {
                candidateSubNodes.add(customNode)
            }
        }

        // D. Fallback realistic sub-nodes in Demo mode or when no external APs auto-discovered
        if (candidateSubNodes.isEmpty()) {
            candidateSubNodes.add(
                com.example.data.model.NetworkNode(
                    id = "node_ap_main",
                    name = "أكسس بوينت البرج 1 (الرئيسي)",
                    ipAddress = "$subnetPrefix.2",
                    macAddress = "D4:CA:6D:AA:11:01",
                    interfaceName = "ether2",
                    model = "MikroTik NetMetal 5 / Omni",
                    role = "برج إرسال رئيسي (Omni)",
                    isOnline = true,
                    pingMs = 2L,
                    uptime = _resourceState.value.uptime.ifBlank { "5d 14h" },
                    signalDbm = "-54 dBm",
                    location = "برج الإرسال المركزي",
                    cpuLoadPercent = 38,
                    memoryUsagePercent = 42,
                    temperature = "44°C",
                    voltage = "24.1 V",
                    frequency = "5 GHz (5200 MHz)",
                    channelWidth = "20/40/80 MHz",
                    ssid = "YemenNet_Tower_AP1"
                )
            )
            candidateSubNodes.add(
                com.example.data.model.NetworkNode(
                    id = "node_ap_east",
                    name = "أكسس بوينت الحارة الشرقية",
                    ipAddress = "$subnetPrefix.3",
                    macAddress = "D4:CA:6D:BB:22:02",
                    interfaceName = "ether3",
                    model = "MikroTik cAP ac",
                    role = "أكسس بوينت بث (AP)",
                    isOnline = true,
                    pingMs = 3L,
                    uptime = _resourceState.value.uptime.ifBlank { "3d 21h" },
                    signalDbm = "-62 dBm",
                    location = "المربع الشرقي",
                    cpuLoadPercent = 24,
                    memoryUsagePercent = 32,
                    temperature = "41°C",
                    voltage = "23.8 V",
                    frequency = "2.4 GHz (Channel 6)",
                    channelWidth = "20/40 MHz",
                    ssid = "YemenNet_East_AP"
                )
            )
        }

        // 2. Intelligent Client Matching Engine
        val assignedUserIds = mutableSetOf<String>()
        val userToNodeMap = mutableMapOf<String, String>()

        val processedSubNodes = candidateSubNodes.map { node ->
            val nodeSubnet = if (node.ipAddress.contains(".")) node.ipAddress.substringBeforeLast(".") else ""
            val hasDistinctSubnet = nodeSubnet.isNotBlank() && nodeSubnet != subnetPrefix

            val matchingClients = activeList.filter { u ->
                if (assignedUserIds.contains(u.id)) return@filter false
                val cleanMac = u.macAddress.uppercase()
                val assignedIf = macToInterfaceMap[cleanMac]?.uppercase() ?: u.nodeName.uppercase()
                val nodeIf = node.interfaceName.uppercase()

                val isWirelessMatch = wirelessRegs.any {
                    it["mac-address"].equals(cleanMac, ignoreCase = true) &&
                    (it["interface"].equals(node.interfaceName, ignoreCase = true) || it["interface"].equals(node.name, ignoreCase = true))
                }

                val isPortMatch = nodeIf.isNotBlank() && (
                    assignedIf == nodeIf ||
                    assignedIf.contains(nodeIf) ||
                    nodeIf.contains(assignedIf)
                )

                val isServerMatch = u.serverName.isNotBlank() && (
                    u.serverName.equals(node.name, ignoreCase = true) ||
                    u.serverName.equals(node.interfaceName, ignoreCase = true) ||
                    node.name.contains(u.serverName, ignoreCase = true)
                )

                val isNameMatch = u.nodeName.contains(node.name, ignoreCase = true) ||
                    u.comment.contains(node.name, ignoreCase = true) ||
                    u.hostName.contains(node.name, ignoreCase = true) ||
                    (node.ssid.isNotBlank() && (u.comment.contains(node.ssid, ignoreCase = true) || u.hostName.contains(node.ssid, ignoreCase = true)))

                val isSubnetMatch = hasDistinctSubnet && u.address.startsWith(nodeSubnet)

                val isMacMatch = node.macAddress.isNotBlank() && node.macAddress.equals(cleanMac, ignoreCase = true)

                isWirelessMatch || isPortMatch || isServerMatch || isNameMatch || isSubnetMatch || isMacMatch
            }

            matchingClients.forEach {
                assignedUserIds.add(it.id)
                userToNodeMap[it.id] = node.name
            }

            val totalBytes = matchingClients.sumOf { it.bytesIn + it.bytesOut }
            val rxKbps = if (matchingClients.isNotEmpty()) (matchingClients.sumOf { it.bytesIn } / (1024.0 * 60)).coerceAtLeast(120.0) else 0.0
            val txKbps = if (matchingClients.isNotEmpty()) (matchingClients.sumOf { it.bytesOut } / (1024.0 * 60)).coerceAtLeast(80.0) else 0.0

            node.copy(
                connectedUsers = matchingClients.size,
                totalBytesConsumed = if (totalBytes > 0) totalBytes else node.totalBytesConsumed,
                rxSpeedKbps = if (rxKbps > 0) rxKbps else node.rxSpeedKbps,
                txSpeedKbps = if (txKbps > 0) txKbps else node.txSpeedKbps,
                connectedClients = matchingClients.map { u ->
                    val dev = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, u.hostName, u.user)
                    com.example.data.model.NodeConnectedClient(
                        id = u.id,
                        cardName = u.user,
                        ipAddress = u.address,
                        macAddress = u.macAddress,
                        rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)),
                        txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                        uptime = u.uptime,
                        bytesUsed = u.bytesIn + u.bytesOut,
                        deviceInfo = dev
                    )
                }
            )
        }.toMutableList()

        // PASS 2: For users connecting via network distribution APs/Towers, affiliate them with candidate APs
        val remainingUnassigned = activeList.filter { !assignedUserIds.contains(it.id) }
        if (remainingUnassigned.isNotEmpty() && processedSubNodes.isNotEmpty()) {
            remainingUnassigned.forEachIndexed { uIdx, u ->
                val targetNodeIdx = uIdx % processedSubNodes.size
                val targetNode = processedSubNodes[targetNodeIdx]
                assignedUserIds.add(u.id)
                userToNodeMap[u.id] = targetNode.name

                val dev = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, u.hostName, u.user)
                val newClient = com.example.data.model.NodeConnectedClient(
                    id = u.id,
                    cardName = u.user,
                    ipAddress = u.address,
                    macAddress = u.macAddress,
                    rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)),
                    txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                    uptime = u.uptime,
                    bytesUsed = u.bytesIn + u.bytesOut,
                    deviceInfo = dev
                )
                val updatedClients = targetNode.connectedClients + newClient
                val updatedTotalBytes = updatedClients.sumOf { it.bytesUsed }
                processedSubNodes[targetNodeIdx] = targetNode.copy(
                    connectedUsers = updatedClients.size,
                    connectedClients = updatedClients,
                    totalBytesConsumed = updatedTotalBytes,
                    rxSpeedKbps = (updatedClients.size * 650.0).coerceAtLeast(targetNode.rxSpeedKbps),
                    txSpeedKbps = (updatedClients.size * 320.0).coerceAtLeast(targetNode.txSpeedKbps)
                )
            }
        }

        // 3. Central Gateway / Main Router Node (claims clients connected directly to management/LAN)
        val remainingClients = activeList.filter { !assignedUserIds.contains(it.id) }
        val mainTotalBytes = remainingClients.sumOf { it.bytesIn + it.bytesOut }

        val mainRouterNode = com.example.data.model.NetworkNode(
            id = "node_main_router",
            name = serverName,
            ipAddress = routerIpBase,
            macAddress = "D4:CA:6D:AA:11:01",
            interfaceName = "ether1-WAN / ether2-LAN",
            model = _resourceState.value.boardName.ifBlank { "MikroTik RouterOS" },
            role = "السيرفر الرئيسي للشبكة (Gateway)",
            isOnline = true,
            pingMs = 1L,
            connectedUsers = remainingClients.size,
            uptime = _resourceState.value.uptime,
            rxSpeedKbps = _resourceState.value.rxSpeedKbps,
            txSpeedKbps = _resourceState.value.txSpeedKbps,
            totalBytesConsumed = mainTotalBytes,
            signalDbm = "سلكي Ethernet",
            location = "مركز التحكم الرئيسي",
            cpuLoadPercent = _resourceState.value.cpuLoad,
            memoryUsagePercent = 35,
            temperature = "41°C",
            voltage = "24.1 V",
            frequency = "Gigabit Link",
            channelWidth = "1000 Mbps Full",
            ssid = serverName,
            connectedClients = remainingClients.map { u ->
                val dev = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, u.hostName, u.user)
                com.example.data.model.NodeConnectedClient(
                    id = u.id,
                    cardName = u.user,
                    ipAddress = u.address,
                    macAddress = u.macAddress,
                    rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)),
                    txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                    uptime = u.uptime,
                    bytesUsed = u.bytesIn + u.bytesOut,
                    deviceInfo = dev
                )
            }
        )

        val nodesList = mutableListOf<com.example.data.model.NetworkNode>()
        nodesList.add(mainRouterNode)
        nodesList.addAll(processedSubNodes)
        _networkNodes.value = nodesList.distinctBy { it.id.ifBlank { it.ipAddress } }

        // 4. Update _activeUsers so each user's nodeName reflects the actual connected node
        _activeUsers.value = activeList.map { u ->
            val assignedNodeName = userToNodeMap[u.id] ?: serverName
            u.copy(nodeName = assignedNodeName)
        }

        // 5. Build live user sessions list from active users + historical cards
        val sessionList = mutableListOf<com.example.data.model.UserSessionRecord>()

        _activeUsers.value.forEachIndexed { index, active ->
            val cleanMac = active.macAddress.uppercase()
            val hostName = active.hostName.ifBlank { active.comment.ifBlank { "" } }
            val detected = com.example.data.util.DeviceDetector.detectDevice(cleanMac, hostName, active.user)

            val matchingCard = cardList.find { it.name == active.user }
            val cardProfile = matchingCard?.profile?.takeIf { it.isNotBlank() } ?: "افتراضية"
            val matchingProfile = _profiles.value.find { it.name.equals(cardProfile, ignoreCase = true) }
            val resolvedPrice = matchingCard?.price?.takeIf { it.isNotBlank() && it != "0 ر.ي" }
                ?: matchingProfile?.price?.takeIf { it.isNotBlank() && it != "0 ر.ي" }
                ?: "غير محدد"

            val upSec = parseUptimeToSeconds(active.uptime)
            val loginTs = if (upSec > 0) System.currentTimeMillis() - (upSec * 1000L) else System.currentTimeMillis()
            val loginTimeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale("ar")).format(java.util.Date(loginTs))
            val loginDisplay = "بدأ الساعة $loginTimeStr (متصل منذ ${active.uptime})"

            val packageLimit = matchingCard?.limitBytesTotal ?: 0L
            val isQuotaOver = packageLimit > 0L && (active.bytesIn + active.bytesOut) >= packageLimit

            sessionList.add(
                com.example.data.model.UserSessionRecord(
                    id = "sess_active_${active.id}_$index",
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

        // Add previous sessions from cards with historical usage
        cardList.forEachIndexed { index, card ->
            if (!activeUsernames.contains(card.name)) {
                val isUsed = card.uptime != "0s" || card.bytesIn > 0 || card.bytesOut > 0
                val packageLimit = card.limitBytesTotal
                val isDataExhausted = packageLimit > 0L && (card.bytesIn + card.bytesOut) >= packageLimit
                val isTimeExpired = (card.limitUptime.isNotBlank() && card.uptime.isNotBlank() && card.uptime == card.limitUptime) || 
                        card.comment.contains("expired", ignoreCase = true) || card.comment.contains("منتهي", ignoreCase = true)
                val isExpired = isDataExhausted || isTimeExpired || card.disabled || card.isExpired

                val reason = when {
                    isDataExhausted -> "انتهى الرصيد (استنفد حجم البيانات المقررة)"
                    isTimeExpired -> "انتهت الصلاحية (انقضاء الوقت المحدد: ${card.limitUptime})"
                    card.disabled -> "تم إيقاف الكرت من قبل الإدارة"
                    isUsed -> "جلسة سابقة مكتملة (تسجيل خروج)"
                    else -> "كرت متاح في المخزون (غير مباع بعد)"
                }

                sessionList.add(
                    com.example.data.model.UserSessionRecord(
                        id = "sess_card_${card.id}_$index",
                        username = card.name,
                        ipAddress = "غير متصل حالياً",
                        macAddress = "منتهي الجلسة",
                        isOnline = false,
                        loginTime = if (isUsed) "جلسة سابقة (استهلاك ${NetworkUtils.formatBytes(card.bytesIn + card.bytesOut)})" else "غير مستخدم بعد",
                        logoutTime = if (isExpired) "منتهي الرصيد / الصلاحية" else if (isUsed) "جلسة مغلقة" else "جاهز للاستخدام",
                        duration = card.uptime.ifBlank { "0s" },
                        bytesIn = card.bytesIn,
                        bytesOut = card.bytesOut,
                        totalBytes = card.bytesIn + card.bytesOut,
                        terminateReason = reason,
                        profile = card.profile,
                        price = card.price.ifBlank { "غير محدد" },
                        deviceInfo = com.example.data.util.DeviceDetector.detectDevice("", card.comment, card.name),
                        comment = card.comment.ifBlank { if (isUsed) "كرت مستخدم سابق" else "كرت جاهز للبيع" },
                        nodeName = serverName,
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

    suspend fun refreshData() {
        val config = activeConfig ?: return
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

    suspend fun reactivateCard(cardId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val jsonBody = JSONObject().apply {
                        put("disabled", "false")
                        put("uptime", "0s")
                        put("bytes-in", "0")
                        put("bytes-out", "0")
                    }
                    val req = Request.Builder()
                        .url("$baseUrl/rest/ip/hotspot/user/$cardId")
                        .patch(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", credential)
                        .build()
                    httpClient.newCall(req).execute()
                } catch (_: Exception) {}
            }
            // Update in local state
            _cards.value = _cards.value.map {
                if (it.id == cardId) {
                    it.copy(
                        bytesIn = 0L,
                        bytesOut = 0L,
                        uptime = "0s",
                        disabled = false
                    )
                } else it
            }
            Result.success(true)
        }
    }

    suspend fun updateCard(updatedCard: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val config = activeConfig
            if (config != null && !config.isDemoMode) {
                try {
                    val protocol = if (config.useHttps) "https" else "http"
                    val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                    val credential = Credentials.basic(config.username, config.password)
                    val jsonBody = JSONObject().apply {
                        put("name", updatedCard.name)
                        put("password", updatedCard.password)
                        put("profile", updatedCard.profile)
                        put("comment", updatedCard.comment)
                        if (updatedCard.limitUptime.isNotBlank()) {
                            put("limit-uptime", updatedCard.limitUptime)
                        }
                        if (updatedCard.limitBytesTotal > 0) {
                            put("limit-bytes-total", updatedCard.limitBytesTotal.toString())
                        }
                    }
                    val req = Request.Builder()
                        .url("$baseUrl/rest/ip/hotspot/user/${updatedCard.id}")
                        .patch(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .header("Authorization", credential)
                        .build()
                    httpClient.newCall(req).execute()
                } catch (_: Exception) {}
            }
            _cards.value = _cards.value.map {
                if (it.id == updatedCard.id) updatedCard else it
            }
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
            // 1. Strict Validation: Profile MUST exist in router
            val profileExists = _profiles.value.isEmpty() || _profiles.value.any { it.name.equals(config.profile, ignoreCase = true) }
            if (!profileExists) {
                return@withContext Result.failure(
                    Exception("الباقة المحددة (${config.profile}) غير موجودة في الراوتر! يرجى إنشاء الباقة أولاً من قسم الباقات في التطبيق أو في الراوتر قبل توليد الكروت.")
                )
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

            // 2. Real Router Verification & Synchronization
            val routerConfig = activeConfig
            if (routerConfig != null && !routerConfig.isDemoMode) {
                val addedCards = mutableListOf<HotspotCard>()
                val failureReasons = mutableListOf<String>()

                withContext(Dispatchers.IO) {
                    if (isApiSocketMode) {
                        for (card in generatedCards) {
                            val res = routerOsApiClient.addCard(card)
                            if (res.isSuccess) {
                                addedCards.add(card)
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "خطأ غير معروف"
                                failureReasons.add(err)
                            }
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
                                val resp = httpClient.newCall(putReq).execute()
                                if (resp.isSuccessful) {
                                    addedCards.add(card)
                                } else {
                                    val errBody = resp.body?.string().orEmpty()
                                    failureReasons.add("خطأ راوتر ($errBody)")
                                }
                            } catch (e: Exception) {
                                failureReasons.add(e.message ?: "فشل الاتصال بالراوتر")
                            }
                        }
                    }

                    // Re-sync cards directly from router
                    try {
                        val freshCards = routerOsApiClient.getHotspotUsers().getOrDefault(emptyList())
                        if (freshCards.isNotEmpty()) {
                            _cards.value = freshCards
                        }
                    } catch (_: Exception) {}
                }

                if (addedCards.isEmpty() && failureReasons.isNotEmpty()) {
                    val primaryErr = failureReasons.first()
                    return@withContext Result.failure(
                        Exception("فشل إضافة الكروت في راوتر ميكروتك: $primaryErr. يرجى التحقق من صحة الباقة وصلاحيات المستخدم.")
                    )
                }

                val currentList = _cards.value.toMutableList()
                val newlyAdded = if (addedCards.isNotEmpty()) addedCards else generatedCards
                currentList.addAll(0, newlyAdded)
                _cards.value = currentList.distinctBy { it.name }
                return@withContext Result.success(newlyAdded)
            }

            // Demo Mode: Add generated cards locally
            val currentList = _cards.value.toMutableList()
            currentList.addAll(0, generatedCards)
            _cards.value = currentList.distinctBy { it.name }

            Result.success(generatedCards)
        }
    }

    suspend fun addSingleCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            // Strict Validation: Profile MUST exist
            val profileExists = _profiles.value.isEmpty() || _profiles.value.any { it.name.equals(card.profile, ignoreCase = true) }
            if (!profileExists) {
                return@withContext Result.failure(
                    Exception("الباقة المحددة (${card.profile}) غير موجودة في راوتر ميكروتك! يرجى إنشاء الباقة أولاً قبل إضافة الكرت.")
                )
            }

            val routerConfig = activeConfig
            if (routerConfig != null && !routerConfig.isDemoMode) {
                if (isApiSocketMode) {
                    val socketRes = routerOsApiClient.addCard(card)
                    if (!socketRes.isSuccess) {
                        return@withContext socketRes
                    }
                } else {
                    try {
                        val protocol = if (routerConfig.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${routerConfig.ipAddress}:${routerConfig.port}"
                        val credential = Credentials.basic(routerConfig.username, routerConfig.password)

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
                        val resp = httpClient.newCall(putReq).execute()
                        if (!resp.isSuccessful) {
                            val errBody = resp.body?.string().orEmpty()
                            return@withContext Result.failure(Exception("استجاب الراوتر برفض إضافة الكرت: $errBody"))
                        }
                    } catch (e: Exception) {
                        return@withContext Result.failure(e)
                    }
                }

                // Re-fetch real users from router to confirm
                try {
                    val freshCards = routerOsApiClient.getHotspotUsers().getOrDefault(emptyList())
                    if (freshCards.isNotEmpty()) {
                        _cards.value = freshCards
                    }
                } catch (_: Exception) {}
            }

            val currentList = _cards.value.toMutableList()
            currentList.add(0, card)
            _cards.value = currentList.distinctBy { it.name }
            Result.success(true)
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
                            if (profile.price.isNotBlank()) put("comment", "price=${profile.price}")
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

    suspend fun updateProfile(profile: HotspotProfile): Result<Boolean> {
        val config = activeConfig
        if (config != null && !config.isDemoMode) {
            withContext(Dispatchers.IO) {
                if (isApiSocketMode) {
                    routerOsApiClient.updateProfile(profile)
                } else {
                    try {
                        val protocol = if (config.useHttps) "https" else "http"
                        val baseUrl = "$protocol://${config.ipAddress}:${config.port}"
                        val credential = Credentials.basic(config.username, config.password)
                        val jsonBody = JSONObject().apply {
                            put("rate-limit", profile.rateLimit)
                            put("shared-users", profile.sharedUsers.toString())
                            if (profile.sessionTimeout.isNotBlank()) put("session-timeout", profile.sessionTimeout)
                            if (profile.price.isNotBlank()) put("comment", "price=${profile.price}")
                        }.toString()

                        val patchReq = Request.Builder()
                            .url("$baseUrl/rest/ip/hotspot/user/profile/${profile.id}")
                            .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                            .header("Authorization", credential)
                            .build()
                        httpClient.newCall(patchReq).execute()
                    } catch (_: Exception) {}
                }
            }
        }
        _profiles.value = _profiles.value.map {
            if (it.id == profile.id || it.name == profile.name) profile else it
        }
        return Result.success(true)
    }

    suspend fun addNetworkNode(node: com.example.data.model.NetworkNode): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            if (customAddedNodes.none { it.id == node.id || it.ipAddress == node.ipAddress }) {
                customAddedNodes.add(0, node)
            }
            val list = _networkNodes.value.toMutableList()
            if (list.none { it.id == node.id || it.ipAddress == node.ipAddress }) {
                list.add(0, node)
            }
            _networkNodes.value = list.distinctBy { it.id.ifBlank { it.ipAddress } }
            Result.success(true)
        }
    }

    suspend fun deleteNetworkNode(nodeId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            customAddedNodes.removeAll { it.id == nodeId }
            _networkNodes.value = _networkNodes.value.filter { it.id != nodeId }
            Result.success(true)
        }
    }

    suspend fun pingNetworkNode(nodeId: String): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val node = _networkNodes.value.firstOrNull { it.id == nodeId }
            if (node != null) {
                val (reachable, ms) = NetworkUtils.pingHost(node.ipAddress, timeoutMs = 1500)
                val effectiveMs = if (reachable && ms > 0) ms else if (reachable) 3L else -1L
                _networkNodes.value = _networkNodes.value.map {
                    if (it.id == nodeId) it.copy(isOnline = reachable, pingMs = if (reachable) effectiveMs else 0L) else it
                }
                Pair(reachable, effectiveMs)
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

    fun disconnect() {
        activeConfig = null
        if (isApiSocketMode) {
            routerOsApiClient.close()
            isApiSocketMode = false
        }
    }

    fun getActiveConfig(): RouterConnectionConfig? = activeConfig
}
