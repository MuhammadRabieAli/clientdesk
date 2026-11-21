package com.example.ui

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ClientDeskRepository
import com.example.data.ClientPortalProfile
import com.example.data.ClientPortalSession
import com.example.data.PortalProject
import com.example.data.PortalTask
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.ui.theme.parseClientColor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ClientPortalData(
    val profile: ClientPortalProfile,
    val accent: Color,
    val projects: List<PortalProject>,
    val tasks: List<PortalTask>,
    val tasksByProject: Map<String, List<PortalTask>>,
    val openCountByProject: Map<String, Int>,
    val doneCountByProject: Map<String, Int>,
    val totalTasks: Int,
    val completedTasks: Int,
    val openTasks: Int,
    val overallProgress: Float,
    val isRevoked: Boolean = false
)

class ClientPortalViewModel(
    private val repository: ClientDeskRepository,
    private val session: ClientPortalSession
) : ViewModel() {

    private val _taskFilter = MutableStateFlow(TaskFilter.ALL)
    val taskFilter: StateFlow<TaskFilter> = _taskFilter.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val portalState: StateFlow<UiState<ClientPortalData>> = combine(
        repository.observePortalProfile(session.portalKey),
        repository.observePortalProjects(session.portalKey),
        repository.observePortalTasks(session.portalKey)
    ) { profile, projects, tasks ->
        val fallbackProfile = ClientPortalProfile(
            portalKey = session.portalKey,
            ownerId = session.ownerId,
            clientId = session.clientId,
            clientName = session.clientName,
            clientCompany = session.clientCompany,
            clientColorHex = session.clientColorHex,
            portalEmail = session.portalEmail,
            active = false
        )
        val effectiveProfile = profile ?: fallbackProfile
        val revoked = profile == null || !profile.active

        val tasksByProject = HashMap<String, MutableList<PortalTask>>(projects.size.coerceAtLeast(2))
        val openCountByProject = HashMap<String, Int>(projects.size.coerceAtLeast(2))
        val doneCountByProject = HashMap<String, Int>(projects.size.coerceAtLeast(2))
        val defaultProjId = projects.firstOrNull { it.defaultProject }?.id ?: projects.firstOrNull()?.id.orEmpty()

        var completedCount = 0
        for (task in tasks) {
            val targetProjId = if (projects.any { it.id == task.projectId }) task.projectId else defaultProjId
            if (targetProjId.isNotBlank()) {
                tasksByProject.getOrPut(targetProjId) { ArrayList() }.add(task)
                if (task.completed) {
                    doneCountByProject[targetProjId] = (doneCountByProject[targetProjId] ?: 0) + 1
                } else {
                    openCountByProject[targetProjId] = (openCountByProject[targetProjId] ?: 0) + 1
                }
            }
            if (task.completed) completedCount++
        }

        val total = tasks.size
        val open = total - completedCount
        val progress = if (total == 0) 0f else completedCount.toFloat() / total.toFloat()

        ClientPortalData(
            profile = effectiveProfile,
            accent = parseClientColor(effectiveProfile.clientColorHex),
            projects = projects,
            tasks = tasks,
            tasksByProject = tasksByProject,
            openCountByProject = openCountByProject,
            doneCountByProject = doneCountByProject,
            totalTasks = total,
            completedTasks = completedCount,
            openTasks = open,
            overallProgress = progress,
            isRevoked = revoked
        )
    }
        .flowOn(Dispatchers.Default)
        .map<ClientPortalData, UiState<ClientPortalData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing client portal", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Couldn't load your Client Portal."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun setTaskFilter(filter: TaskFilter) {
        _taskFilter.value = filter
    }

    fun submitTask(
        projectId: String,
        projectName: String,
        title: String,
        details: String,
        priority: TaskPriority,
        dueLabel: String,
        onSuccess: () -> Unit = {}
    ) {
        if (title.isBlank() || projectId.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitPortalTask(
                session = session,
                projectId = projectId,
                title = title,
                details = details,
                priority = priority,
                dueLabel = dueLabel
            )
            _isBusy.value = false
            result.onSuccess {
                _messages.tryEmit("Task submitted to $projectName")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Couldn't submit task")
            }
        }
    }

    private companion object {
        const val TAG = "ClientPortalVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
