package com.mandallaz.pikadex.ui.detail.sections

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Placeholder that echoes [com.mandallaz.pikadex.ui.detail.DetailContent]'s actual layout (artwork
 *  circle, name/genus bars, a handful of stat rows) instead of a bare spinner — sets the right
 *  expectation for what's about to load in, and reads as faster even at an identical real load
 *  time. A gentle alpha pulse (not a full shimmer sweep) is enough to read as "loading" rather than
 *  "static/broken". */
@Composable
internal fun DetailLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "detail-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "detail-skeleton-alpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.12f)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(160.dp).background(placeholderColor, CircleShape))
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.width(80.dp).height(16.dp).background(placeholderColor, RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.width(160.dp).height(24.dp).background(placeholderColor, RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(24.dp))
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(20.dp)
                    .background(placeholderColor, RoundedCornerShape(8.dp))
            )
        }
    }
}
