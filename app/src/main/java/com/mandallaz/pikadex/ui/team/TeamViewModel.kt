package com.mandallaz.pikadex.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.computeDefensiveMultipliers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamUiState(
    val members: List<NamedApiResource> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // matrix[typeName][memberName] = defensive multiplier against that attacking type
    val matrix: Map<String, Map<String, Double>> = emptyMap()
) {
    /** Types where at least half the team is weak (>1x) — the team's shared vulnerabilities. */
    val sharedWeaknesses: List<String>
        get() {
            if (members.isEmpty()) return emptyList()
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

    init {
        viewModelScope.launch {
            TeamRepository.team.collectLatest { members ->
                if (members.isEmpty()) {
                    _uiState.update { TeamUiState() }
                    return@collectLatest
                }
                _uiState.update { it.copy(members = members, isLoading = true, errorMessage = null) }
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
                    _uiState.update { it.copy(isLoading = false, matrix = matrix) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Network error while computing team matchups. Check your connection.") }
                }
            }
        }
    }

    fun removeFromTeam(member: NamedApiResource) = TeamRepository.remove(member)
}
