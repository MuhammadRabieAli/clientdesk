package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.ClientDeskRepository
import com.example.data.TaskPriority
import com.example.data.WidgetClientSnapshot
import com.example.data.WidgetSnapshotStore
import com.example.data.WidgetTaskSnapshot
import com.example.ui.formatWidgetBidiText
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class WidgetSizeSpec(
    val layoutResId: Int,
    val maxTasks: Int,
    val maxClientTabs: Int,
    val hasAddButton: Boolean,
    val hasProgressBar: Boolean,
    val hasDueLabels: Boolean,
    val hasFilterChip: Boolean,
    val hasClientIndex: Boolean
) {
    SMALL(
        layoutResId = R.layout.widget_client_small,
        maxTasks = 3,
        maxClientTabs = 0,
        hasAddButton = false,
        hasProgressBar = false,
        hasDueLabels = false,
        hasFilterChip = false,
        hasClientIndex = true
    ),
    MEDIUM(
        layoutResId = R.layout.widget_client_medium,
        maxTasks = 4,
        maxClientTabs = 3,
        hasAddButton = true,
        hasProgressBar = false,
        hasDueLabels = true,
        hasFilterChip = true,
        hasClientIndex = false
    ),
    LARGE(
        layoutResId = R.layout.widget_client_large,
        maxTasks = 7,
        maxClientTabs = 4,
        hasAddButton = true,
        hasProgressBar = true,
        hasDueLabels = true,
        hasFilterChip = true,
        hasClientIndex = false
    )
}

object WidgetActions {
    const val ACTION_PREV_CLIENT = "com.example.clientdesk.widget.ACTION_PREV_CLIENT"
    const val ACTION_NEXT_CLIENT = "com.example.clientdesk.widget.ACTION_NEXT_CLIENT"
    const val ACTION_SELECT_CLIENT = "com.example.clientdesk.widget.ACTION_SELECT_CLIENT"
    const val ACTION_TOGGLE_TASK = "com.example.clientdesk.widget.ACTION_TOGGLE_TASK"
    const val ACTION_TOGGLE_FILTER = "com.example.clientdesk.widget.ACTION_TOGGLE_FILTER"

    const val EXTRA_WIDGET_ID = "extra_widget_id"
    const val EXTRA_CLIENT_ID = "extra_client_id"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_OPEN_ADD_TASK = "extra_open_add_task"
}

object WidgetRefresher {
    fun refreshAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        listOf(
            SmallClientWidgetProvider::class.java to WidgetSizeSpec.SMALL,
            MediumClientWidgetProvider::class.java to WidgetSizeSpec.MEDIUM,
            LargeClientWidgetProvider::class.java to WidgetSizeSpec.LARGE
        ).forEach { (providerClass, spec) ->
            val ids = manager.getAppWidgetIds(ComponentName(appContext, providerClass))
            ids.forEach { widgetId ->
                BaseClientWidgetProvider.renderWidget(
                    context = appContext,
                    appWidgetManager = manager,
                    appWidgetId = widgetId,
                    spec = spec,
                    providerClass = providerClass
                )
            }
        }
    }
}

private data class WidgetPalette(
    val cardBgRes: Int,
    val taskRowBgRes: Int,
    val pillBgRes: Int,
    val pillActiveBgRes: Int,
    val dueBgRes: Int,
    val prevIconRes: Int,
    val nextIconRes: Int,
    val addIconRes: Int,
    val checkOffRes: Int,
    val checkOnRes: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val mutedText: Int,
    val pillActiveText: Int
)

