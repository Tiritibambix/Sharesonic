package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A waveform-style seek bar: a row of vertical bars whose played portion is
 * tinted [playedColor] and the rest [trackColor]. Tap or drag anywhere to seek.
 *
 * The bar heights are **deterministic per track** — seeded from [seedKey] (the
 * track id) — so a given track always shows the same shape and the waveform
 * doesn't shimmer as the position updates. This is a decorative waveform (no real
 * audio analysis); it reads as "music UI" and clearly shows progress.
 *
 * @param fraction current playback position as 0..1
 * @param seedKey stable per-track key (e.g. the song id) driving the bar shape
 * @param onSeek committed seek fraction (on tap, or when a drag ends)
 * @param onScrub optional live fraction while dragging (null when the drag ends),
 *   so the caller can show the scrubbed time before the seek commits
 */
@Composable
fun WaveformSeekBar(
    fraction: Float,
    seedKey: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playedColor: Color,
    trackColor: Color,
    barCount: Int = 56,
    onScrub: ((Float?) -> Unit)? = null
) {
    // Deterministic bar heights (0.18..1.0 of the available height), stable per track.
    val heights = remember(seedKey, barCount) {
        val rng = Random(seedKey.hashCode())
        FloatArray(barCount) { 0.18f + rng.nextFloat() * 0.82f }
    }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: fraction).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .pointerInput(seedKey) {
                detectTapGestures { pos ->
                    onSeek((pos.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(seedKey) {
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        val f = (pos.x / size.width).coerceIn(0f, 1f)
                        dragFraction = f
                        onScrub?.invoke(f)
                    },
                    onHorizontalDrag = { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = f
                        onScrub?.invoke(f)
                    },
                    onDragEnd = {
                        dragFraction?.let { onSeek(it) }
                        dragFraction = null
                        onScrub?.invoke(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        onScrub?.invoke(null)
                    }
                )
            }
    ) {
        val n = heights.size
        if (n == 0 || size.width <= 0f) return@Canvas
        val gap = 3f
        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val centerY = size.height / 2f
        val playedBars = (shown * n).toInt()
        for (i in 0 until n) {
            val h = (heights[i] * size.height).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = if (i < playedBars) playedColor else trackColor,
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = radius
            )
        }
    }
}
