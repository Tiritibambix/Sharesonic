package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Four-tier text ink + two-tier border shades, provided by [SharesonicTheme]
 * so callers write intent (`textSecondary`) rather than roles that only
 * approximate it (`onSurfaceVariant`).
 *
 * The Material 3 `ColorScheme` collapses everything into `onSurface` and
 * `onSurfaceVariant`, which reads flat when you need three or four distinct
 * levels of muting (title vs artist vs bitrate vs disabled). These extra
 * inks give the UI the same layered hierarchy the Velvet web player has.
 */
data class SharesonicInks(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDim: Color,
    val borderSoft: Color,
    val borderStrong: Color,
)

val LocalSharesonicInks = staticCompositionLocalOf<SharesonicInks> {
    error("SharesonicInks not provided — wrap the tree in SharesonicTheme.")
}

/** Same-shape shortcut so callsites can read `MaterialTheme.inks.textSecondary`. */
val MaterialTheme.inks: SharesonicInks
    @Composable
    @ReadOnlyComposable
    get() = LocalSharesonicInks.current

/** Convenience getters on [ColorScheme] so a callsite can stay all-`colorScheme.*`. */
val ColorScheme.textPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalSharesonicInks.current.textPrimary
val ColorScheme.textSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalSharesonicInks.current.textSecondary
val ColorScheme.textTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalSharesonicInks.current.textTertiary
val ColorScheme.textDim: Color
    @Composable @ReadOnlyComposable get() = LocalSharesonicInks.current.textDim
val ColorScheme.borderSoft: Color
    @Composable @ReadOnlyComposable get() = LocalSharesonicInks.current.borderSoft
val ColorScheme.borderStrong: Color
    @Composable @ReadOnlyComposable get() = LocalSharesonicInks.current.borderStrong

internal val VelvetInks = SharesonicInks(
    textPrimary   = VelvetT1,
    textSecondary = VelvetT2,
    textTertiary  = VelvetT3,
    textDim       = VelvetT4,
    borderSoft    = VelvetBorder,
    borderStrong  = VelvetBorder2,
)

internal val DarkInks = SharesonicInks(
    textPrimary   = DarkT1,
    textSecondary = DarkT2,
    textTertiary  = DarkT3,
    textDim       = DarkT4,
    borderSoft    = DarkBorder,
    borderStrong  = DarkBorder2,
)

internal val LightInks = SharesonicInks(
    textPrimary   = LightT1,
    textSecondary = LightT2,
    textTertiary  = LightT3,
    textDim       = LightT4,
    borderSoft    = LightBorder,
    borderStrong  = LightBorder2,
)

internal val HighContrastInks = SharesonicInks(
    textPrimary   = HcT1,
    textSecondary = HcT2,
    textTertiary  = HcT3,
    textDim       = HcT4,
    borderSoft    = HcBorder,
    borderStrong  = HcBorder2,
)

internal val ColorblindInks = SharesonicInks(
    textPrimary   = CbT1,
    textSecondary = CbT2,
    textTertiary  = CbT3,
    textDim       = CbT4,
    borderSoft    = CbBorder,
    borderStrong  = CbBorder2,
)
