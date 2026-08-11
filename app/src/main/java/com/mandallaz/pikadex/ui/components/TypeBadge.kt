package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.util.TypeColors
import com.mandallaz.pikadex.util.toDisplayName
import com.mandallaz.pikadex.util.typeNameRes

/** B39 — [typeName]'s localized display name, or [toDisplayName]'s formatted raw name for a type
 *  with no dedicated translation (see [typeNameRes]). */
@Composable
fun String.localizedTypeName(): String = typeNameRes(this)?.let { stringResource(it) } ?: toDisplayName()

/**
 * A colored pill with the type's localized name — replaces the official PokeAPI badge sprite
 * (B39): that sprite bakes the English name into the image itself, with no per-locale variant, so
 * it could never be translated. [typeId] is no longer used for anything (kept as a no-op param so
 * every existing call site — there are a dozen — didn't need touching for this fix); prefer
 * dropping it at any call site you're already editing.
 */
@Composable
fun TypeBadge(typeName: String, typeId: Int? = null, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(TypeColors.of(typeName))
            .padding(horizontal = 10.dp)
    ) {
        Text(
            text = typeName.localizedTypeName().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
