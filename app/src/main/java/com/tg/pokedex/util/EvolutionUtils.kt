package com.tg.pokedex.util

import com.tg.pokedex.data.remote.dto.ChainLink
import com.tg.pokedex.data.remote.dto.EvolutionDetail

data class EvolutionStage(
    val speciesName: String,
    val id: Int,
    val conditionLabel: String?
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

fun describeEvolutionDetail(detail: EvolutionDetail?): String? {
    if (detail == null) return null
    val trigger = detail.trigger?.name
    return when {
        detail.minLevel != null -> "Level ${detail.minLevel}"
        detail.item != null -> "Item: ${detail.item.name.toDisplayName()}"
        trigger == "trade" -> "Trade" + (detail.heldItem?.let { " (${it.name.toDisplayName()})" } ?: "")
        detail.minHappiness != null -> "High friendship"
        detail.knownMove != null -> "Knows ${detail.knownMove.name.toDisplayName()}"
        detail.timeOfDay?.isNotBlank() == true -> "During ${detail.timeOfDay}"
        trigger != null -> trigger.toDisplayName()
        else -> null
    }
}
