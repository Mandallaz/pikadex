package com.mandallaz.pikadex.ui.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.ShowdownSprites
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.PokemonArtwork
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.ui.components.SearchableListDialog
import com.mandallaz.pikadex.ui.components.StatBar
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.MoveCategory
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.StatColors
import com.mandallaz.pikadex.util.TypeColors
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.LearnedMove
import com.mandallaz.pikadex.util.TypeTriangle
import com.mandallaz.pikadex.util.evolutionPaths
import com.mandallaz.pikadex.util.openExternalLink
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.TeamImpactSummary
import com.mandallaz.pikadex.util.toDisplayName
import kotlin.math.roundToInt
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                        snackbarHostState.showSnackbar(
                                            "Your team is full (${TeamRepository.MAX_SIZE}/${TeamRepository.MAX_SIZE}). Remove one first."
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                                contentDescription = if (isInTeam) "Remove from team" else "Add to team",
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
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadCompareCandidatesIfNeeded()
                                showCompareDialog = true
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Compare with…")
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
                        text = uiState.errorMessage ?: "Pokémon not found.",
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
                        Button(onClick = { viewModel.load(pokemonNameOrId) }) { Text("Retry") }
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
                    onViewTypeTriangles = onViewTypeTriangles
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
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Pokémon")
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
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Pokémon")
                    }
                }
            }
        }
    }

    val currentPokemon = uiState.pokemon
    if (showCompareDialog && currentPokemon != null) {
        SearchableListDialog(
            title = "Compare with…",
            options = uiState.compareCandidates.filterNot { it == currentPokemon.name },
            onDismiss = { showCompareDialog = false },
            onSelect = { name ->
                if (name != null) onCompare(currentPokemon.name, name)
                showCompareDialog = false
            }
        )
    }

}

/** Placeholder that echoes [DetailContent]'s actual layout (artwork circle, name/genus bars, a
 *  handful of stat rows) instead of a bare spinner — sets the right expectation for what's about
 *  to load in, and reads as faster even at an identical real load time. A gentle alpha pulse (not
 *  a full shimmer sweep) is enough to read as "loading" rather than "static/broken". */
