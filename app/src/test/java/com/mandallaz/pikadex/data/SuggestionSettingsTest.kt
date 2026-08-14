package com.mandallaz.pikadex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SuggestionSettings.init() needs a real android.content.Context (it calls
 * context.applicationContext.getSharedPreferences(...)), unavailable in a plain JUnit test — so
 * these tests never call init(). Instead:
 *  - resolveMaxTier, the sentinel-translation logic init() itself uses, is tested directly as a
 *    pure function.
 *  - setMaxTier's actual writes are checked against a swapped-in FakeSharedPreferences (`prefs`
 *    set via reflection, same technique JsonDiskCacheTest uses for its own Context-free field).
 *
 * Together these cover the same round-trip a restart would exercise, without needing Context at
 * all. The persistence distinction matters specifically because of the NO_LIMIT_SENTINEL dance:
 * SharedPreferences has no way to store a real null, and "key absent" already means "never
 * configured" (-> the OU default), so an explicitly-chosen "No limit" needs its own sentinel to
 * survive a restart instead of silently reverting to the default.
 */
class SuggestionSettingsTest {

    private fun swapInFakePrefs(fake: FakeSharedPreferences) {
        val field = SuggestionSettings::class.java.getDeclaredField("store")
        field.isAccessible = true
        val store = field.get(SuggestionSettings) as PrefsStore<String?>
        store.prefs = fake
        store.key = "max_tier"
        store.encode = { key, value -> putString(key, value ?: "") }
    }

    // --- resolveMaxTier (pure) ---------------------------------------------------------

    @Test
    fun `a real tier code passes through unchanged`() {
        assertEquals("OU", SuggestionSettings.resolveMaxTier("OU"))
        assertEquals("UU", SuggestionSettings.resolveMaxTier("UU"))
    }

    // The sentinel, not null, is what a never-configured store's own getString default already
    // resolves to before reaching this function — see init()'s own getString(..., DEFAULT_MAX_TIER)
    // call — so this only needs to cover the explicit-no-limit sentinel itself.
    @Test
    fun `the no-limit sentinel resolves to null`() {
        assertNull(SuggestionSettings.resolveMaxTier(""))
    }

    // --- setMaxTier's actual persisted writes ------------------------------------------

    @Test
    fun `setting a tier persists that exact tier code`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)
        SuggestionSettings.setMaxTier("UU")
        assertEquals("UU", fake.getString("max_tier", null))
    }

    // The regression this test exists to catch: an explicit "No limit" must persist as something
    // distinguishable from "never configured" (which would silently revert to the OU default).
    @Test
    fun `explicitly choosing No limit persists the sentinel, not a removed key`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)
        SuggestionSettings.setMaxTier("UU")
        SuggestionSettings.setMaxTier(null)
        assertEquals("", fake.getString("max_tier", "unset"))
    }
}
