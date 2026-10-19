package app.keeper

import app.keeper.data.Backup
import app.keeper.data.Breath
import app.keeper.data.Cargo
import app.keeper.data.Everything
import app.keeper.data.Fate
import app.keeper.data.Outcome
import app.keeper.data.Port
import app.keeper.data.Settings
import app.keeper.data.Ship
import app.keeper.data.Sighting
import app.keeper.domain.Breathing
import app.keeper.domain.Fleet
import app.keeper.domain.WatchClock
import app.keeper.ui.sight.likely
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeeperTest {
    private val utc = ZoneOffset.UTC
    private val day = Fleet.DAY
    private val today = LocalDate.of(2026, 10, 19)
    private val noon = today.atTime(12, 0).toInstant(utc).toEpochMilli()

    private fun cargo(o: Outcome, id: Long = 0) = Cargo(id = id, shipId = 1, text = "x", due = today.toEpochDay(), outcome = o.name)

    @Test
    fun ghostGrowsWithEmptyHolds() {
        assertEquals(0f, Fleet.ghost(emptyList()), 0f)
        assertEquals(0f, Fleet.ghost(listOf(cargo(Outcome.PENDING))), 0f)
        val three = Fleet.ghost(List(3) { cargo(Outcome.EMPTY) })
        val six = Fleet.ghost(List(6) { cargo(Outcome.EMPTY) })
        assertEquals(0.5f, three, 0.001f)
        assertEquals(1f, six, 0.001f)
        // A cargo that did arrive puts some of the ship back.
        assertTrue(Fleet.ghost(List(6) { cargo(Outcome.EMPTY) } + cargo(Outcome.ARRIVED)) < six)
        assertEquals(0f, Fleet.ghost(List(4) { cargo(Outcome.ARRIVED) }), 0f)
    }

    @Test
    fun bottledAfterAQuietMonth() {
        val ship = Ship(id = 1, name = "The Sack", seed = 1, createdAt = noon - 60 * day)
        val log = Fleet.logs(listOf(ship), listOf(Sighting(shipId = 1, at = noon - 31 * day, text = "a", weight = 2)), emptyList()).single()
        assertTrue(log.bottled(noon))
        assertFalse(log.bottled(noon - 2 * day))
        assertEquals(noon - day, log.bottledAt)
    }

    @Test
    fun repeatsCountTheLastHourOnly() {
        val s = listOf(
            Sighting(shipId = 1, at = noon - 10 * 60_000, text = "", weight = 2),
            Sighting(shipId = 1, at = noon - 50 * 60_000, text = "", weight = 2),
            Sighting(shipId = 1, at = noon - 70 * 60_000, text = "", weight = 2),
            Sighting(shipId = 2, at = noon - 5 * 60_000, text = "", weight = 2),
        )
        assertEquals(2, Fleet.repeats(s, 1, noon))
        assertEquals(1, Fleet.repeats(s, 2, noon))
    }

    @Test
    fun fogUsesTheLatestWeight() {
        val s = listOf(
            Sighting(shipId = 1, at = noon, text = "", weight = 4, after = 1),
            Sighting(shipId = 1, at = noon + 1000, text = "", weight = 3),
            Sighting(shipId = 1, at = noon - day, text = "", weight = 4),
        )
        assertEquals(0.4f, Fleet.fog(s, today, utc), 0.001f)
    }

    @Test
    fun lighterCountsReweighed() {
        val s = listOf(
            Sighting(shipId = 1, at = noon, text = "", weight = 4, after = 2, fate = Fate.PASSED.name),
            Sighting(shipId = 1, at = noon, text = "", weight = 2, after = 0, fate = Fate.PASSED.name),
            Sighting(shipId = 1, at = noon, text = "", weight = 2, after = 2, fate = Fate.PASSED.name),
            Sighting(shipId = 1, at = noon, text = "", weight = 3, fate = Fate.PASSED.name),
            Sighting(shipId = 1, at = noon, text = "", weight = 3, after = 1, fate = Fate.WATCHED.name),
        )
        val l = Fleet.lighter(s, Fate.PASSED)
        assertEquals(3, l.looked)
        assertEquals(2, l.lighter)
        assertEquals(1, l.gone)
        assertEquals(1, Fleet.lighter(s, Fate.WATCHED).looked)
    }

    @Test
    fun ledgerAndDue() {
        val c = listOf(
            cargo(Outcome.EMPTY), cargo(Outcome.EMPTY), cargo(Outcome.ARRIVED).copy(coped = true), cargo(Outcome.PARTLY),
            cargo(Outcome.PENDING).copy(due = today.toEpochDay() - 1), cargo(Outcome.PENDING).copy(due = today.toEpochDay() + 3),
        )
        val l = Fleet.ledger(c)
        assertEquals(4, l.answered)
        assertEquals(1, l.arrived)
        assertEquals(2, l.empty)
        assertEquals(1, l.coped)
        assertEquals(2, l.pending)
        assertEquals(1, Fleet.due(c, today).size)
    }

    @Test
    fun watchOpensAtItsHourAndClosesWhenKept() {
        val s = Settings(onboarded = true, watchAt = 20 * 60)
        val before = today.atTime(19, 59).toInstant(utc).toEpochMilli()
        val after = today.atTime(20, 1).toInstant(utc).toEpochMilli()
        assertEquals(WatchClock.State.BEFORE, WatchClock.state(s, before, utc))
        assertEquals(WatchClock.State.OPEN, WatchClock.state(s, after, utc))
        assertEquals(WatchClock.State.KEPT, WatchClock.state(s.copy(lastWatch = today.toEpochDay()), after, utc))
        assertEquals(today.atTime(20, 0).toInstant(utc).toEpochMilli(), WatchClock.next(s, before, utc))
        assertEquals(today.plusDays(1).atTime(20, 0).toInstant(utc).toEpochMilli(), WatchClock.next(s, after, utc))
    }

    @Test
    fun beamKeepsTimeWithTheBreath() {
        val b = Breath.CALM
        val start = Breathing.at(b, 0)
        assertEquals(Breathing.Phase.IN, start.phase)
        assertEquals(0f, start.turn, 0.001f)
        // At the top of the in-breath the beam faces you.
        assertEquals(0.25f, Breathing.at(b, 3999).turn, 0.01f)
        val out = Breathing.at(b, 7000)
        assertEquals(Breathing.Phase.OUT, out.phase)
        assertEquals(0, out.round)
        assertEquals(1, Breathing.at(b, 10_000).round)
        assertEquals(Breathing.Phase.HOLD, Breathing.at(Breath.SQUARE, 5000).phase)
        assertEquals(6, Breathing.rounds(Breath.CALM))
        assertEquals(4, Breathing.rounds(Breath.SLOW))
    }

    @Test
    fun likelyShipBySharedWords() {
        val ships = Fleet.logs(
            listOf(Ship(id = 1, name = "Увольнение", seed = 1), Ship(id = 2, name = "Сердце", seed = 2)),
            listOf(Sighting(shipId = 1, at = noon, text = "Меня уволят после ревью", weight = 2), Sighting(shipId = 2, at = noon, text = "Колет в груди", weight = 2)),
            emptyList(),
        )
        assertEquals(1L, likely("Точно уволят в пятницу", ships)?.ship?.id)
        assertEquals(2L, likely("опять колет сердце", ships)?.ship?.id)
        assertNull(likely("дождь", ships))
    }

    @Test
    fun backupRoundTrips() {
        val all = Everything(
            listOf(Ship(id = 3, name = "The Stove", seed = 77, shoals = "a\nb", lifeboat = "plan", createdAt = 5)),
            listOf(Sighting(id = 9, shipId = 3, at = noon, text = "I left it on", weight = 3, port = Port.QUIET.name, fate = Fate.ANCHORED.name, after = 1, watchedAt = 7)),
            listOf(Cargo(id = 4, shipId = 3, text = "fire", due = 100, outcome = Outcome.EMPTY.name, coped = false, answeredAt = 8, createdAt = 6)),
        )
        val back = Backup.decode(Backup.encode(all))
        assertEquals(all.ships, back.ships)
        assertEquals(all.sightings, back.sightings)
        assertEquals(all.cargo, back.cargo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsOtherFiles() {
        Backup.decode("{\"app\":\"podium\"}")
    }
}
