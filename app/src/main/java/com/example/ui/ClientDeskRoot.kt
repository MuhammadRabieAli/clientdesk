package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutClientDesk
import com.example.data.Client
import com.example.data.ClientDeskRepository
import com.example.data.ClientPortalSession
import com.example.data.ClientProject
import com.example.data.ClientTask
import com.example.data.PortalSessionStore
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.data.WidgetSnapshotStore
import com.example.widget.WidgetRefresher
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TASKS(R.string.nav_tasks, Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle),
    CLIENTS(R.string.nav_clients, Icons.Rounded.Folder, Icons.Outlined.Folder),
    WIDGETS(R.string.nav_widgets, Icons.Rounded.Widgets, Icons.Outlined.Widgets)
}

data class WidgetLaunchRequest(
    val clientId: String?,
    val openAddTask: Boolean,
    val nonce: Long
)

@Composable
fun ClientDeskRoot(
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    launchRequest: WidgetLaunchRequest?,
    onLaunchRequestConsumed: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var portalSession by remember {
        mutableStateOf<ClientPortalSession?>(PortalSessionStore.loadSession(context.applicationContext))
    }
    var authBannerMessage by remember { mutableStateOf<String?>(null) }
    var startInClientPortal by remember { mutableStateOf(false) }

    val revokedMessage = stringResource(R.string.portal_access_revoked)

    val user = currentUser
    when {
        user != null -> {
            key(user.uid) {
                AuthenticatedClientDesk(
                    currentUserId = user.uid,
                    themeMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    onThemeModeChange = onThemeModeChange,
                    launchRequest = launchRequest,
                    onLaunchRequestConsumed = onLaunchRequestConsumed
                )
            }
        }

        portalSession != null -> {
            val activeSession = portalSession!!
            key(activeSession.portalKey) {
                ClientPortalScreen(
                    session = activeSession,
                    isDarkTheme = isDarkTheme,
                    onThemeModeChange = onThemeModeChange,
                    onSignOut = {
                        portalSession = null
                        authBannerMessage = null
                        startInClientPortal = true
                    },
                    onSessionRevoked = {
                        portalSession = null
                        authBannerMessage = revokedMessage
                        startInClientPortal = true
                    }
                )
            }
        }

        else -> {
            AuthScreen(
                onAuthSuccess = {
                    authBannerMessage = null
                    startInClientPortal = false
                },
                onPortalSignInSuccess = { session ->
                    authBannerMessage = null
                    startInClientPortal = false
                    portalSession = session
                },
                initialBannerMessage = authBannerMessage,
                startInClientPortal = startInClientPortal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedClientDesk(
    currentUserId: String,
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    launchRequest: WidgetLaunchRequest?,
    onLaunchRequestConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ClientDeskViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val repo = ClientDeskRepository(app)
                ClientDeskViewModel(
                    repository = repo,
                    currentUserId = currentUserId,
                    onSyncWidgets = { uid, clients, tasks ->
                        WidgetSnapshotStore.saveSnapshot(app, uid, clients, tasks)
                        WidgetRefresher.refreshAllWidgets(app)
                    }
                )
            }
        }
    )

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val selectedClientId by viewModel.selectedClientId.collectAsStateWithLifecycle()
    val selectedProjectByClient by viewModel.selectedProjectByClient.collectAsStateWithLifecycle()
    val taskFilter by viewModel.taskFilter.collectAsStateWithLifecycle()
    val collapsedClientIds by viewModel.collapsedClientIds.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceAutoApply by viewModel.voiceAutoApply.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.TASKS) }
    var showClientEditor by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<Client?>(null) }

    var showProjectEditor by remember { mutableStateOf(false) }
    var projectEditorClient by remember { mutableStateOf<Client?>(null) }
    var editingProject by remember { mutableStateOf<ClientProject?>(null) }
    var projectToDelete by remember { mutableStateOf<ClientProject?>(null) }

    var showTaskEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ClientTask?>(null) }
    var taskEditorInitialClientId by remember { mutableStateOf<String?>(null) }
    var taskEditorInitialProjectId by remember { mutableStateOf<String?>(null) }

    var clientToDelete by remember { mutableStateOf<Client?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var showVoiceAssistant by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val allClientsListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(launchRequest?.nonce) {
        if (launchRequest != null) {
            currentTab = AppTab.TASKS
            if (!launchRequest.clientId.isNullOrBlank()) {
                viewModel.selectClient(launchRequest.clientId)
            }
            if (launchRequest.openAddTask) {
                editingTask = null
                taskEditorInitialClientId = launchRequest.clientId
                taskEditorInitialProjectId = launchRequest.clientId?.let { viewModel.activeProjectIdFor(it) }
                showTaskEditor = true
            }
            onLaunchRequestConsumed()
        }
    }

    BackHandler(enabled = currentTab != AppTab.TASKS || selectedClientId != null) {
        if (currentTab != AppTab.TASKS) {
            currentTab = AppTab.TASKS
        } else if (selectedClientId != null) {
            viewModel.selectClient(null)
        }
    }

    val currentWorkspace = (workspaceState as? UiState.Success)?.data
    val density = LocalDensity.current
    var useRail by remember { mutableStateOf(false) }

    val onSelectClient = remember(viewModel) { viewModel::selectClient }
    val onSelectProject = remember(viewModel) { viewModel::selectProject }
    val onReorderClients = remember(viewModel) { viewModel::reorderClients }
    val onToggleClientCollapsed = remember(viewModel) { viewModel::toggleClientCollapsed }
    val onSetAllClientsCollapsed = remember(viewModel) { viewModel::setAllClientsCollapsed }
    val onSetTaskFilter = remember(viewModel) { viewModel::setTaskFilter }
    val onToggleTaskDone = remember(viewModel) { viewModel::toggleTaskDone }

    val onOpenNewClient = remember {
        {
            editingClient = null
            showClientEditor = true
        }
    }
    val onEditClient = remember {
        { client: Client ->
            editingClient = client
            showClientEditor = true
        }
    }
    val onOpenNewProject = remember {
        { client: Client ->
            projectEditorClient = client
            editingProject = null
            showProjectEditor = true
        }
    }
    val onEditProject = remember {
        { client: Client, project: ClientProject ->
            projectEditorClient = client
            editingProject = project
            showProjectEditor = true
        }
    }
    val onQuickAddTask = remember(viewModel) {
        { clientId: String, projectId: String?, title: String ->
            viewModel.createTask(clientId = clientId, projectId = projectId, title = title)
        }
    }
    val onOpenAddTaskSheet = remember(viewModel) {
        { clientId: String?, projectId: String? ->
            editingTask = null
            taskEditorInitialClientId = clientId
            taskEditorInitialProjectId = projectId ?: clientId?.let { viewModel.activeProjectIdFor(it) }
            showTaskEditor = true
        }
    }
    val onEditTask = remember {
        { task: ClientTask ->
            editingTask = task
            taskEditorInitialClientId = task.clientId
            taskEditorInitialProjectId = task.projectId
            showTaskEditor = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val nextUseRail = with(density) { size.width.toDp() >= 600.dp }
                if (useRail != nextUseRail) {
                    useRail = nextUseRail
                }
            }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showVoiceAssistant = true },
                            modifier = Modifier.testTag("open_voice_assistant_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.voice_open_cd),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        IconButton(
                            onClick = { showAiAssistant = true },
                            modifier = Modifier.testTag("open_ai_assistant_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.ai_open_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                onThemeModeChange(
                                    if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                                )
                            },
                            modifier = Modifier.testTag("theme_toggle_icon_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) {
                                    Icons.Rounded.LightMode
                                } else {
                                    Icons.Rounded.DarkMode
                                },
                                contentDescription = stringResource(R.string.theme_toggle_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.testTag("sign_out_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = stringResource(R.string.auth_sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentTab == AppTab.TASKS && currentWorkspace != null) {
                    val fabElevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                    val fabShapeSmall = remember { RoundedCornerShape(14.dp) }
                    val fabShapeLarge = remember { RoundedCornerShape(18.dp) }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.graphicsLayer {}
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallFloatingActionButton(
                                onClick = { showAiAssistant = true },
                                shape = fabShapeSmall,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                elevation = fabElevation,
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, fabShapeSmall)
                                    .testTag("fab_ai_scan")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = stringResource(R.string.ai_open_cd)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = { showVoiceAssistant = true },
                                shape = fabShapeSmall,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                elevation = fabElevation,
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, fabShapeSmall)
                                    .testTag("fab_voice_assistant")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.voice_open_cd)
                                )
                            }
                        }
                        if (currentWorkspace.clients.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    val targetClientId = selectedClientId
                                        ?: currentWorkspace.clients.firstOrNull()?.id
                                    editingTask = null
                                    taskEditorInitialClientId = targetClientId
                                    taskEditorInitialProjectId = targetClientId?.let {
                                        viewModel.activeProjectIdFor(it)
                                    }
                                    showTaskEditor = true
                                },
                                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.workspace_add_task)) },
                                shape = fabShapeLarge,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                elevation = fabElevation,
                                modifier = Modifier.testTag("fab_add_task")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = workspaceState) {
                        is UiState.Loading -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        is UiState.Success -> {
                            when (currentTab) {
                                AppTab.TASKS -> {
                                    TasksWorkspaceScreen(
                                        workspace = state.data,
                                        selectedClientId = selectedClientId,
                                        selectedProjectByClient = selectedProjectByClient,
                                        taskFilter = taskFilter,
                                        collapsedClientIds = collapsedClientIds,
                                        onSelectClient = onSelectClient,
                                        onSelectProject = onSelectProject,
                                        onReorderClients = onReorderClients,
                                        onToggleClientCollapsed = onToggleClientCollapsed,
                                        onSetAllClientsCollapsed = onSetAllClientsCollapsed,
                                        onSetFilter = onSetTaskFilter,
                                        onOpenNewClient = onOpenNewClient,
                                        onEditClient = onEditClient,
                                        onOpenNewProject = onOpenNewProject,
                                        onEditProject = onEditProject,
                                        onQuickAddTask = onQuickAddTask,
                                        onOpenAddTaskSheet = onOpenAddTaskSheet,
                                        onToggleTaskDone = onToggleTaskDone,
                                        onEditTask = onEditTask,
                                        allClientsListState = allClientsListState
                                    )
                                }

                                AppTab.CLIENTS -> {
                                    ClientsDirectoryScreen(
                                        workspace = state.data,
                                        onOpenClientTasks = { client, projectId ->
                                            viewModel.selectClient(client.id)
                                            if (!projectId.isNullOrBlank()) {
                                                viewModel.selectProject(client.id, projectId)
                                            }
                                            currentTab = AppTab.TASKS
                                        },
                                        onRegisterClient = {
                                            editingClient = null
                                            showClientEditor = true
                                        },
                                        onAddProjectForClient = { client ->
                                            projectEditorClient = client
                                            editingProject = null
                                            showProjectEditor = true
                                        },
                                        onAddTaskForClient = { client ->
                                            editingTask = null
                                            taskEditorInitialClientId = client.id
                                            taskEditorInitialProjectId = viewModel.activeProjectIdFor(client.id)
                                            showTaskEditor = true
                                        },
                                        onEditClient = { client ->
                                            editingClient = client
                                            showClientEditor = true
                                        },
                                        onRequestDeleteClient = { client ->
                                            clientToDelete = client
                                        }
                                    )
                                }

                                AppTab.WIDGETS -> {
                                    WidgetsStudioScreen(
                                        workspace = state.data,
                                        themeMode = themeMode,
                                        isDarkTheme = isDarkTheme,
                                        onThemeModeChange = onThemeModeChange,
                                        onToggleTaskDone = viewModel::toggleTaskDone,
                                        onOpenAddTaskForClient = { clientId ->
                                            viewModel.selectClient(clientId)
                                            editingTask = null
                                            taskEditorInitialClientId = clientId
                                            taskEditorInitialProjectId = viewModel.activeProjectIdFor(clientId)
                                            showTaskEditor = true
                                        },
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Client Create / Edit Modal Sheet
    if (showClientEditor) {
        ClientEditorSheet(
            existingClient = editingClient,
            existingClients = currentWorkspace?.clients.orEmpty(),
            isBusy = isBusy,
            onDismiss = { showClientEditor = false },
            onSubmit = { name, company, notes, colorHex, portalEnabled, portalEmail, portalPassword ->
                val target = editingClient
                if (target == null) {
                    viewModel.registerClient(
                        name = name,
                        company = company,
                        notes = notes,
                        colorHex = colorHex,
                        portalEnabled = portalEnabled,
                        portalEmail = portalEmail,
                        portalPassword = portalPassword,
                        onSuccess = { showClientEditor = false }
                    )
                } else {
                    viewModel.updateClient(
                        existingClient = target,
                        name = name,
                        company = company,
                        notes = notes,
                        colorHex = colorHex,
                        portalEnabled = portalEnabled,
                        portalEmail = portalEmail,
                        newPortalPassword = portalPassword,
                        onSuccess = { showClientEditor = false }
                    )
                }
            }
        )
    }

    // Project Create / Edit Modal Sheet
    val projClient = projectEditorClient
    if (showProjectEditor && projClient != null) {
        val clientProjectsCount = currentWorkspace?.projectsByClient?.get(projClient.id)?.size ?: 1
        ProjectEditorSheet(
            client = projClient,
            existingProject = editingProject,
            canDelete = editingProject != null && clientProjectsCount > 1,
            isBusy = isBusy,
            onDismiss = { showProjectEditor = false },
            onSubmit = { name, description, colorHex ->
                val target = editingProject
                if (target == null) {
                    viewModel.createProject(
                        client = projClient,
                        name = name,
                        description = description,
                        colorHex = colorHex,
                        onSuccess = { showProjectEditor = false }
                    )
                } else {
                    viewModel.updateProject(
                        project = target,
                        name = name,
                        description = description,
                        colorHex = colorHex,
                        onSuccess = { showProjectEditor = false }
                    )
                }
            },
            onRequestDelete = { proj ->
                showProjectEditor = false
                projectToDelete = proj
            }
        )
    }

    // Task Create / Edit Modal Sheet
    if (showTaskEditor && currentWorkspace != null) {
        TaskEditorSheet(
            clients = currentWorkspace.clients,
            projectsByClient = currentWorkspace.projectsByClient,
            initialClientId = taskEditorInitialClientId,
            initialProjectId = taskEditorInitialProjectId,
            existingTask = editingTask,
            isBusy = isBusy,
            onDismiss = { showTaskEditor = false },
            onSubmit = { clientId, projectId, title, details, priority, dueLabel, completed ->
                val target = editingTask
                if (target == null) {
                    viewModel.createTask(
                        clientId = clientId,
                        projectId = projectId,
                        title = title,
                        details = details,
                        priority = priority,
                        dueLabel = dueLabel,
                        onSuccess = { showTaskEditor = false }
                    )
                } else {
                    viewModel.updateTask(
                        task = target,
                        projectId = projectId,
                        title = title,
                        details = details,
                        priority = priority,
                        dueLabel = dueLabel,
                        completed = completed,
                        onSuccess = { showTaskEditor = false }
                    )
                }
            },
            onDelete = { task ->
                viewModel.deleteTask(task)
                showTaskEditor = false
            }
        )
    }

    // Confirm Client Deletion
    val deletingClient = clientToDelete
    if (deletingClient != null && currentWorkspace != null) {
        val projectCount = currentWorkspace.projectsByClient[deletingClient.id]?.size ?: 1
        val taskCount = currentWorkspace.tasks.count { it.clientId == deletingClient.id }
        ConfirmDeleteClientDialog(
            client = deletingClient,
            projectCount = projectCount,
            taskCount = taskCount,
            onDismiss = { clientToDelete = null },
            onConfirm = {
                viewModel.deleteClient(deletingClient)
                clientToDelete = null
            }
        )
    }

    // Confirm Project Deletion
    val deletingProject = projectToDelete
    if (deletingProject != null && currentWorkspace != null) {
        val taskCount = currentWorkspace.preparedByProject[deletingProject.id]?.size ?: 0
        ConfirmDeleteProjectDialog(
            project = deletingProject,
            taskCount = taskCount,
            onDismiss = { projectToDelete = null },
            onConfirm = {
                viewModel.deleteProject(deletingProject)
                projectToDelete = null
            }
        )
    }

    // Confirm Sign Out
    if (showSignOutDialog) {
        ConfirmSignOutDialog(
            onDismiss = { showSignOutDialog = false },
            onConfirm = {
                showSignOutDialog = false
                signOutClientDesk(
                    context = context,
                    credentialManager = credentialManager,
                    scope = scope
                )
            }
        )
    }

    // Gemini AI Assistant & Image Scanner Sheet
    if (showAiAssistant) {
        AiAssistantSheet(
            clients = currentWorkspace?.clients.orEmpty(),
            selectedClientId = selectedClientId,
            aiState = aiState,
            isBusy = isBusy,
            onDismiss = {
                showAiAssistant = false
                viewModel.resetAiState()
            },
            onAnalyze = { promptText, bitmap, preferredClient ->
                viewModel.analyzeWithGemini(
                    prompt = promptText,
                    image = bitmap,
                    preferredClientId = preferredClient
                )
            },
            onCancelAnalyze = viewModel::cancelAiAnalysis,
            onResetAi = viewModel::resetAiState,
            onApplyPlan = { clientsToCreate, tasksToCreate ->
                viewModel.applyAiPlan(
                    clientsToCreate = clientsToCreate,
                    tasksToCreate = tasksToCreate,
                    onSuccess = { showAiAssistant = false }
                )
            },
            onShowMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onSwitchToVoice = {
                showAiAssistant = false
                viewModel.resetAiState()
                showVoiceAssistant = true
            }
        )
    }

    // Gemini Multimodal Voice Assistant Sheet
    if (showVoiceAssistant) {
        VoiceAssistantSheet(
            clients = currentWorkspace?.clients.orEmpty(),
            selectedClientId = selectedClientId,
            voiceState = voiceState,
            autoApply = voiceAutoApply,
            isBusy = isBusy,
            onSetAutoApply = viewModel::setVoiceAutoApply,
            onDismiss = {
                showVoiceAssistant = false
                viewModel.resetVoiceState()
            },
            onSendAudio = { wavBytes, preferredClient ->
                viewModel.processVoiceCommand(
                    audioWavBytes = wavBytes,
                    preferredClientId = preferredClient
                )
            },
            onSendTextCommand = { cmd, preferredClient ->
                viewModel.processVoiceCommand(
                    typedCommand = cmd,
                    preferredClientId = preferredClient
                )
            },
            onRetryLast = viewModel::retryLastVoiceCommand,
            onCancelProcessing = viewModel::cancelVoiceProcessing,
            onResetVoice = viewModel::resetVoiceState,
            onApplyPlan = { plan, clientsToCreate, tasksToCreate, taskActions ->
                viewModel.applyVoicePlan(
                    plan = plan,
                    clientsToCreate = clientsToCreate,
                    tasksToCreate = tasksToCreate,
                    taskActions = taskActions
                )
            }
        )
    }
}
