package com.tg.pokedex.util

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
}
