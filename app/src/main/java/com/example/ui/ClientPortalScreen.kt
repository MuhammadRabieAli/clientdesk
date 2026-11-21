package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ClientDeskRepository
import com.example.data.ClientPortalSession
import com.example.data.PortalProject
import com.example.data.PortalSessionStore
import com.example.data.PortalTask
import com.example.data.TaskPriority
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.ui.theme.StudioTerracotta
import com.example.ui.theme.parseClientColor
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPortalScreen(
    session: ClientPortalSession,
    isDarkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onSessionRevoked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: ClientPortalViewModel = viewModel(
        key = "portal_${session.portalKey}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                ClientPortalViewModel(
                    repository = ClientDeskRepository(app),
                    session = session
                )
            }
        }
    )

    val portalState by viewModel.portalState.collectAsStateWithLifecycle()
    val taskFilter by viewModel.taskFilter.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddTaskSheet by remember { mutableStateOf(false) }
    var activeProjectIdForAdd by remember { mutableStateOf<String?>(null) }
    var inspectingTask by remember { mutableStateOf<Pair<PortalTask, PortalProject?>?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentData = (portalState as? UiState.Success)?.data
    LaunchedEffect(currentData?.isRevoked) {
        if (currentData?.isRevoked == true) {
            PortalSessionStore.clearSession(context.applicationContext)
            onSessionRevoked()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currentData?.profile?.clientName ?: session.clientName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.portal_badge_label),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val sub = currentData?.profile?.clientCompany?.takeIf { it.isNotBlank() }
                            ?: session.portalEmail
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onThemeModeChange(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                        },
                        modifier = Modifier.testTag("portal_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = stringResource(R.string.theme_toggle_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.testTag("portal_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.auth_sign_out),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (currentData != null && currentData.projects.isNotEmpty() && !currentData.isRevoked) {
                ExtendedFloatingActionButton(
                    onClick = { showAddTaskSheet = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.portal_add_task_fab)) },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = currentData.accent,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    ),
                    modifier = Modifier.testTag("portal_fab_add_task")
                )
            }
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = portalState) {
                is UiState.Loading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }

                is UiState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is UiState.Success -> {
                    val data = state.data
                    val projects = data.projects
                    if (projects.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.portal_empty_project_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val pagerState = rememberPagerState(
                            initialPage = 0,
                            pageCount = { projects.size }
                        )
                        val activeProject = projects.getOrNull(pagerState.currentPage) ?: projects.first()
                        LaunchedEffect(activeProject.id) {
                            activeProjectIdForAdd = activeProject.id
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                        ) {
                            // Overall Progress Hero Card
                            PortalOverallProgressCard(
                                data = data,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )

                            // Project Switcher Strip
                            PortalProjectStrip(
                                projects = projects,
                                activeProjectId = activeProject.id,
                                openCountByProject = data.openCountByProject,
                                onSelectProject = { targetProject ->
                                    val idx = projects.indexOfFirst { it.id == targetProject.id }
                                    if (idx >= 0) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                page = idx,
                                                animationSpec = tween(durationMillis = 280)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )

                            // Swipeable Project HorizontalPager
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                key = { idx -> projects.getOrNull(idx)?.id ?: "portal_proj_$idx" },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("portal_project_pager")
                            ) { pageIdx ->
                                val project = projects.getOrNull(pageIdx) ?: return@HorizontalPager

                                val projectTasks = data.tasksByProject[project.id].orEmpty()
                                val filteredTasks = remember(projectTasks, taskFilter) {
                                    when (taskFilter) {
                                        TaskFilter.ALL -> projectTasks
                                        TaskFilter.OPEN -> projectTasks.filter { !it.completed }
                                        TaskFilter.DONE -> projectTasks.filter { it.completed }
                                    }
                                }
                                val doneCount = data.doneCountByProject[project.id] ?: 0
                                val openCount = data.openCountByProject[project.id] ?: 0

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val pageOffset = ((pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction)
                                                .absoluteValue
                                                .coerceIn(0f, 1f)
                                            val scale = 1f - (pageOffset * 0.025f)
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = 1f - (pageOffset * 0.14f)
                                            // ModulateAlpha avoids an offscreen buffer for the whole page on every swipe frame
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                ) {
                                    PortalProjectPage(
                                        project = project,
                                        doneCount = doneCount,
                                        openCount = openCount,
                                        filteredTasks = filteredTasks,
                                        taskFilter = taskFilter,
                                        onSetFilter = viewModel::setTaskFilter,
                                        onInspectTask = { task -> inspectingTask = task to project }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Task Sheet (Client Portal Create-Only)
    if (showAddTaskSheet && currentData != null && currentData.projects.isNotEmpty()) {
        ClientPortalAddTaskSheet(
            projects = currentData.projects,
            initialProjectId = activeProjectIdForAdd ?: currentData.projects.first().id,
            isBusy = isBusy,
            onDismiss = { showAddTaskSheet = false },
            onSubmit = { projectId, projectName, title, details, priority, dueLabel ->
                viewModel.submitTask(
                    projectId = projectId,
                    projectName = projectName,
                    title = title,
                    details = details,
                    priority = priority,
                    dueLabel = dueLabel,
                    onSuccess = { showAddTaskSheet = false }
                )
            }
        )
    }

    // Read-Only Task Detail Bottom Sheet
    val inspected = inspectingTask
    if (inspected != null) {
        PortalTaskDetailReadOnlySheet(
            task = inspected.first,
            project = inspected.second,
            onDismiss = { inspectingTask = null }
        )
    }

    // Confirm Sign Out
    if (showSignOutDialog) {
        ConfirmSignOutDialog(
            titleRes = R.string.portal_sign_out_title,
            bodyRes = R.string.portal_sign_out_body,
            onDismiss = { showSignOutDialog = false },
            onConfirm = {
                showSignOutDialog = false
                PortalSessionStore.clearSession(context.applicationContext)
                onSignOut()
            }
        )
    }
}

@Composable
private fun PortalOverallProgressCard(
    data: ClientPortalData,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = data.overallProgress,
        animationSpec = tween(durationMillis = 360),
        label = "portal_overall_progress"
    )
    val percent = (data.overallProgress * 100f).roundToInt()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.portal_overall_progress_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.portal_projects_summary,
                            data.projects.size,
                            data.completedTasks,
                            data.totalTasks
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = data.accent.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = data.accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = data.accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

@Composable
private fun PortalProjectStrip(
    projects: List<PortalProject>,
    activeProjectId: String,
    openCountByProject: Map<String, Int>,
    onSelectProject: (PortalProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    LaunchedEffect(activeProjectId, projects.size) {
        val idx = projects.indexOfFirst { it.id == activeProjectId }
        if (idx >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == idx && it.offset >= 0 }) {
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
            .testTag("portal_project_strip")
    ) {
        items(items = projects, key = { it.id }) { project ->
            val selected = project.id == activeProjectId
            val accent = parseClientColor(project.colorHex)
            val openCount = openCountByProject[project.id] ?: 0
            val containerColor = if (selected) accent else colorScheme.surfaceContainerLowest
            val contentColor = if (selected) Color.White else colorScheme.onSurface
            val badgeBg = if (selected) Color.White.copy(alpha = 0.22f) else colorScheme.surfaceVariant

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor)
                    .then(
                        if (!selected) {
                            Modifier.border(1.dp, colorScheme.outline, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .clickable { onSelectProject(project) }
                    .padding(horizontal = 13.dp)
                    .testTag("portal_project_tab_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (selected) Color.White else accent,
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
    }
}

@Composable
private fun PortalProjectPage(
    project: PortalProject,
    doneCount: Int,
    openCount: Int,
    filteredTasks: List<PortalTask>,
    taskFilter: TaskFilter,
    onSetFilter: (TaskFilter) -> Unit,
    onInspectTask: (PortalTask) -> Unit
) {
    val accent = parseClientColor(project.colorHex)
    val totalCount = doneCount + openCount
    val progress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 320),
        label = "portal_project_progress"
    )
    val percent = (progress * 100f).roundToInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Per-Project Progress Card
        item(key = "portal_proj_card_${project.id}") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                    .background(accent)
                            )
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleMedium.withBidi(project.name),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                    }

                    if (project.description.isNotBlank()) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodySmall.withBidi(project.description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.portal_project_progress_summary,
                            doneCount,
                            totalCount,
                            openCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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

        // All / Open / Done Filter Bar
        item(key = "portal_filter_${project.id}") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
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
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable { onSetFilter(filter) }
                            .padding(horizontal = 16.dp)
                            .testTag("portal_filter_${filter.name.lowercase()}")
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
        }

        // Read-Only Task List
        if (filteredTasks.isEmpty()) {
            item(key = "portal_empty_${project.id}") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.portal_empty_project_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.portal_empty_project_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredTasks,
                key = { it.id }
            ) { task ->
                PortalReadOnlyTaskCard(
                    task = task,
                    accent = accent,
                    onClick = { onInspectTask(task) }
                )
            }
        }
    }
}

@Composable
private fun PortalReadOnlyTaskCard(
    task: PortalTask,
    accent: Color,
    onClick: () -> Unit
) {
    val priority = task.priorityEnum()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("portal_task_card_${task.id}")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            // Non-interactive read-only status indicator
            Icon(
                imageVector = if (task.completed) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = stringResource(
                    if (task.completed) R.string.portal_status_completed else R.string.portal_status_open
                ),
                tint = if (task.completed) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.withBidi(task.title).copy(
                        fontWeight = if (task.completed) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (task.details.isNotBlank()) {
                    Text(
                        text = task.details,
                        style = MaterialTheme.typography.bodySmall.withBidi(task.details),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Read-only metadata pills: Priority, Due Date, Added by you
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    if (!task.completed && priority == TaskPriority.HIGH) {
                        Surface(
                            shape = CircleShape,
                            color = StudioTerracotta.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "● ${stringResource(R.string.priority_high)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = StudioTerracotta,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else if (!task.completed && priority == TaskPriority.LOW) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.priority_low),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (task.dueLabel.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = task.dueLabel,
                                    style = MaterialTheme.typography.labelSmall.withBidi(task.dueLabel),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (task.createdByClient) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.portal_added_by_you),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalTaskDetailReadOnlySheet(
    task: PortalTask,
    project: PortalProject?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = parseClientColor(project?.colorHex ?: "#2555E6")
    val priority = task.priorityEnum()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (task.completed) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (task.completed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(
                                if (task.completed) R.string.portal_status_completed else R.string.portal_status_open
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (task.completed) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (project != null) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.labelSmall.withBidi(project.name),
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (task.createdByClient) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.portal_added_by_you),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall.withBidi(task.title),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.field_task_priority)}: ${priority.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (priority == TaskPriority.HIGH) StudioTerracotta else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.dueLabel.isNotBlank()) {
                    Text(
                        text = "·  ${task.dueLabel}",
                        style = MaterialTheme.typography.labelMedium.withBidi(task.dueLabel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (task.details.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.details,
                        style = MaterialTheme.typography.bodyMedium.withBidi(task.details),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Calm Read-Only Explanation Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.portal_read_only_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("portal_detail_close_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientPortalAddTaskSheet(
    projects: List<PortalProject>,
    initialProjectId: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        projectId: String,
        projectName: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProjectId by remember(initialProjectId, projects) {
        mutableStateOf(
            initialProjectId.takeIf { id -> projects.any { it.id == id } }
                ?: projects.first().id
        )
    }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var dueLabel by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.first()
    val canSubmit = title.trim().isNotEmpty() && selectedProjectId.isNotBlank() && !isBusy

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.portal_add_task_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.portal_add_task_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Project Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.field_task_project),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    projects.forEach { project ->
                        val chosen = project.id == selectedProjectId
                        val accent = parseClientColor(project.colorHex)
                        FilterChip(
                            selected = chosen,
                            onClick = { selectedProjectId = project.id },
                            label = {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.labelLarge.withBidi(project.name),
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.16f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("portal_add_project_chip_${project.id}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 140) title = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(title),
                label = { Text(stringResource(R.string.field_task_title)) },
                placeholder = { Text(stringResource(R.string.field_task_title_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("portal_task_title_input")
            )

            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 400) details = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(details),
                label = { Text(stringResource(R.string.field_task_details)) },
                placeholder = { Text(stringResource(R.string.field_task_details_placeholder)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("portal_task_details_input")
            )

            OutlinedTextField(
                value = dueLabel,
                onValueChange = { if (it.length <= 40) dueLabel = it },
                textStyle = MaterialTheme.typography.bodyLarge.withBidi(dueLabel),
                label = { Text(stringResource(R.string.field_task_due)) },
                placeholder = { Text(stringResource(R.string.field_task_due_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("portal_task_due_input")
            )

            // Priority Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.field_task_priority),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TaskPriority.entries.forEach { option ->
                        val selected = priority == option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { priority = option }
                                .testTag("portal_priority_${option.raw.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSubmit(
                            selectedProject.id,
                            selectedProject.name,
                            title,
                            details,
                            priority,
                            dueLabel
                        )
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("portal_submit_task_button")
                ) {
                    Text(stringResource(R.string.portal_add_task_submit))
                }
            }
        }
    }
}
