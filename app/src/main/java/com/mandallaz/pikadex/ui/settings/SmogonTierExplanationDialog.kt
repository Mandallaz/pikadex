package com.mandallaz.pikadex.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.LocalizedContext

/** One paragraph of the explanation — [headingRes] null for plain body text following the
 *  previous heading (e.g. a tier's own principle/example lines). Resource ids rather than raw
 *  strings (B8): content lives in strings.xml/values-fr so it follows the F35 language picker,
 *  resolved reactively via [stringResource] in the composable rather than fixed at this
 *  top-level val's init time. */
internal data class TierExplanationSection(@StringRes val headingRes: Int?, @StringRes val bodyRes: Int)

/** Content for [SmogonTierExplanationDialog], from issue #30 — kept as data separate from the
 *  composable so its structure (section count/ordering) is testable without a Compose runtime;
 *  the actual text is tested by parsing strings.xml directly, see
 *  [com.mandallaz.pikadex.ui.settings.SmogonTierExplanationDialogTest]. */
internal val SMOGON_TIER_EXPLANATION: List<TierExplanationSection> = listOf(
    TierExplanationSection(null, R.string.smogon_tier_intro_1),
    TierExplanationSection(null, R.string.smogon_tier_intro_2),
    TierExplanationSection(R.string.smogon_tier_core_concept_heading, R.string.smogon_tier_core_concept_body),
    TierExplanationSection(R.string.smogon_tier_format_scope_heading, R.string.smogon_tier_format_scope_body),
    TierExplanationSection(R.string.smogon_tier_primary_tiers_heading, R.string.smogon_tier_primary_tiers_body),
    TierExplanationSection(R.string.smogon_tier_usage_threshold_heading, R.string.smogon_tier_usage_threshold_body),
    TierExplanationSection(R.string.smogon_tier_mobility_heading, R.string.smogon_tier_mobility_body),
    TierExplanationSection(R.string.smogon_tier_banlists_heading, R.string.smogon_tier_banlists_body),
    TierExplanationSection(R.string.smogon_tier_how_tiers_change_heading, R.string.smogon_tier_how_tiers_change_body),
    TierExplanationSection(R.string.smogon_tier_bl_status_heading, R.string.smogon_tier_bl_status_body),
    TierExplanationSection(R.string.smogon_tier_community_voting_heading, R.string.smogon_tier_community_voting_body)
)

/** Explains Smogon's competitive tiering system (AG/Uber/OU/UU/RU/NU/PU/ZU/BL) — opened from the
 *  "?" next to the Suggestion tier limit picker in Settings, for users unfamiliar with what those
 *  tier codes mean (issue #30). */
@Composable
fun SmogonTierExplanationDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        // B8: Dialog's content composes in a new Android Window whose LocalContext isn't the
        // locale-overridden one MainActivity provides — see LocalizedContext's own doc.
        LocalizedContext {
            // Bounding the Surface itself (not just the scrollable body) matters in landscape:
            // with only the body height-capped, title + body + button could add up to more than
            // the actual window height and push the Close button off-screen with no way to reach
            // it. Capping the Surface and giving the body `weight(1f, fill = false)` makes the
            // body the one section that shrinks first, so title and button stay visible on any
            // screen size/orientation.
            val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier.heightIn(max = maxDialogHeight)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.smogon_tier_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SMOGON_TIER_EXPLANATION.forEach { section ->
                            if (section.headingRes != null) {
                                Text(
                                    stringResource(section.headingRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            Text(stringResource(section.bodyRes), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.smogon_tier_dialog_close))
                    }
                }
            }
        }
    }
}
