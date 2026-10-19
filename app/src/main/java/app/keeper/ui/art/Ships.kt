package app.keeper.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import app.keeper.data.Tonnage
import kotlin.random.Random

/** A ship's colours, picked once from its seed so it looks the same every time it comes. */
class Look(val seed: Int) {
    private val r = Random(seed)
    val hull: Color = HULLS[r.nextInt(HULLS.size)]
    val trim: Color = TRIMS[r.nextInt(TRIMS.size)]
    val sail: Color = SAILS[r.nextInt(SAILS.size)]
    val flag: Color = FLAGS[r.nextInt(FLAGS.size)]
    val flag2: Color = FLAGS.filter { it != flag }[r.nextInt(FLAGS.size - 1)]
    /** 0 plain, 1 stripe, 2 cross, 3 disc. */
    val pattern: Int = r.nextInt(4)
    /** A little sideways lean on the sails, so no two ships stand the same. */
    val lean: Float = r.nextFloat() * 0.06f - 0.03f
    val notches: List<Float> = List(16) { r.nextFloat() }

    companion object {
        private val HULLS = listOf(
            Color(0xFF4A2E22), Color(0xFF3B2A24), Color(0xFF2C3540), Color(0xFF5A3A26),
            Color(0xFF3A3F2E), Color(0xFF6A2E2A), Color(0xFF23384A), Color(0xFF2E2622),
        )
        private val TRIMS = listOf(Color(0xFFC9A45C), Color(0xFFE6DCC4), Color(0xFFA2412F), Color(0xFF3F7F78), Color(0xFF1E1B18))
        private val SAILS = listOf(
            Color(0xFFEFE6CF), Color(0xFFE3D5B6), Color(0xFFD8C7A4), Color(0xFFCDB894),
            Color(0xFFC08E6A), Color(0xFFE9E5DB), Color(0xFFB9A98C),
        )
        private val FLAGS = listOf(
            Color(0xFFD2654B), Color(0xFF2C4F7C), Color(0xFFE2B048), Color(0xFF3F7D5A),
            Color(0xFFF1ECE0), Color(0xFF1C1A1A), Color(0xFF7B4A8E),
        )
    }
}

private val GhostGreen = Color(0xFF8EE3C2)
private val Rope = Color(0xFF2A211C)

/** Points on a ship [l] long whose waterline is centred on [c]; y grows downward, the masts go up into negative y. */
private class Pen(val c: Offset, val l: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * l, c.y + y * l)
}

private fun Path.at(pen: Pen, x: Float, y: Float) = moveTo(pen.c.x + x * pen.l, pen.c.y + y * pen.l)
private fun Path.to(pen: Pen, x: Float, y: Float) = lineTo(pen.c.x + x * pen.l, pen.c.y + y * pen.l)
private fun Path.curve(pen: Pen, cx: Float, cy: Float, x: Float, y: Float) =
    quadraticTo(pen.c.x + cx * pen.l, pen.c.y + cy * pen.l, pen.c.x + x * pen.l, pen.c.y + y * pen.l)

/** How a ship is painted: its own colours, or one flat colour for a silhouette far off; fading toward a ghost. */
private class Paint(val look: Look, val ghost: Float, val flat: Color?, val alpha: Float) {
    private fun tone(base: Color, ghostMix: Float = 0.75f): Color {
        val c = flat ?: lerp(base, GhostGreen.copy(alpha = base.alpha), ghost * ghostMix)
        return c.copy(alpha = c.alpha * alpha * (1f - 0.55f * ghost))
    }
    val hull get() = tone(look.hull, 0.45f)
    val trim get() = tone(look.trim)
    val sail get() = tone(look.sail.copy(alpha = 1f - 0.35f * ghost))
    val sailShade get() = tone(lerp(look.sail, Color.Black, 0.18f).copy(alpha = 1f - 0.35f * ghost))
    val rope get() = tone(Rope, 0.4f)
    val flag get() = tone(look.flag)
    val flag2 get() = tone(look.flag2)
    val lantern get() = flat?.let { Color(0xFFF4B94E).copy(alpha = alpha) } ?: Color(0xFFF4B94E).copy(alpha = alpha * (1f - 0.6f * ghost))
}

