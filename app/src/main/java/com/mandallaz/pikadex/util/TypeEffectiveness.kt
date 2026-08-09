package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto
import kotlin.math.abs

/**
 * Combines the damage_relations of one or two types into a single defensive multiplier per
 * attacking type (e.g. a Water/Flying pokemon takes x4 from Electric). Used both for a single
 * pokemon's weakness chart and for the team-wide matchup matrix.
 */
fun computeDefensiveMultipliers(typeDetails: List<TypeDetailDto>): Map<String, Double> {
    val multipliers = TypeIds.standardTypeNames.associateWith { 1.0 }.toMutableMap()
    typeDetails.forEach { detail ->
        detail.damageRelations.doubleDamageFrom.orEmpty().forEach { multipliers[it.name] = (multipliers[it.name] ?: 1.0) * 2.0 }
        detail.damageRelations.halfDamageFrom.orEmpty().forEach { multipliers[it.name] = (multipliers[it.name] ?: 1.0) * 0.5 }
        detail.damageRelations.noDamageFrom.orEmpty().forEach { multipliers[it.name] = (multipliers[it.name] ?: 1.0) * 0.0 }
    }
    return multipliers
}

/**
 * The multiplier an attack of one type deals to each defending type.
 *
 * The mirror image of [computeDefensiveMultipliers], reading the `*_to` half of damage_relations —
 * which the app has been fetching, parsing and caching all along without ever reading it.
 *
 * Takes a single type rather than a list, and assigns rather than multiplies: a Pokémon defends
 * with both its types at once, so those relations compound, but an attack has exactly one type and
 * therefore exactly one relation against any given defender.
 */
fun computeOffensiveMultipliers(typeDetail: TypeDetailDto): Map<String, Double> {
    val multipliers = TypeIds.standardTypeNames.associateWith { 1.0 }.toMutableMap()
    typeDetail.damageRelations.doubleDamageTo.orEmpty().forEach { multipliers[it.name] = 2.0 }
    typeDetail.damageRelations.halfDamageTo.orEmpty().forEach { multipliers[it.name] = 0.5 }
    typeDetail.damageRelations.noDamageTo.orEmpty().forEach { multipliers[it.name] = 0.0 }
    return multipliers
}

/**
 * The best any of [attackingTypes] can land on each defending type — what one team member is
 * capable of, given every attacking type it has access to.
 *
 * The maximum, not a combination: a Pokémon attacks with one move at a time, so having both Ice and
 * Ground available means hitting each defender with whichever of the two is better, never with some
 * product of the pair.
 *
 * [offensiveByType] maps an attacking type to its [computeOffensiveMultipliers] result. A member
 * with no attacking type at all lands nothing, which is 0.0 rather than a neutral 1.0 — reporting
 * neutral would claim coverage that does not exist.
 */
fun bestOffensiveMultipliers(
    attackingTypes: Collection<String>,
    offensiveByType: Map<String, Map<String, Double>>
): Map<String, Double> = TypeIds.standardTypeNames.associateWith { defendingType ->
    attackingTypes.mapNotNull { offensiveByType[it]?.get(defendingType) }.maxOrNull() ?: 0.0
}

/**
 * Defending types no member of the team can hit for more than neutral damage — the holes a purely
 * defensive read of a team never shows. A team with a flawless resistance spread still loses to
 * anything it cannot meaningfully damage.
 *
 * [offensiveMatrix] is keyed defendingType -> memberName -> best multiplier, matching the shape the
 * defensive matrix already uses.
 */
fun coverageGaps(offensiveMatrix: Map<String, Map<String, Double>>, memberNames: Collection<String>): List<String> {
    if (memberNames.isEmpty()) return emptyList()
    return TypeIds.standardTypeNames.filter { defendingType ->
        val row = offensiveMatrix[defendingType].orEmpty()
        memberNames.none { (row[it] ?: 0.0) > 1.0 }
    }
}

/**
 * Attacking types at least half the team is weak (>1x) to — the team's shared vulnerabilities, and
 * the defensive counterpart of [coverageGaps].
 *
 * [defensiveMatrix] is keyed attackingType -> memberName -> multiplier, matching the shape the
 * offensive matrix already uses.
 */
fun sharedWeaknesses(defensiveMatrix: Map<String, Map<String, Double>>, memberNames: Collection<String>): List<String> {
    if (memberNames.isEmpty()) return emptyList()
    return TypeIds.standardTypeNames.filter { type ->
        val row = defensiveMatrix[type] ?: return@filter false
        val weakCount = memberNames.count { (row[it] ?: 1.0) > 1.0 }
        weakCount * 2 >= memberNames.size && weakCount > 0
    }
}

data class MatchupBucket(val label: String, val multiplier: Double, val types: List<String>)

// "×" + vulgar fractions to match the notation TeamScreen's matrix already uses ("×4", "×½"...)
// — this used to read "x4"/"x1/2" here but "×2"/"×½" there, an inconsistency for the exact same
// concept shown on two screens of the same app.
private val BUCKET_ORDER = listOf(
    4.0 to "Weak to (×4)",
    2.0 to "Weak to (×2)",
    0.5 to "Resists (×½)",
    0.25 to "Resists (×¼)",
    0.0 to "Immune to"
)

// The multipliers actually computed today are all products of exact powers of two (2.0, 0.5, 0.0),
// which IEEE 754 represents and multiplies with zero rounding error — so exact `==` has never
// actually misfired yet. It's still the wrong tool: a future contributor adding, say, a 1.5x
// weather/terrain modifier or any other non-power-of-two factor would silently and confusingly
// break bucketing, since two mathematically-equal multipliers reached via different type
// combinations could then land a bit apart.
private const val EPSILON = 1e-9

/** Groups a defensive-multiplier map into display buckets, skipping neutral (x1) types. */
fun bucketizeMatchups(multipliers: Map<String, Double>): List<MatchupBucket> =
    BUCKET_ORDER.mapNotNull { (multiplier, label) ->
        val types = multipliers.filterValues { abs(it - multiplier) < EPSILON }.keys.sorted()
        if (types.isEmpty()) null else MatchupBucket(label, multiplier, types)
    }
