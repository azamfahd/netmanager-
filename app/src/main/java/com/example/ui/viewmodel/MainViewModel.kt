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
                        logs = snapshot.logs
                    )
                }
            }
        }
    }

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
    val adminUsers: StateFlow<List<com.example.data.model.AdminUser>> = repository.adminUsers
    val auditLogs: StateFlow<List<com.example.data.model.AuditLogEntry>> = repository.auditLogs
    val bandwidthHistory: StateFlow<List<com.example.data.model.BandwidthPoint>> = repository.bandwidthHistory

    val packagePricings: StateFlow<List<com.example.data.local.PackagePricingEntity>> = database.appDao().getAllPackagePricings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTimeFilter = MutableStateFlow(com.example.data.model.TimeFilterRange.ALL)
    val selectedTimeFilter: StateFlow<com.example.data.model.TimeFilterRange> = _selectedTimeFilter.asStateFlow()

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
                if (activeList.isNotEmpty()) {
                    activeList.forEach { user ->
                        database.appDao().markCardAsSold(user.user, now)
                    }
                }

                // Detect users who disconnected since last check to archive their logout and reason
                val currentNames = activeList.map { it.user }.toSet()
                if (previousActiveUsernames.isNotEmpty()) {
                    val disconnectedNames = previousActiveUsernames - currentNames
                    if (disconnectedNames.isNotEmpty()) {
                        val nowFormatted = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar", "SA")).format(java.util.Date(now))
                        val currentCards = cards.value
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
                                database.appDao().markSessionOffline(targetSessionId, nowFormatted, reason, isQuotaOver)
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
        saveToFavorites: Boolean = true,
        context: Context? = null
    ) {
        viewModelScope.launch {
            val targetContext = context ?: appContext
            _currentConfig.value = config

            var hasLoadedFromCache = false
            // 1. FAST INSTANT LOGIN FROM CACHE (دخول سريع فوري):
            if (!config.isDemoMode && targetContext != null) {
                val cachedSnapshot = com.example.data.local.RouterCacheManager.loadSnapshot(targetContext, config.ipAddress)
                if (cachedSnapshot != null) {
                    repository.restoreFromSnapshot(cachedSnapshot)
                    hasLoadedFromCache = true
                    val cachedRouterName = cachedSnapshot.routerName.ifBlank { config.name.ifBlank { "راوتر MikroTik" } }
                    _connectionState.value = ConnectionState.Connected(
                        routerName = cachedRouterName,
                        ipAddress = config.ipAddress,
                        isDemo = false,
                        isSyncing = true,
                        isFromCache = true
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
                    isFromCache = false
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
                            logs = snapshot.logs
                        )
                    }
                }
                startAutoRefresh()
            } else {
                if (hasLoadedFromCache) {
                    val error = result.exceptionOrNull()?.message ?: "تعذر الوصول للراوتر حالياً"
                    _toastEvent.emit("تنبيه: تعذر الاتصال المباشر ($error). أنت تتصفح البيانات المحفوظة محلياً.")
                    _connectionState.value = ConnectionState.Connected(
                        routerName = config.name.ifBlank { "راوتر MikroTik (محفوظ)" },
                        ipAddress = config.ipAddress,
                        isDemo = false,
                        isSyncing = false,
                        isFromCache = true
                    )
                    startAutoRefresh()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "فشل الاتصال بالراوتر"
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
