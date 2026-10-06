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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HotspotCard
import com.example.ui.screens.PRESET_CATEGORIES
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintSelectionDialog(
    allCards: List<HotspotCard>,
    onDismiss: () -> Unit,
    onConfirmSelection: (List<HotspotCard>) -> Unit
) {
    // Selection filters:
    // 1. Category price: "ALL", "200", "300", "500", "1000", "2500"
    var selectedCategory by remember { mutableStateOf("ALL") }
    // 2. Only ready for sale / unused cards
    var onlyUnusedReady by remember { mutableStateOf(true) }
    // 3. Limit count
    var countLimitType by remember { mutableIntStateOf(0) } // 0: All matching, 1: 20 cards, 2: 40 cards, 3: 60 cards (full sheet), 4: 100 cards

    // Compute filtered cards
    val baseList = if (onlyUnusedReady) {
        allCards.filter { !it.isActive && !it.isExpired }
    } else {
        allCards
    }

    val categoryFilteredList = if (selectedCategory == "ALL") {
        baseList
    } else {
        baseList.filter {
            it.price.contains(selectedCategory) ||
                    it.profile.contains(selectedCategory) ||
                    it.comment.contains(selectedCategory)
        }
    }

    val finalSelectedCards = when (countLimitType) {
        1 -> categoryFilteredList.take(20)
        2 -> categoryFilteredList.take(40)
        3 -> categoryFilteredList.take(60) // standard A4 4x15 sheet
        4 -> categoryFilteredList.take(100)
        else -> categoryFilteredList
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
                        text = "اختر فئات الكروت الجاهزة للبيع ونموذج الطباعة",
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
                                    text = "تصفية الكروت المفعلة الجديدة التي لم يستهلكها المستخدم بعد",
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

                // 2. Categories Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "اختر فئة الكروت المطلوبة للطباعة:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // All categories chip
                        val countAll = baseList.size
                        FilterChip(
                            selected = selectedCategory == "ALL",
                            onClick = { selectedCategory = "ALL" },
                            label = { Text("جميع الفئات ($countAll)") },
                            leadingIcon = if (selectedCategory == "ALL") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RouterBlue,
                                selectedLabelColor = Color.White
                            )
                        )

                        // 200, 300, 500, 1000, 2500 chips
                        listOf("200", "300", "500", "1000", "2500").forEach { cat ->
                            val countForCat = baseList.count {
                                it.price.contains(cat) || it.profile.contains(cat) || it.comment.contains(cat)
                            }
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text("فئة $cat ر.ي ($countForCat)") },
                                leadingIcon = if (selectedCategory == cat) {
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

                // 3. Count / Pages limit
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
                            3 to "60 كرت (صفحة A4 كاملة)"
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

                // 4. Live Summary Card
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
                        Column {
                            Text(
                                text = "إجمالي الكروت المحددة:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${finalSelectedCards.size} كرت جاهز",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RouterBlue
                            )
                        }

                        val estimatedPages = (finalSelectedCards.size + 59) / 60
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
                Text("فتح واجهة تخصيص وتصميم الطباعة")
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
