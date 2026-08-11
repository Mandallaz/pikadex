package com.mandallaz.pikadex.data

import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F66 — the rawName -> (languageCode -> localized species name) bulk fetch (B9) used to be
 * duplicated, with its own 12-line best-effort loader and its own UiState field, across
 * [com.mandallaz.pikadex.ui.list.PokedexListViewModel], [com.mandallaz.pikadex.ui.team.TeamViewModel],
 * [com.mandallaz.pikadex.ui.compare.CompareViewModel] and
 * [com.mandallaz.pikadex.ui.detail.PokedexDetailViewModel]. This isn't a performance fix — the
 * repository call itself is already memoized (see [PokedexRepositoryApi.getAllSpeciesNames]'s
 * `AsyncValueCache`-backed implementation, which also coalesces concurrent callers) — it's one
 * shared cache and one shared best-effort fetch instead of four copies of the same 12 lines.
 *
 * Deliberately plain suspend functions run on the *caller's* own scope (`viewModelScope`), unlike
 * [PrefetchManager]'s own long-lived scope: that one genuinely needs to survive its ViewModel being
 * cleared (a multi-minute download the user navigated away from mid-run). A single memoized bulk
 * fetch has no such requirement — if whichever ViewModel's coroutine is doing the fetch gets
 * cancelled, the next caller's own [ensureLoaded]/[await] just retries it.
 */
object LocalizedNames {
    private val _speciesNames = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val speciesNames: StateFlow<Map<String, Map<String, String>>> = _speciesNames.asStateFlow()

    /** Fetches (if not already cached) and publishes to [speciesNames]. Callers that only need to
     *  *observe* it reactively (list/team/compare, which collect it into their own UiState) launch
     *  this fire-and-forget on their own `viewModelScope`. */
    suspend fun ensureLoaded(repository: PokedexRepositoryApi) {
        if (_speciesNames.value.isNotEmpty()) return
        try {
            _speciesNames.value = repository.getAllSpeciesNames()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort: every consumer falls back to the English-formatted raw name via
            // String.localizedDisplayName.
        }
    }

    /** Suspends until the cache is populated (or the fetch fails), then returns it — used by
     *  [com.mandallaz.pikadex.ui.detail.PokedexDetailViewModel], which needs speciesNames resolved
     *  before publishing its own per-Pokémon load, the same guarantee its previous own
     *  `async { repository.getAllSpeciesNames() }` gave it. */
    suspend fun await(repository: PokedexRepositoryApi): Map<String, Map<String, String>> {
        ensureLoaded(repository)
        return _speciesNames.value
    }
}
