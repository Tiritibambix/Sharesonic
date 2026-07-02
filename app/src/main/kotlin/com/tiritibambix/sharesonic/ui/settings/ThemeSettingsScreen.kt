package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.settings.AppTheme

/** Velvet / Dark / Light visual-theme picker, opened from the Settings menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val appTheme by viewModel.appTheme.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
                navigationIcon = {
                    // Keep the hamburger glyph here too — reached via the drawer,
                    // tapping it again exits this screen back to the browser.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Velvet est le thème par défaut de l'application.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    AppTheme.VELVET to "Velvet",
                    AppTheme.DARK to "Dark",
                    AppTheme.LIGHT to "Light"
                )
                options.forEachIndexed { index, (theme, label) ->
                    SegmentedButton(
                        selected = appTheme == theme,
                        onClick = { viewModel.setAppTheme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}
