package com.mandallaz.pikadex.ui.list.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.ui.components.localizedLabel
import com.mandallaz.pikadex.ui.components.localizedTierLabel
import com.mandallaz.pikadex.ui.list.PokedexListUiState
import com.mandallaz.pikadex.ui.list.STAT_KEY_TOTAL
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.toDisplayName

/** Bottom sheet holding every filter control except Sort — Types as one wrapping row (was two
 * independently-scrolling rows split by id, with no visible way to tell which row had e.g. Steel),
 * every control showing a clear selected/active state (was: identical AssistChip whether a filter
 * was set or not), and a note on how type selection combines (undocumented AND semantics). */
@Composable
internal fun FilterSheetContent(
    uiState: PokedexListUiState,
    onToggleFavoritesOnly: () -> Unit,
    onTypeToggled: (String) -> Unit,
    onOpenMove: () -> Unit,
    onOpenAbility: () -> Unit,
    onOpenFormat: () -> Unit,
    onOpenTier: () -> Unit,
    onOpenRarity: () -> Unit,
    onToggleCounterFilter: () -> Unit,
    onStatMinimumChanged: (String, Int) -> Unit
) {
    // verticalScroll: a plain Column here could overflow the sheet's available height in
    // landscape or at large font scales — 18 type chips plus 5 "other filters" chips is enough
    // content that everything past the first few type rows became completely unreachable, with no
    // way to scroll down to Favorites/Move/Ability/Format/Tier at all.
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.list_filters_label), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Text(
            stringResource(R.string.list_type_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (uiState.selectedTypes.size > 1) {
            Text(
                stringResource(R.string.list_type_all_selected_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            uiState.typeOptions.forEach { type ->
                val isSelected = type.name in uiState.selectedTypes
                FilterChip(
                    selected = isSelected,
                    onClick = { onTypeToggled(type.name) },
                    label = { TypeBadge(type.name, type.id ?: 0, height = 20.dp) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.list_selected_cd), modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }

        Text(
            stringResource(R.string.list_other_filters_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(
                label = stringResource(R.string.list_favorites_label),
                selected = uiState.showFavoritesOnly,
                onClick = onToggleFavoritesOnly,
                unselectedIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            SelectableChip(
                label = uiState.selectedMove?.toDisplayName() ?: stringResource(R.string.list_move_label),
                selected = uiState.selectedMove != null,
                onClick = onOpenMove
            )
            SelectableChip(
                label = uiState.selectedAbility?.toDisplayName() ?: stringResource(R.string.list_ability_label),
                selected = uiState.selectedAbility != null,
                onClick = onOpenAbility
            )
            SelectableChip(
                label = uiState.selectedFormatGen?.localizedLabel() ?: stringResource(R.string.list_format_label),
                selected = uiState.selectedFormatGen != null,
                onClick = onOpenFormat
            )
            SelectableChip(
                label = uiState.selectedFormatTier?.let { localizedTierLabel(it) } ?: stringResource(R.string.list_tier_label),
                selected = uiState.selectedFormatTier != null,
                onClick = onOpenTier
            )
            SelectableChip(
                label = uiState.rarityFilter?.label ?: stringResource(R.string.list_rarity_title),
                selected = uiState.rarityFilter != null,
                onClick = onOpenRarity
            )
            // Binary toggle (F33), not a dialog-backed chip like the others above — "counters any
            // triangle" is a single on/off predicate, same shape as Favorites.
            SelectableChip(
                label = stringResource(R.string.list_perfect_counter_label),
                selected = uiState.counterFilterActive,
                onClick = onToggleCounterFilter
            )
        }

        Text(
            stringResource(R.string.list_minimum_stats_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        if (uiState.isStatsLoading && uiState.baseStats.isEmpty()) {
            Text(
                stringResource(R.string.list_loading_base_stats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Every real stat (excludes DEX_NUMBER/NAME/TOTAL, which have no apiName) — reusing
            // SortStat's own list+labels instead of a second, easy-to-drift-apart stat name list.
            SortStat.entries.mapNotNull { stat -> stat.apiName?.let { stat to it } }.forEach { (stat, apiName) ->
                val minimum = uiState.statMinimums[apiName] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stat.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = minimum.toFloat(),
                        onValueChange = { onStatMinimumChanged(apiName, it.toInt()) },
                        valueRange = 0f..255f,
                        steps = 50, // (255 - 5) / 5: every 5 points, including both ends
                        enabled = uiState.baseStats.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (minimum > 0) "$minimum" else stringResource(R.string.list_any_value),
                        modifier = Modifier.widthIn(min = 36.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            // Outside the loop above on purpose: SortStat.TOTAL.apiName is null (it's a derived
            // sum, not a raw GraphQL field), which is exactly what the mapNotNull loop excludes it
            // for — see STAT_KEY_TOTAL's KDoc.
            val totalMinimum = uiState.statMinimums[STAT_KEY_TOTAL] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SortStat.TOTAL.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = totalMinimum.toFloat(),
                    onValueChange = { onStatMinimumChanged(STAT_KEY_TOTAL, it.toInt()) },
                    valueRange = 0f..720f,
                    steps = 71, // 10-point increments, the same range-to-step ratio as the per-stat sliders above
                    enabled = uiState.baseStats.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (totalMinimum > 0) "$totalMinimum" else stringResource(R.string.list_any_value),
                    modifier = Modifier.widthIn(min = 36.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** A chip that visibly shows whether it represents an active selection (a leading checkmark once
 * selected) instead of looking identical whether or not a value is set. */
@Composable
internal fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    unselectedIcon: (@Composable () -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = when {
            selected -> { { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.list_selected_cd), modifier = Modifier.size(18.dp)) } }
            unselectedIcon != null -> unselectedIcon
            else -> null
        }
    )
}
