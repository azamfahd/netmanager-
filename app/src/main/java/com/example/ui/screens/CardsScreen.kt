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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HotspotCard
import com.example.data.network.NetworkUtils
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.viewmodel.CardFilter
import com.example.ui.viewmodel.MainViewModel

@Composable
fun CardsScreen(
    viewModel: MainViewModel,
    onOpenBatchGenerator: () -> Unit,
    onOpenPrintScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredCards by viewModel.filteredCards.collectAsStateWithLifecycle()
    val allCards by viewModel.cards.collectAsStateWithLifecycle()
    val searchQuery by viewModel.cardSearchQuery.collectAsStateWithLifecycle()
    val currentFilter by viewModel.cardFilter.collectAsStateWithLifecycle()

    var cardToDelete by remember { mutableStateOf<HotspotCard?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateCardSearch(it) },
                    label = { Text("بحث برقم الكرت أو الباقة") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateCardSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_cards_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = {
                        viewModel.setPrintableCards(filteredCards)
                        onOpenPrintScreen()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("print_cards_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "طباعة الكروت",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentFilter == CardFilter.ALL,
                    onClick = { viewModel.updateCardFilter(CardFilter.ALL) },
                    label = { Text("الكل (${allCards.size})") }
                )
                FilterChip(
                    selected = currentFilter == CardFilter.UNUSED,
                    onClick = { viewModel.updateCardFilter(CardFilter.UNUSED) },
                    label = { Text("جاهزة للبيع (${allCards.count { !it.isActive && !it.isExpired }})") }
                )
                FilterChip(
                    selected = currentFilter == CardFilter.ACTIVE,
                    onClick = { viewModel.updateCardFilter(CardFilter.ACTIVE) },
                    label = { Text("مستخدمة (${allCards.count { it.isActive && !it.isExpired }})") }
                )
                FilterChip(
                    selected = currentFilter == CardFilter.EXPIRED,
                    onClick = { viewModel.updateCardFilter(CardFilter.EXPIRED) },
                    label = { Text("منتهية (${allCards.count { it.isExpired }})") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cards List
            if (filteredCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "لا توجد كروت تطابق البحث" else "لا توجد كروت، اضغط على زر توليد كروت لإضافة كروت جديدة للشبكة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenBatchGenerator,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("توليد كروت جديدة الآن")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        HotspotCardItem(
                            card = card,
                            onToggleDisabled = {
                                viewModel.toggleCardDisabled(card.id, card.disabled)
                            },
                            onDelete = { cardToDelete = card }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                    }
                }
            }
        }

        // FAB to generate batch
        FloatingActionButton(
            onClick = onOpenBatchGenerator,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_generate_cards"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = "توليد كروت",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Delete Card Dialog
    cardToDelete?.let { target ->
        ConfirmDialog(
            title = "حذف كرت الشبكة",
            message = "هل أنت متأكد من رغبتك في حذف الكرت (${target.name})؟ لن يتمكن المستخدم من تسجيل الدخول به مجدداً.",
            confirmText = "حذف الكرت",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteCard(target.id)
                cardToDelete = null
            },
            onDismiss = { cardToDelete = null }
        )
    }
}

@Composable
fun HotspotCardItem(
    card: HotspotCard,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotspot_card_${card.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (card.disabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Card code & Status Pill
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(RouterBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = RouterBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (card.password != card.name && card.password.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "كلمة السر: ${card.password}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        card.disabled -> StatusPill(text = "معطل", type = StatusType.ERROR)
                        card.isExpired -> StatusPill(text = "منتهي", type = StatusType.WARNING)
                        card.isActive -> StatusPill(text = "نشط", type = StatusType.SUCCESS)
                        else -> StatusPill(text = "جاهز للبيع", type = StatusType.INFO)
                    }

                    IconButton(
                        onClick = onToggleDisabled,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (card.disabled) Icons.Default.CheckCircle else Icons.Default.Block,
                            contentDescription = "تعطيل أو تفعيل",
                            tint = if (card.disabled) StatusActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الكرت",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Profile & Limits Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "الباقة: ${card.profile}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (card.price.isNotBlank()) {
                    Text(
                        text = card.price,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Limits Info & Progress
            val totalBytesUsed = card.bytesIn + card.bytesOut
            if (card.limitBytesTotal > 0) {
                val usageFraction = (totalBytesUsed.toFloat() / card.limitBytesTotal).coerceIn(0f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "استهلاك البيانات: ${NetworkUtils.formatBytes(totalBytesUsed)} من ${NetworkUtils.formatBytes(card.limitBytesTotal)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(usageFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { usageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (usageFraction >= 0.9f) MaterialTheme.colorScheme.error else RouterBlue
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الاستهلاك: ${NetworkUtils.formatBytes(totalBytesUsed)} (غير محدود)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.limitUptime.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "وقت: ${card.uptime} / ${card.limitUptime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
