package com.tg.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tg.pokedex.data.AppContainer
import com.tg.pokedex.data.remote.dto.NamedApiResource
import com.tg.pokedex.data.repository.PokedexRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PokedexListUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val allPokemon: List<NamedApiResource> = emptyList(),
    val typeOptions: List<NamedApiResource> = emptyList(),
    val selectedType: String? = null,
    val typeFilterNames: Set<String>? = null,
    val moveOptions: List<String> = emptyList(),
    val selectedMove: String? = null,
    val moveFilterNames: Set<String>? = null,
    val abilityOptions: List<String> = emptyList(),
    val selectedAbility: String? = null,
    val abilityFilterNames: Set<String>? = null,
    val isFilterLoading: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = selectedType != null || selectedMove != null || selectedAbility != null

    val displayed: List<NamedApiResource>
        get() {
            var list = allPokemon
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter { it.name.contains(q) || it.id?.toString() == q }
            }
            typeFilterNames?.let { set -> list = list.filter { it.name in set } }
            moveFilterNames?.let { set -> list = list.filter { it.name in set } }
            abilityFilterNames?.let { set -> list = list.filter { it.name in set } }
            return list
        }
}

class PokedexListViewModel @JvmOverloads constructor(
    private val repository: PokedexRepository = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PokedexListUiState())
    val uiState: StateFlow<PokedexListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val pokemonList = repository.getMasterList()
                val types = repository.getTypes()
                _uiState.update { it.copy(allPokemon = pokemonList, typeOptions = types, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't load the Pokédex. Check your connection.")
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onTypeSelected(type: String?) {
        _uiState.update { it.copy(selectedType = type, typeFilterNames = null) }
        if (type == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val names = repository.getPokemonNamesForType(type)
                _uiState.update { it.copy(typeFilterNames = names, isFilterLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while filtering by type.") }
            }
        }
    }

    fun loadMoveOptionsIfNeeded() {
        if (_uiState.value.moveOptions.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val moves = repository.getMoveNames()
                _uiState.update { it.copy(moveOptions = moves) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Network error while loading moves.") }
            }
        }
    }

    fun onMoveSelected(move: String?) {
        _uiState.update { it.copy(selectedMove = move, moveFilterNames = null) }
        if (move == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val names = repository.getPokemonNamesForMove(move)
                _uiState.update { it.copy(moveFilterNames = names, isFilterLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while filtering by move.") }
            }
        }
    }

    fun loadAbilityOptionsIfNeeded() {
        if (_uiState.value.abilityOptions.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val abilities = repository.getAbilityNames()
                _uiState.update { it.copy(abilityOptions = abilities) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Network error while loading abilities.") }
            }
        }
    }

    fun onAbilitySelected(ability: String?) {
        _uiState.update { it.copy(selectedAbility = ability, abilityFilterNames = null) }
        if (ability == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val names = repository.getPokemonNamesForAbility(ability)
                _uiState.update { it.copy(abilityFilterNames = names, isFilterLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while filtering by ability.") }
            }
        }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedType = null, typeFilterNames = null,
                selectedMove = null, moveFilterNames = null,
                selectedAbility = null, abilityFilterNames = null
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
