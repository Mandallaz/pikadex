package com.mandallaz.pikadex.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.ui.detail.PokedexDetailScreen
import com.mandallaz.pikadex.ui.list.PokedexListScreen
import com.mandallaz.pikadex.ui.team.TeamScreen
import com.mandallaz.pikadex.ui.typechart.TypeTrianglesScreen

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{pokemonName}"
private const val ROUTE_TEAM = "team"
private const val ROUTE_TYPE_TRIANGLES = "type-triangles"

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

// Pokédex/Triangles/Team icons used to live as unlabelled icon buttons scattered across each
// screen's own top bar (and vanished on whichever screen didn't happen to show them) — a single
// always-visible, labelled bottom bar makes all three destinations equally discoverable from
// anywhere and makes the current one obvious via the selected state.
private val BOTTOM_TABS = listOf(
    BottomTab(ROUTE_LIST, "Pokédex", Icons.Default.Home),
    BottomTab(ROUTE_TYPE_TRIANGLES, "Triangles", Icons.Default.ChangeHistory),
    BottomTab(ROUTE_TEAM, "Team", Icons.Default.Groups)
)

@Composable
fun PokedexNavHost(navController: NavHostController = rememberNavController()) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val team by TeamRepository.team.collectAsState()

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
        bottomBar = {
            // Hidden on the pushed Detail screen — that's reached *from* the Pokédex tab, not a
            // destination of its own, so showing the bar there would offer a confusing 4th "tab"
            // that's really just a worse Back button.
            if (BOTTOM_TABS.any { it.route == currentRoute }) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
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
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LIST,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_LIST) {
                PokedexListScreen(onPokemonClick = { name -> navController.navigate("detail/$name") })
            }
            composable(ROUTE_TYPE_TRIANGLES) {
                TypeTrianglesScreen()
            }
            composable(ROUTE_DETAIL) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("pokemonName").orEmpty()
                PokedexDetailScreen(
                    pokemonNameOrId = name,
                    onBack = { navController.popBackStack() },
                    onPokemonClick = { newName -> navController.navigate("detail/$newName") },
                    onViewTypeTriangles = { switchTab(ROUTE_TYPE_TRIANGLES) }
                )
            }
            composable(ROUTE_TEAM) {
                TeamScreen(onBrowsePokedex = { switchTab(ROUTE_LIST) })
            }
        }
    }
}
