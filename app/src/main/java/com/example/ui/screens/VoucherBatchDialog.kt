package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PosAgentEntity
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.CodeCharType
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.RouterBlueDark
import com.example.ui.theme.RouterBlueLight
import com.example.ui.theme.StatusActiveGreen
import kotlin.random.Random

data class VoucherPreset(
    val priceValue: Int,
    val priceLabel: String,
    val title: String,
    val timeLimit: String,
    val timeDisplay: String,
    val dataLimitMB: Long,
    val dataDisplay: String,
    val rateLimit: String,
    val color: Color
)

val PRESET_CATEGORIES = listOf(
    VoucherPreset(
        priceValue = 200,
        priceLabel = "200 ر.ي",
        title = "فئة 200",
        timeLimit = "2h",
        timeDisplay = "2 ساعة",
        dataLimitMB = 500L,
        dataDisplay = "500 MB",
        rateLimit = "2M/2M",
        color = Color(0xFF1976D2)
    ),
    VoucherPreset(
        priceValue = 300,
        priceLabel = "300 ر.ي",
        title = "فئة 300",
        timeLimit = "6h",
        timeDisplay = "6 ساعات",
        dataLimitMB = 1024L,
        dataDisplay = "1 GB",
        rateLimit = "3M/3M",
        color = Color(0xFF00897B)
    ),
    VoucherPreset(
        priceValue = 500,
        priceLabel = "500 ر.ي",
        title = "فئة 500 (الأكثر طلباً)",
        timeLimit = "1d",
        timeDisplay = "1 يوم (24 ساعة)",
        dataLimitMB = 2560L,
        dataDisplay = "2.5 GB",
        rateLimit = "4M/4M",
        color = Color(0xFFF57C00)
    ),
    VoucherPreset(
        priceValue = 1000,
        priceLabel = "1000 ر.ي",
        title = "فئة 1000 (VIP)",
        timeLimit = "3d",
        timeDisplay = "3 أيام",
        dataLimitMB = 6144L,
        dataDisplay = "6 GB",
        rateLimit = "6M/6M",
        color = Color(0xFF7B1FA2)
    ),
    VoucherPreset(
        priceValue = 2500,
        priceLabel = "2500 ر.ي",
        title = "فئة 2500 (باقة مميزة)",
        timeLimit = "10d",
        timeDisplay = "10 أيام",
        dataLimitMB = 18432L,
        dataDisplay = "18 GB",
        rateLimit = "8M/8M",
        color = Color(0xFFC2185B)
    )
)

