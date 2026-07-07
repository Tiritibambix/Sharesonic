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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.res.stringResource
import com.tiritibambix.sharesonic.R

/**
 * Top-level Settings screen — a classic navigable menu (no tabs):
 * Server / Auto-DJ / Equalizer / Theme / Language / Public Links, each opening
 * its own sub-screen. Only reached on first-run (before a server is configured);
 * afterwards all rows also live in the drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToServer: () -> Unit,
    onNavigateToAutoDj: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToPublicLinks: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_server),
                    subtitle = stringResource(R.string.settings_server_subtitle),
                    onClick = onNavigateToServer
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Headphones,
                    title = stringResource(R.string.settings_autodj),
                    subtitle = stringResource(R.string.settings_autodj_subtitle),
                    onClick = onNavigateToAutoDj
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.settings_equalizer),
                    subtitle = stringResource(R.string.settings_equalizer_subtitle),
                    onClick = onNavigateToEqualizer
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(R.string.settings_theme_subtitle),
                    onClick = onNavigateToTheme
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_subtitle),
                    onClick = onNavigateToLanguage
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsMenuRow(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.settings_public_links),
                    subtitle = stringResource(R.string.settings_public_links_subtitle),
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
