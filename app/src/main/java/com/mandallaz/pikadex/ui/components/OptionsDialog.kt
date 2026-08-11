package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.LocalizedContext
import com.mandallaz.pikadex.util.SmogonGen
import com.mandallaz.pikadex.util.SmogonLink
import com.mandallaz.pikadex.util.SmogonTierLabels
import com.mandallaz.pikadex.util.SortStat

/** issue #71 (B21) — [SmogonGen.labelRes] resolved at render time, same @StringRes-then-render
 *  pattern as [MoveCategory]'s own `localizedLabel()` extension in `PokedexDetailScreen.kt`. */
@Composable
fun SmogonGen.localizedLabel(): String = stringResource(labelRes)

/** Same as [SmogonGen.localizedLabel] above, for the Smogon strategy-dex chips. */
@Composable
fun SmogonLink.localizedLabel(): String = stringResource(labelRes)

/** issue #71 (B21) — resolves a tier code to its localized label via [SmogonTierLabels.labelResFor],
 *  falling back to the raw code for a tier this app doesn't have a label for (same fallback the old
 *  plain-String `labelFor` had). Kept here, not in [SmogonTierLabels] itself, so that plain-Kotlin
 *  object doesn't gain a Compose dependency just for this. */
@Composable
fun localizedTierLabel(tierCode: String): String =
    SmogonTierLabels.labelResFor(tierCode)?.let { stringResource(it) } ?: tierCode

/** B30 — same pattern as [SmogonGen.localizedLabel] above. */
@Composable
fun SortStat.localizedLabel(): String = stringResource(labelRes)

/** Small picker dialog for short, fixed option lists (a handful of items) — no search field,
 * unlike [SearchableListDialog] which is for the hundreds of moves/abilities.
 *
 * [selected], when provided, highlights the currently-active option (background tint + leading
 * checkmark) — previously every row looked identical whether or not it was the current choice, so
 * reopening e.g. the Sort dialog gave no hint of what was already selected. The list height caps
 * at 60% of the screen rather than a flat 420dp, which used to clip the bottom couple of entries
 * (Gen 9 in the Format dialog) on shorter devices while leaving unused space on taller ones.
 *
 * [labelFor] is a @Composable lambda (B21) — needed so callers can resolve a @StringRes-backed
 * label (e.g. [SmogonGen.localizedLabel], [SortStat.localizedLabel]) via stringResource() from
 * inside it, not just return an already-hardcoded String like
 * [com.mandallaz.pikadex.util.RarityFilter.label] still does (out of scope for B30 — not part of
 * the review finding that prompted [SortStat]'s own fix). */
@Composable
fun <T> OptionsDialog(
    title: String,
    options: List<T>,
    labelFor: @Composable (T) -> String,
    selected: T? = null,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Dialog opens its own Window, whose LocalContext isn't the locale-overridden one MainActivity
        // provides — see LocalizedContext's own doc (same fix as SmogonTierExplanationDialog/B8).
        LocalizedContext {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    if (options.isEmpty()) {
                        Text(
                            stringResource(R.string.options_dialog_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
                    LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                        items(options) { option ->
                            val isSelected = option == selected
                            ListItem(
                                headlineContent = {
                                    Text(labelFor(option), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                },
                                leadingContent = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.options_dialog_selected_cd), tint = MaterialTheme.colorScheme.primary) }
                                } else {
                                    null
                                },
                                colors = if (isSelected) {
                                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                } else {
                                    ListItemDefaults.colors()
                                },
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}
