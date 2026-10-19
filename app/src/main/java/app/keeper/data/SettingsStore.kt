package app.keeper.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** The beam's rhythm: seconds in and out, the lamp turning toward you on the in-breath. */
enum class Breath(val inS: Int, val holdS: Int, val outS: Int) {
    CALM(4, 0, 6),
    SQUARE(4, 4, 4),
    SLOW(4, 7, 8);

    val cycleMs: Long get() = (inS + holdS + outS) * 1000L
}

data class Settings(
    val onboarded: Boolean = false,
    /** When the evening watch starts, minutes after midnight. */
    val watchAt: Int = 20 * 60,
    /** How long the watch lasts, minutes. */
    val watchLen: Int = 15,
    val breath: Breath = Breath.CALM,
    /** A soft buzz as the beam comes round. */
    val haptics: Boolean = true,
    /** A note at watch time, only when there's something on the roadstead or a cargo due. */
    val remind: Boolean = true,
    /** The epoch day of the last watch kept. */
    val lastWatch: Long = 0,
    /** First-visit placards already put away. */
    val hints: Set<String> = emptySet(),
    /** Reminders already given ("watch:20380"), so none comes twice. */
    val fired: String = "",
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        watchAt = this[WATCH_AT] ?: (20 * 60),
        watchLen = this[WATCH_LEN] ?: 15,
        breath = runCatching { Breath.valueOf(this[BREATH] ?: "") }.getOrDefault(Breath.CALM),
        haptics = this[HAPTICS] ?: true,
        remind = this[REMIND] ?: true,
        lastWatch = this[LAST_WATCH] ?: 0L,
        hints = (this[HINTS] ?: "").split(',').filter { it.isNotEmpty() }.toSet(),
        fired = this[FIRED] ?: "",
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[WATCH_AT] = s.watchAt
            it[WATCH_LEN] = s.watchLen
            it[BREATH] = s.breath.name
            it[HAPTICS] = s.haptics
            it[REMIND] = s.remind
            it[LAST_WATCH] = s.lastWatch
            it[HINTS] = s.hints.joinToString(",")
            it[FIRED] = s.fired
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val WATCH_AT = intPreferencesKey("watch_at")
        val WATCH_LEN = intPreferencesKey("watch_len")
        val BREATH = stringPreferencesKey("breath")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REMIND = booleanPreferencesKey("remind")
        val LAST_WATCH = longPreferencesKey("last_watch")
        val HINTS = stringPreferencesKey("hints")
        val FIRED = stringPreferencesKey("fired")
    }
}
