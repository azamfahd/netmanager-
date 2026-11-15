package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SystemEventLogEntity
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemEventLogScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }
    val context = LocalContext.current
    val eventLogs by viewModel.systemEventLogs.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedActionFilter by remember { mutableStateOf("الكل") } // الكل, إضافة, تعديل, حذف, الراوتر, فاشلة
    var showDbManagerDialog by remember { mutableStateOf(false) }

    // Filtered logs
    val filteredLogs = remember(eventLogs, searchQuery, selectedActionFilter) {
        eventLogs.filter { log ->
            val matchesAction = when (selectedActionFilter) {
                "الكل" -> true
                "إضافة" -> log.actionType == "إضافة"
                "تعديل" -> log.actionType == "تعديل"
                "حذف" -> log.actionType == "حذف"
                "الراوتر" -> log.actionType in listOf("إعادة تشغيل", "طرد", "اتصال", "قطع الاتصال")
                "فاشلة" -> log.status == "FAILED"
                else -> true
            }
            val query = searchQuery.trim()
            val matchesQuery = query.isBlank() ||
                log.targetName.contains(query, ignoreCase = true) ||
                log.description.contains(query, ignoreCase = true) ||
                log.details.contains(query, ignoreCase = true) ||
                log.operatorUsername.contains(query, ignoreCase = true) ||
                log.routerIp.contains(query, ignoreCase = true) ||
                log.targetType.contains(query, ignoreCase = true)

            matchesAction && matchesQuery
        }
    }

    // Statistics counts
    val addCount = remember(eventLogs) { eventLogs.count { it.actionType == "إضافة" } }
    val editCount = remember(eventLogs) { eventLogs.count { it.actionType == "تعديل" } }
    val deleteCount = remember(eventLogs) { eventLogs.count { it.actionType == "حذف" } }
    val failedCount = remember(eventLogs) { eventLogs.count { it.status == "FAILED" } }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("system_event_log_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "سجل أحداث النظام والراوتر",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مراقبة وتوثيق عمليات الإضافة والتعديل والحذف (${eventLogs.size} عملية)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("btn_back_event_logs")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDbManagerDialog = true },
                        modifier = Modifier.testTag("btn_db_manager")
                    ) {
                        Icon(Icons.Default.ListAlt, contentDescription = "إدارة وقاعدة البيانات")
                    }
                    IconButton(
                        onClick = { shareLogs(context, eventLogs) },
                        enabled = eventLogs.isNotEmpty(),
                        modifier = Modifier.testTag("btn_share_event_logs")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة السجل")
                    }
                    IconButton(
                        onClick = { shareLogs(context, eventLogs) },
                        enabled = eventLogs.isNotEmpty(),
                        modifier = Modifier.testTag("btn_share_event_logs")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة السجل")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Stats Row Summary
            StatsSummaryRow(
                totalCount = eventLogs.size,
                addCount = addCount,
                editCount = editCount,
                deleteCount = deleteCount,
                failedCount = failedCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("input_search_event_logs"),
                placeholder = { Text("بحث باسم الكرت، الباقة، العملية أو الآيبي...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RouterBlue) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Horizontal Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "الكل" to "الكل (${eventLogs.size})",
                    "إضافة" to "➕ إضافة ($addCount)",
                    "تعديل" to "✏️ تعديل ($editCount)",
                    "حذف" to "🗑️ حذف ($deleteCount)",
                    "الراوتر" to "⚡ الراوتر",
                    "فاشلة" to "❌ فاشلة ($failedCount)"
                )
                items(filters) { (key, label) ->
                    val isSelected = selectedActionFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedActionFilter = key },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (key) {
                                "إضافة" -> StatusActiveGreen.copy(alpha = 0.2f)
                                "حذف" -> StatusErrorRed.copy(alpha = 0.2f)
                                "تعديل" -> RouterBlue.copy(alpha = 0.2f)
                                "فاشلة" -> StatusErrorRed.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = when (key) {
                                "إضافة" -> StatusActiveGreen
                                "حذف" -> StatusErrorRed
                                "تعديل" -> RouterBlue
                                "فاشلة" -> StatusErrorRed
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Events List
            if (filteredLogs.isEmpty()) {
                EmptyLogsPlaceholder(
                    searchQuery = searchQuery,
                    selectedFilter = selectedActionFilter,
                    onResetFilters = {
                        searchQuery = ""
                        selectedActionFilter = "الكل"
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    items(filteredLogs, key = { it.id }) { log ->
                        EventLogCard(
                            log = log,
                            onDelete = { viewModel.deleteSystemEventLog(log.id) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Database Management & Storage Optimization Dialog
    if (showDbManagerDialog) {
        DatabaseManagementDialog(
            viewModel = viewModel,
            onDismiss = { showDbManagerDialog = false }
        )
    }
}

@Composable
private fun StatsSummaryRow(
    totalCount: Int,
    addCount: Int,
    editCount: Int,
    deleteCount: Int,
    failedCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KpiStatItem(label = "الإجمالي", count = totalCount.toString(), color = MaterialTheme.colorScheme.primary)
            KpiStatItem(label = "إضافة", count = "+$addCount", color = StatusActiveGreen)
            KpiStatItem(label = "تعديل", count = "~$editCount", color = RouterBlue)
            KpiStatItem(label = "حذف", count = "-$deleteCount", color = StatusErrorRed)
            if (failedCount > 0) {
                KpiStatItem(label = "فشل", count = "$failedCount", color = Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
private fun KpiStatItem(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EventLogCard(
    log: SystemEventLogEntity,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (actionColor, actionIcon) = when (log.actionType) {
        "إضافة" -> StatusActiveGreen to Icons.Default.AddCircle
        "تعديل" -> RouterBlue to Icons.Default.Edit
        "حذف" -> StatusErrorRed to Icons.Default.Delete
        "إعادة تشغيل" -> Color(0xFF7E57C2) to Icons.Default.PowerSettingsNew
        "طرد" -> Color(0xFFFF9800) to Icons.Default.PersonOff
        else -> MaterialTheme.colorScheme.primary to Icons.Default.Info
    }

    val isFailed = log.status == "FAILED"
    val formattedDate = remember(log.timestamp) {
        SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date(log.timestamp))
    }
    val relativeTime = remember(log.timestamp) {
        getRelativeTimeSpan(log.timestamp)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed) StatusErrorRed.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFailed) StatusErrorRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Action badge, target, status, time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(actionColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = log.actionType,
                            tint = actionColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = actionColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = log.actionType,
                                    color = actionColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (log.targetType.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = log.targetType,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (log.targetName.isNotBlank()) {
                            Text(
                                text = log.targetName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Time and Status
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isFailed) {
                            Icon(Icons.Default.Error, contentDescription = "فشل", tint = StatusErrorRed, modifier = Modifier.size(14.dp))
                            Text("فشل", color = StatusErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = "ناجحة", tint = StatusActiveGreen, modifier = Modifier.size(14.dp))
                            Text("ناجحة", color = StatusActiveGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Human readable description
            Text(
                text = log.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Expandable details section
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DetailRow(label = "وقت وتاريخ العملية:", value = formattedDate)
                    if (log.routerIp.isNotBlank()) {
                        DetailRow(label = "عنوان راوتر ميكروتك:", value = log.routerIp)
                    }
                    DetailRow(label = "المنفّذ (المستخدم الإداري):", value = log.operatorUsername)

                    if (log.details.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تفاصيل ومعاملات العملية:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFailed) StatusErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isFailed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val (rootCause, tip) = diagnoseError(log.details.ifBlank { log.description })
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusErrorRed.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, StatusErrorRed.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Warning, contentDescription = null, tint = StatusErrorRed, modifier = Modifier.size(14.dp))
                                    Text("التشخيص الذكي لسبب الخطأ:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = StatusErrorRed)
                                }
                                Text("السبب الجذري: $rootCause", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("💡 الحل المقترح: $tip", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RouterBlue)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = StatusErrorRed)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف هذا القيد", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Expand/Collapse Chevron indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyLogsPlaceholder(
    searchQuery: String,
    selectedFilter: String,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RouterBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ListAlt,
                contentDescription = null,
                tint = RouterBlue,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (searchQuery.isNotBlank() || selectedFilter != "الكل") "لا توجد نتائج تطابق البحث أو الفلتر" else "لا توجد أحداث مسجلة حتى الآن",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (searchQuery.isNotBlank() || selectedFilter != "الكل")
                "جرب تغيير كلمة البحث أو اختيار تصنيف آخر من الأزرار بالأعلى"
            else
                "ستظهر هنا تلقائياً كافة عمليات إضافة الكروت، تعديل الباقات، الحذف، والتحكم بالراوتر لضمان الشفافية.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (searchQuery.isNotBlank() || selectedFilter != "الكل") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onResetFilters) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إعادة ضبط الفلاتر والبحث")
            }
        }
    }
}

private fun getRelativeTimeSpan(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "الآن"
        minutes < 60 -> "منذ $minutes دقيقة"
        hours < 24 -> "منذ $hours ساعة"
        days == 1L -> "أمس"
        days < 7 -> "منذ $days أيام"
        else -> SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(timestamp))
    }
}

private fun shareLogs(context: Context, logs: List<SystemEventLogEntity>) {
    if (logs.isEmpty()) return
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar"))
    val sb = StringBuilder()
    sb.append("📋 سجل أحداث وعمليات نظام NetManager - MikroTik Router\n")
    sb.append("تاريخ التصدير: ${dateFormat.format(Date())}\n")
    sb.append("إجمالي العمليات: ${logs.size}\n")
    sb.append("=========================================\n\n")

    logs.forEachIndexed { index, log ->
        sb.append("${index + 1}. [${log.actionType}] [${log.status}] ${log.description}\n")
        sb.append("   - الهدف: ${log.targetType} (${log.targetName})\n")
        sb.append("   - التاريخ: ${dateFormat.format(Date(log.timestamp))}\n")
        if (log.routerIp.isNotBlank()) sb.append("   - الراوتر: ${log.routerIp}\n")
        if (log.details.isNotBlank()) sb.append("   - التفاصيل: ${log.details}\n")
        sb.append("\n")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "سجل أحداث نظام ميكروتك")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "تصدير سجل أحداث النظام"))
}

private fun diagnoseError(details: String): Pair<String, String> {
    val lower = details.lowercase()
    return when {
        lower.contains("profile") || lower.contains("not match") ->
            Pair("اسم الباقة أو الملف الشخصي غير موجود أو غير مطابق في راوتر الميكروتك.", "تأكد من إنشاء الباقة بنفس الاسم في قسم 'باقات السرعات والأسعار' قبل محاولة إضافة كروت إليها.")
        lower.contains("unknown parameter") || lower.contains("invalid") ->
            Pair("إرسال معلمات أو بارامترات غير مدعومة من إصدار نظام الراوتر.", "تأكد من توافق إصدار النظام أو عدم إرسال قيم غير صالحة للراوتر.")
        lower.contains("socket") || lower.contains("connect") || lower.contains("timeout") ->
            Pair("فشل الاتصال بسيرفر الميكروتك (انقطاع الشبكة أو خطأ في العنوان/البورت).", "تحقق من اتصال الشبكة، صحة عنوان IP الراوتر، وبورت الـ API.")
        lower.contains("logged in") || lower.contains("permission") || lower.contains("trap") ->
            Pair("رفض الصلاحية أو خطأ في بيانات المصادقة (اسم المستخدم أو كلمة المرور).", "تأكد من صحة بيانات الدخول ومنح حساب الـ API صلاحيات كاملة (Full).")
        lower.contains("already exists") ->
            Pair("العنصر المراد إضافته موجود مسبقاً على الراوتر.", "قم بحذف العنصر القديم أولاً أو استخدام اسم مختلف.")
        else ->
            Pair("حدث خطأ غير متوقع أثناء تنفيذ العملية على الراوتر.", "راجع سجلات الراوتر (Log) وتأكد من حالة الاتصال وصحة البيانات المدخلة.")
    }
}

@Composable
private fun DatabaseManagementDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var dbStats by remember { mutableStateOf<Map<String, Int>?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.getDatabaseStats { stats ->
            dbStats = stats
            isLoadingStats = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("إدارة وتطوير قاعدة البيانات والذاكرة", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "نظام إدارة البيانات الضخمة (High-Volume Data Engine) مصمم للتعامل بكفاءة فورية مع آلاف السجلات والكروت وجلسات المستخدمين.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoadingStats) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (dbStats != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("إحصائيات الجداول المخزنة محلياً:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            dbStats?.forEach { (tableName, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(tableName, style = MaterialTheme.typography.bodyMedium)
                                    Text("$count سجل", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CyberGreenPrimary)
                                }
                            }
                        }
                    }
                }

                if (!actionMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = actionMessage ?: "",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("تأمين وحفظ البيانات:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "يتم حفظ كافة السجلات والعمليات بشكل دائم في قاعدة البيانات المحلية والسحابية (Firestore) لضمان عدم ضياع أي بيانات ولتوفير أرشفة كاملة للنشاط.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberGreenPrimary
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

