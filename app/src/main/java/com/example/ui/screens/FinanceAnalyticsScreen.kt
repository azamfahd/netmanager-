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

    var selectedTab by remember { mutableStateOf(FinanceTab.OVERVIEW) }
    var selectedMonthKey by remember { mutableStateOf<String?>(null) }

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
                        AllMonthsAggregateHeader(
                            monthlySales = monthlySales,
                            currencyFormat = currencyFormat
                        )
                    }

                    items(monthlySales, key = { it.monthKey }) { monthItem ->
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

                FinanceTab.PACKAGES -> {
                    item {
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
                    }

                    items(packageStats) { pkg ->
                        PackageProfitDetailCard(
                            pkg = pkg,
                            currencyFormat = currencyFormat
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
                        items(posAgents) { agentSummary ->
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
                text = "تقرير المبيعات التراكمية الشاملة لجميع الأشهر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (month.isCurrentMonth) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Month Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(month.monthName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (month.isCurrentMonth) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("الشهر الحالي", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "${currencyFormat.format(month.netProfit)} ر.ي صافي",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusActiveGreen
                )
            }

            // Sales Ratio Progress
            val ratio = (month.salesPercentage / 100f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (ratio > 0.7f) StatusActiveGreen else RouterBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "مباع: ${month.totalCardsSold} كرت (${month.salesPercentage.toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusActiveGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "غير مباع: ${month.totalCardsUnsold} كرت",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF8F00),
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المبيعات:", style = MaterialTheme.typography.labelSmall)
                                Text("${currencyFormat.format(month.totalRevenue)} ر.ي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي التكلفة التقديرية:", style = MaterialTheme.typography.labelSmall)
                                Text("${currencyFormat.format(month.totalCost)} ر.ي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusErrorRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("قيمة المخزون غير المباع:", style = MaterialTheme.typography.labelSmall)
                                Text("${currencyFormat.format(month.potentialUnsoldValue)} ر.ي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("عجز المبيعات عن الهدف:", style = MaterialTheme.typography.labelSmall)
                                Text("${currencyFormat.format(month.targetDeficit)} ر.ي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusWarningAmber)
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
                    Text(pkg.profileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("سعر البيع: ${currencyFormat.format(pkg.salePrice)} ر.ي | التكلفة: ${currencyFormat.format(pkg.costPrice)} ر.ي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
