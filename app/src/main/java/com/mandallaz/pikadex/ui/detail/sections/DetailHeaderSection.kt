package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.ui.components.PokemonArtwork
import com.mandallaz.pikadex.ui.components.PokemonFrontBackArtwork
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.ui.detail.hasAnimatedSprite
import com.mandallaz.pikadex.ui.detail.selectShowdownBackUrl
import com.mandallaz.pikadex.ui.detail.selectShowdownUrl
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.util.localizedOrEnglish

/** The top-of-page header: shiny/animated/cry controls (issue #50 — grouped with the sprite they
 *  affect, not the top bar), the artwork itself, name/genus, legendary/mythical badges, and type
 *  badges. */
@Composable
internal fun DetailHeaderSection(
    pokemon: PokemonDto,
    species: PokemonSpeciesDto,
    speciesNames: Map<String, Map<String, String>>,
    gameDataLanguage: String,
    shiny: Boolean,
    animated: Boolean,
    frontBackSpritesEnabled: Boolean,
    onToggleShiny: () -> Unit,
    onToggleAnimated: () -> Unit,
    isCryPlaying: Boolean,
    onPlayCry: () -> Unit
) {
    val primaryType = pokemon.types.orEmpty().minByOrNull { it.slot }?.type?.name ?: "normal"
    val primaryColor = com.mandallaz.pikadex.util.TypeColors.of(primaryType)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(primaryColor.copy(alpha = 0.15f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shiny/animated/cry live right above the sprite they affect (issue #50), not in
        // the top bar — grouped with what they act on rather than with unrelated actions
        // like Back and add-to-team.
        Row(horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = onToggleShiny) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = if (shiny) {
                        stringResource(R.string.detail_show_normal_coloring)
                    } else {
                        stringResource(R.string.detail_show_shiny_coloring)
                    },
                    tint = if (shiny) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
            if (hasAnimatedSprite(pokemon.sprites.other?.showdown)) {
                IconButton(onClick = onToggleAnimated) {
                    Icon(
                        imageVector = Icons.Filled.Animation,
                        contentDescription = if (animated) {
                            stringResource(R.string.detail_show_static_artwork)
                        } else {
                            stringResource(R.string.detail_show_animated_sprite)
                        },
                        tint = if (animated) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
            // F34: a one-shot action, not a toggle like shiny/animated above — disabled
            // rather than hidden while a cry is already playing, so tapping it twice fast
            // can't overlap two MediaPlayer instances.
            IconButton(onClick = onPlayCry, enabled = !isCryPlaying) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.detail_play_cry),
                    tint = if (isCryPlaying) {
                        LocalContentColor.current.copy(alpha = 0.38f)
                    } else {
                        LocalContentColor.current
                    }
                )
            }
        }
        if (frontBackSpritesEnabled) {
            PokemonFrontBackArtwork(
                id = pokemon.id,
                contentDescription = pokemon.name,
                baseSpeciesId = pokemon.species.id,
                modifier = Modifier.size(width = 360.dp, height = 180.dp),
                shiny = shiny,
                animated = animated,
                showdownFrontUrl = selectShowdownUrl(shiny, pokemon.sprites.other?.showdown),
                showdownBackUrl = selectShowdownBackUrl(shiny, pokemon.sprites.other?.showdown)
            )
        } else {
            PokemonArtwork(
                id = pokemon.id,
                contentDescription = pokemon.name,
                // Exact here, no name-based guessing: the payload already names this form's species.
                baseSpeciesId = pokemon.species.id,
                modifier = Modifier.size(200.dp),
                shiny = shiny,
                animated = animated,
                showdownUrl = selectShowdownUrl(shiny, pokemon.sprites.other?.showdown)
            )
        }
        Text(
            text = "#${pokemon.id.toString().padStart(4, '0')}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = pokemon.name.localizedDisplayName(speciesNames, gameDataLanguage),
            style = MaterialTheme.typography.titleLarge
        )
        val genus = species.genera.localizedOrEnglish(gameDataLanguage) { it.language.name }?.genus
        if (genus != null) {
            Text(text = genus, style = MaterialTheme.typography.bodyMedium)
        }
        // Both can be true at once in PokeAPI's data (there's no species where they are,
        // today, but nothing rules it out), so this shows both rather than picking one.
        if (species.isLegendary || species.isMythical) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (species.isLegendary) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.detail_legendary)) })
                }
                if (species.isMythical) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.detail_mythical)) })
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            pokemon.types.orEmpty().sortedBy { it.slot }.forEach {
                TypeBadge(it.type.name, it.type.id ?: 0, height = 28.dp)
            }
        }
    }
}
