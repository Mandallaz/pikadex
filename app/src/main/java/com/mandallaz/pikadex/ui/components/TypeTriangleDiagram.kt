package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.util.TypeIds
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val BADGE_HEIGHT = 26.dp
private val BADGE_WIDTH = BADGE_HEIGHT * 5

/** The height the anchors below were tuned against, and the tallest the diagram ever draws. */
private val MAX_DIAGRAM_HEIGHT = 200.dp

/**
 * Width-to-height ratio of the diagram, matching what [MAX_DIAGRAM_HEIGHT] used to produce inside a
 * card on a portrait phone. Holding it fixed is what keeps the triangle a triangle: the diagram used
 * to be `fillMaxWidth().height(200.dp)`, so on a landscape screen it stretched to ~880dp wide while
 * staying 200dp tall — a flat, splayed-out shape rather than the compact one the anchors describe.
 */
private const val DIAGRAM_ASPECT = 1.75f

/**
 * Ceiling on the diagram's height as a fraction of the screen's shorter dimension.
 *
 * At a fixed 200dp the diagram no longer fit a landscape viewport (~250dp of content height once
 * the app bar and navigation bar are taken out), so the apex and the base could never be on screen
 * at the same time — you scrolled to one and lost the other. This leaves room for the card's title
 * and prose around it. Portrait is far taller than the cap, so it is unaffected.
 */
private const val MAX_SCREEN_HEIGHT_FRACTION = 0.35f

/**
 * Clearance left between the two bottom badges, so the arrow between them has room to read as an
 * arrow — shaft plus a 14dp head — rather than the stub a tighter gap leaves.
 *
 * Declared before [MIN_DIAGRAM_WIDTH], which derives from it: top-level properties initialise in
 * declaration order, so the other way round it would silently read as 0.dp.
 */
private val BOTTOM_ARROW_MIN_GAP = 56.dp

/**
 * Narrowest the diagram may get before its two bottom badges collide.
 *
 * [clampBadgeX] pulls a badge back inside the container rather than letting it clip, so once the
 * container is narrower than two badges side by side the bottom-left and bottom-right badges get
 * clamped into the same space and overlap ("GRAS|WATER"). Anything at or above this width leaves
 * them a real gap. This also covers very narrow phones, where the old full-width diagram could
 * already collide.
 */
private val MIN_DIAGRAM_WIDTH = BADGE_WIDTH * 2 + BOTTOM_ARROW_MIN_GAP

private val TOP_ANCHOR = Offset(0.5f, 0.14f)
private val BOTTOM_LEFT_ANCHOR = Offset(0.16f, 0.86f)
private val BOTTOM_RIGHT_ANCHOR = Offset(0.84f, 0.86f)

/**
 * Draws 3 types arranged in a triangle with arrows showing the cyclic "beats" relationship:
 * types[0] beats types[1], types[1] beats types[2], types[2] beats types[0].
 */
