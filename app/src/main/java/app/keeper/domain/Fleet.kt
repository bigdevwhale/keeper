package app.keeper.domain

import app.keeper.data.Cargo
import app.keeper.data.Fate
import app.keeper.data.Outcome
import app.keeper.data.Port
import app.keeper.data.Settings
import app.keeper.data.Ship
import app.keeper.data.Sighting
import app.keeper.data.Tonnage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.min

/** One ship with everything it ever brought: its sightings (newest first) and its cargo. */
class ShipLog(val ship: Ship, val sightings: List<Sighting>, val cargo: List<Cargo>) {
    val count: Int get() = sightings.size
    val lastSeen: Long get() = sightings.firstOrNull()?.at ?: ship.createdAt
    val firstSeen: Long get() = sightings.lastOrNull()?.at ?: ship.createdAt

    /** Drawn the size it came in last. */
    val tonnage: Tonnage get() = sightings.firstOrNull()?.tonnage ?: Tonnage.SCHOONER

    /** Not seen for [Fleet.BOTTLE_DAYS]: it's gone over the horizon and into a bottle on the shelf. */
    fun bottled(now: Long): Boolean = now - lastSeen >= Fleet.BOTTLE_DAYS * Fleet.DAY

    /** The day it went into the bottle. */
    val bottledAt: Long get() = lastSeen + Fleet.BOTTLE_DAYS * Fleet.DAY

    val answered: List<Cargo> get() = cargo.filter { !it.pending }
    val arrived: Int get() = cargo.count { it.outcomeOf == Outcome.ARRIVED }
    val partly: Int get() = cargo.count { it.outcomeOf == Outcome.PARTLY }
    val empty: Int get() = cargo.count { it.outcomeOf == Outcome.EMPTY }

    /** How much of a ghost ship it has become: 0, solid, to 1, nothing but mist and torn sails. */
    val ghost: Float get() = Fleet.ghost(cargo)
}

/** What everything adds up to, recomputed when anything changes. Pure, so it can be tested. */
object Fleet {
    const val DAY = 86_400_000L
    const val HOUR = 3_600_000L
    const val BOTTLE_DAYS = 30

    /** A ship seen this many times in the past hour is not lit again: it goes on the roadstead. */
    const val REPEAT_LIMIT = 3

    fun logs(ships: List<Ship>, sightings: List<Sighting>, cargo: List<Cargo>): List<ShipLog> {
        val bySight = sightings.sortedByDescending { it.at }.groupBy { it.shipId }
        val byCargo = cargo.groupBy { it.shipId }
        return ships.map { ShipLog(it, bySight[it.id].orEmpty(), byCargo[it.id].orEmpty()) }
    }

    /**
     * Each forecast that came to nothing takes a little more of the ship away. Half a ghost after three
     * empty holds, a full one after six; a cargo that did arrive puts some of it back.
     */
    fun ghost(cargo: List<Cargo>): Float {
        val answered = cargo.filter { !it.pending }
        if (answered.isEmpty()) return 0f
        val empty = answered.count { it.outcomeOf == Outcome.EMPTY } + 0.5f * answered.count { it.outcomeOf == Outcome.PARTLY }
        val share = empty / answered.size
        return (share * min(1f, empty / 6f)).coerceIn(0f, 1f)
    }

    /** How many times [shipId] came in the hour before [now]. */
    fun repeats(sightings: List<Sighting>, shipId: Long, now: Long): Int =
        sightings.count { it.shipId == shipId && it.at in (now - HOUR) until now }

    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The weight a sighting has now: what it was weighed at last. */
    fun weightNow(s: Sighting): Int = if (s.reweighed) s.after else s.weight

