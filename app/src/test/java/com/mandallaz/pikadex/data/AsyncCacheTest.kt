package com.mandallaz.pikadex.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A refetch (fetch count going up a second time for the same key) is the only externally
 * observable sign that a key was evicted — [AsyncCache] doesn't expose its map, on purpose.
 */
class AsyncCacheTest {

    private class FetchCounter {
        val counts = mutableMapOf<String, Int>()
        suspend fun fetch(key: String): Int {
            counts[key] = (counts[key] ?: 0) + 1
            return key.length
        }
    }

    @Test
    fun `evicts the least recently used entry once maxSize is exceeded`() = runBlocking {
        val counter = FetchCounter()
        val cache = AsyncCache<String, Int>(maxSize = 2)

        cache.get("a") { counter.fetch("a") }
        cache.get("b") { counter.fetch("b") }
        cache.get("c") { counter.fetch("c") } // over the cap -> evicts "a", the least recently used
        cache.get("a") { counter.fetch("a") } // "a" was evicted, so this is a fresh fetch

        assertEquals(2, counter.counts["a"])
        assertEquals(1, counter.counts["b"])
        assertEquals(1, counter.counts["c"])
    }

    @Test
    fun `reading an entry counts as using it, so it isn't the next one evicted`() = runBlocking {
        val counter = FetchCounter()
        val cache = AsyncCache<String, Int>(maxSize = 2)

        cache.get("a") { counter.fetch("a") }
        cache.get("b") { counter.fetch("b") }
        cache.get("a") { counter.fetch("a") } // touches "a" again, so "b" is now the older one
        cache.get("c") { counter.fetch("c") } // over the cap -> evicts "b", not "a"

        cache.get("a") { counter.fetch("a") } // still cached
        cache.get("b") { counter.fetch("b") } // evicted -> fresh fetch

        assertEquals(1, counter.counts["a"])
        assertEquals(2, counter.counts["b"])
        assertEquals(1, counter.counts["c"])
    }

    @Test
    fun `no maxSize means no eviction`() = runBlocking {
        val counter = FetchCounter()
        val cache = AsyncCache<String, Int>()

        repeat(50) { i -> cache.get("key$i") { counter.fetch("key$i") } }
        cache.get("key0") { counter.fetch("key0") } // still the first entry ever put in

        assertEquals(1, counter.counts["key0"])
    }
}
