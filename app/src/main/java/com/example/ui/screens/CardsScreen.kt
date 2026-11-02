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
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.example.ui.viewmodel.CardSystemFilter
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
    val pendingTasksCount by viewModel.pendingTasksCount.collectAsStateWithLifecycle()
    val isProcessingQueue by viewModel.isProcessingQueue.collectAsStateWithLifecycle()

    var cardToDelete by remember { mutableStateOf<HotspotCard?>(null) }
    var cardToEdit by remember { mutableStateOf<HotspotCard?>(null) }
    var cardToReactivate by remember { mutableStateOf<HotspotCard?>(null) }
    var showPrintSelectionDialog by remember { mutableStateOf(false) }
    var selectedSessionForDetail by remember { mutableStateOf<UserSessionRecord?>(null) }
    var showReadyCardsDetailDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var readySubFilter by remember { mutableStateOf(ReadyCardsSubFilter.ALL) }
    var readySortOrder by remember { mutableStateOf(ReadyCardsSortOrder.NEWEST_FIRST) }
    var collapsedBatchKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

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
    val countExpiredQuota = remember(distinctCards) {
        distinctCards.count { it.isExpiredQuota }
    }
    val countExpiredValidity = remember(distinctCards) {
        distinctCards.count { it.isExpiredValidity }
    }

    val onCardDetailClick: (HotspotCard) -> Unit = { card ->
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

            Spacer(modifier = Modifier.height(4.dp))

            // Task Queue Pending Status Banner
            if (pendingTasksCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessingQueue) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "طابور العمليات المعلقة ($pendingTasksCount عملية)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "تعديلات وحذف محفوظة محلياً - تُرسل للراوتر تلقائياً مع Exponential Backoff",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.processTaskQueueNow() },
                            enabled = !isProcessingQueue,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isProcessingQueue) "جاري الإرسال..." else "إرسال الآن",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

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
                FilterChip(
                    selected = currentFilter == CardFilter.EXPIRED_QUOTA,
                    onClick = { viewModel.updateCardFilter(CardFilter.EXPIRED_QUOTA) },
                    label = { Text("انتهى الرصيد ($countExpiredQuota)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                )
                FilterChip(
                    selected = currentFilter == CardFilter.EXPIRED_VALIDITY,
                    onClick = { viewModel.updateCardFilter(CardFilter.EXPIRED_VALIDITY) },
                    label = { Text("انتهت الصلاحية ($countExpiredValidity)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
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
            } else if (currentFilter == CardFilter.UNUSED) {
                // Specialized Ready-to-Sell View: Frame-based organization with Batch & Individual grouping and full tracking
                val readyCards = remember(distinctCards, activeUsernames, searchQuery) {
                    distinctCards.filter { card ->
                        val isUnused = !activeUsernames.contains(card.name) &&
                                (card.uptime.isBlank() || card.uptime == "0s") &&
                                card.bytesIn == 0L && card.bytesOut == 0L &&
                                !card.isExpired
                        if (!isUnused) return@filter false
                        if (searchQuery.isBlank()) true
                        else {
                            val q = searchQuery.trim().lowercase()
                            card.name.lowercase().contains(q) ||
                                    card.profile.lowercase().contains(q) ||
                                    card.price.lowercase().contains(q) ||
                                    card.comment.lowercase().contains(q)
                        }
                    }
                }

                val (rawBatchCards, rawIndividualCards) = remember(readyCards) {
                    readyCards.partition { card ->
                        val c = card.comment.lowercase()
                        when {
                            c.contains("فردي") -> false
                            c.contains("دفعة") || c.contains("batch") -> true
                            card.id.startsWith("gen_") -> true
                            card.comment.isNotBlank() && readyCards.count { it.comment == card.comment } > 1 -> true
                            else -> false
                        }
                    }
                }

                val batchGroups = remember(rawBatchCards) {
                    rawBatchCards.groupBy { card ->
                        when {
                            card.comment.contains("دفعة") || card.comment.contains("batch") -> card.comment
                            card.id.startsWith("gen_") -> {
                                val parts = card.id.split("_")
                                if (parts.size >= 2) "batch_${parts[1]}_${card.profile}" else card.comment.ifBlank { "دفعة_${card.profile}" }
                            }
                            card.comment.isNotBlank() -> card.comment
                            else -> "batch_${card.profile}_${card.price}"
                        }
                    }.map { (key, cardsInBatch) ->
                        val first = cardsInBatch.first()
                        val ts = cardsInBatch.maxOfOrNull { extractCardTimestamp(it) } ?: 0L
                        val formattedDate = if (ts > 0L) NetworkUtils.formatArabicFullDateTime(ts) else "تاريخ محفوظ في الراوتر"
                        val title = when {
                            first.comment.isNotBlank() && first.comment != "دفعة كروت" -> first.comment
                            first.price.isNotBlank() -> "دفعة كروت (${first.profile}) - ${first.price}"
                            else -> "دفعة كروت (${first.profile})"
                        }
                        val totalVal = cardsInBatch.sumOf { parsePriceNumber(it.price) }
                        CardBatchGroup(
                            batchKey = key,
                            title = title,
                            profile = first.profile,
                            price = first.price,
                            timestamp = ts,
                            formattedDate = formattedDate,
                            totalValue = totalVal,
                            cards = cardsInBatch
                        )
                    }
                }

                val sortedBatches = remember(batchGroups, readySortOrder) {
                    when (readySortOrder) {
                        ReadyCardsSortOrder.NEWEST_FIRST -> batchGroups.sortedByDescending { it.timestamp }
                        ReadyCardsSortOrder.OLDEST_FIRST -> batchGroups.sortedBy { it.timestamp }
                        ReadyCardsSortOrder.BY_PROFILE -> batchGroups.sortedBy { it.profile }
                        ReadyCardsSortOrder.BY_PRICE -> batchGroups.sortedByDescending { it.totalValue }
                    }
                }

                val sortedIndividualCards = remember(rawIndividualCards, readySortOrder) {
                    when (readySortOrder) {
                        ReadyCardsSortOrder.NEWEST_FIRST -> rawIndividualCards.sortedByDescending { viewModel.getCardCreationTimestamp(it) }
                        ReadyCardsSortOrder.OLDEST_FIRST -> rawIndividualCards.sortedBy { viewModel.getCardCreationTimestamp(it) }
                        ReadyCardsSortOrder.BY_PROFILE -> rawIndividualCards.sortedBy { it.profile }
                        ReadyCardsSortOrder.BY_PRICE -> rawIndividualCards.sortedByDescending { parsePriceNumber(it.price) }
                    }
                }

                val totalFinancialVal = remember(readyCards) { readyCards.sumOf { parsePriceNumber(it.price) } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Analytics & Tracking Header Card
                    item {
                        ReadyCardsDashboardCard(
                            totalReadyCount = readyCards.size,
                            totalFinancialValue = totalFinancialVal,
                            batchesCount = batchGroups.size,
                            batchCardsCount = rawBatchCards.size,
                            individualCardsCount = rawIndividualCards.size,
                            currentSubFilter = readySubFilter,
                            onSubFilterSelected = { readySubFilter = it },
                            currentSortOrder = readySortOrder,
                            onSortOrderSelected = { readySortOrder = it },
                            onReadyCardsClick = { showReadyCardsDetailDialog = true }
                        )
                    }

                    // 2. Batches Frames Section
                    if (readySubFilter == ReadyCardsSubFilter.ALL || readySubFilter == ReadyCardsSubFilter.BATCHES) {
                        if (sortedBatches.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = RouterBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "الدفعات الجماعية المتاحة للبيع (${sortedBatches.size} دفعة - ${rawBatchCards.size} كرت)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            sortedBatches.forEach { batch ->
                                val isExpanded = !collapsedBatchKeys.contains(batch.batchKey)
                                item(key = "batch_header_${batch.batchKey}") {
                                    BatchGroupHeaderCard(
                                        batch = batch,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            collapsedBatchKeys = if (isExpanded) {
                                                collapsedBatchKeys + batch.batchKey
                                            } else {
                                                collapsedBatchKeys - batch.batchKey
                                            }
                                        },
                                        onPrintBatch = {
                                            viewModel.setPrintableCards(batch.cards)
                                            onOpenPrintScreen()
                                        },
                                        onCopyBatchCodes = {
                                            val text = batch.cards.joinToString("\n") { card ->
                                                if (card.password.isNotBlank() && card.password != card.name) {
                                                    "كود: ${card.name} | كلمة السر: ${card.password} | باقة: ${card.profile} | سعر: ${card.price}"
                                                } else {
                                                    "كود: ${card.name} | باقة: ${card.profile} | سعر: ${card.price}"
                                                }
                                            }
                                            clipboardManager.setText(AnnotatedString(text))
                                            Toast.makeText(context, "تم نسخ أكواد ${batch.cards.size} كرت للحافظة بنجاح", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                if (isExpanded) {
                                    items(batch.cards, key = { "batch_card_${batch.batchKey}_${it.id}" }) { card ->
                                        Box(modifier = Modifier.padding(start = 12.dp)) {
                                            HotspotCardItem(
                                                card = card,
                                                creationTimestamp = viewModel.getCardCreationTimestamp(card),
                                                onClick = { onCardDetailClick(card) },
                                                onEdit = { cardToEdit = card },
                                                onReactivate = { cardToReactivate = card },
                                                onToggleDisabled = { viewModel.toggleCardDisabled(card.id, card.disabled) },
                                                onDelete = { cardToDelete = card },
                                                onVerifyAndActivate = { viewModel.verifyAndActivateCard(card) }
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (readySubFilter == ReadyCardsSubFilter.BATCHES) {
                            item {
                                EmptyStateComponent(
                                    icon = Icons.Default.Group,
                                    title = "لا توجد دفعات جماعية جاهزة للبيع",
                                    description = "يمكنك توليد دفعة كروت جديدة وتحديد عدد الكروت والباقة ليتم تصنيفها وتتبعها هنا تلقائياً.",
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        }
                    }

                    // 3. Individual Cards Section
                    if (readySubFilter == ReadyCardsSubFilter.ALL || readySubFilter == ReadyCardsSubFilter.INDIVIDUAL) {
                        if (sortedIndividualCards.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = StatusActiveGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "الكروت المولدة فردياً (${sortedIndividualCards.size} كرت)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.setPrintableCards(sortedIndividualCards)
                                            onOpenPrintScreen()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreenPrimary),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("طباعة الكل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            items(sortedIndividualCards, key = { "ind_card_${it.id}" }) { card ->
                                HotspotCardItem(
                                    card = card,
                                    creationTimestamp = viewModel.getCardCreationTimestamp(card),
                                    onClick = { onCardDetailClick(card) },
                                    onEdit = { cardToEdit = card },
                                    onReactivate = { cardToReactivate = card },
                                    onToggleDisabled = { viewModel.toggleCardDisabled(card.id, card.disabled) },
                                    onDelete = { cardToDelete = card },
                                    onVerifyAndActivate = { viewModel.verifyAndActivateCard(card) }
                                )
                            }
                        } else if (readySubFilter == ReadyCardsSubFilter.INDIVIDUAL) {
                            item {
                                EmptyStateComponent(
                                    icon = Icons.Default.Person,
                                    title = "لا توجد كروت فردية جاهزة للبيع",
                                    description = "يمكنك إنشاء كرت مخصص بشكل فردي من نافذة الإضافة وسيظهر في هذا القسم المخصص.",
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
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
                            creationTimestamp = viewModel.getCardCreationTimestamp(card),
                            onClick = { onCardDetailClick(card) },
                            onEdit = { cardToEdit = card },
                            onReactivate = { cardToReactivate = card },
                            onToggleDisabled = {
                                viewModel.toggleCardDisabled(card.id, card.disabled)
                            },
                            onDelete = { cardToDelete = card },
                            onVerifyAndActivate = { viewModel.verifyAndActivateCard(card) }
                        )
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
        val userHistory by viewModel.getLoginHistoryForUser(session.username).collectAsStateWithLifecycle(emptyList())
        val subscriberProfile by viewModel.getSubscriberProfileFlow(session.username).collectAsStateWithLifecycle(null)
        UserDetailDialog(
            session = session,
            subscriberProfile = subscriberProfile,
            loginHistory = userHistory,
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
                viewModel.deleteCard(target.id, target.name)
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

    // Ready Cards Detailed Package Breakdown Dialog
    if (showReadyCardsDetailDialog) {
        val readyCards = remember(distinctCards, activeUsernames) {
            distinctCards.filter { card ->
                !activeUsernames.contains(card.name) &&
                (card.uptime.isBlank() || card.uptime == "0s") &&
                card.bytesIn == 0L && card.bytesOut == 0L &&
                !card.isExpired
            }
        }
        ReadyCardsDetailDialog(
            readyCards = readyCards,
            onDismiss = { showReadyCardsDetailDialog = false },
            onPrintCards = { cards ->
                viewModel.setPrintableCards(cards)
                showReadyCardsDetailDialog = false
                onOpenPrintScreen()
            }
        )
    }
}

@Composable
fun HotspotCardItem(
    card: HotspotCard,
    creationTimestamp: Long,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onReactivate: () -> Unit,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit,
    onVerifyAndActivate: (() -> Unit)? = null
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                color = (if (card.isUserManagerCard) Color(0xFF00897B) else Color(0xFF1976D2)).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = card.systemLabelAr,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (card.isUserManagerCard) Color(0xFF00897B) else Color(0xFF1976D2),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Added: Creation date badge
                        if (creationTimestamp > 0) {
                            Text(
                                text = "أضيف: ${NetworkUtils.formatCardCreationDate(creationTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp
                            )
                        }
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

                // Verify & Activate in Router Button
                if (onVerifyAndActivate != null) {
                    IconButton(
                        onClick = onVerifyAndActivate,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("verify_activate_btn_${card.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "تحقق وتنشيط في الراوتر",
                            tint = StatusActiveGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Ready-To-Sell Cards Organization, Frames, and Tracking Components
// ------------------------------------------------------------------------------------------------

enum class ReadyCardsSubFilter(val title: String) {
    ALL("الكل"),
    BATCHES("دفعات جماعية"),
    INDIVIDUAL("كروت فردية")
}

enum class ReadyCardsSortOrder(val title: String) {
    NEWEST_FIRST("الأحدث إضافة"),
    OLDEST_FIRST("الأقدم إضافة"),
    BY_PROFILE("حسب الباقة"),
    BY_PRICE("حسب السعر")
}

data class CardBatchGroup(
    val batchKey: String,
    val title: String,
    val profile: String,
    val price: String,
    val timestamp: Long,
    val formattedDate: String,
    val totalValue: Double,
    val cards: List<HotspotCard>
)

fun extractCardTimestamp(card: HotspotCard, viewModel: MainViewModel? = null): Long {
    if (viewModel != null) return viewModel.getCardCreationTimestamp(card)
    val parts = card.id.split("_")
    if (parts.size >= 2) {
        val ts = parts[1].toLongOrNull()
        if (ts != null && ts > 1000000000L) return ts
    }
    return 0L
}

fun formatCardCreationDate(card: HotspotCard): String {
    val ts = extractCardTimestamp(card)
    return if (ts > 0L) {
        NetworkUtils.formatArabicFullDateTime(ts)
    } else {
        "جاهز للبيع - مسجل بالراوتر"
    }
}

fun parsePriceNumber(priceStr: String): Double {
    if (priceStr.isBlank()) return 0.0
    val cleaned = priceStr.replace(",", "").filter { it.isDigit() || it == '.' }
    return cleaned.toDoubleOrNull() ?: 0.0
}

@Composable
fun ReadyCardsDashboardCard(
    totalReadyCount: Int,
    totalFinancialValue: Double,
    batchesCount: Int,
    batchCardsCount: Int,
    individualCardsCount: Int,
    currentSubFilter: ReadyCardsSubFilter,
    onSubFilterSelected: (ReadyCardsSubFilter) -> Unit,
    currentSortOrder: ReadyCardsSortOrder,
    onSortOrderSelected: (ReadyCardsSortOrder) -> Unit,
    onReadyCardsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ready_cards_dashboard_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Subtitle
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(RouterBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = RouterBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "لوحة تتبع ومتابعة الكروت الجاهزة للبيع",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "فرز وتنظيم دقيق للكروت المولدة فردياً والدفعات الجماعية مع تواريخ الإضافة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Summary Metrics Grid (4 items)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Ready Cards Count (Clickable to open detailed package breakdown)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onReadyCardsClick),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "جاهزة للبيع (تفاصيل)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(text = "$totalReadyCount كرت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Metric 2: Financial Value
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = CyberGreenPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, CyberGreenPrimary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "القيمة الإجمالية", style = MaterialTheme.typography.labelSmall, color = StatusActiveGreen, fontSize = 10.sp)
                        Text(text = "${totalFinancialValue.toInt()} ر.ي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = StatusActiveGreen)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 3: Batches
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = RouterBlue.copy(alpha = 0.1f),
                    border = BorderStroke(0.6.dp, RouterBlue.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "الدفعات الجماعية", style = MaterialTheme.typography.labelSmall, color = RouterBlue, fontSize = 10.sp)
                        Text(text = "$batchesCount دفعة ($batchCardsCount كرت)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RouterBlue)
                    }
                }

                // Metric 4: Individual Cards
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "الكروت الفردية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                        Text(text = "$individualCardsCount كرت مخصص", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Sub-filter selector chips (All, Batches, Individual)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = currentSubFilter == ReadyCardsSubFilter.ALL,
                    onClick = { onSubFilterSelected(ReadyCardsSubFilter.ALL) },
                    label = { Text("عرض الكل ($totalReadyCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                )
                FilterChip(
                    selected = currentSubFilter == ReadyCardsSubFilter.BATCHES,
                    onClick = { onSubFilterSelected(ReadyCardsSubFilter.BATCHES) },
                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    label = { Text("دفعات جماعية ($batchesCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                )
                FilterChip(
                    selected = currentSubFilter == ReadyCardsSubFilter.INDIVIDUAL,
                    onClick = { onSubFilterSelected(ReadyCardsSubFilter.INDIVIDUAL) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    label = { Text("كروت فردية ($individualCardsCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                )
            }

            // Sorting Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "ترتيب",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "الترتيب:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ReadyCardsSortOrder.entries.forEach { option ->
                    FilterChip(
                        selected = currentSortOrder == option,
                        onClick = { onSortOrderSelected(option) },
                        label = { Text(option.title, fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BatchGroupHeaderCard(
    batch: CardBatchGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPrintBatch: () -> Unit,
    onCopyBatchCodes: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_group_header_${batch.batchKey}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.2.dp, RouterBlue.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Title, Batch Badge, Creation Date, Expand Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggleExpand),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RouterBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = RouterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = batch.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RouterBlue.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "دفعة جماعية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RouterBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Full Arabic Creation Date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = RouterBlue
                            )
                            Text(
                                text = "تاريخ التوليد: ${batch.formattedDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "طي الدفعة" else "عرض كروت الدفعة",
                        tint = RouterBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badges & Value Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "الباقة: ${batch.profile}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (batch.price.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "سعر الكرت: ${batch.price}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${batch.cards.size} كروت بالدفعة",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (batch.totalValue > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusActiveGreenBg
                    ) {
                        Text(
                            text = "إجمالي القيمة: ${batch.totalValue.toInt()} ر.ي",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusActiveGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Toolbar (Print Batch, Copy All Codes, Expand/Collapse)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrintBatch,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreenPrimary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة الدفعة كاملة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCopyBatchCodes,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ جميع الأكواد", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isExpanded) "طي (${batch.cards.size})" else "عرض الكروت (${batch.cards.size})",
                        fontSize = 11.sp,
                        color = RouterBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ReadyCardsDetailDialog(
    readyCards: List<HotspotCard>,
    onDismiss: () -> Unit,
    onPrintCards: (List<HotspotCard>) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val profileGroups = remember(readyCards) { readyCards.groupBy { it.profile.ifBlank { "عام" } } }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = RouterBlue)
                Text("تفاصيل الكروت الجاهزة للبيع حسب الباقات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "إجمالي الكروت الجاهزة: ${readyCards.size} كرت | القيمة الإجمالية: ${readyCards.sumOf { parsePriceNumber(it.price) }.toInt()} ر.ي",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    profileGroups.forEach { (profileName, cardsInProfile) ->
                        val profileTotalVal = cardsInProfile.sumOf { parsePriceNumber(it.price) }
                        item(key = "dialog_profile_$profileName") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(0.8.dp, RouterBlue.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(RouterBlue.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(14.dp))
                                            }
                                            Text(
                                                text = "باقة: $profileName",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = StatusActiveGreenBg
                                        ) {
                                            Text(
                                                text = "${cardsInProfile.size} كرت (${profileTotalVal.toInt()} ر.ي)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusActiveGreen,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onPrintCards(cardsInProfile)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp).weight(1f)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("طباعة كروت الباقة", fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val txt = cardsInProfile.joinToString("\n") { "${it.name} | ${it.password}" }
                                                clipboardManager.setText(AnnotatedString(txt))
                                                Toast.makeText(context, "تم نسخ ${cardsInProfile.size} كرت لباقة $profileName", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp).weight(1f)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("نسخ الأكواد", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("إغلاق")
            }
        }
    )
}
