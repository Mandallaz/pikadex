package com.tg.pokedex.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tg.pokedex.util.StatColors
import com.tg.pokedex.util.toDisplayName

private const val MAX_STAT = 255f

@Composable
fun StatBar(statName: String, value: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = statName.toDisplayName(),
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(36.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = { (value / MAX_STAT).coerceIn(0f, 1f) },
            color = StatColors.of(statName),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}
