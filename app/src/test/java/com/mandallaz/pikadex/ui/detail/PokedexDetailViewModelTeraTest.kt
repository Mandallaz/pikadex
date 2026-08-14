package com.mandallaz.pikadex.ui.detail

import com.mandallaz.pikadex.data.LocalizedNames
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.clearForTest
import com.mandallaz.pikadex.data.remote.dto.DamageRelations
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import com.mandallaz.pikadex.data.repository.PokemonDetailBundle
import com.mandallaz.pikadex.data.repository.fakePokemonDto
import com.mandallaz.pikadex.data.repository.fakePokemonSpeciesDto
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.clearForTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** F90 — previewing a Terastallization type on the detail screen overrides the defensive (Type
 *  Matchups) and offensive (Type Triangles) calculations to use only that one type, instead of the
 *  Pokémon's real types — same "one pure type replaces everything" rule the actual game mechanic
 *  has. Ephemeral: [PokedexDetailViewModel.selectTeraType] mutates in-memory state only, nothing
 *  persisted. */
class PokedexDetailViewModelTeraTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePokedexRepository
    private lateinit var viewModel: PokedexDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        LocalizedNames.clearForTest()
        repository = FakePokedexRepository()
        viewModel = PokedexDetailViewModel(repository, dispatcher)
    }

    @After
    fun tearDown() {
        viewModel.clearForTest(dispatcher.scheduler)
        TeamRepository.replaceAll(emptyList())
        LocalizedNames.clearForTest()
        Dispatchers.resetMain()
    }

    // fakeTypeDetailDto (this class's own repository default) has empty damage relations for
    // every type, which would make every override indistinguishable from the real typing —
    // these fixtures carry real relations so an override actually changes the resulting map.
    private fun typeDetailWithDoubleDamageFrom(name: String, vararg weakTo: String) = TypeDetailDto(
        id = 1,
        name = name,
        damageRelations = DamageRelations(
            doubleDamageFrom = weakTo.map { NamedApiResource(it, "") },
            doubleDamageTo = null,
            halfDamageFrom = null,
            halfDamageTo = null,
            noDamageFrom = null,
            noDamageTo = null
        ),
        pokemon = null
    )

    private fun typeDetailWithHalfDamageFrom(name: String, vararg resists: String) = TypeDetailDto(
        id = 1,
        name = name,
        damageRelations = DamageRelations(
            doubleDamageFrom = null,
            doubleDamageTo = null,
            halfDamageFrom = resists.map { NamedApiResource(it, "") },
            halfDamageTo = null,
            noDamageFrom = null,
            noDamageTo = null
        ),
        pokemon = null
    )

    private fun loadCharizard() {
        repository.detailBundle = PokemonDetailBundle(
            pokemon = fakePokemonDto(id = 6, name = "charizard", types = listOf("fire", "flying")),
            species = fakePokemonSpeciesDto(id = 6, name = "charizard"),
            evolutionChain = null
        )
        repository.typeDetailByName = mapOf(
            "fire" to typeDetailWithDoubleDamageFrom("fire", "water"),
            "flying" to typeDetailWithDoubleDamageFrom("flying", "electric"),
            "water" to typeDetailWithDoubleDamageFrom("water", "grass")
        )
        viewModel.load("charizard")
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `selecting a Tera type overrides the defensive matchups to that single type`() = runTest(dispatcher) {
        loadCharizard()
        val realMatchups = viewModel.uiState.value.typeMatchups

        viewModel.selectTeraType("water")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("water", state.teraType)
        // Overridden matchups must differ from the real fire/flying typing's own matchups —
        // otherwise the override silently did nothing.
        assertTrue(state.teraTypeMatchups != realMatchups)
    }

    @Test
    fun `clearing the Tera type (null) removes the override`() = runTest(dispatcher) {
        loadCharizard()
        viewModel.selectTeraType("water")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTeraType(null)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.teraType)
        assertNull(state.teraTypeMatchups)
    }

    @Test
    fun `loading a different Pokemon resets any active Tera preview`() = runTest(dispatcher) {
        loadCharizard()
        viewModel.selectTeraType("water")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("water", viewModel.uiState.value.teraType)

        repository.detailBundle = PokemonDetailBundle(
            pokemon = fakePokemonDto(id = 7, name = "squirtle", types = listOf("water")),
            species = fakePokemonSpeciesDto(id = 7, name = "squirtle"),
            evolutionChain = null
        )
        viewModel.load("squirtle")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.teraType)
    }

    // F90 follow-up — the Tera-type picker is ranked best-first (util.rankTeraTypes), loaded
    // lazily when the picker opens (same "not part of load()" pattern as loadCompareCandidatesIfNeeded)
    // rather than eagerly, since most detail-screen visits never open it.
    @Test
    fun `loading Tera type options ranks them by how well each resolves the current weaknesses`() = runTest(dispatcher) {
        loadCharizard() // real weaknesses end up water x2 and electric x2 (fire+flying combined)
        // Every standard type needs an entry — getTypeDetail(t) is called for all 18 to build the
        // ranking. "grass" resists both current weaknesses (best); "rock" is weak to one of them
        // (worst); everything else stays neutral (score 0).
        val allTypeDetails = TypeIds.standardTypeNames.associateWith { name ->
            when (name) {
                "grass" -> typeDetailWithHalfDamageFrom("grass", "water", "electric")
                "rock" -> typeDetailWithDoubleDamageFrom("rock", "water")
                else -> typeDetailWithDoubleDamageFrom(name)
            }
        }
        repository.typeDetailByName = allTypeDetails + repository.typeDetailByName

        viewModel.loadTeraTypeOptionsIfNeeded()
        dispatcher.scheduler.advanceUntilIdle()

        // F90 follow-up — the picker shows each option's score, not just its rank, so
        // teraTypeOptions carries the (type, score) pair rankTeraTypes already computes rather
        // than just the ordered names.
        val options = viewModel.uiState.value.teraTypeOptions
        assertEquals(TypeIds.standardTypeNames.size, options.size)
        assertEquals("grass" to 2, options.first()) // resists both x2 weaknesses (water, electric): +1 + +1
        assertEquals("rock" to -1, options.last())
    }
}
