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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CachedPortalMeta
import com.example.data.local.RouterCacheManager
import com.example.data.local.RouterCacheSummary
import com.example.data.local.WebPortalCacheManager
import com.example.data.model.ConnectionState
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.UpdateCheckState
import com.example.data.network.NetworkUtils
import com.example.data.network.RouterConnectionParser
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.components.UpdateBanner
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
    val latestCachedPortal by viewModel.latestCachedPortal.collectAsStateWithLifecycle()

    val formPrefs = remember(context) {
        context.getSharedPreferences("netmanager_connect_form_prefs", Context.MODE_PRIVATE)
    }

    // Credentials (remembered cleanly across launches)
    var rememberCredentials by remember {
        mutableStateOf(formPrefs.getBoolean("remember_credentials", true))
    }
    var username by remember { mutableStateOf(formPrefs.getString("username", "admin") ?: "admin") }
    var password by remember {
        mutableStateOf(
            if (formPrefs.getBoolean("remember_credentials", true)) {
                formPrefs.getString("saved_password", "") ?: ""
            } else {
                ""
            }
        )
    }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Two clean modes ONLY:
    // 1) false (default): Auto MikroTik Network Connection -> connects natively into our app (NEVER opens browser)
    // 2) true: Custom Server / Domain / Platform -> custom IP, domain, URL, or subscribed intermediary web platform
    var isCustomPlatformMode by remember {
        val migratedV3 = formPrefs.getBoolean("two_mode_default_v3", false)
        val initialCustom = if (!migratedV3) {
            false
        } else {
            formPrefs.getBoolean("use_custom_platform_mode_v3", false)
        }
        mutableStateOf(initialCustom)
    }
    var preferWebPlatformBrowser by remember {
        mutableStateOf(formPrefs.getBoolean("prefer_web_platform_browser", false))
    }
    var showCustomPortOptions by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf(formPrefs.getString("manual_ip", "") ?: "") }
    var manualPort by remember { mutableIntStateOf(formPrefs.getInt("manual_port", 443)) }
    var manualUseHttps by remember { mutableStateOf(formPrefs.getBoolean("manual_https", true)) }
    var manualPreferredPath by remember { mutableStateOf<String?>(formPrefs.getString("manual_path", null)) }
    var manualRouterName by remember { mutableStateOf(formPrefs.getString("manual_name", "") ?: "") }

    // Pre-fill saved username/password if blank on launch
    LaunchedEffect(savedRouters) {
        val latestRouter = savedRouters.firstOrNull()
        if (latestRouter != null && rememberCredentials && password.isBlank() && latestRouter.password.isNotBlank()) {
            val match = savedRouters.firstOrNull {
                username.isBlank() || it.username.equals(username, ignoreCase = true)
            } ?: latestRouter
            if (username.isBlank() || username == "admin") {
                username = match.username
            }
            if (match.password.isNotBlank()) {
                password = match.password
            }
        }
    }

    LaunchedEffect(
        username,
        password,
        rememberCredentials,
        isCustomPlatformMode,
        preferWebPlatformBrowser,
        manualIp,
        manualPort,
        manualUseHttps,
        manualPreferredPath,
        manualRouterName
    ) {
        formPrefs.edit()
            .putString("username", username)
            .putBoolean("remember_credentials", rememberCredentials)
            .putString("saved_password", if (rememberCredentials) password else "")
            .putBoolean("use_custom_platform_mode_v3", isCustomPlatformMode)
            .putBoolean("two_mode_default_v3", true)
            .putBoolean("prefer_web_platform_browser", preferWebPlatformBrowser)
            .putString("manual_ip", manualIp)
            .putInt("manual_port", manualPort)
            .putBoolean("manual_https", manualUseHttps)
            .putString("manual_path", manualPreferredPath)
            .putString("manual_name", manualRouterName)
            .apply()
    }

    fun applySmartPaste(rawText: String, showFeedback: Boolean = true): Boolean {
        val parsed = RouterConnectionParser.parseSmartCredentialsPaste(rawText) ?: return false
        val extractedFields = listOfNotNull(parsed.host, parsed.username, parsed.password).size
        val isMultiLineOrBatch = rawText.contains('\n') || extractedFields >= 2
        if (!isMultiLineOrBatch && parsed.host == null) return false

        parsed.host?.let { extractedHost ->
            manualIp = extractedHost
            manualPreferredPath = parsed.path
            isCustomPlatformMode = true
            val resolvedPort = parsed.port ?: if (parsed.useHttps == true) 443 else manualPort
            if (parsed.port != null || parsed.useHttps == true) {
                manualPort = resolvedPort
            }
            if (parsed.useHttps != null) {
                manualUseHttps = parsed.useHttps
            }
            val isWebScheme = rawText.contains("http://", ignoreCase = true) ||
                rawText.contains("https://", ignoreCase = true) ||
                extractedHost.contains("apluswifi.com", ignoreCase = true)
            if (isWebScheme && parsed.port !in listOf(8728, 8729)) {
                preferWebPlatformBrowser = true
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

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkLocalNetwork(context)
        viewModel.refreshCachedSnapshot(context)
    }

    val autoDetectedIp = discoveredRouter?.ip?.ifBlank { null }
        ?: localNetwork.gatewayIp?.ifBlank { null }
        ?: ""
    val autoDetectedApiPort = discoveredRouter?.port?.takeIf { it in listOf(8728, 8729) } ?: 8728
    val autoDetectedApiHttps = autoDetectedApiPort == 8729

    val normalizedManual = remember(manualIp) { RouterConnectionParser.normalizeHostInput(manualIp) }
    val activeIp = if (isCustomPlatformMode) {
        normalizedManual.host.ifBlank { manualIp.trim() }
    } else {
        autoDetectedIp
    }
    val activePort = if (isCustomPlatformMode) {
        normalizedManual.explicitPort ?: manualPort
    } else {
        autoDetectedApiPort
    }
    val activeHttps = if (isCustomPlatformMode) {
        normalizedManual.inferredHttps ?: manualUseHttps
    } else {
        autoDetectedApiHttps
    }

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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
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
                                text = if (!isCustomPlatformMode) {
                                    "الاتصال التلقائي بالراوتر • داخل النظام"
                                } else {
                                    "دومين / منصة مخصصة (IP أو وسيط)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshDiscovery(context, showToast = true) },
                        enabled = !isDiscovering,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_discovery_btn")
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
                                contentDescription = "فحص وتحديث الشبكة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                        IconButton(
                            onClick = { showUpdateDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_connect_screen_update_badge")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

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

            // Cached Offline Router Snapshot Banner
            if (latestSnapshot != null) {
                item {
                    val snapshot = latestSnapshot!!
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StatusActiveGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.2.dp, StatusActiveGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
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
                                        text = "بيانات الراوتر محفوظة محلياً",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = StatusActiveGreen
                                    )
                                    Text(
                                        text = "${snapshot.routerName.ifBlank { "راوتر MikroTik" }} (${snapshot.ipAddress}) • ${snapshot.cards.size} كرت",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.enterOfflineMode(snapshot.ipAddress, context) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فتح الآن", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Offline Web Portal Cache Banner (only for explicit saved web platforms)
            if (latestCachedPortal != null && !latestCachedPortal!!.isMikroTikWebFig) {
                item {
                    val cachedPortal = latestCachedPortal!!
                    val isOnlineNow = NetworkUtils.isNetworkOnline(context)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isOnlineNow) Color(0xFFFFF8E1) else Color(0xFFFFE0B2),
                        border = BorderStroke(1.2.dp, Color(0xFFFB8C00)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_portal_cache_banner")
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE65100)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isOnlineNow) Icons.Default.Language else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isOnlineNow) "منصة ويب محفوظة محلياً" else "بدون إنترنت • نسخة المنصة جاهزة",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF6D3500)
                                    )
                                    Text(
                                        text = "${cachedPortal.serverName} (${cachedPortal.host})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF7A3E00),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.enterOfflineMode(cachedPortal.host, context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_offline_portal_btn")
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصفح", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 1. Network Status Hero Card (Clean, Focused, Zero Clutter)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_discovered_router_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isCustomPlatformMode) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (!isCustomPlatformMode) StatusActiveGreen else RouterBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (!isCustomPlatformMode) Icons.Default.Router else Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (!isCustomPlatformMode) {
                                            "التعرف التلقائي على الميكروتك"
                                        } else {
                                            "سيرفر / دومين أو منصة مخصصة"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (!isCustomPlatformMode) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                    Text(
                                        text = if (!isCustomPlatformMode) {
                                            "يجلب جميع البيانات إلى النظام مباشرةً بدون متصفح"
                                        } else {
                                            "للدخول عبر IP أو دومين أو المنصة المشترك بها"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        !isCustomPlatformMode && autoDetectedIp.isNotBlank() && discoveredRouter?.isReachable == true ->
                                            "راوتر جاهز"
                                        !isCustomPlatformMode && autoDetectedIp.isNotBlank() ->
                                            "تم رصد الشبكة"
                                        !isCustomPlatformMode ->
                                            "بانتظار الواي فاي"
                                        activeIp.isNotBlank() && discoveredRouter?.isCloudWebPortal == true ->
                                            "منصة / لوحة ويب"
                                        activeIp.isNotBlank() && discoveredRouter?.isReachable == true ->
                                            "سيرفر مستجيب"
                                        else ->
                                            "إدخال مخصص"
                                    },
                                    type = when {
                                        !isCustomPlatformMode && autoDetectedIp.isNotBlank() -> StatusType.SUCCESS
                                        !isCustomPlatformMode -> StatusType.WARNING
                                        discoveredRouter?.isReachable == true -> StatusType.SUCCESS
                                        else -> StatusType.INFO
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (!isCustomPlatformMode) "عنوان الراوتر المكتشف:" else "السيرفر / المنصة المحددة:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = activeIp.ifBlank {
                                            if (!isCustomPlatformMode) {
                                                "اتصل بشبكة واي فاي الراوتر أو اختر «دومين / منصة مخصصة»"
                                            } else {
                                                "أدخل IP أو الدومين في الحقل بالأسفل"
                                            }
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = if (activeIp.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (activeIp.isNotBlank()) 14.sp else 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (activeIp.isNotBlank()) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "المسار:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (!isCustomPlatformMode) {
                                                "API ($activePort)"
                                            } else if (preferWebPlatformBrowser) {
                                                "متصفح المنصة"
                                            } else {
                                                "منفذ $activePort"
                                            },
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

                        Spacer(modifier = Modifier.height(8.dp))

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
                                    imageVector = if (localNetwork.isConnectedToWifi) Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = localNetwork.wifiSsid ?: if (localNetwork.isConnectedToWifi) "شبكة Wi-Fi متصلة" else "بيانات الجوال / خارج Wi-Fi",
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

            // Connection Progress & Error Cards
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
                                    text = "تعذر تسجيل الدخول والاتصال",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
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

                            Text(
                                text = if (!isCustomPlatformMode) {
                                    "• تأكد من اتصال الهاتف بنفس شبكة الواي فاي للراوتر، وصحة اسم المستخدم وكلمة المرور، وتفعيل خدمة API في الراوتر (IP -> Services)."
                                } else {
                                    "• تأكد من صحة عنوان الدومين أو الـ IP واسم المستخدم وكلمة المرور وتوفر الاتصال بالشبكة أو الإنترنت."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Web portal fallback is strictly limited to Custom Domain / Platform mode (NEVER Auto MikroTik mode)
                            if (isCustomPlatformMode && !fallbackWebPortalUrl.isNullOrBlank()) {
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
                                    Text("فتح المنصة عبر المتصفح المدمج", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.refreshDiscovery(context, showToast = true) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize(),
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الدخول التجريبي", fontSize = 12.sp)
                                }
                            }

                            if (latestSnapshot != null || (isCustomPlatformMode && latestCachedPortal != null)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val offlineTarget = if (isCustomPlatformMode && latestCachedPortal != null && latestSnapshot == null) {
                                            latestCachedPortal?.host
                                        } else {
                                            latestSnapshot?.ipAddress ?: latestCachedPortal?.host
                                        }
                                        viewModel.enterOfflineMode(offlineTarget, context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (latestSnapshot != null) {
                                            "فتح البيانات المحفوظة محلياً (أوفلاين)"
                                        } else {
                                            "فتح نسخة المنصة المحفوظة (أوفلاين)"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Main Clean Login Card: Strictly 2 Clear Modes
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تسجيل الدخول",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!isCustomPlatformMode) {
                                        "اتصال تلقائي بالميكروتك وجلب كل شيء للنظام"
                                    } else {
                                        "الاتصال عبر IP مخصص، دومين، أو منصة خارجية"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isCustomPlatformMode) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                        if (clipText.isBlank() || !applySmartPaste(clipText, showFeedback = true)) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("انسخ بيانات السيرفر أو الدومين أولاً ثم اضغط لصق ذكي")
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
                        }

                        // Strictly Two Clear Connection Options (Zero Redundant IP-Direct Chip)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    isCustomPlatformMode = false
                                    viewModel.refreshDiscovery(context, showToast = false)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (!isCustomPlatformMode) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    width = if (!isCustomPlatformMode) 1.5.dp else 1.dp,
                                    color = if (!isCustomPlatformMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("mode_auto_chip")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Router,
                                        contentDescription = null,
                                        tint = if (!isCustomPlatformMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "اتصال تلقائي بالميكروتك",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isCustomPlatformMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "في النظام • بدون متصفح",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                onClick = { isCustomPlatformMode = true },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCustomPlatformMode) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    width = if (isCustomPlatformMode) 1.5.dp else 1.dp,
                                    color = if (isCustomPlatformMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("mode_custom_server_chip")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = if (isCustomPlatformMode) RouterBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "دومين / منصة مخصصة",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCustomPlatformMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "عبر IP أو دومين أو وسيط",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Auto Mode Helper Notice when no Wi-Fi router gateway is detected yet
                        AnimatedVisibility(
                            visible = !isCustomPlatformMode && autoDetectedIp.isBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "لم يتم العثور على راوتر ميكروتك في الشبكة الحالية. تأكد من اتصالك بشبكة الواي فاي للراوتر أو اختر «دومين / منصة مخصصة».",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.refreshDiscovery(context, showToast = true) },
                                            enabled = !isDiscovering,
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("فحص الشبكة", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = { isCustomPlatformMode = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Text("دخول عبر IP / دومين", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Custom Server / Domain / Platform Host Input (Visible ONLY in Option 2)
                        AnimatedVisibility(
                            visible = isCustomPlatformMode,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            if (input.contains("http://", ignoreCase = true) ||
                                                input.contains("https://", ignoreCase = true) ||
                                                parsed.host.contains("apluswifi.com", ignoreCase = true)
                                            ) {
                                                if (parsed.explicitPort !in listOf(8728, 8729)) {
                                                    preferWebPlatformBrowser = true
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("عنوان السيرفر أو المنصة (IP أو دومين أو رابط)") },
                                    placeholder = { Text("مثال: panel.apluswifi.com أو 192.168.1.50") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Language, contentDescription = null, tint = RouterBlue)
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val target = manualIp.ifBlank { activeIp }
                                                viewModel.probeCustomHost(target, normalizedManual.explicitPort, showToast = true)
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("probe_custom_host_btn")
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = "فحص السيرفر والمنافذ")
                                        }
                                    },
                                    supportingText = {
                                        Text(
                                            text = "أدخل الدومين أو عنوان IP الخاص بالمنصة المشترك بها أو السيرفر المخصص",
                                            fontSize = 11.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("router_host_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                // Option to open target directly as subscribed web platform browser
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { preferWebPlatformBrowser = !preferWebPlatformBrowser }
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
                                                text = "فتح كمنصة / متصفح ويب مشترك بها",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "فعّله لجلب المنصة والمتصفح حتى عند إدخال IP أو دومين مخصص",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = preferWebPlatformBrowser,
                                            onCheckedChange = { preferWebPlatformBrowser = it },
                                            modifier = Modifier.testTag("prefer_web_browser_switch")
                                        )
                                    }
                                }

                                // Optional Custom Port & Alias inside Option 2 (no duplicate IP field)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showCustomPortOptions = !showCustomPortOptions }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Text(
                                                    text = "تخصيص المنفذ واسم المنصة (اختياري)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Icon(
                                                imageVector = if (showCustomPortOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = showCustomPortOptions,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    listOf(
                                                        443 to "443 (HTTPS)",
                                                        80 to "80 (HTTP)",
                                                        8080 to "8080 (بديل)",
                                                        8728 to "8728 (API)",
                                                        8729 to "8729 (SSL)"
                                                    ).forEach { (port, label) ->
                                                        FilterChip(
                                                            selected = manualPort == port,
                                                            onClick = {
                                                                manualPort = port
                                                                manualUseHttps = (port == 443 || port == 8729)
                                                                if (port in listOf(8728, 8729)) {
                                                                    preferWebPlatformBrowser = false
                                                                }
                                                            },
                                                            label = { Text(label, fontSize = 11.sp) },
                                                            modifier = Modifier.height(48.dp)
                                                        )
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = manualPort.toString(),
                                                    onValueChange = { input ->
                                                        val p = input.filter { it.isDigit() }.toIntOrNull()
                                                        if (p != null && p in 1..65535) {
                                                            manualPort = p
                                                        } else if (input.isBlank()) {
                                                            manualPort = 443
                                                        }
                                                    },
                                                    label = { Text("رقم المنفذ المخصص (Port)") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )

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
                                                        Text("اتصال مشفر (HTTPS / SSL)", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Switch(
                                                        checked = manualUseHttps,
                                                        onCheckedChange = { manualUseHttps = it }
                                                    )
                                                }

                                                OutlinedTextField(
                                                    value = manualRouterName,
                                                    onValueChange = { manualRouterName = it },
                                                    label = { Text("اسم مستعار للمنصة أو السيرفر (اختياري)") },
                                                    placeholder = { Text("منصة الشبكة / السيرفر البعيد") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Username Input
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
                            label = {
                                Text(
                                    if (!isCustomPlatformMode) "اسم المستخدم للراوتر" else "اسم المستخدم أو البريد الإلكتروني"
                                )
                            },
                            placeholder = {
                                Text(if (!isCustomPlatformMode) "admin" else "admin أو user@example.com")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = RouterBlue)
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (isCustomPlatformMode) KeyboardType.Email else KeyboardType.Text
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("router_user_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("كلمة المرور (Password)") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = RouterBlue)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
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
                                .minimumInteractiveComponentSize()
                                .clickable { rememberCredentials = !rememberCredentials }
                        ) {
                            Checkbox(
                                checked = rememberCredentials,
                                onCheckedChange = { rememberCredentials = it }
                            )
                            Text(
                                text = "تذكر بيانات الدخول للاتصال التلقائي",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Primary Login CTA Button
                        Button(
                            onClick = {
                                if (!isCustomPlatformMode) {
                                    val config = RouterConnectionConfig(
                                        name = "راوتر $autoDetectedIp",
                                        ipAddress = autoDetectedIp.trim(),
                                        port = autoDetectedApiPort,
                                        username = username.trim(),
                                        password = password,
                                        useHttps = autoDetectedApiHttps,
                                        autoDetectProtocol = true,
                                        preferredPath = null,
                                        isDemoMode = false,
                                        directApiOnly = true,
                                        preferWebPortal = false
                                    )
                                    viewModel.connectToRouter(config, saveToFavorites = rememberCredentials, context = context)
                                } else {
                                    val rawTarget = manualIp.trim()
                                    val parsedTarget = RouterConnectionParser.normalizeHostInput(rawTarget)
                                    val cleanHost = parsedTarget.host.ifBlank { activeIp.trim() }
                                    val resolvedPort = parsedTarget.explicitPort ?: manualPort
                                    val resolvedHttps = parsedTarget.inferredHttps ?: manualUseHttps
                                    val resolvedPath = parsedTarget.rawPath ?: manualPreferredPath
                                    val config = RouterConnectionConfig(
                                        name = if (manualRouterName.isNotBlank()) manualRouterName else cleanHost,
                                        ipAddress = cleanHost,
                                        port = resolvedPort,
                                        username = username.trim(),
                                        password = password,
                                        useHttps = resolvedHttps,
                                        autoDetectProtocol = parsedTarget.explicitPort == null && !preferWebPlatformBrowser,
                                        preferredPath = resolvedPath,
                                        isDemoMode = false,
                                        directApiOnly = false,
                                        preferWebPortal = preferWebPlatformBrowser
                                    )
                                    viewModel.connectToRouter(config, saveToFavorites = rememberCredentials, context = context)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("connect_router_button"),
                            shape = RoundedCornerShape(14.dp),
                            enabled = connectionState !is ConnectionState.Verifying && username.isNotBlank() && activeIp.isNotBlank()
                        ) {
                            Icon(
                                imageVector = if (!isCustomPlatformMode) Icons.Default.Bolt else Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!isCustomPlatformMode) {
                                    "اتصال تلقائي وجلب بيانات الميكروتك"
                                } else {
                                    "اتصال بالسيرفر / المنصة الآن"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        // Demo Mode Button
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

            // 3. Single Unified Saved Routers / Platforms Quick Reconnect Card
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
                                        text = "السيرفرات والراوترات المحفوظة (${savedRouters.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "دخول فوري بنقرة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            savedRouters.forEach { router ->
                                var cacheSummary by remember(router.ipAddress) { mutableStateOf<RouterCacheSummary?>(null) }
                                var portalCacheMeta by remember(router.ipAddress) { mutableStateOf<CachedPortalMeta?>(null) }
                                LaunchedEffect(router.ipAddress) {
                                    cacheSummary = RouterCacheManager.getCacheSummary(context, router.ipAddress)
                                    portalCacheMeta = WebPortalCacheManager.loadMeta(context, router.ipAddress)
                                }
                                val isKnownCloudPortal = (portalCacheMeta != null && cacheSummary == null && !portalCacheMeta!!.isMikroTikWebFig) ||
                                    router.ipAddress.contains("apluswifi.com", ignoreCase = true)
                                val hasAnyCache = cacheSummary != null || (portalCacheMeta != null && isKnownCloudPortal)

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
                                                if (isKnownCloudPortal) {
                                                    isCustomPlatformMode = true
                                                    preferWebPlatformBrowser = true
                                                    manualIp = router.ipAddress
                                                    manualPort = router.port
                                                    manualUseHttps = router.useHttps
                                                } else if (autoDetectedIp.isNotBlank() && router.ipAddress.equals(autoDetectedIp, ignoreCase = true)) {
                                                    isCustomPlatformMode = false
                                                    preferWebPlatformBrowser = false
                                                } else {
                                                    isCustomPlatformMode = true
                                                    preferWebPlatformBrowser = false
                                                    manualIp = router.ipAddress
                                                    val normalizedPort = if (router.port in listOf(80, 443, 8080)) 8728 else router.port
                                                    manualPort = normalizedPort
                                                    manualUseHttps = (normalizedPort == 8729)
                                                }
                                                Toast.makeText(context, "تم تحديد بيانات ${router.name}", Toast.LENGTH_SHORT).show()
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
                                                        imageVector = if (isKnownCloudPortal) Icons.Default.Language else Icons.Default.Router,
                                                        contentDescription = null,
                                                        tint = RouterBlue,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = router.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${router.ipAddress} • ${router.username}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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
                                                        val targetPort = if (!isKnownCloudPortal && router.port in listOf(80, 443, 8080)) 8728 else router.port
                                                        val config = RouterConnectionConfig(
                                                            id = router.id,
                                                            name = router.name,
                                                            ipAddress = router.ipAddress,
                                                            port = targetPort,
                                                            username = router.username,
                                                            password = router.password,
                                                            useHttps = if (!isKnownCloudPortal) targetPort == 8729 else router.useHttps,
                                                            autoDetectProtocol = true,
                                                            isDemoMode = false,
                                                            directApiOnly = !isKnownCloudPortal,
                                                            preferWebPortal = isKnownCloudPortal
                                                        )
                                                        viewModel.connectToRouter(config, saveToFavorites = true, context = context)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (hasAnyCache) StatusActiveGreen else RouterBlue
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (hasAnyCache) Icons.Default.Bolt else Icons.Default.Login,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isKnownCloudPortal) "فتح المنصة" else if (hasAnyCache) "⚡ دخول" else "اتصال",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deleteSavedRouter(router.id) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "حذف من المحفوظات",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

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
                                                            text = "كاش النظام: ${cacheSummary?.cardsCount} كرت • ${cacheSummary?.profilesCount} باقة",
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
                                        } else if (isKnownCloudPortal && portalCacheMeta != null) {
                                            val meta = portalCacheMeta!!
                                            Surface(
                                                color = Color(0xFFFFF3E0),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.6.dp, Color(0xFFFB8C00)),
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
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CloudOff,
                                                            contentDescription = null,
                                                            tint = Color(0xFFE65100),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = if (meta.hasValidArchive) {
                                                                "منصة ويب محفوظة للتصفح أوفلاين"
                                                            } else {
                                                                "منصة ويب خارجية"
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF6D3500)
                                                        )
                                                    }
                                                    if (meta.lastSavedTime > 0L) {
                                                        Text(
                                                            text = WebPortalCacheManager.formatSavedTimeAr(meta.lastSavedTime),
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF8D4E00)
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
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
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
