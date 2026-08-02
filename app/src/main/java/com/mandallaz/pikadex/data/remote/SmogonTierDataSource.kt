package com.mandallaz.pikadex.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches competitive tier placements (OU, UU, LC...) per Pokemon per generation from Pokemon
 * Showdown's own data files (github.com/smogon/pokemon-showdown), since PokeAPI has no notion of
 * competitive tiers. These are plain-text TypeScript data files (not JSON, not a hosted API), but
 * their structure is a flat, regular `key: { tier: "...", ... },` object literal per Pokemon, small
 * (a few KB to ~90KB per generation) and cheap to fetch once and cache — unlike Smogon's own dex.
 * pages which are a full ~1MB SPA bundle each.
 */
object SmogonTierDataSource {

    // Smogon dex generation code -> Showdown's mod folder name. Gen 9 lives at the data/ root
    // (the "current" generation has no mod folder).
    private val GEN_CODE_TO_MOD = mapOf(
        "rb" to "gen1", "gs" to "gen2", "rs" to "gen3", "dp" to "gen4",
        "bw" to "gen5", "xy" to "gen6", "sm" to "gen7", "ss" to "gen8"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor())
        .build()

    private val ENTRY_REGEX = Regex("""(\w+):\s*\{([^{}]*)\}""")
    private val TIER_REGEX = Regex(""""?tier"?:\s*"([^"]+)"""")

    /** A Pokemon's Showdown data key strips out all hyphens from its PokeAPI name (e.g. "mr-mime" -> "mrmime"). */
    fun showdownKey(pokemonName: String): String = pokemonName.replace("-", "")

    /** Returns pokemonKey (Showdown format, no hyphens) -> tier code, for a Smogon generation code. */
    suspend fun fetchTiers(genCode: String): Map<String, String> = withContext(Dispatchers.IO) {
        val url = if (genCode == "sv") {
            "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/formats-data.ts"
        } else {
            val mod = GEN_CODE_TO_MOD[genCode] ?: return@withContext emptyMap()
            "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/mods/$mod/formats-data.ts"
        }
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyMap()
            val body = response.body?.string() ?: return@withContext emptyMap()
            parseTiers(body)
        }
    }

    private fun parseTiers(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in ENTRY_REGEX.findAll(text)) {
            val key = match.groupValues[1]
            val body = match.groupValues[2]
            val tier = TIER_REGEX.find(body)?.groupValues?.get(1) ?: continue
            if (tier != "Illegal") result[key] = tier
        }
        return result
    }
}
