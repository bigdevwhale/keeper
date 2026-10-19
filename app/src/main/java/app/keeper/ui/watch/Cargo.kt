package app.keeper.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.container
import app.keeper.data.Cargo
import app.keeper.data.Outcome
import app.keeper.ui.Harbor
import app.keeper.ui.T
import app.keeper.ui.components.Chip
import app.keeper.ui.components.DateDialog
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.SoftButton
import app.keeper.ui.components.UCard
import app.keeper.ui.components.UTextField
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import java.time.LocalDate

/**
 * What a ship says it's bringing, written down as a forecast that can be checked: what exactly, and by
 * when. On that day the log asks whether it came.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CargoDialog(shipName: String, initial: String, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    val c = ctx()
    val today = LocalDate.now()
    var text by rememberSaveable { mutableStateOf(initial) }
    var due by rememberSaveable { mutableLongStateOf(today.plusWeeks(1).toEpochDay()) }
    var picking by remember { mutableStateOf(false) }
    val presets = listOf(1L to R.string.due_tomorrow, 7L to R.string.due_week, 30L to R.string.due_month)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.surface,
        title = { Text(stringResource(R.string.cargo_title, T.ship(c, shipName)), style = UType.Name) },
        text = {
            Column {
                Text(stringResource(R.string.cargo_sub), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                UTextField(text, { text = it }, placeholder = stringResource(R.string.cargo_hint), minLines = 2)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.cargo_due), style = UType.Strong)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (d, label) -> Chip(stringResource(label), due == today.toEpochDay() + d, { due = today.toEpochDay() + d }) }
                    val custom = presets.none { due == today.toEpochDay() + it.first }
                    Chip(if (custom) T.day(c, LocalDate.ofEpochDay(due)) else stringResource(R.string.due_pick), custom, { picking = true })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim(), due); onDismiss() }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save), color = if (text.isNotBlank()) U.c.lamp else U.c.faint, style = UType.Button)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) } },
    )
    if (picking) DateDialog(due, { picking = false }) { due = it }
}

/** A cargo whose day has come: did it arrive? And if it did, did you get through it? */
@Composable
fun CargoCheck(cargo: Cargo, h: Harbor) {
    val c = ctx()
    val container = c.container
    val log = h.log(cargo.shipId)
    var asked by remember(cargo.id) { mutableStateOf<Outcome?>(null) }
    UCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShipThumb(log?.ship?.seed ?: 0, log?.tonnage ?: app.keeper.data.Tonnage.SCHOONER, size = 44.dp, ghost = log?.ghost ?: 0f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(T.ship(c, log?.ship?.name.orEmpty()), style = UType.Name.copy(fontSize = 17.sp))
                Text(stringResource(R.string.cargo_promised, T.day(c, cargo.createdAt), T.day(c, LocalDate.ofEpochDay(cargo.due))), style = UType.Small)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(T.quote(c, cargo.text), style = UType.Quote)
        Spacer(Modifier.height(12.dp))
        val o = asked
        if (o == null) {
            Text(stringResource(R.string.cargo_q), style = UType.Strong)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(stringResource(R.string.out_arrived), { asked = Outcome.ARRIVED }, Modifier.weight(1f), color = U.c.rust, soft = U.c.rust.copy(alpha = 0.14f))
                SoftButton(stringResource(R.string.out_partly), { asked = Outcome.PARTLY }, Modifier.weight(1f), color = U.c.muted, soft = U.c.surface2)
                SoftButton(stringResource(R.string.out_empty), {
                    container.write { db.dao().update(cargo.copy(outcome = Outcome.EMPTY.name, answeredAt = System.currentTimeMillis())) }
                }, Modifier.weight(1f), color = U.c.calm, soft = U.c.calm.copy(alpha = 0.14f))
            }
        } else {
            Text(stringResource(R.string.cargo_coped_q), style = UType.Strong)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to R.string.cargo_coped, false to R.string.cargo_coping).forEach { (coped, label) ->
                    SoftButton(stringResource(label), {
                        container.write { db.dao().update(cargo.copy(outcome = o.name, coped = coped, answeredAt = System.currentTimeMillis())) }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** One cargo in a list, checked or not. */
@Composable
fun CargoRow(cargo: Cargo, h: Harbor, showShip: Boolean = true) {
    val c = ctx()
    val color = when (cargo.outcomeOf) {
        Outcome.PENDING -> U.c.lamp
        Outcome.ARRIVED -> U.c.rust
        Outcome.PARTLY -> U.c.muted
        Outcome.EMPTY -> U.c.calm
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        CrateMark(cargo.outcomeOf, Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showShip) Text(T.ship(c, h.ship(cargo.shipId)?.name.orEmpty()), style = UType.Small.copy(color = U.c.muted))
            Text(cargo.text, style = UType.Body)
            val day = LocalDate.ofEpochDay(cargo.due)
            val note = when (cargo.outcomeOf) {
                Outcome.PENDING -> stringResource(R.string.cargo_due_on, T.when_(c, day, h.today))
                Outcome.EMPTY -> T.outcome(c, cargo.outcomeOf) + " · " + T.day(c, day)
                else -> T.outcome(c, cargo.outcomeOf) + " · " + T.day(c, day) + if (cargo.coped) " · " + stringResource(R.string.cargo_coped_short) else ""
            }
            Text(note, style = UType.Small.copy(color = color))
        }
    }
}

/** A crate: full for a cargo that arrived, half for part of it, empty for nothing, dashed while it's still at sea. */
@Composable
fun CrateMark(o: Outcome, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val rust = U.c.rust
    val calm = U.c.calm
    val muted = U.c.muted
    val lamp = U.c.lamp
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.1f
        val inset = stroke / 2
        val box = androidx.compose.ui.geometry.Size(w - stroke, w - stroke)
        val at = androidx.compose.ui.geometry.Offset(inset, inset)
        val r = androidx.compose.ui.geometry.CornerRadius(w * 0.12f)
        when (o) {
            Outcome.ARRIVED -> drawRoundRect(rust, at, box, r)
            Outcome.PARTLY -> {
                drawRoundRect(muted, at, box, r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawRect(muted, androidx.compose.ui.geometry.Offset(inset, w / 2), androidx.compose.ui.geometry.Size(w - stroke, w / 2 - inset))
            }
            Outcome.EMPTY -> {
                drawRoundRect(calm, at, box, r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawLine(calm, androidx.compose.ui.geometry.Offset(w * 0.28f, w * 0.28f), androidx.compose.ui.geometry.Offset(w * 0.72f, w * 0.72f), stroke * 0.8f)
            }
            Outcome.PENDING -> drawRoundRect(
                lamp, at, box, r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.16f, w * 0.12f))),
            )
        }
    }
}
