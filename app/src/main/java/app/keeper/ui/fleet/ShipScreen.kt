package app.keeper.ui.fleet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.container
import app.keeper.data.Cargo
import app.keeper.data.Fate
import app.keeper.data.Tonnage
import app.keeper.domain.Fleet
import app.keeper.domain.ShipLog
import app.keeper.domain.WatchClock
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.art.Look
import app.keeper.ui.art.Night
import app.keeper.ui.art.bottle
import app.keeper.ui.art.fogBanks
import app.keeper.ui.art.rememberSeconds
import app.keeper.ui.art.ship
import app.keeper.ui.art.skyAndSea
import app.keeper.ui.bay.SightingRow
import app.keeper.ui.components.ConfirmDialog
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.SoftButton
import app.keeper.ui.components.Stat
import app.keeper.ui.components.TopBar
import app.keeper.ui.components.UCard
import app.keeper.ui.components.UTextField
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import app.keeper.ui.watch.CargoDialog
import app.keeper.ui.watch.CargoRow
import kotlin.math.roundToInt
import kotlin.math.sin

/** One ship's page in the log: its portrait, when and from where it comes, its cargo record and its chart. */
@Composable
fun ShipScreen(id: Long, h: Harbor, go: Go) {
    val c = ctx()
    val container = c.container
    val l = h.log(id) ?: return
    val bottled = l.bottled(h.now) && l.count > 0
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var cargo by remember { mutableStateOf(false) }
    val open = h.watch == WatchClock.State.OPEN

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(T.ship(c, l.ship.name), go::back) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = U.c.muted) }
            DropdownMenu(menu, { menu = false }, containerColor = U.c.surface2) {
                DropdownMenuItem({ Text(stringResource(R.string.ship_rename)) }, { menu = false; renaming = true })
                if (h.ships.size > 1) DropdownMenuItem({ Text(stringResource(R.string.ship_merge)) }, { menu = false; merging = true })
                DropdownMenuItem({ Text(stringResource(R.string.delete), color = U.c.rust) }, { menu = false; deleting = true })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Portrait(l, bottled)
            Spacer(Modifier.height(12.dp))
            Text(
                if (bottled) stringResource(R.string.ship_bottled, T.date(c, l.bottledAt))
                else stringResource(R.string.ship_seen, T.date(c, l.firstSeen), T.when_(c, Fleet.date(l.lastSeen, h.zone), h.today)),
                style = UType.Log,
            )
            Hint(Hints.SHIP, h.settings, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            UCard {
                Row {
                    Stat(stringResource(R.string.ship_visits), "${l.count}", Modifier.weight(1f))
                    val passed = l.sightings.count { it.fateOf == Fate.PASSED }
                    Stat(stringResource(R.string.ship_lit), if (l.count > 0) "${(100f * passed / l.count).roundToInt()}%" else "—", Modifier.weight(1f), color = U.c.calm)
                    Stat(
                        stringResource(R.string.ship_cargo), if (l.answered.isEmpty()) "—" else "${l.arrived}/${l.answered.size}", Modifier.weight(1f),
                        color = if (l.ghost > 0.1f) U.c.ghost else U.c.ink,
                        sub = if (l.ghost > 0.1f) stringResource(R.string.tag_ghost, (l.ghost * 100).roundToInt()) else null,
                    )
                }
            }
            if (!bottled) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(stringResource(R.string.ship_again), { go.sight(l.ship.id) }, Modifier.weight(1f))
                    if (open) SoftButton(stringResource(R.string.road_cargo), { cargo = true }, Modifier.weight(1f), icon = Icons.Rounded.Inventory2)
                }
            }

            SectionTitle(stringResource(R.string.ship_cargo_log))
            if (l.cargo.isEmpty()) {
                Text(stringResource(if (open) R.string.ship_cargo_none_open else R.string.ship_cargo_none), style = UType.Sub, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                UCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    l.cargo.sortedByDescending { it.due }.forEach { CargoRow(it, h, showShip = false) }
                }
            }

            SectionTitle(stringResource(R.string.ship_chart))
            UCard(onClick = { go.chart(l.ship.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Map, null, tint = U.c.lamp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (l.ship.shoalList.isEmpty()) stringResource(R.string.chart_none) else T.plural(c, R.plurals.shoals, l.ship.shoalList.size),
                            style = UType.Strong,
                        )
                        Text(
                            l.ship.lifeboat.ifBlank { stringResource(R.string.chart_no_boat) },
                            style = UType.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (l.count > 0) {
                SectionTitle(stringResource(R.string.ship_weather))
                UCard { Weather(Fleet.weather(l.sightings, h.zone)) }

                val ports = Fleet.ports(l.sightings)
                if (ports.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.ship_ports))
                    UCard {
                        val max = ports.first().second
                        ports.forEach { (p, n) ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(T.port(c, p), style = UType.Body, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth(n / max.toFloat()).height(10.dp).clip(U.RSm).background(U.c.fog.copy(alpha = 0.6f)))
                                }
                                Text("$n", style = UType.Log, modifier = Modifier.width(36.dp).padding(start = 8.dp))
                            }
                        }
                    }
                }

                SectionTitle(stringResource(R.string.ship_weeks))
                UCard { Weeks(Fleet.weeks(l.sightings, h.today, 8, h.zone), l.ship.seed) }

                SectionTitle(stringResource(R.string.ship_sightings))
                UCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    l.sightings.take(12).forEach { s ->
                        Column {
                            Text(T.dayName(c, Fleet.date(s.at, h.zone), h.today) + " · " + T.time(c, s.at), style = UType.Small, modifier = Modifier.padding(top = 8.dp))
                            SightingRow(s, h, go, showTime = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(l.ship.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = U.c.surface,
            title = { Text(stringResource(R.string.ship_rename), style = UType.Name) },
            text = { UTextField(name, { name = it }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) container.write { db.dao().update(l.ship.copy(name = name.trim())) }
                    renaming = false
                }) { Text(stringResource(R.string.save), color = U.c.lamp) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
        )
    }
    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            containerColor = U.c.surface,
            title = { Text(stringResource(R.string.ship_merge), style = UType.Name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.ship_merge_sub, T.ship(c, l.ship.name)), style = UType.Muted)
                    Spacer(Modifier.height(8.dp))
                    h.logs.filter { it.ship.id != l.ship.id }.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clip(U.RSm).clickable {
                                merging = false
                                container.write { db.dao().merge(l.ship.id, o.ship.id) }
                                go.back()
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShipThumb(o.ship.seed, o.tonnage, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(T.ship(c, o.ship.name), style = UType.Strong)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
        )
    }
    if (deleting) {
        ConfirmDialog(
            stringResource(R.string.ship_delete_title, T.ship(c, l.ship.name)), stringResource(R.string.ship_delete_text), stringResource(R.string.delete),
            {
                container.write { db.dao().deleteShip(l.ship.id) }
                go.back()
            },
            { deleting = false }, danger = true,
        )
    }
    if (cargo) {
        CargoDialog(l.ship.name, l.sightings.firstOrNull()?.text.orEmpty(), { cargo = false }) { text, due ->
            container.write { db.dao().insert(Cargo(shipId = l.ship.id, text = text, due = due)) }
        }
    }
}

/** The ship on its own stretch of night sea, or in its bottle. */
@Composable
private fun Portrait(l: ShipLog, bottled: Boolean) {
    val t = rememberSeconds()
    Canvas(Modifier.fillMaxWidth().height(230.dp).clip(U.RLg)) {
        if (bottled) {
            drawRect(Brush.verticalGradient(listOf(Night.SkyLow, Night.SkyTop)))
            drawRoundRect(androidx.compose.ui.graphics.Color(0xFF4A3020), Offset(0f, size.height * 0.86f), Size(size.width, size.height * 0.14f), CornerRadius(4f))
            bottle(Look(l.ship.seed), l.tonnage, l.ghost, androidx.compose.ui.geometry.Rect(Offset(size.width * 0.12f, size.height * 0.12f), Size(size.width * 0.76f, size.height * 0.76f)))
        } else {
            skyAndSea(0.55f, 0.2f + 0.4f * l.ghost, t, seed = l.ship.seed)
            fogBanks(0.3f + 0.6f * l.ghost, t, 0.4f, 0.7f)
            val h = when (l.tonnage) { Tonnage.BOAT -> 0.7f; Tonnage.SCHOONER -> 0.9f; Tonnage.FRIGATE -> 1.02f; Tonnage.GALLEON -> 1.12f }
            val len = (size.width * 0.62f).coerceAtMost(size.height * 0.62f / h)
            val y = size.height * 0.8f + sin(t * 1.2f) * 3f
            ship(Look(l.ship.seed), l.tonnage, Offset(size.width * 0.5f, y), len, l.ghost, roll = sin(t * 0.8f) * 1.8f)
            drawRect(Night.SeaTop.copy(alpha = 0.9f), Offset(0f, y), Size(size.width, len * 0.11f))
        }
    }
}

/** When it comes: days down, three-hour watches across, darker for more. */
@Composable
private fun Weather(grid: Array<IntArray>) {
    val c = ctx()
    val max = grid.maxOf { r -> r.max() }.coerceAtLeast(1)
    val days = java.time.DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.SHORT, c.resources.configuration.locales[0]) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.forEachIndexed { d, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(days[d], style = UType.Small, modifier = Modifier.width(34.dp))
                row.forEach { n ->
                    Box(
                        Modifier.weight(1f).padding(horizontal = 2.dp).height(18.dp).clip(U.RSm)
                            .background(if (n == 0) U.c.surface2 else U.c.fog.copy(alpha = 0.18f + 0.72f * n / max)),
                    )
                }
            }
        }
        Row {
            Spacer(Modifier.width(34.dp))
            listOf("0", "6", "12", "18").forEach { Text(it, style = UType.Small.copy(fontSize = 10.sp), modifier = Modifier.weight(2f)) }
        }
    }
}

/** The average size it came in, week by week: is it getting smaller? */
@Composable
private fun Weeks(weeks: List<Float?>, seed: Int) {
    val c = ctx()
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEach { w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (w != null) {
                    val t = Tonnage.of(w.roundToInt())
                    ShipThumb(seed, t, size = 30.dp, sea = false)
                    Box(Modifier.fillMaxWidth().height((14 + 18 * w).dp).clip(U.RSm).background(U.c.lamp.copy(alpha = 0.25f + 0.15f * w)))
                } else {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(U.RSm).background(U.c.surface2))
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.ship_weeks_note, T.tonnage(c, Tonnage.BOAT).lowercase(), T.tonnage(c, Tonnage.GALLEON).lowercase()), style = UType.Small)
}
