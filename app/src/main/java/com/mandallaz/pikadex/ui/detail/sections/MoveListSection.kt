package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.ui.components.localizedLabel
import com.mandallaz.pikadex.ui.detail.MoveLabels
import com.mandallaz.pikadex.ui.detail.moveMetaLabel
import com.mandallaz.pikadex.ui.detail.moveStatsLabel
import com.mandallaz.pikadex.util.LearnedMove
import com.mandallaz.pikadex.util.MoveCategory
import com.mandallaz.pikadex.util.SortStat
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.localizedDisplayName

/**
 * Renders one move category as real [LazyListScope] items (a header, then — only while expanded —
 * one item per move) instead of a header wrapping a plain [Column] of all rows. A pokemon like Mew
 * has ~250 TM/HM entries; composing all of them in one non-lazy Column the instant the section
 * expands was a multi-hundred-millisecond hitch. As real lazy items, only the rows actually on or
 * near screen get composed, the same as the rest of this pokemon detail page's own LazyColumn.
 *
 * The header and rows share one rounded-corner "card" look across separate list items: the header
 * is flat-bottomed while expanded, the last row is rounded-bottomed, and both share the same
 * surface color, so it still reads as a single grouped section rather than a stack of independent
 * cards.
 */
internal fun LazyListScope.moveSection(
    category: MoveCategory,
    moves: List<LearnedMove>,
    moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo>,
    expanded: Boolean,
    moveLocalizedNames: Map<String, Map<String, String>>,
    language: String,
    onToggleExpanded: () -> Unit
) {
    // An empty category (e.g. no Egg moves for a legendary) used to render as a normal expandable
    // header with a chevron inviting a tap, only to reveal a single "No moves in this category."
    // line — every empty section cost the user a tap for nothing. It's now flat, non-clickable, and
    // visibly dimmed instead, so "there's nothing here" is obvious without expanding it.
    item(key = "movesection-header-${category.name}") {
        Surface(
            onClick = onToggleExpanded,
            enabled = moves.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
            shape = if (expanded && moves.isNotEmpty()) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            } else {
                RoundedCornerShape(16.dp)
            },
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.detail_move_category_header, category.localizedLabel(), moves.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (moves.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.Unspecified
                )
                if (moves.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) {
                            stringResource(R.string.detail_collapse)
                        } else {
                            stringResource(R.string.detail_expand)
                        }
                    )
                }
            }
        }
    }

    if (!expanded || moves.isEmpty()) return

    itemsIndexed(
        moves,
        key = { _, move -> "movesection-${category.name}-${move.moveName}-${move.level}" }
    ) { index, move ->
        val isLast = index == moves.lastIndex
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = if (isLast) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                MoveRow(move, category, moveInfo, moveLocalizedNames, language)
            }
        }
    }
}

/** B11 — a `when` over [MoveCategory], not a field on the enum itself: [MoveCategory] lives in
 *  `util/MoveGrouping.kt`, which has no Compose dependency and shouldn't gain one just for this. */
@Composable
private fun MoveCategory.localizedLabel(): String = stringResource(
    when (this) {
        MoveCategory.LEVEL_UP -> R.string.detail_move_category_level_up
        MoveCategory.MACHINE -> R.string.detail_move_category_machine
        MoveCategory.EGG -> R.string.detail_move_category_egg
        MoveCategory.TUTOR -> R.string.detail_move_category_tutor
    }
)

/** B30 — resolves [MoveLabels]' full set of strings/templates from resources; [MoveLabels]' own
 *  field defaults exist only for pure-function callers (unit tests), never used here. These
 *  particular resources (`detail_damage_class_*`, `detail_move_stats_line*`, `detail_ailment_*`,
 *  `detail_crit_rate`/`detail_drains`/`detail_recoil`/`detail_heals`/`detail_flinch_chance`)
 *  already existed, already translated into all 11 locales — apparently prepared for exactly this
 *  fix and then never wired up. Only `detail_stat_change_chance` needed adding fresh. */
@Composable
private fun moveLabels(): MoveLabels = MoveLabels(
    physical = stringResource(R.string.detail_damage_class_physical),
    special = stringResource(R.string.detail_damage_class_special),
    status = stringResource(R.string.detail_damage_class_status),
    dash = stringResource(R.string.detail_dash_placeholder),
    line = stringResource(R.string.detail_move_stats_line),
    lineWithPriority = stringResource(R.string.detail_move_stats_line_with_priority),
    always = stringResource(R.string.detail_ailment_always),
    ailmentChance = stringResource(R.string.detail_ailment_chance),
    statChangeChance = stringResource(R.string.detail_stat_change_chance),
    critRate = stringResource(R.string.detail_crit_rate),
    drains = stringResource(R.string.detail_drains),
    recoil = stringResource(R.string.detail_recoil),
    heals = stringResource(R.string.detail_heals),
    flinchChance = stringResource(R.string.detail_flinch_chance),
    statNames = SortStat.entries.filter { it.apiName != null }.associate { it.apiName!! to it.localizedLabel() }
)

@Composable
private fun MoveRow(
    move: LearnedMove,
    category: MoveCategory,
    moveInfo: Map<String, PokeApiGraphQLDataSource.MoveInfo>,
    moveLocalizedNames: Map<String, Map<String, String>>,
    language: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            move.moveName.localizedDisplayName(moveLocalizedNames, language),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (category == MoveCategory.LEVEL_UP) {
            Text(
                if (move.level > 0) {
                    stringResource(R.string.detail_move_level, move.level)
                } else {
                    stringResource(R.string.detail_move_evolution)
                }
            )
        }
    }
    moveInfo[move.moveName]?.let { info ->
        // B30 — resolved here, once per row, and threaded into the two pure functions below rather
        // than having them read stringResource() themselves (they aren't @Composable, so existing
        // unit tests can keep calling them directly — same reasoning as MoveLabels' own doc).
        val labels = moveLabels()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            TypeBadge(info.type, TypeIds.idOrNull(info.type), height = 18.dp)
            Text(
                text = moveStatsLabel(info, labels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // F37: a second line only when there's genuinely competitive info to show (see
        // moveMetaLabel's own doc on why null means "render nothing" here, not an empty Text).
        moveMetaLabel(info, labels)?.let { metaText ->
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
