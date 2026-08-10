package com.mandallaz.pikadex.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.data.PokedexListContext
import com.mandallaz.pikadex.data.SupportedLanguages
import com.mandallaz.pikadex.data.remote.SmogonTierDataSource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import com.mandallaz.pikadex.util.RarityFilter
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.SmogonGen
import com.mandallaz.pikadex.util.SmogonTierLabels
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.TypeTriangles
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.util.toDisplayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import java.text.Collator

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
    val favorites: Set<String> = emptySet(),
    val rarityFilter: RarityFilter? = null,
    // Populated alongside baseStats (same bulk fetch, see loadBaseStatsIfNeeded) — kept as two
    // name sets rather than exposing the full PokemonBasics map, since that's all this filter
    // needs and it keeps this ViewModel's state decoupled from the GraphQL data source's types.
    val legendaryNames: Set<String> = emptySet(),
    val mythicalNames: Set<String> = emptySet(),
    // Same bulk fetch as legendaryNames/mythicalNames above (see loadBaseStatsIfNeeded) — backs the
    // F33 "Perfect Counter" filter, which needs each Pokémon's typing to run through
    // TypeTriangles.isPerfectCounter client-side, no separate network call.
    val typesByName: Map<String, List<String>> = emptyMap(),
    // Only keys with an active (> 0) minimum are present — a slider at its default (0) imposes no
    // constraint, so there's nothing to filter on and no reason for it to occupy a map entry.
    val statMinimums: Map<String, Int> = emptyMap(),
    val counterFilterActive: Boolean = false,
    // B9 — rawName -> (languageCode -> localized species name), fetched once in the background
    // (see loadSpeciesNamesIfNeeded); empty until that completes, which is fine since every card
    // falls back to the English-formatted raw name (String.localizedDisplayName) until then.
    val speciesNames: Map<String, Map<String, String>> = emptyMap()
) {
    val hasActiveFilters: Boolean
        get() = selectedTypes.isNotEmpty() || selectedMove != null || selectedAbility != null ||
            selectedFormatGen != null || selectedFormatTier != null || showFavoritesOnly ||
            sortStat != null || rarityFilter != null || statMinimums.isNotEmpty() || counterFilterActive

    /** How many of the filter controls (not counting sort) are currently set — shown as a badge
     *  count on the "Filters" button so it's clear at a glance whether/how much filtering is active
     *  without opening the sheet. */
    val activeFilterCount: Int
        get() = selectedTypes.size +
            (if (selectedMove != null) 1 else 0) +
            (if (selectedAbility != null) 1 else 0) +
            (if (selectedFormatGen != null || selectedFormatTier != null) 1 else 0) +
            (if (showFavoritesOnly) 1 else 0) +
            (if (rarityFilter != null) 1 else 0) +
            (if (counterFilterActive) 1 else 0) +
            statMinimums.size

    /** The tier filter works standalone (e.g. picking "Uber" with no generation chosen), so it
     *  needs a generation to look up regardless — this defaults to the current one. */
    val effectiveFormatGen: SmogonGen
        get() = selectedFormatGen ?: Smogon.ALL_GENERATIONS.last()
}

/** [PokedexListUiState.statMinimums] key for the stat *total* filter (F14) — a derived sum, not one
 *  of the six raw stat names [SortStat.apiName] provides, so it needs a key of its own rather than
 *  colliding with (or being mistaken for) a real stat. Internal, not private: [FilterSheetContent]
 *  in `PokedexListScreen.kt` reads/writes it too. */
internal const val STAT_KEY_TOTAL = "total"

/** Same filtering/sorting [PokedexListUiState] used to expose as a `displayed` getter, moved to a
 *  plain function fed by a debounced query — a getter re-ran this (up to 5 chained `.filter{}`
 *  passes, plus a full sort, over ~1300 items) on every single recomposition; now it only runs
 *  once per actual state change, off the main thread (see [PokedexListViewModel.displayedPokemon]). */
