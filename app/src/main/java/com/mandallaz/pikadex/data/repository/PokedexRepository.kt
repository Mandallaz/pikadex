package com.mandallaz.pikadex.data.repository

import com.google.gson.reflect.TypeToken
import com.mandallaz.pikadex.data.AsyncCache
import com.mandallaz.pikadex.data.AsyncValueCache
import com.mandallaz.pikadex.data.JsonDiskCache
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.PokeApiService
import com.mandallaz.pikadex.data.remote.SmogonTierDataSource
import com.mandallaz.pikadex.data.remote.dto.AbilityDetailDto
import com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto
import com.mandallaz.pikadex.data.remote.dto.MoveDetailDto
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import com.mandallaz.pikadex.util.MoveCategory
import com.mandallaz.pikadex.util.localizedOrEnglish
import com.mandallaz.pikadex.util.movesForCategory
import com.mandallaz.pikadex.util.TypeIds
import java.util.concurrent.TimeUnit
import retrofit2.HttpException

data class PokemonDetailBundle(
    val pokemon: PokemonDto,
    val species: PokemonSpeciesDto,
    val evolutionChain: EvolutionChainDto?
)

/** [PokedexRepository]'s public surface, extracted (F50) as the one seam ViewModel unit tests
 *  actually need — every ViewModel already took its repository via constructor injection, but
 *  typed against the concrete class there was nothing to substitute a test double for. The
 *  singleton `object`s elsewhere in `data/` (TeamRepository, FavoritesRepository,
 *  PokedexListContext, PrefetchManager, the `*Settings` objects) turned out not to need the same
 *  treatment: each already no-ops safely to a sane default when used without its `init(Context)`
 *  ever being called, which is exactly what a JVM unit test that never touches Android does. */
interface PokedexRepositoryApi {
    suspend fun getMasterList(): List<NamedApiResource>
    suspend fun getTypes(): List<NamedApiResource>
    suspend fun getMoveNames(): List<String>
    suspend fun getAbilityNames(): List<String>
    suspend fun getFormVersionGroup(nameOrId: String): String?
    suspend fun getTypeDetail(type: String): TypeDetailDto
    suspend fun getPokemonNamesForType(type: String): Set<String>
    suspend fun getPokemonNamesForMove(move: String): Set<String>
    suspend fun getPokemonNamesForAbility(ability: String): Set<String>
    suspend fun getAbilityDescription(ability: String): String?
    suspend fun getPokemonTypes(nameOrId: String): List<String>
    suspend fun getPokemonLevelUpMoveNames(nameOrId: String): List<String>
    suspend fun getSmogonTiers(genCode: String): Map<String, String>
    suspend fun getAllBasics(): Map<String, PokeApiGraphQLDataSource.PokemonBasics>
    suspend fun getAllBaseStats(): Map<String, Map<String, Int>>
    suspend fun getAllMoveInfo(): Map<String, PokeApiGraphQLDataSource.MoveInfo>
    suspend fun getStatPercentile(statKey: String, value: Int): Double
    suspend fun getPokemonDetailBundle(nameOrId: String): PokemonDetailBundle
}

/**
 * Access layer for PokeAPI data. Keeps the global lists (pokemon/moves/abilities/types) in memory
 * since they never change during the process lifetime, avoiding re-downloading ~1300 entries on
 * every screen. Every cache is [AsyncCache]/[AsyncValueCache] rather than a plain map, since
 * several callers now fetch concurrently (team matchups, detail screen) and a plain
 * `getOrPut { suspendCall() }` lets concurrent callers race past the cache check before either
 * one's fetch completes.
 */
class PokedexRepository(private val api: PokeApiService) : PokedexRepositoryApi {

    private val masterListCache = AsyncValueCache<List<NamedApiResource>>()
    private val moveNamesCache = AsyncValueCache<List<String>>()
    private val abilityNamesCache = AsyncValueCache<List<String>>()
    private val typesCache = AsyncValueCache<List<NamedApiResource>>()

