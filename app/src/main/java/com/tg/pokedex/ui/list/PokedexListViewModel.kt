package com.tg.pokedex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tg.pokedex.data.AppContainer
import com.tg.pokedex.data.FavoritesRepository
import com.tg.pokedex.data.remote.SmogonTierDataSource
import com.tg.pokedex.data.remote.dto.NamedApiResource
import com.tg.pokedex.data.repository.PokedexRepository
import com.tg.pokedex.util.Smogon
import com.tg.pokedex.util.SmogonGen
import com.tg.pokedex.util.SmogonTierLabels
import com.tg.pokedex.util.SortStat
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
    val selectedTypes: Set<String> = emptySet(),
    val typeFilterNames: Set<String>? = null,
    val moveOptions: List<String> = emptyList(),
    val selectedMove: String? = null,
    val moveFilterNames: Set<String>? = null,
    val abilityOptions: List<String> = emptyList(),
    val selectedAbility: String? = null,
    val abilityFilterNames: Set<String>? = null,
    val selectedFormatGen: SmogonGen? = null,
    val formatTierOptions: List<String> = emptyList(),
    val selectedFormatTier: String? = null,
    val formatFilterNames: Set<String>? = null,
    val isFilterLoading: Boolean = false,
    val sortStat: SortStat? = null,
    val sortAscending: Boolean = false,
    val baseStats: Map<String, Map<String, Int>> = emptyMap(),
    val isStatsLoading: Boolean = false,
    val showFavoritesOnly: Boolean = false,
    val favorites: Set<String> = emptySet()
) {
    val hasActiveFilters: Boolean
        get() = selectedTypes.isNotEmpty() || selectedMove != null || selectedAbility != null ||
            selectedFormatGen != null || showFavoritesOnly

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
            formatFilterNames?.let { set -> list = list.filter { it.name in set } }
            if (showFavoritesOnly) list = list.filter { it.name in favorites }

            sortStat?.let { stat ->
                val keyOf: (NamedApiResource) -> Int = { resource ->
                    val stats = baseStats[resource.name]
                    when {
                        stats == null -> Int.MIN_VALUE
                        stat == SortStat.TOTAL -> stats.values.sum()
                        else -> stats[stat.apiName] ?: Int.MIN_VALUE
                    }
                }
                list = if (sortAscending) list.sortedBy(keyOf) else list.sortedByDescending(keyOf)
            }
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
        viewModelScope.launch {
            FavoritesRepository.favorites.collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
    }

    fun onToggleFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onTypeToggled(type: String) {
        val current = _uiState.value.selectedTypes
        val updated = if (type in current) current - type else current + type
        _uiState.update { it.copy(selectedTypes = updated, typeFilterNames = null) }
        if (updated.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                // AND semantics: a pokemon must match every selected type (e.g. Dragon + Flying = Altaria),
                // not just any one of them, so intersect each type's pokemon set rather than union them.
                val intersection = updated
                    .map { repository.getPokemonNamesForType(it) }
                    .reduce { a, b -> a intersect b }
                _uiState.update { it.copy(typeFilterNames = intersection, isFilterLoading = false) }
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

    fun onFormatGenSelected(gen: SmogonGen?) {
        _uiState.update {
            it.copy(
                selectedFormatGen = gen,
                formatTierOptions = emptyList(),
                selectedFormatTier = null,
                formatFilterNames = null
            )
        }
        if (gen == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val tiers = repository.getSmogonTiers(gen.code)
                val options = SmogonTierLabels.sortedTiers(tiers.values.toSet())
                _uiState.update { it.copy(formatTierOptions = options, isFilterLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while loading formats.") }
            }
        }
    }

    fun onFormatTierSelected(tier: String?) {
        val gen = _uiState.value.selectedFormatGen
        _uiState.update { it.copy(selectedFormatTier = tier, formatFilterNames = null) }
        if (tier == null || gen == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val tiers = repository.getSmogonTiers(gen.code)
                val names = _uiState.value.allPokemon
                    .filter { tiers[SmogonTierDataSource.showdownKey(it.name)] == tier }
                    .map { it.name }
                    .toSet()
                _uiState.update { it.copy(formatFilterNames = names, isFilterLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while filtering by format.") }
            }
        }
    }

    fun loadBaseStatsIfNeeded() {
        if (_uiState.value.baseStats.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStatsLoading = true) }
            try {
                val stats = repository.getAllBaseStats()
                _uiState.update { it.copy(baseStats = stats, isStatsLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isStatsLoading = false, errorMessage = "Network error while loading stats.") }
            }
        }
    }

    fun onSortStatSelected(stat: SortStat?) {
        _uiState.update { it.copy(sortStat = stat, sortAscending = false) }
    }

    fun toggleSortDirection() {
        _uiState.update { it.copy(sortAscending = !it.sortAscending) }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedTypes = emptySet(), typeFilterNames = null,
                selectedMove = null, moveFilterNames = null,
                selectedAbility = null, abilityFilterNames = null,
                selectedFormatGen = null, formatTierOptions = emptyList(),
                selectedFormatTier = null, formatFilterNames = null,
                showFavoritesOnly = false
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
