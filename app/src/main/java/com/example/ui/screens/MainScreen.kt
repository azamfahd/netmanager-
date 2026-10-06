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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
    ACTIVE_USERS("المتصلين", Icons.Default.Group),
    NODES("النقاط", Icons.Default.CellTower),
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
    val networkNodes by viewModel.networkNodes.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val printableCards by viewModel.printableCards.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var showBatchGeneratorDialog by remember { mutableStateOf(false) }
    var isShowingPrintScreen by remember { mutableStateOf(false) }
    var showWelcomeDialog by remember { mutableStateOf(true) }
    val allCards by viewModel.cards.collectAsStateWithLifecycle()

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
                            AppTab.ACTIVE_USERS -> "المستخدمين المتصلين ($routerIp)"
                            AppTab.NODES -> "نقاط وأبراج الشبكة (${networkNodes.size})"
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
                            } else if (tab == AppTab.NODES && networkNodes.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text("${networkNodes.count { it.isOnline }}") }
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
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
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
            AppTab.NODES -> {
                NetworkNodesScreen(
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
            },
            onAddSingleCard = { card ->
                viewModel.addSingleCard(card)
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
}
