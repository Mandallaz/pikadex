package com.mandallaz.pikadex.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.ui.compare.CompareScreen
import com.mandallaz.pikadex.ui.detail.PokedexDetailScreen
import com.mandallaz.pikadex.ui.list.PokedexListScreen
import com.mandallaz.pikadex.ui.settings.SettingsScreen
import com.mandallaz.pikadex.ui.team.TeamScreen
import com.mandallaz.pikadex.ui.typechart.TypeTrianglesScreen

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{pokemonName}"
private const val ROUTE_TEAM = "team"
private const val ROUTE_TYPE_TRIANGLES = "type-triangles"
private const val ROUTE_COMPARE = "compare/{leftName}/{rightName}"
private const val ROUTE_SETTINGS = "settings"

/** Below this screen height, the bottom nav bar's items switch from icon-above-label to
 *  icon-beside-label (issue #48) — landscape on a phone has ~400dp to work with, and a label
 *  under every icon there eats a disproportionate share of the whole screen's height. */
private val COMPACT_NAV_BAR_MAX_HEIGHT = 500.dp

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun PokedexNavHost(navController: NavHostController = rememberNavController()) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val team by TeamRepository.team.collectAsState()

    // Built inside the composable (not hoisted to a top-level val) so it re-resolves via
    // stringResource() on a language change (F35) instead of being fixed at class-init time.
    // A single always-visible, labelled bottom bar makes all four destinations equally
    // discoverable from anywhere and makes the current one obvious via the selected state.
    val bottomTabs = listOf(
        BottomTab(ROUTE_LIST, stringResource(R.string.nav_pokedex), Icons.Default.Home),
        BottomTab(ROUTE_TYPE_TRIANGLES, stringResource(R.string.nav_triangles), Icons.Default.ChangeHistory),
        BottomTab(ROUTE_TEAM, stringResource(R.string.nav_team), Icons.Default.Groups),
        BottomTab(ROUTE_SETTINGS, stringResource(R.string.nav_settings), Icons.Default.Settings)
    )

    // Both taps of a fast double-tap (on a Pokémon card, an evolution stage, or Back) are dispatched
    // before the first one's navigation takes effect. A destination that has already begun
    // navigating away is no longer RESUMED, so gating on that swallows the second tap without
    // needing to track any state of our own.
    fun ifIdle(action: () -> Unit) {
        // Read off the controller rather than the composed `currentBackStackEntry` state so the
        // check reflects the back stack at tap time, not at the last recomposition.
        if (navController.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
            action()
        }
    }

    // Standard single-top-per-tab pattern: switching tabs never stacks duplicates of the same
    // destination, and each tab keeps its own scroll/state (restoreState) while away.
    fun switchTab(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // Every screen already has its own Scaffold+TopAppBar that reserves the top system-bar
        // inset, so zeroing it here means this Scaffold's only job is reserving space for the
        // bottom nav bar itself (whose own NavigationBar composable already handles the bottom
        // system-bar inset internally).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Hidden on the pushed Detail screen — that's reached *from* the Pokédex tab, not a
            // destination of its own, so showing the bar there would offer a confusing 4th "tab"
            // that's really just a worse Back button.
            if (bottomTabs.any { it.route == currentRoute }) {
                // ShortNavigationBar is Material3's compact bar variant — the standard NavigationBar
                // reserves a fixed 80dp container regardless of content, which on device left a
                // disproportionate chunk of the screen for a 4-item tab strip (GitHub issue F30).
                val isCompactHeight = LocalConfiguration.current.screenHeightDp.dp < COMPACT_NAV_BAR_MAX_HEIGHT
                val iconPosition = if (isCompactHeight) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top
                ShortNavigationBar {
                    bottomTabs.forEach { tab ->
                        ShortNavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { switchTab(tab.route) },
                            icon = {
                                if (tab.route == ROUTE_TEAM && team.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${team.size}") } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                            iconPosition = iconPosition
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LIST,
            // consumeWindowInsets, not just padding: each screen has its own Scaffold, which by
            // default reserves the bottom system-bar inset itself — on top of the bottom-bar height
            // already reserved here (and the NavigationBar handles that same inset internally too).
            // Marking this Scaffold's padding as consumed leaves each inner Scaffold nothing left
            // to add.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable(ROUTE_LIST) {
                PokedexListScreen(onPokemonClick = { name -> ifIdle { navController.navigate("detail/$name") } })
            }
            composable(ROUTE_TYPE_TRIANGLES) {
                // Reached either as a bottom tab or pushed from a Pokémon's "View chart" — only the
                // pushed instance gets a Back arrow, since there's nothing to go back to from the tab.
                val pushedFromDetail = navController.previousBackStackEntry?.destination?.route == ROUTE_DETAIL
                TypeTrianglesScreen(
                    onBack = if (pushedFromDetail) ({ ifIdle { navController.popBackStack() } }) else null
                )
            }
            composable(ROUTE_DETAIL) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("pokemonName").orEmpty()
                PokedexDetailScreen(
                    pokemonNameOrId = name,
                    onBack = { ifIdle { navController.popBackStack() } },
                    onPokemonClick = { newName -> ifIdle { navController.navigate("detail/$newName") } },
                    // A plain push (not switchTab's popUpTo-to-start pattern) — this is a
                    // cross-reference link from *within* a Pokémon's page, not the user picking the
                    // Triangles tab, so Back should return to this Pokémon, not to the Pokédex list.
                    onViewTypeTriangles = {
                        ifIdle { navController.navigate(ROUTE_TYPE_TRIANGLES) { launchSingleTop = true } }
                    },
                    onCompare = { left, right ->
                        ifIdle { navController.navigate("compare/$left/$right") }
                    },
                    // Replaces the current back-stack entry rather than pushing (same
                    // popUpTo(...){inclusive=true} pattern the Compare screen's own re-navigate
                    // already uses), so Back always returns to wherever the user actually entered
                    // the detail flow from, not back through every Pokémon swiped past on the way.
                    onNavigateAdjacent = { newName ->
                        ifIdle {
                            navController.navigate("detail/$newName") {
                                popUpTo(ROUTE_DETAIL) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(ROUTE_COMPARE) { backStackEntry ->
                val left = backStackEntry.arguments?.getString("leftName").orEmpty()
                val right = backStackEntry.arguments?.getString("rightName").orEmpty()
                CompareScreen(
                    leftName = left,
                    rightName = right,
                    onBack = { ifIdle { navController.popBackStack() } },
                    // Re-navigates rather than mutating in-place state (swap, or picking a
                    // different pokemon for either side) — popUpTo replaces this back-stack entry
                    // instead of pushing another, so Back from a re-compared screen doesn't step
                    // through every prior pairing on the way out.
                    onRecompare = { newLeft, newRight ->
                        ifIdle {
                            navController.navigate("compare/$newLeft/$newRight") {
                                popUpTo(ROUTE_COMPARE) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(ROUTE_TEAM) {
                TeamScreen(
                    onBrowsePokedex = { switchTab(ROUTE_LIST) },
                    // issue #17 — a plain push, same as the Pokédex list's own onPokemonClick,
                    // so Back returns to the Team screen rather than anywhere else.
                    onPokemonClick = { name -> ifIdle { navController.navigate("detail/$name") } }
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
