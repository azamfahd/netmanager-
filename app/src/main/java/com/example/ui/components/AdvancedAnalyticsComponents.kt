package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BandwidthPoint
import com.example.data.model.DetailedPackageProfitStat
import com.example.data.model.PackageConsumptionStat
import com.example.data.model.PeakHourStat
import com.example.data.model.RevenueAnalyticsSummary
import com.example.data.model.TimeFilterRange
import com.example.data.network.NetworkUtils
import com.example.ui.theme.CyberGreenBright
import com.example.ui.theme.CyberGreenNeon
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.NetworkCyanLight
import kotlin.math.max

/**
 * Main Analytics Hub Section Container with Filter Tabs
 */
@Composable
fun AdvancedAnalyticsHub(
    bandwidthHistory: List<BandwidthPoint>,
    revenueSummary: RevenueAnalyticsSummary,
    peakHourStats: List<PeakHourStat>,
    packageStats: List<PackageConsumptionStat>,
    detailedPackageStats: List<DetailedPackageProfitStat> = emptyList(),
    selectedTimeFilter: TimeFilterRange = TimeFilterRange.ALL,
    onTimeFilterSelected: (TimeFilterRange) -> Unit = {},
    onEditPackagePricing: (profileName: String, cost: Double, sale: Double) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filterTabs = listOf("الكل", "مبيعات وأرباح الكروت 💎", "تقارير الإيرادات", "السرعة اللحظية", "ساعات الذروة", "استهلاك الباقات")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberGreenPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = CyberGreenBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "📊 التحليلات المتقدمة والإحصائيات الشاملة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مراقبة حية للنطاق الترددي، مبيعات وأرباح الكروت، والذروة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filterTabs.forEachIndexed { index, title ->
                    FilterChip(
                        selected = selectedFilterIndex == index,
                        onClick = { selectedFilterIndex = index },
                        label = { Text(title, fontSize = 11.sp, fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberGreenPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyberGreenBright
                        )
                    )
                }
            }

            // Render Selected Analytics Sections
            when (selectedFilterIndex) {
                0 -> {
                    // Show all cards with detailed card profits first
                    PackageSalesProfitAnalyticsCard(
                        detailedStats = detailedPackageStats,
                        revenueSummary = revenueSummary,
                        selectedTimeFilter = selectedTimeFilter,
                        onTimeFilterSelected = onTimeFilterSelected,
                        onEditPackagePricing = onEditPackagePricing
                    )
                    RevenueReportsCard(revenueSummary)
                    LiveBandwidthChartCard(bandwidthHistory)
                    PeakHoursAnalysisCard(peakHourStats)
                    PackageConsumptionBreakdownCard(packageStats)
                }
                1 -> PackageSalesProfitAnalyticsCard(
                    detailedStats = detailedPackageStats,
                    revenueSummary = revenueSummary,
                    selectedTimeFilter = selectedTimeFilter,
                    onTimeFilterSelected = onTimeFilterSelected,
                    onEditPackagePricing = onEditPackagePricing
                )
                2 -> RevenueReportsCard(revenueSummary)
                3 -> LiveBandwidthChartCard(bandwidthHistory)
                4 -> PeakHoursAnalysisCard(peakHourStats)
                5 -> PackageConsumptionBreakdownCard(packageStats)
            }
        }
    }
}

/**
 * 1. Real-Time Bandwidth Usage Charts and Graphs
 */
