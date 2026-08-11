package com.mandallaz.pikadex.data

import com.mandallaz.pikadex.data.repository.FakePokedexRepository
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

    @After
    fun tearDown() = LocalizedNames.clearForTest()

    @Test
    fun `await fetches once and caches for later calls`() = runBlocking {
        LocalizedNames.clearForTest()
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
        LocalizedNames.clearForTest()
        val repository = FakePokedexRepository().apply {
            failWith = RuntimeException("network error")
        }
        val result = LocalizedNames.await(repository)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ensureLoaded populates the shared state flow`() = runBlocking {
        LocalizedNames.clearForTest()
        val repository = FakePokedexRepository().apply {
            allSpeciesNames = mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre"))
        }
        LocalizedNames.ensureLoaded(repository)
        assertEquals(mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre")), LocalizedNames.speciesNames.value)
    }

    // B35 (regression) — this is the exact mechanism that intermittently failed
    // PokedexDetailViewModelLoadTest under the full suite, twice: one ViewModel's test class
    // warms this JVM-wide singleton and forgets to reset it (PokedexListViewModelLoadTest was the
    // one that slipped through the original B35 fix), so the next ViewModel-backed test class to
    // run in the same JVM worker inherits that stale data instead of its own fake repository's,
    // since `ensureLoaded` only ever fetches once. Every ViewModel test's `setUp()`/`@Before` now
    // calls `clearForTest()` (not just its own `tearDown()`), specifically so it can't inherit a
    // dirty cache regardless of what a preceding, unrelated test class left behind — this proves
    // that reset is what makes a "next" caller unaffected by a leaky "previous" one.
    @Test
    fun `resetting before use is not affected by a dirty cache left by another test (B35)`() = runBlocking {
        val leakyPreviousTest = FakePokedexRepository().apply {
            allSpeciesNames = mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre"))
        }
        LocalizedNames.ensureLoaded(leakyPreviousTest)
        assertEquals(mapOf("bulbasaur" to mapOf("fr" to "Bulbizarre")), LocalizedNames.speciesNames.value)

        // The reset every test's own setUp() now performs before building its repository/ViewModel.
        LocalizedNames.clearForTest()

        val thisTestsOwnRepository = FakePokedexRepository().apply {
            allSpeciesNames = mapOf("charmander" to mapOf("fr" to "Salamèche"))
        }
        val result = LocalizedNames.await(thisTestsOwnRepository)
        assertEquals(mapOf("charmander" to mapOf("fr" to "Salamèche")), result)
    }
}
