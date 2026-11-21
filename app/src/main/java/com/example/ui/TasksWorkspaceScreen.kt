package com.example.ui

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Client
import com.example.data.ClientProject
import com.example.data.ClientTask
import com.example.ui.theme.StudioTerracotta
import com.example.ui.theme.parseClientColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.max

private val GroupFullShape = RoundedCornerShape(20.dp)
private val TaskCardShape = RoundedCornerShape(16.dp)
private val FilterPillShape = RoundedCornerShape(99.dp)
private val ProjectPillShape = RoundedCornerShape(12.dp)
private val TaskRowOuterModifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)

@Immutable
private data class PreparedClientGroup(
    val client: Client,
    val accent: Color,
    val badgeBg: Color,
    val badgeStyle: TextStyle,
    val isNameRtl: Boolean,
    val subtitle: String,
    val expandCd: String,
    val collapseCd: String,
    val visibleCountText: String,
    val hasMultipleProjects: Boolean,
    val visibleTasks: List<PreparedTask>
)

@Immutable
private data class WorkspaceRowPalette(
    val cardSurface: Color,
    val outline: Color,
    val sectionDivider: Color,
    val onSurfaceVariant: Color,
    val uncheckedBorder: Color,
    val primaryColor: Color,
    val titleOpenLtr: TextStyle,
    val titleOpenRtl: TextStyle,
    val titleDoneLtr: TextStyle,
    val titleDoneRtl: TextStyle,
    val detailsLtr: TextStyle,
    val detailsRtl: TextStyle,
    val dueLtr: TextStyle,
    val dueRtl: TextStyle,
    val priorityLtr: TextStyle,
    val priorityRtl: TextStyle,
    val clientReqLtr: TextStyle,
    val clientReqRtl: TextStyle,
    val groupBadgeStyle: TextStyle,
    val groupTitleLtr: TextStyle,
    val groupTitleRtl: TextStyle,
    val groupSubLtr: TextStyle,
    val groupSubRtl: TextStyle,
    val priorityHighBadgeText: String,
    val clientRequestedLabel: String,
    val toggleTaskCd: String,
    val addTaskCd: String,
    val noDoneTasksLabel: String,
    val allDoneLabel: String,
    val cardRipple: Indication,
    val iconRipple: Indication
)

@Composable
private fun rememberWorkspaceRowPalette(): WorkspaceRowPalette {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val priorityHighLabel = stringResource(R.string.priority_high)
    val clientRequestedLabel = stringResource(R.string.workspace_client_requested_badge)
    val toggleTaskCd = stringResource(R.string.widget_toggle_task_cd)
    val addTaskCd = stringResource(R.string.workspace_add_task)
    val noDoneTasksLabel = stringResource(R.string.workspace_no_done_tasks)
    val allDoneLabel = stringResource(R.string.workspace_all_done)
    val cardRipple = remember { ripple(bounded = true) }
    val iconRipple = remember { ripple(bounded = false, radius = 20.dp) }

    return remember(
        colorScheme,
        typography,
        priorityHighLabel,
        clientRequestedLabel,
        toggleTaskCd,
        addTaskCd,
        noDoneTasksLabel,
        allDoneLabel
    ) {
        val cardSurface = colorScheme.surfaceContainerLowest
        val openTitleBase = typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        val doneTitleBase = typography.bodyLarge.copy(
            fontWeight = FontWeight.Normal,
            textDecoration = TextDecoration.LineThrough,
            color = colorScheme.onSurfaceVariant
        )
        val detailsBase = typography.bodySmall.copy(color = colorScheme.onSurfaceVariant)
        val dueBase = typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
        val priorityBase = typography.labelSmall.copy(
            color = StudioTerracotta,
            fontWeight = FontWeight.SemiBold
        )
        val clientReqBase = typography.labelSmall.copy(
            color = colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        val groupTitleBase = typography.titleMedium.copy(color = colorScheme.onSurface)
        val groupSubBase = typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)

        WorkspaceRowPalette(
            cardSurface = cardSurface,
            outline = colorScheme.outline,
            sectionDivider = colorScheme.outline.copy(alpha = 0.55f),
            onSurfaceVariant = colorScheme.onSurfaceVariant,
            uncheckedBorder = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            primaryColor = colorScheme.primary,
            titleOpenLtr = openTitleBase.withBidi(false),
            titleOpenRtl = openTitleBase.withBidi(true),
            titleDoneLtr = doneTitleBase.withBidi(false),
            titleDoneRtl = doneTitleBase.withBidi(true),
            detailsLtr = detailsBase.withBidi(false),
            detailsRtl = detailsBase.withBidi(true),
            dueLtr = dueBase.withBidi(false),
            dueRtl = dueBase.withBidi(true),
            priorityLtr = priorityBase.copy(textAlign = TextAlign.Left),
            priorityRtl = priorityBase.copy(textAlign = TextAlign.Right),
            clientReqLtr = clientReqBase.copy(textAlign = TextAlign.Left),
            clientReqRtl = clientReqBase.copy(textAlign = TextAlign.Right),
            groupBadgeStyle = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            groupTitleLtr = groupTitleBase.withBidi(false),
            groupTitleRtl = groupTitleBase.withBidi(true),
            groupSubLtr = groupSubBase.copy(textAlign = TextAlign.Left),
            groupSubRtl = groupSubBase.copy(textAlign = TextAlign.Right),
            priorityHighBadgeText = "● $priorityHighLabel",
            clientRequestedLabel = clientRequestedLabel,
            toggleTaskCd = toggleTaskCd,
            addTaskCd = addTaskCd,
            noDoneTasksLabel = noDoneTasksLabel,
            allDoneLabel = allDoneLabel,
            cardRipple = cardRipple,
            iconRipple = iconRipple
        )
    }
}

