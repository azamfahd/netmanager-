package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Close
import com.example.data.model.UpdateCheckState
import com.example.data.network.RouterConnectionParser
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.UpdateBanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedRouterEntity
import com.example.data.model.ConnectionState
import com.example.data.model.RouterConnectionConfig
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectRouterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val localNetwork by viewModel.localNetworkInfo.collectAsStateWithLifecycle()
    val discoveredRouter by viewModel.discoveredRouter.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val savedRouters by viewModel.savedRouters.collectAsStateWithLifecycle()
    val fallbackWebPortalUrl by viewModel.fallbackWebPortalUrl.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    val showUpdateBanner by viewModel.showUpdateBanner.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    val latestSnapshot by viewModel.latestCachedSnapshot.collectAsStateWithLifecycle()

    // User credentials (supports router usernames and cloud portal emails)
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberCredentials by remember { mutableStateOf(true) }

    // Smart Mode Toggle: FALSE = Local LAN/Wi-Fi Discovery, TRUE = Custom Server / Domain / URL
    var useManualNetworkSettings by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableIntStateOf(8728) }
    var manualUseHttps by remember { mutableStateOf(false) }
    var manualPreferredPath by remember { mutableStateOf<String?>(null) }
    var manualRouterName by remember { mutableStateOf("") }

    fun applySmartPaste(rawText: String, showFeedback: Boolean = true): Boolean {
        val parsed = RouterConnectionParser.parseSmartCredentialsPaste(rawText) ?: return false
        val extractedFields = listOfNotNull(parsed.host, parsed.username, parsed.password).size
        val isMultiLineOrBatch = rawText.contains('\n') || extractedFields >= 2
        if (!isMultiLineOrBatch && parsed.host == null) return false

        parsed.host?.let { extractedHost ->
            manualIp = extractedHost
            manualPreferredPath = parsed.path
            useManualNetworkSettings = true
            val resolvedPort = parsed.port ?: if (parsed.useHttps == true) 443 else manualPort
            if (parsed.port != null || parsed.useHttps == true) {
                manualPort = resolvedPort
            }
            if (parsed.useHttps != null) {
                manualUseHttps = parsed.useHttps
            }
            viewModel.probeCustomHost(extractedHost, parsed.port, showToast = false)
        }
        parsed.username?.let { username = it }
        parsed.password?.let { password = it }

        if (showFeedback) {
            val summaryParts = buildList {
                parsed.host?.let { add("السيرفر: $it") }
                parsed.username?.let { add("المستخدم: $it") }
                if (!parsed.password.isNullOrBlank()) add("كلمة المرور ✓")
            }
            if (summaryParts.isNotEmpty()) {
                scope.launch {
                    snackbarHostState.showSnackbar("تم استخراج البيانات تلقائياً: ${summaryParts.joinToString(" • ")}")
                }
            }
        }
        return true
    }

    // Listen to ViewModel toast events
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Auto-detect network and cached snapshots on launch
    LaunchedEffect(Unit) {
        viewModel.checkLocalNetwork(context)
        viewModel.refreshCachedSnapshot(context)
    }

    // Default auto-detected values from current network
    val autoDetectedIp = discoveredRouter?.ip ?: localNetwork.gatewayIp ?: "192.168.88.1"
    val autoDetectedPort = discoveredRouter?.port ?: 8728
    val autoDetectedHttps = discoveredRouter?.useHttps ?: false
    val normalizedManual = remember(manualIp) { RouterConnectionParser.normalizeHostInput(manualIp) }

    // If custom server mode is active, prioritize normalized custom domain/IP; otherwise auto-detected LAN router
    val activeIp = if (useManualNetworkSettings && normalizedManual.host.isNotBlank()) {
        normalizedManual.host
    } else if (useManualNetworkSettings && manualIp.isNotBlank()) {
        manualIp.trim()
    } else {
        autoDetectedIp
    }
    val activePort = if (useManualNetworkSettings) (normalizedManual.explicitPort ?: manualPort) else autoDetectedPort
    val activeHttps = if (useManualNetworkSettings) (normalizedManual.inferredHttps ?: manualUseHttps) else autoDetectedHttps

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "مدير شبكات ميكروتك",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (useManualNetworkSettings) "إدخال يدوي مخصص (اختياري)" else "التعرف التلقائي الذكي على الشبكة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Top Refresh Button: actively searches, re-probes, and fetches router data from connected network
                    IconButton(
                        onClick = {
                            viewModel.refreshDiscovery(context, showToast = true)
                        },
                        enabled = !isDiscovering,
                        modifier = Modifier.testTag("refresh_discovery_btn")
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث وفحص بيانات الراوتر والشبكة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                        IconButton(
                            onClick = { showUpdateDialog = true },
                            modifier = Modifier.testTag("btn_connect_screen_update_badge")
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Update Notification Banner (إذا كان هناك إصدار جديد على GitHub)
            if (showUpdateBanner && updateCheckState is UpdateCheckState.UpdateAvailable) {
                val updateState = updateCheckState as UpdateCheckState.UpdateAvailable
                item {
                    UpdateBanner(
                        tagName = updateState.release.tagName,
                        isMinorUpdate = updateState.release.isMinorUpdate,
                        downloadState = updateDownloadState,
                        onUpdateClick = { showUpdateDialog = true },
                        onDismiss = { viewModel.dismissUpdateBanner() },
                        onStopDownload = { viewModel.cancelDownloadUpdate() }
                    )
                }
            }

            // Persistent Offline Cache Access Banner (تصفح البيانات المحفوظة محلياً)
            if (latestSnapshot != null) {
                item {
                    val snapshot = latestSnapshot!!
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StatusActiveGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.2.dp, StatusActiveGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
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
                                            .background(StatusActiveGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "بيانات الشبكة والكروت محفوظة محلياً",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = StatusActiveGreen
                                        )
                                        Text(
                                            text = "${snapshot.routerName.ifBlank { "راوتر MikroTik" }} (${snapshot.ipAddress}) • ${snapshot.cards.size} كرت • ${snapshot.profiles.size} باقات",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.enterOfflineMode(snapshot.ipAddress, context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("فتح البيانات الآن", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 1. Hero Router & Network Detection Card (Compact & Elegant)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_discovered_router_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (useManualNetworkSettings)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                                        .background(if (useManualNetworkSettings) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (useManualNetworkSettings) Icons.Default.Tune else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (useManualNetworkSettings) "وضع السيرفر / الدومين المخصص" else "التعرف التلقائي على الراوتر",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontSize = 13.sp,
                                        color = if (useManualNetworkSettings) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val serverKindLabel = discoveredRouter?.detectedServerKind?.takeIf { it.isNotBlank() }
                                    Text(
                                        text = serverKindLabel ?: if (useManualNetworkSettings) "يدعم IP محلي، دومين، أو لوحة سحابية" else "الافتراضي: تم الجلب من شبكتك",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (isDiscovering) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "جاري الفحص...",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                StatusPill(
                                    text = when {
                                        discoveredRouter?.isCloudWebPortal == true -> "لوحة سحابية / ويب"
                                        discoveredRouter?.isReachable == true -> "سيرفر مستجيب"
                                        else -> "تم التعرف"
                                    },
                                    type = when {
                                        discoveredRouter?.isCloudWebPortal == true -> StatusType.INFO
                                        discoveredRouter?.isReachable == true -> StatusType.SUCCESS
                                        else -> StatusType.INFO
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Display of Active Target Host/IP and Port
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "عنوان الراوتر / السيرفر:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = activeIp,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "البروتوكول / المنفذ:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(StatusActiveGreen)
                                        )
                                        Text(
                                            text = "$activePort (${discoveredRouter?.serviceType ?: if (activePort == 8728) "API" else if (activePort == 8729) "API-SSL" else "REST/Web"})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = StatusActiveGreen
                                        )
                                    }
                                }
                            }
                        }

                        // Open Ports Quick Chips (التعرف الذكي على جميع المنافذ المفتوحة)
                        val openPorts = discoveredRouter?.openPorts.orEmpty()
                        if (openPorts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المنافذ المفتوحة:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                openPorts.forEach { p ->
                                    val isSelected = activePort == p
                                    val portLabel = when (p) {
                                        8728 -> "8728 API"
                                        8729 -> "8729 SSL"
                                        8291 -> "8291 Winbox"
                                        80 -> "80 HTTP"
                                        443 -> "443 HTTPS"
                                        8080 -> "8080 Alt"
                                        else -> "$p"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        onClick = {
                                            useManualNetworkSettings = true
                                            if (manualIp.isBlank()) manualIp = autoDetectedIp
                                            manualPort = p
                                            manualUseHttps = (p == 443 || p == 8729)
                                        }
                                    ) {
                                        Text(
                                            text = portLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Wi-Fi Connection Info & Device IP
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = localNetwork.wifiSsid ?: "شبكة Wi-Fi متصلة",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "IP جهازك: ${localNetwork.deviceIp ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Connection Progress / Error Banners
            item {
                AnimatedVisibility(visible = connectionState is ConnectionState.Verifying) {
                    val verifying = connectionState as? ConnectionState.Verifying
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = verifying?.step ?: "جاري التحقق والمصادقة...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { verifying?.progress ?: 0.5f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = connectionState is ConnectionState.Error) {
                    val err = (connectionState as? ConnectionState.Error)?.message.orEmpty()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "تنبيه: تعذر تسجيل الدخول إلى الراوتر",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Exact Error message
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Helpful Troubleshooting Tips
                            Text(
                                text = "💡 نصائح وإرشادات للحل السريع:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• تأكد من كتابة اسم المستخدم (أو البريد الإلكتروني في لوحات التحكم السحابية) وكلمة المرور بشكل صحيح.\n• عند استخدام عنوان محلي، تأكد أن الهاتف متصل بنفس شبكة الواي فاي للراوتر.\n• عند استخدام دومين خارجي أو سيرفر سحابي، تأكد من توفر اتصال بالإنترنت وصحة اسم النطاق (Domain) أو تفعيل خدمة API/API-SSL/www.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (!fallbackWebPortalUrl.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        viewModel.openWebPortal(
                                            url = fallbackWebPortalUrl!!,
                                            serverName = manualRouterName.ifBlank { activeIp },
                                            username = username.trim(),
                                            password = password
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_fallback_web_portal_btn")
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فتح لوحة التحكم عبر الويب (المتصفح الداخلي)", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (activePort != 8728) {
                                Button(
                                    onClick = {
                                        useManualNetworkSettings = true
                                        manualPort = 8728
                                        val cleanHost = RouterConnectionParser.normalizeHostInput(activeIp).host.ifBlank { activeIp.trim() }
                                        val config = RouterConnectionConfig(
                                            name = if (manualRouterName.isNotBlank()) manualRouterName else "راوتر $cleanHost",
                                            ipAddress = cleanHost,
                                            port = 8728,
                                            username = username.trim(),
                                            password = password,
                                            useHttps = false,
                                            autoDetectProtocol = true,
                                            isDemoMode = false
                                        )
                                        viewModel.connectToRouter(config, saveToFavorites = rememberCredentials, context = context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text("محاولة الاتصال عبر منفذ MikroTik API الرسمي (8728)")
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.refreshDiscovery(context, showToast = true) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إعادة فحص الشبكة", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val demoConfig = RouterConnectionConfig(
                                            name = "راوتر تجريبي (Demo)",
                                            ipAddress = activeIp,
                                            port = activePort,
                                            username = username,
                                            password = password,
                                            isDemoMode = true
                                        )
                                        viewModel.connectToRouter(demoConfig, saveToFavorites = false)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الدخول التجريبي", fontSize = 12.sp)
                                }
                            }

                            if (latestSnapshot != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        viewModel.enterOfflineMode(latestSnapshot?.ipAddress, context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فتح البيانات المحفوظة محلياً (وضع الأوفلاين)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Saved Routers History / Quick Reconnect List (المحفوظات للدخول السريع)
            if (savedRouters.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = RouterBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "الراوترات السابقة في المحفوظات (${savedRouters.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "دخول فوري بنقرة واحدة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            savedRouters.forEach { router ->
                                var cacheSummary by remember(router.ipAddress) { mutableStateOf<com.example.data.local.RouterCacheSummary?>(null) }
                                LaunchedEffect(router.ipAddress) {
                                    cacheSummary = com.example.data.local.RouterCacheManager.getCacheSummary(context, router.ipAddress)
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                username = router.username
                                                if (router.password.isNotBlank()) password = router.password
                                                manualIp = router.ipAddress
                                                manualPort = router.port
                                                manualUseHttps = router.useHttps
                                                useManualNetworkSettings = true
                                                Toast.makeText(context, "تم تحديد بيانات راوتر ${router.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(RouterBlue.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Router,
                                                        contentDescription = null,
                                                        tint = RouterBlue,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = router.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${router.ipAddress}:${router.port} • المستخدم: ${router.username}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        username = router.username
                                                        if (router.password.isNotBlank()) password = router.password
                                                        val config = RouterConnectionConfig(
                                                            id = router.id,
                                                            name = router.name,
                                                            ipAddress = router.ipAddress,
                                                            port = router.port,
                                                            username = router.username,
                                                            password = router.password,
                                                            useHttps = router.useHttps,
                                                            isDemoMode = false
                                                        )
                                                        viewModel.connectToRouter(config, saveToFavorites = true, context = context)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (cacheSummary != null) StatusActiveGreen else RouterBlue
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (cacheSummary != null) Icons.Default.Bolt else Icons.Default.Login,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (cacheSummary != null) "⚡ دخول فوري" else "دخول سريع",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deleteSavedRouter(router.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "حذف الراوتر من المحفوظات",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Local cache details banner
                                        if (cacheSummary != null) {
                                            Surface(
                                                color = StatusActiveGreen.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.6.dp, StatusActiveGreen.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Speed,
                                                            contentDescription = null,
                                                            tint = StatusActiveGreen,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "كاش محلي: ${cacheSummary?.cardsCount} كرت • ${cacheSummary?.profilesCount} باقات • مزامنة تلقائية",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = StatusActiveGreen
                                                        )
                                                    }
                                                    Text(
                                                        text = cacheSummary?.formattedTime ?: "",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Main Smart Login Form: Supports Local IP Discovery + Remote Domains + Cloud AC + WhatsApp Smart Paste
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تسجيل الدخول والاتصال الذكي",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (useManualNetworkSettings) {
                                        "متوافق مع الدومينات (APlusWiFi/DDNS)، IP، أو الروابط"
                                    } else {
                                        "العنوان التلقائي: $activeIp"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (clipText.isBlank() || !applySmartPaste(clipText, showFeedback = true)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("انسخ بيانات السيرفر أو رسالة الواتساب أولاً ثم اضغط لصق ذكي")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("smart_paste_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "لصق ذكي من الحافظة", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("لصق ذكي", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Connection Mode Quick Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !useManualNetworkSettings,
                                onClick = {
                                    useManualNetworkSettings = false
                                    viewModel.refreshDiscovery(context, showToast = false)
                                },
                                label = { Text("تلقائي من الشبكة", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mode_auto_chip")
                            )
                            FilterChip(
                                selected = useManualNetworkSettings,
                                onClick = {
                                    useManualNetworkSettings = true
                                },
                                label = { Text("دومين / سيرفر مخصص", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mode_custom_server_chip")
                            )
                        }

                        // Custom Server / Domain / URL Field (Visible when Custom Mode is active)
                        AnimatedVisibility(
                            visible = useManualNetworkSettings,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { input ->
                                    if (input.contains('\n') || input.contains("]:")) {
                                        if (!applySmartPaste(input, showFeedback = true)) {
                                            manualIp = input
                                        }
                                    } else {
                                        manualIp = input
                                        val parsed = RouterConnectionParser.normalizeHostInput(input)
                                        manualPreferredPath = parsed.rawPath
                                        parsed.explicitPort?.let { manualPort = it }
                                        parsed.inferredHttps?.let { manualUseHttps = it }
                                    }
                                },
                                label = { Text("عنوان الراوتر أو السيرفر (IP أو دومين أو رابط)") },
                                placeholder = { Text("مثال: 192.168.88.1 أو myrouter.example.com") },
                                leadingIcon = {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = RouterBlue)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val target = manualIp.ifBlank { activeIp }
                                            viewModel.probeCustomHost(target, normalizedManual.explicitPort, showToast = true)
                                        },
                                        modifier = Modifier.testTag("probe_custom_host_btn")
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "فحص السيرفر والمنافذ")
                                    }
                                },
                                supportingText = {
                                    val kind = discoveredRouter?.detectedServerKind?.takeIf { it.isNotBlank() }
                                    Text(
                                        text = kind ?: "يقبل الدومين، الرابط الكامل، IP:Port، أو لصق رسالة واتساب مباشرة",
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("router_host_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        // 1. Username or Email
                        OutlinedTextField(
                            value = username,
                            onValueChange = { input ->
                                if (input.contains('\n')) {
                                    if (!applySmartPaste(input, showFeedback = true)) {
                                        username = input
                                    }
                                } else {
                                    username = input
                                }
                            },
                            label = { Text("اسم المستخدم أو البريد الإلكتروني") },
                            placeholder = { Text("admin أو user@example.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = RouterBlue)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("router_user_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // 2. Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("كلمة المرور (Password)") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = RouterBlue)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "إظهار كلمة المرور"
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("router_password_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Remember credentials Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rememberCredentials = !rememberCredentials }
                        ) {
                            Checkbox(
                                checked = rememberCredentials,
                                onCheckedChange = { rememberCredentials = it }
                            )
                            Text(
                                text = "تذكر بيانات السيرفر والراوتر للاتصال التلقائي",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Big Primary Smart Login Button
                        Button(
                            onClick = {
                                val rawOrActive = if (useManualNetworkSettings && manualIp.isNotBlank()) manualIp.trim() else activeIp
                                val parsedTarget = RouterConnectionParser.normalizeHostInput(rawOrActive)
                                val cleanHost = parsedTarget.host.ifBlank { activeIp.trim() }
                                val resolvedPort = parsedTarget.explicitPort ?: activePort
                                val resolvedHttps = parsedTarget.inferredHttps ?: activeHttps
                                val resolvedPath = parsedTarget.rawPath ?: if (useManualNetworkSettings) manualPreferredPath else null
                                val config = RouterConnectionConfig(
                                    name = if (manualRouterName.isNotBlank()) manualRouterName else "راوتر $cleanHost",
                                    ipAddress = cleanHost,
                                    port = resolvedPort,
                                    username = username.trim(),
                                    password = password,
                                    useHttps = resolvedHttps,
                                    autoDetectProtocol = true,
                                    preferredPath = resolvedPath,
                                    isDemoMode = false
                                )
                                viewModel.connectToRouter(config, saveToFavorites = rememberCredentials, context = context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("connect_router_button"),
                            shape = RoundedCornerShape(14.dp),
                            enabled = connectionState !is ConnectionState.Verifying && username.isNotBlank() && activeIp.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اتصال ذكي وتسجيل الدخول",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Demo Mode Button for instant test
                        OutlinedButton(
                            onClick = {
                                val demoConfig = RouterConnectionConfig(
                                    name = "راوتر تجريبي (Demo MikroTik)",
                                    ipAddress = activeIp,
                                    port = activePort,
                                    username = username,
                                    password = password,
                                    useHttps = false,
                                    isDemoMode = true
                                )
                                viewModel.connectToRouter(demoConfig, saveToFavorites = false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("demo_mode_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "دخول تجريبي فوري (وضع المحاكاة Demo)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Collapsible Advanced Settings: Optional manual configuration toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedSettings = !showAdvancedSettings },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "إعدادات متقدمة وتخصيص بيانات الشبكة",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (useManualNetworkSettings) "تم تفعيل التخصيص اليدوي الاختياري" else "الافتراضي: التعرف التلقائي مفعل",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (useManualNetworkSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showAdvancedSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvancedSettings,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Toggle Option: Manual Network Data (Optional)
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "إدخال بيانات الشبكة يدوياً (اختياري)",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "الافتراضي هو التعرف التلقائي على الراوتر من الشبكة المتصل بها. فعّل هذا الخيار فقط إذا أردت إدخال IP أو منفذ مخصص بنفسك.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Switch(
                                            checked = useManualNetworkSettings,
                                            onCheckedChange = { isManual ->
                                                useManualNetworkSettings = isManual
                                                if (isManual && manualIp.isBlank()) {
                                                    manualIp = autoDetectedIp
                                                    manualPort = autoDetectedPort
                                                    manualUseHttps = autoDetectedHttps
                                                }
                                            }
                                        )
                                    }
                                }

                                // If Default Automatic Mode is active
                                if (!useManualNetworkSettings) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "نمط التعرف التلقائي نشط (الافتراضي)",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "يتم استخدام بيانات الشبكة والراوتر المكتشفة تلقائياً: عنوان IP هو ($autoDetectedIp) والمنفذ ($autoDetectedPort). لا حاجة لأي إعدادات إضافية.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = { viewModel.refreshDiscovery(context, showToast = true) },
                                                enabled = !isDiscovering,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("إعادة فحص واكتشاف الشبكة والراوتر الآن")
                                            }
                                        }
                                    }
                                } else {
                                    // Manual Settings Form (When optional manual mode is enabled)
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "بيانات الشبكة والراوتر المخصصة:",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            OutlinedTextField(
                                                value = manualIp,
                                                onValueChange = { input ->
                                                    if (input.contains('\n')) {
                                                        if (!applySmartPaste(input, showFeedback = true)) {
                                                            manualIp = input
                                                        }
                                                    } else {
                                                        manualIp = input
                                                    }
                                                },
                                                label = { Text("عنوان الراوتر أو السيرفر (IP أو دومين أو رابط)") },
                                                placeholder = { Text(autoDetectedIp) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Dns, contentDescription = null)
                                                },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("advanced_router_host_input")
                                            )

                                            Column {
                                                Text(
                                                    text = "المنفذ المخصص (Port):",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    listOf(
                                                        8728 to "8728 (API)",
                                                        80 to "80 (HTTP)",
                                                        443 to "443 (HTTPS)",
                                                        8080 to "8080 (بديل)"
                                                    ).forEach { (port, label) ->
                                                        FilterChip(
                                                            selected = manualPort == port,
                                                            onClick = {
                                                                manualPort = port
                                                                if (port == 443) manualUseHttps = true
                                                            },
                                                            label = { Text(label, fontSize = 12.sp) }
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                OutlinedTextField(
                                                    value = manualPort.toString(),
                                                    onValueChange = { input ->
                                                        val p = input.filter { it.isDigit() }.toIntOrNull()
                                                        if (p != null && p in 1..65535) {
                                                            manualPort = p
                                                        } else if (input.isBlank()) {
                                                            manualPort = 8728
                                                        }
                                                    },
                                                    label = { Text("أو كتابة رقم منفذ مخصص (Custom Port)") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Text("استخدام اتصال آمن (HTTPS / SSL)")
                                                }
                                                Switch(
                                                    checked = manualUseHttps,
                                                    onCheckedChange = { manualUseHttps = it }
                                                )
                                            }

                                            OutlinedTextField(
                                                value = manualRouterName,
                                                onValueChange = { manualRouterName = it },
                                                label = { Text("اسم مستعار للراوتر (اختياري)") },
                                                placeholder = { Text("راوتر الشبكة الرئيسية") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            // Action Buttons for Manual Mode
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        manualIp = autoDetectedIp
                                                        manualPort = autoDetectedPort
                                                        manualUseHttps = autoDetectedHttps
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("نسخ المكتشف تلقائياً", fontSize = 12.sp)
                                                }

                                                TextButton(
                                                    onClick = {
                                                        useManualNetworkSettings = false
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("العودة للتعرف التلقائي", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Saved Routers (الراوترات المحفوظة)
            if (savedRouters.isNotEmpty()) {
                item {
                    Text(
                        text = "الراوترات المحفوظة:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(savedRouters, key = { it.id }) { router ->
                    SavedRouterItem(
                        router = router,
                        onConnect = {
                            val config = RouterConnectionConfig(
                                id = router.id,
                                name = router.name,
                                ipAddress = router.ipAddress,
                                port = router.port,
                                username = router.username,
                                password = router.password,
                                useHttps = router.useHttps,
                                isDemoMode = false
                            )
                            viewModel.connectToRouter(config, saveToFavorites = false, context = context)
                        },
                        onDelete = {
                            viewModel.deleteSavedRouter(router.id)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    val autoUpdateMinor by viewModel.autoUpdateMinorReleases.collectAsStateWithLifecycle()
    if (showUpdateDialog) {
        val checkState = updateCheckState
        if (checkState is UpdateCheckState.UpdateAvailable) {
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
}

@Composable
fun SavedRouterItem(
    router: SavedRouterEntity,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var cacheSummary by remember(router.ipAddress) { mutableStateOf<com.example.data.local.RouterCacheSummary?>(null) }
    LaunchedEffect(router.ipAddress) {
        cacheSummary = com.example.data.local.RouterCacheManager.getCacheSummary(context, router.ipAddress)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = router.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${router.ipAddress}:${router.port} (${router.username})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onConnect,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (cacheSummary != null) StatusActiveGreen else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = if (cacheSummary != null) Icons.Default.Bolt else Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (cacheSummary != null) "⚡ دخول فوري" else "اتصال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الراوتر",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (cacheSummary != null) {
                Surface(
                    color = StatusActiveGreen.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ كاش محلي: ${cacheSummary?.cardsCount} كرت • ${cacheSummary?.profilesCount} باقات",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusActiveGreen
                        )
                        Text(
                            text = cacheSummary?.formattedTime ?: "",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
