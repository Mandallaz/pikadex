package com.mandallaz.pikadex.ui.team.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.R
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.ui.components.PokemonSprite
import com.mandallaz.pikadex.util.localizedDisplayName

@Composable
internal fun TeamMemberChip(
    member: NamedApiResource,
    speciesNames: Map<String, Map<String, String>>,
    language: String,
    onRemove: () -> Unit,
    onSpriteClick: () -> Unit
) {
    // The remove button used to be a 20dp IconButton — well under the 48dp minimum touch target
    // and overlapping the sprite. It's now a full 48dp target, offset to peek outside the chip's
    // top-right corner (a standard "close badge" placement) so it doesn't crowd the sprite/name,
    // with extra top padding on the Box to give it room and extra Row spacing (in the caller) so
    // neighboring chips' peeking buttons don't collide.
    Box(modifier = Modifier.padding(top = 12.dp, end = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PokemonSprite(
                id = member.id ?: 0,
                contentDescription = member.name,
                modifier = Modifier.size(56.dp).clickable(onClick = onSpriteClick)
            )
            Text(member.name.localizedDisplayName(speciesNames, language), style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = (-14).dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.team_remove_member_cd, member.name.localizedDisplayName(speciesNames, language)),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun AddMemberChip(onClick: () -> Unit) {
    Box(modifier = Modifier.padding(top = 12.dp, end = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.team_add_member_cd))
            }
            Text(stringResource(R.string.team_add_label), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
