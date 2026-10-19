package app.keeper.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.keeper.R
import app.keeper.container
import app.keeper.data.Backup
import app.keeper.data.Breath
import app.keeper.data.Everything
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.Language
import app.keeper.ui.T
import app.keeper.ui.components.Chip
import app.keeper.ui.components.ConfirmDialog
import app.keeper.ui.components.InfoRow
import app.keeper.ui.components.SectionTitle
import app.keeper.ui.components.TimeDialog
import app.keeper.ui.components.TopBar
import app.keeper.ui.components.UCard
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(h: Harbor, go: Go) {
    val c = ctx()
    val container = c.container
    val s = h.settings
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf<android.net.Uri?>(null) }
    var picking by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.write(c, uri, Everything(h.ships, h.sightings, h.cargo)) }.isSuccess }
            Toast.makeText(c, if (ok) R.string.backup_done else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.settings), go::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            SectionTitle(stringResource(R.string.set_watch))
            UCard {
                InfoRow(Icons.Rounded.Schedule, stringResource(R.string.set_watch_at), T.clock(c, s.watchAt)) { picking = true }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.set_watch_len), style = UType.Strong)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30).forEach { m ->
                        Chip(stringResource(R.string.minutes_n, m), s.watchLen == m, { container.write { settings.update { it.copy(watchLen = m) } } })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Toggle(stringResource(R.string.set_remind), stringResource(R.string.set_remind_sub), s.remind) { v -> container.write { settings.update { it.copy(remind = v) } } }
            }

            SectionTitle(stringResource(R.string.set_beam))
            UCard {
                Text(stringResource(R.string.set_breath), style = UType.Strong)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Breath.entries.forEach { b -> Chip(T.breath(c, b), s.breath == b, { container.write { settings.update { it.copy(breath = b) } } }) }
                }
                Text(T.breathSub(c, s.breath), style = UType.Muted)
                Spacer(Modifier.height(14.dp))
                Toggle(stringResource(R.string.set_haptics), stringResource(R.string.set_haptics_sub), s.haptics) { v -> container.write { settings.update { it.copy(haptics = v) } } }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.set_tile), style = UType.Muted)
            }

            SectionTitle(stringResource(R.string.set_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cur = Language.current()
                Chip(stringResource(R.string.set_lang_system), cur == Language.SYSTEM, { Language.set(Language.SYSTEM) })
                Chip("English", cur == Language.EN, { Language.set(Language.EN) })
                Chip("Русский", cur == Language.RU, { Language.set(Language.RU) })
            }

            SectionTitle(stringResource(R.string.set_data))
            UCard {
                InfoRow(Icons.Rounded.Upload, stringResource(R.string.backup_export_sub), stringResource(R.string.backup_export)) { export.launch("keeper-${LocalDate.now()}.json") }
                InfoRow(Icons.Rounded.Download, stringResource(R.string.backup_import_sub), stringResource(R.string.backup_import)) { import.launch(arrayOf("application/json", "text/plain", "*/*")) }
                InfoRow(Icons.Rounded.HelpOutline, stringResource(R.string.set_hints_sub), stringResource(R.string.set_hints)) {
                    container.write { settings.update { it.copy(hints = emptySet()) } }
                    Toast.makeText(c, R.string.set_hints_done, Toast.LENGTH_SHORT).show()
                }
            }

            SectionTitle(stringResource(R.string.set_about))
            Text(stringResource(R.string.set_method), style = UType.Sub)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.set_not_therapy), style = UType.Sub.copy(color = U.c.ink))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.set_privacy), style = UType.Muted)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picking) TimeDialog(s.watchAt, { picking = false }) { m -> container.write { settings.update { it.copy(watchAt = m) } } }
    restoring?.let { uri ->
        ConfirmDialog(
            stringResource(R.string.backup_import_title),
            stringResource(R.string.backup_import_text),
            stringResource(R.string.backup_import_go),
            {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val all = Backup.read(c, uri)
                            container.db.dao().replaceAll(all)
                            container.settings.update { it.copy(onboarded = true) }
                        }.isSuccess
                    }
                    Toast.makeText(c, if (ok) R.string.backup_restored else R.string.backup_bad, Toast.LENGTH_SHORT).show()
                }
            },
            { restoring = null },
        )
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Strong)
            Text(sub, style = UType.Muted)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = U.c.lamp, checkedThumbColor = U.c.deep))
    }
}
