package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.R
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

    // F90 follow-up — ranking candidate Tera types by how well each one resolves the Pokémon's
    // *current* weaknesses, so the picker can surface the best options first instead of an
    // arbitrary/alphabetical list. Per weakness (weight 2 for a ×4 weakness, 1 for ×2), a
    // candidate scores +2 for becoming immune to that attacking type, +1 for resisting it, 0 for
    // neutral, -1 for still being weak to it (weighted the same way) — types that fix more, and
    // more severe, weaknesses rank higher.
    private val dragonResistsFireImmuneIce = type("dragon", resists = listOf("fire"), immuneTo = listOf("ice"))
    private val steelResistsFireAndIce = type("steel", resists = listOf("fire", "ice"))
    private val rockStillWeakToFire = type("rock", weakTo = listOf("fire"))

    @Test
    fun `types are ranked by how many weaknesses they resolve, weighted by severity`() {
        // fire: x2 weakness (weight 1); ice: x4 weakness (weight 2)
        val currentMatchups = mapOf("fire" to 2.0, "ice" to 4.0, "water" to 1.0)
        val candidates = mapOf(
            "dragon" to dragonResistsFireImmuneIce,
            "steel" to steelResistsFireAndIce,
            "rock" to rockStillWeakToFire
        )

        val ranking = rankTeraTypes(currentMatchups, candidates)

        assertEquals(listOf("dragon", "steel", "rock"), ranking.map { it.first })
        assertEquals(5, ranking.first { it.first == "dragon" }.second) // resist fire (+1) + immune ice (+2*2)
        assertEquals(3, ranking.first { it.first == "steel" }.second) // resist fire (+1) + resist ice (+1*2)
        assertEquals(-1, ranking.first { it.first == "rock" }.second) // still weak to fire (-1), ice neutral (0)
    }

    @Test
    fun `a type with no listed weaknesses scores zero for every candidate`() {
        val ranking = rankTeraTypes(emptyMap(), mapOf("dragon" to dragonResistsFireImmuneIce))
        assertEquals(0, ranking.single().second)
    }

    @Test
    fun `a x1 or resisted matchup is not a weakness and contributes nothing to the ranking`() {
        // water is neutral (x1) and grass would be a resistance (x0.5) — neither should be scored.
        val currentMatchups = mapOf("water" to 1.0, "grass" to 0.5)
        val ranking = rankTeraTypes(currentMatchups, mapOf("dragon" to dragonResistsFireImmuneIce))
        assertEquals(0, ranking.single().second)
    }

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

    // B14 — bucket labels used to be raw English strings; each bucket now carries a @StringRes id
    // (resolved via stringResource() at render time) so the multiplier notation ("×4", "×½"...)
    // follows the app's picked language instead of always being English.
    @Test
    fun `bucket labels resolve to distinct string resources per multiplier`() {
        val buckets = bucketizeMatchups(mapOf("electric" to 4.0, "fire" to 0.5))
        assertEquals(R.string.detail_matchup_weak_x4, buckets.first { it.multiplier == 4.0 }.labelRes)
        assertEquals(R.string.detail_matchup_resists_half, buckets.first { it.multiplier == 0.5 }.labelRes)
    }

    // A tiny drift off the bucket's exact value — the kind a non-power-of-two modifier could
    // introduce in the future — must still land in its bucket rather than being silently dropped.
    @Test
    fun `a multiplier within floating-point tolerance of a bucket still lands in it`() {
        val buckets = bucketizeMatchups(mapOf("electric" to 0.5 + 1e-12, "fire" to 2.0 - 1e-12))
        assertEquals(listOf("electric"), buckets.single { it.multiplier == 0.5 }.types)
        assertEquals(listOf("fire"), buckets.single { it.multiplier == 2.0 }.types)
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

    // --- Shared weaknesses (issue #2) ----------------------------------

    @Test
    fun `a type is shared when at least half the team is weak to it`() {
        val matrix = mapOf(
            "electric" to mapOf("a" to 2.0, "b" to 1.0, "c" to 1.0),
            "fire" to mapOf("a" to 2.0, "b" to 2.0, "c" to 1.0)
        )
        val weaknesses = sharedWeaknesses(matrix, listOf("a", "b", "c"))
        assertTrue("fire" in weaknesses) // 2 of 3 >= half
        assertTrue("electric" !in weaknesses) // 1 of 3 < half
    }

    @Test
    fun `an exact half-team tie counts as shared`() {
        val matrix = mapOf("water" to mapOf("a" to 2.0, "b" to 1.0))
        assertTrue("water" in sharedWeaknesses(matrix, listOf("a", "b")))
    }

    @Test
    fun `a type absent from the matrix is not reported as a shared weakness`() {
        val matrix = mapOf("water" to mapOf("a" to 2.0, "b" to 2.0))
        assertTrue("fire" !in sharedWeaknesses(matrix, listOf("a", "b")))
    }

    @Test
    fun `an empty team has no shared weaknesses to report`() {
        assertEquals(emptyList<String>(), sharedWeaknesses(emptyMap(), emptyList()))
    }

    // --- Team resistances (issue #2, Kingdra feedback) ------------------------------------

    @Test
    fun `a single half-resistant member is enough to report a team resistance`() {
        val matrix = mapOf("water" to mapOf("a" to 1.0, "b" to 0.5))
        assertEquals(listOf("water"), teamResistances(matrix, listOf("a", "b")))
    }

    @Test
    fun `a quarter resistance also counts as a resistance`() {
        val matrix = mapOf("water" to mapOf("a" to 0.25))
        assertEquals(listOf("water"), teamResistances(matrix, listOf("a")))
    }

    @Test
    fun `an outright immunity is not double-counted as a resistance`() {
        val matrix = mapOf("water" to mapOf("a" to 0.0))
        assertTrue(teamResistances(matrix, listOf("a")).isEmpty())
    }

    @Test
    fun `a type absent from the matrix is not reported as a resistance`() {
        val matrix = mapOf("water" to mapOf("a" to 0.5))
        assertTrue("fire" !in teamResistances(matrix, listOf("a")))
    }

    @Test
    fun `an empty team has no resistances to report`() {
        assertEquals(emptyList<String>(), teamResistances(emptyMap(), emptyList()))
    }

    // --- Team immunities / quad weaknesses (issue #2, Toedscool feedback) ---------------

    @Test
    fun `a single immune member is enough to report a team immunity`() {
        val matrix = mapOf("electric" to mapOf("a" to 1.0, "b" to 0.0))
        // Only 1 of 2 members is immune — far below sharedWeaknesses' half-team threshold, but
        // that's irrelevant here: one immune member is already a real defensive asset.
        assertEquals(listOf("electric"), teamImmunities(matrix, listOf("a", "b")))
    }

    @Test
    fun `a single quad-weak member is enough to report a team quad weakness`() {
        val matrix = mapOf("ice" to mapOf("a" to 1.0, "b" to 4.0))
        assertEquals(listOf("ice"), teamQuadWeaknesses(matrix, listOf("a", "b")))
    }

    @Test
    fun `a merely double weakness does not count as a quad weakness`() {
        val matrix = mapOf("ice" to mapOf("a" to 2.0))
        assertTrue(teamQuadWeaknesses(matrix, listOf("a")).isEmpty())
    }

    @Test
    fun `a type absent from the matrix is neither an immunity nor a quad weakness`() {
        val matrix = mapOf("water" to mapOf("a" to 0.0))
        assertTrue("fire" !in teamImmunities(matrix, listOf("a")))
        assertTrue("fire" !in teamQuadWeaknesses(matrix, listOf("a")))
    }

    @Test
    fun `an empty team has no immunities or quad weaknesses to report`() {
        assertEquals(emptyList<String>(), teamImmunities(emptyMap(), emptyList()))
        assertEquals(emptyList<String>(), teamQuadWeaknesses(emptyMap(), emptyList()))
    }
}
