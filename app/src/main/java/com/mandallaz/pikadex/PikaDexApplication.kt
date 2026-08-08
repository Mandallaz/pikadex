package com.mandallaz.pikadex

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.PrefetchSettings
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.CacheControlInterceptor
import okhttp3.OkHttpClient

class PikaDexApplication : Application(), ImageLoaderFactory {

    /** Reuses the shared client's connection pool and dispatcher (a second `OkHttpClient.Builder()`
     *  from scratch would spin up its own, pure overhead) but not its interceptor chain — images
     *  get their own 30-day Cache-Control (sprites/artwork never change once published, versus 7
     *  days for API JSON) and no HTTP-level disk cache of their own, since Coil's dedicated
     *  [DiskCache] below already covers that; keeping both would just double-cache the same bytes. */
    private val imageCacheOkHttpClient: OkHttpClient by lazy {
        AppContainer.sharedOkHttpClient.newBuilder()
            .cache(null)
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            .addNetworkInterceptor(CacheControlInterceptor(30 * 24 * 60 * 60))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        FavoritesRepository.init(this)
        TeamRepository.init(this)
        PrefetchSettings.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(imageCacheOkHttpClient)
        .crossfade(200)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .build()
}
