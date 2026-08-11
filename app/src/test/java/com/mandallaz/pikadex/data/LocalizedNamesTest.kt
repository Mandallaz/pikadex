package com.mandallaz.pikadex.data

import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F66 — [LocalizedNames] replaces four near-identical best-effort loaders (list/team/compare
 * ViewModels via [LocalizedNames.ensureLoaded] + collecting [LocalizedNames.speciesNames], detail
 * via [LocalizedNames.await]) with one shared cache. Plain suspend functions run on the caller's
 * own scope (see the object's own doc for why, unlike PrefetchManager), so `runBlocking` alone is
 * enough here — no test dispatcher/scheduler needed.
 */
class LocalizedNamesTest {

    @Suppress("UNCHECKED_CAST")
    private fun reset() {
        val field = LocalizedNames::class.java.getDeclaredField("_speciesNames")
        field.isAccessible = true
        (field.get(LocalizedNames) as MutableStateFlow<Map<String, Map<String, String>>>).value = emptyMap()
    }

    @After
    fun tearDown() = reset()

    @Test
    fun `await fetches once and caches for later calls`() = runBlocking {
        reset()
        val repository = FakePokedexRepository().apply {
            allSpeciesNames = mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre"))
        }
        val first = LocalizedNames.await(repository)
        assertEquals(mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre")), first)

        // A second, differently-stocked repository is never actually queried once the cache is
        // warm — proves the cache backs the second call, not a fresh fetch.
        val staleRepository = FakePokedexRepository().apply {
            allSpeciesNames = mapOf("charmander" to mapOf("fr" to "Salameche"))
        }
        val second = LocalizedNames.await(staleRepository)
        assertEquals(first, second)
    }

    @Test
    fun `a failed fetch leaves the cache empty rather than throwing`() = runBlocking {
        reset()
        val repository = FakePokedexRepository().apply {
            failWith = RuntimeException("network error")
        }
        val result = LocalizedNames.await(repository)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ensureLoaded populates the shared state flow`() = runBlocking {
        reset()
        val repository = FakePokedexRepository().apply {
            allSpeciesNames = mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre"))
        }
        LocalizedNames.ensureLoaded(repository)
        assertEquals(mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre")), LocalizedNames.speciesNames.value)
    }
}
