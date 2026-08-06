package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.NamedApiResource

/**
 * Pure encode/decode logic for [com.mandallaz.pikadex.data.TeamRepository]'s SharedPreferences
 * storage, pulled out of the repository itself so it's testable without a real Context — the same
 * reasoning as [com.mandallaz.pikadex.data.persistableMembers] and
 * [com.mandallaz.pikadex.data.remote.sleepInterruptibly] elsewhere in this codebase.
 */
private const val ENTRY_DELIMITER = ","
private const val FIELD_DELIMITER = "|"

/** "1,2,3" -> [1, 2, 3]. A malformed entry (shouldn't happen — this app is the only writer) is
 *  dropped rather than crashing the whole list. */
internal fun decodeTeamIds(raw: String?): List<Int> =
    raw?.split(ENTRY_DELIMITER)?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

internal fun encodeTeamIds(ids: List<Int>): String = ids.joinToString(ENTRY_DELIMITER)

/** Assumes every member already has a numeric id — callers filter with
 *  [com.mandallaz.pikadex.data.persistableMembers] first, since an id-less resource can't be
 *  encoded meaningfully (there's no id to reconstruct a url from on the way back in). */
internal fun encodeMembers(members: List<NamedApiResource>): String =
    members.joinToString(ENTRY_DELIMITER) { "${it.name}$FIELD_DELIMITER${it.id}" }

internal fun decodeMembers(raw: String?): List<NamedApiResource> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(ENTRY_DELIMITER).mapNotNull { entry ->
        val (name, id) = entry.split(FIELD_DELIMITER).takeIf { it.size == 2 } ?: return@mapNotNull null
        NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")
    }
}
