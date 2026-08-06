package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.util.decodeMembers
import com.mandallaz.pikadex.util.decodeTeamIds
import com.mandallaz.pikadex.util.encodeMembers
import com.mandallaz.pikadex.util.encodeTeamIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One named team slot's metadata — not its members, which live in [TeamRepository.team] only for
 *  the *active* slot (every other slot's roster is read from storage on demand, e.g. when
 *  switching to it). [size] is shown in the slot picker without needing to switch to a team just
 *  to see how full it is. */
data class TeamSlot(val id: Int, val name: String, val size: Int)

/**
 * One or more named rosters of up to [MAX_SIZE] pokemon, persisted across app restarts. Originally
 * a single unnamed roster (a single "members" key) — multiple teams were added on top of that
 * without disturbing it: the legacy key is migrated into slot 1 ("My Team") on first run under the
 * new scheme and then left alone (never deleted, in case something ever needs to fall back to it).
 *
 * [team]/[isFull]/[contains]/[add]/[remove]/[replaceAll]/[toggle] all still operate on the *active*
 * team specifically, unchanged in meaning from before multiple teams existed — every existing
 * caller (built when there was only ever one team) keeps compiling and behaving the same way.
 *
 * Must be initialized once via [init] before use (done in the Application class, since a Context
 * is needed to open the prefs file).
 */
object TeamRepository {
    const val MAX_SIZE = 6

    private const val PREFS_NAME = "team"
    private const val KEY_MEMBERS_LEGACY = "members"
    private const val KEY_TEAM_IDS = "team_ids"
    private const val KEY_ACTIVE_TEAM_ID = "active_team_id"
    private const val KEY_NEXT_TEAM_ID = "next_team_id"
    private const val PREFIX_TEAM_NAME = "team_name_"
    private const val PREFIX_MEMBERS = "members_"
    private const val DEFAULT_TEAM_NAME = "My Team"
    private const val NEW_TEAM_NAME = "New Team"

    private var prefs: SharedPreferences? = null

    private val _team = MutableStateFlow<List<NamedApiResource>>(emptyList())
    val team: StateFlow<List<NamedApiResource>> = _team.asStateFlow()

    private val _teams = MutableStateFlow<List<TeamSlot>>(emptyList())
    val teams: StateFlow<List<TeamSlot>> = _teams.asStateFlow()

