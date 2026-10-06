package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BatchHistoryEntity
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
    val activeUsers: StateFlow<List<HotspotActiveUser>> = repository.activeUsers
    val cards: StateFlow<List<HotspotCard>> = repository.cards
    val profiles: StateFlow<List<HotspotProfile>> = repository.profiles
    val ipBindings: StateFlow<List<IpBinding>> = repository.ipBindings

    // Filtering & Search
    private val _cardSearchQuery = MutableStateFlow("")
    val cardSearchQuery: StateFlow<String> = _cardSearchQuery.asStateFlow()

    private val _cardFilter = MutableStateFlow(CardFilter.ALL)
    val cardFilter: StateFlow<CardFilter> = _cardFilter.asStateFlow()

    val filteredCards: StateFlow<List<HotspotCard>> = combine(
        cards, _cardSearchQuery, _cardFilter
    ) { allCards, query, filter ->
        allCards.filter { card ->
            val matchesQuery = query.isBlank() ||
                    card.name.contains(query, ignoreCase = true) ||
                    card.comment.contains(query, ignoreCase = true) ||
                    card.profile.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                CardFilter.ALL -> true
                CardFilter.ACTIVE -> card.isActive && !card.isExpired
                CardFilter.UNUSED -> !card.isActive && !card.isExpired
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
                // Set printable cards for instant preview
                _printableCards.value = cards
            } else {
                _toastEvent.emit("فشل توليد الكروت: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun createProfile(profile: HotspotProfile) {
        viewModelScope.launch {
            repository.createProfile(profile)
            _toastEvent.emit("تم إنشاء باقة: ${profile.name}")
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
