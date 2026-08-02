package com.mandallaz.pikadex.ui.list

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.ui.components.OptionsDialog
import com.mandallaz.pikadex.ui.components.PokemonCard
import com.mandallaz.pikadex.ui.components.SearchableListDialog
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.SmogonGen
import com.mandallaz.pikadex.util.SmogonTierLabels
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.toDisplayName

private enum class ActiveDialog { NONE, MOVE, ABILITY, FORMAT_GEN, FORMAT_TIER, SORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexListScreen(
    onPokemonClick: (String) -> Unit,
    onTeamClick: () -> Unit,
    onTypeTrianglesClick: () -> Unit,
    viewModel: PokedexListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val team by TeamRepository.team.collectAsState()
    var activeDialog by remember { mutableStateOf(ActiveDialog.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PikaDex") },
                actions = {
                    IconButton(onClick = onTypeTrianglesClick) {
                        Icon(Icons.Default.ChangeHistory, contentDescription = "Type triangles")
                    }
                    IconButton(onClick = onTeamClick) {
                        BadgedBox(badge = {
                            if (team.isNotEmpty()) Badge { Text("${team.size}") }
                        }) {
                            Icon(Icons.Default.Groups, contentDescription = "My team")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Name or number...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            val typeRowHalf = (uiState.typeOptions.size + 1) / 2
            val typeRows = listOf(
                uiState.typeOptions.take(typeRowHalf),
                uiState.typeOptions.drop(typeRowHalf)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                typeRows.forEach { rowTypes ->
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        lazyRowItems(rowTypes, key = { it.name }) { type ->
                            FilterChip(
                                selected = type.name in uiState.selectedTypes,
                                onClick = { viewModel.onTypeToggled(type.name) },
                                label = { TypeBadge(type.name, type.id ?: 0, height = 20.dp) }
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.showFavoritesOnly,
                    onClick = viewModel::onToggleFavoritesOnly,
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                )
                AssistChip(
                    onClick = {
                        viewModel.loadMoveOptionsIfNeeded()
                        activeDialog = ActiveDialog.MOVE
                    },
                    label = { Text(uiState.selectedMove?.toDisplayName() ?: "Move") }
                )
                AssistChip(
                    onClick = {
                        viewModel.loadAbilityOptionsIfNeeded()
                        activeDialog = ActiveDialog.ABILITY
                    },
                    label = { Text(uiState.selectedAbility?.toDisplayName() ?: "Ability") }
                )
                AssistChip(
                    onClick = { activeDialog = ActiveDialog.FORMAT_GEN },
                    label = { Text(uiState.selectedFormatGen?.label ?: "Format") }
                )
                AssistChip(
                    onClick = {
                        viewModel.loadTierOptionsIfNeeded()
                        activeDialog = ActiveDialog.FORMAT_TIER
                    },
                    label = {
                        Text(uiState.selectedFormatTier?.let { SmogonTierLabels.labelFor(it) } ?: "Tier")
                    }
                )
                AssistChip(
                    onClick = {
                        viewModel.loadBaseStatsIfNeeded()
                        activeDialog = ActiveDialog.SORT
                    },
                    label = { Text(uiState.sortStat?.label ?: "Sort") }
                )
                if (uiState.sortStat != null) {
                    IconButton(onClick = viewModel::toggleSortDirection) {
                        Icon(
                            imageVector = if (uiState.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (uiState.sortAscending) "Ascending" else "Descending"
                        )
                    }
                }
                if (uiState.hasActiveFilters) {
                    AssistChip(
                        onClick = viewModel::clearFilters,
                        label = { Text("Reset") },
                        leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.errorMessage != null && uiState.allPokemon.isEmpty() -> Text(
                        text = uiState.errorMessage ?: "",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    else -> {
                        if (uiState.isFilterLoading || uiState.isStatsLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.displayed, key = { it.name }) { resource ->
                                val id = resource.id ?: return@items
                                PokemonCard(
                                    id = id,
                                    name = resource.name,
                                    onClick = { onPokemonClick(resource.name) }
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AttributionFooter()
                            }
                        }
                    }
                }
            }
        }
    }

    when (activeDialog) {
        ActiveDialog.MOVE -> SearchableListDialog(
            title = "Choose a move",
            options = uiState.moveOptions,
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { move ->
                viewModel.onMoveSelected(move)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.ABILITY -> SearchableListDialog(
            title = "Choose an ability",
            options = uiState.abilityOptions,
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { ability ->
                viewModel.onAbilitySelected(ability)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.FORMAT_GEN -> OptionsDialog(
            title = "Choose a generation",
            options = listOf<SmogonGen?>(null) + Smogon.ALL_GENERATIONS,
            labelFor = { it?.label ?: "Clear format filter" },
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { gen ->
                viewModel.onFormatGenSelected(gen)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.FORMAT_TIER -> OptionsDialog(
            title = "Choose a tier (${uiState.effectiveFormatGen.label})",
            options = listOf<String?>(null) + uiState.formatTierOptions,
            labelFor = { it?.let { tier -> SmogonTierLabels.labelFor(tier) } ?: "Any tier" },
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { tier ->
                viewModel.onFormatTierSelected(tier)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.SORT -> OptionsDialog(
            title = "Sort by",
            options = listOf<SortStat?>(null) + SortStat.entries,
            labelFor = { it?.label ?: "No sorting" },
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { stat ->
                viewModel.onSortStatSelected(stat)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.NONE -> Unit
    }
}

@Composable
private fun AttributionFooter() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pokeapi.co")))
            },
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "Data provided by PokeAPI",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
