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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.AccountTree
import com.example.ui.components.NodeClientItemCard
import com.example.data.model.HotspotActiveUser
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.model.NetworkNode
import com.example.data.model.NetworkNodeType
import com.example.data.model.NodeSortOption
import com.example.data.model.NodeStatus
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
    val selectedNodeTypeFilter by viewModel.nodeTypeFilter.collectAsStateWithLifecycle()
    val isHeartbeatScanning by viewModel.isHeartbeatScanning.collectAsStateWithLifecycle()
    val lastHeartbeatScanTime by viewModel.lastHeartbeatScanTime.collectAsStateWithLifecycle()
    val isClusteringEnabled by viewModel.isClusteringEnabled.collectAsStateWithLifecycle()

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showFullScreenTopology by remember { mutableStateOf(false) }
    var selectedNodeForDetail by remember { mutableStateOf<NetworkNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<NetworkNode?>(null) }
    var selectedDisplayTab by remember { mutableStateOf(0) }

    val activeCount = allNodes.count { it.effectiveStatus == NodeStatus.ACTIVE }
    val offlineCount = allNodes.count { it.effectiveStatus == NodeStatus.OFFLINE }
    val noInternetCount = allNodes.count { it.effectiveStatus == NodeStatus.NO_INTERNET }
    val totalUsersOnNodes = allNodes.sumOf { it.connectedUsers }

    val towersCount = allNodes.count { it.nodeType == NetworkNodeType.MAIN_TOWER }
    val distCount = allNodes.count { it.nodeType == NetworkNodeType.DISTRIBUTION }
    val homeCount = allNodes.count { it.nodeType == NetworkNodeType.HOME_NODE }

    val displayedNodes = nodes

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Ultra-Compact Sleek Network Health Status Bar with Heartbeat Mechanism
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RouterBlue
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "نبضات القلب",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "مراقبة نبضات القلب (Heartbeat)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Heartbeat Check Action Button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isHeartbeatScanning) { viewModel.runHeartbeatCheck() }
                                .testTag("btn_trigger_heartbeat"),
                            color = if (isHeartbeatScanning) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isHeartbeatScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "جارِ الفحص...",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "فحص فوري",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Status counts row with Material 3 4-color indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Active (Green)
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF4CAF50).copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "🟢 $activeCount متصل",
                                    color = Color(0xFFB9F6CA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // No Internet (Orange/Amber)
                            if (noInternetCount > 0) {
                                Surface(
                                    color = Color(0xFFEF6C00).copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFFF9800).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "🟠 $noInternetCount مقطوع نت",
                                        color = Color(0xFFFFE082),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Offline (Red)
                            if (offlineCount > 0) {
                                Surface(
                                    color = Color(0xFFC62828).copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFEF5350).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "🔴 $offlineCount مغلق",
                                        color = Color(0xFFFFCDD2),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Last Heartbeat Time
                        Text(
                            text = if (lastHeartbeatScanTime != null) "آخر فحص: $lastHeartbeatScanTime" else "$totalUsersOnNodes مشترك",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
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

            // 2. View Mode Tabs (Spiderweb Graph / Tree Hierarchy / List View / Load Analytics)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "🌐 المخطط السيبراني", Icons.Default.Dns),
                        Triple(3, "🌳 شجرة الأبراج والفروع", Icons.Default.AccountTree),
                        Triple(1, "📋 قائمة النقاط", Icons.Default.CellTower),
                        Triple(2, "⚡ إحصائيات الضغط", Icons.Default.Speed)
                    )

                    tabs.forEach { (index, title, icon) ->
                        val isSelected = selectedDisplayTab == index
                        Surface(
                            modifier = Modifier
                                .clickable { selectedDisplayTab = index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
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
                // Category Classification Chips (أبراج، نقاط توزيع، موادم منزلية)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedNodeTypeFilter == null,
                        onClick = { viewModel.updateNodeTypeFilter(null) },
                        label = { Text("الكل (${allNodes.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    FilterChip(
                        selected = selectedNodeTypeFilter == NetworkNodeType.MAIN_TOWER,
                        onClick = { viewModel.updateNodeTypeFilter(if (selectedNodeTypeFilter == NetworkNodeType.MAIN_TOWER) null else NetworkNodeType.MAIN_TOWER) },
                        label = { Text("🗼 أبراج رئيسية ($towersCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    FilterChip(
                        selected = selectedNodeTypeFilter == NetworkNodeType.DISTRIBUTION,
                        onClick = { viewModel.updateNodeTypeFilter(if (selectedNodeTypeFilter == NetworkNodeType.DISTRIBUTION) null else NetworkNodeType.DISTRIBUTION) },
                        label = { Text("📡 نقاط توزيع ($distCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    FilterChip(
                        selected = selectedNodeTypeFilter == NetworkNodeType.HOME_NODE,
                        onClick = { viewModel.updateNodeTypeFilter(if (selectedNodeTypeFilter == NetworkNodeType.HOME_NODE) null else NetworkNodeType.HOME_NODE) },
                        label = { Text("🏠 موادم منزلية وفروع ($homeCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }

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
                3 -> { // 🌳 شجرة الأبراج والفروع الهيكلية والمشتركين
                    val currentConfigState = viewModel.currentConfig.collectAsStateWithLifecycle()
                    HierarchicalNetworkTreeView(
                        nodes = displayedNodes,
                        centralRouterName = "الراوتر المركزي (${currentConfigState.value.name.ifBlank { "سيرفر الشبكة" }})",
                        centralRouterIp = currentConfigState.value.ipAddress,
                        onNodeClick = { selectedNodeForDetail = it },
                        onPingNode = { viewModel.pingNetworkNode(it) },
                        onKickUser = { user -> viewModel.kickUser(user) }
                    )
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
    val semanticIcon = when (node.nodeType) {
        NetworkNodeType.MAIN_TOWER -> Icons.Default.CellTower
        NetworkNodeType.DISTRIBUTION -> Icons.Default.WifiTethering
        NetworkNodeType.HOME_NODE -> Icons.Default.Home
    }
    val statusColor = Color(node.statusColorRgb)
    val statusBgColor = statusColor.copy(alpha = 0.14f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClick)
            .testTag("network_node_card_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Semantic Icon, Name, Type Badge & 4-Color Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = semanticIcon,
                            contentDescription = node.nodeType.titleAr,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            // Node Type Pill (برج رئيسي، نقطة توزيع، مودم منزلي)
                            Surface(
                                color = when (node.nodeType) {
                                    NetworkNodeType.MAIN_TOWER -> Color(0xFF1565C0).copy(alpha = 0.15f)
                                    NetworkNodeType.DISTRIBUTION -> Color(0xFF00897B).copy(alpha = 0.15f)
                                    NetworkNodeType.HOME_NODE -> Color(0xFFE65100).copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (node.nodeType == NetworkNodeType.HOME_NODE) "مودم منزلي / فرع" else node.nodeType.titleAr,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (node.nodeType) {
                                        NetworkNodeType.MAIN_TOWER -> Color(0xFF0D47A1)
                                        NetworkNodeType.DISTRIBUTION -> Color(0xFF004D40)
                                        NetworkNodeType.HOME_NODE -> Color(0xFFBF360C)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // IP and Parent Tower if applicable
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${node.ipAddress} • ${node.interfaceName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (node.nodeType == NetworkNodeType.HOME_NODE && (!node.parentTowerName.isNullOrBlank() || !node.parentTowerId.isNullOrBlank())) {
                                Text(
                                    text = "• تابعة لـ: ${node.parentTowerName ?: node.parentTowerId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00897B),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Device Model and MAC Address
                        if (node.model.isNotBlank()) {
                            Text(
                                text = "الجهاز: ${node.model}${if (node.macAddress.isNotBlank()) " • MAC: ${node.macAddress}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Unified Material 3 4-Color Status Pill
                    Surface(
                        color = statusBgColor,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = if (node.effectiveStatus == NodeStatus.ACTIVE) "${node.statusTitleAr} (${node.pingMs}ms)" else node.statusTitleAr,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

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

            // Quick Network & Heartbeat Control Bar
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
                            text = if (node.lastCheckTime != null) "🕒 نبض: ${node.lastCheckTime}" else if (node.isOnline) "⚡ استجابة ${node.pingMs}ms" else "⚠️ غير مستجيب",
                            fontSize = 10.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
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
                    color = statusBgColor,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f)),
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
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "تنبيه النقطة: ${node.issueDescription}",
                            color = statusColor,
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
    var selectedNodeType by remember { mutableStateOf(NetworkNodeType.DISTRIBUTION) }
    var parentTowerName by remember { mutableStateOf("البرج الرئيسي") }

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
                // Node Type Selection
                Text("نوع النقطة الدلالي:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NetworkNodeType.entries.forEach { type ->
                        val isSelected = selectedNodeType == type
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedNodeType = type },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        ) {
                            Text(
                                text = type.titleAr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

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

                if (selectedNodeType == NetworkNodeType.HOME_NODE) {
                    OutlinedTextField(
                        value = parentTowerName,
                        onValueChange = { parentTowerName = it },
                        label = { Text("اسم البرج التابعة له (للتجميع الذكي)") },
                        placeholder = { Text("البرج الرئيسي / برج الشرق") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

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
                            nodeType = selectedNodeType,
                            parentTowerId = if (selectedNodeType == NetworkNodeType.HOME_NODE) "node_ap_main" else null,
                            parentTowerName = if (selectedNodeType == NetworkNodeType.HOME_NODE) parentTowerName.ifBlank { "البرج الرئيسي" } else null,
                            status = NodeStatus.ACTIVE,
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

@Composable
fun HierarchicalNetworkTreeView(
    nodes: List<NetworkNode>,
    centralRouterName: String,
    centralRouterIp: String,
    onNodeClick: (NetworkNode) -> Unit,
    onPingNode: (String) -> Unit,
    onKickUser: (HotspotActiveUser) -> Unit
) {
    var userToKickConfirm by remember { mutableStateOf<Pair<String, String>?>(null) } // (id, username)

    // Identify Root Main Towers
    val mainTowers = remember(nodes) {
        val towers = nodes.filter { it.nodeType == NetworkNodeType.MAIN_TOWER }
        if (towers.isNotEmpty()) {
            towers
        } else {
            val roots = nodes.filter {
                it.parentTowerId.isNullOrBlank() &&
                it.parentTowerName.isNullOrBlank() &&
                it.nodeType != NetworkNodeType.HOME_NODE
            }
            if (roots.isNotEmpty()) roots else nodes.take(1)
        }
    }

    val assignedTowerIds = mainTowers.map { it.id }.toSet()
    val assignedTowerNames = mainTowers.map { it.name.lowercase().trim() }.toSet()

    // Standalone nodes not under any main tower
    val standaloneNodes = remember(nodes, mainTowers) {
        nodes.filter { node ->
            !assignedTowerIds.contains(node.id) &&
            node.parentTowerId.isNullOrBlank() &&
            (node.parentTowerName.isNullOrBlank() || !assignedTowerNames.contains(node.parentTowerName!!.lowercase().trim())) &&
            node.nodeType != NetworkNodeType.HOME_NODE
        }
    }

    val totalConnectedClients = nodes.sumOf { it.connectedClients.size.coerceAtLeast(it.connectedUsers) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Central Server Root Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Router,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = centralRouterName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "IP: $centralRouterIp • جذر وهيكل الشبكة الشجري",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$totalConnectedClients متصل كلي",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Network Hierarchy Overview Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF1565C0))
                                Text("${mainTowers.size} أبراج رئيسية", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00897B))
                                Text("${nodes.count { it.nodeType == NetworkNodeType.DISTRIBUTION }} نقاط توزيع", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFE65100))
                                Text("${nodes.count { it.nodeType == NetworkNodeType.HOME_NODE }} فروع وموادم", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Main Towers and their branches
        items(mainTowers, key = { it.id }) { tower ->
            val childDistributionNodes = remember(nodes, tower) {
                nodes.filter {
                    it.nodeType == NetworkNodeType.DISTRIBUTION &&
                    (it.parentTowerId == tower.id || it.parentTowerName.equals(tower.name, ignoreCase = true) || it.parentTowerName.equals(tower.id, ignoreCase = true))
                }
            }
            val childHomeNodes = remember(nodes, tower) {
                nodes.filter {
                    it.nodeType == NetworkNodeType.HOME_NODE &&
                    (it.parentTowerId == tower.id || it.parentTowerName.equals(tower.name, ignoreCase = true))
                }
            }

            TowerTreeNodeCard(
                tower = tower,
                childDistributions = childDistributionNodes,
                childHomeNodes = childHomeNodes,
                allNodes = nodes,
                onNodeClick = onNodeClick,
                onPingNode = onPingNode,
                onRequestKickUser = { id, user -> userToKickConfirm = id to user }
            )
        }

        // 3. Standalone / Additional Distribution Points
        if (standaloneNodes.isNotEmpty()) {
            item {
                Text(
                    text = "📡 نقاط ومحطات إرسال إضافية (${standaloneNodes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(standaloneNodes, key = { it.id }) { node ->
                GenericTreeNodeCard(
                    node = node,
                    onNodeClick = onNodeClick,
                    onPingNode = onPingNode,
                    onRequestKickUser = { id, user -> userToKickConfirm = id to user }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Kick User Confirmation Dialog
    userToKickConfirm?.let { (uId, uName) ->
        AlertDialog(
            onDismissRequest = { userToKickConfirm = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = StatusErrorRed)
                    Text("قطع اتصال المشترك", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("هل أنت متأكد من رغبتك في قطع اتصال وطرد المستخدم ($uName) من النقطة والراوتر فوراً؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onKickUser(HotspotActiveUser(id = uId, user = uName, address = "", macAddress = "", uptime = "", bytesIn = 0L, bytesOut = 0L))
                        userToKickConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("قطع الاتصال", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { userToKickConfirm = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun TowerTreeNodeCard(
    tower: NetworkNode,
    childDistributions: List<NetworkNode>,
    childHomeNodes: List<NetworkNode>,
    allNodes: List<NetworkNode>,
    onNodeClick: (NetworkNode) -> Unit,
    onPingNode: (String) -> Unit,
    onRequestKickUser: (String, String) -> Unit
) {
    var isBranchesExpanded by remember { mutableStateOf(true) }
    var isDirectUsersExpanded by remember { mutableStateOf(false) }

    val statusColor = Color(tower.statusColorRgb)
    val statusBgColor = statusColor.copy(alpha = 0.12f)
    val towerClients = tower.connectedClients

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, Color(0xFF1565C0).copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tower Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1565C0),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CellTower, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tower.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(color = Color(0xFF1565C0).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                Text("برج رئيسي", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text("${tower.ipAddress} • ${tower.interfaceName} • ${tower.model}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = statusBgColor, shape = RoundedCornerShape(6.dp), border = BorderStroke(0.6.dp, statusColor.copy(alpha = 0.5f))) {
                        Text(tower.statusTitleAr, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                    IconButton(onClick = { isBranchesExpanded = !isBranchesExpanded }, modifier = Modifier.size(28.dp)) {
                        Icon(if (isBranchesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }

            // Tower Quick Metrics Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡ ${tower.pingMs}ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                        Text("📶 ${tower.signalDbm}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("🕒 ${tower.uptime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { onPingNode(tower.id) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Ping", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { onNodeClick(tower) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("تفاصيل", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Direct Tower Users Toggle Button
            Surface(
                color = if (isDirectUsersExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDirectUsersExpanded = !isDirectUsersExpanded }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "المستخدمين المتصلين بالبرج مباشرة (${towerClients.size} مستخدم)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(if (isDirectUsersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            // Direct Users Accordion List
            AnimatedVisibility(visible = isDirectUsersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (towerClients.isEmpty()) {
                        Text(
                            text = "لا يوجد مستخدمين متصلين مباشرة بهذا البرج حالياً",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        towerClients.forEach { client ->
                            TreeNodeUserCard(
                                client = client,
                                onKickClick = { onRequestKickUser(client.id, client.cardName) }
                            )
                        }
                    }
                }
            }

            // Expanded Branches Tree Section
            AnimatedVisibility(visible = isBranchesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (childDistributions.isEmpty() && childHomeNodes.isEmpty()) {
                        Text(
                            text = "ℹ️ لم يتم ربط نقاط توزيع فرعية بهذا البرج بعد. يمكنك تحديد اسم البرج عند إضافة أي نقطة توزيع.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    } else {
                        // Render child distribution APs
                        childDistributions.forEach { ap ->
                            val apHomeNodes = remember(allNodes, ap) {
                                allNodes.filter {
                                    it.nodeType == NetworkNodeType.HOME_NODE &&
                                    (it.parentTowerId == ap.id || it.parentTowerName.equals(ap.name, ignoreCase = true))
                                }
                            }
                            BranchApTreeNodeCard(
                                ap = ap,
                                childHomeNodes = apHomeNodes,
                                onNodeClick = onNodeClick,
                                onPingNode = onPingNode,
                                onRequestKickUser = onRequestKickUser
                            )
                        }

                        // Render direct child home nodes
                        childHomeNodes.forEach { homeNode ->
                            HomeNodeTreeItem(
                                node = homeNode,
                                onNodeClick = onNodeClick,
                                onPingNode = onPingNode,
                                onRequestKickUser = onRequestKickUser
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BranchApTreeNodeCard(
    ap: NetworkNode,
    childHomeNodes: List<NetworkNode>,
    onNodeClick: (NetworkNode) -> Unit,
    onPingNode: (String) -> Unit,
    onRequestKickUser: (String, String) -> Unit
) {
    var isApUsersExpanded by remember { mutableStateOf(false) }
    var isHomeNodesExpanded by remember { mutableStateOf(true) }
    val statusColor = Color(ap.statusColorRgb)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, Color(0xFF00897B).copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF00897B), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(ap.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Surface(color = Color(0xFF00897B).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("نقطة توزيع (AP)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF004D40), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text("${ap.ipAddress} • ${ap.interfaceName}${if (ap.ssid.isNotBlank()) " • بث: ${ap.ssid}" else ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${ap.connectedClients.size.coerceAtLeast(ap.connectedUsers)} مشترك", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                    TextButton(onClick = { onNodeClick(ap) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("عرض", fontSize = 10.sp)
                    }
                }
            }

            // AP Users toggle button
            Surface(
                color = if (isApUsersExpanded) Color(0xFF00897B).copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.6.dp, Color(0xFF00897B).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isApUsersExpanded = !isApUsersExpanded }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👥 المشتركين المتصلين بالنقطة (${ap.connectedClients.size})", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF004D40))
                    Icon(if (isApUsersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            AnimatedVisibility(visible = isApUsersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (ap.connectedClients.isEmpty()) {
                        Text("لا يوجد مستخدمين مسجلين على هذه النقطة حالياً", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 6.dp))
                    } else {
                        ap.connectedClients.forEach { client ->
                            TreeNodeUserCard(client = client, onKickClick = { onRequestKickUser(client.id, client.cardName) })
                        }
                    }
                }
            }

            // Render Child Home Nodes under this AP
            if (childHomeNodes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    childHomeNodes.forEach { homeNode ->
                        HomeNodeTreeItem(
                            node = homeNode,
                            onNodeClick = onNodeClick,
                            onPingNode = onPingNode,
                            onRequestKickUser = onRequestKickUser
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeNodeTreeItem(
    node: NetworkNode,
    onNodeClick: (NetworkNode) -> Unit,
    onPingNode: (String) -> Unit,
    onRequestKickUser: (String, String) -> Unit
) {
    var isUsersExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, Color(0xFFE65100).copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(shape = CircleShape, color = Color(0xFFE65100), modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Column {
                        Text(node.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${node.ipAddress} • ${node.model}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { isUsersExpanded = !isUsersExpanded },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(22.dp)
                    ) {
                        Text("${node.connectedClients.size.coerceAtLeast(node.connectedUsers)} مشترك", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    IconButton(onClick = { onNodeClick(node) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(13.dp))
                    }
                }
            }

            AnimatedVisibility(visible = isUsersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (node.connectedClients.isEmpty()) {
                        Text("لا يوجد مستخدمين متصلين بهذا المودم حالياً", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        node.connectedClients.forEach { client ->
                            TreeNodeUserCard(client = client, onKickClick = { onRequestKickUser(client.id, client.cardName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenericTreeNodeCard(
    node: NetworkNode,
    onNodeClick: (NetworkNode) -> Unit,
    onPingNode: (String) -> Unit,
    onRequestKickUser: (String, String) -> Unit
) {
    var isUsersExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Column {
                        Text(node.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${node.ipAddress} • ${node.interfaceName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { isUsersExpanded = !isUsersExpanded }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("${node.connectedClients.size.coerceAtLeast(node.connectedUsers)} مشترك", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { onNodeClick(node) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }

            AnimatedVisibility(visible = isUsersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (node.connectedClients.isEmpty()) {
                        Text("لا يوجد مستخدمين مسجلين على هذه النقطة حالياً", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        node.connectedClients.forEach { client ->
                            TreeNodeUserCard(client = client, onKickClick = { onRequestKickUser(client.id, client.cardName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TreeNodeUserCard(
    client: com.example.data.model.NodeConnectedClient,
    onKickClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = client.cardName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        if (client.deviceInfo.brand.isNotBlank() && client.deviceInfo.brand != "غير معروف") {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${client.deviceInfo.brand} ${client.deviceInfo.modelName}".trim(),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${client.ipAddress} • MAC: ${client.macAddress}${if (client.uptime.isNotBlank()) " • وقت: ${client.uptime}" else ""}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onKickClick,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = "قطع الاتصال",
                    tint = StatusErrorRed,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
