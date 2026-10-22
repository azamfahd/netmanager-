package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserLoginHistoryEntity
import com.example.data.model.DeviceCategory
import com.example.data.model.SubscriberFullProfile
import com.example.data.model.UserSessionRecord
import com.example.data.network.NetworkUtils
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusErrorRedBg
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWarningAmberBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSessionDetailScreen(
    session: UserSessionRecord,
    subscriberProfile: SubscriberFullProfile? = null,
    loginHistory: List<UserLoginHistoryEntity> = emptyList(),
    onBackClick: () -> Unit,
    onKickUser: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "تم نسخ $label: $text", Toast.LENGTH_SHORT).show()
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

    // Tab filter for views inside details: 0 = نظرة شاملة واستخدام كلي, 1 = الباقات والتفعيلات, 2 = سجل الجلسات وتفاصيل النشاط
    var selectedSectionTab by remember { mutableIntStateOf(0) }

    // Aggregate lifetime values from profile or session
    val lifetimeTotalBytes = subscriberProfile?.lifetimeTotalBytes ?: session.totalBytes
    val lifetimeDownloadBytes = subscriberProfile?.lifetimeBytesOut ?: session.bytesOut
    val lifetimeUploadBytes = subscriberProfile?.lifetimeBytesIn ?: session.bytesIn
    val lifetimeUptimeStr = subscriberProfile?.lifetimeUptimeFormatted?.ifBlank { session.duration } ?: session.duration
    val totalSessionsCount = subscriberProfile?.totalSessionsCount ?: maxOf(1, loginHistory.size)
    val firstSeenDate = subscriberProfile?.firstSeenDate ?: "مسجل بالراوتر"
    val lastSeenDate = subscriberProfile?.lastSeenDate ?: if (session.isOnline) "متصل الآن" else session.logoutTime

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "الملف الشامل للمشترك والاستهلاك",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "كافة بيانات الاستخدام التراكمي والباقات المفعلة لـ ${session.username}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث البيانات الحية",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Subscriber Identity Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    brandBadgeColor.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(brandBadgeColor.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = deviceIcon,
                                        contentDescription = null,
                                        tint = brandBadgeColor,
                                        modifier = Modifier.size(26.dp)
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
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        IconButton(
                                            onClick = { copyToClipboard("اسم المستخدم / كود الكرت", session.username) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "نسخ",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${session.deviceInfo.brand} • ${session.deviceInfo.modelName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            StatusPill(
                                text = if (session.isOnline) "متصل الآن (نشط)" else "جلسة مغلقة",
                                type = if (session.isOnline) StatusType.SUCCESS else StatusType.INFO
                            )
                        }

                        // Subscriber Extra Meta (POS Agent, Node, Profile)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                                            imageVector = Icons.Default.CellTower,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "برج / نقطة الاتصال: ${session.nodeName.ifBlank { "الراوتر الرئيسي" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Text(
                                        text = "الباقة: ${session.profile} (${session.price})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (subscriberProfile?.assignedAgentName?.isNotBlank() == true) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = RouterBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "نقطة البيع / الموزع: ${subscriberProfile.assignedAgentName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RouterBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Section Selector Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSectionTab == 0,
                    onClick = { selectedSectionTab = 0 },
                    label = { Text("الاستهلاك التراكمي الشامل", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(Icons.Default.DataUsage, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedSectionTab == 1,
                    onClick = { selectedSectionTab = 1 },
                    label = { Text("الباقات والتفعيلات", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedSectionTab == 2,
                    onClick = { selectedSectionTab = 2 },
                    label = { Text("سجل النشاط والأجهزة", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // =========================================================================
            // SECTION 0: Comprehensive Lifetime Usage & Quota (الاستهلاك التراكمي الشامل)
            // =========================================================================
            if (selectedSectionTab == 0) {
                // 2. All-Time Lifetime Data Consumption Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
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
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DataUsage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "الاستهلاك التراكمي لكل الأوقات (Lifetime)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "إجمالي التحميل والرفع المسجل في السيرفر عبر كل الجلسات",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = NetworkUtils.formatBytes(lifetimeTotalBytes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // 3-Metric Summary Cards: Lifetime Download, Lifetime Upload, Cumulative Uptime
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Lifetime Download
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = StatusActiveGreenBg,
                                border = BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = StatusActiveGreen, modifier = Modifier.size(15.dp))
                                        Text(text = "التحميل الكلي", style = MaterialTheme.typography.labelSmall, color = StatusActiveGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Text(text = NetworkUtils.formatBytes(lifetimeDownloadBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text(text = "Download كل الأوقات", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Lifetime Upload
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(15.dp))
                                        Text(text = "الرفع الكلي", style = MaterialTheme.typography.labelSmall, color = RouterBlue, fontWeight = FontWeight.Bold)
                                    }
                                    Text(text = NetworkUtils.formatBytes(lifetimeUploadBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text(text = "Upload كل الأوقات", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Cumulative Uptime
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
                                        Text(text = "الوقت التراكمي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                    }
                                    Text(text = lifetimeUptimeStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text(text = "$totalSessionsCount جلسات مسجلة", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Side-by-side: Current Session vs Lifetime Cumulative
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "مقارنة استهلاك الجلسة الحالية مقابل الإجمالي التراكمي:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "استهلاك الجلسة الحالية:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${NetworkUtils.formatBytes(session.totalBytes)} (تحميل: ${NetworkUtils.formatBytes(session.bytesOut)} • رفع: ${NetworkUtils.formatBytes(session.bytesIn)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RouterBlue
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "مدة الجلسة الحالية:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = session.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                HorizontalDivider(thickness = 0.5.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "تاريخ أول ظهور بالشبكة:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text(text = firstSeenDate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "آخر ظهور / نشاط:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text(text = lastSeenDate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (session.isOnline) StatusActiveGreen else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // 3. Card Quota, Balance & Validity Status Card (الرصيد المتبقي والصلاحية)
                val quotaLimit = subscriberProfile?.quotaLimitBytes ?: session.packageLimitBytes
                val limitEffective = if (quotaLimit > 0L) quotaLimit else (1024L * 1024L * 1024L)
                val isExhausted = subscriberProfile?.isQuotaExhausted ?: session.isQuotaExhausted || (quotaLimit > 0L && lifetimeTotalBytes >= quotaLimit)
                val remainingBytes = if (quotaLimit > 0L) (quotaLimit - lifetimeTotalBytes).coerceAtLeast(0L) else 0L
                val quotaPercentage = if (quotaLimit > 0L) (lifetimeTotalBytes.toFloat() / quotaLimit.toFloat()).coerceIn(0f, 1f) else 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "رصيد الكرت والحدود المتبقية (Quota & Balance)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تتبع الرصيد المتبقي بدقة والوقت المتبقي من الباقة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExhausted) StatusErrorRedBg else StatusActiveGreenBg,
                            border = BorderStroke(1.dp, if (isExhausted) StatusErrorRed.copy(alpha = 0.5f) else StatusActiveGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (isExhausted) Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (isExhausted) StatusErrorRed else StatusActiveGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isExhausted) "⚠️ تم استنفاد الرصيد بالكامل" else "✅ الكرت نشط ويوجد رصيد متبقي صالح",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExhausted) StatusErrorRed else StatusActiveGreen
                                        )
                                    }

                                    if (quotaLimit > 0L) {
                                        Text(
                                            text = "${(quotaPercentage * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isExhausted) StatusErrorRed else StatusActiveGreen
                                        )
                                    }
                                }

                                if (quotaLimit > 0L) {
                                    LinearProgressIndicator(
                                        progress = { quotaPercentage },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isExhausted) StatusErrorRed else StatusActiveGreen,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (quotaLimit > 0L) "المتبقي: ${NetworkUtils.formatBytes(remainingBytes)}" else "الرصيد: مفتوح (غير محدد بالحجم)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExhausted) StatusErrorRed else StatusActiveGreen
                                    )
                                    Text(
                                        text = if (quotaLimit > 0L) "من إجمالي: ${NetworkUtils.formatBytes(quotaLimit)}" else "مخصص باقة مفتوحة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (subscriberProfile?.timeRemainingFormatted?.isNotBlank() == true) {
                                    Text(
                                        text = "الوقت المتبقي من الصلاحية: ${subscriberProfile.timeRemainingFormatted}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 1: All Activated Packages (كل الباقات والتفعيلات الحالية والسابقة)
            // =========================================================================
            if (selectedSectionTab == 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RouterBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = RouterBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "كل الباقات والتفعيلات للمشترك (Packages History)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الباقة الحالية المفعلة وتاريخ كافة الباقات والتجديدات للمشترك",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val packagesList = subscriberProfile?.activatedPackages.orEmpty()
                        if (packagesList.isEmpty()) {
                            // Render Current Package at minimum
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "الباقة الحالية: ${session.profile}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        StatusPill(text = "نشطة حالياً", type = StatusType.SUCCESS)
                                    }
                                    Text(text = "سعر البيع: ${session.price}", style = MaterialTheme.typography.bodySmall)
                                    Text(text = "استهلاك البيانات على هذه الباقة: ${NetworkUtils.formatBytes(lifetimeTotalBytes)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            packagesList.forEach { pkg ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (pkg.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (pkg.isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = if (pkg.isCurrent) Icons.Default.CheckCircle else Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = if (pkg.isCurrent) StatusActiveGreen else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = pkg.profileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (pkg.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            StatusPill(
                                                text = if (pkg.isCurrent) "الباقة الحالية النشطة" else "تفعيل سابق",
                                                type = if (pkg.isCurrent) StatusType.SUCCESS else StatusType.INFO
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "السعر: ${pkg.price}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(text = "حد السرعة: ${pkg.rateLimit}", style = MaterialTheme.typography.bodySmall, color = RouterBlue, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "حجم الباقة: ${pkg.quotaLimit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(text = "مدة الصلاحية: ${pkg.validity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        HorizontalDivider(thickness = 0.5.dp)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "إجمالي الاستهلاك على الباقة: ${NetworkUtils.formatBytes(pkg.totalBytesUsedOnPackage)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(text = "${pkg.sessionsCountOnPackage} جلسات مسجلة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 2: Complete Activity Log, Devices & Sessions (النشاط والجلسات)
            // =========================================================================
            if (selectedSectionTab == 2) {
                // Devices and Addresses
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RouterBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Devices, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(20.dp))
                            }
                            Text(text = "الأجهزة وعناوين الاتصال المستخدمة للمشترك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        val usedDevices = subscriberProfile?.usedDevices.orEmpty()
                        if (usedDevices.isNotEmpty()) {
                            Text(text = "الأجهزة المسجلة له عبر كل الأوقات (${usedDevices.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            usedDevices.forEach { dev ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(18.dp))
                                        Text(text = "${dev.brand} - ${dev.modelName} (${dev.category.title})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Current IP & MAC
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { copyToClipboard("عنوان IP", session.ipAddress) },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "عنوان IP المسند:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = session.ipAddress, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { copyToClipboard("عنوان MAC", session.macAddress) },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "عنوان MAC الحالي:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = session.macAddress, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Chronological Sessions History List
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(RouterBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(20.dp))
                                }
                                Text(text = "أرشيف وسجل كافة جلسات المشترك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            if (loginHistory.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(8.dp), color = RouterBlue.copy(alpha = 0.12f)) {
                                    Text(
                                        text = "${loginHistory.size} جلسات محفوظة",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RouterBlue
                                    )
                                }
                            }
                        }

                        if (loginHistory.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "يتم حفظ تاريخ كل جلسة وتفاصيل الأجهزة وأوقات الدخول والخروج تلقائياً في قاعدة البيانات.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            loginHistory.forEach { record ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp), tint = RouterBlue)
                                                Text(
                                                    text = "الدخول: ${NetworkUtils.formatArabicFullDateTimeFromString(record.loginTime, record.timestamp)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            StatusPill(
                                                text = if (record.logoutTime == "متصل الآن") "متصل الآن" else "انتهت (${record.duration})",
                                                type = if (record.logoutTime == "متصل الآن") StatusType.SUCCESS else StatusType.INFO
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "الجهاز: ${record.deviceBrand} ${record.deviceModel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "الاستهلاك: ${NetworkUtils.formatBytes(record.totalBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (record.logoutTime == "متصل الآن") "الخروج: متصل الآن (جلسة نشطة)" else "الخروج: ${NetworkUtils.formatArabicFullDateTimeFromString(record.logoutTime, record.timestamp)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = "السبب: ${record.terminateReason}",
                                                style = MaterialTheme.typography.labelSmall,
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

            // Actions Footer (طرد المستخدم ومشاركة التقرير الشامل)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (session.isOnline && onKickUser != null) {
                    Button(
                        onClick = onKickUser,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_kick_user_from_detail_screen")
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("طرد المستخدم الآن", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val reportText = buildString {
                            appendLine("=== التقرير الشامل للمشترك والاستهلاك لكل الأوقات ===")
                            appendLine("رقم الكرت / المستخدم: ${session.username}")
                            appendLine("الحالة الحالية: ${if (session.isOnline) "متصل الآن (نشط)" else "غير متصل"}")
                            appendLine("نقطة البيع: ${subscriberProfile?.assignedAgentName?.ifBlank { "الراوتر المركزي" } ?: "الراوتر المركزي"}")
                            appendLine("--- الاستهلاك التراكمي الشامل لكل الأوقات ---")
                            appendLine("إجمالي الاستهلاك الكلي: ${NetworkUtils.formatBytes(lifetimeTotalBytes)}")
                            appendLine("إجمالي التحميل الكلي (Download): ${NetworkUtils.formatBytes(lifetimeDownloadBytes)}")
                            appendLine("إجمالي الرفع الكلي (Upload): ${NetworkUtils.formatBytes(lifetimeUploadBytes)}")
                            appendLine("الوقت التراكمي الكلي: $lifetimeUptimeStr")
                            appendLine("إجمالي الجلسات المسجلة: $totalSessionsCount")
                            appendLine("تاريخ أول ظهور: $firstSeenDate")
                            appendLine("تاريخ آخر نشاط: $lastSeenDate")
                            appendLine("--- الجلسة الحالية ---")
                            appendLine("استهلاك الجلسة الحالية: ${NetworkUtils.formatBytes(session.totalBytes)}")
                            appendLine("مدة الجلسة الحالية: ${session.duration}")
                            appendLine("عنوان IP: ${session.ipAddress}")
                            appendLine("عنوان MAC: ${session.macAddress}")
                            appendLine("الجهاز: ${session.deviceInfo.brand} - ${session.deviceInfo.modelName}")
                            appendLine("برج الاتصال: ${session.nodeName.ifBlank { "الراوتر الرئيسي" }}")
                            appendLine("الباقة الحالية: ${session.profile} (${session.price})")
                            val packages = subscriberProfile?.activatedPackages.orEmpty()
                            if (packages.isNotEmpty()) {
                                appendLine("--- الباقات والتفعيلات المسجلة ---")
                                packages.forEach { p ->
                                    appendLine("- ${p.profileName}: سعرها ${p.price} • سرعتها ${p.rateLimit} • استهلاكها ${NetworkUtils.formatBytes(p.totalBytesUsedOnPackage)}")
                                }
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة الملف الشامل للمشترك"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RouterBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مشاركة التقرير الشامل", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
