package com.mandallaz.pikadex.data

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * F75 — Unit test verifying that PrefetchManager.buildWorkRequest applies NetworkType.UNMETERED
 * constraint when PrefetchSettings.wifiOnlyEnabled is true, and omits it when false.
 */
class PrefetchManagerWorkRequestTest {

    private fun swapInFakePrefs(fake: FakeSharedPreferences) {
        val fields = PrefetchSettings::class.java.declaredFields
        for (field in fields) {
            if (PrefsStore::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
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
    fun `buildWorkRequest applies UNMETERED constraint when wifiOnlyEnabled is true`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)
        PrefetchSettings.setWifiOnlyEnabled(true)

        val workRequest = PrefetchManager.buildWorkRequest(listOf(PrefetchTier.ESSENTIALS))
        assertEquals(NetworkType.UNMETERED, workRequest.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `buildWorkRequest omits UNMETERED constraint when wifiOnlyEnabled is false`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)
        PrefetchSettings.setWifiOnlyEnabled(false)

        val workRequest = PrefetchManager.buildWorkRequest(listOf(PrefetchTier.ESSENTIALS))
        assertNotEquals(NetworkType.UNMETERED, workRequest.workSpec.constraints.requiredNetworkType)
        assertEquals(NetworkType.NOT_REQUIRED, workRequest.workSpec.constraints.requiredNetworkType)

        // Restore default for any other tests
        PrefetchSettings.setWifiOnlyEnabled(true)
    }
}
