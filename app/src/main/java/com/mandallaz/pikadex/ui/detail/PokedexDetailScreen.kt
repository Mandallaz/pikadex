package com.mandallaz.pikadex.ui.detail

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.ShowdownSprites
import com.mandallaz.pikadex.ui.UiText
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.PokemonArtwork
import com.mandallaz.pikadex.ui.components.SearchableListDialog
import com.mandallaz.pikadex.ui.detail.sections.AbilitiesCard
import com.mandallaz.pikadex.ui.detail.sections.BaseStatsCard
import com.mandallaz.pikadex.ui.detail.sections.DetailHeaderSection
import com.mandallaz.pikadex.ui.detail.sections.DetailLoadingSkeleton
import com.mandallaz.pikadex.ui.detail.sections.EvolutionCard
import com.mandallaz.pikadex.ui.detail.sections.SmogonLinksCard
import com.mandallaz.pikadex.ui.detail.sections.TeamImpactCard
import com.mandallaz.pikadex.ui.detail.sections.TypeMatchupsCard
import com.mandallaz.pikadex.ui.detail.sections.TypeTrianglesCard
import com.mandallaz.pikadex.ui.detail.sections.moveSection
import com.mandallaz.pikadex.util.MoveCategory
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.LearnedMove
import com.mandallaz.pikadex.util.TypeTriangle
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.util.localizedOrEnglish
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.TeamImpactSummary
import com.mandallaz.pikadex.util.toDisplayName
import kotlinx.coroutines.launch

/**
 * Persists which move sections are open across Activity recreation (rotation, process death).
 *
 * A plain `remember` dropped this on every rotation: expand "Level Up", turn the phone, and the
 * section silently collapsed — the user lost their place in a list they had deliberately opened.
 * An enum `Set` isn't saveable as-is, so it round-trips through the constant enum names rather
 * than ordinals, which would silently re-map if [MoveCategory]'s declaration order ever changed.
 */
