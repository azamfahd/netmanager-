package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionState
import com.example.data.model.HotspotActiveUser
import com.example.data.network.NetworkUtils
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.CyberGreenBright
import com.example.ui.theme.CyberGreenNeon
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToActiveUsers: () -> Unit,
    onNavigateToCards: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToNodes: () -> Unit,
    onOpenBatchGenerator: () -> Unit,
    onOpenPrintScreen: () -> Unit,
    onNavigateToFinance: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val resource by viewModel.resourceState.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val networkNodes by viewModel.networkNodes.collectAsStateWithLifecycle()
    val bandwidthHistory by viewModel.bandwidthHistory.collectAsStateWithLifecycle()
    val revenueSummary by viewModel.revenueAnalytics.collectAsStateWithLifecycle()
    val peakHourStats by viewModel.peakHourStats.collectAsStateWithLifecycle()
    val packageStats by viewModel.packageConsumptionStats.collectAsStateWithLifecycle()
    val detailedPackageStats by viewModel.detailedPackageProfitStats.collectAsStateWithLifecycle()
    val selectedTimeFilter by viewModel.selectedTimeFilter.collectAsStateWithLifecycle()

    var showRebootDialog by remember { mutableStateOf(false) }

    val isConnectedAsDemo = (connectionState as? ConnectionState.Connected)?.isDemo == true
    val connectedIp = (connectionState as? ConnectionState.Connected)?.ipAddress ?: "192.168.88.1"
    val connectedRouterName = (connectionState as? ConnectionState.Connected)?.routerName ?: resource.boardName

    val onlineNodesCount = remember(networkNodes) { networkNodes.count { it.isOnline } }
    val availableCardsCount = remember(cards) { cards.count { !it.isActive && !it.isExpired } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 1. Cyber Router Header Hub (شريط الهوية والراوتر السيبراني)
        item {
            CyberRouterHubCard(
                routerName = connectedRouterName,
                boardName = resource.boardName,
                version = resource.version,
                ipAddress = connectedIp,
                uptime = resource.uptime,
                isDemo = isConnectedAsDemo,
                onRefresh = { viewModel.refreshData() }
            )
        }

        // 2. Real-Time Cyber Traffic HUD (حركة المرور والسرعة الحية)
        item {
            CyberTrafficHudCard(
                rxSpeedKbps = resource.rxSpeedKbps,
                txSpeedKbps = resource.txSpeedKbps
            )
        }

        // 3. Advanced Network Nodes & Towers Summary Card (قسم الأبراج ونقاط البث الاحترافي)
        item {
            CyberTowersSummaryCard(
                networkNodes = networkNodes,
                onNavigateToNodes = onNavigateToNodes
            )
        }

        // 4. Interactive 2x2 Cyber Metrics Matrix (مصفوفة الإحصائيات الذكية)
        item {
            CyberMetricsMatrix(
                activeUsersCount = activeUsers.size,
                totalCardsCount = cards.size,
                availableCardsCount = cards.count { !it.isActive && !it.isExpired },
                cpuLoad = resource.cpuLoad,
                freeMemoryBytes = resource.freeMemoryBytes,
                totalMemoryBytes = resource.totalMemoryBytes,
                onActiveUsersClick = onNavigateToActiveUsers,
                onCardsClick = onNavigateToCards
            )
        }

        // 5. Clean Financial Overview & Direct Navigation to Finance Tab
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToFinance() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                    .clip(CircleShape)
                                    .background(Color(0xFF00C853).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF00C853),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "ملخص الأرباح والمبيعات",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تم نقل وتخصيص كافة الإحصائيات والأرباح في قسم مستقل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "الانتقال لقسم الأرباح",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF00C853).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("إجمالي المبيعات", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00C853))
                                Text(
                                    "${"%,d".format(revenueSummary.totalEstimatedRevenue.toLong())} ر.ي",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00C853)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF00B0FF).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("صافي الأرباح", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00B0FF))
                                Text(
                                    "${"%,d".format(revenueSummary.totalNetProfit.toLong())} ر.ي",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00B0FF)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFFFB300).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("الكروت غير المباعة", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF8F00))
                                Text(
                                    "${revenueSummary.unusedCardsCount} كرت",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8F00)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToFinance,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح قسم الأرباح والمبيعات والإحصائيات الشهرية الكاملة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 6. Cyber Quick Actions (العمليات والإجراءات السريعة)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "العمليات والإجراءات السريعة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "التحكم المباشر",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGreenPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CyberActionCard(
                        icon = Icons.Default.AddCard,
                        title = "توليد كروت",
                        subtitle = "طباعة دفعة",
                        accentColor = CyberGreenBright,
                        onClick = onOpenBatchGenerator,
                        modifier = Modifier.weight(1f),
                        testTag = "action_generate_cards"
                    )

                    CyberActionCard(
                        icon = Icons.Default.Print,
                        title = "طباعة كروت",
                        subtitle = "تصدير فوري",
                        accentColor = NetworkCyan,
                        onClick = onOpenPrintScreen,
                        modifier = Modifier.weight(1f),
                        testTag = "action_print_cards"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CyberActionCard(
                        icon = Icons.Default.Tune,
                        title = "باقات السرعة",
                        subtitle = "إدارة الخطط",
                        accentColor = NetworkCyan,
                        onClick = onNavigateToProfiles,
                        modifier = Modifier.weight(1f),
                        testTag = "action_speed_profiles"
                    )

                    CyberActionCard(
                        icon = Icons.Default.RestartAlt,
                        title = "إعادة تشغيل",
                        subtitle = "إعادة تشغيل الراوتر",
                        accentColor = StatusErrorRed,
                        onClick = { showRebootDialog = true },
                        modifier = Modifier.weight(1f),
                        testTag = "action_reboot_router"
                    )
                }
            }
        }

        // 6. Recent Active Hotspot Users (أحدث المستخدمين المتصلين)
        item {
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CyberGreenNeon)
                    )
                    Text(
                        text = "المستخدمين المتصلين (${activeUsers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onNavigateToActiveUsers,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyberGreenPrimary
                    ),
                    modifier = Modifier.testTag("dashboard_view_all_users_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("عرض الكل", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (activeUsers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا يوجد مستخدمون نشطون متصلون حالياً بالهوتسبوت",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(minOf(activeUsers.size, 4)) { index ->
                val user = activeUsers[index]
                CyberActiveUserItem(user = user)
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showRebootDialog) {
        ConfirmDialog(
            title = "إعادة تشغيل راوتر ميكروتك",
            message = "هل أنت متأكد من رغبتك في إرسال أمر إعادة تشغيل الراوتر؟ سيتم قطع الاتصال بالشبكة مؤقتاً لجميع المستخدمين حتى يعود الراوتر للعمل.",
            confirmText = "إعادة التشغيل الآن",
            isDestructive = true,
            onConfirm = { viewModel.rebootRouter() },
            onDismiss = { showRebootDialog = false }
        )
    }
}

