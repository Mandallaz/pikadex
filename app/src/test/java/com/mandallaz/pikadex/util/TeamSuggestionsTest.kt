package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.DamageRelations
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [rankSuggestions] against a tiny hand-built type chart: the team is weak to water and has a
 * ground coverage gap. Grass resists water and hits ground super effectively (qualifies both
 * ways), water hits ground but doesn't resist itself (gap only), psychic resists water but
 * doesn't touch ground (weakness only) — just enough overlap to exercise the "both required" rule.
 */
class TeamSuggestionsTest {

    private fun ref(name: String) = NamedApiResource(name, "https://pokeapi.co/api/v2/type/$name/")

    private fun type(
        name: String,
        resists: List<String> = emptyList(),
        superEffectiveAgainst: List<String> = emptyList()
    ) = TypeDetailDto(
        id = 0,
        name = name,
        damageRelations = DamageRelations(
            doubleDamageFrom = emptyList(),
            doubleDamageTo = superEffectiveAgainst.map(::ref),
            halfDamageFrom = resists.map(::ref),
            halfDamageTo = emptyList(),
            noDamageFrom = emptyList(),
            noDamageTo = emptyList()
        ),
        pokemon = emptyList()
    )

    private val grass = type("grass", resists = listOf("water"), superEffectiveAgainst = listOf("ground"))
    private val water = type("water", superEffectiveAgainst = listOf("ground"))
    private val psychic = type("psychic", resists = listOf("water"))
    private val ground = type("ground")

    private val typeDetails = mapOf("grass" to grass, "water" to water, "psychic" to psychic, "ground" to ground)

    private val sharedWeaknesses = listOf("water")
    private val coverageGaps = listOf("ground")

