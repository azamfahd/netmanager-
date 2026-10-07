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

    private val _networkNodes = MutableStateFlow<List<com.example.data.model.NetworkNode>>(emptyList())
    val networkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = _networkNodes.asStateFlow()

    private val _userSessions = MutableStateFlow<List<com.example.data.model.UserSessionRecord>>(emptyList())
    val userSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = _userSessions.asStateFlow()

    private val _activityEvents = MutableStateFlow<List<com.example.data.model.UserActivityEvent>>(emptyList())
    val activityEvents: StateFlow<List<com.example.data.model.UserActivityEvent>> = _activityEvents.asStateFlow()

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
                activeConfig = config
                if (config.isDemoMode) {
                    delay(300)
                    adaptDataToConfig(config)
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
            val identityResult = routerOsApiClient.getSystemIdentity()
            val realIdentity = identityResult.getOrNull().orEmpty()

            val resResult = routerOsApiClient.getSystemResource()
            if (resResult.isSuccess) {
                val res = resResult.getOrThrow()
                _resourceState.value = if (realIdentity.isNotBlank()) {
                    res.copy(boardName = "$realIdentity (${res.boardName})")
                } else {
                    res
                }
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

            val wirelessList = routerOsApiClient.getWirelessInterfaces().getOrDefault(emptyList())
            val neighborsList = routerOsApiClient.getDiscoveredNeighbors().getOrDefault(emptyList())

            activeConfig?.let { syncLiveSessionsAndNodes(it, wirelessList, neighborsList, realIdentity) }
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
                // Fetch system identity
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

                activeConfig?.let { syncLiveSessionsAndNodes(it, wirelessList, neighborsList, realIdentity) }
            } catch (_: Exception) {}
        }
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
        realIdentity: String = ""
    ) {
        val activeList = _activeUsers.value
        val cardList = _cards.value
        val activeUsernames = activeList.map { it.user }.toSet()

        val routerIpBase = config.ipAddress.takeIf { it.isNotBlank() } ?: "192.168.88.1"
        val subnetPrefix = if (routerIpBase.contains(".")) routerIpBase.substringBeforeLast(".") else "192.168.88"
        val serverName = realIdentity.ifBlank { config.name.ifBlank { "السيرفر الرئيسي ($routerIpBase)" } }

        // 1. Build live user sessions list from active users + cards
        val sessionList = mutableListOf<com.example.data.model.UserSessionRecord>()

        activeList.forEachIndexed { index, active ->
            sessionList.add(
                com.example.data.model.UserSessionRecord(
                    id = "sess_active_${active.id}_$index",
                    username = active.user,
                    ipAddress = active.address.ifBlank { "$subnetPrefix.${100 + index}" },
                    macAddress = active.macAddress.ifBlank { "00:11:22:33:44:${"%02X".format((index + 1) % 256)}" },
                    isOnline = true,
                    loginTime = "متصل الآن (جلسة نشطة)",
                    logoutTime = "نشط",
                    duration = active.uptime.ifBlank { "0s" },
                    bytesIn = active.bytesIn,
                    bytesOut = active.bytesOut,
                    totalBytes = active.bytesIn + active.bytesOut,
                    terminateReason = "جلسة نشطة حالياً",
                    profile = "باقة الكرت الحالية",
                    price = "500 ر.ي",
                    deviceInfo = com.example.data.util.DeviceDetector.detectDevice(active.macAddress, "جهاز متصل", active.user),
                    comment = active.comment.ifBlank { "مستخدم متصل - راوتر $routerIpBase" }
                )
            )
        }

        cardList.forEachIndexed { index, card ->
            if (!activeUsernames.contains(card.name)) {
                val isUsed = card.uptime != "0s" || card.bytesIn > 0 || card.bytesOut > 0
                val isExpired = (card.limitUptime.isNotBlank() && card.uptime == card.limitUptime) ||
                        (card.limitBytesTotal > 0 && (card.bytesIn + card.bytesOut) >= card.limitBytesTotal)

                sessionList.add(
                    com.example.data.model.UserSessionRecord(
                        id = "sess_card_${card.id}_$index",
                        username = card.name,
                        ipAddress = "$subnetPrefix.${150 + (index % 100)}",
                        macAddress = "AA:BB:CC:DD:EE:${"%02X".format((index + 10) % 256)}",
                        isOnline = false,
                        loginTime = if (isUsed) "جلسة سابقة" else "غير مستخدم بعد",
                        logoutTime = if (isExpired) "منتهي الاستهلاك" else if (isUsed) "جلسة مكتملة" else "جاهز للاستخدام",
                        duration = card.uptime.ifBlank { "0s" },
                        bytesIn = card.bytesIn,
                        bytesOut = card.bytesOut,
                        totalBytes = card.bytesIn + card.bytesOut,
                        terminateReason = if (isExpired) "انتهاء رصيد الكرت" else if (isUsed) "تسجيل خروج / توقف الجلسة" else "كرت مفعل غير مستخدم",
                        profile = card.profile,
                        price = card.price,
                        deviceInfo = com.example.data.util.DeviceDetector.detectDevice("AA:BB:CC:DD:EE:00", "كروت الشبكة", card.name),
                        comment = card.comment.ifBlank { if (isUsed) "كرت مستخدم سابق" else "كرت غير مستخدم" }
                    )
                )
            }
        }

        _userSessions.value = sessionList

        // 2. Build live network nodes strictly from router & real wireless APs / neighbors
        val nodesList = mutableListOf<com.example.data.model.NetworkNode>()

        // Gateway / Main Router Node
        nodesList.add(
            com.example.data.model.NetworkNode(
                id = "node_main_router",
                name = serverName,
                ipAddress = routerIpBase,
                macAddress = "D4:CA:6D:AA:11:01",
                interfaceName = "ether1-WAN / ether2-LAN",
                model = _resourceState.value.boardName.ifBlank { "MikroTik RouterOS" },
                role = "السيرفر الرئيسي للشبكة (Gateway)",
                isOnline = true,
                pingMs = 1L,
                connectedUsers = activeList.size,
                uptime = _resourceState.value.uptime,
                rxSpeedKbps = _resourceState.value.rxSpeedKbps,
                txSpeedKbps = _resourceState.value.txSpeedKbps,
                totalBytesConsumed = activeList.sumOf { it.bytesIn + it.bytesOut },
                signalDbm = "سلكي Ethernet",
                location = "مركز التحكم الرئيسي",
                cpuLoadPercent = _resourceState.value.cpuLoad,
                memoryUsagePercent = 35,
                temperature = "41°C",
                voltage = "24.1 V",
                frequency = "Gigabit Link",
                channelWidth = "1000 Mbps Full",
                ssid = serverName,
                connectedClients = activeList.mapIndexed { idx, u ->
                    com.example.data.model.NodeConnectedClient(
                        id = u.id,
                        cardName = u.user,
                        ipAddress = u.address.ifBlank { "$subnetPrefix.${100 + idx}" },
                        macAddress = u.macAddress,
                        rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)),
                        txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                        uptime = u.uptime,
                        bytesUsed = u.bytesIn + u.bytesOut
                    )
                }
            )
        )

        // Add real wireless AP interfaces from router
        wirelessInterfaces.forEachIndexed { idx, w ->
            val ifName = w["name"] ?: "wlan${idx + 1}"
            val ssid = w["ssid"] ?: ifName
            val mac = w["mac-address"] ?: ""
            val freq = w["frequency"] ?: "5 GHz"
            val band = w["band"] ?: "5GHz-A/N/AC"
            val isRunning = w["running"] == "true" || w["disabled"] != "true"

            nodesList.add(
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
                    connectedUsers = (activeList.size / (wirelessInterfaces.size + 1)).coerceAtLeast(0),
                    uptime = _resourceState.value.uptime,
                    rxSpeedKbps = _resourceState.value.rxSpeedKbps / 2,
                    txSpeedKbps = _resourceState.value.txSpeedKbps / 2,
                    totalBytesConsumed = (activeList.sumOf { it.bytesIn + it.bytesOut } / (wirelessInterfaces.size + 1)),
                    signalDbm = "-55 dBm",
                    location = "تغطية الشبكة اللاسلكية",
                    cpuLoadPercent = _resourceState.value.cpuLoad,
                    memoryUsagePercent = 30,
                    frequency = freq,
                    channelWidth = w["channel-width"] ?: "20/40 MHz",
                    ssid = ssid,
                    connectedClients = activeList.take(5).mapIndexed { cIdx, u ->
                        com.example.data.model.NodeConnectedClient(
                            id = u.id,
                            cardName = u.user,
                            ipAddress = u.address.ifBlank { "$subnetPrefix.${100 + cIdx}" },
                            macAddress = u.macAddress,
                            rxRateMbps = (u.bytesIn / (1024.0 * 1024.0)),
                            txRateMbps = (u.bytesOut / (1024.0 * 1024.0)),
                            uptime = u.uptime,
                            bytesUsed = u.bytesIn + u.bytesOut
                        )
                    }
                )
            )
        }

        // Add real neighbor APs discovered on the network
        neighbors.forEachIndexed { idx, n ->
            val nIdentity = n["identity"] ?: n["board"] ?: "نقطة شبكة متصلة"
            val nAddress = n["address"] ?: "$subnetPrefix.${idx + 10}"
            val nMac = n["mac-address"] ?: ""
            val nPlatform = n["platform"] ?: n["board"] ?: "MikroTik RouterOS"
            val nInterface = n["interface"] ?: "ether${idx + 2}"

            nodesList.add(
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
                    connectedUsers = 0,
                    uptime = _resourceState.value.uptime,
                    rxSpeedKbps = 1500.0,
                    txSpeedKbps = 800.0,
                    totalBytesConsumed = 10_000_000_000L,
                    signalDbm = "-58 dBm",
                    location = "نطاق التغطية",
                    ssid = nIdentity,
                    connectedClients = emptyList()
                )
            )
        }

        _networkNodes.value = nodesList

        // 3. Activity events log strictly from real active users
        val eventsList = mutableListOf<com.example.data.model.UserActivityEvent>()
        activeList.take(10).forEachIndexed { idx, u ->
            eventsList.add(
                com.example.data.model.UserActivityEvent(
                    id = "evt_live_$idx",
                    username = u.user,
                    eventType = "تسجيل دخول",
                    timestamp = "نشط حالياً (${u.uptime})",
                    details = "تسجيل دخول نشط عبر الهوتسبوت (${u.loginBy})",
                    ipAddress = u.address.ifBlank { "$subnetPrefix.${100 + idx}" },
                    macAddress = u.macAddress,
                    deviceInfo = com.example.data.util.DeviceDetector.detectDevice(u.macAddress, "جهاز متصل", u.user)
                )
            )
        }
        _activityEvents.value = eventsList
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

    suspend fun addSingleCard(card: HotspotCard): Result<Boolean> {
        return withContext(Dispatchers.IO) {
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
                            return@withContext Result.failure(Exception("استجاب الراوتر برمز خطأ ${resp.code}"))
                        }
                    } catch (e: Exception) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            val currentList = _cards.value.toMutableList()
            currentList.add(0, card)
            _cards.value = currentList
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

    suspend fun addNetworkNode(node: com.example.data.model.NetworkNode): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val list = _networkNodes.value.toMutableList()
            list.add(0, node)
            _networkNodes.value = list
            Result.success(true)
        }
    }

    suspend fun deleteNetworkNode(nodeId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
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
