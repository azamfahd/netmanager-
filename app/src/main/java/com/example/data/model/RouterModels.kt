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
