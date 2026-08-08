package com.mandallaz.pikadex.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.JsonDiskCache
import com.mandallaz.pikadex.data.PrefetchManager
import com.mandallaz.pikadex.data.PrefetchSettings
import com.mandallaz.pikadex.data.PrefetchState
import com.mandallaz.pikadex.data.PrefetchTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageUsage(val httpCacheBytes: Long, val diskCacheBytes: Long, val imageCacheBytes: Long) {
    val totalBytes: Long get() = httpCacheBytes + diskCacheBytes + imageCacheBytes
}

data class SettingsUiState(
    val essentialsEnabled: Boolean = true,
    val spritesEnabled: Boolean = true,
    val fullDetailEnabled: Boolean = false,
    val storageUsage: StorageUsage? = null,
    val isMeasuringStorage: Boolean = false
) {
    val hasAnyTierEnabled: Boolean
        get() = essentialsEnabled || spritesEnabled || fullDetailEnabled
}

/** [AndroidViewModel], not the usual plain [androidx.lifecycle.ViewModel] — measuring/clearing the
 *  image cache needs a real [android.content.Context] (`context.imageLoader`), and Settings is the
 *  one screen in this app where that's worth the coupling. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val prefetchState: StateFlow<PrefetchState> = PrefetchManager.state

    init {
        viewModelScope.launch { PrefetchSettings.essentialsEnabled.collect { v -> _uiState.update { it.copy(essentialsEnabled = v) } } }
        viewModelScope.launch { PrefetchSettings.spritesEnabled.collect { v -> _uiState.update { it.copy(spritesEnabled = v) } } }
        viewModelScope.launch { PrefetchSettings.fullDetailEnabled.collect { v -> _uiState.update { it.copy(fullDetailEnabled = v) } } }
        measureStorage()
    }

    fun setEssentialsEnabled(enabled: Boolean) = PrefetchSettings.setEssentialsEnabled(enabled)
    fun setSpritesEnabled(enabled: Boolean) = PrefetchSettings.setSpritesEnabled(enabled)
    fun setFullDetailEnabled(enabled: Boolean) = PrefetchSettings.setFullDetailEnabled(enabled)

    fun startPrefetch() {
        val state = _uiState.value
        val tiers = buildList {
            if (state.essentialsEnabled) add(PrefetchTier.ESSENTIALS)
            if (state.spritesEnabled) add(PrefetchTier.SPRITES)
            if (state.fullDetailEnabled) add(PrefetchTier.FULL_DETAIL)
        }
        PrefetchManager.start(getApplication(), AppContainer.repository, tiers)
    }

    fun cancelPrefetch() = PrefetchManager.cancel()

    fun measureStorage() {
        _uiState.update { it.copy(isMeasuringStorage = true) }
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                val httpCache = AppContainer.sharedOkHttpClient.cache
                StorageUsage(
                    httpCacheBytes = try { httpCache?.size() ?: 0L } catch (e: Exception) { 0L },
                    diskCacheBytes = JsonDiskCache.sizeBytes(),
                    imageCacheBytes = getApplication<Application>().imageLoader.diskCache?.size ?: 0L
                )
            }
            _uiState.update { it.copy(storageUsage = usage, isMeasuringStorage = false) }
        }
    }

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
            }
            measureStorage()
        }
    }
}
