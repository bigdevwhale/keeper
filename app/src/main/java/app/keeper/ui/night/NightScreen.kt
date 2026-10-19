package app.keeper.ui.night

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.keeper.R
import app.keeper.SHOTS
import app.keeper.domain.Breathing
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.art.beam
import app.keeper.ui.art.lighthouse
import app.keeper.ui.theme.U
import app.keeper.ui.theme.UType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The foghorn, for three in the morning: nearly black, only the lamp turning in time with the breath
 * and a soft buzz each time it comes round. No words to read, nothing to tap. Swipe down to leave.
 */
@Composable
fun NightScreen(h: Harbor, go: Go) {
    val view = LocalView.current
    val breath = h.settings.breath
    var elapsed by remember { mutableLongStateOf(if (SHOTS) breath.inS * 1000L - 300 else 0L) }
    var hint by remember { mutableStateOf(true) }
    val hintAlpha by animateFloatAsState(if (hint) 0.7f else 0f, tween(1500), label = "hint")
    var pulled by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        if (SHOTS) return@LaunchedEffect
        delay(5000)
        hint = false
    }
    LaunchedEffect(Unit) {
        if (SHOTS) return@LaunchedEffect
        val start = withFrameMillis { it }
        var lastRound = -1
        while (true) {
            elapsed = withFrameMillis { it } - start
            val m = Breathing.at(breath, elapsed)
            if (m.round != lastRound) {
                lastRound = m.round
                if (h.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }
    BackHandler { go.back() }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF02050A)).pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { if (pulled > size.height * 0.18f) go.back(); pulled = 0f },
                onDragCancel = { pulled = 0f },
            ) { _, dy -> pulled = max(0f, pulled + dy) }
        },
    ) {
        val m = Breathing.at(breath, elapsed)
        Canvas(Modifier.fillMaxSize()) {
            val r = Random(11)
            repeat(40) {
                val p = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height * 0.7f)
                drawCircle(Color.White.copy(alpha = 0.08f + 0.12f * r.nextFloat()), 1.2f, p)
            }
            val facing = max(0f, sin(m.turn * 2 * Math.PI).toFloat())
            val lamp = lighthouse(Offset(size.width / 2, size.height * 0.88f), size.height * 0.26f, 0.3f + 0.7f * facing)
            beam(lamp, m.turn, size.width * 0.9f, power = 0.75f)
        }
        Text(
            stringResource(R.string.night_hint),
            style = UType.Muted.copy(color = U.c.faint.copy(alpha = hintAlpha)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
    }
}
