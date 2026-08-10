package com.mandallaz.pikadex.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** One side of the comparison: the pokemon itself plus its stats' percentile ranks (against every
 *  other pokemon), needed to color its [com.mandallaz.pikadex.ui.components.StatBar]s the same way
 *  the single-pokemon detail screen does. */
data class CompareSide(
    val pokemon: PokemonDto,
    val statPercentiles: Map<String, Double>
)

data class CompareUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val left: CompareSide? = null,
    val right: CompareSide? = null,
    val candidateNames: List<String> = emptyList()
)

class CompareViewModel @JvmOverloads constructor(
    private val repository: PokedexRepositoryApi = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    private var loadedFor: Pair<String, String>? = null

    fun load(leftName: String, rightName: String) {
        val key = leftName to rightName
        if (loadedFor == key) return
        loadedFor = key
        viewModelScope.launch {
            _uiState.update { CompareUiState(isLoading = true, candidateNames = it.candidateNames) }
            try {
                // supervisorScope, not a plain coroutine body: without it, one side's async failing
                // before it's awaited cancels this whole launch's Job as a child failure rather than
                // a normal thrown exception, which can surface as an uncaught crash once the
                // cancelled coroutine completes — see the identical note in
                // PokedexDetailViewModel.load, which hit this as a real offline crash.
                supervisorScope {
                    val leftDeferred = async { loadSide(leftName) }
                    val rightDeferred = async { loadSide(rightName) }
                    val left = leftDeferred.await()
                    val right = rightDeferred.await()
                    _uiState.update { it.copy(isLoading = false, left = left, right = right) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedFor = null // let the user retry (e.g. after regaining network)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't load this comparison. Check your connection.")
                }
            }
        }
    }

    private suspend fun loadSide(name: String): CompareSide {
        val bundle = repository.getPokemonDetailBundle(name)
        val stats = bundle.pokemon.stats.orEmpty()
        val percentiles = stats.associate { stat ->
            stat.stat.name to repository.getStatPercentile(stat.stat.name, stat.baseStat)
        } + mapOf("total" to repository.getStatPercentile("total", stats.sumOf { it.baseStat }))
        return CompareSide(bundle.pokemon, percentiles)
    }

    /** The master pokemon-name list, for the "compare with a different pokemon" picker — loaded
     *  lazily since opening the compare screen doesn't always mean the user wants to re-pick. */
    fun loadCandidatesIfNeeded() {
        if (_uiState.value.candidateNames.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val names = repository.getMasterList().map { it.name }
                _uiState.update { it.copy(candidateNames = names) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Network error while loading the Pokémon list.") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
