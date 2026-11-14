package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
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
import com.example.data.local.SystemEventLogEntity
import com.example.data.local.toUserSessionRecord
import com.example.data.local.RouterTaskQueueManager
import com.example.data.local.RouterTaskEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.example.data.model.BatchCardGenerationResult
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.ConnectionState
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.CardBatchGroup
import com.example.data.model.IpBinding
import com.example.data.model.LocalNetworkInfo
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.RouterResource
import com.example.BuildConfig
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.UpdateCheckState
import com.example.data.model.UpdateDownloadState
import com.example.data.model.DetailedPackageProfitStat
import com.example.data.model.MonthlyPackageSales
import com.example.data.model.DailySalesPoint
import com.example.data.model.FinanceCloudSyncStatus
import com.example.data.model.FinanceCloudValidator
import com.example.data.model.ValidatedMonthlyFetchResult
import com.example.data.util.AppUpdateManager
import com.example.data.util.FinanceAnalyticsEngine
import com.example.data.network.FinanceFirestoreRepository
import com.example.data.network.MikrotikRepository
import com.example.data.network.NetworkUtils
import com.example.data.network.OperationType
import com.example.data.network.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class CardFilter {
    ALL,
    BATCHES,
    ACTIVE,
    UNUSED,
    EXPIRED,
    EXPIRED_QUOTA,
    EXPIRED_VALIDITY
}

enum class CardSystemFilter {
    ALL,
    HOTSPOT,
    USER_MANAGER
}

enum class ProfileSystemFilter {
    ALL,
    HOTSPOT,
    USER_MANAGER
}

