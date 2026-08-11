package com.mandallaz.pikadex.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.SuggestionSettings
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.TeamSlot
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import com.mandallaz.pikadex.util.PresetTeam
import com.mandallaz.pikadex.util.PresetTeams
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.SuggestionCandidate
import com.mandallaz.pikadex.util.TeamSuggestion
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.computeTeamMatrices
import com.mandallaz.pikadex.util.coverageGaps
import com.mandallaz.pikadex.util.filterByTierCeiling
import com.mandallaz.pikadex.util.findConflictingForms
import com.mandallaz.pikadex.util.rankSuggestions
import com.mandallaz.pikadex.util.sharedWeaknesses
import com.mandallaz.pikadex.ui.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamUiState(
    val members: List<NamedApiResource> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    // matrix[typeName][memberName] = defensive multiplier against that attacking type
    val matrix: Map<String, Map<String, Double>> = emptyMap(),
    /** offensiveMatrix[defendingType][memberName] = the best multiplier that member can *deal* to
     *  that type, across every attacking type it has access to. Computed in the same pass as
     *  [matrix] and stale under exactly the same conditions. */
    val offensiveMatrix: Map<String, Map<String, Double>> = emptyMap(),
    // Which member names [matrix] was actually computed for — a member missing from a type's row
    // is otherwise indistinguishable from "genuinely neutral (x1)" to a plain `row[name] ?: 1.0`
    // lookup. Whenever this doesn't match the current [members] (mid-fetch, or the fetch just
    // failed and left the previous team's matrix behind), the matrix is stale and must not be
    // rendered as if it were live data.
    val matrixComputedFor: Set<String> = emptySet(),
    /** Dex ids for the species named in [com.mandallaz.pikadex.util.PresetTeams], so the preset
     *  picker can preview each roster as sprites. Empty until the master list is available (the
     *  picker then falls back to names), since presets deliberately store names, not ids. */
    val presetSpriteIds: Map<String, Int> = emptyMap(),
    /** Candidates that would improve both the team's shared weaknesses and coverage gaps at once —
     *  see [rankSuggestions]/issue #11. Cleared whenever the gate in [loadSuggestions]
     *  no longer holds (team full, empty, or the matrix isn't fresh). */
    val suggestions: List<TeamSuggestion> = emptyList(),
    val isSuggestionsLoading: Boolean = false,
    /** Dex ids for [suggestions], resolved the same way [presetSpriteIds] is — sprites are
     *  cosmetic, not part of the pure ranking, so they're kept out of [TeamSuggestion] itself. */
    val suggestionSpriteIds: Map<String, Int> = emptyMap(),
    /** The competitive tier ceiling [suggestions] was filtered against, mirrored from
     *  [SuggestionSettings] at the moment [suggestions] was computed (issue #11) — read by
     *  the Suggestions card so it can explain why the list is short/empty rather than leaving that
     *  unexplained, since the setting itself lives on a different screen. Null means no limit. */
    val suggestionTierCeiling: String? = null,
    // B9 follow-up — same bulk fetch/shape as PokedexListViewModel's speciesNames; the Team screen
    // shows species names too (roster chips, suggestion tiles) and was still falling back to the
    // English-formatted raw name for every non-English language.
    val speciesNames: Map<String, Map<String, String>> = emptyMap()
) {
    val isMatrixStale: Boolean
        get() = matrixComputedFor != members.map { it.name }.toSet()

    /** Types where at least half the team is weak (>1x) — the team's shared vulnerabilities. */
    val sharedWeaknesses: List<String>
        get() {
            if (members.isEmpty() || isMatrixStale) return emptyList()
            return sharedWeaknesses(matrix, members.map { it.name })
        }

    /** Types nobody on the team can hit for more than neutral — the offensive counterpart of
     *  [sharedWeaknesses], and the thing a defence-only view of a team never surfaces. */
    val coverageGaps: List<String>
        get() {
            if (members.isEmpty() || isMatrixStale) return emptyList()
            return coverageGaps(offensiveMatrix, members.map { it.name })
        }

    /** B36 — true when exactly one of [sharedWeaknesses]/[coverageGaps] is non-empty, so
     *  [loadSuggestions]'s dual-fix gate can never find a candidate (it requires both). Without
     *  this, the Suggestions card just vanishes with no explanation even while a weaknesses/gaps
     *  callout is still visibly telling the user something is wrong — this powers a small
     *  explanatory message in its place for that specific case. Deliberately false when *both*
     *  lists are non-empty but suggestions still came back empty (e.g. tier filter too
     *  restrictive) — that's a different, out-of-scope case per the issue. */
    val hasUnfixableSingleAxisIssue: Boolean
        get() = sharedWeaknesses.isNotEmpty() != coverageGaps.isNotEmpty()
}

