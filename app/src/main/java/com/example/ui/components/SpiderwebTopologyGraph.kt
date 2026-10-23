package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 6 Diverse Cybernetic Network Topology Visual Layout Styles:
 * 1. HIERARCHICAL_TREE (شجرة هرمية متسلسلة)
 * 2. ORBITAL_SPIDERWEB (عنكبوتي مداري)
 * 3. HEXAGONAL_MESH (مصفوفة سيبرانية سداسية)
 * 4. CYBER_MATRIX_GRID (مصفوفة دوائر رقمية)
 * 5. RADIAL_CONSTELLATION (كوكبة عقدية نجمية)
 * 6. DUAL_RING_TOKEN (حلقة ألياف ضوئية مزدوجة)
 */
enum class NetworkTopologyLayout(val title: String, val icon: ImageVector, val desc: String) {
    HIERARCHICAL_TREE("شجرة هرمية", Icons.Default.AccountTree, "تسلسل هرمي من الراوتر الرئيسي إلى الأبراج والمحطات"),
    ORBITAL_SPIDERWEB("عنكبوتي مداري", Icons.Default.Dns, "حلقات مدارية مركزية مع أشعة كهرومغناطيسية"),
    HEXAGONAL_MESH("مصفوفة سداسية", Icons.Default.Layers, "خلايا نحل مع مسح رادار حي 360°"),
    CYBER_MATRIX_GRID("دوائر رقمية", Icons.Default.DeveloperBoard, "مسارات دوائر إلكترونية متوازية ذات زوايا قائمة"),
    RADIAL_CONSTELLATION("كوكبة نجمية", Icons.Default.Flare, "تجمعات نجمية متصلة بروابط ضوئية ذكية"),
    DUAL_RING_TOKEN("حلقة مزدوجة", Icons.Default.Sync, "حلقتان متوازيتان لتدفق البيانات وتكرار المسارات")
}

/**
 * Filter mode for nodes displayed inside the topology canvas
 */
enum class TopologyFilterMode(val label: String) {
    ALL("الكل"),
    ONLINE_ONLY("المتصلة"),
    ISSUES_ONLY("بها مشاكل")
}

/**
 * Advanced Multi-Style Cybernetic Network Topology Graph
 *
 * Fully responsive to small screen interfaces with auto-fit, collapsible
 * dropdown control bar, dynamic density scaling, and 6 cybernetic layouts.
 */
