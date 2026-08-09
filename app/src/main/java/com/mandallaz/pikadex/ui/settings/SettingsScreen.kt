package com.mandallaz.pikadex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandallaz.pikadex.data.PrefetchState
import com.mandallaz.pikadex.ui.components.OptionsDialog
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.util.SmogonTierLabels
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val prefetchState by viewModel.prefetchState.collectAsState()
    var showFullDetailWarning by remember { mutableStateOf(false) }
    var showTierDialog by remember { mutableStateOf(false) }
    var showTierExplanationDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { PikaDexTopBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Offline data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Download data ahead of time so the app works fully offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            PrefetchTierRow(
                title = "Essentials",
                subtitle = "Base stats, moves, type chart, Smogon tiers — about 1MB.",
                checked = uiState.essentialsEnabled,
                onCheckedChange = viewModel::setEssentialsEnabled
            )
            PrefetchTierRow(
                title = "Sprites",
                subtitle = "Artwork and sprites for every Pokémon — 50-150MB.",
                checked = uiState.spritesEnabled,
                onCheckedChange = viewModel::setSpritesEnabled
            )
            PrefetchTierRow(
                title = "Shiny & animated sprites",
                subtitle = "Shiny artwork/sprites and animated Showdown GIFs for every Pokémon, so the detail screen's shiny/animated toggles work offline too — roughly doubles the Sprites download.",
                checked = uiState.spritesExtraEnabled,
                onCheckedChange = viewModel::setSpritesExtraEnabled
            )
            PrefetchTierRow(
                title = "Full detail",
                subtitle = "Every Pokémon's complete data (species, evolution chain) for full offline browsing — a large download.",
                checked = uiState.fullDetailEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) showFullDetailWarning = true else viewModel.setFullDetailEnabled(false)
                }
            )
            PrefetchTierRow(
                title = "Cries",
                subtitle = "Every Pokémon's cry, so the play button on its detail page works offline — around 1300 short audio clips.",
                checked = uiState.criesEnabled,
                onCheckedChange = viewModel::setCriesEnabled
            )

            when (val state = prefetchState) {
                is PrefetchState.Running -> {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text("${state.phase}${if (state.total > 0) " — ${state.done}/${state.total}" else "…"}")
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        TextButton(onClick = viewModel::cancelPrefetch, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Cancel")
                        }
                    }
                }
                is PrefetchState.Finished -> {
                    Text(
                        if (state.failed > 0) "Done, ${state.failed} item(s) failed and can be retried." else "Prefetch complete.",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Button(onClick = viewModel::startPrefetch, enabled = uiState.hasAnyTierEnabled, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Prefetch now")
                    }
                }
                is PrefetchState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = viewModel::startPrefetch, enabled = uiState.hasAnyTierEnabled, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Retry")
                    }
                }
                PrefetchState.Idle -> {
                    Button(
                        onClick = viewModel::startPrefetch,
                        enabled = uiState.hasAnyTierEnabled,
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text("Prefetch now") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text(
                "Storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (uiState.isMeasuringStorage && uiState.storageUsage == null) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        val usage = uiState.storageUsage
                        Text("Total: ${formatBytes(usage?.totalBytes ?: 0L)}", fontWeight = FontWeight.Medium)
                        Text("API data: ${formatBytes((usage?.httpCacheBytes ?: 0L) + (usage?.diskCacheBytes ?: 0L))}")
                        Text("Images: ${formatBytes(usage?.imageCacheBytes ?: 0L)}")
                        Text("Cries: ${formatBytes(usage?.criesCacheBytes ?: 0L)}")
                    }
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::measureStorage) { Text("Refresh") }
                        TextButton(onClick = viewModel::clearDownloadedData) { Text("Clear downloaded data") }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text(
                "Display",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PrefetchTierRow(
                title = "AMOLED black",
                subtitle = "Forces dark mode with a true black background, to save battery on AMOLED screens.",
                checked = uiState.amoledEnabled,
                onCheckedChange = viewModel::setAmoledEnabled
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Team suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showTierExplanationDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "What are competitive tiers?")
                }
            }
            Text(
                "Cap the team builder's Suggestions card to a competitive tier and below (e.g. " +
                    "UU also allows RU, NU...), based on Gen 9 Smogon tiers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showTierDialog = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tier limit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    uiState.maxSuggestionTier?.let { SmogonTierLabels.labelFor(it) } ?: "No limit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showTierDialog) {
        OptionsDialog(
            title = "Suggestion tier limit",
            options = listOf<String?>(null) + uiState.suggestionTierOptions,
            labelFor = { it?.let { tier -> SmogonTierLabels.labelFor(tier) } ?: "No limit" },
            selected = uiState.maxSuggestionTier,
            onDismiss = { showTierDialog = false },
            onSelect = { tier ->
                viewModel.setMaxSuggestionTier(tier)
                showTierDialog = false
            }
        )
    }

    if (showTierExplanationDialog) {
        SmogonTierExplanationDialog(onDismiss = { showTierExplanationDialog = false })
    }

    if (showFullDetailWarning) {
        AlertDialog(
            onDismissRequest = { showFullDetailWarning = false },
            title = { Text("Download full detail?") },
            text = {
                Text(
                    "This fetches every Pokémon's complete data — species, evolution chain and more — " +
                        "for around 1300 entries. It's a large download and will use significant data and storage."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFullDetailWarning = false
                    viewModel.setFullDetailEnabled(true)
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showFullDetailWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PrefetchTierRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Binary units (KiB/MiB, "1024-based"), matching what Android's own storage settings show. */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0) * 10).roundToInt() / 10.0} MB"
    bytes >= 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
    else -> "$bytes B"
}
