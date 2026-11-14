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
    val selectedTimeFilter by viewModel.selectedTimeFilter.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(FinanceTab.OVERVIEW) }
    var selectedMonthKey by remember { mutableStateOf<String?>(null) }
    var pkgToEditPrice by remember { mutableStateOf<DetailedPackageProfitStat?>(null) }

    // Filter mode: "ALL" (all months), "LAST_6" (latest 6 months), or specific year string e.g. "2026"
    var activeMonthFilter by remember { mutableStateOf("ALL") }
    var sortNewestFirst by remember { mutableStateOf(true) }

    val distinctYears = remember(monthlySales) {
        monthlySales.mapNotNull {
            it.monthKey.substringBefore("-").takeIf { y -> y.isNotBlank() }
        }.distinct().sortedDescending()
    }

    val filteredMonths = remember(monthlySales, activeMonthFilter, sortNewestFirst) {
        val base = when (activeMonthFilter) {
            "ALL" -> monthlySales
            "LAST_6" -> monthlySales.take(6)
            else -> monthlySales.filter { it.monthKey.startsWith("$activeMonthFilter-") }
        }
        if (sortNewestFirst) {
            base.sortedByDescending { it.monthKey }
        } else {
            base.sortedBy { it.monthKey }
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
        // Global Pricing Dialog
        pkgToEditPrice?.let { targetPkg ->
            PackagePricingDialog(
                profileName = targetPkg.profileName,
                initialSalePrice = targetPkg.salePrice,
                initialCostPrice = targetPkg.costPrice,
                onDismiss = { pkgToEditPrice = null },
                onSave = { sale, cost ->
                    viewModel.savePackagePricing(targetPkg.profileName, sale, cost)
                }
            )
        }

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
                    item {
                        FinanceTimeFilterRow(
                            selectedTimeFilter = selectedTimeFilter,
                            onSelectFilter = { viewModel.setTimeFilter(it) }
                        )
                    }

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
                        val headerTitle = when (activeMonthFilter) {
                            "ALL" -> "إجمالي المبيعات (كل الأشهر)"
                            "LAST_6" -> "إجمالي المبيعات (آخر 6 أشهر)"
                            else -> "إجمالي المبيعات (سنة $activeMonthFilter)"
                        }
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
                            FilterChip(
                                selected = activeMonthFilter == "ALL",
                                onClick = { activeMonthFilter = "ALL" },
                                label = { Text("كل الأشهر (${monthlySales.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(10.dp)
                            )

                            FilterChip(
                                selected = activeMonthFilter == "LAST_6",
                                onClick = { activeMonthFilter = "LAST_6" },
                                label = { Text("آخر 6 أشهر", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Year Filters
                            distinctYears.forEach { yr ->
                                FilterChip(
                                    selected = activeMonthFilter == yr,
                                    onClick = { activeMonthFilter = yr },
                                    label = { Text("سنة $yr", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            FilterChip(
                                selected = !sortNewestFirst,
                                onClick = { sortNewestFirst = !sortNewestFirst },
                                label = {
                                    Text(
                                        text = if (sortNewestFirst) "الأحدث أولاً" else "الأقدم أولاً",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
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
                                    Text("لا توجد إحصائيات مسجلة لهذا النطاق", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "تحليل أرباح وتكاليف الباقات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مقارنة أسعار البيع والتكلفة وهامش الربح الصافي لكل باقة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FinanceTimeFilterRow(
                                selectedTimeFilter = selectedTimeFilter,
                                onSelectFilter = { viewModel.setTimeFilter(it) }
                            )
                        }
                    }

                    items(packageStats, key = { it.profileName }) { pkg ->
                        PackageProfitDetailCard(
                            pkg = pkg,
                            currencyFormat = currencyFormat,
                            onEditPrice = { pkgToEditPrice = pkg }
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
                        text = "${currencyFormat.format(revenueSummary.totalEstimatedRevenue)} ر.ي",
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
private fun FinanceTimeFilterRow(
    selectedTimeFilter: TimeFilterRange,
    onSelectFilter: (TimeFilterRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeFilterRange.entries.forEach { range ->
            FilterChip(
                selected = selectedTimeFilter == range,
                onClick = { onSelectFilter(range) },
                label = {
                    Text(
                        text = range.title,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTimeFilter == range) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun SoldVsUnsoldBreakdownCard(
    revenueSummary: RevenueAnalyticsSummary,
    currencyFormat: NumberFormat
) {
    val soldCount = revenueSummary.activeCardsCount + revenueSummary.expiredCardsCount
    val unsoldCount = revenueSummary.unusedCardsCount
    val trackedCount = (soldCount + unsoldCount)
    val totalCount = maxOf(revenueSummary.totalCardsCount, trackedCount)
    val soldRatio = if (totalCount > 0) (soldCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    val soldPct = if (totalCount > 0) (soldRatio * 100f).toInt().coerceIn(0, 100) else 0
    val unsoldPct = if (totalCount > 0) (100 - soldPct).coerceIn(0, 100) else 0

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
                Text("إجمالي $totalCount كرت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Progress visual
            LinearProgressIndicator(
                progress = { soldRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = StatusActiveGreen,
                trackColor = if (totalCount > 0) Color(0xFFFF8F00).copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
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
                        Text("$soldCount كرت ($soldPct%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("قيمة المبيعات: ${currencyFormat.format(revenueSummary.totalEstimatedRevenue)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("$unsoldCount كرت ($unsoldPct%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    val totalCostAllMonths = monthlySales.sumOf { it.totalCost }
    val totalProfitAllMonths = monthlySales.sumOf { it.netProfit }
    val totalGenAllMonths = monthlySales.sumOf { it.totalCardsGenerated }
    val totalSoldAllMonths = monthlySales.sumOf { it.totalCardsSold }
    val totalUnsoldAllMonths = monthlySales.sumOf { it.totalCardsUnsold }
    val totalDeficitAllMonths = monthlySales.sumOf { it.targetDeficit }
    val overallMarginPercent = if (totalRevenueAllMonths > 0.0) ((totalProfitAllMonths / totalRevenueAllMonths) * 100.0).toInt() else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title (${monthlySales.size} شهر)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (overallMarginPercent > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusActiveGreen.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "هامش $overallMarginPercent%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveGreen
                        )
                    }
                }
            }
            Text(
                text = "إحصائيات دقيقة ومرتبة زمنياً ومحسوبة مباشرة من سجل الكروت والدفعات ونقاط البيع",
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

            // Revenue, Cost, Profit & Deficit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("المبيعات الكلية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalRevenueAllMonths)} ر.ي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("إجمالي التكلفة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalCostAllMonths)} ر.ي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusErrorRed)
                }
                Column {
                    Text("صافي الأرباح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalProfitAllMonths)} ر.ي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("عجز الهدف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(totalDeficitAllMonths)} ر.ي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusWarningAmber)
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
                        Text("توزيع المبيعات والأرباح حسب الباقات:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            month.packageBreakdown.forEach { pkg ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(pkg.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("${pkg.cardsSold} كرت (${pkg.sharePercentage.toInt()}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("الإيراد: ${currencyFormat.format(pkg.totalRevenue)} ر.ي", style = MaterialTheme.typography.labelSmall)
                                            Text("الصافي: ${currencyFormat.format(pkg.netProfit)} ر.ي", style = MaterialTheme.typography.labelSmall, color = StatusActiveGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Daily Sales Distribution
                    if (month.dailyBreakdown.isNotEmpty()) {
                        val maxDaySold = month.dailyBreakdown.maxOfOrNull { it.cardsSold }?.coerceAtLeast(1) ?: 1
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("توزيع المبيعات اليومية:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            month.dailyBreakdown.forEach { point ->
                                val barHeight = ((point.cardsSold.toFloat() / maxDaySold.toFloat()) * 48f).dp.coerceIn(14.dp, 52.dp)
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
                                                .width(22.dp)
                                                .height(barHeight)
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
private fun PackagePricingDialog(
    profileName: String,
    initialSalePrice: Double,
    initialCostPrice: Double,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
) {
    var salePriceInput by remember { mutableStateOf(if (initialSalePrice > 0) initialSalePrice.toInt().toString() else "") }
    var costPriceInput by remember { mutableStateOf(if (initialCostPrice > 0) initialCostPrice.toInt().toString() else "") }
    val previewSale = salePriceInput.toDoubleOrNull() ?: 0.0
    val previewCost = costPriceInput.toDoubleOrNull() ?: 0.0
    val previewProfit = (previewSale - previewCost).coerceAtLeast(0.0)
    val previewMargin = if (previewSale > 0.0) ((previewProfit / previewSale) * 100.0).toInt() else 0

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("ضبط أسعار باقة: $profileName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                androidx.compose.material3.OutlinedTextField(
                    value = salePriceInput,
                    onValueChange = { input ->
                        if (input.count { it == '.' } <= 1 && input.all { c -> c.isDigit() || c == '.' }) {
                            salePriceInput = input
                        }
                    },
                    label = { Text("سعر البيع للجمهور (ر.ي)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )

                androidx.compose.material3.OutlinedTextField(
                    value = costPriceInput,
                    onValueChange = { input ->
                        if (input.count { it == '.' } <= 1 && input.all { c -> c.isDigit() || c == '.' }) {
                            costPriceInput = input
                        }
                    },
                    label = { Text("تكلفة الكرت الواحد (ر.ي)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusActiveGreen.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ربح الكرت: +${previewProfit.toInt()} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                        Text("هامش $previewMargin%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            val sale = salePriceInput.toDoubleOrNull() ?: 0.0
                            val cost = costPriceInput.toDoubleOrNull() ?: 0.0
                            onSave(sale, cost)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("حفظ")
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
    onEditPrice: () -> Unit
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(pkg.profileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (pkg.salePrice > 0) {
                        Text("سعر البيع: ${currencyFormat.format(pkg.salePrice)} ر.ي | التكلفة: ${currencyFormat.format(pkg.costPrice)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("⚠️ السعر غير محدد - يرجى ضبط الأسعار", style = MaterialTheme.typography.labelSmall, color = StatusErrorRed, fontWeight = FontWeight.Bold)
                    }
                }
                
                IconButton(onClick = onEditPrice) {
                    Icon(Icons.Default.Timeline, contentDescription = "تعديل الأسعار", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                
                if (pkg.salePrice > 0) {
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
                    Text("المباع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${pkg.soldCardsCount} كرت", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المخزون", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${pkg.unusedCardsCount} كرت", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي الإيراد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(pkg.totalRevenue)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("صافي الربح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${currencyFormat.format(pkg.netProfit)} ر.ي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
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
