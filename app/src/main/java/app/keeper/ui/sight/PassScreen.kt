package app.keeper.ui.sight

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.SHOTS
import app.keeper.container
import app.keeper.data.Fate
import app.keeper.data.Sighting
import app.keeper.data.Tonnage
import app.keeper.domain.Breathing
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.art.Look
import app.keeper.ui.art.Night
import app.keeper.ui.art.beam
import app.keeper.ui.art.fogBanks
import app.keeper.ui.art.lighthouse
import app.keeper.ui.art.ship
import app.keeper.ui.art.skyAndSea
import app.keeper.ui.components.AfterPicker
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.PrimaryButton
import app.keeper.ui.components.SoftButton
import app.keeper.ui.ctx
import app.keeper.ui.theme.Serif
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import kotlin.math.sin

/**
 * Under the beam. The lamp turns in time with your breath; the ship, its thought written beneath it
 * as a thought, crosses the bay and goes into the fog. You watch from the shore. Then: how heavy is it now?
 */
@Composable
fun PassScreen(id: Long, h: Harbor, go: Go) {
    val c = ctx()
    val container = c.container
    val s = h.sightings.firstOrNull { it.id == id } ?: return
    val log = h.log(s.shipId) ?: return
    val breath = h.settings.breath
    val rounds = Breathing.rounds(breath)
    val total = rounds * breath.cycleMs
    var run by rememberSaveable { mutableIntStateOf(0) }
    var elapsed by remember { mutableLongStateOf(if (SHOTS) (breath.cycleMs * 2.2f).toLong() else 0L) }
    var after by rememberSaveable { mutableStateOf<Int?>(null) }
    val done = elapsed >= total
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(run) {
        if (SHOTS) return@LaunchedEffect
        val start = withFrameMillis { it }
        var lastRound = -1
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - start
            val m = Breathing.at(breath, elapsed)
            if (m.round != lastRound && elapsed < total) {
                lastRound = m.round
                if (h.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            if (elapsed >= total) break
        }
    }

    val words = rememberTextMeasurer()
    val noticing = remember(s.text) { T.noticing(c, s.text) }
    val name = T.ship(c, log.ship.name)
    val moment = Breathing.at(breath, elapsed.coerceAtMost(total - 1))
    val textStyle = TextStyle(fontFamily = Serif, fontSize = 19.sp, lineHeight = 25.sp, color = U.c.paper, textAlign = TextAlign.Center)
    val nameStyle = UType.Eyebrow.copy(color = U.c.lamp, textAlign = TextAlign.Center)

    Box(Modifier.fillMaxSize().background(Night.SeaLow)) {
        Canvas(Modifier.fillMaxSize()) {
            passing(log.ship.seed, s.tonnage, log.ghost, (elapsed.toFloat() / total).coerceIn(0f, 1f), elapsed / 1000f, moment.turn, words, noticing, name, textStyle, nameStyle)
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = go::home) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = U.c.muted) }
            Text(stringResource(R.string.pass_title).uppercase(), style = UType.Eyebrow.copy(color = U.c.muted), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.pass_round, (moment.round + 1).coerceAtMost(rounds), rounds), style = UType.Log, modifier = Modifier.padding(end = 16.dp))
        }
        if (!done) {
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val cue = when (moment.phase) {
                    Breathing.Phase.IN -> R.string.breath_in
                    Breathing.Phase.HOLD -> R.string.breath_hold
                    Breathing.Phase.OUT -> R.string.breath_out
                }
                val a = when (moment.phase) {
                    Breathing.Phase.IN -> 0.45f + 0.55f * moment.progress
                    Breathing.Phase.HOLD -> 1f
                    Breathing.Phase.OUT -> 1f - 0.55f * moment.progress
                }
                Text(stringResource(cue), style = UType.Display.copy(color = U.c.lamp.copy(alpha = a)))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.pass_watch), style = UType.Muted)
            }
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp)) { Hint(Hints.PASS, h.settings) }
        }
        AnimatedVisibility(done, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it / 3 }) {
            Column(
                Modifier.fillMaxWidth().clip(U.RXl).background(U.c.surface).navigationBarsPadding().padding(20.dp),
            ) {
                Text(stringResource(R.string.pass_gone), style = UType.Title)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.pass_weigh, T.tonnage(c, s.tonnage).lowercase()), style = UType.Sub)
                Spacer(Modifier.height(12.dp))
                AfterPicker(after, { w ->
                    after = w
                    container.write { db.dao().update(s.copy(after = w)) }
                })
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.done), go::home, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(stringResource(R.string.pass_again), { elapsed = 0; run++ }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                    SoftButton(stringResource(R.string.pass_anchor), {
                        container.write { db.dao().update(s.copy(fate = Fate.ANCHORED.name, after = -1)) }
                        go.home()
                    }, Modifier.weight(1f), icon = Icons.Rounded.Anchor)
                }
            }
        }
    }
}