private val ExpandedCategoriesSaver = listSaver<MutableState<Set<MoveCategory>>, String>(
    save = { state -> state.value.map(MoveCategory::name) },
    restore = { names -> mutableStateOf(names.map(MoveCategory::valueOf).toSet()) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexDetailScreen(
    pokemonNameOrId: String,
    onBack: () -> Unit,
    onPokemonClick: (String) -> Unit,
    onViewTypeTriangles: () -> Unit,
    onCompare: (left: String, right: String) -> Unit,
    // Distinct from onPokemonClick, which *pushes* a new detail screen — used for cross-references
    // like evolution stages, where Back should return to the page you tapped from. This one
    // replaces the current back-stack entry instead (see PokedexNavHost.kt), so Back from a
    // swiped-through Pokémon always returns to wherever the user actually entered the detail flow
    // from, not back through every Pokémon swiped past on the way (issue #7).
    onNavigateAdjacent: (String) -> Unit,
    viewModel: PokedexDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Only the "Compare with…" picker (below) needs this at this outer scope — DetailContent
    // collects its own copy for the rest of the screen's species/game-data localization.
    val language by LanguageSettings.currentLanguage.collectAsState()
    val team by viewModel.team.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isCryPlaying by viewModel.isCryPlaying.collectAsState()
    val context = LocalContext.current
    val isInTeam = uiState.pokemon?.let { p -> team.any { it.name == p.name } } ?: false
    val isTeamFull = team.size >= TeamRepository.MAX_SIZE
    val isFavorite = uiState.pokemon?.let { p -> favorites.contains(p.name) } ?: false
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // rememberSaveable: rotating the screen used to silently drop back to the normal artwork.
    var shiny by rememberSaveable { mutableStateOf(false) }
    // F38: off by default — an animated GIF looping from the moment the screen opens would be a
    // surprising default for every visit, not just something a user opts into.
    var animated by rememberSaveable { mutableStateOf(false) }
    var showCompareDialog by rememberSaveable { mutableStateOf(false) }
    // Resolved here, in the composable body, not inside the snackbar's coroutineScope.launch{}
    // lambda below — stringResource() is a @Composable function and that lambda isn't one.
    val teamFullMessage = stringResource(R.string.detail_team_full_snackbar, TeamRepository.MAX_SIZE, TeamRepository.MAX_SIZE)

    LaunchedEffect(pokemonNameOrId) { viewModel.load(pokemonNameOrId) }

    // issue #2 (revised 2026-08-09) — the "team coverage impact" card lives inline on the
    // page now, not behind a button, so it needs its own trigger. Keyed on the Pokémon and the
    // team's membership (not just its size — swapping one member for another leaves the size
    // unchanged but should still recompute) so it reruns exactly when the card's own visibility
    // condition below could have changed. loadTeamImpact() is self-gating on that same condition,
    // so calling it unconditionally here and clearing otherwise keeps the two checks from drifting
    // apart into two slightly different rules for the same thing.
    val teamMembership = team.map { it.name }
    LaunchedEffect(uiState.pokemon?.name, teamMembership) {
        if (uiState.pokemon != null && team.isNotEmpty() && team.size < TeamRepository.MAX_SIZE) {
            viewModel.loadTeamImpact()
        } else {
            viewModel.clearTeamImpact()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PikaDexTopBar(
                // No title text here: the name is already shown prominently right under the
                // sprite (DetailContent's header), large and centered — repeating it in the top
                // bar's titleLarge style just wrapped to two lines in the bar's fixed 48dp height
                // and overlapped the row below it, adding clutter rather than information.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    if (uiState.pokemon != null) {
                        IconButton(
                            // Stays enabled when the team is full and explains itself instead, the
                            // same way the Pokédex grid's own add button does — a disabled top-bar
                            // icon here just did nothing on tap, with no hint that the reason was a
                            // full team rather than a broken button.
                            onClick = {
                                // The result, not a re-derived isTeamFull, decides whether this
                                // was actually rejected — see TeamRepository.ToggleResult.
                                if (viewModel.toggleTeamMembership() == TeamRepository.ToggleResult.RejectedTeamFull) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(teamFullMessage)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                                contentDescription = if (isInTeam) {
                                    stringResource(R.string.detail_remove_from_team)
                                } else {
                                    stringResource(R.string.detail_add_to_team)
                                },
                                tint = if (!isInTeam && isTeamFull) {
                                    LocalContentColor.current.copy(alpha = 0.38f)
                                } else {
                                    LocalContentColor.current
                                }
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) {
                                    stringResource(R.string.detail_remove_from_favorites)
                                } else {
                                    stringResource(R.string.detail_add_to_favorites)
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadCompareCandidatesIfNeeded()
                                showCompareDialog = true
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = stringResource(R.string.detail_compare_with))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val previousName = uiState.previousPokemonName
        val nextName = uiState.nextPokemonName
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // Swipe left/right to move to the adjacent Pokémon without backing out to the list
                // and re-selecting. Keyed on the two names (not just pokemonNameOrId) so a fresh
                // gesture detector picks up new thresholds once load() resolves them, rather than
                // capturing null forever from before the fetch completed.
                .pointerInput(pokemonNameOrId, previousName, nextName) {
                    val thresholdPx = with(density) { 80.dp.toPx() }
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onDragEnd = {
                            when {
                                accumulatedDrag <= -thresholdPx && nextName != null -> onNavigateAdjacent(nextName)
                                accumulatedDrag >= thresholdPx && previousName != null -> onNavigateAdjacent(previousName)
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                    }
                }
        ) {
            val pokemon = uiState.pokemon
            val species = uiState.species
            when {
                // A bare spinner told the user "something is happening" but nothing about *what's
                // about to appear* — a skeleton that echoes the actual layout (artwork circle,
                // name bar, stat rows) sets that expectation immediately and feels faster even at
                // the same real load time.
                uiState.isLoading -> DetailLoadingSkeleton()
                uiState.errorMessage != null || pokemon == null || species == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        // uiState.errorMessage is a UiText (B13) — resolves through the app's picked
                        // language, same as the "not found" fallback below.
                        text = uiState.errorMessage?.resolve() ?: stringResource(R.string.detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    // load() resets its internal "already attempted" guard on failure specifically
                    // so a retry can succeed, but nothing ever called load() a second time — the
                    // only call site is keyed on the pokemon name/id, which never changes for this
                    // screen instance, so regaining network did nothing until the user backed out
                    // and re-entered the screen.
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(onClick = { viewModel.load(pokemonNameOrId) }) { Text(stringResource(R.string.detail_retry)) }
                    }
                }
                else -> DetailContent(
                    pokemon = pokemon,
                    species = species,
                    evolutionChain = uiState.evolutionChain,
                    typeMatchups = uiState.typeMatchups,
                    abilityDescriptions = uiState.abilityDescriptions,
                    counteredTriangles = uiState.counteredTriangles,
                    moveInfo = uiState.moveInfo,
                    statPercentiles = uiState.statPercentiles,
                    formVersionGroup = uiState.formVersionGroup,
                    groupedMoves = uiState.groupedMoves,
                    shiny = shiny,
                    animated = animated,
                    onToggleShiny = { shiny = !shiny },
                    onToggleAnimated = { animated = !animated },
                    isCryPlaying = isCryPlaying,
                    // uiState.pokemon is non-null here (this whole branch is gated on `pokemon`
                    // above), but that's `pokemon`, not `uiState.pokemon` — same underlying value,
                    // captured locally so playCry doesn't need its own null check.
                    onPlayCry = { viewModel.playCry(context, pokemon.id) },
                    showTeamImpactCard = shouldShowTeamImpactCard(team, isInTeam),
                    isTeamImpactLoading = uiState.isTeamImpactLoading,
                    teamImpactError = uiState.teamImpactError,
                    teamImpact = uiState.teamImpact,
                    onPokemonClick = onPokemonClick,
                    onViewTypeTriangles = onViewTypeTriangles,
                    speciesNames = uiState.speciesNames,
                    moveLocalizedNames = uiState.moveLocalizedNames,
                    abilityLocalizedNames = uiState.abilityLocalizedNames
                )
            }

            // The swipe gesture alone isn't discoverable (nothing on screen hints it exists) and
            // isn't reachable without touch (TalkBack, a hardware keyboard) — these cover both.
            // Shown only when that direction actually has a target, same as the swipe itself
            // no-ops at either end of the list. Pinned level with the sprite (DetailContent's
            // header Column: 24dp padding + a 200dp artwork, so the artwork's own vertical center
            // sits 124dp below this Box's top edge) rather than centered on the whole screen,
            // where it used to land on top of scrolling body text (flavor text, a stat row...)
            // lower down the page — wrapped in a small elevated circle, the same "floating over
            // content" affordance a FAB uses, so it still reads as a control and not a stray icon
            // drawn over whatever's underneath.
            if (previousName != null) {
                Surface(
                    onClick = { onNavigateAdjacent(previousName) },
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 104.dp).size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.detail_previous_pokemon))
                    }
                }
            }
            if (nextName != null) {
                Surface(
                    onClick = { onNavigateAdjacent(nextName) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 104.dp).size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.detail_next_pokemon))
                    }
                }
            }
        }
    }

    val currentPokemon = uiState.pokemon
    if (showCompareDialog && currentPokemon != null) {
        SearchableListDialog(
            title = stringResource(R.string.detail_compare_with),
            options = uiState.compareCandidates.filterNot { it == currentPokemon.name },
            displayName = { it.localizedDisplayName(uiState.speciesNames, language) },
            onDismiss = { showCompareDialog = false },
            onSelect = { name ->
                if (name != null) onCompare(currentPokemon.name, name)
                showCompareDialog = false
            }
        )
    }

}

