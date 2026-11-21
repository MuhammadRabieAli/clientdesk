package com.example.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Client
import com.example.data.ClientTask
import com.example.data.TaskPriority
import com.example.data.ThemeMode
import com.example.ui.theme.AlabasterCanvas
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianInk
import com.example.ui.theme.PurePaper
import com.example.ui.theme.SlateInk
import com.example.ui.theme.StudioForest
import com.example.ui.theme.StudioTerracotta
import com.example.ui.theme.WarmSand
import com.example.ui.theme.parseClientColor
import com.example.widget.LargeClientWidgetProvider
import com.example.widget.MediumClientWidgetProvider
import com.example.widget.SmallClientWidgetProvider
import com.example.widget.WidgetSizeSpec

private val WidgetDarkCardBg = Color(0xFF16191D)
private val WidgetDarkBorder = Color(0xFF2B313A)
private val WidgetDarkRowBg = Color(0xFF21262D)
private val WidgetDarkRowBorder = Color(0xFF303742)
private val WidgetDarkPillBg = Color(0xFF252B33)
private val WidgetDarkDueBg = Color(0xFF2B323C)
private val WidgetDarkPrimaryText = Color(0xFFF4F1EC)
private val WidgetDarkSecondaryText = Color(0xFFA9B2BD)
private val WidgetDarkMutedText = Color(0xFF79838F)
private val WidgetDarkEmerald = Color(0xFF48C78E)
private val WidgetDarkProgressTrack = Color(0xFF2C333D)

