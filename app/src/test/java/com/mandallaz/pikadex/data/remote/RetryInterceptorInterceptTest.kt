package com.mandallaz.pikadex.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RetryInterceptor.intercept]'s own retry/backoff decision had no coverage — only the extracted
 * [sleepInterruptibly] helper did ([RetryInterceptorTest]). Uses a hand-written fake
 * [Interceptor.Chain]/[Call] rather than a mocking library or MockWebServer, neither of which this
 * project depends on yet — [Interceptor.Chain] is a small enough interface that a fake is less
 * overhead than a new test dependency for this one file.
 */
class RetryInterceptorInterceptTest {

    private val request = Request.Builder().url("https://pokeapi.co/api/v2/pokemon/1").build()

    private fun responseOf(code: Int) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body("".toResponseBody(null))
        .build()

    private class FakeCall(var canceled: Boolean = false) : Call {
        override fun request(): Request = throw NotImplementedError()
        override fun execute(): Response = throw NotImplementedError()
        override fun enqueue(responseCallback: Callback) = throw NotImplementedError()
        override fun cancel() { canceled = true }
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = this
    }

    private class FakeChain(
        private val requestValue: Request,
        private val callValue: FakeCall,
        private val steps: MutableList<() -> Response>
    ) : Interceptor.Chain {
        var proceedCount = 0
            private set

        override fun request(): Request = requestValue
        override fun proceed(request: Request): Response {
            proceedCount++
            return steps.removeAt(0)()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = callValue
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun interceptor() = RetryInterceptor(maxAttempts = 3, initialDelayMs = 1, sleepSliceMs = 1)

    @Test
    fun `a successful response on the first attempt is returned without retrying`() {
        val chain = FakeChain(request, FakeCall(), mutableListOf({ responseOf(200) }))

        val response = interceptor().intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun `a 4xx response is never retried`() {
        val chain = FakeChain(request, FakeCall(), mutableListOf({ responseOf(404) }))

        val response = interceptor().intercept(chain)

        assertEquals(404, response.code)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun `a 5xx response is retried until it succeeds`() {
        val chain = FakeChain(
            request,
            FakeCall(),
            mutableListOf({ responseOf(500) }, { responseOf(200) })
        )

        val response = interceptor().intercept(chain)

        assertEquals(200, response.code)
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun `a 5xx response on every attempt returns the last 5xx after maxAttempts`() {
        val chain = FakeChain(
            request,
            FakeCall(),
            mutableListOf({ responseOf(500) }, { responseOf(502) }, { responseOf(503) })
        )

        val response = interceptor().intercept(chain)

        assertEquals(503, response.code)
        assertEquals(3, chain.proceedCount)
    }

    @Test
    fun `an IOException followed by success recovers`() {
        val chain = FakeChain(
            request,
            FakeCall(),
            mutableListOf({ throw IOException("connection reset") }, { responseOf(200) })
        )

        val response = interceptor().intercept(chain)

        assertEquals(200, response.code)
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun `an IOException on every attempt rethrows the last one after maxAttempts`() {
        val chain = FakeChain(
            request,
            FakeCall(),
            mutableListOf(
                { throw IOException("first") },
                { throw IOException("second") },
                { throw IOException("third") }
            )
        )

        val thrown = try {
            interceptor().intercept(chain)
            null
        } catch (e: IOException) {
            e
        }

        assertTrue(thrown != null && thrown.message == "third")
        assertEquals(3, chain.proceedCount)
    }

    @Test
    fun `cancellation during backoff aborts and rethrows the last IOException`() {
        val call = FakeCall(canceled = true)
        val chain = FakeChain(
            request,
            call,
            mutableListOf({ throw IOException("boom") }, { responseOf(200) })
        )

        val thrown = try {
            interceptor().intercept(chain)
            null
        } catch (e: IOException) {
            e
        }

        assertEquals("boom", thrown?.message)
        // Only the first attempt ran — cancellation was observed during that attempt's backoff,
        // aborting before a second proceed() call.
        assertEquals(1, chain.proceedCount)
    }
}
