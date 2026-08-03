package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.DamageRelations
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The defensive multipliers behind both the single-pokemon weakness chart and the team matrix.
 * Dual typings multiply, so the interesting cases are the ones where two types compound (×4) or
 * where one cancels the other outright (an immunity beats any number of weaknesses).
 */
class TypeEffectivenessTest {

    private fun ref(name: String) = NamedApiResource(name, "https://pokeapi.co/api/v2/type/$name/")

    private fun type(
        name: String,
        weakTo: List<String> = emptyList(),
        resists: List<String> = emptyList(),
        immuneTo: List<String> = emptyList()
    ) = TypeDetailDto(
        id = 0,
        name = name,
        damageRelations = DamageRelations(
            doubleDamageFrom = weakTo.map(::ref),
            doubleDamageTo = emptyList(),
            halfDamageFrom = resists.map(::ref),
            halfDamageTo = emptyList(),
            noDamageFrom = immuneTo.map(::ref),
            noDamageTo = emptyList()
        ),
        pokemon = emptyList()
    )

    private val water = type("water", weakTo = listOf("electric", "grass"), resists = listOf("fire", "water"))
    private val flying = type("flying", weakTo = listOf("electric", "rock", "ice"), resists = listOf("grass", "fighting"), immuneTo = listOf("ground"))
    private val ground = type("ground", weakTo = listOf("water", "grass", "ice"), immuneTo = listOf("electric"))

    @Test
    fun `a single type leaves every unrelated type neutral`() {
        val result = computeDefensiveMultipliers(listOf(water))
        assertEquals(1.0, result.getValue("normal"), 0.0)
        assertEquals(2.0, result.getValue("electric"), 0.0)
        assertEquals(0.5, result.getValue("fire"), 0.0)
    }

    @Test
    fun `two types weak to the same attack compound to four times`() {
        val result = computeDefensiveMultipliers(listOf(water, flying))
        assertEquals(4.0, result.getValue("electric"), 0.0)
    }

    @Test
    fun `a weakness and a resistance cancel back to neutral`() {
        val result = computeDefensiveMultipliers(listOf(water, flying))
        // water is weak to grass, flying resists it.
        assertEquals(1.0, result.getValue("grass"), 0.0)
    }

    @Test
    fun `an immunity wins over any weakness on the other type`() {
        val result = computeDefensiveMultipliers(listOf(ground, flying))
        // flying is weak to electric (x2), ground is immune (x0).
        assertEquals(0.0, result.getValue("electric"), 0.0)
    }

    @Test
    fun `every standard type gets a multiplier, so no cell reads as missing data`() {
        val result = computeDefensiveMultipliers(listOf(water))
        assertEquals(TypeIds.standardTypeNames.toSet(), result.keys)
    }

    @Test
    fun `buckets run from most dangerous to least and skip neutral types`() {
        val buckets = bucketizeMatchups(
            mapOf(
                "electric" to 4.0,
                "rock" to 2.0,
                "normal" to 1.0,
                "fire" to 0.5,
                "grass" to 0.25,
                "ground" to 0.0
            )
        )
        assertEquals(listOf(4.0, 2.0, 0.5, 0.25, 0.0), buckets.map { it.multiplier })
        assertTrue(buckets.none { "normal" in it.types })
    }

    @Test
    fun `an empty bucket is omitted rather than shown with no types`() {
        val buckets = bucketizeMatchups(mapOf("electric" to 2.0, "normal" to 1.0))
        assertEquals(1, buckets.size)
        assertEquals(listOf("electric"), buckets.single().types)
    }

    @Test
    fun `types within a bucket are sorted so the order is stable between renders`() {
        val buckets = bucketizeMatchups(mapOf("rock" to 2.0, "electric" to 2.0, "ice" to 2.0))
        assertEquals(listOf("electric", "ice", "rock"), buckets.single().types)
    }

    @Test
    fun `bucket labels use the same multiplier notation as the team matrix`() {
        val buckets = bucketizeMatchups(mapOf("electric" to 4.0, "fire" to 0.5))
        assertTrue(buckets.any { it.label.contains("×4") })
        assertTrue(buckets.any { it.label.contains("×½") })
    }

