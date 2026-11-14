package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.ConnectionState
import com.example.data.model.NodeStatus
import com.example.data.model.UpdateCheckState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.UpdateBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.navigation.Screen
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts

enum class MainTab(val title: String, val icon: ImageVector) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard),
    ACTIVE_USERS("المتصلين", Icons.Default.Group),
    CARDS("الكروت", Icons.Default.ConfirmationNumber),
    POS_AGENTS("الموزعون", Icons.Default.Store),
    PROFILES("الباقات", Icons.Default.Tune),
    NODES("الأبراج", Icons.Default.CellTower),
    TOOLS("الأدوات", Icons.Default.Build),
    FINANCE("المالية", Icons.Default.MonetizationOn),
    LOGS("سجل العمليات", Icons.AutoMirrored.Filled.ListAlt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onTriggerSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    
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

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = routerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (routerIp.isNotBlank()) {
                                Text(
                                    text = routerIp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshData() },
                        modifier = Modifier.testTag("btn_top_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث البيانات",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                        IconButton(onClick = { showUpdateDialog = true }) {
                            BadgedBox(badge = {
                                Badge { Text("!", fontSize = 10.sp) }
                            }) {
                                Icon(Icons.Default.SystemUpdate, "تحديث متاح", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    
                    StatusPill(
                        text = if (connectedState?.isOffline == true) "أوفلاين" else "متصل",
                        type = if (connectedState?.isOffline == true) StatusType.WARNING else StatusType.SUCCESS,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        val context = LocalContext.current
        val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                viewModel.logApplicationError("PERMISSION", "تم رفض إذن التنبيهات، لن يظهر شريط تقدم التحديث في التنبيهات.")
            }
        }

        LaunchedEffect(updateCheckState) {
            if (updateCheckState is UpdateCheckState.UpdateAvailable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permission = android.Manifest.permission.POST_NOTIFICATIONS
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(permission)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Update Banner
            if (showUpdateBanner && updateCheckState is UpdateCheckState.UpdateAvailable) {
                val availableRelease = (updateCheckState as UpdateCheckState.UpdateAvailable).release
                UpdateBanner(
                    tagName = availableRelease.tagName,
                    isMinorUpdate = availableRelease.isMinorUpdate,
                    downloadState = updateDownloadState,
                    onUpdateClick = { showUpdateDialog = true },
                    onDismiss = { viewModel.dismissUpdateBanner() },
                    onStopDownload = { viewModel.cancelDownloadUpdate() }
                )
            }

            NavHost(
                navController = navController,
                startDestination = Screen.MainPager,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() },
                popEnterTransition = { fadeIn() + slideInHorizontally() },
                popExitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                composable<Screen.MainPager> {
                    MainPagerContent(
                        viewModel = viewModel,
                        activeUsersCount = activeUsers.size,
                        hasOfflineNodes = networkNodes.any { it.effectiveStatus == NodeStatus.OFFLINE },
                        onOpenBatchGenerator = { preselectedId ->
                            preselectedAgentIdForBatch = preselectedId
                            showBatchGeneratorDialog = true
                        },
                        onNavigateToPrintStudio = {
                            navController.navigate(Screen.VoucherPrint)
                        },
                        onOpenPrintScreen = { cards ->
                            viewModel.setPrintableCards(cards)
                            navController.navigate(Screen.VoucherPrint)
                        },
                        onOpenSessionDetail = { username ->
                            navController.navigate(Screen.UserSessionDetail(username))
                        },
                        onOpenAdminAudit = {
                            navController.navigate(Screen.AdminAudit)
                        },
                        onOpenAnalytics = { username, type ->
                            navController.navigate(Screen.UsageAnalytics(username, type))
                        },
                        onTriggerSignIn = onTriggerSignIn
                    )
                }
                composable<Screen.UsageAnalytics> { backStackEntry ->
                    val args = backStackEntry.toRoute<Screen.UsageAnalytics>()
                    UsageAnalyticsScreen(
                        username = args.username,
                        type = args.type,
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
                    val batchOptions by viewModel.printableBatchOptions.collectAsStateWithLifecycle()
                    val activeBatchKey by viewModel.selectedPrintableBatchKey.collectAsStateWithLifecycle()
                    VoucherPrintScreen(
                        cards = printableCards,
                        onBackClick = { navController.popBackStack() },
                        batchOptions = batchOptions,
                        activeBatchKey = activeBatchKey,
                        onSelectBatchCards = { batchCards, batchKey ->
                            viewModel.setPrintableCards(batchCards, batchKey)
                        },
                        onMarkPrinted = {
                            viewModel.markCurrentBatchAsPrinted()
                        }
                    )
                }
                composable<Screen.UserSessionDetail> { backStackEntry ->
                    val args = backStackEntry.toRoute<Screen.UserSessionDetail>()
                    val sessionHistory by viewModel.getLoginHistoryForUser(args.username).collectAsStateWithLifecycle(emptyList())
                    val subscriberProfile by viewModel.getSubscriberProfileFlow(args.username).collectAsStateWithLifecycle(null)
                    val activeUsersList by viewModel.activeUsers.collectAsStateWithLifecycle()
                    val userSessionsList by viewModel.userSessions.collectAsStateWithLifecycle()
                    val allCardsList by viewModel.cards.collectAsStateWithLifecycle()

                    val targetActive = activeUsersList.find { it.user.equals(args.username, ignoreCase = true) }
                    val targetArchived = userSessionsList.find { it.username.equals(args.username, ignoreCase = true) }
                    val targetCard = allCardsList.find { it.name.equals(args.username, ignoreCase = true) }

                    val userSessionRecord = if (targetActive != null) {
                        com.example.data.model.UserSessionRecord(
                            id = targetActive.id,
                            username = targetActive.user,
                            ipAddress = targetActive.address,
                            macAddress = targetActive.macAddress,
                            duration = targetActive.uptime,
                            bytesIn = targetActive.bytesIn,
                            bytesOut = targetActive.bytesOut,
                            isOnline = true,
                            profile = targetActive.serverName
                        )
                    } else if (targetArchived != null) {
                        targetArchived
                    } else if (targetCard != null) {
                        com.example.data.model.UserSessionRecord(
                            id = targetCard.id,
                            username = targetCard.name,
                            ipAddress = "غير متصل",
                            macAddress = "غير مسجل",
                            duration = targetCard.uptime.ifBlank { "0s" },
                            bytesIn = targetCard.bytesIn,
                            bytesOut = targetCard.bytesOut,
                            totalBytes = targetCard.bytesIn + targetCard.bytesOut,
                            isOnline = false,
                            profile = targetCard.profile,
                            price = targetCard.price,
                            comment = targetCard.comment.ifBlank { "كرت جاهز للبيع" }
                        )
                    } else {
                        com.example.data.model.UserSessionRecord(
                            id = "card_${args.username}",
                            username = args.username,
                            ipAddress = "غير متصل",
                            macAddress = "غير مسجل",
                            duration = "0s",
                            isOnline = false,
                            comment = "تفاصيل الكرت"
                        )
                    }

                    UserSessionDetailScreen(
                        session = userSessionRecord,
                        subscriberProfile = subscriberProfile,
                        loginHistory = sessionHistory,
                        onBackClick = { navController.popBackStack() },
                        onKickUser = {
                            if (targetActive != null) {
                                viewModel.kickUser(targetActive)
                            }
                            navController.popBackStack()
                        },
                        onOpenAnalytics = { type ->
                            navController.navigate(Screen.UsageAnalytics(args.username, type))
                        },
                        onRefresh = { viewModel.refreshData() }
                    )
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

    val autoUpdateMinor by viewModel.autoUpdateMinorReleases.collectAsStateWithLifecycle()
    if (showUpdateDialog) {
        val checkState = updateCheckState
        if (checkState is UpdateCheckState.UpdateAvailable) {
            val context = LocalContext.current
            AppUpdateDialog(
                release = checkState.release,
                currentVersion = viewModel.currentAppVersion,
                downloadState = updateDownloadState,
                autoUpdateMinorReleases = autoUpdateMinor,
                onToggleAutoUpdateMinor = { viewModel.setAutoUpdateMinorReleases(it) },
                onDownloadAndInstall = { url ->
                    viewModel.downloadAndInstallUpdate(context, url)
                    showUpdateDialog = false
                },
                onInstallDownloaded = { file ->
                    viewModel.installDownloadedApk(context, file)
                },
                onDismiss = { showUpdateDialog = false },
                onPauseDownload = { viewModel.cancelDownloadUpdate() }
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
fun MainPagerContent(
    viewModel: MainViewModel,
    activeUsersCount: Int,
    hasOfflineNodes: Boolean,
    onOpenBatchGenerator: (Long) -> Unit,
    onNavigateToPrintStudio: () -> Unit,
    onOpenPrintScreen: (List<com.example.data.model.HotspotCard>) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    onOpenAdminAudit: () -> Unit,
    onOpenAnalytics: (String, String) -> Unit,
    onTriggerSignIn: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0) { MainTab.entries.size }
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    var showDashboardPrintDialog by remember { mutableStateOf(false) }

    // Smoothly scroll the floating dock so that the selected tab is always in view
    LaunchedEffect(pagerState.currentPage) {
        val totalTabs = MainTab.entries.size
        if (totalTabs > 1 && tabScrollState.maxValue > 0) {
            val targetScroll = (tabScrollState.maxValue.toFloat() * (pagerState.currentPage.toFloat() / (totalTabs - 1).toFloat())).toInt()
            tabScrollState.animateScrollTo(targetScroll)
        }
    }

    // BackHandler: if not on Dashboard (page 0), return to Dashboard smoothly
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (MainTab.entries[page]) {
                MainTab.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToActiveUsers = { scope.launch { pagerState.animateScrollToPage(MainTab.ACTIVE_USERS.ordinal) } },
                    onNavigateToCards = { scope.launch { pagerState.animateScrollToPage(MainTab.CARDS.ordinal) } },
                    onNavigateToProfiles = { scope.launch { pagerState.animateScrollToPage(MainTab.PROFILES.ordinal) } },
                    onNavigateToNodes = { scope.launch { pagerState.animateScrollToPage(MainTab.NODES.ordinal) } },
                    onOpenBatchGenerator = { onOpenBatchGenerator(0L) },
                    onOpenPrintScreen = {
                        showDashboardPrintDialog = true
                    },
                    onNavigateToFinance = { scope.launch { pagerState.animateScrollToPage(MainTab.FINANCE.ordinal) } },
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.ACTIVE_USERS -> ActiveUsersScreen(
                    viewModel = viewModel,
                    onOpenSessionDetail = { session ->
                        onOpenSessionDetail(session.username)
                    },
                    onOpenAnalytics = { username, type ->
                        onOpenAnalytics(username, type)
                    },
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.CARDS -> CardsScreen(
                    viewModel = viewModel,
                    onOpenBatchGenerator = {
                        onOpenBatchGenerator(0L)
                    },
                    onOpenPrintScreen = { onNavigateToPrintStudio() },
                    onOpenSessionDetail = { session ->
                        onOpenSessionDetail(session.username)
                    },
                    onOpenAnalytics = { username, type ->
                        onOpenAnalytics(username, type)
                    },
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.POS_AGENTS -> PosAgentsScreen(
                    viewModel = viewModel,
                    onOpenBatchGeneratorForAgent = { agent ->
                        onOpenBatchGenerator(agent.id)
                    },
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.PROFILES -> ProfilesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.NODES -> NetworkNodesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.TOOLS -> NetworkToolsScreen(
                    viewModel = viewModel,
                    onOpenSystemEventLog = { scope.launch { pagerState.animateScrollToPage(MainTab.LOGS.ordinal) } },
                    onTriggerSignIn = onTriggerSignIn,
                    onOpenAdminAudit = onOpenAdminAudit,
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.FINANCE -> FinanceAnalyticsScreen(
                    viewModel = viewModel,
                    onNavigateToCards = { scope.launch { pagerState.animateScrollToPage(MainTab.CARDS.ordinal) } },
                    onNavigateToPosAgents = { scope.launch { pagerState.animateScrollToPage(MainTab.POS_AGENTS.ordinal) } },
                    modifier = Modifier.padding(bottom = 78.dp)
                )
                MainTab.LOGS -> SystemEventLogScreen(
                    viewModel = viewModel,
                    onBackClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    modifier = Modifier.padding(bottom = 78.dp)
                )
            }
        }

        // Floating Bottom Navigation Dock with Neon / Glowing Effect
        FloatingBottomNavigationDock(
            pagerState = pagerState,
            tabScrollState = tabScrollState,
            activeUsersCount = activeUsersCount,
            hasOfflineNodes = hasOfflineNodes,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }

    if (showDashboardPrintDialog) {
        val allCards by viewModel.cards.collectAsStateWithLifecycle()
        val batchOptions by viewModel.printableBatchOptions.collectAsStateWithLifecycle()
        var pendingBatchKey by remember { mutableStateOf<String?>(null) }
        com.example.ui.components.PrintSelectionDialog(
            allCards = allCards,
            batchOptions = batchOptions,
            onDismiss = { showDashboardPrintDialog = false },
            onBatchSelectedForPrint = { key -> pendingBatchKey = key },
            onConfirmSelection = { selectedCards ->
                viewModel.setPrintableCards(selectedCards, pendingBatchKey)
                showDashboardPrintDialog = false
                onNavigateToPrintStudio()
            }
        )
    }
}

@Composable
fun FloatingBottomNavigationDock(
    pagerState: PagerState,
    tabScrollState: androidx.compose.foundation.ScrollState,
    activeUsersCount: Int,
    hasOfflineNodes: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(29.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                ambientColor = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(29.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            )
        ),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(tabScrollState)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = pagerState.currentPage == tab.ordinal

                val activeBackground = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(activeBackground)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(22.dp),
                                        spotColor = MaterialTheme.colorScheme.primary
                                    )
                            } else {
                                Modifier.background(Color.Transparent)
                            }
                        )
                        .clickable { onTabSelected(tab.ordinal) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("floating_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            if (tab == MainTab.ACTIVE_USERS && activeUsersCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFFEB3B) else StatusErrorRed)
                                )
                            } else if (tab == MainTab.NODES && hasOfflineNodes) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .clip(CircleShape)
                                        .background(StatusErrorRed)
                                )
                            }
                        }

                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
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
