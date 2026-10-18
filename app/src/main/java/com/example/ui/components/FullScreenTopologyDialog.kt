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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
 * Full-Screen Cyber Network Topology Dialog with Collapsible Controls
 * and multi-layout switching across all 6 cybernetic layouts.
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

    var currentLayout by remember { mutableStateOf(NetworkTopologyLayout.HIERARCHICAL_TREE) }
    var isControlsExpanded by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(0.85f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var spacingScale by remember { mutableFloatStateOf(1.0f) }
    var cardScale by remember { mutableFloatStateOf(1.0f) }
    var isCompactMode by remember { mutableStateOf(nodes.size > 8) }
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

    val secondaryPulseProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_pulse"
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

    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep_fullscreen"
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

    val cardDimensionsPx = remember(isCompactMode, cardScale, localDensity) {
        val cardWidthDp = if (isCompactMode) 80.dp else 108.dp
        val cardHeightDp = if (isCompactMode) 38.dp else 70.dp
        val w = with(localDensity) { (cardWidthDp * cardScale).toPx() }
        val h = with(localDensity) { (cardHeightDp * cardScale).toPx() }
        w to h
    }

    // Partition nodes into multi-ring concentric orbits
    val ringNodes = remember(satelliteNodes) {
        val list = mutableListOf<List<NetworkNode>>()
        var index = 0
        val capacities = listOf(6, 10, 14, 18, 22, 26)
        var capIdx = 0
        while (index < satelliteNodes.size) {
            val cap = capacities.getOrElse(capIdx) { 22 }
            val end = (index + cap).coerceAtMost(satelliteNodes.size)
            list.add(satelliteNodes.subList(index, end))
            index = end
            capIdx++
        }
        list
    }

    val ringRadiiPx = remember(ringNodes, spacingScale, cardDimensionsPx, localDensity) {
        val (cardWidthPx, cardHeightPx) = cardDimensionsPx
        val cardDiagPx = kotlin.math.sqrt(cardWidthPx * cardWidthPx + cardHeightPx * cardHeightPx)
        val centerNodeRadiusPx = with(localDensity) { 54.dp.toPx() }
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

    val centerNodeRelOffset = remember(currentLayout, spacingScale) {
        when (currentLayout) {
            NetworkTopologyLayout.HIERARCHICAL_TREE -> Offset(0f, -160f * spacingScale)
            NetworkTopologyLayout.CYBER_MATRIX_GRID -> Offset(0f, -140f * spacingScale)
            else -> Offset(0f, 0f)
        }
    }

    val nodeRelPositions = remember(currentLayout, satelliteNodes, ringNodes, ringRadiiPx, spacingScale, cardDimensionsPx) {
        val positions = mutableListOf<Offset>()
        val (cardWidthPx, _) = cardDimensionsPx
        val total = satelliteNodes.size

        when (currentLayout) {
            NetworkTopologyLayout.HIERARCHICAL_TREE -> {
                if (total <= 4) {
                    val tierY = 90f * spacingScale
                    val stepX = (cardWidthPx * 1.4f * spacingScale).coerceAtLeast(115f)
                    satelliteNodes.forEachIndexed { i, _ ->
                        val x = (i - (total - 1) / 2f) * stepX
                        positions.add(Offset(x, tierY))
                    }
                } else if (total <= 10) {
                    val tier1Count = ((total + 1) / 2).coerceIn(3, 5)
                    val tier2Count = total - tier1Count

                    val tier1Y = -15f * spacingScale
                    val tier1StepX = (cardWidthPx * 1.35f * spacingScale).coerceAtLeast(110f)
                    for (i in 0 until tier1Count) {
                        val x = (i - (tier1Count - 1) / 2f) * tier1StepX
                        positions.add(Offset(x, tier1Y))
                    }

                    val tier2Y = 160f * spacingScale
                    val tier2StepX = (cardWidthPx * 1.3f * spacingScale).coerceAtLeast(100f)
                    for (i in 0 until tier2Count) {
                        val x = (i - (tier2Count - 1) / 2f) * tier2StepX
                        positions.add(Offset(x, tier2Y))
                    }
                } else {
                    val tier1Count = 4
                    val tier2Count = ((total - tier1Count) * 0.55f).roundToInt().coerceAtLeast(4)
                    val tier3Count = total - tier1Count - tier2Count

                    val tier1Y = -40f * spacingScale
                    val tier1StepX = (cardWidthPx * 1.4f * spacingScale).coerceAtLeast(115f)
                    for (i in 0 until tier1Count) {
                        val x = (i - (tier1Count - 1) / 2f) * tier1StepX
                        positions.add(Offset(x, tier1Y))
                    }

                    val tier2Y = 90f * spacingScale
                    val tier2StepX = (cardWidthPx * 1.25f * spacingScale).coerceAtLeast(95f)
                    for (i in 0 until tier2Count) {
                        val x = (i - (tier2Count - 1) / 2f) * tier2StepX
                        positions.add(Offset(x, tier2Y))
                    }

                    val tier3Y = 210f * spacingScale
                    val tier3StepX = (cardWidthPx * 1.2f * spacingScale).coerceAtLeast(90f)
                    for (i in 0 until tier3Count) {
                        val x = (i - (tier3Count - 1) / 2f) * tier3StepX
                        positions.add(Offset(x, tier3Y))
                    }
                }
            }

            NetworkTopologyLayout.ORBITAL_SPIDERWEB -> {
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
            }

            NetworkTopologyLayout.HEXAGONAL_MESH -> {
                val hexRadiusBase = 130f * spacingScale
                val hexStepRadius = 115f * spacingScale

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
                val cols = when {
                    total <= 4 -> 2
                    total <= 9 -> 3
                    total <= 16 -> 4
                    else -> 5
                }
                val stepX = (cardWidthPx * 1.35f * spacingScale).coerceAtLeast(105f)
                val stepY = 120f * spacingScale
                val startY = -40f * spacingScale

                satelliteNodes.forEachIndexed { i, _ ->
                    val row = i / cols
                    val col = i % cols
                    val x = (col - (cols - 1) / 2f) * stepX
                    val y = startY + (row * stepY)
                    positions.add(Offset(x, y))
                }
            }

            NetworkTopologyLayout.RADIAL_CONSTELLATION -> {
                val clusterCount = when {
                    total <= 5 -> 2
                    total <= 12 -> 3
                    else -> 4
                }
                val clusterRadius = 190f * spacingScale
                val subStarRadius = 75f * spacingScale

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
                val halfCount = (total + 1) / 2
                val innerRadius = 140f * spacingScale
                val outerRadius = 240f * spacingScale

                satelliteNodes.forEachIndexed { i, _ ->
                    if (i < halfCount) {
                        val angle = (i * (2 * Math.PI / halfCount)) - (Math.PI / 2)
                        val x = innerRadius * cos(angle).toFloat()
                        val y = innerRadius * sin(angle).toFloat()
                        positions.add(Offset(x, y))
                    } else {
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030906))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.20f, 3.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                .testTag("fullscreen_topology_dialog")
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            fun autoFitToScreen() {
                if (satelliteNodes.isEmpty()) {
                    zoomScale = 1.0f
                    panOffsetX = 0f
                    panOffsetY = 0f
                    return
                }

                val allOffsets = nodeRelPositions + centerNodeRelOffset
                val minX = allOffsets.minOfOrNull { it.x } ?: -180f
                val maxX = allOffsets.maxOfOrNull { it.x } ?: 180f
                val minY = allOffsets.minOfOrNull { it.y } ?: -180f
                val maxY = allOffsets.maxOfOrNull { it.y } ?: 180f

                val spanX = (maxX - minX).coerceAtLeast(200f) + with(localDensity) { 100.dp.toPx() }
                val spanY = (maxY - minY).coerceAtLeast(200f) + with(localDensity) { 100.dp.toPx() }

                val availableW = widthPx.coerceAtLeast(300f) - with(localDensity) { 40.dp.toPx() }
                val availableH = heightPx.coerceAtLeast(300f) - with(localDensity) { 80.dp.toPx() }

                val optimalScale = minOf(availableW / spanX, availableH / spanY).coerceIn(0.30f, 1.4f)
                zoomScale = optimalScale

                val midX = (minX + maxX) / 2f
                val midY = (minY + maxY) / 2f
                panOffsetX = -midX * optimalScale
                panOffsetY = -midY * optimalScale
            }

            var hasAutoFittedInitial by remember { mutableStateOf(false) }
            LaunchedEffect(widthPx, heightPx, currentLayout) {
                if (widthPx > 0 && heightPx > 0) {
                    autoFitToScreen()
                }
            }

            // 1. Drawing Canvas (6 Cybernetic Layouts)
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

                when (currentLayout) {
                    NetworkTopologyLayout.HIERARCHICAL_TREE -> {
                        val total = satelliteNodes.size
                        val tier1Count = when {
                            total <= 4 -> total
                            total <= 10 -> ((total + 1) / 2).coerceIn(3, 5)
                            else -> 4
                        }

                        val trunkJunctionY = routerAbsolutePos.y + 60f * spacingScale
                        drawLine(
                            color = CyberGreenNeon.copy(alpha = 0.85f),
                            start = routerAbsolutePos,
                            end = Offset(routerAbsolutePos.x, trunkJunctionY),
                            strokeWidth = 2.8f,
                            cap = StrokeCap.Round
                        )

                        val tier1Offsets = nodeRelPositions.take(tier1Count).map { centerOffset + it }
                        if (tier1Offsets.isNotEmpty()) {
                            val minX = tier1Offsets.minOf { it.x }
                            val maxX = tier1Offsets.maxOf { it.x }

                            drawLine(
                                color = NetworkCyan.copy(alpha = 0.85f),
                                start = Offset(minX, trunkJunctionY),
                                end = Offset(maxX, trunkJunctionY),
                                strokeWidth = 2.2f,
                                cap = StrokeCap.Round
                            )

                            tier1Offsets.forEach { nodePos ->
                                drawLine(
                                    color = CyberGreenNeon.copy(alpha = 0.75f),
                                    start = Offset(nodePos.x, trunkJunctionY),
                                    end = nodePos,
                                    strokeWidth = 1.8f
                                )
                                drawCircle(color = CyberGreenNeon, radius = 4f, center = Offset(nodePos.x, trunkJunctionY))
                                val pulseY = trunkJunctionY + (nodePos.y - trunkJunctionY) * pulseProgress
                                drawCircle(color = Color.White, radius = 3.2f, center = Offset(nodePos.x, pulseY))
                            }
                        }

                        if (total > tier1Count) {
                            val tier2Count = if (total <= 10) total - tier1Count else ((total - tier1Count) * 0.55f).roundToInt().coerceAtLeast(4)
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
                        ringRadiiPx.forEach { radiusPx ->
                            drawCircle(
                                color = CyberGreenNeon.copy(alpha = 0.12f),
                                radius = radiusPx,
                                center = routerAbsolutePos,
                                style = Stroke(
                                    width = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                        }

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
                                strokeWidth = 1.8f,
                                cap = StrokeCap.Round
                            )

                            if (node.isOnline) {
                                val pulseOffset = routerAbsolutePos + (absNodeOffset - routerAbsolutePos) * pulseProgress
                                drawCircle(color = CyberGreenGlow, radius = 7f, center = pulseOffset)
                                drawCircle(color = Color.White, radius = 3.5f, center = pulseOffset)
                            }
                        }
                    }

                    NetworkTopologyLayout.HEXAGONAL_MESH -> {
                        for (r in listOf(130f, 245f, 360f)) {
                            drawCircle(
                                color = NetworkCyan.copy(alpha = 0.08f),
                                radius = r * spacingScale,
                                center = routerAbsolutePos,
                                style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f))
                            )
                        }

                        nodeRelPositions.forEachIndexed { i, pos1 ->
                            val abs1 = centerOffset + pos1
                            drawLine(color = CyberGreenNeon.copy(alpha = 0.5f), start = routerAbsolutePos, end = abs1, strokeWidth = 1.4f)
                            val nextIdx = (i + 1) % nodeRelPositions.size
                            val pos2 = nodeRelPositions[nextIdx]
                            drawLine(color = NetworkCyan.copy(alpha = 0.25f), start = abs1, end = centerOffset + pos2, strokeWidth = 1f)
                        }

                        val radarRad = Math.toRadians(radarAngle.toDouble())
                        val sweepExtent = 340f * spacingScale
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
                        nodeRelPositions.forEachIndexed { index, relPos ->
                            val absPos = centerOffset + relPos
                            val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed
                            val cornerOffset = Offset(absPos.x, routerAbsolutePos.y)

                            val circuitPath = Path().apply {
                                moveTo(routerAbsolutePos.x, routerAbsolutePos.y)
                                lineTo(cornerOffset.x, cornerOffset.y)
                                lineTo(absPos.x, absPos.y)
                            }

                            val trackColor = if (!node.isOnline) StatusErrorRed.copy(alpha = 0.6f) else CyberGreenNeon.copy(alpha = 0.6f)
                            drawPath(circuitPath, color = trackColor, style = Stroke(width = 1.6f))
                            drawCircle(color = NetworkCyan, radius = 3.5f, center = cornerOffset)

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
                        val total = satelliteNodes.size
                        val clusterCount = when {
                            total <= 5 -> 2
                            total <= 12 -> 3
                            else -> 4
                        }

                        for (c in 0 until clusterCount) {
                            val anchorOffset = nodeRelPositions.getOrNull(c)
                            if (anchorOffset != null) {
                                val absAnchor = centerOffset + anchorOffset
                                drawLine(color = CyberGreenNeon.copy(alpha = 0.85f), start = routerAbsolutePos, end = absAnchor, strokeWidth = 2.0f)
                            }
                        }

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
                            drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 2f, center = absPos)
                        }
                    }

                    NetworkTopologyLayout.DUAL_RING_TOKEN -> {
                        val halfCount = (satelliteNodes.size + 1) / 2
                        val innerRadius = 140f * spacingScale
                        val outerRadius = 240f * spacingScale

                        drawCircle(color = NetworkCyan.copy(alpha = 0.25f), radius = innerRadius, center = routerAbsolutePos, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)))
                        drawCircle(color = CyberGreenNeon.copy(alpha = 0.25f), radius = outerRadius, center = routerAbsolutePos, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)))

                        nodeRelPositions.forEachIndexed { idx, relPos ->
                            val absPos = centerOffset + relPos
                            if (idx < halfCount) {
                                drawLine(color = NetworkCyan.copy(alpha = 0.6f), start = routerAbsolutePos, end = absPos, strokeWidth = 1.4f)
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

                        val innerAngle = (pulseProgress * 2 * Math.PI).toFloat()
                        val outerAngle = (-pulseProgress * 2 * Math.PI).toFloat()
                        val innerPhoton = Offset(routerAbsolutePos.x + innerRadius * cos(innerAngle), routerAbsolutePos.y + innerRadius * sin(innerAngle))
                        val outerPhoton = Offset(routerAbsolutePos.x + outerRadius * cos(outerAngle), routerAbsolutePos.y + outerRadius * sin(outerAngle))

                        drawCircle(color = Color.White, radius = 4f, center = innerPhoton)
                        drawCircle(color = NetworkCyanLight, radius = 7f, center = innerPhoton)
                        drawCircle(color = Color.White, radius = 4f, center = outerPhoton)
                        drawCircle(color = CyberGreenGlow, radius = 7f, center = outerPhoton)
                    }
                }

                // Pulsing glow wave
                drawCircle(
                    color = CyberGreenNeon.copy(alpha = centralGlowAlpha),
                    radius = 54f * centralGlowScale,
                    center = routerAbsolutePos
                )
            }

            // 2. Center Router Node
            val centerDpX = with(localDensity) { ((centerNodeRelOffset.x * zoomScale) + panOffsetX).toDp() }
            val centerDpY = with(localDensity) { ((centerNodeRelOffset.y * zoomScale) + panOffsetY).toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = centerDpX, y = centerDpY)
                    .shadow(20.dp, CircleShape, spotColor = CyberGreenNeon)
                    .clip(CircleShape)
                    .background(Color(0xFF003820))
                    .border(2.5.dp, CyberGreenNeon, CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = CyberGreenNeon, modifier = Modifier.size(24.dp))
                    Text(centralRouterName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(centralRouterIp, color = CyberGreenNeon, fontSize = 9.sp)
                }
            }

            // 3. Satellite Nodes
            nodeRelPositions.forEachIndexed { index, relOffset ->
                val node = satelliteNodes.getOrNull(index) ?: return@forEachIndexed
                val nodeDpX = with(localDensity) { (relOffset.x * zoomScale + panOffsetX).toDp() }
                val nodeDpY = with(localDensity) { (relOffset.y * zoomScale + panOffsetY).toDp() }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = nodeDpX, y = nodeDpY)
                        .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                ) {
                    val borderColor = when {
                        !node.isOnline -> StatusErrorRed
                        node.hasIssue -> StatusWarningAmber
                        else -> CyberGreenNeon
                    }

                    Surface(
                        modifier = Modifier
                            .clickable { onNodeClick(node) }
                            .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = borderColor),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0A1812),
                        border = BorderStroke(1.2.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (node.isOnline) StatusActiveGreen else StatusErrorRed))
                            Column {
                                Text(node.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                                Text("${node.ipAddress} • ${node.connectedUsers} مستخدم", color = NetworkCyanLight, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }

            // 4. Top Header & Search Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyberGreenNeon.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CellTower, contentDescription = null, tint = CyberGreenNeon, modifier = Modifier.size(20.dp))
                            Column {
                                Text("خريطة وأبراج الشبكة كاملة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("$totalOnline / ${nodes.size} متصل • $totalUsers مستخدم على النقاط", color = CyberGreenNeon, fontSize = 10.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = { isSearchExpanded = !isSearchExpanded }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "بحث", tint = if (isSearchExpanded) CyberGreenNeon else Color.White)
                            }

                            Surface(
                                modifier = Modifier
                                    .clickable { onDismiss() }
                                    .clip(RoundedCornerShape(8.dp)),
                                color = StatusErrorRed.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, StatusErrorRed.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.CloseFullscreen, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text("تصغير", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isSearchExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("بحث عن برج أو نقطة...", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.White)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
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

            // 5. Dropdown-Style Floating Control Bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                AnimatedVisibility(
                    visible = !isControlsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .clickable { isControlsExpanded = true }
                            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = CyberGreenNeon),
                        color = Color(0xFF04140D).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, CyberGreenNeon.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(currentLayout.icon, contentDescription = null, tint = CyberGreenNeon, modifier = Modifier.size(15.dp))
                            Text(currentLayout.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("• ${(zoomScale * 100).roundToInt()}%", color = NetworkCyanLight, fontSize = 10.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "فتح الأدوات", tint = CyberGreenNeon, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isControlsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFF04140E).copy(alpha = 0.96f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, CyberGreenNeon.copy(alpha = 0.7f)),
                        shadowElevation = 14.dp,
                        modifier = Modifier.widthIn(max = 290.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyberGreenNeon, modifier = Modifier.size(16.dp))
                                    Text("أدوات التحكم والأنماط", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                IconButton(onClick = { isControlsExpanded = false }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ArrowDropUp, contentDescription = "إخفاء", tint = CyberGreenNeon, modifier = Modifier.size(22.dp))
                                }
                            }

                            // 6 Layouts Grid
                            Text("شكل الشبكة السيبرانية:", color = CyberGreenNeon, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                NetworkTopologyLayout.entries.chunked(3).forEach { rowLayouts ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                                Column(modifier = Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(layout.icon, contentDescription = null, tint = if (isSelected) CyberGreenNeon else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                                                    Text(layout.title, color = if (isSelected) CyberGreenNeon else Color.White, fontSize = 8.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Zoom controls
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.20f) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text("${(zoomScale * 100).roundToInt()}%", color = CyberGreenNeon, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                IconButton(onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(3.0f) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { autoFitToScreen() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.AspectRatio, contentDescription = "احتواء", tint = NetworkCyanLight, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { zoomScale = 0.85f; panOffsetX = 0f; panOffsetY = 0f }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = "إعادة الضبط", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            // Spacing
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("المسافات والتباعد:", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { spacingScale = (spacingScale - 0.15f).coerceAtLeast(0.55f) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.UnfoldLess, contentDescription = "تضييق", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(onClick = { spacingScale = (spacingScale + 0.15f).coerceAtMost(2.0f) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.UnfoldMore, contentDescription = "توسيع", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
