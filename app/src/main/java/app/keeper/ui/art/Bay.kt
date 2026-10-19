package app.keeper.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import app.keeper.data.Tonnage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

object Night {
    val SkyTop = Color(0xFF060E1A)
    val SkyLow = Color(0xFF15304D)
    val SeaTop = Color(0xFF0F2A45)
    val SeaLow = Color(0xFF050C16)
    val Lamp = Color(0xFFF4B94E)
    val LampHot = Color(0xFFFFE3A3)
    val Fog = Color(0xFF9DB0C5)
    val Stone = Color(0xFF1A2230)
    val Tower = Color(0xFFE9DFC8)
    val Stripe = Color(0xFFC0533C)
    val Iron = Color(0xFF151B24)
}

/** The night sky and the sea under it, with the horizon at [horizon] (a fraction of the height). */
fun DrawScope.skyAndSea(horizon: Float, fog: Float, t: Float, seed: Int = 3) {
    val hy = size.height * horizon
    drawRect(Brush.verticalGradient(listOf(Night.SkyTop, Night.SkyLow), 0f, hy), size = Size(size.width, hy))
    // Stars, fewer and fainter the thicker the fog.
    val r = Random(seed)
    repeat(70) { i ->
        val x = r.nextFloat() * size.width
        val y = r.nextFloat() * hy * 0.92f
        val tw = 0.55f + 0.45f * sin(t * (0.6f + r.nextFloat()) + i)
        val a = (0.75f - y / hy * 0.5f) * tw * (1f - fog * 0.9f)
        drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), if (i % 9 == 0) 2.2f else 1.3f, Offset(x, y))
    }
    // The moon, low on the right, and its road on the water.
    val moon = Offset(size.width * 0.66f, hy * 0.5f)
    val mr = size.minDimension * 0.045f
    drawCircle(Brush.radialGradient(listOf(Color(0x33F3EBD3), Color.Transparent), moon, mr * 5), mr * 5, moon)
    drawCircle(Color(0xFFF1E8D0).copy(alpha = 1f - fog * 0.6f), mr, moon)
    drawCircle(Night.SkyLow.copy(alpha = 0.9f), mr * 0.92f, moon + Offset(mr * 0.45f, -mr * 0.2f))
    drawRect(Brush.verticalGradient(listOf(Night.SeaTop, Night.SeaLow), hy, size.height), Offset(0f, hy), Size(size.width, size.height - hy))
    drawLine(Color(0x55B9CBE0), Offset(0f, hy), Offset(size.width, hy), 1.5f)
    val rw = Random(seed + 1)
    repeat(46) { i ->
        val depth = rw.nextFloat()
        val y = hy + (size.height - hy) * depth * depth + 4
        val len = (12f + 70f * depth) * (0.6f + rw.nextFloat())
        val x = ((rw.nextFloat() * size.width + t * (6f + 20f * depth)) % (size.width + len)) - len / 2
        val nearMoon = 1f - (abs(x - moon.x) / (size.width * 0.25f)).coerceIn(0f, 1f)
        val a = 0.06f + 0.1f * depth + 0.4f * nearMoon * (1f - fog * 0.7f)
        drawLine(Color(0xFFCAD8E6).copy(alpha = a), Offset(x, y), Offset(x + len, y), strokeWidth = 1f + depth * 2f, cap = StrokeCap.Round)
        if (i % 2 == 0) drawLine(Color(0xFF02070D).copy(alpha = 0.35f), Offset(x + len * 0.2f, y + 3 + depth * 3), Offset(x + len * 0.9f, y + 3 + depth * 3), 1f + depth * 2f)
    }
}

/** Banks of fog drifting across, thicker at [fog] 1. */
fun DrawScope.fogBanks(fog: Float, t: Float, from: Float, to: Float, seed: Int = 5) {
    if (fog <= 0.02f) return
    val r = Random(seed)
    repeat(9) {
        val y = size.height * (from + (to - from) * r.nextFloat())
        val rad = size.width * (0.22f + r.nextFloat() * 0.28f)
        val speed = 4f + r.nextFloat() * 10f
        val x = ((r.nextFloat() * size.width * 1.6f + t * speed) % (size.width * 1.6f)) - size.width * 0.3f
        val a = (0.1f + 0.16f * r.nextFloat()) * fog
        drawOval(
            Brush.radialGradient(listOf(Night.Fog.copy(alpha = a), Color.Transparent), Offset(x, y), rad),
            Offset(x - rad, y - rad * 0.38f), Size(rad * 2, rad * 0.76f),
        )
    }
}

/**
 * A lighthouse on its rock, [h] tall from its base at [base]. The lamp room glows by [glow].
 * Returns where the lamp is, for the beam.
 */
