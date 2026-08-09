package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which [PrefetchTier]s the Settings screen's "Prefetch now" button runs, persisted across app
 *  restarts. Essentials/Sprites default on, Full detail and Cries opt-in — matches BACKLOG.md F13
 *  (Cries added for F34, same opt-in-by-default reasoning as Full detail: ~1300 audio files is a
 *  real download, not a trivial one). Same SharedPreferences-backed-StateFlow pattern as
 *  [FavoritesRepository]/[TeamRepository]. */
object PrefetchSettings {
    private const val PREFS_NAME = "prefetch_settings"
    private const val KEY_ESSENTIALS = "essentials_enabled"
    private const val KEY_SPRITES = "sprites_enabled"
    private const val KEY_FULL_DETAIL = "full_detail_enabled"
    private const val KEY_CRIES = "cries_enabled"

    private lateinit var prefs: SharedPreferences

    private val _essentialsEnabled = MutableStateFlow(true)
    val essentialsEnabled: StateFlow<Boolean> = _essentialsEnabled.asStateFlow()

    private val _spritesEnabled = MutableStateFlow(true)
    val spritesEnabled: StateFlow<Boolean> = _spritesEnabled.asStateFlow()

    private val _fullDetailEnabled = MutableStateFlow(false)
    val fullDetailEnabled: StateFlow<Boolean> = _fullDetailEnabled.asStateFlow()

    private val _criesEnabled = MutableStateFlow(false)
    val criesEnabled: StateFlow<Boolean> = _criesEnabled.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _essentialsEnabled.value = prefs.getBoolean(KEY_ESSENTIALS, true)
        _spritesEnabled.value = prefs.getBoolean(KEY_SPRITES, true)
        _fullDetailEnabled.value = prefs.getBoolean(KEY_FULL_DETAIL, false)
        _criesEnabled.value = prefs.getBoolean(KEY_CRIES, false)
    }

    fun setEssentialsEnabled(enabled: Boolean) = set(_essentialsEnabled, KEY_ESSENTIALS, enabled)
    fun setSpritesEnabled(enabled: Boolean) = set(_spritesEnabled, KEY_SPRITES, enabled)
    fun setFullDetailEnabled(enabled: Boolean) = set(_fullDetailEnabled, KEY_FULL_DETAIL, enabled)
    fun setCriesEnabled(enabled: Boolean) = set(_criesEnabled, KEY_CRIES, enabled)

    private fun set(flow: MutableStateFlow<Boolean>, key: String, enabled: Boolean) {
        flow.value = enabled
        prefs.edit { putBoolean(key, enabled) }
    }
}
