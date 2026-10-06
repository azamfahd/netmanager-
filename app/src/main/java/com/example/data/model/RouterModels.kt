package com.example.data.model

data class RouterConnectionConfig(
    val id: Long = 0,
    val name: String = "راوتر الشبكة",
    val ipAddress: String = "192.168.88.1",
    val port: Int = 80,
    val username: String = "admin",
    val password: String = "",
    val useHttps: Boolean = false,
    val isDemoMode: Boolean = false
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Verifying(val step: String, val progress: Float) : ConnectionState()
    data class Connected(
        val routerName: String,
        val ipAddress: String,
        val isDemo: Boolean
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class RouterResource(
    val uptime: String = "3d 14h 22m",
    val version: String = "7.15.2 (stable)",
    val boardName: String = "MikroTik RB4011iGS+",
    val cpuLoad: Int = 18,
    val freeMemoryBytes: Long = 780 * 1024 * 1024L,
    val totalMemoryBytes: Long = 1024 * 1024 * 1024L,
    val architecture: String = "arm64",
    val rxSpeedKbps: Double = 3450.0,
    val txSpeedKbps: Double = 1820.0
)

data class HotspotActiveUser(
    val id: String,
    val user: String,
    val address: String,
    val macAddress: String,
    val uptime: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val loginBy: String = "http-chap",
    val sessionTimeLeft: String = "",
    val comment: String = ""
)

data class HotspotCard(
    val id: String,
    val name: String,
    val password: String,
    val profile: String = "default",
    val uptime: String = "0s",
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val limitUptime: String = "",
    val limitBytesTotal: Long = 0L,
    val comment: String = "",
    val disabled: Boolean = false,
    val price: String = ""
) {
    val isExpired: Boolean
        get() {
            if (limitBytesTotal > 0 && (bytesIn + bytesOut) >= limitBytesTotal) return true
            return false
        }

    val isActive: Boolean
        get() = !disabled && (bytesIn > 0 || bytesOut > 0 || uptime != "0s")
}

data class HotspotProfile(
    val id: String,
    val name: String,
    val rateLimit: String = "2M/2M",
    val sharedUsers: Int = 1,
    val sessionTimeout: String = "1d",
    val keepaliveTimeout: String = "5m",
    val price: String = "500 ر.ي"
)

data class LocalNetworkInfo(
    val isConnectedToWifi: Boolean = false,
    val wifiSsid: String? = null,
    val deviceIp: String? = null,
    val gatewayIp: String? = null
)

enum class CodeCharType {
    NUMBERS_ONLY,
    LETTERS_AND_NUMBERS,
    LETTERS_ONLY
}

data class BatchGenerationConfig(
    val count: Int = 20,
    val profile: String = "1 Hour - 1000MB",
    val prefix: String = "",
    val codeLength: Int = 6,
    val characterSet: CodeCharType = CodeCharType.NUMBERS_ONLY,
    val isSingleCode: Boolean = true,
    val timeLimit: String = "1d",
    val dataLimitMB: Long = 1024L,
    val price: String = "500 ر.ي",
    val batchComment: String = "دفعة اليوم"
)

data class IpBinding(
    val id: String,
    val macAddress: String,
    val address: String,
    val type: String = "bypassed", // bypassed, regular, blocked
    val comment: String = "",
    val disabled: Boolean = false
)

data class DiscoveredRouter(
    val ip: String = "192.168.88.1",
    val port: Int = 80,
    val isReachable: Boolean = true,
    val useHttps: Boolean = false,
    val networkSsid: String = "شبكة Wi-Fi نشطة",
    val statusMessage: String = "تم اكتشاف راوتر ميكروتك تلقائياً"
)

data class NodeConnectedClient(
    val id: String,
    val cardName: String,
    val ipAddress: String,
    val macAddress: String,
    val signalDbm: String = "-55 dBm",
    val ccqRate: String = "98%",
    val rxRateMbps: Double = 72.0,
    val txRateMbps: Double = 54.0,
    val uptime: String = "45m",
    val bytesUsed: Long = 320 * 1024 * 1024L,
    val deviceInfo: DeviceIdentification = DeviceIdentification()
)

data class NetworkNode(
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String = "64:D1:54:3B:88:12",
    val interfaceName: String = "ether2",
    val model: String = "MikroTik cAP ac",
    val role: String = "أكسس بوينت (بث)", // أكسس بوينت, برج رئيسي, موجه فرعي, سيرفر Starlink
    val isOnline: Boolean = true,
    val pingMs: Long = 4L,
    val connectedUsers: Int = 12,
    val uptime: String = "4d 12h",
    val rxSpeedKbps: Double = 4200.0,
    val txSpeedKbps: Double = 1650.0,
    val totalBytesConsumed: Long = 28_500_000_000L,
    val signalDbm: String = "-58 dBm",
    val location: String = "البرج الرئيسي",
    val cpuLoadPercent: Int = 22,
    val memoryUsagePercent: Int = 35,
    val temperature: String = "43°C",
    val voltage: String = "24.2 V",
    val frequency: String = "5 GHz (5200 MHz)",
    val channelWidth: String = "20/40/80 MHz",
    val ssid: String = "YemenNet_Tower_1",
    val firmwareVersion: String = "RouterOS v7.14.3",
    val lastSeenTime: Long = System.currentTimeMillis(),
    val connectedClients: List<NodeConnectedClient> = emptyList()
) {
    val hasIssue: Boolean
        get() {
            if (!isOnline) return true
            if (cpuLoadPercent > 70) return true
            if (pingMs > 80) return true
            val tempVal = temperature.removeSuffix("°C").toIntOrNull() ?: 0
            if (tempVal > 50) return true
            return false
        }

    val issueDescription: String
        get() {
            if (!isOnline) return "النقطة طافية/غير متصلة بالشبكة"
            if (cpuLoadPercent > 70) return "ضغط عالي على المعالج ($cpuLoadPercent%)"
            if (pingMs > 80) return "بطء استجابة وارتفاع Ping (${pingMs}ms)"
            val tempVal = temperature.removeSuffix("°C").toIntOrNull() ?: 0
            if (tempVal > 50) return "ارتفاع حرارة الجهاز ($temperature)"
            return "النقطة مستقرة وتعمل بانتظام"
        }
}

enum class NodeSortOption(val title: String) {
    FAULTY_FIRST("الأعطال والمشاكل أولاً ⚠️"),
    MOST_USERS("الأكثر مستخدمين 🔥"),
    HIGHEST_CONSUMPTION("الأعلى استهلاكاً 📊"),
    HIGHEST_CPU("الأعلى ضغطاً (CPU) ⚡"),
    LOWEST_PING("الأسرع استجابة (Ping) 📶"),
    ONLINE_FIRST("المتصلة أولاً 🟢"),
    NAME_ASC("الأبجدي (أ - ي) 🔤")
}

enum class DeviceCategory(val title: String) {
    ANDROID_PHONE("هاتف أندرويد"),
    IPHONE_IPAD("آيفون / آيباد"),
    LAPTOP_PC("كمبيوتر / لابتوب"),
    ROUTER_AP("أكسس بوينت / راوتر"),
    SMART_DEVICE("شاشة ذكية / جهاز ذكي")
}

data class DeviceIdentification(
    val brand: String = "Samsung",
    val modelName: String = "Galaxy A54",
    val osName: String = "Android 14",
    val category: DeviceCategory = DeviceCategory.ANDROID_PHONE,
    val macVendor: String = "Samsung Electronics"
)

data class UserSessionRecord(
    val id: String,
    val username: String,
    val ipAddress: String,
    val macAddress: String,
    val isOnline: Boolean = false,
    val loginTime: String = "اليوم 14:30",
    val logoutTime: String = "اليوم 16:15",
    val duration: String = "1h 45m",
    val bytesIn: Long = 250 * 1024 * 1024L,
    val bytesOut: Long = 680 * 1024 * 1024L,
    val totalBytes: Long = (250 + 680) * 1024 * 1024L,
    val terminateReason: String = "تسجيل خروج المستخدم", // جلسة نشطة, انتهاء الوقت, انتهاء الرصيد, فقدان الإشارة
    val profile: String = "500 ر.ي (24 ساعة)",
    val price: String = "500 ر.ي",
    val deviceInfo: DeviceIdentification = DeviceIdentification(),
    val comment: String = ""
)

data class UserActivityEvent(
    val id: String,
    val username: String,
    val eventType: String, // تسجيل دخول, تسجيل خروج, استهلاك باقة, طرد مستخدم
    val timestamp: String,
    val details: String,
    val ipAddress: String,
    val macAddress: String,
    val deviceInfo: DeviceIdentification = DeviceIdentification()
)


