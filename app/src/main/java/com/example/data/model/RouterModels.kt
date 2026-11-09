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
        val isDemo: Boolean,
        val isSyncing: Boolean = false,
        val isFromCache: Boolean = false,
        val isOffline: Boolean = false,
        val lastSyncTime: Long = System.currentTimeMillis(),
        val offlineReason: String = ""
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
    val txSpeedKbps: Double = 1820.0,
    val voltage: String = "24.1 V",
    val temperature: String = "42°C",
    val cpuFrequency: String = "1400 MHz",
    val hddFreeBytes: Long = 512 * 1024 * 1024L
)

data class RouterBoardInfo(
    val model: String = "RB4011iGS+",
    val serialNumber: String = "H93A012345",
    val currentFirmware: String = "7.15.2",
    val upgradeFirmware: String = "7.15.2",
    val routerboard: Boolean = true
)

data class HotspotServerInfo(
    val id: String,
    val name: String = "hotspot1",
    val interfaceName: String = "bridge-hotspot",
    val profile: String = "hsprof1",
    val addressPool: String = "hs-pool",
    val addressesPerMac: Int = 1,
    val disabled: Boolean = false
)

data class SystemLogEntry(
    val id: String,
    val time: String,
    val topics: String,
    val message: String
)

data class IpPoolInfo(
    val id: String,
    val name: String,
    val ranges: String
)

data class InterfaceStats(
    val id: String,
    val name: String,
    val type: String,
    val macAddress: String = "",
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxPackets: Long = 0L,
    val txPackets: Long = 0L,
    val rxSpeedKbps: Double = 0.0,
    val txSpeedKbps: Double = 0.0,
    val isRunning: Boolean = true,
    val disabled: Boolean = false,
    val comment: String = ""
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
    val comment: String = "",
    val hostName: String = "",
    val serverName: String = "",
    val nodeName: String = "",
    val deviceModel: String = ""
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
    val price: String = "",
    val posAgent: String = "",
    val cardType: String = "hotspot" // "hotspot" or "usermanager"
) {
    val isUserManagerCard: Boolean
        get() = cardType.contains("usermanager", ignoreCase = true) || id.startsWith("um", ignoreCase = true) || id.startsWith("tum", ignoreCase = true)

    val isHotspotCard: Boolean
        get() = !isUserManagerCard

    val systemLabelAr: String
        get() = if (isUserManagerCard) "يوزرمانجر" else "هوتسبوت"

    val isExpiredQuota: Boolean
        get() {
            if (limitBytesTotal > 0L && (bytesIn + bytesOut) >= limitBytesTotal) return true
            val c = comment.lowercase()
            if (c.contains("traffic limit") || c.contains("quota") || c.contains("رصيد") || c.contains("البيانات") || c.contains("حجم") || c.contains("استنفاد")) return true
            return false
        }

    val isExpiredValidity: Boolean
        get() {
            if (limitUptime.isNotBlank() && uptime.isNotBlank()) {
                val upSec = parseCardUptimeSeconds(uptime)
                val limSec = parseCardUptimeSeconds(limitUptime)
                if (limSec > 0L && upSec >= limSec) return true
                if (uptime.trim() == limitUptime.trim() && uptime.trim() != "0s") return true
            }
            val c = comment.lowercase()
            if (c.contains("uptime limit") || c.contains("time limit") || c.contains("صلاحية") || c.contains("انتهى الوقت") || c.contains("انتهاء الوقت")) return true
            return false
        }

    val isExpired: Boolean
        get() {
            if (isExpiredQuota || isExpiredValidity) return true
            if (disabled && (uptime != "0s" || bytesIn > 0L || bytesOut > 0L)) return true
            if (comment.contains("expired", ignoreCase = true) || comment.contains("منتهي", ignoreCase = true)) return true
            if (disabled) return true
            return false
        }

    val expiryReason: String
        get() = when {
            isExpiredQuota -> "انتهى الرصيد (استنفاد حجم البيانات المحدد)"
            isExpiredValidity -> "انتهت الصلاحية (انقضاء وقت الكرت: ${limitUptime.ifBlank { uptime }})"
            disabled -> "كرت متوقف أو معطل من الإدارة"
            comment.contains("expired", ignoreCase = true) || comment.contains("منتهي", ignoreCase = true) -> "كرت منتهي الصلاحية"
            else -> "صالح ونشط"
        }

    val isActive: Boolean
        get() = !disabled && (bytesIn > 0 || bytesOut > 0 || (uptime.isNotBlank() && uptime != "0s")) && !isExpired

    val isUnused: Boolean
        get() = !disabled && (uptime.isBlank() || uptime == "0s") && bytesIn == 0L && bytesOut == 0L
}