/**
 * 1. Cyber Router Header Hub Card
 */
@Composable
private fun CyberRouterHubCard(
    routerName: String,
    boardName: String,
    version: String,
    ipAddress: String,
    uptime: String,
    isDemo: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("router_status_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(CyberGreenBright, CyberGreenPrimary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = routerName.ifEmpty { boardName },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            CyberPulseDot()
                        }

                        Text(
                            text = "IP: $ipAddress • RouterOS: $version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(
                        text = if (isDemo) "وضع تجريبي" else "متصل حقيقي",
                        type = if (isDemo) StatusType.INFO else StatusType.SUCCESS
                    )

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dashboard_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث البيانات الآن",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Uptime & Status Strip
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = CyberGreenPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "مدة التشغيل (Uptime):",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = uptime,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = CyberGreenPrimary
                    )
                }
            }
        }
    }
}

/**
 * Animated Cyber Radar Pulse Dot
 */
@Composable
private fun CyberPulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(CyberGreenNeon.copy(alpha = alpha))
    )
}

/**
 * 2. Real-Time Cyber Traffic HUD (شاشات حركة المرور والسرعة الحية)
 */
@Composable
private fun CyberTrafficHudCard(
    rxSpeedKbps: Double,
    txSpeedKbps: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cyber_traffic_hud"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CyberGreenBright.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = CyberGreenBright,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "مراقبة سرعة وتدفق البيانات (Traffic)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = CyberGreenBright.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CyberGreenNeon)
                        )
                        Text(
                            text = "بث فوري",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGreenBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download Gauge
                CyberSpeedMeter(
                    title = "التنزيل (Download / Rx)",
                    speed = NetworkUtils.formatSpeed(rxSpeedKbps),
                    icon = Icons.Default.ArrowDownward,
                    color = CyberGreenBright,
                    trafficFraction = (rxSpeedKbps.toFloat() / 10240f).coerceIn(0.08f, 1f),
                    modifier = Modifier.weight(1f)
                )

                // Upload Gauge
                CyberSpeedMeter(
                    title = "الرفع (Upload / Tx)",
                    speed = NetworkUtils.formatSpeed(txSpeedKbps),
                    icon = Icons.Default.ArrowUpward,
                    color = NetworkCyan,
                    trafficFraction = (txSpeedKbps.toFloat() / 5120f).coerceIn(0.08f, 1f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual Cyber Speed Meter
 */
@Composable
private fun CyberSpeedMeter(
    title: String,
    speed: String,
    icon: ImageVector,
    color: Color,
    trafficFraction: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.06f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = speed,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { trafficFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
        }
    }
}

/**
 * 3. Advanced Cyber Towers & Distribution Nodes Summary Card (بطاقة أبراج ونقاط البث الاحترافية)
 */
@Composable
private fun CyberTowersSummaryCard(
    networkNodes: List<com.example.data.model.NetworkNode>,
    onNavigateToNodes: () -> Unit
) {
    val onlineCount = networkNodes.count { it.isOnline }
    val totalCount = networkNodes.size
    val towersCount = networkNodes.count { it.nodeType == com.example.data.model.NetworkNodeType.MAIN_TOWER }
    val distCount = networkNodes.count { it.nodeType == com.example.data.model.NetworkNodeType.DISTRIBUTION }
    val homeCount = networkNodes.count { it.nodeType == com.example.data.model.NetworkNodeType.HOME_NODE }
    val totalClients = networkNodes.sumOf { it.connectedUsers }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToNodes() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, NetworkCyan.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            .clip(CircleShape)
                            .background(NetworkCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = NetworkCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "قسم الأبراج ونقاط البث",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "حالة أبراج التوزيع وربط الشبكة اللاسلكية",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = (if (onlineCount == totalCount && totalCount > 0) StatusActiveGreen else StatusErrorRed).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$onlineCount/$totalCount متصل",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (onlineCount == totalCount && totalCount > 0) StatusActiveGreen else StatusErrorRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("الأبراج الرئيسية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$towersCount برج", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("نقاط التوزيع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$distCount نقطة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("المشتركين/العملاء", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalClients عميل", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberGreenBright)
                    }
                }
            }

            OutlinedButton(
                onClick = onNavigateToNodes,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("استعراض خريطة الشبكة والأبراج بالكامل", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}


/**
 * 4. Interactive 2x2 Cyber Metrics Matrix
 */
@Composable
private fun CyberMetricsMatrix(
    activeUsersCount: Int,
    totalCardsCount: Int,
    availableCardsCount: Int,
    cpuLoad: Int,
    freeMemoryBytes: Long,
    totalMemoryBytes: Long,
    onActiveUsersClick: () -> Unit,
    onCardsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Users Card
            CyberStatCard(
                title = "المستخدمين النشطين",
                value = "$activeUsersCount",
                subtitle = "متصلون الآن بالهوتسبوت",
                icon = Icons.Default.Group,
                accentColor = CyberGreenBright,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onActiveUsersClick() },
                testTag = "metric_active_users"
            )

            // Total Cards Card
            CyberStatCard(
                title = "إجمالي الكروت",
                value = "$totalCardsCount",
                subtitle = "$availableCardsCount كرت متاح للبيع",
                icon = Icons.Default.ConfirmationNumber,
                accentColor = NetworkCyan,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCardsClick() },
                testTag = "metric_total_cards"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CPU Load Card
            val validCpu = cpuLoad.coerceIn(0, 100)
            val cpuColor = if (validCpu > 75) StatusErrorRed else CyberGreenBright
            CyberProgressCard(
                title = "استهلاك المعالج CPU",
                percentage = validCpu,
                detailText = "حمل المعالج: $validCpu%",
                accentColor = cpuColor,
                icon = Icons.Default.Memory,
                modifier = Modifier.weight(1f)
            )

            // RAM Memory Card
            val memUsedPercent = if (totalMemoryBytes > 0L) {
                ((totalMemoryBytes - freeMemoryBytes).toFloat() / totalMemoryBytes * 100f).toInt().coerceIn(1, 99)
            } else {
                35
            }
            CyberProgressCard(
                title = "استهلاك الذاكرة RAM",
                percentage = memUsedPercent,
                detailText = "${NetworkUtils.formatBytes(freeMemoryBytes)} متاح",
                accentColor = NetworkCyan,
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual Cyber Metric Stat Card
 */
@Composable
private fun CyberStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = "cyber_stat_card"
) {
    Card(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Individual Cyber Progress Card (CPU / RAM)
 */
@Composable
private fun CyberProgressCard(
    title: String,
    percentage: Int,
    detailText: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = detailText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 5. Cyber Quick Action Button
 */
@Composable
private fun CyberActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "cyber_action_card"
) {
    Card(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * 6. Cyber Active User Item
 */
@Composable
private fun CyberActiveUserItem(user: HotspotActiveUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CyberGreenBright.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = CyberGreenBright,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = user.user,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "IP: ${user.address} • MAC: ${user.macAddress}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusPill(text = user.uptime, type = StatusType.SUCCESS)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "تحميل: ${NetworkUtils.formatBytes(user.bytesOut)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}
