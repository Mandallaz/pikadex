package com.mandallaz.pikadex.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same Context-free testing approach as [SuggestionSettingsTest] — see that file's own doc. */
class LanguageSettingsTest {

    private fun swapInFakePrefs(fake: FakeSharedPreferences) {
        val field = LanguageSettings::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(LanguageSettings, fake)
    }

    // --- resolveLanguage (pure) ---------------------------------------------------------

    @Test
    fun `a supported language code passes through unchanged`() {
        assertEquals("fr", LanguageSettings.resolveLanguage("fr"))
        assertEquals("zh-Hant", LanguageSettings.resolveLanguage("zh-Hant"))
    }

    @Test
    fun `a never-configured store (null) resolves to the default`() {
        assertEquals(SupportedLanguages.DEFAULT_CODE, LanguageSettings.resolveLanguage(null))
    }

    // A future picker-list change that drops a language must not leave a stale stored code
    // silently applying an unsupported locale — falls back to the default instead.
    @Test
    fun `a stored code no longer in the supported list falls back to the default`() {
        assertEquals(SupportedLanguages.DEFAULT_CODE, LanguageSettings.resolveLanguage("cs"))
    }

    // --- setLanguage's actual persisted writes -------------------------------------------

    @Test
    fun `setting a language persists that exact code`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)
        LanguageSettings.setLanguage("fr")
        assertEquals("fr", fake.getString("language_code", null))
    }
}
