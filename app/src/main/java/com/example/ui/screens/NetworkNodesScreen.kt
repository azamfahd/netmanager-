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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.animation.core.*
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
    val selectedNodeStatusFilter by viewModel.nodeStatusFilter.collectAsStateWithLifecycle()
    val isHeartbeatScanning by viewModel.isHeartbeatScanning.collectAsStateWithLifecycle()
    val lastHeartbeatScanTime by viewModel.lastHeartbeatScanTime.collectAsStateWithLifecycle()
    val isClusteringEnabled by viewModel.isClusteringEnabled.collectAsStateWithLifecycle()

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showFullScreenTopology by remember { mutableStateOf(false) }
    var selectedNodeForDetail by remember { mutableStateOf<NetworkNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<NetworkNode?>(null) }
    var nodeToEdit by remember { mutableStateOf<NetworkNode?>(null) }
    var selectedDisplayTab by remember { mutableStateOf(1) }

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
                            // All (الكل)
                            Surface(
                                color = if (selectedNodeStatusFilter == null) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.6.dp, if (selectedNodeStatusFilter == null) Color.White else Color.White.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { viewModel.updateNodeStatusFilter(null) }
                            ) {
                                Text(
                                    text = "الكل (${allNodes.size})",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (selectedNodeStatusFilter == null) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Active (Green)
                            Surface(
                                color = if (selectedNodeStatusFilter == NodeStatus.ACTIVE) Color(0xFF2E7D32).copy(alpha = 0.7f) else Color(0xFF2E7D32).copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.6.dp, if (selectedNodeStatusFilter == NodeStatus.ACTIVE) Color(0xFF81C784) else Color(0xFF4CAF50).copy(alpha = 0.6f)),
                                modifier = Modifier.clickable {
                                    viewModel.updateNodeStatusFilter(if (selectedNodeStatusFilter == NodeStatus.ACTIVE) null else NodeStatus.ACTIVE)
                                }
                            ) {
                                Text(
                                    text = "🟢 $activeCount تعمل",
                                    color = Color(0xFFB9F6CA),
                                    fontSize = 10.sp,
                                    fontWeight = if (selectedNodeStatusFilter == NodeStatus.ACTIVE) FontWeight.ExtraBold else FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // No Internet (Orange/Amber)
                            if (noInternetCount > 0) {
                                Surface(
                                    color = if (selectedNodeStatusFilter == NodeStatus.NO_INTERNET) Color(0xFFEF6C00).copy(alpha = 0.7f) else Color(0xFFEF6C00).copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.6.dp, if (selectedNodeStatusFilter == NodeStatus.NO_INTERNET) Color(0xFFFFB74D) else Color(0xFFFF9800).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable {
                                        viewModel.updateNodeStatusFilter(if (selectedNodeStatusFilter == NodeStatus.NO_INTERNET) null else NodeStatus.NO_INTERNET)
                                    }
                                ) {
                                    Text(
                                        text = "🟠 $noInternetCount بها خلل",
                                        color = Color(0xFFFFE082),
                                        fontSize = 10.sp,
                                        fontWeight = if (selectedNodeStatusFilter == NodeStatus.NO_INTERNET) FontWeight.ExtraBold else FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Offline (Red)
                            if (offlineCount > 0) {
                                Surface(
                                    color = if (selectedNodeStatusFilter == NodeStatus.OFFLINE) Color(0xFFC62828).copy(alpha = 0.7f) else Color(0xFFC62828).copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.6.dp, if (selectedNodeStatusFilter == NodeStatus.OFFLINE) Color(0xFFE57373) else Color(0xFFEF5350).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable {
                                        viewModel.updateNodeStatusFilter(if (selectedNodeStatusFilter == NodeStatus.OFFLINE) null else NodeStatus.OFFLINE)
                                    }
                                ) {
                                    Text(
                                        text = "🔴 $offlineCount مغلقة",
                                        color = Color(0xFFFFCDD2),
                                        fontSize = 10.sp,
                                        fontWeight = if (selectedNodeStatusFilter == NodeStatus.OFFLINE) FontWeight.ExtraBold else FontWeight.Bold,
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

            // Slim Fault Alert Strip
            val faultyNodes = allNodes.filter { it.hasIssue }
            if (faultyNodes.isNotEmpty() && selectedNodeStatusFilter != NodeStatus.NO_INTERNET) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateNodeStatusFilter(NodeStatus.NO_INTERNET) },
                    shape = RoundedCornerShape(10.dp),
                    color = StatusErrorRedBg,
                    border = BorderStroke(0.8.dp, StatusErrorRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusErrorRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "تنبيه المالك: توجد (${faultyNodes.size}) نقاط بها أعطال أو انقطاع نت!",
                                fontWeight = FontWeight.Bold,
                                color = StatusErrorRed,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            color = StatusErrorRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "معاينة 🔍",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = StatusErrorRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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
                        Triple(1, "📋 قائمة النقاط والأبراج", Icons.Default.CellTower),
                        Triple(3, "🌳 شجرة الأبراج والفروع", Icons.Default.AccountTree),
                        Triple(0, "🌐 المخطط السيبراني", Icons.Default.Dns),
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
                        val isFilterActive = selectedNodeStatusFilter != null || selectedNodeTypeFilter != null || searchQuery.isNotBlank()

                        val faultyGroup = remember(displayedNodes) { displayedNodes.filter { it.hasIssue || it.effectiveStatus == NodeStatus.NO_INTERNET } }
                        val towersGroup = remember(displayedNodes) { displayedNodes.filter { it.nodeType == NetworkNodeType.MAIN_TOWER && !it.hasIssue && it.effectiveStatus != NodeStatus.OFFLINE } }
                        val distGroup = remember(displayedNodes) { displayedNodes.filter { it.nodeType == NetworkNodeType.DISTRIBUTION && !it.hasIssue && it.effectiveStatus != NodeStatus.OFFLINE } }
                        val homeGroup = remember(displayedNodes) { displayedNodes.filter { it.nodeType == NetworkNodeType.HOME_NODE && !it.hasIssue && it.effectiveStatus != NodeStatus.OFFLINE } }
                        val offlineGroup = remember(displayedNodes) { displayedNodes.filter { it.effectiveStatus == NodeStatus.OFFLINE } }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isFilterActive) {
                                // Filter Banner
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "نتائج التصفية والبحث: (${displayedNodes.size}) نقطة",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            TextButton(
                                                onClick = {
                                                    viewModel.updateNodeStatusFilter(null)
                                                    viewModel.updateNodeTypeFilter(null)
                                                    viewModel.updateNodeSearch("")
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("إلغاء التصفية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                items(displayedNodes, key = { it.id }) { node ->
                                    NetworkNodeItem(
                                        node = node,
                                        onDetailClick = { selectedNodeForDetail = node },
                                        onPing = { viewModel.pingNetworkNode(node.id) },
                                        onEdit = { nodeToEdit = node },
                                        onDelete = { nodeToDelete = node }
                                    )
                                }
                            } else {
                                // 1. Section: Faulty / Issues (إذا وجدت)
                                if (faultyGroup.isNotEmpty()) {
                                    item {
                                        Surface(
                                            color = StatusErrorRedBg,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.8.dp, StatusErrorRed.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusErrorRed, modifier = Modifier.size(16.dp))
                                                Text("⚠️ نقاط بها أعطال أو انقطاع نت (${faultyGroup.size})", fontWeight = FontWeight.Bold, color = StatusErrorRed, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    items(faultyGroup, key = { "faulty_${it.id}" }) { node ->
                                        NetworkNodeItem(
                                            node = node,
                                            onDetailClick = { selectedNodeForDetail = node },
                                            onPing = { viewModel.pingNetworkNode(node.id) },
                                            onEdit = { nodeToEdit = node },
                                            onDelete = { nodeToDelete = node }
                                        )
                                    }
                                }

                                // 2. Section: Main Towers & Hubs (الأبراج الرئيسية)
                                if (towersGroup.isNotEmpty()) {
                                    item {
                                        Surface(
                                            color = RouterBlue.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.8.dp, RouterBlue.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.CellTower, contentDescription = null, tint = RouterBlue, modifier = Modifier.size(16.dp))
                                                Text("🗼 الأبراج والمحطات المركزية (${towersGroup.size})", fontWeight = FontWeight.Bold, color = RouterBlue, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    items(towersGroup, key = { "tower_${it.id}" }) { node ->
                                        NetworkNodeItem(
                                            node = node,
                                            onDetailClick = { selectedNodeForDetail = node },
                                            onPing = { viewModel.pingNetworkNode(node.id) },
                                            onEdit = { nodeToEdit = node },
                                            onDelete = { nodeToDelete = node }
                                        )
                                    }
                                }

                                // 3. Section: Active Distribution Access Points (نقاط البث والتوزيع)
                                if (distGroup.isNotEmpty()) {
                                    item {
                                        Surface(
                                            color = StatusActiveGreen.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.8.dp, StatusActiveGreen.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = StatusActiveGreen, modifier = Modifier.size(16.dp))
                                                Text("📡 نقاط البث والتوزيع الفعالة (${distGroup.size})", fontWeight = FontWeight.Bold, color = StatusActiveGreen, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    items(distGroup, key = { "dist_${it.id}" }) { node ->
                                        NetworkNodeItem(
                                            node = node,
                                            onDetailClick = { selectedNodeForDetail = node },
                                            onPing = { viewModel.pingNetworkNode(node.id) },
                                            onEdit = { nodeToEdit = node },
                                            onDelete = { nodeToDelete = node }
                                        )
                                    }
                                }

                                // 4. Section: Home Modems & Subscriber Branches (الموادم المنزلية والفروع)
                                if (homeGroup.isNotEmpty()) {
                                    item {
                                        Surface(
                                            color = Color(0xFFE65100).copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.8.dp, Color(0xFFE65100).copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                                Text("🏠 الموادم والفروع المنزلية (${homeGroup.size})", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    items(homeGroup, key = { "home_${it.id}" }) { node ->
                                        NetworkNodeItem(
                                            node = node,
                                            onDetailClick = { selectedNodeForDetail = node },
                                            onPing = { viewModel.pingNetworkNode(node.id) },
                                            onEdit = { nodeToEdit = node },
                                            onDelete = { nodeToDelete = node }
                                        )
                                    }
                                }

                                // 5. Section: Offline Nodes (النقاط المغلقة)
                                if (offlineGroup.isNotEmpty()) {
                                    item {
                                        Surface(
                                            color = Color(0xFFC62828).copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.8.dp, Color(0xFFC62828).copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                                Text("🔴 نقاط مغلقة / متوقفة عن البث (${offlineGroup.size})", fontWeight = FontWeight.Bold, color = Color(0xFFC62828), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    items(offlineGroup, key = { "offline_${it.id}" }) { node ->
                                        NetworkNodeItem(
                                            node = node,
                                            onDetailClick = { selectedNodeForDetail = node },
                                            onPing = { viewModel.pingNetworkNode(node.id) },
                                            onEdit = { nodeToEdit = node },
                                            onDelete = { nodeToDelete = node }
                                        )
                                    }
                                }
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

    // Edit Node Dialog
    nodeToEdit?.let { targetNode ->
        EditNodeDialog(
            node = targetNode,
            onDismiss = { nodeToEdit = null },
            onConfirm = { updatedNode ->
                viewModel.updateNetworkNode(updatedNode)
                nodeToEdit = null
            }
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semanticIcon = when (node.nodeType) {
        NetworkNodeType.MAIN_TOWER -> Icons.Default.CellTower
        NetworkNodeType.DISTRIBUTION -> Icons.Default.WifiTethering
        NetworkNodeType.HOME_NODE -> Icons.Default.Home
    }
    val statusColor = Color(node.statusColorRgb)
    val statusBgColor = statusColor.copy(alpha = 0.12f)
    val isOffline = node.effectiveStatus == NodeStatus.OFFLINE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClick)
            .testTag("network_node_card_${node.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOffline) Color(0xFFD32F2F).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.8.dp,
            if (isOffline) Color(0xFFD32F2F).copy(alpha = 0.4f) else statusColor.copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Icon + Name & Subtitle + Status Badge + Actions (Edit/Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = semanticIcon,
                            contentDescription = node.nodeType.titleAr,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Surface(
                                color = when (node.nodeType) {
                                    NetworkNodeType.MAIN_TOWER -> Color(0xFF1565C0).copy(alpha = 0.12f)
                                    NetworkNodeType.DISTRIBUTION -> Color(0xFF00897B).copy(alpha = 0.12f)
                                    NetworkNodeType.HOME_NODE -> Color(0xFFE65100).copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (node.nodeType == NetworkNodeType.HOME_NODE) "منزلي" else node.nodeType.titleAr,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (node.nodeType) {
                                        NetworkNodeType.MAIN_TOWER -> Color(0xFF0D47A1)
                                        NetworkNodeType.DISTRIBUTION -> Color(0xFF004D40)
                                        NetworkNodeType.HOME_NODE -> Color(0xFFBF360C)
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = buildString {
                                append(node.ipAddress)
                                if (node.interfaceName.isNotBlank()) append(" • ${node.interfaceName}")
                                if (node.vlanId != null && node.vlanId > 0) append(" • VLAN ${node.vlanId}")
                                if (!node.parentInterface.isNullOrBlank()) append(" (عبر: ${node.parentInterface})")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        color = statusBgColor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.6.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = if (node.effectiveStatus == NodeStatus.ACTIVE) "${node.pingMs}ms" else node.statusTitleAr,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل النقطة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف النقطة",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Row 2: Metrics (Users, Speed, Signal) & Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${node.connectedUsers} متصل",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (node.rxSpeedKbps + node.txSpeedKbps > 0) {
                        Text(
                            text = "⬇${NetworkUtils.formatSpeed(node.rxSpeedKbps)} ⬆${NetworkUtils.formatSpeed(node.txSpeedKbps)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (node.signalDbm.isNotBlank() && node.signalDbm != "-58 dBm") {
                        Text(
                            text = "📶 ${node.signalDbm}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onPing() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkPing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "فحص",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onDetailClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "التفاصيل",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Issue Warning banner (if hasIssue)
            if (node.hasIssue) {
                Surface(
                    color = statusBgColor,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = node.issueDescription,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
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
fun EditNodeDialog(
    node: NetworkNode,
    onDismiss: () -> Unit,
    onConfirm: (NetworkNode) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var ipAddress by remember { mutableStateOf(node.ipAddress) }
    var model by remember { mutableStateOf(node.model) }
    var role by remember { mutableStateOf(node.role) }
    var location by remember { mutableStateOf(node.location) }
    var interfaceName by remember { mutableStateOf(node.interfaceName) }
    var selectedNodeType by remember { mutableStateOf(node.nodeType) }
    var parentTowerName by remember { mutableStateOf(node.parentTowerName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("تعديل بيانات نقطة الشبكة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("عنوان IP النقطة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = parentTowerName,
                    onValueChange = { parentTowerName = it },
                    label = { Text("اسم البرج التابعة له") },
                    placeholder = { Text("البرج الرئيسي") },
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
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = interfaceName,
                        onValueChange = { interfaceName = it },
                        label = { Text("المنفذ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("الدور / الوظيفة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("الموقع الجغرافي / المربع") },
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
                        val updated = node.copy(
                            name = name.trim(),
                            ipAddress = ipAddress.trim(),
                            model = model.trim(),
                            role = role.trim(),
                            location = location.trim(),
                            interfaceName = interfaceName.trim().ifBlank { node.interfaceName },
                            nodeType = selectedNodeType,
                            parentTowerName = parentTowerName.trim().ifBlank { null }
                        )
                        onConfirm(updated)
                    }
                },
                enabled = name.isNotBlank() && ipAddress.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ التعديلات")
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
    // 1. Build the tree structure from the flat list
    val treeNodes = remember(nodes) {
        val nodeMap = nodes.associateBy { it.id }
        // Create secondary lookup by interfaceName for matching parentInterface
        val ifaceToNodeMap = nodes.associateBy { it.interfaceName.lowercase() }

        // Find children grouped by resolved parent node ID
        val childrenMap = mutableMapOf<String, MutableList<NetworkNode>>()
        val rootNodes = mutableListOf<NetworkNode>()

        nodes.forEach { node ->
            // Try resolving parent ID via parentTowerId, then via parentInterface
            val directParentId = node.parentTowerId?.takeIf { nodeMap.containsKey(it) }
            val interfaceParentId = if (directParentId == null && !node.parentInterface.isNullOrBlank()) {
                val cleanParentIf = node.parentInterface!!.lowercase()
                nodeMap["node_if_$cleanParentIf"]?.id ?: ifaceToNodeMap[cleanParentIf]?.id
            } else null

            val effectiveParentId = directParentId ?: interfaceParentId

            if (effectiveParentId != null && effectiveParentId != node.id) {
                childrenMap.getOrPut(effectiveParentId) { mutableListOf() }.add(node)
            } else {
                rootNodes.add(node)
            }
        }
        
        rootNodes.map { root -> buildTreeNode(root, childrenMap) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Root Router Header
        item {
            RootRouterHeader(centralRouterName, centralRouterIp, nodes.size)
        }

        // Recursive Tree Items
        treeNodes.forEach { root ->
            renderRecursiveTree(root, 0, onNodeClick, onPingNode, onKickUser)
        }
        
        if (treeNodes.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد بيانات لبناء الشجرة الهيكلية", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

data class TreeNode(
    val node: NetworkNode,
    val children: List<TreeNode>
)

private fun buildTreeNode(node: NetworkNode, childrenMap: Map<String, List<NetworkNode>>): TreeNode {
    val children = childrenMap[node.id]?.map { buildTreeNode(it, childrenMap) } ?: emptyList()
    return TreeNode(node, children)
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderRecursiveTree(
    treeNode: TreeNode,
    level: Int,
    onNodeClick: (NetworkNode) -> Unit,
    onPingNode: (String) -> Unit,
    onKickUser: (HotspotActiveUser) -> Unit
) {
    item(key = treeNode.node.id) {
        RecursiveNodeTreeItem(
            treeNode = treeNode,
            level = level,
            onNodeClick = { onNodeClick(treeNode.node) },
            onPing = { onPingNode(treeNode.node.id) }
        )
    }

    if (treeNode.children.isNotEmpty()) {
        treeNode.children.forEach { child ->
            renderRecursiveTree(child, level + 1, onNodeClick, onPingNode, onKickUser)
        }
    }
}

@Composable
fun RecursiveNodeTreeItem(
    treeNode: TreeNode,
    level: Int,
    onNodeClick: () -> Unit,
    onPing: () -> Unit
) {
    val node = treeNode.node
    val isOffline = node.effectiveStatus == NodeStatus.OFFLINE
    
    // Pulsing Red Glow for Offline Nodes
    val infiniteTransition = rememberInfiniteTransition(label = "offline_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val paddingStart = (level * 20).dp
    val statusColor = if (isOffline) Color(0xFFD32F2F) else Color(node.statusColorRgb)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .clickable(onClick = onNodeClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOffline) Color(0xFFD32F2F).copy(alpha = glowAlpha) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (level == 0) 1.dp else 0.5.dp,
            color = statusColor.copy(alpha = if (level == 0) 0.4f else 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connector Line (Visual guide for tree)
            if (level > 0) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Icon
            Box(
                modifier = Modifier
                    .size(if (level == 0) 42.dp else 36.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (node.nodeType) {
                        NetworkNodeType.MAIN_TOWER -> Icons.Default.CellTower
                        NetworkNodeType.DISTRIBUTION -> Icons.Default.WifiTethering
                        else -> Icons.Default.Home
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(if (level == 0) 22.dp else 18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = node.name,
                        fontWeight = if (level == 0) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = if (level == 0) 14.sp else 13.sp,
                        color = if (isOffline) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (isOffline) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Red))
                    }
                    if (node.vlanId != null && node.vlanId > 0) {
                        Surface(
                            color = RouterBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VLAN ${node.vlanId}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = RouterBlue,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                val parentPortInfo = if (!node.parentInterface.isNullOrBlank()) " • عبر: ${node.parentInterface}" else ""
                Text(
                    text = "${node.ipAddress} • ${node.connectedUsers} مشترك$parentPortInfo",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.isOnline) {
                    IconButton(onClick = onPing, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.NetworkPing, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onNodeClick, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
                if (treeNode.children.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RootRouterHeader(name: String, ip: String, totalNodes: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Router, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text = "IP: $ip • إجمالي النقاط المسجلة: $totalNodes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}
