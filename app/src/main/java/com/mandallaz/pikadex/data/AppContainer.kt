package com.mandallaz.pikadex.data

import android.content.Context
import com.mandallaz.pikadex.data.remote.CacheControlInterceptor
import com.mandallaz.pikadex.data.remote.PokeApiService
import com.mandallaz.pikadex.data.remote.RetryInterceptor
import com.mandallaz.pikadex.data.repository.PokedexRepository
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Poor man's DI container: one singleton per process, no framework. */
object AppContainer {

    /** 7 days: PokeAPI/Smogon/GitHub data is effectively static between app sessions, so once
     *  fetched there's no reason to hit network again just because a public CDN's own max-age
     *  (5 minutes to 24 hours, meant for a high-traffic API, not a single mobile client) expired. */
    private const val HTTP_CACHE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60
    private const val HTTP_CACHE_SIZE_BYTES = 20L * 1024 * 1024

    /** Must be called once, from [com.mandallaz.pikadex.PikaDexApplication], before [repository]
     *  or [sharedOkHttpClient] are first touched — the disk cache needs a real app directory. */
    fun init(context: Context) {
        appContext = context.applicationContext
        JsonDiskCache.init(context)
    }

    private lateinit var appContext: Context

    /** Shared by every network caller (Retrofit, the GraphQL bulk fetches, the Smogon tier
     *  fetch) so there's one connection pool and one on-disk HTTP cache instead of three. */
    val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(appContext.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES))
            .certificatePinner(
                CertificatePinner.Builder()
                    .add("pokeapi.co", "sha256/vI2c4MzHEbIyjzPN4chWo00EfZeCrlu7OrQuswZxK5Q=")
                    .add("pokeapi.co", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
                    .add("pokeapi.co", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
                    .add("graphql.pokeapi.co", "sha256/vI2c4MzHEbIyjzPN4chWo00EfZeCrlu7OrQuswZxK5Q=")
                    .add("graphql.pokeapi.co", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
                    .add("graphql.pokeapi.co", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
                    .add("raw.githubusercontent.com", "sha256/p4MbBTfU3MTzqPxM2Cjv0q3WON8L6FqSzam65NVEkcM=")
                    .add("raw.githubusercontent.com", "sha256/LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=")
                    .add("raw.githubusercontent.com", "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=")
                    .build()
            )
            .addInterceptor(RetryInterceptor())
            .addNetworkInterceptor(CacheControlInterceptor(HTTP_CACHE_MAX_AGE_SECONDS))
            .apply {
                // Null in a release build (see the release-variant source set of
                // debugLoggingInterceptor) — formatting every request/response body is pure
                // overhead where nothing reads logcat.
                debugLoggingInterceptor()?.let { addInterceptor(it) }
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(PokeApiService.BASE_URL)
            .client(sharedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val pokeApiService: PokeApiService by lazy {
        retrofit.create(PokeApiService::class.java)
    }

    val repository: PokedexRepository by lazy {
        PokedexRepository(pokeApiService)
    }
}
