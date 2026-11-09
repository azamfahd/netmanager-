package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MonthlySalesSummary
import com.example.data.model.DetailedPackageProfitStat
import com.example.data.model.RevenueAnalyticsSummary
import com.example.data.model.TimeFilterRange
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.viewmodel.MainViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FinanceAnalyticsScreen(
    viewModel: MainViewModel,
    onNavigateToCards: () -> Unit,
    onNavigateToPosAgents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val revenueSummary by viewModel.revenueAnalytics.collectAsStateWithLifecycle()
    val monthlySales by viewModel.monthlySalesList.collectAsStateWithLifecycle()
    val packageStats by viewModel.detailedPackageProfitStats.collectAsStateWithLifecycle()
    val posAgents by viewModel.posSummaries.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(FinanceTab.OVERVIEW) }
    var selectedMonthKey by remember { mutableStateOf<String?>(null) }
    var selectedPackageName by remember { mutableStateOf<String?>(null) }
    
    // Filter mode: null means "Last 6 Months" (default), otherwise a specific year string
    var activeYearFilter by remember { mutableStateOf<String?>(null) }

    val distinctYears = remember(monthlySales) {
        monthlySales.map { it.monthKey.substringBefore("-") }.distinct().sortedDescending()
    }
    
    val filteredMonths = remember(monthlySales, activeYearFilter) {
        if (activeYearFilter == null) {
            // Default: Show the most recent 6 months that have any data or are recent
            monthlySales.take(6)
        } else {
            // Filter by the selected year
            monthlySales.filter { it.monthKey.startsWith(activeYearFilter!!) }
        }
    }

    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale("ar", "SA")).apply {
            maximumFractionDigits = 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "مركز الأرباح والمبيعات المالي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إحصائيات شاملة لكل الأشهر، المبيعات المباعة وغير المباعة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sync Status Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RouterBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(14.dp))
                        Text(
                            text = "قاعدة البيانات المحلية: SQLite مدمجة ونشطة ✅ - متزامنة مع السيرفر",
                            style = MaterialTheme.typography.labelSmall,
                            color = RouterBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs within Finance: Overview, All Months, Package Breakdown, POS Agents
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FinanceTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(tab.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }

        // Screen Body depending on selectedTab
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            when (selectedTab) {
                FinanceTab.OVERVIEW -> {
                    // Global KPI cards
                    item {
                        FinanceGlobalKpiCard(
                            revenueSummary = revenueSummary,
                            currencyFormat = currencyFormat,
                            onNavigateToCards = onNavigateToCards
                        )
                    }

                    // Sold vs Unsold Cards Overview
                    item {
                        SoldVsUnsoldBreakdownCard(
                            revenueSummary = revenueSummary,
                            currencyFormat = currencyFormat
                        )
                    }

                    // Multi-Month Trends Teaser
                    item {
                        MonthlySummaryBanner(
                            monthlySales = monthlySales,
                            currencyFormat = currencyFormat,
                            onViewAllMonths = { selectedTab = FinanceTab.MONTHS }
                        )
                    }

                    // Top Package Profitability
                    item {
                        PackageProfitQuickSection(
                            packageStats = packageStats,
                            currencyFormat = currencyFormat,
                            onViewAllPackages = { selectedTab = FinanceTab.PACKAGES }
                        )
                    }
                }

                FinanceTab.MONTHS -> {
                    // Total Sales for All Months Section
                    item {
                        val headerTitle = if (activeYearFilter == null) "إجمالي المبيعات (آخر 6 أشهر)" else "إجمالي المبيعات (سنة $activeYearFilter)"
                        AllMonthsAggregateHeader(
                            title = headerTitle,
                            monthlySales = filteredMonths,
                            currencyFormat = currencyFormat
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Default: Last 6 Months
                            FilterChip(
                                selected = activeYearFilter == null,
                                onClick = { activeYearFilter = null },
                                label = { Text("آخر 6 أشهر", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            // Year Filters
                            distinctYears.forEach { yr ->
                                FilterChip(
                                    selected = activeYearFilter == yr,
                                    onClick = { activeYearFilter = yr },
                                    label = { Text("سنة $yr", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    if (filteredMonths.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("لا توجد إحصائيات مسجلة لهذا العام", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        items(filteredMonths, key = { it.monthKey }) { monthItem ->
                            MonthlySalesDetailedCard(
                                month = monthItem,
                                currencyFormat = currencyFormat,
                                isExpanded = selectedMonthKey == monthItem.monthKey,
                                onToggleExpand = {
                                    selectedMonthKey = if (selectedMonthKey == monthItem.monthKey) null else monthItem.monthKey
                                }
                            )
                        }
                    }
                }

                FinanceTab.PACKAGES -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "تحليل وأرباح مبيعات كل باقة",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "عرض تفصيلي دقيق لمبيعات كل باقة لكل شهر، أداء الشهر الحالي، والتكلفة وصافي الأرباح",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(packageStats, key = { it.profileName }) { pkg ->
                        PackageProfitDetailCard(
                            pkg = pkg,
                            currencyFormat = currencyFormat,
                            isExpanded = selectedPackageName == pkg.profileName,
                            onToggleExpand = {
                                selectedPackageName = if (selectedPackageName == pkg.profileName) null else pkg.profileName
                            }
                        )
                    }
                }

                FinanceTab.POS_AGENTS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "حسابات نقاط البيع والوكلاء",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "إجمالي المبيعات، العمولات، والمستحقات المتبقية",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onNavigateToPosAgents,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("إدارة الوكلاء", fontSize = 11.sp)
                            }
                        }
                    }

                    if (posAgents.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("لا يوجد وكلاء مسجلون حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(posAgents, key = { it.agent.id }) { agentSummary ->
                            PosAgentFinanceCard(
                                summary = agentSummary,
                                currencyFormat = currencyFormat
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

enum class FinanceTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("نظرة عامة", Icons.Default.PieChart),
    MONTHS("كل الأشهر", Icons.Default.CalendarMonth),
    PACKAGES("أرباح الباقات", Icons.Default.Timeline),
    POS_AGENTS("نقاط البيع", Icons.Default.Store)
}

@Composable
private fun FinanceGlobalKpiCard(
    revenueSummary: RevenueAnalyticsSummary,
    currencyFormat: NumberFormat,
    onNavigateToCards: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("صافي الأرباح المحققة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${currencyFormat.format(revenueSummary.totalNetProfit)} ر.ي",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusActiveGreen
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusActiveGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = StatusActiveGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = "هامش ${revenueSummary.averageProfitMarginPercent.toInt()}%",
                            color = StatusActiveGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Metric Row: Total Revenue, Total Cost, Total Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي المبيعات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${currencyFormat.format(revenueSummary.activeRevenue)} ر.ي",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي التكاليف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${currencyFormat.format(revenueSummary.totalCost)} ر.ي",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = StatusErrorRed
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("متوسط سعر الكرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${currencyFormat.format(revenueSummary.averageCardPrice)} ر.ي",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SoldVsUnsoldBreakdownCard(
    revenueSummary: RevenueAnalyticsSummary,
    currencyFormat: NumberFormat
) {
    val totalCount = revenueSummary.totalCardsCount.coerceAtLeast(1)
    val soldCount = revenueSummary.activeCardsCount + revenueSummary.expiredCardsCount
    val unsoldCount = revenueSummary.unusedCardsCount
    val soldRatio = (soldCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(20.dp))
                    Text("حالة الكروت: المباعة مقابل غير المباعة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text("إجمالي ${revenueSummary.totalCardsCount} كرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Progress visual
            LinearProgressIndicator(
                progress = { soldRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = StatusActiveGreen,
                trackColor = Color(0xFFFF8F00).copy(alpha = 0.4f)
            )

            // Grid of Sold vs Unsold
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sold Cards Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = StatusActiveGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusActiveGreen))
                            Text("الكروت المباعة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = StatusActiveGreen)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$soldCount كرت (${(soldRatio * 100).toInt()}%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("قيمة المبيعات: ${currencyFormat.format(revenueSummary.activeRevenue)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Unsold Inventory Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF8F00).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFFFF8F00).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF8F00)))
                            Text("الكروت غير المباعة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF8F00))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$unsoldCount كرت (${((1f - soldRatio) * 100).toInt()}%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("قيمة المخزون: ${currencyFormat.format(revenueSummary.availableInventoryValue)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Deficit / Unsold Risk Note
            if (revenueSummary.availableInventoryValue > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(16.dp))
                        Text(
                            text = "تنبيه المخزون: هناك ${currencyFormat.format(revenueSummary.availableInventoryValue)} ر.ي في الكروت غير المباعة جاهزة للتوزيع أو التفعيل.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryBanner(
    monthlySales: List<MonthlySalesSummary>,
    currencyFormat: NumberFormat,
    onViewAllMonths: () -> Unit
) {
    val totalRevenueAllMonths = monthlySales.sumOf { it.totalRevenue }
    val totalProfitAllMonths = monthlySales.sumOf { it.netProfit }
    val totalSoldAllMonths = monthlySales.sumOf { it.totalCardsSold }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewAllMonths),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("إجمالي المبيعات التراكمية لكل الأشهر", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("إجمالي المبيعات التراكمي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalRevenueAllMonths)} ر.ي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("صافي الربح التراكمي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalProfitAllMonths)} ر.ي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column {
                    Text("إجمالي الكروت المباعة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalSoldAllMonths كرت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AllMonthsAggregateHeader(
    title: String,
    monthlySales: List<MonthlySalesSummary>,
    currencyFormat: NumberFormat
) {
    val totalRevenueAllMonths = monthlySales.sumOf { it.totalRevenue }
    val totalProfitAllMonths = monthlySales.sumOf { it.netProfit }
    val totalGenAllMonths = monthlySales.sumOf { it.totalCardsGenerated }
    val totalSoldAllMonths = monthlySales.sumOf { it.totalCardsSold }
    val totalUnsoldAllMonths = monthlySales.sumOf { it.totalCardsUnsold }
    val totalDeficitAllMonths = monthlySales.sumOf { it.targetDeficit }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "$title (${monthlySales.size} شهر)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "إحصائيات دقيقة ومحسوبة مباشرة من بيانات الكروت والدفعات ونقاط البيع منذ بداية تشغيل الشبكة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Top highlights
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الكروت المولدة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalGenAllMonths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي المباع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalSoldAllMonths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي غير المباع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalUnsoldAllMonths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                }
            }

            // Deficit & Revenue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("المبيعات الكلية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalRevenueAllMonths)} ر.ي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("صافي الأرباح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalProfitAllMonths)} ر.ي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column {
                    Text("عجز المبيعات المستهدف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalDeficitAllMonths)} ر.ي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusErrorRed)
                }
            }

            val totalBytesAllMonths = monthlySales.sumOf { it.totalBytesConsumed }
            if (totalBytesAllMonths > 0L) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RouterBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إجمالي استهلاك البيانات لكل الأشهر:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = com.example.data.network.NetworkUtils.formatBytes(totalBytesAllMonths),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RouterBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySalesDetailedCard(
    month: MonthlySalesSummary,
    currencyFormat: NumberFormat,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (month.isCurrentMonth) 3.dp else 1.dp),
        border = if (month.isCurrentMonth) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Month Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(month.monthName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (month.isCurrentMonth) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("الشهر الحالي", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Growth Indicator
                    if (month.growthRatePercent != 0.0) {
                        val isPositive = month.growthRatePercent > 0
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (isPositive) StatusActiveGreen else StatusErrorRed).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isPositive) StatusActiveGreen else StatusErrorRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${if (isPositive) "+" else ""}${month.growthRatePercent.toInt()}% نمو",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) StatusActiveGreen else StatusErrorRed
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${currencyFormat.format(month.netProfit)} ر.ي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusActiveGreen
                    )
                    Text("صافي الربح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Sales Ratio Progress
            val ratio = (month.salesPercentage / 100f).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "نسبة المبيعات: ${month.salesPercentage.toInt()}% (${month.totalCardsSold} مباع)",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusActiveGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "المخزون الكلي: ${month.totalCardsGenerated} كرت",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF8F00),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (ratio > 0.7f) StatusActiveGreen else RouterBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Financial Breakdown Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("التفاصيل المالية للشهر:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المبيعات:", style = MaterialTheme.typography.bodySmall)
                                Text("${currencyFormat.format(month.totalRevenue)} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي التكلفة التقديرية:", style = MaterialTheme.typography.bodySmall)
                                Text("${currencyFormat.format(month.totalCost)} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = StatusErrorRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("قيمة المخزون غير المباع (${month.totalCardsUnsold} كرت):", style = MaterialTheme.typography.bodySmall)
                                Text("${currencyFormat.format(month.potentialUnsoldValue)} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("عجز المبيعات عن الهدف:", style = MaterialTheme.typography.bodySmall)
                                Text("${currencyFormat.format(month.targetDeficit)} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = StatusWarningAmber)
                            }
                            if (month.totalBytesConsumed > 0L) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("استهلاك بيانات الشبكة:", style = MaterialTheme.typography.bodySmall)
                                    Text(com.example.data.network.NetworkUtils.formatBytes(month.totalBytesConsumed), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = RouterBlue)
                                }
                            }
                        }
                    }

                    // Detailed Package Breakdown for this month
                    if (month.packageBreakdown.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("توزيع ومبيعات الباقات لهذا الشهر:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("${month.packageBreakdown.size} باقة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            month.packageBreakdown.forEach { pkg ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(pkg.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = "سعر ${currencyFormat.format(pkg.salePrice)} ر.ي",
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = StatusActiveGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "${currencyFormat.format(pkg.netProfit)} ر.ي ربح",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusActiveGreen
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("المباع: ${pkg.cardsSold} كرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("المتبقي: ${pkg.cardsUnsold} كرت", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF8F00))
                                            Text("الإيراد: ${currencyFormat.format(pkg.totalRevenue)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                            Text("التكلفة: ${currencyFormat.format(pkg.totalCost)} ر.ي", style = MaterialTheme.typography.labelSmall, color = StatusErrorRed)
                                        }

                                        // Revenue share bar
                                        val shareRatio = (pkg.sharePercentage / 100f).coerceIn(0f, 1f)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { shareRatio },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = RouterBlue,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Text(
                                                text = "${pkg.sharePercentage.toInt()}% من مبيعات الشهر",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Daily Sales Distribution
                    if (month.dailyBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("توزيع المبيعات اليومية:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            month.dailyBreakdown.forEach { point ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(point.dayLabel, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                        Box(
                                            modifier = Modifier
                                                .width(20.dp)
                                                .height((point.cardsSold * 4).dp.coerceIn(12.dp, 50.dp))
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(RouterBlue)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("${point.cardsSold}", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text("${currencyFormat.format(point.revenue)} ر.ي", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = StatusActiveGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageProfitQuickSection(
    packageStats: List<DetailedPackageProfitStat>,
    currencyFormat: NumberFormat,
    onViewAllPackages: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = RouterBlue)
                    Text("أرباح الباقات الأكثر مبيعاً", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onViewAllPackages,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("عرض الكل", fontSize = 11.sp)
                }
            }

            packageStats.take(3).forEach { pkg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(pkg.profileName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("مباع: ${pkg.soldCardsCount} كرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${currencyFormat.format(pkg.netProfit)} ر.ي ربح", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                        Text("هامش ${pkg.profitMarginPercent.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageProfitDetailCard(
    pkg: DetailedPackageProfitStat,
    currencyFormat: NumberFormat,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 3.dp else 1.dp),
        border = BorderStroke(
            1.dp,
            if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Package title, prices, margin chip, and expand arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pkg.profileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "سعر البيع: ${currencyFormat.format(pkg.salePrice)} ر.ي | التكلفة: ${currencyFormat.format(pkg.costPrice)} ر.ي | الربح: ${currencyFormat.format(pkg.profitPerCard)} ر.ي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusActiveGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "هامش ${pkg.profitMarginPercent.toInt()}%",
                            color = StatusActiveGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Summary Metrics Grid (Lifetime / Overall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المباع الكلي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${pkg.soldCardsCount} كرت", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المخزون الحالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${pkg.unusedCardsCount} كرت", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي الإيراد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(pkg.totalRevenue)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("صافي الأرباح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(pkg.netProfit)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
            }

            // Current Month Quick Teaser Pill
            val curMonth = pkg.currentMonthSales
            if (curMonth != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = "مبيعات الشهر الحالي (${curMonth.monthName}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${curMonth.cardsSold} كرت مباع | ${currencyFormat.format(curMonth.netProfit)} ر.ي ربح",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveGreen
                        )
                    }
                }
            }

            // Animated Expanded Content: Current Month Deep Dive + Month-by-Month History
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Current Month Deep-Dive Box
                    if (curMonth != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            "تفاصيل مبيعات الشهر الحالي (${curMonth.monthName})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = StatusActiveGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "نشط ومباشر",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusActiveGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("المباع هذا الشهر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${curMonth.cardsSold} كرت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                                    }
                                    Column {
                                        Text("المتبقي بالمخزون", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${curMonth.cardsUnsold} كرت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                                    }
                                    Column {
                                        Text("المولد هذا الشهر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${curMonth.cardsGenerated} كرت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("إيراد الشهر الحالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${currencyFormat.format(curMonth.totalRevenue)} ر.ي", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Column {
                                        Text("تكلفة الشهر التقديرية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${currencyFormat.format(curMonth.totalCost)} ر.ي", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = StatusErrorRed)
                                    }
                                    Column {
                                        Text("صافي ربح الشهر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${currencyFormat.format(curMonth.netProfit)} ر.ي", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                                    }
                                }

                                val monthRatio = (curMonth.salesPercentage / 100f).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("نسبة تصريف الكروت هذا الشهر:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${curMonth.salesPercentage.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                                    }
                                    LinearProgressIndicator(
                                        progress = { monthRatio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = StatusActiveGreen,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 2. Month-by-Month History
                    if (pkg.monthlyHistory.isNotEmpty()) {
                        Text(
                            text = "سجل مبيعات وأرباح الباقة شهراً بشهر (${pkg.monthlyHistory.size} شهر):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pkg.monthlyHistory.forEach { mStat ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (mStat.isCurrentMonth) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, if (mStat.isCurrentMonth) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(mStat.monthName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                if (mStat.isCurrentMonth) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    ) {
                                                        Text("الشهر الحالي", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            Text(
                                                text = "${currencyFormat.format(mStat.netProfit)} ر.ي ربح",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusActiveGreen
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("المباع: ${mStat.cardsSold} كرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("المتبقي: ${mStat.cardsUnsold} كرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("الإيراد: ${currencyFormat.format(mStat.totalRevenue)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                            Text("التكلفة: ${currencyFormat.format(mStat.totalCost)} ر.ي", style = MaterialTheme.typography.labelSmall, color = StatusErrorRed)
                                        }

                                        // Mini progress bar for sales percentage
                                        val histRatio = (mStat.salesPercentage / 100f).coerceIn(0f, 1f)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { histRatio },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = if (histRatio > 0.6f) StatusActiveGreen else RouterBlue,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Text("${mStat.salesPercentage.toInt()}% مباع", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosAgentFinanceCard(
    summary: com.example.data.local.PosAgentSummary,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(summary.agent.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (summary.agent.phone.isNotBlank()) {
                        Text(summary.agent.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "عمولة ${summary.agent.discountPercent.toInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي المبيعات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(summary.totalSalesVolume)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المسدد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(summary.totalPaidByAgent)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المتبقي بذمته", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(summary.netRemainingDebt)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (summary.netRemainingDebt > 0) StatusErrorRed else StatusActiveGreen)
                }
            }
        }
    }
}
