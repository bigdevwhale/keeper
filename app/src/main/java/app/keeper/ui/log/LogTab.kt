package app.keeper.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.keeper.R
import app.keeper.data.Fate
import app.keeper.data.Sighting
import app.keeper.domain.Fleet
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.Stat
import app.keeper.ui.components.UCard
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import app.keeper.ui.watch.CargoCheck
import app.keeper.ui.watch.CargoRow
import app.keeper.ui.watch.CrateMark
import kotlin.math.roundToInt

/** The ship's log: every forecast and what came of it, what the beam and the wait did, and every entry by day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogTab(h: Harbor, go: Go) {
    val c = ctx()
    val l = h.ledger
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.log_title), style = UType.Display)
        Hint(Hints.LOG, h.settings, Modifier.padding(top = 6.dp))

        SectionTitle(stringResource(R.string.log_ledger))
        UCard {
            if (l.answered == 0) {
                Text(stringResource(R.string.log_ledger_none), style = UType.Sub)
            } else {
                Text(stringResource(R.string.log_ledger_line, T.plural(c, R.plurals.cargoes, l.answered), l.arrived), style = UType.Title)
                Spacer(Modifier.height(6.dp))
                val bits = buildList {
                    if (l.partly > 0) add(stringResource(R.string.log_partly, l.partly))
                    add(stringResource(R.string.log_empty, l.empty))
                    if (l.arrived + l.partly > 0) add(stringResource(R.string.log_coped, l.coped, l.arrived + l.partly))
                }
                Text(bits.joinToString(" · "), style = UType.Muted)
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    h.cargo.filter { !it.pending }.sortedBy { it.answeredAt }.takeLast(120).forEach { CrateMark(it.outcomeOf, size = 15.dp) }
                }
            }
        }

        if (h.due.isNotEmpty()) {
            SectionTitle(stringResource(R.string.watch_due))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { h.due.forEach { CargoCheck(it, h) } }
        }

        val atSea = h.cargo.filter { it.pending && it.due > h.today.toEpochDay() }
        if (atSea.isNotEmpty()) {
            SectionTitle(stringResource(R.string.log_at_sea))
            UCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { atSea.forEach { CargoRow(it, h) } }
        }

        val beam = Fleet.lighter(h.sightings, Fate.PASSED)
        val wait = Fleet.lighter(h.sightings, Fate.WATCHED)
        if (beam.looked + wait.looked > 0) {
            SectionTitle(stringResource(R.string.log_weights))
            UCard {
                Row {
                    if (beam.looked > 0) Stat(
                        stringResource(R.string.log_after_beam), "${(100f * beam.lighter / beam.looked).roundToInt()}%", Modifier.weight(1f),
                        color = U.c.calm, sub = stringResource(R.string.log_lighter_of, beam.lighter, beam.looked),
                    )
                    if (wait.looked > 0) Stat(
                        stringResource(R.string.log_by_watch), "%.1f → %.1f".format(wait.avgBefore, wait.avgAfter), Modifier.weight(1f),
                        color = U.c.lamp, sub = stringResource(R.string.log_gone_of, wait.gone, wait.looked),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.log_weights_note), style = UType.Small)
            }
        }

        val recent = h.sightings.filter { Fleet.daysSince(it.at, h.today, h.zone) < 14 }
        if (recent.isNotEmpty()) {
            SectionTitle(stringResource(R.string.log_entries))
            UCard(padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                recent.groupBy { Fleet.date(it.at, h.zone) }.entries.forEachIndexed { i, (day, list) ->
                    if (i > 0) HorizontalDivider(color = U.c.line, modifier = Modifier.padding(vertical = 6.dp))
                    Text(T.dayName(c, day, h.today).uppercase(), style = UType.Eyebrow, modifier = Modifier.padding(vertical = 4.dp))
                    list.forEach { Entry(it, h, go) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One line of the log, as the keeper writes it: the time, the ship, its size, what was done. */
@Composable
private fun Entry(s: Sighting, h: Harbor, go: Go) {
    val c = ctx()
    val fate = when (s.fateOf) {
        Fate.PASSED -> stringResource(R.string.fate_passed)
        Fate.ANCHORED -> stringResource(R.string.fate_anchored)
        Fate.WATCHED -> stringResource(R.string.fate_watched)
    }
    val after = if (s.reweighed) " → " + T.weight(c, s.after).lowercase() else ""
    Row(Modifier.fillMaxWidth().clickable { go.ship(s.shipId) }.padding(vertical = 4.dp)) {
        Text(T.time(c, s.at), style = UType.Log.copy(color = U.c.faint), modifier = Modifier.width(54.dp))
        Column(Modifier.weight(1f)) {
            Text(T.ship(c, h.ship(s.shipId)?.name.orEmpty()) + " · " + T.tonnage(c, s.tonnage).lowercase(), style = UType.Log.copy(color = U.c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$fate$after".lowercase(), style = UType.Log)
        }
    }
}
