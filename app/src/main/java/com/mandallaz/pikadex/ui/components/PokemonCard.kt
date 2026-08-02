package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mandallaz.pikadex.data.FavoritesRepository
import com.mandallaz.pikadex.data.TeamRepository
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.toDisplayName

@Composable
fun PokemonCard(
    id: Int,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val team by TeamRepository.team.collectAsState()
    val favorites by FavoritesRepository.favorites.collectAsState()
    val isInTeam = team.any { it.name == name }
    val isTeamFull = team.size >= TeamRepository.MAX_SIZE
    val isFavorite = favorites.contains(name)

    Box(modifier = modifier.aspectRatio(0.82f)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = Sprites.officialArtworkUrl(id),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(96.dp)
                )
                Text(
                    text = "#${id.toString().padStart(4, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = name.toDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
        IconButton(
            onClick = { TeamRepository.toggle(NamedApiResource(name, "https://pokeapi.co/api/v2/pokemon/$id/")) },
            enabled = isInTeam || !isTeamFull,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = if (isInTeam) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                contentDescription = if (isInTeam) "Remove from team" else "Add to team",
                tint = if (isInTeam) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
        IconButton(
            onClick = { FavoritesRepository.toggle(name) },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
    }
}
