package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.repository.PokedexRepositoryApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** matrix[typeName][memberName] = defensive multiplier against that attacking type. offensiveMatrix
 *  mirrors it: offensiveMatrix[defendingType][memberName] = the best multiplier that member can
 *  *deal* to that type, across every attacking type it has access to. */
data class TeamMatrixResult(
    val defensive: Map<String, Map<String, Double>>,
    val offensive: Map<String, Map<String, Double>>
)

/** One member's raw inputs, gathered concurrently before the two matrices are assembled. */
private data class MemberMatchups(
    val name: String,
    val defensive: Map<String, Double>,
    val stabTypes: List<String>,
    val moveNames: List<String>
)

/** PokeAPI's damage_class for moves that deal no damage. */
private const val STATUS_DAMAGE_CLASS = "status"

/** Full movepool-based defensive/offensive matrix for [members] — extracted out of
 *  `TeamViewModel.computeMatrix()` (issue #2) so `PokedexDetailViewModel` can call the exact
 *  same logic for a hypothetical roster without depending on `TeamViewModel`. Caller is expected to
 *  wrap this in its own `supervisorScope`/try-catch for cancellation and error handling, same as
 *  `TeamViewModel.computeMatrix()` does. */
suspend fun computeTeamMatrices(
    repository: PokedexRepositoryApi,
    members: List<NamedApiResource>
): TeamMatrixResult = supervisorScope {
    // One bulk, already-cached lookup for the whole app rather than one call per
    // move: a team's six movepools run to well over a thousand entries between them.
    val moveInfoDeferred = async { repository.getAllMoveInfo() }

    // Every member is independent of every other, and every type detail lookup
    // is independent too — sequentially this was up to 18 round trips (6
    // members x up to 3 calls each) before the matrix could render at all.
    val memberResults = members.map { member ->
        async {
            val types = repository.getPokemonTypes(member.name)
            val typeDetails = types.map { async { repository.getTypeDetail(it) } }.awaitAll()
            // Same cache entry as getPokemonTypes above, so this is free.
            val moveNames = repository.getPokemonLevelUpMoveNames(member.name)
            MemberMatchups(
                name = member.name,
                defensive = computeDefensiveMultipliers(typeDetails),
                stabTypes = types,
                moveNames = moveNames
            )
        }
    }.awaitAll()

    val moveInfo = moveInfoDeferred.await()

    // What each member can attack with: its own types, plus the type of every
    // *damaging* move it can learn. Status moves are excluded — Thunder Wave being
    // Electric says nothing about whether this pokemon can dent a Water type.
    val attackingTypesByMember = memberResults.associate { member ->
        val fromMoves = member.moveNames.mapNotNull { moveName ->
            moveInfo[moveName]?.takeIf { it.damageClass != STATUS_DAMAGE_CLASS }?.type
        }
        member.name to (member.stabTypes + fromMoves).toSet()
    }

    val offensiveByType = attackingTypesByMember.values.flatten().distinct()
        .map { type -> async { type to computeOffensiveMultipliers(repository.getTypeDetail(type)) } }
        .awaitAll().toMap()

    val defensive = mutableMapOf<String, MutableMap<String, Double>>()
    val offensive = mutableMapOf<String, MutableMap<String, Double>>()
    TypeIds.standardTypeNames.forEach {
        defensive[it] = mutableMapOf()
        offensive[it] = mutableMapOf()
    }
    memberResults.forEach { member ->
        member.defensive.forEach { (attackType, multiplier) ->
            defensive.getOrPut(attackType) { mutableMapOf() }[member.name] = multiplier
        }
        val best = bestOffensiveMultipliers(
            attackingTypesByMember[member.name].orEmpty(),
            offensiveByType
        )
        best.forEach { (defendingType, multiplier) ->
            offensive.getOrPut(defendingType) { mutableMapOf() }[member.name] = multiplier
        }
    }
    TeamMatrixResult(defensive, offensive)
}