private fun resolveWidgetPalette(isDark: Boolean): WidgetPalette = if (isDark) {
    WidgetPalette(
        cardBgRes = R.drawable.bg_widget_card_dark,
        taskRowBgRes = R.drawable.bg_widget_task_row_dark,
        pillBgRes = R.drawable.bg_widget_pill_dark,
        pillActiveBgRes = R.drawable.bg_widget_pill_active_dark,
        dueBgRes = R.drawable.bg_widget_due_pill_dark,
        prevIconRes = R.drawable.ic_widget_prev_dark,
        nextIconRes = R.drawable.ic_widget_next_dark,
        addIconRes = R.drawable.ic_widget_add_dark,
        checkOffRes = R.drawable.ic_widget_check_off_dark,
        checkOnRes = R.drawable.ic_widget_check_on_dark,
        primaryText = Color.parseColor("#F4F1EC"),
        secondaryText = Color.parseColor("#A9B2BD"),
        mutedText = Color.parseColor("#79838F"),
        pillActiveText = Color.parseColor("#14171A")
    )
} else {
    WidgetPalette(
        cardBgRes = R.drawable.bg_widget_card,
        taskRowBgRes = R.drawable.bg_widget_task_row,
        pillBgRes = R.drawable.bg_widget_pill,
        pillActiveBgRes = R.drawable.bg_widget_pill_active,
        dueBgRes = R.drawable.bg_widget_due_pill,
        prevIconRes = R.drawable.ic_widget_prev,
        nextIconRes = R.drawable.ic_widget_next,
        addIconRes = R.drawable.ic_widget_add,
        checkOffRes = R.drawable.ic_widget_check_off,
        checkOnRes = R.drawable.ic_widget_check_on,
        primaryText = Color.parseColor("#14171A"),
        secondaryText = Color.parseColor("#525A63"),
        mutedText = Color.parseColor("#7B848E"),
        pillActiveText = Color.WHITE
    )
}

