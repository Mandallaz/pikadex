package com.mandallaz.pikadex.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Memoizes a suspend fetch per key, safe against concurrent callers racing to fill the same key —
 * unlike a plain `MutableMap<K, V>` + `getOrPut { suspendCall() }`, which has a real race even on
 * a single thread: `getOrPut`'s lambda suspends *before* the map is updated, so two coroutines
 * that both check the map before either one resumes both see a miss and both fire the fetch (e.g.
 * three Water-type team members all missing the type-detail cache and each triggering their own
 * `GET /type/water`). Caching the [Deferred] instead of the value closes that race: `async {}`
 * itself never suspends, so the map lookup-and-insert happens as one atomic step with no
 * suspension point in between — a second caller either sees nothing yet (and starts its own) or
 * the in-flight [Deferred] (and just awaits it), never both proceeding to fetch.
 *
 * Runs fetches on its own [CoroutineScope] rather than whichever caller's scope happened to start
 * one, for two reasons: (1) if a second caller is still awaiting an in-flight fetch when the first
 * caller's own scope is cancelled (e.g. they navigated away), the shared fetch keeps going instead
 * of cancelling out from under the second caller; (2) with no dispatcher specified, coroutines
 * default to [kotlinx.coroutines.Dispatchers.Default] rather than inheriting whatever dispatcher
 * happened to call `get` (typically `Main.immediate` from a ViewModel) — keeping any CPU-bound
 * work in the fetch (e.g. sorting ~1300 values) off the main thread for free.
 *
 * A failed fetch is evicted so the next call retries fresh, rather than permanently caching the
 * failure for the rest of the process — but only on a genuine failure. A caller whose own
 * coroutine is cancelled while awaiting a *shared* fetch must not evict it out from under other
 * still-interested awaiters, so [CancellationException] specifically is rethrown as-is.
 *
 * The map is guarded by a lock because callers genuinely arrive on different threads: ViewModels
 * call in on `Main.immediate`, while the repository's own derived caches (e.g. the sorted stat
 * arrays) run their fetch lambda on this class's [Dispatchers.Default] scope and call straight
 * back in from there. "No suspension point between lookup and insert" makes the check-and-insert
 * atomic against other *coroutines*, but not against another *thread* — leaving a plain HashMap
 * open to concurrent structural modification, and losing the single-flight guarantee this class
 * exists to provide.
 *
 * [maxSize], when non-null, caps the map at that many entries and evicts the least-recently-used
 * one past that — for a key space large enough that "cache everything forever" is itself the
 * problem (e.g. one entry per pokemon, ~1300 of them and growing). Left null for caches whose key
 * space is small enough that unbounded really does mean bounded in practice (e.g. one entry per
 * type, of which there are 18).
 */
class AsyncCache<K, V>(private val maxSize: Int? = null) {
    private val scope = CoroutineScope(SupervisorJob())
    private val lock = Any()
    private val deferreds = object : LinkedHashMap<K, Deferred<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Deferred<V>>): Boolean =
            maxSize != null && size > maxSize
    }

    suspend fun get(key: K, fetch: suspend () -> V): V {
        // async{} never suspends, so starting it inside the lock can't block another thread on IO.
        val deferred = synchronized(lock) { deferreds.getOrPut(key) { scope.async { fetch() } } }
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            synchronized(lock) { deferreds.remove(key, deferred) }
            throw e
        }
    }
}

/** Same idea as [AsyncCache] but for a single memoized value (a bulk fetch with no key). */
class AsyncValueCache<V> {
    private val scope = CoroutineScope(SupervisorJob())
    private val lock = Any()
    private var deferred: Deferred<V>? = null

    suspend fun get(fetch: suspend () -> V): V {
        val current = synchronized(lock) {
            deferred ?: scope.async { fetch() }.also { deferred = it }
        }
        return try {
            current.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            synchronized(lock) { if (deferred === current) deferred = null }
            throw e
        }
    }
}
