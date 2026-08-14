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
import com.mandallaz.pikadex.ui.components.STAT_BAR_SCALE_MAX
import com.mandallaz.pikadex.ui.components.StatBar
import com.mandallaz.pikadex.util.StatColors
import com.mandallaz.pikadex.util.TOTAL
import com.mandallaz.pikadex.util.baseStatTotal
import kotlin.math.roundToInt

@Composable
internal fun BaseStatsCard(pokemon: PokemonDto, statPercentiles: Map<String, Double>) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.detail_base_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.detail_base_stats_subtitle, STAT_BAR_SCALE_MAX.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            pokemon.stats.orEmpty().forEach { stat ->
                val percentile = statPercentiles[stat.stat.name] ?: 0.5
                StatBar(statName = stat.stat.name, value = stat.baseStat, color = StatColors.forPercentile(percentile))
            }
            val total = pokemon.baseStatTotal()
            Text(
                stringResource(R.string.detail_stat_total, total),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            // Every individual stat gets a percentile-colored bar, but the total — the one
            // number people actually compare Pokémon by — was a bare figure with nothing to
            // judge it against, even though its percentile was already being computed and
            // then thrown away.
            statPercentiles[TOTAL]?.let { percentile ->
                Text(
                    stringResource(R.string.detail_stronger_than, (percentile * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    // Deliberately not StatColors.forPercentile: those hues are tuned to be
                    // read as a filled bar against the surface, and are nowhere near enough
                    // contrast for small text on either theme's background.
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
