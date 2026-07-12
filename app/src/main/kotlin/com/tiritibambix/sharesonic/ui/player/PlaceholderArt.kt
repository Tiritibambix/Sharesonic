package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import kotlin.random.Random

/**
 * Fallback thumbnail for entries without artwork. Renders a small static
 * waveform silhouette — same visual language as [WaveformSeekBar] but
 * decorative and centred — over a flat `surfaceVariant` background. Fully
 * monochrome: no colour is derived from the track, only the bar heights are
 * (deterministic from [seedKey]), so a playlist of artwork-less songs shows
 * varied, distinguishable tiles without introducing any palette guesswork.
 *
 * @param seedKey stable per-entry key (song id / entry id). Same key ⇒ same
 *   waveform on every re-composition, so a track never "shape-shifts" between
 *   the mini bar, the browser row, and the full player.
 * @param shape clipping shape — 12.dp for the Now Playing full-size cover,
 *   4.dp for the mini bar and folder rows to match the cover-art layout.
 */
@Composable
fun NoArtworkThumb(
    seedKey: String,
    modifier: Modifier = Modifier,
    shape: Shape,
) {
    // Bars carry the theme accent so the placeholder still feels part of the
    // app's colour palette (echoes the play/pause halo and the seek bar); alpha
    // keeps them from screaming over the frosted backdrop.
    val barTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    // Same generator as WaveformSeekBar (0.20-1.00 range, deterministic per
    // hashCode). Cached in remember so the FloatArray isn't reallocated on
    // recomposition.
    val heights = remember(seedKey) {
        val rng = Random(seedKey.hashCode())
        FloatArray(BAR_COUNT) { 0.20f + rng.nextFloat() * 0.80f }
    }
    Box(
        modifier = modifier
            .clip(shape)
            // Partially transparent tile — on Now Playing, the ambient halo and
            // the drifting fireflies show through the placeholder instead of
            // being masked by an opaque block. On the mini bar and folder rows,
            // it just blends with the surrounding surface (no visible change).
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        // 72 % of the tile leaves a visible margin so the waveform reads as a
        // motif inside the tile rather than filling it edge-to-edge.
        Canvas(modifier = Modifier.fillMaxSize(0.72f)) {
            val gap = 3f
            val n = BAR_COUNT
            val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val cy = size.height / 2f
            for (i in 0 until n) {
                val h = (heights[i] * size.height).coerceAtLeast(barWidth)
                drawRoundRect(
                    color = barTint,
                    topLeft = Offset(i * (barWidth + gap), cy - h / 2f),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
        }
    }
}

// Balanced count — dense enough to read as "waveform" on a 300 dp cover,
// sparse enough to still show individual bars on a 46 dp mini-bar tile.
private const val BAR_COUNT = 20
