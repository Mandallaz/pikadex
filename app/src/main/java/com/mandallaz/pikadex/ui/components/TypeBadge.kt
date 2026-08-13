package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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

// F88 — a local vector icon per type, distinct from B39's old PokeAPI sprite badge (English baked
// into the image, no per-locale variant — that's what B39 removed). These are plain
// androidx.compose.material.icons.extended icons, language-independent, colored via TypeColors —
// no new binary assets, and B39's localization fix stays intact since the name is still resolved
// separately via localizedTypeName().
private val TYPE_ICONS: Map<String, ImageVector> = mapOf(
    "normal" to Icons.Filled.Circle,
    "fire" to Icons.Filled.LocalFireDepartment,
    "water" to Icons.Filled.WaterDrop,
    "electric" to Icons.Filled.Bolt,
    "grass" to Icons.Filled.Grass,
    "ice" to Icons.Filled.AcUnit,
    "fighting" to Icons.Filled.SportsMma,
    "poison" to Icons.Filled.Science,
    "ground" to Icons.Filled.Terrain,
    "flying" to Icons.Filled.Air,
    "psychic" to Icons.Filled.Psychology,
    "bug" to Icons.Filled.BugReport,
    "rock" to Icons.Filled.Landscape,
    "ghost" to Icons.Filled.NightsStay,
    "dragon" to Icons.Filled.Waves,
    "dark" to Icons.Filled.DarkMode,
    "steel" to Icons.Filled.Shield,
    "fairy" to Icons.Filled.AutoAwesome,
    "stellar" to Icons.Filled.Star,
    "unknown" to Icons.Filled.QuestionMark
)

/** F88 — the icon shown in [TypeBadge] for [typeName], or the same "unknown" fallback
 *  [TypeColors.of] uses for a type this map doesn't recognize (PokeAPI types introduced after
 *  this map was written). Case-insensitive, same as [typeNameRes]. */
fun typeIcon(typeName: String): ImageVector = TYPE_ICONS[typeName.lowercase()] ?: Icons.Filled.QuestionMark

/**
 * A colored pill for a type: [typeIcon]'s icon, plus (when [showLabel] is true) the localized
 * name from B39. [showLabel] false renders icon-only — used by the compact dex list (F82) — but
 * still carries the localized name as the icon's contentDescription, so B39's
 * localization/accessibility fix isn't lost in that mode. [typeId] is no longer used for anything
 * (kept as a no-op param so every existing call site — there are a dozen — didn't need touching
 * for B39's fix); prefer dropping it at any call site you're already editing.
 */
@Composable
fun TypeBadge(
    typeName: String,
    typeId: Int? = null,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    showLabel: Boolean = true
) {
    val localizedName = typeName.localizedTypeName()
    // The user asked for the label's font size to track the badge's own height rather than a
    // fixed typography style — same height-8dp sizing already used for the icon, so text and
    // icon read as the same visual scale regardless of what height a call site passes.
    val labelFontSize = with(LocalDensity.current) { (height - 8.dp).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(TypeColors.of(typeName))
            .padding(horizontal = if (showLabel) 10.dp else 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = typeIcon(typeName),
                // Only source of the type name in icon-only mode — must stay in sync with the
                // visible Text below when showLabel is true, and B39's localization requirement
                // otherwise regresses when the label is hidden.
                contentDescription = if (showLabel) null else localizedName,
                tint = Color.White,
                modifier = Modifier.size(height - 8.dp)
            )
            if (showLabel) {
                Text(
                    text = localizedName.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = labelFontSize),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