// Internal rather than private: instrumented tests render this directly with fake DTOs, rather
// than driving the whole screen through a real ViewModel/network fetch.
@Composable
internal fun DetailContent(
    pokemon: PokemonDto,
    species: PokemonSpeciesDto,
    evolutionChain: com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto?,
    typeMatchups: Map<String, Double>,
    abilityDescriptions: Map<String, String>,
    counteredTriangles: List<TypeTriangle>,
    moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo>,
    statPercentiles: Map<String, Double>,
    formVersionGroup: String?,
    groupedMoves: Map<MoveCategory, List<LearnedMove>>,
    shiny: Boolean,
    animated: Boolean,
    onToggleShiny: () -> Unit,
    onToggleAnimated: () -> Unit,
    isCryPlaying: Boolean,
    onPlayCry: () -> Unit,
    showTeamImpactCard: Boolean,
    isTeamImpactLoading: Boolean,
    teamImpactError: UiText?,
    teamImpact: TeamImpactSummary?,
    onPokemonClick: (String) -> Unit,
    onViewTypeTriangles: () -> Unit,
    // B9 — defaulted so existing instrumented-test call sites (rendering this directly with fake
    // DTOs, no ViewModel) keep compiling unchanged; real usage always passes the ViewModel's
    // uiState.speciesNames.
    speciesNames: Map<String, Map<String, String>> = emptyMap(),
    // B11 — same defaulting reasoning as speciesNames above.
    moveLocalizedNames: Map<String, Map<String, String>> = emptyMap(),
    abilityLocalizedNames: Map<String, Map<String, String>> = emptyMap()
) {
    // F35 — game-data axis: genus/flavor text below read whichever language this resolves to,
    // falling back to English wherever the chosen language's entry is missing.
    val gameDataLanguage by LanguageSettings.currentLanguage.collectAsState()

    // Each category's moves are computed once per load, off the main thread, in the ViewModel
    // (see PokedexDetailUiState.groupedMoves) rather than here — this is the exact same
    // grouping/sorting work regardless of whether the section is expanded, and for a pokemon with
    // a large moveset (e.g. Mew) it was real work to redo on the main thread. Which sections are
    // expanded is still tracked here rather than inside each section, since expanded rows need to
    // be items() in *this* LazyColumn rather than a nested non-lazy Column (composing ~250 rows in
    // one non-lazy Column, all at once on expand, was the actual performance problem —
    // LazyColumn only composes what's on/near screen).
    val levelUpMoves = groupedMoves[MoveCategory.LEVEL_UP].orEmpty()
    val machineMoves = groupedMoves[MoveCategory.MACHINE].orEmpty()
    val eggMoves = groupedMoves[MoveCategory.EGG].orEmpty()
    val tutorMoves = groupedMoves[MoveCategory.TUTOR].orEmpty()
    var expandedCategories by rememberSaveable(saver = ExpandedCategoriesSaver) {
        mutableStateOf(emptySet<MoveCategory>())
    }
    fun toggle(category: MoveCategory) {
        expandedCategories = if (category in expandedCategories) {
            expandedCategories - category
        } else {
            expandedCategories + category
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            DetailHeaderSection(
                pokemon = pokemon,
                species = species,
                speciesNames = speciesNames,
                gameDataLanguage = gameDataLanguage,
                shiny = shiny,
                animated = animated,
                onToggleShiny = onToggleShiny,
                onToggleAnimated = onToggleAnimated,
                isCryPlaying = isCryPlaying,
                onPlayCry = onPlayCry
            )
        }

        item {
            val flavorText = species.flavorTextEntries.localizedOrEnglish(gameDataLanguage) { it.language.name }?.flavorText
            if (flavorText != null) {
                Text(
                    // PokeAPI's raw flavor text carries over an old in-game font quirk where "é"
                    // renders as a distinct glyph the games' original text assumed would be
                    // uppercased along with the rest of "POKé" -- this shows up untouched as literal
                    // "POKéMON" in many entries instead of "Pokémon".
                    text = flavorText.replace('\n', ' ').replace('\u000C', ' ').replace("POKéMON", "Pokémon"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.detail_height), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.detail_height_value, "${pokemon.height / 10.0}"))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.detail_weight), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.detail_weight_value, "${pokemon.weight / 10.0}"))
                }
                val eggGroups = species.eggGroups.orEmpty()
                if (eggGroups.isNotEmpty()) {
                    val undiscoveredLabel = stringResource(R.string.detail_egg_group_undiscovered)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.detail_egg_groups), style = MaterialTheme.typography.labelLarge)
                        Text(eggGroups.joinToString(", ") { eggGroupDisplayName(it.name, undiscoveredLabel) })
                    }
                }
            }
        }

        item {
            BaseStatsCard(pokemon, statPercentiles)
        }

        item {
            AbilitiesCard(pokemon, abilityDescriptions, abilityLocalizedNames, gameDataLanguage)
        }

        item {
            TypeMatchupsCard(typeMatchups)
        }

        // issue #14 — Evolution moved up to sit right after the core stat/ability/matchup
        // cluster (Base Stats/Abilities/Type Matchups), ahead of Team Impact/Type Triangles/Smogon
        // rather than after them — those 3 keep their existing relative order among themselves,
        // just now following Evolution instead of leading it.
        // issue #19 — every alternate form of this species (Mega, Gigantamax, one-off special
        // forms like Ursaluna Bloodmoon...), not just Megas — see SpeciesDto.otherForms.
        val otherForms = species.otherForms
        if (evolutionChain != null || otherForms.isNotEmpty()) {
            item {
                EvolutionCard(pokemon, species, evolutionChain, onPokemonClick, speciesNames, gameDataLanguage)
            }
        }

        // issue #2 — only present while there's an active team with room to grow; a full or
        // empty team, or a Pokémon already on the roster, has nothing meaningful to preview (see
        // PokedexDetailScreen's showTeamImpactCard condition and PokedexDetailViewModel.loadTeamImpact's
        // matching self-gate).
        if (showTeamImpactCard) {
            item {
                TeamImpactCard(isTeamImpactLoading, teamImpactError, teamImpact)
            }
        }

        if (counteredTriangles.isNotEmpty()) {
            item {
                TypeTrianglesCard(counteredTriangles, onViewTypeTriangles)
            }
        }

        item {
            SmogonLinksCard(
                pokemonName = pokemon.name,
                speciesGeneration = species.generation.name,
                formVersionGroup = formVersionGroup
            )
        }

        moveSection(
            MoveCategory.LEVEL_UP, levelUpMoves, moveInfo,
            expanded = MoveCategory.LEVEL_UP in expandedCategories,
            moveLocalizedNames = moveLocalizedNames, language = gameDataLanguage
        ) { toggle(MoveCategory.LEVEL_UP) }
        moveSection(
            MoveCategory.MACHINE, machineMoves, moveInfo,
            expanded = MoveCategory.MACHINE in expandedCategories,
            moveLocalizedNames = moveLocalizedNames, language = gameDataLanguage
        ) { toggle(MoveCategory.MACHINE) }
        moveSection(
            MoveCategory.EGG, eggMoves, moveInfo,
            expanded = MoveCategory.EGG in expandedCategories,
            moveLocalizedNames = moveLocalizedNames, language = gameDataLanguage
        ) { toggle(MoveCategory.EGG) }
        moveSection(
            MoveCategory.TUTOR, tutorMoves, moveInfo,
            expanded = MoveCategory.TUTOR in expandedCategories,
            moveLocalizedNames = moveLocalizedNames, language = gameDataLanguage
        ) { toggle(MoveCategory.TUTOR) }

        item { Spacer(modifier = Modifier.size(24.dp)) }
    }
}

