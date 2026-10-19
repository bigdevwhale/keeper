package app.keeper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.container
import app.keeper.data.Settings
import app.keeper.data.Tonnage
import app.keeper.ui.T
import app.keeper.ui.art.Look
import app.keeper.ui.art.Night
import app.keeper.ui.art.ship
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType

/** A small coloured label: "GHOST 60%", "DUE". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, soft: Color = color.copy(alpha = 0.15f)) {
    Box(modifier.clip(U.RSm).background(soft).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text.uppercase(), style = UType.Small.copy(color = color, fontSize = 10.5.sp), maxLines = 1)
    }
}

/** A label above a big number, for stat rows. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = U.c.ink, sub: String? = null) {
    Column(modifier) {
        Text(label.uppercase(), style = UType.Eyebrow, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = UType.Number.copy(color = color, fontSize = 25.sp), maxLines = 1)
        if (sub != null) Text(sub, style = UType.Small, maxLines = 2)
    }
}

/** A ship on a patch of night sea: for rows, pickers and cards. */
@Composable
fun ShipThumb(seed: Int, tonnage: Tonnage, modifier: Modifier = Modifier, ghost: Float = 0f, size: Dp = 52.dp, sea: Boolean = true) {
    Canvas(modifier.size(size).clip(U.RSm).then(if (sea) Modifier.background(Brush.verticalGradient(listOf(Night.SkyLow, Night.SeaTop))) else Modifier)) {
        val w = this.size.width
        if (sea) drawRect(Night.SeaTop, Offset(0f, this.size.height * 0.78f), androidx.compose.ui.geometry.Size(w, this.size.height * 0.22f))
        val h = when (tonnage) { Tonnage.BOAT -> 0.7f; Tonnage.SCHOONER -> 0.9f; Tonnage.FRIGATE -> 1.02f; Tonnage.GALLEON -> 1.12f }
        val len = (w * 0.86f).coerceAtMost(this.size.height * 0.84f / h)
        ship(Look(seed), tonnage, Offset(w * 0.47f, this.size.height * 0.82f), len, ghost)
    }
}

/** Four hulls to choose from, light to heavy. */
@Composable
fun TonnagePicker(selected: Tonnage?, seed: Int, onPick: (Tonnage) -> Unit, modifier: Modifier = Modifier) {
    val c = ctx()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tonnage.entries.forEach { t ->
            val on = t == selected
            Column(
                Modifier.weight(1f).clip(U.RMd)
                    .background(if (on) U.c.lampSoft else U.c.surface)
                    .border(1.dp, if (on) U.c.lamp.copy(alpha = 0.6f) else U.c.line, U.RMd)
                    .clickable { onPick(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShipThumb(seed, t, size = 58.dp, sea = false)
                Text(T.tonnage(c, t), style = UType.Strong.copy(fontSize = 13.5.sp, color = if (on) U.c.lamp else U.c.ink), maxLines = 1)
                Text(T.tonnageSub(c, t), style = UType.Small.copy(fontSize = 10.sp), maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A weight again, after a while: gone, or one of the four. */
@Composable
fun AfterPicker(selected: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ctx()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..4).forEach { w ->
            val on = w == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(U.RSm)
                    .background(if (on) (if (w == 0) U.c.calm.copy(alpha = 0.2f) else U.c.lampSoft) else U.c.surface2)
                    .border(1.dp, if (on) (if (w == 0) U.c.calm else U.c.lamp).copy(alpha = 0.6f) else U.c.line, U.RSm)
                    .clickable { onPick(w) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (w == 0) stringResource(R.string.after_gone) else T.tonnage(c, Tonnage.of(w)),
                    style = UType.Strong.copy(fontSize = 12.5.sp, color = if (on) (if (w == 0) U.c.calm else U.c.lamp) else U.c.muted),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The screens that explain themselves on a first visit. */
enum class Hints(val title: Int, val text: Int) {
    BAY(R.string.hint_bay_title, R.string.hint_bay),
    SIGHT(R.string.hint_sight_title, R.string.hint_sight),
    PASS(R.string.hint_pass_title, R.string.hint_pass),
    WATCH(R.string.hint_watch_title, R.string.hint_watch),
    LOG(R.string.hint_log_title, R.string.hint_log),
    FLEET(R.string.hint_fleet_title, R.string.hint_fleet),
    SHELF(R.string.hint_shelf_title, R.string.hint_shelf),
    SHIP(R.string.hint_ship_title, R.string.hint_ship),
    CHART(R.string.hint_chart_title, R.string.hint_chart),
}

/**
 * A note pinned in the log that explains the screen on a first visit. "Understood" puts it away for
 * good; Settings can bring them all back.
 */
@Composable
fun Hint(hint: Hints, settings: Settings, modifier: Modifier = Modifier) {
    val container = ctx().container
    var gone by remember { mutableStateOf(false) }
    AnimatedVisibility(hint.name !in settings.hints && !gone, exit = fadeOut() + shrinkVertically()) {
        Column(
            modifier.fillMaxWidth().padding(vertical = 6.dp).clip(U.RMd).background(U.c.surface2).border(1.dp, U.c.lamp.copy(alpha = 0.25f), U.RMd).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(U.RSm).background(U.c.lampSoft), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_tile), null, Modifier.size(22.dp), tint = U.c.lamp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hint_eyebrow).uppercase(), style = UType.Eyebrow)
                    Text(stringResource(hint.title), style = UType.Name)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(hint.text), style = UType.Sub.copy(color = U.c.ink))
            Spacer(Modifier.height(10.dp))
            SoftButton(stringResource(R.string.hint_ok), {
                gone = true
                container.write { this.settings.update { it.copy(hints = it.hints + hint.name) } }
            }, Modifier.height(42.dp))
        }
    }
}

/** A message for an empty screen: a picture, a title, a line. */
@Composable
fun Empty(title: String, text: String, modifier: Modifier = Modifier, art: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        art()
        Spacer(Modifier.height(12.dp))
        Text(title, style = UType.Name, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = UType.Sub, textAlign = TextAlign.Center)
    }
}

/** Keeps a full-screen scene filling its box. */
@Composable
fun Fill(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }
