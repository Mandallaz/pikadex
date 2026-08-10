package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One entry in the Settings language picker. [code] doubles as both the PokeAPI `language.name`
 *  code to look up game data with, and the BCP-47 tag used to override the app's UI locale — see
 *  F35's finalized plan for why a single picker drives both axes at once. */
data class AppLanguage(val code: String, val label: String)

/** F35 — every *official* PokeAPI language except the two redundant Japanese variants
 *  (`ja-Hrkt`/`ja-Roma` collapse to plain `ja`, the one an actual Japanese-locale user expects) and
 *  `cs` (unofficial, no in-game reference text to translate against). Labels are each language's
 *  own native name, as a language picker conventionally shows them — not English glosses. */
object SupportedLanguages {
    val ALL: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("es", "Español"),
        AppLanguage("es-419", "Español (Latinoamérica)"),
        AppLanguage("it", "Italiano"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("zh-Hans", "简体中文"),
        AppLanguage("zh-Hant", "繁體中文")
    )

    const val DEFAULT_CODE = "en"
}

/**
 * Persisted UI/game-data language choice (F35), same `SharedPreferences`-backed-`StateFlow` pattern
 * as [DisplaySettings]/[PrefetchSettings]/[SuggestionSettings]. Must be initialized once via [init]
 * before use (done in the Application class, since a Context is needed to open the prefs file) —
 * like every sibling settings object, [currentLanguage] still defaults sensibly (to
 * [SupportedLanguages.DEFAULT_CODE]) without it, which is what makes this object safe to construct
 * directly in a JVM unit test.
 *
 * Defaults to English regardless of device locale — this is deliberate per F35's spec, not a
 * fallback-that-should-be-smarter: the app's language is meant to be a single explicit choice made
 * through this picker, not inherited from the system automatically.
 */
object LanguageSettings {
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANGUAGE = "language_code"

    private var prefs: SharedPreferences? = null

    private val _currentLanguage = MutableStateFlow(SupportedLanguages.DEFAULT_CODE)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        _currentLanguage.value = resolveLanguage(sharedPrefs.getString(KEY_LANGUAGE, null))
    }

    fun setLanguage(code: String) {
        val p = prefs ?: return
        _currentLanguage.value = code
        p.edit { putString(KEY_LANGUAGE, code) }
    }

    /** A stored code that's no longer in [SupportedLanguages.ALL] (a future picker-list change
     *  dropping one) falls back to the default rather than silently applying an unsupported
     *  language. Internal, not private, so it's unit-testable directly without a real
     *  Context/SharedPreferences — same pattern as [SuggestionSettings.resolveMaxTier]. */
    internal fun resolveLanguage(stored: String?): String =
        stored?.takeIf { code -> SupportedLanguages.ALL.any { it.code == code } } ?: SupportedLanguages.DEFAULT_CODE
}