@Composable
fun TypeTriangleDiagram(types: List<String>, modifier: Modifier = Modifier) {
    require(types.size == 3) { "A type triangle needs exactly 3 types" }
    val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant

    // The screen's shorter dimension, which is the one that runs out in landscape.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val h = MAX_DIAGRAM_HEIGHT.coerceAtMost(screenHeight * MAX_SCREEN_HEIGHT_FRACTION)
        // Keep the shape, but never squeeze below the width the badges need — and never demand more
        // width than the container actually has.
        val w = maxWidth
            .coerceAtMost(h * DIAGRAM_ASPECT)
            .coerceAtLeast(MIN_DIAGRAM_WIDTH.coerceAtMost(maxWidth))

        // Where the bottom badges actually end up, which is not where their anchors point: on any
        // phone-width diagram clampBadgeX pushes both of them outwards to stay in bounds. The
        // bottom arrow is drawn between these real edges — derived from the anchors it ran *under*
        // the badges and only a stub of it showed.
        val bottomLeftBadgeEnd = clampBadgeX(w * BOTTOM_LEFT_ANCHOR.x, w) + BADGE_WIDTH
        val bottomRightBadgeStart = clampBadgeX(w * BOTTOM_RIGHT_ANCHOR.x, w)

        Box(modifier = Modifier.width(w).height(h).align(Alignment.Center)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val top = Offset(size.width * TOP_ANCHOR.x, size.height * TOP_ANCHOR.y)
                val bl = Offset(size.width * BOTTOM_LEFT_ANCHOR.x, size.height * BOTTOM_LEFT_ANCHOR.y)
                val br = Offset(size.width * BOTTOM_RIGHT_ANCHOR.x, size.height * BOTTOM_RIGHT_ANCHOR.y)

                drawTriangleArrow(top, bl, arrowColor)
                drawArrow(
                    Offset(bottomLeftBadgeEnd.toPx(), bl.y),
                    Offset(bottomRightBadgeStart.toPx(), br.y),
                    arrowColor
                )
                drawTriangleArrow(br, top, arrowColor)
            }

            TypeBadge(
                types[0], TypeIds.idOrNull(types[0]), height = BADGE_HEIGHT,
                modifier = Modifier
                    .width(BADGE_WIDTH)
                    .offset(x = clampBadgeX(w * TOP_ANCHOR.x, w), y = h * TOP_ANCHOR.y - BADGE_HEIGHT / 2)
            )
            TypeBadge(
                types[1], TypeIds.idOrNull(types[1]), height = BADGE_HEIGHT,
                modifier = Modifier
                    .width(BADGE_WIDTH)
                    .offset(x = clampBadgeX(w * BOTTOM_LEFT_ANCHOR.x, w), y = h * BOTTOM_LEFT_ANCHOR.y - BADGE_HEIGHT / 2)
            )
            TypeBadge(
                types[2], TypeIds.idOrNull(types[2]), height = BADGE_HEIGHT,
                modifier = Modifier
                    .width(BADGE_WIDTH)
                    .offset(x = clampBadgeX(w * BOTTOM_RIGHT_ANCHOR.x, w), y = h * BOTTOM_RIGHT_ANCHOR.y - BADGE_HEIGHT / 2)
            )
        }
    }
}

/** A badge horizontally centered on [anchorX] would overflow the container on ordinary phone
 *  widths for the bottom-left/right anchors (0.16/0.84 of a ~300-340dp content width puts their
 *  centered badge only ~50-65dp from the edge, well under BADGE_WIDTH/2) — clipped by whatever
 *  clips this composable's bounds (e.g. the surrounding Card). Clamping keeps every badge fully
 *  on-screen while leaving the un-clipped (wide-container) case exactly centered as before. */
private fun clampBadgeX(anchorX: Dp, containerWidth: Dp): Dp {
    val maxX = (containerWidth - BADGE_WIDTH).coerceAtLeast(0.dp)
    return (anchorX - BADGE_WIDTH / 2).coerceIn(0.dp, maxX)
}

/** Draws an arrow between two triangle corners, shrunk at both ends by a fixed fraction so it
 *  doesn't run under the type badges. Right for the two diagonals, whose badges sit near their
 *  anchors; the bottom edge uses [drawArrow] against the badges' clamped edges instead. */
private fun DrawScope.drawTriangleArrow(from: Offset, to: Offset, color: Color) {
    val margin = 0.30f
    val start = Offset(from.x + (to.x - from.x) * margin, from.y + (to.y - from.y) * margin)
    val end = Offset(from.x + (to.x - from.x) * (1 - margin), from.y + (to.y - from.y) * (1 - margin))
    drawArrow(start, end, color)
}

/** Draws an arrow from exactly [start] to exactly [end], head included. */
private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color) {
    val strokeWidthPx = 3.dp.toPx()
    drawLine(color, start, end, strokeWidth = strokeWidthPx)

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowLength = 14.dp.toPx()
    val arrowAngle = Math.toRadians(28.0)
    val left = Offset(
        end.x - (arrowLength * cos(angle - arrowAngle)).toFloat(),
        end.y - (arrowLength * sin(angle - arrowAngle)).toFloat()
    )
    val right = Offset(
        end.x - (arrowLength * cos(angle + arrowAngle)).toFloat(),
        end.y - (arrowLength * sin(angle + arrowAngle)).toFloat()
    )
    drawLine(color, end, left, strokeWidth = strokeWidthPx)
    drawLine(color, end, right, strokeWidth = strokeWidthPx)
}