abstract class BaseClientWidgetProvider(
    private val spec: WidgetSizeSpec
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            renderWidget(context, appWidgetManager, appWidgetId, spec, this::class.java)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetSnapshotStore.removeWidgetSelection(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appWidgetId = intent.getIntExtra(
            WidgetActions.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val manager = AppWidgetManager.getInstance(context)

        when (action) {
            WidgetActions.ACTION_PREV_CLIENT, WidgetActions.ACTION_NEXT_CLIENT -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val snapshot = WidgetSnapshotStore.loadSnapshot(context)
                if (snapshot.clients.isNotEmpty()) {
                    val currentId = WidgetSnapshotStore.getSelectedClientId(context, appWidgetId)
                    val currentIndex = snapshot.clients.indexOfFirst { it.id == currentId }
                        .takeIf { it >= 0 } ?: 0
                    val delta = if (action == WidgetActions.ACTION_NEXT_CLIENT) 1 else -1
                    val nextIndex = Math.floorMod(currentIndex + delta, snapshot.clients.size)
                    WidgetSnapshotStore.setSelectedClientId(
                        context,
                        appWidgetId,
                        snapshot.clients[nextIndex].id
                    )
                }
                renderWidget(context, manager, appWidgetId, spec, this::class.java)
            }

            WidgetActions.ACTION_SELECT_CLIENT -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val clientId = intent.getStringExtra(WidgetActions.EXTRA_CLIENT_ID) ?: return
                WidgetSnapshotStore.setSelectedClientId(context, appWidgetId, clientId)
                renderWidget(context, manager, appWidgetId, spec, this::class.java)
            }

            WidgetActions.ACTION_TOGGLE_FILTER -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                WidgetSnapshotStore.toggleShowCompleted(context, appWidgetId)
                renderWidget(context, manager, appWidgetId, spec, this::class.java)
            }

            WidgetActions.ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(WidgetActions.EXTRA_TASK_ID) ?: return
                val snapshotBefore = WidgetSnapshotStore.loadSnapshot(context)
                val targetClientId = snapshotBefore.tasks.firstOrNull { it.id == taskId }?.clientId
                val targetPortalKey = snapshotBefore.clients.firstOrNull { it.id == targetClientId }?.portalKey.orEmpty()
                val newCompleted = WidgetSnapshotStore.toggleTaskLocally(context, taskId) ?: return
                WidgetRefresher.refreshAllWidgets(context)

                val currentUid = try {
                    Firebase.auth.currentUser?.uid
                } catch (_: Exception) {
                    null
                }
                if (!currentUid.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            val repository = ClientDeskRepository(context.applicationContext)
                            repository.toggleTaskCompleted(
                                userId = currentUid,
                                taskId = taskId,
                                completed = newCompleted,
                                portalKey = targetPortalKey
                            )
                        } catch (e: Exception) {
                            Log.w("ClientWidget", "Widget task sync deferred: ${e.message}")
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val TASK_ROW_IDS = intArrayOf(
            R.id.widget_task_row_0,
            R.id.widget_task_row_1,
            R.id.widget_task_row_2,
            R.id.widget_task_row_3,
            R.id.widget_task_row_4,
            R.id.widget_task_row_5,
            R.id.widget_task_row_6
        )
        private val TASK_PRIORITY_IDS = intArrayOf(
            R.id.widget_task_priority_0,
            R.id.widget_task_priority_1,
            R.id.widget_task_priority_2,
            R.id.widget_task_priority_3,
            R.id.widget_task_priority_4,
            R.id.widget_task_priority_5,
            R.id.widget_task_priority_6
        )
        private val TASK_CHECK_IDS = intArrayOf(
            R.id.widget_task_check_0,
            R.id.widget_task_check_1,
            R.id.widget_task_check_2,
            R.id.widget_task_check_3,
            R.id.widget_task_check_4,
            R.id.widget_task_check_5,
            R.id.widget_task_check_6
        )
        private val TASK_TITLE_IDS = intArrayOf(
            R.id.widget_task_title_0,
            R.id.widget_task_title_1,
            R.id.widget_task_title_2,
            R.id.widget_task_title_3,
            R.id.widget_task_title_4,
            R.id.widget_task_title_5,
            R.id.widget_task_title_6
        )
        private val TASK_DUE_IDS = intArrayOf(
            R.id.widget_task_due_0,
            R.id.widget_task_due_1,
            R.id.widget_task_due_2,
            R.id.widget_task_due_3,
            R.id.widget_task_due_4,
            R.id.widget_task_due_5,
            R.id.widget_task_due_6
        )
        private val TAB_IDS = intArrayOf(
            R.id.widget_tab_0,
            R.id.widget_tab_1,
            R.id.widget_tab_2,
            R.id.widget_tab_3
        )

        fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            spec: WidgetSizeSpec,
            providerClass: Class<out AppWidgetProvider>
        ) {
            val views = RemoteViews(context.packageName, spec.layoutResId)
            val isDark = WidgetSnapshotStore.isDarkThemeActive(context)
            val palette = resolveWidgetPalette(isDark)

            // Apply Light / Dark surface & control theme
            views.setInt(R.id.widget_root, "setBackgroundResource", palette.cardBgRes)
            views.setTextColor(R.id.widget_client_name, palette.primaryText)
            views.setTextColor(R.id.widget_task_counter, palette.secondaryText)
            views.setTextColor(R.id.widget_empty_text, palette.mutedText)
            views.setTextColor(R.id.widget_more_footer, palette.mutedText)

            views.setInt(R.id.widget_btn_prev, "setBackgroundResource", palette.pillBgRes)
            views.setImageViewResource(R.id.widget_btn_prev, palette.prevIconRes)
            views.setInt(R.id.widget_btn_next, "setBackgroundResource", palette.pillBgRes)
            views.setImageViewResource(R.id.widget_btn_next, palette.nextIconRes)

            if (spec.hasAddButton) {
                views.setInt(R.id.widget_btn_add, "setBackgroundResource", palette.pillActiveBgRes)
                views.setImageViewResource(R.id.widget_btn_add, palette.addIconRes)
            }
            if (spec.hasFilterChip) {
                views.setInt(R.id.widget_btn_filter, "setBackgroundResource", palette.pillBgRes)
                views.setTextColor(R.id.widget_btn_filter, palette.primaryText)
            }
            if (spec.hasClientIndex) {
                views.setTextColor(R.id.widget_client_index, palette.mutedText)
            }

            val snapshot = WidgetSnapshotStore.loadSnapshot(context)
            val clients = snapshot.clients

            if (clients.isEmpty()) {
                renderEmptyState(context, views, appWidgetId, spec, isDark)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val savedClientId = WidgetSnapshotStore.getSelectedClientId(context, appWidgetId)
            val selectedIndex = clients.indexOfFirst { it.id == savedClientId }
                .takeIf { it >= 0 } ?: 0
            val activeClient = clients[selectedIndex]
            val showCompleted = if (spec.hasFilterChip) {
                WidgetSnapshotStore.isShowCompleted(context, appWidgetId)
            } else {
                false
            }

            val allClientTasks = snapshot.tasks
                .filter { it.clientId == activeClient.id }
                .sortedWith(
                    compareBy<WidgetTaskSnapshot> { it.completed }
                        .thenByDescending { priorityRank(it.priority) }
                )
            val openCount = allClientTasks.count { !it.completed }
            val doneCount = allClientTasks.count { it.completed }
            val totalCount = allClientTasks.size
            val filteredTasks = if (showCompleted) {
                allClientTasks
            } else {
                allClientTasks.filter { !it.completed }.ifEmpty { allClientTasks }
            }

            // Client accent stripe + dot
            val accentColor = parseSafeColor(activeClient.colorHex)
            views.setInt(R.id.widget_accent_bar, "setColorFilter", accentColor)
            views.setInt(R.id.widget_client_dot, "setColorFilter", accentColor)

            // Header & counter
            views.setTextViewText(R.id.widget_client_name, formatWidgetBidiText(activeClient.name))
            val counterText = when {
                totalCount == 0 -> context.getString(R.string.widget_no_tasks)
                openCount == 0 -> "$doneCount/$totalCount done · All caught up"
                else -> "$openCount open · $doneCount/$totalCount done"
            }
            views.setTextViewText(R.id.widget_task_counter, counterText)

            if (spec.hasClientIndex) {
                if (clients.size > 1) {
                    views.setViewVisibility(R.id.widget_client_index, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_client_index,
                        "${selectedIndex + 1}/${clients.size}"
                    )
                } else {
                    views.setViewVisibility(R.id.widget_client_index, View.GONE)
                }
            }

            // Open app on header tap
            val openClientIntent = buildOpenAppPendingIntent(
                context = context,
                requestCode = appWidgetId * 100 + 1,
                clientId = activeClient.id,
                openAddTask = false
            )
            views.setOnClickPendingIntent(R.id.widget_client_name, openClientIntent)
            views.setOnClickPendingIntent(R.id.widget_task_counter, openClientIntent)

            // Filter toggle chip (Open / All) on Medium & Large
            if (spec.hasFilterChip) {
                views.setViewVisibility(R.id.widget_btn_filter, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_btn_filter,
                    context.getString(
                        if (showCompleted) R.string.widget_filter_all else R.string.widget_filter_open
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_filter,
                    buildActionPendingIntent(
                        context = context,
                        providerClass = providerClass,
                        action = WidgetActions.ACTION_TOGGLE_FILTER,
                        appWidgetId = appWidgetId,
                        requestCode = appWidgetId * 100 + 5
                    )
                )
            }

            // Prev / Next client arrows
            val hasMultipleClients = clients.size > 1
            views.setViewVisibility(
                R.id.widget_btn_prev,
                if (hasMultipleClients) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_btn_next,
                if (hasMultipleClients) View.VISIBLE else View.GONE
            )
            if (hasMultipleClients) {
                views.setOnClickPendingIntent(
                    R.id.widget_btn_prev,
                    buildActionPendingIntent(
                        context = context,
                        providerClass = providerClass,
                        action = WidgetActions.ACTION_PREV_CLIENT,
                        appWidgetId = appWidgetId,
                        requestCode = appWidgetId * 100 + 2
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_next,
                    buildActionPendingIntent(
                        context = context,
                        providerClass = providerClass,
                        action = WidgetActions.ACTION_NEXT_CLIENT,
                        appWidgetId = appWidgetId,
                        requestCode = appWidgetId * 100 + 3
                    )
                )
            }

            // Quick add button on Medium / Large
            if (spec.hasAddButton) {
                views.setViewVisibility(R.id.widget_btn_add, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.widget_btn_add,
                    buildOpenAppPendingIntent(
                        context = context,
                        requestCode = appWidgetId * 100 + 4,
                        clientId = activeClient.id,
                        openAddTask = true
                    )
                )
            }

            // Progress bar & % badge on Large
            if (spec.hasProgressBar) {
                val pct = if (totalCount == 0) 0 else ((doneCount * 100) / totalCount)
                views.setViewVisibility(
                    R.id.widget_progress_bar_light,
                    if (isDark) View.GONE else View.VISIBLE
                )
                views.setViewVisibility(
                    R.id.widget_progress_bar_dark,
                    if (isDark) View.VISIBLE else View.GONE
                )
                views.setProgressBar(
                    if (isDark) R.id.widget_progress_bar_dark else R.id.widget_progress_bar_light,
                    100,
                    pct,
                    false
                )
                views.setTextViewText(R.id.widget_percent_badge, "$pct%")
                views.setTextColor(R.id.widget_percent_badge, palette.secondaryText)
            }

            // Client tab pills on Medium / Large
            if (spec.maxClientTabs > 0) {
                val visibleWindow = buildVisibleClientWindow(clients, selectedIndex, spec.maxClientTabs)
                views.setViewVisibility(
                    R.id.widget_client_tabs_row,
                    if (clients.size > 1) View.VISIBLE else View.GONE
                )
                for (i in 0 until spec.maxClientTabs) {
                    val tabId = TAB_IDS[i]
                    if (i < visibleWindow.size && clients.size > 1) {
                        val tabClient = visibleWindow[i]
                        val isSelected = tabClient.id == activeClient.id
                        views.setViewVisibility(tabId, View.VISIBLE)
                        views.setTextViewText(tabId, formatWidgetBidiText(tabClient.name))
                        views.setInt(
                            tabId,
                            "setBackgroundResource",
                            if (isSelected) palette.pillActiveBgRes else palette.pillBgRes
                        )
                        views.setTextColor(
                            tabId,
                            if (isSelected) palette.pillActiveText else palette.primaryText
                        )
                        views.setOnClickPendingIntent(
                            tabId,
                            buildSelectClientPendingIntent(
                                context = context,
                                providerClass = providerClass,
                                appWidgetId = appWidgetId,
                                clientId = tabClient.id,
                                requestCode = appWidgetId * 100 + 10 + i
                            )
                        )
                    } else {
                        views.setViewVisibility(tabId, View.GONE)
                    }
                }
            }

            // Task rows
            val shownTasks = filteredTasks.take(spec.maxTasks)
            if (shownTasks.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_empty_text,
                    context.getString(R.string.widget_no_tasks)
                )
                views.setOnClickPendingIntent(R.id.widget_empty_text, openClientIntent)
            } else {
                views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            }

            for (i in 0 until spec.maxTasks) {
                val rowId = TASK_ROW_IDS[i]
                if (i < shownTasks.size) {
                    val task = shownTasks[i]
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setInt(rowId, "setBackgroundResource", palette.taskRowBgRes)

                    val isHigh = TaskPriority.fromRaw(task.priority) == TaskPriority.HIGH && !task.completed
                    views.setViewVisibility(
                        TASK_PRIORITY_IDS[i],
                        if (isHigh) View.VISIBLE else View.GONE
                    )

                    views.setImageViewResource(
                        TASK_CHECK_IDS[i],
                        if (task.completed) palette.checkOnRes else palette.checkOffRes
                    )
                    views.setTextViewText(TASK_TITLE_IDS[i], formatWidgetBidiText(task.title))
                    views.setTextColor(
                        TASK_TITLE_IDS[i],
                        if (task.completed) palette.mutedText else palette.primaryText
                    )
                    views.setInt(
                        TASK_TITLE_IDS[i],
                        "setPaintFlags",
                        if (task.completed) {
                            Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
                        } else {
                            Paint.ANTI_ALIAS_FLAG
                        }
                    )
                    if (spec.hasDueLabels) {
                        val dueId = TASK_DUE_IDS[i]
                        if (task.dueLabel.isNotBlank()) {
                            views.setViewVisibility(dueId, View.VISIBLE)
                            views.setTextViewText(dueId, formatWidgetBidiText(task.dueLabel))
                            views.setInt(dueId, "setBackgroundResource", palette.dueBgRes)
                            views.setTextColor(dueId, palette.secondaryText)
                        } else {
                            views.setViewVisibility(dueId, View.GONE)
                        }
                    }
                    views.setOnClickPendingIntent(
                        rowId,
                        buildToggleTaskPendingIntent(
                            context = context,
                            providerClass = providerClass,
                            appWidgetId = appWidgetId,
                            taskId = task.id,
                            requestCode = appWidgetId * 100 + 30 + i
                        )
                    )
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }

            val overflowCount = (filteredTasks.size - shownTasks.size).coerceAtLeast(0)
            if (overflowCount > 0) {
                views.setViewVisibility(R.id.widget_more_footer, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_more_footer,
                    context.getString(R.string.widget_more_tasks, overflowCount)
                )
                views.setOnClickPendingIntent(R.id.widget_more_footer, openClientIntent)
            } else {
                views.setViewVisibility(R.id.widget_more_footer, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun renderEmptyState(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            spec: WidgetSizeSpec,
            isDark: Boolean
        ) {
            val openAppIntent = buildOpenAppPendingIntent(
                context = context,
                requestCode = appWidgetId * 100 + 99,
                clientId = null,
                openAddTask = false
            )
            views.setInt(R.id.widget_accent_bar, "setColorFilter", Color.parseColor("#2555E6"))
            views.setInt(R.id.widget_client_dot, "setColorFilter", Color.parseColor("#2555E6"))
            views.setTextViewText(R.id.widget_client_name, context.getString(R.string.widget_empty_title))
            views.setTextViewText(R.id.widget_task_counter, context.getString(R.string.widget_empty_body))
            views.setViewVisibility(R.id.widget_btn_prev, View.GONE)
            views.setViewVisibility(R.id.widget_btn_next, View.GONE)
            views.setViewVisibility(R.id.widget_more_footer, View.GONE)
            if (spec.hasClientIndex) {
                views.setViewVisibility(R.id.widget_client_index, View.GONE)
            }
            if (spec.hasFilterChip) {
                views.setViewVisibility(R.id.widget_btn_filter, View.GONE)
            }
            if (spec.hasAddButton) {
                views.setViewVisibility(R.id.widget_btn_add, View.GONE)
            }
            if (spec.maxClientTabs > 0) {
                views.setViewVisibility(R.id.widget_client_tabs_row, View.GONE)
            }
            if (spec.hasProgressBar) {
                views.setViewVisibility(
                    R.id.widget_progress_bar_light,
                    if (isDark) View.GONE else View.VISIBLE
                )
                views.setViewVisibility(
                    R.id.widget_progress_bar_dark,
                    if (isDark) View.VISIBLE else View.GONE
                )
                views.setProgressBar(
                    if (isDark) R.id.widget_progress_bar_dark else R.id.widget_progress_bar_light,
                    100,
                    0,
                    false
                )
                views.setTextViewText(R.id.widget_percent_badge, "0%")
            }
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_empty_body))
            for (i in 0 until spec.maxTasks) {
                views.setViewVisibility(TASK_ROW_IDS[i], View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
        }

        private fun priorityRank(raw: String): Int = when (TaskPriority.fromRaw(raw)) {
            TaskPriority.HIGH -> 3
            TaskPriority.NORMAL -> 2
            TaskPriority.LOW -> 1
        }

        private fun buildVisibleClientWindow(
            clients: List<WidgetClientSnapshot>,
            selectedIndex: Int,
            maxTabs: Int
        ): List<WidgetClientSnapshot> {
            if (clients.size <= maxTabs) return clients
            val start = (selectedIndex - 1).coerceIn(0, clients.size - maxTabs)
            return clients.subList(start, start + maxTabs)
        }

        private fun parseSafeColor(hex: String): Int = try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.parseColor("#2555E6")
        }

        private fun buildOpenAppPendingIntent(
            context: Context,
            requestCode: Int,
            clientId: String?,
            openAddTask: Boolean
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!clientId.isNullOrBlank()) {
                    putExtra(WidgetActions.EXTRA_CLIENT_ID, clientId)
                }
                putExtra(WidgetActions.EXTRA_OPEN_ADD_TASK, openAddTask)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildActionPendingIntent(
            context: Context,
            providerClass: Class<out AppWidgetProvider>,
            action: String,
            appWidgetId: Int,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, providerClass).apply {
                this.action = action
                putExtra(WidgetActions.EXTRA_WIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildSelectClientPendingIntent(
            context: Context,
            providerClass: Class<out AppWidgetProvider>,
            appWidgetId: Int,
            clientId: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, providerClass).apply {
                action = WidgetActions.ACTION_SELECT_CLIENT
                putExtra(WidgetActions.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(WidgetActions.EXTRA_CLIENT_ID, clientId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildToggleTaskPendingIntent(
            context: Context,
            providerClass: Class<out AppWidgetProvider>,
            appWidgetId: Int,
            taskId: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, providerClass).apply {
                action = WidgetActions.ACTION_TOGGLE_TASK
                putExtra(WidgetActions.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(WidgetActions.EXTRA_TASK_ID, taskId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
