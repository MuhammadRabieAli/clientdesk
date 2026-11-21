package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantState
import com.example.ai.GeminiVoiceAssistant
import com.example.ai.GeminiWorkspaceAssistant
import com.example.ai.ProposedClient
import com.example.ai.ProposedTask
import com.example.ai.ProposedTaskAction
import com.example.ai.VoiceAssistantPlan
import com.example.ai.VoiceAssistantState
import com.example.ai.VoiceTaskActionType
import com.example.data.Client
import com.example.data.ClientDeskRepository
import com.example.data.ClientProject
import com.example.data.ClientTask
import com.example.data.PortalCrypto
import com.example.data.TaskPriority
import com.example.data.TaskStatus
import com.example.data.UiState
import com.example.ui.theme.parseClientColor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, OPEN, ONGOING, DONE }

@Immutable
data class PreparedTask(
    val task: ClientTask,
    val projectName: String,
    val isTitleRtl: Boolean,
    val isDetailsRtl: Boolean,
    val isDueRtl: Boolean,
    val isHighPriority: Boolean,
    val hasDetails: Boolean,
    val hasDue: Boolean,
    val dueWithSeparator: String
)

@Immutable
data class CollapsedClientsState(
    val ids: Set<String> = emptySet()
)

@Immutable
data class WorkspaceData(
    val clients: List<Client> = emptyList(),
    val projects: List<ClientProject> = emptyList(),
    val tasks: List<ClientTask> = emptyList(),
    val projectsByClient: Map<String, List<ClientProject>> = emptyMap(),
    val projectById: Map<String, ClientProject> = emptyMap(),
    val preparedByClient: Map<String, List<PreparedTask>> = emptyMap(),
    val openPreparedByClient: Map<String, List<PreparedTask>> = emptyMap(),
    val donePreparedByClient: Map<String, List<PreparedTask>> = emptyMap(),
    val ongoingPreparedByClient: Map<String, List<PreparedTask>> = emptyMap(),
    val preparedByProject: Map<String, List<PreparedTask>> = emptyMap(),
    val openPreparedByProject: Map<String, List<PreparedTask>> = emptyMap(),
    val donePreparedByProject: Map<String, List<PreparedTask>> = emptyMap(),
    val ongoingPreparedByProject: Map<String, List<PreparedTask>> = emptyMap(),
    val openCountByClient: Map<String, Int> = emptyMap(),
    val openCountByProject: Map<String, Int> = emptyMap(),
    val totalOpen: Int = 0,
    val clientNameRtlById: Map<String, Boolean> = emptyMap(),
    val clientAccentById: Map<String, Color> = emptyMap(),
    val projectAccentById: Map<String, Color> = emptyMap()
) {
    fun defaultProjectFor(clientId: String): ClientProject? {
        val list = projectsByClient[clientId].orEmpty()
        return list.firstOrNull { it.defaultProject } ?: list.firstOrNull()
    }
}

