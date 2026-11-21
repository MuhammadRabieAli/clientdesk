package com.example.data

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskPriority(val raw: String, val label: String) {
    LOW("LOW", "Low"),
    NORMAL("NORMAL", "Normal"),
    HIGH("HIGH", "High");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

@Immutable
@Keep
@IgnoreExtraProperties
data class Client(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val company: String = "",
    val notes: String = "",
    val colorHex: String = "#2555E6",
    val sortOrder: Long = 0L,
    val archived: Boolean = false,
    val portalEnabled: Boolean = false,
    val portalEmail: String = "",
    val portalKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@Keep
@IgnoreExtraProperties
data class ClientProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val clientId: String = "",
    val name: String = "",
    val description: String = "",
    val colorHex: String = "#2555E6",
    val sortOrder: Long = 0L,
    val defaultProject: Boolean = false,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@Keep
@IgnoreExtraProperties
data class ClientTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val clientId: String = "",
    val projectId: String = "",
    val title: String = "",
    val details: String = "",
    val priority: String = TaskPriority.NORMAL.raw,
    val completed: Boolean = false,
    val dueLabel: String = "",
    val createdByClient: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun priorityEnum(): TaskPriority = TaskPriority.fromRaw(priority)
}

@Immutable
@Keep
@IgnoreExtraProperties
data class ClientPortalProfile(
    @DocumentId val portalKey: String = "",
    val ownerId: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val clientCompany: String = "",
    val clientColorHex: String = "#2555E6",
    val portalEmail: String = "",
    val active: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@Keep
@IgnoreExtraProperties
data class PortalProject(
    @DocumentId val id: String = "",
    val portalKey: String = "",
    val ownerId: String = "",
    val clientId: String = "",
    val name: String = "",
    val description: String = "",
    val colorHex: String = "#2555E6",
    val sortOrder: Long = 0L,
    val defaultProject: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@Keep
@IgnoreExtraProperties
data class PortalTask(
    @DocumentId val id: String = "",
    val portalKey: String = "",
    val ownerId: String = "",
    val clientId: String = "",
    val projectId: String = "",
    val title: String = "",
    val details: String = "",
    val priority: String = TaskPriority.NORMAL.raw,
    val completed: Boolean = false,
    val dueLabel: String = "",
    val createdByClient: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun priorityEnum(): TaskPriority = TaskPriority.fromRaw(priority)
}

@Immutable
data class ClientPortalSession(
    val portalKey: String,
    val ownerId: String,
    val clientId: String,
    val clientName: String,
    val clientCompany: String,
    val clientColorHex: String,
    val portalEmail: String
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
