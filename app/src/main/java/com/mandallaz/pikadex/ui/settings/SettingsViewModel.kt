package com.mandallaz.pikadex.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.CryCache
import com.mandallaz.pikadex.data.DisplaySettings
import com.mandallaz.pikadex.data.JsonDiskCache
import com.mandallaz.pikadex.data.PrefetchManager
import com.mandallaz.pikadex.data.PrefetchSettings
import com.mandallaz.pikadex.data.PrefetchState
import com.mandallaz.pikadex.data.PrefetchTier
import com.mandallaz.pikadex.data.SuggestionSettings
import com.mandallaz.pikadex.data.repository.PokedexRepository
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.SmogonTierLabels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageUsage(
    val httpCacheBytes: Long,
    val diskCacheBytes: Long,
    val imageCacheBytes: Long,
    val criesCacheBytes: Long = 0L
) {
    val totalBytes: Long get() = httpCacheBytes + diskCacheBytes + imageCacheBytes + criesCacheBytes
}

data class SettingsUiState(
    val essentialsEnabled: Boolean = true,
    val spritesEnabled: Boolean = true,
    val spritesExtraEnabled: Boolean = false,
    val fullDetailEnabled: Boolean = false,
    val criesEnabled: Boolean = false,
    val storageUsage: StorageUsage? = null,
    val isMeasuringStorage: Boolean = false,
    /** Team-builder Suggestions' competitive tier ceiling (issue #11) — null means no limit. */
    val maxSuggestionTier: String? = null,
    /** Every tier code Gen 9 actually uses, most-used first — empty until [SettingsViewModel]'s
     *  init block resolves it (best-effort; the picker just shows "Loading..." until then, same
     *  as the Pokédex list's own tier dialog). */
    val suggestionTierOptions: List<String> = emptyList(),
    /** True-black dark theme variant for AMOLED screens (issue #4). */
    val amoledEnabled: Boolean = false
) {
    val hasAnyTierEnabled: Boolean
        get() = essentialsEnabled || spritesEnabled || spritesExtraEnabled || fullDetailEnabled || criesEnabled
}

/** [AndroidViewModel], not the usual plain [androidx.lifecycle.ViewModel] — measuring/clearing the
 *  image cache needs a real [android.content.Context] (`context.imageLoader`), and Settings is the
 *  one screen in this app where that's worth the coupling. */
class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PokedexRepository = AppContainer.repository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val prefetchState: StateFlow<PrefetchState> = PrefetchManager.state

    init {
        viewModelScope.launch { PrefetchSettings.essentialsEnabled.collect { v -> _uiState.update { it.copy(essentialsEnabled = v) } } }
        viewModelScope.launch { PrefetchSettings.spritesEnabled.collect { v -> _uiState.update { it.copy(spritesEnabled = v) } } }
        viewModelScope.launch { PrefetchSettings.spritesExtraEnabled.collect { v -> _uiState.update { it.copy(spritesExtraEnabled = v) } } }
        viewModelScope.launch { PrefetchSettings.fullDetailEnabled.collect { v -> _uiState.update { it.copy(fullDetailEnabled = v) } } }
        viewModelScope.launch { PrefetchSettings.criesEnabled.collect { v -> _uiState.update { it.copy(criesEnabled = v) } } }
        viewModelScope.launch { SuggestionSettings.maxTier.collect { v -> _uiState.update { it.copy(maxSuggestionTier = v) } } }
        viewModelScope.launch { DisplaySettings.amoledEnabled.collect { v -> _uiState.update { it.copy(amoledEnabled = v) } } }
        loadSuggestionTierOptions()
        measureStorage()
    }

    fun setEssentialsEnabled(enabled: Boolean) = PrefetchSettings.setEssentialsEnabled(enabled)
    fun setSpritesEnabled(enabled: Boolean) = PrefetchSettings.setSpritesEnabled(enabled)
    fun setSpritesExtraEnabled(enabled: Boolean) = PrefetchSettings.setSpritesExtraEnabled(enabled)
    fun setFullDetailEnabled(enabled: Boolean) = PrefetchSettings.setFullDetailEnabled(enabled)
    fun setCriesEnabled(enabled: Boolean) = PrefetchSettings.setCriesEnabled(enabled)
    fun setMaxSuggestionTier(tier: String?) = SuggestionSettings.setMaxTier(tier)
    fun setAmoledEnabled(enabled: Boolean) = DisplaySettings.setAmoledEnabled(enabled)

    /** Best-effort, same as every other filter-option fetch in this app (e.g. the Pokédex list's
     *  own tier dialog) — a failure just leaves the picker showing "Loading...", not an error
     *  state, since Suggestions themselves degrade gracefully (see [TeamViewModel.loadSuggestions])
     *  when tier data isn't available. */
    private fun loadSuggestionTierOptions() {
        viewModelScope.launch {
            try {
                val tiers = repository.getSmogonTiers(Smogon.SUGGESTION_TIER_GEN)
                val options = SmogonTierLabels.sortedTiers(tiers.values.toSet())
                _uiState.update { it.copy(suggestionTierOptions = options) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Left as-is: an empty options list, same "Loading..." the OptionsDialog already
                // shows for the initial fetch.
            }
        }
    }

    fun startPrefetch() {
        val state = _uiState.value
        val tiers = buildList {
            if (state.essentialsEnabled) add(PrefetchTier.ESSENTIALS)
            if (state.spritesEnabled) add(PrefetchTier.SPRITES)
            if (state.spritesExtraEnabled) add(PrefetchTier.SPRITES_EXTRA)
            if (state.fullDetailEnabled) add(PrefetchTier.FULL_DETAIL)
            if (state.criesEnabled) add(PrefetchTier.CRIES)
        }
        PrefetchManager.start(getApplication(), AppContainer.repository, tiers)
    }

    fun cancelPrefetch() = PrefetchManager.cancel()

    // DiskCache.size and DiskCache.clear() are still @ExperimentalCoilApi in coil-compose 2.7.0
    // (Coil hasn't stabilized a non-experimental accessor for them yet) — opted in deliberately
    // rather than suppressed, since this is the only way to read/clear the image disk cache.
    @OptIn(ExperimentalCoilApi::class)
    fun measureStorage() {
        _uiState.update { it.copy(isMeasuringStorage = true) }
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                val httpCache = AppContainer.sharedOkHttpClient.cache
                StorageUsage(
                    httpCacheBytes = try { httpCache?.size() ?: 0L } catch (e: Exception) { 0L },
                    diskCacheBytes = JsonDiskCache.sizeBytes(),
                    imageCacheBytes = getApplication<Application>().imageLoader.diskCache?.size ?: 0L,
                    criesCacheBytes = CryCache.sizeBytes(getApplication())
                )
            }
            _uiState.update { it.copy(storageUsage = usage, isMeasuringStorage = false) }
        }
    }

    // See measureStorage()'s @OptIn comment — same experimental DiskCache.clear() call.
    @OptIn(ExperimentalCoilApi::class)
    fun clearDownloadedData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    AppContainer.sharedOkHttpClient.cache?.evictAll()
                } catch (e: Exception) {
                    // Best-effort: a failed evict just leaves stale entries until their TTL expires.
                }
                JsonDiskCache.clear()
                getApplication<Application>().imageLoader.diskCache?.clear()
                CryCache.clear(getApplication())
            }
            measureStorage()
        }
    }
}
