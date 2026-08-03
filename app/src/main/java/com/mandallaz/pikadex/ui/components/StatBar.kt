package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.util.toDisplayName

// 255 is the theoretical maximum (a handful of extreme outliers like Blissey's HP or Shuckle's
// Defense) — scaling every bar against it squashed the ~90% of stats that fall in the ordinary
// 20-150 range into the left half of the track, so nothing looked meaningfully high or low.
// STAT_BAR_SCALE_MAX is a "typical ceiling" instead: most real stats land well inside it, so the
// bar actually uses its full width, and the rare stat above it just caps out at full — which is
// still an accurate "very high" signal, not a misleading one.
const val STAT_BAR_SCALE_MAX = 180f

@Composable
fun StatBar(statName: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Fixed dp widths here used to clip a bold 3-digit value ("255") and unevenly wrap a long
        // label ("Special Defense") at larger font scales. weight()/widthIn(min=) instead of a hard
        // width() means the label can wrap within its own share of the row and the value column can
        // grow past its minimum rather than ever truncating.
        Text(
            text = statName.toDisplayName(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value.toString(),
            modifier = Modifier.widthIn(min = 36.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = { (value / STAT_BAR_SCALE_MAX).coerceIn(0f, 1f) },
            color = color,
            // Material3's default end-of-track "stop indicator" dot is meant for indeterminate
            // download-style progress (marking where 100% is); on a data bar like this it reads as
            // some kind of target/threshold marker for the stat itself, which it isn't — drawing
            // nothing here removes that false signal.
            drawStopIndicator = {},
            modifier = Modifier
                .weight(1.5f)
                .padding(start = 8.dp)
        )
    }
}
