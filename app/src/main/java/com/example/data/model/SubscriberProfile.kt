package com.example.data.model

data class ActivatedPackageInfo(
    val profileName: String,
    val price: String = "",
    val rateLimit: String = "",
    val quotaLimit: String = "",
    val validity: String = "",
    val sharedUsers: Int = 1,
    val source: String = "الراوتر",
    val activationDate: String = "",
    val isCurrent: Boolean = false,
    val totalBytesUsedOnPackage: Long = 0L,
    val sessionsCountOnPackage: Int = 0
)

data class SubscriberFullProfile(
    val username: String,
    val isOnline: Boolean,
    val currentCard: HotspotCard? = null,
    val currentProfile: HotspotProfile? = null,
    val assignedAgentName: String = "",

    // Cumulative Lifetime Usage (إجمالي الاستهلاك لكل الأوقات)
    val lifetimeBytesIn: Long = 0L,
    val lifetimeBytesOut: Long = 0L,
    val lifetimeTotalBytes: Long = 0L,
    val lifetimeUptimeSeconds: Long = 0L,
    val lifetimeUptimeFormatted: String = "0s",
    val totalSessionsCount: Int = 0,
    val firstSeenDate: String = "",
    val lastSeenDate: String = "",

    // Active session comparison
    val activeSessionBytesIn: Long = 0L,
    val activeSessionBytesOut: Long = 0L,
    val activeSessionTotalBytes: Long = 0L,
    val activeSessionDuration: String = "",

    // Quota, Balance & Limits (الرصيد المتبقي والصلاحية)
    val quotaLimitBytes: Long = 0L,
    val quotaRemainingBytes: Long = 0L,
    val quotaUsagePercent: Float = 0f,
    val isQuotaExhausted: Boolean = false,
    val timeLimitFormatted: String = "",
    val timeRemainingFormatted: String = "",
    val isTimeExhausted: Boolean = false,

    // All Activated Packages (كل الباقات والتفعيلات الحالية والسابقة)
    val activatedPackages: List<ActivatedPackageInfo> = emptyList(),

    // Devices & Network Addresses across all sessions
    val usedDevices: List<DeviceIdentification> = emptyList(),
    val usedMacAddresses: List<String> = emptyList(),
    val usedIpAddresses: List<String> = emptyList(),
    val usedNodes: List<String> = emptyList(),

    // Complete Sessions History List
    val sessionHistory: List<UserSessionRecord> = emptyList()
)
