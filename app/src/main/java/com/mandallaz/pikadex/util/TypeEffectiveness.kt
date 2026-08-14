package com.mandallaz.pikadex.util

import androidx.annotation.StringRes
import com.mandallaz.pikadex.R
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

/** F79 — abilities that grant a genuine full type immunity (0x, not just a resistance), keyed by
 *  the type they immunize against. Curated per the issue's grooming decision: excludes
 *  resistance/redirection-without-immunity abilities like Dry Skin, and move-flag-based immunities
 *  like Soundproof/Bulletproof/Overcoat, which aren't type immunities at all. Wonder Guard is
 *  deliberately excluded too — it's immune to everything except super-effective hits, not tied to
 *  one type, and was deferred to its own issue during grooming. Ability names are PokeAPI's
 *  lower-hyphenated slugs, matching [com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource.PokemonBasics.abilities]. */
val IMMUNITY_ABILITIES_BY_TYPE: Map<String, Set<String>> = mapOf(
    "ground" to setOf("levitate", "earth-eater"),
    "fire" to setOf("flash-fire", "well-baked-body"),
    "water" to setOf("water-absorb", "storm-drain"),
    "electric" to setOf("volt-absorb", "lightning-rod", "motor-drive"),
    "grass" to setOf("sap-sipper")
)

/** F79 — overrides [defensive]'s multiplier to `0.0` for any attacking type [abilities] grants a
 *  full immunity to (see [IMMUNITY_ABILITIES_BY_TYPE]), leaving every other entry untouched. Used
 *  only for the Team Suggestions calculation ([com.mandallaz.pikadex.util.TeamMatrixResult.suggestionsDefensive]/
 *  [SuggestionCandidate]) — the displayed type-matchup matrix stays type-only on purpose (confirmed
 *  with the user during grooming, not changing), so this must never feed [computeDefensiveMultipliers]'s
 *  own result back into the UI-facing matrix. Since the app has no per-member ability selection yet
 *  (F81), [abilities] is expected to be every *possible* ability for the species (standard or
 *  hidden), not one actually-chosen ability — an approximation, not an exact read. */
