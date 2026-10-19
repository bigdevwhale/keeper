package app.keeper.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keeper.R
import app.keeper.data.Settings
import app.keeper.data.Tonnage
import app.keeper.ui.T
import app.keeper.ui.art.BayScene
import app.keeper.ui.components.PrimaryButton
import app.keeper.ui.components.ShipThumb
import app.keeper.ui.components.SoftButton
import app.keeper.ui.components.TimeDialog
import app.keeper.ui.ctx
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType

/** Four pages: the keeper, the ships, the watch, and a lighthouse that doesn't console. */
@Composable
fun OnboardingScreen(settings: Settings, onDone: (watchAt: Int) -> Unit) {
    val c = ctx()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var watchAt by rememberSaveable { mutableIntStateOf(settings.watchAt) }
    var picking by remember { mutableStateOf(false) }
    val last = 3

    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        Box(Modifier.fillMaxWidth().weight(0.9f)) {
            BayScene(fog = listOf(0.15f, 0.55f, 0.3f, 0.05f)[page], anchored = emptyList(), modifier = Modifier.fillMaxSize(), lit = page != 2)
            Box(Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, U.c.bg))))
            if (page == 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tonnage.entries.forEachIndexed { i, t -> ShipThumb(listOf(17, 3, 42, 8)[i], t, size = 72.dp) }
                }
            }
        }
        AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page", modifier = Modifier.weight(1.1f)) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Text(stringResource(listOf(R.string.ob1_eyebrow, R.string.ob2_eyebrow, R.string.ob3_eyebrow, R.string.ob4_eyebrow)[p]).uppercase(), style = UType.Eyebrow.copy(color = U.c.lamp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(listOf(R.string.ob1_title, R.string.ob2_title, R.string.ob3_title, R.string.ob4_title)[p]), style = UType.Display)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(listOf(R.string.ob1_text, R.string.ob2_text, R.string.ob3_text, R.string.ob4_text)[p]), style = UType.Sub.copy(color = U.c.ink, fontSize = 16.5.sp, lineHeight = 24.sp))
                if (p == 2) {
                    Spacer(Modifier.height(16.dp))
                    SoftButton(stringResource(R.string.ob3_time, T.clock(c, watchAt)), { picking = true }, icon = Icons.Rounded.Schedule)
                }
                if (p == 3) {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.ob4_note), style = UType.Muted)
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
                (0..last).forEach { i ->
                    Box(Modifier.padding(horizontal = 4.dp).size(if (i == page) 9.dp else 7.dp).clip(CircleShape).background(if (i == page) U.c.lamp else U.c.line))
                }
            }
            PrimaryButton(
                stringResource(if (page == last) R.string.ob_done else R.string.next),
                { if (page == last) onDone(watchAt) else page++ },
                Modifier.fillMaxWidth(),
            )
        }
    }
    if (picking) TimeDialog(watchAt, { picking = false }) { watchAt = it }
}
