package com.mandallaz.pikadex.util

/** PokeAPI doesn't always return a sprite URL for obscure ids that were never refreshed; these
 * helpers rebuild the URL from the official GitHub CDN using just the numeric id, avoiding a full
 * detail fetch just to show a thumbnail. */
object Sprites {
    fun officialArtworkUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png"

    fun defaultSpriteUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"

    fun shinySpriteUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/shiny/$id.png"

    /** Official Scarlet/Violet-style type badge (icon + name baked into the image). */
    fun typeIconUrl(typeId: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/types/generation-ix/scarlet-violet/$typeId.png"

    /**
     * The species an alternate form belongs to, so a form with no images of its own can borrow its
     * base species' artwork — Koraidon's and Miraidon's traversal forms (#10264-#10271) have no
     * artwork *and* no sprite in PokeAPI's repository, since they're movement modes rather than
     * visually distinct Pokémon.
     *
     * Forms are named "{species}-{form}" ("koraidon-limited-build"), so this drops trailing segments
     * until [isKnown] recognises one. Returns null for a name that is already a base species, or
     * whose base can't be identified.
     */
    fun baseSpeciesName(formName: String, isKnown: (String) -> Boolean): String? {
        var candidate = formName
        while (candidate.contains('-')) {
            candidate = candidate.substringBeforeLast('-')
            if (isKnown(candidate)) return candidate
        }
        return null
    }
}
