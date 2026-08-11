package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.components.localizedLabel
import com.mandallaz.pikadex.util.Smogon
import com.mandallaz.pikadex.util.openExternalLink

@Composable
internal fun SmogonLinksCard(pokemonName: String, speciesGeneration: String, formVersionGroup: String?) {
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
                stringResource(R.string.detail_smogon_strategy_dex_title),
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
                        label = { Text(link.localizedLabel(), style = MaterialTheme.typography.labelMedium) },
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
