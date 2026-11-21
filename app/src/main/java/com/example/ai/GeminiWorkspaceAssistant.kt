package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.Client
import com.example.data.ClientTask
import com.example.data.TaskPriority
import com.example.ui.theme.ClientColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ProposedClient(
    val tempId: String = UUID.randomUUID().toString(),
    val name: String,
    val company: String = "",
    val notes: String = "",
    val colorHex: String = ClientColorPalette.first().hex,
    val selected: Boolean = true
)

data class ProposedTask(
    val tempId: String = UUID.randomUUID().toString(),
    val clientName: String,
    val title: String,
    val details: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val dueLabel: String = "",
    val selected: Boolean = true
)

data class ProposedTaskStatusUpdate(
    val tempId: String = UUID.randomUUID().toString(),
    val taskId: String,
    val taskTitle: String,
    val clientName: String,
    val markCompleted: Boolean,
    val selected: Boolean = true
)

data class AiWorkspacePlan(
    val summary: String,
    val transcript: String = "",
    val clients: List<ProposedClient> = emptyList(),
    val tasks: List<ProposedTask> = emptyList(),
    val taskUpdates: List<ProposedTaskStatusUpdate> = emptyList(),
    val focusClientName: String = ""
) {
    val hasAnyAction: Boolean
        get() = clients.isNotEmpty() ||
            tasks.isNotEmpty() ||
            taskUpdates.isNotEmpty() ||
            focusClientName.isNotBlank()
}

sealed interface AiAssistantState {
    data object Idle : AiAssistantState
    data class Loading(val statusMessage: String = "") : AiAssistantState
    data class Review(val plan: AiWorkspacePlan) : AiAssistantState
    data class Applied(val summary: String, val transcript: String = "") : AiAssistantState
    data class Error(val message: String) : AiAssistantState
}

/**
 * Gemini-powered workspace planner & image scanner using `BuildConfig.GEMINI_API_KEY`
 * (configured in the AI Studio Secrets panel), prioritizing `gemini-3.8-flash` with
 * automatic failover to `gemini-3.5-flash-lite`.
 */
class GeminiWorkspaceAssistant {

    private val paletteHexes = ClientColorPalette.map { it.hex }

