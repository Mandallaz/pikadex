package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.util.Sprites

/**
 * Reads its team/favorite status from parameters rather than collecting
 * [com.mandallaz.pikadex.data.TeamRepository]/[com.mandallaz.pikadex.data.FavoritesRepository]
 * itself — every card in the grid subscribing to those flows independently meant tapping one
 * star recomposed all ~15-20 visible cards (a state read inside each card's own scope, not a
 * parameter change strong-skipping could shortcut), plus launched/cancelled two coroutines per
 * card on every scroll. Hoisting the reads to the screen means only the tapped card recomposes.
 */
@Composable
fun PokemonCard(
    id: Int,
    // B9 — the caller resolves this (raw name formatted, or the picked language's localized
    // species name if available) rather than this card calling toDisplayName() on a raw name
    // itself, same "hoist to the screen" reasoning as team/favorite status above: the screen
    // already reads PokedexListUiState.speciesNames and the current language once, not once per
    // card.
    displayName: String,
    baseSpeciesId: Int?,
    // F82 — empty until PokedexListUiState.typesByName has loaded (same lazy bulk fetch already
    // used for the rarity/counter filters), so a card renders with no type row for a moment on a
    // cold list load rather than blocking on it.
    types: List<String> = emptyList(),
    isFavorite: Boolean,
    isInTeam: Boolean,
    isTeamFull: Boolean,
    onClick: () -> Unit,
    onToggleTeam: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    // No fixed aspectRatio here (used to be 0.82f) — a 2-line name like "Zamazenta Crowned" would
    // overflow that fixed height and get clipped in half by the card's own bottom edge.
    // LazyVerticalGrid sizes each row to its tallest cell, so letting the Column size to its
    // actual content (image + up to 2 text lines) is enough to never clip.
    Box(modifier = modifier) {
        Card(
            onClick = onClick,
            // A min height sized for a full 2-line name: without it, a 1-line-name card (e.g.
            // "Pikachu") next to a 2-line-name card in the same grid row (e.g. "Zamazenta
            // Crowned") ends up visibly shorter, top-aligned against a taller neighbor — the
            // Column's own Arrangement.Center below only centers *within* its own measured height,
            // which differs per-card since nothing constrains it. Kept close to a real 2-line
            // name's own measured height, ~173dp (issue #49; see
            // PokemonCardLayoutTest.oneLineAndTwoLineNameCardsHaveEqualHeight), with a small margin
            // rather than padded further — any smaller and that case is shorter than its neighbor
            // again.
            modifier = Modifier.fillMaxWidth().heightIn(min = 176.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PokemonArtwork(
                    id = id,
                    // null, not the name: the card is one merged clickable for accessibility, and
                    // the name is already right below as real text — describing the artwork too made
                    // TalkBack read every Pokémon's name twice.
                    contentDescription = null,
                    baseSpeciesId = baseSpeciesId,
                    modifier = Modifier.size(96.dp)
                )
                Text(
                    text = "#${id.toString().padStart(4, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                if (types.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        types.forEach { TypeBadge(it, height = 16.dp) }
                    }
                }
            }
        }
        IconButton(
            onClick = onToggleTeam,
            enabled = isInTeam || !isTeamFull,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                contentDescription = if (isInTeam) {
                    stringResource(R.string.pokemon_card_remove_from_team_cd)
                } else {
                    stringResource(R.string.pokemon_card_add_to_team_cd)
                },
                // A hardcoded Color.Gray for "not in team" used to also apply when the button was
                // disabled (team full), so disabled looked pixel-identical to enabled — tapping a
                // full team's add button did nothing with zero visual explanation why. Use M3's
                // own disabled-content convention (38% alpha) only when actually disabled.
                tint = when {
                    isInTeam -> MaterialTheme.colorScheme.primary
                    isTeamFull -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) {
                    stringResource(R.string.pokemon_card_remove_from_favorites_cd)
                } else {
                    stringResource(R.string.pokemon_card_add_to_favorites_cd)
                },
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
