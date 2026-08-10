package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.bucketizeMatchups

/** Renders the weak-to/resists/immune groups for a defensive-multiplier map, each as a row of
 * type badge icons. Shared by the single-pokemon detail screen and the team matchup screen. */
@Composable
fun TypeMatchupGroups(multipliers: Map<String, Double>, modifier: Modifier = Modifier) {
    val buckets = bucketizeMatchups(multipliers)
    if (buckets.isEmpty()) {
        Text(stringResource(R.string.detail_no_notable_matchups), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        buckets.forEach { bucket ->
            Text(
                stringResource(bucket.labelRes),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bucket.types.forEach { typeName ->
                    TypeBadge(typeName, TypeIds.idOrNull(typeName), height = 22.dp)
                }
            }
        }
    }
}