fun DrawScope.lighthouse(base: Offset, h: Float, glow: Float): Offset {
    val wb = h * 0.24f
    val wt = h * 0.15f
    val towerTop = base.y - h * 0.68f
    // The rock it stands on.
    val rock = Path().apply {
        moveTo(base.x - h * 0.55f, base.y + h * 0.16f)
        lineTo(base.x - h * 0.42f, base.y + h * 0.02f)
        lineTo(base.x - h * 0.2f, base.y - h * 0.03f)
        lineTo(base.x + h * 0.08f, base.y - h * 0.02f)
        lineTo(base.x + h * 0.3f, base.y + h * 0.03f)
        lineTo(base.x + h * 0.5f, base.y + h * 0.1f)
        lineTo(base.x + h * 0.62f, base.y + h * 0.18f)
        close()
    }
    drawPath(rock, Night.Stone)
    drawPath(Path().apply {
        moveTo(base.x - h * 0.42f, base.y + h * 0.02f); lineTo(base.x - h * 0.2f, base.y - h * 0.03f); lineTo(base.x + h * 0.08f, base.y - h * 0.02f)
    }, Color(0x22FFFFFF), style = Stroke(1.5f))
    // The tower, in bands of white and red.
    val tower = Path().apply {
        moveTo(base.x - wb / 2, base.y); lineTo(base.x - wt / 2, towerTop); lineTo(base.x + wt / 2, towerTop); lineTo(base.x + wb / 2, base.y); close()
    }
    clipPath(tower) {
        drawRect(Night.Tower, Offset(base.x - wb, towerTop), Size(wb * 2, base.y - towerTop))
        val bands = 4
        for (i in 0 until bands) {
            if (i % 2 == 1) continue
            val y0 = towerTop + (base.y - towerTop) * (i + 0.5f) / (bands + 0.5f)
            drawRect(Night.Stripe, Offset(base.x - wb, y0), Size(wb * 2, (base.y - towerTop) / (bands + 0.5f)))
        }
        // Moonlight from the right, shadow on the left.
        drawRect(Brush.horizontalGradient(listOf(Color(0x99000814), Color(0x11000000), Color(0x00000000)), base.x - wb / 2, base.x + wb / 2), Offset(base.x - wb, towerTop), Size(wb * 2, base.y - towerTop))
    }
    drawRect(Night.Iron, Offset(base.x - h * 0.02f, base.y - h * 0.12f), Size(h * 0.04f, h * 0.1f))
    drawRect(Night.Lamp.copy(alpha = 0.7f * glow + 0.15f), Offset(base.x - h * 0.012f, base.y - h * 0.42f), Size(h * 0.024f, h * 0.04f))
    // The gallery and its rail.
    val gy = towerTop
    drawRect(Night.Iron, Offset(base.x - wt * 0.75f, gy - h * 0.025f), Size(wt * 1.5f, h * 0.03f))
    for (i in 0..6) {
        val x = base.x - wt * 0.72f + wt * 1.44f * i / 6
        drawLine(Night.Iron, Offset(x, gy - h * 0.025f), Offset(x, gy - h * 0.07f), 1.6f)
    }
    drawLine(Night.Iron, Offset(base.x - wt * 0.75f, gy - h * 0.07f), Offset(base.x + wt * 0.75f, gy - h * 0.07f), 2f)
    // The lamp room.
    val lr = Rect(base.x - wt * 0.42f, gy - h * 0.16f, base.x + wt * 0.42f, gy - h * 0.025f)
    val lamp = lr.center
    drawCircle(Brush.radialGradient(listOf(Night.Lamp.copy(alpha = 0.45f * glow), Color.Transparent), lamp, h * (0.25f + 0.35f * glow)), h * (0.25f + 0.35f * glow), lamp)
    drawRect(lerpColor(Color(0xFF3A3020), Night.LampHot, 0.35f + 0.65f * glow), lr.topLeft, lr.size)
    for (i in 1..2) drawLine(Night.Iron, Offset(lr.left + lr.width * i / 3, lr.top), Offset(lr.left + lr.width * i / 3, lr.bottom), 1.6f)
    // The cap and its vane.
    val cap = Path().apply {
        moveTo(lr.left - wt * 0.12f, lr.top); quadraticTo(base.x, lr.top - h * 0.12f, lr.right + wt * 0.12f, lr.top); close()
    }
    drawPath(cap, Night.Iron)
    drawLine(Night.Iron, Offset(base.x, lr.top - h * 0.06f), Offset(base.x, lr.top - h * 0.12f), 2f)
    drawCircle(Night.Iron, h * 0.012f, Offset(base.x, lr.top - h * 0.125f))
    return lamp
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/**
 * The lamp's beam, seen from the shore as it turns: [turn] is how far round it is, 0..1, a quarter
 * being straight at you (a flare, no beam) and three quarters straight away. [reach] is how far it
 * throws across the screen.
 */
fun DrawScope.beam(lamp: Offset, turn: Float, reach: Float, power: Float = 1f) {
    val a = turn * 2 * PI
    val side = cos(a).toFloat()
    val toward = sin(a).toFloat()
    val len = reach * abs(side)
    val spread = reach * (0.05f + 0.12f * max(0f, toward))
    val alpha = (0.18f + 0.22f * max(0f, toward)) * power
    if (len > 4f) {
        val end = lamp.x + len * if (side >= 0) 1 else -1
        val wedge = Path().apply {
            moveTo(lamp.x, lamp.y - spread * 0.08f)
            lineTo(end, lamp.y - spread)
            lineTo(end, lamp.y + spread)
            lineTo(lamp.x, lamp.y + spread * 0.08f)
            close()
        }
        drawPath(wedge, Brush.horizontalGradient(listOf(Night.Lamp.copy(alpha = alpha), Night.Lamp.copy(alpha = 0f)), lamp.x, end))
    }
    // Facing you, the lamp flares.
    val flare = max(0f, toward)
    val fr = reach * (0.04f + 0.22f * flare * flare)
    drawCircle(Brush.radialGradient(listOf(Night.LampHot.copy(alpha = (0.35f + 0.6f * flare) * power), Night.Lamp.copy(alpha = 0.18f * flare * power), Color.Transparent), lamp, fr), fr, lamp)
}

/** A ship in a bottle, lying on its side on two wooden cradles, inside [r]. */
fun DrawScope.bottle(look: Look, tonnage: Tonnage, ghost: Float, r: Rect) {
    val body = Rect(r.left, r.top + r.height * 0.06f, r.left + r.width * 0.74f, r.bottom - r.height * 0.14f)
    val neckTop = body.top + body.height * 0.3f
    val neckBottom = body.bottom - body.height * 0.3f
    val glass = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(body, CornerRadius(body.height * 0.42f)))
    }
    val neck = Path().apply {
        moveTo(body.right - body.height * 0.3f, body.top + body.height * 0.06f)
        cubicTo(body.right + r.width * 0.06f, body.top + body.height * 0.08f, body.right + r.width * 0.04f, neckTop, body.right + r.width * 0.12f, neckTop)
        lineTo(r.right - r.width * 0.05f, neckTop)
        lineTo(r.right - r.width * 0.05f, neckBottom)
        lineTo(body.right + r.width * 0.12f, neckBottom)
        cubicTo(body.right + r.width * 0.04f, neckBottom, body.right + r.width * 0.06f, body.bottom - body.height * 0.08f, body.right - body.height * 0.3f, body.bottom - body.height * 0.06f)
        close()
    }
    val outline = Path.combine(androidx.compose.ui.graphics.PathOperation.Union, glass, neck)
    // Cradles.
    val wood = Color(0xFF5B3B26)
    listOf(0.22f, 0.62f).forEach { f ->
        val x = r.left + r.width * f
        drawRoundRect(wood, Offset(x - r.width * 0.05f, body.bottom - body.height * 0.08f), Size(r.width * 0.1f, r.bottom - body.bottom + body.height * 0.08f), CornerRadius(3f))
    }
    drawRoundRect(Color(0xFF3F2818), Offset(r.left + r.width * 0.12f, r.bottom - r.height * 0.05f), Size(r.width * 0.6f, r.height * 0.05f), CornerRadius(3f))
    drawPath(outline, Color(0xFF9DC4D6).copy(alpha = 0.10f))
    // The putty sea, and the ship riding it.
    clipPath(glass) {
        val sea = Path().apply {
            moveTo(body.left, body.bottom - body.height * 0.26f)
            var x = body.left
            val step = body.width / 6
            var up = true
            while (x < body.right) {
                quadraticTo(x + step / 2, body.bottom - body.height * (if (up) 0.34f else 0.2f), x + step, body.bottom - body.height * 0.26f)
                x += step; up = !up
            }
            lineTo(body.right, body.bottom); lineTo(body.left, body.bottom); close()
        }
        val len = body.width * (0.62f + 0.06f * (4 - tonnage.weight) / 3f)
        val h = len * when (tonnage) { Tonnage.BOAT -> 0.7f; Tonnage.SCHOONER -> 0.9f; Tonnage.FRIGATE -> 1.02f; Tonnage.GALLEON -> 1.12f }
        val scale = ((body.height * 0.66f) / h).coerceAtMost(1f)
        ship(look, tonnage, Offset(body.center.x - body.width * 0.04f, body.bottom - body.height * 0.25f), len * scale, ghost)
        drawPath(sea, Color(0xFF2B5C7E))
        drawPath(sea, Color(0x33FFFFFF), style = Stroke(1.2f))
    }
    // Glass edges, a highlight and the cork.
    drawPath(outline, Color(0xFFCFE3EC).copy(alpha = 0.55f), style = Stroke(1.6f))
    drawLine(Color.White.copy(alpha = 0.3f), Offset(body.left + body.width * 0.18f, body.top + body.height * 0.07f), Offset(body.left + body.width * 0.6f, body.top + body.height * 0.07f), 2.4f, StrokeCap.Round)
    drawRoundRect(Color(0xFFA77A4E), Offset(r.right - r.width * 0.07f, neckTop - 1.5f), Size(r.width * 0.07f, neckBottom - neckTop + 3f), CornerRadius(2.5f))
}
