package com.mandallaz.pikadex.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Exercises [TeamRepository] against a real (Robolectric-backed) SharedPreferences instance — the
 * only prior coverage was the pure free functions ([persistableMembers]/[resolveStoredTeamName]);
 * the singleton's actual add/remove/multi-team/persistence behavior had none. Each test method gets
 * a fresh Robolectric sandbox (a fresh app + prefs file), so [TeamRepository.init] here never reads
 * another test's leftover state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TeamRepositoryLifecycleTest {

    private lateinit var context: Context

    private fun resource(name: String, id: Int) = NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")

    // Doesn't call TeamRepository.init() here: the legacy-migration test below needs to seed
    // SharedPreferences *before* the first init() call, so every test triggers init() itself,
    // once its own preconditions (if any) are in place. The "team" prefs file itself persists
    // across test methods in the same Robolectric run (its backing store isn't reset per method
    // the way the object's own in-memory state effectively is via a fresh init() call each time),
    // so it's cleared explicitly here rather than relying on Robolectric to sandbox it away.
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("team", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `a fresh install starts with one empty, unnamed team`() {
        TeamRepository.init(context)
        assertEquals(emptyList<NamedApiResource>(), TeamRepository.team.value)
        assertEquals(listOf(TeamSlot(1, null, 0)), TeamRepository.teams.value)
        assertEquals(1, TeamRepository.activeTeamId.value)
    }

    @Test
    fun `add appends a pokemon and persists across a re-init`() {
        TeamRepository.init(context)
        TeamRepository.add(resource("pikachu", 25))
        assertEquals(listOf(resource("pikachu", 25)), TeamRepository.team.value)

        TeamRepository.init(context)
        assertEquals(listOf(resource("pikachu", 25)), TeamRepository.team.value)
    }

    @Test
    fun `add rejects a duplicate and a team at MAX_SIZE`() {
        TeamRepository.init(context)
        assertTrue(TeamRepository.add(resource("pikachu", 25)))
        assertFalse(TeamRepository.add(resource("pikachu", 25)))

        repeat(5) { i -> TeamRepository.add(resource("mon$i", i + 100)) }
        assertTrue(TeamRepository.isFull())
        assertFalse(TeamRepository.add(resource("overflow", 999)))
    }

    @Test
    fun `remove drops a member and persists`() {
        TeamRepository.init(context)
        TeamRepository.add(resource("pikachu", 25))
        TeamRepository.add(resource("bulbasaur", 1))
        TeamRepository.remove(resource("pikachu", 25))
        assertEquals(listOf(resource("bulbasaur", 1)), TeamRepository.team.value)

        TeamRepository.init(context)
        assertEquals(listOf(resource("bulbasaur", 1)), TeamRepository.team.value)
    }

    @Test
    fun `toggle adds when absent, removes when present, rejects when full`() {
        TeamRepository.init(context)
        assertEquals(TeamRepository.ToggleResult.Added, TeamRepository.toggle(resource("pikachu", 25)))
        assertEquals(TeamRepository.ToggleResult.Removed, TeamRepository.toggle(resource("pikachu", 25)))

        repeat(6) { i -> TeamRepository.add(resource("mon$i", i + 100)) }
        assertEquals(TeamRepository.ToggleResult.RejectedTeamFull, TeamRepository.toggle(resource("newcomer", 999)))
    }

    @Test
    fun `replaceAll swaps the whole roster, dedupes, and trims to MAX_SIZE`() {
        TeamRepository.init(context)
        val oversized = (1..8).map { resource("mon$it", it) } + resource("mon1", 1)
        TeamRepository.replaceAll(oversized)
        assertEquals(TeamRepository.MAX_SIZE, TeamRepository.team.value.size)
        assertEquals((1..6).map { resource("mon$it", it) }, TeamRepository.team.value)
    }

    @Test
    fun `createTeam adds a new slot without switching the active team`() {
        TeamRepository.init(context)
        val newId = TeamRepository.createTeam("Second Squad")
        assertEquals(1, TeamRepository.activeTeamId.value)
        assertTrue(TeamRepository.teams.value.any { it.id == newId && it.name == "Second Squad" })
    }

    @Test
    fun `setActiveTeam switches the visible roster to the target slot's own members`() {
        TeamRepository.init(context)
        TeamRepository.add(resource("pikachu", 25))
        val secondId = TeamRepository.createTeam("Second Squad")

        TeamRepository.setActiveTeam(secondId)
        assertEquals(emptyList<NamedApiResource>(), TeamRepository.team.value)
        assertEquals(secondId, TeamRepository.activeTeamId.value)

        TeamRepository.add(resource("bulbasaur", 1))
        TeamRepository.setActiveTeam(1)
        assertEquals(listOf(resource("pikachu", 25)), TeamRepository.team.value)
    }

    @Test
    fun `setActiveTeam ignores an unknown id`() {
        TeamRepository.init(context)
        TeamRepository.setActiveTeam(999)
        assertEquals(1, TeamRepository.activeTeamId.value)
    }

    @Test
    fun `renameTeam updates the slot's name but ignores a blank name`() {
        TeamRepository.init(context)
        TeamRepository.renameTeam(1, "My Squad")
        assertEquals("My Squad", TeamRepository.teams.value.first { it.id == 1 }.name)

        TeamRepository.renameTeam(1, "   ")
        assertEquals("My Squad", TeamRepository.teams.value.first { it.id == 1 }.name)
    }

    @Test
    fun `deleteTeam removes a non-active slot`() {
        TeamRepository.init(context)
        val secondId = TeamRepository.createTeam("Second Squad")
        TeamRepository.deleteTeam(secondId)
        assertFalse(TeamRepository.teams.value.any { it.id == secondId })
    }

    @Test
    fun `deleteTeam falls back the active team to a remaining slot when deleting the active one`() {
        TeamRepository.init(context)
        val secondId = TeamRepository.createTeam("Second Squad")
        TeamRepository.setActiveTeam(secondId)
        TeamRepository.deleteTeam(secondId)
        assertEquals(1, TeamRepository.activeTeamId.value)
    }

    @Test
    fun `deleteTeam is a no-op when it's the only remaining slot`() {
        TeamRepository.init(context)
        TeamRepository.deleteTeam(1)
        assertEquals(listOf(TeamSlot(1, null, 0)), TeamRepository.teams.value)
    }

    @Test
    fun `a pre-existing legacy single-team roster migrates into slot 1 on first init`() {
        val prefs = context.getSharedPreferences("team", Context.MODE_PRIVATE)
        prefs.edit().putString("members", "pikachu|25,bulbasaur|1").commit()

        TeamRepository.init(context)

        assertEquals(listOf(resource("pikachu", 25), resource("bulbasaur", 1)), TeamRepository.team.value)
        assertEquals(1, TeamRepository.activeTeamId.value)
    }
}
