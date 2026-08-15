package com.mandallaz.pikadex.data.repository

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B29 — the disk cache TTL was 7 days, which silently expired the ESSENTIALS prefetch tier's
 * whole offline promise a week after the user ran it; `getAllBasics`/`getAllMoveInfo`/species
 * names are backed by [com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource]'s hardcoded
 * top-level network calls, with no injection seam, so `diskCached`'s stale-on-failure fallback
 * can't be exercised end-to-end without a real network call. Guards the source-level fix instead
 * — the TTL bump is separately unit-tested for real via
 * [com.mandallaz.pikadex.data.JsonDiskCacheTest]'s `readStale` coverage, which is the actual new
 * capability `diskCached` relies on here.
 */
class PokedexRepositoryDiskCacheTtlTest {

    private val source = File("src/main/java/com/mandallaz/pikadex/data/repository/PokedexRepository.kt").readText()

    @Test
    fun `the disk cache TTL is at least 180 days, not the old 7`() {
        val match = Regex("""DISK_CACHE_MAX_AGE_MILLIS\s*=\s*TimeUnit\.DAYS\.toMillis\((\d+)\)""").find(source)
        val days = requireNotNull(match) { "couldn't find DISK_CACHE_MAX_AGE_MILLIS in PokedexRepository.kt" }
            .groupValues[1].toInt()
        assertTrue("DISK_CACHE_MAX_AGE_MILLIS is $days days, expected at least 180", days >= 180)
    }

    @Test
    fun `diskCached falls back to a stale cache entry when the network fetch fails`() {
        val diskCachedFn = source.substringAfter("private suspend fun <T : Any> diskCached(").substringBefore("\n    }")
        assertTrue(
            "diskCached's catch block should fall back to JsonDiskCache.readStale before propagating",
            diskCachedFn.contains("JsonDiskCache.readStale")
        )
    }

    // B59 — diskCached's write call didn't pass its own `type` parameter through to
    // JsonDiskCache.write, so `type` there defaulted to null and fell back to
    // `moshi.adapter<Any>(value.javaClass)`, which throws for a Map instance (loses its generic
    // parameters) and gets silently caught, sending every write through Gson instead of Moshi —
    // no functional break (Gson round-trips fine), but the "writes via Moshi" half of F100's
    // migration for this cache never actually happened. See [JsonDiskCacheTest] for direct
    // coverage of JsonDiskCache.write itself; this guards the call site that has to pass the type.
    @Test
    fun `diskCached passes its type parameter through to JsonDiskCache write`() {
        val diskCachedFn = source.substringAfter("private suspend fun <T : Any> diskCached(").substringBefore("\n    }")
        assertTrue(
            "diskCached should call JsonDiskCache.write(key, it, type) — a write with no type " +
                "argument falls back to Gson every time",
            diskCachedFn.contains("JsonDiskCache.write(key, it, type)")
        )
    }
}
