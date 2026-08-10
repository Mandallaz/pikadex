package com.mandallaz.pikadex.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.TeamSlot

/** Lets the user switch between, create, rename, or delete their named teams — reached by tapping
 *  the team name in [TeamScreen]'s top bar. A plain [AlertDialog] with a scrolling list rather than
 *  a full-screen picker (unlike [PresetTeamDialog]): the trainer-team list is ~80 entries with a
 *  sprite preview each, this is realistically a handful of teams with just a name and a count. */
@Composable
fun TeamSlotsDialog(
    teams: List<TeamSlot>,
    activeTeamId: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onCreate: () -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    var renaming by remember { mutableStateOf<TeamSlot?>(null) }
    var pendingDelete by remember { mutableStateOf<TeamSlot?>(null) }

    // AlertDialog opens its own Window whose LocalContext re-resolves to the un-overridden one (see
    // LocalizedContext's own doc) — unlike Dialog(), Material3's AlertDialog gives no content slot to
    // wrap from the inside, so every string it needs is resolved here, in this composable's own
    // (correctly-localized) scope, and passed down as a plain string. Same fix SettingsScreen's
    // full-detail-warning AlertDialog already uses.
    val slotsTitle = stringResource(R.string.teamslots_title)
    val activeCd = stringResource(R.string.teamslots_active_cd)
    val newTeamLabel = stringResource(R.string.teamslots_new_team)
    val closeLabel = stringResource(R.string.teamslots_close)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slotsTitle) },
        text = {
            LazyColumn {
                items(teams, key = { it.id }) { slot ->
                    val renameCd = stringResource(R.string.teamslots_rename_cd, slot.name)
                    val deleteCd = stringResource(R.string.teamslots_delete_cd, slot.name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(slot.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (slot.id == activeTeamId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = activeCd,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            // Keeps every row's text aligned to the same start position regardless
                            // of whether it's the active team, instead of the label jumping left
                            // for every non-active row.
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                slot.name,
                                fontWeight = if (slot.id == activeTeamId) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "${slot.size}/${TeamRepository.MAX_SIZE}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { renaming = slot }) {
                            Icon(Icons.Filled.Edit, contentDescription = renameCd)
                        }
                        // Deleting the last remaining team would leave the active-team pointer with
                        // nowhere to point — TeamRepository.deleteTeam already refuses this, hiding
                        // the button here is just not offering an action that would silently do
                        // nothing.
                        if (teams.size > 1) {
                            IconButton(onClick = { pendingDelete = slot }) {
                                Icon(Icons.Filled.Delete, contentDescription = deleteCd)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) { Text(newTeamLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(closeLabel) }
        }
    )

    renaming?.let { slot ->
        RenameTeamDialog(
            initialName = slot.name,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                onRename(slot.id, newName)
                renaming = null
            }
        )
    }

    pendingDelete?.let { slot ->
        val deleteTitle = stringResource(R.string.teamslots_delete_confirm_title, slot.name)
        val deleteBody = stringResource(R.string.teamslots_delete_confirm_body, slot.size)
        val deleteLabel = stringResource(R.string.teamslots_delete)
        val cancelLabel = stringResource(R.string.teamslots_cancel)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(deleteTitle) },
            text = { Text(deleteBody) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(slot.id)
                    pendingDelete = null
                }) { Text(deleteLabel) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(cancelLabel) }
            }
        )
    }
}

@Composable
private fun RenameTeamDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val renameTitle = stringResource(R.string.teamslots_rename_title)
    val saveLabel = stringResource(R.string.teamslots_save)
    val cancelLabel = stringResource(R.string.teamslots_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(renameTitle) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        }
    )
}