fun adjustDefensiveMultipliersForAbilities(
    defensive: Map<String, Double>,
    abilities: Collection<String>
): Map<String, Double> {
    if (abilities.isEmpty()) return defensive
    val abilitySet = abilities.toSet()
    val immuneTypes = IMMUNITY_ABILITIES_BY_TYPE.filterValues { it.any { a -> a in abilitySet } }.keys
    if (immuneTypes.isEmpty()) return defensive
    return defensive + immuneTypes.associateWith { 0.0 }
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

/**
 * Attacking types at least one team member is immune (0x) to — a real defensive asset regardless of
 * how many others share it, unlike [sharedWeaknesses]' majority threshold. issue #2's
 * per-member "adds immunity to..." signal reads off this rather than the team-wide majority rule,
 * since a single immune member is already worth calling out (see the Toedscool/Electric example
 * that motivated this).
 *
 * [defensiveMatrix] is keyed attackingType -> memberName -> multiplier, same shape [sharedWeaknesses]
 * reads.
 */
fun teamImmunities(defensiveMatrix: Map<String, Map<String, Double>>, memberNames: Collection<String>): List<String> {
    if (memberNames.isEmpty()) return emptyList()
    return TypeIds.standardTypeNames.filter { type ->
        val row = defensiveMatrix[type].orEmpty()
        memberNames.any { abs((row[it] ?: 1.0) - 0.0) < EPSILON }
    }
}

/**
 * Attacking types at least one team member resists (½x or ¼x — outright immunity is
 * [teamImmunities], kept separate rather than folded in here) — the per-member counterpart of
 * [sharedWeaknesses] on the "good news" side. Motivated by the same gap as [teamImmunities]/
 * [teamQuadWeaknesses]: adding Kingdra (Water/Dragon) to an all-Fire preset team resists Water
 * (½x from its own Water typing), a real defensive gain the majority-based rule can't see from one
 * new member alone.
 *
 * [defensiveMatrix] is keyed attackingType -> memberName -> multiplier, same shape [sharedWeaknesses]
 * reads.
 */
fun teamResistances(defensiveMatrix: Map<String, Map<String, Double>>, memberNames: Collection<String>): List<String> {
    if (memberNames.isEmpty()) return emptyList()
    return TypeIds.standardTypeNames.filter { type ->
        val row = defensiveMatrix[type].orEmpty()
        memberNames.any { val m = row[it] ?: 1.0; m > 0.0 && m < 1.0 }
    }
}

/**
 * Attacking types at least one team member is quadruple-weak (×4) to — the severe individual
 * counterpart of [teamImmunities]: worth flagging as a liability even when only one member carries
 * it, same as a lone immunity is worth flagging as an asset (see the Toedscool/Ice example that
 * motivated this).
 *
 * [defensiveMatrix] is keyed attackingType -> memberName -> multiplier, same shape [sharedWeaknesses]
 * reads.
 */
fun teamQuadWeaknesses(defensiveMatrix: Map<String, Map<String, Double>>, memberNames: Collection<String>): List<String> {
    if (memberNames.isEmpty()) return emptyList()
    return TypeIds.standardTypeNames.filter { type ->
        val row = defensiveMatrix[type].orEmpty()
        memberNames.any { abs((row[it] ?: 1.0) - 4.0) < EPSILON }
    }
}

data class MatchupBucket(@param:StringRes val labelRes: Int, val multiplier: Double, val types: List<String>)

// "×" + vulgar fractions to match the notation TeamScreen's matrix already uses ("×4", "×½"...)
// — this used to read "x4"/"x1/2" here but "×2"/"×½" there, an inconsistency for the exact same
// concept shown on two screens of the same app.
private val BUCKET_ORDER = listOf(
    4.0 to R.string.detail_matchup_weak_x4,
    2.0 to R.string.detail_matchup_weak_x2,
    0.5 to R.string.detail_matchup_resists_half,
    0.25 to R.string.detail_matchup_resists_quarter,
    0.0 to R.string.detail_matchup_immune
)

// The multipliers actually computed today are all products of exact powers of two (2.0, 0.5, 0.0),
// which IEEE 754 represents and multiplies with zero rounding error — so exact `==` has never
// actually misfired yet. It's still the wrong tool: a future contributor adding, say, a 1.5x
// weather/terrain modifier or any other non-power-of-two factor would silently and confusingly
// break bucketing, since two mathematically-equal multipliers reached via different type
// combinations could then land a bit apart.
private const val EPSILON = 1e-9

/** F93 follow-up — Terastallizing fully replaces a Pokémon's typing, so a weakness/resistance
 *  that held under [baseMatchups] but is neutral under [effectiveMatchups] simply vanishes from
 *  the latter and wouldn't render at all if passed alone to [bucketizeMatchups]. Merges [base]'s
 *  value back in for any such entry (so it still lands in its original bucket) and reports its
 *  name in the second half of the pair, for the caller to render struck through. An entry whose
 *  multiplier merely changes between two notable values (e.g. weak becomes resisted) is not
 *  "removed" — [effectiveMatchups]' own value is kept for it, unflagged. [baseMatchups] empty
 *  (no Tera preview active) means nothing to diff against: returns [effectiveMatchups] unchanged
 *  with an empty removed set. */
fun matchupsForDisplay(
    effectiveMatchups: Map<String, Double>,
    baseMatchups: Map<String, Double>
): Pair<Map<String, Double>, Set<String>> {
    if (baseMatchups.isEmpty()) return effectiveMatchups to emptySet()
    val removed = baseMatchups.filterValues { abs(it - 1.0) > EPSILON }.keys
        .filter { abs((effectiveMatchups[it] ?: 1.0) - 1.0) < EPSILON }
        .toSet()
    val display = effectiveMatchups + removed.associateWith { baseMatchups.getValue(it) }
    return display to removed
}

/** Groups a defensive-multiplier map into display buckets, skipping neutral (x1) types. */
fun bucketizeMatchups(multipliers: Map<String, Double>): List<MatchupBucket> =
    BUCKET_ORDER.mapNotNull { (multiplier, labelRes) ->
        val types = multipliers.filterValues { abs(it - multiplier) < EPSILON }.keys.sorted()
        if (types.isEmpty()) null else MatchupBucket(labelRes, multiplier, types)
    }

/** F90 follow-up — ranks each candidate Tera type by how well it resolves [currentMatchups]'
 *  weaknesses (any attacking type at more than neutral, i.e. x2 or x4), for sorting the Tera-type
 *  picker best-first instead of alphabetically. Per weakness, weighted x2 for a x4 weakness and
 *  x1 for a x2 weakness, a candidate scores +2 for becoming immune to that attacking type, +1 for
 *  resisting it, 0 for staying neutral, -1 for still being weak to it — so a candidate that fixes
 *  more, and more severe, weaknesses ranks higher. Types with no weaknesses in [currentMatchups]
 *  (or with only resisted/neutral entries) score 0 for every candidate, since there's nothing to
 *  resolve. Sorted descending by score; ties keep [candidateTypeDetails]' own iteration order. */
fun rankTeraTypes(
    currentMatchups: Map<String, Double>,
    candidateTypeDetails: Map<String, TypeDetailDto>
): List<Pair<String, Int>> {
    val weaknesses = currentMatchups.filterValues { it > 1.0 + EPSILON }
    return candidateTypeDetails.map { (candidateName, detail) ->
        val candidateProfile = computeDefensiveMultipliers(listOf(detail))
        val score = weaknesses.entries.sumOf { (attackingType, originalMultiplier) ->
            val weight = if (originalMultiplier >= 4.0 - EPSILON) 2 else 1
            val newMultiplier = candidateProfile[attackingType] ?: 1.0
            val points = when {
                newMultiplier < EPSILON -> 2
                newMultiplier < 1.0 - EPSILON -> 1
                newMultiplier < 1.0 + EPSILON -> 0
                else -> -1
            }
            points * weight
        }
        candidateName to score
    }.sortedByDescending { it.second }
}
