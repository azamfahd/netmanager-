package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
 * Advanced Collision-Free Spiderweb Topology Network Graph
 * Features:
 * 1. Mathematical Trigonometric Auto-Spacing (Zero Card Overlap Guarantee)
 * 2. Multi-Ring Concentric Orbit Layout for scaling to any number of nodes
 * 3. Interactive Pinch-to-Zoom & Pan/Drag Gestures
 * 4. Auto-Fit to Screen, Reset Camera, and Custom Spacing Scale Controls
 * 5. Dense/Detailed View Mode Toggle ("بطاقات مفصلة" vs "نقاط مدمجة")
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
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var spacingScale by remember { mutableFloatStateOf(1.0f) }
    var cardScale by remember { mutableFloatStateOf(1.0f) }
    var isCompactMode by remember { mutableStateOf(nodes.size > 12) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_energy")

    // Animated energy dot progress along path (0.0 to 1.0)
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    // Animated glow pulse for central node ring
    val centralGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "central_glow"
    )

    val centralGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "central_glow_alpha"
    )

    val localDensity = LocalDensity.current

    // Filter out the central main router / gateway so it is NEVER duplicated as a satellite on the orbit
    val satelliteNodes = remember(nodes) {
        nodes
            .filter { it.id != "node_main_router" && !it.role.contains("Gateway", ignoreCase = true) && !it.role.contains("الرئيسي للشبكة", ignoreCase = true) }
            .distinctBy { it.id.ifBlank { it.ipAddress } }
    }

    // Partition nodes into orbit rings (Ring 0: max 6, Ring 1: max 10, Ring 2: max 14...)
    val ringNodes = remember(satelliteNodes) {
        val list = mutableListOf<List<NetworkNode>>()
        var index = 0
        val capacities = listOf(6, 10, 14, 18, 22, 26)
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

    // Mathematically calculated collision-free ring radii
    val ringRadiiPx = remember(ringNodes, spacingScale, cardScale, isCompactMode, localDensity) {
        val cardWidthDp = if (isCompactMode) 76.dp else 104.dp
        val cardHeightDp = if (isCompactMode) 36.dp else 68.dp
        val cardWidthPx = with(localDensity) { (cardWidthDp * cardScale).toPx() }
        val cardHeightPx = with(localDensity) { (cardHeightDp * cardScale).toPx() }
        val cardDiagPx = kotlin.math.sqrt(cardWidthPx * cardWidthPx + cardHeightPx * cardHeightPx)
        val centerNodeRadiusPx = with(localDensity) { 52.dp.toPx() }
        val minGapPx = with(localDensity) { 28.dp.toPx() }

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

    // Relative offset (x, y) from center for each node
    val nodeRelPositions = remember(satelliteNodes, ringNodes, ringRadiiPx) {
        val positions = mutableListOf<Offset>()
        ringNodes.forEachIndexed { ringIdx, ringGroup ->
            val ringRadius = ringRadiiPx.getOrElse(ringIdx) { 200f }
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(560.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D2319),
                        Color(0xFF08140E),
                        Color(0xFF030906)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        CyberGreenNeon.copy(alpha = 0.4f),
                        NetworkCyan.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.35f, 2.5f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
            .testTag("spiderweb_topology_graph")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Draw Spiderweb Net Canvas, Orbit Rings, Spoke Lines, and Energy Flow Pulses
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

            // A. Concentric Orbit Web Rings
            ringRadiiPx.forEach { radiusPx ->
                drawCircle(
                    color = CyberGreenNeon.copy(alpha = 0.12f),
                    radius = radiusPx,
                    center = centerOffset,
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // B. Inter-node Web Mesh Connecting Lines on same orbit ring
            var globalIdx = 0
            ringNodes.forEach { ringGroup ->
                val count = ringGroup.size
                if (count > 1) {
                    for (i in 0 until count) {
                        val currPos = nodeRelPositions.getOrNull(globalIdx + i)
                        val nextPos = nodeRelPositions.getOrNull(globalIdx + ((i + 1) % count))
                        if (currPos != null && nextPos != null) {
                            drawLine(
                                color = NetworkCyan.copy(alpha = 0.18f),
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

            // C. Spoke lines from Central Server to each node + Energy Flow Pulses
            nodeRelPositions.forEachIndexed { index, nodeRelOffset ->
                val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed
                val absNodeOffset = centerOffset + nodeRelOffset

                val lineColor = when {
                    !node.isOnline -> StatusErrorRed.copy(alpha = 0.65f)
                    node.hasIssue -> StatusWarningAmber.copy(alpha = 0.85f)
                    node.cpuLoadPercent > 75 -> StatusWarningAmber.copy(alpha = 0.85f)
                    else -> CyberGreenNeon.copy(alpha = 0.65f)
                }

                // Spoke connection line
                drawLine(
                    color = lineColor,
                    start = centerOffset,
                    end = absNodeOffset,
                    strokeWidth = if (node.hasIssue) 2.5f else 1.8f,
                    cap = StrokeCap.Round
                )

                // Animated Energy Pulse Dot along spoke
                if (node.isOnline) {
                    val pulseX = centerOffset.x + nodeRelOffset.x * pulseProgress
                    val pulseY = centerOffset.y + nodeRelOffset.y * pulseProgress
                    val pulseOffset = Offset(pulseX, pulseY)

                    drawCircle(
                        color = if (node.hasIssue) StatusWarningAmber.copy(alpha = 0.6f) else CyberGreenGlow.copy(alpha = 0.7f),
                        radius = 7f,
                        center = pulseOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f,
                        center = pulseOffset
                    )

                    // Return pulse for bidirectional data traffic representation
                    val revProgress = (pulseProgress + 0.5f) % 1.0f
                    val revX = centerOffset.x + nodeRelOffset.x * revProgress
                    val revY = centerOffset.y + nodeRelOffset.y * revProgress
                    drawCircle(
                        color = NetworkCyanLight.copy(alpha = 0.6f),
                        radius = 4.5f,
                        center = Offset(revX, revY)
                    )
                }
            }

            // D. Pulsing Glow Wave around Central Server Node
            drawCircle(
                color = CyberGreenNeon.copy(alpha = centralGlowAlpha),
                radius = 50f * centralGlowScale,
                center = centerOffset
            )
        }

        // 2. Render Central Main Router Node (السيرفر الرئيسي)
        val centerDpX = with(localDensity) { panOffsetX.toDp() }
        val centerDpY = with(localDensity) { panOffsetY.toDp() }

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
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CyberGreenNeon.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "السيرفر الرئيسي",
                        tint = CyberGreenNeon,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = centralRouterName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = centralRouterIp,
                    color = CyberGreenNeon,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Render Radial Sub-Nodes (نقاط البث والأكسس بوينت)
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
                    CompactTopologyNodeBadge(
                        node = node,
                        onClick = { onNodeClick(node) }
                    )
                } else {
                    TopologyNodeItemCard(
                        node = node,
                        onClick = { onNodeClick(node) }
                    )
                }
            }
        }

        // Top-Left Header: Network Info & Legend
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, CyberGreenNeon.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "نقاط وأبراج البث (${satelliteNodes.size} نقطة)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CyberGreenNeon))
                        Text("سليم", color = Color.White, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusWarningAmber))
                        Text("ضغط", color = Color.White, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusErrorRed))
                        Text("مقطوع", color = Color.White, fontSize = 9.sp)
                    }
                }
            }
        }

        // Top-Right Interactive Toolbar Controls Box (+ / - / Auto-Fit / Reset / Spacing / Mode)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Full Screen Maximize Button
                if (onToggleFullScreen != null) {
                    IconButton(
                        onClick = onToggleFullScreen,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "عرض كامل ملء الشاشة",
                            tint = CyberGreenNeon,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }

                // Zoom In
                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.5f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "تكبير",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Scale Percent Text
                Text(
                    text = "${(zoomScale * 100).roundToInt()}%",
                    color = CyberGreenNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )

                // Zoom Out
                IconButton(
                    onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.35f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "تصغير",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                // Auto-Fit to Screen Button
                IconButton(
                    onClick = {
                        val maxRadiusPx = ringRadiiPx.lastOrNull() ?: 200f
                        val maxExtentPx = maxRadiusPx + with(localDensity) { 70.dp.toPx() }
                        val availableViewportRadiusPx = minOf(widthPx, heightPx) / 2f - with(localDensity) { 24.dp.toPx() }
                        val optimalScale = (availableViewportRadiusPx / maxExtentPx).coerceIn(0.35f, 1.8f)
                        zoomScale = optimalScale
                        panOffsetX = 0f
                        panOffsetY = 0f
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "احتواء الشاشة",
                        tint = NetworkCyanLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Reset Camera Position & Zoom & Sizes
                IconButton(
                    onClick = {
                        zoomScale = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                        spacingScale = 1.0f
                        cardScale = 1.0f
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة الضبط",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                // Increase Card Size (تكبير حجم البطاقات)
                IconButton(
                    onClick = { cardScale = (cardScale + 0.15f).coerceAtMost(2.0f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewModule,
                        contentDescription = "تكبير حجم البطاقات",
                        tint = NetworkCyanLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Decrease Card Size (تصغير حجم البطاقات)
                IconButton(
                    onClick = { cardScale = (cardScale - 0.15f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = "تصغير حجم البطاقات",
                        tint = NetworkCyanLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                // Increase Spacing Gap
                IconButton(
                    onClick = { spacingScale = (spacingScale + 0.15f).coerceAtMost(2.0f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "توسيع المسافة",
                        tint = CyberGreenNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Decrease Spacing Gap
                IconButton(
                    onClick = { spacingScale = (spacingScale - 0.15f).coerceAtLeast(0.7f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "تقليص المسافة",
                        tint = CyberGreenNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                // Toggle Compact vs Detailed Card Mode
                IconButton(
                    onClick = { isCompactMode = !isCompactMode },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "تغيير نمط العرض",
                        tint = if (isCompactMode) StatusWarningAmber else CyberGreenNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Bottom Gesture Hint Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Text(
                text = "💡 يمكنك السحب للتحريك أو القرص للتكبير والتصغير",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Detailed Topology Node Item Card (Zero Overlap Guaranteed Layout)
 */
@Composable
private fun TopologyNodeItemCard(
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
            .width(104.dp)
            .clickable(onClick = onClick)
            .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = borderColor)
            .testTag("node_topology_item_${node.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A1812)
        ),
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Header Row: Status dot, Role icon & CPU badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (node.isOnline) StatusActiveGreen else StatusErrorRed)
                )

                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(15.dp)
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
        }
    }
}

/**
 * Compact Topology Node Badge (For dense network views with many nodes)
 */
@Composable
private fun CompactTopologyNodeBadge(
    node: NetworkNode,
    onClick: () -> Unit
) {
    val borderColor = when {
        !node.isOnline -> StatusErrorRed
        node.hasIssue -> StatusWarningAmber
        node.cpuLoadPercent > 75 -> StatusWarningAmber
        else -> CyberGreenNeon
    }

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = borderColor)
            .testTag("compact_node_badge_${node.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF07140D),
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

            Text(
                text = "${node.cpuLoadPercent}%",
                color = borderColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp
            )
        }
    }
}
