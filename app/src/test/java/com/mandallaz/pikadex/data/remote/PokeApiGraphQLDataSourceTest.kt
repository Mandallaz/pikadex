package com.mandallaz.pikadex.data.remote

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
                "pokemonspecy": { "is_legendary": false, "is_mythical": false, "generation": { "name": "generation-i" } },
                "pokemonabilities": [
                  { "ability": { "name": "overgrow" } },
                  { "ability": { "name": "chlorophyll" } }
                ]
              },
              {
                "name": "mewtwo",
                "pokemonstats": [
                  { "base_stat": 106, "stat": { "name": "hp" } }
                ],
                "pokemontypes": [
                  { "type": { "name": "psychic" } }
                ],
                "pokemonspecy": { "is_legendary": true, "is_mythical": false, "generation": { "name": "generation-i" } },
                "pokemonabilities": [
                  { "ability": { "name": "pressure" } }
                ]
              },
              {
                "name": "missingno",
                "pokemonstats": [],
                "pokemontypes": null,
                "pokemonspecy": null,
                "pokemonabilities": null
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
        assertEquals(listOf("overgrow", "chlorophyll"), bulbasaur.abilities)
        assertEquals("generation-i", bulbasaur.generation)
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
        assertEquals(emptyList<String>(), entry.abilities)
        assertEquals("", entry.generation)
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

    // --- fetchAllSpeciesNames / parseSpeciesNames (B9) --------------------------------

    // Shape verified live against graphql.pokeapi.co/v1beta2 for bulbasaur before writing this —
    // see SPECIES_NAMES_QUERY's own comment.
    private val speciesNamesSampleBody = """
        {
          "data": {
            "pokemon": [
              {
                "name": "bulbasaur",
                "pokemonspecy": {
                  "pokemonspeciesnames": [
                    { "name": "Bulbasaur", "language": { "name": "en" } },
                    { "name": "Bulbizarre", "language": { "name": "fr" } },
                    { "name": "Bisasam", "language": { "name": "de" } }
                  ]
                }
              },
              {
                "name": "missingno",
                "pokemonspecy": null
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses one language-code-to-name entry per species`() {
        val names = PokeApiGraphQLDataSource.parseSpeciesNames(speciesNamesSampleBody).getValue("bulbasaur")
        assertEquals(mapOf("en" to "Bulbasaur", "fr" to "Bulbizarre", "de" to "Bisasam"), names)
    }

    @Test
    fun `a null pokemonspecy degrades to an empty name map, not a crash`() {
        val names = PokeApiGraphQLDataSource.parseSpeciesNames(speciesNamesSampleBody).getValue("missingno")
        assertEquals(emptyMap<String, String>(), names)
    }

    // --- fetchAllMoveNames / parseMoveNames (B11) ---------------------------------

    // Shape verified live against graphql.pokeapi.co/v1beta2 for tackle before writing this — see
    // MOVE_NAMES_QUERY's own comment.
    private val moveNamesSampleBody = """
        {
          "data": {
            "move": [
              {
                "name": "tackle",
                "movenames": [
                  { "name": "Tackle", "language": { "name": "en" } },
                  { "name": "Charge", "language": { "name": "fr" } },
                  { "name": "Tackle", "language": { "name": "de" } }
                ]
              },
              {
                "name": "no-names-move",
                "movenames": null
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses one language-code-to-name entry per move`() {
        val names = PokeApiGraphQLDataSource.parseMoveNames(moveNamesSampleBody).getValue("tackle")
        assertEquals(mapOf("en" to "Tackle", "fr" to "Charge", "de" to "Tackle"), names)
    }

    @Test
    fun `a null movenames degrades to an empty name map, not a crash`() {
        val names = PokeApiGraphQLDataSource.parseMoveNames(moveNamesSampleBody).getValue("no-names-move")
        assertEquals(emptyMap<String, String>(), names)
    }

    // --- fetchAllAbilityNames / parseAbilityNames (B11) ---------------------------

    // Shape verified live against graphql.pokeapi.co/v1beta2 for levitate before writing this —
    // see ABILITY_NAMES_QUERY's own comment.
    private val abilityNamesSampleBody = """
        {
          "data": {
            "ability": [
              {
                "name": "levitate",
                "abilitynames": [
                  { "name": "Levitate", "language": { "name": "en" } },
                  { "name": "Lévitation", "language": { "name": "fr" } }
                ]
              },
              {
                "name": "no-names-ability",
                "abilitynames": null
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses one language-code-to-name entry per ability`() {
        val names = PokeApiGraphQLDataSource.parseAbilityNames(abilityNamesSampleBody).getValue("levitate")
        assertEquals(mapOf("en" to "Levitate", "fr" to "Lévitation"), names)
    }

    @Test
    fun `a null abilitynames degrades to an empty name map, not a crash`() {
        val names = PokeApiGraphQLDataSource.parseAbilityNames(abilityNamesSampleBody).getValue("no-names-ability")
        assertEquals(emptyMap<String, String>(), names)
    }

    // --- async & cancellation tests (B49) ----------------------------------------

    @After
    fun tearDown() {
        PokeApiGraphQLDataSource.client = null
    }

    private class FakeCall(val request: Request) : Call {
        var canceled = false
        var executed = false
        var callback: Callback? = null

        override fun request(): Request = request

        override fun execute(): Response {
            executed = true
            Thread.sleep(200)
            val responseBody = "{}".toResponseBody("application/json".toMediaType())
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("OK")
                .code(200)
                .body(responseBody)
                .build()
        }

        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
        }

        override fun cancel() {
            canceled = true
            callback?.onFailure(this, IOException("Canceled"))
        }

        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = this
    }

    @Test
    fun `test runQuery observes coroutine cancellation and cancels OkHttp Call`() = runTest {
        var createdCall: FakeCall? = null
        val fakeFactory = object : Call.Factory {
            override fun newCall(request: Request): Call {
                val call = FakeCall(request)
                createdCall = call
                return call
            }
        }

        PokeApiGraphQLDataSource.client = fakeFactory

        val job = launch {
            try {
                PokeApiGraphQLDataSource.fetchAllBasics()
            } catch (e: Exception) {
                // ignore
            }
        }

        while (createdCall?.executed != true) {
            yield()
        }

        assertFalse(createdCall!!.canceled)

        job.cancelAndJoin()

        assertTrue("OkHttp Call should have been cancelled", createdCall!!.canceled)
    }

    @Test
    fun `test runQuery handles successful async response`() = runTest {
        var createdCall: FakeCall? = null
        val fakeFactory = object : Call.Factory {
            override fun newCall(request: Request): Call {
                val call = FakeCall(request)
                createdCall = call
                return call
            }
        }

        PokeApiGraphQLDataSource.client = fakeFactory

        val job = launch {
            val basics = PokeApiGraphQLDataSource.fetchAllBasics()
            assertEquals(emptyMap<String, Any>(), basics)
        }

        while (createdCall?.executed != true) {
            yield()
        }

        val responseBody = """
            {
              "data": {
                "pokemon": []
              }
            }
        """.trimIndent().toResponseBody("application/json".toMediaType())
        val response = Response.Builder()
            .request(createdCall!!.request)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .code(200)
            .body(responseBody)
            .build()

        createdCall!!.callback!!.onResponse(createdCall!!, response)

        job.join()
    }
}