    // Bounded: one entry per pokemon, ~1300 of them and growing, so unbounded meant a full
    // browse of the dex kept every single detail response alive for the rest of the process.
    private val pokemonDetailCache = AsyncCache<String, PokemonDto>(maxSize = 200)
    private val speciesCache = AsyncCache<Int, PokemonSpeciesDto>()
    private val evolutionChainCache = AsyncCache<Int, EvolutionChainDto>()
    private val typeDetailCache = AsyncCache<String, TypeDetailDto>()
    private val formCache = AsyncCache<String, String?>()
    private val moveDetailCache = AsyncCache<String, MoveDetailDto>()
    private val abilityDetailCache = AsyncCache<String, AbilityDetailDto>()
    private val smogonTierCache = AsyncCache<String, Map<String, String>>()
    private val allBasicsCache = AsyncValueCache<Map<String, PokeApiGraphQLDataSource.PokemonBasics>>()
    private val allMoveInfoCache = AsyncValueCache<Map<String, PokeApiGraphQLDataSource.MoveInfo>>()
    private val sortedStatArraysCache = AsyncValueCache<Map<String, IntArray>>()

    override suspend fun getMasterList(): List<NamedApiResource> =
        masterListCache.get { api.getPokemonList(limit = 100000).results }

    override suspend fun getTypes(): List<NamedApiResource> = typesCache.get {
        val order = TypeIds.standardTypeNames
        api.getTypeList().results
            .filterNot { it.name == "unknown" || it.name == "stellar" || it.name == "shadow" }
            // indexOf returns -1 for a type this app doesn't know about (one PokeAPI adds after
            // this list was written), which sorted it ahead of Normal. Unknown types belong at
            // the end — they're already absent from every matchup calculation, since those
            // iterate standardTypeNames rather than this fetched list.
            .sortedBy { order.indexOf(it.name).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
    }

    // Sorted alphabetically — PokeAPI returns these in id order (Pound, Karate Chop, Double
    // Slap...), which is effectively random for a picker the user has to scan through.
    override suspend fun getMoveNames(): List<String> =
        moveNamesCache.get { api.getMoveList(limit = 100000).results.map { it.name }.sorted() }

    override suspend fun getAbilityNames(): List<String> =
        abilityNamesCache.get { api.getAbilityList(limit = 100000).results.map { it.name }.sorted() }

    /** Which games a form was introduced in ("x-y", "mega-dimension"...), or null for a Pokémon
     *  that has no distinct form entry. Used to decide whether Smogon actually covers a form. */
    override suspend fun getFormVersionGroup(nameOrId: String): String? =
        formCache.get(nameOrId) { api.getPokemonForm(nameOrId).versionGroup?.name }

    /** Full type detail (including damage_relations), cached per type name. */
    override suspend fun getTypeDetail(type: String): TypeDetailDto =
        typeDetailCache.get(type) { api.getType(type) }

    /** Names of pokemon that have this type (the /type endpoint already does the reverse lookup). */
    override suspend fun getPokemonNamesForType(type: String): Set<String> {
        val detail = getTypeDetail(type)
        return detail.pokemon.orEmpty().map { it.pokemon.name }.toSet()
    }

    /** Names of pokemon that can learn this move. */
    override suspend fun getPokemonNamesForMove(move: String): Set<String> {
        val detail = moveDetailCache.get(move) { api.getMove(move) }
        return detail.learnedByPokemon.orEmpty().map { it.name }.toSet()
    }

    /** Names of pokemon that can have this ability. */
    override suspend fun getPokemonNamesForAbility(ability: String): Set<String> {
        val detail = abilityDetailCache.get(ability) { api.getAbility(ability) }
        return detail.pokemon.orEmpty().map { it.pokemon.name }.toSet()
    }

    /** Description of an ability (e.g. "Levitate" -> "Gives full immunity to Ground type moves."),
     *  since PokeAPI's ability names alone are often unclear on their own — in whichever language
     *  [LanguageSettings] currently resolves to (F35's game-data axis), falling back to English.
     *  Read once per call rather than reactively: this is fetched once per [getPokemonDetailBundle]
     *  load, not re-derived on every recomposition — switching language mid-session re-localizes on
     *  the next load, not retroactively. */
    override suspend fun getAbilityDescription(ability: String): String? {
        val detail = abilityDetailCache.get(ability) { api.getAbility(ability) }
        return detail.effectEntries.localizedOrEnglish(LanguageSettings.currentLanguage.value) { it.language.name }?.shortEffect
    }

    /** Just the type names for a pokemon, without pulling the full detail bundle (species, evolution chain). */
    override suspend fun getPokemonTypes(nameOrId: String): List<String> {
        val pokemon = pokemonDetailCache.get(nameOrId) { api.getPokemon(nameOrId) }
        return pokemon.types.orEmpty().map { it.type.name }
    }

    /**
     * The moves this pokemon learns by levelling up. Shares [pokemonDetailCache] with
     * [getPokemonTypes], so asking for both costs one fetch rather than two.
     *
     * Level-up rather than the whole movepool: nearly every pokemon can be taught a TM covering
     * nearly every attacking type, so a coverage matrix built from everything learnable came out a
     * uniform wall of ×2 and reported "no coverage gaps" for any team whatsoever. What a pokemon
     * learns on its own is the discriminating signal.
     */
    override suspend fun getPokemonLevelUpMoveNames(nameOrId: String): List<String> {
        val pokemon = pokemonDetailCache.get(nameOrId) { api.getPokemon(nameOrId) }
        return pokemon.movesForCategory(MoveCategory.LEVEL_UP).map { it.moveName }
    }

    /** pokemonKey (Showdown format, no hyphens) -> tier code, for a Smogon generation (e.g. "ss"). */
    override suspend fun getSmogonTiers(genCode: String): Map<String, String> =
        smogonTierCache.get(genCode) { SmogonTierDataSource.fetchTiers(genCode) }

    /** pokemonName -> [PokeApiGraphQLDataSource.PokemonBasics] (stats, types, legendary/mythical),
     *  fetched once in bulk via GraphQL. Also persisted to disk (GraphQL is POST, so the shared
     *  HTTP cache can't cover it) — this data only changes when a new generation ships, so there's
     *  no reason to re-fetch ~1300 entries every cold start. */
    override suspend fun getAllBasics(): Map<String, PokeApiGraphQLDataSource.PokemonBasics> = allBasicsCache.get {
        diskCached(BASICS_CACHE_KEY, BASICS_TYPE) { PokeApiGraphQLDataSource.fetchAllBasics() }
    }

    /** Thin derived view of [getAllBasics] kept for callers that only care about stats (sorting) —
     *  they don't need to know types/rarity exist at all. */
    override suspend fun getAllBaseStats(): Map<String, Map<String, Int>> = getAllBasics().mapValues { it.value.stats }

    /** Serves [key] from the disk cache if it's still fresh, otherwise runs [fetch] and persists the
     *  result. [fetch] throwing propagates without writing anything, so a failed request can never
     *  persist a placeholder (an empty map, say) for the whole TTL. */
    private suspend fun <T : Any> diskCached(key: String, type: java.lang.reflect.Type, fetch: suspend () -> T): T =
        JsonDiskCache.read<T>(key, type, DISK_CACHE_MAX_AGE_MILLIS)
            ?: fetch().also { JsonDiskCache.write(key, it) }

    /** moveName -> (type, damage class, power, accuracy), fetched once in bulk via GraphQL and
     *  reused for every pokemon's move lists (Level Up / TM-HM / Breeding / Tutor). Persisted to
     *  disk for the same reason as [getAllBaseStats]. */
    override suspend fun getAllMoveInfo(): Map<String, PokeApiGraphQLDataSource.MoveInfo> = allMoveInfoCache.get {
        diskCached(MOVE_INFO_CACHE_KEY, MOVE_INFO_TYPE) { PokeApiGraphQLDataSource.fetchAllMoveInfo() }
    }

    /** Sorted value arrays per stat key (hp/attack/.../speed, plus a synthetic "total"), built
     *  once from the bulk stats map. [getStatPercentile] binary-searches these instead of
     *  re-scanning all ~1300 pokemon's values on every single pokemon detail load. Runs on
     *  [AsyncValueCache]'s default dispatcher (Default, not Main) since sorting ~1300 values
     *  7 times over is real CPU work, not just a cache lookup. */
    private suspend fun getSortedStatArrays(): Map<String, IntArray> = sortedStatArraysCache.get {
        val allStats = getAllBaseStats()
        BASE_STAT_KEYS.associateWith { key ->
            allStats.values.mapNotNull { it[key] }.sorted().toIntArray()
        } + mapOf(
            "total" to allStats.values.map { stats -> BASE_STAT_KEYS.sumOf { stats[it] ?: 0 } }.sorted().toIntArray()
        )
    }

    /** Fraction of every other pokemon's same stat that [value] is greater-or-equal to (0.0..1.0)
     *  — ties split evenly so a value shared by many pokemon doesn't get pushed to either extreme.
     *  [statKey] is one of [BASE_STAT_KEYS] or the synthetic "total". */
    override suspend fun getStatPercentile(statKey: String, value: Int): Double {
        val sorted = getSortedStatArrays()[statKey] ?: return 0.5
        if (sorted.isEmpty()) return 0.5
        val below = sorted.lowerBound(value)
        val belowOrEqual = sorted.lowerBound(value + 1)
        val equal = belowOrEqual - below
        return ((below + equal / 2.0) / sorted.size).coerceIn(0.0, 1.0)
    }

    /** Index of the first element >= [value] (i.e. count of elements strictly less than [value]). */
    private fun IntArray.lowerBound(value: Int): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (this[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    override suspend fun getPokemonDetailBundle(nameOrId: String): PokemonDetailBundle {
        val pokemon = pokemonDetailCache.get(nameOrId) { fetchPokemonResolvingDefaultVariety(nameOrId) }
        // Alternate forms (mega/gmax/regional/gender/cosmetic...) have a pokemon.id in the 10000+
        // range that does NOT match any pokemon-species id — the species must be looked up via the
        // "species" reference embedded in the pokemon payload instead (e.g. basculegion-female,
        // pokemon id 10248, belongs to species "basculegion", id 902).
        val speciesKey = pokemon.species.id ?: pokemon.id
        val species = speciesCache.get(speciesKey) { api.getPokemonSpecies(pokemon.species.name) }
        val chainId = species.evolutionChain?.id
        val chain = chainId?.let { id -> evolutionChainCache.get(id) { api.getEvolutionChain(id) } }
        return PokemonDetailBundle(pokemon, species, chain)
    }

    /**
     * issue #18 — [nameOrId] resolves directly for almost every call site, but a species
     * reached only via its evolution chain (e.g. Kubfu's evolution result names the *species*
     * "urshifu", which has no bare-name Pokémon resource of its own — only its named varieties
     * `urshifu-single-strike`/`urshifu-rapid-strike` exist) 404s on a plain `/pokemon/{name}`
     * fetch, previously surfacing as an opaque "check your connection" error for a request that
     * never touched connectivity at all.
     *
     * Falls back to `pokemon-species/{name}` (which *does* resolve for a bare species name) and
     * retries with its default variety's Pokémon name — the same `is_default` resolution
     * Deoxys/Giratina-style split-form species already need elsewhere (see F20's dataset notes).
     * Left as a fallback behind a normal fetch, not a first step, since it costs an extra request
     * only in this narrow case rather than doubling every ordinary lookup.
     */
    private suspend fun fetchPokemonResolvingDefaultVariety(nameOrId: String): PokemonDto =
        try {
            api.getPokemon(nameOrId)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            val defaultVarietyName = api.getPokemonSpecies(nameOrId).varieties.orEmpty()
                .firstOrNull { it.isDefault }?.pokemon?.name
                ?: throw e
            api.getPokemon(defaultVarietyName)
        }

    private companion object {
        val BASE_STAT_KEYS = listOf("hp", "attack", "defense", "special-attack", "special-defense", "speed")
        // New key (not base_stats_v2 renamed): the payload shape changed (stats+types+rarity, not
        // just stats), so an upgrading install must re-fetch rather than trying to read the old
        // shape back as the new one.
        const val BASICS_CACHE_KEY = "pokemon_basics_v1"
        // _v3: MoveInfo gained a `pp` field — bumped so an upgrading install re-fetches instead of
        // reading back an old cached payload with no pp in it and showing "—" for every move.
        const val MOVE_INFO_CACHE_KEY = "move_info_v3"
        val DISK_CACHE_MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(7)

        val BASICS_TYPE: java.lang.reflect.Type =
            object : TypeToken<Map<String, PokeApiGraphQLDataSource.PokemonBasics>>() {}.type
        val MOVE_INFO_TYPE: java.lang.reflect.Type =
            object : TypeToken<Map<String, PokeApiGraphQLDataSource.MoveInfo>>() {}.type
    }
}
