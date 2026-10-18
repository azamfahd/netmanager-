package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Sort
import com.example.data.model.NetworkNode
import com.example.data.model.NodeSortOption
import com.example.data.network.NetworkUtils
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.FullScreenTopologyDialog
import com.example.ui.components.NodeDetailDialog
import com.example.ui.components.SpiderwebTopologyGraph
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusType
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusErrorRedBg
import com.example.ui.viewmodel.MainViewModel

@Composable
fun NetworkNodesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val nodes by viewModel.filteredNetworkNodes.collectAsStateWithLifecycle()
    val allNodes by viewModel.networkNodes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.nodeSearchQuery.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.nodeSortOption.collectAsStateWithLifecycle()

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showFullScreenTopology by remember { mutableStateOf(false) }
    var selectedNodeForDetail by remember { mutableStateOf<NetworkNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<NetworkNode?>(null) }
    var selectedDisplayTab by remember { mutableStateOf(0) }

    val totalOnline = allNodes.count { it.isOnline }
    val totalUsersOnNodes = allNodes.sumOf { it.connectedUsers }

    val displayedNodes = nodes

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Ultra-Compact Sleek Network Health Status Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RouterBlue
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0D47A1),
                                    Color(0xFF1565C0),
                                    Color(0xFF00695C)
                                )
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
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
                                imageVector = Icons.Default.CellTower,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "حالة الأبراج: $totalOnline / ${allNodes.size} متصل",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color(0xFFB9F6CA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$totalUsersOnNodes مشترك",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                color = if (allNodes.any { it.hasIssue }) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color(0xFFB9F6CA).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (allNodes.any { it.hasIssue }) "تحت المتابعة" else "مستقرة 100%",
                                    color = if (allNodes.any { it.hasIssue }) Color(0xFFFFD54F) else Color(0xFFB9F6CA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Fault Alerts Banner for Owner
            val faultyNodes = allNodes.filter { it.hasIssue }
            if (faultyNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusErrorRedBg),
                    border = BorderStroke(1.dp, StatusErrorRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StatusErrorRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🚨 تنبيه المالك: توجد (${faultyNodes.size}) نقاط بها أعطال أو خلل!",
                                fontWeight = FontWeight.Bold,
                                color = StatusErrorRed,
                                fontSize = 13.sp
                            )
                            Text(
                                text = faultyNodes.joinToString(" • ") { "${it.name}: ${it.issueDescription}" },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }

                        TextButton(
                            onClick = { viewModel.updateNodeSortOption(NodeSortOption.FAULTY_FIRST) }
                        ) {
                            Text("معاينة", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StatusErrorRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. View Mode Tabs (Spiderweb Graph / List View / Load Analytics)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "🌐 المخطط السيبراني", Icons.Default.Dns),
                        Triple(1, "📋 قائمة النقاط", Icons.Default.CellTower),
                        Triple(2, "⚡ إحصائيات الضغط", Icons.Default.Speed)
                    )

                    tabs.forEach { (index, title, icon) ->
                        val isSelected = selectedDisplayTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDisplayTab = index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Compact Search & Add Node Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateNodeSearch(it) },
                    placeholder = { Text("بحث باسم النقطة، IP...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateNodeSearch("") }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(12.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )

                Button(
                    onClick = { showAddNodeDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("btn_add_network_node_top")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "إضافة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Sorting & Filter Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Sorting Options Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الترتيب حسب:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NodeSortOption.entries.forEach { option ->
                        FilterChip(
                            selected = currentSortOption == option,
                            onClick = { viewModel.updateNodeSortOption(option) },
                            label = { Text(option.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Dynamic Content View depending on selectedDisplayTab
            when (selectedDisplayTab) {
                0 -> { // 🕸️ Spiderweb Topology Canvas
                    if (displayedNodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد نقاط مطابقة لعرض الشبكة العنكبوتية", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                val currentConfigState = viewModel.currentConfig.collectAsStateWithLifecycle()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🌐 خريطة وتخطيط الشبكة السيبرانية (${displayedNodes.size} نقطة)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Button(
                                        onClick = { showFullScreenTopology = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RouterBlue,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInFull,
                                            contentDescription = "تكبير ملء الشاشة",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "تكبير ملء الشاشة ⛶",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                SpiderwebTopologyGraph(
                                    centralRouterName = "الراوتر الرئيسي (سيرفر الشبكة)",
                                    centralRouterIp = currentConfigState.value.ipAddress,
                                    nodes = displayedNodes,
                                    onNodeClick = { selectedNodeForDetail = it },
                                    onToggleFullScreen = { showFullScreenTopology = true }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
                1 -> { // 📋 Detailed List Cards
                    if (displayedNodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "لا توجد نقاط تطابق البحث" else "لا توجد نقاط مضافة حالياً",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showAddNodeDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إضافة نقطة / أكسس بوينت جديد")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedNodes, key = { it.id }) { node ->
                                NetworkNodeItem(
                                    node = node,
                                    onDetailClick = { selectedNodeForDetail = node },
                                    onPing = { viewModel.pingNetworkNode(node.id) },
                                    onDelete = { nodeToDelete = node }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
                2 -> { // ⚡ Top Load & Heavy Traffic Analytics View
                    val highestLoadNode = displayedNodes.maxByOrNull { it.cpuLoadPercent }
                    val highestUsersNode = displayedNodes.maxByOrNull { it.connectedUsers }
                    val highestBandwidthNode = displayedNodes.maxByOrNull { it.rxSpeedKbps + it.txSpeedKbps }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text(
                                text = "🔥 تحليلات الضغط والاستهلاك الأعلى في الشبكة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Top Load Card
                        highestLoadNode?.let { node ->
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedNodeForDetail = node },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                Text("النقطة الأكثر ضغطاً (الأعلى حملاً)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(8.dp)) {
                                                Text("${node.cpuLoadPercent}% ضغط", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text("${node.name} (${node.ipAddress})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("الموقع: ${node.location} • عدد المستخدمين: ${node.connectedUsers} • وقت التشغيل: ${node.uptime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        LinearProgressIndicator(
                                            progress = { (node.cpuLoadPercent / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        // Top Users Card
                        highestUsersNode?.let { node ->
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedNodeForDetail = node },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Text("النقطة الأعلى كثافة مستخدمين", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
                                                Text("${node.connectedUsers} مستخدم", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text("${node.name} (${node.ipAddress})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("نوع التغطية: ${node.role} • التردد: ${node.frequency}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        // Top Speed & Bandwidth Card
                        highestBandwidthNode?.let { node ->
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedNodeForDetail = node },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                                Text("النقطة الأعلى سعة ونقل بيانات", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            Surface(color = MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(8.dp)) {
                                                Text("${node.rxSpeedKbps.toInt()} Kbps / ${node.txSpeedKbps.toInt()} Kbps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text("${node.name} (${node.ipAddress})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("قوة الإشارة: ${node.signalDbm} • وقت التشغيل: ${node.uptime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    // Add Node Dialog
    if (showAddNodeDialog) {
        AddNodeDialog(
            onDismiss = { showAddNodeDialog = false },
            onConfirm = { newNode ->
                viewModel.addNetworkNode(newNode)
                showAddNodeDialog = false
            }
        )
    }

    // Delete Node Confirmation Dialog
    nodeToDelete?.let { node ->
        ConfirmDialog(
            title = "حذف نقطة الشبكة",
            message = "هل أنت متأكد من رغبتك في حذف نقطة (${node.name} - ${node.ipAddress}) من قائمة المراقبة؟",
            confirmText = "حذف النقطة",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteNetworkNode(node.id)
                nodeToDelete = null
            },
            onDismiss = { nodeToDelete = null }
        )
    }

    // Node Detail & Connected Devices Dialog
    selectedNodeForDetail?.let { targetNode ->
        NodeDetailDialog(
            node = targetNode,
            onDismiss = { selectedNodeForDetail = null },
            onPingNode = { viewModel.pingNetworkNode(targetNode.id) },
            onRebootNode = {
                viewModel.rebootRouter()
            }
        )
    }

    // Full Screen Topology View Dialog
    if (showFullScreenTopology) {
        val currentConfigState = viewModel.currentConfig.collectAsStateWithLifecycle()
        FullScreenTopologyDialog(
            centralRouterName = "الراوتر الرئيسي (سيرفر الشبكة)",
            centralRouterIp = currentConfigState.value.ipAddress,
            nodes = allNodes,
            onDismiss = { showFullScreenTopology = false },
            onNodeClick = { selectedNodeForDetail = it }
        )
    }
}

@Composable
fun NetworkNodeItem(
    node: NetworkNode,
    onDetailClick: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClick)
            .testTag("network_node_card_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Role & Status Pill
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (node.isOnline) StatusActiveGreenBg else StatusErrorRedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (node.role.contains("Starlink")) Icons.Default.WifiTethering else Icons.Default.CellTower,
                            contentDescription = null,
                            tint = if (node.isOnline) StatusActiveGreen else StatusErrorRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${node.ipAddress} • ${node.interfaceName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(
                        text = if (node.isOnline) "متصل (${node.pingMs}ms)" else "غير متصل",
                        type = if (node.isOnline) StatusType.SUCCESS else StatusType.ERROR
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف النقطة",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // شريط التحكم في الشبكة (Network Quick Control Bar)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${node.connectedUsers} مشترك",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = if (node.isOnline) "⚡ استجابة ${node.pingMs}ms" else "⚠️ غير مستجيب",
                            fontSize = 10.sp,
                            color = if (node.isOnline) StatusActiveGreen else StatusErrorRed
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = onPing,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("فحص Ping", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onDetailClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("المشتركين", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Issue / Fault Warning Banner
            if (node.hasIssue) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = StatusErrorRedBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, StatusErrorRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "تنبيه الخلل: ${node.issueDescription}",
                            color = StatusErrorRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // IP, Interface & Location
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "IP: ${node.ipAddress}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "(${node.interfaceName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = node.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Row: Connected Users, Signal, Uptime, Live Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Users badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "${node.connectedUsers} مستخدم متصل",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Signal
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusActiveGreen)
                    Text(
                        text = node.signalDbm,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Uptime
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = node.uptime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Speed Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سرعة المرور الحالية:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "⬇ ${NetworkUtils.formatSpeed(node.rxSpeedKbps)}  •  ⬆ ${NetworkUtils.formatSpeed(node.txSpeedKbps)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Preview of connected devices on this node
            if (node.connectedClients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable { onDetailClick() }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "الأجهزة المتصلة بهذه النقطة (${node.connectedClients.size}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            node.connectedClients.take(5).forEach { client ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "${client.cardName} (${client.deviceInfo.modelName})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (node.connectedClients.size > 5) {
                                Text(
                                    text = "+${node.connectedClients.size - 5} أجهزة أخرى",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button: View Details & Connected Devices
            OutlinedButton(
                onClick = onDetailClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RouterBlue),
                border = BorderStroke(1.dp, RouterBlue.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("view_node_details_btn_${node.id}")
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تفاصيل النقطة والمستعملين المتصلين (${node.connectedUsers})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddNodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (NetworkNode) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("192.168.88.") }
    var model by remember { mutableStateOf("MikroTik cAP ac") }
    var role by remember { mutableStateOf("أكسس بوينت (بث)") }
    var location by remember { mutableStateOf("البرج الرئيسي") }
    var interfaceName by remember { mutableStateOf("ether2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("إضافة نقطة / أكسس بوينت جديد", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم النقطة / البرج") },
                    placeholder = { Text("مثال: أكسس بوينت الحارة الجنوبية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("عنوان IP النقطة") },
                    placeholder = { Text("192.168.88.6") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("نوع الجهاز") },
                        placeholder = { Text("BaseBox / cAP / SXT") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = interfaceName,
                        onValueChange = { interfaceName = it },
                        label = { Text("المنفذ") },
                        placeholder = { Text("ether2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("الدور / الوظيفة") },
                    placeholder = { Text("أكسس بوينت بث / ربط لاسلكي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("الموقع الجغرافي / المربع") },
                    placeholder = { Text("المربع الجنوبي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ipAddress.isNotBlank()) {
                        val node = NetworkNode(
                            id = "node_${System.currentTimeMillis()}",
                            name = name.trim(),
                            ipAddress = ipAddress.trim(),
                            interfaceName = interfaceName.ifBlank { "ether2" },
                            model = model.ifBlank { "MikroTik AP" },
                            role = role.ifBlank { "أكسس بوينت" },
                            location = location.ifBlank { "الموقع الرئيسي" },
                            isOnline = true,
                            pingMs = 3L,
                            connectedUsers = 0,
                            uptime = "1h",
                            rxSpeedKbps = 1200.0,
                            txSpeedKbps = 400.0,
                            signalDbm = "-60 dBm"
                        )
                        onConfirm(node)
                    }
                },
                enabled = name.isNotBlank() && ipAddress.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إضافة النقطة وتفعيل المراقبة")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إلغاء")
            }
        }
    )
}
