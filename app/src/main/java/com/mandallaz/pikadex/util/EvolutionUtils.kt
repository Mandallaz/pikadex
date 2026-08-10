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
 */
fun evolutionPaths(link: ChainLink): List<List<EvolutionStage>> {
    val currentStage = EvolutionStage(link.species.name, link.species.id ?: 0, null)
    if (link.evolvesTo.isEmpty()) return listOf(listOf(currentStage))

    return link.evolvesTo.flatMap { next ->
        val condition = describeEvolutionDetail(next.evolutionDetails.firstOrNull())
        evolutionPaths(next).map { restOfPath ->
            val restWithCondition = restOfPath.mapIndexed { index, stage ->
                if (index == 0) stage.copy(conditionLabel = condition) else stage
            }
            listOf(currentStage) + restWithCondition
        }
    }
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
