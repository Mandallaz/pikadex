package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.ChainLink
import com.mandallaz.pikadex.data.remote.dto.EvolutionDetail
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B15 — [evolutionPaths] is recursive over a branching tree and does index-sensitive condition
 * attachment; [describeEvolutionDetail]'s branch ordering (minLevel checked before item) is
 * load-bearing. Neither had coverage before this.
 */
class EvolutionUtilsTest {

    private fun species(name: String, id: Int) = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon-species/$id/")
    private fun resource(name: String) = NamedApiResource(name, "https://pokeapi.co/api/v2/x/$name/")

    private fun leaf(name: String, id: Int) = ChainLink(species(name, id), emptyList(), emptyList())

    // ---- describeEvolutionDetail ----

    @Test
    fun `null detail describes as null`() {
        assertNull(describeEvolutionDetail(null))
    }

    @Test
    fun `min level detail`() {
        val detail = EvolutionDetail(null, 16, null, null, null, null, null, null, null)
        assertEquals(UiText(R.string.detail_evolution_condition_level, listOf(16)), describeEvolutionDetail(detail))
    }

    @Test
    fun `item detail`() {
        val detail = EvolutionDetail(null, null, resource("water-stone"), null, null, null, null, null, null)
        assertEquals(
            UiText(R.string.detail_evolution_condition_item, listOf("Water Stone")),
            describeEvolutionDetail(detail)
        )
    }

    // The exact bug this guards: describeEvolutionDetail's `when` checks minLevel before item, so
    // a detail carrying both (PokeAPI never actually sends this combination, but nothing enforces
    // it) must report as a level-up, not an item.
    @Test
    fun `a detail carrying both min level and item reports as a level-up`() {
        val detail = EvolutionDetail(null, 20, resource("water-stone"), null, null, null, null, null, null)
        assertEquals(UiText(R.string.detail_evolution_condition_level, listOf(20)), describeEvolutionDetail(detail))
    }

    @Test
    fun `trade detail with no held item`() {
        val detail = EvolutionDetail(resource("trade"), null, null, null, null, null, null, null, null)
        assertEquals(UiText(R.string.detail_evolution_condition_trade), describeEvolutionDetail(detail))
    }

    @Test
    fun `trade detail with a held item`() {
        val detail = EvolutionDetail(resource("trade"), null, null, resource("kings-rock"), null, null, null, null, null)
        assertEquals(
            UiText(R.string.detail_evolution_condition_trade_with_item, listOf("Kings Rock")),
            describeEvolutionDetail(detail)
        )
    }

    @Test
    fun `min happiness detail`() {
        val detail = EvolutionDetail(null, null, null, null, null, 220, null, null, null)
        assertEquals(UiText(R.string.detail_evolution_condition_friendship), describeEvolutionDetail(detail))
    }

    @Test
    fun `known move detail`() {
        val detail = EvolutionDetail(null, null, null, null, resource("mimic"), null, null, null, null)
        assertEquals(
            UiText(R.string.detail_evolution_condition_move, listOf("Mimic")),
            describeEvolutionDetail(detail)
        )
    }

    @Test
    fun `time of day detail`() {
        val detail = EvolutionDetail(null, null, null, null, null, null, null, "day", null)
        assertEquals(UiText(R.string.detail_evolution_condition_time, listOf("day")), describeEvolutionDetail(detail))
    }

    @Test
    fun `a bare trigger with none of the specific conditions falls back to its own display name`() {
        val detail = EvolutionDetail(resource("shed"), null, null, null, null, null, null, null, null)
        assertEquals(UiText(R.string.detail_evolution_condition_raw, listOf("Shed")), describeEvolutionDetail(detail))
    }

    @Test
    fun `a detail with nothing set at all describes as null`() {
        val detail = EvolutionDetail(null, null, null, null, null, null, null, null, null)
        assertNull(describeEvolutionDetail(detail))
    }

    // ---- evolutionPaths ----

    @Test
    fun `a species with no further evolution is a single one-stage path`() {
        val chain = leaf("magikarp", 129)

        val paths = evolutionPaths(chain)

        assertEquals(1, paths.size)
        assertEquals(listOf("magikarp"), paths.single().map { it.speciesName })
        assertNull(paths.single().single().conditionLabel)
    }

    @Test
    fun `a linear three-stage chain carries each stage's own condition, not the previous one's`() {
        val venusaur = leaf("venusaur", 3)
        val ivysaur = ChainLink(
            species("ivysaur", 2),
            listOf(EvolutionDetail(null, 16, null, null, null, null, null, null, null)),
            listOf(venusaur)
        )
        val bulbasaur = ChainLink(
            species("bulbasaur", 1),
            emptyList(),
            listOf(ivysaur)
        )
        val venusaurEvolution = ChainLink(
            species("venusaur", 3),
            listOf(EvolutionDetail(null, 32, null, null, null, null, null, null, null)),
            emptyList()
        )
        val ivysaurWithEvolution = ChainLink(
            species("ivysaur", 2),
            listOf(EvolutionDetail(null, 16, null, null, null, null, null, null, null)),
            listOf(venusaurEvolution)
        )
        val root = ChainLink(species("bulbasaur", 1), emptyList(), listOf(ivysaurWithEvolution))

        val paths = evolutionPaths(root)

        assertEquals(1, paths.size)
        val path = paths.single()
        assertEquals(listOf("bulbasaur", "ivysaur", "venusaur"), path.map { it.speciesName })
        assertNull(path[0].conditionLabel)
        assertEquals(UiText(R.string.detail_evolution_condition_level, listOf(16)), path[1].conditionLabel)
        assertEquals(UiText(R.string.detail_evolution_condition_level, listOf(32)), path[2].conditionLabel)
    }

    // The exact bug this guards: index-sensitive condition attachment (`if (index == 0) ...`) is
    // easy to get wrong for a branching tree — each branch's condition must land on that branch's
    // own first stage, not leak into the other branch or the shared root.
    @Test
    fun `a branching chain produces one path per branch with the correct condition on each`() {
        val vaporeon = leaf("vaporeon", 134)
        val jolteon = leaf("jolteon", 135)
        val eevee = ChainLink(
            species("eevee", 133),
            emptyList(),
            listOf(
                ChainLink(vaporeon.species, listOf(EvolutionDetail(null, null, resource("water-stone"), null, null, null, null, null, null)), emptyList()),
                ChainLink(jolteon.species, listOf(EvolutionDetail(null, null, resource("thunder-stone"), null, null, null, null, null, null)), emptyList())
            )
        )

        val paths = evolutionPaths(eevee)

        assertEquals(2, paths.size)
        assertEquals(listOf("eevee", "vaporeon"), paths[0].map { it.speciesName })
        assertEquals(
            UiText(R.string.detail_evolution_condition_item, listOf("Water Stone")),
            paths[0][1].conditionLabel
        )
        assertEquals(listOf("eevee", "jolteon"), paths[1].map { it.speciesName })
        assertEquals(
            UiText(R.string.detail_evolution_condition_item, listOf("Thunder Stone")),
            paths[1][1].conditionLabel
        )
        // The shared root must never pick up either branch's condition.
        assertNull(paths[0][0].conditionLabel)
        assertNull(paths[1][0].conditionLabel)
    }

    @Test
    fun `a species with no id-bearing url falls back to id 0 rather than crashing`() {
        val chain = ChainLink(NamedApiResource("missingno", "not-a-real-url"), emptyList(), emptyList())

        val paths = evolutionPaths(chain)

        assertEquals(0, paths.single().single().id)
    }
}
