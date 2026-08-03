package com.mandallaz.pikadex.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.PresetTeam
import com.mandallaz.pikadex.util.PresetTeams
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.bestOffensiveMultipliers
import com.mandallaz.pikadex.util.computeDefensiveMultipliers
import com.mandallaz.pikadex.util.computeOffensiveMultipliers
import com.mandallaz.pikadex.util.coverageGaps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamUiState(
    val members: List<NamedApiResource> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
    val presetSpriteIds: Map<String, Int> = emptyMap()
) {
    val isMatrixStale: Boolean
        get() = matrixComputedFor != members.map { it.name }.toSet()

    /** Types where at least half the team is weak (>1x) — the team's shared vulnerabilities. */
    val sharedWeaknesses: List<String>
        get() {
            if (members.isEmpty() || isMatrixStale) return emptyList()
            return TypeIds.standardTypeNames.filter { type ->
                val row = matrix[type] ?: return@filter false
                val weakCount = members.count { (row[it.name] ?: 1.0) > 1.0 }
                weakCount * 2 >= members.size && weakCount > 0
            }
        }

    /** Types nobody on the team can hit for more than neutral — the offensive counterpart of
     *  [sharedWeaknesses], and the thing a defence-only view of a team never surfaces. */
    val coverageGaps: List<String>
        get() {
            if (members.isEmpty() || isMatrixStale) return emptyList()
            return coverageGaps(offensiveMatrix, members.map { it.name })
        }
}

/** One member's raw inputs, gathered concurrently before the two matrices are assembled. */
private data class MemberMatchups(
    val name: String,
    val defensive: Map<String, Double>,
    val stabTypes: List<String>,
    val moveNames: List<String>
)

/** PokeAPI's damage_class for moves that deal no damage. */
private const val STATUS_DAMAGE_CLASS = "status"

class TeamViewModel @JvmOverloads constructor(
    private val repository: PokedexRepository = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamUiState())
    val uiState: StateFlow<TeamUiState> = _uiState.asStateFlow()

    private var matrixJob: Job? = null

    init {
        viewModelScope.launch {
            TeamRepository.team.collect { members -> computeMatrix(members) }
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
        if (members.isEmpty()) {
            _uiState.update { TeamUiState() }
            return
        }
        _uiState.update { it.copy(members = members, isLoading = true, errorMessage = null) }
        matrixJob = viewModelScope.launch {
            try {
                // supervisorScope so one member's failed fetch surfaces as a normal catchable
                // exception at awaitAll() rather than risking an uncaught crash — see the
                // identical fix (and full explanation) in PokedexDetailViewModel.load().
                val (matrix, offensiveMatrix) = supervisorScope {
                    // One bulk, already-cached lookup for the whole app rather than one call per
                    // move: a team's six movepools run to well over a thousand entries between them.
                    val moveInfoDeferred = async { repository.getAllMoveInfo() }

                    // Every member is independent of every other, and every type detail lookup
                    // is independent too — sequentially this was up to 18 round trips (6
                    // members x up to 3 calls each) before the matrix could render at all.
                    val memberResults = members.map { member ->
                        async {
                            val types = repository.getPokemonTypes(member.name)
                            val typeDetails = types.map { async { repository.getTypeDetail(it) } }.awaitAll()
                            // Same cache entry as getPokemonTypes above, so this is free.
                            val moveNames = repository.getPokemonLevelUpMoveNames(member.name)
                            MemberMatchups(
                                name = member.name,
                                defensive = computeDefensiveMultipliers(typeDetails),
                                stabTypes = types,
                                moveNames = moveNames
                            )
                        }
                    }.awaitAll()

                    val moveInfo = moveInfoDeferred.await()

                    // What each member can attack with: its own types, plus the type of every
                    // *damaging* move it can learn. Status moves are excluded — Thunder Wave being
                    // Electric says nothing about whether this pokemon can dent a Water type.
                    val attackingTypesByMember = memberResults.associate { member ->
                        val fromMoves = member.moveNames.mapNotNull { moveName ->
                            moveInfo[moveName]?.takeIf { it.damageClass != STATUS_DAMAGE_CLASS }?.type
                        }
                        member.name to (member.stabTypes + fromMoves).toSet()
                    }

                    val offensiveByType = attackingTypesByMember.values.flatten().distinct()
                        .map { type -> async { type to computeOffensiveMultipliers(repository.getTypeDetail(type)) } }
                        .awaitAll().toMap()

                    val defensive = mutableMapOf<String, MutableMap<String, Double>>()
                    val offensive = mutableMapOf<String, MutableMap<String, Double>>()
                    TypeIds.standardTypeNames.forEach {
                        defensive[it] = mutableMapOf()
                        offensive[it] = mutableMapOf()
                    }
                    memberResults.forEach { member ->
                        member.defensive.forEach { (attackType, multiplier) ->
                            defensive.getOrPut(attackType) { mutableMapOf() }[member.name] = multiplier
                        }
                        val best = bestOffensiveMultipliers(
                            attackingTypesByMember[member.name].orEmpty(),
                            offensiveByType
                        )
                        best.forEach { (defendingType, multiplier) ->
                            offensive.getOrPut(defendingType) { mutableMapOf() }[member.name] = multiplier
                        }
                    }
                    defensive to offensive
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        matrix = matrix,
                        offensiveMatrix = offensiveMatrix,
                        matrixComputedFor = members.map { m -> m.name }.toSet()
                    )
                }
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
                _uiState.update { it.copy(isLoading = false, errorMessage = "Network error while computing team matchups. Check your connection.") }
            }
        }
    }

    fun removeFromTeam(member: NamedApiResource) = TeamRepository.remove(member)

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
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Couldn't load ${preset.trainer}'s team.") }
                    return@launch
                }
                // The team flow this collects from drives computeMatrix, so no explicit recompute.
                TeamRepository.replaceAll(resolved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Couldn't load ${preset.trainer}'s team. Check your connection.") }
            }
        }
    }
}
