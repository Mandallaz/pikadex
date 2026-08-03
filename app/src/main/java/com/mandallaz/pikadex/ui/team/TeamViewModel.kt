package com.mandallaz.pikadex.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.computeDefensiveMultipliers
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
    // Which member names [matrix] was actually computed for — a member missing from a type's row
    // is otherwise indistinguishable from "genuinely neutral (x1)" to a plain `row[name] ?: 1.0`
    // lookup. Whenever this doesn't match the current [members] (mid-fetch, or the fetch just
    // failed and left the previous team's matrix behind), the matrix is stale and must not be
    // rendered as if it were live data.
    val matrixComputedFor: Set<String> = emptySet()
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
}

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
                val matrix = supervisorScope {
                    // Every member is independent of every other, and every type detail lookup
                    // is independent too — sequentially this was up to 18 round trips (6
                    // members x up to 3 calls each) before the matrix could render at all.
                    val memberResults = members.map { member ->
                        async {
                            val types = repository.getPokemonTypes(member.name)
                            val typeDetails = types.map { async { repository.getTypeDetail(it) } }.awaitAll()
                            member.name to computeDefensiveMultipliers(typeDetails)
                        }
                    }.awaitAll()

                    val result = mutableMapOf<String, MutableMap<String, Double>>()
                    TypeIds.standardTypeNames.forEach { result[it] = mutableMapOf() }
                    memberResults.forEach { (memberName, defensiveMultipliers) ->
                        defensiveMultipliers.forEach { (attackType, multiplier) ->
                            result.getOrPut(attackType) { mutableMapOf() }[memberName] = multiplier
                        }
                    }
                    result
                }
                _uiState.update {
                    it.copy(isLoading = false, matrix = matrix, matrixComputedFor = members.map { m -> m.name }.toSet())
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
}
