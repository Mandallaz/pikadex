package com.mandallaz.pikadex.util

/**
 * F35 — the game-data axis of language selection: every PokeAPI resource with translated text
 * (species genus/flavor text, ability effect text...) carries one entry per language it has a
 * translation for, keyed by a `language.name`-style code read via [languageOf]. Picks the entry for
 * [languageCode], falling back to English wherever that language's entry is missing — per F35's
 * plan, this fallback applies identically to both axes (the UI chrome side gets it for free from
 * Android's own `values-{locale}` resource resolution).
 */
fun <T> List<T>?.localizedOrEnglish(languageCode: String, languageOf: (T) -> String): T? {
    val entries = this.orEmpty()
    return entries.firstOrNull { languageOf(it) == languageCode }
        ?: entries.firstOrNull { languageOf(it) == "en" }
}
