package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.Client
import com.example.ui.theme.parseClientColor

private val PillShape = RoundedCornerShape(14.dp)
private val PillPlacementSpec = spring<IntOffset>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

@Immutable
data class RibbonClientItem(
    val client: Client,
    val openCount: Int,
    val accent: Color,
    val isRtl: Boolean
)

@Composable
fun ClientSwitcherRibbon(
    workspace: WorkspaceData,
    selectedClientId: String?,
    onSelectClient: (String?) -> Unit,
    onReorderClients: (List<Client>) -> Unit,
    onOpenNewClient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clients = workspace.clients
    val openCountByClient = workspace.openCountByClient
    val totalOpen = workspace.totalOpen
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isRtlLayout = LocalLayoutDirection.current == LayoutDirection.Rtl

    val ribbonItems = remember(clients, openCountByClient, workspace.clientAccentById, workspace.clientNameRtlById) {
        clients.map { client ->
            RibbonClientItem(
                client = client,
                openCount = openCountByClient[client.id] ?: 0,
                accent = workspace.clientAccentById[client.id] ?: parseClientColor(client.colorHex),
                isRtl = workspace.clientNameRtlById[client.id] ?: client.name.isRtlText()
            )
        }
    }

    var localItems by remember { mutableStateOf(ribbonItems) }
    var draggedClientId by remember { mutableStateOf<String?>(null) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var edgeScrollSpeed by remember { mutableFloatStateOf(0f) }
    var initialOrderIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val latestItems by rememberUpdatedState(ribbonItems)
    val latestOnReorderClients by rememberUpdatedState(onReorderClients)

    LaunchedEffect(ribbonItems) {
        if (draggedClientId == null) {
            localItems = ribbonItems
        }
    }

    LaunchedEffect(selectedClientId, localItems.size) {
        if (draggedClientId != null) return@LaunchedEffect
        val targetIndex = if (selectedClientId == null) {
            0
        } else {
            val idx = localItems.indexOfFirst { it.client.id == selectedClientId }
            if (idx >= 0) idx + 1 else 0
        }
        val visible = listState.layoutInfo.visibleItemsInfo
        val alreadyVisible = visible.any { it.index == targetIndex && it.offset >= 0 }
        if (!alreadyVisible) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    val edgeThresholdPx = with(density) { 56.dp.toPx() }
    val maxScrollSpeedPxPerSec = with(density) { 420.dp.toPx() }

    fun updateEdgeScrollSpeed() {
        val activeId = draggedClientId
        if (activeId == null) {
            edgeScrollSpeed = 0f
            return
        }
        val layoutInfo = listState.layoutInfo
        val draggedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "pill_$activeId" }
        if (draggedInfo == null) {
            edgeScrollSpeed = 0f
            return
        }
        val visualStart = draggedInfo.offset + dragDeltaX
        val visualEnd = visualStart + draggedInfo.size
        val viewportEnd = (layoutInfo.viewportEndOffset - layoutInfo.beforeContentPadding).toFloat()

        edgeScrollSpeed = when {
            visualEnd > viewportEnd - edgeThresholdPx -> {
                val factor = ((visualEnd - (viewportEnd - edgeThresholdPx)) / edgeThresholdPx)
                    .coerceIn(0f, 1f)
                factor * maxScrollSpeedPxPerSec
            }
            visualStart < edgeThresholdPx -> {
                val factor = ((edgeThresholdPx - visualStart) / edgeThresholdPx)
                    .coerceIn(0f, 1f)
                -factor * maxScrollSpeedPxPerSec
            }
            else -> 0f
        }
    }

    fun trySwapDraggedItem() {
        val activeId = draggedClientId ?: return
        val layoutInfo = listState.layoutInfo
        val spacing = layoutInfo.mainAxisItemSpacing
        val draggedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "pill_$activeId" }
            ?: return
        val currentIdx = localItems.indexOfFirst { it.client.id == activeId }
        if (currentIdx == -1) return
        // Wait for LazyRow layout pass to reflect previous swap before swapping again
        if (draggedInfo.index != currentIdx + 1) return

        val draggedVisualCenter = draggedInfo.offset + dragDeltaX + draggedInfo.size / 2f

        if (currentIdx < localItems.lastIndex) {
            val nextItem = localItems[currentIdx + 1]
            val nextInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "pill_${nextItem.client.id}" }
            if (nextInfo != null && nextInfo.index == currentIdx + 2) {
                val nextCenter = nextInfo.offset + nextInfo.size / 2f
                if (draggedVisualCenter > nextCenter) {
                    val savedIndex = listState.firstVisibleItemIndex
                    val savedOffset = listState.firstVisibleItemScrollOffset
                    localItems = localItems.toMutableList().apply {
                        add(currentIdx + 1, removeAt(currentIdx))
                    }
                    // Only restore scroll anchor if the dragged pill itself was the first visible item
                    if (savedIndex == currentIdx + 1) {
                        listState.requestScrollToItem(savedIndex, savedOffset)
                    }
                    dragDeltaX -= (nextInfo.size + spacing)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    return
                }
            }
        }
        if (currentIdx > 0) {
            val prevItem = localItems[currentIdx - 1]
            val prevInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "pill_${prevItem.client.id}" }
            if (prevInfo != null && prevInfo.index == currentIdx) {
                val prevCenter = prevInfo.offset + prevInfo.size / 2f
                if (draggedVisualCenter < prevCenter) {
                    val savedIndex = listState.firstVisibleItemIndex
                    val savedOffset = listState.firstVisibleItemScrollOffset
                    localItems = localItems.toMutableList().apply {
                        add(currentIdx - 1, removeAt(currentIdx))
                    }
                    if (savedIndex == currentIdx || savedIndex == currentIdx + 1) {
                        listState.requestScrollToItem(savedIndex, savedOffset)
                    }
                    dragDeltaX += (prevInfo.size + spacing)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    val isEdgeScrolling = draggedClientId != null && edgeScrollSpeed != 0f
    LaunchedEffect(isEdgeScrolling) {
        if (!isEdgeScrolling) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (draggedClientId != null && edgeScrollSpeed != 0f) {
            val frameNanos = withFrameNanos { it }
            val dtSec = if (lastFrameNanos == 0L) {
                0f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            }
            lastFrameNanos = frameNanos
            val speed = edgeScrollSpeed
            if (dtSec > 0f && speed != 0f) {
                val consumed = listState.scrollBy(speed * dtSec)
                if (consumed != 0f) {
                    dragDeltaX += consumed
                    trySwapDraggedItem()
                }
            }
        }
    }

    // Pre-resolve colors, TextStyles (with color baked in), and shared ripple ONCE at ribbon level
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val pillRipple = remember { ripple(bounded = true) }

    val selectedContainerColor = colorScheme.onBackground
    val unselectedContainerColor = colorScheme.surfaceContainerLowest
    val selectedContentColor = colorScheme.background
    val unselectedContentColor = colorScheme.onSurface
    val selectedBadgeBg = remember(selectedContentColor) { selectedContentColor.copy(alpha = 0.16f) }
    val unselectedBadgeBg = colorScheme.surfaceVariant
    val outlineColor = colorScheme.outline
    val primaryColor = colorScheme.primary

    val selectedLabelLtr = remember(typography, selectedContentColor) {
        typography.labelLarge.withBidi(false).copy(color = selectedContentColor)
    }
    val selectedLabelRtl = remember(typography, selectedContentColor) {
        typography.labelLarge.withBidi(true).copy(color = selectedContentColor)
    }
    val unselectedLabelLtr = remember(typography, unselectedContentColor) {
        typography.labelLarge.withBidi(false).copy(color = unselectedContentColor)
    }
    val unselectedLabelRtl = remember(typography, unselectedContentColor) {
        typography.labelLarge.withBidi(true).copy(color = unselectedContentColor)
    }
    val selectedBadgeStyle = remember(typography, selectedContentColor) {
        typography.labelSmall.copy(color = selectedContentColor)
    }
    val unselectedBadgeStyle = remember(typography, unselectedContentColor) {
        typography.labelSmall.copy(color = unselectedContentColor)
    }
    val newClientLabelStyle = remember(typography, primaryColor) {
        typography.labelLarge.withBidi(false).copy(color = primaryColor)
    }

    val allClientsLabel = stringResource(R.string.workspace_all_clients)
    val newClientLabel = stringResource(R.string.workspace_new_client)
    val canReorder = localItems.size > 1

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(isRtlLayout) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (localItems.size < 2) return@detectDragGesturesAfterLongPress
                        val layoutInfo = listState.layoutInfo
                        val touchX = if (isRtlLayout) {
                            (size.width - offset.x) - layoutInfo.beforeContentPadding
                        } else {
                            offset.x - layoutInfo.beforeContentPadding
                        }
                        val hitItem = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            touchX >= item.offset && touchX <= item.offset + item.size
                        } ?: return@detectDragGesturesAfterLongPress

                        val clientIdx = hitItem.index - 1 // Index 0 is "All Clients"
                        if (clientIdx in localItems.indices) {
                            draggedClientId = localItems[clientIdx].client.id
                            initialOrderIds = localItems.map { it.client.id }
                            dragDeltaX = 0f
                            edgeScrollSpeed = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (draggedClientId == null) return@detectDragGesturesAfterLongPress
                        change.consume()
                        val signedDeltaX = if (isRtlLayout) -dragAmount.x else dragAmount.x
                        dragDeltaX += signedDeltaX
                        trySwapDraggedItem()
                        updateEdgeScrollSpeed()
                    },
                    onDragEnd = {
                        val active = draggedClientId != null
                        val finalOrder = localItems
                        val changed = active && finalOrder.map { it.client.id } != initialOrderIds
                        draggedClientId = null
                        dragDeltaX = 0f
                        edgeScrollSpeed = 0f
                        if (changed) {
                            latestOnReorderClients(finalOrder.map { it.client })
                        }
                    },
                    onDragCancel = {
                        draggedClientId = null
                        dragDeltaX = 0f
                        edgeScrollSpeed = 0f
                        localItems = latestItems
                    }
                )
            }
            .testTag("client_switcher_ribbon")
    ) {
        item(key = "pill_all", contentType = "pill_all") {
            val isSelected = selectedClientId == null
            FastClientRibbonPill(
                label = allClientsLabel,
                badgeCount = totalOpen,
                accentColor = primaryColor,
                showDot = false,
                showDragAffordance = false,
                selected = isSelected,
                isDragging = false,
                containerColor = if (isSelected) selectedContainerColor else unselectedContainerColor,
                gripColor = Color.Transparent,
                badgeBgColor = if (isSelected) selectedBadgeBg else unselectedBadgeBg,
                outlineColor = outlineColor,
                labelStyle = if (isSelected) selectedLabelLtr else unselectedLabelLtr,
                badgeStyle = if (isSelected) selectedBadgeStyle else unselectedBadgeStyle,
                rippleIndication = pillRipple,
                onClick = { onSelectClient(null) },
                testTag = "client_tab_all"
            )
        }

        items(
            items = localItems,
            key = { "pill_${it.client.id}" },
            contentType = { "pill_client" }
        ) { item ->
            val clientId = item.client.id
            val isDragging = draggedClientId == clientId
            val isSelected = selectedClientId == clientId
            val contentColor = if (isSelected) selectedContentColor else unselectedContentColor
            val gripAlpha = if (isDragging) 0.9f else 0.35f

            val baseModifier = if (isDragging) {
                Modifier
            } else {
                Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = PillPlacementSpec
                )
            }

            // Static graphicsLayer keeps RenderNode intact; only the active pill reads dragDeltaX
            val dragModifier = baseModifier
                .zIndex(if (isDragging) 10f else 0f)
                .graphicsLayer {
                    if (draggedClientId == clientId) {
                        translationX = if (isRtlLayout) -dragDeltaX else dragDeltaX
                        scaleX = 1.05f
                        scaleY = 1.05f
                    } else {
                        translationX = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                }

            FastClientRibbonPill(
                label = item.client.name,
                badgeCount = item.openCount,
                accentColor = item.accent,
                showDot = true,
                showDragAffordance = canReorder,
                selected = isSelected,
                isDragging = isDragging,
                containerColor = if (isSelected) selectedContainerColor else unselectedContainerColor,
                gripColor = contentColor.copy(alpha = gripAlpha),
                badgeBgColor = if (isSelected) selectedBadgeBg else unselectedBadgeBg,
                outlineColor = outlineColor,
                labelStyle = when {
                    isSelected && item.isRtl -> selectedLabelRtl
                    isSelected -> selectedLabelLtr
                    item.isRtl -> unselectedLabelRtl
                    else -> unselectedLabelLtr
                },
                badgeStyle = if (isSelected) selectedBadgeStyle else unselectedBadgeStyle,
                rippleIndication = pillRipple,
                onClick = { onSelectClient(clientId) },
                testTag = "client_tab_$clientId",
                modifier = dragModifier
            )
        }

        item(key = "pill_new_client", contentType = "pill_new") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .background(unselectedContainerColor, PillShape)
                    .drawWithCache {
                        val strokePx = 1.dp.toPx()
                        val half = strokePx / 2f
                        val r = 14.dp.toPx()
                        val cornerRadius = CornerRadius(r, r)
                        val stroke = Stroke(strokePx)
                        val strokeSize = Size(size.width - strokePx, size.height - strokePx)
                        val topLeft = Offset(half, half)
                        onDrawBehind {
                            drawRoundRect(
                                color = outlineColor,
                                topLeft = topLeft,
                                size = strokeSize,
                                cornerRadius = cornerRadius,
                                style = stroke
                            )
                        }
                    }
                    .clickable(
                        interactionSource = null,
                        indication = pillRipple,
                        onClick = onOpenNewClient
                    )
                    .padding(horizontal = 14.dp)
                    .testTag("ribbon_new_client_button")
            ) {
                Spacer(
                    modifier = Modifier
                        .size(14.dp)
                        .drawWithCache {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val half = size.minDimension * 0.42f
                            val strokeW = 2.dp.toPx()
                            val hStart = Offset(cx - half, cy)
                            val hEnd = Offset(cx + half, cy)
                            val vStart = Offset(cx, cy - half)
                            val vEnd = Offset(cx, cy + half)
                            onDrawBehind {
                                drawLine(primaryColor, hStart, hEnd, strokeW, StrokeCap.Round)
                                drawLine(primaryColor, vStart, vEnd, strokeW, StrokeCap.Round)
                            }
                        }
                )
                BasicText(
                    text = newClientLabel,
                    style = newClientLabelStyle,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FastClientRibbonPill(
    label: String,
    badgeCount: Int,
    accentColor: Color,
    showDot: Boolean,
    showDragAffordance: Boolean,
    selected: Boolean,
    isDragging: Boolean,
    containerColor: Color,
    gripColor: Color,
    badgeBgColor: Color,
    outlineColor: Color,
    labelStyle: TextStyle,
    badgeStyle: TextStyle,
    rippleIndication: Indication,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    // Keep modifier chain identical whether dragging or not; draw border purely in drawBehind.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .height(48.dp)
            .background(containerColor, PillShape)
            .drawWithCache {
                val r = 14.dp.toPx()
                val cornerRadius = CornerRadius(r, r)
                val strokePx1 = 1.dp.toPx()
                val strokePx2 = 2.dp.toPx()
                val stroke1 = Stroke(strokePx1)
                val stroke2 = Stroke(strokePx2)
                val size1 = Size(size.width - strokePx1, size.height - strokePx1)
                val size2 = Size(size.width - strokePx2, size.height - strokePx2)
                val offset1 = Offset(strokePx1 / 2f, strokePx1 / 2f)
                val offset2 = Offset(strokePx2 / 2f, strokePx2 / 2f)
                onDrawBehind {
                    if (isDragging) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = offset2,
                            size = size2,
                            cornerRadius = cornerRadius,
                            style = stroke2
                        )
                    } else if (!selected) {
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = offset1,
                            size = size1,
                            cornerRadius = cornerRadius,
                            style = stroke1
                        )
                    }
                }
            }
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = null,
                indication = rippleIndication,
                onClick = onClick
            )
            .padding(
                start = if (showDragAffordance) 10.dp else 14.dp,
                end = 14.dp
            )
            .testTag(testTag)
    ) {
        if (showDragAffordance) {
            Spacer(
                modifier = Modifier
                    .width(8.dp)
                    .height(14.dp)
                    .drawBehind {
                        val r = 1.15.dp.toPx()
                        val x1 = size.width * 0.25f
                        val x2 = size.width * 0.75f
                        val y1 = size.height * 0.2f
                        val y2 = size.height * 0.5f
                        val y3 = size.height * 0.8f
                        drawCircle(gripColor, r, Offset(x1, y1))
                        drawCircle(gripColor, r, Offset(x2, y1))
                        drawCircle(gripColor, r, Offset(x1, y2))
                        drawCircle(gripColor, r, Offset(x2, y2))
                        drawCircle(gripColor, r, Offset(x1, y3))
                        drawCircle(gripColor, r, Offset(x2, y3))
                    }
            )
        }
        if (showDot) {
            Spacer(
                modifier = Modifier
                    .size(9.dp)
                    .drawBehind {
                        drawCircle(color = accentColor, radius = size.minDimension / 2f)
                    }
            )
        }
        BasicText(
            text = label,
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(badgeBgColor, CircleShape)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            BasicText(
                text = badgeCount.toString(),
                style = badgeStyle,
                maxLines = 1
            )
        }
    }
}
