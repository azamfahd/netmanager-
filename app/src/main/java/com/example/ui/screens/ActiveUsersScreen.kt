package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DeviceCategory
import com.example.data.model.HotspotActiveUser
import com.example.data.model.UserActivityEvent
import com.example.data.model.UserSessionRecord
import com.example.data.network.NetworkUtils
import com.example.data.util.DeviceDetector
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.components.UserDetailDialog
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.RouterBlueDark
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusErrorRedBg
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ActiveUsersScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeUsers by viewModel.filteredActiveUsers.collectAsStateWithLifecycle()
    val totalActiveUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val userSessions by viewModel.filteredUserSessions.collectAsStateWithLifecycle()
    val allSessions by viewModel.userSessions.collectAsStateWithLifecycle()
    val activityEvents by viewModel.filteredActivityEvents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.sessionSearchQuery.collectAsStateWithLifecycle()
    val currentFilter by viewModel.sessionFilter.collectAsStateWithLifecycle()
    val isAutoRefresh by viewModel.autoRefresh.collectAsStateWithLifecycle()

    var selectedSessionForDetail by remember { mutableStateOf<UserSessionRecord?>(null) }
    var userToKick by remember { mutableStateOf<UserSessionRecord?>(null) }

    val onlineCount = allSessions.count { it.isOnline }
    val offlineCount = allSessions.count { !it.isOnline }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Perfectly Aligned Search Bar & Refresh Button Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSessionSearch(it) },
                label = { Text("بحث برقم الكرت أو IP أو نوع الجهاز") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSessionSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("search_users_sessions_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Button(
                onClick = { viewModel.refreshData() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RouterBlue
                ),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("refresh_sessions_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تحديث",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تحديث",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Filter Tabs: All, Online, Offline/History, Activity Log (Uniform compact size & scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = currentFilter == "ALL",
                onClick = { viewModel.updateSessionFilter("ALL") },
                label = { Text("الكل (${allSessions.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(15.dp))
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            )

            FilterChip(
                selected = currentFilter == "ONLINE",
                onClick = { viewModel.updateSessionFilter("ONLINE") },
                label = { Text("متصل الآن ($onlineCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp), tint = StatusActiveGreen)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StatusActiveGreen.copy(alpha = 0.18f),
                    selectedLabelColor = StatusActiveGreen
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            )

            FilterChip(
                selected = currentFilter == "OFFLINE",
                onClick = { viewModel.updateSessionFilter("OFFLINE") },
                label = { Text("جلسات سابقة ($offlineCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(15.dp))
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            )

            FilterChip(
                selected = currentFilter == "LOGS",
                onClick = { viewModel.updateSessionFilter("LOGS") },
                label = { Text("سجل النشاط (${activityEvents.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Main Content List
        if (currentFilter == "LOGS") {
            // Activity Events Log List
            if (activityEvents.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.EventNote,
                    text = "لا توجد أحداث نشاط مسجلة حالياً"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activityEvents, key = { it.id }) { event ->
                        ActivityEventCard(
                            event = event,
                            onClick = {
                                val session = UserSessionRecord(
                                    id = event.id,
                                    username = event.username,
                                    ipAddress = event.ipAddress,
                                    macAddress = event.macAddress,
                                    isOnline = event.eventType == "تسجيل دخول",
                                    loginTime = event.timestamp,
                                    logoutTime = if (event.eventType == "تسجيل دخول") "متصل الآن" else event.timestamp,
                                    duration = "غير محدد",
                                    terminateReason = event.details,
                                    deviceInfo = event.deviceInfo
                                )
                                selectedSessionForDetail = session
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        } else {
            // User Sessions List (Active & Disconnected)
            if (userSessions.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Person,
                    text = if (searchQuery.isBlank()) "لا توجد جلسات مستخدمين مطابقة" else "لا توجد نتائج تطابق البحث"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(userSessions, key = { it.id }) { session ->
                        UserSessionCardItem(
                            session = session,
                            onClick = { selectedSessionForDetail = session },
                            onKickClick = { userToKick = session }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // User Detail Dialog
    selectedSessionForDetail?.let { targetSession ->
        UserDetailDialog(
            session = targetSession,
            onDismiss = { selectedSessionForDetail = null },
            onKickUser = if (targetSession.isOnline) {
                { userToKick = targetSession }
            } else null
        )
    }

    // Kick User Confirmation Dialog
    userToKick?.let { targetUser ->
        ConfirmDialog(
            title = "قطع اتصال المستخدم",
            message = "هل أنت متأكد من رغبتك في طرد المستخدم (${targetUser.username}) وعنوان IP (${targetUser.ipAddress}) من الشبكة؟",
            confirmText = "طرد وقطع الاتصال",
            isDestructive = true,
            onConfirm = {
                viewModel.kickUser(
                    HotspotActiveUser(
                        id = targetUser.id,
                        user = targetUser.username,
                        address = targetUser.ipAddress,
                        macAddress = targetUser.macAddress,
                        uptime = targetUser.duration,
                        bytesIn = targetUser.bytesIn,
                        bytesOut = targetUser.bytesOut
                    )
                )
                userToKick = null
            },
            onDismiss = { userToKick = null }
        )
    }
}

@Composable
fun UserSessionCardItem(
    session: UserSessionRecord,
    onClick: () -> Unit,
    onKickClick: () -> Unit
) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("user_session_card_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Device Icon + Username + Model + Status + Kick/Detail Icon
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(brandBadgeColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = brandBadgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = session.username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            StatusPill(
                                text = if (session.isOnline) "متصل الآن" else "غير متصل",
                                type = if (session.isOnline) StatusType.SUCCESS else StatusType.INFO
                            )
                        }

                        // Device Type & Brand name
                        Text(
                            text = "${session.deviceInfo.modelName} • ${session.deviceInfo.osName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (session.isOnline) {
                    OutlinedButton(
                        onClick = onKickClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("kick_session_btn_${session.id}")
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طرد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = RouterBlue)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "تفاصيل",
                                style = MaterialTheme.typography.labelSmall,
                                color = RouterBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timings & Consumed Data Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Login / Logout Timing
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "دخول: ${session.loginTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!session.isOnline) {
                        Text(text = "خروج: ${session.logoutTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(text = "المدة: ${session.duration}", style = MaterialTheme.typography.labelSmall, color = StatusActiveGreen, fontWeight = FontWeight.Bold)
                    }
                }

                // Consumption Data
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(12.dp), tint = StatusActiveGreen)
                        Text(text = NetworkUtils.formatBytes(session.bytesOut), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(12.dp), tint = RouterBlue)
                        Text(text = NetworkUtils.formatBytes(session.bytesIn), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "الإجمالي: ${NetworkUtils.formatBytes(session.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (session.terminateReason.isNotBlank() && !session.isOnline) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = session.terminateReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityEventCard(
    event: UserActivityEvent,
    onClick: () -> Unit
) {
    val isLogin = event.eventType == "تسجيل دخول"
    val eventColor = if (isLogin) StatusActiveGreen else Color(0xFFF57C00)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(eventColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLogin) Icons.Default.Person else Icons.Default.PersonOff,
                        contentDescription = null,
                        tint = eventColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = eventColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = event.eventType,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = eventColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = event.details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = event.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
