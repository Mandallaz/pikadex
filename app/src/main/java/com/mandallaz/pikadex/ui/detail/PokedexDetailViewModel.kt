package com.mandallaz.pikadex.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.TypeTriangle
import com.mandallaz.pikadex.util.TypeTriangles
import com.mandallaz.pikadex.util.computeDefensiveMultipliers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PokedexDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val pokemon: PokemonDto? = null,
    val species: PokemonSpeciesDto? = null,
    val evolutionChain: EvolutionChainDto? = null,
    val typeMatchups: Map<String, Double> = emptyMap(),
    val abilityDescriptions: Map<String, String> = emptyMap(),
    val memberTriangles: List<TypeTriangle> = emptyList(),
    val counteredTriangles: List<TypeTriangle> = emptyList(),
    val moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo> = emptyMap(),
    /** statApiName (hp/attack/.../speed, plus a synthetic "total") -> this pokemon's percentile
     *  rank (0.0..1.0) among every other pokemon's same stat, for coloring stat bars by how good
     *  the value actually is rather than a fixed per-stat hue. */
    val statPercentiles: Map<String, Double> = emptyMap()
)

private val BASE_STAT_KEYS = listOf("hp", "attack", "defense", "special-attack", "special-defense", "speed")

/** Fraction of [allValues] that [value] is greater-or-equal to — ties split evenly so a value
 *  shared by many pokemon doesn't get pushed to either extreme. */
private fun percentileOf(value: Int, allValues: List<Int>): Double {
    if (allValues.isEmpty()) return 0.5
    val below = allValues.count { it < value }
    val equal = allValues.count { it == value }
    return ((below + equal / 2.0) / allValues.size).coerceIn(0.0, 1.0)
}

class PokedexDetailViewModel @JvmOverloads constructor(
    private val repository: PokedexRepository = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PokedexDetailUiState())
    val uiState: StateFlow<PokedexDetailUiState> = _uiState.asStateFlow()

    val team: StateFlow<List<NamedApiResource>> = TeamRepository.team
    val favorites: StateFlow<Set<String>> = FavoritesRepository.favorites

    private var loadedFor: String? = null

    fun load(nameOrId: String) {
        if (loadedFor == nameOrId) return
        loadedFor = nameOrId
        viewModelScope.launch {
            _uiState.update { PokedexDetailUiState(isLoading = true) }
            try {
                val bundle = repository.getPokemonDetailBundle(nameOrId)
                val typeDetails = bundle.pokemon.types.map { repository.getTypeDetail(it.type.name) }
                val matchups = computeDefensiveMultipliers(typeDetails)
                val pokemonTypes = bundle.pokemon.types.map { it.type.name }
                val memberTriangles = TypeTriangles.containing(pokemonTypes)
                val counteredTriangles = TypeTriangles.counteredBy(pokemonTypes)
                val abilityNames = bundle.pokemon.abilities.map { it.ability.name }
                val descriptionsDeferred = async {
                    abilityNames
                        .map { name -> async { name to repository.getAbilityDescription(name) } }
                        .awaitAll()
                        .mapNotNull { (name, description) -> description?.let { name to it } }
                        .toMap()
                }
                val moveInfo = repository.getAllMoveInfo()
                val allStats = repository.getAllBaseStats()
                val descriptions = descriptionsDeferred.await()

                val percentiles = BASE_STAT_KEYS.mapNotNull { key ->
                    val thisValue = bundle.pokemon.stats.firstOrNull { it.stat.name == key }?.baseStat
                    thisValue?.let { key to percentileOf(it, allStats.values.mapNotNull { s -> s[key] }) }
                }.toMap() + mapOf(
                    "total" to percentileOf(
                        bundle.pokemon.stats.sumOf { it.baseStat },
                        allStats.values.map { s -> BASE_STAT_KEYS.sumOf { key -> s[key] ?: 0 } }
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pokemon = bundle.pokemon,
                        species = bundle.species,
                        evolutionChain = bundle.evolutionChain,
                        typeMatchups = matchups,
                        abilityDescriptions = descriptions,
                        memberTriangles = memberTriangles,
                        counteredTriangles = counteredTriangles,
                        moveInfo = moveInfo,
                        statPercentiles = percentiles
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't load this Pokémon.")
                }
            }
        }
    }

    fun toggleTeamMembership() {
        val pokemon = _uiState.value.pokemon ?: return
        TeamRepository.toggle(NamedApiResource(pokemon.name, "https://pokeapi.co/api/v2/pokemon/${pokemon.id}/"))
    }

    fun toggleFavorite() {
        val pokemon = _uiState.value.pokemon ?: return
        FavoritesRepository.toggle(pokemon.name)
    }
}