    /** Fog over the bay: today's sightings, by how heavy they still are. 0 is a clear night, 1 is a pea-souper. */
    fun fog(sightings: List<Sighting>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Float =
        (sightings.filter { date(it.at, zone) == today }.sumOf { weightNow(it) } / 10f).coerceIn(0f, 1f)

    /** When ships come: [weekday 0..6][three-hour watch 0..7]. */
    fun weather(sightings: List<Sighting>, zone: ZoneId = ZoneId.systemDefault()): Array<IntArray> {
        val grid = Array(7) { IntArray(8) }
        sightings.forEach {
            val t = Instant.ofEpochMilli(it.at).atZone(zone)
            grid[t.dayOfWeek.value - 1][t.hour / 3]++
        }
        return grid
    }

    /** Where ships sail in from, most often first. */
    fun ports(sightings: List<Sighting>): List<Pair<Port, Int>> =
        sightings.mapNotNull { it.portOf }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    /** The average weight a week, the last [n] weeks, oldest first; null for a week with none. */
    fun weeks(sightings: List<Sighting>, today: LocalDate, n: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<Float?> =
        (n - 1 downTo 0).map { back ->
            val to = today.minusDays(7L * back)
            val from = to.minusDays(6)
            sightings.filter { date(it.at, zone).let { d -> !d.isBefore(from) && !d.isAfter(to) } }
                .takeIf { it.isNotEmpty() }?.map { it.weight }?.average()?.toFloat()
        }

    /** Sightings weighed twice: the first weight against the second. */
    class Lighter(val looked: Int, val lighter: Int, val gone: Int, val avgBefore: Float, val avgAfter: Float)

    fun lighter(sightings: List<Sighting>, fate: Fate? = null): Lighter {
        val l = sightings.filter { it.reweighed && (fate == null || it.fateOf == fate) }
        return Lighter(
            looked = l.size,
            lighter = l.count { it.after < it.weight },
            gone = l.count { it.after == 0 },
            avgBefore = l.map { it.weight }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f,
            avgAfter = l.map { it.after }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f,
        )
    }

    /** Every forecast ever checked, across the fleet. */
    class Ledger(val answered: Int, val arrived: Int, val partly: Int, val empty: Int, val coped: Int, val pending: Int)

    fun ledger(cargo: List<Cargo>): Ledger = Ledger(
        answered = cargo.count { !it.pending },
        arrived = cargo.count { it.outcomeOf == Outcome.ARRIVED },
        partly = cargo.count { it.outcomeOf == Outcome.PARTLY },
        empty = cargo.count { it.outcomeOf == Outcome.EMPTY },
        coped = cargo.count { it.outcomeOf != Outcome.PENDING && it.outcomeOf != Outcome.EMPTY && it.coped },
        pending = cargo.count { it.pending },
    )

    /** Cargo whose day has come and that hasn't been checked. */
    fun due(cargo: List<Cargo>, today: LocalDate): List<Cargo> = cargo.filter { it.pending && it.due <= today.toEpochDay() }

    /** Days a ship has been gone: "seen 12 days ago". */
    fun daysSince(ms: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long = ChronoUnit.DAYS.between(date(ms, zone), today)
}

/** The evening watch: once a day, from a set hour, for a set while. The only place to look a ship over. */
object WatchClock {
    enum class State {
        /** Not yet: the ships wait at anchor. */
        BEFORE,

        /** The lamp is lit: the watch can be kept. */
        OPEN,

        /** Kept today; the log is closed until tomorrow. */
        KEPT,
    }

    fun opensAt(day: LocalDate, s: Settings, zone: ZoneId = ZoneId.systemDefault()): Long =
        day.atStartOfDay(zone).plusMinutes(s.watchAt.toLong()).toInstant().toEpochMilli()

    fun state(s: Settings, now: Long, zone: ZoneId = ZoneId.systemDefault()): State {
        val today = Fleet.date(now, zone)
        return when {
            s.lastWatch == today.toEpochDay() -> State.KEPT
            now >= opensAt(today, s, zone) -> State.OPEN
            else -> State.BEFORE
        }
    }

    /** When the next watch opens. */
    fun next(s: Settings, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Fleet.date(now, zone)
        return if (state(s, now, zone) == State.BEFORE) opensAt(today, s, zone) else opensAt(today.plusDays(1), s, zone)
    }
}
