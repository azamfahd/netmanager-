package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.ui.theme.RouterBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardDialog(
    card: HotspotCard,
    profiles: List<HotspotProfile>,
    onDismiss: () -> Unit,
    onSave: (HotspotCard) -> Unit
) {
    var password by remember { mutableStateOf(card.password) }
    var selectedProfile by remember { mutableStateOf(card.profile) }
    var price by remember { mutableStateOf(card.price.ifBlank { "" }) }
    var timeLimit by remember { mutableStateOf(card.limitUptime) }
    var comment by remember { mutableStateOf(card.comment) }
    var expandedProfileDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = RouterBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "تعديل كرت الشبكة (${card.name})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تحديث باقة وسعر وصلاحيات الكرت في الراوتر",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card code (Read-only identifier)
                OutlinedTextField(
                    value = card.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("رقم / كود الكرت") },
                    leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Profile Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedProfileDropdown,
                    onExpandedChange = { expandedProfileDropdown = !expandedProfileDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedProfile,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الباقة والسرعة") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfileDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProfileDropdown,
                        onDismissRequest = { expandedProfileDropdown = false }
                    ) {
                        profiles.forEach { prof ->
                            DropdownMenuItem(
                                text = { Text("${prof.name} (${prof.rateLimit} - ${prof.price})") },
                                onClick = {
                                    selectedProfile = prof.name
                                    if (prof.price.isNotBlank() && prof.price != "0 ر.ي") {
                                        price = prof.price
                                    }
                                    if (prof.validity.isNotBlank() && prof.validity != "غير محدد") {
                                        timeLimit = prof.validity
                                    } else if (prof.sessionTimeout.isNotBlank() && prof.sessionTimeout != "غير محدد") {
                                        timeLimit = prof.sessionTimeout
                                    }
                                    expandedProfileDropdown = false
                                }
                            )
                        }
                    }
                }

                // Price
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("سعر البيع (مثال: 500 ر.ي)") },
                    leadingIcon = { Icon(Icons.Default.PriceChange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Time Limit
                OutlinedTextField(
                    value = timeLimit,
                    onValueChange = { timeLimit = it },
                    label = { Text("حد الوقت (مثال: 1d أو 2h)") },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Comment
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("ملاحظة أو اسم العميل") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        card.copy(
                            password = password,
                            profile = selectedProfile.trim(),
                            price = price,
                            limitUptime = timeLimit,
                            comment = comment
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RouterBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_edit_card_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إلغاء")
            }
        }
    )
}
