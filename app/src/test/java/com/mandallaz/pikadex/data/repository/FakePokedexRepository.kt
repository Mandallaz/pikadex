package com.mandallaz.pikadex.data.repository

import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSprites
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import com.mandallaz.pikadex.data.remote.dto.DamageRelations
import kotlinx.coroutines.CompletableDeferred

/**
 * Test double for [PokedexRepositoryApi] (F49/F50) — every method reads a canned return value from
 * a settable property, or throws [failWith] if set. [gate], if set, is awaited before returning —
 * how tests exercise the cancel path (start a call, assert it's suspended, cancel the surrounding
 * job, confirm the gate's suspension point is what got cancelled rather than the call completing).
 *
 * Deliberately not a mocking-library mock: every method here is a couple of lines, and a plain
 * hand-written fake keeps the ViewModel tests readable without pulling in a new dependency for it.
 */
class FakePokedexRepository : PokedexRepositoryApi {
    var masterList: List<NamedApiResource> = emptyList()
    var types: List<NamedApiResource> = emptyList()
    var moveNames: List<String> = emptyList()
    var abilityNames: List<String> = emptyList()
    var formVersionGroup: String? = null
    var typeDetailByName: Map<String, TypeDetailDto> = emptyMap()
    var pokemonNamesForType: Set<String> = emptySet()
    var pokemonNamesForMove: Set<String> = emptySet()
    var pokemonNamesForAbility: Set<String> = emptySet()
    var abilityDescription: String? = null
    var pokemonTypes: List<String> = emptyList()
    var pokemonLevelUpMoveNames: List<String> = emptyList()
    // Per-name overrides for a multi-member team where each member needs distinct types/movepool
    // (e.g. computeTeamMatrices) — checked before the flat properties above, which stay the
    // simpler default for single-member/same-for-everyone tests.
    var pokemonTypesByName: Map<String, List<String>> = emptyMap()
    var pokemonLevelUpMoveNamesByName: Map<String, List<String>> = emptyMap()
    var smogonTiers: Map<String, String> = emptyMap()
    var allBasics: Map<String, PokeApiGraphQLDataSource.PokemonBasics> = emptyMap()
    var allMoveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo> = emptyMap()
    var statPercentile: Double = 0.5
    var detailBundle: PokemonDetailBundle? = null
    var allSpeciesNames: Map<String, Map<String, String>> = emptyMap()
    var allMoveLocalizedNames: Map<String, Map<String, String>> = emptyMap()
    var allAbilityLocalizedNames: Map<String, Map<String, String>> = emptyMap()

    /** Thrown by every method below if set — the "network error" path every ViewModel's `catch`
     *  block exists to handle. */
    var failWith: Exception? = null

    /** Awaited (if set) by every method below before it returns or throws — lets a test suspend a
     *  call indefinitely, assert something about the "in flight" state, then cancel the job that's
     *  awaiting it and assert cancellation propagated instead of falling into the failure path. */
    var gate: CompletableDeferred<Unit>? = null

    private suspend fun <T> resolve(value: T): T {
        gate?.await()
        failWith?.let { throw it }
        return value
    }

    override suspend fun getMasterList() = resolve(masterList)
    override suspend fun masterIdByName() = resolve(masterList.mapNotNull { r -> r.id?.let { r.name to it } }.toMap())
    override suspend fun getTypes() = resolve(types)
    override suspend fun getMoveNames() = resolve(moveNames)
    override suspend fun getAbilityNames() = resolve(abilityNames)
    override suspend fun getFormVersionGroup(nameOrId: String) = resolve(formVersionGroup)
    override suspend fun getTypeDetail(type: String) = resolve(typeDetailByName.getValue(type))
    override suspend fun getPokemonNamesForType(type: String) = resolve(pokemonNamesForType)
    override suspend fun getPokemonNamesForMove(move: String) = resolve(pokemonNamesForMove)
    override suspend fun getPokemonNamesForAbility(ability: String) = resolve(pokemonNamesForAbility)
    override suspend fun getAbilityDescription(ability: String) = resolve(abilityDescription)
    override suspend fun getPokemonTypes(nameOrId: String) = resolve(pokemonTypesByName[nameOrId] ?: pokemonTypes)
    override suspend fun getPokemonLevelUpMoveNames(nameOrId: String) =
        resolve(pokemonLevelUpMoveNamesByName[nameOrId] ?: pokemonLevelUpMoveNames)
    override suspend fun getSmogonTiers(genCode: String) = resolve(smogonTiers)
    override suspend fun getAllBasics() = resolve(allBasics)
    override suspend fun getAllBaseStats() = resolve(allBasics.mapValues { it.value.stats })
    override suspend fun getAllMoveInfo() = resolve(allMoveInfo)
    override suspend fun getStatPercentile(statKey: String, value: Int) = resolve(statPercentile)
    override suspend fun getPokemonDetailBundle(nameOrId: String) = resolve(requireNotNull(detailBundle) {
        "FakePokedexRepository.detailBundle must be set before getPokemonDetailBundle is called"
    })
    override suspend fun getAllSpeciesNames() = resolve(allSpeciesNames)
    override suspend fun getAllMoveLocalizedNames() = resolve(allMoveLocalizedNames)
    override suspend fun getAllAbilityLocalizedNames() = resolve(allAbilityLocalizedNames)
}

/** Minimal-but-valid [PokemonDto] fixture — every nullable field defaults to null/empty, matching
 *  how a real Gson-deserialized response degrades on a field the API didn't send, per this DTO's
 *  own doc. Callers override only the fields their test actually cares about. */
fun fakePokemonDto(
    id: Int = 1,
    name: String = "bulbasaur",
    types: List<String> = listOf("grass", "poison")
) = PokemonDto(
    id = id,
    name = name,
    height = 7,
    weight = 69,
    baseExperience = 64,
    types = types.mapIndexed { index, typeName ->
        com.mandallaz.pikadex.data.remote.dto.PokemonTypeSlot(index, NamedApiResource(typeName, "https://pokeapi.co/api/v2/type/$typeName/"))
    },
    stats = emptyList(),
    abilities = emptyList(),
    moves = emptyList(),
    sprites = PokemonSprites(frontDefault = null, frontShiny = null, other = null),
    species = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon-species/$id/")
)

fun fakePokemonSpeciesDto(id: Int = 1, name: String = "bulbasaur") = PokemonSpeciesDto(
    id = id,
    name = name,
    evolutionChain = null,
    flavorTextEntries = null,
    genera = null,
    color = NamedApiResource("green", "https://pokeapi.co/api/v2/pokemon-color/green/"),
    eggGroups = null,
    generation = NamedApiResource("generation-i", "https://pokeapi.co/api/v2/generation/1/"),
    isLegendary = false,
    isMythical = false,
    varieties = null
)

fun fakeTypeDetailDto(name: String) = TypeDetailDto(
    id = 1,
    name = name,
    damageRelations = DamageRelations(null, null, null, null, null, null),
    pokemon = null
)
