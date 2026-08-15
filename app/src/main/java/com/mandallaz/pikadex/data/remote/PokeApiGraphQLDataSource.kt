package com.mandallaz.pikadex.data.remote

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.mandallaz.pikadex.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches base stats for every Pokemon in a single request via PokeAPI's GraphQL endpoint,
 * instead of ~1300 individual REST calls just to be able to sort the list by a stat — this is
 * exactly the "use GraphQL to fetch only what you need, batch requests" guidance PokeAPI's own
 * best-practice notes ask for.
 *
 * Targets `v1beta2` (`graphql.pokeapi.co`), not the older `v1beta` (`beta.pokeapi.co`) — that
 * spec was announced as sunsetting and was already past its scheduled summer-2025 removal by the
 * time this was checked. The schema also dropped the `pokemon_v2_` prefix from every type in the
 * move, so nothing here shares a field name with the old queries; the shape below was verified
 * against the live endpoint via an introspection query before being written, not guessed from
 * the deprecated console UI.
 */
object PokeApiGraphQLDataSource {

    private const val URL = "https://graphql.pokeapi.co/v1beta2"

    /** [QUERY] and [MOVE_QUERY] ask for this many rows; PokeAPI has ~1300 Pokemon and ~950 moves
     *  today, so this is comfortable headroom, not a real bound. A future dex/movedex growing past
     *  it would truncate *silently* — [logIfTruncated] is the tripwire for that, since raising the
     *  number preemptively just moves the same risk further out rather than removing it. */
    private const val ROW_LIMIT = 2000

    private const val QUERY = """
        query {
          pokemon(limit: $ROW_LIMIT) {
            name
            pokemonstats {
              base_stat
              stat { name }
            }
            pokemontypes {
              type { name }
            }
            pokemonspecy {
              is_legendary
              is_mythical
            }
            pokemonabilities {
              ability { name }
            }
          }
        }
    """

    // priority and movemeta/movemetastatchanges (F37) verified live against graphql.pokeapi.co's
    // introspection before being added — movemeta is a to-one relation modeled as a list (real
    // moves return exactly one entry or none, never more), and movemetastatchanges is its own
    // top-level field on move, not nested under movemeta, despite the name.
    private const val MOVE_QUERY = """
        query {
          move(limit: $ROW_LIMIT) {
            name
            power
            accuracy
            pp
            priority
            type { name }
            movedamageclass { name }
            movemeta {
              crit_rate
              drain
              healing
              flinch_chance
              ailment_chance
              stat_chance
              movemetaailment { name }
            }
            movemetastatchanges {
              change
              stat { name }
            }
          }
        }
    """

    /** Logs, rather than silently truncating, if a result exactly fills [ROW_LIMIT] — the one
     *  shape a real truncation and a coincidental exact count are indistinguishable, but a log is
     *  cheap and a silently-missing 1301st Pokemon is not. */
    private fun logIfTruncated(what: String, count: Int) {
        if (count >= ROW_LIMIT) {
            Log.w("PokeApiGraphQL", "$what returned $count rows, at or above the $ROW_LIMIT limit — results may be truncated")
        }
    }

    internal var client: okhttp3.Call.Factory? = null
    private val okHttpClient get() = client ?: AppContainer.sharedOkHttpClient

    private val moshi = Moshi.Builder().build()
    @Suppress("UNCHECKED_CAST")
    private val queryRequestAdapter = moshi.adapter(Map::class.java) as com.squareup.moshi.JsonAdapter<Map<String, String>>

