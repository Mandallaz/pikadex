package com.mandallaz.pikadex.ui.team

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.toDisplayName

private val TYPE_COLUMN_WIDTH = 88.dp
private val MEMBER_COLUMN_WIDTH = 64.dp
private val MATRIX_ROW_HEIGHT = 32.dp

/**
 * Below this much content height, the pinned-header matrix layout stops working and the screen
 * scrolls as one page instead — see the comment at the [BoxWithConstraints] in [TeamScreen].
 *
 * The header above the matrix (team chips ~120dp, weakness banner ~80dp, sprite header ~48dp) runs
 * to roughly 250dp, so this leaves a matrix viewport of at least ~150dp — about five type rows,
 * enough for the pinned layout to be worth having. A typical phone has ~750dp here in portrait and
 * ~250dp in landscape, so the two orientations land either side of it with room to spare.
 */
private val COMPACT_LAYOUT_MIN_HEIGHT = 400.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    onBrowsePokedex: () -> Unit,
    viewModel: TeamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPresetPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Team (${uiState.members.size}/${TeamRepository.MAX_SIZE})") },
                actions = {
                    IconButton(onClick = {
                        viewModel.preparePresetPreviews()
                        showPresetPicker = true
                    }) {
                        Icon(Icons.Filled.Groups, contentDescription = "Load a trainer's team")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.members.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Your team is empty. Add up to 6 Pokémon from the Pokédex to see how the team resists or is weak to each type.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onBrowsePokedex) { Text("Browse Pokédex") }
                    TextButton(onClick = {
                        viewModel.preparePresetPreviews()
                        showPresetPicker = true
                    }) { Text("Or load a trainer's team") }
                }
                return@BoxWithConstraints
            }

            // The matrix used to take whatever vertical space the header left over. In landscape
            // the header alone is taller than the entire content area, so "left over" was zero:
            // the matrix measured to nothing and, since this Column never scrolled, there was no
            // gesture that could bring it back — the coverage matrix, the whole point of the
            // screen, was simply unreachable. When the viewport is too short to host both, the
            // screen now scrolls as one page and the matrix renders at full height instead. The
            // sprite header gives up being pinned there; the type-name column stays pinned in both
            // layouts, since losing track of *which row is which* is the more disorienting of the
            // two, and it costs nothing because it rides the horizontal axis.
            val compact = maxHeight < COMPACT_LAYOUT_MIN_HEIGHT
            val pageScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(pageScrollState) else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.members.forEach { member ->
                        TeamMemberChip(member, onRemove = { viewModel.removeFromTeam(member) })
                    }
                    // There used to be no way to add a member from this screen at all — only Back,
                    // browse the Pokédex, then return. A trailing "add" slot lets you keep building the
                    // team without leaving, same spirit as the empty-state's Browse Pokédex button.
                    if (uiState.members.size < TeamRepository.MAX_SIZE) {
                        AddMemberChip(onClick = onBrowsePokedex)
                    }
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }

                uiState.errorMessage?.let {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        // Without this the matrix only ever recomputed when the team itself changed, so
                        // a failed fetch left every cell blank until the user added or removed a member.
                        Button(onClick = viewModel::retry, modifier = Modifier.padding(start = 8.dp)) { Text("Retry") }
                    }
                }

                val sharedWeaknesses = uiState.sharedWeaknesses
                if (sharedWeaknesses.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.WarningAmber, contentDescription = null)
                                Text(
                                    " Team-wide weaknesses",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                "At least half your team is weak to: " +
                                    sharedWeaknesses.joinToString(", ") { it.toDisplayName() },
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Two independent scroll axes, shared between a pinned header/column and the scrolling
                // body — previously the whole thing (corner, sprite header, type-name column, and cells)
                // scrolled together in one nested horizontal-then-vertical scroll, so scrolling down to
                // see e.g. Dragon/Dark/Steel/Fairy lost the sprite header, and scrolling right to see a
                // 5th/6th member lost the type-name column — there was no way to tell rows/columns apart
                // once scrolled.
                val horizontalScrollState = rememberScrollState()
                val verticalScrollState = rememberScrollState()

                // The pinned type-name column and the scrolling cell grid are two separate Columns that
                // only stay row-aligned because both use this exact same fixed height — so it can't just
                // wrap its content. Instead it grows with the user's font scale, since at 1.5-2x the
                // multiplier text ("×4", "×½") no longer fits 32dp and was getting vertically clipped.
                val rowHeight = MATRIX_ROW_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)

                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Box(modifier = Modifier.width(TYPE_COLUMN_WIDTH))
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        uiState.members.forEach { member ->
                            Column(
                                modifier = Modifier.width(MEMBER_COLUMN_WIDTH),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PokemonSprite(
                                        id = member.id ?: 0,
                                        contentDescription = member.name,
                                        modifier = Modifier.size(48.dp)
                                    )
                            }
                        }
                    }
                }

                // Compact takes its height from the content and lets the page scroll it; otherwise
                // it claims the leftover space and scrolls its own two axes inside that viewport.
                Row(
                    modifier = Modifier
                        .then(if (compact) Modifier else Modifier.weight(1f, fill = false))
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = if (compact) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(verticalScrollState)
                        }
                    ) {
                        TypeIds.standardTypeNames.forEach { typeName ->
                            Box(
                                modifier = Modifier.width(TYPE_COLUMN_WIDTH).height(rowHeight),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                TypeBadge(typeName, TypeIds.of(typeName), height = 20.dp)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .horizontalScroll(horizontalScrollState)
                            .then(
                                if (compact) Modifier else Modifier.verticalScroll(verticalScrollState)
                            )
                    ) {
                        // While a fetch is running (or just failed) for the current team composition,
                        // the matrix is either incomplete or belongs to a *previous* team — a member
                        // missing from a type's row is indistinguishable from a genuine neutral (x1)
                        // matchup under a plain `row[name] ?: 1.0` lookup, so a just-added Pokémon used
                        // to render as "neutral to all 18 types" instead of "unknown, still loading" (or,
                        // worse, kept showing an old team's colors next to an error message after a
                        // failed fetch). Cells render blank instead of guessing whenever that's the case.
                        val showKnownData = !uiState.isLoading && !uiState.isMatrixStale
                        TypeIds.standardTypeNames.forEach { typeName ->
                            val row = uiState.matrix[typeName].orEmpty()
                            Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                                uiState.members.forEach { member ->
                                    val multiplier = row[member.name] ?: 1.0
                                    val (background, content) = if (showKnownData) {
                                        multiplierColors(multiplier)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(MEMBER_COLUMN_WIDTH)
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .background(background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (showKnownData) multiplierLabel(multiplier) else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = content
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPresetPicker) {
        PresetTeamDialog(
            currentTeamSize = uiState.members.size,
            spriteIds = uiState.presetSpriteIds,
            onDismiss = { showPresetPicker = false },
            onSelect = { preset ->
                viewModel.loadPreset(preset)
                showPresetPicker = false
            }
        )
    }
}

@Composable
private fun TeamMemberChip(member: NamedApiResource, onRemove: () -> Unit) {
    // The remove button used to be a 20dp IconButton — well under the 48dp minimum touch target
    // and overlapping the sprite. It's now a full 48dp target, offset to peek outside the chip's
    // top-right corner (a standard "close badge" placement) so it doesn't crowd the sprite/name,
    // with extra top padding on the Box to give it room and extra Row spacing (in the caller) so
    // neighboring chips' peeking buttons don't collide.
    Box(modifier = Modifier.padding(top = 12.dp, end = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PokemonSprite(
                id = member.id ?: 0,
                contentDescription = member.name,
                modifier = Modifier.size(56.dp)
            )
            Text(member.name.toDisplayName(), style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            onClick = onRemove,
            // No explicit .size() override — IconButton's own default (48dp) is the actual touch
            // target; only the icon glyph itself is shrunk, below.
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-12).dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove ${member.name} from team",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddMemberChip(onClick: () -> Unit) {
    Box(modifier = Modifier.padding(top = 12.dp, end = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Add a Pokémon to your team")
            }
            Text("Add", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun multiplierLabel(multiplier: Double): String = when (multiplier) {
    4.0 -> "×4"
    2.0 -> "×2"
    0.5 -> "×½"
    0.25 -> "×¼"
    0.0 -> "×0"
    else -> ""
}

/** Background/content color pair per bucket. The tinted buckets use fixed, explicitly dark text on
 *  a fixed, explicitly light background regardless of app theme — these pastel fills were designed
 *  as light-mode swatches, and pairing them with the *theme's* default text color meant near-white
 *  text on a light pink/blue/green background in dark mode, illegible. The neutral (1x) bucket has
 *  no fill of its own, so its text keeps following the theme's normal contrast (Color.Unspecified
 *  resolves to the current LocalContentColor). */
private fun multiplierColors(multiplier: Double): Pair<Color, Color> = when {
    multiplier >= 2.0 -> Color(0xFFFFCDD2) to Color(0xFFB71C1C)
    multiplier == 0.0 -> Color(0xFFB3E5FC) to Color(0xFF01579B)
    multiplier < 1.0 -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
    else -> Color.Transparent to Color.Unspecified
}
