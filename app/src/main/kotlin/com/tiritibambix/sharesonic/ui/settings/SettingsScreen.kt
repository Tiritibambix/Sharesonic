package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level Settings screen — a classic navigable menu (no tabs):
 * Server / Auto-DJ / Theme / Public Links, each opening its own sub-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToServer: () -> Unit,
    onNavigateToAutoDj: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToPublicLinks: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Dns,
                    title = "Server",
                    subtitle = "Velvet URL, account, connection test",
                    onClick = onNavigateToServer
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Headphones,
                    title = "Auto-DJ",
                    subtitle = "BPM continuity, harmonic mixing, similar artists…",
                    onClick = onNavigateToAutoDj
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Equalizer",
                    subtitle = "Per-band gains for playback",
                    onClick = onNavigateToEqualizer
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = "Velvet (default), Dark or Light",
                    onClick = onNavigateToTheme
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Link,
                    title = "Public Links",
                    subtitle = "View, copy, open or revoke shared links",
                    onClick = onNavigateToPublicLinks
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
    )
}
