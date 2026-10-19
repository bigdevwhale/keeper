package app.keeper.ui.fleet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.domain.Fleet
import app.keeper.domain.ShipLog
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.art.Look
import app.keeper.ui.art.bottle
import app.keeper.ui.components.Empty
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.Tag
import app.keeper.ui.components.UCard
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import kotlin.math.roundToInt

/** Every ship still sailing in, the latest first. */
@Composable
fun FleetTab(h: Harbor, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.fleet_title), style = UType.Display)
        if (h.fleet.isNotEmpty()) Text(T.plural(c, R.plurals.ships, h.fleet.size), style = UType.Log)
        Hint(Hints.FLEET, h.settings, Modifier.padding(top = 6.dp))
        if (h.fleet.isEmpty()) {
            Empty(stringResource(R.string.fleet_empty), stringResource(R.string.fleet_empty_sub)) {
                ShipThumb(7, app.keeper.data.Tonnage.SCHOONER, size = 120.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            h.fleet.forEach { ShipCard(it, h, go) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShipCard(l: ShipLog, h: Harbor, go: Go) {
    val c = ctx()
    val week = l.sightings.count { Fleet.daysSince(it.at, h.today, h.zone) < 7 }
    val quiet = Fleet.daysSince(l.lastSeen, h.today, h.zone)
    UCard(onClick = { go.ship(l.ship.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShipThumb(l.ship.seed, l.tonnage, size = 78.dp, ghost = l.ghost)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(T.ship(c, l.ship.name), style = UType.Name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.fleet_line, T.plural(c, R.plurals.visits_week, week), T.when_(c, Fleet.date(l.lastSeen, h.zone), h.today)),
                    style = UType.Muted,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (h.roadstead.any { it.shipId == l.ship.id }) Tag(stringResource(R.string.fate_anchored), U.c.lamp)
                    if (l.ghost >= 0.1f) Tag(stringResource(R.string.tag_ghost, (l.ghost * 100).roundToInt()), U.c.ghost)
                    if (l.answered.isNotEmpty()) Tag(stringResource(R.string.tag_cargo, l.arrived, l.answered.size), U.c.muted)
                    if (quiet >= 14) Tag(stringResource(R.string.tag_quiet, T.plural(c, R.plurals.days, (Fleet.BOTTLE_DAYS - quiet).toInt().coerceAtLeast(1))), U.c.calm)
                }
            }
        }
    }
}

/** Ships that haven't come for a month, each in its bottle on the keeper's shelf. */
@Composable
fun ShelfTab(h: Harbor, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.shelf_title), style = UType.Display)
        if (h.shelf.isNotEmpty()) Text(T.plural(c, R.plurals.bottles, h.shelf.size), style = UType.Log)
        Hint(Hints.SHELF, h.settings, Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        if (h.shelf.isEmpty()) {
            Shelf(listOf(null, null), go, h)
            Text(stringResource(R.string.shelf_empty), style = UType.Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
        } else {
            h.shelf.chunked(2).forEach { row -> Shelf(row + List(2 - row.size) { null }, go, h) }
        }
        val soon = h.fleet.filter { Fleet.daysSince(it.lastSeen, h.today, h.zone) >= 10 }.sortedBy { it.lastSeen }
        if (soon.isNotEmpty()) {
            SectionTitle(stringResource(R.string.shelf_soon))
            UCard {
                soon.forEach { l ->
                    val quiet = Fleet.daysSince(l.lastSeen, h.today, h.zone).toInt()
                    Row(Modifier.fillMaxWidth().clickable { go.ship(l.ship.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShipThumb(l.ship.seed, l.tonnage, size = 40.dp, ghost = l.ghost)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(T.ship(c, l.ship.name), style = UType.Strong)
                            Text(stringResource(R.string.shelf_soon_line, T.plural(c, R.plurals.days, quiet), T.plural(c, R.plurals.days, (Fleet.BOTTLE_DAYS - quiet).coerceAtLeast(1))), style = UType.Muted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One plank of the shelf with two bottles on it (or empty places). */
@Composable
private fun Shelf(row: List<ShipLog?>, go: Go, h: Harbor) {
    val c = ctx()
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            row.forEach { l ->
                Column(Modifier.weight(1f).clip(U.RSm).then(if (l != null) Modifier.clickable { go.ship(l.ship.id) } else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (l != null) {
                        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                            val w = size.width
                            bottle(Look(l.ship.seed), l.tonnage, l.ghost, Rect(Offset(w * 0.04f, size.height * 0.08f), Size(w * 0.92f, size.height * 0.92f)))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.7f).height(52.dp).padding(bottom = 6.dp).clip(U.RMd).background(U.c.surface.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
        }
        // The plank.
        Box(Modifier.fillMaxWidth().height(12.dp).clip(U.RSm).background(Brush.verticalGradient(listOf(Color(0xFF6E4A30), Color(0xFF3E2716)))))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { l ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (l != null) {
                        // A paper tag tied to the bottle.
                        Column(Modifier.clip(U.RSm).background(U.c.paper).padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(l.ship.name, style = UType.Name.copy(fontSize = 15.sp, color = Color(0xFF2A2118)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.shelf_since, T.day(c, l.bottledAt)), style = UType.Small.copy(color = Color(0xFF6B5A44), fontSize = 10.sp), maxLines = 1)
                            Text(T.plural(c, R.plurals.visits, l.count), style = UType.Small.copy(color = Color(0xFF6B5A44), fontSize = 10.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