// internal, not private: lets PokedexListViewModelTest exercise the filter/sort pipeline directly,
// pure-function-style, rather than only through the ViewModel's coroutine/StateFlow machinery.
internal fun computeDisplayed(state: PokedexListUiState, debouncedQuery: String, language: String = SupportedLanguages.DEFAULT_CODE): List<NamedApiResource> {
    // A resource with no id can't render a card (no sprite, no #number) — the grid used to filter
    // these out one at a time with an early return inside each item, re-running the same check on
    // every recomposition instead of once here.
    var list = state.allPokemon.filter { it.id != null }
    if (debouncedQuery.isNotBlank()) {
        val trimmed = debouncedQuery.trim().lowercase()
        // Cards display the zero-padded dex number ("#0004"), but the old exact string match
        // (`it.id?.toString() == q`) only matched the *unpadded* form — searching the exact text
        // on screen ("0004") returned nothing. Comparing as Int handles both: "4".toIntOrNull()
        // and "0004".toIntOrNull() are both 4.
        val numericQuery = trimmed.toIntOrNull()
        // Cards display names via toDisplayName() ("Mr. Mime", "Ho-Oh", "Deoxys Attack"), which
        // inserts spaces/hyphens/punctuation the raw API name ("mr-mime") doesn't have — typing
        // exactly what's on screen used to match nothing for any hyphenated name. Stripping hyphens
        // from both sides (same normalization SearchableListDialog already uses for moves/abilities)
        // makes either form find it.
        val normalizedQuery = trimmed.replace(" ", "").replace("-", "")
        list = list.filter { resource ->
            val rawMatches = resource.name.replace("-", "").contains(normalizedQuery)
            // B10 — search must also follow the picked language, not just the raw/English name:
            // in French, searching "ray" shouldn't be the only way to find Mudbray if its French
            // name "Tiboudet" doesn't contain the query at all. Matching both (not replacing the
            // raw-name check) rather than switching exclusively to the localized name — a user who
            // knows a Pokémon's English name should still be able to find it.
            val localizedMatches = resource.name.localizedDisplayName(state.speciesNames, language)
                .lowercase().replace(" ", "").replace("-", "").contains(normalizedQuery)
            rawMatches || localizedMatches || (numericQuery != null && resource.id == numericQuery)
        }
    }
    state.typeFilterNames?.let { set -> list = list.filter { it.name in set } }
    state.moveFilterNames?.let { set -> list = list.filter { it.name in set } }
    state.abilityFilterNames?.let { set -> list = list.filter { it.name in set } }
    state.formatFilterNames?.let { set -> list = list.filter { it.name in set } }
    if (state.showFavoritesOnly) list = list.filter { it.name in state.favorites }

    // Same reasoning as the stat-sort guard below: legendaryNames/mythicalNames come from the same
    // bulk fetch as baseStats, so both empty means "not loaded yet", not "no legendaries exist" —
    // applying the filter against that would silently empty the whole grid instead of just not
    // filtering yet.
    if (state.rarityFilter != null && (state.legendaryNames.isNotEmpty() || state.mythicalNames.isNotEmpty())) {
        list = list.filter { resource ->
            when (state.rarityFilter) {
                RarityFilter.LEGENDARY -> resource.name in state.legendaryNames
                RarityFilter.MYTHICAL -> resource.name in state.mythicalNames
                RarityFilter.ORDINARY -> resource.name !in state.legendaryNames && resource.name !in state.mythicalNames
            }
        }
    }

    // Same "no data yet, don't filter" guard as rarity above — typesByName comes from the same bulk
    // fetch as legendaryNames/mythicalNames, so empty means "not loaded yet", not "no Pokémon counter
    // any triangle".
    if (state.counterFilterActive && state.typesByName.isNotEmpty()) {
        list = list.filter { resource ->
            val types = state.typesByName[resource.name] ?: return@filter false
            TypeTriangles.isPerfectCounter(types)
        }
    }

    // Same "no data yet, don't filter" guard as rarity/sort above — statMinimums is only ever set
    // from the same sheet that already triggered loadBaseStatsIfNeeded, so this window is brief,
    // but a Pokémon whose own entry is missing from an otherwise-loaded map (a partial/stale fetch)
    // still can't prove it satisfies a minimum, so it's excluded rather than assumed to pass.
    if (state.statMinimums.isNotEmpty() && state.baseStats.isNotEmpty()) {
        list = list.filter { resource ->
            val stats = state.baseStats[resource.name] ?: return@filter false
            state.statMinimums.all { (key, minimum) ->
                // STAT_KEY_TOTAL is a derived sum, not a raw stat name — never present in `stats`
                // itself, so a plain lookup would read it as 0 and filter out everything.
                val value = if (key == STAT_KEY_TOTAL) stats.values.sum() else stats[key] ?: 0
                value >= minimum
            }
        }
    }

    state.sortStat?.let { stat ->
        if (stat == SortStat.NAME) {
            // decorate-sort-undecorate again: toDisplayName() does real work (special-case lookup,
            // hyphen splitting/joining), no reason to redo it on every comparison.
            val decorated = list.map { it to it.name.toDisplayName() }
            val comparator = compareBy(NAME_COLLATOR) { pair: Pair<NamedApiResource, String> -> pair.second }
            list = decorated.sortedWith(if (state.sortAscending) comparator else comparator.reversed()).map { it.first }
            return@let
        }
        // A stat sort with no bulk stats loaded used to compute an all-equal Int.MIN_VALUE key set,
        // which a stable sort leaves untouched — the grid silently ignored the sort while the chip
        // still claimed "Sort: Attack", with no indication anything was wrong. Dex-number sort needs
        // no stats and is unaffected by this early return.
        if (stat != SortStat.DEX_NUMBER && state.baseStats.isEmpty()) return@let
        val keyOf: (NamedApiResource) -> Int = { resource ->
            // Checked before the stats lookup, not inside it: sorting by dex number doesn't need
            // the bulk stats map at all, so it must not fall into the "no stats loaded -> every key
            // is MIN_VALUE" branch below and silently do nothing.
            if (stat == SortStat.DEX_NUMBER) {
                resource.id ?: Int.MIN_VALUE
            } else {
                val stats = state.baseStats[resource.name]
                when {
                    stats == null -> Int.MIN_VALUE
                    stat == SortStat.TOTAL -> stats.values.sum()
                    else -> stats[stat.apiName] ?: Int.MIN_VALUE
                }
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

// PRIMARY strength: base-letter-only comparison, so accented display names (Flabébé, Ho-Oh's
// hyphen aside) sort next to their unaccented neighbors instead of Java's default ordering, which
// would put every non-ASCII character in its own separate bucket after all plain-ASCII names.
private val NAME_COLLATOR: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

class PokedexListViewModel @JvmOverloads constructor(
    private val repository: PokedexRepositoryApi = AppContainer.repository
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
            // Only a non-empty query is worth waiting on. A flat debounce(150) also delayed the
            // *initial* "" emission, and since combine can't produce anything until both arms have
            // emitted, displayedPokemon stayed empty for the first 150ms of the screen's life — long
            // enough that a cache-warm cold start (list ready in well under 150ms) flashed
            // "0 Pokémon"/"No Pokémon match your search and filters" before showing the grid. It also
            // makes clearing the search box snap back instantly instead of lagging.
            debouncedSearchQuery.debounce { query -> if (query.isEmpty()) 0L else 150L },
            // B10 — search results must react to a language change too, not just to state/query
            // changes: switching language mid-search should re-filter against the new language's
            // names without the user having to retype anything.
            LanguageSettings.currentLanguage
        ) { state, query, language -> computeDisplayed(state, query, language) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInitialData()
        loadSpeciesNamesIfNeeded()
        viewModelScope.launch {
            FavoritesRepository.favorites.collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
        // Published for the detail screen's swipe/chevron navigation (issue #7) to step
        // through whatever's actually on screen — filtered to Fire types, swiping should only ever
        // land on another Fire type. See PokedexListContext's own doc for the fallback when a
        // Pokémon isn't part of this list at all.
        viewModelScope.launch {
            displayedPokemon.collect { list -> PokedexListContext.update(list.map { it.name }) }
        }
    }

    /** Loads the master list + type options. Called from [init], and again from
     *  [retryInitialLoad] — a cold start with no connection used to be a genuine dead end: the
     *  ViewModel only ever attempted this once, so there was no way back in short of force-killing
     *  the app. */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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
    }

    fun retryInitialLoad() = loadInitialData()

    fun onToggleFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        debouncedSearchQuery.value = query
    }

    // Each filter kind gets its own tracked Job so a rapid second tap cancels the first request
    // instead of racing it — two `viewModelScope.launch{}` calls in a row (e.g. toggling Fire then
    // quickly Flying) used to both run to completion with no ordering guarantee, so whichever
    // network call happened to finish *last* silently won even if it was for the stale,
    // already-abandoned selection.
    private var typeFilterJob: Job? = null
    private var moveFilterJob: Job? = null
    private var abilityFilterJob: Job? = null
    private var tierFilterJob: Job? = null

    fun onTypeToggled(type: String) {
        val current = _uiState.value.selectedTypes
        val updated = if (type in current) current - type else current + type
        typeFilterJob?.cancel()
        // isFilterLoading has to be cleared here, at the cancel site: the cancelled job's own
        // `isFilterLoading = false` lives in a catch block that no longer runs (see the
        // CancellationException rethrow below), so deselecting the last type while its request was
        // still in flight used to leave the spinner over the grid spinning forever.
        _uiState.update { it.copy(selectedTypes = updated, typeFilterNames = null, isFilterLoading = false) }
        if (updated.isEmpty()) return
        typeFilterJob = viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                // AND semantics: a pokemon must match every selected type (e.g. Dragon + Flying = Altaria),
                // not just any one of them, so intersect each type's pokemon set rather than union them.
                val intersection = updated
                    .map { repository.getPokemonNamesForType(it) }
                    .reduce { a, b -> a intersect b }
                _uiState.update { it.copy(typeFilterNames = intersection, isFilterLoading = false) }
            } catch (e: CancellationException) {
                // Cancelling this job (the rapid-tap guard above) throws CancellationException out of
                // whatever network call was in flight — and CancellationException *is* an Exception,
                // so the generic handler below used to catch it and report a "Network error" that
                // never happened. That error then also force-closed the filter sheet (see the error
                // effect in PokedexListScreen), so every quick second tap on a type chip slammed the
                // sheet shut with a bogus network error. Rethrowing keeps cancellation as cancellation.
                throw e
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
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Network error while loading moves.") }
            }
        }
    }

    fun onMoveSelected(move: String?) {
        moveFilterJob?.cancel()
        _uiState.update { it.copy(selectedMove = move, moveFilterNames = null, isFilterLoading = false) }
        if (move == null) return
        moveFilterJob = viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val names = repository.getPokemonNamesForMove(move)
                _uiState.update { it.copy(moveFilterNames = names, isFilterLoading = false) }
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
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
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Network error while loading abilities.") }
            }
        }
    }

    fun onAbilitySelected(ability: String?) {
        abilityFilterJob?.cancel()
        _uiState.update { it.copy(selectedAbility = ability, abilityFilterNames = null, isFilterLoading = false) }
        if (ability == null) return
        abilityFilterJob = viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val names = repository.getPokemonNamesForAbility(ability)
                _uiState.update { it.copy(abilityFilterNames = names, isFilterLoading = false) }
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
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
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while loading tiers.") }
            }
        }
    }

    fun onFormatTierSelected(tier: String?) {
        tierFilterJob?.cancel()
        _uiState.update { it.copy(selectedFormatTier = tier, isFilterLoading = false) }
        if (tier == null) {
            _uiState.update { it.copy(formatFilterNames = null) }
            return
        }
        applyTierFilter(tier)
    }

    private fun applyTierFilter(tier: String) {
        val gen = _uiState.value.effectiveFormatGen
        tierFilterJob?.cancel()
        tierFilterJob = viewModelScope.launch {
            _uiState.update { it.copy(isFilterLoading = true) }
            try {
                val tiers = repository.getSmogonTiers(gen.code)
                // A tier selected under one generation may not exist in another (e.g. "NFE" picked
                // in Gen 9, then switching to Gen 1) — leaving it silently selected produced a
                // confidently-checked Tier chip alongside an empty (wrong, unexplained) result grid.
                if (tiers.values.none { it == tier }) {
                    _uiState.update {
                        it.copy(selectedFormatTier = null, formatFilterNames = null, isFilterLoading = false)
                    }
                    return@launch
                }
                val names = _uiState.value.allPokemon
                    .filter { tiers[SmogonTierDataSource.showdownKey(it.name)] == tier }
                    .map { it.name }
                    .toSet()
                _uiState.update { it.copy(formatFilterNames = names, isFilterLoading = false) }
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
            } catch (e: Exception) {
                _uiState.update { it.copy(isFilterLoading = false, errorMessage = "Network error while filtering by tier.") }
            }
        }
    }

    /** B9 — bulk species-name fetch, triggered unconditionally from [init] (unlike
     *  [loadBaseStatsIfNeeded], gated behind opening filters/sort): every visible card needs a
     *  display name immediately, not just on some later user action. Best-effort — a failure here
     *  just leaves every card showing its English-formatted raw name via
     *  [com.mandallaz.pikadex.util.localizedDisplayName]'s own fallback, no error surfaced, same as
     *  every other enrichment-only fetch in this app (e.g. [PokedexDetailViewModel]'s
     *  formVersionGroup). */
    private fun loadSpeciesNamesIfNeeded() {
        if (_uiState.value.speciesNames.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val names = repository.getAllSpeciesNames()
                _uiState.update { it.copy(speciesNames = names) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort, see this function's own doc.
            }
        }
    }

    fun loadBaseStatsIfNeeded() {
        if (_uiState.value.baseStats.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStatsLoading = true) }
            try {
                // One bulk fetch feeds both stats (for sorting) and legendary/mythical status (for
                // the rarity filter) — they're the same GraphQL payload (see S-1/PokemonBasics).
                val basics = repository.getAllBasics()
                _uiState.update {
                    it.copy(
                        baseStats = basics.mapValues { (_, b) -> b.stats },
                        legendaryNames = basics.filterValues { b -> b.isLegendary }.keys,
                        mythicalNames = basics.filterValues { b -> b.isMythical }.keys,
                        typesByName = basics.mapValues { (_, b) -> b.types },
                        isStatsLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e // see onTypeToggled
            } catch (e: Exception) {
                // Also clears sortStat: leaving a stat sort selected here left the chip reading
                // "Sort: Attack" while the grid — now that computeDisplayed refuses to apply a
                // stat sort with no data behind it — silently stayed in whatever order it was in,
                // with no indication the sort never actually happened.
                _uiState.update {
                    it.copy(
                        isStatsLoading = false,
                        sortStat = null,
                        errorMessage = "Couldn't load base stats — sorting by stat is unavailable."
                    )
                }
            }
        }
    }

    fun onSortStatSelected(stat: SortStat?) {
        // Every stat sort's natural first look is "biggest first" (descending), but a name sort's
        // natural first look is A-Z (ascending) — defaulting it to descending too would show Z-A
        // on first pick, backwards from what "Name (A-Z)" itself says.
        _uiState.update { it.copy(sortStat = stat, sortAscending = stat == SortStat.NAME) }
    }

    fun toggleSortDirection() {
        _uiState.update { it.copy(sortAscending = !it.sortAscending) }
    }

    /** [minimum] of 0 removes the constraint entirely rather than storing a no-op 0 entry — see
     *  the KDoc on [PokedexListUiState.statMinimums]. */
    fun onStatMinimumChanged(statKey: String, minimum: Int) {
        _uiState.update {
            it.copy(statMinimums = if (minimum <= 0) it.statMinimums - statKey else it.statMinimums + (statKey to minimum))
        }
    }

    fun clearFilters() {
        tierOptionsGen = null
        typeFilterJob?.cancel()
        moveFilterJob?.cancel()
        abilityFilterJob?.cancel()
        tierFilterJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTypes = emptySet(), typeFilterNames = null,
                selectedMove = null, moveFilterNames = null,
                selectedAbility = null, abilityFilterNames = null,
                selectedFormatGen = null, formatTierOptions = emptyList(),
                selectedFormatTier = null, formatFilterNames = null,
                showFavoritesOnly = false,
                rarityFilter = null,
                counterFilterActive = false,
                statMinimums = emptyMap(),
                // Same reason as the individual cancel sites: whichever of the four jobs just got
                // cancelled will never clear this itself, so Reset while a filter was still
                // resolving used to leave the grid's spinner up permanently.
                isFilterLoading = false,
                // Reset used to leave a sort applied while making the Reset chip itself disappear
                // (hasActiveFilters didn't count sortStat) — so there was no visible way back to
                // dex order except reopening the Sort dialog and picking "No sorting" by hand.
                sortStat = null, sortAscending = false
            )
        }
    }

    fun onRarityFilterSelected(filter: RarityFilter?) {
        _uiState.update { it.copy(rarityFilter = filter) }
    }

    /** Binary toggle, same shape as [RarityFilter]'s on/off entries — "counters *any* triangle" per
     *  F33's resolved scope, not a per-triangle picker. */
    fun onCounterFilterToggled() {
        _uiState.update { it.copy(counterFilterActive = !it.counterFilterActive) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
