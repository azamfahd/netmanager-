package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.NetworkNode
import com.example.ui.theme.CyberGreenGlow
import com.example.ui.theme.CyberGreenNeon
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.NetworkCyanLight
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusErrorRed
import com.example.ui.theme.StatusWarningAmber
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-Screen Network Topology Dialog.
 * Expands across the entire phone display for professional, high-density visualization
 * of all routers, towers, and access points, with collision-free multi-orbit layout.
 */
@Composable
fun FullScreenTopologyDialog(
    centralRouterName: String,
    centralRouterIp: String,
    nodes: List<NetworkNode>,
    onDismiss: () -> Unit,
    onNodeClick: (NetworkNode) -> Unit
) {
    BackHandler { onDismiss() }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var spacingScale by remember { mutableFloatStateOf(1.15f) }
    var cardScale by remember { mutableFloatStateOf(1.0f) }
    var isCompactMode by remember { mutableStateOf(nodes.size > 12) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "fullscreen_pulse")

    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    val centralGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "central_glow"
    )

    val centralGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "central_glow_alpha"
    )

    val localDensity = LocalDensity.current

    val filteredNodes = remember(nodes, searchQuery) {
        if (searchQuery.isBlank()) nodes else {
            nodes.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.ipAddress.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true) ||
                it.interfaceName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val satelliteNodes = remember(filteredNodes) {
        filteredNodes
            .filter { it.id != "node_main_router" && !it.role.contains("Gateway", ignoreCase = true) && !it.role.contains("الرئيسي للشبكة", ignoreCase = true) }
            .distinctBy { it.id.ifBlank { it.ipAddress } }
    }

    val totalOnline = nodes.count { it.isOnline }
    val totalUsers = nodes.sumOf { it.connectedUsers }

    // Partition nodes into multi-ring concentric orbits
    val ringNodes = remember(satelliteNodes) {
        val list = mutableListOf<List<NetworkNode>>()
        var index = 0
        val capacities = listOf(6, 10, 14, 18, 22, 26, 30)
        var capIdx = 0
        while (index < satelliteNodes.size) {
            val cap = capacities.getOrElse(capIdx) { 24 }
            val end = (index + cap).coerceAtMost(satelliteNodes.size)
            list.add(satelliteNodes.subList(index, end))
            index = end
            capIdx++
        }
        list
    }

    val ringRadiiPx = remember(ringNodes, spacingScale, cardScale, isCompactMode, localDensity) {
        val cardWidthDp = if (isCompactMode) 80.dp else 110.dp
        val cardHeightDp = if (isCompactMode) 40.dp else 74.dp
        val cardWidthPx = with(localDensity) { (cardWidthDp * cardScale).toPx() }
        val cardHeightPx = with(localDensity) { (cardHeightDp * cardScale).toPx() }
        val cardDiagPx = kotlin.math.sqrt(cardWidthPx * cardWidthPx + cardHeightPx * cardHeightPx)
        val centerNodeRadiusPx = with(localDensity) { 56.dp.toPx() }
        val minGapPx = with(localDensity) { 32.dp.toPx() }

        val minRing0RadiusPx = centerNodeRadiusPx + (cardDiagPx / 2f) + minGapPx

        val radii = mutableListOf<Float>()
        var prevRadiusPx = 0f

        ringNodes.forEachIndexed { ringIdx, ringGroup ->
            val count = ringGroup.size
            val minArcDistancePx = cardDiagPx + minGapPx
            val requiredArcRadiusPx = if (count > 1) {
                (minArcDistancePx / (2 * sin(Math.PI / count))).toFloat()
            } else {
                minRing0RadiusPx
            }

            val baseRadiusPx = if (ringIdx == 0) {
                maxOf(minRing0RadiusPx, requiredArcRadiusPx)
            } else {
                maxOf(prevRadiusPx + cardDiagPx + minGapPx, requiredArcRadiusPx)
            }

            val finalRadius = baseRadiusPx * spacingScale
            radii.add(finalRadius)
            prevRadiusPx = finalRadius
        }
        radii
    }

    val nodeRelPositions = remember(satelliteNodes, ringNodes, ringRadiiPx) {
        val positions = mutableListOf<Offset>()
        ringNodes.forEachIndexed { ringIdx, ringGroup ->
            val ringRadius = ringRadiiPx.getOrElse(ringIdx) { 220f }
            val count = ringGroup.size
            val angleStep = (2 * Math.PI) / count
            ringGroup.forEachIndexed { posInRing, _ ->
                val startAngle = -Math.PI / 2 + (ringIdx * 0.35)
                val angle = posInRing * angleStep + startAngle
                val x = ringRadius * cos(angle).toFloat()
                val y = ringRadius * sin(angle).toFloat()
                positions.add(Offset(x, y))
            }
        }
        positions
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0B1F16),
                            Color(0xFF06130D),
                            Color(0xFF020704)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.25f, 3.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                .testTag("fullscreen_topology_view")
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            // 1. Cyber Canvas: Rings, Spokes, Energy Pulses, Grid
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = panOffsetX
                        translationY = panOffsetY
                        scaleX = zoomScale
                        scaleY = zoomScale
                    }
            ) {
                val centerOffset = Offset(widthPx / 2f, heightPx / 2f)

                // Background radar web rings
                ringRadiiPx.forEach { radiusPx ->
                    drawCircle(
                        color = CyberGreenNeon.copy(alpha = 0.14f),
                        radius = radiusPx,
                        center = centerOffset,
                        style = Stroke(
                            width = 1.8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )
                }

                // Inter-node ring connecting mesh
                var globalIdx = 0
                ringNodes.forEach { ringGroup ->
                    val count = ringGroup.size
                    if (count > 1) {
                        for (i in 0 until count) {
                            val currPos = nodeRelPositions.getOrNull(globalIdx + i)
                            val nextPos = nodeRelPositions.getOrNull(globalIdx + ((i + 1) % count))
                            if (currPos != null && nextPos != null) {
                                drawLine(
                                    color = NetworkCyan.copy(alpha = 0.22f),
                                    start = centerOffset + currPos,
                                    end = centerOffset + nextPos,
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                )
                            }
                        }
                    }
                    globalIdx += count
                }

                // Spoke lines and energy flow dots
                nodeRelPositions.forEachIndexed { index, nodeRelOffset ->
                    val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed
                    val absNodeOffset = centerOffset + nodeRelOffset

                    val lineColor = when {
                        !node.isOnline -> StatusErrorRed.copy(alpha = 0.7f)
                        node.hasIssue -> StatusWarningAmber.copy(alpha = 0.9f)
                        node.cpuLoadPercent > 75 -> StatusWarningAmber.copy(alpha = 0.9f)
                        else -> CyberGreenNeon.copy(alpha = 0.7f)
                    }

                    drawLine(
                        color = lineColor,
                        start = centerOffset,
                        end = absNodeOffset,
                        strokeWidth = if (node.hasIssue) 2.8f else 2.0f,
                        cap = StrokeCap.Round
                    )

                    if (node.isOnline) {
                        val pulseX = centerOffset.x + nodeRelOffset.x * pulseProgress
                        val pulseY = centerOffset.y + nodeRelOffset.y * pulseProgress
                        val pulseOffset = Offset(pulseX, pulseY)

                        drawCircle(
                            color = if (node.hasIssue) StatusWarningAmber.copy(alpha = 0.7f) else CyberGreenGlow.copy(alpha = 0.8f),
                            radius = 8f,
                            center = pulseOffset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = pulseOffset
                        )

                        val revProgress = (pulseProgress + 0.5f) % 1.0f
                        val revX = centerOffset.x + nodeRelOffset.x * revProgress
                        val revY = centerOffset.y + nodeRelOffset.y * revProgress
                        drawCircle(
                            color = NetworkCyanLight.copy(alpha = 0.7f),
                            radius = 5f,
                            center = Offset(revX, revY)
                        )
                    }
                }

                // Central Router glow ring
                drawCircle(
                    color = CyberGreenNeon.copy(alpha = centralGlowAlpha),
                    radius = 58f * centralGlowScale,
                    center = centerOffset
                )
            }

            // 2. Central Router Node
            val centerDpX = with(localDensity) { panOffsetX.toDp() }
            val centerDpY = with(localDensity) { panOffsetY.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = centerDpX, y = centerDpY)
                    .shadow(20.dp, CircleShape, spotColor = CyberGreenNeon)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF004426),
                                Color(0xFF002213)
                            )
                        )
                    )
                    .border(2.5.dp, CyberGreenNeon, CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CyberGreenNeon.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "السيرفر الرئيسي",
                            tint = CyberGreenNeon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = centralRouterName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = centralRouterIp,
                        color = CyberGreenNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 3. Radial Sub-Nodes (Towers / APs)
            nodeRelPositions.forEachIndexed { index, relOffset ->
                val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed

                val nodeDpX = with(localDensity) { (relOffset.x * zoomScale + panOffsetX).toDp() }
                val nodeDpY = with(localDensity) { (relOffset.y * zoomScale + panOffsetY).toDp() }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = nodeDpX, y = nodeDpY)
                        .graphicsLayer {
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                ) {
                    if (isCompactMode) {
                        FullScreenCompactNodeBadge(
                            node = node,
                            onClick = { onNodeClick(node) }
                        )
                    } else {
                        FullScreenDetailedNodeCard(
                            node = node,
                            onClick = { onNodeClick(node) }
                        )
                    }
                }
            }

            // 4. Top Header & Title Bar with Exit and Search
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
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
                                    .clip(CircleShape)
                                    .background(CyberGreenNeon.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = CyberGreenNeon,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "خريطة وأبراج الشبكة كاملة (شاشة كاملة)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$totalOnline / ${nodes.size} متصل • $totalUsers مستخدم على النقاط",
                                    color = CyberGreenNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { isSearchExpanded = !isSearchExpanded },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "بحث في النقاط",
                                    tint = if (isSearchExpanded) CyberGreenNeon else Color.White
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .clickable { onDismiss() }
                                    .clip(RoundedCornerShape(10.dp)),
                                color = StatusErrorRed.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, StatusErrorRed.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloseFullscreen,
                                        contentDescription = "تصغير الشاشة",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "تصغير",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Optional Search Bar
                    AnimatedVisibility(visible = isSearchExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("بحث عن برج أو نقطة باسمها أو IP أو المنفذ...", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.White)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberGreenNeon,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 5. Right HUD Floating Toolbar (+, -, Auto-Fit, Reset, Spacing, Mode)
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.18f).coerceAtMost(3.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        color = CyberGreenNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )

                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.18f).coerceAtLeast(0.25f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(22.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))

                    // Auto-Fit to Screen
                    IconButton(
                        onClick = {
                            val maxRadiusPx = ringRadiiPx.lastOrNull() ?: 220f
                            val maxExtentPx = maxRadiusPx + with(localDensity) { 80.dp.toPx() }
                            val availableRadiusPx = minOf(widthPx, heightPx) / 2f - with(localDensity) { 40.dp.toPx() }
                            val optimalScale = (availableRadiusPx / maxExtentPx).coerceIn(0.25f, 2.0f)
                            zoomScale = optimalScale
                            panOffsetX = 0f
                            panOffsetY = 0f
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "احتواء الشاشة تلقائياً", tint = NetworkCyanLight, modifier = Modifier.size(18.dp))
                    }

                    // Reset Center
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0f
                            panOffsetY = 0f
                            spacingScale = 1.15f
                            cardScale = 1.0f
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة التوسيط", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(22.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))

                    // Increase Card Size (تكبير حجم البطاقات)
                    IconButton(
                        onClick = { cardScale = (cardScale + 0.15f).coerceAtMost(2.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ViewModule, contentDescription = "تكبير حجم البطاقات", tint = NetworkCyanLight, modifier = Modifier.size(18.dp))
                    }

                    // Decrease Card Size (تصغير حجم البطاقات)
                    IconButton(
                        onClick = { cardScale = (cardScale - 0.15f).coerceAtLeast(0.6f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.FormatSize, contentDescription = "تصغير حجم البطاقات", tint = NetworkCyanLight, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(22.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))

                    // Spacing Expansion
                    IconButton(
                        onClick = { spacingScale = (spacingScale + 0.15f).coerceAtMost(2.4f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.UnfoldMore, contentDescription = "زيادة التباعد", tint = CyberGreenNeon, modifier = Modifier.size(18.dp))
                    }

                    // Spacing Compaction
                    IconButton(
                        onClick = { spacingScale = (spacingScale - 0.15f).coerceAtLeast(0.6f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.UnfoldLess, contentDescription = "تقليص التباعد", tint = CyberGreenNeon, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(22.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))

                    // Toggle Compact / Detailed
                    IconButton(
                        onClick = { isCompactMode = !isCompactMode },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "تبديل التفاصيل", tint = if (isCompactMode) StatusWarningAmber else CyberGreenNeon, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 6. Bottom Status & Gesture Tip Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(0.6.dp, CyberGreenNeon.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💡 اسحب للتحريك • اقرص بأصبعين للتقريب • اضغط على أي نقطة لعرض مستخدميها",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CyberGreenNeon))
                            Text("متصل", color = Color.White, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusWarningAmber))
                            Text("ضغط", color = Color.White, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusErrorRed))
                            Text("عطل", color = Color.White, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenDetailedNodeCard(
    node: NetworkNode,
    onClick: () -> Unit
) {
    val borderColor = when {
        !node.isOnline -> StatusErrorRed
        node.hasIssue -> StatusWarningAmber
        node.cpuLoadPercent > 75 -> StatusWarningAmber
        else -> CyberGreenNeon
    }

    val iconVector = when {
        node.role.contains("برج", ignoreCase = true) -> Icons.Default.CellTower
        node.role.contains("Starlink", ignoreCase = true) -> Icons.Default.WifiTethering
        node.role.contains("لاسلكي", ignoreCase = true) -> Icons.Default.Wifi
        else -> Icons.Default.Router
    }

    Card(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick)
            .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = borderColor)
            .testTag("fs_node_item_${node.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091711)),
        border = BorderStroke(1.4.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Header Row: Status dot, Role icon & CPU %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (node.isOnline) StatusActiveGreen else StatusErrorRed)
                )

                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(16.dp)
                )

                Surface(
                    color = borderColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${node.cpuLoadPercent}%",
                        color = borderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            // Node Name
            Text(
                text = node.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1
            )

            // IP Address
            Text(
                text = node.ipAddress,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )

            // Connected Users Highlight Badge (CRITICAL FOR USER REQUIREMENT)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = if (node.connectedUsers > 0) CyberGreenNeon.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.6.dp, if (node.connectedUsers > 0) CyberGreenNeon.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = if (node.connectedUsers > 0) CyberGreenNeon else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${node.connectedUsers} مستخدم",
                        color = if (node.connectedUsers > 0) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenCompactNodeBadge(
    node: NetworkNode,
    onClick: () -> Unit
) {
    val borderColor = when {
        !node.isOnline -> StatusErrorRed
        node.hasIssue -> StatusWarningAmber
        else -> CyberGreenNeon
    }

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = borderColor)
            .testTag("fs_compact_node_${node.id}"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF07150E),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (node.isOnline) StatusActiveGreen else StatusErrorRed)
            )

            Text(
                text = node.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                maxLines = 1
            )

            Surface(
                shape = CircleShape,
                color = CyberGreenNeon.copy(alpha = 0.3f)
            ) {
                Text(
                    text = "${node.connectedUsers}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}
