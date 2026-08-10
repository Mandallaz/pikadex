package com.mandallaz.pikadex.ui.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.CryCache
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.PokedexListContext
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import com.mandallaz.pikadex.util.Cries
import com.mandallaz.pikadex.util.CryPlayer
import com.mandallaz.pikadex.util.LearnedMove
import com.mandallaz.pikadex.util.MoveCategory
import com.mandallaz.pikadex.util.TeamImpactSummary
import com.mandallaz.pikadex.util.TypeTriangle
import com.mandallaz.pikadex.util.TypeTriangles
import com.mandallaz.pikadex.util.adjacentNames
import com.mandallaz.pikadex.util.computeDefensiveMultipliers
import com.mandallaz.pikadex.util.computeTeamImpact
import com.mandallaz.pikadex.util.computeTeamMatrices
import com.mandallaz.pikadex.util.coverageGaps
import com.mandallaz.pikadex.util.movesForCategory
import com.mandallaz.pikadex.util.namesForAdjacency
import com.mandallaz.pikadex.util.sharedWeaknesses
import com.mandallaz.pikadex.util.teamImmunities
import com.mandallaz.pikadex.util.teamQuadWeaknesses
import com.mandallaz.pikadex.util.teamResistances
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.File

data class PokedexDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val pokemon: PokemonDto? = null,
    val species: PokemonSpeciesDto? = null,
    val evolutionChain: EvolutionChainDto? = null,
    val typeMatchups: Map<String, Double> = emptyMap(),
    val abilityDescriptions: Map<String, String> = emptyMap(),
    /** issue #16 — triangles this Pokémon's typing is the exact best counter to (see
     *  [TypeTriangles.counteredBy]). The card that reads this is hidden entirely when it's empty;
     *  merely being *part of* a triangle (the pre-F26 `memberTriangles`) was dropped as too weak a
     *  signal to be worth a callout on its own. */
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
    val compareCandidates: List<String> = emptyList(),
    /** Swipe/chevron targets for issue #7 — the Pokédex list's own current filtered/sorted
     *  order when this Pokémon is part of it (see [PokedexListContext]/[namesForAdjacency]),
     *  otherwise [getMasterList]'s order. Best-effort: a failure here leaves both null (see
     *  [PokedexDetailViewModel.load]'s `orNullUnlessCancelled` use for it) rather than blocking the
     *  rest of the page on a feature that's a convenience, not the point of the page. */
    val previousPokemonName: String? = null,
    val nextPokemonName: String? = null,
    /** Result of issue #2's "team coverage impact" card — what adding this Pokémon would
     *  change about the team's shared weaknesses and coverage gaps. Null until [loadTeamImpact]
     *  computes it, and reset by [clearTeamImpact] whenever the card's visibility condition stops
     *  holding (no team, team full, or navigating to a different Pokémon) so a later reappearance
     *  doesn't flash stale data. */
    val teamImpact: TeamImpactSummary? = null,
    val isTeamImpactLoading: Boolean = false,
    val teamImpactError: String? = null,
    // B9 — rawName -> (languageCode -> localized species name), bulk-fetched alongside moveInfo/
    // percentiles below; best-effort like those, so a failure just leaves the title/evolution
    // chain names in their English-formatted fallback rather than failing the whole page.
    val speciesNames: Map<String, Map<String, String>> = emptyMap(),
    // B11 — same shape/reasoning as speciesNames, for move and ability names respectively.
    val moveLocalizedNames: Map<String, Map<String, String>> = emptyMap(),
    val abilityLocalizedNames: Map<String, Map<String, String>> = emptyMap()
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
    private val repository: PokedexRepositoryApi = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PokedexDetailUiState())
    val uiState: StateFlow<PokedexDetailUiState> = _uiState.asStateFlow()

    val team: StateFlow<List<NamedApiResource>> = TeamRepository.team
    val favorites: StateFlow<Set<String>> = FavoritesRepository.favorites

    private var loadedFor: String? = null
    private var teamImpactJob: Job? = null

    private val cryPlayer = CryPlayer()
    val isCryPlaying: StateFlow<Boolean> = cryPlayer.isPlaying

    /** F34 — plays this Pokémon's cry, preferring an already-prefetched local file (see
     *  [CryCache]/[com.mandallaz.pikadex.data.PrefetchTier.CRIES]) over streaming it, and falling
     *  back from the current-gen cry to the Gen 5-era one on failure (see [CryPlayer.play]'s doc on
     *  why that's a silent fallback, not a surfaced error). [context] is used transiently to resolve
     *  the cache file path — same per-call-not-stored pattern as [com.mandallaz.pikadex.data.PrefetchManager.start]. */
    fun playCry(context: Context, id: Int) {
        viewModelScope.launch {
            val cachedFile = withContext(Dispatchers.IO) { CryCache.file(context, id) }
            val source = resolveCrySource(cachedFile, id)
            cryPlayer.play(source.primary, source.fallback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cryPlayer.release()
    }

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
                    val speciesNamesDeferred = async { repository.getAllSpeciesNames() }
                    val moveLocalizedNamesDeferred = async { repository.getAllMoveLocalizedNames() }
                    val abilityLocalizedNamesDeferred = async { repository.getAllAbilityLocalizedNames() }

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
                    val speciesNames = orNullUnlessCancelled { speciesNamesDeferred.await() }.orEmpty()
                    val moveLocalizedNames = orNullUnlessCancelled { moveLocalizedNamesDeferred.await() }.orEmpty()
                    val abilityLocalizedNames = orNullUnlessCancelled { abilityLocalizedNamesDeferred.await() }.orEmpty()

                    // Best-effort, same shape as formVersionGroup above. getMasterList() is already
                    // cached by the time most detail screens open (the list screen itself, Compare,
                    // and this screen's own Add-to-team path all warm it first), so this is normally
                    // instant regardless of which of the two orders namesForAdjacency picks.
                    val (previousName, nextName) = orNullUnlessCancelled {
                        val displayedNames = PokedexListContext.displayedNames.value
                        val masterNames = repository.getMasterList().map { it.name }
                        val orderedNames = namesForAdjacency(displayedNames, masterNames, bundle.pokemon.name)
                        adjacentNames(orderedNames, bundle.pokemon.name)
                    } ?: (null to null)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pokemon = bundle.pokemon,
                            species = bundle.species,
                            evolutionChain = bundle.evolutionChain,
                            typeMatchups = matchups,
                            abilityDescriptions = descriptions,
                            counteredTriangles = counteredTriangles,
                            moveInfo = moveInfo,
                            statPercentiles = percentiles,
                            formVersionGroup = formVersionGroup,
                            groupedMoves = groupedMoves,
                            previousPokemonName = previousName,
                            nextPokemonName = nextName,
                            speciesNames = speciesNames,
                            moveLocalizedNames = moveLocalizedNames,
                            abilityLocalizedNames = abilityLocalizedNames
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
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

    /** issue #2 — previews what adding this Pokémon to the active team would change about the
     *  team's shared weaknesses and coverage gaps. Shown as an always-on card on the detail page
     *  rather than behind a button (revised 2026-08-09), so this is now a no-op self-gating on
     *  exactly the same condition the screen uses to decide whether to show that card: a team
     *  exists, has room to grow, and doesn't already contain this Pokémon (there's nothing to
     *  preview about "adding" something already on the roster). Safe to call on every recomposition
     *  the card's visibility condition holds — [clearTeamImpact] is what the screen calls once it
     *  doesn't.
     *
     *  Computes the "before" matrix fresh alongside "after" rather than threading TeamViewModel's
     *  already-computed one across ViewModels — every fetch involved is AsyncCache'd, so
     *  recomputing costs nothing extra in practice on a warm cache. */
    fun loadTeamImpact() {
        val pokemon = _uiState.value.pokemon ?: return
        val beforeMembers = TeamRepository.team.value
        if (beforeMembers.isEmpty() || beforeMembers.size >= TeamRepository.MAX_SIZE) return
        if (beforeMembers.any { it.name == pokemon.name }) return
        val candidate = NamedApiResource(pokemon.name, "https://pokeapi.co/api/v2/pokemon/${pokemon.id}/")
        val afterMembers = beforeMembers + candidate
        teamImpactJob?.cancel()
        _uiState.update { it.copy(isTeamImpactLoading = true, teamImpactError = null, teamImpact = null) }
        teamImpactJob = viewModelScope.launch {
            try {
                val (before, after) = supervisorScope {
                    val beforeDeferred = async { computeTeamMatrices(repository, beforeMembers) }
                    val afterDeferred = async { computeTeamMatrices(repository, afterMembers) }
                    beforeDeferred.await() to afterDeferred.await()
                }
                val beforeNames = beforeMembers.map { it.name }
                val afterNames = afterMembers.map { it.name }
                val impact = computeTeamImpact(
                    beforeSharedWeaknesses = sharedWeaknesses(before.defensive, beforeNames),
                    afterSharedWeaknesses = sharedWeaknesses(after.defensive, afterNames),
                    beforeCoverageGaps = coverageGaps(before.offensive, beforeNames),
                    afterCoverageGaps = coverageGaps(after.offensive, afterNames),
                    beforeImmunities = teamImmunities(before.defensive, beforeNames),
                    afterImmunities = teamImmunities(after.defensive, afterNames),
                    beforeQuadWeaknesses = teamQuadWeaknesses(before.defensive, beforeNames),
                    afterQuadWeaknesses = teamQuadWeaknesses(after.defensive, afterNames),
                    beforeResistances = teamResistances(before.defensive, beforeNames),
                    afterResistances = teamResistances(after.defensive, afterNames)
                )
                _uiState.update { it.copy(isTeamImpactLoading = false, teamImpact = impact) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isTeamImpactLoading = false, teamImpactError = "Network error while previewing team impact.")
                }
            }
        }
    }

    /** Resets the F15 card's state, called by the screen whenever its visibility condition stops
     *  holding so a later reappearance (different team, or the card returning) doesn't flash the
     *  previous result while a new one loads. */
    fun clearTeamImpact() {
        teamImpactJob?.cancel()
        _uiState.update { it.copy(isTeamImpactLoading = false, teamImpactError = null, teamImpact = null) }
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

/** F34: what [CryPlayer] should be given for Pokémon [id] — the local file if [cachedFile] is
 *  genuinely non-empty (i.e. actually downloaded by the F34 prefetch tier, not just a path that
 *  doesn't exist yet), otherwise the network URL with a legacy-cry fallback. A cached file has no
 *  fallback: it's already a specific cry (whichever [com.mandallaz.pikadex.data.PrefetchTier.CRIES]
 *  downloaded), not something to retry with a different variant on failure. Internal, not private,
 *  so it's unit-testable directly against a plain [File] — no Android framework/Context needed for
 *  this half of the decision, same reasoning as [selectShowdownUrl] in `PokedexDetailScreen.kt`. */
internal fun resolveCrySource(cachedFile: File, id: Int): CrySource =
    if (cachedFile.length() > 0L) {
        CrySource(primary = cachedFile.absolutePath, fallback = null)
    } else {
        CrySource(primary = Cries.latestCryUrl(id), fallback = Cries.legacyCryUrl(id))
    }

internal data class CrySource(val primary: String, val fallback: String?)
