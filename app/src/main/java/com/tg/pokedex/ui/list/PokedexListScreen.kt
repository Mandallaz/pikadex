package com.tg.pokedex.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tg.pokedex.data.TeamRepository
import com.tg.pokedex.ui.components.PokemonCard
import com.tg.pokedex.ui.components.SearchableListDialog
import com.tg.pokedex.ui.components.TypeBadge
import com.tg.pokedex.util.toDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexListScreen(
    onPokemonClick: (String) -> Unit,
    onTeamClick: () -> Unit,
    viewModel: PokedexListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val team by TeamRepository.team.collectAsState()
    var showMoveDialog by remember { mutableStateOf(false) }
    var showAbilityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PikaDex") },
                actions = {
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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                lazyRowItems(uiState.typeOptions, key = { it.name }) { type ->
                    FilterChip(
                        selected = uiState.selectedType == type.name,
                        onClick = {
                            viewModel.onTypeSelected(if (uiState.selectedType == type.name) null else type.name)
                        },
                        label = { TypeBadge(type.name, type.id ?: 0, height = 20.dp) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        viewModel.loadMoveOptionsIfNeeded()
                        showMoveDialog = true
                    },
                    label = { Text(uiState.selectedMove?.toDisplayName() ?: "Move") }
                )
                AssistChip(
                    onClick = {
                        viewModel.loadAbilityOptionsIfNeeded()
                        showAbilityDialog = true
                    },
                    label = { Text(uiState.selectedAbility?.toDisplayName() ?: "Ability") }
                )
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
                        if (uiState.isFilterLoading) {
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
                        }
                    }
                }
            }
        }
    }

    if (showMoveDialog) {
        SearchableListDialog(
            title = "Choose a move",
            options = uiState.moveOptions,
            onDismiss = { showMoveDialog = false },
            onSelect = { move ->
                viewModel.onMoveSelected(move)
                showMoveDialog = false
            }
        )
    }

    if (showAbilityDialog) {
        SearchableListDialog(
            title = "Choose an ability",
            options = uiState.abilityOptions,
            onDismiss = { showAbilityDialog = false },
            onSelect = { ability ->
                viewModel.onAbilitySelected(ability)
                showAbilityDialog = false
            }
        )
    }
}
