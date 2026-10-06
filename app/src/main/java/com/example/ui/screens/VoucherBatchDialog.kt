package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.CodeCharType
import com.example.data.model.HotspotProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherBatchDialog(
    profiles: List<HotspotProfile>,
    onDismiss: () -> Unit,
    onGenerate: (BatchGenerationConfig) -> Unit
) {
    var count by remember { mutableIntStateOf(20) }
    var selectedProfile by remember { mutableStateOf(profiles.firstOrNull()?.name ?: "default") }
    var prefix by remember { mutableStateOf("") }
    var codeLength by remember { mutableIntStateOf(6) }
    var charType by remember { mutableStateOf(CodeCharType.NUMBERS_ONLY) }
    var isSingleCode by remember { mutableStateOf(true) }
    var timeLimit by remember { mutableStateOf("1d") }
    var dataLimitMB by remember { mutableLongStateOf(2048L) }
    var price by remember { mutableStateOf("500 ر.ي") }
    var batchComment by remember { mutableStateOf("دفعة كروت جديدة") }

    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("توليد كروت هوتسبوت بالجملة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Count Selector
                Text("عدد الكروت المطلوبة:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 20, 50, 100).forEach { num ->
                        FilterChip(
                            selected = count == num,
                            onClick = { count = num },
                            label = { Text("$num كرت") }
                        )
                    }
                }

                // Profile Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedProfile,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("باقة السرعة (Profile)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text("${profile.name} (${profile.rateLimit})") },
                                onClick = {
                                    selectedProfile = profile.name
                                    price = profile.price
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                // Character Type (Numbers vs Letters+Numbers)
                Text("نوع كود الكرت:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = charType == CodeCharType.NUMBERS_ONLY,
                        onClick = { charType = CodeCharType.NUMBERS_ONLY },
                        label = { Text("أرقام فقط") }
                    )
                    FilterChip(
                        selected = charType == CodeCharType.LETTERS_AND_NUMBERS,
                        onClick = { charType = CodeCharType.LETTERS_AND_NUMBERS },
                        label = { Text("أرقام وحروف") }
                    )
                }

                // Code Length
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("طول الكود:")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(4, 5, 6, 8).forEach { len ->
                            FilterChip(
                                selected = codeLength == len,
                                onClick = { codeLength = len },
                                label = { Text("$len خانات") }
                            )
                        }
                    }
                }

                // Single PIN mode Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isSingleCode = !isSingleCode }
                ) {
                    Checkbox(
                        checked = isSingleCode,
                        onCheckedChange = { isSingleCode = it }
                    )
                    Text(
                        text = "رقم الكرت هو كلمة المرور (كود تسجيل دخول موحد)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Prefix & Limits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("بادئة (Prefix)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = timeLimit,
                        onValueChange = { timeLimit = it },
                        label = { Text("مهلة الوقت (1d/7d)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = if (dataLimitMB > 0) dataLimitMB.toString() else "",
                        onValueChange = { dataLimitMB = it.toLongOrNull() ?: 0L },
                        label = { Text("حجم البيانات (MB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("سعر الكرت") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = batchComment,
                    onValueChange = { batchComment = it },
                    label = { Text("ملاحظة أو اسم الدفعة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGenerate(
                        BatchGenerationConfig(
                            count = count,
                            profile = selectedProfile,
                            prefix = prefix.trim(),
                            codeLength = codeLength,
                            characterSet = charType,
                            isSingleCode = isSingleCode,
                            timeLimit = timeLimit.trim(),
                            dataLimitMB = dataLimitMB,
                            price = price.trim(),
                            batchComment = batchComment.trim()
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("submit_generate_batch_btn")
            ) {
                Text("توليد $count كرت الآن")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
