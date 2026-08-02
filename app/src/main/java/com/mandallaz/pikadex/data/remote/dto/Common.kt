package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Generic named reference returned everywhere by PokeAPI (e.g. {"name": "pikachu", "url": ".../pokemon/25/"}). */
data class NamedApiResource(
    val name: String,
    val url: String
) {
    // NOT `by lazy` here: Gson deserializes this class via unsafe allocation (it has no no-arg
    // constructor), which skips the constructor entirely — including whatever sets up a `lazy`
    // delegate's backing field, leaving it null and crashing on first `.id` read. A plain var
    // works because its "not computed yet" state is the JVM's own zero-initialized default
    // (null/false), which a freshly-allocated object gets regardless of whether its constructor
    // ran, so it's fine to (redundantly, harmlessly) compute again if two threads race here.
    @Volatile
    private var cachedId: Int? = null
    @Volatile
    private var idResolved: Boolean = false

    /** Extracts the numeric id from the URL (e.g. .../pokemon/25/ -> 25) — computed once and
     *  cached, since this used to re-parse the URL string on every access; that's cheap once, but
     *  reading `.id` while filtering/sorting a ~1300-item list did it thousands of times per pass.
     *  Absent for some id-less resources. */
    val id: Int?
        get() {
            if (!idResolved) {
                cachedId = url.trimEnd('/').substringAfterLast('/').toIntOrNull()
                idResolved = true
            }
            return cachedId
        }
}

data class NamedApiResourceList(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<NamedApiResource>
)