/**
 * Draws a ship, bow to the right, its waterline centred on [c] and [length] long from stern to the
 * tip of the bowsprit. [ghost] fades it toward green mist and tears its sails; [flat] draws it as one
 * colour, a silhouette with a lantern, for ships far off in the dark. [roll] tilts it with the swell.
 */
fun DrawScope.ship(
    look: Look,
    tonnage: Tonnage,
    c: Offset,
    length: Float,
    ghost: Float = 0f,
    flat: Color? = null,
    alpha: Float = 1f,
    roll: Float = 0f,
) {
    val pen = Pen(c, length)
    val paint = Paint(look, ghost.coerceIn(0f, 1f), flat, alpha)
    if (ghost > 0.05f && flat == null) {
        drawCircle(
            Brush.radialGradient(listOf(GhostGreen.copy(alpha = 0.28f * ghost * alpha), Color.Transparent), center = pen.p(0f, -0.4f), radius = length * 0.75f),
            radius = length * 0.75f, center = pen.p(0f, -0.4f),
        )
    }
    rotate(roll, pivot = c) {
        when (tonnage) {
            Tonnage.BOAT -> boat(pen, paint)
            Tonnage.SCHOONER -> schooner(pen, paint)
            Tonnage.FRIGATE -> frigate(pen, paint)
            Tonnage.GALLEON -> galleon(pen, paint)
        }
    }
}

private fun DrawScope.mast(pen: Pen, p: Paint, x: Float, from: Float, to: Float, w: Float = 0.012f) =
    drawLine(p.rope, pen.p(x, from), pen.p(x, to), strokeWidth = pen.l * w, cap = StrokeCap.Round)

private fun DrawScope.line(pen: Pen, p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 0.004f) =
    drawLine(p.rope.copy(alpha = p.rope.alpha * 0.8f), pen.p(x1, y1), pen.p(x2, y2), strokeWidth = (pen.l * w).coerceAtLeast(1f), cap = StrokeCap.Round)

/** The bottom edge of a sail from [x1],[y1] to [x2],[y2]: a gentle belly, or torn into tongues on a ghost. */
private fun Path.foot(pen: Pen, p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, belly: Float, salt: Int) {
    val tear = ((p.ghost - 0.3f) / 0.7f).coerceIn(0f, 1f)
    if (tear <= 0f) {
        curve(pen, (x1 + x2) / 2, (y1 + y2) / 2 + belly, x2, y2)
        return
    }
    val n = 6
    for (i in 1..n) {
        val t = i / n.toFloat()
        val x = x1 + (x2 - x1) * t
        val y = y1 + (y2 - y1) * t + belly * 4 * t * (1 - t)
        val cut = if (i < n) p.look.notches[(i + salt) % p.look.notches.size] * tear * 0.09f else 0f
        val mid = x1 + (x2 - x1) * (t - 0.5f / n)
        to(pen, mid, y - cut)
        to(pen, x, y)
    }
}

/** A square sail hung from a yard: [top] and [bottom] y, half-widths at each, on a mast at [x]. */
private fun DrawScope.square(pen: Pen, p: Paint, x: Float, top: Float, bottom: Float, wTop: Float, wBottom: Float, salt: Int) {
    val lean = p.look.lean
    val path = Path().apply {
        at(pen, x - wTop, top)
        to(pen, x + wTop, top)
        curve(pen, x + wBottom + 0.03f + lean, (top + bottom) / 2, x + wBottom + lean, bottom)
        foot(pen, p, x + wBottom + lean, bottom, x - wBottom + lean, bottom, 0.035f, salt)
        curve(pen, x - wBottom + 0.02f + lean, (top + bottom) / 2, x - wTop, top)
        close()
    }
    drawPath(path, p.sail)
    // A shadow down the leeward side, so the canvas has a belly.
    val shade = Path().apply {
        at(pen, x + wTop * 0.35f, top)
        to(pen, x + wTop, top)
        curve(pen, x + wBottom + 0.03f + lean, (top + bottom) / 2, x + wBottom + lean, bottom)
        to(pen, x + wBottom * 0.4f + lean, bottom + 0.02f)
        close()
    }
    drawPath(shade, p.sailShade)
    line(pen, p, x - wTop - 0.015f, top, x + wTop + 0.015f, top, 0.008f)
}

