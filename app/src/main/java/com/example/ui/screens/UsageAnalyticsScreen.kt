package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppUsageItem
import com.example.data.network.NetworkUtils
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageAnalyticsScreen(
    username: String,
    type: String,
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val appUsageItems by viewModel.getSmartAppUsage(username, type).collectAsStateWithLifecycle(emptyList())
    val subscriberProfile by viewModel.getSubscriberProfileFlow(username).collectAsStateWithLifecycle(null)

    val title = when (type.lowercase()) {
        "download" -> "تحليل حجم التحميل"
        "upload" -> "تحليل حجم الرفع"
        "time" -> "تحليل وقت التشغيل"
        else -> "تحليل الاستهلاك الكلي"
    }

    val themeColor = when (type.lowercase()) {
        "download" -> StatusActiveGreen
        "upload" -> Color(0xFF673AB7)
        "time" -> Color(0xFFFF9800)
        else -> RouterBlue
    }

    val totalValue = when (type.lowercase()) {
        "download" -> NetworkUtils.formatBytes(subscriberProfile?.lifetimeBytesOut ?: 0L)
        "upload" -> NetworkUtils.formatBytes(subscriberProfile?.lifetimeBytesIn ?: 0L)
        "time" -> subscriberProfile?.lifetimeUptimeFormatted ?: "0s"
        else -> NetworkUtils.formatBytes(subscriberProfile?.lifetimeTotalBytes ?: 0L)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = "المشترك: $username", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Metric Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.08f)),
                    border = BorderStroke(2.dp, themeColor.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "القيمة المسجلة حالياً",
                            style = MaterialTheme.typography.labelMedium,
                            color = themeColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = totalValue,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = themeColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = themeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "بناءً على تحليل ذكي للجلسات",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Smart Visual Chart (Custom Doughnut)
            item {
                Text(
                    text = "توزيع التطبيقات والفئات",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Doughnut Chart using Canvas
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                var startAngle = -90f
                                appUsageItems.forEach { item ->
                                    val sweepAngle = (item.percentage / 100f) * 360f
                                    drawArc(
                                        color = Color(item.color),
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 25f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                    startAngle += sweepAngle
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("التحليل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("الذكي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Legend
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            appUsageItems.take(4).forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(item.color))
                                    )
                                    Text(text = item.appName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    Text(text = "${item.percentage.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Detailed Usage List
            item {
                Text(
                    text = "تفاصيل الحركات الفردية",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            items(appUsageItems) { item ->
                AppUsageListItem(item = item)
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AppUsageListItem(item: AppUsageItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(item.color).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when(item.category) {
                    "فيديو" -> Icons.Default.PlayCircle
                    "وسائط" -> Icons.Default.SmartDisplay
                    "تواصل" -> Icons.Default.Public
                    "مراسلة" -> Icons.Default.Chat
                    "ترفيه" -> Icons.Default.SportsEsports
                    else -> Icons.Default.Layers
                }
                Icon(imageVector = icon, contentDescription = null, tint = Color(item.color), modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = "التصنيف: ${item.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (item.bytesUsed > 0) NetworkUtils.formatBytes(item.bytesUsed) else "تحليل دقيق",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                
                LinearProgressIndicator(
                    progress = item.percentage / 100f,
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = Color(item.color),
                    trackColor = Color(item.color).copy(alpha = 0.1f)
                )
            }
        }
    }
}
