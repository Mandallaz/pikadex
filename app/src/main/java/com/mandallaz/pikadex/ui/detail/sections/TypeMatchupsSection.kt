package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.OptionsDialog
import com.mandallaz.pikadex.ui.components.localizedTypeName
import com.mandallaz.pikadex.ui.components.typeIcon
import com.mandallaz.pikadex.util.TypeColors
import com.mandallaz.pikadex.util.TypeIds

/** [onSelectTeraType] null clears the preview back to the Pokémon's real typing. [teraTypeOptions],
 *  when non-empty, ranks the picker best-first (see [com.mandallaz.pikadex.util.rankTeraTypes]);
 *  empty falls back to [TypeIds.standardTypeNames]'s own order (still loading, or the ranking
 *  fetch failed). [onOpenTeraDialog] triggers that ranking fetch — called once when the dialog
 *  opens, not before, since most detail-screen visits never open it. All F90-follow-up params are
 *  defaulted to no-ops/empty so callers that don't wire up Tera preview (e.g. instrumented tests
 *  rendering this card standalone) keep compiling unchanged. */
@Composable
internal fun TypeMatchupsCard(
    typeMatchups: Map<String, Double>,
    teraType: String? = null,
    onSelectTeraType: (String?) -> Unit = {},
    teraTypeOptions: List<Pair<String, Int>> = emptyList(),
    onOpenTeraDialog: () -> Unit = {}
) {
    var showTeraDialog by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    stringResource(R.string.detail_type_matchups_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // F90 — chip label reflects the active preview (the type's own localized name) so
                // it's visible at a glance without opening the dialog, same as the Sort chip in
                // PokedexListScreen showing its currently-picked stat. Leading icon mirrors
                // TypeBadge's icon-only mode once a type is actually selected — no icon for the
                // default "Terastallize" label, since there's no type to represent yet.
                AssistChip(
                    onClick = {
                        onOpenTeraDialog()
                        showTeraDialog = true
                    },
                    label = {
                        Text(
                            teraType?.localizedTypeName()
                                ?: stringResource(R.string.detail_tera_type_label)
                        )
                    },
                    leadingIcon = teraType?.let { type ->
                        {
                            Icon(
                                imageVector = typeIcon(type),
                                contentDescription = null,
                                tint = TypeColors.of(type),
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    }
                )
            }
            com.mandallaz.pikadex.ui.components.TypeMatchupGroups(typeMatchups)
        }
    }

    if (showTeraDialog) {
        val noneLabel = stringResource(R.string.detail_tera_type_none)
        // F90 follow-up — falls back to standardTypeNames' own order (unscored) while the ranking
        // is still loading or failed; scoreByType stays empty then, so scoreSuffix omits the score
        // rather than showing a misleading "(0)" for every option.
        val rankedNames = teraTypeOptions.map { it.first }.ifEmpty { TypeIds.standardTypeNames }
        val scoreByType = teraTypeOptions.toMap()
        OptionsDialog(
            title = stringResource(R.string.detail_tera_type_dialog_title),
            options = listOf<String?>(null) + rankedNames,
            labelFor = { type ->
                if (type == null) {
                    noneLabel
                } else {
                    val score = scoreByType[type]
                    val scoreSuffix = if (score != null) " (${if (score > 0) "+$score" else score.toString()})" else ""
                    type.localizedTypeName() + scoreSuffix
                }
            },
            selected = teraType,
            iconFor = { type ->
                type?.let {
                    Icon(
                        imageVector = typeIcon(it),
                        contentDescription = null,
                        tint = TypeColors.of(it),
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                }
            },
            onDismiss = { showTeraDialog = false },
            onSelect = { type ->
                onSelectTeraType(type)
                showTeraDialog = false
            }
        )
    }
}
