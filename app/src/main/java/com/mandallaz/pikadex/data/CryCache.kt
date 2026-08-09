package com.mandallaz.pikadex.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * F34's own local cache for cry audio, separate from Coil's image disk cache ([PikaDexApplication])
 * and the API JSON [JsonDiskCache] — [android.media.MediaPlayer] plays from a local file path or a
 * network URL, not from Coil, so a cry has nowhere else to land once downloaded ahead of time.
 * Same "download once, keep as our own artifact" shape as the rest of this app's offline story.
 */
object CryCache {
    private const val DIR_NAME = "cries"

    private fun dir(context: Context): File =
        File(context.applicationContext.cacheDir, DIR_NAME).apply { mkdirs() }

    fun file(context: Context, id: Int): File = File(dir(context), "$id.ogg")

    fun isCached(context: Context, id: Int): Boolean = file(context, id).length() > 0L

    /** Downloads [url] to this id's cache file. Returns false (not an exception) on any failure —
     *  callers here are always best-effort bulk prefetch units, same as every other
     *  [PrefetchManager] unit, where one failed download shouldn't abort the other ~1300. */
    suspend fun download(context: Context, id: Int, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            AppContainer.sharedOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val bytes = response.body?.bytes() ?: return@withContext false
                file(context, id).writeBytes(bytes)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun sizeBytes(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
