package app.keeper.ui.fleet

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.keeper.R
import app.keeper.container
import app.keeper.domain.WatchClock
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.PrimaryButton
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.TopBar
import app.keeper.ui.components.UTextField
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType

/**
 * The chart: "and if it does, then what?" sounded step by step down to the bottom, and at the bottom
 * the lifeboat, the plan for what you'd do. Drawn only on the watch; the lifeboat can be read any time.
 */
@Composable
fun ChartScreen(id: Long, h: Harbor, go: Go) {
    val c = ctx()
    val container = c.container
    val l = h.log(id) ?: return
    val open = h.watch == WatchClock.State.OPEN
    var shoals by rememberSaveable(id) { mutableStateOf(l.ship.shoalList) }
    var next by rememberSaveable { mutableStateOf("") }
    var boat by rememberSaveable(id) { mutableStateOf(l.ship.lifeboat) }

    fun save(list: List<String> = shoals, lifeboat: String = boat) {
        container.write { db.dao().ship(id)?.let { db.dao().update(it.copy(shoals = list.joinToString("\n"), lifeboat = lifeboat.trim())) } }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(stringResource(R.string.chart_title, T.ship(c, l.ship.name)), go::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Hint(Hints.CHART, h.settings)
            if (!open) Text(stringResource(R.string.chart_closed, T.clock(c, h.settings.watchAt)), style = UType.Muted, modifier = Modifier.padding(4.dp))
            Spacer(Modifier.height(8.dp))
            // The surface: the thought as it came.
            Sounding(0, first = true) {
                Text(stringResource(R.string.chart_if).uppercase(), style = UType.Eyebrow)
                Text(T.quote(c, l.sightings.firstOrNull()?.text.orEmpty()), style = UType.Quote)
            }
            shoals.forEachIndexed { i, step ->
                Sounding(i + 1) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.chart_then).uppercase(), style = UType.Eyebrow)
                            Text(step, style = UType.Body)
                        }
                        if (open) IconButton(onClick = { shoals = shoals.filterIndexed { j, _ -> j != i }; save() }, Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = U.c.faint, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (open) {
                Sounding(shoals.size + 1) {
                    Text(stringResource(R.string.chart_next_q), style = UType.Strong)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { UTextField(next, { next = it }, placeholder = stringResource(R.string.chart_next_hint)) }
                        IconButton(onClick = {
                            if (next.isNotBlank()) {
                                shoals = shoals + next.trim(); next = ""; save()
                            }
                        }) { Icon(Icons.Rounded.Add, stringResource(R.string.chart_add), tint = U.c.lamp) }
                    }
                }
            }

            SectionTitle(stringResource(R.string.chart_bottom))
            Column(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.surface).border(1.dp, U.c.calm.copy(alpha = 0.4f), U.RMd).padding(14.dp)) {
                Text(stringResource(R.string.chart_boat), style = UType.Name)
                Text(stringResource(R.string.chart_boat_sub), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                if (open) {
                    UTextField(boat, { boat = it }, placeholder = stringResource(R.string.chart_boat_hint), minLines = 4)
                } else {
                    Text(boat.ifBlank { stringResource(R.string.chart_no_boat) }, style = UType.Body)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (open) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                PrimaryButton(stringResource(R.string.save), {
                    save()
                    Toast.makeText(c, R.string.chart_saved, Toast.LENGTH_SHORT).show()
                    go.back()
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** One sounding on the chart: a depth mark on the lead line, and what was found there. */
@Composable
private fun Sounding(depth: Int, first: Boolean = false, content: @Composable () -> Unit) {
    val line = U.c.fog.copy(alpha = 0.5f)
    val mark = U.c.lamp
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (first) "≈" else "${depth * 3}", style = UType.Log.copy(color = mark))
            Canvas(Modifier.weight(1f).width(2.dp)) {
                drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
        }
        Column(Modifier.weight(1f).padding(bottom = 16.dp)) { content() }
    }
}
