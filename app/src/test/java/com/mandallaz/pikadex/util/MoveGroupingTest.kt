package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonMoveEntry
import com.mandallaz.pikadex.data.remote.dto.VersionGroupDetail
import com.mandallaz.pikadex.data.repository.fakePokemonDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B15 — [PokemonDto.movesForCategory] was one of the densest untested functions in the app: the
 * "most recent version group wins" de-duplication and the per-category sort are exactly the kind
 * of rule that regresses silently (a move shown twice, or an old game's level surfacing instead of
 * the current one).
 */
class MoveGroupingTest {

    private fun resource(name: String) = NamedApiResource(name, "https://pokeapi.co/api/v2/x/$name/")

    private fun detail(level: Int, method: String, versionGroup: String) = VersionGroupDetail(
        levelLearnedAt = level,
        moveLearnMethod = resource(method),
        versionGroup = resource(versionGroup)
    )

    @Test
    fun `level-up moves are sorted by level ascending`() {
        val pokemon = fakePokemonDto().copy(
            moves = listOf(
                PokemonMoveEntry(resource("tackle"), listOf(detail(1, "level-up", "scarlet-violet"))),
                PokemonMoveEntry(resource("growl"), listOf(detail(1, "level-up", "scarlet-violet"))),
                PokemonMoveEntry(resource("vine-whip"), listOf(detail(13, "level-up", "scarlet-violet")))
            )
        )

        val learned = pokemon.movesForCategory(MoveCategory.LEVEL_UP)

        assertEquals(listOf(1, 1, 13), learned.map { it.level })
    }

    @Test
    fun `non level-up moves are sorted alphabetically by name`() {
        val pokemon = fakePokemonDto().copy(
            moves = listOf(
                PokemonMoveEntry(resource("solar-beam"), listOf(detail(0, "machine", "scarlet-violet"))),
                PokemonMoveEntry(resource("dig"), listOf(detail(0, "machine", "scarlet-violet")))
            )
        )

        val learned = pokemon.movesForCategory(MoveCategory.MACHINE)

        assertEquals(listOf("dig", "solar-beam"), learned.map { it.moveName })
    }

    // The bug this guards against: a move appearing twice (once per game it was learnable in)
    // because the "most recent version group wins" rule picked the wrong — or every — entry.
    @Test
    fun `a move learnable in multiple version groups appears only once, from the most recent`() {
        val pokemon = fakePokemonDto().copy(
            moves = listOf(
                PokemonMoveEntry(
                    resource("tackle"),
                    listOf(
                        detail(level = 1, method = "level-up", versionGroup = "red-blue"),
                        detail(level = 5, method = "level-up", versionGroup = "scarlet-violet")
                    )
                )
            )
        )

        val learned = pokemon.movesForCategory(MoveCategory.LEVEL_UP)

        assertEquals(1, learned.size)
        assertEquals(5, learned.single().level)
        assertEquals("scarlet-violet", learned.single().versionGroup)
    }

    @Test
    fun `only entries matching the requested category's learn method are included`() {
        val pokemon = fakePokemonDto().copy(
            moves = listOf(
                PokemonMoveEntry(resource("tackle"), listOf(detail(1, "level-up", "scarlet-violet"))),
                PokemonMoveEntry(resource("thunderbolt"), listOf(detail(0, "machine", "scarlet-violet"))),
                PokemonMoveEntry(resource("counter"), listOf(detail(0, "egg", "scarlet-violet"))),
                PokemonMoveEntry(resource("mimic"), listOf(detail(0, "tutor", "scarlet-violet")))
            )
        )

        assertEquals(listOf("tackle"), pokemon.movesForCategory(MoveCategory.LEVEL_UP).map { it.moveName })
        assertEquals(listOf("thunderbolt"), pokemon.movesForCategory(MoveCategory.MACHINE).map { it.moveName })
        assertEquals(listOf("counter"), pokemon.movesForCategory(MoveCategory.EGG).map { it.moveName })
        assertEquals(listOf("mimic"), pokemon.movesForCategory(MoveCategory.TUTOR).map { it.moveName })
    }

    @Test
    fun `a pokemon with no moves for a category returns an empty list`() {
        val pokemon = fakePokemonDto().copy(
            moves = listOf(PokemonMoveEntry(resource("tackle"), listOf(detail(1, "level-up", "scarlet-violet"))))
        )

        assertEquals(emptyList<LearnedMove>(), pokemon.movesForCategory(MoveCategory.TUTOR))
    }

    @Test
    fun `an unrecognized version group is treated as the most recent, not dropped`() {
        // VersionGroups.rank() falls back to Int.MAX_VALUE for names it doesn't know — a future
        // game not yet added to that list must still win over a known older one, not disappear.
        val pokemon = fakePokemonDto().copy(
            moves = listOf(
                PokemonMoveEntry(
                    resource("tackle"),
                    listOf(
                        detail(level = 1, method = "level-up", versionGroup = "red-blue"),
                        detail(level = 3, method = "level-up", versionGroup = "some-future-game")
                    )
                )
            )
        )

        val learned = pokemon.movesForCategory(MoveCategory.LEVEL_UP)

        assertEquals(1, learned.size)
        assertEquals(3, learned.single().level)
        assertEquals("some-future-game", learned.single().versionGroup)
    }
}
