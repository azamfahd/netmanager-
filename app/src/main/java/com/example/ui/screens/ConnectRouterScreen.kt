package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectRouterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val localNetwork by viewModel.localNetworkInfo.collectAsStateWithLifecycle()
    val discoveredRouter by viewModel.discoveredRouter.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val savedRouters by viewModel.savedRouters.collectAsStateWithLifecycle()

    // Login state - User only needs to enter username and password!
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberCredentials by remember { mutableStateOf(true) }

    // Advanced manual customization toggle
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableIntStateOf(80) }
    var manualUseHttps by remember { mutableStateOf(false) }
    var manualRouterName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.checkLocalNetwork(context)
    }

    // Keep manual IP in sync with auto-detected router if not set manually
    LaunchedEffect(discoveredRouter) {
        discoveredRouter?.let { discovered ->
            if (manualIp.isBlank()) {
                manualIp = discovered.ip
                manualPort = discovered.port
                manualUseHttps = discovered.useHttps
            }
        }
    }

    val activeIp = if (manualIp.isNotBlank()) manualIp else (discoveredRouter?.ip ?: localNetwork.gatewayIp ?: "192.168.88.1")
    val activePort = if (manualIp.isNotBlank()) manualPort else (discoveredRouter?.port ?: 80)
    val activeHttps = if (manualIp.isNotBlank()) manualUseHttps else (discoveredRouter?.useHttps ?: false)

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                                text = "التعرف التلقائي على الراوتر وتسجيل الدخول",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkLocalNetwork(context) },
                        modifier = Modifier.testTag("refresh_discovery_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "إعادة فحص واكتشاف الراوتر"
                        )
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

            // 1. Hero Auto-Discovered Router Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_discovered_router_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
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
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "تم التعرف على الراوتر تلقائياً",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            if (isDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                StatusPill(
                                    text = "راوتر نشط",
                                    type = StatusType.SUCCESS
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Display of Detected IP and Port
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "عنوان IP الراوتر:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = activeIp,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "المنفذ (Port):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$activePort",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Wi-Fi Connection Info
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
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = localNetwork.wifiSsid ?: "شبكة Wi-Fi نشطة",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "IP جهازك: ${localNetwork.deviceIp ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "تعذر تسجيل الدخول إلى الراوتر",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("الدخول بالوضع التجريبي بنفس البيانات للاستكشاف")
                            }
                        }
                    }
                }
            }

            // 2. Simple Main Form: Only Username & Password required!
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "تسجيل الدخول إلى الراوتر",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أدخل بيانات حساب إدارة راوتر ميكروتك للاتصال مباشرة:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Username (default "admin")
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("اسم المستخدم (Username)") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = RouterBlue)
                            },
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
                            modifier = Modifier.clickable { rememberCredentials = !rememberCredentials }
                        ) {
                            Checkbox(
                                checked = rememberCredentials,
                                onCheckedChange = { rememberCredentials = it }
                            )
                            Text(
                                text = "تذكر بيانات الراوتر للاتصال التلقائي",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Big Primary Login Button
                        Button(
                            onClick = {
                                val config = RouterConnectionConfig(
                                    name = if (manualRouterName.isNotBlank()) manualRouterName else "راوتر $activeIp",
                                    ipAddress = activeIp.trim(),
                                    port = activePort,
                                    username = username.trim(),
                                    password = password,
                                    useHttps = activeHttps,
                                    isDemoMode = false
                                )
                                viewModel.connectToRouter(config, saveToFavorites = rememberCredentials)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("connect_router_button"),
                            shape = RoundedCornerShape(14.dp),
                            enabled = connectionState !is ConnectionState.Verifying && username.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تسجيل الدخول والاتصال بالراوتر",
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

            // 3. Collapsible Advanced / Manual Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "إعدادات متقدمة وتخصيص العنوان يدوياً",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualIp,
                                    onValueChange = { manualIp = it },
                                    label = { Text("عنوان IP مخصص") },
                                    placeholder = { Text(activeIp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column {
                                    Text(
                                        text = "المنفذ المخصص (Port):",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(80, 443, 8728, 8080).forEach { port ->
                                            FilterChip(
                                                selected = manualPort == port,
                                                onClick = {
                                                    manualPort = port
                                                    if (port == 443) manualUseHttps = true
                                                },
                                                label = { Text("$port") }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("استخدام اتصال آمن (HTTPS / SSL)")
                                    Switch(
                                        checked = manualUseHttps,
                                        onCheckedChange = { manualUseHttps = it }
                                    )
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
                            viewModel.connectToRouter(config, saveToFavorites = false)
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
}

@Composable
fun SavedRouterItem(
    router: SavedRouterEntity,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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

            Row {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text("اتصال")
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
    }
}

