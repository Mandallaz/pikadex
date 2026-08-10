package com.mandallaz.pikadex.ui.team

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.SmogonTierLabels
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.isCompactMatrixLayout
import com.mandallaz.pikadex.util.TeamSuggestion
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.util.toDisplayName

private val TYPE_COLUMN_WIDTH = 88.dp
private val MEMBER_COLUMN_WIDTH = 64.dp
private val MATRIX_ROW_HEIGHT = 32.dp

/**
 * Below this much *leftover* height (viewport minus the actually-measured header above the
 * matrix), the pinned-header matrix layout stops working and the screen scrolls as one page
 * instead — see the comment at the [BoxWithConstraints] in [TeamScreen].
 *
 * Originally this compared against a hardcoded guess of the header's height (~250dp) instead of
 * the real, measured one. The suggestions card has grown since (tier-ceiling line, wider tiles,
 * multi-line "why" text) — on an ordinary portrait phone the header could end up tall enough to
 * squeeze the matrix's leftover space toward zero while the guess still said "plenty of room",
 * leaving no scroll gesture able to reach it. Comparing against the measured header height keeps
 * this correct regardless of how tall the header grows later.
 *
 * ~150dp leaves room for about five type rows, enough for the pinned layout to be worth having.
 */
private val COMPACT_LAYOUT_MIN_REMAINING_HEIGHT = 150.dp

/**
 * Which direction of the matchup the matrix is showing.
 *
 * The captions matter more than they look: both modes draw an 18-row grid of type badges and
 * multipliers, and without them "×2 on the Fire row" is genuinely ambiguous between "takes double
 * from Fire" and "deals double to Fire".
 */
private enum class MatrixMode {
    DEFENSE,
    OFFENSE
}

@Composable
private fun MatrixMode.label(): String = when (this) {
    MatrixMode.DEFENSE -> stringResource(R.string.team_mode_defense_label)
    MatrixMode.OFFENSE -> stringResource(R.string.team_mode_offense_label)
}

