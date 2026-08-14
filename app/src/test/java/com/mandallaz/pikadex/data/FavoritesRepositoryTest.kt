package com.mandallaz.pikadex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM test for the refactored [FavoritesRepository] ensuring its toggle and persistent behavior.
 */
class FavoritesRepositoryTest {

    private fun swapInFakePrefs(fake: FakeSharedPreferences) {
        val field = FavoritesRepository::class.java.getDeclaredField("store")
        field.isAccessible = true
        val store = field.get(FavoritesRepository) as PrefsStore<Set<String>>
        store.prefs = fake
        store.key = "favorite_names"
        store.encode = { key, value -> putStringSet(key, value.toMutableSet()) }
    }

    @Test
    fun `favorites defaults to emptySet before init`() {
        assertTrue(FavoritesRepository.favorites.value.isEmpty())
    }

    @Test
    fun `toggle adds and removes favorites and persists them`() {
        val fake = FakeSharedPreferences()
        swapInFakePrefs(fake)

        assertFalse(FavoritesRepository.isFavorite("Pikachu"))
        FavoritesRepository.toggle("Pikachu")
        assertTrue(FavoritesRepository.isFavorite("Pikachu"))
        assertEquals(setOf("Pikachu"), fake.getStringSet("favorite_names", null))

        FavoritesRepository.toggle("Pikachu")
        assertFalse(FavoritesRepository.isFavorite("Pikachu"))
        assertEquals(emptySet<String>(), fake.getStringSet("favorite_names", null))
    }
}
