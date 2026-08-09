package com.mandallaz.pikadex.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.mandallaz.pikadex.util.Sprites

/**
 * Pokémon imagery, degrading through the pictures that actually exist for a given entry.
 *
 * PokeAPI's sprite repository is incomplete in three different ways, and each one used to render as
 * an empty gap — which reads as a broken app rather than as missing upstream data:
 *  - no official artwork, but a sprite exists (#10143, #10145, #10322, #10323)
 *  - a sprite is missing, but the artwork exists (#10158, #10159, #10301 Zygarde Mega)
 *  - neither exists (#10264-#10271, Koraidon's and Miraidon's traversal forms) — those fall back to
 *    the base species, which is the right picture anyway since they're movement modes rather than
 *    visually distinct Pokémon.
 */
@Composable
fun PokemonArtwork(
    id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    baseSpeciesId: Int? = null,
    contentScale: ContentScale = ContentScale.Fit,
    // Only the detail screen's header offers a shiny toggle — evolution-chain tiles and team
    // chips (which use PokemonSprite, not this) always show the regular form.
    shiny: Boolean = false,
    // F38: animated Showdown battle sprite, only offered alongside this toggle on the detail
    // screen header. Unlike every other URL here, this one can't be rebuilt from just the id by
    // convention — coverage is incomplete, so it comes from the actual fetched DTO
    // (PokemonDto.sprites.other.showdown) rather than a guessed CDN path. Null when animated is
    // off, or when this Pokémon has no Showdown sprite — either way the fallback chain below
    // covers it the same way a 404 on any other candidate already does.
    animated: Boolean = false,
    showdownUrl: String? = null
) {
    FallbackImage(
        candidates = listOfNotNull(
            if (animated) showdownUrl else null,
            if (shiny) Sprites.shinyOfficialArtworkUrl(id) else null,
            Sprites.officialArtworkUrl(id),
            Sprites.defaultSpriteUrl(id),
            baseSpeciesId?.let { Sprites.officialArtworkUrl(it) }
        ),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

/** The small in-game sprite, with the same fallbacks as [PokemonArtwork] in the other order — used
 *  where the art is displayed thumbnail-sized (evolution chains, team rosters) and downloading a
 *  full-size artwork PNG for it would be wasteful. */
@Composable
fun PokemonSprite(
    id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    baseSpeciesId: Int? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    FallbackImage(
        candidates = listOfNotNull(
            Sprites.defaultSpriteUrl(id),
            Sprites.officialArtworkUrl(id),
            baseSpeciesId?.let { Sprites.defaultSpriteUrl(it) }
        ),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
private fun FallbackImage(
    candidates: List<String>,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier
) {
    // Keyed on the candidates so a recycled grid cell showing a different Pokémon restarts at the
    // first option rather than inheriting the previous occupant's fallback.
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    AsyncImage(
        model = candidates.getOrNull(attempt),
        contentDescription = contentDescription,
        contentScale = contentScale,
        // `size - 1`, not `size`: incrementing on the last candidate's failure handed AsyncImage a
        // null model, which Coil reports as another error — an extra recomposition to reach the
        // same settled state (a permanently empty cell) instead of stopping there directly.
        onError = { if (attempt < candidates.size - 1) attempt++ },
        modifier = modifier
    )
}
