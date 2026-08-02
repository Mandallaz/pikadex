package com.mandallaz.pikadex.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
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
import com.mandallaz.pikadex.util.movesForCategory
import com.mandallaz.pikadex.util.toDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexDetailScreen(
    pokemonNameOrId: String,
    onBack: () -> Unit,
    onPokemonClick: (String) -> Unit,
    viewModel: PokedexDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val team by viewModel.team.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isInTeam = uiState.pokemon?.let { p -> team.any { it.name == p.name } } ?: false
    val isTeamFull = team.size >= com.mandallaz.pikadex.data.TeamRepository.MAX_SIZE
    val isFavorite = uiState.pokemon?.let { p -> favorites.contains(p.name) } ?: false

    LaunchedEffect(pokemonNameOrId) { viewModel.load(pokemonNameOrId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.pokemon?.name?.toDisplayName() ?: pokemonNameOrId.toDisplayName()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.pokemon != null) {
                        IconButton(
                            onClick = { viewModel.toggleTeamMembership() },
                            enabled = isInTeam || !isTeamFull
                        ) {
                            Icon(
                                imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                                contentDescription = if (isInTeam) "Remove from team" else "Add to team"
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val pokemon = uiState.pokemon
            val species = uiState.species
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.errorMessage != null || pokemon == null || species == null -> Text(
                    text = uiState.errorMessage ?: "Pokémon not found.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> DetailContent(
                    pokemon = pokemon,
                    species = species,
                    evolutionChain = uiState.evolutionChain,
                    typeMatchups = uiState.typeMatchups,
                    abilityDescriptions = uiState.abilityDescriptions,
                    memberTriangles = uiState.memberTriangles,
                    counteredTriangles = uiState.counteredTriangles,
                    moveInfo = uiState.moveInfo,
                    statPercentiles = uiState.statPercentiles,
                    onPokemonClick = onPokemonClick
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    pokemon: PokemonDto,
    species: PokemonSpeciesDto,
    evolutionChain: com.mandallaz.pikadex.data.remote.dto.EvolutionChainDto?,
    typeMatchups: Map<String, Double>,
    abilityDescriptions: Map<String, String>,
    memberTriangles: List<TypeTriangle>,
    counteredTriangles: List<TypeTriangle>,
    moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo>,
    statPercentiles: Map<String, Double>,
    onPokemonClick: (String) -> Unit
) {
    val primaryType = pokemon.types.minByOrNull { it.slot }?.type?.name ?: "normal"
    val primaryColor = TypeColors.of(primaryType)

    // Each category's moves computed once per pokemon (not on every recomposition — this is the
    // exact same grouping/sorting work regardless of whether the section is expanded), and which
    // sections are expanded is tracked here rather than inside each section, since expanded rows
    // now need to be items() in *this* LazyColumn rather than a nested non-lazy Column (composing
    // ~250 rows for a pokemon like Mew in one non-lazy Column, all at once on expand, was the
    // actual performance problem — LazyColumn only composes what's on/near screen).
    val levelUpMoves = remember(pokemon) { pokemon.movesForCategory(MoveCategory.LEVEL_UP) }
    val machineMoves = remember(pokemon) { pokemon.movesForCategory(MoveCategory.MACHINE) }
    val eggMoves = remember(pokemon) { pokemon.movesForCategory(MoveCategory.EGG) }
    val tutorMoves = remember(pokemon) { pokemon.movesForCategory(MoveCategory.TUTOR) }
    var expandedCategories by remember { mutableStateOf(emptySet<MoveCategory>()) }
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
                AsyncImage(
                    model = Sprites.officialArtworkUrl(pokemon.id),
                    contentDescription = pokemon.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(200.dp)
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
                val genus = species.genera.firstOrNull { it.language.name == "en" }?.genus
                if (genus != null) {
                    Text(text = genus, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    pokemon.types.sortedBy { it.slot }.forEach {
                        TypeBadge(it.type.name, it.type.id ?: 0, height = 28.dp)
                    }
                }
            }
        }

        item {
            val flavorText = species.flavorTextEntries.firstOrNull { it.language.name == "en" }?.flavorText
            if (flavorText != null) {
                Text(
                    text = flavorText.replace('\n', ' ').replace('\u000C', ' '),
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
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Base Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    pokemon.stats.forEach { stat ->
                        val percentile = statPercentiles[stat.stat.name] ?: 0.5
                        StatBar(statName = stat.stat.name, value = stat.baseStat, color = StatColors.forPercentile(percentile))
                    }
                    val total = pokemon.stats.sumOf { it.baseStat }
                    Text(
                        "Total: $total",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Abilities",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    pokemon.abilities.sortedBy { it.slot }.forEach { slot ->
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

        if (memberTriangles.isNotEmpty() || counteredTriangles.isNotEmpty()) {
            item {
                TypeTrianglesCard(memberTriangles, counteredTriangles)
            }
        }

        item {
            SmogonLinksCard(pokemonName = pokemon.name, speciesGeneration = species.generation.name)
        }

        if (evolutionChain != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Evolution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val paths = remember(evolutionChain) { evolutionPaths(evolutionChain.chain) }
                        paths.forEach { path ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                path.forEachIndexed { index, stage ->
                                    if (index > 0) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                            stage.conditionLabel?.let {
                                                Text(it, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .background(
                                                if (stage.speciesName == pokemon.name)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable(enabled = stage.id != 0) { onPokemonClick(stage.speciesName) }
                                            .padding(8.dp)
                                    ) {
                                        AsyncImage(
                                            model = Sprites.defaultSpriteUrl(stage.id),
                                            contentDescription = stage.speciesName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Text(
                                            stage.speciesName.toDisplayName(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

        item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp)) }
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

@Composable
private fun TypeTrianglesCard(memberTriangles: List<TypeTriangle>, counteredTriangles: List<TypeTriangle>) {
    val counteredTitles = counteredTriangles.map { it.title }.toSet()
    // A pokemon's typing can counter a triangle without being "in" it at all — e.g. Dragonite
    // (Flying/Dragon) counters Fire/Grass/Water, but neither Flying nor Dragon is one of that
    // triangle's 3 types — so these need their own section, not just an inline note on triangles
    // that happen to already be in memberTriangles.
    val counterOnlyTriangles = counteredTriangles.filter { it !in memberTriangles }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Type Triangles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (counterOnlyTriangles.isNotEmpty()) {
                Text(
                    "This typing is the best counter to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                counterOnlyTriangles.forEachIndexed { index, triangle ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    TriangleRow(triangle)
                }
                if (memberTriangles.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            }

            if (memberTriangles.isNotEmpty()) {
                Text(
                    "This typing is part of these rock-paper-scissors loops.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                memberTriangles.forEachIndexed { index, triangle ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    TriangleRow(triangle, isCounter = triangle.title in counteredTitles)
                }
            }
        }
    }
}

@Composable
private fun TriangleRow(triangle: TypeTriangle, isCounter: Boolean = false) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            triangle.types.forEach { type -> TypeBadge(type, TypeIds.of(type)) }
        }
        Text(
            text = triangle.title + if (triangle.isPerfect) " (Perfect)" else " (Imperfect)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (isCounter) {
            Text(
                text = "This typing is the best counter to this triangle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SmogonLinksCard(pokemonName: String, speciesGeneration: String) {
    val links = remember(pokemonName, speciesGeneration) { Smogon.linksFor(pokemonName, speciesGeneration) }
    if (links.isEmpty()) return
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Smogon Strategy Dex",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                links.forEach { link ->
                    AssistChip(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) },
                        label = { Text(link.label) },
                        trailingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
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
    item(key = "movesection-header-${category.name}") {
        Surface(
            onClick = onToggleExpanded,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
            shape = if (expanded) {
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
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    }

    if (!expanded) return

    if (moves.isEmpty()) {
        item(key = "movesection-empty-${category.name}") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "No moves in this category.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
        return
    }

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
            TypeBadge(info.type, TypeIds.of(info.type), height = 18.dp)
            Text(
                text = moveStatsLabel(info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun moveStatsLabel(info: PokeApiGraphQLDataSource.MoveInfo): String {
    val category = when (info.damageClass) {
        "physical" -> "Physical"
        "special" -> "Special"
        else -> "Status"
    }
    val power = info.power?.toString() ?: "—"
    val accuracy = info.accuracy?.let { "$it%" } ?: "—"
    return "$category · Power $power · Accuracy $accuracy"
}