@Composable
fun TasksWorkspaceScreen(
    workspace: WorkspaceData,
    selectedClientId: String?,
    selectedProjectByClient: Map<String, String>,
    taskFilter: TaskFilter,
    collapsedClientIds: CollapsedClientsState,
    onSelectClient: (String?) -> Unit,
    onSelectProject: (clientId: String, projectId: String) -> Unit,
    onReorderClients: (List<Client>) -> Unit,
    onToggleClientCollapsed: (String) -> Unit,
    onSetAllClientsCollapsed: (Boolean) -> Unit,
    onSetFilter: (TaskFilter) -> Unit,
    onOpenNewClient: () -> Unit,
    onEditClient: (Client) -> Unit,
    onOpenNewProject: (Client) -> Unit,
    onEditProject: (Client, ClientProject) -> Unit,
    onQuickAddTask: (clientId: String, projectId: String?, title: String) -> Unit,
    onOpenAddTaskSheet: (clientId: String?, projectId: String?) -> Unit,
    onToggleTaskDone: (ClientTask) -> Unit,
    onEditTask: (ClientTask) -> Unit,
    modifier: Modifier = Modifier,
    allClientsListState: LazyListState = rememberLazyListState()
) {
    val clients = workspace.clients
    if (clients.isEmpty()) {
        EmptyClientsWorkspace(
            onRegisterFirstClient = onOpenNewClient,
            modifier = modifier
        )
        return
    }

    val palette = rememberWorkspaceRowPalette()
    val resources: Resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 1536)
    val scope = rememberCoroutineScope()

    val pageCount = 1 + clients.size
    val targetClientPage = remember(selectedClientId, clients) {
        if (selectedClientId == null) {
            0
        } else {
            val idx = clients.indexOfFirst { it.id == selectedClientId }
            if (idx >= 0) idx + 1 else 0
        }
    }

    val clientPagerState = rememberPagerState(
        initialPage = targetClientPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )

    // Sync programmatic selectedClientId changes into the outer Client HorizontalPager
    LaunchedEffect(targetClientPage, pageCount) {
        val clamped = targetClientPage.coerceIn(0, pageCount - 1)
        if (clientPagerState.currentPage != clamped && !clientPagerState.isScrollInProgress) {
            clientPagerState.animateScrollToPage(
                page = clamped,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    // Sync user swipe gestures on outer HorizontalPager back to selectedClientId
    LaunchedEffect(clientPagerState, clients) {
        snapshotFlow { clientPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val newClientId = if (page <= 0) null else clients.getOrNull(page - 1)?.id
                if (newClientId != selectedClientId) {
                    onSelectClient(newClientId)
                }
            }
    }

    val preparedGroups = remember(workspace, taskFilter, palette, configuration) {
        clients.map { client ->
            val allTasks = workspace.preparedByClient[client.id].orEmpty()
            val visible = when (taskFilter) {
                TaskFilter.ALL -> allTasks
                TaskFilter.OPEN -> workspace.openPreparedByClient[client.id].orEmpty()
                TaskFilter.DONE -> workspace.donePreparedByClient[client.id].orEmpty()
            }
            val accent = workspace.clientAccentById[client.id] ?: parseClientColor(client.colorHex)
            val openTaskCount = workspace.openCountByClient[client.id] ?: 0
            val projectCount = workspace.projectsByClient[client.id]?.size ?: 1
            val bidiName = bidiIsolate(client.name)
            val subtitle = when {
                taskFilter == TaskFilter.DONE && visible.isEmpty() -> palette.noDoneTasksLabel
                allTasks.isEmpty() -> resources.getString(R.string.workspace_empty_tasks_title, bidiName)
                openTaskCount == 0 -> palette.allDoneLabel
                projectCount > 1 -> {
                    val projLabel = resources.getString(R.string.workspace_project_count_many, projectCount)
                    val openLabel = resources.getString(R.string.workspace_active_count, openTaskCount)
                    "$projLabel · $openLabel"
                }
                else -> resources.getString(R.string.workspace_active_count, openTaskCount)
            }
            PreparedClientGroup(
                client = client,
                accent = accent,
                badgeBg = accent.copy(alpha = 0.14f),
                badgeStyle = palette.groupBadgeStyle.copy(color = accent),
                isNameRtl = workspace.clientNameRtlById[client.id] ?: false,
                subtitle = subtitle,
                expandCd = resources.getString(R.string.workspace_expand_client, bidiName),
                collapseCd = resources.getString(R.string.workspace_collapse_client, bidiName),
                visibleCountText = visible.size.toString(),
                hasMultipleProjects = projectCount > 1,
                visibleTasks = visible
            )
        }
    }

    val collapsedIds = collapsedClientIds.ids
    val allClientsCollapsed = remember(clients, collapsedIds) {
        clients.isNotEmpty() && clients.all { it.id in collapsedIds }
    }

    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        ClientSwitcherRibbon(
            workspace = workspace,
            selectedClientId = selectedClientId,
            onSelectClient = { newId ->
                onSelectClient(newId)
                val target = if (newId == null) {
                    0
                } else {
                    val idx = clients.indexOfFirst { it.id == newId }
                    if (idx >= 0) idx + 1 else 0
                }
                scope.launch {
                    clientPagerState.animateScrollToPage(
                        page = target.coerceIn(0, pageCount - 1),
                        animationSpec = tween(durationMillis = 300)
                    )
                }
            },
            onReorderClients = onReorderClients,
            onOpenNewClient = onOpenNewClient,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .padding(bottom = 4.dp)
                .graphicsLayer {}
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )

        HorizontalPager(
            state = clientPagerState,
            beyondViewportPageCount = 1,
            key = { page -> if (page == 0) "page_all_clients" else "page_client_${clients.getOrNull(page - 1)?.id ?: page}" },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .testTag("client_workspace_pager")
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Read pager offset in the draw phase only — reading it in composition
                        // recomposes the whole page (and its LazyColumn) on every scroll frame.
                        val pageOffset = ((clientPagerState.currentPage - page) + clientPagerState.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        val scale = 1f - (pageOffset * 0.03f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (pageOffset * 0.18f)
                    }
            ) {
                if (page == 0) {
                    AllClientsOverviewPage(
                        preparedGroups = preparedGroups,
                        collapsedIds = collapsedIds,
                        allClientsCollapsed = allClientsCollapsed,
                        showCollapseAllControl = clients.size > 1,
                        taskFilter = taskFilter,
                        palette = palette,
                        textMeasurer = textMeasurer,
                        listState = allClientsListState,
                        onSetFilter = onSetFilter,
                        onToggleAllCollapsed = { onSetAllClientsCollapsed(!allClientsCollapsed) },
                        onToggleClientCollapsed = onToggleClientCollapsed,
                        onOpenAddTaskSheet = { cid -> onOpenAddTaskSheet(cid, null) },
                        onToggleTaskDone = onToggleTaskDone,
                        onEditTask = onEditTask
                    )
                } else {
                    val client = clients.getOrNull(page - 1)
                    if (client != null) {
                        ClientProjectsSwipePage(
                            client = client,
                            workspace = workspace,
                            selectedProjectId = selectedProjectByClient[client.id],
                            taskFilter = taskFilter,
                            palette = palette,
                            textMeasurer = textMeasurer,
                            onSelectProject = { projId -> onSelectProject(client.id, projId) },
                            onSetFilter = onSetFilter,
                            onEditClient = { onEditClient(client) },
                            onOpenNewProject = { onOpenNewProject(client) },
                            onEditProject = { project -> onEditProject(client, project) },
                            onQuickAddTask = { projectId, title -> onQuickAddTask(client.id, projectId, title) },
                            onOpenAddTaskSheet = { projectId -> onOpenAddTaskSheet(client.id, projectId) },
                            onToggleTaskDone = onToggleTaskDone,
                            onEditTask = onEditTask
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllClientsOverviewPage(
    preparedGroups: List<PreparedClientGroup>,
    collapsedIds: Set<String>,
    allClientsCollapsed: Boolean,
    showCollapseAllControl: Boolean,
    taskFilter: TaskFilter,
    palette: WorkspaceRowPalette,
    textMeasurer: TextMeasurer,
    listState: LazyListState,
    onSetFilter: (TaskFilter) -> Unit,
    onToggleAllCollapsed: () -> Unit,
    onToggleClientCollapsed: (String) -> Unit,
    onOpenAddTaskSheet: (String) -> Unit,
    onToggleTaskDone: (ClientTask) -> Unit,
    onEditTask: (ClientTask) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)
    ) {
        item(key = "filter_bar_all", contentType = "filter_bar") {
            FilterBarRow(
                taskFilter = taskFilter,
                showCollapseAllControl = showCollapseAllControl,
                allClientsCollapsed = allClientsCollapsed,
                cardRipple = palette.cardRipple,
                onSetFilter = onSetFilter,
                onToggleAllCollapsed = onToggleAllCollapsed
            )
        }

        preparedGroups.forEachIndexed { groupIndex, group ->
            val clientId = group.client.id
            val isExpanded = clientId !in collapsedIds
            val hasExpandedTasks = isExpanded && group.visibleTasks.isNotEmpty()

            item(
                key = "group_header_$clientId",
                contentType = "group_header"
            ) {
                ClientGroupHeaderRow(
                    group = group,
                    isExpanded = isExpanded,
                    hasExpandedTasks = hasExpandedTasks,
                    palette = palette,
                    onToggleExpand = { onToggleClientCollapsed(clientId) },
                    onAddTaskForClient = { onOpenAddTaskSheet(clientId) },
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (groupIndex == 0) 2.dp else 14.dp,
                        bottom = if (hasExpandedTasks) 8.dp else 4.dp
                    )
                )
            }

            if (hasExpandedTasks) {
                items(
                    items = group.visibleTasks,
                    key = { it.task.id },
                    contentType = { "task_row" }
                ) { prepared ->
                    FastTaskRowCard(
                        prepared = prepared,
                        accent = group.accent,
                        palette = palette,
                        textMeasurer = textMeasurer,
                        showProjectTag = group.hasMultipleProjects && prepared.projectName.isNotBlank(),
                        onToggleDone = onToggleTaskDone,
                        onEdit = onEditTask,
                        modifier = TaskRowOuterModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientProjectsSwipePage(
    client: Client,
    workspace: WorkspaceData,
    selectedProjectId: String?,
    taskFilter: TaskFilter,
    palette: WorkspaceRowPalette,
    textMeasurer: TextMeasurer,
    onSelectProject: (String) -> Unit,
    onSetFilter: (TaskFilter) -> Unit,
    onEditClient: () -> Unit,
    onOpenNewProject: () -> Unit,
    onEditProject: (ClientProject) -> Unit,
    onQuickAddTask: (projectId: String?, title: String) -> Unit,
    onOpenAddTaskSheet: (projectId: String?) -> Unit,
    onToggleTaskDone: (ClientTask) -> Unit,
    onEditTask: (ClientTask) -> Unit
) {
    val accent = workspace.clientAccentById[client.id] ?: parseClientColor(client.colorHex)
    val projects = workspace.projectsByClient[client.id].orEmpty()
    val clientAllTasksCount = workspace.preparedByClient[client.id]?.size ?: 0
    val clientOpenCount = workspace.openCountByClient[client.id] ?: 0
    val scope = rememberCoroutineScope()

    val effectiveProjects = remember(projects, client) {
        if (projects.isNotEmpty()) {
            projects
        } else {
            listOf(
                ClientProject(
                    id = "default_${client.id}",
                    clientId = client.id,
                    name = client.name,
                    colorHex = client.colorHex,
                    defaultProject = true
                )
            )
        }
    }

    val targetProjectIndex = remember(selectedProjectId, effectiveProjects) {
        val idx = effectiveProjects.indexOfFirst { it.id == selectedProjectId }
        if (idx >= 0) idx else 0
    }

    val projectPagerState = rememberPagerState(
        initialPage = targetProjectIndex.coerceIn(0, effectiveProjects.lastIndex),
        pageCount = { effectiveProjects.size }
    )

    // Sync selectedProjectId -> inner projectPagerState
    LaunchedEffect(targetProjectIndex, effectiveProjects.size) {
        val clamped = targetProjectIndex.coerceIn(0, effectiveProjects.lastIndex)
        if (projectPagerState.currentPage != clamped && !projectPagerState.isScrollInProgress) {
            projectPagerState.animateScrollToPage(
                page = clamped,
                animationSpec = tween(durationMillis = 260)
            )
        }
    }

    // Sync user swipes on inner projectPagerState -> selectedProjectByClient
    LaunchedEffect(projectPagerState, effectiveProjects) {
        snapshotFlow { projectPagerState.settledPage }
            .distinctUntilChanged()
            .collect { idx ->
                val proj = effectiveProjects.getOrNull(idx)
                if (proj != null && proj.id != selectedProjectId) {
                    onSelectProject(proj.id)
                }
            }
    }

    val activeProject = effectiveProjects.getOrNull(projectPagerState.currentPage) ?: effectiveProjects.first()

    Column(modifier = Modifier.fillMaxSize()) {
        // Client Banner (lives above inner pager so swiping horizontally here jumps directly between clients!)
        SelectedClientBanner(
            client = client,
            accent = accent,
            projectCount = effectiveProjects.size,
            openCount = clientOpenCount,
            totalCount = clientAllTasksCount,
            onEditClient = onEditClient,
            onAddProject = onOpenNewProject,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
        )

        // Horizontal Project Strip
        ProjectSwitcherStrip(
            projects = effectiveProjects,
            activeProjectId = activeProject.id,
            workspace = workspace,
            cardRipple = palette.cardRipple,
            onSelectProject = { proj ->
                onSelectProject(proj.id)
                val targetIdx = effectiveProjects.indexOfFirst { it.id == proj.id }
                if (targetIdx >= 0) {
                    scope.launch {
                        projectPagerState.animateScrollToPage(
                            page = targetIdx,
                            animationSpec = tween(durationMillis = 260)
                        )
                    }
                }
            },
            onAddProject = onOpenNewProject,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Inner HorizontalPager across projects (overscrolling boundaries glides into adjacent clients!)
        HorizontalPager(
            state = projectPagerState,
            beyondViewportPageCount = 1,
            key = { idx -> effectiveProjects.getOrNull(idx)?.id ?: "proj_$idx" },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("project_pager_${client.id}")
        ) { projectIdx ->
            val project = effectiveProjects.getOrNull(projectIdx) ?: return@HorizontalPager

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val projOffset = ((projectPagerState.currentPage - projectIdx) + projectPagerState.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        val scale = 1f - (projOffset * 0.025f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (projOffset * 0.14f)
                    }
            ) {
                SingleProjectTasksList(
                    client = client,
                    project = project,
                    workspace = workspace,
                    taskFilter = taskFilter,
                    palette = palette,
                    textMeasurer = textMeasurer,
                    onSetFilter = onSetFilter,
                    onEditProject = { onEditProject(project) },
                    onQuickAddTask = { title -> onQuickAddTask(project.id, title) },
                    onOpenAddTaskSheet = { onOpenAddTaskSheet(project.id) },
                    onToggleTaskDone = onToggleTaskDone,
                    onEditTask = onEditTask
                )
            }
        }
    }
}

@Composable
private fun ProjectSwitcherStrip(
    projects: List<ClientProject>,
    activeProjectId: String,
    workspace: WorkspaceData,
    cardRipple: Indication,
    onSelectProject: (ClientProject) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    LaunchedEffect(activeProjectId, projects.size) {
        val idx = projects.indexOfFirst { it.id == activeProjectId }
        if (idx >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val alreadyVisible = visible.any { it.index == idx && it.offset >= 0 }
            if (!alreadyVisible) {
                listState.animateScrollToItem(idx)
            }
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("project_switcher_strip")
    ) {
        items(
            items = projects,
            key = { "proj_pill_${it.id}" }
        ) { project ->
            val selected = project.id == activeProjectId
            val projAccent = workspace.projectAccentById[project.id] ?: parseClientColor(project.colorHex)
            val openCount = workspace.openCountByProject[project.id] ?: 0
            val containerColor = if (selected) projAccent else colorScheme.surfaceContainerLowest
            val contentColor = if (selected) Color.White else colorScheme.onSurface
            val badgeBg = if (selected) Color.White.copy(alpha = 0.22f) else colorScheme.surfaceVariant
            val borderColor = if (selected) Color.Transparent else colorScheme.outline

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clip(ProjectPillShape)
                    .background(containerColor)
                    .then(
                        if (!selected) {
                            Modifier.border(1.dp, borderColor, ProjectPillShape)
                        } else {
                            Modifier
                        }
                    )
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .clickable(
                        interactionSource = null,
                        indication = cardRipple,
                        onClick = { onSelectProject(project) }
                    )
                    .padding(horizontal = 13.dp)
                    .testTag("project_tab_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (selected) Color.White else projAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = project.name,
                    style = typography.labelLarge.withBidi(project.name).copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(badgeBg, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = openCount.toString(),
                        style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor
                    )
                }
            }
        }

        item(key = "proj_pill_add") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clip(ProjectPillShape)
                    .background(colorScheme.surfaceContainerLowest)
                    .border(1.dp, colorScheme.outline, ProjectPillShape)
                    .clickable(
                        interactionSource = null,
                        indication = cardRipple,
                        onClick = onAddProject
                    )
                    .padding(horizontal = 12.dp)
                    .testTag("strip_add_project_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.workspace_project_add_cd),
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.workspace_new_project),
                    style = typography.labelLarge,
                    color = colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SingleProjectTasksList(
    client: Client,
    project: ClientProject,
    workspace: WorkspaceData,
    taskFilter: TaskFilter,
    palette: WorkspaceRowPalette,
    textMeasurer: TextMeasurer,
    onSetFilter: (TaskFilter) -> Unit,
    onEditProject: () -> Unit,
    onQuickAddTask: (String) -> Unit,
    onOpenAddTaskSheet: () -> Unit,
    onToggleTaskDone: (ClientTask) -> Unit,
    onEditTask: (ClientTask) -> Unit
) {
    val projAccent = workspace.projectAccentById[project.id] ?: parseClientColor(project.colorHex)
    val openTasks = workspace.openPreparedByProject[project.id].orEmpty()
    val doneTasks = workspace.donePreparedByProject[project.id].orEmpty()
    val totalCount = openTasks.size + doneTasks.size
    val progress = if (totalCount == 0) 0f else doneTasks.size.toFloat() / totalCount.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 320),
        label = "project_progress"
    )

    var quickTitle by remember(project.id) { mutableStateOf("") }
    var showCompleted by remember(project.id) { mutableStateOf(true) }
    val listState = remember(project.id) { LazyListState() }

    val showOpen = taskFilter != TaskFilter.DONE
    val showDone = taskFilter != TaskFilter.OPEN

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)
    ) {
        // Project Mini Header + Progress
        item(key = "project_header_${project.id}", contentType = "project_header") {
            Surface(
                shape = TaskCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(projAccent)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium.withBidi(project.name),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (project.description.isNotBlank()) {
                                    Text(
                                        text = project.description,
                                        style = MaterialTheme.typography.bodySmall.withBidi(project.description),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onEditProject,
                            modifier = Modifier.testTag("edit_project_${project.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.workspace_project_edit_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (openTasks.isEmpty() && totalCount > 0) {
                                stringResource(R.string.workspace_all_done)
                            } else {
                                stringResource(R.string.workspace_active_count, openTasks.size)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = projAccent
                        )
                        Text(
                            text = stringResource(R.string.clients_done_progress, doneTasks.size, totalCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = projAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }
            }
        }

        item(key = "filter_bar_${project.id}", contentType = "filter_bar") {
            FilterBarRow(
                taskFilter = taskFilter,
                showCollapseAllControl = false,
                allClientsCollapsed = false,
                cardRipple = palette.cardRipple,
                onSetFilter = onSetFilter,
                onToggleAllCollapsed = {}
            )
        }

        item(key = "quick_add_${project.id}", contentType = "quick_add") {
            QuickAddTaskBar(
                targetName = project.name,
                accent = projAccent,
                value = quickTitle,
                onValueChange = { if (it.length <= 140) quickTitle = it },
                onSubmit = {
                    if (quickTitle.isNotBlank()) {
                        onQuickAddTask(quickTitle)
                        quickTitle = ""
                    }
                },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )
        }

        if (showOpen) {
            if (openTasks.isEmpty() && (taskFilter == TaskFilter.OPEN || doneTasks.isEmpty())) {
                item(key = "empty_open_${project.id}", contentType = "empty_open") {
                    AllCaughtUpCard(
                        targetName = project.name,
                        accent = projAccent,
                        onAddTask = onOpenAddTaskSheet,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    )
                }
            } else {
                items(
                    items = openTasks,
                    key = { it.task.id },
                    contentType = { "task_row" }
                ) { prepared ->
                    FastTaskRowCard(
                        prepared = prepared,
                        accent = projAccent,
                        palette = palette,
                        textMeasurer = textMeasurer,
                        showProjectTag = false,
                        onToggleDone = onToggleTaskDone,
                        onEdit = onEditTask,
                        modifier = TaskRowOuterModifier
                    )
                }
            }
        }

        if (showDone && doneTasks.isNotEmpty()) {
            item(key = "completed_header_${project.id}", contentType = "completed_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
                        .clickable(
                            interactionSource = null,
                            indication = palette.cardRipple,
                            onClick = { showCompleted = !showCompleted }
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.workspace_completed_section, doneTasks.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (showCompleted) {
                            Icons.Rounded.ExpandLess
                        } else {
                            Icons.Rounded.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showCompleted) {
                items(
                    items = doneTasks,
                    key = { it.task.id },
                    contentType = { "task_row" }
                ) { prepared ->
                    FastTaskRowCard(
                        prepared = prepared,
                        accent = projAccent,
                        palette = palette,
                        textMeasurer = textMeasurer,
                        showProjectTag = false,
                        onToggleDone = onToggleTaskDone,
                        onEdit = onEditTask,
                        modifier = TaskRowOuterModifier
                    )
                }
            }
        } else if (taskFilter == TaskFilter.DONE && doneTasks.isEmpty()) {
            item(key = "empty_done_${project.id}", contentType = "empty_done") {
                Surface(
                    shape = TaskCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.workspace_no_done_tasks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBarRow(
    taskFilter: TaskFilter,
    showCollapseAllControl: Boolean,
    allClientsCollapsed: Boolean,
    cardRipple: Indication,
    onSetFilter: (TaskFilter) -> Unit,
    onToggleAllCollapsed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskFilter.entries.forEach { filter ->
                val active = taskFilter == filter
                val labelRes = when (filter) {
                    TaskFilter.ALL -> R.string.workspace_filter_all
                    TaskFilter.OPEN -> R.string.workspace_filter_open
                    TaskFilter.DONE -> R.string.workspace_filter_done
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(36.dp)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            FilterPillShape
                        )
                        .clickable(
                            interactionSource = null,
                            indication = cardRipple,
                            onClick = { onSetFilter(filter) }
                        )
                        .padding(horizontal = 16.dp)
                        .testTag("filter_${filter.name.lowercase()}")
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        if (showCollapseAllControl) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, FilterPillShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, FilterPillShape)
                    .clickable(
                        interactionSource = null,
                        indication = cardRipple,
                        onClick = onToggleAllCollapsed
                    )
                    .padding(horizontal = 11.dp)
                    .testTag("toggle_all_clients_collapse")
            ) {
                Icon(
                    imageVector = if (allClientsCollapsed) {
                        Icons.Rounded.UnfoldMore
                    } else {
                        Icons.Rounded.UnfoldLess
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(
                        if (allClientsCollapsed) {
                            R.string.workspace_expand_all
                        } else {
                            R.string.workspace_collapse_all
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClientGroupHeaderRow(
    group: PreparedClientGroup,
    isExpanded: Boolean,
    hasExpandedTasks: Boolean,
    palette: WorkspaceRowPalette,
    onToggleExpand: () -> Unit,
    onAddTaskForClient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val client = group.client
    val accent = group.accent
    val toggleCd = if (isExpanded) group.collapseCd else group.expandCd

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val strokePx = 1.dp.toPx()
                val y = size.height - strokePx / 2f
                val lineStart = Offset(0f, y)
                val lineEnd = Offset(size.width, y)
                val barX = 2.dp.toPx()
                val barW = 4.dp.toPx()
                val barH = 26.dp.toPx()
                val barY = ((size.height - strokePx - barH) / 2f).coerceAtLeast(0f)
                val barTopLeft = Offset(barX, barY)
                val barSize = Size(barW, barH)
                val barRadius = CornerRadius(barW / 2f, barW / 2f)
                onDrawBehind {
                    drawRoundRect(
                        color = accent,
                        topLeft = barTopLeft,
                        size = barSize,
                        cornerRadius = barRadius
                    )
                    drawLine(
                        color = palette.sectionDivider,
                        start = lineStart,
                        end = lineEnd,
                        strokeWidth = strokePx
                    )
                }
            }
            .clickable(
                onClickLabel = toggleCd,
                interactionSource = null,
                indication = palette.cardRipple,
                onClick = onToggleExpand
            )
            .padding(
                start = 16.dp,
                end = 0.dp,
                top = 6.dp,
                bottom = if (hasExpandedTasks) 8.dp else 10.dp
            )
            .testTag("group_header_${client.id}")
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            BasicText(
                text = client.name,
                style = if (group.isNameRtl) palette.groupTitleRtl else palette.groupTitleLtr,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            BasicText(
                text = group.subtitle,
                style = if (group.isNameRtl) palette.groupSubRtl else palette.groupSubLtr,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isExpanded && group.visibleTasks.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .background(group.badgeBg, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    BasicText(
                        text = group.visibleCountText,
                        style = group.badgeStyle,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = palette.addTaskCd
                    }
                    .clickable(
                        interactionSource = null,
                        indication = palette.iconRipple,
                        onClick = onAddTaskForClient
                    )
                    .drawWithCache {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val half = 7.dp.toPx()
                        val strokeW = 2.2.dp.toPx()
                        val hStart = Offset(cx - half, cy)
                        val hEnd = Offset(cx + half, cy)
                        val vStart = Offset(cx, cy - half)
                        val vEnd = Offset(cx, cy + half)
                        onDrawBehind {
                            drawLine(accent, hStart, hEnd, strokeW, StrokeCap.Round)
                            drawLine(accent, vStart, vEnd, strokeW, StrokeCap.Round)
                        }
                    }
                    .testTag("group_add_task_${client.id}")
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = toggleCd
                    }
                    .clickable(
                        interactionSource = null,
                        indication = palette.iconRipple,
                        onClick = onToggleExpand
                    )
                    .drawWithCache {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val halfW = 5.5.dp.toPx()
                        val halfH = 3.dp.toPx()
                        val strokeW = 2.1.dp.toPx()
                        val upLeft = Offset(cx - halfW, cy + halfH)
                        val upApex = Offset(cx, cy - halfH)
                        val upRight = Offset(cx + halfW, cy + halfH)
                        val dnLeft = Offset(cx - halfW, cy - halfH)
                        val dnApex = Offset(cx, cy + halfH)
                        val dnRight = Offset(cx + halfW, cy - halfH)
                        val tint = palette.onSurfaceVariant
                        onDrawBehind {
                            if (isExpanded) {
                                drawLine(tint, upLeft, upApex, strokeW, StrokeCap.Round)
                                drawLine(tint, upApex, upRight, strokeW, StrokeCap.Round)
                            } else {
                                drawLine(tint, dnLeft, dnApex, strokeW, StrokeCap.Round)
                                drawLine(tint, dnApex, dnRight, strokeW, StrokeCap.Round)
                            }
                        }
                    }
                    .testTag("group_toggle_${client.id}")
            )
        }
    }
}

private class TaskRowRenderCache {
    var titleResult: TextLayoutResult? = null
    var titleOffset: Offset = Offset.Zero
    var detailsResult: TextLayoutResult? = null
    var detailsOffset: Offset = Offset.Zero
    var priorityResult: TextLayoutResult? = null
    var priorityOffset: Offset = Offset.Zero
    var metaResult: TextLayoutResult? = null
    var metaOffset: Offset = Offset.Zero
}

@Composable
private fun FastTaskRowCard(
    prepared: PreparedTask,
    accent: Color,
    palette: WorkspaceRowPalette,
    textMeasurer: TextMeasurer,
    showProjectTag: Boolean,
    onToggleDone: (ClientTask) -> Unit,
    onEdit: (ClientTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val task = prepared.task
    val renderCache = remember { TaskRowRenderCache() }
    val titleStyle = if (task.completed) {
        if (prepared.isTitleRtl) palette.titleDoneRtl else palette.titleDoneLtr
    } else {
        if (prepared.isTitleRtl) palette.titleOpenRtl else palette.titleOpenLtr
    }

    val metaSegments = remember(
        showProjectTag,
        prepared.projectName,
        prepared.hasDue,
        task.dueLabel,
        task.createdByClient,
        palette.clientRequestedLabel
    ) {
        buildList {
            if (showProjectTag && prepared.projectName.isNotBlank()) {
                add(prepared.projectName)
            }
            if (prepared.hasDue) {
                add(task.dueLabel)
            }
            if (task.createdByClient) {
                add(palette.clientRequestedLabel)
            }
        }.joinToString("  ·  ")
    }
    val hasMeta = metaSegments.isNotEmpty()
    val metaStyle = if (task.createdByClient && !prepared.hasDue && !showProjectTag) {
        if (prepared.isTitleRtl) palette.clientReqRtl else palette.clientReqLtr
    } else {
        if (prepared.isDueRtl || prepared.isTitleRtl) palette.dueRtl else palette.dueLtr
    }

    Layout(
        content = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        role = Role.Checkbox,
                        onClickLabel = palette.toggleTaskCd,
                        interactionSource = null,
                        indication = palette.iconRipple,
                        onClick = { onToggleDone(task) }
                    )
                    .drawWithCache {
                        val radius = 11.5.dp.toPx()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val center = Offset(cx, cy)
                        val strokeW = 2.dp.toPx()
                        val uncheckedRadius = radius - strokeW / 2f
                        val uncheckedStroke = Stroke(width = strokeW)
                        val p1 = Offset(cx - 4.3.dp.toPx(), cy + 0.3.dp.toPx())
                        val p2 = Offset(cx - 1.2.dp.toPx(), cy + 3.4.dp.toPx())
                        val p3 = Offset(cx + 4.8.dp.toPx(), cy - 3.1.dp.toPx())
                        onDrawBehind {
                            if (task.completed) {
                                drawCircle(color = accent, radius = radius, center = center)
                                drawLine(Color.White, p1, p2, strokeWidth = strokeW, cap = StrokeCap.Round)
                                drawLine(Color.White, p2, p3, strokeWidth = strokeW, cap = StrokeCap.Round)
                            } else {
                                drawCircle(
                                    color = palette.uncheckedBorder,
                                    radius = uncheckedRadius,
                                    center = center,
                                    style = uncheckedStroke
                                )
                            }
                        }
                    }
                    .testTag("task_toggle_${task.id}")
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val cardTask = prepared
                val cardPalette = palette
                val cardHasMeta = hasMeta
                val r = 16.dp.toPx()
                val cornerRadius = CornerRadius(r, r)
                val strokePx = 1.dp.toPx()
                val half = strokePx / 2f
                val strokeTopLeft = Offset(half, half)
                val strokeSize = Size(size.width - strokePx, size.height - strokePx)
                val stroke = Stroke(strokePx)
                onDrawBehind {
                    drawRoundRect(
                        color = cardPalette.cardSurface,
                        cornerRadius = cornerRadius
                    )
                    drawRoundRect(
                        color = cardPalette.outline,
                        topLeft = strokeTopLeft,
                        size = strokeSize,
                        cornerRadius = cornerRadius,
                        style = stroke
                    )
                    renderCache.titleResult?.let {
                        drawText(textLayoutResult = it, topLeft = renderCache.titleOffset)
                    }
                    if (cardTask.hasDetails) {
                        renderCache.detailsResult?.let {
                            drawText(textLayoutResult = it, topLeft = renderCache.detailsOffset)
                        }
                    }
                    if (cardTask.isHighPriority) {
                        renderCache.priorityResult?.let {
                            drawText(textLayoutResult = it, topLeft = renderCache.priorityOffset)
                        }
                    }
                    if (cardHasMeta) {
                        renderCache.metaResult?.let {
                            drawText(textLayoutResult = it, topLeft = renderCache.metaOffset)
                        }
                    }
                }
            }
            .clickable(
                interactionSource = null,
                indication = palette.cardRipple,
                onClick = { onEdit(task) }
            )
            .semantics {
                contentDescription = buildString {
                    append(task.title)
                    if (prepared.hasDetails) {
                        append(", ")
                        append(task.details)
                    }
                    if (prepared.isHighPriority) {
                        append(", ")
                        append(palette.priorityHighBadgeText)
                    }
                    if (hasMeta) {
                        append(", ")
                        append(metaSegments)
                    }
                }
            }
            .testTag("task_card_${task.id}")
    ) { measurables, constraints ->
        val cardWidth = constraints.maxWidth
        val leadPadPx = 6.dp.roundToPx()
        val checkboxSizePx = 48.dp.roundToPx()
        val gapAfterCheckboxPx = 4.dp.roundToPx()
        val trailPadPx = 14.dp.roundToPx()
        val vertPadPx = 8.dp.roundToPx()
        val textGapPx = 3.dp.roundToPx()
        val metaTopGapPx = 5.dp.roundToPx()
        val metaInlineGapPx = 6.dp.roundToPx()

        val isRtlLayout = layoutDirection == LayoutDirection.Rtl
        val checkboxX = if (isRtlLayout) cardWidth - leadPadPx - checkboxSizePx else leadPadPx
        val textLeftPx = if (isRtlLayout) trailPadPx else leadPadPx + checkboxSizePx + gapAfterCheckboxPx
        val textWidthPx = (cardWidth - leadPadPx - checkboxSizePx - gapAfterCheckboxPx - trailPadPx).coerceAtLeast(1)
        val fullTextConstraints = Constraints(minWidth = textWidthPx, maxWidth = textWidthPx)

        val titleRes = textMeasurer.measure(
            text = task.title,
            style = titleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            constraints = fullTextConstraints
        )
        var cursorY = titleRes.size.height

        var detailsRes: TextLayoutResult? = null
        var detailsRelY = 0
        if (prepared.hasDetails) {
            val dRes = textMeasurer.measure(
                text = task.details,
                style = if (prepared.isDetailsRtl) palette.detailsRtl else palette.detailsLtr,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = fullTextConstraints
            )
            detailsRes = dRes
            detailsRelY = cursorY + textGapPx
            cursorY = detailsRelY + dRes.size.height
        }

        var priorityRes: TextLayoutResult? = null
        var priorityX = textLeftPx
        var priorityRelY = 0
        var metaRes: TextLayoutResult? = null
        var metaX = textLeftPx
        var metaRelY = 0

        if (prepared.isHighPriority && hasMeta) {
            val pRes = textMeasurer.measure(
                text = palette.priorityHighBadgeText,
                style = palette.priorityLtr,
                maxLines = 1,
                constraints = Constraints(maxWidth = textWidthPx)
            )
            val metaMaxWidth = (textWidthPx - pRes.size.width - metaInlineGapPx).coerceAtLeast(0)
            val mRes = textMeasurer.measure(
                text = "·  $metaSegments",
                style = metaStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = metaMaxWidth)
            )
            val metaRowY = cursorY + metaTopGapPx
            val metaHeight = max(pRes.size.height, mRes.size.height)
            val pairStartX = if (prepared.isTitleRtl) {
                textLeftPx + (textWidthPx - pRes.size.width - metaInlineGapPx - mRes.size.width).coerceAtLeast(0)
            } else {
                textLeftPx
            }
            priorityRes = pRes
            priorityX = pairStartX
            priorityRelY = metaRowY + (metaHeight - pRes.size.height) / 2

            metaRes = mRes
            metaX = pairStartX + pRes.size.width + metaInlineGapPx
            metaRelY = metaRowY + (metaHeight - mRes.size.height) / 2
            cursorY = metaRowY + metaHeight
        } else if (prepared.isHighPriority) {
            val pRes = textMeasurer.measure(
                text = palette.priorityHighBadgeText,
                style = if (prepared.isTitleRtl) palette.priorityRtl else palette.priorityLtr,
                maxLines = 1,
                constraints = fullTextConstraints
            )
            priorityRes = pRes
            priorityX = textLeftPx
            priorityRelY = cursorY + metaTopGapPx
            cursorY = priorityRelY + pRes.size.height
        } else if (hasMeta) {
            val mRes = textMeasurer.measure(
                text = metaSegments,
                style = metaStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = fullTextConstraints
            )
            metaRes = mRes
            metaX = textLeftPx
            metaRelY = cursorY + metaTopGapPx
            cursorY = metaRelY + mRes.size.height
        }

        val textHeight = cursorY
        val contentHeight = max(checkboxSizePx, textHeight)
        val cardHeight = vertPadPx * 2 + contentHeight
        val textTopPx = vertPadPx + (contentHeight - textHeight) / 2
        val textLeftF = textLeftPx.toFloat()

        renderCache.titleResult = titleRes
        renderCache.titleOffset = Offset(textLeftF, textTopPx.toFloat())
        renderCache.detailsResult = detailsRes
        if (detailsRes != null) {
            renderCache.detailsOffset = Offset(textLeftF, (textTopPx + detailsRelY).toFloat())
        }
        renderCache.priorityResult = priorityRes
        if (priorityRes != null) {
            renderCache.priorityOffset = Offset(priorityX.toFloat(), (textTopPx + priorityRelY).toFloat())
        }
        renderCache.metaResult = metaRes
        if (metaRes != null) {
            renderCache.metaOffset = Offset(metaX.toFloat(), (textTopPx + metaRelY).toFloat())
        }

        val checkboxPlaceable = measurables[0].measure(
            Constraints.fixed(checkboxSizePx, checkboxSizePx)
        )
        val checkboxY = vertPadPx + (contentHeight - checkboxSizePx) / 2

        layout(cardWidth, cardHeight) {
            checkboxPlaceable.place(checkboxX, checkboxY)
        }
    }
}

@Composable
private fun SelectedClientBanner(
    client: Client,
    accent: Color,
    projectCount: Int,
    openCount: Int,
    totalCount: Int,
    onEditClient: () -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doneCount = totalCount - openCount
    val progress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 320),
        label = "client_banner_progress"
    )
    val initials = remember(client.name) { clientInitials(client.name) }
    val isNameRtl = remember(client.name) { client.name.isRtlText() }

    Surface(
        shape = GroupFullShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(accent, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.titleLarge.withBidi(isNameRtl),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (projectCount == 1) {
                                    stringResource(R.string.workspace_project_count_one)
                                } else {
                                    stringResource(R.string.workspace_project_count_many, projectCount)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (client.portalEnabled) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.clients_portal_active_badge),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onAddProject,
                        modifier = Modifier.testTag("banner_add_project_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = stringResource(R.string.workspace_project_add_cd),
                            tint = accent
                        )
                    }
                    IconButton(
                        onClick = onEditClient,
                        modifier = Modifier.testTag("edit_selected_client_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.clients_edit_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (openCount == 0 && totalCount > 0) {
                        stringResource(R.string.workspace_all_done)
                    } else {
                        stringResource(R.string.workspace_active_count, openCount)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Text(
                    text = stringResource(R.string.clients_done_progress, doneCount, totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}

@Composable
private fun QuickAddTaskBar(
    targetName: String,
    accent: Color,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.withBidi(value),
            placeholder = {
                Text(
                    text = stringResource(R.string.workspace_quick_add_hint, bidiIsolate(targetName)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = TaskCardShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedBorderColor = accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .testTag("quick_add_task_input")
        )
        FilledIconButton(
            onClick = onSubmit,
            enabled = value.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent,
                contentColor = Color.White
            ),
            shape = TaskCardShape,
            modifier = Modifier
                .size(54.dp)
                .testTag("quick_add_task_submit")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.workspace_add_task)
            )
        }
    }
}

@Composable
private fun EmptyClientsWorkspace(
    onRegisterFirstClient: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_workspace_hero_1795086558017),
                    contentDescription = stringResource(R.string.cd_workspace_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(TaskCardShape)
                )
                Text(
                    text = stringResource(R.string.workspace_empty_clients_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.workspace_empty_clients_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRegisterFirstClient,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("empty_register_client_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.clients_register_button))
                }
            }
        }
    }
}

@Composable
private fun AllCaughtUpCard(
    targetName: String,
    accent: Color,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = GroupFullShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.workspace_empty_tasks_title, bidiIsolate(targetName)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.workspace_empty_tasks_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onAddTask) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.workspace_add_task))
            }
        }
    }
}
