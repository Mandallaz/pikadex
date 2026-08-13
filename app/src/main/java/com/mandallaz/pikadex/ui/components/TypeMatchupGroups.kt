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
 * type badge icons. Shared by the single-pokemon detail screen and the team matchup screen.
 *
 * [highlightedTypes] — F93 follow-up: outlines any entry whose name is in this set (e.g. an
 * attacking type that only shows up here *because* of an active Tera preview) — empty for every
 * existing caller, which don't have anything to highlight. Ignored for an entry also in
 * [strikethroughTypes]: a removed weakness/resistance never gets the "added" border, regardless
 * of what the caller passes.
 *
 * [strikethroughTypes] — F93 follow-up: grays out and strikes any entry whose name is in this set
 * (a weakness/resistance the base typing had that the active Tera typing no longer does — see
 * [com.mandallaz.pikadex.util.matchupsForDisplay]) — empty for every existing caller. */
@Composable
fun TypeMatchupGroups(
    multipliers: Map<String, Double>,
    modifier: Modifier = Modifier,
    highlightedTypes: Set<String> = emptySet(),
    strikethroughTypes: Set<String> = emptySet()
) {
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
                    TypeBadge(
                        typeName,
                        TypeIds.idOrNull(typeName),
                        height = 22.dp,
                        strikethrough = typeName in strikethroughTypes,
                        bordered = typeName in highlightedTypes && typeName !in strikethroughTypes
                    )
                }
            }
        }
    }
}
