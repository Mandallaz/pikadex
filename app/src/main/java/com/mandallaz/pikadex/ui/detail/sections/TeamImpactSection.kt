package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.UiText
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.TeamImpactSummary
import com.mandallaz.pikadex.util.TypeIds

/** issue #2's "team coverage impact" — what adding this Pokémon to the active team would
 *  change about its shared weaknesses and coverage gaps. Only ever composed while
 *  [com.mandallaz.pikadex.ui.detail.PokedexDetailScreen]'s showTeamImpactCard condition holds, so
 *  there's always a real team to preview against; the three states below (loading/error/result)
 *  cover everything that condition leaves open. */
@Composable
internal fun TeamImpactCard(
    isLoading: Boolean,
    error: UiText?,
    impact: TeamImpactSummary?
) {
    // A loaded result with all 7 categories empty means this Pokémon genuinely wouldn't change
    // anything about the team's coverage — worth saying explicitly ("Nothing.") rather than leaving
    // the generic subtitle as the only text on an otherwise-blank card, which read as if the card
    // just hadn't finished loading.
    val hasNoImpact = impact != null &&
        impact.weaknessesFixed.isEmpty() && impact.weaknessesIntroduced.isEmpty() &&
        impact.gapsClosed.isEmpty() && impact.gapsOpened.isEmpty() &&
        impact.immunitiesGained.isEmpty() && impact.quadWeaknessesGained.isEmpty() &&
        impact.resistancesGained.isEmpty()

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.detail_team_impact_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                if (hasNoImpact) {
                    stringResource(R.string.detail_team_impact_nothing)
                } else {
                    stringResource(R.string.detail_team_impact_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                error != null -> Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                impact != null && !hasNoImpact -> TeamImpactSummaryText(impact)
            }
        }
    }
}

/** The delta summary for issue #2 (revised 2026-08-09 twice more — user feedback: only show
 *  what actually changes, with type badges instead of plain type names, and grouped so it's clear
 *  which half is this Pokémon's defensive contribution (shared weaknesses, from [computeDefensiveMultipliers]-
 *  derived data) versus its offensive one (coverage gaps, from the offensive matrix) rather than 4
 *  same-looking rows a reader has to parse individually. A row (and its parent section, if both of
 *  its rows are empty) is omitted entirely rather than spelled out as "no new..." — a Pokémon that
 *  changes nothing on either side shows no text at all here (see [TeamImpactCard]'s "Nothing." case
 *  for when *both* sections are empty). */
@Composable
private fun TeamImpactSummaryText(impact: TeamImpactSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ImpactSection(
            stringResource(R.string.detail_defensively),
            stringResource(R.string.detail_fixes_weaknesses) to impact.weaknessesFixed,
            stringResource(R.string.detail_introduces_weaknesses) to impact.weaknessesIntroduced,
            stringResource(R.string.detail_adds_immunity) to impact.immunitiesGained,
            stringResource(R.string.detail_adds_resistance) to impact.resistancesGained,
            stringResource(R.string.detail_adds_quad_weakness) to impact.quadWeaknessesGained
        )
        ImpactSection(
            stringResource(R.string.detail_offensively),
            stringResource(R.string.detail_closes_gaps) to impact.gapsClosed,
            stringResource(R.string.detail_opens_gaps) to impact.gapsOpened
        )
    }
}

/** One half (defensive or offensive) of the summary — a heading over its rows, omitted entirely
 *  when both of [rows] are empty rather than showing a heading over nothing. */
@Composable
private fun ImpactSection(heading: String, vararg rows: Pair<String, List<String>>) {
    if (rows.all { (_, types) -> types.isEmpty() }) return
    Column {
        Text(heading, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            rows.forEach { (label, types) -> ImpactTypeRow(label, types) }
        }
    }
}

/** One impact category — omits itself (no label, no row) when [types] is empty, rather than the
 *  section above having to special-case which of its rows actually render. */
@Composable
private fun ImpactTypeRow(label: String, types: List<String>) {
    if (types.isEmpty()) return
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            types.forEach { type -> TypeBadge(type, TypeIds.idOrNull(type), height = 20.dp) }
        }
    }
}