private fun parseCardUptimeSeconds(uptime: String): Long {
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

data class HotspotProfile(
    val id: String,
    val name: String,
    val rateLimit: String = "2M/2M",
    val sharedUsers: Int = 1,
    val sessionTimeout: String = "1d",
    val keepaliveTimeout: String = "5m",
    val price: String = "500 ر.ي",
    val uploadLimit: String = "",
    val downloadLimit: String = "",
    val validity: String = "",
    val dataLimit: String = "",
    val source: String = "hotspot", // "hotspot", "usermanager_v7", or "usermanager_v6"
    val comment: String = "",
    val priceNumber: Double = 0.0,
    val costPrice: Double = 0.0
) {
    val isHotspot: Boolean
        get() = source.equals("hotspot", ignoreCase = true)

    val isUserManager: Boolean
        get() = source.contains("usermanager", ignoreCase = true)

    val systemTitleAr: String
        get() = if (isUserManager) "يوزرمانجر" else "هوتسبوت ميكروتك"
}

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
    val batchComment: String = "دفعة اليوم",
    val posAgentId: Long = 0L,
    val posAgentName: String = "",
    val discountPercent: Double = 0.0,
    val targetSystem: String = "usermanager", // "usermanager" or "hotspot"
    val profileSource: String = "usermanager_v7" // "usermanager_v7", "usermanager_v6", "hotspot"
)

data class BatchCardGenerationResult(
    val totalRequested: Int,
    val successfulCards: List<HotspotCard>,
    val failedCards: List<HotspotCard> = emptyList(),
    val failureReasons: List<String> = emptyList()
) {
    val isCompleteSuccess: Boolean get() = failedCards.isEmpty() && successfulCards.size == totalRequested
    val isPartialSuccess: Boolean get() = successfulCards.isNotEmpty() && failedCards.isNotEmpty()
    val isTotalFailure: Boolean get() = successfulCards.isEmpty()
}

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
    val port: Int = 8728,
    val isReachable: Boolean = true,
    val useHttps: Boolean = false,
    val networkSsid: String = "شبكة Wi-Fi نشطة",
    val statusMessage: String = "تم اكتشاف راوتر ميكروتك تلقائياً",
    val openPorts: List<Int> = emptyList(),
    val serviceType: String = "MikroTik API (8728)"
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

enum class NodeStatus(val labelAr: String) {
    ACTIVE("متصل (نشط)"),
    OFFLINE("مغلق / لا يوجد رد"),
    NO_INTERNET("مقطوع الإنترنت"),
    DISABLED("معطل / غير مؤرشف");

    val titleAr: String get() = labelAr
}

enum class NetworkNodeType(val labelAr: String) {
    MAIN_TOWER("برج رئيسي"),
    DISTRIBUTION("نقطة توزيع"),
    HOME_NODE("نقطة منزلية");

    val titleAr: String get() = labelAr
}

