package app.keeper.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.keeper.BuildConfig
import app.keeper.R
import app.keeper.container
import app.keeper.data.Cargo
import app.keeper.data.Settings
import app.keeper.data.Ship
import app.keeper.data.Sighting
import app.keeper.domain.Fleet
import app.keeper.domain.ShipLog
import app.keeper.domain.WatchClock
import app.keeper.notify.Alarms
import app.keeper.ui.bay.BayTab
import app.keeper.ui.fleet.ChartScreen
import app.keeper.ui.fleet.FleetTab
import app.keeper.ui.fleet.ShelfTab
import app.keeper.ui.fleet.ShipScreen
import app.keeper.ui.log.LogTab
import app.keeper.ui.night.NightScreen
import app.keeper.ui.onboarding.OnboardingScreen
import app.keeper.ui.settings.SettingsScreen
import app.keeper.ui.sight.PassScreen
import app.keeper.ui.sight.SightScreen
import app.keeper.ui.theme.Sans
import app.keeper.ui.theme.U
import app.keeper.ui.watch.WatchScreen
import app.keeper.ui.components.rememberNow
import java.time.LocalDate
import java.time.ZoneId

enum class Tab { BAY, LOG, FLEET, SHELF }

/** Everything stored, plus what is derived from it, recomputed every minute or on change. */
@Immutable
class Harbor(
    val ships: List<Ship>,
    val sightings: List<Sighting>,
    val cargo: List<Cargo>,
    val settings: Settings,
    val now: Long,
) {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = Fleet.date(now, zone)

    val logs: List<ShipLog> by lazy { Fleet.logs(ships, sightings, cargo) }
    private val byId: Map<Long, ShipLog> by lazy { logs.associateBy { it.ship.id } }
    fun log(id: Long): ShipLog? = byId[id]
    fun ship(id: Long): Ship? = byId[id]?.ship

    /** Ships still sailing in, the latest first. */
    val fleet: List<ShipLog> by lazy { logs.filter { !it.bottled(now) }.sortedByDescending { it.lastSeen } }

    /** Ships gone quiet for a month, in their bottles, the newest bottled first. */
    val shelf: List<ShipLog> by lazy { logs.filter { it.bottled(now) && it.count > 0 }.sortedByDescending { it.bottledAt } }

    /** Waiting at anchor for the watch, oldest first. */
    val roadstead: List<Sighting> by lazy { sightings.filter { it.anchored }.sortedBy { it.at } }

    val todays: List<Sighting> by lazy { sightings.filter { Fleet.date(it.at, zone) == today } }
    val fog: Float by lazy { Fleet.fog(sightings, today, zone) }
    val due: List<Cargo> by lazy { Fleet.due(cargo, today) }
    /** The screenshot build always finds the lamp lit. */
    val watch: WatchClock.State get() = if (app.keeper.SHOTS) WatchClock.State.OPEN else WatchClock.state(settings, now, zone)
    val ledger: Fleet.Ledger by lazy { Fleet.ledger(cargo) }
}

/** Everything a screen can open. */
class Go(private val nav: NavHostController) {
    fun sight(ship: Long = 0) = nav.navigate("sight?ship=$ship") { launchSingleTop = true }
    fun pass(sighting: Long) = nav.navigate("pass/$sighting") {
        popUpTo("main") { inclusive = false }
        launchSingleTop = true
    }
    fun watch() = nav.navigate("watch") { launchSingleTop = true }
    fun ship(id: Long) = nav.navigate("ship/$id") { launchSingleTop = true }
    fun chart(id: Long) = nav.navigate("chart/$id") { launchSingleTop = true }
    fun night() = nav.navigate("night") { launchSingleTop = true }
    fun settings() = nav.navigate("settings") { launchSingleTop = true }
    fun home() { nav.popBackStack("main", false) }
    fun back() { if (!nav.popBackStack()) home() }
}

