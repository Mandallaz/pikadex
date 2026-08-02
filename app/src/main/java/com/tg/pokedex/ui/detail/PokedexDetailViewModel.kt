package com.tg.pokedex.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tg.pokedex.data.AppContainer
import com.tg.pokedex.data.FavoritesRepository
import com.tg.pokedex.data.TeamRepository
import com.tg.pokedex.data.remote.dto.EvolutionChainDto
import com.tg.pokedex.data.remote.dto.NamedApiResource
import com.tg.pokedex.data.remote.dto.PokemonDto
import com.tg.pokedex.data.remote.dto.PokemonSpeciesDto
import com.tg.pokedex.data.repository.PokedexRepository
import com.tg.pokedex.util.computeDefensiveMultipliers
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
    val typeMatchups: Map<String, Double> = emptyMap()
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pokemon = bundle.pokemon,
                        species = bundle.species,
                        evolutionChain = bundle.evolutionChain,
                        typeMatchups = matchups
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
