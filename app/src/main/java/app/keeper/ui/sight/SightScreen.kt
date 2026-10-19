package app.keeper.ui.sight

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.container
import app.keeper.data.Fate
import app.keeper.data.Port
import app.keeper.data.Ship
import app.keeper.data.Sighting
import app.keeper.data.Tonnage
import app.keeper.domain.Fleet
import app.keeper.domain.ShipLog
import app.keeper.domain.WatchClock
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.components.Chip
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.PrimaryButton
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.SoftButton
import app.keeper.ui.components.TonnagePicker
import app.keeper.ui.components.TopBar
import app.keeper.ui.components.UTextField
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** No ship picked yet. */
private const val NONE = -1L

/** A new ship, to be named here. */
private const val NEW = 0L

/** A ship on the horizon: the thought in a line, which ship it is, how heavy, where from. Then light it, or anchor it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SightScreen(preset: Long, h: Harbor, go: Go) {
    val c = ctx()
    val container = c.container
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var pick by rememberSaveable { mutableLongStateOf(if (preset > 0) preset else if (h.ships.isEmpty()) NEW else NONE) }
    var name by rememberSaveable { mutableStateOf("") }
    var tonnage by rememberSaveable { mutableStateOf<Tonnage?>(null) }
    var port by rememberSaveable { mutableStateOf<Port?>(null) }
    val newSeed = rememberSaveable { Random.nextInt() }
    var saving by remember { mutableStateOf(false) }

    // The latest first, bottled ones too: a ship can come back from the shelf.
    val ships = remember(h.logs) { h.logs.sortedByDescending { it.lastSeen } }
    val guess = remember(text, ships) { if (text.length >= 4) likely(text, ships) else null }
    val chosen = h.log(pick)
    val repeats = if (pick > 0) Fleet.repeats(h.sightings, pick, h.now) else 0
    val crowded = repeats >= Fleet.REPEAT_LIMIT
    val ready = text.isNotBlank() && tonnage != null && (pick > 0 || pick == NEW && name.isNotBlank()) && !saving

    fun save(fate: Fate) {
        saving = true
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                val dao = container.db.dao()
                val shipId = if (pick == NEW) dao.insert(Ship(name = name.trim().trim('«', '»', '"'), seed = newSeed)) else pick
                dao.insert(Sighting(shipId = shipId, at = System.currentTimeMillis(), text = text.trim(), weight = tonnage!!.weight, port = port?.name.orEmpty(), fate = fate.name))
            }
            container.write { }
            if (fate == Fate.PASSED) go.pass(id) else {
                val at = if (h.watch == WatchClock.State.OPEN) c.getString(R.string.anchored_now) else c.getString(R.string.anchored_toast, T.clock(c, h.settings.watchAt))
                Toast.makeText(c, at, Toast.LENGTH_LONG).show()
                go.home()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(stringResource(R.string.sight_title), go::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Hint(Hints.SIGHT, h.settings)
            SectionTitle(stringResource(R.string.sight_what))
            UTextField(text, { text = it }, placeholder = stringResource(R.string.sight_what_hint), minLines = 2)

            SectionTitle(stringResource(R.string.sight_which))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShipChoice(null, pick == NEW) { pick = NEW }
                // A likely match comes first.
                (listOfNotNull(guess) + ships.filter { it != guess }).forEach { l ->
                    ShipChoice(l, pick == l.ship.id, likely = l == guess) { pick = l.ship.id }
                }
            }
            if (pick == NEW) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.sight_name), style = UType.Strong)
                Text(stringResource(R.string.sight_name_sub), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                UTextField(name, { name = it }, placeholder = stringResource(R.string.sight_name_hint))
            } else if (chosen != null && chosen.bottled(h.now)) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sight_from_bottle, T.ship(c, chosen.ship.name)), style = UType.Muted)
            }

            SectionTitle(stringResource(R.string.sight_tonnage))
            TonnagePicker(tonnage, chosen?.ship?.seed ?: newSeed, { tonnage = it })

            SectionTitle(stringResource(R.string.sight_port))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Port.entries.forEach { p -> Chip(T.port(c, p), port == p, { port = if (port == p) null else p }) }
            }

            if (crowded && chosen != null) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.lampSoft).border(1.dp, U.c.lamp.copy(alpha = 0.4f), U.RMd).padding(14.dp)) {
                    Text(stringResource(R.string.crowded_title, T.ship(c, chosen.ship.name), T.plural(c, R.plurals.times, repeats)), style = UType.Strong)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (h.watch == WatchClock.State.OPEN) stringResource(R.string.crowded_text_open) else stringResource(R.string.crowded_text, T.clock(c, h.settings.watchAt)),
                        style = UType.Sub.copy(color = U.c.ink),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // A ship that keeps coming back this hour goes to anchor: lighting it again would be one more round of the same.
            if (crowded) {
                PrimaryButton(stringResource(R.string.sight_anchor), { save(Fate.ANCHORED) }, Modifier.fillMaxWidth(), enabled = ready, icon = Icons.Rounded.Anchor)
            } else {
                PrimaryButton(stringResource(R.string.sight_light), { save(Fate.PASSED) }, Modifier.fillMaxWidth(), enabled = ready)
                SoftButton(stringResource(R.string.sight_anchor_long), { if (ready) save(Fate.ANCHORED) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Anchor, color = if (ready) U.c.lamp else U.c.faint)
            }
        }
    }
}

/** A ship to pick, or the "new ship" card when [log] is null. */
@Composable
private fun ShipChoice(log: ShipLog?, on: Boolean, likely: Boolean = false, onClick: () -> Unit) {
    val c = ctx()
    Column(
        Modifier.width(98.dp).clip(U.RMd)
            .background(if (on) U.c.lampSoft else U.c.surface)
            .border(1.dp, if (on) U.c.lamp.copy(alpha = 0.6f) else if (likely) U.c.calm.copy(alpha = 0.6f) else U.c.line, U.RMd)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (log == null) {
            Box(Modifier.size(56.dp).clip(U.RSm).background(U.c.surface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = U.c.lamp)
            }
        } else {
            ShipThumb(log.ship.seed, log.tonnage, size = 56.dp, ghost = log.ghost)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (log == null) stringResource(R.string.sight_new) else log.ship.name,
            style = UType.Strong.copy(fontSize = 13.5.sp, color = if (on) U.c.lamp else U.c.ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (likely) Text(stringResource(R.string.sight_likely), style = UType.Small.copy(color = U.c.calm, fontSize = 10.sp), maxLines = 1)
        else if (log != null) Text(T.plural(c, R.plurals.visits, log.count), style = UType.Small.copy(fontSize = 10.sp), maxLines = 1)
    }
}

/** The ship whose past sightings share the most words with [text], if any share at least one. */
internal fun likely(text: String, ships: List<ShipLog>): ShipLog? {
    fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 }.map { it.take(5) }.toSet()
    val mine = words(text)
    if (mine.isEmpty()) return null
    return ships.map { l -> l to l.sightings.take(20).sumOf { (words(it.text) intersect mine).size } + (words(l.ship.name) intersect mine).size * 2 }
        .filter { it.second > 0 }.maxByOrNull { it.second }?.first
}