@Composable
private fun MatrixMode.caption(): String = when (this) {
    MatrixMode.DEFENSE -> stringResource(R.string.team_mode_defense_caption)
    MatrixMode.OFFENSE -> stringResource(R.string.team_mode_offense_caption)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    onBrowsePokedex: () -> Unit,
    // issue #17 — opens a suggestion tile's own detail page on sprite tap. Distinct from
    // onBrowsePokedex (which switches to the Pokédex list tab); this pushes a detail screen the
    // same way tapping a Pokédex list row does, so Back returns here.
    onPokemonClick: (String) -> Unit,
    viewModel: TeamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val language by LanguageSettings.currentLanguage.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val activeTeamId by viewModel.activeTeamId.collectAsState()
    val activeTeamName = teams.firstOrNull { it.id == activeTeamId }?.name ?: stringResource(R.string.team_default_name)
    var showPresetPicker by rememberSaveable { mutableStateOf(false) }
    var showTeamSlots by rememberSaveable { mutableStateOf(false) }
    // rememberSaveable, so rotating doesn't silently drop the user back to Defense.
    var matrixMode by rememberSaveable { mutableStateOf(MatrixMode.DEFENSE) }
    // Resolved here, not inside TeamSlotsDialog's onCreate lambda below — stringResource() is
    // @Composable and that lambda isn't.
    val newTeamDefaultName = stringResource(R.string.team_new_team_default_name)

    Scaffold(
        topBar = {
            PikaDexTopBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showTeamSlots = true }
                    ) {
                        Text("$activeTeamName (${uiState.members.size}/${TeamRepository.MAX_SIZE})")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.team_switch_team_cd))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.preparePresetPreviews()
                        showPresetPicker = true
                    }) {
                        Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.team_load_trainer_team_cd))
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
                        stringResource(R.string.team_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onBrowsePokedex) { Text(stringResource(R.string.team_browse_pokedex)) }
                    TextButton(onClick = {
                        viewModel.preparePresetPreviews()
                        showPresetPicker = true
                    }) { Text(stringResource(R.string.team_or_load_trainer_team)) }
                }
                return@BoxWithConstraints
            }

            // The matrix used to take whatever vertical space the header left over. In landscape
            // (or whenever the header itself grows, e.g. the suggestions card) the header alone
            // can be taller than the entire content area, so "left over" was zero: the matrix
            // measured to nothing and, since this Column never scrolled, there was no gesture that
            // could bring it back — the coverage matrix, the whole point of the screen, was simply
            // unreachable. When the *measured* header leaves too little room, the screen now
            // scrolls as one page and the matrix renders at full height instead. The sprite header
            // gives up being pinned there; the type-name column stays pinned in both layouts, since
            // losing track of *which row is which* is the more disorienting of the two, and it
            // costs nothing because it rides the horizontal axis.
            //
            // headerHeightPx starts at 0 (nothing measured yet), which reads as "plenty of room" —
            // compact briefly, then corrects itself once onGloballyPositioned reports the real
            // size, same one-frame settle every measure-then-decide layout in Compose has.
            var headerHeightPx by remember { mutableStateOf(0) }
            val headerHeightDp = with(LocalDensity.current) { headerHeightPx.toDp() }
            val compact = isCompactMatrixLayout(maxHeight, headerHeightDp, COMPACT_LAYOUT_MIN_REMAINING_HEIGHT)
            val pageScrollState = rememberScrollState()

            // Two independent scroll axes, shared between a pinned header/column and the scrolling
            // body — previously the whole thing (corner, sprite header, type-name column, and cells)
            // scrolled together in one nested horizontal-then-vertical scroll, so scrolling down to
            // see e.g. Dragon/Dark/Steel/Fairy lost the sprite header, and scrolling right to see a
            // 5th/6th member lost the type-name column — there was no way to tell rows/columns apart
            // once scrolled. Declared here (rather than next to the sprite/matrix rows that use them)
            // since both the sprite header row (still part of the measured header below) and the
            // matrix row after it need to share the same state.
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()

            // The pinned type-name column and the scrolling cell grid are two separate Columns that
            // only stay row-aligned because both use this exact same fixed height — so it can't just
            // wrap its content. Instead it grows with the user's font scale, since at 1.5-2x the
            // multiplier text ("×4", "×½") no longer fits 32dp and was getting vertically clipped.
            val rowHeight = MATRIX_ROW_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(pageScrollState) else Modifier)
            ) {
                // Everything here down to the sprite header row is what "compact" above measures
                // against — wrapped in one Column purely so onGloballyPositioned can report its
                // total height, not for any layout purpose (a Column wrapping a Column changes
                // nothing about how its children are placed).
                Column(modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.members.forEach { member ->
                        TeamMemberChip(
                            member,
                            uiState.speciesNames,
                            language,
                            onRemove = { viewModel.removeFromTeam(member) }
                        )
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
                        Button(onClick = viewModel::retry, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.team_retry)) }
                    }
                }

                // Defense answers "what beats my team", offense answers "what can my team not
                // touch". Both are the same 18-row grid of multipliers, so they share one matrix
                // rather than stacking a second, near-identical table below the first.
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MatrixMode.entries.forEach { mode ->
                        FilterChip(
                            selected = matrixMode == mode,
                            onClick = { matrixMode = mode },
                            label = { Text(mode.label()) }
                        )
                    }
                }

                Text(
                    text = matrixMode.caption(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                when (matrixMode) {
                    MatrixMode.DEFENSE -> {
                        val sharedWeaknesses = uiState.sharedWeaknesses
                        if (sharedWeaknesses.isNotEmpty()) {
                            MatrixCallout(
                                title = stringResource(R.string.team_weaknesses_title),
                                body = stringResource(
                                    R.string.team_weaknesses_body,
                                    sharedWeaknesses.joinToString(", ") { it.toDisplayName() }
                                )
                            )
                        }
                    }
                    MatrixMode.OFFENSE -> {
                        val gaps = uiState.coverageGaps
                        // Only worth saying once the matrix is real data — mid-fetch the gap list is
                        // empty for the boring reason, and "no gaps" would be a lie.
                        if (!uiState.isLoading && !uiState.isMatrixStale) {
                            if (gaps.isNotEmpty()) {
                                MatrixCallout(
                                    title = stringResource(R.string.team_gaps_title),
                                    body = stringResource(
                                        R.string.team_gaps_body,
                                        gaps.joinToString(", ") { it.toDisplayName() }
                                    )
                                )
                            } else {
                                MatrixCallout(
                                    title = stringResource(R.string.team_no_gaps_title),
                                    body = stringResource(R.string.team_no_gaps_body),
                                    isWarning = false
                                )
                            }
                        }
                    }
                }

                // Only worth showing once there's actually room to add someone and something to
                // suggest — hidden rather than an empty/placeholder card the rest of the time.
                if (uiState.members.size < TeamRepository.MAX_SIZE && uiState.suggestions.isNotEmpty()) {
                    SuggestionsCard(
                        suggestions = uiState.suggestions,
                        spriteIds = uiState.suggestionSpriteIds,
                        tierCeiling = uiState.suggestionTierCeiling,
                        speciesNames = uiState.speciesNames,
                        language = language,
                        onAdd = viewModel::addSuggestion,
                        onPokemonClick = onPokemonClick
                    )
                }

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
                                TypeBadge(typeName, TypeIds.idOrNull(typeName), height = 20.dp)
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
                        val activeMatrix = when (matrixMode) {
                            MatrixMode.DEFENSE -> uiState.matrix
                            MatrixMode.OFFENSE -> uiState.offensiveMatrix
                        }
                        TypeIds.standardTypeNames.forEach { typeName ->
                            val row = activeMatrix[typeName].orEmpty()
                            Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                                uiState.members.forEach { member ->
                                    val multiplier = row[member.name] ?: 1.0
                                    val (background, content) = if (showKnownData) {
                                        multiplierColors(multiplier, isOffense = matrixMode == MatrixMode.OFFENSE)
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
            speciesNames = uiState.speciesNames,
            language = language,
            onDismiss = { showPresetPicker = false },
            onSelect = { preset ->
                viewModel.loadPreset(preset)
                showPresetPicker = false
            },
            onSelectIntoNewTeam = { preset ->
                viewModel.loadPresetIntoNewTeam(preset)
                showPresetPicker = false
            }
        )
    }

    if (showTeamSlots) {
        TeamSlotsDialog(
            teams = teams,
            activeTeamId = activeTeamId,
            onDismiss = { showTeamSlots = false },
            onSelect = { id ->
                viewModel.setActiveTeam(id)
                showTeamSlots = false
            },
            onCreate = {
                val newId = viewModel.createTeam(newTeamDefaultName)
                viewModel.setActiveTeam(newId)
                showTeamSlots = false
            },
            onRename = viewModel::renameTeam,
            onDelete = viewModel::deleteTeam
        )
    }
}

@Composable
private fun TeamMemberChip(
    member: NamedApiResource,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onRemove: () -> Unit
) {
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
            Text(member.name.localizedDisplayName(speciesNames, language), style = MaterialTheme.typography.bodyMedium)
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
                contentDescription = stringResource(R.string.team_remove_member_cd, member.name.localizedDisplayName(speciesNames, language)),
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
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.team_add_member_cd))
            }
            Text(stringResource(R.string.team_add_label), style = MaterialTheme.typography.bodyMedium)
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
/**
 * Background/foreground for one matrix cell.
 *
 * The same multiplier means opposite things in the two modes — ×2 *taken* is a problem, ×2 *dealt*
 * is an advantage — so the palette keys on whether the number is good news for the player rather
 * than on the number itself. Sharing one scale between both turned the offense matrix into a wall
 * of red danger cells reporting what was actually a well-covered team.
 *
 * Blue stays reserved for a defensive immunity, the one genuinely special case; dealing ×0 is
 * simply the worst offensive outcome and reads as such.
 */
