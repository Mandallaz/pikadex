package com.mandallaz.pikadex

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.annotation.ExperimentalCoilApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [PikaDexApplication.newImageLoader] previously had only the source-text-scraping guard in
 * [PikaDexApplicationTest] (its own doc claims a real `Context` is "unavailable to a plain JVM
 * unit test" — true for a bare JUnit test, but Robolectric supplies exactly that, so this
 * constructs the real `ImageLoader` and inspects its actual configuration instead of grepping the
 * source for the literal that builds it).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PikaDexApplicationImageLoaderTest {

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `newImageLoader configures a disk cache sized for both Sprites tiers at once`() {
        val app = ApplicationProvider.getApplicationContext<PikaDexApplication>()

        val imageLoader = app.newImageLoader()

        val diskCache = imageLoader.diskCache
        assertNotNull(diskCache)
        assertEquals(400L * 1024 * 1024, diskCache!!.maxSize)
        assertEquals("image_cache", diskCache.directory.name)
    }

    @Test
    fun `newImageLoader configures a memory cache`() {
        val app = ApplicationProvider.getApplicationContext<PikaDexApplication>()

        val imageLoader = app.newImageLoader()

        assertNotNull(imageLoader.memoryCache)
    }
}
