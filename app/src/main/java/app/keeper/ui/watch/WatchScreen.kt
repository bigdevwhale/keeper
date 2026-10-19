package app.keeper.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.container
import app.keeper.data.Cargo
import app.keeper.data.Fate
import app.keeper.data.Sighting
import app.keeper.domain.Fleet
import app.keeper.domain.WatchClock
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.art.Anchored
import app.keeper.ui.art.BayScene
import app.keeper.ui.components.AfterPicker
import app.keeper.ui.components.Bar
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.PrimaryButton
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.SoftButton
import app.keeper.ui.components.TopBar
import app.keeper.ui.components.UCard
import app.keeper.ui.components.rememberNow
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType

/**
 * The evening watch, the one place a ship is looked over: each one at anchor weighed again, a
 * forecast written for it if you like, its chart drawn; every cargo due checked. Then the log closes
 * until tomorrow.
 */
@Composable
fun WatchScreen(h: Harbor, go: Go) {
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.watch_title), go::back)
        when (h.watch) {
            WatchClock.State.BEFORE -> Closed(h, go, kept = false)
            WatchClock.State.KEPT -> Closed(h, go, kept = true)
            WatchClock.State.OPEN -> Open(h, go)
        }
    }
}

@Composable
private fun Open(h: Harbor, go: Go) {
    val c = ctx()
    val container = c.container
    val started = rememberSaveable { System.currentTimeMillis() }
    val now = rememberNow(1000)
    val length = h.settings.watchLen * 60_000L
    val left = length - (now - started)
    var cargoFor by remember { mutableStateOf<Sighting?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // The lamp's oil: the watch's time going down.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (left > 0) stringResource(R.string.watch_left, T.timer(left)) else stringResource(R.string.watch_over), style = UType.Log.copy(color = if (left > 0) U.c.lamp else U.c.muted), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Bar((left.toFloat() / length).coerceIn(0f, 1f))
            Hint(Hints.WATCH, h.settings, Modifier.padding(top = 8.dp))

            SectionTitle(stringResource(R.string.watch_roadstead))
            if (h.roadstead.isEmpty()) {
                Text(stringResource(R.string.watch_roadstead_none), style = UType.Sub, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    h.roadstead.forEach { s ->
                        RoadCard(s, h, go, onCargo = { cargoFor = s }) { w ->
                            container.write { db.dao().update(s.copy(after = w, watchedAt = System.currentTimeMillis())) }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.watch_due))
            if (h.due.isEmpty()) {
                Text(stringResource(R.string.watch_due_none), style = UType.Sub, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { h.due.forEach { CargoCheck(it, h) } }
            }

            val passed = h.todays.filter { it.fateOf == Fate.PASSED }
            if (passed.isNotEmpty()) {
                SectionTitle(stringResource(R.string.watch_passed))
                val l = Fleet.lighter(passed)
                Text(
                    T.plural(c, R.plurals.passed_today, passed.size) + if (l.looked > 0) " " + stringResource(R.string.watch_passed_lighter, l.lighter, l.looked) else "",
                    style = UType.Sub, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            PrimaryButton(stringResource(R.string.watch_finish), {
                container.write {
                    val dao = db.dao()
                    dao.sightings().filter { it.anchored }.forEach { dao.update(it.copy(fate = Fate.WATCHED.name, watchedAt = if (it.watchedAt > 0) it.watchedAt else System.currentTimeMillis())) }
                    settings.update { it.copy(lastWatch = Fleet.date(System.currentTimeMillis()).toEpochDay()) }
                }
            }, Modifier.fillMaxWidth())
        }
    }
    cargoFor?.let { s ->
        CargoDialog(h.ship(s.shipId)?.name.orEmpty(), s.text, { cargoFor = null }) { text, due ->
            container.write { db.dao().insert(Cargo(shipId = s.shipId, text = text, due = due)) }
        }
    }
}

/** A ship at anchor: what it said this morning, how heavy it was, and how heavy now. */
@Composable
private fun RoadCard(s: Sighting, h: Harbor, go: Go, onCargo: () -> Unit, onWeigh: (Int) -> Unit) {
    val c = ctx()
    val log = h.log(s.shipId)
    var after by remember(s.id, s.after) { mutableStateOf(if (s.reweighed) s.after else null) }
    UCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShipThumb(log?.ship?.seed ?: 0, s.tonnage, size = 52.dp, ghost = log?.ghost ?: 0f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(T.ship(c, log?.ship?.name.orEmpty()), style = UType.Name.copy(fontSize = 18.sp))
                Text(stringResource(R.string.road_when, T.dayName(c, Fleet.date(s.at), h.today).lowercase(), T.time(c, s.at), T.tonnage(c, s.tonnage).lowercase()), style = UType.Small)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(T.quote(c, s.text), style = UType.Quote)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.road_now), style = UType.Strong)
        Spacer(Modifier.height(8.dp))
        AfterPicker(after, { after = it; onWeigh(it) })
        val a = after
        if (a != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (a == 0) stringResource(R.string.road_gone) else stringResource(R.string.road_was_now, T.tonnage(c, s.tonnage), T.weight(c, a).lowercase()),
                style = UType.Muted.copy(color = if (a < s.weight) U.c.calm else U.c.muted),
            )
            if (a > 0) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(stringResource(R.string.road_cargo), onCargo, Modifier.weight(1f), icon = Icons.Rounded.Inventory2)
                    SoftButton(stringResource(R.string.road_chart), { go.chart(s.shipId) }, Modifier.weight(1f), icon = Icons.Rounded.Map)
                }
            }
        }
    }
}

/** Before the watch, or after it: the lamp unlit, or the log closed. */
@Composable
private fun Closed(h: Harbor, go: Go, kept: Boolean) {
    val c = ctx()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            BayScene(h.fog, h.roadstead.map { Anchored(it.id, h.ship(it.shipId)?.seed ?: 0, it.tonnage) }, Modifier.fillMaxSize(), lit = kept)
        }
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (kept) {
                Text(stringResource(R.string.watch_kept), style = UType.Title, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                val watched = h.sightings.filter { it.fateOf == Fate.WATCHED && Fleet.date(it.watchedAt) == h.today }
                val l = Fleet.lighter(watched)
                val text = if (l.looked > 0) stringResource(R.string.watch_kept_summary, l.looked, l.lighter, l.gone) else stringResource(R.string.watch_kept_sub)
                Text(text, style = UType.Sub, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.watch_next, T.clock(c, h.settings.watchAt)), style = UType.Log, textAlign = TextAlign.Center)
            } else {
                Text(stringResource(R.string.watch_before, T.clock(c, h.settings.watchAt)), style = UType.Title, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.watch_before_sub), style = UType.Sub, textAlign = TextAlign.Center)
                if (h.roadstead.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(T.plural(c, R.plurals.at_anchor, h.roadstead.size), style = UType.Log.copy(color = U.c.lamp))
                }
            }
            Spacer(Modifier.height(24.dp))
            SoftButton(stringResource(R.string.to_shore), go::home, Modifier.fillMaxWidth())
        }
    }
}
