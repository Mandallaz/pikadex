package com.mandallaz.pikadex.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Standard Material3 `TopAppBar` reserves a fixed 64dp (small) container height regardless of
 *  content, plus generous internal padding around the title — measured on device this left ~80dp
 *  of mostly-empty bar below the status bar (GitHub issue F31). Building the bar directly out of a
 *  fixed-height Row instead gives an explicit, much smaller target height. */
private val TopBarHeight = 48.dp
private val NavigationIconSlotSize = 48.dp

@Composable
fun PikaDexTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                // consumeWindowInsets must follow windowInsetsPadding on the same chain: applied
                // there, it marks the status-bar inset as spent for whatever reads WindowInsets
                // further down (including the caller's own Scaffold, which otherwise doesn't know
                // this bar already accounted for it and pads the content again on top of it). Putting
                // it on a separate ancestor composable instead zeroed the padding out entirely and
                // let the title render under the status bar icons.
                .windowInsetsPadding(WindowInsets.statusBars)
                .consumeWindowInsets(WindowInsets.statusBars)
                .height(TopBarHeight)
                .padding(horizontal = if (navigationIcon == null) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                Box(
                    modifier = Modifier.size(NavigationIconSlotSize),
                    contentAlignment = Alignment.Center
                ) {
                    navigationIcon()
                }
            }
            Box(modifier = Modifier.weight(1f).padding(start = if (navigationIcon != null) 4.dp else 0.dp)) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