@Composable
private fun DetailLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "detail-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "detail-skeleton-alpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.12f)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(160.dp).background(placeholderColor, CircleShape))
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.width(80.dp).height(16.dp).background(placeholderColor, RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.width(160.dp).height(24.dp).background(placeholderColor, RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(24.dp))
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(20.dp)
                    .background(placeholderColor, RoundedCornerShape(8.dp))
            )
        }
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
    teamImpactError: String?,
    teamImpact: TeamImpactSummary?,
    onPokemonClick: (String) -> Unit,
    onViewTypeTriangles: () -> Unit
) {
    val primaryType = pokemon.types.orEmpty().minByOrNull { it.slot }?.type?.name ?: "normal"
    val primaryColor = TypeColors.of(primaryType)

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primaryColor.copy(alpha = 0.15f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shiny/animated/cry live right above the sprite they affect (issue #50), not in
                // the top bar — grouped with what they act on rather than with unrelated actions
                // like Back and add-to-team.
                Row(horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = onToggleShiny) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = if (shiny) "Show normal coloring" else "Show shiny coloring",
                            tint = if (shiny) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onToggleAnimated) {
                        Icon(
                            imageVector = Icons.Filled.Animation,
                            contentDescription = if (animated) "Show static artwork" else "Show animated battle sprite",
                            tint = if (animated) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    // F34: a one-shot action, not a toggle like shiny/animated above — disabled
                    // rather than hidden while a cry is already playing, so tapping it twice fast
                    // can't overlap two MediaPlayer instances.
                    IconButton(onClick = onPlayCry, enabled = !isCryPlaying) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Play cry",
                            tint = if (isCryPlaying) {
                                LocalContentColor.current.copy(alpha = 0.38f)
                            } else {
                                LocalContentColor.current
                            }
                        )
                    }
                }
                PokemonArtwork(
                    id = pokemon.id,
                    contentDescription = pokemon.name,
                    // Exact here, no name-based guessing: the payload already names this form's species.
                    baseSpeciesId = pokemon.species.id,
                    modifier = Modifier.size(200.dp),
                    shiny = shiny,
                    animated = animated,
                    showdownUrl = selectShowdownUrl(shiny, pokemon.sprites.other?.showdown)
                )
                Text(
                    text = "#${pokemon.id.toString().padStart(4, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = pokemon.name.toDisplayName(),
                    style = MaterialTheme.typography.titleLarge
                )
                val genus = species.genera.orEmpty().firstOrNull { it.language.name == "en" }?.genus
                if (genus != null) {
                    Text(text = genus, style = MaterialTheme.typography.bodyMedium)
                }
                // Both can be true at once in PokeAPI's data (there's no species where they are,
                // today, but nothing rules it out), so this shows both rather than picking one.
                if (species.isLegendary || species.isMythical) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (species.isLegendary) {
                            AssistChip(onClick = {}, label = { Text("Legendary") })
                        }
                        if (species.isMythical) {
                            AssistChip(onClick = {}, label = { Text("Mythical") })
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    pokemon.types.orEmpty().sortedBy { it.slot }.forEach {
                        TypeBadge(it.type.name, it.type.id ?: 0, height = 28.dp)
                    }
                }
            }
        }

        item {
            val flavorText = species.flavorTextEntries.orEmpty().firstOrNull { it.language.name == "en" }?.flavorText
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
                    Text("Height", style = MaterialTheme.typography.labelLarge)
                    Text("${pokemon.height / 10.0} m")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Weight", style = MaterialTheme.typography.labelLarge)
                    Text("${pokemon.weight / 10.0} kg")
                }
                val eggGroups = species.eggGroups.orEmpty()
                if (eggGroups.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Egg Groups", style = MaterialTheme.typography.labelLarge)
                        Text(eggGroups.joinToString(", ") { eggGroupDisplayName(it.name) })
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Base Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Bar length scaled to ${com.mandallaz.pikadex.ui.components.STAT_BAR_SCALE_MAX.toInt()} · color ranks this stat against every other Pokémon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    pokemon.stats.orEmpty().forEach { stat ->
                        val percentile = statPercentiles[stat.stat.name] ?: 0.5
                        StatBar(statName = stat.stat.name, value = stat.baseStat, color = StatColors.forPercentile(percentile))
                    }
                    val total = pokemon.stats.orEmpty().sumOf { it.baseStat }
                    Text(
                        "Total: $total",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // Every individual stat gets a percentile-colored bar, but the total — the one
                    // number people actually compare Pokémon by — was a bare figure with nothing to
                    // judge it against, even though its percentile was already being computed and
                    // then thrown away.
                    statPercentiles["total"]?.let { percentile ->
                        Text(
                            "Stronger than ${(percentile * 100).roundToInt()}% of all Pokémon",
                            style = MaterialTheme.typography.bodySmall,
                            // Deliberately not StatColors.forPercentile: those hues are tuned to be
                            // read as a filled bar against the surface, and are nowhere near enough
                            // contrast for small text on either theme's background.
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Every other card on this screen pads all 4 sides (16dp) for an even gap above/below;
            // this one only padded horizontally, so it sat flush against the Base Stats card above
            // and Type Matchups below it — the one place on the page with no breathing room.
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Abilities",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    pokemon.abilities.orEmpty().sortedBy { it.slot }.forEach { slot ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = slot.ability.name.toDisplayName() + if (slot.isHidden) " (hidden)" else "",
                                fontWeight = FontWeight.Medium
                            )
                            abilityDescriptions[slot.ability.name]?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
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
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Evolution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val paths = remember(evolutionChain) {
                            evolutionChain?.let { evolutionPaths(it.chain) }.orEmpty()
                        }
                        if (paths.all { it.size <= 1 }) {
                            Text(
                                "This Pokémon does not evolve.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            paths.forEach { path ->
                                // FlowRow, not a horizontalScroll Row: a 3-stage chain (the most
                                // common case) didn't fit on one line and had no scroll affordance,
                                // so the last stage just looked clipped off — wrapping to a second
                                // line means every stage stays visible instead of silently hidden.
                                // Grouping "arrow + its destination stage" as one FlowRow child (not
                                // stage-by-stage) means wrapping never splits an arrow from what it
                                // points to.
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    path.forEachIndexed { index, stage ->
                                        if (index > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                                    stage.conditionLabel?.let {
                                                        Text(it, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                                EvolutionStageBox(stage, pokemon, onPokemonClick)
                                            }
                                        } else {
                                            EvolutionStageBox(stage, pokemon, onPokemonClick)
                                        }
                                    }
                                }
                            }
                        }

                        if (otherForms.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                "Other Forms",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            // Deliberately doesn't say *how* each is obtained — PokeAPI's
                            // evolution-chain data doesn't cover these at all (Mega/Gigantamax are
                            // battle-only forms, not evolutions; one-off forms like Ursaluna
                            // Bloodmoon come from a species-specific in-game method the API has no
                            // field for), so this only confirms the form exists rather than
                            // guessing or fabricating an acquisition method.
                            Text(
                                "Alternate forms of this species, not covered by the evolution steps above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                otherForms.forEach { variety ->
                                    PokemonSpriteTile(
                                        name = variety.pokemon.name,
                                        id = variety.pokemon.id ?: 0,
                                        isCurrent = variety.pokemon.name == pokemon.name,
                                        onClick = { onPokemonClick(variety.pokemon.name) }
                                    )
                                }
                            }
                        }
                    }
                }
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
            expanded = MoveCategory.LEVEL_UP in expandedCategories
        ) { toggle(MoveCategory.LEVEL_UP) }
        moveSection(
            MoveCategory.MACHINE, machineMoves, moveInfo,
            expanded = MoveCategory.MACHINE in expandedCategories
        ) { toggle(MoveCategory.MACHINE) }
        moveSection(
            MoveCategory.EGG, eggMoves, moveInfo,
            expanded = MoveCategory.EGG in expandedCategories
        ) { toggle(MoveCategory.EGG) }
        moveSection(
            MoveCategory.TUTOR, tutorMoves, moveInfo,
            expanded = MoveCategory.TUTOR in expandedCategories
        ) { toggle(MoveCategory.TUTOR) }

        item { Spacer(modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun EvolutionStageBox(
    stage: com.mandallaz.pikadex.util.EvolutionStage,
    pokemon: PokemonDto,
    onPokemonClick: (String) -> Unit
) {
    PokemonSpriteTile(
        name = stage.speciesName,
        id = stage.id,
        isCurrent = stage.speciesName == pokemon.name,
        onClick = { onPokemonClick(stage.speciesName) }
    )
}

/** A tappable sprite + name, highlighted when it's the Pokémon already on screen. Shared by the
 *  evolution chain and the Mega Evolution list so the two read as the same kind of link. */
@Composable
private fun PokemonSpriteTile(
    name: String,
    id: Int,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            // Excludes the current Pokémon too, not just the placeholder id=0 case — tapping the
            // highlighted "you are here" stage in its own chain used to push a duplicate detail
            // screen of the page already on screen.
            .clickable(enabled = id != 0 && !isCurrent, onClick = onClick)
            .padding(8.dp)
    ) {
        PokemonSprite(
            id = id,
            contentDescription = name,
            modifier = Modifier.size(64.dp)
        )
        Text(
            name.toDisplayName(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** issue #2's "team coverage impact" — what adding this Pokémon to the active team would
 *  change about its shared weaknesses and coverage gaps. Only ever composed while
 *  [PokedexDetailScreen]'s showTeamImpactCard condition holds, so there's always a real team to
 *  preview against; the three states below (loading/error/result) cover everything that condition
 *  leaves open. */
@Composable
private fun TeamImpactCard(
    isLoading: Boolean,
    error: String?,
    impact: TeamImpactSummary?
) {
    // A loaded result with all 7 categories empty means this Pokémon genuinely wouldn't change
    // anything about the team's coverage — worth saying explicitly ("Nothing.") rather than leaving
    // the generic subtitle as the only text on an otherwise-blank card, which read as if the card
    // just hadn't finished loading.
    val hasNoImpact = impact != null &&
        impact.weaknessesFixed.isEmpty() && impact.weaknessesIntroduced.isEmpty() &&
        impact.gapsClosed.isEmpty() && impact.gapsOpened.isEmpty() &&
        impact.immunitiesGained.isEmpty() && impact.quadWeaknessesGained.isEmpty() &&
        impact.resistancesGained.isEmpty()

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Team Coverage Impact",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                if (hasNoImpact) "Nothing." else "What adding this Pokémon to your active team would change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                impact != null && !hasNoImpact -> TeamImpactSummaryText(impact)
            }
        }
    }
}

/** The delta summary for issue #2 (revised 2026-08-09 twice more — user feedback: only show
 *  what actually changes, with type badges instead of plain type names, and grouped so it's clear
 *  which half is this Pokémon's defensive contribution (shared weaknesses, from [computeDefensiveMultipliers]-
 *  derived data) versus its offensive one (coverage gaps, from the offensive matrix) rather than 4
 *  same-looking rows a reader has to parse individually. A row (and its parent section, if both of
 *  its rows are empty) is omitted entirely rather than spelled out as "no new..." — a Pokémon that
 *  changes nothing on either side shows no text at all here (see [TeamImpactCard]'s "Nothing." case
 *  for when *both* sections are empty). */
@Composable
private fun TeamImpactSummaryText(impact: TeamImpactSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ImpactSection(
            "Defensively",
            "Fixes these shared weaknesses:" to impact.weaknessesFixed,
            "Introduces these shared weaknesses:" to impact.weaknessesIntroduced,
            "Adds an immunity to:" to impact.immunitiesGained,
            "Adds a resistance to:" to impact.resistancesGained,
            "Adds a severe (×4) weakness to:" to impact.quadWeaknessesGained
        )
        ImpactSection(
            "Offensively",
            "Closes these coverage gaps:" to impact.gapsClosed,
            "Opens these coverage gaps:" to impact.gapsOpened
        )
    }
}

/** One half (defensive or offensive) of the summary — a heading over its rows, omitted entirely
 *  when both of [rows] are empty rather than showing a heading over nothing. */
@Composable
private fun ImpactSection(heading: String, vararg rows: Pair<String, List<String>>) {
    if (rows.all { (_, types) -> types.isEmpty() }) return
    Column {
        Text(heading, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            rows.forEach { (label, types) -> ImpactTypeRow(label, types) }
        }
    }
}

/** One impact category — omits itself (no label, no row) when [types] is empty, rather than the
 *  section above having to special-case which of its rows actually render. */
@Composable
private fun ImpactTypeRow(label: String, types: List<String>) {
    if (types.isEmpty()) return
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            types.forEach { type -> TypeBadge(type, TypeIds.idOrNull(type), height = 20.dp) }
        }
    }
}

