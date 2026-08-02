package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted set of favorite pokemon names, remembered across app restarts (unlike [TeamRepository],
 * which is an in-memory, per-session team of up to 6). Backed by plain SharedPreferences since it's
 * just a set of strings — no need for Room here. Must be initialized once via [init] before use
 * (done in the Application class, since a Context is needed to open the prefs file). */
object FavoritesRepository {
    private const val PREFS_NAME = "favorites"
    private const val KEY_FAVORITE_NAMES = "favorite_names"

    private lateinit var prefs: SharedPreferences
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _favorites.value = prefs.getStringSet(KEY_FAVORITE_NAMES, emptySet()).orEmpty().toSet()
    }

    fun isFavorite(name: String): Boolean = _favorites.value.contains(name)

    fun toggle(name: String) {
        val updated = if (_favorites.value.contains(name)) _favorites.value - name else _favorites.value + name
        _favorites.value = updated
        prefs.edit { putStringSet(KEY_FAVORITE_NAMES, updated) }
    }
}
