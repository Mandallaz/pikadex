package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.ChainLink
import com.mandallaz.pikadex.data.remote.dto.EvolutionDetail
import com.mandallaz.pikadex.ui.UiText

data class EvolutionStage(
    val speciesName: String,
    val id: Int,
    val conditionLabel: UiText?
)

/**
 * Flattens the evolution tree into a list of root -> leaf paths. Needed for branching evolutions
 * (e.g. Eevee) that can't be represented as a simple list.
 *
 * [viewedPokemonName] — B66 — the specific variety currently on screen (e.g. "corsola-galar"),
 * used to drop branches restricted to a *different* variety via [EvolutionDetail.baseForm] (e.g.
 * only Galarian Corsola evolves into Cursola; standard Corsola never evolves). Only applied to
 * this call's own direct branches, not recursively — a chain stacking two variety-restricted
 * evolutions back to back doesn't occur in practice, and recursing with the same name would risk
 * hiding a legitimate later-stage branch that happens to carry an unrelated base_form check.
 * `null` (the default) disables the filter entirely, e.g. for chains it's fetched independently of
 * any specific Pokémon on screen.
 */
fun evolutionPaths(link: ChainLink, viewedPokemonName: String? = null): List<List<EvolutionStage>> {
    val currentStage = EvolutionStage(link.species.name, link.species.id ?: 0, null)
    if (link.evolvesTo.isEmpty()) return listOf(listOf(currentStage))

    val applicableBranches = link.evolvesTo.filter { next -> isBranchApplicable(next.evolutionDetails, viewedPokemonName) }
    if (applicableBranches.isEmpty()) return listOf(listOf(currentStage))

    return applicableBranches.flatMap { next ->
        val condition = describeEvolutionDetail(selectDetail(next.evolutionDetails, viewedPokemonName))
        evolutionPaths(next, viewedPokemonName = null).map { restOfPath ->
            val restWithCondition = restOfPath.mapIndexed { index, stage ->
                if (index == 0) stage.copy(conditionLabel = condition) else stage
            }
            listOf(currentStage) + restWithCondition
        }
    }
}

/** A branch with no [EvolutionDetail.baseForm] restriction applies to every variety (the
 *  overwhelming majority of evolutions). A branch where *every* detail names a base_form applies
 *  only when [viewedPokemonName] matches one of them. */
private fun isBranchApplicable(details: List<EvolutionDetail>, viewedPokemonName: String?): Boolean {
    if (viewedPokemonName == null || details.isEmpty()) return true
    return details.any { it.baseForm == null || it.baseForm.name == viewedPokemonName }
}

/** B67 — a branch can carry multiple [EvolutionDetail]s, one per pre-evolution variety, with
 *  genuinely different conditions (e.g. Rockruff's day/night/dusk-Own-Tempo split into Lycanroc).
 *  A bare `firstOrNull()` always showed the first entry's condition regardless of which variety
 *  was actually on screen. Prefers the entry whose [EvolutionDetail.baseForm] matches
 *  [viewedPokemonName], falling back to the first entry when there's no name to match against or
 *  none of them do — same fallback [describeEvolutionDetail] already handles via a null detail. */
private fun selectDetail(details: List<EvolutionDetail>, viewedPokemonName: String?): EvolutionDetail? {
    if (viewedPokemonName != null) {
        details.firstOrNull { it.baseForm?.name == viewedPokemonName }?.let { return it }
    }
    return details.firstOrNull()
}

fun describeEvolutionDetail(detail: EvolutionDetail?): UiText? {
    if (detail == null) return null
    val trigger = detail.trigger?.name
    return when {
        detail.minLevel != null -> UiText(R.string.detail_evolution_condition_level, listOf(detail.minLevel))
        detail.item != null -> UiText(R.string.detail_evolution_condition_item, listOf(detail.item.name.toDisplayName()))
        trigger == "trade" -> detail.heldItem?.let {
            UiText(R.string.detail_evolution_condition_trade_with_item, listOf(it.name.toDisplayName()))
        } ?: UiText(R.string.detail_evolution_condition_trade)
        detail.minHappiness != null -> UiText(R.string.detail_evolution_condition_friendship)
        detail.knownMove != null -> UiText(R.string.detail_evolution_condition_move, listOf(detail.knownMove.name.toDisplayName()))
        detail.timeOfDay?.isNotBlank() == true -> UiText(R.string.detail_evolution_condition_time, listOf(detail.timeOfDay))
        trigger != null -> UiText(R.string.detail_evolution_condition_raw, listOf(trigger.toDisplayName()))
        else -> null
    }
}
