package com.mandallaz.pikadex.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mandallaz.pikadex.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

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
          }
        }
    """

    private const val MOVE_QUERY = """
        query {
          move(limit: $ROW_LIMIT) {
            name
            power
            accuracy
            type { name }
            movedamageclass { name }
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

    private val client get() = AppContainer.sharedOkHttpClient

    private val gson = Gson()

    /** Runs [query] and hands the response body to [parse].
     *
     *  Throws on any failure rather than returning an empty result. That distinction matters a lot
     *  here: callers memoize this in an [com.mandallaz.pikadex.data.AsyncValueCache] *and* persist
     *  it to disk with a multi-day TTL, both of which treat "returned normally" as success. Handing
     *  back an empty map on a transient 500 therefore didn't degrade gracefully — it cached
     *  emptiness for the rest of the process and wrote it to disk for the next week, silently
     *  breaking stat sorting and every move's type/power/accuracy line with no error anywhere. An
     *  exception instead evicts the cache entry, skips the disk write, and surfaces to the UI. */
    private suspend fun <T> runQuery(query: String, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(mapOf("query" to query)).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(URL).post(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GraphQL request failed: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("GraphQL response had no body")
            parse(body)
        }
    }

    /** pokemonName -> (statApiName -> baseStat), e.g. "bulbasaur" -> {"hp" to 45, "attack" to 49, ...}. */
    suspend fun fetchAllBaseStats(): Map<String, Map<String, Int>> = runQuery(QUERY) { body ->
        val pokemon = gson.fromJson(body, GraphQLResponse::class.java)?.data?.pokemon
            ?: throw IOException("GraphQL response had no pokemon data")
        logIfTruncated("pokemon", pokemon.size)
        pokemon.associate { p -> p.name to p.pokemonstats.associate { it.stat.name to it.baseStat } }
    }

    private data class GraphQLResponse(val data: GraphQLData?)
    private data class GraphQLData(val pokemon: List<GraphQLPokemon>?)
    private data class GraphQLPokemon(val name: String, val pokemonstats: List<GraphQLStat>)
    private data class GraphQLStat(
        @SerializedName("base_stat") val baseStat: Int,
        val stat: GraphQLStatName
    )
    private data class GraphQLStatName(val name: String)

    /** A move's type, damage class (physical/special = an attack, status = a buff/debuff/other
     *  non-damaging effect), power and accuracy — null power/accuracy is normal for status moves. */
    data class MoveInfo(val type: String, val damageClass: String, val power: Int?, val accuracy: Int?)

    /** moveName -> info for every move, fetched once in bulk via GraphQL, since showing this
     *  alongside each move in a pokemon's Level Up / TM-HM / Breeding / Tutor lists would otherwise
     *  mean dozens of individual REST calls per pokemon detail screen. */
    suspend fun fetchAllMoveInfo(): Map<String, MoveInfo> = runQuery(MOVE_QUERY) { body ->
        val moves = gson.fromJson(body, MoveGraphQLResponse::class.java)?.data?.move
            ?: throw IOException("GraphQL response had no move data")
        logIfTruncated("move", moves.size)
        moves.associate { m ->
            m.name to MoveInfo(
                type = m.type?.name ?: "normal",
                damageClass = m.movedamageclass?.name ?: "status",
                power = m.power,
                accuracy = m.accuracy
            )
        }
    }

    private data class MoveGraphQLResponse(val data: MoveGraphQLData?)
    private data class MoveGraphQLData(val move: List<MoveGraphQLMove>?)
    private data class MoveGraphQLMove(
        val name: String,
        val power: Int?,
        val accuracy: Int?,
        val type: MoveGraphQLType?,
        val movedamageclass: MoveGraphQLDamageClass?
    )
    private data class MoveGraphQLType(val name: String)
    private data class MoveGraphQLDamageClass(val name: String)
}
