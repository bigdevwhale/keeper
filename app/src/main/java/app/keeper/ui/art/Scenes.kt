package app.keeper.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.keeper.data.Tonnage
import kotlin.math.sin
import kotlin.random.Random

/** A ship far out on the roadstead: who it is and how heavy, drawn as a dark shape with its riding light. */
class Anchored(val key: Long, val seed: Int, val tonnage: Tonnage)

/** Seconds that keep running while the scene is on screen. */
@Composable
fun rememberSeconds(): Float {
    val t by rememberInfiniteTransition(label = "sea").animateFloat(
        0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    return t
}

/**
 * The bay at night: sky, sea, the lighthouse on its rock with its beam turning, ships riding at
 * anchor out on the roadstead, and as much fog as the day has brought. [still] stops the clock, for
 * renders and screenshots.
 */
@Composable
fun BayScene(fog: Float, anchored: List<Anchored>, modifier: Modifier = Modifier, lit: Boolean = true, still: Float? = null) {
    val t = still ?: rememberSeconds()
    Canvas(modifier) { bay(fog, anchored, t, lit) }
}

fun DrawScope.bay(fog: Float, anchored: List<Anchored>, t: Float, lit: Boolean) {
    val horizon = 0.5f
    skyAndSea(horizon, fog, t)
    fogBanks(fog, t, 0.38f, 0.56f, seed = 2)
    // The roadstead: out past the rock, to the right.
    anchored.take(9).forEachIndexed { i, a ->
        val r = Random(a.key)
        val x = size.width * (0.42f + 0.5f * ((i * 0.37f + r.nextFloat() * 0.25f) % 1f))
        val depth = 0.06f + 0.22f * r.nextFloat()
        val y = size.height * (horizon + depth) + sin(t * 1.3f + i) * 2.5f
        val len = size.width * (0.07f + 0.05f * depth / 0.28f) * (0.75f + 0.12f * a.tonnage.weight)
        ship(Look(a.seed), a.tonnage, Offset(x, y), len, flat = Color(0xFF050B14), alpha = 0.92f, roll = sin(t * 0.9f + i) * 2.5f)
        anchorLight(Offset(x - len * 0.42f, y - len * 0.28f), len, 0.8f + 0.2f * sin(t * 2f + i * 1.7f))
    }
    val h = size.height * 0.6f
    val lamp = lighthouse(Offset(size.width * 0.2f, size.height * 0.86f), h, if (lit) 1f else 0.08f)
    if (lit) beam(lamp, (t / 14f) % 1f, size.width * 1.05f)
    fogBanks(fog * 0.6f, t * 1.4f, 0.62f, 0.95f, seed = 9)
}