@Composable
private fun TypeMatchupsCard(typeMatchups: Map<String, Double>) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Type Matchups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            com.mandallaz.pikadex.ui.components.TypeMatchupGroups(typeMatchups)
        }
    }
}

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
private fun TypeTrianglesCard(
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
                    "Type Triangles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onViewTypeTriangles) {
                    Text("View chart")
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp)
                    )
                }
            }

            Text(
                "This typing is the best counter to:",
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
            text = triangle.title + if (triangle.isPerfect) " (Perfect)" else " (Imperfect)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SmogonLinksCard(pokemonName: String, speciesGeneration: String, formVersionGroup: String?) {
    val links = remember(pokemonName, speciesGeneration, formVersionGroup) {
        Smogon.linksFor(pokemonName, speciesGeneration, formVersionGroup)
    }
    if (links.isEmpty()) return
    val context = LocalContext.current

    // issue #15 — user feedback: this card took up too much visual space for what it shows (a
    // title plus a handful of link chips). The outer 16dp Card padding plus a second, separate 16dp
    // inner Column padding (every other card on this page has that exact same double-16dp — worth
    // revisiting more broadly later, but out of scope here) was the single biggest lever: shrunk to
    // 12dp inner padding just for this card. Title's bottom gap and the chip grid's own spacing
    // tightened to match (8dp -> 6dp each). Chip height is left at Material3's own default (32dp)
    // rather than forced smaller — AssistChip's internal padding assumes that height, and shrinking
    // it risked clipping the label/icon rather than actually saving visible space.
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Smogon Strategy Dex",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                links.forEach { link ->
                    AssistChip(
                        onClick = { context.openExternalLink(link.url) },
                        label = { Text(link.label, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Renders one move category as real [LazyListScope] items (a header, then — only while expanded —
 * one item per move) instead of a header wrapping a plain [Column] of all rows. A pokemon like Mew
 * has ~250 TM/HM entries; composing all of them in one non-lazy Column the instant the section
 * expands was a multi-hundred-millisecond hitch. As real lazy items, only the rows actually on or
 * near screen get composed, the same as the rest of this pokemon detail page's own LazyColumn.
 *
 * The header and rows share one rounded-corner "card" look across separate list items: the header
 * is flat-bottomed while expanded, the last row is rounded-bottomed, and both share the same
 * surface color, so it still reads as a single grouped section rather than a stack of independent
 * cards.
 */
private fun LazyListScope.moveSection(
    category: MoveCategory,
    moves: List<LearnedMove>,
    moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    // An empty category (e.g. no Egg moves for a legendary) used to render as a normal expandable
    // header with a chevron inviting a tap, only to reveal a single "No moves in this category."
    // line — every empty section cost the user a tap for nothing. It's now flat, non-clickable, and
    // visibly dimmed instead, so "there's nothing here" is obvious without expanding it.
    item(key = "movesection-header-${category.name}") {
        Surface(
            onClick = onToggleExpanded,
            enabled = moves.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
            shape = if (expanded && moves.isNotEmpty()) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            } else {
                RoundedCornerShape(16.dp)
            },
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${category.label} (${moves.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (moves.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.Unspecified
                )
                if (moves.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
        }
    }

    if (!expanded || moves.isEmpty()) return

    itemsIndexed(
        moves,
        key = { _, move -> "movesection-${category.name}-${move.moveName}-${move.level}" }
    ) { index, move ->
        val isLast = index == moves.lastIndex
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = if (isLast) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                MoveRow(move, category, moveInfo)
            }
        }
    }
}

@Composable
private fun MoveRow(move: LearnedMove, category: MoveCategory, moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(move.moveName.toDisplayName(), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (category == MoveCategory.LEVEL_UP) {
            Text(if (move.level > 0) "Lv. ${move.level}" else "Evolution")
        }
    }
    moveInfo[move.moveName]?.let { info ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            TypeBadge(info.type, TypeIds.idOrNull(info.type), height = 18.dp)
            Text(
                text = moveStatsLabel(info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // F37: a second line only when there's genuinely competitive info to show (see
        // moveMetaLabel's own doc on why null means "render nothing" here, not an empty Text).
        moveMetaLabel(info)?.let { metaText ->
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

internal fun moveStatsLabel(info: PokeApiGraphQLDataSource.MoveInfo): String {
    val category = when (info.damageClass) {
        "physical" -> "Physical"
        "special" -> "Special"
        else -> "Status"
    }
    val power = info.power?.toString() ?: "—"
    val accuracy = info.accuracy?.let { "$it%" } ?: "—"
    val pp = info.pp?.toString() ?: "—"
    val base = "$category · Power $power · Accuracy $accuracy · PP $pp"
    // Priority 0 is the overwhelming majority of moves (turn order follows Speed alone) — worth
    // calling out only when it actually changes turn order, same "don't show a default" reasoning
    // as moveMetaLabel below.
    return if (info.priority != 0) "$base · Priority ${signed(info.priority)}" else base
}

/** F37: a compact line of competitive info PokeAPI's `movemeta`/`movemetastatchanges` carries but
 *  every move row ignored until now — critical-hit rate, secondary status ailment + its chance,
 *  drain/recoil or self-heal, flinch chance, and stat changes + their chance. Returns null (render
 *  nothing) when a move has none of these, which is most moves — MoveInfo's 0/"none" defaults mean
 *  "no effect" (see its own doc), so a wall of "0% chance, 0 drain..." would be actively misleading
 *  filler on the common case rather than useful density. */
internal fun moveMetaLabel(info: PokeApiGraphQLDataSource.MoveInfo): String? {
    val parts = buildList {
        if (info.ailment != "none") {
            val chance = if (info.ailmentChance > 0) "${info.ailmentChance}%" else "Always"
            add("$chance ${info.ailment.toDisplayName()}")
        }
        if (info.statChanges.isNotEmpty()) {
            val chance = if (info.statChangeChance > 0) "${info.statChangeChance}% chance: " else ""
            val changes = info.statChanges.joinToString(", ") { (stat, change) ->
                "${signed(change)} ${statDisplayName(stat)}"
            }
            add("$chance$changes")
        }
        if (info.critRate > 0) add("Crit rate +${info.critRate}")
        if (info.drain > 0) add("Drains ${info.drain}% dealt")
        if (info.drain < 0) add("Recoil ${-info.drain}% dealt")
        if (info.healing > 0) add("Heals ${info.healing}% max HP")
        if (info.flinchChance > 0) add("${info.flinchChance}% Flinch")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "attack" -> "Attack", "special-defense" -> "Sp. Def" — reuses SortStat's existing labels (the
 *  Pokédex sort dialog) rather than a second lookup table for the same six stat names. */
private fun statDisplayName(apiName: String): String =
    SortStat.entries.firstOrNull { it.apiName == apiName }?.label ?: apiName.toDisplayName()

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
internal fun eggGroupDisplayName(name: String): String = when (name) {
    "no-eggs" -> "Undiscovered"
    else -> name.toDisplayName()
}

