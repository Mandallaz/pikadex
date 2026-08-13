package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.clearForTest
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
        // B35 — reset before, not just after: guards against a *different* test class touching
        // this JVM-wide singleton and forgetting its own cleanup, which is exactly what let this
        // bug regress once already (see the issue) despite this class's own @After already doing it.
        LocalizedNames.clearForTest()
        repository = FakePokedexRepository()
        viewModel = PokedexDetailViewModel(repository, dispatcher)
    }

    @After
    fun tearDown() {
        // Order matters — see TeamViewModelTest.tearDown's identical comment: clearing first
        // avoids waking a dead collector on an already-reset Main dispatcher.
        viewModel.clearForTest()
        // Global singleton state — reset so other test classes in the same JVM worker don't
        // inherit whatever team/species-name cache this test left behind (B35 for the latter).
        TeamRepository.replaceAll(emptyList())
        LocalizedNames.clearForTest()
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

    // B11 — load() must also bulk-fetch move/ability localized names, same as it already does for
    // species names (B9), so the detail screen's move list and ability cards can follow the
    // picked language too.
    @Test
    fun `load populates move and ability localized names alongside species names`() = runTest(dispatcher) {
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf("fire" to fakeTypeDetailDto("fire"))
        repository.allSpeciesNames = mapOf("charmander" to mapOf("fr" to "Salamèche"))
        repository.allMoveLocalizedNames = mapOf("scratch" to mapOf("fr" to "Griffe"))
        repository.allAbilityLocalizedNames = mapOf("blaze" to mapOf("fr" to "Brasier"))

        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(mapOf("fr" to "Salamèche"), state.speciesNames["charmander"])
        assertEquals(mapOf("fr" to "Griffe"), state.moveLocalizedNames["scratch"])
        assertEquals(mapOf("fr" to "Brasier"), state.abilityLocalizedNames["blaze"])
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

    // Regression test for B42 / issue #111: reproduces the timing race when the default Dispatchers.Default
    // is used (not overridden with the test dispatcher). In that case, load() suspends on a real-time
    // background thread pool, meaning advanceUntilIdle() on the test scheduler does not wait for it.
    // Consequently, uiState.value.pokemon is still null when loadTeamImpact() is called, so it returns early (no-op).
    @Test
    fun `reproduce loadTeamImpact timing race when defaultDispatcher is not overridden`() = runTest(dispatcher) {
        // Construct the ViewModel WITHOUT the test dispatcher (so it uses real Dispatchers.Default)
        val vmWithRace = PokedexDetailViewModel(repository) // uses Dispatchers.Default

        TeamRepository.replaceAll(listOf(NamedApiResource("squirtle", "https://pokeapi.co/api/v2/pokemon/7/")))
        repository.detailBundle = bundleFor("charmander")
        repository.typeDetailByName = mapOf(
            "fire" to fakeTypeDetailDto("fire"),
            "water" to fakeTypeDetailDto("water")
        )
        repository.pokemonTypes = listOf("water")
        repository.pokemonLevelUpMoveNames = emptyList()
        repository.allMoveInfo = emptyMap()

        // Call load() which launches a coroutine that suspends on a real-time thread (Dispatchers.Default)
        vmWithRace.load("charmander")

        // Immediately calling loadTeamImpact() (before real-time thread finishes)
        vmWithRace.loadTeamImpact()

        // At this point, the load() coroutine is still in progress, so uiState.pokemon is null,
        // and loadTeamImpact() gets skipped.
        val state = vmWithRace.uiState.value
        assertNull("Expected teamImpact to be null because of the timing race skip", state.teamImpact)

        // However, with the properly configured viewModel (which overrides defaultDispatcher with test dispatcher),
        // we can run a deterministic version of this test and assert it compiles and finishes successfully.
        viewModel.load("charmander")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadTeamImpact()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull("Expected teamImpact to be computed under deterministic test dispatcher", viewModel.uiState.value.teamImpact)
    }
}