    private val workspacePlanResponseSchema: JSONObject by lazy {
        JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "transcript",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Exact transcription of spoken audio if audio was provided; empty string for text-only requests.")
                    )
                    .put(
                        "summary",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Short, friendly 1-sentence confirmation in the same language as the user's input or voice.")
                    )
                    .put(
                        "focusClientName",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Exact name of a client to switch/open if the user asked to open or view a client workspace, else empty string.")
                    )
                    .put(
                        "newClients",
                        JSONObject()
                            .put("type", "ARRAY")
                            .put("description", "New clients to register. Empty if all tasks belong to existing clients.")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "OBJECT")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("name", JSONObject().put("type", "STRING").put("description", "Client name (max 60 chars)."))
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
                            .put("description", "Actionable tasks to create.")
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
                                            .put("dueLabel", JSONObject().put("type", "STRING").put("description", "Optional short due label like Today, Fri, Nov 24, or Arabic equivalent."))
                                    )
                                    .put("required", JSONArray().put("clientName").put("title"))
                            )
                    )
                    .put(
                        "taskUpdates",
                        JSONObject()
                            .put("type", "ARRAY")
                            .put("description", "Existing tasks the user asked to mark done/completed or reopen.")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "OBJECT")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("taskId", JSONObject().put("type", "STRING").put("description", "Exact task ID from the provided existing tasks list."))
                                            .put("markCompleted", JSONObject().put("type", "BOOLEAN").put("description", "true to mark the task done, false to reopen it."))
                                    )
                                    .put("required", JSONArray().put("taskId").put("markCompleted"))
                            )
                    )
            )
            .put(
                "required",
                JSONArray()
                    .put("summary")
                    .put("newClients")
                    .put("newTasks")
            )
    }

    suspend fun analyzeInput(
        userPrompt: String = "",
        image: Bitmap? = null,
        audioBytes: ByteArray? = null,
        audioMimeType: String = VoiceAudioRecorder.AUDIO_MIME_TYPE,
        existingClients: List<Client>,
        existingTasks: List<ClientTask> = emptyList(),
        preferredClient: Client? = null,
        onProgress: (String) -> Unit = {}
    ): AiWorkspacePlan = withContext(Dispatchers.IO) {
        val clientsById = existingClients.associateBy { it.id }

        val clientsCatalog = if (existingClients.isEmpty()) {
            "None yet."
        } else {
            existingClients.joinToString("\n") { c ->
                val companyPart = if (c.company.isNotBlank()) " (${c.company})" else ""
                "- ${c.name}$companyPart"
            }
        }

        val tasksCatalog = if (existingTasks.isEmpty()) {
            "None yet."
        } else {
            existingTasks
                .sortedWith(compareBy<ClientTask> { it.completed }.thenByDescending { it.updatedAt?.seconds ?: 0L })
                .take(35)
                .joinToString("\n") { t ->
                    val clientName = clientsById[t.clientId]?.name ?: "Unknown"
                    val state = t.statusEnum().raw
                    "- id=${t.id} | client=$clientName | status=$state | title=${t.title}"
                }
        }

        val preferredLine = if (preferredClient != null) {
            "Default client: \"${preferredClient.name}\". Use this client unless another client is clearly named."
        } else {
            "Client mode: Auto-detect from existing clients or propose a new client."
        }

        val effectivePrompt = when {
            audioBytes != null && userPrompt.isNotBlank() ->
                "Listen to the attached voice command and also consider: ${userPrompt.trim()}"
            audioBytes != null ->
                "Listen carefully to the attached voice command, transcribe it into transcript, and extract all requested workspace actions (new clients, new tasks, task completions/reopens, or switching clients)."
            userPrompt.isNotBlank() -> userPrompt.trim()
            else -> "Scan this image and extract all clients and actionable tasks visible in it."
        }

        val instructionBlock = buildString {
            appendLine("You are the smart voice and workspace assistant for ClientDesk, a client-separated task manager.")
            appendLine("Existing clients:")
            appendLine(clientsCatalog)
            appendLine("Existing tasks (use exact id for taskUpdates):")
            appendLine(tasksCatalog)
            appendLine(preferredLine)
            appendLine("Rules:")
            appendLine("- Keep the exact language of the user's voice/prompt/image (Arabic stays Arabic, English stays English).")
            appendLine("- If audio is attached, put the accurate transcription in 'transcript'.")
            appendLine("- Match existing clients by exact name and do NOT duplicate them in newClients.")
            appendLine("- Put newly requested or newly detected clients in newClients and reference their exact name in newTasks[].clientName.")
            appendLine("- If the user creates tasks and no client exists or is named, propose one sensible client in newClients (e.g. 'General' or 'عام').")
            appendLine("- If the user says they finished/completed/checked off a task (e.g. 'mark done', 'finished', 'خلصت', 'تمت'), match the closest existing task ID into taskUpdates with markCompleted=true (or false if reopening).")
            appendLine("- If the user asks to open/switch to a client workspace (e.g. 'open Northstar', 'افتح عميل النور'), set focusClientName to that client's exact name.")
            appendLine("- Keep task titles concise and clean. Set priority to HIGH if urgent/ASAP/عاجل/مهم, else NORMAL or LOW. Set dueLabel only when a date/deadline is stated.")
            appendLine("Request:")
            append(effectivePrompt)
        }

        onProgress(
            when {
                audioBytes != null -> "Listening & transcribing with Gemini 3.8…"
                image != null -> "Scanning image with Gemini 3.8…"
                else -> "Connecting to Gemini 3.8…"
            }
        )

        val rawJson = GeminiDirectApiClient.generateStructuredJson(
            instructionBlock = instructionBlock,
            responseSchema = workspacePlanResponseSchema,
            image = image,
            audioBytes = audioBytes,
            audioMimeType = audioMimeType,
            onProgress = onProgress
        )

        parsePlanJson(rawJson, existingClients, existingTasks, preferredClient)
    }

    private fun parsePlanJson(
        rawJson: String,
        existingClients: List<Client>,
        existingTasks: List<ClientTask>,
        preferredClient: Client?
    ): AiWorkspacePlan {
        val root = JSONObject(rawJson)
        val transcript = root.optString("transcript", "").trim()
        val summary = root.optString("summary", "").trim()
            .ifBlank { "Ready to apply your workspace updates." }
        val rawFocusClient = root.optString("focusClientName", "").trim()

        val existingNamesLower = existingClients.map { it.name.trim().lowercase() }.toSet()
        val clientsById = existingClients.associateBy { it.id }
        val tasksById = existingTasks.associateBy { it.id }

        val clientsArray = root.optJSONArray("newClients")
        val parsedClients = mutableListOf<ProposedClient>()
        if (clientsArray != null) {
            for (i in 0 until clientsArray.length()) {
                val obj = clientsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(80)
                if (name.isBlank()) continue
                if (name.lowercase() in existingNamesLower) continue
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

        val updatesArray = root.optJSONArray("taskUpdates")
        val parsedUpdates = mutableListOf<ProposedTaskStatusUpdate>()
        val seenTaskIds = mutableSetOf<String>()
        if (updatesArray != null) {
            for (i in 0 until updatesArray.length()) {
                val obj = updatesArray.optJSONObject(i) ?: continue
                val taskId = obj.optString("taskId", "").trim()
                val existingTask = tasksById[taskId] ?: continue
                if (!seenTaskIds.add(existingTask.id)) continue
                val markCompleted = obj.optBoolean("markCompleted", true)
                val clientName = clientsById[existingTask.clientId]?.name ?: "Client"
                parsedUpdates.add(
                    ProposedTaskStatusUpdate(
                        taskId = existingTask.id,
                        taskTitle = existingTask.title,
                        clientName = clientName,
                        markCompleted = markCompleted,
                        selected = true
                    )
                )
            }
        }

        val resolvedFocusClient = if (rawFocusClient.isNotBlank()) {
            existingClients.firstOrNull { it.name.equals(rawFocusClient, ignoreCase = true) }?.name
                ?: parsedClients.firstOrNull { it.name.equals(rawFocusClient, ignoreCase = true) }?.name
                ?: ""
        } else {
            ""
        }

        return AiWorkspacePlan(
            summary = summary,
            transcript = transcript,
            clients = parsedClients,
            tasks = parsedTasks,
            taskUpdates = parsedUpdates,
            focusClientName = resolvedFocusClient
        )
    }

    companion object {
        private const val TAG = "GeminiWorkspaceAI"

        /**
         * Safely decodes and downscales a picked or captured image URI to Gemini's native 1K
         * vision tile size (1024px) for fast upload & OCR while preserving crisp Arabic/English text.
         */
        suspend fun decodeScaledBitmap(
            context: Context,
            uri: Uri,
            maxDimensionPx: Int = 1024
        ): Bitmap? = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sampleSize = 1
                var width = bounds.outWidth
                var height = bounds.outHeight
                while (width / 2 >= maxDimensionPx || height / 2 >= maxDimensionPx) {
                    width /= 2
                    height /= 2
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null

                val longest = maxOf(decoded.width, decoded.height)
                if (longest > maxDimensionPx) {
                    val scale = maxDimensionPx.toFloat() / longest.toFloat()
                    val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
                    val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                    if (scaled != decoded) decoded.recycle()
                    scaled
                } else {
                    decoded
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode image for Gemini scan", e)
                null
            }
        }

        fun createTempScanFile(context: Context): File {
            val dir = File(context.cacheDir, "scans").apply { mkdirs() }
            return File(dir, "scan_${System.currentTimeMillis()}.jpg")
        }
    }
}
