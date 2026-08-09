package com.mandallaz.pikadex.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Gunzip + Gson-reflection-parsing ~1300 entries is 150-350ms of work — must run off the
     *  main thread, or every cold-cache screen open freezes the UI for that long. */
    suspend fun <T> read(key: String, type: java.lang.reflect.Type, maxAgeMillis: Long): T? =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, "$key.json.gz")
            if (!file.exists()) return@withContext null
            val age = System.currentTimeMillis() - file.lastModified()
            if (age > maxAgeMillis) return@withContext null
            try {
                GZIPInputStream(file.inputStream()).bufferedReader().use { gson.fromJson<T>(it, type) }
            } catch (e: Exception) {
                // A corrupt/truncated file would otherwise sit here and be re-read and re-fail on
                // every cold start until maxAgeMillis expires it — delete it so the next write wins.
                file.delete()
                null
            }
        }

    /** Writes to a `.tmp` file and renames it over the real one only once it's fully written —
     *  writing `file` directly left a reader (or the next app launch) able to see a truncated,
     *  half-gzipped file if the process died mid-write (e.g. backgrounded and killed right after a
     *  bulk fetch completes). A same-directory rename is atomic, so [read] never observes a
     *  partial file: either the old complete one or the new complete one, never in between. */
    suspend fun write(key: String, value: Any) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "$key.json.gz")
        val tempFile = File(cacheDir, "$key.json.gz.tmp")
        try {
            GZIPOutputStream(tempFile.outputStream()).bufferedWriter().use { gson.toJson(value, it) }
            if (!tempFile.renameTo(file)) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            // Best-effort — a failed write just means the next cold start re-fetches from network.
            tempFile.delete()
        }
    }

    /** Deletes every cached entry — the "Clear downloaded data" settings action. The in-memory
     *  caches layered on top of this ([PokedexRepository]'s [AsyncValueCache]s) are untouched, so a
     *  screen that already loaded this process's data keeps working; only the *next* cold start (or
     *  an explicit re-prefetch) actually re-fetches from network. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Total bytes on disk — shown as storage accounting on the Settings screen. */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