data class NetworkNode(
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String = "64:D1:54:3B:88:12",
    val interfaceName: String = "ether2",
    val model: String = "MikroTik cAP ac",
    val role: String = "أكسس بوينت (بث)", // أكسس بوينت, برج رئيسي, موجه فرعي, سيرفر Starlink, نقطة منزلية
    val nodeType: NetworkNodeType = NetworkNodeType.DISTRIBUTION,
    val status: NodeStatus = NodeStatus.ACTIVE,
    val parentTowerId: String? = null,
    val parentTowerName: String? = null,
    val isOnline: Boolean = (status == NodeStatus.ACTIVE || status == NodeStatus.NO_INTERNET),
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
    val firmwareVersion: String = "",
    val vlanId: Int? = null,
    val parentInterface: String? = null,
    val lastSeenTime: Long = System.currentTimeMillis(),
    val lastCheckTime: Long = System.currentTimeMillis(),
    val connectedClients: List<NodeConnectedClient> = emptyList()
) {
    val effectiveStatus: NodeStatus
        get() = when {
            status == NodeStatus.DISABLED -> NodeStatus.DISABLED
            status == NodeStatus.NO_INTERNET -> NodeStatus.NO_INTERNET
            !isOnline || status == NodeStatus.OFFLINE -> NodeStatus.OFFLINE
            else -> NodeStatus.ACTIVE
        }

    val statusColorRgb: Long
        get() = when (effectiveStatus) {
            NodeStatus.ACTIVE -> 0xFF2E7D32 // أخضر: للنقاط المفعلة والنشطة
            NodeStatus.OFFLINE -> 0xFFC62828 // أحمر: للنقاط المغلقة (لا يوجد رد)
            NodeStatus.NO_INTERNET -> 0xFFEF6C00 // برتقالي/أصفر: لمشاكل الإنترنت
            NodeStatus.DISABLED -> 0xFF757575 // رمادي: غير مؤرشفة أو معطلة
        }

    val statusTitleAr: String
        get() = when (effectiveStatus) {
            NodeStatus.ACTIVE -> "متصل (نشط)"
            NodeStatus.OFFLINE -> "مغلق / لا يوجد رد"
            NodeStatus.NO_INTERNET -> "مقطوع الإنترنت"
            NodeStatus.DISABLED -> "معطل / غير مؤرشف"
        }

    val hasIssue: Boolean
        get() {
            if (effectiveStatus == NodeStatus.OFFLINE || effectiveStatus == NodeStatus.NO_INTERNET) return true
            if (!isOnline) return true
            if (cpuLoadPercent > 70) return true
            if (pingMs > 80) return true
            val tempVal = temperature.removeSuffix("°C").toIntOrNull() ?: 0
            if (tempVal > 50) return true
            return false
        }

    val issueDescription: String
        get() {
            if (effectiveStatus == NodeStatus.OFFLINE) return "النقطة مغلقة تماماً (لا يوجد رد نهائياً من الـ IP)"
            if (effectiveStatus == NodeStatus.NO_INTERNET) return "يوجد اتصال مع الراوتر ولكن مقطوع الإنترنت (Gateway unreachable)"
            if (effectiveStatus == NodeStatus.DISABLED) return "النقطة معطلة يدوياً أو غير مؤرشفة"
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

data class HourlyUsageItem(
    val hourLabel: String,
    val bytesUsed: Long,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val note: String = "",
    val timeAndDateText: String = ""
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
    val comment: String = "",
    val nodeName: String = "",
    val packageLimitBytes: Long = 1024L * 1024L * 1024L, // Default 1GB package limit
    val isQuotaExhausted: Boolean = false,
    val hourlyUsageList: List<HourlyUsageItem> = emptyList()
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

data class AdminUser(
    val id: String,
    val username: String,
    val fullName: String,
    val role: String, // "مدير عام (Full Admin)", "مشرف كروت", "مدير أسعار وإعدادات", "مراقب"
    val permissions: List<String>,
    val lastActiveTime: String,
    val ipAddress: String,
    val isActive: Boolean = true,
    val password: String = "1234"
)

data class AuditLogEntry(
    val id: String,
    val adminUsername: String,
    val actionType: String, // "إضافة كروت", "تعديل أسعار", "تغيير إعدادات", "تسجيل دخول", "طرد مستخدم"
    val description: String,
    val timestamp: String,
    val ipAddress: String,
    val severity: String = "INFO" // INFO, WARNING, CRITICAL
)

data class PackageTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultRateLimit: String,
    val defaultSessionTimeout: String,
    val defaultPrice: String,
    val defaultCostPrice: Double = 0.0,
    val defaultDataQuota: String = "",
    val sharedUsers: Int = 1,
    val badge: String = ""
)

val PREDEFINED_PACKAGE_TEMPLATES = listOf(
    PackageTemplate(
        id = "tpl_1h_express",
        title = "ساعة تيربو (1 Hour Express)",
        subtitle = "سرعة فائقة لتصفح سريع",
        defaultRateLimit = "2M/4M",
        defaultSessionTimeout = "1h",
        defaultPrice = "100 ر.ي",
        defaultCostPrice = 40.0,
        defaultDataQuota = "500MB",
        sharedUsers = 1,
        badge = "سريعة"
    ),
    PackageTemplate(
        id = "tpl_3h_standard",
        title = "3 ساعات قياسية (3 Hours Standard)",
        subtitle = "مناسبة للدراسة وتصفح الوسائط",
        defaultRateLimit = "2M/3M",
        defaultSessionTimeout = "3h",
        defaultPrice = "200 ر.ي",
        defaultCostPrice = 80.0,
        defaultDataQuota = "1024MB",
        sharedUsers = 1,
        badge = "اقتصادية"
    ),
    PackageTemplate(
        id = "tpl_1d_unlimited",
        title = "باقة يومية غير محدودة (1 Day Unlimited)",
        subtitle = "يوم كامل 24 ساعة بدون انقطاع",
        defaultRateLimit = "3M/6M",
        defaultSessionTimeout = "1d",
        defaultPrice = "500 ر.ي",
        defaultCostPrice = 200.0,
        defaultDataQuota = "3072MB",
        sharedUsers = 1,
        badge = "الأكثر طلباً"
    ),
    PackageTemplate(
        id = "tpl_3d_family",
        title = "3 أيام عائلية (3 Days Family)",
        subtitle = "مشاركة لجهازين مع سرعة مستقرة",
        defaultRateLimit = "4M/8M",
        defaultSessionTimeout = "3d",
        defaultPrice = "1200 ر.ي",
        defaultCostPrice = 500.0,
        defaultDataQuota = "8192MB",
        sharedUsers = 2,
        badge = "عائلية"
    ),
    PackageTemplate(
        id = "tpl_7d_gold",
        title = "باقة أسبوعية ذهبية (1 Week Gold)",
        subtitle = "صلاحية 7 أيام كاملة بسرعة مضاعفة",
        defaultRateLimit = "5M/10M",
        defaultSessionTimeout = "7d",
        defaultPrice = "2500 ر.ي",
        defaultCostPrice = 1000.0,
        defaultDataQuota = "16384MB",
        sharedUsers = 2,
        badge = "توفير"
    ),
    PackageTemplate(
        id = "tpl_30d_monthly",
        title = "باقة شهرية فائقة (1 Month Super)",
        subtitle = "اشتراك شهري 30 يوماً للمكاتب والمنازل",
        defaultRateLimit = "8M/16M",
        defaultSessionTimeout = "30d",
        defaultPrice = "6000 ر.ي",
        defaultCostPrice = 2500.0,
        defaultDataQuota = "65536MB",
        sharedUsers = 3,
        badge = "VIP"
    ),
    PackageTemplate(
        id = "tpl_gaming",
        title = "باقة ألعاب بينج منخفض (Gaming Pro)",
        subtitle = "أولوية قصوى لحزم الألعاب والأونلاين",
        defaultRateLimit = "10M/20M",
        defaultSessionTimeout = "1d",
        defaultPrice = "700 ر.ي",
        defaultCostPrice = 300.0,
        defaultDataQuota = "5120MB",
        sharedUsers = 1,
        badge = "Gaming"
    ),
    PackageTemplate(
        id = "tpl_night_owl",
        title = "باقة ليلية مخفضة (Night Owl)",
        subtitle = "صلاحية 12 ساعة من الليل للصباح",
        defaultRateLimit = "4M/8M",
        defaultSessionTimeout = "12h",
        defaultPrice = "300 ر.ي",
        defaultCostPrice = 120.0,
        defaultDataQuota = "4096MB",
        sharedUsers = 1,
        badge = "ليلي"
    )
)

data class BandwidthPoint(
    val timestamp: Long,
    val rxKbps: Double,
    val txKbps: Double
)

data class PeakHourStat(
    val hour: Int,
    val userCount: Int,
    val isPeak: Boolean,
    val percentage: Float
)

data class PackageConsumptionStat(
    val profileName: String,
    val activeUsersCount: Int,
    val totalCardsCount: Int,
    val totalBytes: Long,
    val formattedTraffic: String,
    val percentage: Float,
    val revenueAmount: Double
)

enum class TimeFilterRange(val title: String) {
    ALL("الكل"),
    TODAY("اليوم"),
    WEEK("هذا الأسبوع"),
    MONTH("هذا الشهر")
}

data class PackageMonthlyStat(
    val monthKey: String, // e.g. "2026-11"
    val monthName: String, // e.g. "نوفمبر 2026"
    val isCurrentMonth: Boolean = false,
    val cardsGenerated: Int = 0,
    val cardsSold: Int = 0,
    val cardsUnsold: Int = 0,
    val salePrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val salesPercentage: Float = 0f
)

data class DetailedPackageProfitStat(
    val profileName: String,
    val costPrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val profitPerCard: Double = 0.0, // salePrice - costPrice
    val profitMarginPercent: Double = 0.0, // (profitPerCard / salePrice * 100)
    val totalCardsGenerated: Int = 0,
    val soldCardsCount: Int = 0,
    val activeCardsCount: Int = 0,
    val unusedCardsCount: Int = 0,
    val expiredCardsCount: Int = 0,
    val totalRevenue: Double = 0.0, // soldCardsCount * salePrice
    val totalCost: Double = 0.0, // soldCardsCount * costPrice
    val netProfit: Double = 0.0, // totalRevenue - totalCost
    val profitSharePercent: Float = 0f, // percentage of total network profit
    val salesSharePercent: Float = 0f, // percentage of total cards sold
    val currentMonthSales: PackageMonthlyStat? = null,
    val monthlyHistory: List<PackageMonthlyStat> = emptyList()
)

data class RevenueAnalyticsSummary(
    val totalEstimatedRevenue: Double = 0.0,
    val activeRevenue: Double = 0.0,
    val availableInventoryValue: Double = 0.0,
    val totalCardsCount: Int = 0,
    val activeCardsCount: Int = 0,
    val unusedCardsCount: Int = 0,
    val expiredCardsCount: Int = 0,
    val topSellingPackage: String = "غير محدد",
    val topSellingPackageCount: Int = 0,
    val averageCardPrice: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalNetProfit: Double = 0.0,
    val averageProfitMarginPercent: Double = 0.0,
    val topProfitablePackage: String = "غير محدد",
    val topProfitablePackageProfit: Double = 0.0,
    val selectedTimeFilter: TimeFilterRange = TimeFilterRange.ALL
)

data class MonthlyPackageSales(
    val packageName: String,
    val cardsSold: Int = 0,
    val cardsGenerated: Int = 0,
    val cardsUnsold: Int = 0,
    val salePrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val sharePercentage: Float = 0f
)

data class DailySalesPoint(
    val dayNumber: Int,
    val dayLabel: String,
    val cardsSold: Int = 0,
    val revenue: Double = 0.0,
    val profit: Double = 0.0
)

data class MonthlySalesSummary(
    val monthKey: String, // e.g. "2026-10"
    val monthName: String, // e.g. "أكتوبر 2026"
    val totalCardsGenerated: Int = 0,
    val totalCardsSold: Int = 0,
    val totalCardsUnsold: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val potentialUnsoldValue: Double = 0.0,
    val salesPercentage: Float = 0f, // (sold / total) * 100
    val targetDeficit: Double = 0.0, // target gap or unrealized sales
    val isCurrentMonth: Boolean = false,
    val growthRatePercent: Double = 0.0,
    val packageBreakdown: List<MonthlyPackageSales> = emptyList(),
    val dailyBreakdown: List<DailySalesPoint> = emptyList(),
    val totalBytesConsumed: Long = 0L
)

data class NetworkConsumptionReport(
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val totalSessionsCount: Int = 0,
    val activeSessionsCount: Int = 0,
    val offlineSessionsCount: Int = 0,
    val expiredCardsCount: Int = 0,
    val quotaExhaustedCount: Int = 0,
    val timeExpiredCount: Int = 0,
    val averageBytesPerUser: Long = 0L,
    val topPackagesByConsumption: List<PackageConsumptionSummary> = emptyList(),
    val topDevicesByConsumption: List<DeviceConsumptionSummary> = emptyList(),
    val expiredCardsList: List<ExpiredCardSummary> = emptyList(),
    val selectedTimeFilter: TimeFilterRange = TimeFilterRange.ALL
)

data class PackageConsumptionSummary(
    val profileName: String,
    val totalBytes: Long,
    val sessionsCount: Int,
    val percentageOfTotal: Float
)

data class DeviceConsumptionSummary(
    val deviceBrand: String,
    val deviceModel: String,
    val macAddress: String,
    val username: String,
    val totalBytes: Long
)

data class ExpiredCardSummary(
    val cardId: String,
    val cardName: String,
    val profile: String,
    val price: String,
    val uptime: String,
    val limitUptime: String,
    val totalBytes: Long,
    val limitBytes: Long,
    val expiredReason: String,
    val isQuotaExhausted: Boolean,
    val isTimeExpired: Boolean,
    val lastSeenDate: String = "سجل الأرشيف"
)
