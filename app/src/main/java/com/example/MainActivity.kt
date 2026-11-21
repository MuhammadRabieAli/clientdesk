package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.configureAppCheck
import com.example.ai.refreshDebugAppCheckTokenIfPresent
import com.example.data.ThemeMode
import com.example.data.WidgetSnapshotStore
import com.example.ui.ClientDeskRoot
import com.example.ui.WidgetLaunchRequest
import com.example.ui.theme.ClientDeskTheme
import com.example.widget.WidgetActions
import com.example.widget.WidgetRefresher

class MainActivity : ComponentActivity() {

    private var pendingWidgetLaunch by mutableStateOf<WidgetLaunchRequest?>(null)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        themeMode = WidgetSnapshotStore.getThemeMode(this)
        enableEdgeToEdge()
        enableHighRefreshRate()
        consumeWidgetIntent(intent)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            ClientDeskTheme(darkTheme = isDark) {
                ClientDeskRoot(
                    themeMode = themeMode,
                    isDarkTheme = isDark,
                    onThemeModeChange = { nextMode ->
                        themeMode = nextMode
                        WidgetSnapshotStore.setThemeMode(this, nextMode)
                        WidgetRefresher.refreshAllWidgets(this)
                    },
                    launchRequest = pendingWidgetLaunch,
                    onLaunchRequestConsumed = { pendingWidgetLaunch = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshDebugAppCheckTokenIfPresent(this, intent)
        consumeWidgetIntent(intent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        enableHighRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableHighRefreshRate()
        }
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.isFrameRatePowerSavingsBalanced = false
        }
        val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val activeMode = currentDisplay?.mode ?: return
        val bestMode = currentDisplay.supportedModes
            .filter {
                it.physicalWidth == activeMode.physicalWidth &&
                    it.physicalHeight == activeMode.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return

        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != bestMode.modeId ||
            attrs.preferredRefreshRate != bestMode.refreshRate
        ) {
            attrs.preferredDisplayModeId = bestMode.modeId
            attrs.preferredRefreshRate = bestMode.refreshRate
            window.attributes = attrs
        }
    }

    private fun consumeWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val clientId = intent.getStringExtra(WidgetActions.EXTRA_CLIENT_ID)
        val openAddTask = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_ADD_TASK, false)
        if (!clientId.isNullOrBlank() || openAddTask) {
            pendingWidgetLaunch = WidgetLaunchRequest(
                clientId = clientId,
                openAddTask = openAddTask,
                nonce = System.nanoTime()
            )
            intent.removeExtra(WidgetActions.EXTRA_CLIENT_ID)
            intent.removeExtra(WidgetActions.EXTRA_OPEN_ADD_TASK)
        }
    }
}