    // --- Offensive coverage -------------------------------------------------

    private fun attacker(
        name: String,
        superEffectiveAgainst: List<String> = emptyList(),
        resistedBy: List<String> = emptyList(),
        uselessAgainst: List<String> = emptyList()
    ) = TypeDetailDto(
        id = 0,
        name = name,
        damageRelations = DamageRelations(
            doubleDamageFrom = emptyList(),
            doubleDamageTo = superEffectiveAgainst.map(::ref),
            halfDamageFrom = emptyList(),
            halfDamageTo = resistedBy.map(::ref),
            noDamageFrom = emptyList(),
            noDamageTo = uselessAgainst.map(::ref)
        ),
        pokemon = emptyList()
    )

    private val ice = attacker("ice", superEffectiveAgainst = listOf("dragon", "flying"), resistedBy = listOf("steel", "fire"))
    private val groundAtk = attacker("ground", superEffectiveAgainst = listOf("steel", "fire"), uselessAgainst = listOf("flying"))
    private val normal = attacker("normal", resistedBy = listOf("rock"), uselessAgainst = listOf("ghost"))

    @Test
    fun `offensive multipliers read the to-side of damage relations`() {
        val result = computeOffensiveMultipliers(ice)
        assertEquals(2.0, result.getValue("dragon"), 0.0)
        assertEquals(0.5, result.getValue("steel"), 0.0)
        assertEquals(1.0, result.getValue("water"), 0.0)
    }

    @Test
    fun `an attacking type that cannot touch a defender reads zero, not neutral`() {
        assertEquals(0.0, computeOffensiveMultipliers(normal).getValue("ghost"), 0.0)
        assertEquals(0.0, computeOffensiveMultipliers(groundAtk).getValue("flying"), 0.0)
    }

    // Defending with two types compounds (x2 and x2 makes x4); attacking with two does not, because
    // only one move is used at a time.
    @Test
    fun `two attacking types take the better of the two rather than compounding`() {
        val offensive = mapOf(
            "ice" to computeOffensiveMultipliers(ice),
            "ground" to computeOffensiveMultipliers(groundAtk)
        )
        val best = bestOffensiveMultipliers(listOf("ice", "ground"), offensive)
        // ice is resisted by steel, ground is super effective on it: take the 2.0.
        assertEquals(2.0, best.getValue("steel"), 0.0)
        // ice hits flying for x2, ground cannot touch it at all: take the 2.0, not the 0.0.
        assertEquals(2.0, best.getValue("flying"), 0.0)
        // neither is better than neutral on water.
        assertEquals(1.0, best.getValue("water"), 0.0)
    }

    @Test
    fun `a member with no attacking type lands nothing rather than reading neutral`() {
        val best = bestOffensiveMultipliers(emptyList(), emptyMap())
        assertTrue(best.values.all { it == 0.0 })
    }

    @Test
    fun `unknown attacking types are skipped instead of poisoning the maximum`() {
        val offensive = mapOf("ice" to computeOffensiveMultipliers(ice))
        val best = bestOffensiveMultipliers(listOf("ice", "not-a-type"), offensive)
        assertEquals(2.0, best.getValue("dragon"), 0.0)
    }

    @Test
    fun `a coverage gap is a defender no member beats, even if some hit it neutrally`() {
        val matrix = mapOf(
            "steel" to mapOf("a" to 1.0, "b" to 0.5),
            "dragon" to mapOf("a" to 2.0, "b" to 1.0)
        )
        val gaps = coverageGaps(matrix, listOf("a", "b"))
        assertTrue("steel" in gaps)
        assertTrue("dragon" !in gaps)
    }

    @Test
    fun `a defender absent from the matrix counts as a gap rather than as covered`() {
        val gaps = coverageGaps(mapOf("dragon" to mapOf("a" to 2.0)), listOf("a"))
        assertTrue("steel" in gaps)
        assertTrue("dragon" !in gaps)
    }

    @Test
    fun `an empty team has no gaps to report`() {
        assertEquals(emptyList<String>(), coverageGaps(emptyMap(), emptyList()))
    }
}
