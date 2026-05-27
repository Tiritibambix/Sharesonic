package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Purple20,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,
    secondary = PurpleGrey80,
    onSecondary = PurpleGrey20,
    secondaryContainer = PurpleGrey30,
    onSecondaryContainer = PurpleGrey90,
    tertiary = Pink80,
    background = Purple10,
    surface = PurpleGrey10,
    onBackground = Purple90,
    onSurface = PurpleGrey90
)

@Composable
fun SharesonicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
