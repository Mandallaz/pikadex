package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource.MoveInfo
import com.mandallaz.pikadex.data.remote.dto.DamageRelations
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B17 — [computeTeamMatrices] is the assembly step between the well-tested leaf helpers
 * ([computeDefensiveMultipliers]/[computeOffensiveMultipliers], covered by
 * [TypeEffectivenessTest]) and the diffing on top ([TeamImpactTest]) — but that assembly step
 * carries real rules of its own (status moves excluded from offensive typing, STAB unioned with
 * damaging move types) that neither of those suites exercises.
 */
class TeamMatrixCalculatorTest {

    private fun typeDetail(name: String, doubleTo: List<String> = emptyList(), doubleFrom: List<String> = emptyList()) =
        TypeDetailDto(
            id = 1,
            name = name,
            damageRelations = DamageRelations(
                doubleDamageFrom = doubleFrom.map { NamedApiResource(it, "https://pokeapi.co/api/v2/type/$it/") },
                doubleDamageTo = doubleTo.map { NamedApiResource(it, "https://pokeapi.co/api/v2/type/$it/") },
                halfDamageFrom = null,
                halfDamageTo = null,
                noDamageFrom = null,
                noDamageTo = null
            ),
            pokemon = null
        )

    private fun repository() = FakePokedexRepository().apply {
        typeDetailByName = mapOf(
            // fire is weak to water (x2) and deals x2 to grass — grass is never queried since
            // nothing in this fixture attacks with grass, only checked defensively/offensively
            // through fire/water/fighting.
            "fire" to typeDetail("fire", doubleTo = listOf("grass"), doubleFrom = listOf("water")),
            "water" to typeDetail("water", doubleTo = listOf("fire")),
            "fighting" to typeDetail("fighting", doubleTo = listOf("normal")),
            "electric" to typeDetail("electric", doubleFrom = listOf("ground"))
        )
    }

    @Test
    fun `defensive matrix reflects each member's own types`() = runTest {
        val repo = repository().apply {
            pokemonTypesByName = mapOf("torkoal" to listOf("fire"))
            pokemonLevelUpMoveNamesByName = mapOf("torkoal" to emptyList())
            allMoveInfo = emptyMap()
        }

        val result = computeTeamMatrices(repo, listOf(NamedApiResource("torkoal", "")))

        assertEquals(2.0, result.defensive.getValue("water").getValue("torkoal"), 0.0)
    }

    @Test
    fun `stab types contribute to offense even with no damaging move of that type`() = runTest {
        // poliwrath's fighting STAB must count offensively even though its only move ("surf") is
        // water, not fighting — the offensive type set is stabTypes UNION move types, not either
        // alone.
        val repo = repository().apply {
            pokemonTypesByName = mapOf("poliwrath" to listOf("water", "fighting"))
            pokemonLevelUpMoveNamesByName = mapOf("poliwrath" to listOf("surf"))
            allMoveInfo = mapOf(
                "surf" to MoveInfo(type = "water", damageClass = "special", power = 90, accuracy = 100, pp = 15)
            )
        }

        val result = computeTeamMatrices(repo, listOf(NamedApiResource("poliwrath", "")))

        // water -> fire is x2 (from the water type detail), so poliwrath's best hit on a fire
        // defender is 2.0 — this alone doesn't prove fighting STAB was included, but the next
        // assertion (fighting -> normal x2) does, since poliwrath never learned a fighting move.
        assertEquals(2.0, result.offensive.getValue("fire").getValue("poliwrath"), 0.0)
        assertEquals(2.0, result.offensive.getValue("normal").getValue("poliwrath"), 0.0)
    }

