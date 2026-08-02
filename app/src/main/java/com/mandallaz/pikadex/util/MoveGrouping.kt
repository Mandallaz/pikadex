package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.PokemonDto

data class LearnedMove(
    val moveName: String,
    val level: Int,
    val versionGroup: String
)

enum class MoveCategory(val apiMethodName: String, val label: String) {
    LEVEL_UP("level-up", "Level Up"),
    MACHINE("machine", "TM/HM"),
    EGG("egg", "Breeding"),
    TUTOR("tutor", "Tutor")
}

/**
 * For a given learn method, only keeps the entry from the most recent version_group available for
 * each move (a pokemon can appear in several games with different movesets; we show the most
 * recent game's data to avoid duplicates).
 */
fun PokemonDto.movesForCategory(category: MoveCategory): List<LearnedMove> {
    val learned = moves.mapNotNull { entry ->
        val best = entry.versionGroupDetails
            .filter { it.moveLearnMethod.name == category.apiMethodName }
            .maxByOrNull { VersionGroups.rank(it.versionGroup.name) }
        best?.let {
            LearnedMove(
                moveName = entry.move.name,
                level = it.levelLearnedAt,
                versionGroup = it.versionGroup.name
            )
        }
    }
    return when (category) {
        MoveCategory.LEVEL_UP -> learned.sortedBy { it.level }
        else -> learned.sortedBy { it.moveName }
    }
}