/** A fore-and-aft sail behind a mast at [x]: luff from [top] to [bottom], boom to [boom], gaff to [gaff]. */
private fun DrawScope.gaff(pen: Pen, p: Paint, x: Float, top: Float, bottom: Float, boom: Float, gaff: Float, salt: Int) {
    val path = Path().apply {
        at(pen, x - 0.006f, top)
        to(pen, x - 0.006f, bottom)
        foot(pen, p, x - 0.006f, bottom, x - boom, bottom - 0.01f, 0.02f, salt)
        curve(pen, x - boom - 0.03f, (top + bottom) / 2 + 0.05f, x - gaff, top + 0.08f)
        close()
    }
    drawPath(path, p.sail)
    line(pen, p, x, bottom, x - boom - 0.02f, bottom - 0.01f, 0.007f)
    line(pen, p, x, top, x - gaff - 0.01f, top + 0.085f, 0.006f)
}

/** A triangular headsail from the mast at [x],[top] down to the bowsprit at [bx],[by]. */
private fun DrawScope.jib(pen: Pen, p: Paint, x: Float, top: Float, bx: Float, by: Float, foot: Float, salt: Int) {
    val path = Path().apply {
        at(pen, x + 0.01f, top)
        curve(pen, (x + bx) / 2 + 0.05f, (top + by) / 2 - 0.02f, bx, by)
        foot(pen, p, bx, by, x + 0.03f, foot, 0.02f, salt)
        close()
    }
    drawPath(path, p.sail)
    line(pen, p, x, top, bx, by)
}

/** A pennant at a masthead streaming downwind, toward the bow. */
private fun DrawScope.pennant(pen: Pen, p: Paint, x: Float, y: Float, long: Float) {
    val path = Path().apply {
        at(pen, x, y)
        curve(pen, x + long * 0.5f, y - 0.02f, x + long, y + 0.012f)
        curve(pen, x + long * 0.5f, y + 0.02f, x, y + 0.03f)
        close()
    }
    drawPath(path, p.flag)
}

/** A flag on a staff at the stern, in the ship's colours and pattern. */
private fun DrawScope.ensign(pen: Pen, p: Paint, x: Float, y: Float, s: Float) {
    line(pen, p, x, y + s * 1.6f, x, y - 0.01f, 0.006f)
    val tl = pen.p(x - s * 1.4f, y)
    val w = s * 1.4f * pen.l
    val h = s * pen.l
    drawRect(p.flag, tl, androidx.compose.ui.geometry.Size(w, h))
    when (p.look.pattern) {
        1 -> drawRect(p.flag2, Offset(tl.x, tl.y + h * 0.36f), androidx.compose.ui.geometry.Size(w, h * 0.28f))
        2 -> {
            drawRect(p.flag2, Offset(tl.x, tl.y + h * 0.4f), androidx.compose.ui.geometry.Size(w, h * 0.2f))
            drawRect(p.flag2, Offset(tl.x + w * 0.32f, tl.y), androidx.compose.ui.geometry.Size(w * 0.16f, h))
        }
        3 -> drawCircle(p.flag2, h * 0.28f, Offset(tl.x + w / 2, tl.y + h / 2))
    }
}

