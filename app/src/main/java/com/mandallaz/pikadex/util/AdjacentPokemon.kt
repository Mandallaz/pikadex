package com.mandallaz.pikadex.util

/**
 * The previous/next name to swipe to from [currentName], stepping through [names] in whatever
 * order the caller passes — see [namesForAdjacency] for how that order is chosen.
 *
 * Null on either side at the respective end of the list, and both null if [currentName] isn't in
 * [names] at all (e.g. still loading).
 */
fun adjacentNames(names: List<String>, currentName: String): Pair<String?, String?> {
    val index = names.indexOf(currentName)
    if (index == -1) return null to null
    return names.getOrNull(index - 1) to names.getOrNull(index + 1)
}

/**
 * Which ordered name list [adjacentNames] should step through for [currentName]: the Pokédex
 * list's current filtered/sorted order ([displayedNames], from
 * [PokedexListContext][com.mandallaz.pikadex.data.PokedexListContext]) when [currentName] is
 * actually part of it, so swiping through a filtered view (e.g. Fire types only) stays inside that
 * filter — otherwise [masterNames], for every other entry point (an evolution chain tap, Compare,
 * a team member chip, or the list screen never having loaded this session).
 */
fun namesForAdjacency(displayedNames: List<String>, masterNames: List<String>, currentName: String): List<String> =
    if (currentName in displayedNames) displayedNames else masterNames
