package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mandallaz.pikadex.util.Sprites
import com.mandallaz.pikadex.util.toDisplayName

/** Official type badge image (icon + name baked in), used instead of a plain text pill.
 *
 *  [typeId] is nullable (and `0`, [com.mandallaz.pikadex.util.TypeIds.of]'s legacy sentinel for
 *  the same case, is treated the same way) for a type this app has no icon id for — a move whose
 *  type PokeAPI added after [com.mandallaz.pikadex.util.TypeIds]'s map was written, say. Building
 *  a URL from id 0 anyway is a guaranteed 404 that renders as a permanent blank gap; a text pill
 *  is degraded but genuinely informative. */
@Composable
fun TypeBadge(typeName: String, typeId: Int?, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    if (typeId == null || typeId == 0) {
        Text(typeName.toDisplayName(), style = MaterialTheme.typography.labelMedium, modifier = modifier)
        return
    }
    AsyncImage(
        model = Sprites.typeIconUrl(typeId),
        contentDescription = typeName.toDisplayName(),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .aspectRatio(5f)
    )
}