private fun DrawScope.hull(pen: Pen, p: Paint, path: Path, sheer: List<Pair<Float, Float>>) {
    drawPath(path, p.hull)
    drawPath(
        Path().apply { sheer.forEachIndexed { i, (x, y) -> if (i == 0) at(pen, x, y) else to(pen, x, y) } },
        p.trim, style = Stroke(width = pen.l * 0.012f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.boat(pen: Pen, p: Paint) {
    mast(pen, p, 0.02f, -0.04f, -0.66f)
    gaff(pen, p, 0.02f, -0.6f, -0.1f, 0.34f, 0.24f, 0)
    jib(pen, p, 0.02f, -0.6f, 0.42f, -0.08f, -0.09f, 3)
    pennant(pen, p, 0.02f, -0.67f, 0.12f)
    val h = Path().apply {
        at(pen, -0.38f, -0.05f)
        curve(pen, 0f, -0.02f, 0.42f, -0.09f)
        curve(pen, 0.36f, 0.06f, 0.16f, 0.08f)
        to(pen, -0.26f, 0.08f)
        curve(pen, -0.37f, 0.07f, -0.38f, -0.05f)
        close()
    }
    hull(pen, p, h, listOf(-0.38f to -0.05f, 0f to -0.035f, 0.42f to -0.09f))
}

private fun DrawScope.schooner(pen: Pen, p: Paint) {
    line(pen, p, 0.40f, -0.08f, 0.6f, -0.15f, 0.012f)
    mast(pen, p, -0.12f, -0.06f, -0.82f)
    mast(pen, p, 0.15f, -0.06f, -0.88f)
    gaff(pen, p, -0.12f, -0.76f, -0.12f, 0.3f, 0.24f, 1)
    gaff(pen, p, 0.15f, -0.8f, -0.12f, 0.24f, 0.2f, 5)
    jib(pen, p, 0.15f, -0.82f, 0.58f, -0.15f, -0.13f, 2)
    jib(pen, p, 0.15f, -0.6f, 0.46f, -0.11f, -0.12f, 7)
    pennant(pen, p, 0.15f, -0.89f, 0.14f)
    pennant(pen, p, -0.12f, -0.83f, 0.1f)
    val h = Path().apply {
        at(pen, -0.45f, -0.07f)
        curve(pen, 0f, -0.04f, 0.43f, -0.09f)
        curve(pen, 0.38f, 0.06f, 0.2f, 0.09f)
        to(pen, -0.32f, 0.09f)
        curve(pen, -0.44f, 0.07f, -0.45f, -0.07f)
        close()
    }
    hull(pen, p, h, listOf(-0.45f to -0.07f, 0f to -0.05f, 0.43f to -0.09f))
    ensign(pen, p, -0.44f, -0.16f, 0.05f)
}

private fun DrawScope.frigate(pen: Pen, p: Paint) {
    line(pen, p, 0.42f, -0.09f, 0.64f, -0.2f, 0.013f)
    val masts = listOf(-0.24f to 0.84f, 0f to 1.0f, 0.24f to 0.88f)
    masts.forEach { (x, h) -> mast(pen, p, x, -0.08f, -h) }
    // Shrouds down to the rails.
    masts.forEach { (x, h) -> line(pen, p, x, -h * 0.75f, x - 0.07f, -0.1f); line(pen, p, x, -h * 0.75f, x + 0.07f, -0.1f) }
    jib(pen, p, 0.24f, -0.78f, 0.62f, -0.2f, -0.17f, 4)
    masts.forEachIndexed { i, (x, h) ->
        val span = h - 0.18f
        val k = if (i == 1) 1f else 0.88f
        square(pen, p, x, -0.18f - span * 0.98f, -0.18f - span * 0.7f, 0.06f * k, 0.075f * k, i * 3)
        square(pen, p, x, -0.18f - span * 0.68f, -0.18f - span * 0.36f, 0.08f * k, 0.1f * k, i * 3 + 1)
        square(pen, p, x, -0.18f - span * 0.34f, -0.16f, 0.105f * k, 0.12f * k, i * 3 + 2)
        pennant(pen, p, x, -h - 0.01f, if (i == 1) 0.2f else 0.11f)
    }
    val h = Path().apply {
        at(pen, -0.47f, -0.13f)
        to(pen, -0.36f, -0.11f)
        curve(pen, 0f, -0.08f, 0.44f, -0.1f)
        curve(pen, 0.4f, 0.06f, 0.24f, 0.09f)
        to(pen, -0.34f, 0.09f)
        curve(pen, -0.46f, 0.06f, -0.47f, -0.13f)
        close()
    }
    hull(pen, p, h, listOf(-0.47f to -0.13f, -0.36f to -0.11f, 0f to -0.085f, 0.44f to -0.1f))
    // The gun deck: a pale band with dark ports.
    val band = Path().apply {
        at(pen, -0.43f, -0.05f); curve(pen, 0f, -0.025f, 0.38f, -0.05f)
    }
    drawPath(band, p.trim, style = Stroke(width = pen.l * 0.03f))
    for (i in 0 until 9) {
        val x = -0.34f + i * 0.08f
        drawRect(p.hull, pen.p(x, -0.06f), androidx.compose.ui.geometry.Size(pen.l * 0.022f, pen.l * 0.02f))
    }
    ensign(pen, p, -0.47f, -0.24f, 0.055f)
}

private fun DrawScope.galleon(pen: Pen, p: Paint) {
    line(pen, p, 0.48f, -0.12f, 0.68f, -0.26f, 0.015f)
    val masts = listOf(-0.27f to 0.88f, 0f to 1.08f, 0.23f to 0.94f)
    masts.forEach { (x, h) -> mast(pen, p, x, -0.12f, -h, 0.014f) }
    masts.forEach { (x, h) -> line(pen, p, x, -h * 0.75f, x - 0.08f, -0.13f); line(pen, p, x, -h * 0.75f, x + 0.08f, -0.13f) }
    // A spritsail under the bowsprit and a lateen on the mizzen, the old way.
    square(pen, p, 0.6f, -0.24f, -0.15f, 0.04f, 0.05f, 9)
    val lateen = Path().apply {
        at(pen, -0.42f, -0.24f)
        to(pen, -0.17f, -0.86f)
        foot(pen, p, -0.17f, -0.86f, -0.27f, -0.26f, 0.0f, 11)
        close()
    }
    drawPath(lateen, p.sail)
    line(pen, p, -0.44f, -0.22f, -0.15f, -0.9f, 0.008f)
    masts.drop(1).forEachIndexed { i, (x, h) ->
        val span = h - 0.24f
        val k = if (i == 0) 1f else 0.9f
        square(pen, p, x, -0.24f - span * 0.98f, -0.24f - span * 0.66f, 0.065f * k, 0.085f * k, i * 3)
        square(pen, p, x, -0.24f - span * 0.63f, -0.24f - span * 0.3f, 0.095f * k, 0.12f * k, i * 3 + 1)
        square(pen, p, x, -0.24f - span * 0.27f, -0.2f, 0.12f * k, 0.14f * k, i * 3 + 2)
    }
    masts.forEachIndexed { i, (x, h) -> pennant(pen, p, x, -h - 0.01f, if (i == 1) 0.26f else 0.14f) }
    val h = Path().apply {
        at(pen, -0.5f, -0.27f)
        to(pen, -0.27f, -0.25f)
        to(pen, -0.25f, -0.15f)
        curve(pen, 0f, -0.12f, 0.24f, -0.15f)
        to(pen, 0.26f, -0.2f)
        to(pen, 0.43f, -0.2f)
        to(pen, 0.47f, -0.14f)
        to(pen, 0.56f, -0.11f)
        to(pen, 0.45f, -0.08f)
        curve(pen, 0.42f, 0.06f, 0.28f, 0.1f)
        to(pen, -0.34f, 0.1f)
        curve(pen, -0.48f, 0.06f, -0.5f, -0.27f)
        close()
    }
    hull(pen, p, h, listOf(-0.5f to -0.27f, -0.27f to -0.25f, -0.25f to -0.15f, 0f to -0.125f, 0.24f to -0.15f, 0.26f to -0.2f, 0.43f to -0.2f))
    drawPath(Path().apply { at(pen, -0.46f, -0.07f); curve(pen, 0f, -0.035f, 0.42f, -0.07f) }, p.trim, style = Stroke(width = pen.l * 0.025f))
    // The stern gallery's windows and its great lantern: the light of a ship at night.
    for (i in 0 until 3) drawRect(p.lantern.copy(alpha = p.lantern.alpha * 0.8f), pen.p(-0.46f + i * 0.055f, -0.21f), androidx.compose.ui.geometry.Size(pen.l * 0.03f, pen.l * 0.035f))
    line(pen, p, -0.5f, -0.27f, -0.53f, -0.36f, 0.008f)
    drawCircle(p.lantern, pen.l * 0.022f, pen.p(-0.535f, -0.37f))
    ensign(pen, p, -0.48f, -0.4f, 0.06f)
}

/** A ship far off at night: a dark shape, its riding light the only bright thing. */
fun DrawScope.anchorLight(c: Offset, length: Float, glow: Float) {
    val r = length * 0.06f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFF4B94E).copy(alpha = 0.55f * glow), Color.Transparent), center = c, radius = r * 6),
        radius = r * 6, center = c,
    )
    drawCircle(Color(0xFFFFD98A).copy(alpha = glow), r, c)
}
