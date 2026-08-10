package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.ui.LocalizedContext
import com.mandallaz.pikadex.util.toDisplayName

/** Full-screen dialog with a search field, used to pick a move or ability among several hundred
 * options — a plain dropdown would be unusable at that scale. [clearLabel], when non-null, adds a
 * leading "Any ___" row that calls [onSelect] with null — otherwise there was no way to clear a
 * move/ability filter short of the app-wide Reset, unlike the sibling Format/Tier dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableListDialog(
    title: String,
    options: List<String>,
    clearLabel: String? = null,
    // B9 follow-up — defaults to the existing English-only formatting for the move/ability callers,
    // which have no language-specific data at all today. The species pickers (Compare's own and the
    // detail screen's "Compare with…") pass their localizedDisplayName lookup instead, so this list
    // follows the picked language the same way every other species name in the app now does.
    displayName: (String) -> String = { it.toDisplayName() },
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    // rememberSaveable: rotating while this dialog was open used to silently clear whatever the
    // user had already typed.
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) {
            options
        } else {
            // Options are raw API names ("double-slap"); typing the exact text the dialog *shows*
            // ("double slap", via toDisplayName()) matched nothing, since a hyphen and a space
            // aren't the same character. Stripping both hyphens and spaces from each side before
            // comparing means either form finds it.
            val normalizedQuery = query.trim().lowercase().replace(" ", "").replace("-", "")
            options.filter { it.lowercase().replace("-", "").contains(normalizedQuery) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false lets this dialog's window draw edge-to-edge behind the
        // status/nav bars, so Scaffold's own systemBars inset handling (below) can reserve the
        // right amount of space — without it, the window was sized incorrectly and left dark
        // scrim bands showing above/below the content instead of a true full screen.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Dialog opens its own Window, whose LocalContext isn't the locale-overridden one MainActivity
        // provides — see LocalizedContext's own doc (same fix as SmogonTierExplanationDialog/B8).
        LocalizedContext {
            Surface(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars,
                    topBar = {
                        TopAppBar(
                            title = { Text(title) },
                            actions = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.searchable_dialog_close_cd))
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.searchable_dialog_search_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true
                        )
                        if (options.isEmpty()) {
                            Text(
                                stringResource(R.string.searchable_dialog_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else if (filtered.isEmpty()) {
                            Text(
                                stringResource(R.string.searchable_dialog_no_matches, query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            if (clearLabel != null) {
                                item {
                                    ListItem(
                                        headlineContent = { Text(clearLabel, fontWeight = FontWeight.Medium) },
                                        leadingContent = { Icon(Icons.Default.Clear, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                            items(filtered) { option ->
                                ListItem(
                                    headlineContent = { Text(displayName(option)) },
                                    modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
