package com.tiritibambix.sharesonic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Returns a [ColorScheme] whose `primary` and every role Material 3 derives
 * from it have been repointed at [a]. One user-picked hue drives the button
 * fill, tonal-elevation tint and inverse primary; on-primary text flips
 * between white and near-black based on the accent's luminance so button
 * labels stay legible whatever colour the user picks.
 *
 * Only the primary family is overridden — `secondary` / `tertiary` still come
 * from the underlying theme so the theme's identity is preserved. Set `accent`
 * back to `null` in [SharesonicTheme] to revert to the theme's built-in primary.
 */
fun ColorScheme.withAccent(a: Color): ColorScheme {
    val onA = onAccent(a)
    return copy(
        primary = a,
        onPrimary = onA,
        // Dim tint of the accent over the theme's surface variant — matches
        // the 16 % alpha "primary-dim" convention Velvet uses.
        primaryContainer = a.copy(alpha = 0.16f).compositeOver(surfaceVariant),
        // Legible ink on the container — flip based on the accent's luminance
        // so a light custom accent doesn't leave white-on-white text.
        onPrimaryContainer = if (a.luminance() > 0.5f) Color.Black else Color.White,
        inversePrimary = a,
        surfaceTint = a,
    )
}

/**
 * Black or white — whichever reads better on top of [c]. Keeps buttons and
 * badges labelled on a user-picked accent legible whatever colour is chosen.
 */
fun onAccent(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White
