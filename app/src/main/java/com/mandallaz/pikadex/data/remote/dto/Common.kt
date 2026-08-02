package com.mandallaz.pikadex.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Generic named reference returned everywhere by PokeAPI (e.g. {"name": "pikachu", "url": ".../pokemon/25/"}). */
data class NamedApiResource(
    val name: String,
    val url: String
) {
    /** Extracts the numeric id from the URL (e.g. .../pokemon/25/ -> 25). Absent for some id-less resources. */
    val id: Int?
        get() = url.trimEnd('/').substringAfterLast('/').toIntOrNull()
}

data class NamedApiResourceList(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<NamedApiResource>
)
