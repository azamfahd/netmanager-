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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HotspotCard
import com.example.data.model.PrintableBatchOption
import com.example.data.util.VoucherSerialEngine
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.viewmodel.MainViewModel

private enum class PrintSelectionTab {
    READY_CARDS,
    DISABLED_STOCK
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintSelectionDialog(
    allCards: List<HotspotCard>,
    batchOptions: List<PrintableBatchOption> = emptyList(),
    lastSavedSerial: Long? = null,
    stockActivationState: MainViewModel.StockActivationUiState = MainViewModel.StockActivationUiState.Idle,
    timestampProvider: (HotspotCard) -> Long = { it.createdAtHint },
    onDismiss: () -> Unit,
    onConfirmSelection: (List<HotspotCard>) -> Unit,
    onConfirmStockSelection: ((selectedCards: List<HotspotCard>, activateNow: Boolean, batchKey: String?) -> Unit)? = null,
    onBatchSelectedForPrint: (String?) -> Unit = {}
) {
    val totalDisabledStockAll = remember(allCards) { allCards.count { it.isManuallyDisabled } }
    val totalEnabledUnusedAll = remember(allCards) { allCards.count { !it.disabled && !it.isActive && !it.isExpired } }

    var activeTab by remember {
        mutableStateOf(
            if (totalDisabledStockAll > 0 && totalEnabledUnusedAll == 0) {
                PrintSelectionTab.DISABLED_STOCK
            } else {
                PrintSelectionTab.READY_CARDS
            }
        )
    }

    // Shared / Ready Tab State
    var selectedBatchKey by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("ALL") } // "ALL", "PROFILE:<name>", "PRICE:<num>"
    var onlyUnusedReady by remember { mutableStateOf(true) }
    var sortSequentially by remember { mutableStateOf(true) }
    var countLimitType by remember { mutableIntStateOf(0) } // 0: All matching, 1: 20, 2: 40, 3: 60

    // Disabled Stock Sequential Pull State
    var stockBatchKey by remember { mutableStateOf<String?>(null) }
    var stockCategory by remember { mutableStateOf("ALL") }
    var stockCountInput by remember { mutableStateOf("48") }
    var activateBeforePrint by remember { mutableStateOf(true) }

    val baseList = remember(allCards, onlyUnusedReady) {
        if (onlyUnusedReady) {
            allCards.filter { !it.disabled && !it.isActive && !it.isExpired }
        } else {
            allCards
        }
    }

    val selectedBatch = remember(batchOptions, selectedBatchKey) {
        batchOptions.find { it.batchKey == selectedBatchKey }
    }

