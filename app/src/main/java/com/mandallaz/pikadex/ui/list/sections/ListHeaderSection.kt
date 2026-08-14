package com.mandallaz.pikadex.ui.list.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.localizedLabel
import com.mandallaz.pikadex.ui.list.PokedexListUiState

@Composable
internal fun ListHeader(
    uiState: PokedexListUiState,
    displayedCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSort: () -> Unit,
    onToggleSortDirection: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.list_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.list_clear_search_cd))
                    }
                }
            } else {
                null
            },
            singleLine = true
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            val filterCount = uiState.activeFilterCount
            FilterChip(
                selected = filterCount > 0,
                onClick = onOpenFilters,
                label = {
                    Text(
                        if (filterCount > 0) {
                            stringResource(R.string.list_filters_label_count, filterCount)
                        } else {
                            stringResource(R.string.list_filters_label)
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = uiState.sortStat != null,
                onClick = onOpenSort,
                label = {
                    Text(
                        uiState.sortStat?.let { stringResource(R.string.list_sort_label_with_stat, it.localizedLabel()) }
                            ?: stringResource(R.string.list_sort_label)
                    )
                }
            )
            if (uiState.sortStat != null) {
                IconButton(onClick = onToggleSortDirection) {
                    Icon(
                        imageVector = if (uiState.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = if (uiState.sortAscending) {
                            stringResource(R.string.list_sort_ascending_cd)
                        } else {
                            stringResource(R.string.list_sort_descending_cd)
                        }
                    )
                }
            }
            if (uiState.hasActiveFilters) {
                AssistChip(
                    onClick = onClearFilters,
                    label = { Text(stringResource(R.string.list_reset)) },
                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
                )
            }
        }

        if (!uiState.isLoading && uiState.allPokemon.isNotEmpty()) {
            Text(
                stringResource(R.string.list_result_count, displayedCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
