package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.PI

/**
 * Deterministic per-key ambient seed. Hashes [key] to a hue on the OKLCH
 * circle, at fixed `l = 0.55`, `c = 0.12` — safely above [AmbientEngine]'s
 * `CHROMA_FLOOR` (0.030, would return null) and well below `CHROMA_CAP`
 * (0.220, would look neon).
 *
 * Used as a fallback when a track has no embedded artwork — feeds the same
 * [ambientBrush] the real album-art halo uses so the visual language matches.
 */
fun syntheticSeed(key: String): Color {
    // Kotlin's String.hashCode is stable across JVM versions — replaying the
    // same track always yields the same tint. Modulo into a full radian
    // circle, then run through the OKLCH → sRGB conversion the ambient engine
    // uses so grayscale-reject / contrast-floor rules still apply upstream.
    val hue = ((key.hashCode().toDouble().rem(360.0) + 360.0) % 360.0) * (PI / 180.0)
    val rgb = oklchToRgb(l = 0.55, c = 0.12, h = hue)
    return Color(rgb[0], rgb[1], rgb[2])
}

/**
 * Fallback thumbnail for entries without artwork: a soft ambient-tinted
 * radial gradient (top-left hot spot) with a small, semi-transparent
 * MusicNote glyph centred over it. Uses the same [ambientBrush] as the real
 * album-art halo so a no-art song still feels like it belongs to the app's
 * visual language.
 *
 * @param seedKey stable per-entry key (song id / entry id) — the tint is
 *   deterministic and different per key, so a playlist of artwork-less songs
 *   looks like a set of varied cards instead of identical blank tiles.
 * @param shape clipping shape — 12.dp for the Now Playing full-size cover,
 *   4.dp for mini bar / list rows to match the surrounding cover art layout.
 * @param iconFraction size of the glyph as a fraction of the container's
 *   shortest side (default 0.42 → ~ half the box, half the old fallback's
 *   footprint so it reads as a watermark rather than a placeholder).
 * @param iconAlpha visibility of the glyph — kept low so the ambient tint
 *   is the primary signal and the icon doesn't compete with it.
 */
@Composable
fun NoArtworkThumb(
    seedKey: String,
    modifier: Modifier = Modifier,
    shape: Shape,
    iconFraction: Float = 0.42f,
    iconAlpha: Float = 0.35f,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val seed = remember(seedKey) { syntheticSeed(seedKey) }
    // Build the OKLCH radial at draw time so the hot-spot radius scales with
    // the actual container pixel size — a fixed radius clips awkwardly on the
    // small mini-bar tile and doesn't span the full Now Playing cover. Falls
    // back to solid `base` when ambientBrush returns null (shouldn't happen
    // for our seed: chroma is 0.12, well above CHROMA_FLOOR = 0.030).
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val brush = ambientBrush(
                    seed = seed,
                    base = base,
                    vibrant = false,
                    center = Offset.Zero,
                    radius = size.maxDimension * 1.2f,
                ) ?: SolidColor(base)
                drawRect(brush = brush)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha),
            modifier = Modifier.fillMaxSize(iconFraction),
        )
    }
}