@Composable
fun LiveBandwidthChartCard(
    bandwidthHistory: List<BandwidthPoint>,
    modifier: Modifier = Modifier
) {
    val points = if (bandwidthHistory.size >= 2) bandwidthHistory else listOf(
        BandwidthPoint(1, 1500.0, 600.0),
        BandwidthPoint(2, 2800.0, 1100.0),
        BandwidthPoint(3, 2100.0, 900.0),
        BandwidthPoint(4, 3800.0, 1600.0),
        BandwidthPoint(5, 3450.0, 1500.0),
        BandwidthPoint(6, 4200.0, 1900.0)
    )

    val currentPoint = points.last()
    val maxRx = points.maxOfOrNull { it.rxKbps } ?: 5000.0
    val maxTx = points.maxOfOrNull { it.txKbps } ?: 2500.0
    val chartMax = max(maxRx, maxTx).coerceAtLeast(1000.0) * 1.25

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = CyberGreenBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "📈 حركة النطاق الترددي الحية (Real-Time Traffic)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = CyberGreenPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "مباشر • Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGreenBright.copy(alpha = pulseAlpha),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Speeds Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = CyberGreenBright, modifier = Modifier.size(14.dp))
                    Text(
                        text = "تحميل: ${NetworkUtils.formatSpeed(currentPoint.rxKbps)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreenBright
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = NetworkCyan, modifier = Modifier.size(14.dp))
                    Text(
                        text = "رفع: ${NetworkUtils.formatSpeed(currentPoint.txKbps)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetworkCyan
                    )
                }

                Text(
                    text = "الذروة: ${NetworkUtils.formatSpeed(maxRx)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Real-Time Canvas Line Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF09140F))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val n = points.size

                    // 1. Draw Grid Lines
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = (height / gridSteps) * i
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    if (n < 2) return@Canvas

                    val stepX = width / (n - 1)

                    // 2. Build Rx Path (Download)
                    val rxPath = Path()
                    val rxFillPath = Path()

                    points.forEachIndexed { i, p ->
                        val x = i * stepX
                        val normalizedY = 1f - (p.rxKbps.toFloat() / chartMax.toFloat()).coerceIn(0f, 1f)
                        val y = normalizedY * height

                        if (i == 0) {
                            rxPath.moveTo(x, y)
                            rxFillPath.moveTo(x, height)
                            rxFillPath.lineTo(x, y)
                        } else {
                            rxPath.lineTo(x, y)
                            rxFillPath.lineTo(x, y)
                        }

                        if (i == n - 1) {
                            rxFillPath.lineTo(x, height)
                            rxFillPath.close()
                        }
                    }

                    // 3. Draw Rx Fill Gradient & Stroke
                    drawPath(
                        path = rxFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(CyberGreenBright.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )
                    drawPath(
                        path = rxPath,
                        color = CyberGreenBright,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )

                    // 4. Build Tx Path (Upload)
                    val txPath = Path()
                    points.forEachIndexed { i, p ->
                        val x = i * stepX
                        val normalizedY = 1f - (p.txKbps.toFloat() / chartMax.toFloat()).coerceIn(0f, 1f)
                        val y = normalizedY * height

                        if (i == 0) txPath.moveTo(x, y) else txPath.lineTo(x, y)
                    }

                    drawPath(
                        path = txPath,
                        color = NetworkCyan,
                        style = Stroke(width = 2f, cap = StrokeCap.Round)
                    )

                    // 5. Draw Pulse indicator at latest Rx point
                    val lastX = (n - 1) * stepX
                    val lastRxY = (1f - (currentPoint.rxKbps.toFloat() / chartMax.toFloat()).coerceIn(0f, 1f)) * height
                    drawCircle(
                        color = CyberGreenNeon.copy(alpha = pulseAlpha),
                        radius = 6f,
                        center = Offset(lastX, lastRxY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5f,
                        center = Offset(lastX, lastRxY)
                    )
                }
            }
        }
    }
}

/**
 * 2. Revenue and Sales Analytics Reports
 */
