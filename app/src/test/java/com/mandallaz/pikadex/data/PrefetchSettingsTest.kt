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
        val field = PrefetchSettings::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(PrefetchSettings, fake)
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
