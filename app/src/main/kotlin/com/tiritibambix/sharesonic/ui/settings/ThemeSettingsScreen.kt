package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.settings.AppTheme
import com.tiritibambix.sharesonic.ui.theme.CbBg
import com.tiritibambix.sharesonic.ui.theme.CbPrimary
import com.tiritibambix.sharesonic.ui.theme.DarkBg
import com.tiritibambix.sharesonic.ui.theme.DarkPrimary
import com.tiritibambix.sharesonic.ui.theme.HcBg
import com.tiritibambix.sharesonic.ui.theme.HcPrimary
import com.tiritibambix.sharesonic.ui.theme.LightBg
import com.tiritibambix.sharesonic.ui.theme.LightPrimary
import com.tiritibambix.sharesonic.ui.theme.VelvetBg
import com.tiritibambix.sharesonic.ui.theme.VelvetPrimary
import com.tiritibambix.sharesonic.ui.theme.borderSoft
import com.tiritibambix.sharesonic.ui.theme.textSecondary

/**
 * Theme + accent picker. Five stacked rows (Velvet's five CSS themes) each
 * previewing the theme's `bg` + `primary`, and a sixth "Accent color" row
 * that opens [AccentColorSheet]. The old 3-way `SingleChoiceSegmentedButtonRow`
 * didn't scale to five entries and had no room for a swatch preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val appTheme by viewModel.appTheme.collectAsState()
    val accentArgb by viewModel.accentColor.collectAsState()
    var showAccentSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
                navigationIcon = {
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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Velvet est le thème par défaut. Les 5 palettes proviennent de Velvet.",
                color = MaterialTheme.colorScheme.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Themes.forEach { spec ->
                ThemeRow(
                    spec = spec,
                    selected = appTheme == spec.theme,
                    onSelect = { viewModel.setAppTheme(spec.theme) },
                )
            }

            AccentRow(
                current = accentArgb?.let { Color(it) }
                    ?: currentThemeDefault(appTheme),
                onTap = { showAccentSheet = true },
            )
        }
    }

    if (showAccentSheet) {
        AccentColorSheet(
            currentArgb = accentArgb,
            themeDefault = currentThemeDefault(appTheme),
            onPick = viewModel::setAccentColor,
            onDismiss = { showAccentSheet = false },
        )
    }
}

private data class ThemeSpec(
    val theme: AppTheme,
    val label: String,
    val description: String,
    val bg: Color,
    val primary: Color,
)

private val Themes = listOf(
    ThemeSpec(AppTheme.VELVET,          "Velvet",             "Navy background, purple accent.",
        bg = VelvetBg, primary = VelvetPrimary),
    ThemeSpec(AppTheme.DARK,            "Dark",               "True near-black, light purple accent.",
        bg = DarkBg,   primary = DarkPrimary),
    ThemeSpec(AppTheme.LIGHT,           "Light",              "Soft lavender-gray, deep purple accent.",
        bg = LightBg,  primary = LightPrimary),
    ThemeSpec(AppTheme.HIGH_CONTRAST,   "High-Contrast (AAA)","Pure black/white with yellow accent.",
        bg = HcBg,     primary = HcPrimary),
    ThemeSpec(AppTheme.COLORBLIND_SAFE, "Colourblind-safe",   "Blue + orange, no red/green reliance.",
        bg = CbBg,     primary = CbPrimary),
)

private fun currentThemeDefault(theme: AppTheme): Color = when (theme) {
    AppTheme.VELVET          -> VelvetPrimary
    AppTheme.DARK            -> DarkPrimary
    AppTheme.LIGHT           -> LightPrimary
    AppTheme.HIGH_CONTRAST   -> HcPrimary
    AppTheme.COLORBLIND_SAFE -> CbPrimary
}

@Composable
private fun ThemeRow(spec: ThemeSpec, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Two overlapping colour dots: `bg` behind, `primary` overlapping.
            Box(modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(spec.bg)
                        .border(1.dp, MaterialTheme.colorScheme.borderSoft, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 4.dp)
                        .clip(CircleShape)
                        .background(spec.primary)
                        .border(1.dp, MaterialTheme.colorScheme.borderSoft, CircleShape),
                )
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)) {
                Text(spec.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                )
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun AccentRow(current: Color, onTap: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Accent color", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Override the theme's primary. Presets + HSV picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(current)
                    .border(1.dp, MaterialTheme.colorScheme.borderSoft, CircleShape),
            )
        }
    }
}
