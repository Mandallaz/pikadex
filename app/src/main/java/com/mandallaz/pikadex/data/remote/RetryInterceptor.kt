package com.mandallaz.pikadex.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries transient failures (connection/timeout IOExceptions, or 5xx server responses) with
 * exponential backoff. Applied once at the OkHttpClient level so every PokeAPI call benefits from
 * it without repeating retry logic in each repository method. A 4xx response (bad request, not
 * found...) is never retried since retrying the exact same request won't change the outcome.
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val initialDelayMs: Long = 500
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var delayMs = initialDelayMs
        var lastIoException: IOException? = null

        for (attempt in 0 until maxAttempts) {
            val isLastAttempt = attempt == maxAttempts - 1
            try {
                val response = chain.proceed(chain.request())
                if (response.code < 500 || isLastAttempt) return response
                response.close()
            } catch (e: IOException) {
                lastIoException = e
                if (isLastAttempt) throw e
            }
            Thread.sleep(delayMs)
            delayMs *= 2
        }
        throw lastIoException ?: IOException("Request failed after $maxAttempts attempts")
    }
}
