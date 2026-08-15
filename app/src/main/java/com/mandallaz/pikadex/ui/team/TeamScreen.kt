package com.mandallaz.pikadex.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.ui.components.OptionsDialog
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.localizedTypeName
import com.mandallaz.pikadex.ui.components.typeIcon
import com.mandallaz.pikadex.ui.team.sections.AddMemberChip
import com.mandallaz.pikadex.util.TypeColors
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.localizedDisplayName
import com.mandallaz.pikadex.ui.team.sections.TeamMatrix
import com.mandallaz.pikadex.ui.team.sections.TeamMemberChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    onBrowsePokedex: () -> Unit,
    // issue #17 — opens a suggestion tile's own detail page on sprite tap. Distinct from
    // onBrowsePokedex (which switches to the Pokédex list tab); this pushes a detail screen the
    // same way tapping a Pokédex list row does, so Back returns here.
    onPokemonClick: (String) -> Unit,
    viewModel: TeamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val language by LanguageSettings.currentLanguage.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val activeTeamId by viewModel.activeTeamId.collectAsState()
    val activeTeamName = teams.firstOrNull { it.id == activeTeamId }?.name ?: stringResource(R.string.team_default_name)
    var showPresetPicker by rememberSaveable { mutableStateOf(false) }
    var showTeamSlots by rememberSaveable { mutableStateOf(false) }
    var selectedMemberForTera by rememberSaveable { mutableStateOf<String?>(null) }
    // Resolved here, not inside TeamSlotsDialog's onCreate lambda below — stringResource() is
    // @Composable and that lambda isn't.
    val newTeamDefaultName = stringResource(R.string.team_new_team_default_name)

    Scaffold(
        topBar = {
            PikaDexTopBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showTeamSlots = true }
                    ) {
                        Text("$activeTeamName (${uiState.members.size}/${TeamRepository.MAX_SIZE})")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.team_switch_team_cd))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.preparePresetPreviews()
                        showPresetPicker = true
                    }) {
                        Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.team_load_trainer_team_cd))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.members.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.team_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onBrowsePokedex) { Text(stringResource(R.string.team_browse_pokedex)) }
                    TextButton(onClick = {
                        viewModel.preparePresetPreviews()
                        showPresetPicker = true
                    }) { Text(stringResource(R.string.team_or_load_trainer_team)) }
                }
                return@BoxWithConstraints
            }

            TeamMatrix(
                uiState = uiState,
                language = language,
                maxHeight = maxHeight,
                onAddSuggestion = viewModel::addSuggestion,
                onPokemonClick = onPokemonClick,
                onTeraClick = { memberName ->
                    selectedMemberForTera = memberName
                    viewModel.loadTeraTypeOptionsForMember(memberName)
                },
                headerContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        uiState.members.forEach { member ->
                            TeamMemberChip(
                                member = member,
                                speciesNames = uiState.speciesNames,
                                language = language,
                                teraType = uiState.teraTypes[member.name],
                                onRemove = { viewModel.removeFromTeam(member) },
                                onSpriteClick = { onPokemonClick(member.name) },
                                onTeraClick = {
                                    selectedMemberForTera = member.name
                                    viewModel.loadTeraTypeOptionsForMember(member.name)
                                }
                            )
                        }
                        if (uiState.members.size < TeamRepository.MAX_SIZE) {
                            AddMemberChip(onClick = onBrowsePokedex)
                        }
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }

                    uiState.errorMessage?.let {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(it.resolve(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            Button(onClick = viewModel::retry, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.team_retry)) }
                        }
                    }
                }
            )
        }
    }

    if (showPresetPicker) {
        PresetTeamDialog(
            currentTeamSize = uiState.members.size,
            spriteIds = uiState.presetSpriteIds,
            speciesNames = uiState.speciesNames,
            language = language,
            onDismiss = { showPresetPicker = false },
            onSelect = { preset ->
                viewModel.loadPreset(preset)
                showPresetPicker = false
            },
            onSelectIntoNewTeam = { preset ->
                viewModel.loadPresetIntoNewTeam(preset)
                showPresetPicker = false
            }
        )
    }

    if (showTeamSlots) {
        TeamSlotsDialog(
            teams = teams,
            activeTeamId = activeTeamId,
            onDismiss = { showTeamSlots = false },
            onSelect = { id ->
                viewModel.setActiveTeam(id)
                showTeamSlots = false
            },
            onCreate = {
                val newId = viewModel.createTeam(newTeamDefaultName)
                viewModel.setActiveTeam(newId)
                showTeamSlots = false
            },
            onRename = viewModel::renameTeam,
            onDelete = viewModel::deleteTeam
        )
    }

    selectedMemberForTera?.let { memberName ->
        val noneLabel = stringResource(R.string.detail_tera_type_none)
        val rankedNames = uiState.memberTeraTypeOptions.map { it.first }.ifEmpty { TypeIds.standardTypeNames }
        val scoreByType = uiState.memberTeraTypeOptions.toMap()
        val displayName = memberName.localizedDisplayName(uiState.speciesNames, language)
        OptionsDialog(
            title = stringResource(R.string.team_tera_type_dialog_title, displayName),
            options = listOf<String?>(null) + rankedNames,
            labelFor = { type ->
                if (type == null) {
                    noneLabel
                } else {
                    val score = scoreByType[type]
                    val scoreSuffix = if (score != null) " (${if (score > 0) "+$score" else score.toString()})" else ""
                    type.localizedTypeName() + scoreSuffix
                }
            },
            selected = uiState.teraTypes[memberName],
            iconFor = { type ->
                type?.let {
                    Icon(
                        imageVector = typeIcon(it),
                        contentDescription = null,
                        tint = TypeColors.of(it),
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                }
            },
            onDismiss = { selectedMemberForTera = null },
            onSelect = { type ->
                viewModel.setTeraType(memberName, type)
                selectedMemberForTera = null
            }
        )
    }
}
