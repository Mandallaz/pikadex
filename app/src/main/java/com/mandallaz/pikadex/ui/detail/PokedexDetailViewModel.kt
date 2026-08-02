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
    val moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo> = emptyMap()
)

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
                val descriptions = descriptionsDeferred.await()
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
                        moveInfo = moveInfo
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
