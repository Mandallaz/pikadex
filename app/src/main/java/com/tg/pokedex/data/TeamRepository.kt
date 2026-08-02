package com.tg.pokedex.data

import com.tg.pokedex.data.remote.dto.NamedApiResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory roster of up to 6 pokemon the user is building a team with. Lives for the process
 * lifetime only (no persistence) — simple global state, same spirit as [AppContainer]. */
object TeamRepository {
    const val MAX_SIZE = 6

    private val _team = MutableStateFlow<List<NamedApiResource>>(emptyList())
    val team: StateFlow<List<NamedApiResource>> = _team.asStateFlow()

    fun isFull(): Boolean = _team.value.size >= MAX_SIZE

    fun contains(name: String): Boolean = _team.value.any { it.name == name }

    /** Returns false if the team is already full or the pokemon is already on the team. */
    fun add(pokemon: NamedApiResource): Boolean {
        if (isFull() || contains(pokemon.name)) return false
        _team.value = _team.value + pokemon
        return true
    }

    fun remove(pokemon: NamedApiResource) {
        _team.value = _team.value.filterNot { it.name == pokemon.name }
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
