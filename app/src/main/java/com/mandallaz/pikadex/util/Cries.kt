package com.mandallaz.pikadex.util

/** F34: cry audio URLs, built from just the numeric id — same "GitHub CDN by convention" pattern
 *  as [Sprites], and verified live against a real `pokemon/{id}` REST response before hardcoding
 *  (`cries.latest`/`cries.legacy` point at these exact paths on `PokeAPI/cries`, a separate repo
 *  from the sprites one [Sprites] reads). Kept as its own object rather than folded into [Sprites]
 *  since these are audio, not images. */
object Cries {
    /** The current-generation cry. Not every id has one (very new additions can lag the mirror),
     *  same real-coverage-gap category as every URL [Sprites] builds by convention. */
    fun latestCryUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/$id.ogg"

    /** The Gen 5-era cry, kept for Pokémon whose cry has since been redone. Coverage is narrower
     *  than [latestCryUrl] by nature (only pre-Gen-6 additions have one at all). */
    fun legacyCryUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/legacy/$id.ogg"
}
