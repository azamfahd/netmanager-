package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.UserLoginHistoryEntity
import com.example.data.local.toUserSessionRecord
import com.example.data.model.DeviceCategory
import com.example.data.model.SubscriberFullProfile
import com.example.data.model.UserSessionRecord
import com.example.data.network.NetworkUtils
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusErrorRedBg

@Composable
fun UserDetailDialog(
    session: UserSessionRecord,
    subscriberProfile: SubscriberFullProfile? = null,
    loginHistory: List<UserLoginHistoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onKickUser: (() -> Unit)? = null,
    onOpenAnalytics: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "تم نسخ $label بنجاح: $text", Toast.LENGTH_SHORT).show()
    }

    val deviceIcon: ImageVector = when (session.deviceInfo.category) {
        DeviceCategory.IPHONE_IPAD -> Icons.Default.PhoneIphone
        DeviceCategory.ANDROID_PHONE -> Icons.Default.PhoneAndroid
        DeviceCategory.LAPTOP_PC -> Icons.Default.Laptop
        DeviceCategory.ROUTER_AP -> Icons.Default.Router
        DeviceCategory.SMART_DEVICE -> Icons.Default.Tv
    }

    val brandBadgeColor = when (session.deviceInfo.brand.lowercase()) {
        "apple" -> Color(0xFF424242)
        "samsung" -> Color(0xFF1428A0)
        "xiaomi" -> Color(0xFFFF6900)
        "huawei" -> Color(0xFFCF0A2C)
        "infinix", "tecno" -> Color(0xFF00897B)
        "oppo", "realme" -> Color(0xFF2E7D32)
        else -> RouterBlue
    }

    var isMaximized by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = الاستهلاك الكلي, 1 = الباقات, 2 = الجلسات والأجهزة
    var selectedMetricDetail by remember { mutableStateOf<String?>(null) } // "total", "download", "upload"

    val lifetimeTotalBytes = subscriberProfile?.lifetimeTotalBytes ?: session.totalBytes
    val lifetimeDownloadBytes = subscriberProfile?.lifetimeBytesOut ?: session.bytesOut
    val lifetimeUploadBytes = subscriberProfile?.lifetimeBytesIn ?: session.bytesIn
    val lifetimeUptimeStr = subscriberProfile?.lifetimeUptimeFormatted?.ifBlank { session.duration } ?: session.duration
    val totalSessionsCount = subscriberProfile?.totalSessionsCount ?: maxOf(1, loginHistory.size)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isMaximized),
        modifier = if (isMaximized) {
            Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.95f)
        } else {
            Modifier.fillMaxWidth()
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(brandBadgeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = deviceIcon,
                                contentDescription = null,
                                tint = brandBadgeColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = session.username,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                IconButton(
                                    onClick = { copyToClipboard("كود الكرت", session.username) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "نسخ الكود",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${session.deviceInfo.brand} ${session.deviceInfo.modelName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusPill(
                            text = if (session.isOnline) "متصل الآن" else "غير متصل",
                            type = if (session.isOnline) StatusType.SUCCESS else StatusType.INFO
                        )

                        IconButton(
                            onClick = { isMaximized = !isMaximized },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isMaximized) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                contentDescription = if (isMaximized) "تصغير" else "تكبير للكل",
                                tint = RouterBlue
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Sub-tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("الاستهلاك الكلي", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("الباقات المفعلة", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("الأجهزة والسجل", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // TAB 0: الاستهلاك التراكمي الشامل لكل الأوقات والرصيد
                if (selectedTab == 0) {
                    // Cumulative Lifetime Card (Enhanced Modern Material 3 Design)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, RouterBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Smart Analytics Grid (2x2 Interactive Design)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UsageMetricCard(
                                        modifier = Modifier.weight(1f),
                                        title = "الإجمالي",
                                        value = NetworkUtils.formatBytes(lifetimeTotalBytes),
                                        icon = Icons.Default.DataUsage,
                                        color = RouterBlue,
                                        onClick = { onOpenAnalytics?.invoke("total") }
                                    )
                                    UsageMetricCard(
                                        modifier = Modifier.weight(1f),
                                        title = "التحميل",
                                        value = NetworkUtils.formatBytes(lifetimeDownloadBytes),
                                        icon = Icons.Default.ArrowDownward,
                                        color = StatusActiveGreen,
                                        onClick = { onOpenAnalytics?.invoke("download") }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UsageMetricCard(
                                        modifier = Modifier.weight(1f),
                                        title = "الرفع",
                                        value = NetworkUtils.formatBytes(lifetimeUploadBytes),
                                        icon = Icons.Default.ArrowUpward,
                                        color = Color(0xFF673AB7),
                                        onClick = { onOpenAnalytics?.invoke("upload") }
                                    )
                                    UsageMetricCard(
                                        modifier = Modifier.weight(1f),
                                        title = "الوقت الكلي",
                                        value = lifetimeUptimeStr,
                                        icon = Icons.Default.Schedule,
                                        color = Color(0xFFFF9800),
                                        onClick = { onOpenAnalytics?.invoke("time") }
                                    )
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Additional Session Metrics
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "إجمالي الوقت المستهلك:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(text = lifetimeUptimeStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "عدد الجلسات التراكمية المسجلة:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = RouterBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$totalSessionsCount جلسات تفعيل",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = RouterBlue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "استهلاك الجلسة الحالية:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(text = NetworkUtils.formatBytes(session.totalBytes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Balance & Quota Card
                    val quotaLimit = subscriberProfile?.quotaLimitBytes ?: session.packageLimitBytes
                    val isExhausted = subscriberProfile?.isQuotaExhausted ?: session.isQuotaExhausted || (quotaLimit > 0L && lifetimeTotalBytes >= quotaLimit)
                    val remainingBytes = if (quotaLimit > 0L) (quotaLimit - lifetimeTotalBytes).coerceAtLeast(0L) else 0L
                    val quotaPercent = if (quotaLimit > 0L) (lifetimeTotalBytes.toFloat() / quotaLimit.toFloat()).coerceIn(0f, 1f) else 0f

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExhausted) StatusErrorRedBg else StatusActiveGreenBg,
                        border = BorderStroke(1.dp, if (isExhausted) StatusErrorRed.copy(alpha = 0.5f) else StatusActiveGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isExhausted) "⚠️ تم استنفاد الرصيد بالكامل" else "✅ الكرت نشط ويوجد رصيد متبقي",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExhausted) StatusErrorRed else StatusActiveGreen
                                )
                                if (quotaLimit > 0L) {
                                    Text(
                                        text = "${(quotaPercent * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isExhausted) StatusErrorRed else StatusActiveGreen
                                    )
                                }
                            }
                            if (quotaLimit > 0L) {
                                LinearProgressIndicator(
                                    progress = { quotaPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (isExhausted) StatusErrorRed else StatusActiveGreen
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "المتبقي: ${NetworkUtils.formatBytes(remainingBytes)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(text = "من إجمالي: ${NetworkUtils.formatBytes(quotaLimit)}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (subscriberProfile?.timeRemainingFormatted?.isNotBlank() == true) {
                                Text(text = "الوقت المتبقي: ${subscriberProfile.timeRemainingFormatted}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // TAB 1: الباقات المفعلة والتجديدات
                if (selectedTab == 1) {
                    val packages = subscriberProfile?.activatedPackages.orEmpty()
                    if (packages.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "الباقة المفعلة: ${session.profile}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = "السعر: ${session.price}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        packages.forEach { pkg ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (pkg.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, if (pkg.isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = pkg.profileName, fontWeight = FontWeight.Bold, color = if (pkg.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        StatusPill(text = if (pkg.isCurrent) "الباقة الحالية" else "باقة سابقة", type = if (pkg.isCurrent) StatusType.SUCCESS else StatusType.INFO)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "السعر: ${pkg.price}", style = MaterialTheme.typography.labelSmall)
                                        Text(text = "السرعة: ${pkg.rateLimit}", style = MaterialTheme.typography.labelSmall, color = RouterBlue, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "الاستهلاك: ${NetworkUtils.formatBytes(pkg.totalBytesUsedOnPackage)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(text = "${pkg.sessionsCountOnPackage} جلسات", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: الأجهزة وعناوين الاتصال وسجل الجلسات
                if (selectedTab == 2) {
                    // Network Details
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { copyToClipboard("عنوان IP", session.ipAddress) },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "عنوان IP:", style = MaterialTheme.typography.bodySmall)
                                Text(text = session.ipAddress, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { copyToClipboard("عنوان MAC", session.macAddress) },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "عنوان MAC:", style = MaterialTheme.typography.bodySmall)
                                Text(text = session.macAddress, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "نقطة / برج الاتصال:", style = MaterialTheme.typography.bodySmall)
                                Text(text = session.nodeName.ifBlank { "الراوتر الرئيسي" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = RouterBlue)
                            }
                        }
                    }

                    // Sessions List
                    val sessions = subscriberProfile?.sessionHistory.orEmpty().ifEmpty {
                        loginHistory.map { it.toUserSessionRecord() }
                    }
                    if (sessions.isEmpty()) {
                        Text(text = "لا توجد جلسات سابقة مسجلة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        sessions.take(6).forEach { rec ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "الدخول: ${rec.loginTime}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        StatusPill(text = if (rec.isOnline) "متصل الآن" else "انتهت (${rec.duration})", type = if (rec.isOnline) StatusType.SUCCESS else StatusType.INFO)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "${rec.deviceInfo.brand} ${rec.deviceInfo.modelName}", style = MaterialTheme.typography.labelSmall)
                                        Text(text = NetworkUtils.formatBytes(rec.totalBytes), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.isOnline && onKickUser != null) {
                    Button(
                        onClick = {
                            onKickUser()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طرد المستخدم")
                    }
                }

                Button(
                    onClick = {
                        val reportText = buildString {
                            appendLine("=== التقرير الشامل للمشترك واستخدامه لكل الأوقات ===")
                            appendLine("رقم الكرت: ${session.username}")
                            appendLine("الاستهلاك التراكمي الشامل: ${NetworkUtils.formatBytes(lifetimeTotalBytes)}")
                            appendLine("التحميل الكلي: ${NetworkUtils.formatBytes(lifetimeDownloadBytes)}")
                            appendLine("الرفع الكلي: ${NetworkUtils.formatBytes(lifetimeUploadBytes)}")
                            appendLine("الوقت التراكمي: $lifetimeUptimeStr")
                            appendLine("عدد الجلسات: $totalSessionsCount")
                            appendLine("الجلسة الحالية: ${NetworkUtils.formatBytes(session.totalBytes)} (${session.duration})")
                            appendLine("الجهاز: ${session.deviceInfo.brand} - ${session.deviceInfo.modelName}")
                            appendLine("عنوان IP: ${session.ipAddress}")
                            appendLine("عنوان MAC: ${session.macAddress}")
                            appendLine("الباقة الحالية: ${session.profile} (${session.price})")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير المشترك"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RouterBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة التقرير")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إغلاق")
                }
            }
        }
    )
}

@Composable
fun UsageMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
