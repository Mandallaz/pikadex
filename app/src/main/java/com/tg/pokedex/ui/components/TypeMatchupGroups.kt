package com.tg.pokedex.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tg.pokedex.util.TypeIds
import com.tg.pokedex.util.bucketizeMatchups

/** Renders the weak-to/resists/immune groups for a defensive-multiplier map, each as a row of
 * type badge icons. Shared by the single-pokemon detail screen and the team matchup screen. */
@Composable
fun TypeMatchupGroups(multipliers: Map<String, Double>, modifier: Modifier = Modifier) {
    val buckets = bucketizeMatchups(multipliers)
    if (buckets.isEmpty()) {
        Text("No notable weaknesses or resistances.", style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        buckets.forEach { bucket ->
            Text(
                bucket.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                bucket.types.forEach { typeName ->
                    TypeBadge(typeName, TypeIds.of(typeName), height = 22.dp)
                }
            }
        }
    }
}
