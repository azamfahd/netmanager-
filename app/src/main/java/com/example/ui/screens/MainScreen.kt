package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.data.model.UpdateCheckState
import com.example.ui.components.AppUpdateDialog
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard),
    FINANCE("المالية والأرباح", Icons.Default.MonetizationOn),
    ACTIVE_USERS("المتصلين", Icons.Default.Group),
    NODES("النقاط", Icons.Default.CellTower),
    CARDS("الكروت", Icons.Default.ConfirmationNumber),
    POS_AGENTS("نقاط البيع", Icons.Default.Store),
    PROFILES("الباقات", Icons.Default.Tune),
    TOOLS("الأدوات", Icons.Default.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onTriggerSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val networkNodes by viewModel.networkNodes.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val printableCards by viewModel.printableCards.collectAsStateWithLifecycle()
    val posAgents by viewModel.posAgents.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var showBatchGeneratorDialog by remember { mutableStateOf(false) }
    var preselectedAgentIdForBatch by remember { mutableLongStateOf(0L) }
    var isShowingPrintScreen by remember { mutableStateOf(false) }
    var isShowingAdminAuditScreen by remember { mutableStateOf(false) }
    var isShowingSystemEventLogScreen by remember { mutableStateOf(false) }
    var selectedSessionForDetailScreen by remember { mutableStateOf<com.example.data.model.UserSessionRecord?>(null) }
    var showWelcomeDialog by remember { mutableStateOf(true) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val allCards by viewModel.cards.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    val showUpdateBanner by viewModel.showUpdateBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // If showing system event log screen
    if (isShowingSystemEventLogScreen) {
        BackHandler { isShowingSystemEventLogScreen = false }
        SystemEventLogScreen(
            viewModel = viewModel,
            onBackClick = { isShowingSystemEventLogScreen = false }
        )
        return
    }

    // If showing admin audit and permissions screen
    if (isShowingAdminAuditScreen) {
        BackHandler { isShowingAdminAuditScreen = false }
        val adminUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
        val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
        AdminAuditScreen(
            adminUsers = adminUsers,
            auditLogs = auditLogs,
            onBackClick = { isShowingAdminAuditScreen = false },
            onAddAdmin = { newAdmin -> viewModel.addAdminUser(newAdmin) }
        )
        return
    }

    // If showing printable vouchers screen
    if (isShowingPrintScreen) {
        BackHandler { isShowingPrintScreen = false }
        VoucherPrintScreen(
            cards = printableCards,
            onBackClick = { isShowingPrintScreen = false }
        )
        return
    }

    // If showing detailed user session screen
    selectedSessionForDetailScreen?.let { targetSession ->
        BackHandler { selectedSessionForDetailScreen = null }
        val sessionHistory by viewModel.getLoginHistoryForUser(targetSession.username).collectAsStateWithLifecycle(emptyList())
        val subscriberProfile by viewModel.getSubscriberProfileFlow(targetSession.username).collectAsStateWithLifecycle(null)
        UserSessionDetailScreen(
            session = targetSession,
            subscriberProfile = subscriberProfile,
            loginHistory = sessionHistory,
            onBackClick = { selectedSessionForDetailScreen = null },
            onKickUser = if (targetSession.isOnline) {
                {
                    viewModel.kickUser(
                        com.example.data.model.HotspotActiveUser(
                            id = targetSession.id,
                            user = targetSession.username,
                            address = targetSession.ipAddress,
                            macAddress = targetSession.macAddress,
                            uptime = targetSession.duration,
                            bytesIn = targetSession.bytesIn,
                            bytesOut = targetSession.bytesOut
                        )
                    )
                    selectedSessionForDetailScreen = null
                }
            } else null,
            onRefresh = { viewModel.refreshData() }
        )
        return
    }

    // Handle back press to return to Dashboard tab
    BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
        currentTab = AppTab.DASHBOARD
    }

    val connectedState = connectionState as? ConnectionState.Connected
    val routerTitle = connectedState?.routerName ?: "راوتر ميكروتك"
    val routerIp = connectedState?.ipAddress ?: ""

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentTab) {
                            AppTab.DASHBOARD -> routerTitle
                            AppTab.FINANCE -> "الأرباح والمبيعات والإحصائيات"
                            AppTab.ACTIVE_USERS -> "المستخدمين المتصلين ($routerIp)"
                            AppTab.NODES -> "نقاط وأبراج الشبكة (${networkNodes.size})"
                            AppTab.CARDS -> "إدارة كروت الشبكة"
                            AppTab.POS_AGENTS -> "نقاط البيع والموزعين (${posAgents.size})"
                            AppTab.PROFILES -> "باقات السرعات والأسعار"
                            AppTab.TOOLS -> "أدوات فحص وتحكم الشبكة"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
                    val systemLogs by viewModel.systemEventLogs.collectAsStateWithLifecycle()

                    // System Event Log Icon button
                    IconButton(
                        onClick = { isShowingSystemEventLogScreen = true },
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .testTag("btn_system_event_log_topbar")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.ListAlt,
                                contentDescription = "سجل أحداث وعمليات النظام",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            if (systemLogs.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(StatusActiveGreen)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    // Admin Audit & Router Users button
                    IconButton(
                        onClick = { isShowingAdminAuditScreen = true },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("btn_admin_audit_topbar")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "سجل التدقيق وصلاحيات المديرين",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            if (auditLogs.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                        IconButton(
                            onClick = { showUpdateDialog = true },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .testTag("btn_topbar_update_badge")
                        ) {
                            BadgedBox(badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("تحديث", fontSize = 9.sp)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "تحديث جديد متاح",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    val pillText = when {
                        connectedState?.isDemo == true -> "تجريبي"
                        connectedState?.isOffline == true -> "كاش محلي"
                        connectedState?.isSyncing == true -> "مزامنة..."
                        else -> "متصل"
                    }
                    val pillType = when {
                        connectedState?.isDemo == true -> StatusType.INFO
                        connectedState?.isOffline == true -> StatusType.WARNING
                        connectedState?.isSyncing == true -> StatusType.INFO
                        else -> StatusType.SUCCESS
                    }
                    StatusPill(
                        text = pillText,
                        type = pillType,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .clickable { currentTab = tab }
                                .testTag("nav_item_${tab.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (tab == AppTab.ACTIVE_USERS && activeUsers.isNotEmpty()) {
                                    BadgedBox(badge = {
                                        Badge { Text("${activeUsers.size}") }
                                    }) {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else if (tab == AppTab.NODES && networkNodes.isNotEmpty()) {
                                    BadgedBox(badge = {
                                        Badge { Text("${networkNodes.count { it.isOnline }}") }
                                    }) {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Offline Local Data Banner (شعار البيانات المحلية أوفلاين)
            if (connectedState?.isOffline == true || connectedState?.isFromCache == true) {
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("offline_local_data_badge")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFE0B2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "⚡ وضع البيانات المحلية (أوفلاين)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFBF360C)
                                )
                                Text(
                                    text = "يتم عرض الكروت والبيانات المحفوظة محلياً على جهازك • جاهز للعمل والطباعة",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.refreshData() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة المحاولة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // App Update Notification Banner (إشعار التحديث الجديد المباشر من GitHub Releases)
            if (showUpdateBanner && updateCheckState is UpdateCheckState.UpdateAvailable) {
                val updateState = updateCheckState as UpdateCheckState.UpdateAvailable
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("app_update_notification_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showUpdateDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "يوجد تحديث جديد للبرنامج (${updateState.release.tagName})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "اضغط للتنزيل والتثبيت مباشرة بدون فقد البيانات",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = { showUpdateDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("تحديث", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { viewModel.dismissUpdateBanner() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق التنبيه",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    AppTab.DASHBOARD -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToActiveUsers = { currentTab = AppTab.ACTIVE_USERS },
                            onNavigateToCards = { currentTab = AppTab.CARDS },
                            onNavigateToProfiles = { currentTab = AppTab.PROFILES },
                            onOpenBatchGenerator = { showBatchGeneratorDialog = true },
                            onOpenPrintScreen = {
                                viewModel.setPrintableCards(viewModel.cards.value)
                                isShowingPrintScreen = true
                            },
                            onNavigateToFinance = { currentTab = AppTab.FINANCE }
                        )
                    }
                    AppTab.FINANCE -> {
                        FinanceAnalyticsScreen(
                            viewModel = viewModel,
                            onNavigateToCards = { currentTab = AppTab.CARDS },
                            onNavigateToPosAgents = { currentTab = AppTab.POS_AGENTS }
                        )
                    }
                    AppTab.ACTIVE_USERS -> {
                        ActiveUsersScreen(
                            viewModel = viewModel,
                            onOpenSessionDetail = { session ->
                                selectedSessionForDetailScreen = session
                            }
                        )
                    }
                    AppTab.NODES -> {
                        NetworkNodesScreen(
                            viewModel = viewModel
                        )
                    }
                    AppTab.CARDS -> {
                        CardsScreen(
                            viewModel = viewModel,
                            onOpenBatchGenerator = {
                                preselectedAgentIdForBatch = 0L
                                showBatchGeneratorDialog = true
                            },
                            onOpenPrintScreen = { isShowingPrintScreen = true },
                            onOpenSessionDetail = { session ->
                                selectedSessionForDetailScreen = session
                            }
                        )
                    }
                    AppTab.POS_AGENTS -> {
                        PosAgentsScreen(
                            viewModel = viewModel,
                            onOpenBatchGeneratorForAgent = { agent ->
                                preselectedAgentIdForBatch = agent.id
                                showBatchGeneratorDialog = true
                            }
                        )
                    }
                    AppTab.PROFILES -> {
                        ProfilesScreen(
                            viewModel = viewModel
                        )
                    }
                    AppTab.TOOLS -> {
                        NetworkToolsScreen(
                            viewModel = viewModel,
                            onOpenSystemEventLog = { isShowingSystemEventLogScreen = true },
                            onTriggerSignIn = onTriggerSignIn
                        )
                    }
                }
            }
        }
    }

    if (showBatchGeneratorDialog) {
        VoucherBatchDialog(
            profiles = profiles,
            posAgents = posAgents,
            preselectedAgentId = preselectedAgentIdForBatch,
            onDismiss = {
                showBatchGeneratorDialog = false
                preselectedAgentIdForBatch = 0L
            },
            onGenerate = { config ->
                viewModel.generateBatch(config)
            },
            onAddSingleCard = { card, agentId, agentName, discount ->
                viewModel.addSingleCard(card, agentId, agentName, discount)
            }
        )
    }

    // Beautiful Welcome Connection Alert
    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = { showWelcomeDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(StatusActiveGreenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusActiveGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "🎉 تم الاتصال بالراوتر بنجاح!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "تم التحقق من بياناتك والاتصال براوتر ميكروتك بنجاح وبشكل مباشر.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "الراوتر: $routerTitle ($routerIp)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "المستخدمون النشطون: ${activeUsers.size} جهاز متصل",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "الكروت المتاحة: ${allCards.size} كرت هوتسبوت",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Text(
                        text = "✓ تم تفعيل المزامنة اللحظية ومراقبة السرعات واستهلاك البيانات.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusActiveGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showWelcomeDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بدء إدارة الشبكة الآن", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showUpdateDialog) {
        val checkState = updateCheckState
        if (checkState is UpdateCheckState.UpdateAvailable) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AppUpdateDialog(
                release = checkState.release,
                currentVersion = viewModel.currentAppVersion,
                downloadState = updateDownloadState,
                onDownloadAndInstall = { url ->
                    viewModel.downloadAndInstallUpdate(context, url)
                },
                onInstallDownloaded = { file ->
                    viewModel.installDownloadedApk(context, file)
                },
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}
