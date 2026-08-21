package com.mandallaz.pikadex.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * [CryCache.writeAtomically] is a plain java.io.File operation — no Context needed, same reasoning
 * as [JsonDiskCacheTest]'s own doc on testing file-backed caches without one.
 */
class CryCacheTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = createTempDirectory(prefix = "cry-cache-test").toFile()
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    @Test
    fun `a write leaves exactly the target file behind, no tmp leftover`() {
        val target = File(dir, "25.ogg")
        assertTrue(CryCache.writeAtomically(target, byteArrayOf(1, 2, 3)))
        assertEquals(listOf("25.ogg"), dir.listFiles()!!.map { it.name })
    }

    @Test
    fun `a written value round-trips through the target file's bytes`() {
        val target = File(dir, "25.ogg")
        CryCache.writeAtomically(target, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    // Regression: writing the target file directly left a reader able to observe a truncated file
    // if the process died mid-write — a same-directory rename is atomic, so a reader never sees a
    // partial file.
    @Test
    fun `a write that can't rename its tmp file over the target leaves the previous file untouched`() {
        val target = File(dir, "25.ogg")
        CryCache.writeAtomically(target, byteArrayOf(1, 2, 3))

        dir.setWritable(false)
        val result = CryCache.writeAtomically(target, byteArrayOf(9, 9, 9))
        dir.setWritable(true)

        assertFalse(result)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(File(dir, "25.ogg.tmp").exists())
    }
}

/**
 * The Context-touching half of [CryCache] ([CryCache.file]/[CryCache.isCached]/
 * [CryCache.sizeBytes]/[CryCache.clear]) had no coverage at all — a separate Robolectric-based
 * class rather than folding into [CryCacheTest] above, which deliberately stays a plain JVM test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CryCacheContextTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        CryCache.clear(context)
    }

    @Test
    fun `file resolves to the id-named ogg file under the cries cache directory`() {
        val file = CryCache.file(context, 25)
        assertEquals("25.ogg", file.name)
        assertEquals("cries", file.parentFile?.name)
    }

    @Test
    fun `isCached is false for a file that was never written`() {
        assertFalse(CryCache.isCached(context, 999))
    }

    @Test
    fun `isCached is true once the file has content`() {
        CryCache.writeAtomically(CryCache.file(context, 25), byteArrayOf(1, 2, 3))
        assertTrue(CryCache.isCached(context, 25))
    }

    @Test
    fun `sizeBytes sums every cached file's length`() {
        CryCache.writeAtomically(CryCache.file(context, 1), byteArrayOf(1, 2, 3))
        CryCache.writeAtomically(CryCache.file(context, 2), byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(8L, CryCache.sizeBytes(context))
    }

    @Test
    fun `sizeBytes is zero for an empty cache`() {
        assertEquals(0L, CryCache.sizeBytes(context))
    }

    @Test
    fun `clear deletes every cached file`() {
        CryCache.writeAtomically(CryCache.file(context, 1), byteArrayOf(1))
        CryCache.writeAtomically(CryCache.file(context, 2), byteArrayOf(1))

        CryCache.clear(context)

        assertFalse(CryCache.isCached(context, 1))
        assertFalse(CryCache.isCached(context, 2))
        assertEquals(0L, CryCache.sizeBytes(context))
    }
}
