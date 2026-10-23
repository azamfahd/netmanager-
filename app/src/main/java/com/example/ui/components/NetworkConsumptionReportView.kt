package com.example.ui.components

import android.content.Intent
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExpiredCardSummary
import com.example.data.model.TimeFilterRange
import com.example.data.network.NetworkUtils
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.viewmodel.MainViewModel

@Composable
fun NetworkConsumptionReportView(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val report by viewModel.networkConsumptionReport.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchExpiredQuery by remember { mutableStateOf("") }
    var cardToReactivate by remember { mutableStateOf<ExpiredCardSummary?>(null) }
    var showClearArchiveConfirm by remember { mutableStateOf(false) }

    val filteredExpiredCards = remember(report.expiredCardsList, searchExpiredQuery) {
        if (searchExpiredQuery.isBlank()) report.expiredCardsList else {
            report.expiredCardsList.filter {
                it.cardName.contains(searchExpiredQuery, ignoreCase = true) ||
                        it.profile.contains(searchExpiredQuery, ignoreCase = true) ||
                        it.expiredReason.contains(searchExpiredQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("network_consumption_report_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Time Range Filter & Actions Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    TimeFilterRange.ALL to "كل الفترات",
                    TimeFilterRange.TODAY to "اليوم",
                    TimeFilterRange.WEEK to "آخر 7 أيام",
                    TimeFilterRange.MONTH to "هذا الشهر"
                ).forEach { (range, label) ->
                    FilterChip(
                        selected = report.selectedTimeFilter == range,
                        onClick = { viewModel.setTimeFilter(range) },
                        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RouterBlue.copy(alpha = 0.15f),
                            selectedLabelColor = RouterBlue
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Share / Export Report Button
                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            appendLine("📊 تقرير استهلاك شبكة الميكروتك وأرشيف الجلسات:")
                            appendLine("🌐 إجمالي الاستهلاك: ${NetworkUtils.formatBytes(report.totalBytes)}")
                            appendLine("⬇️ إجمالي التحميل: ${NetworkUtils.formatBytes(report.totalDownloadBytes)}")
                            appendLine("⬆️ إجمالي الرفع: ${NetworkUtils.formatBytes(report.totalUploadBytes)}")
                            appendLine("👥 إجمالي الجلسات: ${report.totalSessionsCount} (${report.activeSessionsCount} نشطة، ${report.offlineSessionsCount} سابقة)")
                            appendLine("🎟️ الكروت المنتهية: ${report.expiredCardsCount} كرت")
                            appendLine("⚠️ انتهت بالرصيد: ${report.quotaExhaustedCount}")
                            appendLine("⏱️ انتهت بالصلاحية: ${report.timeExpiredCount}")
                            appendLine("📈 متوسط استهلاك الكرت: ${NetworkUtils.formatBytes(report.averageBytesPerUser)}")
                            if (report.topPackagesByConsumption.isNotEmpty()) {
                                appendLine("📦 أعلى الباقات استهلاكاً:")
                                report.topPackagesByConsumption.take(3).forEach {
                                    appendLine(" • ${it.profileName}: ${NetworkUtils.formatBytes(it.totalBytes)} (${it.percentageOfTotal.toInt()}%)")
                                }
                            }
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة تقرير الاستهلاك"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة التقرير", fontSize = 12.sp)
                }

                // Clear Archive Button
                IconButton(
                    onClick = { showClearArchiveConfirm = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "تفريغ الأرشيف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 2. Main KPI Metric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Network Consumption Card
                    DetailedMetricCard(
                        title = "إجمالي الاستهلاك",
                        icon = Icons.Default.Speed,
                        iconTint = RouterBlue,
                        value = NetworkUtils.formatBytes(report.totalBytes),
                        valueColor = RouterBlue,
                        modifier = Modifier.weight(1f),
                        subDetails = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⬇️ ${NetworkUtils.formatBytes(report.totalDownloadBytes)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("⬆️ ${NetworkUtils.formatBytes(report.totalUploadBytes)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )

                    // Expired Cards Tracker Card
                    DetailedMetricCard(
                        title = "كروت منتهية الصلاحية",
                        icon = Icons.Default.Warning,
                        iconTint = StatusErrorRed,
                        value = "${report.expiredCardsCount} كرت",
                        valueColor = StatusErrorRed,
                        modifier = Modifier.weight(1f),
                        subDetails = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚠️ بالرصيد: ${report.quotaExhaustedCount}", fontSize = 10.sp, color = StatusErrorRed)
                                Text("⏱️ بالوقت: ${report.timeExpiredCount}", fontSize = 10.sp, color = StatusWarningAmber)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Archived Sessions Count Card
                    DetailedMetricCard(
                        title = "الجلسات المؤرشفة",
                        icon = Icons.Default.History,
                        iconTint = StatusActiveGreen,
                        value = "${report.totalSessionsCount} جلسة",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        subDetails = {
                            Text("${report.activeSessionsCount} نشطة • ${report.offlineSessionsCount} مكتملة", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )

                    // Average Usage Per User Card
                    DetailedMetricCard(
                        title = "معدل استهلاك الكرت",
                        icon = Icons.Default.PieChart,
                        iconTint = MaterialTheme.colorScheme.primary,
                        value = NetworkUtils.formatBytes(report.averageBytesPerUser),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        subDetails = {
                            Text("متوسط البيانات لكل مستخدم", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }
        }

        // 3. Top Consuming Packages Breakdown Section
        if (report.topPackagesByConsumption.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(18.dp))
                            Text(
                                text = "تحليل الباقات الأكثر استهلاكاً للباندوث والبيانات:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        report.topPackagesByConsumption.take(5).forEach { pkg ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pkg.profileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${NetworkUtils.formatBytes(pkg.totalBytes)} (${pkg.percentageOfTotal.toInt()}%) • ${pkg.sessionsCount} جلسة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (pkg.percentageOfTotal / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = RouterBlue,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Expired Cards Tracker Section Header & Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = StatusErrorRed, modifier = Modifier.size(20.dp))
                        Text(
                            text = "سجل تتبع الكروت المنتهية (${filteredExpiredCards.size}):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "كروت استنفدت رصيدها أو وقتها",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = searchExpiredQuery,
                    onValueChange = { searchExpiredQuery = it },
                    placeholder = { Text("بحث برقم الكرت المنتهي أو الباقة...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchExpiredQuery.isNotBlank()) {
                            IconButton(onClick = { searchExpiredQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        }

        // 5. Expired Cards List Items
        if (filteredExpiredCards.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (searchExpiredQuery.isBlank()) "لا توجد كروت منتهية الصلاحية مسجلة في الأرشيف حالياً" else "لا توجد نتائج مطابقة للبحث",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredExpiredCards, key = { it.cardId }) { card ->
                ExpiredCardItemRow(
                    card = card,
                    onReactivateClick = { cardToReactivate = card }
                )
            }
        }

        // 6. Top Consuming Devices
        if (report.topDevicesByConsumption.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(18.dp))
                            Text(
                                text = "الأجهزة الأكثر استهلاكاً للإنترنت:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        report.topDevicesByConsumption.take(5).forEach { dev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${dev.deviceBrand} ${dev.deviceModel}".ifBlank { "جهاز متصل" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "كرت: ${dev.username} • MAC: ${dev.macAddress}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                Surface(
                                    color = RouterBlue.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = NetworkUtils.formatBytes(dev.totalBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RouterBlue,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }

    // Reactivate Confirmation Dialog
    cardToReactivate?.let { target ->
        ConfirmDialog(
            title = "إعادة تفعيل الكرت وتصفير رصيده",
            message = "هل أنت متأكد من رغبتك في تجديد وإعادة تفعيل الكرت (${target.cardName}) وتصفير عداد الوقت والاستهلاك في الراوتر ليتمكن الزبون من استخدامه مجدداً؟",
            confirmText = "تجديد وتفعيل الآن",
            isDestructive = false,
            onConfirm = {
                viewModel.reactivateCardFromReport(target.cardId)
                cardToReactivate = null
            },
            onDismiss = { cardToReactivate = null }
        )
    }

    // Clear Archive Confirmation Dialog
    if (showClearArchiveConfirm) {
        ConfirmDialog(
            title = "إفراغ أرشيف الجلسات السابقة",
            message = "هل أنت متأكد من رغبتك في حذف وتفريغ سجل أرشيف الجلسات السابقة المخزنة محلياً؟ لن يؤثر ذلك على كروت الراوتر الحالية.",
            confirmText = "إفراغ الأرشيف",
            isDestructive = true,
            onConfirm = {
                viewModel.clearSessionArchive()
                showClearArchiveConfirm = false
            },
            onDismiss = { showClearArchiveConfirm = false }
        )
    }
}

@Composable
fun ExpiredCardItemRow(
    card: ExpiredCardSummary,
    onReactivateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = card.cardName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val (badgeText, badgeColor) = when {
                        card.isQuotaExhausted -> "انتهى الرصيد (بيانات)" to StatusErrorRed
                        card.isTimeExpired -> "انتهت الصلاحية (وقت)" to StatusWarningAmber
                        else -> "كرت منتهي" to StatusErrorRed
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onReactivateClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RouterBlue),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تجديد الكرت", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الباقة: ${card.profile} (${card.price})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الاستهلاك: ${NetworkUtils.formatBytes(card.totalBytes)}" + if (card.limitBytes > 0) " / ${NetworkUtils.formatBytes(card.limitBytes)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "سبب الانتهاء: ${card.expiredReason} • وقت الاتصال المسجل: ${card.uptime}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
        }
    }
}