@Composable
fun SpiderwebTopologyGraph(
    centralRouterName: String,
    centralRouterIp: String,
    nodes: List<NetworkNode>,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFullScreen: (() -> Unit)? = null
) {
    var currentLayout by remember(nodes.size) {
        mutableStateOf(if (nodes.size > 8) NetworkTopologyLayout.HIERARCHICAL_TREE else NetworkTopologyLayout.HIERARCHICAL_TREE)
    }
    var isControlsExpanded by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(TopologyFilterMode.ALL) }
    var isCompactHeight by remember { mutableStateOf(true) } // Default small view height for mobile
    var isClusteringActive by remember { mutableStateOf(true) }
    var selectedClusterTower by remember { mutableStateOf<NetworkNode?>(null) }

    // Map parent tower ID to its child home nodes
    val towerChildNodesMap = remember(nodes) {
        val map = mutableMapOf<String, MutableList<NetworkNode>>()
        nodes.filter { it.nodeType == com.example.data.model.NetworkNodeType.HOME_NODE }.forEach { homeNode ->
            val pId = homeNode.parentTowerId ?: "node_ap_main"
            map.getOrPut(pId) { mutableListOf() }.add(homeNode)
        }
        map
    }

    // Filter nodes according to internal quick status filter
    val rawFilteredNodes = remember(nodes, filterMode) {
        when (filterMode) {
            TopologyFilterMode.ALL -> nodes
            TopologyFilterMode.ONLINE_ONLY -> nodes.filter { it.isOnline }
            TopologyFilterMode.ISSUES_ONLY -> nodes.filter { it.hasIssue || !it.isOnline }
        }
    }

    // Filter out central router from satellites; in clustering mode, hide clustered home nodes to prevent clutter
    val satelliteNodes = remember(rawFilteredNodes, isClusteringActive) {
        val nonCentral = rawFilteredNodes
            .filter { it.id != "node_main_router" && !it.role.contains("Gateway", ignoreCase = true) && !it.role.contains("الرئيسي للشبكة", ignoreCase = true) }
            .distinctBy { it.id.ifBlank { it.ipAddress } }
        if (isClusteringActive) {
            nonCentral.filter { it.nodeType != com.example.data.model.NetworkNodeType.HOME_NODE || it.parentTowerId == null }
        } else {
            nonCentral
        }
    }

    // Adaptive default initial scale for small screens
    val defaultInitialZoom = remember(satelliteNodes.size, currentLayout) {
        when {
            satelliteNodes.size <= 3 -> 0.95f
            satelliteNodes.size <= 6 -> 0.82f
            satelliteNodes.size <= 10 -> 0.68f
            satelliteNodes.size <= 16 -> 0.54f
            else -> 0.44f
        }
    }

    var zoomScale by remember { mutableFloatStateOf(defaultInitialZoom) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var spacingScale by remember(satelliteNodes.size) {
        mutableFloatStateOf(if (satelliteNodes.size > 8) 0.80f else 1.0f)
    }
    var cardScale by remember(satelliteNodes.size) {
        mutableFloatStateOf(if (satelliteNodes.size > 10) 0.85f else 1.0f)
    }
    var isCompactMode by remember(satelliteNodes.size) { mutableStateOf(satelliteNodes.size > 6) }

    val infiniteTransition = rememberInfiniteTransition(label = "cyber_pulse")

    // Animated energy dot progress along paths (0.0 to 1.0)
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    // Animated secondary pulse (counter direction or phase shifted)
    val secondaryPulseProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_pulse"
    )

    // Animated glow pulse for central node ring
    val centralGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "central_glow"
    )

    val centralGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "central_glow_alpha"
    )

    // 360-degree radar sweep angle animation for Hexagonal Mesh mode
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val localDensity = LocalDensity.current

    // Dimensions of cards in pixels
    val cardDimensionsPx = remember(isCompactMode, cardScale, localDensity) {
        val cardWidthDp = if (isCompactMode) 76.dp else 104.dp
        val cardHeightDp = if (isCompactMode) 36.dp else 66.dp
        val w = with(localDensity) { (cardWidthDp * cardScale).toPx() }
        val h = with(localDensity) { (cardHeightDp * cardScale).toPx() }
        w to h
    }

    // Partition nodes into orbit rings for SPIDERWEB & DUAL_RING mode
    val ringNodes = remember(satelliteNodes) {
        val list = mutableListOf<List<NetworkNode>>()
        var index = 0
        val capacities = listOf(6, 10, 14, 18, 22)
        var capIdx = 0
        while (index < satelliteNodes.size) {
            val cap = capacities.getOrElse(capIdx) { 20 }
            val end = (index + cap).coerceAtMost(satelliteNodes.size)
            list.add(satelliteNodes.subList(index, end))
            index = end
            capIdx++
        }
        list
    }

    // Mathematically calculated collision-free ring radii for SPIDERWEB
    val ringRadiiPx = remember(ringNodes, spacingScale, cardDimensionsPx, localDensity) {
        val (cardWidthPx, cardHeightPx) = cardDimensionsPx
        val cardDiagPx = kotlin.math.sqrt(cardWidthPx * cardWidthPx + cardHeightPx * cardHeightPx)
        val centerNodeRadiusPx = with(localDensity) { 50.dp.toPx() }
        val minGapPx = with(localDensity) { 24.dp.toPx() }

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

    // Center Node Offset relative to the container center
    val centerNodeRelOffset = remember(currentLayout, spacingScale) {
        when (currentLayout) {
            NetworkTopologyLayout.HIERARCHICAL_TREE -> Offset(0f, -130f * spacingScale)
            NetworkTopologyLayout.CYBER_MATRIX_GRID -> Offset(0f, -125f * spacingScale)
            else -> Offset(0f, 0f)
        }
    }

    // Relative offset (x, y) from container center for each satellite node based on layout
    val nodeRelPositions = remember(currentLayout, satelliteNodes, ringNodes, ringRadiiPx, spacingScale, cardDimensionsPx) {
        val positions = mutableListOf<Offset>()
        val (cardWidthPx, _) = cardDimensionsPx
        val total = satelliteNodes.size

        when (currentLayout) {
            NetworkTopologyLayout.HIERARCHICAL_TREE -> {
                // Tier 1: Distribution Bridges / Core Towers (approx 1/3 or max 4)
                // Tier 2: Access Points & Sectors
                // Tier 3: Remote Stations / Edge Clients (if total > 8)
                if (total <= 4) {
                    val tierY = 70f * spacingScale
                    val stepX = (cardWidthPx * 1.30f * spacingScale).coerceAtLeast(100f)
                    satelliteNodes.forEachIndexed { i, _ ->
                        val x = (i - (total - 1) / 2f) * stepX
                        positions.add(Offset(x, tierY))
                    }
                } else if (total <= 9) {
                    val tier1Count = ((total + 1) / 2).coerceIn(3, 4)
                    val tier2Count = total - tier1Count

                    val tier1Y = -10f * spacingScale
                    val tier1StepX = (cardWidthPx * 1.25f * spacingScale).coerceAtLeast(95f)
                    for (i in 0 until tier1Count) {
                        val x = (i - (tier1Count - 1) / 2f) * tier1StepX
                        positions.add(Offset(x, tier1Y))
                    }

                    val tier2Y = 135f * spacingScale
                    val tier2StepX = (cardWidthPx * 1.20f * spacingScale).coerceAtLeast(88f)
                    for (i in 0 until tier2Count) {
                        val x = (i - (tier2Count - 1) / 2f) * tier2StepX
                        positions.add(Offset(x, tier2Y))
                    }
                } else {
                    // 3 Tiers for large network nodes
                    val tier1Count = 3
                    val tier2Count = ((total - tier1Count) * 0.55f).roundToInt().coerceAtLeast(3)
                    val tier3Count = total - tier1Count - tier2Count

                    val tier1Y = -35f * spacingScale
                    val tier1StepX = (cardWidthPx * 1.35f * spacingScale).coerceAtLeast(110f)
                    for (i in 0 until tier1Count) {
                        val x = (i - (tier1Count - 1) / 2f) * tier1StepX
                        positions.add(Offset(x, tier1Y))
                    }

                    val tier2Y = 75f * spacingScale
                    val tier2StepX = (cardWidthPx * 1.15f * spacingScale).coerceAtLeast(85f)
                    for (i in 0 until tier2Count) {
                        val x = (i - (tier2Count - 1) / 2f) * tier2StepX
                        positions.add(Offset(x, tier2Y))
                    }

                    val tier3Y = 180f * spacingScale
                    val tier3StepX = (cardWidthPx * 1.10f * spacingScale).coerceAtLeast(80f)
                    for (i in 0 until tier3Count) {
                        val x = (i - (tier3Count - 1) / 2f) * tier3StepX
                        positions.add(Offset(x, tier3Y))
                    }
                }
            }

            NetworkTopologyLayout.ORBITAL_SPIDERWEB -> {
                ringNodes.forEachIndexed { ringIdx, ringGroup ->
                    val ringRadius = ringRadiiPx.getOrElse(ringIdx) { 180f }
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
            }

            NetworkTopologyLayout.HEXAGONAL_MESH -> {
                // Honeycomb coordinates
                val hexRadiusBase = 115f * spacingScale
                val hexStepRadius = 100f * spacingScale

                satelliteNodes.forEachIndexed { i, _ ->
                    val ring = (i / 6) + 1
                    val posInHex = i % 6
                    val angle = (posInHex * (Math.PI / 3)) + if (ring % 2 == 0) (Math.PI / 6) else 0.0
                    val r = hexRadiusBase + (ring - 1) * hexStepRadius
                    val x = r * cos(angle).toFloat()
                    val y = r * sin(angle).toFloat()
                    positions.add(Offset(x, y))
                }
            }

            NetworkTopologyLayout.CYBER_MATRIX_GRID -> {
                // Parallel Orthogonal Matrix Layout with circuit tracks
                val cols = when {
                    total <= 4 -> 2
                    total <= 9 -> 3
                    total <= 16 -> 4
                    else -> 5
                }
                val stepX = (cardWidthPx * 1.28f * spacingScale).coerceAtLeast(95f)
                val stepY = 105f * spacingScale
                val startY = -30f * spacingScale

                satelliteNodes.forEachIndexed { i, _ ->
                    val row = i / cols
                    val col = i % cols
                    val x = (col - (cols - 1) / 2f) * stepX
                    val y = startY + (row * stepY)
                    positions.add(Offset(x, y))
                }
            }

            NetworkTopologyLayout.RADIAL_CONSTELLATION -> {
                // Stellar Constellation Clusters (e.g. 3 orbital star groups)
                val clusterCount = when {
                    total <= 5 -> 2
                    total <= 12 -> 3
                    else -> 4
                }
                val clusterRadius = 160f * spacingScale
                val subStarRadius = 65f * spacingScale

                satelliteNodes.forEachIndexed { i, _ ->
                    val clusterIdx = i % clusterCount
                    val clusterAngle = (clusterIdx * (2 * Math.PI / clusterCount)) - (Math.PI / 2)
                    val clusterCenterX = clusterRadius * cos(clusterAngle).toFloat()
                    val clusterCenterY = clusterRadius * sin(clusterAngle).toFloat()

                    val subIdx = i / clusterCount
                    val subAngle = subIdx * (Math.PI / 2.5) + (clusterIdx * 0.4)
                    val subX = clusterCenterX + (subStarRadius * cos(subAngle).toFloat())
                    val subY = clusterCenterY + (subStarRadius * sin(subAngle).toFloat())
                    positions.add(Offset(subX, subY))
                }
            }

            NetworkTopologyLayout.DUAL_RING_TOKEN -> {
                // Dual Concentric Counter-Rotating Ring Tracks
                val halfCount = (total + 1) / 2
                val innerRadius = 120f * spacingScale
                val outerRadius = 210f * spacingScale

                satelliteNodes.forEachIndexed { i, _ ->
                    if (i < halfCount) {
                        // Inner Primary Ring
                        val angle = (i * (2 * Math.PI / halfCount)) - (Math.PI / 2)
                        val x = innerRadius * cos(angle).toFloat()
                        val y = innerRadius * sin(angle).toFloat()
                        positions.add(Offset(x, y))
                    } else {
                        // Outer Secondary Ring (interleaved angle)
                        val outerIdx = i - halfCount
                        val outerTotal = total - halfCount
                        val angle = (outerIdx * (2 * Math.PI / outerTotal)) - (Math.PI / 2) + (Math.PI / outerTotal)
                        val x = outerRadius * cos(angle).toFloat()
                        val y = outerRadius * sin(angle).toFloat()
                        positions.add(Offset(x, y))
                    }
                }
            }
        }
        positions
    }

    val containerHeightDp = if (isCompactHeight) 360.dp else 470.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeightDp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0C2419),
                        Color(0xFF07150E),
                        Color(0xFF020705)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        CyberGreenNeon.copy(alpha = 0.5f),
                        NetworkCyan.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.28f, 2.5f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
            .testTag("spiderweb_topology_graph")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Helper function for automatic optimal zoom & center on screen
        fun autoFitToScreen() {
            if (satelliteNodes.isEmpty()) {
                zoomScale = 1.0f
                panOffsetX = 0f
                panOffsetY = 0f
                return
            }

            val allOffsets = nodeRelPositions + centerNodeRelOffset
            val minX = allOffsets.minOfOrNull { it.x } ?: -150f
            val maxX = allOffsets.maxOfOrNull { it.x } ?: 150f
            val minY = allOffsets.minOfOrNull { it.y } ?: -150f
            val maxY = allOffsets.maxOfOrNull { it.y } ?: 150f

            val spanX = (maxX - minX).coerceAtLeast(180f) + with(localDensity) { 90.dp.toPx() }
            val spanY = (maxY - minY).coerceAtLeast(180f) + with(localDensity) { 85.dp.toPx() }

            val availableW = widthPx.coerceAtLeast(200f) - with(localDensity) { 32.dp.toPx() }
            val availableH = heightPx.coerceAtLeast(200f) - with(localDensity) { 32.dp.toPx() }

            val optimalScale = minOf(availableW / spanX, availableH / spanY).coerceIn(0.32f, 1.25f)
            zoomScale = optimalScale

            // Re-center around the bounding box midpoint
            val midX = (minX + maxX) / 2f
            val midY = (minY + maxY) / 2f
            panOffsetX = -midX * optimalScale
            panOffsetY = -midY * optimalScale
        }

        // Auto-fit upon initial layout or when layout/node count changes
        LaunchedEffect(currentLayout, satelliteNodes.size, isCompactHeight) {
            if (widthPx > 0 && heightPx > 0) {
                autoFitToScreen()
            }
        }

        // 1. Draw Canvas: Geometric Layouts, Grid Lines, Energy Flow Pulses
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
            val routerAbsolutePos = centerOffset + centerNodeRelOffset

            // Draw Subtle Cyber Background Grid
            val gridStep = 50f
            for (gx in -600..600 step gridStep.toInt()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.02f),
                    start = Offset(centerOffset.x + gx, centerOffset.y - 600f),
                    end = Offset(centerOffset.x + gx, centerOffset.y + 600f),
                    strokeWidth = 0.5f
                )
            }
            for (gy in -600..600 step gridStep.toInt()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.02f),
                    start = Offset(centerOffset.x - 600f, centerOffset.y + gy),
                    end = Offset(centerOffset.x + 600f, centerOffset.y + gy),
                    strokeWidth = 0.5f
                )
            }

            when (currentLayout) {
                NetworkTopologyLayout.HIERARCHICAL_TREE -> {
                    // Cyber circuit branching tree
                    val total = satelliteNodes.size
                    val tier1Count = when {
                        total <= 4 -> total
                        total <= 9 -> ((total + 1) / 2).coerceIn(3, 4)
                        else -> 3
                    }

                    // Vertical trunk line from Main Router down to distribution junction
                    val trunkJunctionY = routerAbsolutePos.y + 50f * spacingScale
                    drawLine(
                        color = CyberGreenNeon.copy(alpha = 0.85f),
                        start = routerAbsolutePos,
                        end = Offset(routerAbsolutePos.x, trunkJunctionY),
                        strokeWidth = 2.8f,
                        cap = StrokeCap.Round
                    )

                    // Draw Horizontal Distribution Bus Bar
                    val tier1Offsets = nodeRelPositions.take(tier1Count).map { centerOffset + it }
                    if (tier1Offsets.isNotEmpty()) {
                        val minX = tier1Offsets.minOf { it.x }
                        val maxX = tier1Offsets.maxOf { it.x }

                        // Bus line
                        drawLine(
                            color = NetworkCyan.copy(alpha = 0.85f),
                            start = Offset(minX, trunkJunctionY),
                            end = Offset(maxX, trunkJunctionY),
                            strokeWidth = 2.2f,
                            cap = StrokeCap.Round
                        )

                        // Branch drop lines to each Tier 1 node
                        tier1Offsets.forEachIndexed { _, nodePos ->
                            drawLine(
                                color = CyberGreenNeon.copy(alpha = 0.75f),
                                start = Offset(nodePos.x, trunkJunctionY),
                                end = nodePos,
                                strokeWidth = 1.8f
                            )

                            // Glowing junction circle
                            drawCircle(color = CyberGreenNeon, radius = 4f, center = Offset(nodePos.x, trunkJunctionY))

                            // Pulse traveling down to Tier 1 node
                            val pulseY = trunkJunctionY + (nodePos.y - trunkJunctionY) * pulseProgress
                            drawCircle(color = Color.White, radius = 3.2f, center = Offset(nodePos.x, pulseY))
                        }
                    }

                    // Links from Tier 1 to Tier 2 (if present)
                    if (total > tier1Count) {
                        val tier2Count = if (total <= 9) total - tier1Count else ((total - tier1Count) * 0.55f).roundToInt().coerceAtLeast(3)
                        val tier2Offsets = nodeRelPositions.drop(tier1Count).take(tier2Count).map { centerOffset + it }

                        tier2Offsets.forEachIndexed { idx, subNodePos ->
                            val parentIdx = idx % tier1Count
                            val parentPos = tier1Offsets.getOrNull(parentIdx)
                            if (parentPos != null) {
                                drawLine(
                                    color = NetworkCyan.copy(alpha = 0.50f),
                                    start = parentPos,
                                    end = subNodePos,
                                    strokeWidth = 1.3f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )

                                val pulse = parentPos + (subNodePos - parentPos) * pulseProgress
                                drawCircle(color = NetworkCyanLight, radius = 3f, center = pulse)
                            }
                        }

                        // Links from Tier 2 to Tier 3 (if present)
                        if (total > tier1Count + tier2Count) {
                            val tier3Offsets = nodeRelPositions.drop(tier1Count + tier2Count).map { centerOffset + it }
                            tier3Offsets.forEachIndexed { idx, subSubPos ->
                                val parentIdx = idx % tier2Offsets.size
                                val parentPos = tier2Offsets.getOrNull(parentIdx)
                                if (parentPos != null) {
                                    drawLine(
                                        color = CyberGreenNeon.copy(alpha = 0.40f),
                                        start = parentPos,
                                        end = subSubPos,
                                        strokeWidth = 1.0f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                    )
                                    val pulse = parentPos + (subSubPos - parentPos) * secondaryPulseProgress
                                    drawCircle(color = Color.White, radius = 2.5f, center = pulse)
                                }
                            }
                        }
                    }
                }

                NetworkTopologyLayout.ORBITAL_SPIDERWEB -> {
                    // Concentric Orbit Web Rings
                    ringRadiiPx.forEach { radiusPx ->
                        drawCircle(
                            color = CyberGreenNeon.copy(alpha = 0.14f),
                            radius = radiusPx,
                            center = routerAbsolutePos,
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }

                    // Inter-node Web Mesh Connecting Lines on same ring
                    var globalIdx = 0
                    ringNodes.forEach { ringGroup ->
                        val count = ringGroup.size
                        if (count > 1) {
                            for (i in 0 until count) {
                                val currPos = nodeRelPositions.getOrNull(globalIdx + i)
                                val nextPos = nodeRelPositions.getOrNull(globalIdx + ((i + 1) % count))
                                if (currPos != null && nextPos != null) {
                                    drawLine(
                                        color = NetworkCyan.copy(alpha = 0.20f),
                                        start = centerOffset + currPos,
                                        end = centerOffset + nextPos,
                                        strokeWidth = 1f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )
                                }
                            }
                        }
                        globalIdx += count
                    }

                    // Radial Spoke Lines and Energy Pulses
                    nodeRelPositions.forEachIndexed { index, nodeRelOffset ->
                        val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed
                        val absNodeOffset = centerOffset + nodeRelOffset

                        val lineColor = when {
                            !node.isOnline -> StatusErrorRed.copy(alpha = 0.65f)
                            node.hasIssue -> StatusWarningAmber.copy(alpha = 0.85f)
                            else -> CyberGreenNeon.copy(alpha = 0.65f)
                        }

                        drawLine(
                            color = lineColor,
                            start = routerAbsolutePos,
                            end = absNodeOffset,
                            strokeWidth = if (node.hasIssue) 2.2f else 1.6f,
                            cap = StrokeCap.Round
                        )

                        if (node.isOnline) {
                            val pulseOffset = routerAbsolutePos + (absNodeOffset - routerAbsolutePos) * pulseProgress
                            drawCircle(color = CyberGreenGlow.copy(alpha = 0.7f), radius = 6.5f, center = pulseOffset)
                            drawCircle(color = Color.White, radius = 3.5f, center = pulseOffset)

                            val revProgress = (pulseProgress + 0.5f) % 1.0f
                            val revOffset = routerAbsolutePos + (absNodeOffset - routerAbsolutePos) * revProgress
                            drawCircle(color = NetworkCyanLight.copy(alpha = 0.6f), radius = 4f, center = revOffset)
                        }
                    }
                }

                NetworkTopologyLayout.HEXAGONAL_MESH -> {
                    // Hexagonal mesh background grid
                    for (r in listOf(115f, 215f, 315f)) {
                        val scaledR = r * spacingScale
                        drawCircle(
                            color = NetworkCyan.copy(alpha = 0.08f),
                            radius = scaledR,
                            center = routerAbsolutePos,
                            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f))
                        )
                    }

                    nodeRelPositions.forEachIndexed { i, pos1 ->
                        val abs1 = centerOffset + pos1
                        drawLine(
                            color = CyberGreenNeon.copy(alpha = 0.5f),
                            start = routerAbsolutePos,
                            end = abs1,
                            strokeWidth = 1.4f
                        )
                        val nextIdx = (i + 1) % nodeRelPositions.size
                        val pos2 = nodeRelPositions[nextIdx]
                        drawLine(
                            color = NetworkCyan.copy(alpha = 0.25f),
                            start = abs1,
                            end = centerOffset + pos2,
                            strokeWidth = 1f
                        )
                    }

                    // 360-Degree Radar Sweeper Beam Line
                    val radarRad = Math.toRadians(radarAngle.toDouble())
                    val sweepExtent = 300f * spacingScale
                    val sweepEnd = Offset(
                        routerAbsolutePos.x + (sweepExtent * cos(radarRad)).toFloat(),
                        routerAbsolutePos.y + (sweepExtent * sin(radarRad)).toFloat()
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(CyberGreenNeon.copy(alpha = 0.9f), Color.Transparent),
                            start = routerAbsolutePos,
                            end = sweepEnd
                        ),
                        start = routerAbsolutePos,
                        end = sweepEnd,
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }

                NetworkTopologyLayout.CYBER_MATRIX_GRID -> {
                    // Orthogonal Circuit Tracks (Right-angled connections)
                    nodeRelPositions.forEachIndexed { index, relPos ->
                        val absPos = centerOffset + relPos
                        val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed

                        val cornerOffset = Offset(absPos.x, routerAbsolutePos.y)

                        // Path from router -> corner -> node
                        val circuitPath = Path().apply {
                            moveTo(routerAbsolutePos.x, routerAbsolutePos.y)
                            lineTo(cornerOffset.x, cornerOffset.y)
                            lineTo(absPos.x, absPos.y)
                        }

                        val trackColor = if (!node.isOnline) StatusErrorRed.copy(alpha = 0.6f) else CyberGreenNeon.copy(alpha = 0.6f)
                        drawPath(circuitPath, color = trackColor, style = Stroke(width = 1.6f))

                        // Glowing junction pad at corner
                        drawCircle(color = NetworkCyan, radius = 3.5f, center = cornerOffset)

                        // Circuit data packet travelling along path
                        if (node.isOnline) {
                            val packetPos = if (pulseProgress < 0.5f) {
                                val t = pulseProgress / 0.5f
                                routerAbsolutePos + (cornerOffset - routerAbsolutePos) * t
                            } else {
                                val t = (pulseProgress - 0.5f) / 0.5f
                                cornerOffset + (absPos - cornerOffset) * t
                            }
                            drawCircle(color = Color.White, radius = 3.5f, center = packetPos)
                            drawCircle(color = CyberGreenGlow, radius = 6f, center = packetPos)
                        }
                    }
                }

                NetworkTopologyLayout.RADIAL_CONSTELLATION -> {
                    // Stellar Constellation Links
                    val total = satelliteNodes.size
                    val clusterCount = when {
                        total <= 5 -> 2
                        total <= 12 -> 3
                        else -> 4
                    }

                    // Backbone to cluster anchors
                    for (c in 0 until clusterCount) {
                        val anchorIdx = c
                        val anchorOffset = nodeRelPositions.getOrNull(anchorIdx)
                        if (anchorOffset != null) {
                            val absAnchor = centerOffset + anchorOffset
                            drawLine(
                                color = CyberGreenNeon.copy(alpha = 0.85f),
                                start = routerAbsolutePos,
                                end = absAnchor,
                                strokeWidth = 2.0f
                            )
                        }
                    }

                    // Intra-cluster stellar links
                    nodeRelPositions.forEachIndexed { idx, relPos ->
                        val absPos = centerOffset + relPos
                        val clusterIdx = idx % clusterCount
                        val anchorOffset = nodeRelPositions.getOrNull(clusterIdx)
                        if (anchorOffset != null && idx != clusterIdx) {
                            val absAnchor = centerOffset + anchorOffset
                            drawLine(
                                color = NetworkCyanLight.copy(alpha = 0.40f),
                                start = absAnchor,
                                end = absPos,
                                strokeWidth = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                        }

                        // Star twinkle
                        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 2f, center = absPos)
                    }
                }

                NetworkTopologyLayout.DUAL_RING_TOKEN -> {
                    val halfCount = (satelliteNodes.size + 1) / 2
                    val innerRadius = 120f * spacingScale
                    val outerRadius = 210f * spacingScale

                    // Draw Dual Ring Tracks
                    drawCircle(
                        color = NetworkCyan.copy(alpha = 0.25f),
                        radius = innerRadius,
                        center = routerAbsolutePos,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                    )
                    drawCircle(
                        color = CyberGreenNeon.copy(alpha = 0.25f),
                        radius = outerRadius,
                        center = routerAbsolutePos,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                    )

                    // Spoke links from router to inner ring
                    nodeRelPositions.forEachIndexed { idx, relPos ->
                        val absPos = centerOffset + relPos
                        if (idx < halfCount) {
                            drawLine(
                                color = NetworkCyan.copy(alpha = 0.6f),
                                start = routerAbsolutePos,
                                end = absPos,
                                strokeWidth = 1.4f
                            )
                        } else {
                            val innerIdx = idx - halfCount
                            val innerPos = nodeRelPositions.getOrNull(innerIdx)
                            if (innerPos != null) {
                                drawLine(
                                    color = CyberGreenNeon.copy(alpha = 0.45f),
                                    start = centerOffset + innerPos,
                                    end = absPos,
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                )
                            }
                        }
                    }

                    // Rotating Optical Photons along rings
                    val innerAngle = (pulseProgress * 2 * Math.PI).toFloat()
                    val outerAngle = (-pulseProgress * 2 * Math.PI).toFloat()

                    val innerPhoton = Offset(
                        routerAbsolutePos.x + innerRadius * cos(innerAngle),
                        routerAbsolutePos.y + innerRadius * sin(innerAngle)
                    )
                    val outerPhoton = Offset(
                        routerAbsolutePos.x + outerRadius * cos(outerAngle),
                        routerAbsolutePos.y + outerRadius * sin(outerAngle)
                    )

                    drawCircle(color = Color.White, radius = 4f, center = innerPhoton)
                    drawCircle(color = NetworkCyanLight, radius = 7f, center = innerPhoton)
                    drawCircle(color = Color.White, radius = 4f, center = outerPhoton)
                    drawCircle(color = CyberGreenGlow, radius = 7f, center = outerPhoton)
                }
            }

            // Pulsing Glow Wave around Central Server Node
            drawCircle(
                color = CyberGreenNeon.copy(alpha = centralGlowAlpha),
                radius = 48f * centralGlowScale,
                center = routerAbsolutePos
            )
        }

        // 2. Render Central Main Router Node (السيرفر الرئيسي)
        val centerDpX = with(localDensity) { ((centerNodeRelOffset.x * zoomScale) + panOffsetX).toDp() }
        val centerDpY = with(localDensity) { ((centerNodeRelOffset.y * zoomScale) + panOffsetY).toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = centerDpX, y = centerDpY)
                .shadow(16.dp, CircleShape, spotColor = CyberGreenNeon)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF003820),
                            Color(0xFF001F10)
                        )
                    )
                )
                .border(2.dp, CyberGreenNeon, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CyberGreenNeon.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "السيرفر الرئيسي",
                        tint = CyberGreenNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = centralRouterName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                Text(
                    text = centralRouterIp,
                    color = CyberGreenNeon,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Render Satellite Nodes (الأبراج ونقاط البث)
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
                val childHomeNodes = towerChildNodesMap[node.id] ?: emptyList()
                val hasCluster = isClusteringActive && childHomeNodes.isNotEmpty()
                if (isCompactMode) {
                    CompactTopologyNodeBadge(
                        node = node,
                        childCount = if (hasCluster) childHomeNodes.size else 0,
                        onClusterClick = { selectedClusterTower = node },
                        onClick = { onNodeClick(node) }
                    )
                } else {
                    TopologyNodeItemCard(
                        node = node,
                        childCount = if (hasCluster) childHomeNodes.size else 0,
                        onClusterClick = { selectedClusterTower = node },
                        onClick = { onNodeClick(node) }
                    )
                }
            }
        }

        // 4. Dropdown-Style Floating Control Bar (شريط التحكم مثل القائمة المنسدلة يتصغر ويكبر)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            AnimatedVisibility(
                visible = !isControlsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                // Collapsed State: Super-compact, attractive cyber pill button with dropdown chevron
                Surface(
                    modifier = Modifier
                        .clickable { isControlsExpanded = true }
                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = CyberGreenNeon)
                        .testTag("btn_expand_topology_controls"),
                    color = Color(0xFF04140D).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, CyberGreenNeon.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = currentLayout.icon,
                            contentDescription = null,
                            tint = CyberGreenNeon,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = currentLayout.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• ${(zoomScale * 100).roundToInt()}%",
                            color = NetworkCyanLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "فتح قائمة التحكم",
                            tint = CyberGreenNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isControlsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                // Expanded State: Comprehensive Cyber Dropdown Menu Card
                Surface(
                    color = Color(0xFF03120B).copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, CyberGreenNeon.copy(alpha = 0.7f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier.widthIn(max = 310.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header with Title & Collapse Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = CyberGreenNeon, modifier = Modifier.size(16.dp))
                                Text("أدوات التحكم وتخطيط الشبكة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            IconButton(
                                onClick = { isControlsExpanded = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ArrowDropUp, contentDescription = "طي القائمة", tint = CyberGreenNeon, modifier = Modifier.size(22.dp))
                            }
                        }

                        // A. 6 Cybernetic Topology Layout Options
                        Text("أنماط وأشكال الشبكة السيبرانية:", color = CyberGreenNeon, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val rows = NetworkTopologyLayout.entries.chunked(3)
                            rows.forEach { rowLayouts ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowLayouts.forEach { layout ->
                                        val isSelected = currentLayout == layout
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    currentLayout = layout
                                                    autoFitToScreen()
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) CyberGreenNeon.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(1.dp, if (isSelected) CyberGreenNeon else Color.White.copy(alpha = 0.15f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    layout.icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) CyberGreenNeon else Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = layout.title,
                                                    color = if (isSelected) CyberGreenNeon else Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // B. Zoom & Camera Actions
                        Text("التقريب وملاءمة الشاشة:", color = NetworkCyanLight, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Zoom Out
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.28f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(16.dp))
                            }

                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                color = CyberGreenNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )

                            // Zoom In
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.5f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(16.dp))
                            }

                            // Auto-Fit button
                            IconButton(
                                onClick = { autoFitToScreen() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "احتواء كامل", tint = NetworkCyanLight, modifier = Modifier.size(16.dp))
                            }

                            // Reset Camera
                            IconButton(
                                onClick = {
                                    zoomScale = defaultInitialZoom
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                    spacingScale = 1.0f
                                    cardScale = 1.0f
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة ضبط", tint = Color.White, modifier = Modifier.size(16.dp))
                            }

                            // Height toggle (Small screen 360dp vs Expanded 470dp)
                            IconButton(
                                onClick = { isCompactHeight = !isCompactHeight },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Height,
                                    contentDescription = "تبديل الارتفاع",
                                    tint = if (isCompactHeight) StatusWarningAmber else CyberGreenNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Full screen toggle if available
                            if (onToggleFullScreen != null) {
                                IconButton(
                                    onClick = onToggleFullScreen,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.OpenInFull, contentDescription = "ملء الشاشة", tint = CyberGreenNeon, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // C. Density & Spacing Gap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { isCompactMode = !isCompactMode },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp),
                                border = BorderStroke(1.dp, if (isCompactMode) StatusWarningAmber else CyberGreenNeon)
                            ) {
                                Text(
                                    text = if (isCompactMode) "شارات مدمجة" else "بطاقات مفصلة",
                                    color = if (isCompactMode) StatusWarningAmber else CyberGreenNeon,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("المسافات:", color = Color.White.copy(alpha = 0.7f), fontSize = 9.5.sp)
                                IconButton(
                                    onClick = { spacingScale = (spacingScale - 0.15f).coerceAtLeast(0.55f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.UnfoldLess, contentDescription = "تضييق المسافة", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { spacingScale = (spacingScale + 0.15f).coerceAtMost(2.0f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.UnfoldMore, contentDescription = "توسيع المسافة", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // D. Quick Status Filter Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تصفية:", color = Color.White.copy(alpha = 0.7f), fontSize = 9.5.sp)
                            TopologyFilterMode.entries.forEach { mode ->
                                val isSelected = filterMode == mode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { filterMode = mode },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CyberGreenNeon.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(0.8.dp, if (isSelected) CyberGreenNeon else Color.White.copy(alpha = 0.12f))
                                ) {
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) CyberGreenNeon else Color.White.copy(alpha = 0.7f),
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Bottom Touch Gesture Hint Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Text(
                text = "💡 اسحب للتحريك أو اقرص للتكبير • نمط: ${currentLayout.title}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 9.5.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        // 6. Cluster Home Nodes Dialog
        selectedClusterTower?.let { tower ->
            val childNodes = towerChildNodesMap[tower.id] ?: emptyList()
            ClusterHomeNodesDialog(
                tower = tower,
                childNodes = childNodes,
                onNodeClick = { childNode ->
                    selectedClusterTower = null
                    onNodeClick(childNode)
                },
                onDismiss = { selectedClusterTower = null }
            )
        }
    }
}

/**
 * Detailed Topology Node Item Card with Semantic Icons & Material 3 4-State Visual Indicators
 */
@Composable
private fun TopologyNodeItemCard(
    node: NetworkNode,
    childCount: Int = 0,
    onClusterClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val statusColor = when (node.effectiveStatus) {
        com.example.data.model.NodeStatus.ACTIVE -> Color(0xFF2E7D32) // الأخضر: للنقاط المفعلة والنشطة
        com.example.data.model.NodeStatus.OFFLINE -> Color(0xFFC62828) // الأحمر: للنقاط المغلقة (لا يوجد رد)
        com.example.data.model.NodeStatus.NO_INTERNET -> Color(0xFFEF6C00) // البرتقالي/الأصفر: مشاكل في الإنترنت
        com.example.data.model.NodeStatus.DISABLED -> Color(0xFF757575) // الرمادي: غير مؤرشفة أو معطلة
    }

    val iconVector = when (node.nodeType) {
        com.example.data.model.NetworkNodeType.MAIN_TOWER -> Icons.Default.CellTower
        com.example.data.model.NetworkNodeType.HOME_NODE -> Icons.Default.Home
        com.example.data.model.NetworkNodeType.DISTRIBUTION -> {
            if (node.role.contains("Starlink", ignoreCase = true)) Icons.Default.WifiTethering
            else if (node.role.contains("لاسلكي", ignoreCase = true)) Icons.Default.Wifi
            else Icons.Default.Router
        }
    }

    Card(
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onClick)
            .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = statusColor)
            .testTag("node_topology_item_${node.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A1812)
        ),
        border = BorderStroke(1.2.dp, statusColor)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Header Row: Status dot, Semantic Icon & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Icon(
                    imageVector = iconVector,
                    contentDescription = node.nodeType.labelAr,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (node.isOnline) "${node.pingMs}ms" else "طافي",
                        color = statusColor,
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

            // IP Address & Connected Users Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.ipAddress,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    maxLines = 1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = NetworkCyanLight,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "${node.connectedUsers}",
                        color = NetworkCyanLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            // Smart Clustering Badge (if this tower has child home nodes)
            if (childCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClusterClick?.invoke() }
                        .padding(top = 2.dp),
                    color = CyberGreenNeon.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.6.dp, CyberGreenNeon.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = CyberGreenNeon,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "+$childCount منازل",
                            color = CyberGreenNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Topology Node Badge (For dense network views with many nodes)
 */
@Composable
private fun CompactTopologyNodeBadge(
    node: NetworkNode,
    childCount: Int = 0,
    onClusterClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val statusColor = when (node.effectiveStatus) {
        com.example.data.model.NodeStatus.ACTIVE -> Color(0xFF2E7D32)
        com.example.data.model.NodeStatus.OFFLINE -> Color(0xFFC62828)
        com.example.data.model.NodeStatus.NO_INTERNET -> Color(0xFFEF6C00)
        com.example.data.model.NodeStatus.DISABLED -> Color(0xFF757575)
    }

    val iconVector = when (node.nodeType) {
        com.example.data.model.NetworkNodeType.MAIN_TOWER -> Icons.Default.CellTower
        com.example.data.model.NetworkNodeType.HOME_NODE -> Icons.Default.Home
        else -> Icons.Default.WifiTethering
    }

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = statusColor)
            .testTag("compact_node_badge_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF07140D),
        border = BorderStroke(1.dp, statusColor)
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
                    .background(statusColor)
            )

            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(12.dp)
            )

            Text(
                text = node.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                maxLines = 1
            )

            if (childCount > 0) {
                Surface(
                    color = CyberGreenNeon.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onClusterClick?.invoke() }
                ) {
                    Text(
                        text = "+$childCount",
                        color = CyberGreenNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            } else {
                Text(
                    text = if (node.isOnline) "${node.pingMs}ms" else "طافي",
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp
                )
            }
        }
    }
}

/**
 * Smart Clustering Expandable Dialog for Tower Home Nodes
 */
@Composable
private fun ClusterHomeNodesDialog(
    tower: NetworkNode,
    childNodes: List<NetworkNode>,
    onNodeClick: (NetworkNode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CyberGreenNeon.copy(alpha = 0.15f)),
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
                        text = tower.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "النقاط المنزلية المتصلة بالبرج (${childNodes.size} محطة)",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = NetworkCyanLight
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (childNodes.isEmpty()) {
                    Text(
                        text = "لا توجد نقاط منزلية تابعة لهذا البرج حالياً.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    childNodes.forEach { child ->
                        val childStatusColor = when (child.effectiveStatus) {
                            com.example.data.model.NodeStatus.ACTIVE -> Color(0xFF2E7D32)
                            com.example.data.model.NodeStatus.OFFLINE -> Color(0xFFC62828)
                            com.example.data.model.NodeStatus.NO_INTERNET -> Color(0xFFEF6C00)
                            com.example.data.model.NodeStatus.DISABLED -> Color(0xFF757575)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNodeClick(child) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, childStatusColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = childStatusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = child.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${child.ipAddress} • ${child.model}",
                                            fontSize = 10.sp,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    color = childStatusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = child.statusTitleAr,
                                        color = childStatusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreenNeon.copy(alpha = 0.9f))
            ) {
                Text("إغلاق", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}