private fun multiplierColors(multiplier: Double, isOffense: Boolean = false): Pair<Color, Color> {
    val bad = Color(0xFFFFCDD2) to Color(0xFFB71C1C)
    val good = Color(0xFFC8E6C9) to Color(0xFF1B5E20)
    val immune = Color(0xFFB3E5FC) to Color(0xFF01579B)
    return when {
        multiplier == 1.0 -> Color.Transparent to Color.Unspecified
        multiplier == 0.0 -> if (isOffense) bad else immune
        multiplier > 1.0 -> if (isOffense) good else bad
        else -> if (isOffense) bad else good
    }
}

/** Candidates that would fix both a shared weakness and a coverage gap at once — see
 *  [TeamViewModel.loadSuggestions]/issue #11. Sorted by total impact (weaknesses resisted
 *  plus gaps hit) descending, stat total ascending as a tiebreak, so the most useful, least
 *  overpowering options lead the row. */
@Composable
private fun SuggestionsCard(
    suggestions: List<TeamSuggestion>,
    spriteIds: Map<String, Int>,
    tierCeiling: String?,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onAdd: (String) -> Unit,
    onPokemonClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.team_suggestions_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.team_suggestions_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            // The ceiling itself lives in Settings, out of sight from this card — without this
            // line, a shorter-than-expected list here (or one that suddenly changed) had no
            // visible cause.
            if (tierCeiling != null) {
                Text(
                    stringResource(R.string.team_suggestions_tier_limited, SmogonTierLabels.labelFor(tierCeiling)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // More than fit on a portrait phone in one glance — the row scrolls, but nothing about
            // a plain horizontalScroll Row hints that on its own, so a bare "4 shown, 6 more
            // offscreen" used to read as "only 4 suggestions" with no reason to swipe further.
            if (suggestions.size > 4) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        stringResource(R.string.team_suggestions_swipe_all, suggestions.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = if (suggestions.size > 4) 0.dp else 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionTile(
                        suggestion = suggestion,
                        spriteId = spriteIds[suggestion.name] ?: 0,
                        speciesNames = speciesNames,
                        language = language,
                        onAdd = { onAdd(suggestion.name) },
                        onSpriteClick = { onPokemonClick(suggestion.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionTile(
    suggestion: TeamSuggestion,
    spriteId: Int,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onAdd: () -> Unit,
    // issue #17 — sprite-only tap target, not the whole tile: the "+" IconButton already
    // claims its own tap area, and the ask specifically named "the sprite", read narrowly.
    onSpriteClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        PokemonSprite(
            id = spriteId,
            contentDescription = suggestion.name,
            modifier = Modifier.size(48.dp).clickable(onClick = onSpriteClick)
        )
        Text(
            suggestion.name.localizedDisplayName(speciesNames, language),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.team_suggestion_bst, suggestion.statTotal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            suggestion.types.forEach { type ->
                TypeBadge(type, TypeIds.idOrNull(type), height = 14.dp)
            }
        }
        // The "why" behind this specific suggestion — without it the reasoning in the card's own
        // subtitle ("would help both...") never ties back to any one tile, and the user has to
        // work it out by eye from the type badges above. Also what [rankSuggestions] sorts by, so
        // it doubles as an explanation for the tile's position in the row.
        Text(
            stringResource(
                R.string.team_suggestion_resists_hits,
                suggestion.weaknessesResisted.joinToString(", ") { it.toDisplayName() },
                suggestion.gapsHit.joinToString(", ") { it.toDisplayName() }
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        // The suggestion was scored on this species' base typing only — a mega/gmax/regional form
        // that changes the typing can silently stop qualifying, so name exactly which ones not to
        // pick. Stripped of the shared "<species>-" prefix ("charizard-mega-x" -> "Mega X") to fit
        // this tile's width.
        if (suggestion.conflictingForms.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp).padding(top = 1.dp)
                )
                Text(
                    stringResource(
                        R.string.team_suggestion_not_as,
                        suggestion.conflictingForms.joinToString(", ") {
                            it.removePrefix("${suggestion.name}-").toDisplayName()
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.team_suggestion_add_cd, suggestion.name.localizedDisplayName(speciesNames, language)),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** The banner above the matrix. Shared by both modes so the two read as the same kind of summary;
 *  [isWarning] is what separates "here is a problem" from "here is a clean bill of health". */
@Composable
private fun MatrixCallout(title: String, body: String, isWarning: Boolean = true) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isWarning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWarning) Icons.Filled.WarningAmber else Icons.Filled.Check,
                    contentDescription = null
                )
                Text(
                    " $title",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(body, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
