package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionState
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard),
    ACTIVE_USERS("المستخدمين", Icons.Default.Group),
    CARDS("الكروت", Icons.Default.ConfirmationNumber),
    PROFILES("الباقات", Icons.Default.Tune),
    TOOLS("الأدوات", Icons.Default.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val printableCards by viewModel.printableCards.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var showBatchGeneratorDialog by remember { mutableStateOf(false) }
    var isShowingPrintScreen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
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
                            AppTab.ACTIVE_USERS -> "المستخدمين النشطون ($routerIp)"
                            AppTab.CARDS -> "إدارة كروت الشبكة"
                            AppTab.PROFILES -> "باقات السرعات والأسعار"
                            AppTab.TOOLS -> "أدوات فحص وتحكم الشبكة"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    StatusPill(
                        text = if (connectedState?.isDemo == true) "تجريبي" else "متصل",
                        type = if (connectedState?.isDemo == true) StatusType.INFO else StatusType.SUCCESS,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == AppTab.ACTIVE_USERS && activeUsers.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text("${activeUsers.size}") }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
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
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AppTab.ACTIVE_USERS -> {
                ActiveUsersScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AppTab.CARDS -> {
                CardsScreen(
                    viewModel = viewModel,
                    onOpenBatchGenerator = { showBatchGeneratorDialog = true },
                    onOpenPrintScreen = { isShowingPrintScreen = true },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AppTab.PROFILES -> {
                ProfilesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AppTab.TOOLS -> {
                NetworkToolsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (showBatchGeneratorDialog) {
        VoucherBatchDialog(
            profiles = profiles,
            onDismiss = { showBatchGeneratorDialog = false },
            onGenerate = { config ->
                viewModel.generateBatch(config)
            }
        )
    }
}
