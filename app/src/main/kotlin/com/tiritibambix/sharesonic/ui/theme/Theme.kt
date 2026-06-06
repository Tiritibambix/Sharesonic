package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * mStream Velvet dark scheme.
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

@Composable
fun SharesonicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VelvetColorScheme,
        typography  = Typography,
        content     = content
    )
}
