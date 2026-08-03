package com.mandallaz.pikadex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.remote.SmogonTierDataSource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.SmogonGen
import com.mandallaz.pikadex.util.SmogonTierLabels
import com.mandallaz.pikadex.util.SortStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

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
            selectedFormatGen != null || selectedFormatTier != null || showFavoritesOnly || sortStat != null

    /** How many of the filter controls (not counting sort) are currently set — shown as a badge
     *  count on the "Filters" button so it's clear at a glance whether/how much filtering is active
     *  without opening the sheet. */
    val activeFilterCount: Int
        get() = selectedTypes.size +
            (if (selectedMove != null) 1 else 0) +
            (if (selectedAbility != null) 1 else 0) +
            (if (selectedFormatGen != null || selectedFormatTier != null) 1 else 0) +
            (if (showFavoritesOnly) 1 else 0)

    /** The tier filter works standalone (e.g. picking "Uber" with no generation chosen), so it
     *  needs a generation to look up regardless — this defaults to the current one. */
    val effectiveFormatGen: SmogonGen
        get() = selectedFormatGen ?: Smogon.ALL_GENERATIONS.last()
}

/** Same filtering/sorting [PokedexListUiState] used to expose as a `displayed` getter, moved to a
 *  plain function fed by a debounced query — a getter re-ran this (up to 5 chained `.filter{}`
 *  passes, plus a full sort, over ~1300 items) on every single recomposition; now it only runs
 *  once per actual state change, off the main thread (see [PokedexListViewModel.displayedPokemon]). */
private fun computeDisplayed(state: PokedexListUiState, debouncedQuery: String): List<NamedApiResource> {
    var list = state.allPokemon
    if (debouncedQuery.isNotBlank()) {
        val q = debouncedQuery.trim().lowercase()
        // Cards display the zero-padded dex number ("#0004"), but the old exact string match
        // (`it.id?.toString() == q`) only matched the *unpadded* form — searching the exact text
        // on screen ("0004") returned nothing. Comparing as Int handles both: "4".toIntOrNull()
        // and "0004".toIntOrNull() are both 4.
        val numericQuery = q.toIntOrNull()
        list = list.filter { it.name.contains(q) || (numericQuery != null && it.id == numericQuery) }
    }
    state.typeFilterNames?.let { set -> list = list.filter { it.name in set } }
    state.moveFilterNames?.let { set -> list = list.filter { it.name in set } }
    state.abilityFilterNames?.let { set -> list = list.filter { it.name in set } }
    state.formatFilterNames?.let { set -> list = list.filter { it.name in set } }
    if (state.showFavoritesOnly) list = list.filter { it.name in state.favorites }

    state.sortStat?.let { stat ->
        val keyOf: (NamedApiResource) -> Int = { resource ->
            val stats = state.baseStats[resource.name]
            when {
                stats == null -> Int.MIN_VALUE
                stat == SortStat.TOTAL -> stats.values.sum()
                else -> stats[stat.apiName] ?: Int.MIN_VALUE
            }
        }
        // sortedBy/sortedByDescending call the key selector on every *comparison*, not once per
        // element (~27k calls for a ~1300-item sort instead of 1300) — decorate-sort-undecorate
        // computes each key exactly once.
        val decorated = list.map { it to keyOf(it) }
        list = if (state.sortAscending) {
            decorated.sortedBy { it.second }
        } else {
            decorated.sortedByDescending { it.second }
        }.map { it.first }
    }
    return list
}

