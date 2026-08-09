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

    // --- fetchAllMoveInfo / parseMoveInfo (F37: priority + movemeta) --------------------

    // Shape verified live against graphql.pokeapi.co/v1beta2 for swords-dance, acid and
    // quick-attack before writing this — see MOVE_QUERY's own comment on why movemetastatchanges
    // sits at the move level, not nested under movemeta.
    private val moveSampleBody = """
        {
          "data": {
            "move": [
              {
                "name": "tackle",
                "power": 40,
                "accuracy": 100,
                "pp": 35,
                "priority": 0,
                "type": { "name": "normal" },
                "movedamageclass": { "name": "physical" },
                "movemeta": [
                  { "crit_rate": 0, "drain": 0, "healing": 0, "flinch_chance": 0, "ailment_chance": 0, "stat_chance": 0, "movemetaailment": { "name": "none" } }
                ],
                "movemetastatchanges": []
              },
              {
                "name": "quick-attack",
                "power": 40,
                "accuracy": 100,
                "pp": 30,
                "priority": 1,
                "type": { "name": "normal" },
                "movedamageclass": { "name": "physical" },
                "movemeta": [
                  { "crit_rate": 0, "drain": 0, "healing": 0, "flinch_chance": 0, "ailment_chance": 0, "stat_chance": 0, "movemetaailment": { "name": "none" } }
                ],
                "movemetastatchanges": []
              },
              {
                "name": "acid",
                "power": 40,
                "accuracy": 100,
                "pp": 30,
                "priority": 0,
                "type": { "name": "poison" },
                "movedamageclass": { "name": "special" },
                "movemeta": [
                  { "crit_rate": 0, "drain": 0, "healing": 0, "flinch_chance": 0, "ailment_chance": 0, "stat_chance": 10, "movemetaailment": { "name": "none" } }
                ],
                "movemetastatchanges": [
                  { "change": -1, "stat": { "name": "special-defense" } }
                ]
              },
              {
                "name": "thunder-wave",
                "power": null,
                "accuracy": 90,
                "pp": 20,
                "priority": 0,
                "type": { "name": "electric" },
                "movedamageclass": { "name": "status" },
                "movemeta": [
                  { "crit_rate": 0, "drain": 0, "healing": 0, "flinch_chance": 0, "ailment_chance": 0, "stat_chance": 0, "movemetaailment": { "name": "paralysis" } }
                ],
                "movemetastatchanges": []
              },
              {
                "name": "no-meta-move",
                "power": 40,
                "accuracy": 100,
                "pp": 20,
                "priority": null,
                "type": { "name": "normal" },
                "movedamageclass": { "name": "physical" },
                "movemeta": [],
                "movemetastatchanges": []
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `a move with no secondary effects parses to MoveInfo's no-effect defaults`() {
        val tackle = PokeApiGraphQLDataSource.parseMoveInfo(moveSampleBody).getValue("tackle")
        assertEquals(0, tackle.priority)
        assertEquals(0, tackle.critRate)
        assertEquals("none", tackle.ailment)
        assertEquals(emptyList<PokeApiGraphQLDataSource.MoveStatChange>(), tackle.statChanges)
    }

    @Test
    fun `priority is parsed for a priority move`() {
        val quickAttack = PokeApiGraphQLDataSource.parseMoveInfo(moveSampleBody).getValue("quick-attack")
        assertEquals(1, quickAttack.priority)
    }

    @Test
    fun `a stat-lowering move parses its stat change and chance`() {
        val acid = PokeApiGraphQLDataSource.parseMoveInfo(moveSampleBody).getValue("acid")
        assertEquals(listOf(PokeApiGraphQLDataSource.MoveStatChange("special-defense", -1)), acid.statChanges)
        assertEquals(10, acid.statChangeChance)
    }

    // ailment_chance 0 alongside a real ailment (not "none") is PokeAPI's own convention for a
    // guaranteed effect, not "0% chance of nothing" — Thunder Wave's paralysis is unconditional.
    @Test
    fun `an always-on status ailment has a real ailment name with zero chance`() {
        val thunderWave = PokeApiGraphQLDataSource.parseMoveInfo(moveSampleBody).getValue("thunder-wave")
        assertEquals("paralysis", thunderWave.ailment)
        assertEquals(0, thunderWave.ailmentChance)
    }

    // An empty movemeta list (no row at all, distinct from a row of all-zero values) and a null
    // priority both degrade to MoveInfo's defaults rather than crashing.
    @Test
    fun `an empty movemeta list and null priority degrade to defaults, not a crash`() {
        val entry = PokeApiGraphQLDataSource.parseMoveInfo(moveSampleBody).getValue("no-meta-move")
        assertEquals(0, entry.priority)
        assertEquals(0, entry.critRate)
        assertEquals("none", entry.ailment)
    }
}