    private val _activeTeamId = MutableStateFlow(1)
    val activeTeamId: StateFlow<Int> = _activeTeamId.asStateFlow()

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        migrateLegacySingleTeamIfNeeded(sharedPrefs)
        reload(sharedPrefs)
    }

    /** Runs exactly once, the first time this app version's storage scheme is read — detected by
     *  the absence of [KEY_TEAM_IDS], which every install under the new scheme always has. The
     *  legacy `members` key (if any; a fresh install has none) becomes slot 1, "My Team". */
    private fun migrateLegacySingleTeamIfNeeded(p: SharedPreferences) {
        if (p.contains(KEY_TEAM_IDS)) return
        p.edit {
            putString(KEY_TEAM_IDS, encodeTeamIds(listOf(1)))
            putString(PREFIX_TEAM_NAME + 1, DEFAULT_TEAM_NAME)
            putString(PREFIX_MEMBERS + 1, p.getString(KEY_MEMBERS_LEGACY, null) ?: "")
            putInt(KEY_ACTIVE_TEAM_ID, 1)
            putInt(KEY_NEXT_TEAM_ID, 2)
        }
    }

    private fun reload(p: SharedPreferences) {
        val ids = decodeTeamIds(p.getString(KEY_TEAM_IDS, null)).ifEmpty { listOf(1) }
        _teams.value = ids.map { id -> TeamSlot(id, teamName(p, id), decodeMembers(p.getString(PREFIX_MEMBERS + id, null)).size) }
        val storedActive = p.getInt(KEY_ACTIVE_TEAM_ID, ids.first())
        val active = if (storedActive in ids) storedActive else ids.first()
        _activeTeamId.value = active
        _team.value = decodeMembers(p.getString(PREFIX_MEMBERS + active, null))
    }

    private fun teamName(p: SharedPreferences, id: Int): String = p.getString(PREFIX_TEAM_NAME + id, null) ?: DEFAULT_TEAM_NAME

    private fun persistActiveTeam() {
        val p = prefs ?: return
        val id = _activeTeamId.value
        p.edit { putString(PREFIX_MEMBERS + id, encodeMembers(persistableMembers(_team.value))) }
        _teams.value = _teams.value.map { if (it.id == id) it.copy(size = _team.value.size) else it }
    }

    fun isFull(): Boolean = _team.value.size >= MAX_SIZE

    fun contains(name: String): Boolean = _team.value.any { it.name == name }

    /** Returns false if the team is already full or the pokemon is already on the team. */
    fun add(pokemon: NamedApiResource): Boolean {
        if (isFull() || contains(pokemon.name)) return false
        _team.value = _team.value + pokemon
        persistActiveTeam()
        return true
    }

    /** Swaps the whole roster in one shot (loading a preset team), so subscribers recompute once
     *  instead of once per member the way six add() calls would. Trimmed to [MAX_SIZE] defensively:
     *  the invariant "a team never exceeds MAX_SIZE" belongs here, not in every caller. */
    fun replaceAll(pokemon: List<NamedApiResource>) {
        _team.value = pokemon.distinctBy { it.name }.take(MAX_SIZE)
        persistActiveTeam()
    }

    fun remove(pokemon: NamedApiResource) {
        _team.value = _team.value.filterNot { it.name == pokemon.name }
        persistActiveTeam()
    }

    /** What [toggle] actually did — a plain Boolean return couldn't distinguish "removed" from
     *  "added successfully", and "rejected, team full" collapsed onto the same `false` as a
     *  successful removal ever returning `false` would (it never does today, but nothing in the
     *  type said so). Every caller had already worked around this by pre-checking [isFull]
     *  themselves before calling [toggle] at all, duplicating that guard in three places. */
    sealed interface ToggleResult {
        data object Added : ToggleResult
        data object Removed : ToggleResult
        data object RejectedTeamFull : ToggleResult
    }

    fun toggle(pokemon: NamedApiResource): ToggleResult = when {
        contains(pokemon.name) -> {
            remove(pokemon)
            ToggleResult.Removed
        }
        isFull() -> ToggleResult.RejectedTeamFull
        else -> {
            add(pokemon)
            ToggleResult.Added
        }
    }

    /** Creates a new, empty team slot and returns its id — [setActiveTeam] it explicitly if it
     *  should become the one being edited; creating one doesn't switch to it on its own, since a
     *  caller might want to e.g. populate it with a preset before ever showing it as active. */
    fun createTeam(name: String = NEW_TEAM_NAME): Int {
        val p = prefs ?: return _activeTeamId.value
        val id = p.getInt(KEY_NEXT_TEAM_ID, 2)
        val ids = decodeTeamIds(p.getString(KEY_TEAM_IDS, null)).ifEmpty { listOf(1) } + id
        p.edit {
            putString(KEY_TEAM_IDS, encodeTeamIds(ids))
            putString(PREFIX_TEAM_NAME + id, name.ifBlank { NEW_TEAM_NAME })
            putString(PREFIX_MEMBERS + id, "")
            putInt(KEY_NEXT_TEAM_ID, id + 1)
        }
        _teams.value = _teams.value + TeamSlot(id, name.ifBlank { NEW_TEAM_NAME }, 0)
        return id
    }

    fun renameTeam(id: Int, name: String) {
        if (name.isBlank()) return
        val p = prefs ?: return
        p.edit { putString(PREFIX_TEAM_NAME + id, name) }
        _teams.value = _teams.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    /** No-ops if [id] is the only remaining team — a team roster can be emptied, but there must
     *  always be at least one slot to hold it, or "my active team" would have nowhere to point. */
    fun deleteTeam(id: Int) {
        val p = prefs ?: return
        val currentIds = _teams.value.map { it.id }
        if (currentIds.size <= 1 || id !in currentIds) return
        val remainingIds = currentIds - id
        p.edit {
            remove(PREFIX_TEAM_NAME + id)
            remove(PREFIX_MEMBERS + id)
            putString(KEY_TEAM_IDS, encodeTeamIds(remainingIds))
        }
        _teams.value = _teams.value.filterNot { it.id == id }
        if (_activeTeamId.value == id) {
            setActiveTeam(remainingIds.first())
        }
    }

    fun setActiveTeam(id: Int) {
        val p = prefs ?: return
        if (id !in _teams.value.map { it.id }) return
        p.edit { putInt(KEY_ACTIVE_TEAM_ID, id) }
        _activeTeamId.value = id
        _team.value = decodeMembers(p.getString(PREFIX_MEMBERS + id, null))
    }
}

/** The members of [team] actually worth writing to storage. An id-less resource has no numeric id
 *  to reconstruct a url from on the way back in — encoding it with the old `it.id ?: 0` fallback
 *  meant [TeamRepository.decode] read it back as a real (and wrong) id 0 next launch, instead of
 *  the id-less resource it actually was. A free function, not private logic inline in persist(),
 *  so it's testable without a real Context/SharedPreferences. */
internal fun persistableMembers(team: List<NamedApiResource>): List<NamedApiResource> =
    team.filter { it.id != null }
