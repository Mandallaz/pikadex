package com.tg.pokedex.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tg.pokedex.util.Sprites
import com.tg.pokedex.util.toDisplayName

/** Official type badge image (icon + name baked in), used instead of a plain text pill. */
@Composable
fun TypeBadge(typeName: String, typeId: Int, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    AsyncImage(
        model = Sprites.typeIconUrl(typeId),
        contentDescription = typeName.toDisplayName(),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .aspectRatio(5f)
    )
}
