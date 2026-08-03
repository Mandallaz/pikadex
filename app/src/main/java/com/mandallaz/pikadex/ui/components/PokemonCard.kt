package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.toDisplayName

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
    name: String,
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
            // which differs per-card since nothing constrains it.
            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = Sprites.officialArtworkUrl(id),
                    // null, not the name: the card is one merged clickable for accessibility, and
                    // the name is already right below as real text — describing the artwork too made
                    // TalkBack read every Pokémon's name twice.
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(96.dp)
                )
                Text(
                    text = "#${id.toString().padStart(4, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = name.toDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        IconButton(
            onClick = onToggleTeam,
            enabled = isInTeam || !isTeamFull,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                contentDescription = if (isInTeam) "Remove from team" else "Add to team",
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
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
