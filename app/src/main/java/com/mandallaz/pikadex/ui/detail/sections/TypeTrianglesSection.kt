package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.TypeTriangle

/**
 * issue #16 (simplified 2026-08-09) — used to also list every triangle this Pokémon's typing
 * is merely a *member* of (`TypeTriangles.containing`, since removed as unused), collapsed behind
 * a "show all" past a small limit. Dropped per user feedback: sharing a type with one leg of a
 * triangle isn't a meaningful callout on its own, unlike being the loop's exact best counter
 * ([TypeTriangles.counteredBy]). With only ever a couple of counter matches at most (each
 * triangle's counter typing is fixed, and no typing counters many at once), the collapse/expand
 * complexity the old member list needed no longer earns its keep either — dropped alongside it.
 * The caller hides this card entirely when [counteredTriangles] is empty, so every call here has
 * at least one row to show.
 */
@Composable
internal fun TypeTrianglesCard(
    counteredTriangles: List<TypeTriangle>,
    onViewTypeTriangles: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.detail_type_triangles_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onViewTypeTriangles) {
                    Text(stringResource(R.string.detail_view_chart))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp)
                    )
                }
            }

            Text(
                stringResource(R.string.detail_best_counter_to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            counteredTriangles.forEachIndexed { index, triangle ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                TriangleRow(triangle)
            }
        }
    }
}

@Composable
private fun TriangleRow(triangle: TypeTriangle) {
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            triangle.types.forEach { type -> TypeBadge(type, TypeIds.idOrNull(type)) }
        }
        Text(
            // triangle.title itself is data-level (from util/TypeTriangles), out of this pass's
            // scope — same boundary as ViewModel error messages; only the suffix is screen chrome.
            text = triangle.title + " " + if (triangle.isPerfect) {
                stringResource(R.string.detail_triangle_perfect_suffix)
            } else {
                stringResource(R.string.detail_triangle_imperfect_suffix)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
