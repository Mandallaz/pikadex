package com.mandallaz.pikadex.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mandallaz.pikadex.data.remote.CacheControlInterceptor
import com.mandallaz.pikadex.data.remote.RetryInterceptor
import com.mandallaz.pikadex.data.repository.FakePokedexRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [AppContainer.sharedOkHttpClient] had no coverage at all — its retry/cache-control interceptor
 * wiring and disk cache setup only had the source-text-scraping guard in [CertificatePinningTest]
 * for the certificate pins themselves. Built via Robolectric rather than a bare mock `Context`:
 * `Cache(File(appContext.cacheDir, ...))` needs a real (if sandboxed) filesystem directory.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppContainerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppContainer.init(context)
        // AppContainer.repository is a JVM-wide singleton var — start from a known "unset" state
        // regardless of what an earlier test class in this same run left behind.
        AppContainer.resetRepositoryForTest()
    }

    @After
    fun tearDown() {
        AppContainer.resetRepositoryForTest()
    }

    @Test
    fun `sharedOkHttpClient wires the retry and cache-control interceptors`() {
        val client = AppContainer.sharedOkHttpClient
        assertTrue(client.interceptors.any { it is RetryInterceptor })
        assertTrue(client.networkInterceptors.any { it is CacheControlInterceptor })
    }

    @Test
    fun `sharedOkHttpClient sets up a disk cache under the app's cache directory`() {
        val cache = AppContainer.sharedOkHttpClient.cache
        assertNotNull(cache)
        assertEquals("http_cache", cache!!.directory.name)
        assertEquals(20L * 1024 * 1024, cache.maxSize())
    }

    @Test
    fun `sharedOkHttpClient uses a 30 second connect and read timeout`() {
        val client = AppContainer.sharedOkHttpClient
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
    }

    @Test
    fun `repository defaults to a real PokedexRepository when none was set for testing`() {
        assertNotNull(AppContainer.repository)
    }

    @Test
    fun `repository can be swapped for testing and restored via resetRepositoryForTest`() {
        val default = AppContainer.repository
        val fake = FakePokedexRepository()

        AppContainer.repository = fake
        assertEquals(fake, AppContainer.repository)
        assertNotEquals(default, AppContainer.repository)

        AppContainer.resetRepositoryForTest()
        assertEquals(default, AppContainer.repository)
    }
}
