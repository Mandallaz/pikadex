package com.mandallaz.pikadex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F63 — [PrefetchSettings.wifiOnlyEnabled] defaults to true (prefetch tiers can run 50-300MB+, so
 * the safe default is to require Wi-Fi rather than silently spending a user's mobile data), and
 * that choice must persist across restarts. Same Context-free technique as
 * [SuggestionSettingsTest]: PrefetchSettings.init() itself isn't called (it needs a real
 * android.content.Context), instead swapping in a [FakeSharedPreferences] via reflection.
 */
class PrefetchSettingsTest {

    private fun swapInFakePrefs(fake: FakeSharedPreferences) {
        val fields = PrefetchSettings::class.java.declaredFields
        for (field in fields) {
            if (PrefsStore::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                val store = field.get(PrefetchSettings) as PrefsStore<Boolean>
                store.prefs = fake
                val key = when (field.name) {
                    "essentialsStore" -> "essentials_enabled"
                    "spritesStore" -> "sprites_enabled"
                    "spritesExtraStore" -> "sprites_extra_enabled"
                    "fullDetailStore" -> "full_detail_enabled"
                    "criesStore" -> "cries_enabled"
                    "wifiOnlyStore" -> "wifi_only_enabled"
                    else -> ""
                }
                store.key = key
                store.encode = { k, v -> putBoolean(k, v) }
            }
        }
    }

    @Test
    fun `wifi-only defaults to true before init is ever called`() {
        assertTrue(PrefetchSettings.wifiOnlyEnabled.value)
    }

    @Test
    fun `disabling wifi-only persists false`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)
        PrefetchSettings.setWifiOnlyEnabled(false)
        assertEquals(false, fake.getBoolean("wifi_only_enabled", true))
        assertEquals(false, PrefetchSettings.wifiOnlyEnabled.value)
        // restore default for any other test sharing this singleton
        PrefetchSettings.setWifiOnlyEnabled(true)
    }
}
