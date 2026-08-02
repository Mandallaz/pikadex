package com.tg.pokedex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tg.pokedex.ui.detail.PokedexDetailScreen
import com.tg.pokedex.ui.list.PokedexListScreen
import com.tg.pokedex.ui.team.TeamScreen

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{pokemonName}"
private const val ROUTE_TEAM = "team"

@Composable
fun PokedexNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            PokedexListScreen(
                onPokemonClick = { name -> navController.navigate("detail/$name") },
                onTeamClick = { navController.navigate(ROUTE_TEAM) }
            )
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
