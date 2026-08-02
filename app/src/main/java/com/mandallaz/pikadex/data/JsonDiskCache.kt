package com.mandallaz.pikadex.data

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Persists a single gzipped JSON blob per cache key across app restarts, for data that a plain
 * OkHttp response cache can't cover — namely the bulk GraphQL stats/move-info fetches, which are
 * POST requests (the HTTP cache spec, and OkHttp's implementation of it, only caches GET). Written
 * to internal storage (`filesDir/disk_cache`), which is private to the app and needs no
 * permissions. Gzipped because the source JSON is a ~1300/~940-entry map with the same handful of
 * key names repeated in every entry (`"hp"`, `"attack"`, `"special-defense"`...) — exactly the
 * repetition gzip is best at, typically 5-10x smaller on this shape of data.
 *
 * [maxAgeMillis] is a safety net, not the primary staleness signal — this data (base stats, move
 * data) only changes when a new game generation ships, so a generous TTL (weeks) just guards
 * against ever serving a permanently-stale cache if the app is never updated.
 */
object JsonDiskCache {
    private const val DIR_NAME = "disk_cache"
    private val gson = Gson()
    private lateinit var cacheDir: File

    fun init(context: Context) {
        cacheDir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
    }

    fun <T> read(key: String, type: java.lang.reflect.Type, maxAgeMillis: Long): T? {
        val file = File(cacheDir, "$key.json.gz")
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (age > maxAgeMillis) return null
        return try {
            GZIPInputStream(file.inputStream()).bufferedReader().use { gson.fromJson<T>(it, type) }
        } catch (e: Exception) {
            null
        }
    }

    fun write(key: String, value: Any) {
        val file = File(cacheDir, "$key.json.gz")
        try {
            GZIPOutputStream(file.outputStream()).bufferedWriter().use { gson.toJson(value, it) }
        } catch (e: Exception) {
            // Best-effort — a failed write just means the next cold start re-fetches from network.
        }
    }
}
