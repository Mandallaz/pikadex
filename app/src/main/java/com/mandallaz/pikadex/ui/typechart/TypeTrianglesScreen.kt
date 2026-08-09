package com.mandallaz.pikadex.ui.typechart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mandallaz.pikadex.ui.components.PikaDexTopBar
import com.mandallaz.pikadex.ui.components.TypeBadge
import com.mandallaz.pikadex.ui.components.TypeTriangleDiagram
import com.mandallaz.pikadex.util.TypeIds
import com.mandallaz.pikadex.util.TypeTriangle
import com.mandallaz.pikadex.util.TypeTriangles

/** [onBack] is non-null only when this screen was pushed on top of a Pokémon's page (rather than
 *  selected as a bottom-nav tab), where the bottom bar still shows "Triangles" as the current tab
 *  and there was otherwise no visible way back to the Pokémon short of the system Back gesture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeTrianglesScreen(onBack: (() -> Unit)? = null) {
    Scaffold(
        topBar = {
            PikaDexTopBar(
                title = { Text("Type Triangles") },
                navigationIcon = onBack?.let { back ->
                    {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val (perfect, imperfect) = remember { TypeTriangles.ALL.partition { it.isPerfect } }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "Each type here is super effective against the next one in the cycle — " +
                        "a rock-paper-scissors relationship that repeats throughout the type chart.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                SectionHeader(
                    title = "Perfect triangles",
                    subtitle = "Each type also resists the one before it in the loop — the " +
                        "offense and defense are fully symmetric."
                )
            }
            items(perfect) { triangle -> TriangleCard(triangle) }
            item {
                SectionHeader(
                    title = "Imperfect triangles",
                    subtitle = "The offensive loop still holds, but a defensive link is only a " +
                        "neutral hit — or a full immunity — instead of a resistance."
                )
            }
            items(imperfect) { triangle -> TriangleCard(triangle) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TriangleCard(triangle: TypeTriangle) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                triangle.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TypeTriangleDiagram(triangle.types, modifier = Modifier.padding(top = 8.dp))
            Text(
                triangle.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Text(
                "Best counter",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                triangle.counter.types.forEach { type ->
                    TypeBadge(type, TypeIds.idOrNull(type))
                }
            }
            Text(
                triangle.counter.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
