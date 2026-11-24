package com.samyak.blackboxmailt

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.samyak.blackboxmailt.data.SettingsStore
import com.samyak.blackboxmailt.data.ThemeMode
import com.samyak.blackboxmailt.notifications.MailNotifier
import com.samyak.blackboxmailt.ui.MailApp
import com.samyak.blackboxmailt.ui.theme.BlackBoxMailTheme

class MainActivity : ComponentActivity() {

    /** Message id handed over by a notification tap, if any. */
    private var pendingMessageId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingMessageId = intent?.getStringExtra(MailNotifier.EXTRA_MESSAGE_ID)

        setContent {
            val settingsStore = remember { SettingsStore.getInstance(applicationContext) }
            val settings by settingsStore.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep the system bar icons readable against whichever theme is active.
            val view = LocalView.current
            SideEffect {
                val window = (view.context as? Activity)?.window ?: this@MainActivity.window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            BlackBoxMailTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    MailApp(
                        openMessageId = pendingMessageId,
                        onOpenMessageHandled = { pendingMessageId = null }
                    )
                }
            }
        }
    }

    /** launchMode is singleTop, so a notification tap on a live activity arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(MailNotifier.EXTRA_MESSAGE_ID)?.let { pendingMessageId = it }
    }
}
