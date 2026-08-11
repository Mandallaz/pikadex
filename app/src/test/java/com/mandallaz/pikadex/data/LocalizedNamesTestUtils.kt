package com.mandallaz.pikadex.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Resets [LocalizedNames]'s shared cache to empty. Without this, whichever test in the JVM worker
 * happens to populate the cache first (via [LocalizedNames.ensureLoaded]/[LocalizedNames.await])
 * wins for every other test that runs afterward — [LocalizedNames.ensureLoaded] short-circuits once
 * the cache is non-empty, so a later test's own `FakePokedexRepository.allSpeciesNames` is silently
 * ignored (B35). Every test that populates the cache must call this in `tearDown`.
 */
@Suppress("UNCHECKED_CAST")
fun LocalizedNames.clearForTest() {
    val field = LocalizedNames::class.java.getDeclaredField("_speciesNames")
    field.isAccessible = true
    (field.get(LocalizedNames) as MutableStateFlow<Map<String, Map<String, String>>>).value = emptyMap()
}