/** "attack" -> "Attack", "special-defense" -> "Sp. Def" — matches [SortStat]'s own labels (the
 *  Pokédex sort dialog) rather than a second lookup table for the same six stat names; the real
 *  call site passes [SortStat]'s actual localized labels in, this is only the pure-function/test
 *  default. */
private val defaultStatNames = mapOf(
    "hp" to "HP", "attack" to "Attack", "defense" to "Defense",
    "special-attack" to "Sp. Atk", "special-defense" to "Sp. Def", "speed" to "Speed"
)

/** B30 — every localized string/template [moveStatsLabel]/[moveMetaLabel] need, gathered once by
 *  the composable call site ([com.mandallaz.pikadex.ui.detail.sections.MoveRow]) via
 *  `stringResource` and threaded through as data — same "pass localized strings as parameters
 *  with English defaults" pattern B11 used for [eggGroupDisplayName], so these stay plain
 *  (non-`@Composable`) functions the existing unit tests call directly; the defaults below
 *  reproduce the exact English text this file used to hardcode, so those tests keep passing
 *  unchanged. Templates use `%1$s`/`%1$d`-style positional placeholders (resolved via
 *  [String.format], same convention Android string resources use) rather than word-by-word
 *  fragments, so a translation can reorder a whole phrase naturally instead of being constrained
 *  to English word order — e.g. `detail_drains`'s French translation is "Draine %1$d %% des
 *  dégâts", not a literal "Drains"-then-number-then-"dealt" concatenation.
 *
 *  Move ailment names ([PokeApiGraphQLDataSource.MoveInfo.ailment], e.g. "paralysis") are
 *  deliberately NOT covered here: translating that would mean a second, much larger open-ended
 *  lookup table (~20 ailment values) — a different shape of problem than this bundle's fixed set
 *  of templates — so they stay English API tokens for now, consistent with other raw API category
 *  names this app already shows untranslated (e.g. egg group names like "Monster"/"Water1", not
 *  covered by [eggGroupDisplayName] either). */