    /** Runs [query] and hands the response body to [parse].
     *
     *  Throws on any failure rather than returning an empty result. That distinction matters a lot
     *  here: callers memoize this in an [com.mandallaz.pikadex.data.AsyncValueCache] *and* persist
     *  it to disk with a multi-day TTL, both of which treat "returned normally" as success. Handing
     *  back an empty map on a transient 500 therefore didn't degrade gracefully — it cached
     *  emptiness for the rest of the process and wrote it to disk for the next week, silently
     *  breaking stat sorting and every move's type/power/accuracy line with no error anywhere. An
     *  exception instead evicts the cache entry, skips the disk write, and surfaces to the UI. */
    @Suppress("DEPRECATION")
    private suspend fun <T> runQuery(query: String, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val requestBody = queryRequestAdapter.toJson(mapOf("query" to query)).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(URL).post(requestBody).build()
        val call = okHttpClient.newCall(request)

        val response = suspendCancellableCoroutine { continuation ->
            call.enqueue(object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    continuation.resume(response) {
                        response.close()
                    }
                }

                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })

            continuation.invokeOnCancellation {
                call.cancel()
            }
        }

        response.use { r ->
            if (!r.isSuccessful) throw IOException("GraphQL request failed: HTTP ${r.code}")
            val body = r.body?.string() ?: throw IOException("GraphQL response had no body")
            parse(body)
        }
    }

    /** Base stats, types and legendary/mythical status for one pokemon, fetched in bulk — the
     *  shared prerequisite for every feature that needs typing or rarity across the whole dex
     *  (weakness/resistance filtering, legendary badges, team suggestions) without a per-pokemon
     *  REST call. */
    @JsonClass(generateAdapter = true)
    data class PokemonBasics(
        val stats: Map<String, Int>,
        val types: List<String>,
        val isLegendary: Boolean,
        val isMythical: Boolean,
        // F79 — every possible ability (standard or hidden), not just the ones an actual instance
        // has — the app has no per-member ability selection yet (see F81), so team suggestions
        // treat a species as immunity-eligible whenever any of its possible abilities grants one.
        val abilities: List<String> = emptyList()
    )

    /** pokemonName -> [PokemonBasics], e.g. "bulbasaur" -> stats={"hp" to 45, ...}, types=[grass,
     *  poison], isLegendary=false, isMythical=false. */
    suspend fun fetchAllBasics(): Map<String, PokemonBasics> = runQuery(QUERY, ::parseBasics)

    /** Parses [QUERY]'s response body. A separate function (not inlined into [fetchAllBasics])
     *  purely so it's unit-testable against a hand-written JSON body, without a real network call. */
    internal fun parseBasics(body: String): Map<String, PokemonBasics> {
        val pokemon = moshi.adapter(GraphQLResponse::class.java).fromJson(body)?.data?.pokemon
            ?: throw IOException("GraphQL response had no pokemon data")
        logIfTruncated("pokemon", pokemon.size)
        return pokemon.associate { p ->
            p.name to PokemonBasics(
                stats = p.pokemonstats.associate { it.stat.name to it.baseStat },
                types = p.pokemontypes.orEmpty().mapNotNull { it.type?.name },
                isLegendary = p.pokemonspecy?.isLegendary ?: false,
                isMythical = p.pokemonspecy?.isMythical ?: false,
                abilities = p.pokemonabilities.orEmpty().mapNotNull { it.ability?.name }
            )
        }
    }

    @JsonClass(generateAdapter = true)
    internal data class GraphQLResponse(val data: GraphQLData?)
    @JsonClass(generateAdapter = true)
    internal data class GraphQLData(val pokemon: List<GraphQLPokemon>?)
    @JsonClass(generateAdapter = true)
    internal data class GraphQLPokemon(
        val name: String,
        val pokemonstats: List<GraphQLStat>,
        val pokemontypes: List<GraphQLPokemonType>?,
        val pokemonspecy: GraphQLSpecy?,
        val pokemonabilities: List<GraphQLPokemonAbility>?
    )
    @JsonClass(generateAdapter = true)
    internal data class GraphQLStat(
        @field:Json(name = "base_stat") val baseStat: Int,
        val stat: GraphQLStatName
    )
    @JsonClass(generateAdapter = true)
    internal data class GraphQLStatName(val name: String)
    @JsonClass(generateAdapter = true)
    internal data class GraphQLPokemonType(val type: GraphQLTypeName?)
    @JsonClass(generateAdapter = true)
    internal data class GraphQLTypeName(val name: String)
    @JsonClass(generateAdapter = true)
    internal data class GraphQLSpecy(
        @field:Json(name = "is_legendary") val isLegendary: Boolean?,
        @field:Json(name = "is_mythical") val isMythical: Boolean?
    )
    @JsonClass(generateAdapter = true)
    internal data class GraphQLPokemonAbility(val ability: GraphQLAbilityName?)
    @JsonClass(generateAdapter = true)
    internal data class GraphQLAbilityName(val name: String)

    /** A move's type, damage class (physical/special = an attack, status = a buff/debuff/other
     *  non-damaging effect), power and accuracy — null power/accuracy is normal for status moves.
     *  pp is nullable for the same reason as everything else here: a response missing the field
     *  must degrade at the read site, not crash.
     *
     *  [priority]/[critRate]/[drain]/[healing]/[flinchChance]/[ailment]/[ailmentChance] default to
     *  0/"none" rather than null (F37) — that's PokeAPI's own convention for "this move has no such
     *  effect" (verified live: Tackle's movemeta row itself reports crit_rate 0, ailment "none",
     *  not a missing row), so 0/"none" here means the same real "no effect" the API already means,
     *  not "unknown" — the UI layer decides what's worth displaying, not this data class. */
    @JsonClass(generateAdapter = true)
    data class MoveInfo(
        val type: String,
        val damageClass: String,
        val power: Int?,
        val accuracy: Int?,
        val pp: Int?,
        val priority: Int = 0,
        val critRate: Int = 0,
        val drain: Int = 0,
        val healing: Int = 0,
        val flinchChance: Int = 0,
        val ailment: String = "none",
        val ailmentChance: Int = 0,
        val statChanges: List<MoveStatChange> = emptyList(),
        // Chance (%) that statChanges actually applies — distinct from ailmentChance, which governs
        // the separate status-ailment effect on the same move (e.g. a move can inflict a status
        // *and* a stat change at two different probabilities).
        val statChangeChance: Int = 0
    )

    /** One entry of a move's `movemetastatchanges` — [change] is signed (e.g. Swords Dance is
     *  `+2` on `attack`, Acid is `-1` on `special-defense`), not a magnitude plus a separate sign. */
    @JsonClass(generateAdapter = true)
    data class MoveStatChange(val stat: String, val change: Int)

    /** moveName -> info for every move, fetched once in bulk via GraphQL, since showing this
     *  alongside each move in a pokemon's Level Up / TM-HM / Breeding / Tutor lists would otherwise
     *  mean dozens of individual REST calls per pokemon detail screen. */
    suspend fun fetchAllMoveInfo(): Map<String, MoveInfo> = runQuery(MOVE_QUERY, ::parseMoveInfo)

    /** Parses [MOVE_QUERY]'s response body. A separate function (not inlined into
     *  [fetchAllMoveInfo]) purely so it's unit-testable against a hand-written JSON body, without a
     *  real network call — same reasoning as [parseBasics]. */
    internal fun parseMoveInfo(body: String): Map<String, MoveInfo> {
        val moves = moshi.adapter(MoveGraphQLResponse::class.java).fromJson(body)?.data?.move
            ?: throw IOException("GraphQL response had no move data")
        logIfTruncated("move", moves.size)
        return moves.associate { m ->
            // A to-one relation modeled as a list by the schema (see MOVE_QUERY's comment) — real
            // moves have exactly one movemeta row or none, so firstOrNull is the whole story, not a
            // "pick one of several" choice.
            val meta = m.movemeta.orEmpty().firstOrNull()
            m.name to MoveInfo(
                type = m.type?.name ?: "normal",
                damageClass = m.movedamageclass?.name ?: "status",
                power = m.power,
                accuracy = m.accuracy,
                pp = m.pp,
                priority = m.priority ?: 0,
                critRate = meta?.critRate ?: 0,
                drain = meta?.drain ?: 0,
                healing = meta?.healing ?: 0,
                flinchChance = meta?.flinchChance ?: 0,
                ailment = meta?.movemetaailment?.name ?: "none",
                ailmentChance = meta?.ailmentChance ?: 0,
                statChanges = m.movemetastatchanges.orEmpty().map { MoveStatChange(it.stat.name, it.change) },
                statChangeChance = meta?.statChance ?: 0
            )
        }
    }

    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLResponse(val data: MoveGraphQLData?)
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLData(val move: List<MoveGraphQLMove>?)
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLMove(
        val name: String,
        val power: Int?,
        val accuracy: Int?,
        val pp: Int?,
        val priority: Int?,
        val type: MoveGraphQLType?,
        val movedamageclass: MoveGraphQLDamageClass?,
        val movemeta: List<MoveGraphQLMeta>?,
        val movemetastatchanges: List<MoveGraphQLStatChange>?
    )
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLType(val name: String)
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLDamageClass(val name: String)
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLMeta(
        @field:Json(name = "crit_rate") val critRate: Int?,
        val drain: Int?,
        val healing: Int?,
        @field:Json(name = "flinch_chance") val flinchChance: Int?,
        @field:Json(name = "ailment_chance") val ailmentChance: Int?,
        @field:Json(name = "stat_chance") val statChance: Int?,
        val movemetaailment: MoveGraphQLAilment?
    )
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLAilment(val name: String)
    @JsonClass(generateAdapter = true)
    internal data class MoveGraphQLStatChange(val change: Int, val stat: GraphQLStatName)

    // B9 — verified live against graphql.pokeapi.co before being written, same as every other
    // query here: pokemonspeciesnames is the species' names relation (one row per language it has
    // a translation for), reached via pokemonspecy the same way pokemonspecy.is_legendary already
    // is in QUERY above.
    private const val SPECIES_NAMES_QUERY = """
        query {
          pokemon(limit: $ROW_LIMIT) {
            name
            pokemonspecy {
              pokemonspeciesnames {
                name
                language { name }
              }
            }
          }
        }
    """

    /** pokemonName -> (languageCode -> localized species name), fetched once in bulk via GraphQL
     *  (B9) — every species' `names` field already carries every language PokeAPI has a
     *  translation for, same server-side localization [fetchAllBasics]'s genus/flavor-text
     *  siblings already read elsewhere; this is the same data for the species' *name* itself,
     *  which nothing in this app read before B9 (every screen used the raw English/romanized
     *  `name` identifier instead, formatted via `toDisplayName()`). */
    suspend fun fetchAllSpeciesNames(): Map<String, Map<String, String>> = runQuery(SPECIES_NAMES_QUERY, ::parseSpeciesNames)

    /** Parses [SPECIES_NAMES_QUERY]'s response body — separate from [fetchAllSpeciesNames] so it's
     *  unit-testable against a hand-written JSON body, same reasoning as [parseBasics]. */
    internal fun parseSpeciesNames(body: String): Map<String, Map<String, String>> {
        val pokemon = moshi.adapter(SpeciesNamesGraphQLResponse::class.java).fromJson(body)?.data?.pokemon
            ?: throw IOException("GraphQL response had no pokemon data")
        logIfTruncated("pokemon (species names)", pokemon.size)
        return pokemon.associate { p ->
            p.name to p.pokemonspecy?.pokemonspeciesnames.orEmpty()
                .associate { it.language.name to it.name }
        }
    }

    @JsonClass(generateAdapter = true)
    internal data class SpeciesNamesGraphQLResponse(val data: SpeciesNamesGraphQLData?)
    @JsonClass(generateAdapter = true)
    internal data class SpeciesNamesGraphQLData(val pokemon: List<SpeciesNamesGraphQLPokemon>?)
    @JsonClass(generateAdapter = true)
    internal data class SpeciesNamesGraphQLPokemon(val name: String, val pokemonspecy: SpeciesNamesGraphQLSpecy?)
    @JsonClass(generateAdapter = true)
    internal data class SpeciesNamesGraphQLSpecy(val pokemonspeciesnames: List<SpeciesNamesGraphQLName>?)
    @JsonClass(generateAdapter = true)
    internal data class SpeciesNamesGraphQLName(val name: String, val language: GraphQLStatName)

    // B11 — same pattern as B9's SPECIES_NAMES_QUERY, verified live against graphql.pokeapi.co
    // before being written: movenames/abilitynames are the move/ability names relations, one row
    // per language each has a translation for.
    private const val MOVE_NAMES_QUERY = """
        query {
          move(limit: $ROW_LIMIT) {
            name
            movenames {
              name
              language { name }
            }
          }
        }
    """

    private const val ABILITY_NAMES_QUERY = """
        query {
          ability(limit: $ROW_LIMIT) {
            name
            abilitynames {
              name
              language { name }
            }
          }
        }
    """

    /** moveName -> (languageCode -> localized move name), fetched once in bulk via GraphQL (B11) —
     *  same reasoning as [fetchAllSpeciesNames]: nothing read this before, every screen showed the
     *  raw English/hyphenated move identifier formatted via `toDisplayName()` regardless of the
     *  picked language. */
    suspend fun fetchAllMoveNames(): Map<String, Map<String, String>> = runQuery(MOVE_NAMES_QUERY, ::parseMoveNames)

    /** abilityName -> (languageCode -> localized ability name), fetched once in bulk via GraphQL
     *  (B11) — same reasoning as [fetchAllMoveNames]. */
    suspend fun fetchAllAbilityNames(): Map<String, Map<String, String>> = runQuery(ABILITY_NAMES_QUERY, ::parseAbilityNames)

    /** Parses [MOVE_NAMES_QUERY]'s response body — separate from [fetchAllMoveNames] so it's
     *  unit-testable against a hand-written JSON body, same reasoning as [parseSpeciesNames]. */
    internal fun parseMoveNames(body: String): Map<String, Map<String, String>> {
        val moves = moshi.adapter(MoveNamesGraphQLResponse::class.java).fromJson(body)?.data?.move
            ?: throw IOException("GraphQL response had no move data")
        logIfTruncated("move (names)", moves.size)
        return moves.associate { m -> m.name to m.movenames.orEmpty().associate { it.language.name to it.name } }
    }

    /** Parses [ABILITY_NAMES_QUERY]'s response body — same reasoning as [parseMoveNames]. */
    internal fun parseAbilityNames(body: String): Map<String, Map<String, String>> {
        val abilities = moshi.adapter(AbilityNamesGraphQLResponse::class.java).fromJson(body)?.data?.ability
            ?: throw IOException("GraphQL response had no ability data")
        logIfTruncated("ability (names)", abilities.size)
        return abilities.associate { a -> a.name to a.abilitynames.orEmpty().associate { it.language.name to it.name } }
    }

    @JsonClass(generateAdapter = true)
    internal data class MoveNamesGraphQLResponse(val data: MoveNamesGraphQLData?)
    @JsonClass(generateAdapter = true)
    internal data class MoveNamesGraphQLData(val move: List<MoveNamesGraphQLMove>?)
    @JsonClass(generateAdapter = true)
    internal data class MoveNamesGraphQLMove(val name: String, val movenames: List<SpeciesNamesGraphQLName>?)

    @JsonClass(generateAdapter = true)
    internal data class AbilityNamesGraphQLResponse(val data: AbilityNamesGraphQLData?)
    @JsonClass(generateAdapter = true)
    internal data class AbilityNamesGraphQLData(val ability: List<AbilityNamesGraphQLAbility>?)
    @JsonClass(generateAdapter = true)
    internal data class AbilityNamesGraphQLAbility(val name: String, val abilitynames: List<SpeciesNamesGraphQLName>?)
}
