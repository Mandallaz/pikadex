package com.tg.pokedex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Small picker dialog for short, fixed option lists (a handful of items) — no search field,
 * unlike [SearchableListDialog] which is for the hundreds of moves/abilities. */
@Composable
fun <T> OptionsDialog(
    title: String,
    options: List<T>,
    labelFor: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                if (options.isEmpty()) {
                    Text(
                        "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(options) { option ->
                        TextButton(
                            onClick = { onSelect(option) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(labelFor(option), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
