package com.mandallaz.pikadex.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [PrefsStore.init] and its typed (Boolean/String/Set<String>) overloads had no coverage — every
 * existing settings-singleton test (e.g. [PrefetchSettingsTest]) deliberately swaps in a
 * [FakeSharedPreferences] via reflection specifically to avoid needing a real `Context`, so `init`
 * itself (the actual `context.getSharedPreferences(...)` call and its read-back) never ran. Uses
 * Robolectric here instead, which supplies one without touching a real singleton's shared prefs
 * file.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrefsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `Boolean init falls back to the default when nothing was persisted`() {
        val store = PrefsStore(false)
        store.init(context, "prefs_store_test", "flag", default = true)
        assertEquals(true, store.flow.value)
    }

    @Test
    fun `Boolean init reads back a previously persisted value`() {
        context.getSharedPreferences("prefs_store_test_bool", Context.MODE_PRIVATE)
            .edit().putBoolean("flag", true).commit()

        val store = PrefsStore(false)
        store.init(context, "prefs_store_test_bool", "flag", default = false)

        assertEquals(true, store.flow.value)
    }

    @Test
    fun `Boolean set persists the new value and updates the flow`() {
        val store = PrefsStore(false)
        store.init(context, "prefs_store_test_bool_set", "flag", default = false)

        store.set(true)

        assertEquals(true, store.flow.value)
        val reread = context.getSharedPreferences("prefs_store_test_bool_set", Context.MODE_PRIVATE)
            .getBoolean("flag", false)
        assertEquals(true, reread)
    }

    @Test
    fun `String init reads back a previously persisted value`() {
        context.getSharedPreferences("prefs_store_test_string", Context.MODE_PRIVATE)
            .edit().putString("language", "fr").commit()

        val store = PrefsStore("en")
        store.init(context, "prefs_store_test_string", "language", default = "en")

        assertEquals("fr", store.flow.value)
    }

    @Test
    fun `String set persists the new value`() {
        val store = PrefsStore("en")
        store.init(context, "prefs_store_test_string_set", "language", default = "en")

        store.set("de")

        assertEquals("de", store.flow.value)
        val reread = context.getSharedPreferences("prefs_store_test_string_set", Context.MODE_PRIVATE)
            .getString("language", "en")
        assertEquals("de", reread)
    }

    @Test
    fun `Set of String init reads back a previously persisted value`() {
        context.getSharedPreferences("prefs_store_test_set", Context.MODE_PRIVATE)
            .edit().putStringSet("tags", setOf("a", "b")).commit()

        val store = PrefsStore(emptySet<String>())
        store.init(context, "prefs_store_test_set", "tags", default = emptySet())

        assertEquals(setOf("a", "b"), store.flow.value)
    }

    @Test
    fun `set before init is called only updates the flow, without crashing`() {
        val store = PrefsStore(false)
        store.set(true)
        assertEquals(true, store.flow.value)
    }
}
