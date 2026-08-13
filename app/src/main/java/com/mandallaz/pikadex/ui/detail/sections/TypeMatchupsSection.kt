package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.OptionsDialog
import com.mandallaz.pikadex.ui.components.localizedTypeName
import com.mandallaz.pikadex.util.TypeIds

/** [onSelectTeraType] null clears the preview back to the Pokémon's real typing — F90's picker,
 *  defaulted to a no-op so callers that don't wire up Tera preview (e.g. instrumented tests
 *  rendering this card standalone) keep compiling unchanged. */
@Composable
internal fun TypeMatchupsCard(
    typeMatchups: Map<String, Double>,
    teraType: String? = null,
    onSelectTeraType: (String?) -> Unit = {}
) {
    var showTeraDialog by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    stringResource(R.string.detail_type_matchups_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // F90 — chip label reflects the active preview (the type's own localized name) so
                // it's visible at a glance without opening the dialog, same as the Sort chip in
                // PokedexListScreen showing its currently-picked stat.
                AssistChip(
                    onClick = { showTeraDialog = true },
                    label = {
                        Text(
                            teraType?.localizedTypeName()
                                ?: stringResource(R.string.detail_tera_type_label)
                        )
                    }
                )
            }
            com.mandallaz.pikadex.ui.components.TypeMatchupGroups(typeMatchups)
        }
    }

    if (showTeraDialog) {
        val noneLabel = stringResource(R.string.detail_tera_type_none)
        OptionsDialog(
            title = stringResource(R.string.detail_tera_type_dialog_title),
            options = listOf<String?>(null) + TypeIds.standardTypeNames,
            labelFor = { it?.localizedTypeName() ?: noneLabel },
            selected = teraType,
            onDismiss = { showTeraDialog = false },
            onSelect = { type ->
                onSelectTeraType(type)
                showTeraDialog = false
            }
        )
    }
}
