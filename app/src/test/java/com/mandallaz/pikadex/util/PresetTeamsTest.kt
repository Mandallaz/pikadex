package com.mandallaz.pikadex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B16 — [PresetTeams.ALL] is ~350 hand-typed PokeAPI species names with no structural check at
 * all. [TeamViewModel.loadPreset] resolves each name with `mapNotNull { byName[it] }` — a typo
 * doesn't fail loudly, it just silently drops that one Pokémon from the roster, and the only guard
 * (`resolved.isEmpty()`) needs *every* name in a team to be wrong before it fires. This doesn't
 * catch every possible typo (a mistyped-but-still-valid species name would still resolve), but it
 * catches the class of mistake that PokeAPI names are especially prone to: stray spaces, wrong
 * case, and copy-paste duplicates.
 */
class PresetTeamsTest {

    // Every game label PresetTeams.kt's private constants can produce — kept as an independent
    // literal list (not a reference to those constants, which are private) so a typo'd constant
    // *value* itself would also be caught, not just a typo in a call site.
    private val knownGameLabels = setOf(
        "Red / Blue / Yellow",
        "Gold / Silver / Crystal",
        "Ruby / Sapphire",
        "Emerald",
        "Diamond / Pearl / Platinum",
        "Black / White",
        "Black 2 / White 2",
        "X / Y",
        "Sun / Moon",
        "Sword / Shield",
        "Sword",
        "Shield",
        "Scarlet / Violet"
    )

    @Test
    fun `every roster has at least one and at most six members`() {
        PresetTeams.ALL.forEach { team ->
            assertTrue(
                "${team.trainer} (${team.game}) has ${team.pokemon.size} members",
                team.pokemon.size in 1..6
            )
        }
    }

    @Test
    fun `every species name is lowercase`() {
        PresetTeams.ALL.forEach { team ->
            team.pokemon.forEach { name ->
                assertEquals("${team.trainer}: '$name' should be lowercase", name.lowercase(), name)
            }
        }
    }

    @Test
    fun `no species name contains whitespace`() {
        PresetTeams.ALL.forEach { team ->
            team.pokemon.forEach { name ->
                assertTrue("${team.trainer}: '$name' contains whitespace", name.none { it.isWhitespace() })
            }
        }
    }

    // PokeAPI species names are lowercase-hyphenated ASCII (e.g. "mr-mime", "meowstic-male") — no
    // other punctuation is ever valid, so anything else is a typo (a stray apostrophe, an accented
    // character copy-pasted from a display name instead of the raw API name).
    @Test
    fun `every species name is only lowercase letters, digits and hyphens`() {
        val validName = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        PresetTeams.ALL.forEach { team ->
            team.pokemon.forEach { name ->
                assertTrue("${team.trainer}: '$name' has an unexpected character", validName.matches(name))
            }
        }
    }

    @Test
    fun `no roster repeats the same species twice`() {
        PresetTeams.ALL.forEach { team ->
            assertEquals(
                "${team.trainer} (${team.game}) has a duplicate species: ${team.pokemon}",
                team.pokemon.size,
                team.pokemon.distinct().size
            )
        }
    }

    @Test
    fun `every team's game is one of the known release-group labels`() {
        PresetTeams.ALL.forEach { team ->
            assertTrue("${team.trainer}: unrecognized game label '${team.game}'", team.game in knownGameLabels)
        }
    }

    @Test
    fun `every trainer name is non-blank`() {
        PresetTeams.ALL.forEach { team ->
            assertTrue(team.trainer.isNotBlank())
        }
    }

    // Not flatMap-equal to ALL directly: games interleave in ALL (e.g. Gen 8's "Sword", "Shield"
    // and "Sword / Shield" entries alternate rather than sitting in contiguous blocks), so
    // groupBy legitimately reorders across game boundaries — it's each game's own internal order,
    // and the total membership, that must be preserved.
    @Test
    fun `BY_GAME groups every team from ALL exactly once, preserving each game's own order`() {
        val fromByGame = PresetTeams.BY_GAME.flatMap { (_, teams) -> teams }
        assertEquals(PresetTeams.ALL.toSet(), fromByGame.toSet())
        assertEquals(PresetTeams.ALL.size, fromByGame.size)

        PresetTeams.ALL.groupBy { it.game }.forEach { (game, expectedOrder) ->
            val actualOrder = PresetTeams.BY_GAME.single { it.first == game }.second
            assertEquals(game, expectedOrder, actualOrder)
        }
    }
}
