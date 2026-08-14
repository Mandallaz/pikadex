package com.mandallaz.pikadex.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.PokemonArtwork
import com.mandallaz.pikadex.ui.components.SearchableListDialog
import com.mandallaz.pikadex.ui.components.StatBar
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.StatColors
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.util.BASE_STATS
import com.mandallaz.pikadex.util.TOTAL
import com.mandallaz.pikadex.util.baseStatTotal

private val SIDE_BY_SIDE_MIN_WIDTH = 500.dp

private enum class Side { LEFT, RIGHT }

/** Side-by-side stat comparison of two pokemon. Reached from a Pokémon's detail page via
 *  "Compare with…"; [onRecompare] re-navigates to a fresh compare/{left}/{right} route rather than
 *  mutating state in place, so the two names in the URL stay the single source of truth for which
 *  pokemon are being compared (swap, and re-picking either side, both go through it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    leftName: String,
    rightName: String,
    onBack: () -> Unit,
    onRecompare: (left: String, right: String) -> Unit,
    viewModel: CompareViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val language by LanguageSettings.currentLanguage.collectAsState()
    var pickingSide by rememberSaveable { mutableStateOf<Side?>(null) }

    LaunchedEffect(leftName, rightName) { viewModel.load(leftName, rightName) }

    Scaffold(
        topBar = {
            PikaDexTopBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.compare_back_cd))
                    }
                },
                actions = {
                    if (uiState.left != null && uiState.right != null) {
                        IconButton(onClick = { onRecompare(rightName, leftName) }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.compare_swap_cd))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val left = uiState.left
            val right = uiState.right
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.errorMessage != null || left == null || right == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.errorMessage?.resolve() ?: stringResource(R.string.compare_load_error),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(onClick = { viewModel.load(leftName, rightName) }) { Text(stringResource(R.string.compare_retry)) }
                    }
                }
                else -> CompareContent(
                    left = left,
                    right = right,
                    speciesNames = uiState.speciesNames,
                    language = language,
                    onChangeLeft = { viewModel.loadCandidatesIfNeeded(); pickingSide = Side.LEFT },
                    onChangeRight = { viewModel.loadCandidatesIfNeeded(); pickingSide = Side.RIGHT }
                )
            }
        }
    }

    if (pickingSide != null) {
        SearchableListDialog(
            title = stringResource(R.string.compare_with_title),
            options = uiState.candidateNames.filterNot { it == leftName || it == rightName },
            displayName = { it.localizedDisplayName(uiState.speciesNames, language) },
            onDismiss = { pickingSide = null },
            onSelect = { name ->
                val picked = pickingSide
                if (name != null && picked != null) {
                    when (picked) {
                        Side.LEFT -> onRecompare(name, rightName)
                        Side.RIGHT -> onRecompare(leftName, name)
                    }
                }
                pickingSide = null
            }
        )
    }
}

@Composable
private fun CompareContent(
    left: CompareSide,
    right: CompareSide,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onChangeLeft: () -> Unit,
    onChangeRight: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Two full-width StatBars plus a delta label between them needs real horizontal room —
        // below the threshold each stat stacks its two bars instead of squeezing three columns
        // into a narrow portrait screen.
        val sideBySide = maxWidth > SIDE_BY_SIDE_MIN_WIDTH
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CompareHeader(left.pokemon, speciesNames, language, onChangeLeft, Modifier.weight(1f))
                    CompareHeader(right.pokemon, speciesNames, language, onChangeRight, Modifier.weight(1f))
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.compare_base_stats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        BASE_STATS.forEach { key ->
                            StatCompareRow(
                                statKey = key,
                                leftValue = left.pokemon.stats.orEmpty().firstOrNull { it.stat.name == key }?.baseStat ?: 0,
                                rightValue = right.pokemon.stats.orEmpty().firstOrNull { it.stat.name == key }?.baseStat ?: 0,
                                leftPercentile = left.statPercentiles[key] ?: 0.5,
                                rightPercentile = right.statPercentiles[key] ?: 0.5,
                                sideBySide = sideBySide
                            )
                        }
                        val leftTotal = left.pokemon.baseStatTotal()
                        val rightTotal = right.pokemon.baseStatTotal()
                        StatCompareRow(
                            statKey = TOTAL,
                            leftValue = leftTotal,
                            rightValue = rightTotal,
                            leftPercentile = left.statPercentiles[TOTAL] ?: 0.5,
                            rightPercentile = right.statPercentiles[TOTAL] ?: 0.5,
                            sideBySide = sideBySide
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareHeader(
    pokemon: PokemonDto,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PokemonArtwork(id = pokemon.id, contentDescription = pokemon.name, modifier = Modifier.size(120.dp))
        Text(
            text = pokemon.name.localizedDisplayName(speciesNames, language),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            pokemon.types.orEmpty().sortedBy { it.slot }.forEach {
                TypeBadge(it.type.name, it.type.id ?: 0, height = 20.dp)
            }
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.compare_change)) }
    }
}

@Composable
private fun StatCompareRow(
    statKey: String,
    leftValue: Int,
    rightValue: Int,
    leftPercentile: Double,
    rightPercentile: Double,
    sideBySide: Boolean
) {
    val delta = deltaLabel(leftValue - rightValue)
    if (sideBySide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatBar(statKey, leftValue, StatColors.forPercentile(leftPercentile), modifier = Modifier.weight(1f))
            Text(
                delta,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            StatBar(statKey, rightValue, StatColors.forPercentile(rightPercentile), modifier = Modifier.weight(1f))
        }
    } else {
        Column {
            StatBar(statKey, leftValue, StatColors.forPercentile(leftPercentile))
            Text(
                delta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            StatBar(statKey, rightValue, StatColors.forPercentile(rightPercentile))
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** "how much better/worse the left side is", from left's perspective — not colored good/bad since
 *  a higher raw stat isn't universally better (e.g. a lower Speed can matter for some strategies),
 *  just the plain signed difference. */
internal fun deltaLabel(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "$delta"
    else -> "±0"
}