    // The exact rule this guards: STATUS_DAMAGE_CLASS moves must not contribute their type to a
    // member's offensive coverage. Iron Defense is a Steel-type status move here, and
    // typeDetailByName deliberately has no "steel" entry — if the exclusion regressed, resolving
    // offensiveByType would call getTypeDetail("steel") and this test would fail with an
    // unhandled exception (NoSuchElementException from FakePokedexRepository.getValue), not just
    // a wrong number.
    @Test
    fun `status moves are excluded from a member's offensive typing`() = runTest {
        val repo = repository().apply {
            pokemonTypesByName = mapOf("torkoal" to listOf("fire"))
            pokemonLevelUpMoveNamesByName = mapOf("torkoal" to listOf("ember", "iron-defense"))
            allMoveInfo = mapOf(
                "ember" to MoveInfo(type = "fire", damageClass = "special", power = 40, accuracy = 100, pp = 25),
                "iron-defense" to MoveInfo(type = "steel", damageClass = "status", power = null, accuracy = null, pp = 15)
            )
        }

        val result = computeTeamMatrices(repo, listOf(NamedApiResource("torkoal", "")))

        // Torkoal's only real attacking type is fire (from STAB and from Ember); fire deals
        // nothing special to another fire type, so this stays neutral. Had "steel" leaked in from
        // the status move, this assertion would still likely hold, but the test would already
        // have failed above resolving the (deliberately absent) "steel" type detail.
        assertEquals(1.0, result.offensive.getValue("fire").getValue("torkoal"), 0.0)
    }

    // F79 — a member whose species can learn an immunity-granting ability (Levitate vs. Ground)
    // must have that weakness zeroed out in suggestionsDefensive (the shared-weakness input), but
    // the displayed defensive matrix stays type-only and unaffected — confirmed unchanged with the
    // user during grooming.
    @Test
    fun `suggestionsDefensive matrix overrides weaknesses for immune-ability members but leaves defensive matrix unaffected`() = runTest {
        val repo = repository().apply {
            pokemonTypesByName = mapOf("bronzong" to listOf("electric"))
            pokemonLevelUpMoveNamesByName = mapOf("bronzong" to emptyList())
            pokemonAbilitiesByName = mapOf("bronzong" to listOf("levitate", "heatproof"))
            allMoveInfo = emptyMap()
        }

        val result = computeTeamMatrices(repo, listOf(NamedApiResource("bronzong", "")))

        assertEquals(2.0, result.defensive.getValue("ground").getValue("bronzong"), 0.0)
        assertEquals(0.0, result.suggestionsDefensive.getValue("ground").getValue("bronzong"), 0.0)
    }

    @Test
    fun `each member's row is keyed by its own name, independent of team order`() = runTest {
        val repo = repository().apply {
            pokemonTypesByName = mapOf(
                "torkoal" to listOf("fire"),
                "poliwrath" to listOf("water", "fighting")
            )
            pokemonLevelUpMoveNamesByName = mapOf("torkoal" to emptyList(), "poliwrath" to emptyList())
            allMoveInfo = emptyMap()
        }

        val result = computeTeamMatrices(
            repo,
            listOf(NamedApiResource("torkoal", ""), NamedApiResource("poliwrath", ""))
        )

        // Torkoal (fire) is weak to water; Poliwrath (water/fighting) is not.
        assertEquals(2.0, result.defensive.getValue("water").getValue("torkoal"), 0.0)
        assertEquals(1.0, result.defensive.getValue("water").getValue("poliwrath"), 0.0)
        // Both members' entries exist side by side in the same row.
        assertEquals(setOf("torkoal", "poliwrath"), result.defensive.getValue("water").keys)
    }

    @Test
    fun `typeOverrides parameter replaces defensive typing and adds STAB for specified member`() = runTest {
        val repo = repository().apply {
            pokemonTypesByName = mapOf("torkoal" to listOf("fire"))
            pokemonLevelUpMoveNamesByName = mapOf("torkoal" to emptyList())
            allMoveInfo = emptyMap()
        }

        // Torkoal is Fire, but overridden with Tera type "water".
        val result = computeTeamMatrices(
            repo,
            listOf(NamedApiResource("torkoal", "")),
            typeOverrides = mapOf("torkoal" to "water")
        )

        // Defensively, torkoal is now pure Water: Water does not take x2 from Water.
        assertEquals(1.0, result.defensive.getValue("water").getValue("torkoal"), 0.0)
        // Offensively, torkoal gained Water STAB: Water deals x2 to Fire defenders.
        assertEquals(2.0, result.offensive.getValue("fire").getValue("torkoal"), 0.0)
    }
}
