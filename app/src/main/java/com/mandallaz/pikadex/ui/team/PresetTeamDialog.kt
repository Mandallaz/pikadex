package com.mandallaz.pikadex.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.util.PresetRole
import com.mandallaz.pikadex.util.PresetTeam
import com.mandallaz.pikadex.util.PresetTeams
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.toDisplayName

/**
 * Full-screen picker for the built-in trainer rosters, grouped by game. Full-screen rather than a
 * plain [AlertDialog] because the list is ~80 entries long and each row previews its whole team;
 * the same reasoning (and window-inset handling) as the move/ability picker.
 *
 * Confirms before replacing a non-empty roster: loading a preset is destructive and there's no
 * undo, so silently discarding a team the user spent time assembling isn't acceptable — but
 * confirming an *empty* team would be a pointless extra tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetTeamDialog(
    currentTeamSize: Int,
    spriteIds: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelect: (PresetTeam) -> Unit,
    onSelectIntoNewTeam: (PresetTeam) -> Unit
) {
    var pendingConfirmation by remember { mutableStateOf<PresetTeam?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    TopAppBar(
                        title = { Text("Trainer teams") },
                        actions = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    PresetTeams.BY_GAME.forEach { (game, teams) ->
                        item(key = "header-$game") {
                            Text(
                                game,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(teams.size, key = { "$game-${teams[it].trainer}-${teams[it].role}" }) { index ->
                            val team = teams[index]
                            PresetTeamRow(
                                team = team,
                                spriteIds = spriteIds,
                                onClick = {
                                    if (currentTeamSize == 0) onSelect(team) else pendingConfirmation = team
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    pendingConfirmation?.let { team ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Load ${team.trainer}'s team?") },
            text = {
                Text(
                    "Your current team has $currentTeamSize Pokémon. Replace it with " +
                        "${team.trainer}'s team, or load it into a new team instead?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingConfirmation = null
                    onSelect(team)
                }) { Text("Replace") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingConfirmation = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        pendingConfirmation = null
                        onSelectIntoNewTeam(team)
                    }) { Text("New team") }
                }
            }
        )
    }
}

@Composable
private fun PresetTeamRow(team: PresetTeam, spriteIds: Map<String, Int>, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(team.trainer, fontWeight = FontWeight.Medium)
                if (team.role == PresetRole.CHAMPION) {
                    AssistChip(
                        onClick = onClick,
                        enabled = false,
                        label = { Text(team.role.label, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        supportingContent = {
            // Sprites rather than a comma-joined name list: at 6 members the names wrap to three
            // lines and stop being scannable, and the sprite is what identifies a Pokémon at a
            // glance anyway. Falls back to names when the dex ids aren't resolved yet (offline).
            if (spriteIds.isEmpty()) {
                Text(team.pokemon.joinToString(" · ") { it.toDisplayName() })
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    team.pokemon.forEach { name ->
                        PokemonSprite(
                            id = spriteIds[name] ?: 0,
                            contentDescription = name.toDisplayName(),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
