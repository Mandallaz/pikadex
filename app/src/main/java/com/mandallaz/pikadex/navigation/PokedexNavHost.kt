package com.mandallaz.pikadex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mandallaz.pikadex.ui.detail.PokedexDetailScreen
import com.mandallaz.pikadex.ui.list.PokedexListScreen
import com.mandallaz.pikadex.ui.team.TeamScreen
import com.mandallaz.pikadex.ui.typechart.TypeTrianglesScreen

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{pokemonName}"
private const val ROUTE_TEAM = "team"
private const val ROUTE_TYPE_TRIANGLES = "type-triangles"

@Composable
fun PokedexNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            PokedexListScreen(
                onPokemonClick = { name -> navController.navigate("detail/$name") },
                onTeamClick = { navController.navigate(ROUTE_TEAM) },
                onTypeTrianglesClick = { navController.navigate(ROUTE_TYPE_TRIANGLES) }
            )
        }
        composable(ROUTE_TYPE_TRIANGLES) {
            TypeTrianglesScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_DETAIL) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("pokemonName").orEmpty()
            PokedexDetailScreen(
                pokemonNameOrId = name,
                onBack = { navController.popBackStack() },
                onPokemonClick = { newName -> navController.navigate("detail/$newName") }
            )
        }
        composable(ROUTE_TEAM) {
            TeamScreen(
                onBack = { navController.popBackStack() },
                onBrowsePokedex = { navController.popBackStack() }
            )
        }
    }
}
