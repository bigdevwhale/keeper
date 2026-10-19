package app.keeper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.keeper.data.Tonnage
import app.keeper.ui.art.Anchored
import app.keeper.ui.art.Look
import app.keeper.ui.art.Night
import app.keeper.ui.art.bay
import app.keeper.ui.art.bottle
import app.keeper.ui.art.ship
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the ships, the bottles and the bay to PNGs (run with `-Prender`): a look at the art without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArt {
    private val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }

    private fun render(name: String, w: Int, h: Int, draw: DrawScope.() -> Unit) {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), draw)
        File(out, "$name.png").outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun ships() {
        val seeds = listOf(11, 23, 5, 42, 77, 8, 19)
        val cell = 260f
        render("ships", (cell * 4).toInt(), (cell * seeds.size).toInt()) {
            drawRect(Night.SkyLow)
            seeds.forEachIndexed { r, seed ->
                Tonnage.entries.forEachIndexed { c, t ->
                    val ghost = if (r == seeds.size - 1) c / 3f else 0f
                    ship(Look(seed), t, Offset(c * cell + cell / 2, r * cell + cell * 0.82f), cell * 0.7f, ghost)
                    drawRect(Night.SeaTop, Offset(c * cell, r * cell + cell * 0.82f), Size(cell, cell * 0.18f))
                }
            }
        }
    }

    @Test
    fun bottles() {
        render("bottles", 800, 300) {
            drawRect(Night.SkyTop)
            listOf(11, 8).forEachIndexed { i, seed ->
                bottle(Look(seed), if (i == 0) Tonnage.GALLEON else Tonnage.SCHOONER, i * 0.6f, Rect(Offset(20f + i * 400f, 40f), Size(360f, 240f)))
            }
        }
    }

    @Test
    fun bayScene() {
        render("bay", 1080, 1000) {
            bay(0.45f, listOf(Anchored(1, 42, Tonnage.SCHOONER), Anchored(2, 77, Tonnage.FRIGATE)), 37f, true)
        }
        render("bay-clear", 1080, 1000) { bay(0f, emptyList(), 12f, true) }
    }
}
