package com.mandallaz.pikadex.ui.team.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.ui.components.localizedTypeNames
import com.mandallaz.pikadex.ui.team.TeamUiState
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.isCompactMatrixLayout
import com.mandallaz.pikadex.util.multiplierColors
import com.mandallaz.pikadex.util.multiplierLabel

private val TYPE_COLUMN_WIDTH = 88.dp
private val MEMBER_COLUMN_WIDTH = 64.dp
private val MATRIX_ROW_HEIGHT = 32.dp

private val COMPACT_LAYOUT_MIN_REMAINING_HEIGHT = 150.dp

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

@Composable
internal fun TeamMatrix(
    uiState: TeamUiState,
    language: String,
    maxHeight: Dp,
    onAddSuggestion: (String) -> Unit,
    onPokemonClick: (String) -> Unit,
    onTeraClick: (String) -> Unit = {},
    headerContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var headerHeightPx by remember { mutableStateOf(0) }
    val headerHeightDp = with(LocalDensity.current) { headerHeightPx.toDp() }
    val compact = isCompactMatrixLayout(maxHeight, headerHeightDp, COMPACT_LAYOUT_MIN_REMAINING_HEIGHT)
    val pageScrollState = rememberScrollState()

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val rowHeight = MATRIX_ROW_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)
    var matrixMode by rememberSaveable { mutableStateOf(MatrixMode.DEFENSE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (compact) Modifier.verticalScroll(pageScrollState) else Modifier)
    ) {
        Column(modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height }) {
            headerContent()

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
                                sharedWeaknesses.localizedTypeNames().joinToString(", ")
                            )
                        )
                    }
                }
                MatrixMode.OFFENSE -> {
                    val gaps = uiState.coverageGaps
                    if (!uiState.isLoading && !uiState.isMatrixStale) {
                        if (gaps.isNotEmpty()) {
                            MatrixCallout(
                                title = stringResource(R.string.team_gaps_title),
                                body = stringResource(
                                    R.string.team_gaps_body,
                                    gaps.localizedTypeNames().joinToString(", ")
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

            if (uiState.members.size < TeamRepository.MAX_SIZE && uiState.suggestions.isNotEmpty()) {
                SuggestionsCard(
                    suggestions = uiState.suggestions,
                    spriteIds = uiState.suggestionSpriteIds,
                    tierCeiling = uiState.suggestionTierCeiling,
                    speciesNames = uiState.speciesNames,
                    language = language,
                    onAdd = onAddSuggestion,
                    onPokemonClick = onPokemonClick
                )
            } else if (
                uiState.members.size < TeamRepository.MAX_SIZE &&
                !uiState.isSuggestionsLoading &&
                !uiState.isLoading &&
                !uiState.isMatrixStale &&
                uiState.hasUnfixableSingleAxisIssue
            ) {
                MatrixCallout(
                    title = stringResource(R.string.team_no_suggestions_title),
                    body = stringResource(R.string.team_no_suggestions_body),
                    isWarning = false
                )
            }

            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(modifier = Modifier.width(TYPE_COLUMN_WIDTH))
                Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    uiState.members.forEach { member ->
                        val teraType = uiState.teraTypes[member.name]
                        Column(
                            modifier = Modifier.width(MEMBER_COLUMN_WIDTH),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PokemonSprite(
                                id = member.id ?: 0,
                                contentDescription = member.name,
                                modifier = Modifier.size(48.dp).clickable { onPokemonClick(member.name) }
                            )
                            if (teraType != null) {
                                TypeBadge(
                                    typeName = teraType,
                                    height = 16.dp,
                                    bordered = true,
                                    modifier = Modifier.clickable { onTeraClick(member.name) }
                                )
                            }
                        }
                    }
                }
            }
        }

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
