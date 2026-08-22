package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Storm
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.data.LanguageSettings
import com.mandallaz.pikadex.util.TypeColors
import com.mandallaz.pikadex.util.toDisplayName
import com.mandallaz.pikadex.util.typeNameRes
import com.mandallaz.pikadex.util.typeShortNameEn
import com.mandallaz.pikadex.util.resolvedTypeNames

/** B39 — [typeName]'s localized display name, or [toDisplayName]'s formatted raw name for a type
 *  with no dedicated translation (see [typeNameRes]). */
@Composable
fun String.localizedTypeName(): String = typeNameRes(this)?.let { stringResource(it) } ?: toDisplayName()

/** B45 — resolves and localizes a list of raw PokeAPI type names. */
@Composable
fun List<String>.localizedTypeNames(): List<String> = resolvedTypeNames().map {
    when (it) {
        is Int -> stringResource(it)
        else -> it as String
    }
}

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
    "dragon" to Icons.Filled.Storm,
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
 *
 * [strikethrough] — F93 follow-up: strikes the label through (icon untouched), grays out the whole
 * badge (color desaturated toward gray) and fades it (reduced alpha — gray alone wasn't enough to
 * read as "inactive" for pale type colors like Fairy, which land close to their normal color even
 * after the gray blend; the fade reads as "washed out" against the card background regardless of
 * hue), for a base type badge that a currently-previewed Tera type has *replaced* rather than
 * combined with (Terastallizing overwrites the Pokémon's typing entirely — see F90/F93's own
 * docs) — signals "this type isn't in effect right now" without removing the badge outright, since
 * the real typing is still what's underneath when the preview is cleared. No-op when [showLabel]
 * is false, since there's no label to strike.
 *
 * [bordered] — F93 follow-up: outlines the badge, for a Weak-to/Resists entry in
 * [TypeMatchupGroups] that only applies *because* of the active Tera preview (differs from what
 * the Pokémon's real typing alone would show) — same "here's what changed" signal as
 * [strikethrough], for the opposite direction (types the preview added rather than removed).
 *
 * F114 — when a caller constrains this badge's width (e.g. [TeamScreen]'s type-matrix column) and
 * the full localized name wouldn't fit next to the icon, the label falls back to
 * [typeShortNameEn]'s abbreviation ("Fighting" -> "Fight") rather
 * than clipping or ellipsizing — but only when the active language is English, per the issue's
 * scope; every other locale keeps showing its full translated name exactly as before, since no
 * short-form translations exist for them. A caller with unconstrained width (the common case —
 * most badges just wrap their content) never measures as "doesn't fit," so this is a no-op there.
 */
@Composable
fun TypeBadge(
    typeName: String,
    typeId: Int? = null,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    showLabel: Boolean = true,
    strikethrough: Boolean = false,
    bordered: Boolean = false
) {
    val localizedName = typeName.localizedTypeName()
    // The user asked for the label's font size to track the badge's own height rather than a
    // fixed typography style — same height-8dp sizing already used for the icon, so text and
    // icon read as the same visual scale regardless of what height a call site passes.
    val labelFontSize = with(LocalDensity.current) { (height - 8.dp).toSp() }
    val textStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = labelFontSize,
        textDecoration = if (strikethrough) TextDecoration.LineThrough else null
    )
    val language by LanguageSettings.currentLanguage.collectAsState()
    val density = LocalDensity.current
    val iconSize = height - 8.dp
    val iconTextSpacing = 4.dp
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .alpha(if (strikethrough) 0.5f else 1f)
            .clip(RoundedCornerShape(50))
            .background(TypeColors.of(typeName).let { if (strikethrough) lerp(it, Color.Gray, 0.85f) else it })
            .let { if (bordered) it.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(50)) else it }
            .padding(horizontal = if (showLabel) 10.dp else 4.dp)
    ) {
        val availableWidth = maxWidth
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = typeIcon(typeName),
                // Only source of the type name in icon-only mode — must stay in sync with the
                // visible Text below when showLabel is true, and B39's localization requirement
                // otherwise regresses when the label is hidden.
                contentDescription = if (showLabel) null else localizedName,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
            if (showLabel) {
                val fullText = localizedName.uppercase()
                val displayText = if (availableWidth != Dp.Infinity && language == "en") {
                    typeShortNameEn(typeName)?.let { shortName ->
                        val availableForText = with(density) {
                            (availableWidth - iconSize - iconTextSpacing).toPx().coerceAtLeast(0f)
                        }
                        val fullTextWidth = textMeasurer.measure(fullText, style = textStyle).size.width
                        if (fullTextWidth > availableForText) shortName.uppercase() else fullText
                    } ?: fullText
                } else {
                    fullText
                }
                Text(
                    text = displayText,
                    style = textStyle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
