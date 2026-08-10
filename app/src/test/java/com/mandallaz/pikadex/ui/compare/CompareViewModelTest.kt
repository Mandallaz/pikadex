package com.mandallaz.pikadex.ui.compare

import com.mandallaz.pikadex.data.repository.FakePokedexRepository
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
        repository = FakePokedexRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `species names load into state for the Compare screen to localize with`() = runTest(dispatcher) {
        repository.allSpeciesNames = mapOf("squirtle" to mapOf("fr" to "Carapuce"))

        viewModel = CompareViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("fr" to "Carapuce"), viewModel.uiState.value.speciesNames["squirtle"])
    }
}
