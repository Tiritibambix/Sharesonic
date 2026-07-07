package com.tiritibambix.sharesonic.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.ui.theme.textSecondary

/**
 * Twelve-language picker + "System default" row. Selecting a row persists the
 * BCP-47 tag to DataStore and then calls [Activity.recreate], which fires
 * [com.tiritibambix.sharesonic.MainActivity.attachBaseContext] and reloads the
 * resource configuration with the chosen locale. Native labels are hardcoded on
 * purpose — a language name must always appear in its own script, so users can
 * find their language even if the app is currently in a foreign one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val currentTag by viewModel.appLanguage.collectAsState()
    val activity = LocalContext.current as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_menu))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.language_intro),
                color = MaterialTheme.colorScheme.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            // "System default" row — the only row whose label is a resource
            // (it should appear in the currently active locale).
            LanguageRow(
                title = stringResource(R.string.language_system_default),
                description = stringResource(R.string.language_system_default_desc),
                selected = currentTag.isEmpty(),
                onSelect = {
                    viewModel.setAppLanguage("") { activity?.recreate() }
                }
            )

            // Twelve locales — native labels are NOT resources so each one
            // always appears in its own script even from a foreign UI.
            Languages.forEach { spec ->
                LanguageRow(
                    title = spec.nativeLabel,
                    description = null,
                    selected = currentTag == spec.tag,
                    onSelect = {
                        viewModel.setAppLanguage(spec.tag) { activity?.recreate() }
                    }
                )
            }
        }
    }
}

private data class LanguageSpec(val tag: String, val nativeLabel: String)

// Mirrors Velvet's picker (12 languages). `zh` == Simplified Chinese, same as
// Velvet's single 中文 entry.
private val Languages = listOf(
    LanguageSpec("en", "English"),
    LanguageSpec("nl", "Nederlands"),
    LanguageSpec("de", "Deutsch"),
    LanguageSpec("fr", "Français"),
    LanguageSpec("es", "Español"),
    LanguageSpec("it", "Italiano"),
    LanguageSpec("pt", "Português"),
    LanguageSpec("pl", "Polski"),
    LanguageSpec("ru", "Русский"),
    LanguageSpec("zh", "中文"),
    LanguageSpec("ja", "日本語"),
    LanguageSpec("ko", "한국어"),
)

@Composable
private fun LanguageRow(
    title: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textSecondary,
                    )
                }
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}
