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

/** B9 — the species-*name* half of the game-data axis, which nothing in the app read before this:
 *  every screen displayed the raw PokeAPI `name` identifier (e.g. "bulbasaur") formatted via
 *  [toDisplayName], regardless of the picked language. [speciesNames] is
 *  [com.mandallaz.pikadex.data.repository.PokedexRepositoryApi.getAllSpeciesNames]'s bulk map —
 *  looks up this raw name's entry for [languageCode], falling back to English, and finally to the
 *  formatted raw name itself for a species [speciesNames] has no entry for at all (a fetch that
 *  hasn't completed yet, or a genuinely untranslated form). */
fun String.localizedDisplayName(speciesNames: Map<String, Map<String, String>>, languageCode: String): String {
    // English keeps its existing, separately-maintained formatting (toDisplayName's own special-case
    // table for e.g. "nidoran-f" -> "Nidoran♀") unconditionally rather than PokeAPI's own "en" name
    // entry — same output as before B9 for every existing user on the default language, zero risk of
    // a formatting regression there.
    if (languageCode == "en") return toDisplayName()
    val namesForSpecies = speciesNames[this] ?: return toDisplayName()
    return namesForSpecies[languageCode] ?: namesForSpecies["en"] ?: toDisplayName()
}

/**
 * B45 — resolves a list of raw PokeAPI type names against the [typeNameRes] mapping, returning
 * the resource ID (Int) if a translation exists, or the fallback formatted name (String) if not.
 * This is a pure-Kotlin helper, fully testable on the JVM.
 */
fun List<String>.resolvedTypeNames(): List<Any> {
    return map { typeNameRes(it) ?: it.toDisplayName() }
}
