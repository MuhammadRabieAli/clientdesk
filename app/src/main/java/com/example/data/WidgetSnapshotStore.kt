package com.example.data

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode(val raw: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

data class WidgetClientSnapshot(
    val id: String,
    val name: String,
    val company: String,
    val colorHex: String,
    val sortOrder: Long,
    val portalKey: String = ""
)

data class WidgetTaskSnapshot(
    val id: String,
    val clientId: String,
    val title: String,
    val priority: String,
    val completed: Boolean,
    val dueLabel: String
)

data class WidgetSnapshot(
    val userId: String = "",
    val clients: List<WidgetClientSnapshot> = emptyList(),
    val tasks: List<WidgetTaskSnapshot> = emptyList()
)

object WidgetSnapshotStore {
    private const val PREFS_NAME = "clientdesk_widget_cache"
    private const val KEY_SNAPSHOT = "snapshot_json"
    private const val KEY_WIDGET_CLIENT_PREFIX = "selected_client_"
    private const val KEY_WIDGET_SHOW_DONE_PREFIX = "show_done_"
    private const val KEY_THEME_MODE = "theme_mode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromRaw(prefs(context).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.raw))

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.raw).apply()
    }

    fun isDarkThemeActive(context: Context): Boolean = when (getThemeMode(context)) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMask == Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun isShowCompleted(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("$KEY_WIDGET_SHOW_DONE_PREFIX$appWidgetId", false)

    fun toggleShowCompleted(context: Context, appWidgetId: Int): Boolean {
        val next = !isShowCompleted(context, appWidgetId)
        prefs(context).edit().putBoolean("$KEY_WIDGET_SHOW_DONE_PREFIX$appWidgetId", next).apply()
        return next
    }

    fun saveSnapshot(context: Context, userId: String, clients: List<Client>, tasks: List<ClientTask>) {
        val activeClients = clients
            .filter { !it.archived }
            .sortedWith(compareBy<Client> { it.sortOrder }.thenBy { it.name.lowercase() })

        val root = JSONObject().apply {
            put("userId", userId)
            put("clients", JSONArray().apply {
                activeClients.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("company", c.company)
                        put("colorHex", c.colorHex)
                        put("sortOrder", c.sortOrder)
                        put("portalKey", if (c.portalEnabled) c.portalKey else "")
                    })
                }
            })
            put("tasks", JSONArray().apply {
                tasks.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("clientId", t.clientId)
                        put("title", t.title)
                        put("priority", t.priority)
                        put("completed", t.completed)
                        put("dueLabel", t.dueLabel)
                    })
                }
            })
        }
        prefs(context).edit().putString(KEY_SNAPSHOT, root.toString()).apply()
    }

    fun loadSnapshot(context: Context): WidgetSnapshot {
        val raw = prefs(context).getString(KEY_SNAPSHOT, null) ?: return WidgetSnapshot()
        return try {
            val root = JSONObject(raw)
            val userId = root.optString("userId", "")
            val clientsArr = root.optJSONArray("clients") ?: JSONArray()
            val tasksArr = root.optJSONArray("tasks") ?: JSONArray()

            val clients = buildList {
                for (i in 0 until clientsArr.length()) {
                    val obj = clientsArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (id.isNotBlank()) {
                        add(
                            WidgetClientSnapshot(
                                id = id,
                                name = obj.optString("name", "Client"),
                                company = obj.optString("company", ""),
                                colorHex = obj.optString("colorHex", "#2555E6"),
                                sortOrder = obj.optLong("sortOrder", i.toLong()),
                                portalKey = obj.optString("portalKey", "")
                            )
                        )
                    }
                }
            }

            val tasks = buildList {
                for (i in 0 until tasksArr.length()) {
                    val obj = tasksArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (id.isNotBlank()) {
                        add(
                            WidgetTaskSnapshot(
                                id = id,
                                clientId = obj.optString("clientId", ""),
                                title = obj.optString("title", ""),
                                priority = obj.optString("priority", TaskPriority.NORMAL.raw),
                                completed = obj.optBoolean("completed", false),
                                dueLabel = obj.optString("dueLabel", "")
                            )
                        )
                    }
                }
            }

            WidgetSnapshot(userId = userId, clients = clients, tasks = tasks)
        } catch (_: Exception) {
            WidgetSnapshot()
        }
    }

    fun toggleTaskLocally(context: Context, taskId: String): Boolean? {
        val snapshot = loadSnapshot(context)
        var newCompleted: Boolean? = null
        val updatedTasks = snapshot.tasks.map { task ->
            if (task.id == taskId) {
                val toggled = !task.completed
                newCompleted = toggled
                task.copy(completed = toggled)
            } else {
                task
            }
        }
        val toggledValue = newCompleted ?: return null
        val root = JSONObject().apply {
            put("userId", snapshot.userId)
            put("clients", JSONArray().apply {
                snapshot.clients.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("company", c.company)
                        put("colorHex", c.colorHex)
                        put("sortOrder", c.sortOrder)
                        put("portalKey", c.portalKey)
                    })
                }
            })
            put("tasks", JSONArray().apply {
                updatedTasks.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("clientId", t.clientId)
                        put("title", t.title)
                        put("priority", t.priority)
                        put("completed", t.completed)
                        put("dueLabel", t.dueLabel)
                    })
                }
            })
        }
        prefs(context).edit().putString(KEY_SNAPSHOT, root.toString()).apply()
        return toggledValue
    }

    fun clearAll(context: Context) {
        val savedTheme = getThemeMode(context)
        prefs(context).edit().clear().putString(KEY_THEME_MODE, savedTheme.raw).apply()
    }

    fun getSelectedClientId(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("$KEY_WIDGET_CLIENT_PREFIX$appWidgetId", null)

    fun setSelectedClientId(context: Context, appWidgetId: Int, clientId: String) {
        prefs(context).edit().putString("$KEY_WIDGET_CLIENT_PREFIX$appWidgetId", clientId).apply()
    }

    fun removeWidgetSelection(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("$KEY_WIDGET_CLIENT_PREFIX$appWidgetId")
            .remove("$KEY_WIDGET_SHOW_DONE_PREFIX$appWidgetId")
            .apply()
    }
}
