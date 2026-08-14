package com.mandallaz.pikadex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A small generic helper for SharedPreferences-backed settings objects,
 * exposing a [MutableStateFlow] of [T] and simplifying initialization and updates.
 */
class PrefsStore<T>(val default: T) {
    val flow = MutableStateFlow(default)

    var prefs: SharedPreferences? = null
    var key: String? = null
    var encode: (SharedPreferences.Editor.(String, T) -> Unit)? = null

    fun init(
        context: Context,
        name: String,
        key: String,
        default: T,
        encode: SharedPreferences.Editor.(String, T) -> Unit,
        decode: SharedPreferences.(String, T) -> T
    ) {
        this.key = key
        this.encode = encode
        val sharedPrefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        this.prefs = sharedPrefs
        flow.value = sharedPrefs.decode(key, default)
    }

    fun set(value: T) {
        flow.value = value
        val p = prefs ?: return
        val k = key ?: return
        val e = encode ?: return
        p.edit { e(k, value) }
    }
}

// Simple types overloads for easier integration with standard preference types.
fun PrefsStore<Boolean>.init(context: Context, name: String, key: String, default: Boolean) {
    init(
        context = context,
        name = name,
        key = key,
        default = default,
        encode = { k, v -> putBoolean(k, v) },
        decode = { k, d -> getBoolean(k, d) }
    )
}

fun PrefsStore<String>.init(context: Context, name: String, key: String, default: String) {
    init(
        context = context,
        name = name,
        key = key,
        default = default,
        encode = { k, v -> putString(k, v) },
        decode = { k, d -> getString(k, d) ?: d }
    )
}

fun PrefsStore<Set<String>>.init(context: Context, name: String, key: String, default: Set<String>) {
    init(
        context = context,
        name = name,
        key = key,
        default = default,
        encode = { k, v -> putStringSet(k, v) },
        decode = { k, d -> getStringSet(k, d)?.toSet() ?: d }
    )
}
