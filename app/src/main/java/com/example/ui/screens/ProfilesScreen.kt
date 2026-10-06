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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HotspotProfile
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ProfilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "باقات السرعات والأسعار (Hotspot Profiles)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "تحديد سرعة الرفع والتحميل، مدة الجلسة، وعدد الأجهزة لكل كرت",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCardItem(profile = profile)
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_add_profile"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = "إضافة باقة",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newProfile ->
                viewModel.createProfile(newProfile)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProfileCardItem(profile: HotspotProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NetworkCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NetworkCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = profile.price,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Rate Limit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = RouterBlue
                    )
                    Column {
                        Text(
                            text = "تحديد السرعة (Rate)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = profile.rateLimit,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Shared Users
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NetworkCyan
                    )
                    Column {
                        Text(
                            text = "الأجهزة المسموحة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${profile.sharedUsers} جهاز",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Session Timeout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column {
                        Text(
                            text = "مهلة الجلسة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = profile.sessionTimeout,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (HotspotProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rateLimit by remember { mutableStateOf("3M/3M") }
    var sharedUsers by remember { mutableIntStateOf(1) }
    var sessionTimeout by remember { mutableStateOf("1d") }
    var price by remember { mutableStateOf("500 ر.ي") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة باقة هوتسبوت جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الباقة (مثال: Daily 2GB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = rateLimit,
                    onValueChange = { rateLimit = it },
                    label = { Text("تحديد السرعة Rx/Tx (مثال: 2M/4M)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_rate_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عدد الأجهزة المسموح بها:")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 3, 5).forEach { num ->
                            OutlinedButton(
                                onClick = { sharedUsers = num },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$num",
                                    fontWeight = if (sharedUsers == num) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = sessionTimeout,
                    onValueChange = { sessionTimeout = it },
                    label = { Text("مهلة الجلسة (مثال: 1h, 1d, 7d)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("السعر المقترح (مثال: 500 ر.ي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_price_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            HotspotProfile(
                                id = "prof_${System.currentTimeMillis()}",
                                name = name.trim(),
                                rateLimit = rateLimit.trim(),
                                sharedUsers = sharedUsers,
                                sessionTimeout = sessionTimeout.trim(),
                                price = price.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("submit_profile_btn")
            ) {
                Text("حفظ الباقة")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