internal data class MoveLabels(
    val physical: String = "Physical",
    val special: String = "Special",
    val status: String = "Status",
    val dash: String = "—",
    val line: String = "%1\$s · Power %2\$s · Accuracy %3\$s · PP %4\$s",
    val lineWithPriority: String = "%1\$s · Power %2\$s · Accuracy %3\$s · PP %4\$s · Priority %5\$s",
    val always: String = "Always",
    val ailmentChance: String = "%1\$d%%",
    val statChangeChance: String = "%1\$d%% chance: ",
    val critRate: String = "Crit rate +%1\$d",
    val drains: String = "Drains %1\$d%% dealt",
    val recoil: String = "Recoil %1\$d%% dealt",
    val heals: String = "Heals %1\$d%% max HP",
    val flinchChance: String = "%1\$d%% Flinch",
    val statNames: Map<String, String> = defaultStatNames
)

internal fun moveStatsLabel(info: PokeApiGraphQLDataSource.MoveInfo, labels: MoveLabels = MoveLabels()): String {
    val category = when (info.damageClass) {
        "physical" -> labels.physical
        "special" -> labels.special
        else -> labels.status
    }
    val power = info.power?.toString() ?: labels.dash
    val accuracy = info.accuracy?.let { "$it%" } ?: labels.dash
    val pp = info.pp?.toString() ?: labels.dash
    // Priority 0 is the overwhelming majority of moves (turn order follows Speed alone) — worth
    // calling out only when it actually changes turn order, same "don't show a default" reasoning
    // as moveMetaLabel below.
    return if (info.priority != 0) {
        String.format(labels.lineWithPriority, category, power, accuracy, pp, signed(info.priority))
    } else {
        String.format(labels.line, category, power, accuracy, pp)
    }
}

