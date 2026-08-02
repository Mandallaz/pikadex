package com.mandallaz.pikadex.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Overrides the response's Cache-Control header so OkHttp's disk cache keeps it far longer than
 * the origin asks for. PokeAPI, GitHub's raw-content CDN, and the sprite CDN all send short-lived
 * or moderate max-age values (5 minutes to 24 hours) meant for a high-traffic public API, not for
 * "how long can a single mobile client trust data that basically never changes" — Pokemon stats,
 * moves, and sprites don't change between app sessions, so re-fetching them every time the cache
 * expires is pure waste. Must run as a *network* interceptor (applied via
 * `.addNetworkInterceptor`) so it sees the real response before OkHttp's cache layer decides
 * whether to store it.
 */
class CacheControlInterceptor(private val maxAgeSeconds: Int) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Cache-Control")
            .header("Cache-Control", "public, max-age=$maxAgeSeconds")
            .build()
    }
}
