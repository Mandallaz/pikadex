package com.mandallaz.pikadex.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheControlInterceptorTest {

    private val request = Request.Builder().url("https://pokeapi.co/api/v2/pokemon/1").build()

    private fun responseOf(code: Int, pragma: String? = "no-cache", cacheControl: String? = "max-age=300") =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .apply {
                pragma?.let { header("Pragma", it) }
                cacheControl?.let { header("Cache-Control", it) }
            }
            .build()

    private class FakeChain(private val response: Response) : Interceptor.Chain {
        override fun request(): Request = response.request
        override fun proceed(request: Request): Response = response
        override fun connection(): Connection? = null
        override fun call(): Call = object : Call {
            override fun request(): Request = throw NotImplementedError()
            override fun execute(): Response = throw NotImplementedError()
            override fun enqueue(responseCallback: Callback) = throw NotImplementedError()
            override fun cancel() = throw NotImplementedError()
            override fun isExecuted(): Boolean = false
            override fun isCanceled(): Boolean = false
            override fun timeout(): Timeout = Timeout.NONE
            override fun clone(): Call = this
        }
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    @Test
    fun `overrides Cache-Control on a successful response with the given max-age`() {
        val chain = FakeChain(responseOf(200))

        val response = CacheControlInterceptor(maxAgeSeconds = 604800).intercept(chain)

        assertEquals("public, max-age=604800", response.header("Cache-Control"))
    }

    @Test
    fun `strips the origin's Pragma header on a successful response`() {
        val chain = FakeChain(responseOf(200))

        val response = CacheControlInterceptor(maxAgeSeconds = 300).intercept(chain)

        assertNull(response.header("Pragma"))
    }

    @Test
    fun `leaves an unsuccessful response's caching headers untouched`() {
        val chain = FakeChain(responseOf(500, pragma = "no-cache", cacheControl = "no-store"))

        val response = CacheControlInterceptor(maxAgeSeconds = 604800).intercept(chain)

        assertEquals("no-cache", response.header("Pragma"))
        assertEquals("no-store", response.header("Cache-Control"))
    }
}
