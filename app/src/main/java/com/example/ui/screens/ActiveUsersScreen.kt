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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HotspotActiveUser
import com.example.data.network.NetworkUtils
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ActiveUsersScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeUsers by viewModel.filteredActiveUsers.collectAsStateWithLifecycle()
    val totalActiveUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val searchQuery by viewModel.activeUserSearch.collectAsStateWithLifecycle()
    val isAutoRefresh by viewModel.autoRefresh.collectAsStateWithLifecycle()

    var userToKick by remember { mutableStateOf<HotspotActiveUser?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search & Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateActiveUserSearch(it) },
                label = { Text("بحث باسم المستخدم أو IP أو MAC") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateActiveUserSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_active_users_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            IconButton(
                onClick = { viewModel.refreshData() },
                modifier = Modifier.testTag("refresh_active_users_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تحديث يدوي",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Auto Refresh Toggle & Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = "${activeUsers.size} مستخدم نشط",
                    type = StatusType.SUCCESS
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "(من إجمالي ${totalActiveUsers.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "تحديث تلقائي",
                    style = MaterialTheme.typography.labelMedium
                )
                Switch(
                    checked = isAutoRefresh,
                    onCheckedChange = { viewModel.toggleAutoRefresh(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Users List
        if (activeUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "لا يوجد مستخدمون متصلون حالياً بالهوتسبوت" else "لا توجد نتائج تطابق البحث",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeUsers, key = { it.id }) { user ->
                    ActiveUserItem(
                        user = user,
                        onKickClick = { userToKick = user }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Kick User Confirmation Dialog
    userToKick?.let { targetUser ->
        ConfirmDialog(
            title = "قطع اتصال المستخدم",
            message = "هل أنت متأكد من رغبتك في طرد المستخدم (${targetUser.user}) وعنوان IP (${targetUser.address}) من الشبكة؟",
            confirmText = "طرد وقطع الاتصال",
            isDestructive = true,
            onConfirm = {
                viewModel.kickUser(targetUser)
                userToKick = null
            },
            onDismiss = { userToKick = null }
        )
    }
}

@Composable
fun ActiveUserItem(
    user: HotspotActiveUser,
    onKickClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_user_card_${user.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Username + Status + Kick Button
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(StatusActiveGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = StatusActiveGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = user.user,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (user.comment.isNotBlank()) {
                            Text(
                                text = user.comment,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onKickClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("kick_user_btn_${user.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طرد", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details: IP, MAC, Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "عنوان IP:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = user.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column {
                    Text(
                        text = "عنوان MAC:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = user.macAddress,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مدة الاتصال:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = StatusActiveGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = user.uptime,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Data consumption (Bytes in & Bytes out)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = StatusActiveGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "تحميل: ${NetworkUtils.formatBytes(user.bytesOut)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = NetworkCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "رفع: ${NetworkUtils.formatBytes(user.bytesIn)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (user.sessionTimeLeft.isNotBlank()) {
                        Text(
                            text = "متبقي: ${user.sessionTimeLeft}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
