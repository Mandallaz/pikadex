package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import com.mandallaz.pikadex.data.repository.PokemonDetailBundle
import com.mandallaz.pikadex.data.repository.fakePokemonDto
import com.mandallaz.pikadex.data.repository.fakePokemonSpeciesDto
import com.mandallaz.pikadex.data.repository.fakeTypeDetailDto
import com.mandallaz.pikadex.util.clearForTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F49 — load/error/cancel coverage for [PokedexDetailViewModel], the ViewModel [F49's tracking
 * issue](https://github.com/Mandallaz/pikadex/issues/32) singled out as having real bugs (infinite
 * spinner, phantom network error) that pure-function tests like [PokedexDetailViewModelTest] can't
 * reach, since they live in the coroutine/state-update code itself.
 */
class PokedexDetailViewModelLoadTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePokedexRepository
    private lateinit var viewModel: PokedexDetailViewModel

    private fun bundleFor(name: String, types: List<String> = listOf("fire")) = PokemonDetailBundle(
        pokemon = fakePokemonDto(id = 4, name = name, types = types),
        species = fakePokemonSpeciesDto(id = 4, name = name),
        evolutionChain = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePokedexRepository()
        viewModel = PokedexDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        // Order matters — see TeamViewModelTest.tearDown's identical comment: clearing first
        // avoids waking a dead collector on an already-reset Main dispatcher.
        viewModel.clearForTest()
        // Global singleton state — reset so other test classes in the same JVM worker don't
        // inherit whatever team this test left behind.
        TeamRepository.replaceAll(emptyList())
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates state on success`() = runTest(dispatcher) {
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf("fire" to fakeTypeDetailDto("fire"))

        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("charmander", state.pokemon?.name)
        assertNull(state.errorMessage)
    }

    // The bug this guards against: PokedexDetailViewModel.load's `catch` block used to be the
    // only way isLoading ever went back to false on a network failure — if it were ever removed
    // or bypassed, a failed load leaves the screen spinning forever.
    @Test
    fun `load failure clears the spinner and sets an error message`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")

        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.errorMessage)
        assertNull(state.pokemon)
    }

    // loadedFor must be reset on failure, or a Pokémon that failed to load once becomes
    // permanently un-retryable (load() no-ops whenever loadedFor already equals nameOrId).
    @Test
    fun `a failed load can be retried by calling load again with the same name`() = runTest(dispatcher) {
        repository.failWith = RuntimeException("boom")
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        repository.failWith = null
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf("fire" to fakeTypeDetailDto("fire"))
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("charmander", viewModel.uiState.value.pokemon?.name)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `a second load call for the same name already in progress is a no-op`() = runTest(dispatcher) {
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf("fire" to fakeTypeDetailDto("fire"))

        viewModel.load("charmander")
        viewModel.load("charmander") // same name, first load hasn't resolved yet
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("charmander", viewModel.uiState.value.pokemon?.name)
    }

    @Test
    fun `loadTeamImpact computes an impact summary when the team has room`() = runTest(dispatcher) {
        TeamRepository.replaceAll(listOf(NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")))
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf(
            "fire" to fakeTypeDetailDto("fire"),
            "water" to fakeTypeDetailDto("water")
        )
        repository.pokemonTypes = listOf("water") // computeTeamMatrices reuses this for every member
        repository.pokemonLevelUpMoveNames = emptyList()
        repository.allMoveInfo = emptyMap()
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadTeamImpact()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isTeamImpactLoading)
        assertNull(state.teamImpactError)
        assertNotNull(state.teamImpact)
    }

    @Test
    fun `loadTeamImpact failure surfaces an error instead of leaving the spinner on`() = runTest(dispatcher) {
        TeamRepository.replaceAll(listOf(NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")))
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf("fire" to fakeTypeDetailDto("fire"))
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        // Only fail from here on, so load() above (already awaited) isn't what throws.
        repository.failWith = RuntimeException("boom")
        viewModel.loadTeamImpact()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isTeamImpactLoading)
        assertNotNull(state.teamImpactError)
        assertNull(state.teamImpact)
    }

    // A superseded loadTeamImpact call (team impact re-requested before the previous one settled)
    // must cancel, not race to completion and possibly overwrite a later result with a stale one —
    // same "latest call wins" invariant as every other tracked job in this codebase.
    @Test
    fun `a stale loadTeamImpact call is cancelled by a newer one, not left racing it`() = runTest(dispatcher) {
        TeamRepository.replaceAll(listOf(NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")))
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf(
            "fire" to fakeTypeDetailDto("fire"),
            "water" to fakeTypeDetailDto("water")
        )
        repository.pokemonTypes = listOf("water")
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        repository.gate = gate
        viewModel.loadTeamImpact() // suspends on the gate before it can finish
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isTeamImpactLoading)

        repository.gate = null // the second call's own fetches resolve immediately
        viewModel.loadTeamImpact()
        dispatcher.scheduler.advanceUntilIdle()
        gate.complete(Unit) // release the first call late — it must not still land afterwards
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isTeamImpactLoading)
        assertNotNull(viewModel.uiState.value.teamImpact)
    }

    @Test
    fun `clearTeamImpact resets the card state`() = runTest(dispatcher) {
        TeamRepository.replaceAll(listOf(NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")))
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf(
            "fire" to fakeTypeDetailDto("fire"),
            "water" to fakeTypeDetailDto("water")
        )
        repository.pokemonTypes = listOf("water")
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadTeamImpact()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.teamImpact)

        viewModel.clearTeamImpact()

        val state = viewModel.uiState.value
        assertNull(state.teamImpact)
        assertNull(state.teamImpactError)
        assertEquals(false, state.isTeamImpactLoading)
    }
}
