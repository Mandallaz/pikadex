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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
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
    viewModel: PokedexListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val displayedPokemon by viewModel.displayedPokemon.collectAsState()
    val team by TeamRepository.team.collectAsState()
    val favorites by FavoritesRepository.favorites.collectAsState()
    var activeDialog by remember { mutableStateOf(ActiveDialog.NONE) }
    // rememberSaveable: rotating the device with the filter sheet open used to dismiss it outright
    // (it reads everything it shows from the ViewModel, so nothing else is lost by restoring it).
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Filter/network errors used to be written into state and never shown anywhere — picking a
    // type filter that failed to load flashed a spinner and then silently left the list unchanged,
    // with no indication anything had gone wrong.
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            // A dialog or the filter sheet covers the whole screen, so a Snackbar underneath would
            // never be seen — picking Move/Ability/Tier with no network used to leave that full-screen
            // dialog reading "Loading..." forever with no visible error at all. Closing whatever's
            // open surfaces the Snackbar instead.
            activeDialog = ActiveDialog.NONE
            showFilterSheet = false
            if (uiState.allPokemon.isNotEmpty()) {
                // The master list failing to load at all is a persistent, full-screen condition
                // (the inline error branch below, with its own Retry) — auto-dismissing it here too
                // used to make it vanish the instant the 4-second Snackbar timed out, leaving a
                // permanently wrong "No Pokémon match your search and filters" empty state with no
                // way back short of force-killing the app. Only flash-and-clear errors that happen
                // once real data is already showing (a filter/sort/network hiccup).
                snackbarHostState.showSnackbar(message)
                viewModel.dismissError()
            }
        }
    }

    // Applying a sort should jump back to the top of the now-reordered grid — but LaunchedEffect
    // re-runs its body on every fresh entry into composition too, not only on a genuine key change,
    // so a plain `LaunchedEffect(sortStat, sortAscending) { scrollToItem(0) }` also fired (and wiped
    // the position `gridState` had just restored) on ordinary back-navigation from a detail screen
    // and on switching bottom-nav tabs and back. Tracking the last sort actually applied in
    // rememberSaveable (so it survives that same save/restore) distinguishes a real sort change from
    // just re-entering this screen with the same sort as before.
    var lastAppliedSortOrdinal by rememberSaveable { mutableStateOf(uiState.sortStat?.ordinal ?: -1) }
    var lastAppliedSortAscending by rememberSaveable { mutableStateOf(uiState.sortAscending) }
    LaunchedEffect(uiState.sortStat, uiState.sortAscending) {
        val newOrdinal = uiState.sortStat?.ordinal ?: -1
        if (newOrdinal != lastAppliedSortOrdinal || uiState.sortAscending != lastAppliedSortAscending) {
            gridState.scrollToItem(0)
        }
        lastAppliedSortOrdinal = newOrdinal
        lastAppliedSortAscending = uiState.sortAscending
    }

    Scaffold(
        // Team/Type Triangles access moved to the bottom navigation bar (see PokedexNavHost) — a
        // labelled, always-visible tab reads as a much clearer destination than an icon-only button
        // buried in this screen's own top bar, and it no longer disappears while browsing this list.
        topBar = { TopAppBar(title = { Text("PikaDex") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Name or number...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                singleLine = true
            )

            // Search + Filters + Sort + Reset — the search field is the only thing that must
            // always be visible; everything else used to permanently occupy ~36% of the screen
            // above the grid (two type-chip rows plus a filter-chip row that never scrolled away).
            // Collapsing type/move/ability/format/tier/favorites behind one "Filters" sheet gives
            // that space back to the actual Pokémon grid.
            // FlowRow, not Row: with a long sort label ("Sort: Dex number") plus the direction
            // toggle plus Reset, a plain Row squeezed the last chip until its text broke mid-word
            // ("Res/et") instead of letting it move to the next line.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                val filterCount = uiState.activeFilterCount
                FilterChip(
                    selected = filterCount > 0,
                    onClick = { showFilterSheet = true },
                    label = { Text(if (filterCount > 0) "Filters ($filterCount)" else "Filters") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                // FilterChip, not AssistChip: an active sort is exactly the same kind of "a filter
                // control has a non-default value set" state as the Filters button next to it, so
                // it gets the same visual treatment (tonal fill once selected) instead of always
                // looking like an inert, un-set button.
                FilterChip(
                    selected = uiState.sortStat != null,
                    onClick = {
                        viewModel.loadBaseStatsIfNeeded()
                        activeDialog = ActiveDialog.SORT
                    },
                    // "Sort: " prefix once a stat is picked — a bare "Attack" next to "Filters (2)"
                    // read as if it were itself a filter value, not what it's actually sorting by.
                    label = { Text(uiState.sortStat?.let { "Sort: ${it.label}" } ?: "Sort") }
                )
                if (uiState.sortStat != null) {
                    IconButton(onClick = viewModel::toggleSortDirection) {
                        Icon(
                            imageVector = if (uiState.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (uiState.sortAscending) {
                                "Sorted ascending — tap to sort descending"
                            } else {
                                "Sorted descending — tap to sort ascending"
                            }
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

            if (!uiState.isLoading && uiState.allPokemon.isNotEmpty()) {
                Text(
                    "${displayedPokemon.size} Pokémon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.errorMessage != null && uiState.allPokemon.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // A failed cold start used to be a genuine dead end — load() only ever ran
                        // once from init{}, so there was no way back into the app short of
                        // force-killing it, even after connectivity came back.
                        Button(onClick = viewModel::retryInitialLoad) { Text("Retry") }
                    }
                    displayedPokemon.isEmpty() -> EmptyResultsState(
                        hasActiveFilters = uiState.hasActiveFilters,
                        onResetFilters = {
                            viewModel.clearFilters()
                            viewModel.onSearchQueryChange("")
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedPokemon, key = { it.name }) { resource ->
                                val id = resource.id ?: return@items
                                val isInTeamAlready = team.any { it.name == resource.name }
                                val isTeamFull = team.size >= TeamRepository.MAX_SIZE
                                PokemonCard(
                                    id = id,
                                    name = resource.name,
                                    isFavorite = resource.name in favorites,
                                    isInTeam = isInTeamAlready,
                                    isTeamFull = isTeamFull,
                                    onClick = { onPokemonClick(resource.name) },
                                    onToggleTeam = {
                                        if (!isInTeamAlready && isTeamFull) {
                                            // The button used to just render disabled with zero
                                            // explanation of why tapping it did nothing.
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Your team is full (${TeamRepository.MAX_SIZE}/${TeamRepository.MAX_SIZE}). Remove one first.")
                                            }
                                        } else {
                                            TeamRepository.toggle(NamedApiResource(resource.name, "https://pokeapi.co/api/v2/pokemon/$id/"))
                                        }
                                    },
                                    onToggleFavorite = { FavoritesRepository.toggle(resource.name) }
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AttributionFooter()
                            }
                        }
                        // Drawn after (i.e. on top of) the grid, not before it — composed first, the
                        // spinner used to sit directly behind the opaque first row of cards and was
                        // never actually visible while a type/move/ability/tier filter was applying.
                        if (uiState.isFilterLoading || uiState.isStatsLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, sheetState = sheetState) {
            FilterSheetContent(
                uiState = uiState,
                onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
                onTypeToggled = viewModel::onTypeToggled,
                onOpenMove = {
                    viewModel.loadMoveOptionsIfNeeded()
                    activeDialog = ActiveDialog.MOVE
                },
                onOpenAbility = {
                    viewModel.loadAbilityOptionsIfNeeded()
                    activeDialog = ActiveDialog.ABILITY
                },
                onOpenFormat = { activeDialog = ActiveDialog.FORMAT_GEN },
                onOpenTier = {
                    viewModel.loadTierOptionsIfNeeded()
                    activeDialog = ActiveDialog.FORMAT_TIER
                }
            )
        }
    }

    when (activeDialog) {
        ActiveDialog.MOVE -> SearchableListDialog(
            title = "Choose a move",
            options = uiState.moveOptions,
            clearLabel = "Any move",
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { move ->
                viewModel.onMoveSelected(move)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.ABILITY -> SearchableListDialog(
            title = "Choose an ability",
            options = uiState.abilityOptions,
            clearLabel = "Any ability",
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { ability ->
                viewModel.onAbilitySelected(ability)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.FORMAT_GEN -> OptionsDialog(
            title = "Choose a generation",
            options = listOf<SmogonGen?>(null) + Smogon.ALL_GENERATIONS,
            labelFor = { it?.label ?: "Any format" },
            selected = uiState.selectedFormatGen,
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
            selected = uiState.selectedFormatTier,
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
            selected = uiState.sortStat,
            onDismiss = { activeDialog = ActiveDialog.NONE },
            onSelect = { stat ->
                viewModel.onSortStatSelected(stat)
                activeDialog = ActiveDialog.NONE
            }
        )

        ActiveDialog.NONE -> Unit
    }
}

/** Bottom sheet holding every filter control except Sort — Types as one wrapping row (was two
 * independently-scrolling rows split by id, with no visible way to tell which row had e.g. Steel),
 * every control showing a clear selected/active state (was: identical AssistChip whether a filter
 * was set or not), and a note on how type selection combines (undocumented AND semantics). */
@Composable
private fun FilterSheetContent(
    uiState: PokedexListUiState,
    onToggleFavoritesOnly: () -> Unit,
    onTypeToggled: (String) -> Unit,
    onOpenMove: () -> Unit,
    onOpenAbility: () -> Unit,
    onOpenFormat: () -> Unit,
    onOpenTier: () -> Unit
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
        Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Text(
            "Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (uiState.selectedTypes.size > 1) {
            Text(
                "Matching all selected types",
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
                        { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }

        Text(
            "Other filters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(
                label = "Favorites",
                selected = uiState.showFavoritesOnly,
                onClick = onToggleFavoritesOnly,
                unselectedIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            SelectableChip(
                label = uiState.selectedMove?.toDisplayName() ?: "Move",
                selected = uiState.selectedMove != null,
                onClick = onOpenMove
            )
            SelectableChip(
                label = uiState.selectedAbility?.toDisplayName() ?: "Ability",
                selected = uiState.selectedAbility != null,
                onClick = onOpenAbility
            )
            SelectableChip(
                label = uiState.selectedFormatGen?.label ?: "Format",
                selected = uiState.selectedFormatGen != null,
                onClick = onOpenFormat
            )
            SelectableChip(
                label = uiState.selectedFormatTier?.let { SmogonTierLabels.labelFor(it) } ?: "Tier",
                selected = uiState.selectedFormatTier != null,
                onClick = onOpenTier
            )
        }
    }
}

/** A chip that visibly shows whether it represents an active selection (a leading checkmark once
 * selected) instead of looking identical whether or not a value is set. */
@Composable
private fun SelectableChip(
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
            selected -> { { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) } }
            unselectedIcon != null -> unselectedIcon
            else -> null
        }
    )
}

@Composable
private fun EmptyResultsState(hasActiveFilters: Boolean, onResetFilters: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "No Pokémon match your search and filters.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (hasActiveFilters) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onResetFilters) { Text("Reset filters") }
        }
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