    // Dynamic actual packages in ready cards
    val dynamicProfiles = remember(baseList) {
        baseList.mapNotNull { it.profile.trim().takeIf { p -> p.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

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

    val categoryFilteredList = remember(selectedBatch, selectedCategory, baseList, onlyUnusedReady, sortSequentially) {
        val raw = if (selectedBatch != null) {
            val baseBatch = if (onlyUnusedReady) {
                selectedBatch.readyCards.filter { !it.disabled }
            } else {
                selectedBatch.allCardsInBatch
            }
            baseBatch
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
        if (sortSequentially) {
            VoucherSerialEngine.sortCardsSequentially(raw, timestampProvider).map { it.card }
        } else {
            raw
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

    // --- Stock Mode Computations ---
    val disabledAllCards = remember(allCards) { allCards.filter { it.isManuallyDisabled } }
    val stockBatches = remember(batchOptions) {
        batchOptions.filter { opt -> opt.allCardsInBatch.any { it.isManuallyDisabled } }
    }
    val selectedStockBatch = remember(stockBatches, stockBatchKey) {
        stockBatches.find { it.batchKey == stockBatchKey }
    }

    val stockDynamicProfiles = remember(disabledAllCards) {
        disabledAllCards.mapNotNull { it.profile.trim().takeIf { p -> p.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    val stockDynamicPrices = remember(disabledAllCards) {
        disabledAllCards.mapNotNull { card ->
            card.price.filter { it.isDigit() }.takeIf { it.isNotBlank() }
        }.groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .map { it.key to it.value }
    }

    val stockScopedAllCards = remember(selectedStockBatch, stockCategory, allCards) {
        if (selectedStockBatch != null) {
            selectedStockBatch.allCardsInBatch
        } else {
            when {
                stockCategory == "ALL" -> allCards
                stockCategory.startsWith("PROFILE:") -> {
                    val targetProf = stockCategory.removePrefix("PROFILE:")
                    allCards.filter { it.profile.trim().equals(targetProf, ignoreCase = true) }
                }
                stockCategory.startsWith("PRICE:") -> {
                    val targetPrice = stockCategory.removePrefix("PRICE:")
                    allCards.filter { card ->
                        val numPrice = card.price.filter { it.isDigit() }
                        numPrice == targetPrice ||
                            card.price.contains(targetPrice) ||
                            card.profile.contains(targetPrice) ||
                            card.comment.contains(targetPrice)
                    }
                }
                else -> allCards
            }
        }
    }

    val requestedStockCount = stockCountInput.trim().toIntOrNull()?.coerceIn(1, 5000) ?: 0
    val stockSliceSummary = remember(stockScopedAllCards, requestedStockCount, lastSavedSerial) {
        VoucherSerialEngine.analyzeStockAndSelectSlice(
            scopedCards = stockScopedAllCards,
            requestedCount = requestedStockCount,
            lastSavedSerial = lastSavedSerial,
            timestampProvider = timestampProvider
        )
    }

    val isActivatingNow = stockActivationState is MainViewModel.StockActivationUiState.Activating

    AlertDialog(
        onDismissRequest = { if (!isActivatingNow) onDismiss() },
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
                        imageVector = if (activeTab == PrintSelectionTab.DISABLED_STOCK) Icons.Default.Inventory2 else Icons.Default.Print,
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
                        text = if (activeTab == PrintSelectionTab.DISABLED_STOCK) {
                            "سحب وتفعيل وطباعة كروت المخزون حسب التسلسل"
                        } else {
                            "اختر دفعة مضافة أو باقة للطباعة بالترتيب"
                        },
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
                // Mode Switcher (Ready vs Sequential Disabled Stock)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeTab == PrintSelectionTab.READY_CARDS,
                        onClick = { if (!isActivatingNow) activeTab = PrintSelectionTab.READY_CARDS },
                        label = {
                            Text(
                                "كروت جاهزة ($totalEnabledUnusedAll)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RouterBlue,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("print_mode_ready_tab")
                    )

                    FilterChip(
                        selected = activeTab == PrintSelectionTab.DISABLED_STOCK,
                        onClick = { if (!isActivatingNow) activeTab = PrintSelectionTab.DISABLED_STOCK },
                        label = {
                            Text(
                                "سحب مخزون ($totalDisabledStockAll)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE65100),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("print_mode_stock_tab")
                    )
                }

                if (activeTab == PrintSelectionTab.DISABLED_STOCK) {
                    // =====================================================================
                    // SEQUENTIAL DISABLED STOCK TAB (e.g. 4000 stored -> pick 48 -> enable)
                    // =====================================================================

                    if (disabledAllCards.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "لا توجد كروت مخزنة معطّلة حالياً",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "يمكنك توليد دفعة مخزون معطّلة من زر (+ توليد دفعة) أو مزامنة الكروت المعطلة من الراوتر لسحبها بالتسلسل هنا.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // 1. Batch Filter (if disabled stock batches exist)
                        if (stockBatches.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "اختر دفعة مخزنة (${stockBatches.size}):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(stockBatches, key = { it.batchKey }) { batch ->
                                        val isSel = stockBatchKey == batch.batchKey
                                        val disabledCountInBatch = batch.allCardsInBatch.count { it.isManuallyDisabled }
                                        Card(
                                            modifier = Modifier
                                                .width(195.dp)
                                                .clickable(enabled = !isActivatingNow) {
                                                    stockBatchKey = if (isSel) null else batch.batchKey
                                                }
                                                .testTag("stock_batch_card_${batch.batchKey}"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSel) Color(0xFFE65100).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            ),
                                            border = BorderStroke(
                                                width = if (isSel) 1.8.dp else 1.dp,
                                                color = if (isSel) Color(0xFFE65100) else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = batch.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "باقة: ${batch.profileName.ifBlank { "عام" }}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFE65100).copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "$disabledCountInBatch كرت معطّل",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFE65100),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Profile / Price Scope Filter
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "تصفية المخزون المعطّل حسب الباقة أو الفئة:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isAllStock = stockBatchKey == null && stockCategory == "ALL"
                                FilterChip(
                                    selected = isAllStock,
                                    onClick = {
                                        stockBatchKey = null
                                        stockCategory = "ALL"
                                    },
                                    label = { Text("كل المخزون (${disabledAllCards.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE65100),
                                        selectedLabelColor = Color.White
                                    )
                                )
                                stockDynamicProfiles.forEach { (profName, cnt) ->
                                    val key = "PROFILE:$profName"
                                    val sel = stockBatchKey == null && stockCategory == key
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            stockBatchKey = null
                                            stockCategory = key
                                        },
                                        label = { Text("باقة: $profName ($cnt)", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF00838F),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                stockDynamicPrices.forEach { (priceVal, cnt) ->
                                    val key = "PRICE:$priceVal"
                                    val sel = stockBatchKey == null && stockCategory == key
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            stockBatchKey = null
                                            stockCategory = key
                                        },
                                        label = { Text("فئة $priceVal ر.ي ($cnt)", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = RouterBlue,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // 3. Exact Quantity Input & Presets
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "تحديد عدد الكروت المطلوب سحبها بالتسلسل:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = stockCountInput,
                                onValueChange = { raw ->
                                    if (raw.length <= 4 && raw.all { it.isDigit() }) {
                                        stockCountInput = raw
                                    }
                                },
                                enabled = !isActivatingNow,
                                label = { Text("عدد الكروت (مثلاً 48)", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stock_pull_count_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(24, 48, 60, 120).forEach { preset ->
                                    FilterChip(
                                        selected = requestedStockCount == preset,
                                        onClick = { if (!isActivatingNow) stockCountInput = preset.toString() },
                                        label = { Text("$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 4. Smart Serial Watermark & Range Analyzer Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE65100).copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المتاح في المخزن: ${stockSliceSummary.totalDisabledStock} كرت",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    val lastSnText = stockSliceSummary.effectiveLastSerial?.let {
                                        "#${VoucherSerialEngine.formatSerialDigits(it)}"
                                    } ?: "بداية المخزون"
                                    Text(
                                        text = "آخر تسلسل: $lastSnText",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = RouterBlue
                                    )
                                }

                                if (stockSliceSummary.selectedEntries.isNotEmpty()) {
                                    val firstEntry = stockSliceSummary.selectedEntries.first()
                                    val lastEntry = stockSliceSummary.selectedEntries.last()
                                    Text(
                                        text = "نطاق السحب التالي: #${stockSliceSummary.startSerialLabel}  ◄  #${stockSliceSummary.endSerialLabel} (${stockSliceSummary.selectedEntries.size} كرت)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "من كرت (${firstEntry.card.name}) إلى (${lastEntry.card.name})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (stockSliceSummary.exceedsAvailableStock) {
                                        Text(
                                            text = "ملاحظة: العدد المطلوب أكبر من المتبقي؛ تم تحديد كامل المتبقي (${stockSliceSummary.selectedEntries.size} كرت).",
                                            fontSize = 10.sp,
                                            color = Color(0xFFC62828)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "أدخل عدداً أكبر من 0 لسحب الكروت التالية بالترتيب.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 5. Activation Timing Preference
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "توقيت تفعيل الكروت المختارة في الراوتر:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (activateBeforePrint) StatusActiveGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(1.dp, if (activateBeforePrint) StatusActiveGreen else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isActivatingNow) { activateBeforePrint = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = activateBeforePrint,
                                        onClick = { if (!isActivatingNow) activateBeforePrint = true }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("تفعيل الكروت في الراوتر ثم فتح الطباعة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("يفعّل الـ ${stockSliceSummary.selectedEntries.size} كرتاً الآن مباشرة", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!activateBeforePrint) RouterBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(1.dp, if (!activateBeforePrint) RouterBlue else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isActivatingNow) { activateBeforePrint = false }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = !activateBeforePrint,
                                        onClick = { if (!isActivatingNow) activateBeforePrint = false }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("معاينة الكروت أولاً وتفعيلها لحظة الطباعة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("تبقى معطلة أثناء تصميم القالب وتُفعّل تلقائياً عند الطباعة أو PDF", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        if (stockActivationState is MainViewModel.StockActivationUiState.Activating) {
                            val pct = if (stockActivationState.total > 0) {
                                stockActivationState.completed.toFloat() / stockActivationState.total.toFloat()
                            } else 0f
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "جاري تفعيل ${stockActivationState.completed} / ${stockActivationState.total} (${stockActivationState.currentCardName})...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RouterBlue
                                )
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                    color = RouterBlue
                                )
                            }
                        }
                    }
                } else {
                    // =====================================================================
                    // READY CARDS & BATCHES TAB
                    // =====================================================================
                    // 1. Status Filter (Unused / Ready vs All)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column {
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
                                            text = "كروت جاهزة ومفعّلة غير مستخدمة فقط",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "تصفية الكروت الجاهزة التي لم يبدأ استخدامها بعد",
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { sortSequentially = !sortSequentially }
                                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = null,
                                        tint = RouterBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "ترتيب الكروت تصاعدياً حسب التسلسل",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Checkbox(
                                    checked = sortSequentially,
                                    onCheckedChange = { sortSequentially = it }
                                )
                            }
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
                                    val displayCount = if (onlyUnusedReady) {
                                        batch.readyCards.count { !it.disabled }
                                    } else {
                                        batch.allCardsInBatch.size
                                    }
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
            }
        },
        confirmButton = {
            if (activeTab == PrintSelectionTab.DISABLED_STOCK) {
                val stockCards = stockSliceSummary.selectedCards
                Button(
                    onClick = {
                        if (onConfirmStockSelection != null) {
                            onConfirmStockSelection(stockCards, activateBeforePrint, stockBatchKey)
                        } else {
                            onBatchSelectedForPrint(stockBatchKey)
                            onConfirmSelection(stockCards)
                        }
                    },
                    enabled = stockCards.isNotEmpty() && !isActivatingNow,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activateBeforePrint) Color(0xFFE65100) else RouterBlue
                    ),
                    modifier = Modifier.testTag("confirm_stock_pull_btn")
                ) {
                    if (isActivatingNow) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري التفعيل...")
                    } else {
                        Icon(
                            imageVector = if (activateBeforePrint) Icons.Default.PlayArrow else Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (activateBeforePrint) {
                                "تفعيل (${stockCards.size}) وفتح الطباعة"
                            } else {
                                "معاينة (${stockCards.size}) قبل التفعيل"
                            }
                        )
                    }
                }
            } else {
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
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { if (!isActivatingNow) onDismiss() },
                enabled = !isActivatingNow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إلغاء")
            }
        }
    )
}


