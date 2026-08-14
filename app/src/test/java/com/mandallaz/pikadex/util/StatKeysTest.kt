package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonStatSlot
import com.mandallaz.pikadex.data.remote.dto.PokemonSprites
import org.junit.Assert.assertEquals
import org.junit.Test

class StatKeysTest {

    @Test
    fun `BASE_STATS has exactly the 6 core stats`() {
        assertEquals(
            listOf("hp", "attack", "defense", "special-attack", "special-defense", "speed"),
            BASE_STATS
        )
    }

    @Test
    fun `TOTAL constant matches expected literal value`() {
        assertEquals("total", TOTAL)
    }

    @Test
    fun `PokemonDto baseStatTotal computes correct sum`() {
        val statsList = listOf(
            PokemonStatSlot(baseStat = 80, effort = 0, stat = NamedApiResource("hp", "")),
            PokemonStatSlot(baseStat = 120, effort = 0, stat = NamedApiResource("attack", "")),
            PokemonStatSlot(baseStat = 70, effort = 0, stat = NamedApiResource("defense", "")),
            PokemonStatSlot(baseStat = 60, effort = 0, stat = NamedApiResource("special-attack", "")),
            PokemonStatSlot(baseStat = 70, effort = 0, stat = NamedApiResource("special-defense", "")),
            PokemonStatSlot(baseStat = 100, effort = 0, stat = NamedApiResource("speed", ""))
        )
        val pokemon = PokemonDto(
            id = 1,
            name = "bulbasaur",
            height = 7,
            weight = 69,
            baseExperience = 64,
            types = emptyList(),
            stats = statsList,
            abilities = emptyList(),
            moves = emptyList(),
            sprites = PokemonSprites(null, null, null),
            species = NamedApiResource("bulbasaur", "")
        )

        assertEquals(500, pokemon.baseStatTotal())
    }

    @Test
    fun `PokemonDto baseStatTotal handles empty stats list`() {
        val pokemon = PokemonDto(
            id = 1,
            name = "bulbasaur",
            height = 7,
            weight = 69,
            baseExperience = 64,
            types = emptyList(),
            stats = null,
            abilities = emptyList(),
            moves = emptyList(),
            sprites = PokemonSprites(null, null, null),
            species = NamedApiResource("bulbasaur", "")
        )

        assertEquals(0, pokemon.baseStatTotal())
    }

    @Test
    fun `Map statTotal helper computes correct sum of base stats`() {
        val statsMap = mapOf(
            "hp" to 80,
            "attack" to 120,
            "defense" to 70,
            "special-attack" to 60,
            "special-defense" to 70,
            "speed" to 100,
            "extra-stat" to 150 // should be ignored
        )

        assertEquals(500, statsMap.statTotal())
    }

    @Test
    fun `Map statTotal helper handles missing keys`() {
        val statsMap = mapOf(
            "hp" to 80,
            "attack" to 120
        )

        assertEquals(200, statsMap.statTotal())
    }
}
