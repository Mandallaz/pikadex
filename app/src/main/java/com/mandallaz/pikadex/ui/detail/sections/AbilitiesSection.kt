package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.util.localizedDisplayName

@Composable
internal fun AbilitiesCard(
    pokemon: PokemonDto,
    abilityDescriptions: Map<String, String>,
    abilityLocalizedNames: Map<String, Map<String, String>>,
    gameDataLanguage: String
) {
    // Every other card on this screen pads all 4 sides (16dp) for an even gap above/below;
    // this one only padded horizontally, so it sat flush against the Base Stats card above
    // and Type Matchups below it — the one place on the page with no breathing room.
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.detail_abilities_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val hiddenSuffix = stringResource(R.string.detail_ability_hidden_suffix)
            pokemon.abilities.orEmpty().sortedBy { it.slot }.forEach { slot ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = slot.ability.name.localizedDisplayName(abilityLocalizedNames, gameDataLanguage) +
                            if (slot.isHidden) " $hiddenSuffix" else "",
                        fontWeight = FontWeight.Medium
                    )
                    abilityDescriptions[slot.ability.name]?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
