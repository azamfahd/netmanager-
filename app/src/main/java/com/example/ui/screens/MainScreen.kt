package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.ConnectionState
import com.example.data.model.UpdateCheckState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.navigation.Screen
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: ImageVector, val route: Screen) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard, Screen.Dashboard),
    ACTIVE_USERS("المتصلين", Icons.Default.Group, Screen.ActiveUsers),
    CARDS("الكروت", Icons.Default.ConfirmationNumber, Screen.Cards),
    POS_AGENTS("الموزعون", Icons.Default.Store, Screen.POSAgents),
    PROFILES("الباقات", Icons.Default.Tune, Screen.Profiles),
    NODES("الأبراج", Icons.Default.CellTower, Screen.Nodes),
    TOOLS("الأدوات", Icons.Default.Build, Screen.Tools),
    FINANCE("المالية", Icons.Default.MonetizationOn, Screen.Finance),
    LOGS("سجل العمليات", Icons.AutoMirrored.Filled.ListAlt, Screen.SystemEventLog)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onTriggerSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val networkNodes by viewModel.networkNodes.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val posAgents by viewModel.posAgents.collectAsStateWithLifecycle()
    val allCards by viewModel.cards.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    val showUpdateBanner by viewModel.showUpdateBanner.collectAsStateWithLifecycle()
    
    var showBatchGeneratorDialog by remember { mutableStateOf(false) }
    var preselectedAgentIdForBatch by remember { mutableLongStateOf(0L) }
    var showWelcomeDialog by remember { mutableStateOf(true) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val connectedState = connectionState as? ConnectionState.Connected
    val routerTitle = connectedState?.routerName ?: "راوتر ميكروتك"
    val routerIp = connectedState?.ipAddress ?: ""

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NetManager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إدارة متقدمة للشبكة",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                NavigationDrawerItem(
                    label = { Text("نقاط وأبراج الشبكة") },
                    icon = { Icon(Icons.Default.CellTower, null) },
                    selected = currentDestination?.hasRoute<Screen.Nodes>() == true,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Nodes)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                NavigationDrawerItem(
                    label = { Text("الموزعون ونقاط البيع") },
                    icon = { Icon(Icons.Default.Store, null) },
                    selected = currentDestination?.hasRoute<Screen.POSAgents>() == true,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.POSAgents)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                NavigationDrawerItem(
                    label = { Text("باقات السرعات") },
                    icon = { Icon(Icons.Default.Tune, null) },
                    selected = currentDestination?.hasRoute<Screen.Profiles>() == true,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Profiles)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                NavigationDrawerItem(
                    label = { Text("أدوات الشبكة") },
                    icon = { Icon(Icons.Default.Build, null) },
                    selected = currentDestination?.hasRoute<Screen.Tools>() == true,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Tools)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                NavigationDrawerItem(
                    label = { Text("سجل العمليات") },
                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                    selected = currentDestination?.hasRoute<Screen.SystemEventLog>() == true,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.SystemEventLog)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                NavigationDrawerItem(
                    label = { Text("التدقيق والصلاحيات") },
                    icon = { Icon(Icons.Default.AdminPanelSettings, null) },
                    selected = currentDestination?.hasRoute<Screen.AdminAudit>() == true,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.AdminAudit)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = routerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "القائمة")
                        }
                    },
                    actions = {
                        if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                            IconButton(onClick = { showUpdateDialog = true }) {
                                BadgedBox(badge = {
                                    Badge { Text("!", fontSize = 10.sp) }
                                }) {
                                    Icon(Icons.Default.SystemUpdate, "تحديث متاح")
                                }
                            }
                        }
                        
                        StatusPill(
                            text = if (connectedState?.isOffline == true) "أوفلاين" else "متصل",
                            type = if (connectedState?.isOffline == true) StatusType.WARNING else StatusType.SUCCESS,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val tabsToShow = MainTab.entries.toList()
                    
                    tabsToShow.forEach { tab ->
                        val isSelected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Screen.Dashboard) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab == MainTab.ACTIVE_USERS && activeUsers.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text("${activeUsers.size}")
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Update Banner
                if (showUpdateBanner && updateCheckState is UpdateCheckState.UpdateAvailable) {
                    UpdateBanner(
                        tagName = (updateCheckState as UpdateCheckState.UpdateAvailable).release.tagName,
                        onUpdateClick = { showUpdateDialog = true },
                        onDismiss = { viewModel.dismissUpdateBanner() }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard,
                    modifier = Modifier.weight(1f),
                    enterTransition = { fadeIn() + slideInHorizontally() },
                    exitTransition = { fadeOut() + slideOutHorizontally() },
                    popEnterTransition = { fadeIn() + slideInHorizontally() },
                    popExitTransition = { fadeOut() + slideOutHorizontally() }
                ) {
                    composable<Screen.Dashboard> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToActiveUsers = { navController.navigate(Screen.ActiveUsers) },
                            onNavigateToCards = { navController.navigate(Screen.Cards) },
                            onNavigateToProfiles = { navController.navigate(Screen.Profiles) },
                            onOpenBatchGenerator = { showBatchGeneratorDialog = true },
                            onOpenPrintScreen = {
                                viewModel.setPrintableCards(viewModel.cards.value)
                                navController.navigate(Screen.VoucherPrint)
                            },
                            onNavigateToFinance = { navController.navigate(Screen.Finance) }
                        )
                    }
                    composable<Screen.ActiveUsers> {
                        ActiveUsersScreen(
                            viewModel = viewModel,
                            onOpenSessionDetail = { session ->
                                navController.navigate(Screen.UserSessionDetail(session.username))
                            }
                        )
                    }
                    composable<Screen.Cards> {
                        CardsScreen(
                            viewModel = viewModel,
                            onOpenBatchGenerator = {
                                preselectedAgentIdForBatch = 0L
                                showBatchGeneratorDialog = true
                            },
                            onOpenPrintScreen = { navController.navigate(Screen.VoucherPrint) },
                            onOpenSessionDetail = { session ->
                                navController.navigate(Screen.UserSessionDetail(session.username))
                            }
                        )
                    }
                    composable<Screen.Finance> {
                        FinanceAnalyticsScreen(
                            viewModel = viewModel,
                            onNavigateToCards = { navController.navigate(Screen.Cards) },
                            onNavigateToPosAgents = { navController.navigate(Screen.POSAgents) }
                        )
                    }
                    composable<Screen.Nodes> {
                        NetworkNodesScreen(viewModel = viewModel)
                    }
                    composable<Screen.POSAgents> {
                        PosAgentsScreen(
                            viewModel = viewModel,
                            onOpenBatchGeneratorForAgent = { agent ->
                                preselectedAgentIdForBatch = agent.id
                                showBatchGeneratorDialog = true
                            }
                        )
                    }
                    composable<Screen.Profiles> {
                        ProfilesScreen(viewModel = viewModel)
                    }
                    composable<Screen.Tools> {
                        NetworkToolsScreen(
                            viewModel = viewModel,
                            onOpenSystemEventLog = { navController.navigate(Screen.SystemEventLog) },
                            onTriggerSignIn = onTriggerSignIn
                        )
                    }
                    composable<Screen.SystemEventLog> {
                        SystemEventLogScreen(
                            viewModel = viewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable<Screen.AdminAudit> {
                        val adminUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
                        val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
                        AdminAuditScreen(
                            adminUsers = adminUsers,
                            auditLogs = auditLogs,
                            onBackClick = { navController.popBackStack() },
                            onAddAdmin = { newAdmin -> viewModel.addAdminUser(newAdmin) }
                        )
                    }
                    composable<Screen.VoucherPrint> {
                        val printableCards by viewModel.printableCards.collectAsStateWithLifecycle()
                        VoucherPrintScreen(
                            cards = printableCards,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable<Screen.UserSessionDetail> { backStackEntry ->
                        val args = backStackEntry.toRoute<Screen.UserSessionDetail>()
                        val sessionHistory by viewModel.getLoginHistoryForUser(args.username).collectAsStateWithLifecycle(emptyList())
                        val subscriberProfile by viewModel.getSubscriberProfileFlow(args.username).collectAsStateWithLifecycle(null)
                        val activeUsersList by viewModel.activeUsers.collectAsStateWithLifecycle()
                        val targetSession = activeUsersList.find { it.user == args.username } ?: return@composable
                        
                        val userSessionRecord = com.example.data.model.UserSessionRecord(
                            id = targetSession.id,
                            username = targetSession.user,
                            ipAddress = targetSession.address,
                            macAddress = targetSession.macAddress,
                            duration = targetSession.uptime,
                            bytesIn = targetSession.bytesIn,
                            bytesOut = targetSession.bytesOut,
                            isOnline = true
                        )

                        UserSessionDetailScreen(
                            session = userSessionRecord,
                            subscriberProfile = subscriberProfile,
                            loginHistory = sessionHistory,
                            onBackClick = { navController.popBackStack() },
                            onKickUser = {
                                viewModel.kickUser(targetSession)
                                navController.popBackStack()
                            },
                            onRefresh = { viewModel.refreshData() }
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

    if (showWelcomeDialog) {
        WelcomeDialog(
            routerTitle = routerTitle,
            routerIp = routerIp,
            activeUsersCount = activeUsers.size,
            cardsCount = allCards.size,
            onDismiss = { showWelcomeDialog = false }
        )
    }

    if (showUpdateDialog) {
        val checkState = updateCheckState
        if (checkState is UpdateCheckState.UpdateAvailable) {
            val context = LocalContext.current
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

    val conflictState by viewModel.conflictPromptState.collectAsStateWithLifecycle()
    conflictState?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConflictPrompt(keepLocal = true) },
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("تنبيه تعارض بيانات (${conflict.recordType})", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تم اكتشاف اختلاف بين التعديل المحلي والبيانات الحالية في الراوتر للنقطة (${conflict.recordName}):")
                    Text("• التعديل المحلي: ${conflict.localValue}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• بيانات الراوتر: ${conflict.remoteValue}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("أي القيمتين تريد الاحتفاظ به وتطبيقها؟")
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissConflictPrompt(keepLocal = true) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("الاحتفاظ بالتعديل المحلي")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissConflictPrompt(keepLocal = false) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تطبيق بيانات الراوتر")
                }
            }
        )
    }
}

@Composable
fun UpdateBanner(tagName: String, onUpdateClick: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "يوجد تحديث جديد ($tagName)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اضغط للتنزيل والتثبيت مباشرة",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                TextButton(onClick = onUpdateClick) {
                    Text("تحديث")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun WelcomeDialog(
    routerTitle: String,
    routerIp: String,
    activeUsersCount: Int,
    cardsCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(StatusActiveGreenBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = StatusActiveGreen, modifier = Modifier.size(32.dp))
            }
        },
        title = { Text("تم الاتصال بنجاح!", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("متصل بـ $routerTitle ($routerIp)")
                Text("المستخدمون النشطون: $activeUsersCount")
                Text("الكروت المتاحة: $cardsCount")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("بدء الاستخدام")
            }
        }
    )
}
