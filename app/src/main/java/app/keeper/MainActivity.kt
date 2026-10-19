package app.keeper

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.keeper.data.Settings
import app.keeper.notify.Alarms
import app.keeper.ui.KeeperRoot
import app.keeper.ui.theme.KeeperTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Night all the way through: light icons in the bars.
        val style = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (savedInstanceState == null) opened(intent)
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            KeeperTheme {
                // Wait for settings so the welcome screens don't flash for someone already here.
                settings?.let { KeeperRoot(it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)
    }

    /** A tap from the shade, a tile or a shortcut: remember where it wants to go, and when. */
    private fun opened(intent: Intent?) {
        val open = intent?.getStringExtra(Alarms.EXTRA_OPEN) ?: return
        container.open.value = open to System.currentTimeMillis()
    }
}
