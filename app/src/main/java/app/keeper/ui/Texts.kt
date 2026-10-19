package app.keeper.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.keeper.R
import app.keeper.data.Breath
import app.keeper.data.Outcome
import app.keeper.data.Port
import app.keeper.data.Tonnage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Words, times and dates in the app's language. Plain functions over a [Context] so receivers can use them too. */
object T {
    private fun locale(c: Context): Locale = c.resources.configuration.locales[0]

    fun plural(c: Context, id: Int, n: Int): String = c.resources.getQuantityString(id, n, n)

    fun local(ms: Long, zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(ms).atZone(zone)

    /** "17 Oct 2026". */
    fun date(c: Context, d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(c)))
    fun date(c: Context, ms: Long): String = date(c, local(ms).toLocalDate())

    /** "17 Oct". */
    fun day(c: Context, d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", locale(c)))
    fun day(c: Context, ms: Long): String = day(c, local(ms).toLocalDate())

    /** "Saturday, 17 Oct". */
    fun weekday(c: Context, d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale(c))).replaceFirstChar { it.titlecase(locale(c)) }

    /** "Today", "Yesterday" or "Saturday, 17 Oct". */
    fun dayName(c: Context, d: LocalDate, today: LocalDate): String = when (d) {
        today -> c.getString(R.string.today)
        today.minusDays(1) -> c.getString(R.string.yesterday)
        else -> weekday(c, d)
    }

    fun time(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(c)))

    /** "20:00" from minutes after midnight. */
    fun clock(c: Context, minutes: Int): String =
        java.time.LocalTime.of(minutes / 60 % 24, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(c)))

    /** "12:40" left on the watch. */
    fun timer(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** "Today", "tomorrow", "in 5 days", "3 days ago". */
    fun when_(c: Context, d: LocalDate, today: LocalDate): String {
        val n = java.time.temporal.ChronoUnit.DAYS.between(today, d).toInt()
        return when {
            n == 0 -> c.getString(R.string.today_lower)
            n == 1 -> c.getString(R.string.tomorrow_lower)
            n == -1 -> c.getString(R.string.yesterday_lower)
            n > 1 -> c.getString(R.string.in_days, plural(c, R.plurals.days, n))
            else -> c.getString(R.string.days_ago, plural(c, R.plurals.days, -n))
        }
    }

    /** "«Dismissal»": a ship's name as it's painted on the hull. */
    fun ship(c: Context, name: String): String = c.getString(R.string.ship_quoted, name)

    /** Someone's words, in the language's own quotation marks. */
    fun quote(c: Context, text: String): String = c.getString(R.string.quoted, text)

    fun tonnage(c: Context, t: Tonnage): String = c.getString(
        when (t) {
            Tonnage.BOAT -> R.string.t_boat
            Tonnage.SCHOONER -> R.string.t_schooner
            Tonnage.FRIGATE -> R.string.t_frigate
            Tonnage.GALLEON -> R.string.t_galleon
        }
    )

    fun tonnageSub(c: Context, t: Tonnage): String = c.getString(
        when (t) {
            Tonnage.BOAT -> R.string.t_boat_sub
            Tonnage.SCHOONER -> R.string.t_schooner_sub
            Tonnage.FRIGATE -> R.string.t_frigate_sub
            Tonnage.GALLEON -> R.string.t_galleon_sub
        }
    )

    /** A weight from 0 (gone) to 4. */
    fun weight(c: Context, w: Int): String = if (w <= 0) c.getString(R.string.after_gone) else tonnage(c, Tonnage.of(w))

    fun port(c: Context, p: Port): String = c.getString(
        when (p) {
            Port.MESSAGE -> R.string.port_message
            Port.MEETING -> R.string.port_meeting
            Port.NEWS -> R.string.port_news
            Port.NIGHT -> R.string.port_night
            Port.QUIET -> R.string.port_quiet
            Port.BODY -> R.string.port_body
            Port.MEMORY -> R.string.port_memory
            Port.OTHER -> R.string.port_other
        }
    )

    fun outcome(c: Context, o: Outcome): String = c.getString(
        when (o) {
            Outcome.PENDING -> R.string.out_pending
            Outcome.ARRIVED -> R.string.out_arrived
            Outcome.PARTLY -> R.string.out_partly
            Outcome.EMPTY -> R.string.out_empty
        }
    )

    fun breath(c: Context, b: Breath): String =
        if (b.holdS > 0) "${b.inS} · ${b.holdS} · ${b.outS}" else "${b.inS} · ${b.outS}"

    fun breathSub(c: Context, b: Breath): String = c.getString(
        when (b) {
            Breath.CALM -> R.string.breath_calm
            Breath.SQUARE -> R.string.breath_square
            Breath.SLOW -> R.string.breath_slow
        }
    )

    /** The weather over the bay, from the day's fog. */
    fun weather(c: Context, fog: Float): String = c.getString(
        when {
            fog < 0.08f -> R.string.wx_clear
            fog < 0.3f -> R.string.wx_haze
            fog < 0.65f -> R.string.wx_fog
            else -> R.string.wx_thick
        }
    )

    /**
     * The thought said as a thought: "I'm having the thought that I'll be fired". The first letter is
     * lowered so it reads on, except for "I" in English.
     */
    fun noticing(c: Context, text: String): String {
        val t = text.trim().trimEnd('.', '!')
        val keep = t.startsWith("I ") || t.startsWith("I'") || t.startsWith("I’") || t.length > 1 && t[1].isUpperCase()
        val body = if (keep) t else t.replaceFirstChar { it.lowercase(locale(c)) }
        return c.getString(R.string.noticing, body)
    }
}

@Composable
fun ctx(): Context = LocalContext.current