class MainViewModel(
    private val repository: MikrotikRepository,
    private val database: AppDatabase,
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) : ViewModel() {



    data class ConflictData(
        val recordType: String,
        val recordName: String,
        val localValue: String,
        val remoteValue: String,
        val onResolve: (Boolean) -> Unit
    )

    private val _conflictPromptState = MutableStateFlow<ConflictData?>(null)
    val conflictPromptState: StateFlow<ConflictData?> = _conflictPromptState.asStateFlow()

    fun promptOwnerForConflict(recordType: String, recordName: String, localVal: String, remoteVal: String, onResolve: (Boolean) -> Unit) {
        _conflictPromptState.value = ConflictData(recordType, recordName, localVal, remoteVal, onResolve)
    }

    fun dismissConflictPrompt(keepLocal: Boolean) {
        val current = _conflictPromptState.value
        current?.onResolve?.invoke(keepLocal)
        _conflictPromptState.value = null
    }

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    val updateDownloadState: StateFlow<UpdateDownloadState> = AppUpdateManager.downloadState

    fun cancelDownloadUpdate() {
        if (appContext != null) {
            val stopIntent = Intent(appContext, com.example.data.util.AppUpdateService::class.java)
            appContext.stopService(stopIntent)
        }
        AppUpdateManager.updateState(UpdateDownloadState.Idle)
        _toastEvent.tryEmit("⏸️ تم إيقاف التحميل.")
    }

    private val _showUpdateBanner = MutableStateFlow(false)
    val showUpdateBanner: StateFlow<Boolean> = _showUpdateBanner.asStateFlow()

    private val _autoUpdateMinorReleases = MutableStateFlow(true)
    val autoUpdateMinorReleases: StateFlow<Boolean> = _autoUpdateMinorReleases.asStateFlow()

    fun setAutoUpdateMinorReleases(enabled: Boolean) {
        _autoUpdateMinorReleases.value = enabled
    }

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

    // Local Task Queue for Offline Operations & Exponential Backoff
    val taskQueueManager = RouterTaskQueueManager(database.appDao())
    val pendingTasksCount: StateFlow<Int> = database.appDao().getPendingTaskCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val isProcessingQueue: StateFlow<Boolean> = taskQueueManager.isProcessing

    fun processTaskQueueNow() {
        viewModelScope.launch {
            _toastEvent.emit("جاري معالجة طابور العمليات المؤجلة مع الراوتر...")
            val result = taskQueueManager.forceRetryAllPending(repository)
            _toastEvent.emit(result.message)
        }
    }

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
                    val entity = com.example.data.local.NetworkNodeEntity(
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
                    
                    // Sync to Cloud
                    syncToCloud("networkNodes", n.id, mapOf(
                        "id" to n.id,
                        "name" to n.name,
                        "ipAddress" to n.ipAddress,
                        "macAddress" to n.macAddress,
                        "status" to n.effectiveStatus.name,
                        "isOnline" to n.isOnline,
                        "pingMs" to n.pingMs,
                        "connectedUsers" to n.connectedUsers,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    ))
                    entity
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

    val systemEventLogs: StateFlow<List<com.example.data.local.SystemEventLogEntity>> = database.appDao().getAllSystemEventLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val financeFirestoreRepository = FinanceFirestoreRepository(db)

    private val _financeCloudSyncStatus = MutableStateFlow<FinanceCloudSyncStatus>(
        if (getUserId() != null) FinanceCloudSyncStatus.Syncing else FinanceCloudSyncStatus.SignedOut
    )
    val financeCloudSyncStatus: StateFlow<FinanceCloudSyncStatus> = _financeCloudSyncStatus.asStateFlow()

    private fun getUserId(): String? {
        return runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    }

    private fun authStateFlow(): kotlinx.coroutines.flow.Flow<String?> = callbackFlow {
        val firebaseAuth = runCatching { Firebase.auth }.getOrNull()
        if (firebaseAuth == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        trySend(firebaseAuth.currentUser?.uid)
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    @Volatile
    private var lastSnapshotSaveTimeMs: Long = 0L
    @Volatile
    private var lastSnapshotSignature: String = ""
    private val lastArchivedSessionSignatures = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastActiveLoginStatsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun syncToCloud(collectionName: String, docId: String, data: Map<String, Any?>) {
        val uid = getUserId() ?: return
        val safeDocId = FinanceCloudValidator.sanitizeFirestoreDocId(docId)
        val path = "users/$uid/$collectionName/$safeDocId"
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.collection("users").document(uid).collection(collectionName).document(safeDocId)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { e ->
                        handleFirestoreError(e, OperationType.WRITE, path)
                        logApplicationError("Cloud Sync", "فشل المزامنة لـ $collectionName: ${e.message}")
                    }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
            }
        }
    }

    fun recordSystemEvent(
        actionType: String,
        targetType: String,
        targetName: String,
        description: String,
        status: String = "SUCCESS",
        details: String = ""
    ) {
        viewModelScope.launch {
            val routerIp = (_connectionState.value as? ConnectionState.Connected)?.ipAddress ?: _currentConfig.value.ipAddress.ifBlank { "192.168.88.1" }
            val timestamp = System.currentTimeMillis()
            val log = com.example.data.local.SystemEventLogEntity(
                actionType = actionType,
                targetType = targetType,
                targetName = targetName,
                description = description,
                operatorUsername = "admin",
                routerIp = routerIp,
                status = status,
                details = details,
                timestamp = timestamp
            )
            val insertedId = database.appDao().insertSystemEventLog(log)
            
            // Sync to Firestore
            val cloudData = mapOf(
                "id" to insertedId.toString(),
                "timestamp" to com.google.firebase.Timestamp(java.util.Date(timestamp)),
                "actionType" to actionType,
                "targetType" to targetType,
                "targetName" to targetName,
                "description" to description,
                "status" to status,
                "operatorUsername" to "admin",
                "routerIp" to routerIp,
                "details" to details
            )
            syncToCloud("systemLogs", insertedId.toString(), cloudData)
        }
    }

    fun deleteSystemEventLog(id: Long) {
        viewModelScope.launch {
            database.appDao().deleteSystemEventLogById(id)
            _toastEvent.emit("تم حذف القيد من السجل")
        }
    }

    fun getLoginHistoryForUser(username: String): kotlinx.coroutines.flow.Flow<List<com.example.data.local.UserLoginHistoryEntity>> {
        return database.appDao().getLoginHistoryForUser(username)
    }

    fun getArchivedSessionsForUser(username: String): kotlinx.coroutines.flow.Flow<List<com.example.data.local.SessionArchiveEntity>> {
        return database.appDao().getArchivedSessionsForUser(username)
    }

    fun savePackagePricing(profileName: String, salePrice: Double, costPrice: Double) {
        updatePackageCostAndPrice(
            profileName = profileName,
            costPrice = costPrice,
            salePrice = salePrice
        )
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

    private val _inspectedApps = MutableStateFlow<List<com.example.data.model.AppUsageItem>>(emptyList())
    val inspectedApps: StateFlow<List<com.example.data.model.AppUsageItem>> = _inspectedApps.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    fun inspectSubscriberTraffic(ipAddress: String) {
        viewModelScope.launch {
            _isInspecting.value = true
            try {
                val results = repository.inspectSubscriberApps(ipAddress)
                _inspectedApps.value = results
                _toastEvent.emit("⚡ تم إتمام فحص وتصنيف التطبيقات بنجاح للمشترك ($ipAddress)")
            } catch (e: Exception) {
                _toastEvent.emit("تعذر فحص التطبيقات: ${e.localizedMessage}")
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun getUsageBreakdown(username: String, type: String): kotlinx.coroutines.flow.Flow<List<com.example.data.model.AppUsageItem>> {
        return getSubscriberProfileFlow(username).map { profile ->
            val total = when(type.lowercase()) {
                "download" -> profile.lifetimeBytesOut
                "upload" -> profile.lifetimeBytesIn
                "time" -> profile.lifetimeUptimeSeconds * 1024 * 1024L // Scale for visualization
                else -> profile.lifetimeTotalBytes
            }
            
            if (total <= 0L) {
                return@map listOf(
                    com.example.data.model.AppUsageItem("لا توجد بيانات استهلاك", "نظام", 0L, 0f, 0xFF9E9E9E)
                )
            }

            // Smart heuristic app category distribution weights (deterministic variation per user based on hash)
            val hash = kotlin.math.abs(username.hashCode())
            val ytShare = 0.40f + ((hash % 10) / 100f)        // ~40-49%
            val igShare = 0.25f + (((hash / 10) % 8) / 100f)   // ~25-32%
            val fbShare = 0.15f                               // ~15%
            val tkShare = 0.10f                               // ~10%
            val waShare = 0.06f                               // ~6%
            val otShare = maxOf(0.01f, 1.0f - (ytShare + igShare + fbShare + tkShare + waShare)) // remainder (~4%)

            val apps = listOf(
                Triple("YouTube (فيديوهات وبث)", "تدفق وسائط مرئية عالي الدقة", ytShare to 0xFFFF0000L), // Red
                Triple("Instagram (ريلز وستوري)", "صور وفيديوهات قصيرة وتصفح", igShare to 0xFFE1306CL), // Pink/Magenta
                Triple("Facebook (فيديوهات ومنشورات)", "شبكة اجتماعية وفيديوهات", fbShare to 0xFF1877F2L), // Blue
                Triple("TikTok (فيديوهات قصيرة)", "مقاطع فيديو ترفيهية قصيرة", tkShare to 0xFF000000L), // Black
                Triple("WhatsApp (مراسلة ومكالمات)", "محادثات ومكالمات صوتية ومرئية", waShare to 0xFF25D366L), // Green
                Triple("Web Browsing & Other (تصفح عام)", "تصفح الويب وتطبيقات أخرى", otShare to 0xFFFFB300L) // Amber
            )

            var allocatedBytes = 0L
            val items = apps.mapIndexed { idx, (appName, category, pair) ->
                val (weight, color) = pair
                val appBytes = if (idx == apps.lastIndex) {
                    total - allocatedBytes
                } else {
                    (total * weight).toLong()
                }
                allocatedBytes += appBytes
                val pct = if (total > 0) (appBytes.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f) else 0f

                com.example.data.model.AppUsageItem(
                    appName = appName,
                    category = category,
                    bytesUsed = if (type.lowercase() == "time") appBytes / (1024 * 1024) else appBytes,
                    percentage = pct,
                    color = color
                )
            }
            items.sortedByDescending { it.percentage }
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

        // Quota & Limits (Improved: Fallback to profile limit if card limit is 0)
        val cardLimit = currentCard?.limitBytesTotal ?: 0L
        val profileLimit = currentProfile?.dataLimit?.let { NetworkUtils.parseSizeToBytes(it) } ?: 0L
        val quotaLimitBytes = if (cardLimit > 0L) cardLimit else profileLimit

        val quotaRemainingBytes = if (quotaLimitBytes > 0L) {
            (quotaLimitBytes - lifetimeTotal).coerceAtLeast(0L)
        } else 0L

        val quotaUsagePercent = if (quotaLimitBytes > 0L) {
            (lifetimeTotal.toFloat() / quotaLimitBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val isQuotaExhausted = (quotaLimitBytes > 0L && lifetimeTotal >= quotaLimitBytes) ||
                currentCard?.isExpired == true ||
                archivedList.any { it.isQuotaExhausted }

        val timeLimitFormatted = currentCard?.limitUptime?.takeIf { it.isNotBlank() } 
            ?: currentProfile?.validity?.takeIf { it.isNotBlank() } 
            ?: ""
        val timeRemainingFormatted = if (timeLimitFormatted.isNotBlank()) {
            val limitSec = NetworkUtils.parseUptimeToSeconds(timeLimitFormatted)
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
                price = matchingProf?.price ?: currentCard?.price.orEmpty().ifBlank { "" },
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
                val timestamp = System.currentTimeMillis()
                val log = com.example.data.local.RouterErrorLogEntity(
                    operation = operation,
                    errorMessage = errorMessage,
                    details = details,
                    timestamp = timestamp
                )
                val insertedId = database.appDao().insertErrorLog(log)
                
                // Sync to Cloud
                syncToCloud("errorLogs", insertedId.toString(), mapOf(
                    "id" to insertedId.toString(),
                    "operation" to operation,
                    "errorMessage" to errorMessage,
                    "timestamp" to com.google.firebase.Timestamp(java.util.Date(timestamp))
                ))
            } catch (_: Exception) {}
        }
    }

    fun setTimeFilter(filter: com.example.data.model.TimeFilterRange) {
        _selectedTimeFilter.value = filter
    }

    fun updatePackageCostAndPrice(profileName: String, costPrice: Double, salePrice: Double) {
        viewModelScope.launch {
            val cleanCost = if (costPrice.isFinite()) costPrice.coerceAtLeast(0.0) else 0.0
            val cleanSale = if (salePrice.isFinite()) salePrice.coerceAtLeast(0.0) else 0.0
            val timestamp = System.currentTimeMillis()
            database.appDao().insertPackagePricing(
                com.example.data.local.PackagePricingEntity(
                    profileName = profileName,
                    costPrice = cleanCost,
                    salePrice = cleanSale,
                    updatedAt = timestamp
                )
            )

            // Validated Cloud Sync using sanitized docId
            financeFirestoreRepository.saveValidatedPackagePricing(
                profileName = profileName,
                costPrice = cleanCost,
                salePrice = cleanSale
            ).onFailure { err ->
                logApplicationError("Cloud Pricing Sync", "تعذرت مزامنة سعر الباقة ($profileName): ${err.message}")
            }
            _toastEvent.emit("تم حفظ وتحديث سعر التكلفة والبيع لباقة ($profileName) بنجاح")
        }
    }

    fun syncFinanceWithCloud() {
        val uid = getUserId()
        if (uid.isNullOrBlank()) {
            _financeCloudSyncStatus.value = FinanceCloudSyncStatus.SignedOut
            viewModelScope.launch {
                _toastEvent.emit("يرجى تسجيل الدخول بحساب Google أولاً لمزامنة السجل المالي السحابي")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Syncing
            try {
                syncPackagePricingsFromCloud(uid)
                val fetchRes = financeFirestoreRepository.fetchMonthlyProfitsSequentially(uid)
                val fetched = fetchRes.getOrElse { err ->
                    _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Error(err.message ?: "خطأ في الجلب السحابي")
                    _toastEvent.emit("تعذر جلب السجل المالي السحابي: ${err.message}")
                    return@launch
                }
                applyValidatedCloudMonthlyFetch(fetched)

                val pushReport = financeFirestoreRepository.upsertMonthlyProfitsValidated(monthlySalesList.value)
                    .getOrElse { err ->
                        _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Error(err.message ?: "خطأ في الحفظ السحابي")
                        _toastEvent.emit("تعذر رفع تحديثات الأرباح للسحابة: ${err.message}")
                        return@launch
                    }

                val postFetch = financeFirestoreRepository.fetchMonthlyProfitsSequentially(uid).getOrNull() ?: fetched
                applyValidatedCloudMonthlyFetch(postFetch)
                _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Synced(
                    syncedMonthsCount = postFetch.entities.size,
                    skippedInvalidCount = postFetch.rejectedDocsCount + pushReport.rejectedValidationCount
                )
                _toastEvent.emit(
                    "تمت المزامنة المالية المتسلسلة: ${postFetch.entities.size} شهر موثق" +
                        if (pushReport.totalWrittenCount > 0) " (تم رفع ${pushReport.totalWrittenCount} تحديث)" else ""
                )
            } catch (e: Exception) {
                _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Error(e.message ?: "خطأ غير متوقع")
                _toastEvent.emit("حدث خطأ أثناء مزامنة السحابة: ${e.message}")
            }
        }
    }

    private suspend fun syncPackagePricingsFromCloud(uid: String) {
        val cloudPricings = financeFirestoreRepository.fetchPackagePricings(uid).getOrNull() ?: return
        if (cloudPricings.isEmpty()) return
        val localByName = database.appDao().getAllPackagePricingsSync().associateBy { it.profileName }
        for (remote in cloudPricings) {
            val local = localByName[remote.profileName]
            if (local == null || remote.updatedAt > local.updatedAt) {
                database.appDao().insertPackagePricing(remote)
            }
        }
    }

    private suspend fun applyValidatedCloudMonthlyFetch(fetchResult: ValidatedMonthlyFetchResult) {
        if (fetchResult.breakdownsByMonth != _cloudPackageBreakdowns.value) {
            _cloudPackageBreakdowns.value = fetchResult.breakdownsByMonth
        }
        if (fetchResult.entities != _cloudMonthlyRecordsFlow.value) {
            _cloudMonthlyRecordsFlow.value = fetchResult.entities
        }
        if (fetchResult.entities.isNotEmpty()) {
            try {
                val existingByKey = database.appDao().getAllMonthlyProfitRecordsSync().associateBy { it.monthKey }
                val toUpsert = fetchResult.entities.filter { cloud ->
                    val cloudHasData = cloud.totalRevenue > 0.0 || cloud.soldCardsCount > 0 || cloud.totalCardsCount > 0 || cloud.totalBytesConsumed > 0L
                    val local = existingByKey[cloud.monthKey]
                    val localHasData = local != null && (local.totalRevenue > 0.0 || local.soldCardsCount > 0 || local.totalCardsCount > 0 || local.totalBytesConsumed > 0L)
                    if (!cloudHasData && localHasData) {
                        false
                    } else if (local == null) {
                        cloudHasData
                    } else {
                        cloudHasData && !localHasData
                    }
                }
                if (toUpsert.isNotEmpty()) {
                    database.appDao().insertOrUpdateMonthlyProfitRecords(toUpsert)
                }
            } catch (_: Exception) {}
        }
    }

    private val localCardsFlow = database.appDao().getAllLocalCardsFlow()
    private val archivedSessionsFlow = database.appDao().getAllArchivedSessions()
    private val _cloudMonthlyRecordsFlow = MutableStateFlow<List<com.example.data.local.MonthlyProfitRecordEntity>>(emptyList())
    private val _cloudPackageBreakdowns = MutableStateFlow<Map<String, List<MonthlyPackageSales>>>(emptyMap())
    private val initialMonthlyProfitRecordsFlow = flow {
        val saved = try {
            withContext(Dispatchers.IO) {
                database.appDao().getAllMonthlyProfitRecordsSync()
            }
        } catch (_: Exception) {
            emptyList()
        }
        emit(saved)
    }

    private val mergedSavedMonthlyRecordsFlow = combine(
        initialMonthlyProfitRecordsFlow,
        _cloudMonthlyRecordsFlow
    ) { initialRoom, cloudRecords ->
        val merged = initialRoom.associateBy { it.monthKey }.toMutableMap()
        for (cloud in cloudRecords) {
            val cloudHasData = cloud.totalRevenue > 0.0 || cloud.soldCardsCount > 0 || cloud.totalCardsCount > 0 || cloud.totalBytesConsumed > 0L
            val existing = merged[cloud.monthKey]
            val existingHasData = existing != null && (existing.totalRevenue > 0.0 || existing.soldCardsCount > 0 || existing.totalCardsCount > 0 || existing.totalBytesConsumed > 0L)
            if (existing == null || (cloudHasData && (!existingHasData || cloud.lastUpdatedTimestamp >= existing.lastUpdatedTimestamp))) {
                merged[cloud.monthKey] = cloud
            }
        }
        merged.values.sortedWith(
            compareByDescending<com.example.data.local.MonthlyProfitRecordEntity> { it.year }
                .thenByDescending { it.month }
        )
    }

    private data class FinancePrimaryInputs(
        val cards: List<HotspotCard>,
        val profiles: List<HotspotProfile>,
        val pricings: List<com.example.data.local.PackagePricingEntity>,
        val assignments: List<PosCardAssignmentEntity>,
        val batches: List<BatchHistoryEntity>
    )

    private data class FinanceHistoryInputs(
        val localCards: List<com.example.data.local.LocalCardEntity>,
        val loginHistory: List<com.example.data.local.UserLoginHistoryEntity>,
        val archivedSessions: List<com.example.data.local.SessionArchiveEntity>,
        val savedMonthlyRecords: List<com.example.data.local.MonthlyProfitRecordEntity>,
        val savedPackageBreakdowns: Map<String, List<MonthlyPackageSales>>
    )

    private val financePrimaryInputsFlow = combine(
        cards,
        profiles,
        packagePricings,
        database.appDao().getAllCardAssignments(),
        batchHistory
    ) { cardList, profileList, pricingList, assignmentList, batchList ->
        FinancePrimaryInputs(cardList, profileList, pricingList, assignmentList, batchList)
    }

    private val financeHistoryInputsFlow = combine(
        localCardsFlow,
        allLoginHistory,
        archivedSessionsFlow,
        mergedSavedMonthlyRecordsFlow,
        _cloudPackageBreakdowns
    ) { localCardList, loginList, sessionList, savedMonthlyList, cloudBreakdowns ->
        FinanceHistoryInputs(localCardList, loginList, sessionList, savedMonthlyList, cloudBreakdowns)
    }

    val detailedPackageProfitStats: StateFlow<List<com.example.data.model.DetailedPackageProfitStat>> = combine(
        financePrimaryInputsFlow,
        financeHistoryInputsFlow,
        _selectedTimeFilter
    ) { primary, history, timeFilter ->
        FinanceAnalyticsEngine.computeDetailedPackageProfitStats(
            cardList = primary.cards,
            profileList = primary.profiles,
            pricingList = primary.pricings,
            assignmentList = primary.assignments,
            timeFilter = timeFilter,
            localCards = history.localCards,
            loginHistory = history.loginHistory,
            archivedSessions = history.archivedSessions,
            batches = primary.batches
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val revenueAnalytics: StateFlow<com.example.data.model.RevenueAnalyticsSummary> = combine(
        detailedPackageProfitStats, cards, _selectedTimeFilter
    ) { stats, allCards, timeFilter ->
        FinanceAnalyticsEngine.computeRevenueAnalyticsSummary(
            stats = stats,
            allCards = allCards,
            timeFilter = timeFilter
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.model.RevenueAnalyticsSummary())

    val monthlySalesList: StateFlow<List<com.example.data.model.MonthlySalesSummary>> = combine(
        financePrimaryInputsFlow,
        financeHistoryInputsFlow
    ) { primary, history ->
        FinanceAnalyticsEngine.computeMonthlySalesList(
            allCards = primary.cards,
            localCards = history.localCards,
            profiles = primary.profiles,
            pricings = primary.pricings,
            assignments = primary.assignments,
            batches = primary.batches,
            loginHistory = history.loginHistory,
            archivedSessions = history.archivedSessions,
            savedMonthlyRecords = history.savedMonthlyRecords,
            savedPackageBreakdownsByMonth = history.savedPackageBreakdowns
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                c.price.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var previousActiveUsernames = emptySet<String>()



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

            // Clean up legacy template-only rows only if no live router profile/card uses that name
            val activeProfileNames = (profiles.value.map { it.name.trim().lowercase() } +
                cards.value.map { it.profile.trim().lowercase() }).toSet()
            if (activeProfileNames.isNotEmpty()) {
                com.example.data.model.PREDEFINED_PACKAGE_TEMPLATES.forEach { tpl ->
                    val profName = tpl.title.split("(").first().trim()
                    if (!activeProfileNames.contains(profName.lowercase())) {
                        database.appDao().deletePackagePricing(profName)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun checkAndMarkSoldCards(cardList: List<HotspotCard>) {
        val usedNames = cardList.asSequence()
            .filter {
                (it.uptime.isNotBlank() && it.uptime != "0s") ||
                    it.bytesIn > 0L ||
                    it.bytesOut > 0L ||
                    it.isExpired ||
                    it.isActive
            }
            .map { it.name }
            .toList()
        if (usedNames.isEmpty()) return

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dao = database.appDao()
                val unsoldAssigned = dao.getUnsoldAssignedCardNamesSync().toSet()
                if (unsoldAssigned.isEmpty()) return@withContext
                val newlySold = usedNames.filter { unsoldAssigned.contains(it) }
                if (newlySold.isEmpty()) return@withContext

                val now = System.currentTimeMillis()
                newlySold.chunked(400).forEach { chunk ->
                    dao.markCardsAsSoldBatch(chunk, now)
                }
                val soldTs = com.google.firebase.Timestamp(java.util.Date(now))
                newlySold.forEach { cardName ->
                    syncToCloud("posCards", cardName, mapOf(
                        "isSold" to true,
                        "soldAt" to soldTs
                    ))
                }
            } catch (_: Exception) {}
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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.model.NetworkConsumptionReport())

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

    private val _nodeTypeFilter = MutableStateFlow<com.example.data.model.NetworkNodeType?>(null)
    val nodeTypeFilter: StateFlow<com.example.data.model.NetworkNodeType?> = _nodeTypeFilter.asStateFlow()

    fun updateNodeTypeFilter(nodeType: com.example.data.model.NetworkNodeType?) {
        _nodeTypeFilter.value = nodeType
    }

    val filteredNetworkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = combine(
        networkNodes, _nodeSearchQuery, _nodeSortOption, _nodeTypeFilter
    ) { nodes, query, sortOption, typeFilter ->
        val filteredQuery = if (query.isBlank()) nodes else {
            nodes.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.ipAddress.contains(query, ignoreCase = true) ||
                        it.location.contains(query, ignoreCase = true) ||
                        it.model.contains(query, ignoreCase = true)
            }
        }

        val filtered = if (typeFilter == null) filteredQuery else {
            filteredQuery.filter { it.nodeType == typeFilter }
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

    private val _cardSystemFilter = MutableStateFlow(CardSystemFilter.ALL)
    val cardSystemFilter: StateFlow<CardSystemFilter> = _cardSystemFilter.asStateFlow()

    private val _cardDisplayLimit = MutableStateFlow(100)
    val cardDisplayLimit: StateFlow<Int> = _cardDisplayLimit.asStateFlow()

    fun loadMoreCards() {
        _cardDisplayLimit.value += 100
    }

    data class CardStats(
        val active: Int = 0,
        val unused: Int = 0,
        val expired: Int = 0,
        val expiredQuota: Int = 0,
        val expiredValidity: Int = 0,
        val total: Int = 0
    )

    val cardStats: StateFlow<CardStats> = combine(cards, activeUsers) { allCards, liveActiveUsers ->
        val activeNames = liveActiveUsers.map { it.user }.toSet()
        val distinct = allCards.distinctBy { it.name }
        var act = 0
        var unused = 0
        var exp = 0
        var expQ = 0
        var expV = 0
        for (c in distinct) {
            val isAct = activeNames.contains(c.name) && !c.isExpired
            if (isAct) act++
            else if ((c.uptime.isBlank() || c.uptime == "0s") && c.bytesIn == 0L && c.bytesOut == 0L && !c.isExpired) unused++
            if (c.isExpired) exp++
            if (c.isExpiredQuota) expQ++
            if (c.isExpiredValidity) expV++
        }
        CardStats(act, unused, exp, expQ, expV, distinct.size)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardStats())

    val filteredCards: StateFlow<List<HotspotCard>> = combine(
        cards, activeUsers, _cardSearchQuery, _cardFilter, _cardSystemFilter
    ) { allCards, liveActiveUsers, query, filter, systemFilter ->
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
                CardFilter.ALL, CardFilter.BATCHES -> true
                CardFilter.ACTIVE -> isCurrentlyOnline && !card.isExpired
                CardFilter.UNUSED -> isUnused
                CardFilter.EXPIRED -> card.isExpired
                CardFilter.EXPIRED_QUOTA -> card.isExpiredQuota
                CardFilter.EXPIRED_VALIDITY -> card.isExpiredValidity
            }

            val matchesSystem = when (systemFilter) {
                CardSystemFilter.ALL -> true
                CardSystemFilter.HOTSPOT -> card.isHotspotCard
                CardSystemFilter.USER_MANAGER -> card.isUserManagerCard
            }

            matchesQuery && matchesFilter && matchesSystem
        }.sortedWith(
            compareByDescending<HotspotCard> { repository.getCardCreationTimestamp(it) }
                .thenByDescending { it.id }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun cardMatchesSearchAndSystem(
        card: HotspotCard,
        query: String,
        systemFilter: CardSystemFilter,
        extraTextMatch: Boolean = false
    ): Boolean {
        val matchesQuery = query.isBlank() || extraTextMatch ||
                card.name.contains(query, ignoreCase = true) ||
                card.comment.contains(query, ignoreCase = true) ||
                card.profile.contains(query, ignoreCase = true)
        val matchesSystem = when (systemFilter) {
            CardSystemFilter.ALL -> true
            CardSystemFilter.HOTSPOT -> card.isHotspotCard
            CardSystemFilter.USER_MANAGER -> card.isUserManagerCard
        }
        return matchesQuery && matchesSystem
    }

    // Printable vouchers sheet & batch print tracking
    private val printedBatchesPrefs = appContext?.getSharedPreferences("netmanager_printed_batches", Context.MODE_PRIVATE)
    private val _printedBatchKeys = MutableStateFlow<Set<String>>(
        printedBatchesPrefs?.getStringSet("printed_keys", emptySet())?.toSet() ?: emptySet()
    )
    private val _selectedPrintableBatchKey = MutableStateFlow<String?>(null)
    val selectedPrintableBatchKey: StateFlow<String?> = _selectedPrintableBatchKey.asStateFlow()

    private val _printableCards = MutableStateFlow<List<HotspotCard>>(emptyList())
    val printableCards: StateFlow<List<HotspotCard>> = _printableCards.asStateFlow()

    private val batchPartitionState: StateFlow<IndependentBatchPartitionResult> = combine(
        cards, activeUsers, batchHistory, _printedBatchKeys
    ) { allCards, liveActiveUsers, batches, printedKeys ->
        val activeNamesLower = liveActiveUsers.map { it.user.lowercase() }.toSet()
        partitionCardsIntoIndependentBatches(
            allCards = allCards,
            activeUserNamesLower = activeNamesLower,
            batches = batches,
            printedKeys = printedKeys,
            timestampProvider = { repository.getCardCreationTimestamp(it) }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IndependentBatchPartitionResult())

    val allBatchGroups: StateFlow<List<CardBatchGroup>> = combine(
        batchPartitionState, _cardSearchQuery, _cardSystemFilter
    ) { partition, query, systemFilter ->
        partition.batchGroups.mapNotNull { batch ->
            val batchHeaderMatches = query.isNotBlank() && (
                batch.title.contains(query, ignoreCase = true) ||
                    batch.profile.contains(query, ignoreCase = true)
            )
            val matchingAll = batch.allBatchCards.filter {
                cardMatchesSearchAndSystem(it, query, systemFilter, batchHeaderMatches)
            }
            if (matchingAll.isEmpty()) return@mapNotNull null
            val matchingReady = batch.readyCards.filter {
                cardMatchesSearchAndSystem(it, query, systemFilter, batchHeaderMatches)
            }
            val totalVal = matchingAll.sumOf { parseBatchPriceValue(it.price.ifBlank { batch.price }) }
            batch.copy(
                cards = matchingAll,
                allBatchCards = matchingAll,
                readyCards = matchingReady,
                totalValue = totalVal
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allIndividualCards: StateFlow<List<HotspotCard>> = combine(
        batchPartitionState, _cardSearchQuery, _cardSystemFilter
    ) { partition, query, systemFilter ->
        partition.ungroupedCards.filter {
            cardMatchesSearchAndSystem(it, query, systemFilter)
        }.sortedWith(
            compareByDescending<HotspotCard> { repository.getCardCreationTimestamp(it) }
                .thenByDescending { it.id }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readyCardGroups: StateFlow<List<CardBatchGroup>> = combine(
        batchPartitionState, _cardFilter, _cardSearchQuery, _cardSystemFilter
    ) { partition, filter, query, systemFilter ->
        if (filter != CardFilter.UNUSED) return@combine emptyList()
        partition.batchGroups.mapNotNull { batch ->
            val batchHeaderMatches = query.isNotBlank() && (
                batch.title.contains(query, ignoreCase = true) ||
                    batch.profile.contains(query, ignoreCase = true)
            )
            val matchingReady = batch.readyCards.filter {
                cardMatchesSearchAndSystem(it, query, systemFilter, batchHeaderMatches)
            }
            if (matchingReady.isEmpty()) return@mapNotNull null
            val readyVal = matchingReady.sumOf { parseBatchPriceValue(it.price.ifBlank { batch.price }) }
            batch.copy(
                cards = matchingReady,
                readyCards = matchingReady,
                totalValue = readyVal
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readyIndividualCards: StateFlow<List<HotspotCard>> = combine(
        batchPartitionState, activeUsers, _cardFilter, _cardSearchQuery, _cardSystemFilter
    ) { partition, liveActiveUsers, filter, query, systemFilter ->
        if (filter != CardFilter.UNUSED) return@combine emptyList()
        val activeNamesLower = liveActiveUsers.map { it.user.lowercase() }.toSet()
        partition.ungroupedCards.filter { card ->
            val lower = card.name.lowercase()
            val isReady = !activeNamesLower.contains(lower) &&
                (card.uptime.isBlank() || card.uptime == "0s") &&
                card.bytesIn == 0L && card.bytesOut == 0L && !card.isExpired
            isReady && cardMatchesSearchAndSystem(card, query, systemFilter)
        }.sortedWith(
            compareByDescending<HotspotCard> { repository.getCardCreationTimestamp(it) }
                .thenByDescending { it.id }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagedFilteredCards: StateFlow<List<HotspotCard>> = combine(
        filteredCards, _cardDisplayLimit
    ) { list, limit ->
        list.take(limit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getCardCreationTimestamp(card: HotspotCard): Long {
        return repository.getCardCreationTimestamp(card)
    }

    // Profile Filtering & Search
    private val _profileSearchQuery = MutableStateFlow("")
    val profileSearchQuery: StateFlow<String> = _profileSearchQuery.asStateFlow()

    private val _profileSystemFilter = MutableStateFlow(ProfileSystemFilter.ALL)
    val profileSystemFilter: StateFlow<ProfileSystemFilter> = _profileSystemFilter.asStateFlow()

    val filteredProfiles: StateFlow<List<HotspotProfile>> = combine(
        profiles, _profileSearchQuery, _profileSystemFilter
    ) { allProfiles, query, systemFilter ->
        allProfiles.filter { prof ->
            val matchesQuery = query.isBlank() ||
                    prof.name.contains(query, ignoreCase = true) ||
                    prof.comment.contains(query, ignoreCase = true) ||
                    prof.rateLimit.contains(query, ignoreCase = true)

            val matchesSystem = when (systemFilter) {
                ProfileSystemFilter.ALL -> true
                ProfileSystemFilter.HOTSPOT -> prof.isHotspot
                ProfileSystemFilter.USER_MANAGER -> prof.isUserManager
            }

            matchesQuery && matchesSystem
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

    val printableBatchOptions: StateFlow<List<com.example.data.model.PrintableBatchOption>> =
        batchPartitionState.map { it.printableOptions }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auto-refresh loop
    private val _autoRefresh = MutableStateFlow(true)
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    private val _profileCheckState = MutableStateFlow<Result<Boolean>?>(null)
    val profileCheckState: StateFlow<Result<Boolean>?> = _profileCheckState.asStateFlow()

    fun checkProfileValidity(profileName: String) {
        viewModelScope.launch {
            _profileCheckState.value = null
            val result = repository.verifyUserConnectionAndProfile(profileName)
            _profileCheckState.value = result
            if (result.isFailure) {
                _toastEvent.emit(result.exceptionOrNull()?.message ?: "فشل فحص الباقة")
            } else {
                _toastEvent.emit("الباقة صالحة والاتصال بالراوتر سليم ✅")
            }
        }
    }

    private var refreshJob: Job? = null
    private var connectJob: Job? = null
    private var consecutiveRefreshFailures = 0
    private val seenRouterLogKeys = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    // User message toasts / snackbars
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun updateCardSearch(query: String) {
        _cardSearchQuery.value = query
    }

    fun updateCardFilter(filter: CardFilter) {
        _cardFilter.value = filter
    }

    fun updateCardSystemFilter(filter: CardSystemFilter) {
        _cardSystemFilter.value = filter
    }

    fun updateProfileSearchQuery(query: String) {
        _profileSearchQuery.value = query
    }

    fun updateProfileSystemFilter(filter: ProfileSystemFilter) {
        _profileSystemFilter.value = filter
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
        connectJob?.cancel()
        stopAutoRefresh()
        consecutiveRefreshFailures = 0
        seenRouterLogKeys.clear()
        connectJob = viewModelScope.launch {
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
                    val routerEntity = SavedRouterEntity(
                        id = existing?.id ?: 0,
                        name = finalRouterName,
                        ipAddress = config.ipAddress,
                        port = config.port,
                        username = config.username,
                        password = if (config.password.isNotBlank()) config.password else (existing?.password ?: ""),
                        useHttps = config.useHttps,
                        lastConnectedTime = System.currentTimeMillis()
                    )
                    val insertedId = database.appDao().insertSavedRouter(routerEntity)
                    
                    // Sync to Cloud
                    syncToCloud("routers", if (routerEntity.id > 0) routerEntity.id.toString() else insertedId.toString(), mapOf(
                        "id" to if (routerEntity.id > 0) routerEntity.id.toString() else insertedId.toString(),
                        "name" to finalRouterName,
                        "ipAddress" to config.ipAddress,
                        "port" to config.port,
                        "username" to config.username,
                        "useHttps" to config.useHttps,
                        "isDemoMode" to false
                    ))

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
                // Process pending offline operations upon connection
                viewModelScope.launch {
                    val qResult = taskQueueManager.processPendingTasks(repository)
                    if (qResult.processedCount > 0) {
                        _toastEvent.emit(qResult.message)
                    }
                }
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
        connectJob?.cancel()
        connectJob = null
        consecutiveRefreshFailures = 0
        stopAutoRefresh()
        repository.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshData(forceHistorySync = true)
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            val currentConn = _connectionState.value
            if (currentConn is ConnectionState.Connected) {
                _connectionState.value = currentConn.copy(isSyncing = true)
                _toastEvent.emit("جاري محاولة الاتصال بالراوتر ومزامنة البيانات...")
                val res = repository.refreshData(forceHistorySync = true)
                val latestConn = _connectionState.value as? ConnectionState.Connected ?: return@launch
                if (res.isSuccess) {
                    consecutiveRefreshFailures = 0
                    _connectionState.value = latestConn.copy(
                        isSyncing = false,
                        isOffline = false,
                        offlineReason = "",
                        lastSyncTime = System.currentTimeMillis()
                    )
                    _toastEvent.emit("✅ تم الاتصال بالراوتر بنجاح وتحديث كافة البيانات!")
                    // Process pending tasks on successful reconnection
                    viewModelScope.launch {
                        val qResult = taskQueueManager.processPendingTasks(repository)
                        if (qResult.processedCount > 0) {
                            _toastEvent.emit(qResult.message)
                        }
                    }
                } else {
                    val err = res.exceptionOrNull()?.message ?: "الراوتر غير متاح حالياً"
                    logApplicationError("SYNC_RETRY", err, "IP: ${latestConn.ipAddress}")
                    _connectionState.value = latestConn.copy(
                        isSyncing = false,
                        isOffline = true,
                        offlineReason = err
                    )
                    _toastEvent.emit("⚠️ تعذر الوصول للراوتر ($err) - البيانات المحفوظة محلياً متاحة ومحفوظة.")
                }
            }
        }
    }

    private var pauseAutoRefreshUntil: Long = 0L

    fun pauseAutoRefreshFor(durationMs: Long = 6000L) {
        pauseAutoRefreshUntil = System.currentTimeMillis() + durationMs
    }

    private var trafficPollJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        stopAutoRefresh()
        consecutiveRefreshFailures = 0
        // Fast 3-second live traffic polling loop for real-time speed display
        trafficPollJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val currentConn = _connectionState.value
                if (currentConn is ConnectionState.Connected && !currentConn.isOffline) {
                    try {
                        repository.pollLiveTrafficSpeeds()
                    } catch (_: Exception) {}
                }
            }
        }

        refreshJob = viewModelScope.launch {
            while (isActive && _autoRefresh.value) {
                // Background cycle every 8 seconds for overall router data
                delay(8000)

                if (System.currentTimeMillis() < pauseAutoRefreshUntil) {
                    continue
                }

                val currentConn = _connectionState.value
                if (currentConn is ConnectionState.Connected) {
                    if (currentConn.isDemo) {
                        repository.refreshData()
                    } else {
                        val res = repository.refreshData()
                        val latestConn = _connectionState.value as? ConnectionState.Connected ?: continue
                        if (res.isSuccess) {
                            consecutiveRefreshFailures = 0
                            if (latestConn.isOffline) {
                                _connectionState.value = latestConn.copy(
                                    isOffline = false,
                                    offlineReason = "",
                                    lastSyncTime = System.currentTimeMillis()
                                )
                                _toastEvent.emit("✅ تم استعادة الاتصال بالراوتر وتحديث البيانات بنجاح!")
                                taskQueueManager.processPendingTasks(repository)
                            } else {
                                _connectionState.value = latestConn.copy(
                                    lastSyncTime = System.currentTimeMillis()
                                )
                                if (pendingTasksCount.value > 0) {
                                    // Background automatic retry with exponential backoff
                                    taskQueueManager.processPendingTasks(repository)
                                }
                            }
                        } else {
                            consecutiveRefreshFailures++
                            // Require 2 consecutive failures before marking offline to avoid single-packet flapping
                            if (consecutiveRefreshFailures >= 2 && !latestConn.isOffline) {
                                val err = res.exceptionOrNull()?.message ?: "انقطاع مؤقت في الاتصال بالراوتر"
                                _connectionState.value = latestConn.copy(
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
        trafficPollJob?.cancel()
        trafficPollJob = null
    }

    fun kickUser(user: HotspotActiveUser) {
        viewModelScope.launch {
            val result = repository.kickUser(user)
            if (result.isSuccess) {
                recordSystemEvent("طرد", "جلسة", user.user, "تم فصل وقطع اتصال المستخدم (${user.user}) من الراوتر", "WARNING", "الآيبي: ${user.address}, الماك: ${user.macAddress}, وقت الاتصال: ${user.uptime}")
                _toastEvent.emit("تم قطع اتصال المستخدم: ${user.user}")
            } else {
                recordSystemEvent("طرد", "جلسة", user.user, "فشل قطع اتصال المستخدم (${user.user})", "FAILED", result.exceptionOrNull()?.message ?: "")
                _toastEvent.emit("تعذر قطع اتصال المستخدم: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteCard(cardId: String, cardName: String = "") {
        viewModelScope.launch {
            val targetName = cardName.trim().ifBlank { 
                cards.value.find { it.id == cardId }?.name.orEmpty() 
            }
            // Instant optimistic deletion from UI
            repository.deleteLocalCard(cardId, targetName)
            val result = repository.deleteCard(cardId, targetName)
            if (result.isSuccess) {
                recordSystemEvent("حذف", "كرت", targetName.ifBlank { cardId }, "تم حذف الكرت (${targetName.ifBlank { cardId }}) من الراوتر وقاعدة البيانات بنجاح", "SUCCESS")
                _toastEvent.emit("تم حذف الكرت بنجاح")
            } else {
                val errorMsg = result.exceptionOrNull()?.message.orEmpty()
                // Enqueue in Task Queue for offline retry with Exponential Backoff
                taskQueueManager.enqueueDeleteCard(cardId, targetName)
                // Optimistically remove from local database so it doesn't bounce back in the UI
                if (targetName.isNotBlank()) {
                    try { database.appDao().deleteLocalCardByName(targetName) } catch (_: Exception) {}
                }
                recordSystemEvent("طابور العمليات", "كرت", targetName.ifBlank { cardId }, "تم حفظ أمر حذف الكرت محلياً وسيتم تنفيذه على الراوتر تلقائياً عند استعادة الاتصال.", "PENDING", errorMsg)
                _toastEvent.emit("تم حفظ الحذف محلياً وسيتم تنفيذه على الراوتر تلقائياً عند الاتصال (طابور العمليات)")
            }
        }
    }

    fun reactivateCard(card: HotspotCard) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            // 1. Instant optimistic update
            val resetCard = card.copy(uptime = "0s", bytesIn = 0L, bytesOut = 0L, disabled = false)
            repository.updateLocalCard(resetCard)
            repository.upsertLocalCardPreservingTimestamp(resetCard)

            // 2. Execute on router
            val result = repository.reactivateCard(card)
            if (result.isSuccess) {
                recordSystemEvent("تعديل", "كرت", card.name, "تمت إعادة تفعيل وتصفير عدادات استهلاك الكرت (${card.name}) في الراوتر", "SUCCESS")
                _toastEvent.emit("تمت إعادة تفعيل وتصفير عدادات الكرت (${card.name}) بنجاح!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message.orEmpty()
                taskQueueManager.enqueueReactivateCard(card)
                recordSystemEvent("طابور العمليات", "كرت", card.name, "تم حفظ إعادة التفعيل محلياً وسيتم تنفيذها على الراوتر فور استعادة الاتصال.", "PENDING", errorMsg)
                _toastEvent.emit("تم حفظ إعادة التفعيل محلياً وسيتم تنفيذها على الراوتر فور استعادة الاتصال (طابور العمليات)")
            }
        }
    }

    fun updateCard(updatedCard: HotspotCard) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            // 1. Instant optimistic update
            repository.updateLocalCard(updatedCard)
            repository.upsertLocalCardPreservingTimestamp(updatedCard)

            // 2. Execute on router
            val result = repository.updateCard(updatedCard)
            if (result.isSuccess) {
                recordSystemEvent("تعديل", "كرت", updatedCard.name, "تم تحديث بيانات وإعدادات الكرت (${updatedCard.name}) في الراوتر", "SUCCESS", "الباقة: ${updatedCard.profile}, التعليق: ${updatedCard.comment}")
                _toastEvent.emit("تم تحديث بيانات الكرت (${updatedCard.name}) بنجاح")
            } else {
                val errorMsg = result.exceptionOrNull()?.message.orEmpty()
                taskQueueManager.enqueueEditCard(updatedCard)
                recordSystemEvent("طابور العمليات", "كرت", updatedCard.name, "تم حفظ تعديل الكرت محلياً وسيتم إرساله للراوتر فور استعادة الاتصال.", "PENDING", errorMsg)
                _toastEvent.emit("تم حفظ التعديل محلياً وسيتم إرساله للراوتر فور استعادة الاتصال (طابور العمليات)")
            }
        }
    }

    fun toggleCardDisabled(cardId: String, currentDisabled: Boolean) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            val targetDisabled = !currentDisabled
            val targetCard = cards.value.find { it.id == cardId }
            val targetName = targetCard?.name.orEmpty()

            // 1. Instant optimistic update
            if (targetCard != null) {
                val updated = targetCard.copy(disabled = targetDisabled)
                repository.updateLocalCard(updated)
                repository.upsertLocalCardPreservingTimestamp(updated)
            }

            // 2. Execute on router
            val result = repository.toggleCardDisabled(cardId, currentDisabled)
            if (result.isSuccess) {
                val actionDesc = if (targetDisabled) "تم إيقاف وتعطيل الكرت (${targetName.ifBlank { cardId }}) عن العمل في الراوتر" else "تم تفعيل وتشغيل الكرت (${targetName.ifBlank { cardId }}) في الراوتر"
                recordSystemEvent("تعديل", "كرت", targetName.ifBlank { cardId }, actionDesc, "SUCCESS")
                _toastEvent.emit(if (targetDisabled) "تم إيقاف الكرت" else "تم تفعيل الكرت")
            } else {
                val errorMsg = result.exceptionOrNull()?.message.orEmpty()
                taskQueueManager.enqueueToggleDisabled(cardId, targetName, targetDisabled)
                recordSystemEvent("طابور العمليات", "كرت", targetName.ifBlank { cardId }, "تم حفظ تغيير الحالة محلياً وسيتم تنفيذها على الراوتر فور استعادة الاتصال.", "PENDING", errorMsg)
                _toastEvent.emit(if (targetDisabled) "تم حفظ إيقاف الكرت محلياً (طابور العمليات)" else "تم حفظ تفعيل الكرت محلياً (طابور العمليات)")
            }
        }
    }

    private var lastBatchTimestamp = 0L

    fun generateBatch(config: BatchGenerationConfig) {
        viewModelScope.launch {
            val result = repository.generateBatch(config, database.appDao())
            if (result.isSuccess) {
                val batchResult = result.getOrNull()
                val cards = batchResult?.successfulCards.orEmpty()
                val failedCards = batchResult?.failedCards.orEmpty()
                val failureReasons = batchResult?.failureReasons.orEmpty()

                val batchName = config.batchComment.ifBlank { "دفعة ${cards.size} كروت" }
                
                if (failedCards.isEmpty()) {
                    recordSystemEvent("إضافة", "دفعة كروت", batchName, "تم توليد وتفعيل جميع الكروت (${cards.size} كرت) للباقة (${config.profile}) في الراوتر", "SUCCESS", "عدد الكروت: ${cards.size}, الباقة: ${config.profile}, البادئة: ${config.prefix}")
                    _toastEvent.emit("تم توليد وتفعيل جميع الكروت (${cards.size} كرت) في الراوتر بنجاح!")
                } else {
                    val firstErr = failureReasons.firstOrNull() ?: "رفض الراوتر بعض الكروت"
                    recordSystemEvent("إضافة", "دفعة كروت جزئية", batchName, "تم توليد ${cards.size} من أصل ${batchResult?.totalRequested ?: config.count} كرت. فشل ${failedCards.size} كرت", "PARTIAL", "الناجح: ${cards.size}, الفاشل: ${failedCards.size}, السبب: $firstErr")
                    _toastEvent.emit("نجاح جزئي: تم توليد وتفعيل ${cards.size} من أصل ${batchResult?.totalRequested ?: config.count} كرت. فشل ${failedCards.size} كرت: $firstErr")
                }

                if (cards.isNotEmpty()) {
                    // Save batch history with all generated card names for independent group separation & tracking
                    val jsonArr = JSONArray()
                    cards.forEach { jsonArr.put(it.name) }
                    val nowTs = System.currentTimeMillis()
                    val timestamp = if (nowTs <= lastBatchTimestamp) lastBatchTimestamp + 1 else nowTs
                    lastBatchTimestamp = timestamp
                    val batchId = "batch_$timestamp"
                    database.appDao().insertBatch(
                        BatchHistoryEntity(
                            batchId = batchId,
                            name = batchName,
                            profileName = config.profile,
                            cardCount = cards.size,
                            price = config.price,
                            createdAt = timestamp,
                            cardsJson = jsonArr.toString()
                        )
                    )
                    
                    // Sync to Cloud
                    syncToCloud("batchHistory", batchId, mapOf(
                        "id" to batchId,
                        "profileName" to config.profile,
                        "count" to cards.size,
                        "timestamp" to com.google.firebase.Timestamp(java.util.Date(timestamp)),
                        "comment" to batchName
                    ))

                    // If assigned to a POS Agent, save assignments
                    if (config.posAgentId > 0 && config.posAgentName.isNotBlank()) {
                        val priceNum = parseBatchPriceValue(config.price).takeIf { it > 0.0 } ?: 200.0
                        val assignments = cards.map { card ->
                            val assignAt = System.currentTimeMillis()
                            val assignmentId = "assign_${card.name}_${assignAt}"
                            
                            // Sync Assignment to Cloud
                            syncToCloud("posCards", assignmentId, mapOf(
                                "id" to assignmentId,
                                "agentId" to config.posAgentId.toString(),
                                "cardName" to card.name,
                                "profileName" to config.profile,
                                "priceAmount" to priceNum,
                                "isSold" to false,
                                "soldAt" to null
                            ))

                            PosCardAssignmentEntity(
                                agentId = config.posAgentId,
                                agentName = config.posAgentName,
                                cardName = card.name,
                                batchId = batchId,
                                profileName = config.profile,
                                priceAmount = priceNum,
                                discountPercent = config.discountPercent,
                                assignedAt = assignAt,
                                isSold = false
                            )
                        }
                        database.appDao().insertCardAssignments(assignments)
                        _toastEvent.emit("تم تخصيص وتسليم ${cards.size} كرت لنقطة (${config.posAgentName}) بنجاح!")
                    }

                    // Set printable cards for instant preview
                    _printableCards.value = cards
                    _selectedPrintableBatchKey.value = batchId

                    // Ensure the newly generated batch is immediately visible in CardsScreen
                    if (_cardFilter.value != CardFilter.ALL &&
                        _cardFilter.value != CardFilter.BATCHES &&
                        _cardFilter.value != CardFilter.UNUSED
                    ) {
                        _cardFilter.value = CardFilter.BATCHES
                    }
                }
            } else {
                recordSystemEvent("إضافة", "دفعة كروت", config.batchComment.ifBlank { "دفعة كروت" }, "فشل توليد دفعة كروت في الراوتر", "FAILED", result.exceptionOrNull()?.message ?: "")
                _toastEvent.emit("فشل توليد الكروت: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun addSingleCard(card: HotspotCard, posAgentId: Long = 0L, posAgentName: String = "", discountPercent: Double = 0.0) {
        viewModelScope.launch {
            _toastEvent.emit("جاري الاتصال بالراوتر والتحقق من باقة User Manager...")
            val result = repository.addSingleCard(card)
            if (result.isSuccess) {
                recordSystemEvent(
                    "إضافة وتفعيل",
                    "كرت",
                    card.name,
                    "تم إنشاء المستخدم وربط الباقة (${card.profile}) وتفعيل الكرت بنجاح في راوتر ميكروتك",
                    "SUCCESS",
                    "الباقة: ${card.profile}, كلمة المرور: ${card.password}, النظام: ${card.systemLabelAr}"
                )
                if (posAgentId > 0L && posAgentName.isNotBlank()) {
                    val priceNum = parseBatchPriceValue(card.price).takeIf { it > 0.0 } ?: 200.0
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
                    _toastEvent.emit("✅ تم إنشاء وتفعيل الكرت (${card.name}) وتخصيصه لنقطة ($posAgentName) بنجاح!")
                } else {
                    _toastEvent.emit("✅ تم إنشاء الكرت (${card.name}) وربط باقة (${card.profile}) وتفعيله بنجاح!")
                }
            } else {
                val err = result.exceptionOrNull()?.message.orEmpty()
                val friendlyMessage = when {
                    err.contains("Read timed out", ignoreCase = true) || err.contains("Timeout", ignoreCase = true) -> {
                        "⚠️ تأخر رد الراوتر أثناء تفعيل الباقة. قام النظام بالتحقق التلقائي لمنع التكرار، يرجى تحديث قائمة الكروت للتحقق."
                    }
                    err.contains("already exists", ignoreCase = true) -> {
                        "⚠️ الكرت (${card.name}) موجود مسبقاً في السيرفر. تم تحديث حالته."
                    }
                    err.contains("الباقة المحددة", ignoreCase = true) -> {
                        "❌ فشل العملية: $err"
                    }
                    err.contains("الاتصال", ignoreCase = true) -> {
                        "❌ تعذر الوصول للراوتر: يرجى فحص الاتصال بالشبكة وإعدادات المنفذ."
                    }
                    else -> "❌ فشل إضافة الكرت: $err"
                }
                recordSystemEvent("إضافة", "كرت", card.name, friendlyMessage, "FAILED", err)
                _toastEvent.emit(friendlyMessage)
            }
        }
    }

    // ==========================================
    // POS Agent Operations
    // ==========================================
    fun addPosAgent(agent: PosAgentEntity) {
        viewModelScope.launch {
            val insertedId = database.appDao().insertPosAgent(agent)
            
            // Sync to Cloud
            syncToCloud("posAgents", insertedId.toString(), mapOf(
                "id" to insertedId.toString(),
                "name" to agent.name,
                "phone" to agent.phone,
                "discountPercent" to agent.discountPercent
            ))
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
            val timestamp = System.currentTimeMillis()
            val payment = PosPaymentEntity(
                agentId = agentId,
                agentName = agentName,
                amount = amount,
                paymentDate = timestamp,
                paymentNote = note.ifBlank { "دفعة نقدية / سداد" }
            )
            val insertedId = database.appDao().insertPosPayment(payment)
            
            // Sync to Cloud
            syncToCloud("posPayments", insertedId.toString(), mapOf(
                "id" to insertedId.toString(),
                "agentId" to agentId.toString(),
                "amount" to amount,
                "timestamp" to com.google.firebase.Timestamp(java.util.Date(timestamp)),
                "note" to note.ifBlank { "دفعة نقدية / سداد" }
            ))
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
                val priceNum = parseBatchPriceValue(card.price).takeIf { it > 0.0 } ?: 200.0
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
            pauseAutoRefreshFor(6000)
            val res = repository.createProfile(profile)
            if (res.isSuccess) {
                recordSystemEvent("إضافة", "باقة", profile.name, "تم إنشاء باقة جديدة (${profile.name}) في راوتر ميكروتك", "SUCCESS", "السرعة: ${profile.rateLimit}, الجلسة: ${profile.sessionTimeout}, الأجهزة: ${profile.sharedUsers}, السعر: ${profile.price}")
                _toastEvent.emit("تم إنشاء باقة: ${profile.name} في الراوتر بنجاح")
            } else {
                recordSystemEvent("إضافة", "باقة", profile.name, "فشل إنشاء باقة (${profile.name}) في الراوتر", "FAILED", res.exceptionOrNull()?.message ?: "")
                _toastEvent.emit(res.exceptionOrNull()?.message ?: "فشل إنشاء الباقة في الراوتر")
            }
        }
    }

    fun updateProfile(profile: HotspotProfile) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            val res = repository.updateProfile(profile)
            if (res.isSuccess) {
                recordSystemEvent("تعديل", "باقة", profile.name, "تم تحديث إعدادات باقة (${profile.name}) في راوتر ميكروتك", "SUCCESS", "السرعة: ${profile.rateLimit}, الجلسة: ${profile.sessionTimeout}, الأجهزة: ${profile.sharedUsers}, السعر: ${profile.price}")
                _toastEvent.emit("تم حفظ وتحديث باقة: ${profile.name} في الراوتر بنجاح")
            } else {
                recordSystemEvent("تعديل", "باقة", profile.name, "فشل تحديث باقة (${profile.name}) في الراوتر", "FAILED", res.exceptionOrNull()?.message ?: "")
                _toastEvent.emit(res.exceptionOrNull()?.message ?: "فشل تحديث الباقة في الراوتر")
            }
        }
    }

    fun deleteProfile(profile: HotspotProfile) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            val res = repository.deleteProfile(profile)
            if (res.isSuccess) {
                recordSystemEvent("حذف", "باقة", profile.name, "تم حذف باقة (${profile.name}) من راوتر ميكروتك", "SUCCESS")
                _toastEvent.emit("تم حذف باقة: ${profile.name} من الراوتر بنجاح")
            } else {
                recordSystemEvent("حذف", "باقة", profile.name, "فشل حذف باقة (${profile.name}) من الراوتر", "FAILED", res.exceptionOrNull()?.message ?: "")
                _toastEvent.emit(res.exceptionOrNull()?.message ?: "فشل حذف الباقة من الراوتر")
            }
        }
    }

    fun syncProfileBetweenSystems(profile: HotspotProfile, targetSystem: String) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            val res = repository.syncProfileBetweenSystems(profile, targetSystem)
            if (res.isSuccess) {
                val targetLabel = if (targetSystem == "hotspot") "الهوتسبوت (Hotspot)" else "اليوزرمانجر (User Manager)"
                recordSystemEvent("مزامنة", "باقة", profile.name, "تمت مزامنة الباقة (${profile.name}) مع $targetLabel بنجاح", "SUCCESS")
                _toastEvent.emit("تمت مزامنة وتثبيت باقة (${profile.name}) مع $targetLabel بنجاح!")
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "فشلت المزامنة"
                recordSystemEvent("مزامنة", "باقة", profile.name, "فشلت مزامنة الباقة (${profile.name})", "FAILED", errorMsg)
                _toastEvent.emit("فشلت المزامنة: $errorMsg")
            }
        }
    }

    fun verifyAndActivateCard(card: HotspotCard) {
        viewModelScope.launch {
            pauseAutoRefreshFor(6000)
            // 1. Instant optimistic update in memory and local DB
            val activated = card.copy(disabled = false)
            repository.updateLocalCard(activated)
            repository.upsertLocalCardPreservingTimestamp(activated)

            // 2. Execute on router in background
            val res = repository.verifyAndActivateCard(card)
            if (res.isSuccess) {
                recordSystemEvent("تفعيل", "كرت", card.name, "تم التحقق وتفعيل الكرت (${card.name}) بالراوتر بنجاح", "SUCCESS")
                _toastEvent.emit("تم التحقق وتفعيل الكرت (${card.name}) في الراوتر بنجاح! جاهز لتسجيل الدخول.")
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "فشل التحقق والتفعيل"
                taskQueueManager.enqueueVerifyAndActivateCard(card)
                recordSystemEvent("طابور العمليات", "كرت", card.name, "تم تفعيل الكرت محلياً وسيتم المزامنة مع الراوتر تلقائياً عند الاتصال.", "PENDING", errorMsg)
                _toastEvent.emit("تم تفعيل الكرت محلياً وسيتم تنفيذه على الراوتر فوراً (طابور العمليات)")
            }
        }
    }

    fun rebootRouter() {
        viewModelScope.launch {
            _toastEvent.emit("جاري إرسال أمر إعادة تشغيل الراوتر...")
            val result = repository.rebootRouter()
            if (result.isSuccess) {
                recordSystemEvent("إعادة تشغيل", "راوتر", "RouterOS", "تم إرسال أمر إعادة تشغيل راوتر ميكروتك بنجاح", "WARNING")
                _toastEvent.emit("تمت إعادة تشغيل الراوتر!")
                disconnect()
            } else {
                recordSystemEvent("إعادة تشغيل", "راوتر", "RouterOS", "فشل أمر إعادة تشغيل الراوتر", "FAILED", result.exceptionOrNull()?.message ?: "")
                _toastEvent.emit("فشل أمر إعادة التشغيل")
            }
        }
    }

    fun setPrintableCards(cardsToPrint: List<HotspotCard>, batchKey: String? = null) {
        _printableCards.value = cardsToPrint
        _selectedPrintableBatchKey.value = batchKey
    }

    fun markBatchAsPrinted(batchKey: String) {
        if (batchKey.isBlank()) return
        val updated = _printedBatchKeys.value + batchKey
        _printedBatchKeys.value = updated
        try {
            printedBatchesPrefs?.edit()?.putStringSet("printed_keys", updated)?.apply()
        } catch (_: Exception) {}
    }

    fun markCurrentBatchAsPrinted() {
        val currentKey = _selectedPrintableBatchKey.value
        if (!currentKey.isNullOrBlank()) {
            markBatchAsPrinted(currentKey)
        }
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

    fun updateNetworkNode(node: com.example.data.model.NetworkNode) {
        viewModelScope.launch {
            repository.updateNetworkNode(node)
            _toastEvent.emit("تم حفظ تعديلات النقطة (${node.name}) بنجاح")
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
            recordSystemEvent("حذف", "دفعة كروت", batchId, "تم حذف سجل الدفعة ($batchId) من قاعدة البيانات", "SUCCESS")
        }
    }

    fun dismissUpdateBanner() {
        _showUpdateBanner.value = false
    }

    fun checkForUpdates(isManual: Boolean = false, context: Context? = null) {
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

                    // Auto-update if it's a minor/light update and context is provided and user has auto-update enabled
                    if (releaseInfo.isMinorUpdate && _autoUpdateMinorReleases.value && context != null && updateDownloadState.value !is UpdateDownloadState.Downloading) {
                        val apkUrl = releaseInfo.apkAsset?.downloadUrl
                        if (!apkUrl.isNullOrBlank()) {
                            _toastEvent.emit("⚡ تم رصد تحديث خفيف وسريع (${releaseInfo.tagName}). جاري التحميل المباشر والتثبيت...")
                            downloadAndInstallUpdate(context, apkUrl, autoLaunchInstall = true)
                            return@launch
                        }
                    }

                    if (isManual) {
                        val msg = if (releaseInfo.isMinorUpdate) {
                            "⚡ يوجد تحديث خفيف ومباشر متاح: ${releaseInfo.tagName}"
                        } else {
                            "يوجد تحديث جديد متاح: ${releaseInfo.tagName}"
                        }
                        _toastEvent.emit(msg)
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
            _toastEvent.emit("جاري فحص وتنزيل أحدث نسخة للبرنامج...")
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
                    downloadAndInstallUpdate(context, apkUrl, autoLaunchInstall = true)
                } else {
                    val directTagUrl = "https://github.com/azamfahd/netmanager-/releases/download/${releaseInfo.tagName}/NetManager-Mikrotik-Manager.apk"
                    _toastEvent.emit("جاري التنزيل المباشر لأحدث حزمة...")
                    downloadAndInstallUpdate(context, directTagUrl, autoLaunchInstall = true)
                }
            }.onFailure { err ->
                val directLatestUrl = "https://github.com/azamfahd/netmanager-/releases/latest/download/NetManager-Mikrotik-Manager.apk"
                _toastEvent.emit("جاري التنزيل المباشر لأحدث إصدار رسمي...")
                downloadAndInstallUpdate(context, directLatestUrl, autoLaunchInstall = true)
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, autoLaunchInstall: Boolean = true) {
        _showUpdateBanner.value = true
        _toastEvent.tryEmit("⚡ جاري تنزيل التحديث في الخلفية...")
        com.example.data.util.AppUpdateService.start(context, downloadUrl)
    }



    fun installDownloadedApk(context: Context, apkFile: java.io.File) {
        val result = AppUpdateManager.installApk(context, apkFile)
        result.onFailure { err ->
            viewModelScope.launch {
                _toastEvent.emit(err.localizedMessage ?: "تعذر فتح ملف التثبيت")
            }
        }
    }

    fun getDatabaseStats(onResult: (Map<String, Int>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dao = database.appDao()
                val stats = mapOf(
                    "جلسات الأرشيف" to dao.getSessionArchiveCount(),
                    "سجلات الأحداث" to dao.getSystemEventLogCount(),
                    "سجلات الأخطاء" to dao.getRouterErrorLogCount(),
                    "تعيينات الكروت" to dao.getCardAssignmentCount(),
                    "مدفوعات الوكلاء" to dao.getPosPaymentCount()
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(stats)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(emptyMap())
                }
            }
        }
    }

    init {
        repository.onDataRefreshedListener = { snapshot ->
            val ctx = appContext
            if (ctx != null) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val hasMeaningfulData = snapshot.cards.isNotEmpty() ||
                        snapshot.activeUsers.isNotEmpty() ||
                        snapshot.nodes.isNotEmpty() ||
                        snapshot.profiles.isNotEmpty()
                    if (hasMeaningfulData) {
                        val nowMs = System.currentTimeMillis()
                        val sig = "${snapshot.ipAddress}|${snapshot.cards.size}|${snapshot.profiles.size}|${snapshot.activeUsers.size}|${snapshot.nodes.size}"
                        if (sig != lastSnapshotSignature || (nowMs - lastSnapshotSaveTimeMs) >= 30_000L) {
                            lastSnapshotSignature = sig
                            lastSnapshotSaveTimeMs = nowMs
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

                    // Persist only unseen logs to Room & Cloud so 8s refreshes do not flood DB/Firestore
                    val dao = database.appDao()
                    snapshot.logs.forEach { log ->
                        val logKey = "${log.time}|${log.topics}|${log.message}"
                        val isNewLog = synchronized(seenRouterLogKeys) {
                            if (seenRouterLogKeys.size > 500) {
                                val first = seenRouterLogKeys.firstOrNull()
                                if (first != null) seenRouterLogKeys.remove(first)
                            }
                            seenRouterLogKeys.add(logKey)
                        }
                        if (isNewLog && log.message.isNotBlank()) {
                            val timestamp = System.currentTimeMillis()
                            val insertedId = dao.insertSystemEventLog(
                                SystemEventLogEntity(
                                    actionType = if (log.topics.contains("firewall")) "جدار الحماية" else "أحداث النظام",
                                    targetType = "راوتر",
                                    targetName = "سجل خارجي",
                                    description = log.message,
                                    details = "Topic: ${log.topics}",
                                    timestamp = timestamp
                                )
                            )

                            syncToCloud("systemLogs", insertedId.toString(), mapOf(
                                "id" to insertedId.toString(),
                                "timestamp" to com.google.firebase.Timestamp(java.util.Date(timestamp)),
                                "actionType" to (if (log.topics.contains("firewall")) "جدار الحماية" else "أحداث النظام"),
                                "targetType" to "راوتر",
                                "targetName" to "سجل خارجي",
                                "description" to log.message,
                                "status" to "INFO",
                                "operatorUsername" to "system"
                            ))
                        }
                    }
                }
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Remove legacy synthetic sess_card_* archive snapshots that poisoned historical months
                database.appDao().deleteLegacyCardSnapshotSessions()
            } catch (_: Exception) {}
            repository.initializeFromLocalDatabase()
            val ctx = appContext
            if (ctx != null) {
                try {
                    val latest = com.example.data.local.RouterCacheManager.getLatestSnapshot(ctx)
                    if (latest != null) {
                        _latestCachedSnapshot.value = latest
                        if (repository.cards.value.isEmpty()) {
                            repository.restoreFromSnapshot(latest)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 15-Minute Periodic Background Sync Worker
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                delay(15 * 60 * 1000L) // 15 minutes
                if (_connectionState.value is ConnectionState.Connected) {
                    try {
                        repository.refreshData()
                        runHeartbeatCheck(appContext)
                        Log.d("MainViewModel", "Background incremental sync completed successfully.")
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Background sync failed: ${e.message}")
                    }
                }
            }
        }

        // Materialized Aggregates Persistence Collector
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            revenueAnalytics.collectLatest { summary ->
                try {
                    val dao = database.appDao()
                    dao.insertOrUpdateMaterializedSummary(
                        com.example.data.local.MaterializedSummaryEntity(
                            id = "global_summary",
                            routerIp = (_connectionState.value as? ConnectionState.Connected)?.ipAddress ?: "192.168.88.1",
                            totalEstimatedRevenue = summary.totalEstimatedRevenue,
                            totalNetProfit = summary.totalNetProfit,
                            activeCardsCount = summary.activeCardsCount,
                            unusedCardsCount = summary.unusedCardsCount,
                            expiredCardsCount = summary.expiredCardsCount,
                            totalCardsCount = summary.totalCardsCount,
                            totalActiveUsers = activeUsers.value.size,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}
            }
        }

        // Auth-Gated Sequential Firestore Finance Sync & Real-Time Observer
        viewModelScope.launch(Dispatchers.IO) {
            authStateFlow()
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid.isNullOrBlank()) {
                        _financeCloudSyncStatus.value = FinanceCloudSyncStatus.SignedOut
                        return@collectLatest
                    }
                    _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Syncing
                    syncPackagePricingsFromCloud(uid)

                    val initialFetch = financeFirestoreRepository.fetchMonthlyProfitsSequentially(uid)
                    initialFetch.onSuccess { res ->
                        applyValidatedCloudMonthlyFetch(res)
                    }

                    val currentLocal = monthlySalesList.value
                    if (currentLocal.isNotEmpty()) {
                        financeFirestoreRepository.upsertMonthlyProfitsValidated(currentLocal)
                    }

                    financeFirestoreRepository.observeMonthlyProfits(uid)
                        .catch { err ->
                            _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Error(
                                err.message ?: "تعذرت قراءة الأرباح السحابية"
                            )
                        }
                        .collectLatest { liveFetch ->
                            applyValidatedCloudMonthlyFetch(liveFetch)
                            _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Synced(
                                syncedMonthsCount = liveFetch.entities.size,
                                skippedInvalidCount = liveFetch.rejectedDocsCount
                            )
                        }
                }
        }

        // Monthly Profit Records Persistence Collector (Room + Validated Cloud Upsert, loop-free & zero-clobber guarded)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            monthlySalesList
                .collectLatest { months ->
                    if (months.isEmpty()) return@collectLatest
                    try {
                        val existingByKey = database.appDao().getAllMonthlyProfitRecordsSync().associateBy { it.monthKey }
                        val ts = System.currentTimeMillis()
                        val entities = months.mapNotNull { m ->
                            val isZero = m.totalRevenue == 0.0 && m.totalCardsSold == 0 && m.totalCardsGenerated == 0 && m.totalBytesConsumed == 0L
                            val prev = existingByKey[m.monthKey]
                            val prevHasData = prev != null && (prev.totalRevenue > 0.0 || prev.soldCardsCount > 0 || prev.totalCardsCount > 0 || prev.totalBytesConsumed > 0L)
                            if (isZero && prevHasData) {
                                null
                            } else {
                                val yearInt = m.monthKey.substringBefore("-").toIntOrNull() ?: 2026
                                val monthInt = m.monthKey.substringAfter("-").toIntOrNull() ?: 1
                                val topPkg = m.packageBreakdown.firstOrNull()?.packageName ?: ""
                                val unchanged = prev != null &&
                                    prev.monthNameAr == m.monthName &&
                                    kotlin.math.abs(prev.totalRevenue - m.totalRevenue) < 0.001 &&
                                    kotlin.math.abs(prev.totalCost - m.totalCost) < 0.001 &&
                                    kotlin.math.abs(prev.netProfit - m.netProfit) < 0.001 &&
                                    prev.soldCardsCount == m.totalCardsSold &&
                                    prev.totalCardsCount == m.totalCardsGenerated &&
                                    prev.topSellingProfile == topPkg &&
                                    prev.totalBytesConsumed == m.totalBytesConsumed
                                if (unchanged) {
                                    null
                                } else {
                                    com.example.data.local.MonthlyProfitRecordEntity(
                                        monthKey = m.monthKey,
                                        monthNameAr = m.monthName,
                                        year = yearInt,
                                        month = monthInt,
                                        totalRevenue = m.totalRevenue,
                                        totalCost = m.totalCost,
                                        netProfit = m.netProfit,
                                        soldCardsCount = m.totalCardsSold,
                                        totalCardsCount = m.totalCardsGenerated,
                                        topSellingProfile = topPkg,
                                        totalBytesConsumed = m.totalBytesConsumed,
                                        lastUpdatedTimestamp = ts
                                    )
                                }
                            }
                        }
                        if (entities.isNotEmpty()) {
                            database.appDao().insertOrUpdateMonthlyProfitRecords(entities)
                        }

                        if (!getUserId().isNullOrBlank()) {
                            financeFirestoreRepository.upsertMonthlyProfitsValidated(months)
                                .onSuccess { report ->
                                    if (report.totalWrittenCount > 0) {
                                        val meaningfulCount = months.count {
                                            it.totalRevenue > 0.0 || it.totalCardsSold > 0 || it.totalCardsGenerated > 0 || it.totalBytesConsumed > 0L
                                        }
                                        _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Synced(
                                            syncedMonthsCount = meaningfulCount,
                                            skippedInvalidCount = report.rejectedValidationCount
                                        )
                                    }
                                }
                                .onFailure { err ->
                                    _financeCloudSyncStatus.value = FinanceCloudSyncStatus.Error(
                                        err.message ?: "تعذرت مزامنة الأرباح السحابية"
                                    )
                                }
                        }
                    } catch (_: Exception) {}
                }
        }

        viewModelScope.launch {
            delay(2500)
            checkForUpdates(isManual = false)
        }

        viewModelScope.launch {
            cards.collectLatest { cardList ->
                checkAndMarkSoldCards(cardList)
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            activeUsers.collectLatest { activeList ->
                val now = System.currentTimeMillis()
                val nowFormatted = com.example.data.network.NetworkUtils.formatArabicFullDateTime(now)
                val currentCards = cards.value
                val dao = database.appDao()

                if (activeList.isNotEmpty()) {
                    try {
                        val unsoldAssigned = dao.getUnsoldAssignedCardNamesSync().toSet()
                        val newlySoldActive = activeList.map { it.user }.filter { unsoldAssigned.contains(it) }
                        if (newlySoldActive.isNotEmpty()) {
                            newlySoldActive.chunked(400).forEach { chunk ->
                                dao.markCardsAsSoldBatch(chunk, now)
                            }
                            val soldTs = com.google.firebase.Timestamp(java.util.Date(now))
                            newlySoldActive.forEach { cardName ->
                                syncToCloud("posCards", cardName, mapOf(
                                    "isSold" to true,
                                    "soldAt" to soldTs
                                ))
                            }
                        }
                    } catch (_: Exception) {}

                    activeList.forEach { user ->
                        // Persistent subscriber login tracking (تاريخ تسجيل الدخول والأجهزة والأوقات)
                        try {
                            val activeLogin = dao.getActiveLoginForUser(user.user)
                            val card = currentCards.firstOrNull { it.name == user.user }
                            val cardProf = card?.profile?.ifBlank { "افتراضي" } ?: "افتراضي"
                            val cardPrice = card?.price?.ifBlank { "غير محدد" } ?: "غير محدد"
                            val quotaLimitBytes = card?.limitBytesTotal ?: 0L
                            val detectedDevice = com.example.data.util.DeviceDetector.detectDevice(user.macAddress, user.hostName, user.user)

                            if (activeLogin == null) {
                                val loginEntity = com.example.data.local.UserLoginHistoryEntity(
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
                                    packageLimitBytes = quotaLimitBytes,
                                    timestamp = now
                                )
                                val insertedId = dao.insertLoginHistory(loginEntity)

                                syncToCloud("loginHistory", insertedId.toString(), mapOf(
                                    "id" to insertedId.toString(),
                                    "username" to user.user,
                                    "ipAddress" to user.address,
                                    "macAddress" to user.macAddress,
                                    "timestamp" to com.google.firebase.Timestamp(java.util.Date(now)),
                                    "profile" to cardProf
                                ))
                            } else {
                                val statsSig = "${user.bytesIn}|${user.bytesOut}|${user.uptime}"
                                if (lastActiveLoginStatsCache[user.user] != statsSig) {
                                    lastActiveLoginStatsCache[user.user] = statsSig
                                    dao.updateActiveLoginStats(
                                        username = user.user,
                                        bytesIn = user.bytesIn,
                                        bytesOut = user.bytesOut,
                                        totalBytes = user.bytesIn + user.bytesOut,
                                        duration = user.uptime.ifBlank { "0s" }
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Detect users who disconnected since last check ONLY when actively online (never during offline drops)
                val isOnlineConnected = _connectionState.value.let { it is ConnectionState.Connected && !it.isOffline }
                val currentNames = activeList.map { it.user }.toSet()
                if (isOnlineConnected && previousActiveUsernames.isNotEmpty()) {
                    val disconnectedNames = previousActiveUsernames - currentNames
                    if (disconnectedNames.isNotEmpty()) {
                        val logoutTimeFormatted = com.example.data.network.NetworkUtils.formatArabicFullDateTime(System.currentTimeMillis())
                        disconnectedNames.forEach { discoName ->
                            lastActiveLoginStatsCache.remove(discoName)
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
                                dao.markSessionOffline(targetSessionId, logoutTimeFormatted, reason, isQuotaOver)

                                val bytesIn = matchingCard?.bytesIn ?: 0L
                                val bytesOut = matchingCard?.bytesOut ?: 0L
                                val duration = matchingCard?.uptime ?: "0s"
                                val telemetry = repository.getLastKnownTelemetryForUser(discoName)
                                dao.finalizeUserLogin(
                                    username = discoName,
                                    logoutTime = logoutTimeFormatted,
                                    duration = duration,
                                    bytesIn = bytesIn,
                                    bytesOut = bytesOut,
                                    totalBytes = bytesIn + bytesOut,
                                    reason = reason,
                                    ipAddress = telemetry?.ipAddress ?: "",
                                    macAddress = telemetry?.macAddress ?: "",
                                    deviceModel = telemetry?.deviceInfo?.modelName ?: "",
                                    deviceBrand = telemetry?.deviceInfo?.brand ?: "",
                                    deviceCategory = telemetry?.deviceInfo?.category?.name ?: "",
                                    nodeName = telemetry?.nodeName ?: ""
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
                if (isOnlineConnected || activeList.isNotEmpty()) {
                    previousActiveUsernames = currentNames
                }
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            userSessions.collectLatest { sessionList ->
                // Only archive real sessions (never overwrite history with synthetic sess_card_* inventory rows)
                val realSessions = sessionList.filter { !it.id.startsWith("sess_card_") }
                if (realSessions.isNotEmpty()) {
                    try {
                        val now = System.currentTimeMillis()
                        val changedEntities = mutableListOf<com.example.data.local.SessionArchiveEntity>()
                        for (session in realSessions) {
                            val sig = "${session.isOnline}|${session.duration}|${session.totalBytes}|${session.logoutTime}"
                            if (lastArchivedSessionSignatures[session.id] == sig) continue
                            lastArchivedSessionSignatures[session.id] = sig

                            val entity = com.example.data.local.SessionArchiveEntity(
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
                                timestamp = now
                            )
                            changedEntities.add(entity)

                            syncToCloud("sessionArchive", session.id, mapOf(
                                "id" to session.id,
                                "username" to session.username,
                                "macAddress" to session.macAddress,
                                "ipAddress" to session.ipAddress,
                                "loginTime" to session.loginTime,
                                "logoutTime" to session.logoutTime,
                                "duration" to session.duration,
                                "totalBytes" to session.totalBytes,
                                "timestamp" to com.google.firebase.Timestamp(java.util.Date(now))
                            ))
                        }
                        if (changedEntities.isNotEmpty()) {
                            database.appDao().insertOrUpdateSessions(changedEntities)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        viewModelScope.launch {
            seedInitialPosDataIfEmpty()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }

    companion object {
        internal fun parseBatchPriceValue(priceStr: String): Double {
            return FinanceAnalyticsEngine.parsePriceValue(priceStr)
        }

        internal fun partitionCardsIntoIndependentBatches(
            allCards: List<HotspotCard>,
            activeUserNamesLower: Set<String>,
            batches: List<BatchHistoryEntity>,
            printedKeys: Set<String>,
            timestampProvider: (HotspotCard) -> Long
        ): IndependentBatchPartitionResult {
            val distinctCards = allCards.distinctBy { it.name.lowercase() }
            if (distinctCards.isEmpty()) {
                return IndependentBatchPartitionResult()
            }

            fun isCardReady(card: HotspotCard): Boolean {
                val lower = card.name.lowercase()
                return !activeUserNamesLower.contains(lower) &&
                    (card.uptime.isBlank() || card.uptime == "0s") &&
                    card.bytesIn == 0L && card.bytesOut == 0L && !card.isExpired
            }

            fun computeBatchGroup(
                batchKey: String,
                title: String,
                profile: String,
                price: String,
                timestamp: Long,
                formattedDate: String,
                cardsInBatch: List<HotspotCard>,
                expectedCount: Int,
                batchNumber: Int
            ): Pair<CardBatchGroup, com.example.data.model.PrintableBatchOption> {
                val normalized = cardsInBatch.map { card ->
                    if (card.price.isBlank() && price.isNotBlank()) card.copy(price = price) else card
                }
                val readyList = normalized.filter(::isCardReady)
                var activeCnt = 0
                var usedOfflineCnt = 0
                var expiredCnt = 0
                for (c in normalized) {
                    val lower = c.name.lowercase()
                    when {
                        c.isExpired -> expiredCnt++
                        activeUserNamesLower.contains(lower) -> activeCnt++
                        !isCardReady(c) -> usedOfflineCnt++
                    }
                }
                val totalVal = normalized.sumOf { parseBatchPriceValue(it.price.ifBlank { price }) }
                val isPrinted = printedKeys.contains(batchKey)
                val firstCard = normalized.first()
                val resolvedProfile = profile.ifBlank { firstCard.profile }
                val resolvedPrice = price.ifBlank { firstCard.price }
                val hasUm = normalized.any { it.isUserManagerCard }
                val hasHs = normalized.any { it.isHotspotCard }
                val sysLabel = when {
                    hasUm && hasHs -> "مختلط"
                    hasUm -> "يوزرمانجر"
                    else -> "هوتسبوت"
                }

                val group = CardBatchGroup(
                    batchKey = batchKey,
                    title = title,
                    profile = resolvedProfile,
                    price = resolvedPrice,
                    timestamp = timestamp,
                    formattedDate = formattedDate,
                    totalValue = totalVal,
                    cards = normalized,
                    allBatchCards = normalized,
                    readyCards = readyList,
                    totalGeneratedCount = maxOf(expectedCount, normalized.size),
                    readyCount = readyList.size,
                    activeCount = activeCnt,
                    usedOfflineCount = usedOfflineCnt,
                    expiredCount = expiredCnt,
                    isPrintedBefore = isPrinted,
                    batchNumber = batchNumber,
                    sourceSystemLabel = sysLabel
                )
                val printOption = com.example.data.model.PrintableBatchOption(
                    batchKey = batchKey,
                    title = if (batchNumber > 0) "#$batchNumber • $title" else title,
                    profileName = resolvedProfile,
                    price = resolvedPrice,
                    timestamp = timestamp,
                    formattedDate = formattedDate,
                    readyCards = readyList,
                    allCardsInBatch = normalized,
                    isPrintedBefore = isPrinted,
                    isNewlyAdded = !isPrinted && readyList.isNotEmpty()
                )
                return group to printOption
            }

            // Pre-parse explicit card sets for each saved BatchHistoryEntity
            val parsedNamesByBatchId = mutableMapOf<String, Set<String>>()
            val allExplicitSavedNames = mutableSetOf<String>()
            batches.forEach { batch ->
                val names = mutableSetOf<String>()
                if (batch.cardsJson.isNotBlank()) {
                    try {
                        val arr = JSONArray(batch.cardsJson)
                        for (i in 0 until arr.length()) {
                            val n = arr.optString(i, "").trim().lowercase()
                            if (n.isNotBlank()) {
                                names.add(n)
                                allExplicitSavedNames.add(n)
                            }
                        }
                    } catch (_: Exception) {}
                }
                parsedNamesByBatchId[batch.batchId] = names
            }

            val batchNumberById = batches
                .sortedWith(compareBy<BatchHistoryEntity> { it.createdAt }.thenBy { it.batchId })
                .mapIndexed { idx, batch -> batch.batchId to (idx + 1) }
                .toMap()

            val consumedNames = mutableSetOf<String>()
            val batchGroups = mutableListOf<CardBatchGroup>()
            val printOptions = mutableListOf<com.example.data.model.PrintableBatchOption>()

            // 1. Strictly isolate every saved BatchHistoryEntity into its own independent group
            batches.sortedWith(
                compareByDescending<BatchHistoryEntity> { it.createdAt }
                    .thenByDescending { it.batchId }
            ).forEach { batch ->
                val savedNames = parsedNamesByBatchId[batch.batchId].orEmpty()
                val directMatches = distinctCards.filter {
                    val lower = it.name.lowercase()
                    !consumedNames.contains(lower) && savedNames.contains(lower)
                }.toMutableList()

                // Fallback strictly for legacy DB entries where cardsJson was truncated to 10 names
                if (savedNames.size < batch.cardCount && directMatches.size < batch.cardCount) {
                    val needed = (batch.cardCount - directMatches.size).coerceAtLeast(0)
                    val extraMatches = distinctCards.filter { card ->
                        val lower = card.name.lowercase()
                        if (consumedNames.contains(lower) || savedNames.contains(lower)) return@filter false
                        // Never steal cards explicitly recorded under another batch
                        if (allExplicitSavedNames.contains(lower)) return@filter false
                        val matchesCommentAndProfile = batch.name.isNotBlank() &&
                            card.comment.equals(batch.name, ignoreCase = true) &&
                            card.profile.equals(batch.profileName, ignoreCase = true)
                        val cardTs = timestampProvider(card)
                        val matchesTimestampAndProfile = cardTs > 0L &&
                            kotlin.math.abs(cardTs - batch.createdAt) <= 120_000L &&
                            card.profile.equals(batch.profileName, ignoreCase = true)
                        matchesCommentAndProfile || matchesTimestampAndProfile
                    }.take(needed)
                    directMatches.addAll(extraMatches)
                }

                if (directMatches.isNotEmpty()) {
                    directMatches.forEach { consumedNames.add(it.name.lowercase()) }
                    val formattedDate = if (batch.createdAt > 0L) {
                        NetworkUtils.formatArabicFullDateTime(batch.createdAt)
                    } else {
                        "دفعة مسجلة"
                    }
                    val title = batch.name.ifBlank { "دفعة (${batch.profileName})" }
                    val (group, opt) = computeBatchGroup(
                        batchKey = batch.batchId,
                        title = title,
                        profile = batch.profileName,
                        price = batch.price,
                        timestamp = batch.createdAt,
                        formattedDate = formattedDate,
                        cardsInBatch = directMatches,
                        expectedCount = batch.cardCount,
                        batchNumber = batchNumberById[batch.batchId] ?: 0
                    )
                    batchGroups.add(group)
                    printOptions.add(opt)
                }
            }

            // 2. Group remaining batch-like router cards WITHOUT merging separate batches generated at different times
            val remainingCards = distinctCards.filter { !consumedNames.contains(it.name.lowercase()) }
            val commentCounts = remainingCards.groupingBy { it.comment.trim() }.eachCount()
            val remainingBatchCards = remainingCards.filter { card ->
                val c = card.comment.lowercase()
                when {
                    c.contains("فردي") -> false
                    c.contains("دفعة") || c.contains("batch") -> true
                    card.id.startsWith("gen_") -> true
                    card.comment.isNotBlank() && (commentCounts[card.comment.trim()] ?: 0) > 1 -> true
                    else -> false
                }
            }

            // Partition into gen_-timestamped batches vs time-clustered router groups
            val routerClusters = linkedMapOf<String, List<HotspotCard>>()
            val (genCards, nonGenCards) = remainingBatchCards.partition {
                it.id.startsWith("gen_") && (it.id.split("_").getOrNull(1)?.toLongOrNull() != null)
            }

            genCards.groupBy { card ->
                val genTs = card.id.split("_").getOrNull(1)?.toLongOrNull() ?: 0L
                val bucket = genTs / 5_000L
                "router_gen_${bucket}_${card.profile.trim().lowercase()}"
            }.forEach { (k, list) ->
                routerClusters[k] = list
            }

            nonGenCards.groupBy { card ->
                card.comment.trim().lowercase() to card.profile.trim().lowercase()
            }.forEach { (pairKey, cardsInPair) ->
                val sortedByTime = cardsInPair.sortedBy { timestampProvider(it) }
                var clusterIdx = 0
                var currentCluster = mutableListOf<HotspotCard>()
                var prevTs = 0L
                for (card in sortedByTime) {
                    val ts = timestampProvider(card)
                    if (currentCluster.isNotEmpty() && prevTs > 0L && ts > 0L && kotlin.math.abs(ts - prevTs) > 120_000L) {
                        val clusterKey = "router_grp_${pairKey.first.hashCode()}_${pairKey.second.hashCode()}_$clusterIdx"
                        routerClusters[clusterKey] = currentCluster
                        clusterIdx++
                        currentCluster = mutableListOf()
                    }
                    currentCluster.add(card)
                    if (ts > 0L) prevTs = ts
                }
                if (currentCluster.isNotEmpty()) {
                    val clusterKey = "router_grp_${pairKey.first.hashCode()}_${pairKey.second.hashCode()}_$clusterIdx"
                    routerClusters[clusterKey] = currentCluster
                }
            }

            routerClusters.forEach { (key, cardsInGroup) ->
                cardsInGroup.forEach { consumedNames.add(it.name.lowercase()) }
                val first = cardsInGroup.first()
                val ts = cardsInGroup.maxOfOrNull { timestampProvider(it) } ?: 0L
                val formattedDate = if (ts > 0L) NetworkUtils.formatArabicFullDateTime(ts) else "تاريخ محفوظ في الراوتر"
                val title = when {
                    first.comment.isNotBlank() && first.comment != "دفعة كروت" -> first.comment
                    first.price.isNotBlank() -> "مجموعة (${first.profile}) - ${first.price}"
                    else -> "مجموعة (${first.profile})"
                }
                val (group, opt) = computeBatchGroup(
                    batchKey = key,
                    title = title,
                    profile = first.profile,
                    price = first.price,
                    timestamp = ts,
                    formattedDate = formattedDate,
                    cardsInBatch = cardsInGroup,
                    expectedCount = cardsInGroup.size,
                    batchNumber = 0
                )
                batchGroups.add(group)
                printOptions.add(opt)
            }

            val ungrouped = distinctCards.filter { !consumedNames.contains(it.name.lowercase()) }
            val sortedGroups = batchGroups.sortedWith(
                compareByDescending<CardBatchGroup> { it.timestamp }
                    .thenByDescending { it.batchNumber }
                    .thenByDescending { it.batchKey }
            )
            val sortedPrintOptions = printOptions.sortedWith(
                compareByDescending<com.example.data.model.PrintableBatchOption> { it.isNewlyAdded }
                    .thenByDescending { it.timestamp }
            )
            return IndependentBatchPartitionResult(
                batchGroups = sortedGroups,
                ungroupedCards = ungrouped,
                printableOptions = sortedPrintOptions
            )
        }
    }
}

data class IndependentBatchPartitionResult(
    val batchGroups: List<CardBatchGroup> = emptyList(),
    val ungroupedCards: List<HotspotCard> = emptyList(),
    val printableOptions: List<com.example.data.model.PrintableBatchOption> = emptyList()
)

class MainViewModelFactory(
    private val repository: MikrotikRepository,
    private val database: AppDatabase,
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, database, db, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
