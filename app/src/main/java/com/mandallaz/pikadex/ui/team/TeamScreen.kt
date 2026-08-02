package com.mandallaz.pikadex.ui.team

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.toDisplayName

private val TYPE_COLUMN_WIDTH = 88.dp
private val MEMBER_COLUMN_WIDTH = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    onBack: () -> Unit,
    onBrowsePokedex: () -> Unit,
    viewModel: TeamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Team (${uiState.members.size}/${TeamRepository.MAX_SIZE})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.members.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Your team is empty. Add up to 6 Pokémon from the Pokédex to see how the team resists or is weak to each type.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onBrowsePokedex) { Text("Browse Pokédex") }
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.members.forEach { member ->
                    TeamMemberChip(member, onRemove = { viewModel.removeFromTeam(member) })
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            val sharedWeaknesses = uiState.sharedWeaknesses
            if (sharedWeaknesses.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null)
                            Text(
                                " Team-wide weaknesses",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            "At least half your team is weak to: " +
                                sharedWeaknesses.joinToString(", ") { it.toDisplayName() },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Header row: one column per team member.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(modifier = Modifier.width(TYPE_COLUMN_WIDTH))
                        uiState.members.forEach { member ->
                            Column(
                                modifier = Modifier.width(MEMBER_COLUMN_WIDTH),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    // The small sprite (~1-2KB) looks equal or better at this size
                                    // than an upscaled full artwork image (~100-200KB) would.
                                    model = Sprites.defaultSpriteUrl(member.id ?: 0),
                                    contentDescription = member.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    TypeIds.standardTypeNames.forEach { typeName ->
                        val row = uiState.matrix[typeName].orEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(modifier = Modifier.width(TYPE_COLUMN_WIDTH)) {
                                TypeBadge(typeName, TypeIds.of(typeName), height = 20.dp)
                            }
                            uiState.members.forEach { member ->
                                val multiplier = row[member.name] ?: 1.0
                                Box(
                                    modifier = Modifier
                                        .width(MEMBER_COLUMN_WIDTH)
                                        .padding(2.dp)
                                        .background(multiplierColor(multiplier)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(multiplierLabel(multiplier), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMemberChip(member: NamedApiResource, onRemove: () -> Unit) {
    Box {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = Sprites.defaultSpriteUrl(member.id ?: 0),
                contentDescription = member.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp)
            )
            Text(member.name.toDisplayName(), style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove ${member.name} from team")
        }
    }
}

private fun multiplierLabel(multiplier: Double): String = when (multiplier) {
    4.0 -> "×4"
    2.0 -> "×2"
    0.5 -> "×½"
    0.25 -> "×¼"
    0.0 -> "×0"
    else -> ""
}

private fun multiplierColor(multiplier: Double): Color = when {
    multiplier >= 2.0 -> Color(0xFFFFCDD2)
    multiplier == 0.0 -> Color(0xFFB3E5FC)
    multiplier < 1.0 -> Color(0xFFC8E6C9)
    else -> Color.Transparent
}