/** The scene: [f] is how far through the passage, [t] the seconds, [turn] where the beam is. */
private fun DrawScope.passing(
    seed: Int, tonnage: Tonnage, ghost: Float, f: Float, t: Float, turn: Float,
    words: TextMeasurer, text: String, name: String, style: TextStyle, nameStyle: TextStyle,
) {
    val horizon = 0.42f
    skyAndSea(horizon, 0.3f, t)
    fogBanks(0.5f, t, 0.36f, 0.6f, seed = 4)
    val w = size.width
    val len = w * (0.3f + 0.035f * tonnage.weight)
    val x = w * (0.42f + 0.85f * f)
    val y = size.height * (horizon + 0.2f) + sin(t * 1.1f) * 4f
    // Into the fog: the further right, the less of it is left.
    val fade = 1f - ((x - w * 0.72f) / (w * 0.45f)).coerceIn(0f, 1f)
    ship(Look(seed), tonnage, Offset(x, y), len, ghost, alpha = fade, roll = sin(t * 0.8f) * 2.2f)
    drawRect(Brush.verticalGradient(listOf(Night.SeaTop.copy(alpha = 0.9f), Night.SeaTop.copy(alpha = 0.6f)), y, y + len * 0.12f), Offset(0f, y), Size(w, len * 0.12f))
    val lamp = lighthouse(Offset(w * 0.1f, size.height * 0.96f), size.height * 0.42f, 0.4f + 0.6f * kotlin.math.max(0f, sin(turn * 2 * Math.PI).toFloat()))
    beam(lamp, turn, w * 1.1f)
    // The thought, beneath it, going with it.
    val maxW = (w * 0.78f).toInt()
    val laid = words.measure(text, style, constraints = Constraints(maxWidth = maxW))
    val label = words.measure(name.uppercase(), nameStyle, constraints = Constraints(maxWidth = maxW))
    val tx = (x - laid.size.width / 2f).coerceIn(16f, w - laid.size.width - 16f)
    val ty = y + len * 0.2f
    val pad = 14f
    drawRoundRect(
        Night.SeaLow.copy(alpha = 0.6f * fade), Offset(tx - pad, ty - pad * 0.6f),
        Size(laid.size.width + pad * 2, label.size.height + 6f + laid.size.height + pad * 1.4f), androidx.compose.ui.geometry.CornerRadius(pad),
    )
    drawText(label, topLeft = Offset(tx + (laid.size.width - label.size.width) / 2f, ty), alpha = fade)
    drawText(laid, topLeft = Offset(tx, ty + label.size.height + 6f), alpha = fade)
    // A wall of fog on the right that it sails into.
    val bank = Offset(w * 1.05f, y - len * 0.3f)
    drawOval(
        Brush.radialGradient(listOf(Night.Fog.copy(alpha = 0.4f), Night.Fog.copy(alpha = 0.12f), Color.Transparent), bank, w * 0.5f),
        Offset(bank.x - w * 0.5f, bank.y - w * 0.45f), Size(w, w * 0.9f),
    )
}
