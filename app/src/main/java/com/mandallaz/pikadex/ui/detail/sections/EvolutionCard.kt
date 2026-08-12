package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.util.evolutionPaths
import com.mandallaz.pikadex.util.localizedDisplayName

// issue #14 — Evolution moved up to sit right after the core stat/ability/matchup
// cluster (Base Stats/Abilities/Type Matchups), ahead of Team Impact/Type Triangles/Smogon
// rather than after them — those 3 keep their existing relative order among themselves,
// just now following Evolution instead of leading it.
// issue #19 — every alternate form of this species (Mega, Gigantamax, one-off special
// forms like Ursaluna Bloodmoon...), not just Megas — see SpeciesDto.otherForms.
@Composable
internal fun EvolutionCard(
    pokemon: PokemonDto,
    species: PokemonSpeciesDto,
    evolutionChain: EvolutionChainDto?,
    onPokemonClick: (String) -> Unit,
    speciesNames: Map<String, Map<String, String>>,
    gameDataLanguage: String
) {
    val otherForms = species.otherForms(pokemon.name)
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.detail_evolution_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val paths = remember(evolutionChain) {
                evolutionChain?.let { evolutionPaths(it.chain) }.orEmpty()
            }
            if (paths.all { it.size <= 1 }) {
                Text(
                    stringResource(R.string.detail_no_evolution),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                paths.forEach { path ->
                    // FlowRow, not a horizontalScroll Row: a 3-stage chain (the most
                    // common case) didn't fit on one line and had no scroll affordance,
                    // so the last stage just looked clipped off — wrapping to a second
                    // line means every stage stays visible instead of silently hidden.
                    // Grouping "arrow + its destination stage" as one FlowRow child (not
                    // stage-by-stage) means wrapping never splits an arrow from what it
                    // points to.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        path.forEachIndexed { index, stage ->
                            if (index > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                        stage.conditionLabel?.let {
                                            Text(it.resolve(), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    EvolutionStageBox(stage, pokemon, onPokemonClick, speciesNames, gameDataLanguage)
                                }
                            } else {
                                EvolutionStageBox(stage, pokemon, onPokemonClick, speciesNames, gameDataLanguage)
                            }
                        }
                    }
                }
            }

            if (otherForms.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    stringResource(R.string.detail_other_forms_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                // Deliberately doesn't say *how* each is obtained — PokeAPI's
                // evolution-chain data doesn't cover these at all (Mega/Gigantamax are
                // battle-only forms, not evolutions; one-off forms like Ursaluna
                // Bloodmoon come from a species-specific in-game method the API has no
                // field for), so this only confirms the form exists rather than
                // guessing or fabricating an acquisition method.
                Text(
                    stringResource(R.string.detail_other_forms_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    otherForms.forEach { variety ->
                        PokemonSpriteTile(
                            displayName = variety.pokemon.name.localizedDisplayName(speciesNames, gameDataLanguage),
                            id = variety.pokemon.id ?: 0,
                            isCurrent = variety.pokemon.name == pokemon.name,
                            onClick = { onPokemonClick(variety.pokemon.name) }
                        )
                    }
                }
            }
        }
    }
}
