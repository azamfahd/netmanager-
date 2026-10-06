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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentConfig by viewModel.currentConfig.collectAsStateWithLifecycle()
    val ipBindings by viewModel.ipBindings.collectAsStateWithLifecycle()

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
}
