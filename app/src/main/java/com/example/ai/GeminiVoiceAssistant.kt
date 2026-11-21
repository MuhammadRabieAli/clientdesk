package com.example.ai

import com.example.data.Client
import com.example.data.ClientTask
import com.example.data.TaskPriority
import com.example.ui.TaskFilter
import com.example.ui.theme.ClientColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class VoiceTaskActionType {
    COMPLETE,
    REOPEN,
    UPDATE,
    DELETE;

    companion object {
        fun fromRaw(raw: String?): VoiceTaskActionType? = when (raw?.trim()?.uppercase()) {
            "COMPLETE", "DONE", "CHECK" -> COMPLETE
            "REOPEN", "UNDO", "INCOMPLETE" -> REOPEN
            "UPDATE", "EDIT", "MODIFY" -> UPDATE
            "DELETE", "REMOVE" -> DELETE
            else -> null
        }
    }
}

data class ProposedTaskAction(
    val tempId: String = UUID.randomUUID().toString(),
    val existingTask: ClientTask,
    val clientName: String,
    val actionType: VoiceTaskActionType,
    val newTitle: String? = null,
    val newDetails: String? = null,
    val newPriority: TaskPriority? = null,
    val newDueLabel: String? = null,
    val selected: Boolean = true
)

data class VoiceAssistantPlan(
    val transcript: String,
    val spokenReply: String,
    val clients: List<ProposedClient>,
    val tasks: List<ProposedTask>,
    val taskActions: List<ProposedTaskAction>,
    val focusClientName: String? = null,
    val filterMode: TaskFilter? = null
) {
    val hasMutations: Boolean
        get() = clients.isNotEmpty() || tasks.isNotEmpty() || taskActions.isNotEmpty()

    val hasDestructiveActions: Boolean
        get() = taskActions.any { it.actionType == VoiceTaskActionType.DELETE }

    val totalActionCount: Int
        get() = clients.size + tasks.size + taskActions.size
}

sealed interface VoiceAssistantState {
    data object Idle : VoiceAssistantState
    data class Processing(val statusMessage: String) : VoiceAssistantState
    data class Review(val plan: VoiceAssistantPlan) : VoiceAssistantState
    data class Executed(
        val transcript: String,
        val spokenReply: String,
        val executedSummary: String
    ) : VoiceAssistantState
    data class Error(
        val message: String,
        val canRetry: Boolean = false
    ) : VoiceAssistantState
}

/**
 * Gemini-powered multimodal Voice Assistant for ClientDesk.
 * Calls the Gemini API using `BuildConfig.GEMINI_API_KEY` (configured via the AI Studio
 * Secrets panel), prioritizing `gemini-3.8-flash` with automatic failover to `gemini-3.5-flash-lite`.
 */
class GeminiVoiceAssistant {

    private val paletteHexes = ClientColorPalette.map { it.hex }

