package com.mandallaz.pikadex.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Pokédex list screen's current filtered/sorted order (names only), published so the detail
 * screen's swipe/chevron navigation (issue #7) can step through *that* order instead of
 * always the full master list — filtered to Fire types, swiping from a Fire Pokémon should only
 * ever land on another Fire Pokémon, not the next dex number regardless of type.
 *
 * Purely in-memory and session-scoped, unlike [FavoritesRepository]/[TeamRepository] — there's
 * nothing here worth surviving a process death, it's just whatever the list screen most recently
 * displayed. [PokedexDetailViewModel][com.mandallaz.pikadex.ui.detail.PokedexDetailViewModel]
 * falls back to the master list's own order when the entered Pokémon isn't in [displayedNames] at
 * all — a different entry point (an evolution chain tap, Compare, a team member chip) or this
 * being empty before the list screen has loaded once this session.
 */
object PokedexListContext {
    private val _displayedNames = MutableStateFlow<List<String>>(emptyList())
    val displayedNames: StateFlow<List<String>> = _displayedNames.asStateFlow()

    fun update(names: List<String>) {
        _displayedNames.value = names
    }
}
