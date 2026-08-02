package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.TypeDetailDto

/**
 * Combines the damage_relations of one or two types into a single defensive multiplier per
 * attacking type (e.g. a Water/Flying pokemon takes x4 from Electric). Used both for a single
 * pokemon's weakness chart and for the team-wide matchup matrix.
 */
fun computeDefensiveMultipliers(typeDetails: List<TypeDetailDto>): Map<String, Double> {
    val multipliers = TypeIds.standardTypeNames.associateWith { 1.0 }.toMutableMap()
    typeDetails.forEach { detail ->
        detail.damageRelations.doubleDamageFrom.forEach { multipliers[it.name] = (multipliers[it.name] ?: 1.0) * 2.0 }
        detail.damageRelations.halfDamageFrom.forEach { multipliers[it.name] = (multipliers[it.name] ?: 1.0) * 0.5 }
        detail.damageRelations.noDamageFrom.forEach { multipliers[it.name] = (multipliers[it.name] ?: 1.0) * 0.0 }
    }
    return multipliers
}

data class MatchupBucket(val label: String, val multiplier: Double, val types: List<String>)

private val BUCKET_ORDER = listOf(
    4.0 to "Weak to (x4)",
    2.0 to "Weak to (x2)",
    0.5 to "Resists (x1/2)",
    0.25 to "Resists (x1/4)",
    0.0 to "Immune to"
)

/** Groups a defensive-multiplier map into display buckets, skipping neutral (x1) types. */
fun bucketizeMatchups(multipliers: Map<String, Double>): List<MatchupBucket> =
    BUCKET_ORDER.mapNotNull { (multiplier, label) ->
        val types = multipliers.filterValues { it == multiplier }.keys.sorted()
        if (types.isEmpty()) null else MatchupBucket(label, multiplier, types)
    }