    private val voicePlanResponseSchema: JSONObject by lazy {
        JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "transcript",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Accurate verbatim transcription of what the user said in its original language.")
                    )
                    .put(
                        "spokenReply",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Friendly, concise 1-2 sentence spoken response in the exact same language as the user.")
                    )
                    .put(
                        "newClients",
                        JSONObject()
                            .put("type", "ARRAY")
                            .put("description", "New clients to register. Empty if not adding new clients.")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "OBJECT")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("name", JSONObject().put("type", "STRING").put("description", "New client name (max 60 chars)."))
                                            .put("company", JSONObject().put("type", "STRING").put("description", "Optional project or company label."))
                                            .put("notes", JSONObject().put("type", "STRING").put("description", "Optional short workspace notes."))
                                    )
                                    .put("required", JSONArray().put("name"))
                            )
                    )
                    .put(
                        "newTasks",
                        JSONObject()
                            .put("type", "ARRAY")
                            .put("description", "New tasks to create.")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "OBJECT")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("clientName", JSONObject().put("type", "STRING").put("description", "Exact name of an existing client or a client in newClients."))
                                            .put("title", JSONObject().put("type", "STRING").put("description", "Concise, actionable task title (max 120 chars)."))
                                            .put("details", JSONObject().put("type", "STRING").put("description", "Optional short task details."))
                                            .put("priority", JSONObject().put("type", "STRING").put("description", "One of LOW, NORMAL, or HIGH."))
                                            .put("dueLabel", JSONObject().put("type", "STRING").put("description", "Optional short due label like Today, Tomorrow, Fri, Nov 24, or Arabic equivalent."))
                                    )
                                    .put("required", JSONArray().put("clientName").put("title"))
                            )
                    )
                    .put(
                        "taskActions",
                        JSONObject()
                            .put("type", "ARRAY")
                            .put("description", "Actions to perform on existing tasks (mark complete, reopen, edit, or delete).")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "OBJECT")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("taskRef", JSONObject().put("type", "INTEGER").put("description", "Exact numeric # index of the existing task from the task catalog."))
                                            .put("action", JSONObject().put("type", "STRING").put("description", "One of COMPLETE, REOPEN, UPDATE, or DELETE."))
                                            .put("newTitle", JSONObject().put("type", "STRING").put("description", "Updated title if action is UPDATE."))
                                            .put("newDetails", JSONObject().put("type", "STRING").put("description", "Updated details if action is UPDATE."))
                                            .put("newPriority", JSONObject().put("type", "STRING").put("description", "Updated priority (LOW, NORMAL, HIGH) if changed."))
                                            .put("newDueLabel", JSONObject().put("type", "STRING").put("description", "Updated due label if changed."))
                                    )
                                    .put("required", JSONArray().put("taskRef").put("action"))
                            )
                    )
                    .put(
                        "focusClientName",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Optional existing client name if the user asked to open or view that client's workspace.")
                    )
                    .put(
                        "filterMode",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Optional task filter if requested: ALL, OPEN, ONGOING, or DONE.")
                    )
            )
            .put(
                "required",
                JSONArray()
                    .put("transcript")
                    .put("spokenReply")
                    .put("newClients")
                    .put("newTasks")
                    .put("taskActions")
            )
    }

    suspend fun processVoiceCommand(
        audioWavBytes: ByteArray?,
        typedCommand: String?,
        existingClients: List<Client>,
        existingTasks: List<ClientTask>,
        preferredClient: Client?,
        onProgress: (String) -> Unit = {}
    ): VoiceAssistantPlan = withContext(Dispatchers.IO) {
        val clientNameById = existingClients.associate { it.id to it.name }

        val indexedTasks = existingTasks.take(MAX_CONTEXT_TASKS)
        val taskByRef = indexedTasks.mapIndexed { idx, task -> (idx + 1) to task }.toMap()

        val clientsCatalog = if (existingClients.isEmpty()) {
            "None yet."
        } else {
            existingClients.joinToString("\n") { c ->
                val companyPart = if (c.company.isNotBlank()) " (${c.company})" else ""
                "- ${c.name}$companyPart"
            }
        }

        val tasksCatalog = if (indexedTasks.isEmpty()) {
            "No tasks yet."
        } else {
            indexedTasks.mapIndexed { idx, task ->
                val ref = idx + 1
                val cName = clientNameById[task.clientId] ?: "Unknown"
                val status = task.statusEnum().raw
                val due = if (task.dueLabel.isNotBlank()) ", due: ${task.dueLabel}" else ""
                "#$ref [$status | ${task.priority} | $cName$due] ${task.title}"
            }.joinToString("\n")
        }

        val preferredLine = if (preferredClient != null) {
            "Currently focused client: \"${preferredClient.name}\". Use this client for new tasks unless another client is mentioned."
        } else {
            "Currently viewing: All Clients. Match any mentioned client or propose a new client if clearly requested."
        }

        val instructionBlock = buildString {
            appendLine("You are ClientDesk Voice Assistant, a fast, helpful workspace copilot.")
            appendLine("Existing clients:")
            appendLine(clientsCatalog)
            appendLine("Existing tasks (reference them strictly by their # integer index in taskActions.taskRef):")
            appendLine(tasksCatalog)
            appendLine(preferredLine)
            appendLine("Rules:")
            appendLine("- Respond and transcribe in the EXACT language spoken or typed (Arabic stays Arabic, English stays English).")
            appendLine("- Support: registering new clients, creating tasks with priority/due labels, marking existing tasks COMPLETE or REOPEN, updating task priority/due/title/details, deleting tasks, switching focusClientName/filterMode, and answering questions about current tasks/clients.")
            appendLine("- Do NOT duplicate an existing client in newClients; match existing clients by exact name.")
            appendLine("- If creating tasks when no client exists or is named, propose one clean client in newClients (e.g. 'General' or 'عام').")
            appendLine("- If the user is asking a question (e.g. 'What tasks are left for Acme?', 'كم مهمة باقية؟'), leave newClients/newTasks/taskActions empty and give a concise, accurate answer in spokenReply.")
            appendLine("- Keep spokenReply natural, brief (1-2 sentences), and suitable for text-to-speech playback.")
            if (!typedCommand.isNullOrBlank()) {
                appendLine("User command:")
                append(typedCommand.trim())
            } else {
                append("Listen carefully to the attached audio clip, transcribe it accurately in transcript, and produce the workspace plan.")
            }
        }

        onProgress(
            if (audioWavBytes != null) "Understanding your voice with Gemini 3.8…" else "Thinking with Gemini 3.8…"
        )

        val rawJson = GeminiDirectApiClient.generateStructuredJson(
            instructionBlock = instructionBlock,
            responseSchema = voicePlanResponseSchema,
            audioBytes = audioWavBytes,
            audioMimeType = VoiceAudioRecorder.AUDIO_MIME_TYPE,
            onProgress = onProgress
        )

        parseVoicePlanJson(
            rawJson = rawJson,
            fallbackTranscript = typedCommand?.trim().orEmpty(),
            existingClients = existingClients,
            taskByRef = taskByRef,
            clientNameById = clientNameById,
            preferredClient = preferredClient
        )
    }

    private fun parseVoicePlanJson(
        rawJson: String,
        fallbackTranscript: String,
        existingClients: List<Client>,
        taskByRef: Map<Int, ClientTask>,
        clientNameById: Map<String, String>,
        preferredClient: Client?
    ): VoiceAssistantPlan {
        val root = JSONObject(rawJson)
        val transcript = root.optString("transcript", "").trim().ifBlank { fallbackTranscript }
        val spokenReply = root.optString("spokenReply", "").trim()
            .ifBlank { "Here is what I prepared for your workspace." }

        val existingNamesLower = existingClients.map { it.name.trim().lowercase() }.toSet()

        // 1. Parse new clients
        val clientsArray = root.optJSONArray("newClients")
        val parsedClients = mutableListOf<ProposedClient>()
        if (clientsArray != null) {
            for (i in 0 until clientsArray.length()) {
                val obj = clientsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(80)
                if (name.isBlank()) continue
                if (name.lowercase() in existingNamesLower) continue
                if (parsedClients.any { it.name.equals(name, ignoreCase = true) }) continue
                val company = obj.optString("company", "").trim().take(80)
                val notes = obj.optString("notes", "").trim().take(300)
                val hex = paletteHexes[(existingClients.size + parsedClients.size) % paletteHexes.size]
                parsedClients.add(
                    ProposedClient(
                        name = name,
                        company = company,
                        notes = notes,
                        colorHex = hex,
                        selected = true
                    )
                )
            }
        }

        val fallbackClientName = preferredClient?.name
            ?: parsedClients.firstOrNull()?.name
            ?: existingClients.firstOrNull()?.name
            ?: "General"

        // 2. Parse new tasks
        val tasksArray = root.optJSONArray("newTasks")
        val parsedTasks = mutableListOf<ProposedTask>()
        if (tasksArray != null) {
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim().take(140)
                if (title.isBlank()) continue
                val rawClientName = obj.optString("clientName", "").trim().ifBlank { fallbackClientName }
                val matchedExisting = existingClients.firstOrNull {
                    it.name.equals(rawClientName, ignoreCase = true)
                }?.name
                val matchedNew = parsedClients.firstOrNull {
                    it.name.equals(rawClientName, ignoreCase = true)
                }?.name
                val resolvedClientName = matchedExisting ?: matchedNew ?: rawClientName

                if (matchedExisting == null && matchedNew == null &&
                    parsedClients.none { it.name.equals(resolvedClientName, ignoreCase = true) }
                ) {
                    parsedClients.add(
                        ProposedClient(
                            name = resolvedClientName,
                            colorHex = paletteHexes[(existingClients.size + parsedClients.size) % paletteHexes.size]
                        )
                    )
                }

                val details = obj.optString("details", "").trim().take(400)
                val priority = TaskPriority.fromRaw(obj.optString("priority", "NORMAL"))
                val dueLabel = obj.optString("dueLabel", "").trim().take(40)
                parsedTasks.add(
                    ProposedTask(
                        clientName = resolvedClientName,
                        title = title,
                        details = details,
                        priority = priority,
                        dueLabel = dueLabel,
                        selected = true
                    )
                )
            }
        }

        // 3. Parse actions on existing tasks
        val actionsArray = root.optJSONArray("taskActions")
        val parsedTaskActions = mutableListOf<ProposedTaskAction>()
        val seenTaskIds = mutableSetOf<String>()
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val obj = actionsArray.optJSONObject(i) ?: continue
                val ref = obj.optInt("taskRef", -1)
                val targetTask = taskByRef[ref] ?: continue
                if (!seenTaskIds.add(targetTask.id)) continue
                val actionType = VoiceTaskActionType.fromRaw(obj.optString("action", "")) ?: continue
                val newTitle = obj.optString("newTitle", "").trim().take(140).ifBlank { null }
                val newDetails = obj.optString("newDetails", "").trim().take(400).ifBlank { null }
                val rawPrio = obj.optString("newPriority", "").trim()
                val newPriority = if (rawPrio.isNotBlank()) TaskPriority.fromRaw(rawPrio) else null
                val newDue = obj.optString("newDueLabel", "").trim().take(40).ifBlank { null }

                parsedTaskActions.add(
                    ProposedTaskAction(
                        existingTask = targetTask,
                        clientName = clientNameById[targetTask.clientId] ?: "Client",
                        actionType = actionType,
                        newTitle = newTitle,
                        newDetails = newDetails,
                        newPriority = newPriority,
                        newDueLabel = newDue,
                        selected = true
                    )
                )
            }
        }

        // 4. Optional navigation/filter directives
        val rawFocusClient = root.optString("focusClientName", "").trim().ifBlank { null }
        val resolvedFocusClient = if (rawFocusClient != null) {
            existingClients.firstOrNull { it.name.equals(rawFocusClient, ignoreCase = true) }?.name
                ?: parsedClients.firstOrNull { it.name.equals(rawFocusClient, ignoreCase = true) }?.name
        } else {
            null
        }

        val parsedFilter = when (root.optString("filterMode", "").trim().uppercase()) {
            "ALL" -> TaskFilter.ALL
            "OPEN" -> TaskFilter.OPEN
            "ONGOING", "IN_PROGRESS" -> TaskFilter.ONGOING
            "DONE" -> TaskFilter.DONE
            else -> null
        }

        return VoiceAssistantPlan(
            transcript = transcript,
            spokenReply = spokenReply,
            clients = parsedClients,
            tasks = parsedTasks,
            taskActions = parsedTaskActions,
            focusClientName = resolvedFocusClient,
            filterMode = parsedFilter
        )
    }

    companion object {
        private const val MAX_CONTEXT_TASKS = 60
    }
}
