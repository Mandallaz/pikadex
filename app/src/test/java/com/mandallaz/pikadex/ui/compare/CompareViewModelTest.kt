package com.mandallaz.pikadex.ui.compare

import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.clearForTest
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import com.mandallaz.pikadex.data.repository.PokemonDetailBundle
import com.mandallaz.pikadex.data.repository.fakePokemonDto
import com.mandallaz.pikadex.data.repository.fakePokemonSpeciesDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * B9 — the Compare screen's headers fell back to the raw English name forever, even once F35's
 * language picker was set to French, since CompareViewModel never fetched the bulk species-name
 * map PokedexListViewModel already had (issue #52).
 */
class CompareViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePokedexRepository
    private lateinit var viewModel: CompareViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // B35 — reset before, not just after: guards against a *different* test class touching
        // this JVM-wide singleton and forgetting its own cleanup, which is exactly what let this
        // bug regress once already (see the issue) despite this class's own @After already doing it.
        LocalizedNames.clearForTest()
        repository = FakePokedexRepository()
    }

    @After
    fun tearDown() {
        // B35 — reset the shared LocalizedNames cache this test's own species-name assertion
        // warms, or it stays stale for every other test class sharing this JVM worker.
        LocalizedNames.clearForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun `species names load into state for the Compare screen to localize with`() = runTest(dispatcher) {
        repository.allSpeciesNames = mapOf("squirtle" to mapOf("fr" to "Carapuce"))

        viewModel = CompareViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("fr" to "Carapuce"), viewModel.uiState.value.speciesNames["squirtle"])
    }

    @Test
    fun `load does not wipe out species names`() = runTest(dispatcher) {
        repository.allSpeciesNames = mapOf(
            "squirtle" to mapOf("fr" to "Carapuce"),
            "charmander" to mapOf("fr" to "Salamèche")
        )
        repository.detailBundle = PokemonDetailBundle(
            pokemon = fakePokemonDto(id = 7, name = "squirtle", types = listOf("water")),
            species = fakePokemonSpeciesDto(id = 7, name = "squirtle"),
            evolutionChain = null
        )

        viewModel = CompareViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        // Verify speciesNames loaded first
        assertEquals(mapOf("fr" to "Carapuce"), viewModel.uiState.value.speciesNames["squirtle"])

        // Call load, which used to wipe out speciesNames
        viewModel.load("squirtle", "charmander")
        dispatcher.scheduler.advanceUntilIdle()

        // Verify speciesNames is still populated after load
        assertEquals(mapOf("fr" to "Carapuce"), viewModel.uiState.value.speciesNames["squirtle"])
    }
}
