package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.util.localizedDisplayName

@Composable
internal fun EvolutionStageBox(
    stage: com.mandallaz.pikadex.util.EvolutionStage,
    pokemon: PokemonDto,
    onPokemonClick: (String) -> Unit,
    speciesNames: Map<String, Map<String, String>>,
    language: String
) {
    PokemonSpriteTile(
        displayName = stage.speciesName.localizedDisplayName(speciesNames, language),
        id = stage.id,
        isCurrent = stage.speciesName == pokemon.name,
        onClick = { onPokemonClick(stage.speciesName) }
    )
}

/** A tappable sprite + name, highlighted when it's the Pokémon already on screen. Shared by the
 *  evolution chain and the Mega Evolution list so the two read as the same kind of link. B9: the
 *  caller resolves [displayName] (same "hoist to the caller" pattern as PokemonCard), since both
 *  call sites already have `speciesNames`/the current language in scope. */
@Composable
internal fun PokemonSpriteTile(
    displayName: String,
    id: Int,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            // Excludes the current Pokémon too, not just the placeholder id=0 case — tapping the
            // highlighted "you are here" stage in its own chain used to push a duplicate detail
            // screen of the page already on screen.
            .clickable(enabled = id != 0 && !isCurrent, onClick = onClick)
            .padding(8.dp)
    ) {
        PokemonSprite(
            id = id,
            contentDescription = displayName,
            modifier = Modifier.size(64.dp)
        )
        Text(
            displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false
        )
    }
}
