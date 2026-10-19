package app.keeper.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.keeper.MainActivity
import app.keeper.R
import app.keeper.container
import app.keeper.domain.Fleet
import app.keeper.domain.WatchClock
import app.keeper.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The one reminder Keeper gives: the evening watch, and only when there is something to look at,
 * a ship at anchor or a cargo due. A quiet day gets no note at all.
 */
object Alarms {
    private const val CHANNEL = "watch_v1"
    private const val ACTION = "app.keeper.notify.WATCH"
    const val EXTRA_OPEN = "open"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val l = context.localized()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, l.getString(R.string.channel_watch), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = l.getString(R.string.channel_watch_desc)
            }
        )
    }

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** At the next watch. */
    suspend fun reschedule(context: Context) {
        val s = context.container.settings.now()
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        if (!s.onboarded || !s.remind) {
            am.cancel(p)
            return
        }
        // A watch doesn't need to-the-second precision; this avoids the exact-alarm permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, WatchClock.next(s, System.currentTimeMillis()), p)
    }

    suspend fun fire(context: Context) {
        val c = context.container
        val s = c.settings.now()
        val today = LocalDate.now()
        val key = "watch:${today.toEpochDay()}"
        if (!s.remind || key in s.fired.split(',') || WatchClock.state(s, System.currentTimeMillis()) != WatchClock.State.OPEN) return
        val dao = c.db.dao()
        val anchored = dao.sightings().count { it.anchored }
        val due = Fleet.due(dao.cargo(), today).size
        if (anchored == 0 && due == 0) return
        c.settings.update { it.copy(fired = (it.fired.split(',').filter { f -> f.isNotEmpty() }.takeLast(10) + key).joinToString(",")) }
        if (!canNotify(context)) return
        val l = context.localized()
        val bits = buildList {
            if (anchored > 0) add(l.resources.getQuantityString(R.plurals.note_anchored, anchored, anchored))
            if (due > 0) add(l.resources.getQuantityString(R.plurals.note_due, due, due))
        }
        val text = bits.joinToString(" · ")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.lamp))
            .setContentTitle(l.getString(R.string.note_watch_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, "watch", 100))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(10, n)
        } catch (_: SecurityException) {
        }
    }

    fun open(context: Context, open: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal const val WATCH = ACTION
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.WATCH) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.fire(context)
                Alarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** A tile in the quick settings: a ship on the horizon, logged two swipes away from anything. */
class SightTile : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val open = Intent(this, MainActivity::class.java).putExtra(Alarms.EXTRA_OPEN, "sight")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 21, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(open)
        }
    }
}

/** On a long press of the launcher icon: a ship on the horizon, and the foghorn for a sleepless night. */
object Shortcuts {
    fun publish(context: Context) {
        val l = context.localized()
        val list = listOf(
            Triple("sight", R.string.shortcut_sight, R.string.shortcut_sight_long),
            Triple("night", R.string.shortcut_night, R.string.shortcut_night_long),
        ).mapIndexed { i, (open, short, long) ->
            ShortcutInfoCompat.Builder(context, open)
                .setShortLabel(l.getString(short))
                .setLongLabel(l.getString(long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(Alarms.EXTRA_OPEN, open))
                .setRank(i)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}
