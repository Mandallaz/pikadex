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
import com.mandallaz.pikadex.util.LearnedMove
import com.mandallaz.pikadex.util.MoveCategory
import com.mandallaz.pikadex.util.TypeTriangle
import com.mandallaz.pikadex.util.TypeTriangles
import com.mandallaz.pikadex.util.computeDefensiveMultipliers
import com.mandallaz.pikadex.util.movesForCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

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
    /** Learned moves grouped and sorted per [MoveCategory] — used to be recomputed by the screen
     *  itself on every recomposition-surviving `remember(pokemon)`, which for a pokemon with a
     *  large moveset (e.g. Mew) meant scanning/sorting hundreds of version-group-detail entries
     *  on the main thread. Computed once here instead, off the main thread. */
    val groupedMoves: Map<MoveCategory, List<LearnedMove>> = emptyMap(),
    /** statApiName (hp/attack/.../speed, plus a synthetic "total") -> this pokemon's percentile
     *  rank (0.0..1.0) among every other pokemon's same stat, for coloring stat bars by how good
     *  the value actually is rather than a fixed per-stat hue. */
    val statPercentiles: Map<String, Double> = emptyMap(),
    /** The form's own PokeAPI version group, used to decide which Smogon dex generations actually
     *  have a page for it. Null when unknown (not fetched yet, or the request failed). */
    val formVersionGroup: String? = null,
    /** Every pokemon name, for the "Compare with…" picker — loaded lazily (see
     *  [PokedexDetailViewModel.loadCompareCandidatesIfNeeded]), not as part of [load], since most
     *  visits to a detail page never open that picker. */
    val compareCandidates: List<String> = emptyList()
)

/** Result of [block], or null if it failed — but never swallowing coroutine cancellation, which
 *  `runCatching` would, letting an already-cancelled load carry on and publish state anyway. */
private inline fun <T> orNullUnlessCancelled(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
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
                // supervisorScope, not a plain coroutine body: without it, a child `async` that
                // fails (e.g. no network) before it's awaited cancels this whole launch's Job as
                // a *child failure*, not a normal thrown exception — the surrounding try/catch
                // can appear to catch it, but the coroutine machinery still surfaces the original
                // exception as uncaught once the (now-cancelled) coroutine completes, crashing the
                // app. This was a real, reproducible offline crash: open any pokemon detail whose
                // data isn't cached with no network. supervisorScope makes a failed child's
                // exception surface only when *that* child is awaited, a normal catchable throw.
                supervisorScope {
                    // The two bulk fetches depend on nothing else here — start them immediately
                    // instead of waiting until after the pokemon/species/evolution chain (which is
                    // a genuine 3-step dependency chain and can't be parallelized further).
                    val moveInfoDeferred = async { repository.getAllMoveInfo() }
                    val allStatsDeferred = async { repository.getAllBaseStats() }

                    val bundle = repository.getPokemonDetailBundle(nameOrId)
                    // Off Dispatchers.Default, not the caller's dispatcher (Main.immediate): this
                    // is pure CPU work over a pokemon's full moveset, not I/O.
                    val groupedMovesDeferred = async(Dispatchers.Default) {
                        MoveCategory.entries.associateWith { bundle.pokemon.movesForCategory(it) }
                    }
                    val typeDetails = bundle.pokemon.types.orEmpty()
                        .map { async { repository.getTypeDetail(it.type.name) } }
                        .awaitAll()
                    val matchups = computeDefensiveMultipliers(typeDetails)
                    val pokemonTypes = bundle.pokemon.types.orEmpty().map { it.type.name }
                    val memberTriangles = TypeTriangles.containing(pokemonTypes)
                    val counteredTriangles = TypeTriangles.counteredBy(pokemonTypes)
                    val abilityNames = bundle.pokemon.abilities.orEmpty().map { it.ability.name }
                    val descriptionsDeferred = async {
                        abilityNames
                            .map { name -> async { name to repository.getAbilityDescription(name) } }
                            .awaitAll()
                            .mapNotNull { (name, description) -> description?.let { name to it } }
                            .toMap()
                    }
                    val descriptions = descriptionsDeferred.await()

                    // Only alternate forms can disagree with their species about which games they
                    // exist in, so ordinary Pokémon skip this request entirely. Non-fatal: without
                    // it the Smogon card just falls back to its suffix-based guess.
                    val formVersionGroup = if ('-' in bundle.pokemon.name) {
                        orNullUnlessCancelled { repository.getFormVersionGroup(bundle.pokemon.name) }
                    } else {
                        null
                    }

                    // These two bulk fetches only *enrich* the page — percentile tint on the stat
                    // bars, and type/power/accuracy under each move name. Everything the page is
                    // actually about (stats, types, abilities, evolution, move names) comes from the
                    // REST bundle already awaited above, so a failure here degrades one detail
                    // rather than failing the whole screen with "check your connection".
                    val moveInfo = orNullUnlessCancelled { moveInfoDeferred.await() }.orEmpty()
                    // Binary-searches a sorted array the repository builds once (see
                    // PokedexRepository.getSortedStatArrays) instead of re-scanning all ~1300
                    // pokemon's values on every single detail load.
                    val percentiles = orNullUnlessCancelled {
                        allStatsDeferred.await() // warms the repository's cache before the per-key lookups below
                        bundle.pokemon.stats.orEmpty().associate { stat ->
                            stat.stat.name to repository.getStatPercentile(stat.stat.name, stat.baseStat)
                        } + mapOf(
                            "total" to repository.getStatPercentile("total", bundle.pokemon.stats.orEmpty().sumOf { it.baseStat })
                        )
                    }.orEmpty()

                    val groupedMoves = groupedMovesDeferred.await()

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
                            statPercentiles = percentiles,
                            formVersionGroup = formVersionGroup,
                            groupedMoves = groupedMoves
                        )
                    }
                }
            } catch (e: Exception) {
                loadedFor = null // let the user retry (e.g. after regaining network) instead of being stuck
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't load this Pokémon. Check your connection.")
                }
            }
        }
    }

    /** Returns null only when there's no pokemon loaded yet to toggle — the screen uses the real
     *  result to decide whether to show the "team is full" snackbar, instead of re-deriving
     *  isTeamFull itself right after the fact. */
    fun toggleTeamMembership(): TeamRepository.ToggleResult? {
        val pokemon = _uiState.value.pokemon ?: return null
        return TeamRepository.toggle(NamedApiResource(pokemon.name, "https://pokeapi.co/api/v2/pokemon/${pokemon.id}/"))
    }

    fun toggleFavorite() {
        val pokemon = _uiState.value.pokemon ?: return
        FavoritesRepository.toggle(pokemon.name)
    }

    fun loadCompareCandidatesIfNeeded() {
        if (_uiState.value.compareCandidates.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val names = repository.getMasterList().map { it.name }
                _uiState.update { it.copy(compareCandidates = names) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Network error while loading the Pokémon list.") }
            }
        }
    }
}