class TeamViewModel @JvmOverloads constructor(
    private val repository: PokedexRepositoryApi = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamUiState())
    val uiState: StateFlow<TeamUiState> = _uiState.asStateFlow()

    val teams: StateFlow<List<TeamSlot>> = TeamRepository.teams
    val activeTeamId: StateFlow<Int> = TeamRepository.activeTeamId

    private var matrixJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        viewModelScope.launch {
            TeamRepository.team.collect { members -> computeMatrix(members) }
        }
        // Settings lives on a different tab, and this ViewModel survives a tab switch (bottom-nav
        // back stack entries keep their ViewModelStore) — without this, changing the tier ceiling
        // in Settings and switching back to Team left the previous ceiling's suggestions on screen
        // until the team itself changed again. loadSuggestions() no-ops safely (isMatrixStale gate)
        // if the matrix isn't ready yet for this collector's first (immediate) emission.
        viewModelScope.launch {
            SuggestionSettings.maxTier.collect { loadSuggestions() }
        }
        loadSpeciesNamesIfNeeded()
    }

    /** F66 — collects the shared [LocalizedNames] cache into this screen's own UiState; see
     *  [LocalizedNames]'s doc for the full rationale. */
    private fun loadSpeciesNamesIfNeeded() {
        viewModelScope.launch { LocalizedNames.ensureLoaded(repository) }
        viewModelScope.launch {
            LocalizedNames.speciesNames.collect { names -> _uiState.update { it.copy(speciesNames = names) } }
        }
    }

    /** A failed matrix fetch used to be a dead end: the matrix was only ever recomputed when the
     *  team itself changed, so an offline failure left blank cells and an error line until the user
     *  added or removed a member. */
    fun retry() = computeMatrix(_uiState.value.members)

    private fun computeMatrix(members: List<NamedApiResource>) {
        // Explicit job tracking rather than collectLatest, so [retry] can restart the same work
        // without a team change to trigger it — and so a superseded fetch is cancelled the same way
        // the Pokédex list's filter jobs are.
        matrixJob?.cancel()
        suggestionsJob?.cancel()
        if (members.isEmpty()) {
            // Clear only what belongs to the team itself. presetSpriteIds is unrelated to team
            // membership and expensive to rebuild (it resolves every preset roster's sprites
            // against the master list) — a blanket TeamUiState() dropped it every time the user
            // removed their last member, so the preset picker's previews fell back to bare names
            // until preparePresetPreviews() re-ran on the next dialog open.
            _uiState.update {
                it.copy(
                    members = emptyList(),
                    isLoading = false,
                    errorMessage = null,
                    matrix = emptyMap(),
                    offensiveMatrix = emptyMap(),
                    matrixComputedFor = emptySet(),
                    suggestions = emptyList(),
                    suggestionSpriteIds = emptyMap()
                )
            }
            return
        }
        _uiState.update { it.copy(members = members, isLoading = true, errorMessage = null) }
        matrixJob = viewModelScope.launch {
            try {
                // supervisorScope so one member's failed fetch surfaces as a normal catchable
                // exception rather than risking an uncaught crash — see the identical fix (and
                // full explanation) in PokedexDetailViewModel.load().
                val result = computeTeamMatrices(repository, members)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        matrix = result.defensive,
                        offensiveMatrix = result.offensive,
                        matrixComputedFor = members.map { m -> m.name }.toSet()
                    )
                }
                // Only worth attempting once the matrix that sharedWeaknesses/coverageGaps are
                // read from is actually fresh for this team.
                loadSuggestions()
            } catch (e: CancellationException) {
                // A superseded fetch (the team changed again, or Retry was tapped) isn't a network
                // failure — and CancellationException *is* an Exception, so without this it fell into
                // the handler below and flashed a "check your connection" error that never happened.
                throw e
            } catch (e: Exception) {
                // matrixComputedFor is deliberately left untouched here: it now reflects
                // whatever the *previous* successful fetch was for, which — since `members` has
                // already changed — no longer matches the current team. UI reads that mismatch
                // (isMatrixStale) to render the matrix as unknown rather than as leftover data
                // for a team composition that no longer exists.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = UiText(R.string.team_error_compute_matrix),
                        suggestions = emptyList(),
                        suggestionSpriteIds = emptyMap()
                    )
                }
            }
        }
    }

    /** Candidates that would fix both a shared weakness and a coverage gap at once — see
     *  [rankSuggestions]/issue #11. Gated on the matrix being fresh (it's what [TeamUiState.sharedWeaknesses]
     *  and [TeamUiState.coverageGaps] are read from) and the team having room to grow; anything
     *  outside that gate just clears the list rather than showing suggestions for a team that no
     *  longer applies. */
    private fun loadSuggestions() {
        suggestionsJob?.cancel()
        val state = _uiState.value
        if (state.isMatrixStale || state.members.isEmpty() || state.members.size >= TeamRepository.MAX_SIZE) {
            _uiState.update { it.copy(isSuggestionsLoading = false, suggestions = emptyList(), suggestionSpriteIds = emptyMap()) }
            return
        }
        val sharedWeaknesses = state.sharedWeaknesses
        val coverageGaps = state.coverageGaps
        if (sharedWeaknesses.isEmpty() || coverageGaps.isEmpty()) {
            _uiState.update { it.copy(isSuggestionsLoading = false, suggestions = emptyList(), suggestionSpriteIds = emptyMap()) }
            return
        }
        val excludeNames = state.members.map { it.name }.toSet()
        val maxTier = SuggestionSettings.maxTier.value
        _uiState.update { it.copy(isSuggestionsLoading = true, suggestionTierCeiling = maxTier) }
        suggestionsJob = viewModelScope.launch {
            try {
                var tierByShowdownKey: Map<String, String> = emptyMap()
                val (masterList, basics, typeDetails) = supervisorScope {
                    val masterDeferred = async { repository.getMasterList() }
                    val basicsDeferred = async { repository.getAllBasics() }
                    // Reuses whatever computeMatrix already warmed in the cache — a plain cache
                    // hit for every type that mattered to this team, a real fetch only for the
                    // handful (if any) it didn't need.
                    val typeDetailsDeferred = TypeIds.standardTypeNames.associateWith { type ->
                        async { repository.getTypeDetail(type) }
                    }
                    // Only fetched when a ceiling is actually set — no reason to pay for this
                    // request (even a cached one) for the common case of no tier filter.
                    val tiersDeferred = maxTier?.let { async { repository.getSmogonTiers(Smogon.SUGGESTION_TIER_GEN) } }
                    tierByShowdownKey = tiersDeferred?.await().orEmpty()
                    Triple(masterDeferred.await(), basicsDeferred.await(), typeDetailsDeferred.mapValues { it.value.await() })
                }
                val idByName = masterList.mapNotNull { r -> r.id?.let { r.name to it } }.toMap()
                val candidates = basics.mapNotNull { (name, basic) ->
                    val id = idByName[name] ?: return@mapNotNull null
                    // Alternate forms (mega/gmax/regional/...) — same id-range heuristic as
                    // PokedexRepository.getPokemonDetailBundle's comment.
                    if (id >= 10000) return@mapNotNull null
                    val total = basic.stats.values.sum()
                    if (total < 300) return@mapNotNull null
                    SuggestionCandidate(name, basic.types, total)
                }
                val tierFilteredCandidates = filterByTierCeiling(candidates, maxTier, tierByShowdownKey)
                val ranked = rankSuggestions(sharedWeaknesses, coverageGaps, tierFilteredCandidates, typeDetails, excludeNames)
                // basics (unlike candidates) still has every alt form — exactly the universe
                // findConflictingForms needs to check a suggested species' mega/gmax/regional
                // variants against.
                val typesByName = basics.mapValues { it.value.types }
                val withFormNotes = ranked.map { suggestion ->
                    suggestion.copy(
                        conflictingForms = findConflictingForms(
                            suggestion.name, suggestion.types, sharedWeaknesses, coverageGaps, typesByName, typeDetails
                        )
                    )
                }
                val spriteIds = withFormNotes.mapNotNull { s -> idByName[s.name]?.let { s.name to it } }.toMap()
                _uiState.update { it.copy(isSuggestionsLoading = false, suggestions = withFormNotes, suggestionSpriteIds = spriteIds) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort: the Suggestions card just doesn't show anything new, same as it
                // wouldn't if offline entirely.
                _uiState.update { it.copy(isSuggestionsLoading = false) }
            }
        }
    }

    fun removeFromTeam(member: NamedApiResource) = TeamRepository.remove(member)

    /** Adds a suggested candidate by name — the id comes from [TeamUiState.suggestionSpriteIds],
     *  resolved via the master list the same way every other add-to-team entry point builds its
     *  [NamedApiResource]. No-ops (same as [TeamRepository.add]) if the team filled up since the
     *  suggestions list was computed. */
    fun addSuggestion(name: String) {
        val id = _uiState.value.suggestionSpriteIds[name] ?: return
        TeamRepository.add(NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/"))
    }

    fun createTeam(name: String): Int = TeamRepository.createTeam(name)
    fun renameTeam(id: Int, name: String) = TeamRepository.renameTeam(id, name)
    fun deleteTeam(id: Int) = TeamRepository.deleteTeam(id)
    fun setActiveTeam(id: Int) = TeamRepository.setActiveTeam(id)

    /** Resolves sprite ids for every preset roster, for the picker's previews. Best-effort: with no
     *  master list (offline, first run) the picker just lists names instead. */
    fun preparePresetPreviews() {
        if (_uiState.value.presetSpriteIds.isNotEmpty()) return
        viewModelScope.launch {
            val ids = try {
                val byName = repository.getMasterList().associateBy { it.name }
                PresetTeams.ALL.flatMap { it.pokemon }.distinct()
                    .mapNotNull { name -> byName[name]?.id?.let { name to it } }
                    .toMap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            _uiState.update { it.copy(presetSpriteIds = ids) }
        }
    }

    /** Replaces the roster with [preset]'s. The preset stores species *names*; the real resources
     *  (and thus the sprite ids) come from the already-downloaded master list, so a name that no
     *  longer exists in the dex is dropped rather than rendering as a broken id-0 sprite. */
    fun loadPreset(preset: PresetTeam) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val byName = repository.getMasterList().associateBy { it.name }
                val resolved = preset.pokemon.mapNotNull { byName[it] }
                if (resolved.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = UiText(R.string.team_error_load_preset, listOf(preset.trainer)))
                    }
                    return@launch
                }
                // The team flow this collects from drives computeMatrix, so no explicit recompute —
                // except when there is nothing to emit. TeamRepository.team is a StateFlow, and a
                // StateFlow conflates equal values: loading the preset you are already running
                // assigns an equal list, emits nothing, and the collector that would have cleared
                // the spinner set above never runs. The screen then span forever over a blank
                // matrix, since every cell is blanked while isLoading is true.
                val before = TeamRepository.team.value
                TeamRepository.replaceAll(resolved)
                if (TeamRepository.team.value == before) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = UiText(R.string.team_error_load_preset_network, listOf(preset.trainer)))
                }
            }
        }
    }

    /** Same as [loadPreset], but into a brand new team slot (named after the trainer) instead of
     *  overwriting the active one — the "Load into a new team" choice in the preset confirmation. */
    fun loadPresetIntoNewTeam(preset: PresetTeam) {
        val newId = TeamRepository.createTeam(preset.trainer)
        TeamRepository.setActiveTeam(newId)
        loadPreset(preset)
    }
}
