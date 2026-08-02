package com.mandallaz.pikadex.data.repository

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

data class PokemonDetailBundle(
    val pokemon: PokemonDto,
    val species: PokemonSpeciesDto,
    val evolutionChain: EvolutionChainDto?
)

/**
 * Access layer for PokeAPI data. Keeps the global lists (pokemon/moves/abilities/types) in memory
 * since they never change during the process lifetime, avoiding re-downloading ~1300 entries on
 * every screen.
 */
class PokedexRepository(private val api: PokeApiService) {

    private var masterListCache: List<NamedApiResource>? = null
    private var moveNamesCache: List<String>? = null
    private var abilityNamesCache: List<String>? = null
    private var typesCache: List<NamedApiResource>? = null

    private val pokemonDetailCache = mutableMapOf<String, PokemonDto>()
    private val speciesCache = mutableMapOf<Int, PokemonSpeciesDto>()
    private val evolutionChainCache = mutableMapOf<Int, EvolutionChainDto>()
    private val typeDetailCache = mutableMapOf<String, TypeDetailDto>()
    private val moveDetailCache = mutableMapOf<String, MoveDetailDto>()
    private val abilityDetailCache = mutableMapOf<String, AbilityDetailDto>()
    private val smogonTierCache = mutableMapOf<String, Map<String, String>>()
    private var allBaseStatsCache: Map<String, Map<String, Int>>? = null

    suspend fun getMasterList(): List<NamedApiResource> {
        masterListCache?.let { return it }
        val list = api.getPokemonList(limit = 100000).results
        masterListCache = list
        return list
    }

    suspend fun getTypes(): List<NamedApiResource> {
        typesCache?.let { return it }
        val list = api.getTypeList().results
            .filterNot { it.name == "unknown" || it.name == "stellar" || it.name == "shadow" }
        typesCache = list
        return list
    }

    suspend fun getMoveNames(): List<String> {
        moveNamesCache?.let { return it }
        val list = api.getMoveList(limit = 100000).results.map { it.name }
        moveNamesCache = list
        return list
    }

    suspend fun getAbilityNames(): List<String> {
        abilityNamesCache?.let { return it }
        val list = api.getAbilityList(limit = 100000).results.map { it.name }
        abilityNamesCache = list
        return list
    }

    /** Full type detail (including damage_relations), cached per type name. */
    suspend fun getTypeDetail(type: String): TypeDetailDto =
        typeDetailCache.getOrPut(type) { api.getType(type) }

    /** Names of pokemon that have this type (the /type endpoint already does the reverse lookup). */
    suspend fun getPokemonNamesForType(type: String): Set<String> {
        val detail = getTypeDetail(type)
        return detail.pokemon.map { it.pokemon.name }.toSet()
    }

    /** Names of pokemon that can learn this move. */
    suspend fun getPokemonNamesForMove(move: String): Set<String> {
        val detail = moveDetailCache.getOrPut(move) { api.getMove(move) }
        return detail.learnedByPokemon.map { it.name }.toSet()
    }

    /** Names of pokemon that can have this ability. */
    suspend fun getPokemonNamesForAbility(ability: String): Set<String> {
        val detail = abilityDetailCache.getOrPut(ability) { api.getAbility(ability) }
        return detail.pokemon.map { it.pokemon.name }.toSet()
    }

    /** Plain-English description of an ability (e.g. "Levitate" -> "Gives full immunity to Ground
     *  type moves."), since PokeAPI's ability names alone are often unclear on their own. */
    suspend fun getAbilityDescription(ability: String): String? {
        val detail = abilityDetailCache.getOrPut(ability) { api.getAbility(ability) }
        return detail.effectEntries.firstOrNull { it.language.name == "en" }?.shortEffect
    }

    /** Just the type names for a pokemon, without pulling the full detail bundle (species, evolution chain). */
    suspend fun getPokemonTypes(nameOrId: String): List<String> {
        val pokemon = pokemonDetailCache.getOrPut(nameOrId) { api.getPokemon(nameOrId) }
        return pokemon.types.map { it.type.name }
    }

    /** pokemonKey (Showdown format, no hyphens) -> tier code, for a Smogon generation (e.g. "ss"). */
    suspend fun getSmogonTiers(genCode: String): Map<String, String> =
        smogonTierCache.getOrPut(genCode) { SmogonTierDataSource.fetchTiers(genCode) }

    /** pokemonName -> (statApiName -> baseStat), fetched once in bulk via GraphQL for sorting. */
    suspend fun getAllBaseStats(): Map<String, Map<String, Int>> {
        allBaseStatsCache?.let { return it }
        val stats = PokeApiGraphQLDataSource.fetchAllBaseStats()
        allBaseStatsCache = stats
        return stats
    }

    suspend fun getPokemonDetailBundle(nameOrId: String): PokemonDetailBundle {
        val pokemon = pokemonDetailCache.getOrPut(nameOrId) { api.getPokemon(nameOrId) }
        // Alternate forms (mega/gmax/regional/gender/cosmetic...) have a pokemon.id in the 10000+
        // range that does NOT match any pokemon-species id — the species must be looked up via the
        // "species" reference embedded in the pokemon payload instead (e.g. basculegion-female,
        // pokemon id 10248, belongs to species "basculegion", id 902).
        val speciesKey = pokemon.species.id ?: pokemon.id
        val species = speciesCache.getOrPut(speciesKey) { api.getPokemonSpecies(pokemon.species.name) }
        val chainId = species.evolutionChain?.id
        val chain = chainId?.let { id -> evolutionChainCache.getOrPut(id) { api.getEvolutionChain(id) } }
        return PokemonDetailBundle(pokemon, species, chain)
    }
}
