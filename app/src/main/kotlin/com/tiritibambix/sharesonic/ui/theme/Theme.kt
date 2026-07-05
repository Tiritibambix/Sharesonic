package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.tiritibambix.sharesonic.data.settings.AppTheme

/**
 * Velvet dark scheme — the app's default theme.
 *
 * Color mapping (CSS var → M3 role):
 *   --bg            → background              #1a1a2e  deepest layer
 *   --surface       → surface                 #16213e  cards, top bar
 *   --raised        → surfaceVariant          #0f3460  elevated surfaces
 *   --card          → (used as surfaceContainer via Scaffold defaults)
 *   --primary       → primary                 #8b5cf6  purple CTA
 *   --primary-h     → inversePrimary          #7c3aed  inverse / hover
 *   --accent        → secondary               #60a5fa  blue accent
 *   --green         → tertiary                #34d399  queue / positive
 *   --red           → error                   #f87171
 *   --t1            → onBackground/onSurface  #eeeeff  primary text
 *   --t2            → onSurfaceVariant        #8888b0  secondary text
 *   --border        → outline                 #2a3a5e
 *   --border2       → outlineVariant          #3a4e72
 */
private val VelvetColorScheme = darkColorScheme(
    // ── Primary — purple ─────────────────────────────────────────────────────
    primary             = VelvetPrimary,        // #8b5cf6
    onPrimary           = VelvetInk,            // near-black on purple button
    primaryContainer    = VelvetPrimaryDark,    // #3b1a78  dark purple chip/card bg
    onPrimaryContainer  = VelvetPrimaryOnC,     // #ede9fe  light lavender text on container

    // ── Secondary — blue accent ───────────────────────────────────────────────
    secondary             = VelvetAccent,       // #60a5fa
    onSecondary           = VelvetInk,
    secondaryContainer    = VelvetAccentDark,   // #1e3a5f
    onSecondaryContainer  = VelvetAccentOnC,    // #bfdbfe

    // ── Tertiary — teal/green (swipe-to-queue) ────────────────────────────────
    tertiary             = VelvetGreen,         // #34d399
    onTertiary           = VelvetInk,
    tertiaryContainer    = VelvetGreenDark,     // #064e3b
    onTertiaryContainer  = VelvetGreenOnC,      // #a7f3d0

    // ── Error — red ───────────────────────────────────────────────────────────
    error             = VelvetRed,              // #f87171
    onError           = VelvetInk,
    errorContainer    = VelvetRedDark,          // #7f1d1d
    onErrorContainer  = VelvetRed,

    // ── Backgrounds & surfaces ────────────────────────────────────────────────
    background   = VelvetBg,                    // #1a1a2e  root background
    onBackground = VelvetT1,                    // #eeeeff
    surface      = VelvetSurface,               // #16213e  cards, top bar
    onSurface    = VelvetT1,
    surfaceVariant   = VelvetRaised,            // #0f3460  elevated / chip bg
    onSurfaceVariant = VelvetT2,                // #8888b0  secondary text

    // ── Outlines ──────────────────────────────────────────────────────────────
    outline        = VelvetBorder,              // #2a3a5e
    outlineVariant = VelvetBorder2,             // #3a4e72

    // ── Inverse ───────────────────────────────────────────────────────────────
    inverseSurface    = VelvetT1,
    inverseOnSurface  = VelvetBg,
    inversePrimary    = VelvetPrimaryHov,       // #7c3aed
)

/**
 * "Dark" scheme — true near-black, Material/Apple dark guidelines.
 * Mapped from the user-supplied :root.dark CSS variables (see [DarkPrimary] etc.).
 */
private val DarkAppColorScheme = darkColorScheme(
    primary             = DarkPrimary,
    onPrimary           = DarkInk,
    primaryContainer    = DarkPrimaryDark,
    onPrimaryContainer  = DarkPrimaryOnC,

    secondary             = DarkAccent,
    onSecondary           = DarkInk,
    secondaryContainer    = DarkAccentDark,
    onSecondaryContainer  = DarkAccentOnC,

    tertiary             = DarkGreen,
    onTertiary           = DarkInk,
    tertiaryContainer    = DarkGreenDark,
    onTertiaryContainer  = DarkGreenOnC,

    error             = DarkRed,
    onError           = DarkInk,
    errorContainer    = DarkRedDark,
    onErrorContainer  = DarkRed,

    background   = DarkBg,
    onBackground = DarkT1,
    surface      = DarkSurface,
    onSurface    = DarkT1,
    surfaceVariant   = DarkRaised,
    onSurfaceVariant = DarkT2,

    outline        = DarkBorder,
    outlineVariant = DarkBorder2,

    inverseSurface    = DarkT1,
    inverseOnSurface  = DarkBg,
    inversePrimary    = DarkPrimaryHov,
)

/**
 * "Light" scheme — soft lavender-gray.
 * Mapped from the user-supplied :root.light CSS variables (see [LightPrimary] etc.).
 */