@Composable
fun KeeperRoot(settings: Settings) {
    val context = ctx()
    val container = context.container
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(Tab.BAY) }
    val start = remember { if (settings.onboarded) "main" else "welcome" }
    val dao = container.db.dao()

    val ships by dao.observeShips().collectAsStateWithLifecycle(initialValue = null)
    val sightings by dao.observeSightings().collectAsStateWithLifecycle(initialValue = null)
    val cargo by dao.observeCargo().collectAsStateWithLifecycle(initialValue = null)
    val tick = rememberNow(60_000)
    val harbor = remember(ships, sightings, cargo, settings, tick) {
        Harbor(ships.orEmpty(), sightings.orEmpty(), cargo.orEmpty(), settings, System.currentTimeMillis())
    }
    val go = remember(nav) { Go(nav) }

    // Onboarded elsewhere (a restored backup, the demo seed): leave the welcome screens.
    // Keyed on the route too: the demo seed can finish before the nav graph has its first destination.
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(settings.onboarded, route) {
        if (settings.onboarded && route == "welcome") nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
    }
    // A tap from the shade, the tile or a shortcut.
    val open by container.open.collectAsStateWithLifecycle()
    LaunchedEffect(open, ships != null, settings.onboarded) {
        val (where, _) = open ?: return@LaunchedEffect
        if (ships == null || !settings.onboarded) return@LaunchedEffect
        container.open.value = null
        go.home()
        when (where) {
            "sight" -> go.sight()
            "night" -> go.night()
            "watch" -> go.watch()
        }
    }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(settings.onboarded) {
        // Asked once there is a watch to remind of.
        if (settings.onboarded && android.os.Build.VERSION.SDK_INT >= 33 && !Alarms.canNotify(context) && !BuildConfig.DEMO) {
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(U.c.bg)) {
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.background(U.c.bg),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable("welcome") {
                OnboardingScreen(settings) { watchAt ->
                    container.write { this.settings.update { it.copy(onboarded = true, watchAt = watchAt) } }
                    nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
                }
            }
            composable("main") { MainTabs(tab, { tab = it }, go, harbor) }
            composable("sight?ship={ship}", arguments = listOf(navArgument("ship") { type = NavType.LongType; defaultValue = 0L })) { e ->
                SightScreen(e.arguments?.getLong("ship") ?: 0L, harbor, go)
            }
            composable("pass/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PassScreen(e.arguments?.getLong("id") ?: 0L, harbor, go)
            }
            composable("watch") { WatchScreen(harbor, go) }
            composable("ship/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ShipScreen(e.arguments?.getLong("id") ?: 0L, harbor, go)
            }
            composable("chart/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ChartScreen(e.arguments?.getLong("id") ?: 0L, harbor, go)
            }
            composable("night") { NightScreen(harbor, go) }
            composable("settings") { SettingsScreen(harbor, go) }
        }
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, go: Go, harbor: Harbor) {
    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.BAY -> BayTab(harbor, go)
                Tab.LOG -> LogTab(harbor, go)
                Tab.FLEET -> FleetTab(harbor, go)
                Tab.SHELF -> ShelfTab(harbor, go)
            }
        }
        HorizontalDivider(color = U.c.line)
        Row(
            Modifier.fillMaxWidth().background(U.c.surface).navigationBarsPadding().height(66.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Water, stringResource(R.string.tab_bay), tab == Tab.BAY) { onTab(Tab.BAY) }
            NavItem(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.tab_log), tab == Tab.LOG) { onTab(Tab.LOG) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.offset(y = (-10).dp).size(62.dp).shadow(10.dp, CircleShape, ambientColor = U.c.lamp, spotColor = U.c.lamp).clip(CircleShape)
                        .background(U.c.lamp)
                        .border(3.dp, U.c.surface, CircleShape)
                        .clickable { go.sight() },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_tile), stringResource(R.string.sight_cd), Modifier.size(30.dp), tint = U.c.deep) }
            }
            NavItem(Icons.Rounded.DirectionsBoat, stringResource(R.string.tab_fleet), tab == Tab.FLEET) { onTab(Tab.FLEET) }
            NavItem(Icons.Rounded.Inventory2, stringResource(R.string.tab_shelf), tab == Tab.SHELF) { onTab(Tab.SHELF) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) U.c.lamp else U.c.faint
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) U.c.lampSoft else Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
        Spacer(Modifier.height(3.dp))
        Text(label, fontFamily = Sans, fontSize = 12.5.sp, color = color, maxLines = 1)
    }
}
