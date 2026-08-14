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
import com.mandallaz.pikadex.util.BASE_STATS
import com.mandallaz.pikadex.util.TOTAL
import com.mandallaz.pikadex.util.statTotal
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
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
    /** F108 — master species name -> dex id, built once from the cached [getMasterList]. Several
     *  screens (team suggestions, preset previews, prefetch tiers, detail-screen name lookups)
     *  used to rebuild this same name->id map themselves via `mapNotNull { it.id }` blocks. */
    suspend fun masterIdByName(): Map<String, Int>
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
    suspend fun getAllSpeciesNames(): Map<String, Map<String, String>>
    suspend fun getAllMoveLocalizedNames(): Map<String, Map<String, String>>
    suspend fun getAllAbilityLocalizedNames(): Map<String, Map<String, String>>
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
    private val masterIdByNameCache = AsyncValueCache<Map<String, Int>>()
    private val moveNamesCache = AsyncValueCache<List<String>>()
    private val abilityNamesCache = AsyncValueCache<List<String>>()
    private val typesCache = AsyncValueCache<List<NamedApiResource>>()

    // Bounded: one entry per pokemon, ~1300 of them and growing, so unbounded meant a full
    // browse of the dex kept every single detail response alive for the rest of the process.
    // speciesCache and evolutionChainCache are populated on the exact same code path
    // (getPokemonDetailBundle) and are of the same order (~1000 species, ~1000 chains), so they're
    // bounded the same way; moveDetailCache/abilityDetailCache are populated by their own,
    // separately-sized key spaces (~950 moves, ~350 abilities) but the same unbounded-growth
    // concern applies.
    private val pokemonDetailCache = AsyncCache<String, PokemonDto>(maxSize = 200)
    private val speciesCache = AsyncCache<Int, PokemonSpeciesDto>(maxSize = 200)
    private val evolutionChainCache = AsyncCache<Int, EvolutionChainDto>(maxSize = 200)
    private val typeDetailCache = AsyncCache<String, TypeDetailDto>()
    private val formCache = AsyncCache<String, String?>()
    private val moveDetailCache = AsyncCache<String, MoveDetailDto>(maxSize = 200)
    private val abilityDetailCache = AsyncCache<String, AbilityDetailDto>(maxSize = 200)
    private val smogonTierCache = AsyncCache<String, Map<String, String>>()
    private val allBasicsCache = AsyncValueCache<Map<String, PokeApiGraphQLDataSource.PokemonBasics>>()
    private val allMoveInfoCache = AsyncValueCache<Map<String, PokeApiGraphQLDataSource.MoveInfo>>()
    private val allSpeciesNamesCache = AsyncValueCache<Map<String, Map<String, String>>>()
    private val allMoveLocalizedNamesCache = AsyncValueCache<Map<String, Map<String, String>>>()
    private val allAbilityLocalizedNamesCache = AsyncValueCache<Map<String, Map<String, String>>>()
    private val sortedStatArraysCache = AsyncValueCache<Map<String, IntArray>>()

    override suspend fun getMasterList(): List<NamedApiResource> =
        masterListCache.get { api.getPokemonList(limit = 100000).results }

    /** F108 — derived once from [getMasterList] and cached: the mapping itself is immutable (it
     *  only changes when a new game generation ships a species, same cadence as the master list),
     *  so building it fresh on every caller that wants name->id was pure duplicated work. */
    override suspend fun masterIdByName(): Map<String, Int> = masterIdByNameCache.get {
        getMasterList().mapNotNull { r -> r.id?.let { r.name to it } }.toMap()
    }

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
     *  result. [fetch] failing never persists a placeholder (an empty map, say) for the whole TTL —
     *  but before propagating that failure, B29's stale-on-failure fallback tries a cached entry
     *  past its TTL: this data "only changes when a new game generation ships" (see
     *  [JsonDiskCache]'s own doc), so serving it stale on a failed refresh is strictly better than
     *  an offline device getting nothing. Only reached when [fetch] actually throws — a normal
     *  within-TTL hit never touches this path. */
    private suspend fun <T : Any> diskCached(key: String, type: java.lang.reflect.Type, fetch: suspend () -> T): T {
        JsonDiskCache.read<T>(key, type, DISK_CACHE_MAX_AGE_MILLIS)?.let { return it }
        return try {
            fetch().also { JsonDiskCache.write(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JsonDiskCache.readStale<T>(key, type) ?: throw e
        }
    }

    /** moveName -> (type, damage class, power, accuracy), fetched once in bulk via GraphQL and
     *  reused for every pokemon's move lists (Level Up / TM-HM / Breeding / Tutor). Persisted to
     *  disk for the same reason as [getAllBaseStats]. */
    override suspend fun getAllMoveInfo(): Map<String, PokeApiGraphQLDataSource.MoveInfo> = allMoveInfoCache.get {
        diskCached(MOVE_INFO_CACHE_KEY, MOVE_INFO_TYPE) { PokeApiGraphQLDataSource.fetchAllMoveInfo() }
    }

    /** pokemonName -> (languageCode -> localized species name), fetched once in bulk via GraphQL
     *  (B9). Persisted to disk for the same reason as [getAllBaseStats]/[getAllMoveInfo] — this
     *  only changes when PokeAPI adds/corrects a translation, not on any cadence this app's users
     *  would notice, so there's no reason to re-fetch ~1300 entries' worth of names every cold
     *  start just to read one language's worth back out of it. */
    override suspend fun getAllSpeciesNames(): Map<String, Map<String, String>> = allSpeciesNamesCache.get {
        diskCached(SPECIES_NAMES_CACHE_KEY, SPECIES_NAMES_TYPE) { PokeApiGraphQLDataSource.fetchAllSpeciesNames() }
    }

    /** moveName -> (languageCode -> localized move name), fetched once in bulk via GraphQL (B11) —
     *  same reasoning/caching as [getAllSpeciesNames]. */
    override suspend fun getAllMoveLocalizedNames(): Map<String, Map<String, String>> = allMoveLocalizedNamesCache.get {
        diskCached(MOVE_NAMES_CACHE_KEY, SPECIES_NAMES_TYPE) { PokeApiGraphQLDataSource.fetchAllMoveNames() }
    }

    /** abilityName -> (languageCode -> localized ability name), fetched once in bulk via GraphQL
     *  (B11) — same reasoning/caching as [getAllSpeciesNames]. */
    override suspend fun getAllAbilityLocalizedNames(): Map<String, Map<String, String>> = allAbilityLocalizedNamesCache.get {
        diskCached(ABILITY_NAMES_CACHE_KEY, SPECIES_NAMES_TYPE) { PokeApiGraphQLDataSource.fetchAllAbilityNames() }
    }

    /** Sorted value arrays per stat key (hp/attack/.../speed, plus a synthetic "total"), built
     *  once from the bulk stats map. [getStatPercentile] binary-searches these instead of
     *  re-scanning all ~1300 pokemon's values on every single pokemon detail load. Runs on
     *  [AsyncValueCache]'s default dispatcher (Default, not Main) since sorting ~1300 values
     *  7 times over is real CPU work, not just a cache lookup. */
    private suspend fun getSortedStatArrays(): Map<String, IntArray> = sortedStatArraysCache.get {
        val allStats = getAllBaseStats()
        BASE_STATS.associateWith { key ->
            allStats.values.mapNotNull { it[key] }.sorted().toIntArray()
        } + mapOf(
            TOTAL to allStats.values.map { stats -> stats.statTotal() }.sorted().toIntArray()
        )
    }

    /** Fraction of every other pokemon's same stat that [value] is greater-or-equal to (0.0..1.0)
     *  — ties split evenly so a value shared by many pokemon doesn't get pushed to either extreme.
     *  [statKey] is one of [BASE_STATS] or the synthetic [TOTAL]. */
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

    // B23 — the three REST calls behind a detail bundle (~100-300KB each, ~1300 of them for the
    // Full Detail prefetch tier) used to be persisted only in the 200-entry in-memory caches above
    // and AppContainer's 20MB HTTP cache, so a full prefetch's ~250-400MB round-trip mostly
    // evicted itself before it finished: opening an early-dex Pokémon offline after "completing"
    // the prefetch still hit "check your connection". Persisted through JsonDiskCache the same way
    // the bulk GraphQL fetches already are — gzipped, no size cap (proportional to what the user
    // actually prefetched, not an arbitrary ceiling), and already counted in
    // [com.mandallaz.pikadex.ui.settings.SettingsViewModel.measureStorage]'s storage readout since
    // that already sums [JsonDiskCache.sizeBytes].
    override suspend fun getPokemonDetailBundle(nameOrId: String): PokemonDetailBundle =
        diskCached("detail_bundle_v1_$nameOrId", POKEMON_DETAIL_BUNDLE_TYPE) {
            val pokemon = pokemonDetailCache.get(nameOrId) { fetchPokemonResolvingDefaultVariety(nameOrId) }
            // Alternate forms (mega/gmax/regional/gender/cosmetic...) have a pokemon.id in the
            // 10000+ range that does NOT match any pokemon-species id — the species must be
            // looked up via the "species" reference embedded in the pokemon payload instead (e.g.
            // basculegion-female, pokemon id 10248, belongs to species "basculegion", id 902).
            val speciesKey = pokemon.species.id ?: pokemon.id
            val species = speciesCache.get(speciesKey) { api.getPokemonSpecies(pokemon.species.name) }
            val chainId = species.evolutionChain?.id
            val chain = chainId?.let { id -> evolutionChainCache.get(id) { api.getEvolutionChain(id) } }
            PokemonDetailBundle(pokemon, species, chain)
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
        // New key (not base_stats_v2 renamed): the payload shape changed (stats+types+rarity, not
        // just stats), so an upgrading install must re-fetch rather than trying to read the old
        // shape back as the new one.
        const val BASICS_CACHE_KEY = "pokemon_basics_v1"
        // _v3: MoveInfo gained a `pp` field — bumped so an upgrading install re-fetches instead of
        // reading back an old cached payload with no pp in it and showing "—" for every move.
        const val MOVE_INFO_CACHE_KEY = "move_info_v3"
        const val SPECIES_NAMES_CACHE_KEY = "species_names_v1"
        const val MOVE_NAMES_CACHE_KEY = "move_names_v1"
        const val ABILITY_NAMES_CACHE_KEY = "ability_names_v1"
        // B29 — was 7 days, which contradicted JsonDiskCache's own doc ("a generous TTL (weeks)")
        // and meant the ESSENTIALS prefetch tier's whole offline promise silently expired a week
        // after the user ran it. This data only changes when a new game generation ships (every
        // few years), so 180 days is still a safety net, not a real staleness signal — and
        // diskCached's stale-on-failure fallback (see that function) covers the rest.
        val DISK_CACHE_MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(180)

        val BASICS_TYPE: java.lang.reflect.Type =
            object : TypeToken<Map<String, PokeApiGraphQLDataSource.PokemonBasics>>() {}.type
        val MOVE_INFO_TYPE: java.lang.reflect.Type =
            object : TypeToken<Map<String, PokeApiGraphQLDataSource.MoveInfo>>() {}.type
        val SPECIES_NAMES_TYPE: java.lang.reflect.Type =
            object : TypeToken<Map<String, Map<String, String>>>() {}.type
        val POKEMON_DETAIL_BUNDLE_TYPE: java.lang.reflect.Type =
            object : TypeToken<PokemonDetailBundle>() {}.type
    }
}
