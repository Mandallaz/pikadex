package com.mandallaz.pikadex.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted set of favorite pokemon names, remembered across app restarts (unlike [TeamRepository],
 * which is an in-memory, per-session team of up to 6). Backed by plain SharedPreferences since it's
 * just a set of strings — no need for Room here. Must be initialized once via [init] before use
 * (done in the Application class, since a Context is needed to open the prefs file). */
object FavoritesRepository {
    private const val PREFS_NAME = "favorites"
    private const val KEY_FAVORITE_NAMES = "favorite_names"

    private val store = PrefsStore(emptySet<String>())
    val favorites: StateFlow<Set<String>> = store.flow.asStateFlow()

    fun init(context: Context) {
        store.init(context, PREFS_NAME, KEY_FAVORITE_NAMES, emptySet())
    }

    fun isFavorite(name: String): Boolean = favorites.value.contains(name)

    fun toggle(name: String) {
        val updated = if (favorites.value.contains(name)) favorites.value - name else favorites.value + name
        store.set(updated)
    }
}
