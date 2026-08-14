package com.mandallaz.pikadex.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSprites
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shiny/animated/cry buttons moved from the top app bar into [DetailContent]'s own header,
 * next to the sprite they affect (issue #50). This renders [DetailContent] directly (no
 * ViewModel/network) and asserts the 3 buttons are part of it.
 */
@RunWith(AndroidJUnit4::class)
class PokedexDetailScreenLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakePokemon() = PokemonDto(
        id = 25,
        name = "pikachu",
        height = 4,
        weight = 60,
        baseExperience = null,
        types = null,
        stats = null,
        abilities = null,
        moves = null,
        sprites = PokemonSprites(null, null, null),
        species = NamedApiResource("pikachu", "https://pokeapi.co/api/v2/pokemon-species/25/")
    )

    private fun fakeSpecies() = PokemonSpeciesDto(
        id = 25,
        name = "pikachu",
        evolutionChain = null,
        flavorTextEntries = null,
        genera = null,
        color = NamedApiResource("yellow", "https://pokeapi.co/api/v2/pokemon-color/yellow/"),
        eggGroups = null,
        generation = NamedApiResource("generation-i", "https://pokeapi.co/api/v2/generation/1/"),
        isLegendary = false,
        isMythical = false,
        varieties = null
    )

    @Test
    fun spriteToggleButtonsAreRenderedByDetailContent() {
        composeTestRule.setContent {
            DetailContent(
                data = DetailData(
                    pokemon = fakePokemon(),
                    species = fakeSpecies()
                ),
                onToggleShiny = {},
                onToggleAnimated = {},
                onToggleFrontBackSprites = {},
                onPlayCry = {},
                onPokemonClick = {},
                onViewTypeTriangles = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Show shiny coloring").assertExists()
        composeTestRule.onNodeWithContentDescription("Show animated battle sprite").assertExists()
        composeTestRule.onNodeWithContentDescription("Show front and back").assertExists()
        composeTestRule.onNodeWithContentDescription("Play cry").assertExists()
    }
}
