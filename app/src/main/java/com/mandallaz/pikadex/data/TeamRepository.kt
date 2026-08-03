package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Roster of up to 6 pokemon the user is building a team with, persisted across app restarts —
 *  it used to be in-memory only, so force-closing the app (or the process just getting killed in
 *  the background, routine on Android) silently wiped a team the user had spent time building.
 *  Backed by plain SharedPreferences like [FavoritesRepository]; order matters here (unlike the
 *  favorites set) so members are stored as a single delimited "name|id" string rather than an
 *  unordered string set. Must be initialized once via [init] before use (done in the Application
 *  class, since a Context is needed to open the prefs file). */
object TeamRepository {
    const val MAX_SIZE = 6

    private const val PREFS_NAME = "team"
    private const val KEY_MEMBERS = "members"
    private const val ENTRY_DELIMITER = ","
    private const val FIELD_DELIMITER = "|"

    private var prefs: SharedPreferences? = null
    private val _team = MutableStateFlow<List<NamedApiResource>>(emptyList())
    val team: StateFlow<List<NamedApiResource>> = _team.asStateFlow()

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        _team.value = decode(sharedPrefs.getString(KEY_MEMBERS, null))
    }

    private fun decode(raw: String?): List<NamedApiResource> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ENTRY_DELIMITER).mapNotNull { entry ->
            val (name, id) = entry.split(FIELD_DELIMITER).takeIf { it.size == 2 } ?: return@mapNotNull null
            NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")
        }
    }

    private fun persist() {
        val encoded = _team.value.joinToString(ENTRY_DELIMITER) { "${it.name}$FIELD_DELIMITER${it.id ?: 0}" }
        prefs?.edit { putString(KEY_MEMBERS, encoded) }
    }

    fun isFull(): Boolean = _team.value.size >= MAX_SIZE

    fun contains(name: String): Boolean = _team.value.any { it.name == name }

    /** Returns false if the team is already full or the pokemon is already on the team. */
    fun add(pokemon: NamedApiResource): Boolean {
        if (isFull() || contains(pokemon.name)) return false
        _team.value = _team.value + pokemon
        persist()
        return true
    }

    /** Swaps the whole roster in one shot (loading a preset team), so subscribers recompute once
     *  instead of once per member the way six add() calls would. Trimmed to [MAX_SIZE] defensively:
     *  the invariant "a team never exceeds MAX_SIZE" belongs here, not in every caller. */
    fun replaceAll(pokemon: List<NamedApiResource>) {
        _team.value = pokemon.distinctBy { it.name }.take(MAX_SIZE)
        persist()
    }

    fun remove(pokemon: NamedApiResource) {
        _team.value = _team.value.filterNot { it.name == pokemon.name }
        persist()
    }

    fun toggle(pokemon: NamedApiResource): Boolean {
        return if (contains(pokemon.name)) {
            remove(pokemon)
            true
        } else {
            add(pokemon)
        }
    }
}