enum class VoucherDialogTab {
    BATCH_GENERATE,
    SINGLE_MANUAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherBatchDialog(
    profiles: List<HotspotProfile>,
    posAgents: List<PosAgentEntity> = emptyList(),
    preselectedAgentId: Long = 0L,
    onDismiss: () -> Unit,
    onGenerate: (BatchGenerationConfig) -> Unit,
    onAddSingleCard: ((HotspotCard, Long, String, Double) -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(VoucherDialogTab.BATCH_GENERATE) }

    // Selected preset category (default 500)
    var selectedPresetIndex by remember { mutableIntStateOf(2) } // default 500

    // POS Agent selection
    var selectedAgentId by remember { mutableLongStateOf(preselectedAgentId) }
    var expandedAgentDropdown by remember { mutableStateOf(false) }

    // Common configuration state
    var selectedProfile by remember {
        mutableStateOf(profiles.firstOrNull()?.name ?: "default")
    }
    var price by remember { mutableStateOf(PRESET_CATEGORIES[2].priceLabel) }
    var timeLimit by remember { mutableStateOf(PRESET_CATEGORIES[2].timeLimit) }
    var dataLimitMBInput by remember { mutableStateOf(PRESET_CATEGORIES[2].dataLimitMB.toString()) }
    var isSingleCode by remember { mutableStateOf(true) }

    // Batch specific state
    var batchCountInput by remember { mutableStateOf("20") }
    var prefix by remember { mutableStateOf("") }
    var codeLengthInput by remember { mutableStateOf("6") }
    var charType by remember { mutableStateOf(CodeCharType.NUMBERS_ONLY) }
    var batchComment by remember { mutableStateOf("دفعة فئة 500 ر.ي") }

    // Single card specific state
    var singleCode by remember { mutableStateOf("849201") }
    var singlePassword by remember { mutableStateOf("849201") }
    var singleComment by remember { mutableStateOf("كرت فردي") }

    var expandedDropdown by remember { mutableStateOf(false) }

    // Helper to apply preset category
    fun applyPreset(preset: VoucherPreset) {
        price = preset.priceLabel
        timeLimit = preset.timeLimit
        dataLimitMBInput = preset.dataLimitMB.toString()
        batchComment = "دفعة ${preset.title}"
        singleComment = "كرت ${preset.title}"
        // Find matching profile if available
        val matchedProfile = profiles.firstOrNull {
            it.name.contains(preset.priceValue.toString()) ||
                    it.name.contains(preset.timeLimit, ignoreCase = true) ||
                    it.price.contains(preset.priceValue.toString())
        }
        if (matchedProfile != null) {
            selectedProfile = matchedProfile.name
        }
    }

    // Function to generate random single code
    fun generateRandomSingleCode() {
        val pool = if (charType == CodeCharType.NUMBERS_ONLY) "1234567890" else "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val length = (codeLengthInput.toIntOrNull() ?: 6).coerceIn(3, 16)
        val code = (1..length).map { pool.random() }.joinToString("")
        val finalCode = if (prefix.isNotBlank()) "$prefix$code" else code
        singleCode = finalCode
        if (isSingleCode) {
            singlePassword = finalCode
        }
    }

    val isProfileValid = profiles.isEmpty() || profiles.any { it.name.equals(selectedProfile, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .imePadding(),
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
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
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = "إدارة وتوليد كروت الهوتسبوت",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إضافة وتفعيل الكروت في الراوتر مع الباقات المعتمدة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Tabs: Batch vs Single
                TabRow(
                    selectedTabIndex = if (selectedTab == VoucherDialogTab.BATCH_GENERATE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == VoucherDialogTab.BATCH_GENERATE,
                        onClick = { selectedTab = VoucherDialogTab.BATCH_GENERATE },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("توليد مجموعة كروت", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == VoucherDialogTab.SINGLE_MANUAL,
                        onClick = {
                            selectedTab = VoucherDialogTab.SINGLE_MANUAL
                            if (singleCode.isBlank() || singleCode == "849201") {
                                generateRandomSingleCode()
                            }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("إضافة كرت فردي يدوي", fontWeight = FontWeight.Bold)
                            }
                        }
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
                // 1. Live Interactive Card Mockup Preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0D47A1)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0D47A1),
                                        Color(0xFF1565C0),
                                        Color(0xFF00838F)
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Top Row: Network & WiFi
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
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "كرت شبكة ميكروتك",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    color = Color(0xFFFFD54F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = price,
                                        color = Color(0xFF3E2723),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Middle: Card Code / PIN
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "كود الدخول (PIN / Username):",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = if (selectedTab == VoucherDialogTab.SINGLE_MANUAL) singleCode else "${if (prefix.isNotBlank()) "$prefix" else ""}XXXXXX",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        letterSpacing = 2.sp
                                    )
                                    if (!isSingleCode && selectedTab == VoucherDialogTab.SINGLE_MANUAL) {
                                        Text(
                                            text = "كلمة المرور: $singlePassword",
                                            color = Color.White.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            // Bottom Badges: Limits & Profile
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "⏱ $timeLimit",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    val previewDataMB = dataLimitMBInput.toLongOrNull() ?: 0L
                                    if (previewDataMB > 0) {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "📦 ${if (previewDataMB >= 1024) "${previewDataMB / 1024} GB" else "$previewDataMB MB"}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "باقة: $selectedProfile",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 2. Predefined Categories (فئات الكروت الشائعة: 200, 300, 500, 1000, 2500)
                Column {
                    Text(
                        text = "اختر فئة الكرت السريعة (الأسعار والصلاحيات):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PRESET_CATEGORIES.take(3).forEachIndexed { index, preset ->
                            FilterChip(
                                selected = selectedPresetIndex == index,
                                onClick = {
                                    selectedPresetIndex = index
                                    applyPreset(preset)
                                },
                                label = {
                                    Text(
                                        text = preset.priceLabel,
                                        fontWeight = if (selectedPresetIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = preset.color.copy(alpha = 0.15f),
                                    selectedLabelColor = preset.color
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PRESET_CATEGORIES.drop(3).forEachIndexed { idx, preset ->
                            val actualIndex = idx + 3
                            FilterChip(
                                selected = selectedPresetIndex == actualIndex,
                                onClick = {
                                    selectedPresetIndex = actualIndex
                                    applyPreset(preset)
                                },
                                label = {
                                    Text(
                                        text = preset.priceLabel,
                                        fontWeight = if (selectedPresetIndex == actualIndex) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = preset.color.copy(alpha = 0.15f),
                                    selectedLabelColor = preset.color
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Router Profile Dropdown
                Column {
                    Text(
                        text = "باقة السرعة في الراوتر (Hotspot Profile):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = !expandedDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedProfile,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("باقة الراوتر") },
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
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(profile.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${profile.rateLimit} • ${profile.price}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedProfile = profile.name
                                        if (profile.price.isNotBlank() && profile.price != "0 ر.ي") {
                                            price = profile.price
                                        }
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. POS Agent Assignment (نقطة البيع والموزع)
                if (posAgents.isNotEmpty()) {
                    Column {
                        Text(
                            text = "تخصيص لنقطة بيع / موزع (اختياري):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val currentAgent = posAgents.find { it.id == selectedAgentId }
                        ExposedDropdownMenuBox(
                            expanded = expandedAgentDropdown,
                            onExpandedChange = { expandedAgentDropdown = !expandedAgentDropdown }
                        ) {
                            OutlinedTextField(
                                value = currentAgent?.let { "${it.name} (عمولة ${it.discountPercent.toInt()}%)" } ?: "بدون تخصيص (كروت للمالك)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("نقطة البيع المستلمة للدفعة") },
                                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAgentDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAgentDropdown,
                                onDismissRequest = { expandedAgentDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("بدون تخصيص (كروت المالك المباشرة)") },
                                    onClick = {
                                        selectedAgentId = 0L
                                        expandedAgentDropdown = false
                                    }
                                )
                                posAgents.forEach { agent ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(agent.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "عمولة ${agent.discountPercent.toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedAgentId = agent.id
                                            expandedAgentDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Tab Specific Content
                if (selectedTab == VoucherDialogTab.BATCH_GENERATE) {
                    // ---- BATCH GENERATE MODE ----
                    // Count Selector
                    Column {
                        Text("عدد الكروت المراد توليدها في الدفعة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(10, 25, 50, 100).forEach { num ->
                                val isSelected = batchCountInput == num.toString()
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { batchCountInput = num.toString() },
                                    label = { Text("$num كرت", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Character Type
                    Column {
                        Text("نوع الرموز في كود الكرت:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = charType == CodeCharType.NUMBERS_ONLY,
                                onClick = { charType = CodeCharType.NUMBERS_ONLY },
                                label = { Text("أرقام فقط (PIN)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = charType == CodeCharType.LETTERS_AND_NUMBERS,
                                onClick = { charType = CodeCharType.LETTERS_AND_NUMBERS },
                                label = { Text("أرقام وحروف", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Count, Code Length & Prefix
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = batchCountInput,
                            onValueChange = { input ->
                                batchCountInput = input.filter { ch -> ch.isDigit() }.take(5)
                            },
                            label = { Text("عدد الكروت") },
                            placeholder = { Text("20") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = codeLengthInput,
                            onValueChange = { input ->
                                codeLengthInput = input.filter { ch -> ch.isDigit() }.take(2)
                            },
                            label = { Text("طول الكود") },
                            placeholder = { Text("6") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it.uppercase() },
                            label = { Text("بادئة الكود") },
                            placeholder = { Text("VIP-") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = batchComment,
                        onValueChange = { batchComment = it },
                        label = { Text("اسم الدفعة / ملاحظات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    // ---- SINGLE CARD MANUAL MODE ----
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "كود الكرت (يمكنك كتابة كود مخصص يدوياً أو التوليد تلقائياً):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = singleCode,
                                onValueChange = {
                                    singleCode = it
                                    if (isSingleCode) singlePassword = it
                                },
                                label = { Text("كود الكرت / اسم المستخدم") },
                                placeholder = { Text("أدخل كود مخصص...") },
                                trailingIcon = {
                                    if (singleCode.isNotBlank()) {
                                        IconButton(onClick = {
                                            singleCode = ""
                                            if (isSingleCode) singlePassword = ""
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح الكود", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedButton(
                                onClick = { generateRandomSingleCode() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "توليد كود عشوائي")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("توليد", fontSize = 12.sp)
                            }
                        }

                        if (!isSingleCode) {
                            OutlinedTextField(
                                value = singlePassword,
                                onValueChange = { singlePassword = it },
                                label = { Text("كلمة مرور الكرت") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        OutlinedTextField(
                            value = singleComment,
                            onValueChange = { singleComment = it },
                            label = { Text("اسم العميل / ملاحظة الكرت (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Single PIN Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isSingleCode = !isSingleCode
                        if (isSingleCode) singlePassword = singleCode
                    }
                ) {
                    Checkbox(
                        checked = isSingleCode,
                        onCheckedChange = {
                            isSingleCode = it
                            if (it) singlePassword = singleCode
                        }
                    )
                    Text(
                        text = "كود موحد (اسم المستخدم هو نفسه كلمة المرور)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Time Limit Quick Chips & Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("مدة الصلاحية (الوقت):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("1h" to "1ساعة", "3h" to "3ساعات", "1d" to "يوم", "3d" to "3أيام", "7d" to "أسبوع", "30d" to "شهر").forEach { (vKey, vLabel) ->
                            FilterChip(
                                selected = timeLimit == vKey,
                                onClick = { timeLimit = vKey },
                                label = { Text(vLabel, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Time Limit & Price Customization Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = timeLimit,
                        onValueChange = { timeLimit = it },
                        label = { Text("صلاحية الوقت (Limit)") },
                        placeholder = { Text("1d أو 2h") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("سعر البيع") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Data Quota Quick Chips & Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("حد وحصة البيانات:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0L to "مفتوح", 500L to "500M", 1024L to "1GB", 2560L to "2.5G", 5120L to "5GB", 10240L to "10G").forEach { (mbVal, mbLabel) ->
                            FilterChip(
                                selected = dataLimitMBInput == mbVal.toString(),
                                onClick = { dataLimitMBInput = mbVal.toString() },
                                label = { Text(mbLabel, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dataLimitMBInput,
                    onValueChange = { input ->
                        dataLimitMBInput = input.filter { ch -> ch.isDigit() }.take(8)
                    },
                    label = { Text("حد البيانات بالميجابايت (0 = غير محدود)") },
                    placeholder = { Text("2560") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (!isProfileValid) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(
                                text = "⚠️ الباقة المحددة ($selectedProfile) غير موجودة في راوتر MikroTik! تم إيقاف التوليد لحماية الراوتر. يرجى اختيار باقة متوفرة أو إنشاؤها من قسم الباقات أولاً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val parsedCount = batchCountInput.toIntOrNull()?.coerceIn(1, 5000) ?: 20
            val parsedCodeLength = codeLengthInput.toIntOrNull()?.coerceIn(3, 16) ?: 6
            val parsedDataLimitMB = dataLimitMBInput.toLongOrNull() ?: 0L

            if (selectedTab == VoucherDialogTab.BATCH_GENERATE) {
                Button(
                    onClick = {
                        if (!isProfileValid) return@Button
                        val selectedAgent = posAgents.find { it.id == selectedAgentId }
                        val config = BatchGenerationConfig(
                            count = parsedCount,
                            profile = selectedProfile,
                            prefix = prefix,
                            codeLength = parsedCodeLength,
                            characterSet = charType,
                            isSingleCode = isSingleCode,
                            timeLimit = timeLimit,
                            dataLimitMB = parsedDataLimitMB,
                            price = price,
                            batchComment = if (selectedAgent != null) "${batchComment} (نقطة: ${selectedAgent.name})" else batchComment,
                            posAgentId = selectedAgent?.id ?: 0L,
                            posAgentName = selectedAgent?.name.orEmpty(),
                            discountPercent = selectedAgent?.discountPercent ?: 0.0
                        )
                        onGenerate(config)
                        onDismiss()
                    },
                    enabled = isProfileValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProfileValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_generate_batch_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isProfileValid) "توليد وحفظ ($parsedCount كرت) في الراوتر فوراً" else "يرجى اختيار باقة صالحة أولاً",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        if (!isProfileValid) return@Button
                        if (singleCode.isNotBlank()) {
                            val selectedAgent = posAgents.find { it.id == selectedAgentId }
                            val card = HotspotCard(
                                id = "card_${System.currentTimeMillis()}",
                                name = singleCode.trim(),
                                password = if (isSingleCode) singleCode.trim() else singlePassword.trim(),
                                profile = selectedProfile,
                                uptime = "0s",
                                bytesIn = 0,
                                bytesOut = 0,
                                limitUptime = timeLimit,
                                limitBytesTotal = if (parsedDataLimitMB > 0) parsedDataLimitMB * 1024 * 1024L else 0L,
                                comment = if (selectedAgent != null) "${singleComment.ifBlank { "كرت فردي" }} (نقطة: ${selectedAgent.name})" else singleComment.ifBlank { "كرت فردي" },
                                disabled = false,
                                price = price
                            )
                            onAddSingleCard?.invoke(
                                card,
                                selectedAgent?.id ?: 0L,
                                selectedAgent?.name.orEmpty(),
                                selectedAgent?.discountPercent ?: 0.0
                            )
                            onDismiss()
                        }
                    },
                    enabled = isProfileValid && singleCode.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_add_single_card_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isProfileValid) "إضافة وتفعيل الكرت الفردي في الراوتر" else "يرجى اختيار باقة صالحة أولاً",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء")
            }
        }
    )
}
