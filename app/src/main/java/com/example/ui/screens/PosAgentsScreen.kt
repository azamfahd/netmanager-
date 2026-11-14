package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PosAgentEntity
import com.example.data.local.PosAgentSummary
import com.example.data.local.PosCardAssignmentEntity
import com.example.data.local.PosPaymentEntity
import com.example.data.model.HotspotCard
import com.example.ui.theme.CyberGreenDark
import com.example.ui.theme.CyberGreenNeon
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.RouterBlueDark
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusErrorRedBg
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWarningAmberBg
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PosAgentsScreen(
    viewModel: MainViewModel,
    onOpenBatchGeneratorForAgent: (PosAgentEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summaries by viewModel.posSummaries.collectAsStateWithLifecycle()
    val allCards by viewModel.cards.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, WITH_DEBT, TOP_SALES

    var showAddAgentDialog by remember { mutableStateOf(false) }
    var agentToEdit by remember { mutableStateOf<PosAgentEntity?>(null) }
    var selectedAgentForDetails by remember { mutableStateOf<PosAgentSummary?>(null) }
    var agentForPayment by remember { mutableStateOf<PosAgentEntity?>(null) }
    var agentForAssignCards by remember { mutableStateOf<PosAgentEntity?>(null) }
    var agentToDelete by remember { mutableStateOf<PosAgentEntity?>(null) }

    // Aggregate statistics
    val totalAgents = summaries.size
    val totalAssignedCards = summaries.sumOf { it.totalAssignedCards }
    val totalSoldCards = summaries.sumOf { it.soldCardsCount }
    val totalRemainingCards = summaries.sumOf { it.remainingCardsCount }
    val totalGrossSales = summaries.sumOf { it.totalSalesVolume }
    val totalOwnerDue = summaries.sumOf { it.totalDueToOwner }
    val totalCollected = summaries.sumOf { it.totalPaidByAgent }
    val totalOutstandingDebt = summaries.sumOf { it.netRemainingDebt }

    val filteredSummaries = summaries.filter { item ->
        val matchesQuery = searchQuery.isBlank() ||
                item.agent.name.contains(searchQuery, ignoreCase = true) ||
                item.agent.phone.contains(searchQuery, ignoreCase = true) ||
                item.agent.location.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "WITH_DEBT" -> item.netRemainingDebt > 0
            "TOP_SALES" -> item.soldCardsCount > 0
            else -> true
        }

        matchesQuery && matchesFilter
    }.sortedWith(
        when (selectedFilter) {
            "WITH_DEBT" -> compareByDescending { it.netRemainingDebt }
            "TOP_SALES" -> compareByDescending { it.soldCardsCount }
            else -> compareByDescending<PosAgentSummary> { it.netRemainingDebt }.thenByDescending { it.soldCardsCount }
        }
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                // Cyber Overview Header Banner
                PosDashboardHeader(
                    totalAgents = totalAgents,
                    totalAssignedCards = totalAssignedCards,
                    totalSoldCards = totalSoldCards,
                    totalRemainingCards = totalRemainingCards,
                    totalGrossSales = totalGrossSales,
                    totalOwnerDue = totalOwnerDue,
                    totalCollected = totalCollected,
                    totalOutstandingDebt = totalOutstandingDebt
                )
            }

            // Search and filter controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_search_input"),
                        placeholder = { Text("بحث عن نقطة بيع، بقالة، رقم هاتف أو موقع...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberGreenNeon) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("كافة النقاط ($totalAgents)") },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberGreenNeon.copy(alpha = 0.18f),
                                selectedLabelColor = CyberGreenNeon
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == "WITH_DEBT",
                            onClick = { selectedFilter = "WITH_DEBT" },
                            label = { Text("عليها مبالغ متبقية (${summaries.count { it.netRemainingDebt > 0 }})") },
                            leadingIcon = { Icon(Icons.Default.MoneyOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusWarningAmber.copy(alpha = 0.18f),
                                selectedLabelColor = StatusWarningAmber
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == "TOP_SALES",
                            onClick = { selectedFilter = "TOP_SALES" },
                            label = { Text("الأكثر مبيعاً 🔥") },
                            leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberGreenNeon.copy(alpha = 0.18f),
                                selectedLabelColor = CyberGreenNeon
                            )
                        )
                    }
                }
            }

            if (filteredSummaries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Store,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = CyberGreenNeon
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة لبحثك" else "لا توجد نقاط بيع مضافة حتى الآن",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "أضف أصحاب البقالات والموزعين لتوزيع الكروت وتتبع المبيعات والديون تلقائياً فور تسجيل دخول الزبائن.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showAddAgentDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إضافة نقطة بيع جديدة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filteredSummaries, key = { it.agent.id }) { summary ->
                    PosAgentCard(
                        summary = summary,
                        onViewDetails = { selectedAgentForDetails = summary },
                        onRecordPayment = { agentForPayment = summary.agent },
                        onAssignCards = { agentForAssignCards = summary.agent },
                        onGenerateBatch = { onOpenBatchGeneratorForAgent(summary.agent) },
                        onEdit = { agentToEdit = summary.agent },
                        onDelete = { agentToDelete = summary.agent }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Action Button to Add New POS Agent
        FloatingActionButton(
            onClick = { showAddAgentDialog = true },
            containerColor = CyberGreenNeon,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_add_pos_agent")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة نقطة بيع", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Dialogs
    if (showAddAgentDialog) {
        AddEditAgentDialog(
            agent = null,
            onDismiss = { showAddAgentDialog = false },
            onSave = { newAgent ->
                viewModel.addPosAgent(newAgent)
                showAddAgentDialog = false
            }
        )
    }

    agentToEdit?.let { agent ->
        AddEditAgentDialog(
            agent = agent,
            onDismiss = { agentToEdit = null },
            onSave = { updatedAgent ->
                viewModel.updatePosAgent(updatedAgent)
                agentToEdit = null
            }
        )
    }

    agentToDelete?.let { agent ->
        AlertDialog(
            onDismissRequest = { agentToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusErrorRed) },
            title = { Text("حذف نقطة البيع") },
            text = { Text("هل أنت متأكد من حذف نقطة البيع (${agent.name}) وكافة سجلات تسليم الكروت والدفعات الخاصة بها؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePosAgent(agent.id)
                        agentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed)
                ) {
                    Text("نعم، حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { agentToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    agentForPayment?.let { agent ->
        RecordPaymentDialog(
            agent = agent,
            suggestedAmount = summaries.find { it.agent.id == agent.id }?.netRemainingDebt ?: 0.0,
            onDismiss = { agentForPayment = null },
            onSave = { amount, note ->
                viewModel.recordPosPayment(agent.id, agent.name, amount, note)
                agentForPayment = null
            }
        )
    }

    agentForAssignCards?.let { agent ->
        AssignCardsDialog(
            agent = agent,
            availableCards = allCards.filter { it.isUnused },
            onDismiss = { agentForAssignCards = null },
            onAssign = { selectedCards ->
                viewModel.assignCardsToAgent(agent.id, agent.name, selectedCards, agent.discountPercent)
                agentForAssignCards = null
            }
        )
    }

    selectedAgentForDetails?.let { currentSummary ->
        val liveSummary = summaries.find { it.agent.id == currentSummary.agent.id } ?: currentSummary
        AgentDetailDialog(
            summary = liveSummary,
            onDismiss = { selectedAgentForDetails = null },
            onRecordPayment = { agentForPayment = liveSummary.agent },
            onAssignCards = { agentForAssignCards = liveSummary.agent },
            onDeletePayment = { paymentId -> viewModel.deletePosPayment(paymentId) }
        )
    }
}

@Composable
fun PosDashboardHeader(
    totalAgents: Int,
    totalAssignedCards: Int,
    totalSoldCards: Int,
    totalRemainingCards: Int,
    totalGrossSales: Double,
    totalOwnerDue: Double,
    totalCollected: Double,
    totalOutstandingDebt: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pos_dashboard_header"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberGreenNeon.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = CyberGreenNeon)
                    }
                    Column {
                        Text(
                            text = "إدارة نقاط البيع والموزعين",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyberGreenNeon
                        )
                        Text(
                            text = "تتبع فوري لمبيعات البقالات ومستحقاتك",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberGreenNeon.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "$totalAgents نقاط نشطة",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreenNeon
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 4 Key Metric Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    title = "إجمالي المبيعات",
                    value = "${totalGrossSales.toInt()} ر.ي",
                    icon = Icons.Default.TrendingUp,
                    tint = CyberGreenNeon,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "مستحقاتك الصافية",
                    value = "${totalOwnerDue.toInt()} ر.ي",
                    icon = Icons.Default.AttachMoney,
                    tint = StatusActiveGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    title = "المبالغ المحصلة",
                    value = "${totalCollected.toInt()} ر.ي",
                    icon = Icons.Default.CheckCircle,
                    tint = StatusActiveGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "المتبقي في السوق (ديون)",
                    value = "${totalOutstandingDebt.toInt()} ر.ي",
                    icon = Icons.Default.MoneyOff,
                    tint = if (totalOutstandingDebt > 0) StatusWarningAmber else StatusActiveGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            // Cards Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "حالة الكروت الموزعة: $totalSoldCards مباع / $totalAssignedCards إجمالي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "المتبقي: $totalRemainingCards كرت",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreenNeon
                    )
                }
                val fraction = if (totalAssignedCards > 0) (totalSoldCards.toFloat() / totalAssignedCards).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = CyberGreenNeon,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
        }
    }
}

@Composable
fun PosAgentCard(
    summary: PosAgentSummary,
    onViewDetails: () -> Unit,
    onRecordPayment: () -> Unit,
    onAssignCards: () -> Unit,
    onGenerateBatch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val agent = summary.agent
    val context = LocalContext.current
    val progressFraction = if (summary.totalAssignedCards > 0) {
        (summary.soldCardsCount.toFloat() / summary.totalAssignedCards).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() }
            .testTag("pos_agent_card_${agent.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (summary.netRemainingDebt > 0) StatusWarningAmber.copy(alpha = 0.4f) else CyberGreenNeon.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Name, Location, Discount Pill & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CyberGreenNeon.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = CyberGreenNeon)
                    }
                    Column {
                        Text(
                            text = agent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (agent.location.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = agent.location,
                                    style = MaterialTheme.typography.bodySmall,
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyberGreenNeon.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "عمولة ${agent.discountPercent.toInt()}%",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberGreenNeon
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = StatusErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Cards Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المبيعات: ${summary.soldCardsCount} مباع من ${summary.totalAssignedCards}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "المتبقي: ${summary.remainingCardsCount} كرت",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = CyberGreenNeon,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Financial Breakdown Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("إجمالي المبيعات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("${summary.totalSalesVolume.toInt()} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("ربح النقطة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("${summary.totalAgentCommission.toInt()} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = CyberGreenNeon)
                }
                Column {
                    Text("مستحق التسليم", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text("${summary.totalDueToOwner.toInt()} ر.ي", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("المتبقي بذمته", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Text(
                        text = "${summary.netRemainingDebt.toInt()} ر.ي",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.netRemainingDebt > 0) StatusWarningAmber else StatusActiveGreen
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("كشف الحساب", fontSize = 12.sp)
                }

                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("سند قبض", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (agent.phone.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${agent.phone}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = CyberGreenNeon, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditAgentDialog(
    agent: PosAgentEntity?,
    onDismiss: () -> Unit,
    onSave: (PosAgentEntity) -> Unit
) {
    var name by remember { mutableStateOf(agent?.name.orEmpty()) }
    var phone by remember { mutableStateOf(agent?.phone.orEmpty()) }
    var location by remember { mutableStateOf(agent?.location.orEmpty()) }
    var discountText by remember { mutableStateOf(agent?.discountPercent?.toInt()?.toString() ?: "15") }
    var notes by remember { mutableStateOf(agent?.notes.orEmpty()) }

    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberGreenNeon.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = CyberGreenNeon)
                    }
                    Text(
                        text = if (agent == null) "إضافة نقطة بيع جديدة" else "تعديل بيانات نقطة البيع",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; isError = false },
                    label = { Text("اسم نقطة البيع / البقالة *") },
                    placeholder = { Text("مثال: بقالة ياسين، ميني ماركت السلام") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف / الواتساب") },
                    placeholder = { Text("77xxxxxxx") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = CyberGreenNeon) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("الموقع / الحارة") },
                    placeholder = { Text("مثال: الحارة الشرقية - بجانب المسجد") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = CyberGreenNeon) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("نسبة الخصم / العمولة للبائع (%)") },
                    placeholder = { Text("مثال: 10 أو 15") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, tint = CyberGreenNeon) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إضافية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                isError = true
                                return@Button
                            }
                            val discount = discountText.toDoubleOrNull() ?: 15.0
                            val newAgent = agent?.copy(
                                name = name.trim(),
                                phone = phone.trim(),
                                location = location.trim(),
                                discountPercent = discount,
                                notes = notes.trim()
                            ) ?: PosAgentEntity(
                                name = name.trim(),
                                phone = phone.trim(),
                                location = location.trim(),
                                discountPercent = discount,
                                notes = notes.trim()
                            )
                            onSave(newAgent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black)
                    ) {
                        Text("حفظ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordPaymentDialog(
    agent: PosAgentEntity,
    suggestedAmount: Double,
    onDismiss: () -> Unit,
    onSave: (amount: Double, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf(if (suggestedAmount > 0) suggestedAmount.toInt().toString() else "") }
    var note by remember { mutableStateOf("دفعة نقدية") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StatusActiveGreen.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, tint = StatusActiveGreen)
                    }
                    Column {
                        Text("سند قبض / تحصيل دفعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(agent.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() }; isError = false },
                    label = { Text("المبلغ المستلم (ر.ي) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = StatusActiveGreen) },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("البيان / ملاحظة الدفعة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) {
                                isError = true
                                return@Button
                            }
                            onSave(amt, note)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black)
                    ) {
                        Text("حفظ السند", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AssignCardsDialog(
    agent: PosAgentEntity,
    availableCards: List<HotspotCard>,
    onDismiss: () -> Unit,
    onAssign: (List<HotspotCard>) -> Unit
) {
    var countToAssign by remember { mutableStateOf(minOf(20, availableCards.size).toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("تسليم كروت جاهزة إلى (${agent.name})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "الكروت غير المستخدمة المتوفرة حالياً في السيرفر: ${availableCards.size} كرت",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (availableCards.isEmpty()) {
                    Text(
                        "لا توجد كروت غير مستخدمة متاحة حالياً. يمكنك توليد دفعة كروت جديدة واختيار (${agent.name}) مباشرة أثناء التوليد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarningAmber
                    )
                } else {
                    OutlinedTextField(
                        value = countToAssign,
                        onValueChange = { countToAssign = it.filter { ch -> ch.isDigit() } },
                        label = { Text("عدد الكروت المراد تسليمها") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (availableCards.isNotEmpty()) {
                        Button(
                            onClick = {
                                val count = countToAssign.toIntOrNull() ?: 0
                                if (count > 0) {
                                    val cards = availableCards.take(count)
                                    onAssign(cards)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black)
                        ) {
                            Text("تأكيد التسليم", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentDetailDialog(
    summary: PosAgentSummary,
    onDismiss: () -> Unit,
    onRecordPayment: () -> Unit,
    onAssignCards: () -> Unit,
    onDeletePayment: (Long) -> Unit
) {
    val agent = summary.agent
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Cards, 1: Payments & Ledger, 2: WhatsApp Share

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Column {
                            Text(agent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("كشف الحساب والعمليات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = onRecordPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سند قبض", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الكروت (${summary.totalAssignedCards})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("السندات (${summary.payments.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("مشاركة الكشف 📤", fontSize = 12.sp) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Cards List
                        if (summary.assignedCards.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لم يتم تسليم أي كروت لهذه النقطة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(summary.assignedCards, key = { "${it.id}_${it.cardName}" }) { card ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (card.isSold) StatusActiveGreenBg.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(card.cardName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text("الفئة: ${card.priceAmount.toInt()} ر.ي | ${card.profileName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (card.isSold) StatusActiveGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = if (card.isSold) "تم البيع ✅" else "متبقي معه ⏳",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (card.isSold) StatusActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Payments Ledger
                        if (summary.payments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد سندات قبض مسجلة حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(summary.payments, key = { "${it.id}_${it.paymentDate}" }) { payment ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("${payment.amount.toInt()} ر.ي", fontWeight = FontWeight.Bold, color = StatusActiveGreen)
                                                Text(payment.paymentNote, style = MaterialTheme.typography.labelSmall)
                                                Text(dateFormat.format(Date(payment.paymentDate)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { onDeletePayment(payment.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = StatusErrorRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Share invoice / Statement
                        val reportText = buildString {
                            appendLine("📊 *كشف حساب مبيعات كروت الشبكة* 📊")
                            appendLine("━━━━━━━━━━━━━━━━━━━")
                            appendLine("🏪 *نقطة البيع:* ${agent.name}")
                            if (agent.phone.isNotBlank()) appendLine("📱 *الهاتف:* ${agent.phone}")
                            appendLine("🏷️ *نسبة العمولة:* ${agent.discountPercent.toInt()}%")
                            appendLine("━━━━━━━━━━━━━━━━━━━")
                            appendLine("🎫 *الكروت المسلمة:* ${summary.totalAssignedCards} كرت")
                            appendLine("✅ *الكروت المباعة:* ${summary.soldCardsCount} كرت")
                            appendLine("⏳ *الكروت المتبقية:* ${summary.remainingCardsCount} كرت")
                            appendLine("━━━━━━━━━━━━━━━━━━━")
                            appendLine("💵 *إجمالي قيمة المبيعات:* ${summary.totalSalesVolume.toInt()} ر.ي")
                            appendLine("🎁 *أرباح وعمولة النقطة:* ${summary.totalAgentCommission.toInt()} ر.ي")
                            appendLine("💰 *المستحق لمالك الشبكة:* ${summary.totalDueToOwner.toInt()} ر.ي")
                            appendLine("📥 *إجمالي المسدد والمقبوض:* ${summary.totalPaidByAgent.toInt()} ر.ي")
                            appendLine("⚠️ *المتبقي بذمتكم للتسليم:* ${summary.netRemainingDebt.toInt()} ر.ي")
                            appendLine("━━━━━━━━━━━━━━━━━━━")
                            appendLine("تم التوليد بواسطة تطبيق إدارة الشبكة.")
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = reportText,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "مشاركة كشف الحساب")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة كشف الحساب عبر واتساب", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
