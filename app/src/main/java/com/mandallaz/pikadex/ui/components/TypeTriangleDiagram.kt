package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val w = maxWidth
        val h = maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            val top = Offset(size.width * TOP_ANCHOR.x, size.height * TOP_ANCHOR.y)
            val bl = Offset(size.width * BOTTOM_LEFT_ANCHOR.x, size.height * BOTTOM_LEFT_ANCHOR.y)
            val br = Offset(size.width * BOTTOM_RIGHT_ANCHOR.x, size.height * BOTTOM_RIGHT_ANCHOR.y)

            drawTriangleArrow(top, bl, arrowColor)
            drawTriangleArrow(bl, br, arrowColor)
            drawTriangleArrow(br, top, arrowColor)
        }

        TypeBadge(
            types[0], TypeIds.of(types[0]), height = BADGE_HEIGHT,
            modifier = Modifier.offset(x = clampBadgeX(w * TOP_ANCHOR.x, w), y = h * TOP_ANCHOR.y - BADGE_HEIGHT / 2)
        )
        TypeBadge(
            types[1], TypeIds.of(types[1]), height = BADGE_HEIGHT,
            modifier = Modifier.offset(x = clampBadgeX(w * BOTTOM_LEFT_ANCHOR.x, w), y = h * BOTTOM_LEFT_ANCHOR.y - BADGE_HEIGHT / 2)
        )
        TypeBadge(
            types[2], TypeIds.of(types[2]), height = BADGE_HEIGHT,
            modifier = Modifier.offset(x = clampBadgeX(w * BOTTOM_RIGHT_ANCHOR.x, w), y = h * BOTTOM_RIGHT_ANCHOR.y - BADGE_HEIGHT / 2)
        )
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

/** Draws an arrow from [from] to [to], shrunk at both ends so it doesn't run under the type badges. */
private fun DrawScope.drawTriangleArrow(from: Offset, to: Offset, color: Color) {
    val margin = 0.30f
    val start = Offset(from.x + (to.x - from.x) * margin, from.y + (to.y - from.y) * margin)
    val end = Offset(from.x + (to.x - from.x) * (1 - margin), from.y + (to.y - from.y) * (1 - margin))

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