@Composable
fun WidgetsStudioScreen(
    workspace: WorkspaceData,
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggleTaskDone: (ClientTask) -> Unit,
    onOpenAddTaskForClient: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clients = workspace.clients
    val tasksByClient = remember(workspace.tasks) { workspace.tasks.groupBy { it.clientId } }

    var smallClientIndex by remember(clients.size) { mutableIntStateOf(0) }
    var mediumClientIndex by remember(clients.size) { mutableIntStateOf(0) }
    var largeClientIndex by remember(clients.size) { mutableIntStateOf(0) }
    var mediumShowCompleted by remember { mutableStateOf(false) }
    var largeShowCompleted by remember { mutableStateOf(false) }

    fun requestPin(providerClass: Class<*>, label: String) {
        val manager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
            val component = ComponentName(context, providerClass)
            val requested = manager.requestPinAppWidget(component, null, null)
            if (requested) {
                onShowMessage(context.getString(R.string.widgets_pin_requested, label))
            } else {
                onShowMessage(context.getString(R.string.widgets_pin_unsupported))
            }
        } else {
            onShowMessage(context.getString(R.string.widgets_pin_unsupported))
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "widgets_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.widgets_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.widgets_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Theme & Dark Mode Control Card (applies to App + All Home Screen Widgets)
            item(key = "theme_control_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.theme_appearance_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeOptionPill(
                                label = stringResource(R.string.theme_mode_system),
                                icon = Icons.Rounded.Smartphone,
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                                testTag = "theme_option_system",
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionPill(
                                label = stringResource(R.string.theme_mode_light),
                                icon = Icons.Rounded.LightMode,
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                                testTag = "theme_option_light",
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionPill(
                                label = stringResource(R.string.theme_mode_dark),
                                icon = Icons.Rounded.DarkMode,
                                selected = themeMode == ThemeMode.DARK,
                                onClick = { onThemeModeChange(ThemeMode.DARK) },
                                testTag = "theme_option_dark",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 1. Small 2x2 Widget Showcase
            item(key = "widget_card_small") {
                val label = stringResource(R.string.widget_small_label)
                WidgetShowcaseContainer(
                    title = label,
                    description = stringResource(R.string.widget_small_desc),
                    sizeBadge = "2 × 2",
                    isDarkPreview = isDarkTheme,
                    onPinClick = { requestPin(SmallClientWidgetProvider::class.java, label) },
                    pinButtonTag = "pin_widget_small"
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LiveInteractiveWidgetPreview(
                            spec = WidgetSizeSpec.SMALL,
                            isDark = isDarkTheme,
                            clients = clients,
                            tasksByClient = tasksByClient,
                            selectedIndex = smallClientIndex,
                            showCompleted = false,
                            onToggleFilter = {},
                            onIndexChange = { smallClientIndex = it },
                            onToggleTask = onToggleTaskDone,
                            onAddTaskForClient = onOpenAddTaskForClient,
                            modifier = Modifier.widthIn(max = 228.dp)
                        )
                    }
                }
            }

            // 2. Medium 4x2 Widget Showcase
            item(key = "widget_card_medium") {
                val label = stringResource(R.string.widget_medium_label)
                WidgetShowcaseContainer(
                    title = label,
                    description = stringResource(R.string.widget_medium_desc),
                    sizeBadge = "4 × 2",
                    isDarkPreview = isDarkTheme,
                    onPinClick = { requestPin(MediumClientWidgetProvider::class.java, label) },
                    pinButtonTag = "pin_widget_medium"
                ) {
                    LiveInteractiveWidgetPreview(
                        spec = WidgetSizeSpec.MEDIUM,
                        isDark = isDarkTheme,
                        clients = clients,
                        tasksByClient = tasksByClient,
                        selectedIndex = mediumClientIndex,
                        showCompleted = mediumShowCompleted,
                        onToggleFilter = { mediumShowCompleted = !mediumShowCompleted },
                        onIndexChange = { mediumClientIndex = it },
                        onToggleTask = onToggleTaskDone,
                        onAddTaskForClient = onOpenAddTaskForClient,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Large 4x4 Widget Showcase
            item(key = "widget_card_large") {
                val label = stringResource(R.string.widget_large_label)
                WidgetShowcaseContainer(
                    title = label,
                    description = stringResource(R.string.widget_large_desc),
                    sizeBadge = "4 × 4",
                    isDarkPreview = isDarkTheme,
                    onPinClick = { requestPin(LargeClientWidgetProvider::class.java, label) },
                    pinButtonTag = "pin_widget_large"
                ) {
                    LiveInteractiveWidgetPreview(
                        spec = WidgetSizeSpec.LARGE,
                        isDark = isDarkTheme,
                        clients = clients,
                        tasksByClient = tasksByClient,
                        selectedIndex = largeClientIndex,
                        showCompleted = largeShowCompleted,
                        onToggleFilter = { largeShowCompleted = !largeShowCompleted },
                        onIndexChange = { largeClientIndex = it },
                        onToggleTask = onToggleTaskDone,
                        onAddTaskForClient = onOpenAddTaskForClient,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(key = "widgets_bottom_spacer") {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }
}

@Composable
private fun ThemeOptionPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WidgetShowcaseContainer(
    title: String,
    description: String,
    sizeBadge: String,
    isDarkPreview: Boolean,
    onPinClick: () -> Unit,
    pinButtonTag: String,
    previewContent: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {}
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = sizeBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Wallpaper-style backdrop framing the live interactive widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isDarkPreview) Color(0xFF0B0D10) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                    )
                    .padding(14.dp)
            ) {
                previewContent()
            }

            Button(
                onClick = onPinClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(pinButtonTag)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddToHomeScreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.widgets_pin_button))
            }
        }
    }
}

@Composable
private fun LiveInteractiveWidgetPreview(
    spec: WidgetSizeSpec,
    isDark: Boolean,
    clients: List<Client>,
    tasksByClient: Map<String, List<ClientTask>>,
    selectedIndex: Int,
    showCompleted: Boolean,
    onToggleFilter: () -> Unit,
    onIndexChange: (Int) -> Unit,
    onToggleTask: (ClientTask) -> Unit,
    onAddTaskForClient: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = if (clients.isEmpty()) 0 else selectedIndex.coerceIn(0, clients.lastIndex)
    val activeClient = clients.getOrNull(safeIndex)
    val allClientTasks = activeClient?.let { tasksByClient[it.id].orEmpty() }.orEmpty()
    val filteredTasks = if (spec.hasFilterChip && !showCompleted) {
        allClientTasks.filter { !it.completed }.ifEmpty { allClientTasks }
    } else if (!spec.hasFilterChip) {
        allClientTasks.filter { !it.completed }.ifEmpty { allClientTasks }
    } else {
        allClientTasks
    }
    val openCount = allClientTasks.count { !it.completed }
    val doneCount = allClientTasks.count { it.completed }
    val totalCount = allClientTasks.size
    val accent = activeClient?.let { parseClientColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary

    val cardBg = if (isDark) WidgetDarkCardBg else AlabasterCanvas
    val cardBorder = if (isDark) WidgetDarkBorder else HairlineBorder
    val rowBg = if (isDark) WidgetDarkRowBg else PurePaper
    val rowBorder = if (isDark) WidgetDarkRowBorder else HairlineBorder
    val pillBg = if (isDark) WidgetDarkPillBg else WarmSand
    val pillActiveBg = if (isDark) WidgetDarkPrimaryText else ObsidianInk
    val pillActiveFg = if (isDark) ObsidianInk else Color.White
    val primaryText = if (isDark) WidgetDarkPrimaryText else ObsidianInk
    val secondaryText = if (isDark) WidgetDarkSecondaryText else SlateInk
    val mutedText = if (isDark) WidgetDarkMutedText else SlateInk
    val checkColor = if (isDark) WidgetDarkEmerald else StudioForest
    val checkGlyphTint = if (isDark) Color(0xFF0E1A15) else Color.White
    val dueBg = if (isDark) WidgetDarkDueBg else WarmSand
    val progressTrack = if (isDark) WidgetDarkProgressTrack else WarmSand

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            // Top Client Accent Stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent)
            )

            // Widget Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    val headerClientText = activeClient?.name ?: stringResource(R.string.widget_empty_title)
                    Text(
                        text = headerClientText,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ).withBidi(headerClientText),
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                activeClient == null -> stringResource(R.string.widget_empty_body)
                                totalCount == 0 -> stringResource(R.string.widget_no_tasks)
                                openCount == 0 -> "$doneCount/$totalCount done · All caught up"
                                else -> "$openCount open · $doneCount/$totalCount done"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (spec.hasClientIndex && clients.size > 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${safeIndex + 1}/${clients.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedText
                            )
                        }
                    }
                }

                if (spec.hasFilterChip && activeClient != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = pillBg,
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(onClick = onToggleFilter)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (showCompleted) R.string.widget_filter_all else R.string.widget_filter_open
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = primaryText
                            )
                        }
                    }
                }

                if (clients.size > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    WidgetMiniCircleButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.widget_prev_client_cd),
                        containerColor = pillBg,
                        iconTint = primaryText,
                        onClick = {
                            onIndexChange(Math.floorMod(safeIndex - 1, clients.size))
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    WidgetMiniCircleButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.widget_next_client_cd),
                        containerColor = pillBg,
                        iconTint = primaryText,
                        onClick = {
                            onIndexChange(Math.floorMod(safeIndex + 1, clients.size))
                        }
                    )
                }

                if (spec.hasAddButton && activeClient != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(pillActiveBg)
                            .clickable { onAddTaskForClient(activeClient.id) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.widget_add_task_cd),
                            tint = pillActiveFg,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (spec.hasProgressBar && activeClient != null) {
                val pctInt = if (totalCount == 0) 0 else ((doneCount * 100) / totalCount)
                val pct = pctInt / 100f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { pct },
                        color = accent,
                        trackColor = progressTrack,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                    )
                    Text(
                        text = "$pctInt%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = secondaryText
                    )
                }
            }

            if (spec.maxClientTabs > 0 && clients.size > 1) {
                val start = (safeIndex - 1).coerceIn(0, (clients.size - spec.maxClientTabs).coerceAtLeast(0))
                val visibleTabs = clients.subList(start, (start + spec.maxClientTabs).coerceAtMost(clients.size))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    visibleTabs.forEach { tabClient ->
                        val isSelected = tabClient.id == activeClient?.id
                        val targetIndex = clients.indexOfFirst { it.id == tabClient.id }
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (isSelected) pillActiveBg else pillBg,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(99.dp))
                                .clickable {
                                    if (targetIndex >= 0) onIndexChange(targetIndex)
                                }
                        ) {
                            Text(
                                text = tabClient.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ).withBidiCentered(tabClient.name),
                                color = if (isSelected) pillActiveFg else primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            val shownTasks = filteredTasks.take(spec.maxTasks)
            if (shownTasks.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = if (activeClient == null) {
                            stringResource(R.string.widget_empty_body)
                        } else {
                            stringResource(R.string.widget_no_tasks)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shownTasks.forEach { task ->
                        val isHigh = task.priorityEnum() == TaskPriority.HIGH && !task.completed
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = rowBg,
                            border = BorderStroke(1.dp, rowBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleTask(task) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                            ) {
                                if (isHigh) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(15.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(StudioTerracotta)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (task.completed) checkColor else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (task.completed) checkColor else mutedText.copy(alpha = 0.7f),
                                            CircleShape
                                        )
                                ) {
                                    if (task.completed) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = checkGlyphTint,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = if (task.completed) {
                                            TextDecoration.LineThrough
                                        } else {
                                            TextDecoration.None
                                        }
                                    ).withBidi(task.title),
                                    color = if (task.completed) mutedText else primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (spec.hasDueLabels && task.dueLabel.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = dueBg
                                    ) {
                                        Text(
                                            text = task.dueLabel,
                                            style = MaterialTheme.typography.labelSmall.withBidi(task.dueLabel),
                                            color = secondaryText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val overflowCount = (filteredTasks.size - shownTasks.size).coerceAtLeast(0)
            if (overflowCount > 0) {
                Text(
                    text = stringResource(R.string.widget_more_tasks, overflowCount),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = mutedText,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun WidgetMiniCircleButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}
