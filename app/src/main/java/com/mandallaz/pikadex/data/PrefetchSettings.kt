package com.mandallaz.pikadex.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which [PrefetchTier]s the Settings screen's "Prefetch now" button runs, persisted across app
 *  restarts. Essentials/Sprites default on, Full detail/Cries/Sprites-extra opt-in
 *  (Cries added for issue #24, same opt-in-by-default reasoning as Full detail: ~1300 audio files
 *  is a real download, not a trivial one; Sprites-extra added for issue #31, same reasoning —
 *  shiny + animated Showdown GIFs roughly double SPRITES' own volume). Same
 *  SharedPreferences-backed-StateFlow pattern as [FavoritesRepository]/[TeamRepository]. */
object PrefetchSettings {
    private const val PREFS_NAME = "prefetch_settings"
    private const val KEY_ESSENTIALS = "essentials_enabled"
    private const val KEY_SPRITES = "sprites_enabled"
    private const val KEY_SPRITES_EXTRA = "sprites_extra_enabled"
    private const val KEY_FULL_DETAIL = "full_detail_enabled"
    private const val KEY_CRIES = "cries_enabled"
    // F63 — defaults on: prefetch tiers can run 50-300MB+, so the safe default is to require
    // Wi-Fi rather than silently spend a user's mobile data plan.
    private const val KEY_WIFI_ONLY = "wifi_only_enabled"

    private val essentialsStore = PrefsStore(true)
    val essentialsEnabled: StateFlow<Boolean> = essentialsStore.flow.asStateFlow()

    private val spritesStore = PrefsStore(true)
    val spritesEnabled: StateFlow<Boolean> = spritesStore.flow.asStateFlow()

    private val spritesExtraStore = PrefsStore(false)
    val spritesExtraEnabled: StateFlow<Boolean> = spritesExtraStore.flow.asStateFlow()

    private val fullDetailStore = PrefsStore(false)
    val fullDetailEnabled: StateFlow<Boolean> = fullDetailStore.flow.asStateFlow()

    private val criesStore = PrefsStore(false)
    val criesEnabled: StateFlow<Boolean> = criesStore.flow.asStateFlow()

    private val wifiOnlyStore = PrefsStore(true)
    val wifiOnlyEnabled: StateFlow<Boolean> = wifiOnlyStore.flow.asStateFlow()

    fun init(context: Context) {
        essentialsStore.init(context, PREFS_NAME, KEY_ESSENTIALS, true)
        spritesStore.init(context, PREFS_NAME, KEY_SPRITES, true)
        spritesExtraStore.init(context, PREFS_NAME, KEY_SPRITES_EXTRA, false)
        fullDetailStore.init(context, PREFS_NAME, KEY_FULL_DETAIL, false)
        criesStore.init(context, PREFS_NAME, KEY_CRIES, false)
        wifiOnlyStore.init(context, PREFS_NAME, KEY_WIFI_ONLY, true)
    }

    fun setEssentialsEnabled(enabled: Boolean) = essentialsStore.set(enabled)
    fun setSpritesEnabled(enabled: Boolean) = spritesStore.set(enabled)
    fun setSpritesExtraEnabled(enabled: Boolean) = spritesExtraStore.set(enabled)
    fun setFullDetailEnabled(enabled: Boolean) = fullDetailStore.set(enabled)
    fun setCriesEnabled(enabled: Boolean) = criesStore.set(enabled)
    fun setWifiOnlyEnabled(enabled: Boolean) = wifiOnlyStore.set(enabled)
}