class ClientDeskViewModel(
    private val repository: ClientDeskRepository,
    private val currentUserId: String,
    private val onSyncWidgets: (String, List<Client>, List<ClientTask>) -> Unit
) : ViewModel() {

    private val _selectedClientId = MutableStateFlow<String?>(null)
    val selectedClientId: StateFlow<String?> = _selectedClientId.asStateFlow()

    private val _selectedProjectByClient = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedProjectByClient: StateFlow<Map<String, String>> = _selectedProjectByClient.asStateFlow()

    private val _taskFilter = MutableStateFlow(TaskFilter.ALL)
    val taskFilter: StateFlow<TaskFilter> = _taskFilter.asStateFlow()

    // Clients are opened by default in "All Clients" tab; only explicitly collapsed IDs are tracked.
    private val _collapsedClientIds = MutableStateFlow(CollapsedClientsState())
    val collapsedClientIds: StateFlow<CollapsedClientsState> = _collapsedClientIds.asStateFlow()

    private val _pendingClientOrder = MutableStateFlow<List<String>?>(null)
    private var reorderJob: Job? = null
    private var widgetSyncJob: Job? = null
    private var migrationJob: Job? = null
    private var lastMigrationSignature: String = ""

    // Active portal listener jobs keyed by portalKey so client-submitted tasks auto-ingest live
    private val portalIngestionJobs = mutableMapOf<String, Job>()
    private val portalClientByKey = mutableMapOf<String, Client>()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val geminiAssistant = GeminiWorkspaceAssistant()
    private var aiAnalysisJob: Job? = null

    private val _aiState = MutableStateFlow<AiAssistantState>(AiAssistantState.Idle)
    val aiState: StateFlow<AiAssistantState> = _aiState.asStateFlow()

    private val voiceAssistant = GeminiVoiceAssistant()
    private var voiceJob: Job? = null
    private var lastVoiceAudioBytes: ByteArray? = null
    private var lastVoiceTextCommand: String? = null
    private var lastVoicePreferredClientId: String? = null

    private val _voiceState = MutableStateFlow<VoiceAssistantState>(VoiceAssistantState.Idle)
    val voiceState: StateFlow<VoiceAssistantState> = _voiceState.asStateFlow()

    private val _voiceAutoApply = MutableStateFlow(false)
    val voiceAutoApply: StateFlow<Boolean> = _voiceAutoApply.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val workspaceState: StateFlow<UiState<WorkspaceData>> = combine(
        repository.observeClients(currentUserId),
        repository.observeProjects(currentUserId),
        repository.observeTasks(currentUserId),
        _pendingClientOrder
    ) { clients, projects, tasks, pendingOrder ->
        val activeClients = clients.filter { !it.archived }
        val orderedClients = if (pendingOrder != null) {
            val rankById = pendingOrder.withIndex().associate { it.value to it.index }
            activeClients.sortedWith(
                compareBy<Client> { rankById[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.sortOrder }
            )
        } else {
            activeClients
        }

        val activeProjects = projects.filter { !it.archived }
        val projectsByClient = HashMap<String, MutableList<ClientProject>>(orderedClients.size.coerceAtLeast(4))
        val projectById = HashMap<String, ClientProject>(activeProjects.size.coerceAtLeast(4))
        val projectAccentById = HashMap<String, Color>(activeProjects.size.coerceAtLeast(4))
        for (project in activeProjects) {
            projectsByClient.getOrPut(project.clientId) { ArrayList() }.add(project)
            projectById[project.id] = project
            projectAccentById[project.id] = parseClientColor(project.colorHex)
        }

        val defaultProjectByClient = HashMap<String, ClientProject>(orderedClients.size)
        for (client in orderedClients) {
            val clientProjects = projectsByClient[client.id].orEmpty()
            val def = clientProjects.firstOrNull { it.defaultProject } ?: clientProjects.firstOrNull()
            if (def != null) {
                defaultProjectByClient[client.id] = def
            }
        }

        val initialCap = orderedClients.size.coerceAtLeast(4)
        val preparedByClient = HashMap<String, MutableList<PreparedTask>>(initialCap)
        val openPreparedByClient = HashMap<String, MutableList<PreparedTask>>(initialCap)
        val donePreparedByClient = HashMap<String, MutableList<PreparedTask>>(initialCap)
        val preparedByProject = HashMap<String, MutableList<PreparedTask>>(activeProjects.size.coerceAtLeast(4))
        val openPreparedByProject = HashMap<String, MutableList<PreparedTask>>(activeProjects.size.coerceAtLeast(4))
        val donePreparedByProject = HashMap<String, MutableList<PreparedTask>>(activeProjects.size.coerceAtLeast(4))
        val ongoingPreparedByClient = HashMap<String, MutableList<PreparedTask>>(initialCap)
        val ongoingPreparedByProject = HashMap<String, MutableList<PreparedTask>>(activeProjects.size.coerceAtLeast(4))
        val openCountByClient = HashMap<String, Int>(initialCap)
        val openCountByProject = HashMap<String, Int>(activeProjects.size.coerceAtLeast(4))
        var totalOpen = 0

        for (task in tasks) {
            val resolvedProject = projectById[task.projectId] ?: defaultProjectByClient[task.clientId]
            val resolvedProjectId = resolvedProject?.id ?: task.projectId
            val hasDetails = task.details.isNotBlank()
            val isHigh = !task.completed && task.priorityEnum() == TaskPriority.HIGH
            val hasDue = task.dueLabel.isNotBlank()
            val prepared = PreparedTask(
                task = task,
                projectName = resolvedProject?.name.orEmpty(),
                isTitleRtl = task.title.isRtlText(),
                isDetailsRtl = if (hasDetails) task.details.isRtlText() else false,
                isDueRtl = if (hasDue) task.dueLabel.isRtlText() else false,
                isHighPriority = isHigh,
                hasDetails = hasDetails,
                hasDue = hasDue,
                dueWithSeparator = if (hasDue) "·  ${task.dueLabel}" else ""
            )
            preparedByClient.getOrPut(task.clientId) { ArrayList() }.add(prepared)
            if (resolvedProjectId.isNotBlank()) {
                preparedByProject.getOrPut(resolvedProjectId) { ArrayList() }.add(prepared)
            }
            if (!task.completed) {
                openPreparedByClient.getOrPut(task.clientId) { ArrayList() }.add(prepared)
                openCountByClient[task.clientId] = (openCountByClient[task.clientId] ?: 0) + 1
                if (resolvedProjectId.isNotBlank()) {
                    openPreparedByProject.getOrPut(resolvedProjectId) { ArrayList() }.add(prepared)
                    openCountByProject[resolvedProjectId] = (openCountByProject[resolvedProjectId] ?: 0) + 1
                }
                if (task.ongoing) {
                    ongoingPreparedByClient.getOrPut(task.clientId) { ArrayList() }.add(prepared)
                    if (resolvedProjectId.isNotBlank()) {
                        ongoingPreparedByProject.getOrPut(resolvedProjectId) { ArrayList() }.add(prepared)
                    }
                }
                totalOpen++
            } else {
                donePreparedByClient.getOrPut(task.clientId) { ArrayList() }.add(prepared)
                if (resolvedProjectId.isNotBlank()) {
                    donePreparedByProject.getOrPut(resolvedProjectId) { ArrayList() }.add(prepared)
                }
            }
        }

        val clientNameRtlById = HashMap<String, Boolean>(orderedClients.size)
        val clientAccentById = HashMap<String, Color>(orderedClients.size)
        for (client in orderedClients) {
            clientNameRtlById[client.id] = client.name.isRtlText()
            clientAccentById[client.id] = parseClientColor(client.colorHex)
        }

        WorkspaceData(
            clients = orderedClients,
            projects = activeProjects,
            tasks = tasks,
            projectsByClient = projectsByClient,
            projectById = projectById,
            preparedByClient = preparedByClient,
            openPreparedByClient = openPreparedByClient,
            donePreparedByClient = donePreparedByClient,
            ongoingPreparedByClient = ongoingPreparedByClient,
            preparedByProject = preparedByProject,
            openPreparedByProject = openPreparedByProject,
            donePreparedByProject = donePreparedByProject,
            ongoingPreparedByProject = ongoingPreparedByProject,
            openCountByClient = openCountByClient,
            openCountByProject = openCountByProject,
            totalOpen = totalOpen,
            clientNameRtlById = clientNameRtlById,
            clientAccentById = clientAccentById,
            projectAccentById = projectAccentById
        )
    }
        .flowOn(Dispatchers.Default)
        .onEach { data ->
            widgetSyncJob?.cancel()
            widgetSyncJob = viewModelScope.launch(Dispatchers.IO) {
                delay(WIDGET_SYNC_DEBOUNCE_MS)
                onSyncWidgets(currentUserId, data.clients, data.tasks)
            }

            // Ensure selectedClientId stays valid
            val currentSelection = _selectedClientId.value
            if (currentSelection != null && data.clients.none { it.id == currentSelection }) {
                _selectedClientId.value = data.clients.firstOrNull()?.id
            }

            // Ensure selectedProjectByClient points to existing projects
            _selectedProjectByClient.update { currentMap ->
                var changed = false
                val mutable = currentMap.toMutableMap()
                for (client in data.clients) {
                    val clientProjects = data.projectsByClient[client.id].orEmpty()
                    val currentProjId = mutable[client.id]
                    if (clientProjects.isNotEmpty() && (currentProjId == null || clientProjects.none { it.id == currentProjId })) {
                        mutable[client.id] = clientProjects.first().id
                        changed = true
                    }
                }
                if (changed) mutable else currentMap
            }

            // Automatic idempotent migration for legacy clients & tasks
            maybeTriggerLegacyMigration(data)

            // Sync live Client Portal task ingestion listeners
            reconcilePortalListeners(data)
        }
        .map<WorkspaceData, UiState<WorkspaceData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing workspace", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load your workspaces."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private fun maybeTriggerLegacyMigration(data: WorkspaceData) {
        val clientsNeedingDefault = data.clients.filter { data.projectsByClient[it.id].isNullOrEmpty() }
        val tasksNeedingProject = data.tasks.filter { task ->
            val clientProjects = data.projectsByClient[task.clientId].orEmpty()
            task.projectId.isBlank() || (clientProjects.isNotEmpty() && clientProjects.none { it.id == task.projectId })
        }
        if (clientsNeedingDefault.isEmpty() && tasksNeedingProject.isEmpty()) return

        val signature = "${clientsNeedingDefault.map { it.id }.sorted()}|${tasksNeedingProject.map { it.id }.sorted()}"
        if (signature == lastMigrationSignature && migrationJob?.isActive == true) return
        lastMigrationSignature = signature

        migrationJob?.cancel()
        migrationJob = viewModelScope.launch {
            delay(250L)
            repository.migrateClientsAndTasksToDefaultProjects(
                userId = currentUserId,
                clients = data.clients,
                projects = data.projects,
                tasks = data.tasks
            ).onFailure { err ->
                Log.w(TAG, "Legacy project migration deferred: ${err.message}")
            }
        }
    }

    private fun reconcilePortalListeners(data: WorkspaceData) {
        val activePortalClients = data.clients.filter {
            it.portalEnabled && PortalCrypto.isValidPortalKey(it.portalKey)
        }
        val activeKeys = activePortalClients.mapTo(HashSet()) { it.portalKey }

        // Stop listeners for removed/disabled portals
        val staleKeys = portalIngestionJobs.keys.filter { it !in activeKeys }
        staleKeys.forEach { key ->
            portalIngestionJobs.remove(key)?.cancel()
            portalClientByKey.remove(key)
        }

        // Start listeners for newly active portals
        activePortalClients.forEach { client ->
            portalClientByKey[client.portalKey] = client
            if (client.portalKey !in portalIngestionJobs) {
                val portalKey = client.portalKey
                portalIngestionJobs[portalKey] = viewModelScope.launch {
                    repository.observePortalTasks(portalKey)
                        .catch { err ->
                            Log.w(TAG, "Portal listener paused for ${client.name}: ${err.message}")
                        }
                        .collect { portalTasks ->
                            val latestWorkspace = (workspaceState.value as? UiState.Success)?.data ?: return@collect
                            val latestClient = portalClientByKey[portalKey] ?: client
                            val existingIds = latestWorkspace.tasks.mapTo(HashSet()) { it.id }
                            val fallbackProjId = latestWorkspace.defaultProjectFor(latestClient.id)?.id
                                ?: ClientDeskRepository.defaultProjectIdFor(latestClient.id)

                            val hasNewClientTasks = portalTasks.any {
                                it.createdByClient && it.clientId == latestClient.id && it.id !in existingIds
                            }
                            if (hasNewClientTasks) {
                                repository.ingestClientSubmittedTasks(
                                    userId = currentUserId,
                                    client = latestClient,
                                    portalTasks = portalTasks,
                                    existingOwnerTaskIds = existingIds,
                                    fallbackProjectId = fallbackProjId
                                ).onSuccess { ingestedCount ->
                                    if (ingestedCount > 0) {
                                        val label = if (ingestedCount == 1) "1 new task" else "$ingestedCount new tasks"
                                        _messages.tryEmit("$label submitted by ${latestClient.name}")
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        portalIngestionJobs.values.forEach { it.cancel() }
        portalIngestionJobs.clear()
    }

    fun selectClient(clientId: String?) {
        _selectedClientId.value = clientId
    }

    fun selectProject(clientId: String, projectId: String) {
        if (clientId.isBlank() || projectId.isBlank()) return
        _selectedProjectByClient.update { current ->
            if (current[clientId] == projectId) current else current + (clientId to projectId)
        }
    }

    fun activeProjectIdFor(clientId: String): String {
        val data = (workspaceState.value as? UiState.Success)?.data
        val selected = _selectedProjectByClient.value[clientId]
        val clientProjects = data?.projectsByClient?.get(clientId).orEmpty()
        if (!selected.isNullOrBlank() && clientProjects.any { it.id == selected }) {
            return selected
        }
        return data?.defaultProjectFor(clientId)?.id
            ?: ClientDeskRepository.defaultProjectIdFor(clientId)
    }

    fun setTaskFilter(filter: TaskFilter) {
        _taskFilter.value = filter
    }

    fun toggleClientCollapsed(clientId: String) {
        _collapsedClientIds.update { current ->
            val next = if (clientId in current.ids) current.ids - clientId else current.ids + clientId
            CollapsedClientsState(next)
        }
    }

    fun setAllClientsCollapsed(collapsed: Boolean) {
        val currentClients = (workspaceState.value as? UiState.Success)?.data?.clients.orEmpty()
        _collapsedClientIds.value = if (collapsed) {
            CollapsedClientsState(currentClients.mapTo(HashSet(currentClients.size)) { it.id })
        } else {
            CollapsedClientsState()
        }
    }

    fun reorderClients(reorderedClients: List<Client>) {
        if (reorderedClients.size < 2) return
        val orderedIds = reorderedClients.map { it.id }
        _pendingClientOrder.value = orderedIds

        val updates = reorderedClients.mapIndexedNotNull { index, client ->
            val targetOrder = index.toLong()
            if (client.sortOrder != targetOrder) client.id to targetOrder else null
        }
        if (updates.isEmpty()) {
            _pendingClientOrder.value = null
            return
        }

        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            val result = repository.reorderClients(currentUserId, updates)
            _pendingClientOrder.value = null
            result.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't save client order")
            }
        }
    }

    // --- CLIENT CRUD ---

    fun registerClient(
        name: String,
        company: String,
        notes: String,
        colorHex: String,
        portalEnabled: Boolean = false,
        portalEmail: String = "",
        portalPassword: String = "",
        onSuccess: (String) -> Unit = {}
    ) {
        val currentClients = (workspaceState.value as? UiState.Success)?.data?.clients.orEmpty()
        if (portalEnabled) {
            val normalized = PortalCrypto.normalizeEmail(portalEmail)
            if (currentClients.any { it.portalEnabled && it.portalEmail.equals(normalized, ignoreCase = true) }) {
                _messages.tryEmit("That portal email is already assigned to another client.")
                return
            }
        }
        val nextSortOrder = (currentClients.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createClient(
                userId = currentUserId,
                name = name,
                company = company,
                notes = notes,
                colorHex = colorHex,
                sortOrder = nextSortOrder,
                portalEnabled = portalEnabled,
                portalEmail = portalEmail,
                portalPassword = portalPassword
            )
            _isBusy.value = false
            result.onSuccess { newClientId ->
                _selectedClientId.value = newClientId
                _messages.tryEmit("Registered ${name.trim()}")
                onSuccess(newClientId)
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't register client")
            }
        }
    }

    fun updateClient(
        existingClient: Client,
        name: String,
        company: String,
        notes: String,
        colorHex: String,
        portalEnabled: Boolean,
        portalEmail: String,
        newPortalPassword: String,
        onSuccess: () -> Unit = {}
    ) {
        val workspace = (workspaceState.value as? UiState.Success)?.data
        val currentClients = workspace?.clients.orEmpty()
        if (portalEnabled) {
            val normalized = PortalCrypto.normalizeEmail(portalEmail)
            if (currentClients.any {
                    it.id != existingClient.id &&
                        it.portalEnabled &&
                        it.portalEmail.equals(normalized, ignoreCase = true)
                }
            ) {
                _messages.tryEmit("That portal email is already assigned to another client.")
                return
            }
        }
        val clientProjects = workspace?.projectsByClient?.get(existingClient.id).orEmpty()
        val clientTasks = workspace?.tasks?.filter { it.clientId == existingClient.id }.orEmpty()

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateClient(
                userId = currentUserId,
                existingClient = existingClient,
                name = name,
                company = company,
                notes = notes,
                colorHex = colorHex,
                portalEnabled = portalEnabled,
                portalEmail = portalEmail,
                newPortalPassword = newPortalPassword,
                clientProjects = clientProjects,
                clientTasks = clientTasks
            )
            _isBusy.value = false
            result.onSuccess {
                _messages.tryEmit("Updated ${name.trim()}")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't update client")
            }
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteClientAndTasks(currentUserId, client)
            _isBusy.value = false
            result.onSuccess {
                if (_selectedClientId.value == client.id) {
                    _selectedClientId.value = null
                }
                _messages.tryEmit("Removed ${client.name}")
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't remove client")
            }
        }
    }

    // --- PROJECT CRUD ---

    fun createProject(
        client: Client,
        name: String,
        description: String,
        colorHex: String,
        onSuccess: (String) -> Unit = {}
    ) {
        val existingProjects = (workspaceState.value as? UiState.Success)?.data
            ?.projectsByClient?.get(client.id).orEmpty()
        val nextSortOrder = (existingProjects.maxOfOrNull { it.sortOrder } ?: 0L) + 1L
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createProject(
                userId = currentUserId,
                client = client,
                name = name,
                description = description,
                colorHex = colorHex,
                sortOrder = nextSortOrder
            )
            _isBusy.value = false
            result.onSuccess { newProjectId ->
                _selectedClientId.value = client.id
                selectProject(client.id, newProjectId)
                _messages.tryEmit("Created project ${name.trim()}")
                onSuccess(newProjectId)
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't create project")
            }
        }
    }

    fun updateProject(
        project: ClientProject,
        name: String,
        description: String,
        colorHex: String,
        onSuccess: () -> Unit = {}
    ) {
        val client = (workspaceState.value as? UiState.Success)?.data
            ?.clients?.firstOrNull { it.id == project.clientId }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProject(
                userId = currentUserId,
                client = client,
                projectId = project.id,
                name = name,
                description = description,
                colorHex = colorHex
            )
            _isBusy.value = false
            result.onSuccess {
                _messages.tryEmit("Updated project ${name.trim()}")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't update project")
            }
        }
    }

    fun deleteProject(
        project: ClientProject,
        onSuccess: () -> Unit = {}
    ) {
        val workspace = (workspaceState.value as? UiState.Success)?.data
        val clientProjects = workspace?.projectsByClient?.get(project.clientId).orEmpty()
        if (clientProjects.size <= 1) {
            _messages.tryEmit("Every client needs at least one project.")
            return
        }
        val client = workspace?.clients?.firstOrNull { it.id == project.clientId }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteProjectAndTasks(
                userId = currentUserId,
                client = client,
                projectId = project.id
            )
            _isBusy.value = false
            result.onSuccess {
                val fallback = clientProjects.firstOrNull { it.id != project.id }
                if (fallback != null) {
                    selectProject(project.clientId, fallback.id)
                }
                _messages.tryEmit("Deleted project ${project.name}")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't delete project")
            }
        }
    }

    // --- TASK CRUD ---

    private fun activePortalKeyFor(clientId: String): String {
        val client = (workspaceState.value as? UiState.Success)?.data
            ?.clients?.firstOrNull { it.id == clientId }
        return if (client != null && client.portalEnabled && PortalCrypto.isValidPortalKey(client.portalKey)) {
            client.portalKey
        } else {
            ""
        }
    }

    fun createTask(
        clientId: String,
        projectId: String? = null,
        title: String,
        details: String = "",
        priority: TaskPriority = TaskPriority.NORMAL,
        dueLabel: String = "",
        onSuccess: () -> Unit = {}
    ) {
        if (title.isBlank()) return
        val resolvedProjectId = projectId?.takeIf { it.isNotBlank() } ?: activeProjectIdFor(clientId)
        val portalKey = activePortalKeyFor(clientId)
        viewModelScope.launch {
            val result = repository.createTask(
                userId = currentUserId,
                clientId = clientId,
                projectId = resolvedProjectId,
                title = title,
                details = details,
                priority = priority,
                dueLabel = dueLabel,
                portalKey = portalKey
            )
            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't add task")
            }
        }
    }

    /** Checkbox tap: Open -> Ongoing -> Done -> Open. */
    fun cycleTaskStatus(task: ClientTask) {
        setTaskStatus(task, task.statusEnum().next())
    }

    fun setTaskStatus(task: ClientTask, status: TaskStatus) {
        if (task.statusEnum() == status) return
        val portalKey = activePortalKeyFor(task.clientId)
        viewModelScope.launch {
            val result = repository.setTaskStatus(
                userId = currentUserId,
                taskId = task.id,
                status = status,
                wasOngoing = task.ongoing,
                portalKey = portalKey
            )
            result.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't update task")
            }
        }
    }

    fun updateTask(
        task: ClientTask,
        projectId: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String,
        status: TaskStatus,
        onSuccess: () -> Unit = {}
    ) {
        val portalKey = activePortalKeyFor(task.clientId)
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateTask(
                userId = currentUserId,
                taskId = task.id,
                projectId = projectId.ifBlank { task.projectId },
                title = title,
                details = details,
                priority = priority,
                dueLabel = dueLabel,
                completed = status == TaskStatus.DONE,
                // Only touch `ongoing` when it's involved, so ordinary edits write the same fields as before.
                ongoing = if (status == TaskStatus.ONGOING || task.ongoing) status == TaskStatus.ONGOING else null,
                portalKey = portalKey
            )
            _isBusy.value = false
            result.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't save task")
            }
        }
    }

    fun deleteTask(task: ClientTask) {
        val portalKey = activePortalKeyFor(task.clientId)
        viewModelScope.launch {
            val result = repository.deleteTask(currentUserId, task.id, portalKey = portalKey)
            result.onSuccess {
                _messages.tryEmit("Deleted task")
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't delete task")
            }
        }
    }

    // --- GEMINI AI ASSISTANT ---

    fun analyzeWithGemini(
        prompt: String,
        image: Bitmap?,
        preferredClientId: String?
    ) {
        if (prompt.isBlank() && image == null) return
        val existingClients = (workspaceState.value as? UiState.Success)?.data?.clients.orEmpty()
        val preferredClient = existingClients.firstOrNull { it.id == preferredClientId }

        aiAnalysisJob?.cancel()
        aiAnalysisJob = viewModelScope.launch {
            _aiState.value = AiAssistantState.Loading(
                statusMessage = if (image != null) "Scanning image with Gemini…" else "Connecting to Gemini…"
            )
            try {
                val plan = geminiAssistant.analyzeInput(
                    userPrompt = prompt,
                    image = image,
                    existingClients = existingClients,
                    preferredClient = preferredClient,
                    onProgress = { status ->
                        if (_aiState.value is AiAssistantState.Loading) {
                            _aiState.value = AiAssistantState.Loading(statusMessage = status)
                        }
                    }
                )
                if (plan.clients.isEmpty() && plan.tasks.isEmpty()) {
                    _aiState.value = AiAssistantState.Error(
                        "No clients or tasks were detected. Try adding more detail or a clearer photo."
                    )
                } else {
                    _aiState.value = AiAssistantState.Review(plan)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini analysis timed out", e)
                _aiState.value = AiAssistantState.Error(
                    "Analysis timed out. Please check your connection and try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini analysis failed", e)
                _aiState.value = AiAssistantState.Error(
                    e.localizedMessage ?: "Gemini couldn't analyze this request."
                )
            }
        }
    }

    fun cancelAiAnalysis() {
        aiAnalysisJob?.cancel()
        aiAnalysisJob = null
        _aiState.value = AiAssistantState.Idle
    }

    fun resetAiState() {
        aiAnalysisJob?.cancel()
        aiAnalysisJob = null
        _aiState.value = AiAssistantState.Idle
    }

    fun applyAiPlan(
        clientsToCreate: List<ProposedClient>,
        tasksToCreate: List<ProposedTask>,
        onSuccess: () -> Unit = {}
    ) {
        val checkedClients = clientsToCreate.filter { it.selected && it.name.isNotBlank() }
        val checkedTasks = tasksToCreate.filter { it.selected && it.title.isNotBlank() }
        if (checkedClients.isEmpty() && checkedTasks.isEmpty()) return

        val existingWorkspace = (workspaceState.value as? UiState.Success)?.data
        val existingClients = existingWorkspace?.clients.orEmpty()

        viewModelScope.launch {
            _isBusy.value = true
            try {
                val clientIdByLowerName = existingClients
                    .associate { it.name.trim().lowercase() to it.id }
                    .toMutableMap()

                var nextSortOrder = (existingClients.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
                var createdClientsCount = 0
                var lastTouchedClientId: String? = null

                for (client in checkedClients) {
                    val key = client.name.trim().lowercase()
                    if (key in clientIdByLowerName) {
                        lastTouchedClientId = clientIdByLowerName[key]
                        continue
                    }
                    val result = repository.createClient(
                        userId = currentUserId,
                        name = client.name,
                        company = client.company,
                        notes = client.notes,
                        colorHex = client.colorHex,
                        sortOrder = nextSortOrder++
                    )
                    result.onSuccess { newId ->
                        clientIdByLowerName[key] = newId
                        lastTouchedClientId = newId
                        createdClientsCount++
                    }.onFailure { err ->
                        Log.e(TAG, "Failed creating AI client ${client.name}", err)
                    }
                }

                var createdTasksCount = 0
                for (task in checkedTasks) {
                    val key = task.clientName.trim().lowercase()
                    var targetClientId = clientIdByLowerName[key]

                    if (targetClientId == null) {
                        val fallbackId = lastTouchedClientId ?: existingClients.firstOrNull()?.id
                        if (fallbackId != null) {
                            targetClientId = fallbackId
                        } else {
                            val autoName = task.clientName.trim().ifBlank { "General" }
                            val created = repository.createClient(
                                userId = currentUserId,
                                name = autoName,
                                company = "",
                                notes = "",
                                colorHex = "#2555E6",
                                sortOrder = nextSortOrder++
                            ).getOrNull()
                            if (created != null) {
                                clientIdByLowerName[autoName.lowercase()] = created
                                targetClientId = created
                                createdClientsCount++
                            }
                        }
                    }

                    if (targetClientId != null) {
                        val targetProjectId = activeProjectIdFor(targetClientId)
                        val taskResult = repository.createTask(
                            userId = currentUserId,
                            clientId = targetClientId,
                            projectId = targetProjectId,
                            title = task.title,
                            details = task.details,
                            priority = task.priority,
                            dueLabel = task.dueLabel,
                            portalKey = activePortalKeyFor(targetClientId)
                        )
                        taskResult.onSuccess {
                            createdTasksCount++
                            lastTouchedClientId = targetClientId
                        }.onFailure { err ->
                            Log.e(TAG, "Failed creating AI task ${task.title}", err)
                        }
                    }
                }

                if (lastTouchedClientId != null) {
                    _selectedClientId.value = lastTouchedClientId
                }

                val summaryParts = buildList {
                    if (createdClientsCount > 0) {
                        add(if (createdClientsCount == 1) "1 client" else "$createdClientsCount clients")
                    }
                    if (createdTasksCount > 0) {
                        add(if (createdTasksCount == 1) "1 task" else "$createdTasksCount tasks")
                    }
                }
                if (summaryParts.isNotEmpty()) {
                    _messages.tryEmit("Added ${summaryParts.joinToString(" & ")} via Gemini")
                }
                _aiState.value = AiAssistantState.Idle
                onSuccess()
            } finally {
                _isBusy.value = false
            }
        }
    }

    // --- VOICE ASSISTANT ---

    fun setVoiceAutoApply(enabled: Boolean) {
        _voiceAutoApply.value = enabled
    }

    fun processVoiceCommand(
        audioWavBytes: ByteArray? = null,
        typedCommand: String? = null,
        preferredClientId: String? = null
    ) {
        if (audioWavBytes == null && typedCommand.isNullOrBlank()) return
        lastVoiceAudioBytes = audioWavBytes
        lastVoiceTextCommand = typedCommand
        lastVoicePreferredClientId = preferredClientId

        val workspace = (workspaceState.value as? UiState.Success)?.data ?: WorkspaceData()
        val preferredClient = workspace.clients.firstOrNull { it.id == preferredClientId }

        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            _voiceState.value = VoiceAssistantState.Processing(
                statusMessage = if (audioWavBytes != null) {
                    "Listening & understanding with Gemini…"
                } else {
                    "Understanding your command…"
                }
            )
            try {
                val plan = voiceAssistant.processVoiceCommand(
                    audioWavBytes = audioWavBytes,
                    typedCommand = typedCommand,
                    existingClients = workspace.clients,
                    existingTasks = workspace.tasks,
                    preferredClient = preferredClient,
                    onProgress = { status ->
                        if (_voiceState.value is VoiceAssistantState.Processing) {
                            _voiceState.value = VoiceAssistantState.Processing(status)
                        }
                    }
                )

                clearLastVoiceRequest()

                val shouldAutoApply = _voiceAutoApply.value &&
                    plan.hasMutations &&
                    !plan.hasDestructiveActions

                when {
                    !plan.hasMutations -> {
                        applyVoiceNavigationDirectives(
                            existingClients = workspace.clients,
                            focusClientName = plan.focusClientName,
                            filterMode = plan.filterMode
                        )
                        _voiceState.value = VoiceAssistantState.Executed(
                            transcript = plan.transcript,
                            spokenReply = plan.spokenReply,
                            executedSummary = ""
                        )
                    }

                    shouldAutoApply -> {
                        val summary = executeVoicePlanInternal(
                            clientsToCreate = plan.clients,
                            tasksToCreate = plan.tasks,
                            taskActions = plan.taskActions,
                            focusClientName = plan.focusClientName,
                            filterMode = plan.filterMode
                        )
                        _voiceState.value = VoiceAssistantState.Executed(
                            transcript = plan.transcript,
                            spokenReply = plan.spokenReply,
                            executedSummary = summary
                        )
                    }

                    else -> {
                        _voiceState.value = VoiceAssistantState.Review(plan)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Voice assistant request timed out", e)
                _voiceState.value = VoiceAssistantState.Error(
                    message = "Voice request timed out. Tap Retry to send your recording again.",
                    canRetry = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice assistant request failed", e)
                _voiceState.value = VoiceAssistantState.Error(
                    message = e.localizedMessage ?: "Couldn't process that voice command.",
                    canRetry = lastVoiceAudioBytes != null || !lastVoiceTextCommand.isNullOrBlank()
                )
            }
        }
    }

    fun retryLastVoiceCommand() {
        val audio = lastVoiceAudioBytes
        val text = lastVoiceTextCommand
        if (audio == null && text.isNullOrBlank()) return
        processVoiceCommand(
            audioWavBytes = audio,
            typedCommand = text,
            preferredClientId = lastVoicePreferredClientId
        )
    }

    fun cancelVoiceProcessing() {
        voiceJob?.cancel()
        voiceJob = null
        _voiceState.value = VoiceAssistantState.Idle
    }

    fun resetVoiceState() {
        voiceJob?.cancel()
        voiceJob = null
        clearLastVoiceRequest()
        _voiceState.value = VoiceAssistantState.Idle
    }

    private fun clearLastVoiceRequest() {
        lastVoiceAudioBytes = null
        lastVoiceTextCommand = null
        lastVoicePreferredClientId = null
    }

    fun applyVoicePlan(
        plan: VoiceAssistantPlan,
        clientsToCreate: List<ProposedClient>,
        tasksToCreate: List<ProposedTask>,
        taskActions: List<ProposedTaskAction>,
        onCompleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val summary = executeVoicePlanInternal(
                clientsToCreate = clientsToCreate,
                tasksToCreate = tasksToCreate,
                taskActions = taskActions,
                focusClientName = plan.focusClientName,
                filterMode = plan.filterMode
            )
            _voiceState.value = VoiceAssistantState.Executed(
                transcript = plan.transcript,
                spokenReply = plan.spokenReply,
                executedSummary = summary
            )
            onCompleted()
        }
    }

    private fun applyVoiceNavigationDirectives(
        existingClients: List<Client>,
        focusClientName: String?,
        filterMode: TaskFilter?
    ) {
        if (!focusClientName.isNullOrBlank()) {
            val matched = existingClients.firstOrNull {
                it.name.equals(focusClientName.trim(), ignoreCase = true)
            }
            if (matched != null) {
                _selectedClientId.value = matched.id
            }
        }
        if (filterMode != null) {
            _taskFilter.value = filterMode
        }
    }

    private suspend fun executeVoicePlanInternal(
        clientsToCreate: List<ProposedClient>,
        tasksToCreate: List<ProposedTask>,
        taskActions: List<ProposedTaskAction>,
        focusClientName: String?,
        filterMode: TaskFilter?
    ): String {
        val checkedClients = clientsToCreate.filter { it.selected && it.name.isNotBlank() }
        val checkedTasks = tasksToCreate.filter { it.selected && it.title.isNotBlank() }
        val checkedActions = taskActions.filter { it.selected }

        val existingClients = (workspaceState.value as? UiState.Success)?.data?.clients.orEmpty()

        _isBusy.value = true
        return try {
            val clientIdByLowerName = existingClients
                .associate { it.name.trim().lowercase() to it.id }
                .toMutableMap()

            var nextSortOrder = (existingClients.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
            var createdClientsCount = 0
            var createdTasksCount = 0
            var completedCount = 0
            var reopenedCount = 0
            var updatedCount = 0
            var deletedCount = 0
            var lastTouchedClientId: String? = null

            for (client in checkedClients) {
                val key = client.name.trim().lowercase()
                if (key in clientIdByLowerName) {
                    lastTouchedClientId = clientIdByLowerName[key]
                    continue
                }
                val result = repository.createClient(
                    userId = currentUserId,
                    name = client.name,
                    company = client.company,
                    notes = client.notes,
                    colorHex = client.colorHex,
                    sortOrder = nextSortOrder++
                )
                result.onSuccess { newId ->
                    clientIdByLowerName[key] = newId
                    lastTouchedClientId = newId
                    createdClientsCount++
                }.onFailure { err ->
                    Log.e(TAG, "Failed creating voice client ${client.name}", err)
                }
            }

            for (task in checkedTasks) {
                val key = task.clientName.trim().lowercase()
                var targetClientId = clientIdByLowerName[key]

                if (targetClientId == null) {
                    val fallbackId = lastTouchedClientId ?: existingClients.firstOrNull()?.id
                    if (fallbackId != null) {
                        targetClientId = fallbackId
                    } else {
                        val autoName = task.clientName.trim().ifBlank { "General" }
                        val created = repository.createClient(
                            userId = currentUserId,
                            name = autoName,
                            company = "",
                            notes = "",
                            colorHex = "#2555E6",
                            sortOrder = nextSortOrder++
                        ).getOrNull()
                        if (created != null) {
                            clientIdByLowerName[autoName.lowercase()] = created
                            targetClientId = created
                            createdClientsCount++
                        }
                    }
                }

                if (targetClientId != null) {
                    val taskResult = repository.createTask(
                        userId = currentUserId,
                        clientId = targetClientId,
                        projectId = activeProjectIdFor(targetClientId),
                        title = task.title,
                        details = task.details,
                        priority = task.priority,
                        dueLabel = task.dueLabel,
                        portalKey = activePortalKeyFor(targetClientId)
                    )
                    taskResult.onSuccess {
                        createdTasksCount++
                        lastTouchedClientId = targetClientId
                    }.onFailure { err ->
                        Log.e(TAG, "Failed creating voice task ${task.title}", err)
                    }
                }
            }

            for (action in checkedActions) {
                val existing = action.existingTask
                val portalKey = activePortalKeyFor(existing.clientId)
                when (action.actionType) {
                    VoiceTaskActionType.COMPLETE -> {
                        repository.toggleTaskCompleted(
                            userId = currentUserId,
                            taskId = existing.id,
                            completed = true,
                            portalKey = portalKey
                        ).onSuccess {
                            completedCount++
                            lastTouchedClientId = existing.clientId
                        }
                    }

                    VoiceTaskActionType.REOPEN -> {
                        repository.toggleTaskCompleted(
                            userId = currentUserId,
                            taskId = existing.id,
                            completed = false,
                            portalKey = portalKey
                        ).onSuccess {
                            reopenedCount++
                            lastTouchedClientId = existing.clientId
                        }
                    }

                    VoiceTaskActionType.UPDATE -> {
                        repository.updateTask(
                            userId = currentUserId,
                            taskId = existing.id,
                            projectId = existing.projectId,
                            title = action.newTitle ?: existing.title,
                            details = action.newDetails ?: existing.details,
                            priority = action.newPriority ?: existing.priorityEnum(),
                            dueLabel = action.newDueLabel ?: existing.dueLabel,
                            completed = existing.completed,
                            portalKey = portalKey
                        ).onSuccess {
                            updatedCount++
                            lastTouchedClientId = existing.clientId
                        }
                    }

                    VoiceTaskActionType.DELETE -> {
                        repository.deleteTask(
                            userId = currentUserId,
                            taskId = existing.id,
                            portalKey = portalKey
                        ).onSuccess {
                            deletedCount++
                        }
                    }
                }
            }

            val focusId = focusClientName?.trim()?.lowercase()?.let { clientIdByLowerName[it] }
            if (focusId != null) {
                _selectedClientId.value = focusId
            } else if (lastTouchedClientId != null) {
                _selectedClientId.value = lastTouchedClientId
            }

            if (filterMode != null) {
                _taskFilter.value = filterMode
            }

            val summaryParts = buildList {
                if (createdClientsCount > 0) {
                    add(if (createdClientsCount == 1) "1 client added" else "$createdClientsCount clients added")
                }
                if (createdTasksCount > 0) {
                    add(if (createdTasksCount == 1) "1 task added" else "$createdTasksCount tasks added")
                }
                if (completedCount > 0) {
                    add(if (completedCount == 1) "1 task completed" else "$completedCount tasks completed")
                }
                if (reopenedCount > 0) {
                    add(if (reopenedCount == 1) "1 task reopened" else "$reopenedCount tasks reopened")
                }
                if (updatedCount > 0) {
                    add(if (updatedCount == 1) "1 task updated" else "$updatedCount tasks updated")
                }
                if (deletedCount > 0) {
                    add(if (deletedCount == 1) "1 task deleted" else "$deletedCount tasks deleted")
                }
            }

            val summaryText = summaryParts.joinToString(" · ")
            if (summaryText.isNotEmpty()) {
                _messages.tryEmit("Voice: $summaryText")
            }
            summaryText
        } finally {
            _isBusy.value = false
        }
    }

    private companion object {
        const val TAG = "ClientDeskVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val WIDGET_SYNC_DEBOUNCE_MS = 350L
    }
}