    @Test
    fun `a candidate that both resists the weakness and hits the gap qualifies`() {
        val candidates = listOf(SuggestionCandidate("victreebel", listOf("grass"), statTotal = 490))
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetails, excludeNames = emptySet())
        assertEquals(listOf("victreebel"), result.map { it.name })
    }

    @Test
    fun `hitting the gap alone is not enough without also resisting the weakness`() {
        val candidates = listOf(SuggestionCandidate("gyarados", listOf("water"), statTotal = 540))
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetails, excludeNames = emptySet())
        assertEquals(emptyList<String>(), result.map { it.name })
    }

    @Test
    fun `resisting the weakness alone is not enough without also closing a gap`() {
        val candidates = listOf(SuggestionCandidate("alakazam", listOf("psychic"), statTotal = 500))
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetails, excludeNames = emptySet())
        assertEquals(emptyList<String>(), result.map { it.name })
    }

    @Test
    fun `excluded names are dropped even when they qualify`() {
        val candidates = listOf(SuggestionCandidate("victreebel", listOf("grass"), statTotal = 490))
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetails, excludeNames = setOf("victreebel"))
        assertEquals(emptyList<String>(), result.map { it.name })
    }

    @Test
    fun `equally impactful candidates are sorted by stat total ascending`() {
        val strong = SuggestionCandidate("strongmon", listOf("grass"), statTotal = 600)
        val weak = SuggestionCandidate("weakmon", listOf("grass"), statTotal = 300)
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, listOf(strong, weak), typeDetails, excludeNames = emptySet())
        assertEquals(listOf("weakmon", "strongmon"), result.map { it.name })
    }

    @Test
    fun `a candidate resisting more weaknesses and hitting more gaps ranks first regardless of stat total`() {
        // Two shared weaknesses (water, ice), two coverage gaps (ground, dragon). "grassSteel"
        // resists both weaknesses and hits both gaps (impact 4); "grassOnly" only resists water and
        // only hits ground (impact 2) despite a much higher stat total.
        val steel = type("steel", resists = listOf("ice"), superEffectiveAgainst = listOf("dragon"))
        val details = typeDetails + ("steel" to steel)
        val weaknesses = listOf("water", "ice")
        val gaps = listOf("ground", "dragon")
        val highImpactLowStats = SuggestionCandidate("grassSteel", listOf("grass", "steel"), statTotal = 300)
        val lowImpactHighStats = SuggestionCandidate("grassOnly", listOf("grass"), statTotal = 600)
        val result = rankSuggestions(weaknesses, gaps, listOf(lowImpactHighStats, highImpactLowStats), details, excludeNames = emptySet())
        assertEquals(listOf("grassSteel", "grassOnly"), result.map { it.name })
    }

    @Test
    fun `weaknessesResisted and gapsHit list exactly what qualified, not every shared weakness or gap`() {
        val steel = type("steel", resists = listOf("ice"), superEffectiveAgainst = listOf("dragon"))
        val details = typeDetails + ("steel" to steel)
        val weaknesses = listOf("water", "ice")
        val gaps = listOf("ground", "dragon")
        val candidates = listOf(SuggestionCandidate("victreebel", listOf("grass"), statTotal = 490))
        val result = rankSuggestions(weaknesses, gaps, candidates, details, excludeNames = emptySet())
        assertEquals(listOf("water"), result.single().weaknessesResisted)
        assertEquals(listOf("ground"), result.single().gapsHit)
    }

    @Test
    fun `results are capped at the limit`() {
        val candidates = (1..15).map { SuggestionCandidate("mon$it", listOf("grass"), statTotal = it) }
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetails, excludeNames = emptySet(), limit = 10)
        assertEquals(10, result.size)
        assertEquals((1..10).map { "mon$it" }, result.map { it.name })
    }

    @Test
    fun `the default limit is 6, not 10`() {
        val candidates = (1..15).map { SuggestionCandidate("mon$it", listOf("grass"), statTotal = it) }
        val result = rankSuggestions(sharedWeaknesses, coverageGaps, candidates, typeDetails, excludeNames = emptySet())
        assertEquals(6, result.size)
    }

    @Test
    fun `no shared weaknesses or no coverage gaps means nothing to suggest`() {
        val candidates = listOf(SuggestionCandidate("victreebel", listOf("grass"), statTotal = 490))
        assertEquals(emptyList<String>(), rankSuggestions(emptyList(), coverageGaps, candidates, typeDetails, emptySet()).map { it.name })
        assertEquals(emptyList<String>(), rankSuggestions(sharedWeaknesses, emptyList(), candidates, typeDetails, emptySet()).map { it.name })
    }

    // --- Tier ceiling filter (issue #11) ---------------------------------

    @Test
    fun `no ceiling set is a no-op`() {
        val candidates = listOf(SuggestionCandidate("landorus", listOf("ground"), statTotal = 600))
        val result = filterByTierCeiling(candidates, maxTier = null, tierByShowdownKey = mapOf("landorus" to "Uber"))
        assertEquals(candidates, result)
    }

    @Test
    fun `a candidate at or below the ceiling is kept`() {
        val candidates = listOf(
            SuggestionCandidate("tangrowth", listOf("grass"), statTotal = 535), // UU
            SuggestionCandidate("ferrothorn", listOf("grass", "steel"), statTotal = 489) // RU, below UU
        )
        val tiers = mapOf("tangrowth" to "UU", "ferrothorn" to "RU")
        val result = filterByTierCeiling(candidates, maxTier = "UU", tierByShowdownKey = tiers)
        assertEquals(listOf("tangrowth", "ferrothorn"), result.map { it.name })
    }

    @Test
    fun `a candidate above the ceiling is dropped`() {
        val candidates = listOf(SuggestionCandidate("landorus", listOf("ground"), statTotal = 600))
        val result = filterByTierCeiling(candidates, maxTier = "UU", tierByShowdownKey = mapOf("landorus" to "Uber"))
        assertEquals(emptyList<String>(), result.map { it.name })
    }

    @Test
    fun `a candidate with no known tier is kept rather than excluded`() {
        val candidates = listOf(SuggestionCandidate("mysteron", listOf("ground"), statTotal = 400))
        val result = filterByTierCeiling(candidates, maxTier = "UU", tierByShowdownKey = emptyMap())
        assertEquals(listOf("mysteron"), result.map { it.name })
    }

    @Test
    fun `showdown keys with hyphens stripped still resolve`() {
        val candidates = listOf(SuggestionCandidate("mr-mime", listOf("psychic", "fairy"), statTotal = 460))
        val result = filterByTierCeiling(candidates, maxTier = "UU", tierByShowdownKey = mapOf("mrmime" to "Uber"))
        assertEquals(emptyList<String>(), result.map { it.name })
    }
}
