package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExitToApp
import com.google.firebase.auth.auth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.UpdateCheckState
import com.example.data.model.UpdateDownloadState
import com.example.data.util.AppUpdateManager
import com.example.ui.components.AppUpdateDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.IpBinding
import com.example.data.network.NetworkUtils
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun NetworkToolsScreen(
    viewModel: MainViewModel,
    onOpenSystemEventLog: () -> Unit = {},
    onTriggerSignIn: () -> Unit = {},
    onOpenAdminAudit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentConfig by viewModel.currentConfig.collectAsStateWithLifecycle()
    val ipBindings by viewModel.ipBindings.collectAsStateWithLifecycle()
    val currentUser = com.google.firebase.Firebase.auth.currentUser

    val context = LocalContext.current
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var manualCheckTriggered by remember { mutableStateOf(false) }

    var pingHost by remember { mutableStateOf(currentConfig.ipAddress) }
    var isPinging by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }

    var showRebootDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Ping Utility Card
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RouterBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = RouterBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "أداة فحص الاتصال والاستجابة (Ping)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "فحص استجابة وسرعة الوصول للراوتر أو أي سيرفر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pingHost,
                            onValueChange = { pingHost = it },
                            label = { Text("عنوان IP أو الدومين") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ping_host_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                isPinging = true
                                pingResult = null
                                coroutineScope.launch {
                                    val res = NetworkUtils.pingHost(pingHost)
                                    pingResult = res
                                    isPinging = false
                                }
                            },
                            enabled = !isPinging && pingHost.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(52.dp).testTag("ping_submit_btn")
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فحص")
                            }
                        }
                    }

                    pingResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        val (success, timeMs) = result
                        Surface(
                            color = if (success) StatusActiveGreen.copy(alpha = 0.1f) else StatusErrorRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (success) StatusActiveGreen else StatusErrorRed
                                )
                                Text(
                                    text = if (success) "تم الوصول بنجاح! وقت الاستجابة: $timeMs مللي ثانية" else "فشل الوصول للعنوان أو انتهت المهلة",
                                    fontWeight = FontWeight.Bold,
                                    color = if (success) StatusActiveGreen else StatusErrorRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // IP Bindings (Bypassed Devices)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NetworkCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NetworkCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "الأجهزة المعفاة من تسجيل الدخول (IP Binding)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "أجهزة تعمل بدون كرت (كاميرات مراقبة، أجهزة الإدارة)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (ipBindings.isEmpty()) {
                        Text(
                            text = "لا توجد أجهزة معفاة مسجلة حالياً",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ipBindings.forEach { binding ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = binding.comment.ifBlank { "جهاز معفى" },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "IP: ${binding.address} • MAC: ${binding.macAddress}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(text = "معفى (Bypassed)", type = StatusType.SUCCESS)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // App Updates Card (GitHub Releases Integration)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("app_updates_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RouterBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = RouterBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "تحديثات وترقية البرنامج",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "فحص التحديثات وتنزيل أحدث إصدار مع التثبيت المباشر",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Current version and status pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "الإصدار الحالي المثبت:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${viewModel.currentAppVersion}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = RouterBlue
                            )
                        }

                        StatusPill(
                            text = when (updateCheckState) {
                                is UpdateCheckState.Checking -> "جاري الفحص..."
                                is UpdateCheckState.UpdateAvailable -> "يوجد تحديث جديد!"
                                is UpdateCheckState.UpToDate -> "محدث لأحدث نسخة"
                                is UpdateCheckState.Error -> "فحص غير متاح"
                                else -> "جاهز للفحص"
                            },
                            type = when (updateCheckState) {
                                is UpdateCheckState.UpdateAvailable -> StatusType.WARNING
                                is UpdateCheckState.UpToDate -> StatusType.SUCCESS
                                is UpdateCheckState.Error -> StatusType.ERROR
                                else -> StatusType.INFO
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Conditional status banner
                    when (val state = updateCheckState) {
                        is UpdateCheckState.UpdateAvailable -> {
                            Surface(
                                color = StatusActiveGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = StatusActiveGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "يتوفر إصدار جديد: ${state.release.tagName}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StatusActiveGreen
                                        )
                                    }

                                    state.release.apkAsset?.let { asset ->
                                        Text(
                                            text = "حجم ملف التحديث: ${asset.formattedSize}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (state.release.body.isNotBlank()) {
                                        Text(
                                            text = state.release.body.take(120) + if (state.release.body.length > 120) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { showUpdateDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen),
                                        modifier = Modifier.fillMaxWidth().testTag("install_update_now_btn")
                                    ) {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تحديث وتثبيت الآن (بدون فقد البيانات)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        is UpdateCheckState.UpToDate -> {
                            Surface(
                                color = StatusActiveGreen.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StatusActiveGreen
                                    )
                                    Text(
                                        text = "تطبيقك محدث بالكامل لأحدث إصدار (${state.latestTag})",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusActiveGreen
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        is UpdateCheckState.Error -> {
                            Surface(
                                color = StatusErrorRed.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = StatusErrorRed)
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusErrorRed
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        else -> {}
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.checkForUpdates(isManual = true) },
                            enabled = updateCheckState !is UpdateCheckState.Checking,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("check_updates_now_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = RouterBlue)
                        ) {
                            if (updateCheckState is UpdateCheckState.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري الفحص...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("التحقق من التحديثات", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.forceInstallLatestRelease(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تثبيت آخر نسخة على أي حال", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                    
                    // Trigger dialog immediately if manual check finds an update
                    LaunchedEffect(updateCheckState) {
                        if (manualCheckTriggered && updateCheckState is UpdateCheckState.UpdateAvailable) {
                            showUpdateDialog = true
                            manualCheckTriggered = false
                        }
                    }
                }
            }
        }

        // System Event Log Card (سجل أحداث وعمليات الراوتر والشفافية)
        item {
            val eventLogs by viewModel.systemEventLogs.collectAsStateWithLifecycle()
            val addCount = remember(eventLogs) { eventLogs.count { it.actionType == "إضافة" } }
            val editCount = remember(eventLogs) { eventLogs.count { it.actionType == "تعديل" } }
            val deleteCount = remember(eventLogs) { eventLogs.count { it.actionType == "حذف" } }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_event_log_entry_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RouterBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ListAlt, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = "سجل أحداث النظام (System Event Log)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "توثيق وتتبع عمليات الإضافة، التعديل، والحذف",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        StatusPill(
                            text = "${eventLogs.size} عملية",
                            type = StatusType.INFO
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusActiveGreen.copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("+$addCount", fontWeight = FontWeight.Bold, color = StatusActiveGreen, fontSize = 14.sp)
                                Text("إضافات", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RouterBlue.copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("~$editCount", fontWeight = FontWeight.Bold, color = RouterBlue, fontSize = 14.sp)
                                Text("تعديلات", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusErrorRed.copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("-$deleteCount", fontWeight = FontWeight.Bold, color = StatusErrorRed, fontSize = 14.sp)
                                Text("حذفيات", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onOpenSystemEventLog,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_open_system_event_log"),
                        colors = ButtonDefaults.buttonColors(containerColor = RouterBlue)
                    ) {
                        Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح ومراقبة سجل أحداث النظام (شفافية كاملة)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onOpenAdminAudit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_open_admin_audit")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("سجل التدقيق والصلاحيات الإدارية", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Cloud Sync & Account Linking Card (المزامنة السحابية وربط الحساب)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_sync_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "المزامنة السحابية (Cloud Sync)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ربط البرنامج بحساب جوجل لحماية البيانات والمزامنة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        StatusPill(
                            text = if (currentUser != null) "مرتبط" else "غير مرتبط",
                            type = if (currentUser != null) StatusType.SUCCESS else StatusType.WARNING
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentUser != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // User info or avatar placeholder
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser.email?.take(1)?.uppercase() ?: "U",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser.displayName ?: "مستخدم البرنامج",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentUser.email ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                com.google.firebase.Firebase.auth.signOut()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusErrorRed)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الخروج وإلغاء المزامنة السحابية")
                        }
                    } else {
                        Text(
                            text = "المزامنة السحابية تتيح لك حفظ نسخة احتياطية من جميع العمليات والكروت والمبيعات في السحاب بشكل آمن، مما يضمن عدم فقدانها حتى في حال تغيير الجهاز.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onTriggerSignIn,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ربط البرنامج بحساب جوجل وتفعيل المزامنة")
                        }
                    }
                }
            }
        }

        // Local Storage & Database Management Card (إدارة قاعدة البيانات والتخزين المحلي)
        item {
            val cards by viewModel.cards.collectAsStateWithLifecycle()
            val profiles by viewModel.profiles.collectAsStateWithLifecycle()
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            val isOffline = (connectionState as? com.example.data.model.ConnectionState.Connected)?.isOffline == true
            val lastSyncTime = (connectionState as? com.example.data.model.ConnectionState.Connected)?.lastSyncTime ?: 0L

            Card(
                modifier = Modifier.fillMaxWidth().testTag("local_storage_management_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StatusActiveGreen.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = StatusActiveGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "قاعدة البيانات والتخزين المحلي (Offline DB)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "حفظ البيانات للعمل في حال عدم توفر الإنترنت",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        StatusPill(
                            text = if (isOffline) "وضع محلي" else "متزامن ومحفوظ",
                            type = if (isOffline) StatusType.WARNING else StatusType.SUCCESS
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الكروت المحفوظة محلياً:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${cards.size} كرت هوتسبوت",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "باقات السرعات المسجلة:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${profiles.size} باقة",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "آخر حفظ ومزامنة للبيانات:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = com.example.data.local.RouterCacheManager.formatCacheAge(lastSyncTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusActiveGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = StatusActiveGreen.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = StatusActiveGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "يتم الحفظ والمزامنة والتحديثات تلقائياً وبشكل فوري ومتزامن في الخلفية.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusActiveGreen
                            )
                        }
                    }
                }
            }
        }

        // Router Control Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "إدارة الاتصال والراوتر",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = { showRebootDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reboot_router_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إعادة تشغيل راوتر ميكروتك (Reboot)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showDisconnectDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("disconnect_router_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("قطع الاتصال وتبديل الراوتر", fontWeight = FontWeight.Bold)
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
            title = "إعادة تشغيل الراوتر",
            message = "هل أنت متأكد من رغبتك في إرسال أمر إعادة التشغيل؟",
            confirmText = "إعادة التشغيل",
            isDestructive = true,
            onConfirm = { viewModel.rebootRouter() },
            onDismiss = { showRebootDialog = false }
        )
    }

    if (showDisconnectDialog) {
        ConfirmDialog(
            title = "قطع الاتصال بالراوتر",
            message = "هل تريد الخروج والعودة لشاشة الاتصال بالراوترات؟",
            confirmText = "قطع الاتصال",
            isDestructive = false,
            onConfirm = { viewModel.disconnect() },
            onDismiss = { showDisconnectDialog = false }
        )
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
