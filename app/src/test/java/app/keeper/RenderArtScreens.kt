package app.keeper

import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.keeper.data.DemoSeed
import app.keeper.ui.Go
import app.keeper.ui.Harbor
import app.keeper.ui.bay.BayTab
import app.keeper.ui.fleet.ChartScreen
import app.keeper.ui.fleet.FleetTab
import app.keeper.ui.fleet.ShelfTab
import app.keeper.ui.fleet.ShipScreen
import app.keeper.ui.log.LogTab
import app.keeper.ui.onboarding.OnboardingScreen
import app.keeper.ui.sight.PassScreen
import app.keeper.ui.sight.SightScreen
import app.keeper.ui.theme.KeeperTheme
import app.keeper.ui.watch.WatchScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every main screen over the demo log, drawn to PNGs (run with `-Prender`), English and Russian: a
 * look at the layouts without a phone. The clock stands at 20:30 so the watch is open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }

    private fun harbor(): Harbor = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KeeperApp>()
        val c = app.container
        DemoSeed.seedIfEmpty(app, c)
        val dao = c.db.dao()
        val now = LocalDate.now().atTime(20, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Harbor(dao.ships(), dao.sightings(), dao.cargo(), c.settings.now(), now)
    }

    private fun shot(name: String, h: Harbor, content: @Composable (Harbor, Go) -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val nav = rememberNavController()
            KeeperTheme { content(h, Go(nav)) }
        }
        rule.mainClock.advanceTimeBy(2_500)
        // Drawn in software straight from the window: the moving sea never lets Compose's own capture settle.
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun bay() = harbor().let { shot("1-bay", it) { h, go -> BayTab(h, go) } }
    @Test fun sight() = harbor().let { shot("2-sight", it) { h, go -> SightScreen(h.fleet.first().ship.id, h, go) } }
    @Test fun pass() = harbor().let { h -> shot("3-pass", h) { hh, go -> PassScreen(hh.sightings.first { it.fateOf == app.keeper.data.Fate.PASSED }.id, hh, go) } }
    @Test fun watch() = harbor().let { shot("4-watch", it) { h, go -> WatchScreen(h, go) } }
    @Test fun log() = harbor().let { shot("5-log", it) { h, go -> LogTab(h, go) } }
    @Test fun fleet() = harbor().let { shot("6-fleet", it) { h, go -> FleetTab(h, go) } }
    @Test fun ship() = harbor().let { shot("7-ship", it) { h, go -> ShipScreen(h.fleet.first { s -> s.ship.lifeboat.isNotEmpty() }.ship.id, h, go) } }
    @Test fun chart() = harbor().let { shot("8-chart", it) { h, go -> ChartScreen(h.logs.first().ship.id, h, go) } }
    @Test fun shelf() = harbor().let { shot("9-shelf", it) { h, go -> ShelfTab(h, go) } }
    @Test fun welcome() = harbor().let { shot("0-welcome", it) { h, _ -> OnboardingScreen(h.settings) {} } }
}

class RenderArtScreensEn : ScreensBase() {
    override val lang = "en"
}

@Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() {
    override val lang = "ru"
}
