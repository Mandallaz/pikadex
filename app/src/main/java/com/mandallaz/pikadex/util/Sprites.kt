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

    /** F76 — back-facing counterpart of [defaultSpriteUrl], for the front+back side-by-side
     *  display option. Coverage gaps here (a back sprite missing where a front one exists)
     *  degrade the same way every other candidate in [FallbackImage] does — no back-specific
     *  handling needed beyond this URL. */
    fun backSpriteUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/$id.png"

    /** Shiny counterpart of [backSpriteUrl]. */
    fun shinyBackSpriteUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/shiny/$id.png"

    /** Shiny counterpart of [officialArtworkUrl] — checked to actually exist on the CDN (e.g.
     *  #1, #25, #10043) before this was added, same as every other hardcoded path here. */
    fun shinyOfficialArtworkUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/shiny/$id.png"

    /** Animated Showdown battle sprite by convention, same CDN layout as every URL above — the
     *  detail screen normally reads this from [com.mandallaz.pikadex.data.remote.dto.PokemonDto]'s
     *  own `sprites.other.showdown` field instead, but a prefetch tier has no fetched DTO to read
     *  it from, only the numeric id. */
    fun showdownGifUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/$id.gif"

    /** Shiny counterpart of [showdownGifUrl]. */
    fun shinyShowdownGifUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/shiny/$id.gif"

    /** F76 — back-facing counterpart of [showdownGifUrl], same CDN layout as every URL above. */
    fun showdownBackGifUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/back/$id.gif"

    /** Shiny counterpart of [showdownBackGifUrl]. */
    fun shinyShowdownBackGifUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/back/shiny/$id.gif"

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