private val LightAppColorScheme = lightColorScheme(
    primary             = LightPrimary,
    onPrimary           = LightInk,
    primaryContainer    = LightPrimaryDark,
    onPrimaryContainer  = LightPrimaryOnC,

    secondary             = LightAccent,
    onSecondary           = LightInk,
    secondaryContainer    = LightAccentDark,
    onSecondaryContainer  = LightAccentOnC,

    tertiary             = LightGreen,
    onTertiary           = LightInk,
    tertiaryContainer    = LightGreenDark,
    onTertiaryContainer  = LightGreenOnC,

    error             = LightRed,
    onError           = LightInk,
    errorContainer    = LightRedDark,
    onErrorContainer  = LightRed,

    background   = LightBg,
    onBackground = LightT1,
    surface      = LightSurface,
    onSurface    = LightT1,
    surfaceVariant   = LightRaised,
    onSurfaceVariant = LightT2,

    outline        = LightBorder,
    outlineVariant = LightBorder2,

    inverseSurface    = LightT1,
    inverseOnSurface  = LightBg,
    inversePrimary    = LightPrimaryHov,
)

/**
 * High-contrast scheme (AAA) — pure black on white with a yellow accent.
 * Mapped from the user-supplied :root.hc CSS variables.
 */
private val HighContrastColorScheme = darkColorScheme(
    primary             = HcPrimary,
    onPrimary           = HcInk,
    primaryContainer    = HcPrimaryDark,
    onPrimaryContainer  = HcPrimaryOnC,

    secondary             = HcAccent,
    onSecondary           = HcInk,
    secondaryContainer    = HcAccentDark,
    onSecondaryContainer  = HcAccentOnC,

    tertiary             = HcGreen,
    onTertiary           = HcInk,
    tertiaryContainer    = HcGreenDark,
    onTertiaryContainer  = HcGreenOnC,

    error             = HcRed,
    onError           = HcInk,
    errorContainer    = HcRedDark,
    onErrorContainer  = HcRed,

    background   = HcBg,
    onBackground = HcT1,
    surface      = HcSurface,
    onSurface    = HcT1,
    surfaceVariant   = HcRaised,
    onSurfaceVariant = HcT2,

    outline        = HcBorder,
    outlineVariant = HcBorder2,

    inverseSurface    = HcT1,
    inverseOnSurface  = HcBg,
    inversePrimary    = HcPrimaryHov,
)

/**
 * Colourblind-safe scheme — blue primary + orange accent, no red/green
 * reliance (Velvet :root.cb). Deuteranopia-friendly.
 */
private val ColorblindColorScheme = darkColorScheme(
    primary             = CbPrimary,
    onPrimary           = CbInk,
    primaryContainer    = CbPrimaryDark,
    onPrimaryContainer  = CbPrimaryOnC,

    secondary             = CbAccent,
    onSecondary           = CbInk,
    secondaryContainer    = CbAccentDark,
    onSecondaryContainer  = CbAccentOnC,

    tertiary             = CbGreen,
    onTertiary           = CbInk,
    tertiaryContainer    = CbGreenDark,
    onTertiaryContainer  = CbGreenOnC,

    error             = CbRed,
    onError           = CbInk,
    errorContainer    = CbRedDark,
    onErrorContainer  = CbRed,

    background   = CbBg,
    onBackground = CbT1,
    surface      = CbSurface,
    onSurface    = CbT1,
    surfaceVariant   = CbRaised,
    onSurfaceVariant = CbT2,

    outline        = CbBorder,
    outlineVariant = CbBorder2,

    inverseSurface    = CbT1,
    inverseOnSurface  = CbBg,
    inversePrimary    = CbPrimaryHov,
)

@Composable
fun SharesonicTheme(
    appTheme: AppTheme = AppTheme.VELVET,
    accent: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = when (appTheme) {
        AppTheme.VELVET          -> VelvetColorScheme
        AppTheme.DARK            -> DarkAppColorScheme
        AppTheme.LIGHT           -> LightAppColorScheme
        AppTheme.HIGH_CONTRAST   -> HighContrastColorScheme
        AppTheme.COLORBLIND_SAFE -> ColorblindColorScheme
    }
    // User-picked accent (theme's primary override). See AccentOverride.kt for
    // the derivation of onPrimary / surfaceTint / inversePrimary from `accent`.
    val colorScheme = if (accent != null) baseScheme.withAccent(accent) else baseScheme
    val inks = when (appTheme) {
        AppTheme.VELVET          -> VelvetInks
        AppTheme.DARK            -> DarkInks
        AppTheme.LIGHT           -> LightInks
        AppTheme.HIGH_CONTRAST   -> HighContrastInks
        AppTheme.COLORBLIND_SAFE -> ColorblindInks
    }
    CompositionLocalProvider(LocalSharesonicInks provides inks) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
