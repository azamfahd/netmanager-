package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionState
import com.example.data.network.NetworkUtils
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.MetricCard
import com.example.ui.components.ResourceProgressCard
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToActiveUsers: () -> Unit,
    onNavigateToCards: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onOpenBatchGenerator: () -> Unit,
    onOpenPrintScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val resource by viewModel.resourceState.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    var showRebootDialog by remember { mutableStateOf(false) }

    val isConnectedAsDemo = (connectionState as? ConnectionState.Connected)?.isDemo == true

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Live Router Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("router_status_banner"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = resource.boardName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "إصدار RouterOS: ${resource.version}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        StatusPill(
                            text = if (isConnectedAsDemo) "وضع تجريبي (Demo)" else "متصل بالراوتر مباشر",
                            type = if (isConnectedAsDemo) StatusType.INFO else StatusType.SUCCESS
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مدة تشغيل الراوتر (Uptime):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = resource.uptime,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshData() },
                            modifier = Modifier.testTag("dashboard_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث البيانات الآن",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Throughput / Speed Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "حركة مرور الشبكة المباشرة (Traffic)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "مباشر الآن",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusActiveGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SpeedGauge(
                            title = "سرعة التنزيل (Download / Rx)",
                            speed = NetworkUtils.formatSpeed(resource.rxSpeedKbps),
                            icon = Icons.Default.ArrowDownward,
                            color = StatusActiveGreen
                        )
                        SpeedGauge(
                            title = "سرعة الرفع (Upload / Tx)",
                            speed = NetworkUtils.formatSpeed(resource.txSpeedKbps),
                            icon = Icons.Default.ArrowUpward,
                            color = NetworkCyan
                        )
                    }
                }
            }
        }

        // Metrics 2x2 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "المستخدمين النشطون",
                        value = "${activeUsers.size}",
                        subtitle = "متصلون الآن بالهوتسبوت",
                        icon = Icons.Default.Group,
                        iconTint = RouterBlue,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToActiveUsers() },
                        testTag = "metric_active_users"
                    )

                    MetricCard(
                        title = "إجمالي كروت الشبكة",
                        value = "${cards.size}",
                        subtitle = "${cards.count { !it.isActive && !it.isExpired }} كرت متاح للبيع",
                        icon = Icons.Default.ConfirmationNumber,
                        iconTint = NetworkCyan,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToCards() },
                        testTag = "metric_total_cards"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val memUsedPercent = (100 - (resource.freeMemoryBytes.toFloat() / resource.totalMemoryBytes * 100)).toInt()
                    ResourceProgressCard(
                        title = "استهلاك المعالج CPU",
                        percentage = resource.cpuLoad,
                        detailText = "حمل المعالج: ${resource.cpuLoad}%",
                        barColor = if (resource.cpuLoad > 75) MaterialTheme.colorScheme.error else RouterBlue,
                        modifier = Modifier.weight(1f)
                    )

                    ResourceProgressCard(
                        title = "استهلاك الذاكرة RAM",
                        percentage = memUsedPercent.coerceIn(10, 95),
                        detailText = "${NetworkUtils.formatBytes(resource.freeMemoryBytes)} متاح",
                        barColor = NetworkCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Actions
        item {
            Text(
                text = "الإجراءات والعمليات السريعة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    icon = Icons.Default.AddCard,
                    label = "توليد كروت",
                    onClick = onOpenBatchGenerator,
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    icon = Icons.Default.Print,
                    label = "طباعة كروت",
                    onClick = onOpenPrintScreen,
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    icon = Icons.Default.Tune,
                    label = "باقات السرعة",
                    onClick = onNavigateToProfiles,
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    icon = Icons.Default.RestartAlt,
                    label = "إعادة تشغيل",
                    onClick = { showRebootDialog = true },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Active Users Quick Preview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المستخدمين المتصلين حالياً (${activeUsers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onNavigateToActiveUsers,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("عرض الكل")
                }
            }
        }

        if (activeUsers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا يوجد مستخدمون نشطون حالياً",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(minOf(activeUsers.size, 3)) { index ->
                val user = activeUsers[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = user.user,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "IP: ${user.address} • MAC: ${user.macAddress}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(text = user.uptime, type = StatusType.SUCCESS)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "تحميل: ${NetworkUtils.formatBytes(user.bytesOut)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showRebootDialog) {
        ConfirmDialog(
            title = "إعادة تشغيل راوتر ميكروتك",
            message = "هل أنت متأكد من رغبتك في إرسال أمر إعادة تشغيل الراوتر؟ سيتم قطع الاتصال بالشبكة مؤقتاً لجميع المستخدمين.",
            confirmText = "إعادة التشغيل الآن",
            isDestructive = true,
            onConfirm = { viewModel.rebootRouter() },
            onDismiss = { showRebootDialog = false }
        )
    }
}

@Composable
fun SpeedGauge(
    title: String,
    speed: String,
    icon: ImageVector,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = speed,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}
