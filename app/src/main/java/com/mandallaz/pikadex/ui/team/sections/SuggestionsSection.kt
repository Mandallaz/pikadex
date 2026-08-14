package com.mandallaz.pikadex.ui.team.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.ui.components.localizedTierLabel
import com.mandallaz.pikadex.ui.components.localizedTypeNames
import com.mandallaz.pikadex.util.TeamSuggestion
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.util.toDisplayName

/** Candidates that would fix both a shared weakness and a coverage gap at once — see
 *  [com.mandallaz.pikadex.ui.team.TeamViewModel.loadSuggestions]/issue #11. Sorted by total impact
 *  (weaknesses resisted plus gaps hit) descending, stat total ascending as a tiebreak, so the most
 *  useful, least overpowering options lead the row. */
@Composable
internal fun SuggestionsCard(
    suggestions: List<TeamSuggestion>,
    spriteIds: Map<String, Int>,
    tierCeiling: String?,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onAdd: (String) -> Unit,
    onPokemonClick: (String) -> Unit
) {
    val subtitleRes = when {
        suggestions.any { it.weaknessesResisted.isNotEmpty() && it.gapsHit.isNotEmpty() } -> R.string.team_suggestions_subtitle
        suggestions.any { it.weaknessesResisted.isNotEmpty() } -> R.string.team_suggestions_subtitle_defense
        suggestions.any { it.gapsHit.isNotEmpty() } -> R.string.team_suggestions_subtitle_offense
        else -> R.string.team_suggestions_subtitle
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.team_suggestions_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            // The ceiling itself lives in Settings, out of sight from this card — without this
            // line, a shorter-than-expected list here (or one that suddenly changed) had no
            // visible cause.
            if (tierCeiling != null) {
                Text(
                    stringResource(R.string.team_suggestions_tier_limited, localizedTierLabel(tierCeiling)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // More than fit on a portrait phone in one glance — the row scrolls, but nothing about
            // a plain horizontalScroll Row hints that on its own, so a bare "4 shown, 6 more
            // offscreen" used to read as "only 4 suggestions" with no reason to swipe further.
            if (suggestions.size > 4) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        stringResource(R.string.team_suggestions_swipe_all, suggestions.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = if (suggestions.size > 4) 0.dp else 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionTile(
                        suggestion = suggestion,
                        spriteId = spriteIds[suggestion.name] ?: 0,
                        speciesNames = speciesNames,
                        language = language,
                        onAdd = { onAdd(suggestion.name) },
                        onSpriteClick = { onPokemonClick(suggestion.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionTile(
    suggestion: TeamSuggestion,
    spriteId: Int,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onAdd: () -> Unit,
    // issue #17 — sprite-only tap target, not the whole tile: the "+" IconButton already
    // claims its own tap area, and the ask specifically named "the sprite", read narrowly.
    onSpriteClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        PokemonSprite(
            id = spriteId,
            contentDescription = suggestion.name,
            modifier = Modifier.size(48.dp).clickable(onClick = onSpriteClick)
        )
        Text(
            suggestion.name.localizedDisplayName(speciesNames, language),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.team_suggestion_bst, suggestion.statTotal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            suggestion.types.forEach { type ->
                TypeBadge(type, TypeIds.idOrNull(type), height = 14.dp)
            }
        }
        // The "why" behind this specific suggestion — without it the reasoning in the card's own
        // subtitle ("would help both...") never ties back to any one tile, and the user has to
        // work it out by eye from the type badges above. Also what [rankSuggestions] sorts by, so
        // it doubles as an explanation for the tile's position in the row.
        val whyText = when {
            suggestion.weaknessesResisted.isNotEmpty() && suggestion.gapsHit.isNotEmpty() -> {
                stringResource(
                    R.string.team_suggestion_resists_hits,
                    suggestion.weaknessesResisted.localizedTypeNames().joinToString(", "),
                    suggestion.gapsHit.localizedTypeNames().joinToString(", ")
                )
            }
            suggestion.weaknessesResisted.isNotEmpty() -> {
                stringResource(
                    R.string.team_suggestion_resists,
                    suggestion.weaknessesResisted.localizedTypeNames().joinToString(", ")
                )
            }
            suggestion.gapsHit.isNotEmpty() -> {
                stringResource(
                    R.string.team_suggestion_hits,
                    suggestion.gapsHit.localizedTypeNames().joinToString(", ")
                )
            }
            else -> ""
        }
        Text(
            text = whyText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.team_suggestion_add_cd, suggestion.name.localizedDisplayName(speciesNames, language)),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
