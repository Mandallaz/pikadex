package com.mandallaz.pikadex.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** One paragraph of the explanation — [heading] null for plain body text following the previous
 *  heading (e.g. a tier's own principle/example lines). */
internal data class TierExplanationSection(val heading: String?, val body: String)

/** Content for [SmogonTierExplanationDialog], from issue #30 — kept as data separate from the
 *  composable so it's testable without a Compose runtime. */
internal val SMOGON_TIER_EXPLANATION: List<TierExplanationSection> = listOf(
    TierExplanationSection(
        null,
        "Smogon's tiering system is the global standard for competitive singles Pokémon battles. " +
            "Its primary goal is to create a balanced environment where as many Pokémon as possible " +
            "can be played competitively."
    ),
    TierExplanationSection(
        null,
        "With the exception of Ubers and Anything Goes, most tiers are usage-based: the more a " +
            "Pokémon is used in a higher tier, the higher it ranks in the hierarchy."
    ),
    TierExplanationSection(
        "Core concept",
        "Smogon organizes Pokémon into playability tiers so lower-powered Pokémon do not have to " +
            "compete against overpowered legendaries."
    ),
    TierExplanationSection(
        "Format scope",
        "These tiers apply specifically to competitive Singles formats hosted on Pokémon Showdown."
    ),
    TierExplanationSection(
        "Primary tiers (from highest to lowest)",
        "1. AG (Anything Goes) — virtually no restrictions besides the 6-Pokémon team limit. " +
            "Home to specimens too powerful even for Ubers (e.g. Mega Rayquaza, Calyrex-Shadow) and " +
            "mechanics banned elsewhere.\n\n" +
            "2. Uber — historically the banlist for OU. The battleground for major legendaries and " +
            "over-centralizing threats (Kyogre, Koraidon, Miraidon, Zacian...).\n\n" +
            "3. OU (OverUsed) — the benchmark tier. Smogon's flagship format, used for most official " +
            "tournaments. Top-tier non-legendaries and balanced legendaries (Landorus-Therian, " +
            "Kingambit, Iron Valiant...).\n\n" +
            "4. UU (UnderUsed) — Pokémon below OU's usage threshold (~4.52%) but still strong " +
            "competitive options: powerful niche attackers or Pokémon outcompeted by current OU " +
            "trends.\n\n" +
            "5. RU (RarelyUsed) — below UU, for Pokémon that lack the versatility or power for UU.\n\n" +
            "6. NU (NeverUsed) — below RU, Pokémon often overlooked in higher divisions.\n\n" +
            "7. PU — the lowest official usage-based tier (named after \"P.U.\", a bad smell), still a " +
            "highly competitive and complex metagame.\n\n" +
            "8. ZU (Zero Used) — an unofficial tier below PU, giving the remaining Pokémon their own " +
            "competitive metagame."
    ),
    TierExplanationSection(
        "Usage threshold",
        "The boundary to stay in a tier is roughly 4.52%, meaning a Pokémon must appear on at least " +
            "1 in every 22 teams in that tier's ladder."
    ),
    TierExplanationSection(
        "Tier mobility",
        "A Pokémon in a lower tier (e.g. NU) can always be used in a higher tier (e.g. OU), but " +
            "higher-tier Pokémon cannot drop into lower tiers."
    ),
    TierExplanationSection(
        "Intermediate banlists & tier shifts",
        "Between main tiers exist intermediate categories called BL (e.g. UUBL, RUBL, NUBL, PUBL). " +
            "A Pokémon is placed in UUBL if it was banned from UU for being too strong, but doesn't " +
            "have high enough usage to be officially classified as OU — it can't be used in UU, but " +
            "is legal in OU."
    ),
    TierExplanationSection(
        "How tiers change",
        "1. Quarterly usage updates — every three months, Smogon analyzes battle statistics from " +
            "Pokémon Showdown. Pokémon exceeding the usage threshold move up, underused ones drop " +
            "down.\n\n" +
            "2. Suspect tests & quickbans — if a Pokémon, move, or ability overpowers a tier, the " +
            "council runs a community vote (Suspect Test) or quickbans it to the tier above."
    ),
    TierExplanationSection(
        "BL status",
        "Borderline categories are purely banlists for lower tiers, not playable metagames with " +
            "their own ladders."
    ),
    TierExplanationSection(
        "Community voting",
        "Suspect tests require players to achieve a high skill rating (GXE) on a fresh ladder " +
            "account to earn voting rights."
    )
)

/** Explains Smogon's competitive tiering system (AG/Uber/OU/UU/RU/NU/PU/ZU/BL) — opened from the
 *  "?" next to the Suggestion tier limit picker in Settings, for users unfamiliar with what those
 *  tier codes mean (issue #30). */
@Composable
fun SmogonTierExplanationDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        // Bounding the Surface itself (not just the scrollable body) matters in landscape: with
        // only the body height-capped, title + body + button could add up to more than the actual
        // window height and push the Close button off-screen with no way to reach it. Capping the
        // Surface and giving the body `weight(1f, fill = false)` makes the body the one section
        // that shrinks first, so title and button stay visible on any screen size/orientation.
        val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.heightIn(max = maxDialogHeight)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Standard Competitive Tiers",
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
                        if (section.heading != null) {
                            Text(
                                section.heading,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        Text(section.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
