package com.mandallaz.pikadex.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Fetches base stats for every Pokemon in a single request via PokeAPI's GraphQL beta endpoint,
 * instead of ~1300 individual REST calls just to be able to sort the list by a stat — this is
 * exactly the "use GraphQL to fetch only what you need, batch requests" guidance PokeAPI's own
 * best-practice notes ask for.
 */
object PokeApiGraphQLDataSource {

    private const val URL = "https://beta.pokeapi.co/graphql/v1beta"
    private const val QUERY = """
        query {
          pokemon_v2_pokemon(limit: 2000) {
            name
            pokemon_v2_pokemonstats {
              base_stat
              pokemon_v2_stat { name }
            }
          }
        }
    """

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor())
        .build()

    private val gson = Gson()

    /** pokemonName -> (statApiName -> baseStat), e.g. "bulbasaur" -> {"hp" to 45, "attack" to 49, ...}. */
    suspend fun fetchAllBaseStats(): Map<String, Map<String, Int>> = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(mapOf("query" to QUERY)).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(URL).post(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyMap()
            val body = response.body?.string() ?: return@withContext emptyMap()
            val parsed = gson.fromJson(body, GraphQLResponse::class.java)
            parsed.data?.pokemon.orEmpty().associate { p ->
                p.name to p.stats.associate { it.stat.name to it.baseStat }
            }
        }
    }

    private data class GraphQLResponse(val data: GraphQLData?)
    private data class GraphQLData(@SerializedName("pokemon_v2_pokemon") val pokemon: List<GraphQLPokemon>?)
    private data class GraphQLPokemon(
        val name: String,
        @SerializedName("pokemon_v2_pokemonstats") val stats: List<GraphQLStat>
    )
    private data class GraphQLStat(
        @SerializedName("base_stat") val baseStat: Int,
        @SerializedName("pokemon_v2_stat") val stat: GraphQLStatName
    )
    private data class GraphQLStatName(val name: String)
}
