package com.mandallaz.pikadex.data

import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * JsonDiskCache is a singleton normally wired up via [JsonDiskCache.init] and a real
 * android.content.Context, neither available in a plain JUnit test. [cacheDir] is a plain
 * `java.io.File` field though, so it's swapped in directly via reflection to point at a real temp
 * directory — no Context, no Robolectric, needed for what these tests actually check.
 */
class JsonDiskCacheTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = createTempDirectory(prefix = "json-disk-cache-test").toFile()
        val field = JsonDiskCache::class.java.getDeclaredField("cacheDir")
        field.isAccessible = true
        field.set(JsonDiskCache, dir)
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    private val mapType = object : TypeToken<Map<String, Int>>() {}.type

    @Test
    fun `a write leaves exactly the target file behind, no tmp leftover`() = runBlocking {
        JsonDiskCache.write("key", mapOf("a" to 1))
        assertEquals(listOf("key.json.gz"), dir.listFiles()!!.map { it.name })
    }

    @Test
    fun `a written value round-trips through read`() = runBlocking {
        JsonDiskCache.write("key", mapOf("a" to 1))
        val result = JsonDiskCache.read<Map<String, Int>>("key", mapType, maxAgeMillis = 60_000)
        assertEquals(mapOf("a" to 1), result)
    }

    @Test
    fun `a write that can't create its tmp file leaves the previous complete file untouched`() = runBlocking {
        JsonDiskCache.write("key", mapOf("a" to 1))
        val before = File(dir, "key.json.gz").readBytes()

        // Forces the tmp file's outputStream() to fail, exercising the catch branch — without
        // this, a failed write (disk full, permission change mid-session) would either crash or,
        // if it wrote the real file directly, could leave it half-written.
        dir.setWritable(false)
        JsonDiskCache.write("key", mapOf("a" to 2))
        dir.setWritable(true)

        assertArrayEquals(before, File(dir, "key.json.gz").readBytes())
        assertFalse(File(dir, "key.json.gz.tmp").exists())
    }

    // Regression: a corrupt/truncated file (e.g. from a process kill outside this cache's own
    // atomic write, or on-disk bit rot) used to be left behind on a failed read, so it was re-read
    // and re-fail on every cold start until maxAgeMillis expired it.
    @Test
    fun `a corrupted file is deleted on a failed read, not left behind to fail again`() = runBlocking {
        File(dir, "key.json.gz").writeBytes(byteArrayOf(1, 2, 3, 4))

        val result = JsonDiskCache.read<Map<String, Int>>("key", mapType, maxAgeMillis = 60_000)

        assertEquals(null, result)
        assertFalse(File(dir, "key.json.gz").exists())
    }

    // B29 — readStale is the stale-on-failure fallback PokedexRepository.diskCached reaches for
    // when a network refresh fails: an entry past its TTL must still be readable through it.
    @Test
    fun `readStale returns a value past its maxAge, unlike read`() = runBlocking {
        JsonDiskCache.write("key", mapOf("a" to 1))
        val file = File(dir, "key.json.gz")
        file.setLastModified(System.currentTimeMillis() - 60_000)

        assertEquals(null, JsonDiskCache.read<Map<String, Int>>("key", mapType, maxAgeMillis = 1_000))
        assertEquals(mapOf("a" to 1), JsonDiskCache.readStale<Map<String, Int>>("key", mapType))
    }

    @Test
    fun `readStale returns null when nothing was ever written`() = runBlocking {
        assertEquals(null, JsonDiskCache.readStale<Map<String, Int>>("never-written", mapType))
    }

    // B47: write() uses a shared, non-unique temp filename ($key.json.gz.tmp) per key with no locking,
    // so two concurrent writes to the same key can corrupt/truncate each other.
    // With synchronization and unique temp files, this is now safe and the latest written data or other
    // valid write data is correctly read without corruption.
    @Test
    fun `concurrent writes to the same key do not corrupt each other`() = runBlocking {
        val jobs = List(20) { index ->
            launch(Dispatchers.Default) {
                JsonDiskCache.write("concurrent-key", mapOf("val" to index))
            }
        }
        jobs.forEach { it.join() }

        // Read back the value and verify it parsed successfully without throwing,
        // and is one of the valid writes.
        val result = JsonDiskCache.read<Map<String, Int>>("concurrent-key", mapType, maxAgeMillis = 60_000)
        org.junit.Assert.assertNotNull(result)
        org.junit.Assert.assertTrue(result!!["val"] in 0..19)
    }

    // B48: readFile() unconditionally deletes the cache file on a failed read with no locking against a concurrent write,
    // so it can delete a file that a racing write just made valid.
    // By synchronizing around the key, a failed read will lock the key, read/fail/delete, and only then does the
    // concurrent write execute (or vice versa), preventing the delete from wiping out the newly written file.
    @Test
    fun `read failure does not delete a racing valid write`() = runBlocking {
        // Prepare a corrupted file first
        File(dir, "racing-key.json.gz").writeBytes(byteArrayOf(1, 2, 3, 4))

        val readJob = launch(Dispatchers.Default) {
            // This will try to read, fail, and attempt to delete. Since it locks the key,
            // the write cannot execute in the middle or be deleted by the cleanup.
            JsonDiskCache.read<Map<String, Int>>("racing-key", mapType, maxAgeMillis = 60_000)
        }

        val writeJob = launch(Dispatchers.Default) {
            JsonDiskCache.write("racing-key", mapOf("a" to 1))
        }

        readJob.join()
        writeJob.join()

        // Verify the file exists and has the correct, uncorrupted value written by the racing write
        val result = JsonDiskCache.read<Map<String, Int>>("racing-key", mapType, maxAgeMillis = 60_000)
        assertEquals(mapOf("a" to 1), result)
    }
}
