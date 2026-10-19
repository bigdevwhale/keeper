package app.keeper

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.keeper.data.DemoSeed
import app.keeper.data.KeeperDatabase
import app.keeper.data.SettingsStore
import app.keeper.notify.Alarms
import app.keeper.notify.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {
    val db = KeeperDatabase.create(context)
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Where a tap from outside wants to go ("sight", "night", "watch") and when it was made. */
    val open = MutableStateFlow<Pair<String, Long>?>(null)

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch {
            block()
            Alarms.reschedule(context)
        }
    }
}

class KeeperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alarms.ensureChannels(this)
        Shortcuts.publish(this)
        // Re-arm on every start: OEM task killers (HyperOS & co.) silently drop alarms.
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(this@KeeperApp, container)
            Alarms.reschedule(this@KeeperApp)
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as KeeperApp).container

/** Resources in the in-app language, for receivers that AppCompat doesn't localise before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

/** The screenshot build (`-Pdemo -Pshots`): moving scenes hold still so every capture looks the same. */
val SHOTS: Boolean get() = BuildConfig.APPLICATION_ID.endsWith(".shots")
