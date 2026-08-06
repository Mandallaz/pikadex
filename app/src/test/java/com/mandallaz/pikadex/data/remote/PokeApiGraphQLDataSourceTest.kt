package com.mandallaz.pikadex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * parseBasics is fed a hand-written body matching the real shape verified against the live
 * graphql.pokeapi.co/v1beta2 endpoint (see PokedexRepository/S-1 — introspection + a live sample
 * query against bulbasaur/pikachu/mewtwo were run before writing this).
 */
class PokeApiGraphQLDataSourceTest {

    private val sampleBody = """
        {
          "data": {
            "pokemon": [
              {
                "name": "bulbasaur",
                "pokemonstats": [
                  { "base_stat": 45, "stat": { "name": "hp" } },
                  { "base_stat": 49, "stat": { "name": "attack" } }
                ],
                "pokemontypes": [
                  { "type": { "name": "grass" } },
                  { "type": { "name": "poison" } }
                ],
                "pokemonspecy": { "is_legendary": false, "is_mythical": false }
              },
              {
                "name": "mewtwo",
                "pokemonstats": [
                  { "base_stat": 106, "stat": { "name": "hp" } }
                ],
                "pokemontypes": [
                  { "type": { "name": "psychic" } }
                ],
                "pokemonspecy": { "is_legendary": true, "is_mythical": false }
              },
              {
                "name": "missingno",
                "pokemonstats": [],
                "pokemontypes": null,
                "pokemonspecy": null
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses stats, types and legendary flag for a normal entry`() {
        val basics = PokeApiGraphQLDataSource.parseBasics(sampleBody)
        val bulbasaur = basics.getValue("bulbasaur")

        assertEquals(mapOf("hp" to 45, "attack" to 49), bulbasaur.stats)
        assertEquals(listOf("grass", "poison"), bulbasaur.types)
        assertFalse(bulbasaur.isLegendary)
        assertFalse(bulbasaur.isMythical)
    }

    @Test
    fun `parses a legendary entry`() {
        val mewtwo = PokeApiGraphQLDataSource.parseBasics(sampleBody).getValue("mewtwo")
        assertTrue(mewtwo.isLegendary)
        assertFalse(mewtwo.isMythical)
        assertEquals(listOf("psychic"), mewtwo.types)
    }

    @Test
    fun `a null pokemontypes or pokemonspecy degrades to empty types and non-legendary, not a crash`() {
        val entry = PokeApiGraphQLDataSource.parseBasics(sampleBody).getValue("missingno")
        assertEquals(emptyList<String>(), entry.types)
        assertFalse(entry.isLegendary)
        assertFalse(entry.isMythical)
    }
}