/** F37: a compact line of competitive info PokeAPI's `movemeta`/`movemetastatchanges` carries but
 *  every move row ignored until now — critical-hit rate, secondary status ailment + its chance,
 *  drain/recoil or self-heal, flinch chance, and stat changes + their chance. Returns null (render
 *  nothing) when a move has none of these, which is most moves — MoveInfo's 0/"none" defaults mean
 *  "no effect" (see its own doc), so a wall of "0% chance, 0 drain..." would be actively misleading
 *  filler on the common case rather than useful density. */
internal fun moveMetaLabel(info: PokeApiGraphQLDataSource.MoveInfo, labels: MoveLabels = MoveLabels()): String? {
    val parts = buildList {
        if (info.ailment != "none") {
            val chance = if (info.ailmentChance > 0) String.format(labels.ailmentChance, info.ailmentChance) else labels.always
            add("$chance ${info.ailment.toDisplayName()}")
        }
        if (info.statChanges.isNotEmpty()) {
            val chance = if (info.statChangeChance > 0) String.format(labels.statChangeChance, info.statChangeChance) else ""
            val changes = info.statChanges.joinToString(", ") { (stat, change) ->
                "${signed(change)} ${statDisplayName(stat, labels.statNames)}"
            }
            add("$chance$changes")
        }
        if (info.critRate > 0) add(String.format(labels.critRate, info.critRate))
        if (info.drain > 0) add(String.format(labels.drains, info.drain))
        if (info.drain < 0) add(String.format(labels.recoil, -info.drain))
        if (info.healing > 0) add(String.format(labels.heals, info.healing))
        if (info.flinchChance > 0) add(String.format(labels.flinchChance, info.flinchChance))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun statDisplayName(apiName: String, statNames: Map<String, String>): String =
    statNames[apiName] ?: apiName.toDisplayName()

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

/** The "team coverage impact" card is only worth showing while there's an active team with room to
 *  grow and this Pokémon isn't already on it — a full or empty team, or one already containing this
 *  Pokémon (loadTeamImpact() bails out early in that case since adding it would change nothing),
 *  has nothing meaningful to preview, and would otherwise render the card's title over a permanently
 *  empty body. Internal, not private, so it's unit-testable directly. */
internal fun shouldShowTeamImpactCard(team: List<NamedApiResource>, isInTeam: Boolean): Boolean =
    team.isNotEmpty() && team.size < TeamRepository.MAX_SIZE && !isInTeam

/** F38: which Showdown sprite URL (if any) [PokemonArtwork] should show when animated is on —
 *  the shiny variant when the shiny toggle is also active, falling back to the regular animated
 *  sprite if this Pokémon has no animated shiny (a real coverage gap, not an error), and null when
 *  there's no Showdown sprite at all so the caller's own static-artwork fallback chain takes over. */
internal fun selectShowdownUrl(shiny: Boolean, showdown: ShowdownSprites?): String? =
    showdown?.let { if (shiny) it.frontShiny ?: it.frontDefault else it.frontDefault }

/** PokeAPI's own name for "can't breed" is the literal string "no-eggs" — toDisplayName() would
 *  render that as "No Eggs", which reads like a typo rather than the actual game term. Internal,
 *  not private, so it's unit-testable directly. */
// B11 — undiscoveredLabel defaulted to the English literal so the existing JVM unit test
// (PokedexDetailScreenTest, no Compose runtime) keeps calling this as a pure function; the real
// call site in DetailContent passes stringResource(R.string.detail_egg_group_undiscovered).
internal fun eggGroupDisplayName(name: String, undiscoveredLabel: String = "Undiscovered"): String = when (name) {
    "no-eggs" -> undiscoveredLabel
    else -> name.toDisplayName()
}

