package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import com.example.ui.components.EmptyStateComponent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.model.UserSessionRecord
import com.example.data.network.NetworkUtils
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.RouterBlueDark
import com.example.ui.theme.CyberGreenNeon
import com.example.ui.theme.CyberGreenDark
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusErrorRedBg
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWarningAmberBg
import com.example.data.util.DeviceDetector
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EditCardDialog
import com.example.ui.components.PrintSelectionDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.components.UserDetailDialog
import com.example.ui.theme.CyberGreenBright
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.viewmodel.CardFilter
import com.example.ui.viewmodel.MainViewModel

@Composable
fun CardsScreen(
    viewModel: MainViewModel,
    onOpenBatchGenerator: () -> Unit,
    onOpenPrintScreen: () -> Unit,
    onOpenSessionDetail: ((UserSessionRecord) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val filteredCards by viewModel.filteredCards.collectAsStateWithLifecycle()
    val allCards by viewModel.cards.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val userSessions by viewModel.userSessions.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val searchQuery by viewModel.cardSearchQuery.collectAsStateWithLifecycle()
    val currentFilter by viewModel.cardFilter.collectAsStateWithLifecycle()

    var cardToDelete by remember { mutableStateOf<HotspotCard?>(null) }
    var cardToEdit by remember { mutableStateOf<HotspotCard?>(null) }
    var cardToReactivate by remember { mutableStateOf<HotspotCard?>(null) }
    var showPrintSelectionDialog by remember { mutableStateOf(false) }
    var selectedSessionForDetail by remember { mutableStateOf<UserSessionRecord?>(null) }

    val activeUsernames = remember(activeUsers) { activeUsers.map { it.user }.toSet() }
    val activeUsersMap = remember(activeUsers) { activeUsers.associateBy { it.user.lowercase() } }
    val userSessionsMap = remember(userSessions) { userSessions.associateBy { it.username.lowercase() } }
    val distinctCards = remember(allCards) { allCards.distinctBy { it.name } }

    val countActive = remember(distinctCards, activeUsernames) {
        distinctCards.count { activeUsernames.contains(it.name) && !it.isExpired }
    }
    val countUnused = remember(distinctCards, activeUsernames) {
        distinctCards.count { !activeUsernames.contains(it.name) && (it.uptime.isBlank() || it.uptime == "0s") && it.bytesIn == 0L && it.bytesOut == 0L && !it.isExpired }
    }
    val countExpired = remember(distinctCards) {
        distinctCards.count { it.isExpired }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & Action Buttons
            // Search Bar & Action Buttons (Compact & Sleek)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateCardSearch(it) },
                    placeholder = { Text("بحث برقم الكرت أو الباقة...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateCardSearch("") }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("search_cards_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )

                Button(
                    onClick = {
                        showPrintSelectionDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberGreenPrimary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("print_cards_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "طباعة الكروت",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips (Scrollable & Uniform compact height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = currentFilter == CardFilter.ALL,
                    onClick = { viewModel.updateCardFilter(CardFilter.ALL) },
                    label = { Text("الكل (${distinctCards.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                )
                FilterChip(
                    selected = currentFilter == CardFilter.UNUSED,
                    onClick = { viewModel.updateCardFilter(CardFilter.UNUSED) },
                    label = { Text("جاهزة للبيع ($countUnused)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                )
                FilterChip(
                    selected = currentFilter == CardFilter.ACTIVE,
                    onClick = { viewModel.updateCardFilter(CardFilter.ACTIVE) },
                    label = { Text("نشطة حالياً ($countActive)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                )
                FilterChip(
                    selected = currentFilter == CardFilter.EXPIRED,
                    onClick = { viewModel.updateCardFilter(CardFilter.EXPIRED) },
                    label = { Text("منتهية ($countExpired)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
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
                if (filteredCards.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.Default.AddCard,
                            title = if (searchQuery.isNotEmpty()) "لا توجد نتائج بحث مطابقة" else "لا توجد كروت متاحة",
                            description = if (searchQuery.isNotEmpty()) "يرجى تجربة كلمات بحث أخرى." else "لا توجد كروت هوتسبوت مسجلة في الراوتر حالياً. يمكنك البدء بتوليد كروت جديدة.",
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else {
                    items(filteredCards, key = { it.id }) { card ->
                        HotspotCardItem(
                            card = card,
                            onClick = {
                                val cardKey = card.name.lowercase()
                                val realSession = userSessionsMap[cardKey]
                                val activeUser = activeUsersMap[cardKey]
                                val session = realSession ?: UserSessionRecord(
                                    id = card.id,
                                    username = card.name,
                                    ipAddress = activeUser?.address?.ifBlank { "غير متصل" } ?: "192.168.88.x",
                                    macAddress = activeUser?.macAddress?.ifBlank { "00:00:00:00:00:00" } ?: "غير مسجل",
                                    isOnline = activeUser != null || (card.isActive && !card.isExpired),
                                    loginTime = if (activeUser != null) "متصل الآن (${activeUser.uptime})" else if (card.uptime != "0s") "جلسة سابقة (${card.uptime})" else "غير متصل",
                                    logoutTime = if (activeUser != null) "نشط الآن" else if (card.isExpired) "منتهي الاستهلاك" else "كرت جاهز / توقف",
                                    duration = if (activeUser != null) activeUser.uptime else if (card.uptime != "0s") card.uptime else "لم يبدأ",
                                    bytesIn = activeUser?.bytesIn ?: card.bytesIn,
                                    bytesOut = activeUser?.bytesOut ?: card.bytesOut,
                                    totalBytes = (activeUser?.bytesIn ?: card.bytesIn) + (activeUser?.bytesOut ?: card.bytesOut),
                                    terminateReason = if (card.isExpired) "انتهاء رصيد الكرت" else if (activeUser != null) "جلسة نشطة حالياً عبر ${activeUser.nodeName}" else if (card.uptime != "0s") "توقف / خروج الجلسة" else "كرت جاهز للبيع",
                                    profile = card.profile,
                                    price = card.price,
                                    deviceInfo = DeviceDetector.detectDevice(activeUser?.macAddress ?: "", card.comment, card.name),
                                    comment = card.comment.ifBlank { "كرت شبكة ميكروتك" }
                                )
                                if (onOpenSessionDetail != null) {
                                    onOpenSessionDetail(session)
                                } else {
                                    selectedSessionForDetail = session
                                }
                            },
                            onEdit = { cardToEdit = card },
                            onReactivate = { cardToReactivate = card },
                            onToggleDisabled = {
                                viewModel.toggleCardDisabled(card.id, card.disabled)
                            },
                            onDelete = { cardToDelete = card }
                        )
                    }
                }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                    }
                }
            }
        }

        // FAB to generate batch or add single card
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
                    text = "إضافة وتوليد كروت",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // User Detail Dialog
    selectedSessionForDetail?.let { session ->
        UserDetailDialog(
            session = session,
            onDismiss = { selectedSessionForDetail = null }
        )
    }

    // Edit Card Dialog
    cardToEdit?.let { targetCard ->
        EditCardDialog(
            card = targetCard,
            profiles = profiles,
            onDismiss = { cardToEdit = null },
            onSave = { updated ->
                viewModel.updateCard(updated)
                cardToEdit = null
            }
        )
    }

    // Reactivate Confirmation Dialog
    cardToReactivate?.let { targetCard ->
        ConfirmDialog(
            title = "إعادة تفعيل الكرت وتصفير العدادات",
            message = "هل أنت متأكد من رغبتك في إعادة تفعيل الكرت (${targetCard.name}) وتصفير عداد الوقت والاستهلاك ليصبح جاهزاً للبيع والاستخدام مجدداً؟",
            confirmText = "إعادة التفعيل الآن",
            isDestructive = false,
            onConfirm = {
                viewModel.reactivateCard(targetCard)
                cardToReactivate = null
            },
            onDismiss = { cardToReactivate = null }
        )
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

    // Print Category & Ready Cards Selection Dialog
    if (showPrintSelectionDialog) {
        PrintSelectionDialog(
            allCards = allCards,
            onDismiss = { showPrintSelectionDialog = false },
            onConfirmSelection = { selectedCards ->
                viewModel.setPrintableCards(selectedCards)
                showPrintSelectionDialog = false
                onOpenPrintScreen()
            }
        )
    }
}

@Composable
fun HotspotCardItem(
    card: HotspotCard,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onReactivate: () -> Unit,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hotspot_card_${card.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (card.disabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Card code & Status Pill & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(onClick = onClick)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

            // Bottom Action Buttons: [إعادة تفعيل الكرت] & [تعديل الكرت] & [التفاصيل]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reactivate Button (Especially prominent for expired or used cards)
                OutlinedButton(
                    onClick = onReactivate,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusActiveGreen
                    ),
                    border = BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reactivate_card_btn_${card.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "إعادة تفعيل",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Edit Card Button
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RouterBlue
                    ),
                    border = BorderStroke(1.dp, RouterBlue.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_card_btn_${card.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تعديل الكرت",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Details Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "تفاصيل",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
