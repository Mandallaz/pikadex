package com.mandallaz.pikadex.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import com.mandallaz.pikadex.ui.UiText
import com.mandallaz.pikadex.util.TOTAL
import com.mandallaz.pikadex.util.baseStatTotal
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
    val errorMessage: UiText? = null,
    val left: CompareSide? = null,
    val right: CompareSide? = null,
    val candidateNames: List<String> = emptyList(),
    // B9 follow-up — see PokedexListUiState.speciesNames's doc; the Compare screen's headers show
    // species names too and were still falling back to the English-formatted raw name.
    val speciesNames: Map<String, Map<String, String>> = emptyMap()
)

class CompareViewModel @JvmOverloads constructor(
    private val repository: PokedexRepositoryApi = AppContainer.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    private var loadedFor: Pair<String, String>? = null

    init {
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

    fun load(leftName: String, rightName: String) {
        val key = leftName to rightName
        if (loadedFor == key) return
        loadedFor = key
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, left = null, right = null, errorMessage = null) }
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
                    it.copy(isLoading = false, errorMessage = UiText(R.string.compare_error_load))
                }
            }
        }
    }

    private suspend fun loadSide(name: String): CompareSide {
        val bundle = repository.getPokemonDetailBundle(name)
        val stats = bundle.pokemon.stats.orEmpty()
        val percentiles = stats.associate { stat ->
            stat.stat.name to repository.getStatPercentile(stat.stat.name, stat.baseStat)
        } + mapOf(TOTAL to repository.getStatPercentile(TOTAL, bundle.pokemon.baseStatTotal()))
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
                _uiState.update { it.copy(errorMessage = UiText(R.string.compare_error_load_pokemon_list)) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
