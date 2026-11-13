package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HotspotCard
import com.example.data.model.PrintableBatchOption
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintSelectionDialog(
    allCards: List<HotspotCard>,
    batchOptions: List<PrintableBatchOption> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmSelection: (List<HotspotCard>) -> Unit,
    onBatchSelectedForPrint: (String?) -> Unit = {}
) {
    // Selection mode: either a specific added batch (selectedBatchKey != null) OR a category/profile filter
    var selectedBatchKey by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("ALL") } // "ALL", "PROFILE:<name>", "PRICE:<num>"
    var onlyUnusedReady by remember { mutableStateOf(true) }
    var countLimitType by remember { mutableIntStateOf(0) } // 0: All matching, 1: 20, 2: 40, 3: 60

    val baseList = remember(allCards, onlyUnusedReady) {
        if (onlyUnusedReady) {
            allCards.filter { !it.isActive && !it.isExpired }
        } else {
            allCards
        }
    }

    val selectedBatch = remember(batchOptions, selectedBatchKey) {
        batchOptions.find { it.batchKey == selectedBatchKey }
    }

    // Dynamic actual packages in cards
    val dynamicProfiles = remember(baseList) {
        baseList.mapNotNull { it.profile.trim().takeIf { p -> p.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    // Dynamic price categories merged with standard presets (without duplicates)
    val dynamicPriceCategories = remember(baseList) {
        val standardPrices = listOf("200", "300", "500", "1000", "2500")
        val cardPrices = baseList.mapNotNull { card ->
            card.price.filter { it.isDigit() }.takeIf { it.isNotBlank() }
        }
        (standardPrices + cardPrices)
            .distinct()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            .map { pCat ->
                val cnt = baseList.count { card ->
                    val numPrice = card.price.filter { it.isDigit() }
                    numPrice == pCat || card.price.contains(pCat) || card.profile.contains(pCat) || card.comment.contains(pCat)
                }
                pCat to cnt
            }
            .filter { (pCat, cnt) -> cnt > 0 || pCat in standardPrices }
    }

    val categoryFilteredList = remember(selectedBatch, selectedCategory, baseList, onlyUnusedReady) {
        if (selectedBatch != null) {
            if (onlyUnusedReady) selectedBatch.readyCards else selectedBatch.allCardsInBatch
        } else {
            when {
                selectedCategory == "ALL" -> baseList
                selectedCategory.startsWith("PROFILE:") -> {
                    val targetProf = selectedCategory.removePrefix("PROFILE:")
                    baseList.filter { it.profile.trim().equals(targetProf, ignoreCase = true) }
                }
                selectedCategory.startsWith("PRICE:") -> {
                    val targetPrice = selectedCategory.removePrefix("PRICE:")
                    baseList.filter { card ->
                        val numPrice = card.price.filter { it.isDigit() }
                        numPrice == targetPrice ||
                            card.price.contains(targetPrice) ||
                            card.profile.contains(targetPrice) ||
                            card.comment.contains(targetPrice)
                    }
                }
                else -> baseList
            }
        }
    }

    val finalSelectedCards = when (countLimitType) {
        1 -> categoryFilteredList.take(20)
        2 -> categoryFilteredList.take(40)
        3 -> categoryFilteredList.take(60)
        else -> categoryFilteredList
    }

    val selectionSummaryLabel = when {
        selectedBatch != null -> "دفعة: ${selectedBatch.title}"
        selectedCategory.startsWith("PROFILE:") -> "باقة: ${selectedCategory.removePrefix("PROFILE:")}"
        selectedCategory.startsWith("PRICE:") -> "فئة ${selectedCategory.removePrefix("PRICE:")} ر.ي"
        else -> "جميع الكروت"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(RouterBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = RouterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "تحديد الكروت للطباعة والتصميم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اختر دفعة مضافة حديثاً أو فئة/باقة محددة للطباعة",
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Status Filter (Unused / Ready vs All)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onlyUnusedReady = !onlyUnusedReady }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(StatusActiveGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sell,
                                    contentDescription = null,
                                    tint = StatusActiveGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "كروت جاهزة للبيع وغير مستخدمة فقط",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تصفية الكروت الجديدة التي لم يبدأ استخدامها بعد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Checkbox(
                            checked = onlyUnusedReady,
                            onCheckedChange = { onlyUnusedReady = it }
                        )
                    }
                }

                // 2. Dynamic Newly Added Batches & Groups Section
                if (batchOptions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = RouterBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "الدفعات والمجاميع المضافة (${batchOptions.size}):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "اضغط على أي دفعة لطباعة كروتها الجديدة فقط بدون خلطها مع الباقي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(batchOptions, key = { it.batchKey }) { batch ->
                                val isBatchSelected = selectedBatchKey == batch.batchKey
                                val displayCount = if (onlyUnusedReady) batch.readyCards.size else batch.allCardsInBatch.size
                                val accentColor = if (batch.isNewlyAdded) StatusActiveGreen else RouterBlue

                                Card(
                                    modifier = Modifier
                                        .width(205.dp)
                                        .clickable {
                                            if (isBatchSelected) {
                                                selectedBatchKey = null
                                            } else {
                                                selectedBatchKey = batch.batchKey
                                                countLimitType = 0
                                            }
                                        }
                                        .testTag("print_batch_card_${batch.batchKey}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isBatchSelected) {
                                            RouterBlue.copy(alpha = 0.14f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isBatchSelected) 1.8.dp else 1.dp,
                                        color = if (isBatchSelected) RouterBlue else if (batch.isNewlyAdded) StatusActiveGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                                    )
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
                                            if (batch.isNewlyAdded) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = StatusActiveGreen.copy(alpha = 0.18f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.NewReleases,
                                                            contentDescription = null,
                                                            tint = StatusActiveGreen,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Text(
                                                            text = "جديد • لم يُطبع",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = StatusActiveGreen
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = if (batch.isPrintedBefore) "مطبوع مسبقاً" else "مجموعة محفوظة",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            if (isBatchSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "محدد",
                                                    tint = RouterBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = batch.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = "باقة: ${batch.profileName.ifBlank { "عام" }}${if (batch.price.isNotBlank()) " • ${batch.price}" else ""}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = "$displayCount كرت",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = batch.formattedDate.take(16),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Dynamic Categories Selection (Packages + Price Tiers)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = RouterBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "أو اختر حسب الباقة أو الفئة السعرية:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isAllSelected = selectedBatchKey == null && selectedCategory == "ALL"
                        FilterChip(
                            selected = isAllSelected,
                            onClick = {
                                selectedBatchKey = null
                                selectedCategory = "ALL"
                            },
                            label = { Text("جميع الكروت (${baseList.size})", fontSize = 11.sp) },
                            leadingIcon = if (isAllSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RouterBlue,
                                selectedLabelColor = Color.White
                            )
                        )

                        // Dynamic actual packages from router cards
                        dynamicProfiles.forEach { (profName, cnt) ->
                            val key = "PROFILE:$profName"
                            val isSelected = selectedBatchKey == null && selectedCategory == key
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBatchKey = null
                                    selectedCategory = key
                                    countLimitType = 0
                                },
                                label = { Text("باقة: $profName ($cnt)", fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00838F),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        // Dynamic + Standard Price Categories
                        dynamicPriceCategories.forEach { (cat, countForCat) ->
                            val key = "PRICE:$cat"
                            val isSelected = selectedBatchKey == null && selectedCategory == key
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBatchKey = null
                                    selectedCategory = key
                                    countLimitType = 0
                                },
                                label = { Text("فئة $cat ر.ي ($countForCat)", fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (cat) {
                                        "500" -> Color(0xFFF57C00)
                                        "1000" -> Color(0xFF7B1FA2)
                                        "2500" -> Color(0xFFC2185B)
                                        "300" -> Color(0xFF00897B)
                                        else -> RouterBlue
                                    },
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // 4. Count / Pages limit
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "عدد الكروت المراد تضمينها:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0 to "الكل (${categoryFilteredList.size})",
                            1 to "20 كرت",
                            2 to "40 كرت",
                            3 to "60 كرت"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = countLimitType == type,
                                onClick = { countLimitType = type },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 5. Live Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = RouterBlue.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "المحدد: $selectionSummaryLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${finalSelectedCards.size} كرت للطباعة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RouterBlue
                            )
                        }

                        val estimatedPages = if (finalSelectedCards.isEmpty()) 0 else (finalSelectedCards.size + 59) / 60
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RouterBlue,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "$estimatedPages صفحة A4",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onBatchSelectedForPrint(selectedBatchKey)
                    onConfirmSelection(finalSelectedCards)
                },
                enabled = finalSelectedCards.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RouterBlue
                ),
                modifier = Modifier.testTag("open_print_studio_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("فتح واجهة تصميم الطباعة")
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

