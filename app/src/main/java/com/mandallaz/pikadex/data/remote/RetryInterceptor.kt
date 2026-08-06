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
    private val initialDelayMs: Long = 500,
    private val sleepSliceMs: Long = 100
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
            // Cancelling the coroutine that made this call cancels the OkHttp Call, but that alone
            // does not interrupt a thread already sitting in Thread.sleep — sleepInterruptibly
            // rechecks cancellation between short slices instead of one long sleep, so a call
            // abandoned mid-backoff (a multi-second wait by the last attempt) doesn't tie up a
            // dispatcher thread for the rest of it.
            val wasCanceled = sleepInterruptibly(delayMs, sleepSliceMs) { chain.call().isCanceled() }
            if (wasCanceled) throw lastIoException ?: IOException("Canceled")
            delayMs *= 2
        }
        throw lastIoException ?: IOException("Request failed after $maxAttempts attempts")
    }
}

/** Sleeps for [totalMs], polling [isCanceled] every [sliceMs] instead of one uninterruptible
 *  [Thread.sleep] call. Returns `true` if cancellation (or thread interruption) was observed
 *  before the full duration elapsed. A free function taking a plain lambda, not a method on
 *  [RetryInterceptor] reading `chain.call()` directly, so it's testable without a real OkHttp
 *  Interceptor.Chain/Call. */
internal fun sleepInterruptibly(totalMs: Long, sliceMs: Long, isCanceled: () -> Boolean): Boolean {
    var remaining = totalMs
    while (remaining > 0) {
        if (isCanceled()) return true
        val slice = minOf(sliceMs, remaining)
        try {
            Thread.sleep(slice)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return true
        }
        remaining -= slice
    }
    return false
}
