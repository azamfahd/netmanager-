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
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.ConnectionState
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.IpBinding
import com.example.data.model.LocalNetworkInfo
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.RouterResource
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
    EXPIRED
}

class MainViewModel(
    private val repository: MikrotikRepository,
    private val database: AppDatabase
) : ViewModel() {

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
    val ipBindings: StateFlow<List<IpBinding>> = repository.ipBindings
    val networkNodes: StateFlow<List<com.example.data.model.NetworkNode>> = repository.networkNodes
    val userSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = repository.userSessions
    val activityEvents: StateFlow<List<com.example.data.model.UserActivityEvent>> = repository.activityEvents

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

    init {
        viewModelScope.launch {
            cards.collectLatest { cardList ->
                checkAndMarkSoldCards(cardList)
            }
        }
        viewModelScope.launch {
            activeUsers.collectLatest { activeList ->
                if (activeList.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    activeList.forEach { user ->
                        database.appDao().markCardAsSold(user.user, now)
                    }
                }
            }
        }
        viewModelScope.launch {
            seedInitialPosDataIfEmpty()
        }
    }

    private suspend fun seedInitialPosDataIfEmpty() {
        try {
            val existingAgents = database.appDao().getPosAgentById(1L)
            if (existingAgents == null) {
                val agent1Id = database.appDao().insertPosAgent(
                    PosAgentEntity(
                        name = "بقالة ياسين",
                        phone = "771234567",
                        location = "شارع جمال - بجانب المستوصف",
                        discountPercent = 15.0,
                        notes = "نقطة بيع رئيسية"
                    )
                )
                val agent2Id = database.appDao().insertPosAgent(
                    PosAgentEntity(
                        name = "مكتبة الأمل",
                        phone = "734567890",
                        location = "الحارة الشرقية - أمام المدرسة",
                        discountPercent = 12.0,
                        notes = "بائع معتمد"
                    )
                )
                val agent3Id = database.appDao().insertPosAgent(
                    PosAgentEntity(
                        name = "محمد (موزع الحارة الغربية)",
                        phone = "778901234",
                        location = "الحارة الغربية",
                        discountPercent = 15.0,
                        notes = "موزع كروت متجول"
                    )
                )

                // Assign initial sample cards to agents
                val sampleAssignments = listOf(
                    PosCardAssignmentEntity(agentId = agent1Id, agentName = "بقالة ياسين", cardName = "784912", profileName = "فئة 500", priceAmount = 500.0, discountPercent = 15.0, isSold = true, soldAt = System.currentTimeMillis() - 7200000),
                    PosCardAssignmentEntity(agentId = agent1Id, agentName = "بقالة ياسين", cardName = "319405", profileName = "فئة 200", priceAmount = 200.0, discountPercent = 15.0, isSold = true, soldAt = System.currentTimeMillis() - 3600000),
                    PosCardAssignmentEntity(agentId = agent1Id, agentName = "بقالة ياسين", cardName = "849201", profileName = "فئة 2000", priceAmount = 2000.0, discountPercent = 15.0, isSold = true, soldAt = System.currentTimeMillis() - 86400000),
                    PosCardAssignmentEntity(agentId = agent1Id, agentName = "بقالة ياسين", cardName = "510938", profileName = "فئة 500", priceAmount = 500.0, discountPercent = 15.0, isSold = false),
                    PosCardAssignmentEntity(agentId = agent1Id, agentName = "بقالة ياسين", cardName = "928341", profileName = "فئة 500", priceAmount = 500.0, discountPercent = 15.0, isSold = false),
                    PosCardAssignmentEntity(agentId = agent2Id, agentName = "مكتبة الأمل", cardName = "651829", profileName = "فئة 500", priceAmount = 500.0, discountPercent = 12.0, isSold = true, soldAt = System.currentTimeMillis() - 18000000),
                    PosCardAssignmentEntity(agentId = agent2Id, agentName = "مكتبة الأمل", cardName = "401928", profileName = "فئة 300", priceAmount = 300.0, discountPercent = 12.0, isSold = false),
                    PosCardAssignmentEntity(agentId = agent2Id, agentName = "مكتبة الأمل", cardName = "719304", profileName = "فئة 300", priceAmount = 300.0, discountPercent = 12.0, isSold = false),
                    PosCardAssignmentEntity(agentId = agent3Id, agentName = "محمد (موزع الحارة الغربية)", cardName = "390124", profileName = "فئة 200", priceAmount = 200.0, discountPercent = 15.0, isSold = true, soldAt = System.currentTimeMillis() - 3600000),
                    PosCardAssignmentEntity(agentId = agent3Id, agentName = "محمد (موزع الحارة الغربية)", cardName = "882910", profileName = "فئة 200", priceAmount = 200.0, discountPercent = 15.0, isSold = false)
                )
                database.appDao().insertCardAssignments(sampleAssignments)

                // Record initial sample payments received
                database.appDao().insertPosPayment(
                    PosPaymentEntity(
                        agentId = agent1Id,
                        agentName = "بقالة ياسين",
                        amount = 1500.0,
                        paymentDate = System.currentTimeMillis() - 86400000,
                        paymentNote = "دفعة نقدية سابقة"
                    )
                )
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

    private val _sessionFilter = MutableStateFlow("ALL") // ALL, ONLINE, OFFLINE, LOGS
    val sessionFilter: StateFlow<String> = _sessionFilter.asStateFlow()

    val filteredUserSessions: StateFlow<List<com.example.data.model.UserSessionRecord>> = combine(
        userSessions, _sessionSearchQuery, _sessionFilter
    ) { sessions, query, filter ->
        sessions.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.ipAddress.contains(query, ignoreCase = true) ||
                    item.macAddress.contains(query, ignoreCase = true) ||
                    item.deviceInfo.modelName.contains(query, ignoreCase = true) ||
                    item.deviceInfo.brand.contains(query, ignoreCase = true) ||
                    item.comment.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                "ONLINE" -> item.isOnline
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

    fun connectToRouter(
        config: RouterConnectionConfig,
        saveToFavorites: Boolean = true
    ) {
        viewModelScope.launch {
            _currentConfig.value = config
            _connectionState.value = ConnectionState.Verifying("التحقق من الاتصال بالشبكة المحلية...", 0.25f)
            delay(350)

            _connectionState.value = ConnectionState.Verifying("فحص عنوان IP واستجابة الراوتر (${config.ipAddress})...", 0.50f)
            delay(350)

            _connectionState.value = ConnectionState.Verifying("التحقق من اسم المستخدم وكلمة المرور...", 0.75f)

            val result = repository.verifyAndConnect(config)
            if (result.isSuccess) {
                _connectionState.value = ConnectionState.Connected(
                    routerName = config.name.ifBlank { "راوتر MikroTik" },
                    ipAddress = config.ipAddress,
                    isDemo = config.isDemoMode
                )
                _toastEvent.emit(if (config.isDemoMode) "تم الدخول في وضع العرض التفاعلي" else "تم الاتصال بالراوتر بنجاح!")

                if (saveToFavorites && !config.isDemoMode) {
                    database.appDao().insertSavedRouter(
                        SavedRouterEntity(
                            name = config.name.ifBlank { "راوتر ${config.ipAddress}" },
                            ipAddress = config.ipAddress,
                            port = config.port,
                            username = config.username,
                            password = config.password,
                            useHttps = config.useHttps,
                            lastConnectedTime = System.currentTimeMillis()
                        )
                    )
                }
                startAutoRefresh()
            } else {
                val error = result.exceptionOrNull()?.message ?: "فشل الاتصال بالراوتر"
                _connectionState.value = ConnectionState.Error(error)
                _toastEvent.emit(error)
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

    private fun startAutoRefresh() {
        stopAutoRefresh()
        refreshJob = viewModelScope.launch {
            while (isActive && _autoRefresh.value) {
                delay(4000)
                if (_connectionState.value is ConnectionState.Connected) {
                    repository.refreshData()
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
            val result = repository.reactivateCard(card.id)
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

    fun addSingleCard(card: HotspotCard) {
        viewModelScope.launch {
            val result = repository.addSingleCard(card)
            if (result.isSuccess) {
                _toastEvent.emit("تمت إضافة وتفعيل الكرت (${card.name}) بنجاح!")
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

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }
}

class MainViewModelFactory(
    private val repository: MikrotikRepository,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
