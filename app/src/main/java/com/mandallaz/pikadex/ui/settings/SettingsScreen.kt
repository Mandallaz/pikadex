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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.AppLanguage
import com.mandallaz.pikadex.data.PrefetchManager
import com.mandallaz.pikadex.data.PrefetchState
import com.mandallaz.pikadex.data.SupportedLanguages
import com.mandallaz.pikadex.ui.components.OptionsDialog
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.localizedTierLabel
import com.mandallaz.pikadex.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val prefetchState by viewModel.prefetchState.collectAsState()
    var showFullDetailWarning by remember { mutableStateOf(false) }
    var showPrefetchConfirm by remember { mutableStateOf(false) }
    var showTierDialog by remember { mutableStateOf(false) }
    var showTierExplanationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { PikaDexTopBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // F35 — the first choice on this screen, ahead of everything else, driving both the
            // UI chrome (this screen included) and game data (species/move/ability text) at once.
            Text(
                stringResource(R.string.settings_language_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_language_section),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    uiState.currentLanguage.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text(
                stringResource(R.string.settings_offline_data_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.settings_offline_data_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            PrefetchTierRow(
                title = stringResource(R.string.settings_tier_essentials_title),
                subtitle = stringResource(R.string.settings_tier_essentials_subtitle),
                checked = uiState.essentialsEnabled,
                onCheckedChange = viewModel::setEssentialsEnabled
            )
            PrefetchTierRow(
                title = stringResource(R.string.settings_tier_sprites_title),
                subtitle = stringResource(R.string.settings_tier_sprites_subtitle),
                checked = uiState.spritesEnabled,
                onCheckedChange = viewModel::setSpritesEnabled
            )
            PrefetchTierRow(
                title = stringResource(R.string.settings_tier_sprites_extra_title),
                subtitle = stringResource(R.string.settings_tier_sprites_extra_subtitle),
                checked = uiState.spritesExtraEnabled,
                onCheckedChange = viewModel::setSpritesExtraEnabled
            )
            PrefetchTierRow(
                title = stringResource(R.string.settings_tier_full_detail_title),
                subtitle = stringResource(R.string.settings_tier_full_detail_subtitle),
                checked = uiState.fullDetailEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) showFullDetailWarning = true else viewModel.setFullDetailEnabled(false)
                }
            )
            PrefetchTierRow(
                title = stringResource(R.string.settings_tier_cries_title),
                subtitle = stringResource(R.string.settings_tier_cries_subtitle),
                checked = uiState.criesEnabled,
                onCheckedChange = viewModel::setCriesEnabled
            )
            PrefetchTierRow(
                title = stringResource(R.string.settings_wifi_only_title),
                subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                checked = uiState.wifiOnlyEnabled,
                onCheckedChange = viewModel::setWifiOnlyEnabled
            )

            when (val state = prefetchState) {
                is PrefetchState.Running -> {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text("${stringResource(state.phaseRes)}${if (state.total > 0) " — ${state.done}/${state.total}" else "…"}")
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        TextButton(onClick = viewModel::cancelPrefetch, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                    }
                }
                is PrefetchState.Finished -> {
                    Text(
                        if (state.failed > 0) {
                            stringResource(R.string.settings_prefetch_finished_with_failures, state.failed)
                        } else {
                            stringResource(R.string.settings_prefetch_finished)
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Button(onClick = { showPrefetchConfirm = true }, enabled = uiState.hasAnyTierEnabled, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.settings_prefetch_now))
                    }
                }
                is PrefetchState.Failed -> {
                    Text(stringResource(state.messageRes), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = { showPrefetchConfirm = true }, enabled = uiState.hasAnyTierEnabled, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.settings_retry))
                    }
                }
                PrefetchState.Idle -> {
                    Button(
                        onClick = { showPrefetchConfirm = true },
                        enabled = uiState.hasAnyTierEnabled,
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text(stringResource(R.string.settings_prefetch_now)) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text(
                stringResource(R.string.settings_storage_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (uiState.isMeasuringStorage && uiState.storageUsage == null) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        val usage = uiState.storageUsage
                        Text(
                            stringResource(R.string.settings_storage_total, formatBytes(usage?.totalBytes ?: 0L)),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(
                                R.string.settings_storage_api_data,
                                formatBytes((usage?.httpCacheBytes ?: 0L) + (usage?.diskCacheBytes ?: 0L))
                            )
                        )
                        Text(stringResource(R.string.settings_storage_images, formatBytes(usage?.imageCacheBytes ?: 0L)))
                        Text(stringResource(R.string.settings_storage_cries, formatBytes(usage?.criesCacheBytes ?: 0L)))
                    }
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::measureStorage) { Text(stringResource(R.string.settings_storage_refresh)) }
                        TextButton(onClick = viewModel::clearDownloadedData) { Text(stringResource(R.string.settings_storage_clear)) }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text(
                stringResource(R.string.settings_display_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PrefetchTierRow(
                title = stringResource(R.string.settings_amoled_title),
                subtitle = stringResource(R.string.settings_amoled_subtitle),
                checked = uiState.amoledEnabled,
                onCheckedChange = viewModel::setAmoledEnabled
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_suggestions_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showTierExplanationDialog = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.settings_suggestions_help_content_description)
                    )
                }
            }
            Text(
                stringResource(R.string.settings_suggestions_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showTierDialog = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_tier_limit_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    uiState.maxSuggestionTier?.let { localizedTierLabel(it) } ?: stringResource(R.string.settings_tier_limit_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showLanguageDialog) {
        OptionsDialog(
            title = stringResource(R.string.settings_language_section),
            options = SupportedLanguages.ALL,
            labelFor = { it.label },
            selected = uiState.currentLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { language ->
                viewModel.setLanguage(language.code)
                showLanguageDialog = false
            }
        )
    }

    if (showTierDialog) {
        val noLimitLabel = stringResource(R.string.settings_tier_limit_none)
        OptionsDialog(
            title = stringResource(R.string.settings_tier_limit_dialog_title),
            options = listOf<String?>(null) + uiState.suggestionTierOptions,
            labelFor = { it?.let { tier -> localizedTierLabel(tier) } ?: noLimitLabel },
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
        // B8: resolved here, in SettingsScreen's own (correctly localized) composition, rather
        // than inside AlertDialog's title/text/button slot lambdas — those compose in a separate
        // Window whose LocalContext isn't the locale-overridden one (see LocalizedContext's doc,
        // and SmogonTierExplanationDialog for the same fix applied a different way).
        val warningTitle = stringResource(R.string.settings_full_detail_warning_title)
        val warningText = stringResource(R.string.settings_full_detail_warning_text)
        val enableLabel = stringResource(R.string.settings_full_detail_warning_enable)
        val cancelLabel = stringResource(R.string.settings_cancel)
        AlertDialog(
            onDismissRequest = { showFullDetailWarning = false },
            title = { Text(warningTitle) },
            text = { Text(warningText) },
            confirmButton = {
                TextButton(onClick = {
                    showFullDetailWarning = false
                    viewModel.setFullDetailEnabled(true)
                }) { Text(enableLabel) }
            },
            dismissButton = {
                TextButton(onClick = { showFullDetailWarning = false }) { Text(cancelLabel) }
            }
        )
    }

    if (showPrefetchConfirm) {
        // F63 — surfaces a size estimate (reusing each enabled tier's own subtitle, which already
        // states its rough size) at the moment of the tap, per B8's same resolved-here-not-in-slot-
        // lambdas fix used by showFullDetailWarning above.
        val estimateLines = buildList {
            if (uiState.essentialsEnabled) add(stringResource(R.string.settings_tier_essentials_subtitle))
            if (uiState.spritesEnabled) add(stringResource(R.string.settings_tier_sprites_subtitle))
            if (uiState.spritesExtraEnabled) add(stringResource(R.string.settings_tier_sprites_extra_subtitle))
            if (uiState.fullDetailEnabled) add(stringResource(R.string.settings_tier_full_detail_subtitle))
            if (uiState.criesEnabled) add(stringResource(R.string.settings_tier_cries_subtitle))
        }
        val confirmTitle = stringResource(R.string.settings_prefetch_confirm_title)
        val downloadLabel = stringResource(R.string.settings_prefetch_now)
        val cancelLabel = stringResource(R.string.settings_cancel)
        // F70 — isMeteredNetworkBlocked() previously only ran inside startPrefetch() itself, so
        // this dialog showed a plain "Download" button even when the tap was about to be refused
        // (Wi-Fi only on, off Wi-Fi) or would spend mobile data with no warning at all (Wi-Fi only
        // off, off Wi-Fi). Checked here too so the dialog reflects reality before the user commits.
        val meteredWarning = if (viewModel.isMeteredNetworkBlocked()) {
            stringResource(R.string.settings_prefetch_confirm_wifi_required_warning)
        } else if (PrefetchManager.isActiveNetworkMetered(LocalContext.current)) {
            stringResource(R.string.settings_prefetch_confirm_metered_warning)
        } else {
            null
        }
        AlertDialog(
            onDismissRequest = { showPrefetchConfirm = false },
            title = { Text(confirmTitle) },
            text = {
                Column {
                    Text(estimateLines.joinToString("\n\n"))
                    if (meteredWarning != null) {
                        Text(
                            meteredWarning,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrefetchConfirm = false
                    viewModel.startPrefetch()
                }) { Text(downloadLabel) }
            },
            dismissButton = {
                TextButton(onClick = { showPrefetchConfirm = false }) { Text(cancelLabel) }
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