@Composable
fun RevenueReportsCard(
    summary: RevenueAnalyticsSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = CyberGreenBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "💰 تقارير المبيعات والأرباح المالية",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = CyberGreenPrimary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "إجمالي: ${summary.totalEstimatedRevenue.toLong()} ر.ي",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 4 Mini Financial Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniCard(
                    title = "أرباح نشطة",
                    value = "${summary.activeRevenue.toLong()} ر.ي",
                    color = CyberGreenBright,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "قيمة المخزون المتاح",
                    value = "${summary.availableInventoryValue.toLong()} ر.ي",
                    color = NetworkCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniCard(
                    title = "متوسط سعر الكرت",
                    value = "${summary.averageCardPrice.toInt()} ر.ي",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "الباقة الأكثر مبيعاً",
                    value = summary.topSellingPackage,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Cards Inventory Breakdown Progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "توزيع الكروت: ${summary.activeCardsCount} نشط • ${summary.unusedCardsCount} متاح • ${summary.expiredCardsCount} منتهي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.totalCardsCount} كرت",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                val activeFraction = if (summary.totalCardsCount > 0) summary.activeCardsCount.toFloat() / summary.totalCardsCount else 0f
                LinearProgressIndicator(
                    progress = { activeFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyberGreenBright,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

/**
 * 3. Active User Peak Hours Analysis
 */
@Composable
fun PeakHoursAnalysisCard(
    peakStats: List<PeakHourStat>,
    modifier: Modifier = Modifier
) {
    val stats = if (peakStats.isNotEmpty()) peakStats else (0..23).map { h ->
        PeakHourStat(h, (h * 2 + 5), h in 19..22, (h.toFloat() / 24f))
    }

    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val maxUsers = stats.maxOfOrNull { it.userCount } ?: 1
    val peakHourItem = stats.maxByOrNull { it.userCount }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "⏰ تحليل ساعات الذروة للمتصلين (24h Peak Hours)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                peakHourItem?.let { peak ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ذروة: ${formatHourLabel(peak.hour)} (${peak.userCount} متصل)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = "توزيع كثافة الاتصال خلال الـ 24 ساعة الماضية لمساعدتك في تخطيط السرعات والباقات:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 24-Hour Bar Graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF09140F))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    stats.forEach { stat ->
                        val isCurrent = stat.hour == currentHour
                        val heightFraction = (stat.userCount.toFloat() / maxUsers.toFloat()).coerceIn(0.08f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(
                                        when {
                                            isCurrent -> CyberGreenNeon
                                            stat.isPeak -> CyberGreenBright
                                            else -> NetworkCyan.copy(alpha = 0.45f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            // Hours labels strip (every 4 hours)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0, 4, 8, 12, 16, 20, 23).forEach { hour ->
                    Text(
                        text = formatHourLabel(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

private fun formatHourLabel(hour: Int): String {
    return when {
        hour == 0 -> "12ص"
        hour < 12 -> "${hour}ص"
        hour == 12 -> "12م"
        else -> "${hour - 12}م"
    }
}

/**
 * 4. Data Consumption Breakdown per Package
 */
@Composable
fun PackageConsumptionBreakdownCard(
    packageStats: List<PackageConsumptionStat>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = NetworkCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "📦 تفصيل استهلاك البيانات لكل باقة (Data Breakdown)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${packageStats.size} باقات",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (packageStats.isEmpty()) {
                Text(
                    text = "لا توجد بيانات استهلاك مسجلة حتى الآن للباقات",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    packageStats.take(5).forEach { stat ->
                        PackageConsumptionItemRow(stat = stat)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageConsumptionItemRow(
    stat: PackageConsumptionStat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.profileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stat.formattedTraffic,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CyberGreenBright
            )
        }

        LinearProgressIndicator(
            progress = { stat.percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = CyberGreenBright,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "المستخدمين: ${stat.activeUsersCount} نشط • الكروت: ${stat.totalCardsCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = "حصة الاستهلاك: ${(stat.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = NetworkCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Detailed Package Sales, Cost, and Profit Analytics Card & Table
 */
@Composable
fun PackageSalesProfitAnalyticsCard(
    detailedStats: List<DetailedPackageProfitStat>,
    revenueSummary: RevenueAnalyticsSummary,
    selectedTimeFilter: TimeFilterRange,
    onTimeFilterSelected: (TimeFilterRange) -> Unit,
    onEditPackagePricing: (profileName: String, cost: Double, sale: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var packageToEdit by remember { mutableStateOf<DetailedPackageProfitStat?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberGreenPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = CyberGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "💎 مبيعات الكروت والأرباح التفصيلية لكل باقة",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "احصائيات دقيقة لمبيعات وتكاليف وصافي ربح كل فئة كرت",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. Time Filter Selector Chips (الكل، اليوم، الأسبوع، الشهر)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeFilterRange.values().forEach { filter ->
                    val isSelected = selectedTimeFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTimeFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberGreenPrimary.copy(alpha = 0.25f),
                            selectedLabelColor = CyberGreenBright
                        )
                    )
                }
            }

            // 2. Executive Financial KPI Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialMetricBox(
                    title = "إجمالي المبيعات",
                    value = "${revenueSummary.totalEstimatedRevenue.toLong()} ر.ي",
                    subtitle = "الفترة: ${selectedTimeFilter.title}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                FinancialMetricBox(
                    title = "صافي الأرباح",
                    value = "+${revenueSummary.totalNetProfit.toLong()} ر.ي",
                    subtitle = "هامش ربح: ${revenueSummary.averageProfitMarginPercent.toInt()}%",
                    valueColor = CyberGreenBright,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialMetricBox(
                    title = "إجمالي التكلفة",
                    value = "${revenueSummary.totalCost.toLong()} ر.ي",
                    subtitle = "تكلفة الكروت المباعة",
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FinancialMetricBox(
                    title = "أعلى باقة ربحية",
                    value = revenueSummary.topProfitablePackage,
                    subtitle = "ربح: +${revenueSummary.topProfitablePackageProfit.toLong()} ر.ي",
                    valueColor = NetworkCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Detailed Package Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "جدول تفاصيل الباقات والكروت (${detailedStats.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "انقر 'تعديل' لضبط التكلفة والبيع",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            if (detailedStats.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد باقات أو كروت مسجلة في السيرفر حالياً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // List of Detailed Package Cards
                detailedStats.forEach { stat ->
                    PackageProfitRowCard(
                        stat = stat,
                        onEditClick = { packageToEdit = stat }
                    )
                }
            }
        }
    }

    // Edit Pricing Dialog
    packageToEdit?.let { stat ->
        EditPackagePricingDialog(
            stat = stat,
            onDismiss = { packageToEdit = null },
            onSave = { cost, sale ->
                onEditPackagePricing(stat.profileName, cost, sale)
                packageToEdit = null
            }
        )
    }
}

@Composable
private fun FinancialMetricBox(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PackageProfitRowCard(
    stat: DetailedPackageProfitStat,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Package Title and Pricing Badges + Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stat.profileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Profit Per Card Pill
                    Surface(
                        color = CyberGreenPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ربح الكرت: +${stat.profitPerCard.toInt()} ر.ي",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGreenBright,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }

                    // Edit Price Button
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل التكلفة والبيع",
                            tint = CyberGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Row 2: Price vs Cost Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "سعر البيع:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${stat.salePrice.toInt()} ر.ي", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "سعر التكلفة:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${stat.costPrice.toInt()} ر.ي", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = CyberGreenPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "هامش الربح:", fontSize = 10.sp, color = CyberGreenBright)
                        Text(text = "${stat.profitMarginPercent.toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberGreenBright)
                    }
                }
            }

            // Row 3: Cards Breakdown Counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الكروت المباعة: ${stat.soldCardsCount} كرت",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
                Text(
                    text = "نشطة: ${stat.activeCardsCount} • متاحة: ${stat.unusedCardsCount} • منتهية: ${stat.expiredCardsCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            // Row 4: Financial Summary for this package
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "المبيعات: ${stat.totalRevenue.toLong()} ر.ي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "التكلفة: ${stat.totalCost.toLong()} ر.ي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "صافي ربح الباقة:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "+${stat.netProfit.toLong()} ر.ي",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberGreenBright
                        )
                    }
                }
            }

            // Row 5: Contribution Bar
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LinearProgressIndicator(
                    progress = { (stat.profitSharePercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyberGreenBright,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المساهمة في أرباح الشبكة: ${stat.profitSharePercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "حصة المبيعات: ${stat.salesSharePercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetworkCyan,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

/**
 * Dialog to customize Cost Price and Sale Price for any Package
 */
@Composable
fun EditPackagePricingDialog(
    stat: DetailedPackageProfitStat,
    onDismiss: () -> Unit,
    onSave: (cost: Double, sale: Double) -> Unit
) {
    var costInput by remember { mutableStateOf(stat.costPrice.toInt().toString()) }
    var saleInput by remember { mutableStateOf(stat.salePrice.toInt().toString()) }

    val currentCost = costInput.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    val currentSale = saleInput.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    val currentProfit = (currentSale - currentCost).coerceAtLeast(0.0)
    val currentMargin = if (currentSale > 0) ((currentProfit / currentSale) * 100.0) else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PriceCheck,
                    contentDescription = null,
                    tint = CyberGreenPrimary
                )
                Text(
                    text = "تحديد أسعار وتكلفة (${stat.profileName})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "حدد سعر التكلفة وسعر البيع لحساب أرباح كروت هذه الباقة بدقة في السيرفر:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sale Price Input
                OutlinedTextField(
                    value = saleInput,
                    onValueChange = { saleInput = it },
                    label = { Text("سعر البيع للكرت الواحد (ر.ي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Cost Price Input
                OutlinedTextField(
                    value = costInput,
                    onValueChange = { costInput = it },
                    label = { Text("سعر التكلفة للكرت الواحد (ر.ي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview Card
                Surface(
                    color = CyberGreenPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📊 معاينة الأرباح المحسوبة للكرت:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberGreenBright
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "صافي ربح الكرت:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "+${currentProfit.toInt()} ر.ي",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberGreenBright
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "نسبة هامش الربح:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "${currentMargin.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = NetworkCyan
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(currentCost, currentSale)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreenPrimary
                )
            ) {
                Text("حفظ وتطبيق", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
