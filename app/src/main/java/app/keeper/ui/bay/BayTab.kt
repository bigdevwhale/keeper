package app.keeper.ui.bay

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.SHOTS
import app.keeper.R
import app.keeper.data.Fate
import app.keeper.data.Sighting
import app.keeper.domain.WatchClock
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.art.Anchored
import app.keeper.ui.art.BayScene
import app.keeper.ui.components.Hint
import app.keeper.ui.components.Hints
import app.keeper.ui.components.PrimaryButton
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.Tag
import app.keeper.ui.components.UCard
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType

@Composable
fun BayTab(h: Harbor, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxSize().background(U.c.bg).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(400.dp)) {
            val anchored = h.roadstead.map { s -> Anchored(s.id, h.ship(s.shipId)?.seed ?: 0, s.tonnage) }
            BayScene(h.fog, anchored, Modifier.fillMaxSize(), still = if (SHOTS) 37f else null)
            Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, U.c.bg))))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(T.weekday(c, h.today).uppercase(), style = UType.Eyebrow.copy(color = U.c.muted))
                    Spacer(Modifier.height(2.dp))
                    Text(T.weather(c, h.fog), style = UType.Title)
                }
                IconButton(onClick = go::night) { Icon(Icons.Rounded.Bedtime, stringResource(R.string.night_cd), tint = U.c.muted) }
                IconButton(onClick = go::settings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = U.c.muted) }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Hint(Hints.BAY, h.settings)
            WatchCard(h, go)
            SectionTitle(stringResource(R.string.bay_today))
            if (h.todays.isEmpty()) {
                Text(stringResource(R.string.bay_today_none), style = UType.Sub, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                UCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    h.todays.forEach { s -> SightingRow(s, h, go) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The evening watch: when it opens, what waits for it, or that it's been kept. */
@Composable
private fun WatchCard(h: Harbor, go: Go) {
    val c = ctx()
    val waiting = buildList {
        if (h.roadstead.isNotEmpty()) add(T.plural(c, R.plurals.at_anchor, h.roadstead.size))
        if (h.due.isNotEmpty()) add(T.plural(c, R.plurals.cargo_due, h.due.size))
    }.joinToString(" · ")
    UCard(onClick = go::watch) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val open = h.watch == WatchClock.State.OPEN
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (open) U.c.lamp else U.c.faint))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.watch_eyebrow).uppercase(), style = UType.Eyebrow, modifier = Modifier.weight(1f))
            Text(T.clock(c, h.settings.watchAt), style = UType.Log.copy(color = U.c.lamp))
        }
        Spacer(Modifier.height(8.dp))
        when (h.watch) {
            WatchClock.State.BEFORE -> {
                Text(stringResource(R.string.watch_before, T.clock(c, h.settings.watchAt)), style = UType.Name)
                Spacer(Modifier.height(4.dp))
                Text(waiting.ifEmpty { stringResource(R.string.watch_nothing_yet) }, style = UType.Muted)
            }
            WatchClock.State.OPEN -> {
                Text(stringResource(R.string.watch_open), style = UType.Name)
                Spacer(Modifier.height(4.dp))
                Text(waiting.ifEmpty { stringResource(R.string.watch_open_empty) }, style = UType.Muted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.watch_start), go::watch, Modifier.fillMaxWidth())
            }
            WatchClock.State.KEPT -> {
                Text(stringResource(R.string.watch_kept), style = UType.Name)
                Spacer(Modifier.height(4.dp))
                Text(waiting.ifEmpty { stringResource(R.string.watch_kept_sub) }, style = UType.Muted)
            }
        }
    }
}

/** One sighting in a list: the ship, what it said, when, and what became of it. */
@Composable
fun SightingRow(s: Sighting, h: Harbor, go: Go, showTime: Boolean = true) {
    val c = ctx()
    val log = h.log(s.shipId)
    Row(
        Modifier.fillMaxWidth().clip(U.RSm).clickable { go.ship(s.shipId) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShipThumb(log?.ship?.seed ?: 0, s.tonnage, size = 46.dp, ghost = log?.ghost ?: 0f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(T.ship(c, log?.ship?.name.orEmpty()), style = UType.Name.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.text, style = UType.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showTime) Text(T.time(c, s.at), style = UType.Small)
            when (s.fateOf) {
                Fate.ANCHORED -> Tag(stringResource(R.string.fate_anchored), U.c.lamp)
                Fate.PASSED -> Tag(stringResource(R.string.fate_passed), U.c.calm)
                Fate.WATCHED -> Tag(stringResource(R.string.fate_watched), U.c.muted)
            }
        }
    }
}