class PokedexListViewModel @JvmOverloads constructor(
    private val repository: PokedexRepository = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PokedexListUiState())
    val uiState: StateFlow<PokedexListUiState> = _uiState.asStateFlow()

    /** The search box itself must reflect every keystroke instantly, but re-filtering the list on
     *  every single character felt mushy while typing — this settles 150ms after the last change,
     *  independent of [_uiState] so type/move/ability/format taps (not text input) stay instant. */
    private val debouncedSearchQuery = MutableStateFlow("")

    /** The filtered/sorted list, recomputed once per actual state change (not once per
     *  recomposition) and off the main thread — see [computeDisplayed]. */
    @OptIn(FlowPreview::class)
    val displayedPokemon: StateFlow<List<NamedApiResource>> =
        combine(
            // searchQuery is deliberately zeroed out here — it's already fed in separately, via
            // the debounced flow below. Left in, every keystroke would re-emit *this* arm too
            // (searchQuery lives on the same _uiState as everything else), which re-ran the
            // filter/sort pass on the un-debounced text and defeated the debounce entirely.
            _uiState.map { it.copy(searchQuery = "") }.distinctUntilChanged(),
            debouncedSearchQuery.debounce(150)
        ) { state, query -> computeDisplayed(state, query) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                // supervisorScope: a plain `async {}` here that fails before being awaited would
                // otherwise cancel this whole launch's Job as a child failure rather than a normal
                // catchable exception, which can crash the app uncaught even with a try/catch
                // around it — see the full explanation on the identical fix in
                // PokedexDetailViewModel.load().
                supervisorScope {
                    // Independent requests — no reason the type-chip row should block the
                    // ~1300-item master list (or vice versa) from appearing.
                    val masterListDeferred = async { repository.getMasterList() }
                    val typesDeferred = async { repository.getTypes() }
                    val pokemonList = masterListDeferred.await()
                    val types = typesDeferred.await()
                    _uiState.update { it.copy(allPokemon = pokemonList, typeOptions = types, isLoading = false) }
                }
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
        debouncedSearchQuery.value = query
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

    /** Which generation's tier list [formatTierOptions] currently reflects, so a stale list
     *  (loaded for a different generation) gets refreshed instead of reused. Not part of
     *  [PokedexListUiState] since it's bookkeeping, not something the UI renders directly. */
    private var tierOptionsGen: SmogonGen? = null

    /** Format (generation) and Tier are independent filters — picking "Uber" doesn't require
     *  choosing a generation first, it just falls back to [PokedexListUiState.effectiveFormatGen].
     *  Changing the generation while a tier is already selected re-resolves that same tier code
     *  against the new generation's data. */
    fun onFormatGenSelected(gen: SmogonGen?) {
        _uiState.update { it.copy(selectedFormatGen = gen, formatTierOptions = emptyList()) }
        tierOptionsGen = null
        _uiState.value.selectedFormatTier?.let { applyTierFilter(it) }
    }

    fun loadTierOptionsIfNeeded() {
        val gen = _uiState.value.effectiveFormatGen
        if (tierOptionsGen == gen && _uiState.value.formatTierOptions.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val tiers = repository.getSmogonTiers(gen.code)
                val options = SmogonTierLabels.sortedTiers(tiers.values.toSet())
                tierOptionsGen = gen
                _uiState.update { it.copy(formatTierOptions = options, isFilterLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while loading tiers.") }
            }
        }
    }

    fun onFormatTierSelected(tier: String?) {
        _uiState.update { it.copy(selectedFormatTier = tier) }
        if (tier == null) {
            _uiState.update { it.copy(formatFilterNames = null) }
            return
        }
        applyTierFilter(tier)
    }

    private fun applyTierFilter(tier: String) {
        val gen = _uiState.value.effectiveFormatGen
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
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while filtering by tier.") }
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
        tierOptionsGen = null
        _uiState.update {
            it.copy(
                selectedTypes = emptySet(), typeFilterNames = null,
                selectedMove = null, moveFilterNames = null,
                selectedAbility = null, abilityFilterNames = null,
                selectedFormatGen = null, formatTierOptions = emptyList(),
                selectedFormatTier = null, formatFilterNames = null,
                showFavoritesOnly = false,
                // Reset used to leave a sort applied while making the Reset chip itself disappear
                // (hasActiveFilters didn't count sortStat) — so there was no visible way back to
                // dex order except reopening the Sort dialog and picking "No sorting" by hand.
                sortStat = null, sortAscending = false
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
