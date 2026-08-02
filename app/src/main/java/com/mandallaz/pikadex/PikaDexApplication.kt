package com.mandallaz.pikadex

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mandallaz.pikadex.data.AppContainer
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.remote.CacheControlInterceptor
import okhttp3.OkHttpClient

class PikaDexApplication : Application(), ImageLoaderFactory {

    /** 30 days: sprites/artwork never change once published, but the CDN they're served from
     *  sends a 5-minute max-age (fine for a high-traffic public site, not for a mobile client
     *  that would otherwise re-check network every time a screen is revisited). */
    private val imageCacheOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addNetworkInterceptor(CacheControlInterceptor(30 * 24 * 60 * 60))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        FavoritesRepository.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(imageCacheOkHttpClient)
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
