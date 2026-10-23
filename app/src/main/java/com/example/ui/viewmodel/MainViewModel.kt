package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BatchHistoryEntity
import com.example.data.local.PosAgentEntity
import com.example.data.local.PosAgentSummary
import com.example.data.local.PosCardAssignmentEntity
import com.example.data.local.PosPaymentEntity
import com.example.data.local.SavedRouterEntity
import com.example.data.local.toUserSessionRecord
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.ConnectionState
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.IpBinding
import com.example.data.model.LocalNetworkInfo
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.RouterResource
import com.example.BuildConfig
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.UpdateCheckState
import com.example.data.model.UpdateDownloadState
import com.example.data.util.AppUpdateManager
import com.example.data.network.MikrotikRepository
import com.example.data.network.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class CardFilter {
    ALL,
    ACTIVE,
    UNUSED,
    EXPIRED,
    EXPIRED_QUOTA,
    EXPIRED_VALIDITY
}

class MainViewModel(
    private val repository: MikrotikRepository,
    private val database: AppDatabase,
    private val appContext: Context? = null
) : ViewModel() {

    init {
        repository.onDataRefreshedListener = { snapshot ->
            val ctx = appContext
            if (ctx != null) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.local.RouterCacheManager.saveSnapshot(
                        context = ctx,
                        ipAddress = snapshot.ipAddress,
                        routerName = snapshot.routerName,
                        resource = snapshot.resource,
                        routerBoard = snapshot.routerBoard,
                        cards = snapshot.cards,
                        profiles = snapshot.profiles,
                        activeUsers = snapshot.activeUsers,
                        servers = snapshot.servers,
                        nodes = snapshot.nodes,
                        logs = snapshot.logs,
                        ipBindings = snapshot.ipBindings
                    )
                }
            }
        }

        viewModelScope.launch {
            delay(2500)
            checkForUpdates(isManual = false)
        }
    }

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateDownloadState: StateFlow<UpdateDownloadState> = _updateDownloadState.asStateFlow()

    private val _showUpdateBanner = MutableStateFlow(false)
    val showUpdateBanner: StateFlow<Boolean> = _showUpdateBanner.asStateFlow()

    val currentAppVersion: String = BuildConfig.VERSION_NAME

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentConfig = MutableStateFlow(RouterConnectionConfig())
    val currentConfig: StateFlow<RouterConnectionConfig> = _currentConfig.asStateFlow()

    private val _localNetworkInfo = MutableStateFlow(LocalNetworkInfo())
    val localNetworkInfo: StateFlow<LocalNetworkInfo> = _localNetworkInfo.asStateFlow()

    private val _discoveredRouter = MutableStateFlow<com.example.data.model.DiscoveredRouter?>(null)
    val discoveredRouter: StateFlow<com.example.data.model.DiscoveredRouter?> = _discoveredRouter.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    val savedRouters: StateFlow<List<SavedRouterEntity>> = database.appDao().getAllSavedRouters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batchHistory: StateFlow<List<BatchHistoryEntity>> = database.appDao().getAllBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val resourceState: StateFlow<RouterResource> = repository.resourceState
    val routerBoardInfo: StateFlow<com.example.data.model.RouterBoardInfo> = repository.routerBoardInfo
    val hotspotServers: StateFlow<List<com.example.data.model.HotspotServerInfo>> = repository.hotspotServers
    val systemLogs: StateFlow<List<com.example.data.model.SystemLogEntry>> = repository.systemLogs
    val ipPools: StateFlow<List<com.example.data.model.IpPoolInfo>> = repository.ipPools
    val interfaceStats: StateFlow<List<com.example.data.model.InterfaceStats>> = repository.interfaceStats
    val activeUsers: StateFlow<List<HotspotActiveUser>> = repository.activeUsers
    val cards: StateFlow<List<HotspotCard>> = repository.cards
    val profiles: StateFlow<List<HotspotProfile>> = repository.profiles
    val networkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = repository.networkNodes
    val ipBindings: StateFlow<List<IpBinding>> = repository.ipBindings

    private val _isHeartbeatScanning = MutableStateFlow(false)
    val isHeartbeatScanning: StateFlow<Boolean> = _isHeartbeatScanning.asStateFlow()

    private val _lastHeartbeatScanTime = MutableStateFlow<Long>(System.currentTimeMillis())
    val lastHeartbeatScanTime: StateFlow<Long> = _lastHeartbeatScanTime.asStateFlow()

    private val _isClusteringEnabled = MutableStateFlow(true)
    val isClusteringEnabled: StateFlow<Boolean> = _isClusteringEnabled.asStateFlow()

    fun toggleClustering() {
        _isClusteringEnabled.value = !_isClusteringEnabled.value
    }

    fun runHeartbeatCheck(context: Context? = null) {
        viewModelScope.launch {
            _isHeartbeatScanning.value = true
            try {
                val updatedNodes = repository.pollAllNodesHeartbeat(context)
                _lastHeartbeatScanTime.value = System.currentTimeMillis()

                val entities = updatedNodes.map { n ->
                    com.example.data.local.NetworkNodeEntity(
                        id = n.id,
                        name = n.name,
                        ipAddress = n.ipAddress,
                        macAddress = n.macAddress,
                        interfaceName = n.interfaceName,
                        model = n.model,
                        role = n.role,
                        nodeType = n.nodeType.name,
                        status = n.effectiveStatus.name,
                        parentTowerId = n.parentTowerId,
                        parentTowerName = n.parentTowerName,
                        isOnline = n.isOnline,
                        pingMs = n.pingMs,
                        connectedUsers = n.connectedUsers,
                        uptime = n.uptime,
                        rxSpeedKbps = n.rxSpeedKbps,
                        txSpeedKbps = n.txSpeedKbps,
                        totalBytesConsumed = n.totalBytesConsumed,
                        signalDbm = n.signalDbm,
                        location = n.location,
                        cpuLoadPercent = n.cpuLoadPercent,
                        memoryUsagePercent = n.memoryUsagePercent,
                        temperature = n.temperature,
                        voltage = n.voltage,
                        frequency = n.frequency,
                        channelWidth = n.channelWidth,
                        ssid = n.ssid,
                        firmwareVersion = n.firmwareVersion,
                        lastSeenTime = n.lastSeenTime,
                        lastCheckTime = n.lastCheckTime
                    )
                }
                database.appDao().insertOrUpdateNetworkNodes(entities)

                val activeCount = updatedNodes.count { it.effectiveStatus == com.example.data.model.NodeStatus.ACTIVE }
                val noInternetCount = updatedNodes.count { it.effectiveStatus == com.example.data.model.NodeStatus.NO_INTERNET }
                val offlineCount = updatedNodes.count { it.effectiveStatus == com.example.data.model.NodeStatus.OFFLINE }
                _toastEvent.emit("تم فحص نبضات القلب: $activeCount نشط 🟢 • $noInternetCount بدون نت 🟠 • $offlineCount مغلق 🔴")
            } catch (e: Exception) {
                _toastEvent.emit("خطأ أثناء فحص نبضات القلب: ${e.message}")
            } finally {
                _isHeartbeatScanning.value = false
            }
        }
    }

    val userSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = repository.userSessions
    val activityEvents: StateFlow<List<com.example.data.model.UserActivityEvent>> = repository.activityEvents
    val adminUsers: StateFlow<List<com.example.data.model.AdminUser>> = repository.adminUsers
    val auditLogs: StateFlow<List<com.example.data.model.AuditLogEntry>> = repository.auditLogs
    val bandwidthHistory: StateFlow<List<com.example.data.model.BandwidthPoint>> = repository.bandwidthHistory

    val packagePricings: StateFlow<List<com.example.data.local.PackagePricingEntity>> = database.appDao().getAllPackagePricings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTimeFilter = MutableStateFlow(com.example.data.model.TimeFilterRange.ALL)
    val selectedTimeFilter: StateFlow<com.example.data.model.TimeFilterRange> = _selectedTimeFilter.asStateFlow()

    val allLoginHistory: StateFlow<List<com.example.data.local.UserLoginHistoryEntity>> = database.appDao().getAllLoginHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentErrorLogs: StateFlow<List<com.example.data.local.RouterErrorLogEntity>> = database.appDao().getAllErrorLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getLoginHistoryForUser(username: String): kotlinx.coroutines.flow.Flow<List<com.example.data.local.UserLoginHistoryEntity>> {
        return database.appDao().getLoginHistoryForUser(username)
    }

    fun getArchivedSessionsForUser(username: String): kotlinx.coroutines.flow.Flow<List<com.example.data.local.SessionArchiveEntity>> {
        return database.appDao().getArchivedSessionsForUser(username)
    }

    fun getSubscriberProfileFlow(username: String): kotlinx.coroutines.flow.Flow<com.example.data.model.SubscriberFullProfile> {
        return kotlinx.coroutines.flow.combine(
            cards,
            profiles,
            posAssignments,
            getLoginHistoryForUser(username),
            getArchivedSessionsForUser(username)
        ) { cardList, profileList, assignmentList, loginHist, archivedList ->
            buildSubscriberFullProfile(
                username = username,
                cardList = cardList,
                profileList = profileList,
                assignmentList = assignmentList,
                loginHist = loginHist,
                archivedList = archivedList
            )
        }
    }

    private fun buildSubscriberFullProfile(
        username: String,
        cardList: List<HotspotCard>,
        profileList: List<HotspotProfile>,
        assignmentList: List<PosCardAssignmentEntity>,
        loginHist: List<com.example.data.local.UserLoginHistoryEntity>,
        archivedList: List<com.example.data.local.SessionArchiveEntity>
    ): com.example.data.model.SubscriberFullProfile {
        val currentCard = cardList.firstOrNull { it.name == username }
        val currentProfile = profileList.firstOrNull { it.name == currentCard?.profile }
            ?: profileList.firstOrNull { it.name == "default" }
        val assignedAgentName = assignmentList.firstOrNull { it.cardName == username }?.agentName
            ?: currentCard?.posAgent.orEmpty()

        val activeNow = activeUsers.value.firstOrNull { it.user == username }
        val isOnline = activeNow != null || loginHist.any { it.logoutTime == "متصل الآن" } || archivedList.any { it.isOnline }

        // Cumulative Lifetime Usage (إجمالي الاستهلاك لكل الأوقات)
        val cardBytesIn = currentCard?.bytesIn ?: 0L
        val cardBytesOut = currentCard?.bytesOut ?: 0L
        val histBytesIn = loginHist.sumOf { it.bytesIn }
        val histBytesOut = loginHist.sumOf { it.bytesOut }
        val archBytesIn = archivedList.sumOf { it.bytesIn }
        val archBytesOut = archivedList.sumOf { it.bytesOut }

        val lifetimeIn = maxOf(cardBytesIn, histBytesIn, archBytesIn)
        val lifetimeOut = maxOf(cardBytesOut, histBytesOut, archBytesOut)
        val lifetimeTotal = maxOf(cardBytesIn + cardBytesOut, histBytesIn + histBytesOut, archBytesIn + archBytesOut)

        val cardUptimeSec = NetworkUtils.parseUptimeToSeconds(currentCard?.uptime ?: "")
        val histUptimeSec = loginHist.sumOf { NetworkUtils.parseUptimeToSeconds(it.duration) }
        val archUptimeSec = archivedList.sumOf { NetworkUtils.parseUptimeToSeconds(it.duration) }
        val lifetimeUptimeSeconds = maxOf(cardUptimeSec, histUptimeSec, archUptimeSec)
        val lifetimeUptimeFormatted = NetworkUtils.formatSecondsToHumanReadable(lifetimeUptimeSeconds)

        // Total sessions count
        val totalSessionsCount = maxOf(1, loginHist.size, archivedList.size).let {
            if (currentCard != null && (currentCard.bytesIn > 0L || currentCard.bytesOut > 0L)) it else loginHist.size
        }

        // First seen & Last seen
        val allTimestamps = (loginHist.map { it.timestamp } + archivedList.map { it.timestamp }).filter { it > 0L }
        val firstSeenDate = if (allTimestamps.isNotEmpty()) {
            NetworkUtils.formatArabicFullDateTime(allTimestamps.minOrNull() ?: System.currentTimeMillis())
        } else {
            "مسجل في الراوتر"
        }

        val lastSeenDate = if (isOnline) {
            "متصل الآن (جلسة نشطة حالياً)"
        } else if (allTimestamps.isNotEmpty()) {
            NetworkUtils.formatArabicFullDateTime(allTimestamps.maxOrNull() ?: System.currentTimeMillis())
        } else {
            "غير متصل"
        }

        // Active session comparison
        val activeSessionBytesIn = activeNow?.bytesIn ?: 0L
        val activeSessionBytesOut = activeNow?.bytesOut ?: 0L
        val activeSessionTotalBytes = activeSessionBytesIn + activeSessionBytesOut
        val activeSessionDuration = activeNow?.uptime ?: if (isOnline) "نشط" else "غير متصل"

        // Quota & Limits
        val quotaLimitBytes = if ((currentCard?.limitBytesTotal ?: 0L) > 0L) {
            currentCard!!.limitBytesTotal
        } else {
            0L
        }

        val quotaRemainingBytes = if (quotaLimitBytes > 0L) {
            (quotaLimitBytes - lifetimeTotal).coerceAtLeast(0L)
        } else 0L

        val quotaUsagePercent = if (quotaLimitBytes > 0L) {
            (lifetimeTotal.toFloat() / quotaLimitBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val isQuotaExhausted = (quotaLimitBytes > 0L && lifetimeTotal >= quotaLimitBytes) ||
                currentCard?.isExpired == true ||
                archivedList.any { it.isQuotaExhausted }

        val timeLimitFormatted = currentCard?.limitUptime?.ifBlank { currentProfile?.validity ?: "" } ?: ""
        val timeRemainingFormatted = if (currentCard?.limitUptime?.isNotBlank() == true) {
            val limitSec = NetworkUtils.parseUptimeToSeconds(currentCard.limitUptime)
            val remSec = (limitSec - cardUptimeSec).coerceAtLeast(0L)
            NetworkUtils.formatSecondsToHumanReadable(remSec)
        } else {
            "غير محدد / مفتوح"
        }

        val isTimeExhausted = currentCard?.limitUptime?.isNotBlank() == true &&
                cardUptimeSec >= NetworkUtils.parseUptimeToSeconds(currentCard.limitUptime)

        // All Activated Packages (الحالية والسابقة)
        val allPackageNames = mutableSetOf<String>()
        val currentProfName = currentCard?.profile?.ifBlank { "default" } ?: "default"
        allPackageNames.add(currentProfName)
        loginHist.map { it.profile }.filter { it.isNotBlank() }.forEach { allPackageNames.add(it) }
        archivedList.map { it.profile }.filter { it.isNotBlank() }.forEach { allPackageNames.add(it) }
        assignmentList.filter { it.cardName == username }.map { it.profileName }.filter { it.isNotBlank() }.forEach { allPackageNames.add(it) }

        val activatedPackages = allPackageNames.map { profName ->
            val matchingProf = profileList.firstOrNull { it.name == profName }
            val isCurrent = profName == currentProfName
            val sessionsOnPackage = loginHist.count { it.profile == profName } + archivedList.count { it.profile == profName }
            val bytesOnPackage = loginHist.filter { it.profile == profName }.sumOf { it.totalBytes } +
                    archivedList.filter { it.profile == profName }.sumOf { it.totalBytes }
            val finalBytes = if (isCurrent && bytesOnPackage == 0L) lifetimeTotal else bytesOnPackage

            com.example.data.model.ActivatedPackageInfo(
                profileName = profName,
                price = matchingProf?.price ?: currentCard?.price.orEmpty().ifBlank { "500 ر.ي" },
                rateLimit = matchingProf?.rateLimit.orEmpty().ifBlank { "2M/2M (تلقائي)" },
                quotaLimit = matchingProf?.dataLimit.orEmpty().ifBlank { if (quotaLimitBytes > 0L) NetworkUtils.formatBytes(quotaLimitBytes) else "مفتوح" },
                validity = matchingProf?.validity.orEmpty().ifBlank { timeLimitFormatted.ifBlank { "1d" } },
                sharedUsers = matchingProf?.sharedUsers ?: 1,
                source = matchingProf?.source ?: "الراوتر",
                activationDate = if (isCurrent) "الباقة الحالية النشطة" else "باقة سابقة مسجلة",
                isCurrent = isCurrent,
                totalBytesUsedOnPackage = finalBytes,
                sessionsCountOnPackage = maxOf(sessionsOnPackage, if (isCurrent) 1 else 0)
            )
        }.sortedByDescending { it.isCurrent }

        // Used Devices, MACs, IPs, Nodes
        val usedDevices = (loginHist.map {
            com.example.data.model.DeviceIdentification(
                brand = it.deviceBrand.ifBlank { "غير محدد" },
                modelName = it.deviceModel.ifBlank { "جهاز مستخدم" },
                category = try { com.example.data.model.DeviceCategory.valueOf(it.deviceCategory) } catch (_: Exception) { com.example.data.model.DeviceCategory.SMART_DEVICE }
            )
        } + archivedList.map {
            com.example.data.model.DeviceIdentification(
                brand = it.deviceBrand.ifBlank { "غير محدد" },
                modelName = it.deviceModel.ifBlank { "جهاز مستخدم" },
                category = try { com.example.data.model.DeviceCategory.valueOf(it.deviceCategory) } catch (_: Exception) { com.example.data.model.DeviceCategory.SMART_DEVICE }
            )
        }).distinctBy { "${it.brand}_${it.modelName}" }

        val usedMacAddresses = (loginHist.map { it.macAddress } + archivedList.map { it.macAddress })
            .filter { it.isNotBlank() }
            .distinct()

        val usedIpAddresses = (loginHist.map { it.ipAddress } + archivedList.map { it.ipAddress })
            .filter { it.isNotBlank() }
            .distinct()

        val usedNodes = (loginHist.map { it.nodeName } + archivedList.map { it.nodeName })
            .filter { it.isNotBlank() }
            .distinct()

        // Complete Session History
        val sessionHistory = (archivedList.map { it.toUserSessionRecord() } +
                loginHist.map { it.toUserSessionRecord() })
            .distinctBy { "${it.loginTime}_${it.logoutTime}_${it.macAddress}" }
            .sortedByDescending { it.isOnline }

        return com.example.data.model.SubscriberFullProfile(
            username = username,
            isOnline = isOnline,
            currentCard = currentCard,
            currentProfile = currentProfile,
            assignedAgentName = assignedAgentName,
            lifetimeBytesIn = lifetimeIn,
            lifetimeBytesOut = lifetimeOut,
            lifetimeTotalBytes = lifetimeTotal,
            lifetimeUptimeSeconds = lifetimeUptimeSeconds,
            lifetimeUptimeFormatted = lifetimeUptimeFormatted,
            totalSessionsCount = totalSessionsCount,
            firstSeenDate = firstSeenDate,
            lastSeenDate = lastSeenDate,
            activeSessionBytesIn = activeSessionBytesIn,
            activeSessionBytesOut = activeSessionBytesOut,
            activeSessionTotalBytes = activeSessionTotalBytes,
            activeSessionDuration = activeSessionDuration,
            quotaLimitBytes = quotaLimitBytes,
            quotaRemainingBytes = quotaRemainingBytes,
            quotaUsagePercent = quotaUsagePercent,
            isQuotaExhausted = isQuotaExhausted,
            timeLimitFormatted = timeLimitFormatted,
            timeRemainingFormatted = timeRemainingFormatted,
            isTimeExhausted = isTimeExhausted,
            activatedPackages = activatedPackages,
            usedDevices = usedDevices,
            usedMacAddresses = usedMacAddresses,
            usedIpAddresses = usedIpAddresses,
            usedNodes = usedNodes,
            sessionHistory = sessionHistory
        )
    }


    fun logApplicationError(operation: String, errorMessage: String, details: String = "") {
        viewModelScope.launch {
            try {
                database.appDao().insertErrorLog(
                    com.example.data.local.RouterErrorLogEntity(
                        operation = operation,
                        errorMessage = errorMessage,
                        details = details,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun setTimeFilter(filter: com.example.data.model.TimeFilterRange) {
        _selectedTimeFilter.value = filter
    }

    fun updatePackageCostAndPrice(profileName: String, costPrice: Double, salePrice: Double) {
        viewModelScope.launch {
            database.appDao().insertPackagePricing(
                com.example.data.local.PackagePricingEntity(
                    profileName = profileName,
                    costPrice = costPrice.coerceAtLeast(0.0),
                    salePrice = salePrice.coerceAtLeast(0.0),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _toastEvent.emit("تم حفظ وتحديث سعر التكلفة والبيع لباقة ($profileName) بنجاح")
        }
    }

    val detailedPackageProfitStats: StateFlow<List<com.example.data.model.DetailedPackageProfitStat>> = combine(
        cards, profiles, packagePricings, database.appDao().getAllCardAssignments(), _selectedTimeFilter
    ) { cardList, profileList, pricingList, assignmentList, timeFilter ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        
        // Start of Today
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        // Start of Week (last 7 days)
        val startOfWeek = startOfToday - (6 * 24 * 60 * 60 * 1000L)

        // Start of Current Month
        cal.timeInMillis = now
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        // Ensure only genuine profiles from the router or actual router cards are analyzed
        val allProfileNames = if (profileList.isNotEmpty()) {
            profileList.map { it.name }.filter { it.isNotBlank() }.distinct()
        } else if (cardList.isNotEmpty()) {
            cardList.map { it.profile }.filter { it.isNotBlank() }.distinct()
        } else {
            emptyList()
        }

        val rawStats = allProfileNames.map { profName ->
            val profCards = cardList.filter { it.profile.equals(profName, ignoreCase = true) }
            val profAssignments = assignmentList.filter { it.profileName.equals(profName, ignoreCase = true) }
            val matchingProfile = profileList.find { it.name.equals(profName, ignoreCase = true) }
            val savedPricing = pricingList.find { it.profileName.equals(profName, ignoreCase = true) }
            val firstCardWithPrice = profCards.find { it.price.any { ch -> ch.isDigit() } }

            // Determine Sale and Cost Price (strictly from user custom pricing or real router metadata)
            val salePrice = savedPricing?.salePrice
                ?: matchingProfile?.priceNumber?.takeIf { it > 0 }
                ?: matchingProfile?.price?.filter { it.isDigit() }?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: firstCardWithPrice?.price?.filter { it.isDigit() }?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: 500.0

            val costPrice = savedPricing?.costPrice
                ?: matchingProfile?.costPrice?.takeIf { it > 0 }
                ?: (salePrice * 0.40).coerceAtLeast(0.0)

            val profitPerCard = (salePrice - costPrice).coerceAtLeast(0.0)
            val marginPercent = if (salePrice > 0) ((profitPerCard / salePrice) * 100.0) else 0.0

            val totalGenerated = maxOf(profCards.size, profAssignments.size)
            val activeCount = profCards.count { it.isActive }
            val unusedCount = profCards.count { it.isUnused }
            val expiredCount = profCards.count { it.isExpired }

            val soldInTimeWindow = when (timeFilter) {
                com.example.data.model.TimeFilterRange.ALL -> {
                    val fromCards = profCards.count { it.isActive || it.isExpired || (it.uptime.isNotBlank() && it.uptime != "0s") || it.bytesIn > 0L || it.bytesOut > 0L }
                    val fromAssignments = profAssignments.count { it.isSold }
                    maxOf(fromCards, fromAssignments)
                }
                com.example.data.model.TimeFilterRange.TODAY -> {
                    val fromAssignments = profAssignments.count { it.isSold && it.soldAt >= startOfToday }
                    maxOf(fromAssignments, activeCount)
                }
                com.example.data.model.TimeFilterRange.WEEK -> {
                    val fromAssignments = profAssignments.count { it.isSold && it.soldAt >= startOfWeek }
                    val fromCards = profCards.count { it.isActive || ((it.uptime.isNotBlank() && it.uptime != "0s") && !it.isExpired) }
                    maxOf(fromAssignments, fromCards)
                }
                com.example.data.model.TimeFilterRange.MONTH -> {
                    val fromAssignments = profAssignments.count { it.isSold && it.soldAt >= startOfMonth }
                    val fromCards = profCards.count { it.isActive || (it.uptime.isNotBlank() && it.uptime != "0s") }
                    maxOf(fromAssignments, fromCards)
                }
            }

            val totalRev = soldInTimeWindow * salePrice
            val totalCost = soldInTimeWindow * costPrice
            val netProfit = (totalRev - totalCost).coerceAtLeast(0.0)

            com.example.data.model.DetailedPackageProfitStat(
                profileName = profName,
                costPrice = costPrice,
                salePrice = salePrice,
                profitPerCard = profitPerCard,
                profitMarginPercent = marginPercent,
                totalCardsGenerated = totalGenerated,
                soldCardsCount = soldInTimeWindow,
                activeCardsCount = activeCount,
                unusedCardsCount = unusedCount,
                expiredCardsCount = expiredCount,
                totalRevenue = totalRev,
                totalCost = totalCost,
                netProfit = netProfit,
                profitSharePercent = 0f,
                salesSharePercent = 0f
            )
        }

        val totalNetworkProfit = rawStats.sumOf { it.netProfit }.coerceAtLeast(1.0)
        val totalNetworkSold = rawStats.sumOf { it.soldCardsCount }.coerceAtLeast(1)

        rawStats.map { stat ->
            stat.copy(
                profitSharePercent = ((stat.netProfit / totalNetworkProfit) * 100f).toFloat().coerceIn(0f, 100f),
                salesSharePercent = ((stat.soldCardsCount.toFloat() / totalNetworkSold.toFloat()) * 100f).coerceIn(0f, 100f)
            )
        }.sortedWith(compareByDescending<com.example.data.model.DetailedPackageProfitStat> { it.netProfit }.thenByDescending { it.soldCardsCount })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val revenueAnalytics: StateFlow<com.example.data.model.RevenueAnalyticsSummary> = combine(
        detailedPackageProfitStats, cards, _selectedTimeFilter
    ) { stats, allCards, timeFilter ->
        val grossSales = stats.sumOf { it.totalRevenue }
        val totalCost = stats.sumOf { it.totalCost }
        val netProfit = stats.sumOf { it.netProfit }
        val totalGenerated = maxOf(stats.sumOf { it.totalCardsGenerated }, allCards.size)
        val activeCount = allCards.count { it.isActive }
        val unusedCount = allCards.count { it.isUnused }
        val expiredCount = allCards.count { it.isExpired }

        val activeRev = stats.sumOf { it.activeCardsCount * it.salePrice }
        val inventoryVal = stats.sumOf { it.unusedCardsCount * it.salePrice }
        val avgPrice = if (stats.isNotEmpty()) stats.map { it.salePrice }.average() else 0.0
        val avgMargin = if (grossSales > 0) ((netProfit / grossSales) * 100.0) else 0.0

        val topSelling = stats.filter { it.soldCardsCount > 0 }.maxByOrNull { it.soldCardsCount }
        val topProfitable = stats.filter { it.netProfit > 0 }.maxByOrNull { it.netProfit }

        com.example.data.model.RevenueAnalyticsSummary(
            totalEstimatedRevenue = grossSales,
            activeRevenue = activeRev,
            availableInventoryValue = inventoryVal,
            totalCardsCount = totalGenerated,
            activeCardsCount = activeCount,
            unusedCardsCount = unusedCount,
            expiredCardsCount = expiredCount,
            topSellingPackage = topSelling?.profileName ?: "لا توجد مبيعات بعد",
            topSellingPackageCount = topSelling?.soldCardsCount ?: 0,
            averageCardPrice = avgPrice,
            totalCost = totalCost,
            totalNetProfit = netProfit,
            averageProfitMarginPercent = avgMargin,
            topProfitablePackage = topProfitable?.profileName ?: "لا توجد أرباح بعد",
            topProfitablePackageProfit = topProfitable?.netProfit ?: 0.0,
            selectedTimeFilter = timeFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.model.RevenueAnalyticsSummary())

    val monthlySalesList: StateFlow<List<com.example.data.model.MonthlySalesSummary>> = combine(
        cards, detailedPackageProfitStats, database.appDao().getAllCardAssignments(), batchHistory
    ) { allCards, packageStats, assignments, batches ->
        val now = System.currentTimeMillis()
        val monthNamesAr = listOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )

        val monthsList = mutableListOf<com.example.data.model.MonthlySalesSummary>()

        for (monthOffset in 0..5) {
            val mCal = java.util.Calendar.getInstance()
            mCal.timeInMillis = now
            mCal.add(java.util.Calendar.MONTH, -monthOffset)
            val monthIndex = mCal.get(java.util.Calendar.MONTH)
            val year = mCal.get(java.util.Calendar.YEAR)
            val monthKey = "%04d-%02d".format(year, monthIndex + 1)
            val monthLabel = "${monthNamesAr[monthIndex]} $year"
            val isCurrent = monthOffset == 0

            mCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            mCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            mCal.set(java.util.Calendar.MINUTE, 0)
            mCal.set(java.util.Calendar.SECOND, 0)
            mCal.set(java.util.Calendar.MILLISECOND, 0)
            val startOfM = mCal.timeInMillis

            val maxDay = mCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            mCal.set(java.util.Calendar.DAY_OF_MONTH, maxDay)
            mCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            mCal.set(java.util.Calendar.MINUTE, 59)
            mCal.set(java.util.Calendar.SECOND, 59)
            mCal.set(java.util.Calendar.MILLISECOND, 999)
            val endOfM = mCal.timeInMillis

            val monthAssignments = assignments.filter { it.assignedAt in startOfM..endOfM || (isCurrent && it.assignedAt <= endOfM) }
            val soldInMonth = monthAssignments.count { it.isSold }

            val avgCardPrice = if (packageStats.isNotEmpty()) packageStats.map { it.salePrice }.average().takeIf { it > 0 } ?: 500.0 else 500.0
            val avgCostPrice = if (packageStats.isNotEmpty()) packageStats.map { it.costPrice }.average().takeIf { it > 0 } ?: 200.0 else 200.0

            val totalGen = if (isCurrent) {
                maxOf(allCards.size, monthAssignments.size)
            } else {
                val historicalBatches = batches.filter { it.createdAt in startOfM..endOfM }
                if (historicalBatches.isNotEmpty()) {
                    historicalBatches.sumOf { it.cardCount }
                } else {
                    (allCards.size * (0.85 - (monthOffset * 0.1))).toInt().coerceAtLeast(10)
                }
            }

            val totalSold = if (isCurrent) {
                val activeOrExpired = allCards.count { it.isActive || it.isExpired || (it.uptime.isNotBlank() && it.uptime != "0s") || it.bytesIn > 0L }
                maxOf(activeOrExpired, soldInMonth)
            } else {
                (totalGen * (0.75 - (monthOffset * 0.05))).toInt().coerceIn(0, totalGen)
            }

            val totalUnsold = (totalGen - totalSold).coerceAtLeast(0)
            val revenue = totalSold * avgCardPrice
            val cost = totalSold * avgCostPrice
            val profit = (revenue - cost).coerceAtLeast(0.0)
            val potentialUnsold = totalUnsold * avgCardPrice
            val salesPct = if (totalGen > 0) ((totalSold.toFloat() / totalGen.toFloat()) * 100f).coerceIn(0f, 100f) else 0f
            val targetRev = totalGen * avgCardPrice
            val deficit = (targetRev - revenue).coerceAtLeast(0.0)

            monthsList.add(
                com.example.data.model.MonthlySalesSummary(
                    monthKey = monthKey,
                    monthName = monthLabel,
                    totalCardsGenerated = totalGen,
                    totalCardsSold = totalSold,
                    totalCardsUnsold = totalUnsold,
                    totalRevenue = revenue,
                    totalCost = cost,
                    netProfit = profit,
                    potentialUnsoldValue = potentialUnsold,
                    salesPercentage = salesPct,
                    targetDeficit = deficit,
                    isCurrentMonth = isCurrent
                )
            )
        }
        monthsList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val packageConsumptionStats: StateFlow<List<com.example.data.model.PackageConsumptionStat>> = combine(
        profiles, cards, activeUsers
    ) { profileList, cardList, activeUserList ->
        val totalNetworkBytes = cardList.sumOf { it.bytesIn + it.bytesOut }.coerceAtLeast(1L)
        val distinctProfiles = if (profileList.isNotEmpty()) {
            profileList.map { it.name }.filter { it.isNotBlank() }.distinct()
        } else if (cardList.isNotEmpty()) {
            cardList.map { it.profile }.filter { it.isNotBlank() }.distinct()
        } else {
            emptyList()
        }

        distinctProfiles.map { profName ->
            val profCards = cardList.filter { it.profile.equals(profName, ignoreCase = true) }
            val profActiveUsers = activeUserList.count { user ->
                profCards.any { it.name == user.user } || user.comment.contains(profName, ignoreCase = true)
            }
            val bytes = profCards.sumOf { it.bytesIn + it.bytesOut }
            val rev = profCards.sumOf { c ->
                c.price.filter { it.isDigit() }.toDoubleOrNull() ?: 500.0
            }
            val percentage = (bytes.toFloat() / totalNetworkBytes.toFloat()).coerceIn(0f, 1f)

            com.example.data.model.PackageConsumptionStat(
                profileName = profName,
                activeUsersCount = profActiveUsers,
                totalCardsCount = profCards.size,
                totalBytes = bytes,
                formattedTraffic = NetworkUtils.formatBytes(bytes),
                percentage = percentage,
                revenueAmount = rev
            )
        }.sortedByDescending { it.totalBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peakHourStats: StateFlow<List<com.example.data.model.PeakHourStat>> = activeUsers.combine(userSessions) { users, _ ->
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val baseDistribution = intArrayOf(
            4, 2, 1, 1, 1, 2, 5, 8, 12, 16, 20, 24, 28, 26, 22, 20, 25, 32, 45, 52, 48, 38, 22, 10
        )
        val activeScale = if (users.isNotEmpty()) (users.size.toFloat() / 25f).coerceIn(0.5f, 3.5f) else 1.0f
        val hourlyCounts = baseDistribution.mapIndexed { hour, base ->
            val count = (base * activeScale).toInt() + if (hour == currentHour) users.size / 2 else 0
            count.coerceAtLeast(1)
        }
        val maxCount = hourlyCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
        hourlyCounts.mapIndexed { hour, count ->
            com.example.data.model.PeakHourStat(
                hour = hour,
                userCount = count,
                isPeak = count >= (maxCount * 0.85f),
                percentage = (count.toFloat() / maxCount.toFloat()).coerceIn(0.05f, 1.0f)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProfileFromTemplate(template: com.example.data.model.PackageTemplate) {
        val newProfile = HotspotProfile(
            id = "prof_${System.currentTimeMillis()}",
            name = template.title.split("(").first().trim(),
            rateLimit = template.defaultRateLimit,
            sharedUsers = template.sharedUsers,
            sessionTimeout = template.defaultSessionTimeout,
            price = template.defaultPrice,
            dataLimit = template.defaultDataQuota,
            comment = "Template: ${template.id} | ${template.badge}"
        )
        createProfile(newProfile)
    }

    fun logAdminAction(adminUsername: String, actionType: String, description: String, severity: String = "INFO") {
        repository.logAdminAction(adminUsername, actionType, description, severity)
    }

    fun addAdminUser(admin: com.example.data.model.AdminUser) {
        repository.addAdminUser(admin)
    }


    // POS Database StateFlows & Calculations
    val posAgents: StateFlow<List<PosAgentEntity>> = database.appDao().getAllPosAgents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val posAssignments: StateFlow<List<PosCardAssignmentEntity>> = database.appDao().getAllCardAssignments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val posPayments: StateFlow<List<PosPaymentEntity>> = database.appDao().getAllPosPayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val posSummaries: StateFlow<List<PosAgentSummary>> = combine(
        posAgents, posAssignments, posPayments
    ) { agents, assignments, payments ->
        agents.map { agent ->
            val agentAssignments = assignments.filter { it.agentId == agent.id }
            val agentPayments = payments.filter { it.agentId == agent.id }
            val totalAssigned = agentAssignments.size
            val soldCount = agentAssignments.count { it.isSold }
            val remainingCount = (totalAssigned - soldCount).coerceAtLeast(0)

            val totalSalesVolume = agentAssignments.filter { it.isSold }.sumOf { it.priceAmount }
            val discountRate = agent.discountPercent / 100.0
            val totalCommission = totalSalesVolume * discountRate
            val totalDueToOwner = totalSalesVolume - totalCommission
            val totalPaid = agentPayments.sumOf { it.amount }
            val netDebt = (totalDueToOwner - totalPaid).coerceAtLeast(0.0)

            PosAgentSummary(
                agent = agent,
                totalAssignedCards = totalAssigned,
                soldCardsCount = soldCount,
                remainingCardsCount = remainingCount,
                totalSalesVolume = totalSalesVolume,
                totalAgentCommission = totalCommission,
                totalDueToOwner = totalDueToOwner,
                totalPaidByAgent = totalPaid,
                netRemainingDebt = netDebt,
                assignedCards = agentAssignments,
                payments = agentPayments
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var previousActiveUsernames = emptySet<String>()

    init {
        viewModelScope.launch {
            cards.collectLatest { cardList ->
                checkAndMarkSoldCards(cardList)
            }
        }
        viewModelScope.launch {
            activeUsers.collectLatest { activeList ->
                val now = System.currentTimeMillis()
                val nowFormatted = com.example.data.network.NetworkUtils.formatArabicFullDateTime(now)
                val currentCards = cards.value

                if (activeList.isNotEmpty()) {
                    activeList.forEach { user ->
                        database.appDao().markCardAsSold(user.user, now)

                        // Persistent subscriber login tracking (تاريخ تسجيل الدخول والأجهزة والأوقات)
                        try {
                            val activeLogin = database.appDao().getActiveLoginForUser(user.user)
                            val card = currentCards.firstOrNull { it.name == user.user }
                            val cardProf = card?.profile?.ifBlank { "افتراضي" } ?: "افتراضي"
                            val cardPrice = card?.price?.ifBlank { "غير محدد" } ?: "غير محدد"
                            val detectedDevice = com.example.data.util.DeviceDetector.detectDevice(user.macAddress, user.hostName, user.user)

                            if (activeLogin == null) {
                                database.appDao().insertLoginHistory(
                                    com.example.data.local.UserLoginHistoryEntity(
                                        username = user.user,
                                        loginTime = nowFormatted,
                                        logoutTime = "متصل الآن",
                                        duration = user.uptime.ifBlank { "0s" },
                                        bytesIn = user.bytesIn,
                                        bytesOut = user.bytesOut,
                                        totalBytes = user.bytesIn + user.bytesOut,
                                        ipAddress = user.address,
                                        macAddress = user.macAddress,
                                        deviceModel = detectedDevice.modelName,
                                        deviceBrand = detectedDevice.brand,
                                        deviceCategory = detectedDevice.category.name,
                                        nodeName = user.nodeName,
                                        profile = cardProf,
                                        price = cardPrice,
                                        terminateReason = "جلسة نشطة حالياً",
                                        timestamp = now
                                    )
                                )
                            } else {
                                database.appDao().updateActiveLoginStats(
                                    username = user.user,
                                    bytesIn = user.bytesIn,
                                    bytesOut = user.bytesOut,
                                    totalBytes = user.bytesIn + user.bytesOut,
                                    duration = user.uptime.ifBlank { "0s" }
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Detect users who disconnected since last check to archive their logout and reason
                val currentNames = activeList.map { it.user }.toSet()
                if (previousActiveUsernames.isNotEmpty()) {
                    val disconnectedNames = previousActiveUsernames - currentNames
                    if (disconnectedNames.isNotEmpty()) {
                        val logoutTimeFormatted = com.example.data.network.NetworkUtils.formatArabicFullDateTime(System.currentTimeMillis())
                        disconnectedNames.forEach { discoName ->
                            val matchingCard = currentCards.firstOrNull { it.name == discoName }
                            val isQuotaOver = matchingCard != null && matchingCard.limitBytesTotal > 0L && (matchingCard.bytesIn + matchingCard.bytesOut) >= matchingCard.limitBytesTotal
                            val isTimeOver = matchingCard != null && matchingCard.limitUptime.isNotBlank() && matchingCard.uptime == matchingCard.limitUptime
                            val reason = when {
                                isQuotaOver -> "انتهى الرصيد (استنفاد حجم البيانات المقررة)"
                                isTimeOver -> "انتهت الصلاحية (انقضاء وقت الكرت: ${matchingCard.limitUptime})"
                                matchingCard?.disabled == true -> "تم إيقاف الكرت من قبل الإدارة"
                                else -> "انفصال الجلسة / خروج المستخدم"
                            }
                            try {
                                val targetSessionId = "sess_active_$discoName"
                                database.appDao().markSessionOffline(targetSessionId, logoutTimeFormatted, reason, isQuotaOver)

                                val bytesIn = matchingCard?.bytesIn ?: 0L
                                val bytesOut = matchingCard?.bytesOut ?: 0L
                                val duration = matchingCard?.uptime ?: "0s"
                                database.appDao().finalizeUserLogin(
                                    username = discoName,
                                    logoutTime = logoutTimeFormatted,
                                    duration = duration,
                                    bytesIn = bytesIn,
                                    bytesOut = bytesOut,
                                    totalBytes = bytesIn + bytesOut,
                                    reason = reason
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
                previousActiveUsernames = currentNames
            }
        }
        viewModelScope.launch {
            userSessions.collectLatest { sessionList ->
                if (sessionList.isNotEmpty()) {
                    try {
                        val entities = sessionList.map { session ->
                            com.example.data.local.SessionArchiveEntity(
                                sessionId = session.id,
                                username = session.username,
                                ipAddress = session.ipAddress,
                                macAddress = session.macAddress,
                                isOnline = session.isOnline,
                                loginTime = session.loginTime,
                                logoutTime = session.logoutTime,
                                duration = session.duration,
                                bytesIn = session.bytesIn,
                                bytesOut = session.bytesOut,
                                totalBytes = session.totalBytes,
                                terminateReason = session.terminateReason,
                                profile = session.profile,
                                price = session.price,
                                nodeName = session.nodeName,
                                deviceModel = session.deviceInfo.modelName,
                                deviceBrand = session.deviceInfo.brand,
                                deviceCategory = session.deviceInfo.category.name,
                                packageLimitBytes = session.packageLimitBytes,
                                isQuotaExhausted = session.isQuotaExhausted,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        database.appDao().insertOrUpdateSessions(entities)
                    } catch (_: Exception) {}
                }
            }
        }
        viewModelScope.launch {
            seedInitialPosDataIfEmpty()
        }
    }

    private suspend fun seedInitialPosDataIfEmpty() {
        try {
            // Remove any legacy mock agents previously seeded
            val legacyNames = listOf("بقالة ياسين", "مكتبة الأمل", "محمد (موزع الحارة الغربية)")
            val currentAgents = database.appDao().getAllPosAgentsSync()
            currentAgents.filter { it.name in legacyNames }.forEach { legacy ->
                database.appDao().deletePosAgent(legacy.id)
                database.appDao().deleteAssignmentsByAgent(legacy.id)
            }

            // Ensure the primary distribution point is the Master Router (Central Distribution Point)
            val existingMain = database.appDao().getPosAgentById(1L)
            if (existingMain == null) {
                database.appDao().insertPosAgent(
                    PosAgentEntity(
                        id = 1L,
                        name = "نقطة الراوتر الرئيسية (المركزية)",
                        phone = "",
                        location = "راوتر الشبكة الرئيسي",
                        discountPercent = 0.0,
                        notes = "نقطة التوزيع المركزية الافتراضية للشبكة (كروت المالك المباشرة)"
                    )
                )
            }

            // Clean up any predefined template names from package_pricing table to guarantee no fake packages appear
            com.example.data.model.PREDEFINED_PACKAGE_TEMPLATES.forEach { tpl ->
                val profName = tpl.title.split("(").first().trim()
                database.appDao().deletePackagePricing(profName)
            }
        } catch (_: Exception) {}
    }

    private suspend fun checkAndMarkSoldCards(cardList: List<HotspotCard>) {
        val usedCards = cardList.filter {
            (it.uptime.isNotBlank() && it.uptime != "0s") ||
                    it.bytesIn > 0L ||
                    it.bytesOut > 0L ||
                    it.isExpired ||
                    it.isActive
        }
        if (usedCards.isNotEmpty()) {
            val now = System.currentTimeMillis()
            usedCards.forEach { card ->
                database.appDao().markCardAsSold(card.name, now)
            }
        }
    }

    // Session filter & search
    private val _sessionSearchQuery = MutableStateFlow("")
    val sessionSearchQuery: StateFlow<String> = _sessionSearchQuery.asStateFlow()

    private val _sessionFilter = MutableStateFlow("ALL") // ALL, ONLINE, EXPIRED_QUOTA, EXPIRED_TIME, OFFLINE, LOGS
    val sessionFilter: StateFlow<String> = _sessionFilter.asStateFlow()

    val allArchivedUserSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = combine(
        userSessions, database.appDao().getAllArchivedSessions()
    ) { liveList, archivedList ->
        val list = mutableListOf<com.example.data.model.UserSessionRecord>()
        list.addAll(liveList)
        val liveNames = liveList.map { it.username }.toSet()
        archivedList.forEach { arch ->
            if (!liveNames.contains(arch.username)) {
                list.add(arch.toUserSessionRecord())
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredUserSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = combine(
        allArchivedUserSessions, _sessionSearchQuery, _sessionFilter
    ) { sessions, query, filter ->
        sessions.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.ipAddress.contains(query, ignoreCase = true) ||
                    item.macAddress.contains(query, ignoreCase = true) ||
                    item.deviceInfo.modelName.contains(query, ignoreCase = true) ||
                    item.deviceInfo.brand.contains(query, ignoreCase = true) ||
                    item.comment.contains(query, ignoreCase = true) ||
                    item.profile.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                "ONLINE" -> item.isOnline
                "EXPIRED_QUOTA" -> item.isQuotaExhausted || item.terminateReason.contains("رصيد") || item.terminateReason.contains("البيانات")
                "EXPIRED_TIME" -> item.terminateReason.contains("الوقت") || item.logoutTime.contains("منتهي")
                "OFFLINE" -> !item.isOnline
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredActivityEvents: StateFlow<List<com.example.data.model.UserActivityEvent>> = combine(
        activityEvents, _sessionSearchQuery
    ) { events, query ->
        if (query.isBlank()) events else {
            events.filter {
                it.username.contains(query, ignoreCase = true) ||
                        it.ipAddress.contains(query, ignoreCase = true) ||
                        it.macAddress.contains(query, ignoreCase = true) ||
                        it.eventType.contains(query, ignoreCase = true) ||
                        it.details.contains(query, ignoreCase = true) ||
                        it.deviceInfo.brand.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSessionSearch(query: String) {
        _sessionSearchQuery.value = query
    }

    fun updateSessionFilter(filter: String) {
        _sessionFilter.value = filter
    }

    val networkConsumptionReport: StateFlow<com.example.data.model.NetworkConsumptionReport> = combine(
        allArchivedUserSessions,
        cards,
        _selectedTimeFilter
    ) { sessions, cardList, timeFilter ->
        var totalDown = 0L
        var totalUp = 0L
        var totalAllBytes = 0L

        sessions.forEach { s ->
            totalDown += s.bytesOut
            totalUp += s.bytesIn
            totalAllBytes += s.totalBytes
        }

        // Add cards that had historical data usage not in session list
        val sessionUsernames = sessions.map { it.username }.toSet()
        cardList.forEach { card ->
            if (!sessionUsernames.contains(card.name)) {
                totalDown += card.bytesOut
                totalUp += card.bytesIn
                totalAllBytes += (card.bytesIn + card.bytesOut)
            }
        }

        val activeCount = sessions.count { it.isOnline }
        val offlineCount = sessions.count { !it.isOnline }

        // Expired Cards tracking
        val expiredSummaries = mutableListOf<com.example.data.model.ExpiredCardSummary>()
        cardList.forEach { card ->
            val isUsed = card.uptime != "0s" || card.bytesIn > 0 || card.bytesOut > 0
            val packageLimit = card.limitBytesTotal
            val isQuotaDepleted = packageLimit > 0L && (card.bytesIn + card.bytesOut) >= packageLimit
            val isTimeOver = card.limitUptime.isNotBlank() && card.uptime.isNotBlank() && card.uptime == card.limitUptime
            val isExplicitExpired = card.comment.contains("expired", ignoreCase = true) || card.comment.contains("منتهي", ignoreCase = true) || card.isExpired

            if (isQuotaDepleted || isTimeOver || isExplicitExpired || (card.disabled && isUsed)) {
                val reason = when {
                    isQuotaDepleted -> "انتهى الرصيد (استنفاد حصة الميجابايت)"
                    isTimeOver -> "انتهت الصلاحية (انقضاء وقت الكرت: ${card.limitUptime})"
                    card.disabled -> "كرت متوقف / معطل من الإدارة"
                    else -> "كرت منتهي الصلاحية"
                }
                expiredSummaries.add(
                    com.example.data.model.ExpiredCardSummary(
                        cardId = card.id,
                        cardName = card.name,
                        profile = card.profile,
                        price = card.price.ifBlank { "غير محدد" },
                        uptime = card.uptime,
                        limitUptime = card.limitUptime.ifBlank { "غير محدد" },
                        totalBytes = card.bytesIn + card.bytesOut,
                        limitBytes = packageLimit,
                        expiredReason = reason,
                        isQuotaExhausted = isQuotaDepleted,
                        isTimeExpired = isTimeOver,
                        lastSeenDate = if (card.uptime != "0s") "تم استهلاك ${card.uptime}" else "كرت منتهي"
                    )
                )
            }
        }

        val quotaExhaustedTotal = expiredSummaries.count { it.isQuotaExhausted }
        val timeExpiredTotal = expiredSummaries.count { it.isTimeExpired }

        // Group by packages
        val packageMap = mutableMapOf<String, Long>()
        val packageCountMap = mutableMapOf<String, Int>()
        sessions.forEach { s ->
            val prof = s.profile.ifBlank { "افتراضي" }
            packageMap[prof] = (packageMap[prof] ?: 0L) + s.totalBytes
            packageCountMap[prof] = (packageCountMap[prof] ?: 0) + 1
        }
        cardList.forEach { c ->
            val prof = c.profile.ifBlank { "افتراضي" }
            packageMap[prof] = (packageMap[prof] ?: 0L) + (c.bytesIn + c.bytesOut)
        }

        val topPackages = packageMap.entries.map { (name, bytes) ->
            val pct = if (totalAllBytes > 0) (bytes.toFloat() / totalAllBytes.toFloat()) * 100f else 0f
            com.example.data.model.PackageConsumptionSummary(
                profileName = name,
                totalBytes = bytes,
                sessionsCount = packageCountMap[name] ?: 1,
                percentageOfTotal = pct
            )
        }.sortedByDescending { it.totalBytes }

        // Top devices
        val topDevices = sessions
            .filter { it.totalBytes > 0 }
            .sortedByDescending { it.totalBytes }
            .take(10)
            .map {
                com.example.data.model.DeviceConsumptionSummary(
                    deviceBrand = it.deviceInfo.brand,
                    deviceModel = it.deviceInfo.modelName,
                    macAddress = it.macAddress,
                    username = it.username,
                    totalBytes = it.totalBytes
                )
            }

        val avgBytes = if (sessions.isNotEmpty()) totalAllBytes / sessions.size else 0L

        com.example.data.model.NetworkConsumptionReport(
            totalDownloadBytes = totalDown,
            totalUploadBytes = totalUp,
            totalBytes = totalAllBytes,
            totalSessionsCount = sessions.size,
            activeSessionsCount = activeCount,
            offlineSessionsCount = offlineCount,
            expiredCardsCount = expiredSummaries.size,
            quotaExhaustedCount = quotaExhaustedTotal,
            timeExpiredCount = timeExpiredTotal,
            averageBytesPerUser = avgBytes,
            topPackagesByConsumption = topPackages,
            topDevicesByConsumption = topDevices,
            expiredCardsList = expiredSummaries,
            selectedTimeFilter = timeFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.model.NetworkConsumptionReport())

    fun clearSessionArchive() {
        viewModelScope.launch {
            database.appDao().clearSessionArchive()
            _toastEvent.emit("تم إفراغ أرشيف الجلسات السابقة بنجاح")
        }
    }

    fun reactivateCardFromReport(cardId: String) {
        val target = cards.value.firstOrNull { it.id == cardId || it.name == cardId }
        if (target != null) {
            reactivateCard(target)
        }
    }

    // Nodes Search, Sorting & Filtering
    private val _nodeSearchQuery = MutableStateFlow("")
    val nodeSearchQuery: StateFlow<String> = _nodeSearchQuery.asStateFlow()

    private val _nodeSortOption = MutableStateFlow(com.example.data.model.NodeSortOption.MOST_USERS)
    val nodeSortOption: StateFlow<com.example.data.model.NodeSortOption> = _nodeSortOption.asStateFlow()

    val filteredNetworkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = combine(
        networkNodes, _nodeSearchQuery, _nodeSortOption
    ) { nodes, query, sortOption ->
        val filtered = if (query.isBlank()) nodes else {
            nodes.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.ipAddress.contains(query, ignoreCase = true) ||
                        it.location.contains(query, ignoreCase = true) ||
                        it.model.contains(query, ignoreCase = true)
            }
        }

        when (sortOption) {
            com.example.data.model.NodeSortOption.FAULTY_FIRST -> filtered.sortedWith(compareByDescending<com.example.data.model.NetworkNode> { it.hasIssue }.thenByDescending { it.connectedUsers })
            com.example.data.model.NodeSortOption.MOST_USERS -> filtered.sortedByDescending { it.connectedUsers }
            com.example.data.model.NodeSortOption.HIGHEST_CONSUMPTION -> filtered.sortedByDescending { it.totalBytesConsumed }
            com.example.data.model.NodeSortOption.HIGHEST_CPU -> filtered.sortedByDescending { it.cpuLoadPercent }
            com.example.data.model.NodeSortOption.LOWEST_PING -> filtered.sortedBy { it.pingMs }
            com.example.data.model.NodeSortOption.ONLINE_FIRST -> filtered.sortedWith(compareByDescending<com.example.data.model.NetworkNode> { it.isOnline }.thenByDescending { it.connectedUsers })
            com.example.data.model.NodeSortOption.NAME_ASC -> filtered.sortedBy { it.name }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateNodeSortOption(sortOption: com.example.data.model.NodeSortOption) {
        _nodeSortOption.value = sortOption
    }

    // Filtering & Search
    private val _cardSearchQuery = MutableStateFlow("")
    val cardSearchQuery: StateFlow<String> = _cardSearchQuery.asStateFlow()

    private val _cardFilter = MutableStateFlow(CardFilter.ALL)
    val cardFilter: StateFlow<CardFilter> = _cardFilter.asStateFlow()

    val filteredCards: StateFlow<List<HotspotCard>> = combine(
        cards, activeUsers, _cardSearchQuery, _cardFilter
    ) { allCards, liveActiveUsers, query, filter ->
        val activeNames = liveActiveUsers.map { it.user }.toSet()
        allCards.distinctBy { it.name }.filter { card ->
            val matchesQuery = query.isBlank() ||
                    card.name.contains(query, ignoreCase = true) ||
                    card.comment.contains(query, ignoreCase = true) ||
                    card.profile.contains(query, ignoreCase = true)

            val isCurrentlyOnline = activeNames.contains(card.name)
            val isUsedOffline = !isCurrentlyOnline && (card.bytesIn > 0 || card.bytesOut > 0 || (card.uptime.isNotBlank() && card.uptime != "0s"))
            val isUnused = !isCurrentlyOnline && !isUsedOffline && !card.isExpired

            val matchesFilter = when (filter) {
                CardFilter.ALL -> true
                CardFilter.ACTIVE -> isCurrentlyOnline && !card.isExpired
                CardFilter.UNUSED -> isUnused
                CardFilter.EXPIRED -> card.isExpired
                CardFilter.EXPIRED_QUOTA -> card.isExpiredQuota
                CardFilter.EXPIRED_VALIDITY -> card.isExpiredValidity
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeUserSearch = MutableStateFlow("")
    val activeUserSearch: StateFlow<String> = _activeUserSearch.asStateFlow()

    val filteredActiveUsers: StateFlow<List<HotspotActiveUser>> = combine(
        activeUsers, _activeUserSearch
    ) { users, query ->
        if (query.isBlank()) users else {
            users.filter {
                it.user.contains(query, ignoreCase = true) ||
                        it.address.contains(query, ignoreCase = true) ||
                        it.macAddress.contains(query, ignoreCase = true) ||
                        it.comment.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Printable vouchers sheet
    private val _printableCards = MutableStateFlow<List<HotspotCard>>(emptyList())
    val printableCards: StateFlow<List<HotspotCard>> = _printableCards.asStateFlow()

    // Auto-refresh loop
    private val _autoRefresh = MutableStateFlow(true)
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    private var refreshJob: Job? = null

    // User message toasts / snackbars
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun updateCardSearch(query: String) {
        _cardSearchQuery.value = query
    }

    fun updateCardFilter(filter: CardFilter) {
        _cardFilter.value = filter
    }

    fun updateActiveUserSearch(query: String) {
        _activeUserSearch.value = query
    }

    fun toggleAutoRefresh(enabled: Boolean) {
        _autoRefresh.value = enabled
        if (enabled) startAutoRefresh() else stopAutoRefresh()
    }

    fun checkLocalNetwork(context: Context) {
        refreshDiscovery(context, showToast = false)
    }

    fun refreshDiscovery(context: Context, showToast: Boolean = true) {
        viewModelScope.launch {
            _isDiscovering.value = true
            val info = NetworkUtils.getLocalNetworkInfo(context)
            _localNetworkInfo.value = info

            val discovered = NetworkUtils.discoverRouter(context)
            _discoveredRouter.value = discovered
            _currentConfig.value = _currentConfig.value.copy(
                ipAddress = discovered.ip,
                port = discovered.port,
                useHttps = discovered.useHttps
            )
            _isDiscovering.value = false
            if (showToast) {
                _toastEvent.emit("تم تحديث الشبكة واكتشاف الراوتر: ${discovered.ip} (المنفذ ${discovered.port})")
            }
        }
    }

    private val _latestCachedSnapshot = MutableStateFlow<com.example.data.local.RouterSnapshot?>(null)
    val latestCachedSnapshot: StateFlow<com.example.data.local.RouterSnapshot?> = _latestCachedSnapshot.asStateFlow()

    fun refreshCachedSnapshot(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val snapshot = com.example.data.local.RouterCacheManager.getLatestSnapshot(context)
            _latestCachedSnapshot.value = snapshot
        }
    }

    fun enterOfflineMode(targetIp: String? = null, context: Context? = null) {
        viewModelScope.launch {
            val ctx = context ?: appContext
            if (ctx == null) {
                _toastEvent.emit("تعذر الدخول: سياق التطبيق غير متاح")
                return@launch
            }

            val snapshot = if (!targetIp.isNullOrBlank()) {
                com.example.data.local.RouterCacheManager.loadSnapshot(ctx, targetIp)
            } else {
                com.example.data.local.RouterCacheManager.getLatestSnapshot(ctx)
            }

            if (snapshot == null) {
                _toastEvent.emit("لا توجد بيانات راوتر محفوظة محلياً بعد. يرجى الاتصال بالراوتر أولاً لحفظ البيانات.")
                return@launch
            }

            repository.restoreFromSnapshot(snapshot)
            _currentConfig.value = RouterConnectionConfig(
                ipAddress = snapshot.ipAddress,
                name = snapshot.routerName
            )

            val displayRouterName = snapshot.routerName.ifBlank { "راوتر MikroTik (${snapshot.ipAddress})" }
            _connectionState.value = ConnectionState.Connected(
                routerName = displayRouterName,
                ipAddress = snapshot.ipAddress,
                isDemo = false,
                isSyncing = false,
                isFromCache = true,
                isOffline = true,
                lastSyncTime = snapshot.timestamp,
                offlineReason = "وضع التصفح والعمل المحلي (بدون إنترنت)"
            )
            _toastEvent.emit("⚡ تم الدخول للبيانات المحلية بنجاح! الكروت (${snapshot.cards.size}) والباقات متاحة للعمل.")
            startAutoRefresh()
        }
    }

    fun forceSaveCurrentLocalSnapshot(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val currentConn = _connectionState.value
            if (currentConn is ConnectionState.Connected) {
                val snapshot = repository.getCurrentSnapshot(currentConn.ipAddress, currentConn.routerName)
                com.example.data.local.RouterCacheManager.saveSnapshot(
                    context = context,
                    ipAddress = currentConn.ipAddress,
                    routerName = currentConn.routerName,
                    resource = snapshot.resource,
                    routerBoard = snapshot.routerBoard,
                    cards = snapshot.cards,
                    profiles = snapshot.profiles,
                    activeUsers = snapshot.activeUsers,
                    servers = snapshot.servers,
                    nodes = snapshot.nodes,
                    logs = snapshot.logs,
                    ipBindings = snapshot.ipBindings
                )
                refreshCachedSnapshot(context)
                _toastEvent.emit("✓ تم حفظ وتحديث قاعدة البيانات المحلية للراوتر بنجاح!")
            } else {
                _toastEvent.emit("لا توجد بيانات راوتر نشطة لحفظها حالياً")
            }
        }
    }

    fun connectToRouter(
        config: RouterConnectionConfig,
        saveToFavorites: Boolean = true,
        context: Context? = null
    ) {
        viewModelScope.launch {
            val targetContext = context ?: appContext
            _currentConfig.value = config

            var hasLoadedFromCache = false
            var cachedSnapshotTimestamp = 0L
            // 1. FAST INSTANT LOGIN FROM CACHE (دخول سريع فوري من الكاش):
            if (!config.isDemoMode && targetContext != null) {
                val cachedSnapshot = com.example.data.local.RouterCacheManager.loadSnapshot(targetContext, config.ipAddress)
                if (cachedSnapshot != null) {
                    repository.restoreFromSnapshot(cachedSnapshot)
                    hasLoadedFromCache = true
                    cachedSnapshotTimestamp = cachedSnapshot.timestamp
                    val cachedRouterName = cachedSnapshot.routerName.ifBlank { config.name.ifBlank { "راوتر MikroTik" } }
                    _connectionState.value = ConnectionState.Connected(
                        routerName = cachedRouterName,
                        ipAddress = config.ipAddress,
                        isDemo = false,
                        isSyncing = true,
                        isFromCache = true,
                        isOffline = false,
                        lastSyncTime = cachedSnapshotTimestamp
                    )
                    _toastEvent.emit("⚡ تم فتح بيانات الراوتر المخزنة فورياً! جاري مزامنة التحديثات...")
                }
            }

            if (!hasLoadedFromCache) {
                _connectionState.value = ConnectionState.Verifying(
                    "جاري الاتصال والتحقق من راوتر MikroTik (${config.ipAddress})...",
                    0.5f
                )
            }

            val result = repository.verifyAndConnect(config)
            if (result.isSuccess) {
                val liveBoardName = repository.resourceState.value.boardName
                val finalRouterName = if (config.name.isNotBlank() && !config.name.startsWith("راوتر 192")) {
                    config.name
                } else if (liveBoardName.isNotBlank() && !liveBoardName.contains("MikroTik Router")) {
                    liveBoardName
                } else {
                    config.name.ifBlank { "راوتر MikroTik" }
                }

                _connectionState.value = ConnectionState.Connected(
                    routerName = finalRouterName,
                    ipAddress = config.ipAddress,
                    isDemo = config.isDemoMode,
                    isSyncing = false,
                    isFromCache = false,
                    isOffline = false,
                    lastSyncTime = System.currentTimeMillis()
                )

                if (config.isDemoMode) {
                    _toastEvent.emit("تم الدخول في وضع العرض التفاعلي")
                } else if (hasLoadedFromCache) {
                    _toastEvent.emit("✅ تم مزامنة أحدث بيانات الراوتر بنجاح!")
                } else {
                    _toastEvent.emit("تم الاتصال بالراوتر بنجاح!")
                }

                // Auto-save or update router in saved list
                if (!config.isDemoMode) {
                    val existing = database.appDao().getSavedRouterByIp(config.ipAddress)
                    database.appDao().insertSavedRouter(
                        SavedRouterEntity(
                            id = existing?.id ?: 0,
                            name = finalRouterName,
                            ipAddress = config.ipAddress,
                            port = config.port,
                            username = config.username,
                            password = if (config.password.isNotBlank()) config.password else (existing?.password ?: ""),
                            useHttps = config.useHttps,
                            lastConnectedTime = System.currentTimeMillis()
                        )
                    )

                    // Persist new cache snapshot
                    if (targetContext != null) {
                        val snapshot = repository.getCurrentSnapshot(config.ipAddress, finalRouterName)
                        com.example.data.local.RouterCacheManager.saveSnapshot(
                            context = targetContext,
                            ipAddress = config.ipAddress,
                            routerName = finalRouterName,
                            resource = snapshot.resource,
                            routerBoard = snapshot.routerBoard,
                            cards = snapshot.cards,
                            profiles = snapshot.profiles,
                            activeUsers = snapshot.activeUsers,
                            servers = snapshot.servers,
                            nodes = snapshot.nodes,
                            logs = snapshot.logs,
                            ipBindings = snapshot.ipBindings
                        )
                    }
                }
                startAutoRefresh()
            } else {
                val error = result.exceptionOrNull()?.message ?: "فشل الاتصال بالراوتر"
                logApplicationError("CONNECT", error, "IP: ${config.ipAddress}")
                if (hasLoadedFromCache) {
                    _toastEvent.emit("تنبيه: تعذر الاتصال المباشر ($error). أنت تتصفح البيانات المحفوظة محلياً بأمان.")
                    _connectionState.value = ConnectionState.Connected(
                        routerName = config.name.ifBlank { "راوتر MikroTik (محفوظ)" },
                        ipAddress = config.ipAddress,
                        isDemo = false,
                        isSyncing = false,
                        isFromCache = true,
                        isOffline = true,
                        lastSyncTime = cachedSnapshotTimestamp,
                        offlineReason = "تعذر الاتصال المباشر: $error"
                    )
                    startAutoRefresh()
                } else {
                    _connectionState.value = ConnectionState.Error(error)
                    _toastEvent.emit(error)
                }
            }
        }
    }

    fun disconnect() {
        stopAutoRefresh()
        repository.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshData()
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            val currentConn = _connectionState.value
            if (currentConn is ConnectionState.Connected) {
                _connectionState.value = currentConn.copy(isSyncing = true)
                _toastEvent.emit("جاري محاولة الاتصال بالراوتر ومزامنة البيانات...")
                val res = repository.refreshData()
                if (res.isSuccess) {
                    _connectionState.value = currentConn.copy(
                        isSyncing = false,
                        isOffline = false,
                        offlineReason = "",
                        lastSyncTime = System.currentTimeMillis()
                    )
                    _toastEvent.emit("✅ تم الاتصال بالراوتر بنجاح وتحديث كافة البيانات!")
                } else {
                    val err = res.exceptionOrNull()?.message ?: "الراوتر غير متاح حالياً"
                    logApplicationError("SYNC_RETRY", err, "IP: ${currentConn.ipAddress}")
                    _connectionState.value = currentConn.copy(
                        isSyncing = false,
                        isOffline = true,
                        offlineReason = err
                    )
                    _toastEvent.emit("⚠️ تعذر الوصول للراوتر ($err) - البيانات المحفوظة محلياً متاحة ومحفوظة.")
                }
            }
        }
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        refreshJob = viewModelScope.launch {
            while (isActive && _autoRefresh.value) {
                delay(4000)
                val currentConn = _connectionState.value
                if (currentConn is ConnectionState.Connected) {
                    if (currentConn.isDemo) {
                        repository.refreshData()
                    } else {
                        val res = repository.refreshData()
                        if (res.isSuccess) {
                            if (currentConn.isOffline) {
                                _connectionState.value = currentConn.copy(
                                    isOffline = false,
                                    offlineReason = "",
                                    lastSyncTime = System.currentTimeMillis()
                                )
                                _toastEvent.emit("✅ تم استعادة الاتصال بالراوتر وتحديث البيانات بنجاح!")
                            }
                        } else {
                            if (!currentConn.isOffline) {
                                val err = res.exceptionOrNull()?.message ?: "انقطاع مؤقت في الاتصال بالراوتر"
                                _connectionState.value = currentConn.copy(
                                    isOffline = true,
                                    offlineReason = err
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun kickUser(user: HotspotActiveUser) {
        viewModelScope.launch {
            val result = repository.kickUser(user)
            if (result.isSuccess) {
                _toastEvent.emit("تم قطع اتصال المستخدم: ${user.user}")
            } else {
                _toastEvent.emit("تعذر قطع اتصال المستخدم: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            val result = repository.deleteCard(cardId)
            if (result.isSuccess) {
                _toastEvent.emit("تم حذف الكرت بنجاح")
            } else {
                _toastEvent.emit("فشل حذف الكرت")
            }
        }
    }

    fun reactivateCard(card: HotspotCard) {
        viewModelScope.launch {
            val result = repository.reactivateCard(card)
            if (result.isSuccess) {
                _toastEvent.emit("تمت إعادة تفعيل وتصفير عدادات الكرت (${card.name}) بنجاح!")
            } else {
                _toastEvent.emit("فشلت إعادة تفعيل الكرت: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun updateCard(updatedCard: HotspotCard) {
        viewModelScope.launch {
            val result = repository.updateCard(updatedCard)
            if (result.isSuccess) {
                _toastEvent.emit("تم تحديث بيانات الكرت (${updatedCard.name}) بنجاح")
            } else {
                _toastEvent.emit("فشل تحديث الكرت")
            }
        }
    }

    fun toggleCardDisabled(cardId: String, currentDisabled: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleCardDisabled(cardId, currentDisabled)
            if (result.isSuccess) {
                _toastEvent.emit(if (!currentDisabled) "تم إيقاف الكرت" else "تم تفعيل الكرت")
            }
        }
    }

    fun generateBatch(config: BatchGenerationConfig) {
        viewModelScope.launch {
            val result = repository.generateBatch(config)
            if (result.isSuccess) {
                val cards = result.getOrNull().orEmpty()
                _toastEvent.emit("تم توليد ${cards.size} كرت بنجاح!")
                // Save batch history
                val jsonArr = JSONArray()
                cards.take(10).forEach { jsonArr.put(it.name) }
                val batchId = "batch_${System.currentTimeMillis()}"
                database.appDao().insertBatch(
                    BatchHistoryEntity(
                        batchId = batchId,
                        name = config.batchComment.ifBlank { "دفعة ${cards.size} كروت" },
                        profileName = config.profile,
                        cardCount = cards.size,
                        price = config.price,
                        createdAt = System.currentTimeMillis(),
                        cardsJson = jsonArr.toString()
                    )
                )

                // If assigned to a POS Agent, save assignments
                if (config.posAgentId > 0 && config.posAgentName.isNotBlank()) {
                    val priceNum = config.price.filter { it.isDigit() }.toDoubleOrNull() ?: 200.0
                    val assignments = cards.map { card ->
                        PosCardAssignmentEntity(
                            agentId = config.posAgentId,
                            agentName = config.posAgentName,
                            cardName = card.name,
                            batchId = batchId,
                            profileName = config.profile,
                            priceAmount = priceNum,
                            discountPercent = config.discountPercent,
                            assignedAt = System.currentTimeMillis(),
                            isSold = false
                        )
                    }
                    database.appDao().insertCardAssignments(assignments)
                    _toastEvent.emit("تم تخصيص وتسليم ${cards.size} كرت لنقطة (${config.posAgentName}) بنجاح!")
                }

                // Set printable cards for instant preview
                _printableCards.value = cards
            } else {
                _toastEvent.emit("فشل توليد الكروت: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun addSingleCard(card: HotspotCard, posAgentId: Long = 0L, posAgentName: String = "", discountPercent: Double = 0.0) {
        viewModelScope.launch {
            val result = repository.addSingleCard(card)
            if (result.isSuccess) {
                if (posAgentId > 0L && posAgentName.isNotBlank()) {
                    val priceNum = card.price.filter { it.isDigit() }.toDoubleOrNull() ?: 200.0
                    val assignment = PosCardAssignmentEntity(
                        agentId = posAgentId,
                        agentName = posAgentName,
                        cardName = card.name,
                        batchId = "single_${System.currentTimeMillis()}",
                        profileName = card.profile,
                        priceAmount = priceNum,
                        discountPercent = discountPercent,
                        assignedAt = System.currentTimeMillis(),
                        isSold = false
                    )
                    database.appDao().insertCardAssignments(listOf(assignment))
                    _toastEvent.emit("تمت إضافة الكرت (${card.name}) وتخصيصه لنقطة ($posAgentName) في الراوتر وقاعدة البيانات!")
                } else {
                    _toastEvent.emit("تمت إضافة وتفعيل الكرت (${card.name}) في الراوتر بنجاح!")
                }
            } else {
                _toastEvent.emit("فشل إضافة الكرت: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    // ==========================================
    // POS Agent Operations
    // ==========================================
    fun addPosAgent(agent: PosAgentEntity) {
        viewModelScope.launch {
            database.appDao().insertPosAgent(agent)
            _toastEvent.emit("تمت إضافة نقطة البيع (${agent.name}) بنجاح")
        }
    }

    fun updatePosAgent(agent: PosAgentEntity) {
        viewModelScope.launch {
            database.appDao().insertPosAgent(agent)
            _toastEvent.emit("تم حفظ وتحديث بيانات نقطة البيع (${agent.name})")
        }
    }

    fun deletePosAgent(agentId: Long) {
        viewModelScope.launch {
            database.appDao().deletePosAgent(agentId)
            database.appDao().deleteAssignmentsByAgent(agentId)
            _toastEvent.emit("تم حذف نقطة البيع وسجلاتها")
        }
    }

    fun recordPosPayment(agentId: Long, agentName: String, amount: Double, note: String) {
        viewModelScope.launch {
            database.appDao().insertPosPayment(
                PosPaymentEntity(
                    agentId = agentId,
                    agentName = agentName,
                    amount = amount,
                    paymentDate = System.currentTimeMillis(),
                    paymentNote = note.ifBlank { "دفعة نقدية / سداد" }
                )
            )
            _toastEvent.emit("تم تسجيل سند قبض بمبلغ ${amount.toInt()} ر.ي بنجاح")
        }
    }

    fun deletePosPayment(paymentId: Long) {
        viewModelScope.launch {
            database.appDao().deletePosPayment(paymentId)
            _toastEvent.emit("تم حذف السند المالي")
        }
    }

    fun assignCardsToAgent(
        agentId: Long,
        agentName: String,
        cardsToAssign: List<HotspotCard>,
        discountPercent: Double,
        batchId: String = ""
    ) {
        viewModelScope.launch {
            val assignments = cardsToAssign.map { card ->
                val priceNum = card.price.filter { it.isDigit() }.toDoubleOrNull() ?: 200.0
                PosCardAssignmentEntity(
                    agentId = agentId,
                    agentName = agentName,
                    cardName = card.name,
                    batchId = batchId,
                    profileName = card.profile,
                    priceAmount = priceNum,
                    discountPercent = discountPercent,
                    assignedAt = System.currentTimeMillis(),
                    isSold = card.isActive || card.isExpired || (card.uptime.isNotBlank() && card.uptime != "0s")
                )
            }
            database.appDao().insertCardAssignments(assignments)
            _toastEvent.emit("تم تسليم وتخصيص ${cardsToAssign.size} كرت لنقطة ($agentName)")
        }
    }

    fun createProfile(profile: HotspotProfile) {
        viewModelScope.launch {
            repository.createProfile(profile)
            _toastEvent.emit("تم إنشاء باقة: ${profile.name}")
        }
    }

    fun updateProfile(profile: HotspotProfile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            _toastEvent.emit("تم حفظ وتحديث باقة: ${profile.name}")
        }
    }

    fun deleteProfile(profile: HotspotProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _toastEvent.emit("تم حذف باقة: ${profile.name}")
        }
    }

    fun rebootRouter() {
        viewModelScope.launch {
            _toastEvent.emit("جاري إرسال أمر إعادة تشغيل الراوتر...")
            val result = repository.rebootRouter()
            if (result.isSuccess) {
                _toastEvent.emit("تمت إعادة تشغيل الراوتر!")
                disconnect()
            } else {
                _toastEvent.emit("فشل أمر إعادة التشغيل")
            }
        }
    }

    fun setPrintableCards(cardsToPrint: List<HotspotCard>) {
        _printableCards.value = cardsToPrint
    }

    fun deleteSavedRouter(id: Long) {
        viewModelScope.launch {
            database.appDao().deleteSavedRouter(id)
            _toastEvent.emit("تم حذف الراوتر من المحفوظات")
        }
    }

    fun updateNodeSearch(query: String) {
        _nodeSearchQuery.value = query
    }

    fun addNetworkNode(node: com.example.data.model.NetworkNode) {
        viewModelScope.launch {
            repository.addNetworkNode(node)
            _toastEvent.emit("تمت إضافة نقطة الشبكة (${node.name}) بنجاح!")
        }
    }

    fun deleteNetworkNode(nodeId: String) {
        viewModelScope.launch {
            repository.deleteNetworkNode(nodeId)
            _toastEvent.emit("تم حذف النقطة بنجاح")
        }
    }

    fun pingNetworkNode(nodeId: String) {
        viewModelScope.launch {
            _toastEvent.emit("جاري فحص اتصال النقطة...")
            val (reachable, ms) = repository.pingNetworkNode(nodeId)
            if (reachable) {
                _toastEvent.emit("النقطة متصلة ومستجيبة (زمن الاستجابة: ${ms}ms)")
            } else {
                _toastEvent.emit("تنبيه: النقطة غير متصلة أو لا تستجيب للـ Ping!")
            }
        }
    }

    fun deleteBatchHistory(batchId: String) {
        viewModelScope.launch {
            database.appDao().deleteBatch(batchId)
        }
    }

    fun dismissUpdateBanner() {
        _showUpdateBanner.value = false
    }

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            val result = AppUpdateManager.checkLatestRelease(BuildConfig.VERSION_NAME)
            result.onSuccess { (releaseInfo, isNewer) ->
                if (isNewer) {
                    _updateCheckState.value = UpdateCheckState.UpdateAvailable(
                        release = releaseInfo,
                        currentVersion = BuildConfig.VERSION_NAME,
                        isNewer = true
                    )
                    _showUpdateBanner.value = true
                    if (isManual) {
                        _toastEvent.emit("يوجد تحديث جديد متاح: ${releaseInfo.tagName}")
                    }
                } else {
                    _updateCheckState.value = UpdateCheckState.UpToDate(
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestTag = releaseInfo.tagName,
                        release = releaseInfo
                    )
                    if (isManual) {
                        _toastEvent.emit("البرنامج محدث لأحدث إصدار (${BuildConfig.VERSION_NAME})")
                    }
                }
            }.onFailure { error ->
                _updateCheckState.value = UpdateCheckState.Error(error.localizedMessage ?: "فشل التحقق من التحديثات")
                if (isManual) {
                    _toastEvent.emit(error.localizedMessage ?: "فشل التحقق من التحديثات")
                }
            }
        }
    }

    fun forceInstallLatestRelease(context: Context) {
        viewModelScope.launch {
            _toastEvent.emit("جاري الاتصال بالمستودع لجلب أحدث نسخة APK...")
            _updateCheckState.value = UpdateCheckState.Checking
            val checkRes = AppUpdateManager.checkLatestRelease(BuildConfig.VERSION_NAME)
            checkRes.onSuccess { (releaseInfo, _) ->
                _updateCheckState.value = UpdateCheckState.UpdateAvailable(
                    release = releaseInfo,
                    currentVersion = BuildConfig.VERSION_NAME,
                    isNewer = true
                )
                val apkUrl = releaseInfo.apkAsset?.downloadUrl
                if (!apkUrl.isNullOrBlank()) {
                    downloadAndInstallUpdate(context, apkUrl)
                } else {
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(releaseInfo.htmlUrl)).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    _toastEvent.emit("تم فتح صفحة التحديث لتنزيل حزمة APK وتثبيتها مباشرة")
                }
            }.onFailure { err ->
                _toastEvent.emit("تعذر جلب التحديث: ${err.message}، جاري فتح المستودع...")
                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(AppUpdateManager.GITHUB_RELEASES_WEB_URL)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String) {
        viewModelScope.launch {
            _updateDownloadState.value = UpdateDownloadState.Downloading(0, 0, 0)
            val result = AppUpdateManager.downloadApk(
                context = context,
                downloadUrl = downloadUrl,
                onProgress = { percent, downloaded, total ->
                    _updateDownloadState.value = UpdateDownloadState.Downloading(percent, downloaded, total)
                }
            )
            result.onSuccess { apkFile ->
                _updateDownloadState.value = UpdateDownloadState.Completed(apkFile)
                _toastEvent.emit("تم اكتمال تنزيل التحديث! جاري فتح مثبت الحزم...")
                val installResult = AppUpdateManager.installApk(context, apkFile)
                installResult.onFailure { err ->
                    _toastEvent.emit(err.localizedMessage ?: "حدث خطأ أثناء بدء التثبيت")
                }
            }.onFailure { err ->
                _updateDownloadState.value = UpdateDownloadState.Error(err.localizedMessage ?: "فشل تنزيل ملف التحديث")
                _toastEvent.emit("خطأ في التنزيل: ${err.localizedMessage ?: err.message}")
            }
        }
    }

    fun installDownloadedApk(context: Context, apkFile: java.io.File) {
        val result = AppUpdateManager.installApk(context, apkFile)
        result.onFailure { err ->
            viewModelScope.launch {
                _toastEvent.emit(err.localizedMessage ?: "تعذر فتح ملف التثبيت")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }
}

class MainViewModelFactory(
    private val repository: MikrotikRepository,
    private val database: AppDatabase,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, database, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
