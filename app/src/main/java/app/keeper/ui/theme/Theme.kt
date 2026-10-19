package app.keeper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.keeper.R

/**
 * A lighthouse at night: deep sea blue, the lamp's warm amber, fog in grey-blue, the logbook's paper
 * for the words. Rust red only on the tower's stripes; a cold green only for ghost ships.
 */
@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val lamp: Color,
    val lampSoft: Color,
    val sea: Color,
    val deep: Color,
    val sky: Color,
    val fog: Color,
    val ghost: Color,
    val rust: Color,
    val calm: Color,
    val paper: Color,
)

val NightPalette = Palette(
    bg = Color(0xFF0B1624),
    surface = Color(0xFF12223A),
    surface2 = Color(0xFF182B45),
    line = Color(0xFF243A57),
    ink = Color(0xFFEFE7D6),
    muted = Color(0xFFA7B3C2),
    faint = Color(0xFF6C7F96),
    lamp = Color(0xFFF4B94E),
    lampSoft = Color(0x29F4B94E),
    sea = Color(0xFF123150),
    deep = Color(0xFF081220),
    sky = Color(0xFF0E1D31),
    fog = Color(0xFF9DB0C5),
    ghost = Color(0xFF8EE3C2),
    rust = Color(0xFFD2654B),
    calm = Color(0xFF7EC3B0),
    paper = Color(0xFFE9DFC8),
)

val LocalPalette = staticCompositionLocalOf { NightPalette }

@OptIn(ExperimentalTextApi::class)
private fun alegreya(w: Int) = Font(R.font.alegreya, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Alegreya: ship names and titles, like a hand on a chart. */
val Serif = FontFamily(alegreya(400), alegreya(500), alegreya(700), alegreya(800))

/** Alegreya Sans for reading. */
val Sans = FontFamily(
    Font(R.font.alegreyasans_regular, FontWeight.Normal),
    Font(R.font.alegreyasans_medium, FontWeight.Medium),
    Font(R.font.alegreyasans_medium, FontWeight.SemiBold),
    Font(R.font.alegreyasans_bold, FontWeight.Bold),
)

/** PT Mono for the log: times, dates and counts, as a keeper would enter them. */
val Mono = FontFamily(Font(R.font.pt_mono, FontWeight.Normal))

object U {
    val c: Palette @Composable get() = LocalPalette.current

    val RXl = RoundedCornerShape(24.dp)
    val RLg = RoundedCornerShape(18.dp)
    val RMd = RoundedCornerShape(14.dp)
    val RSm = RoundedCornerShape(10.dp)
}

object UType {
    val Hero: TextStyle @Composable get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, color = U.c.ink)
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 28.sp, color = U.c.ink)
    val Name: TextStyle @Composable get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 23.sp, color = U.c.ink)
    val Quote: TextStyle @Composable get() = TextStyle(fontFamily = Serif, fontSize = 18.sp, lineHeight = 25.sp, color = U.c.paper)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontSize = 15.5.sp, lineHeight = 22.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.14.em, color = U.c.faint)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 19.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, color = U.c.faint)
    val Log: TextStyle @Composable get() = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp, color = U.c.muted)
    val Number: TextStyle @Composable get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = U.c.ink, fontFeatureSettings = "lnum, tnum")
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

@Composable
fun KeeperTheme(content: @Composable () -> Unit) {
    val p = NightPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = p.lamp,
                onPrimary = p.deep,
                primaryContainer = p.lampSoft,
                onPrimaryContainer = p.ink,
                secondary = p.calm,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface2,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
                error = p.rust,
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = UType.Body,
                bodyMedium = UType.Body.copy(fontSize = 15.sp),
                labelLarge = UType.Button,
            ),
            content = content,
        )
    }
}
