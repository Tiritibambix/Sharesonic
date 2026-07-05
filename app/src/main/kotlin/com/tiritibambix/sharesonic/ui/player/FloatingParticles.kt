package com.tiritibambix.sharesonic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soft "firefly" particles drifting behind the Now Playing content.
 *
 *   * Deterministic per-track: seeded from [seedKey] so a given track always
 *     has the same particle field — no jitter when the composable rebuilds.
 *   * Each particle drifts along its own low-frequency Lissajous path
 *     (a sin() on x and y with slightly different periods) so the motion
 *     never looks metronomic. When it leaves the box it wraps back in on the
 *     opposite side, so density stays constant without visible respawns.
 *   * A gentle sinusoidal alpha oscillator makes each dot twinkle at its own
 *     phase — the ethereal "breathing" feel the user asked for.
 *   * Every dot is drawn as a radial gradient centred on itself, going from
 *     `color` at full alpha to transparent at [Particle.radius], so the halo
 *     bleeds around each particle instead of a hard-edged disc.
 *   * When [color] is null (grayscale album art, or before the seed loads),
 *     the layer renders nothing — no cold white dots on a dark background.
 *
 * The whole layer is wrapped in `Modifier.graphicsLayer(compositingStrategy =
 * Offscreen)` so the many overlapping alpha-blended radial gradients composite
 * in one pass instead of stacking on the ambient halo behind — cheaper for
 * fill-rate and stops the particles washing the halo out where they overlap.
 */
@Composable
fun FloatingParticles(
    color: Color,
    seedKey: String,
    modifier: Modifier = Modifier,
    count: Int = 22,
) {

    val particles = remember(seedKey, count) {
        val rng = Random(seedKey.hashCode())
        List(count) { Particle.random(rng) }
    }
    var elapsedSec by remember { mutableStateOf(0f) }
    LaunchedEffect(seedKey) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) elapsedSec += (t - last) / 1_000_000_000f
                last = t
            }
        }
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val cx = ((p.originX + sin(p.omegaX * elapsedSec + p.phaseX) * p.amplitudeX + 1f) % 1f) * w
            val cy = ((p.originY + sin(p.omegaY * elapsedSec + p.phaseY) * p.amplitudeY + 1f) % 1f) * h
            val twinkle = 0.55f + 0.45f * sin(p.twinkleOmega * elapsedSec + p.twinklePhase)
            val alpha = (p.baseAlpha * twinkle).coerceIn(0f, 1f)
            val rPx = p.radius * size.minDimension
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.50f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = rPx,
                ),
                radius = rPx,
                center = Offset(cx, cy),
            )
        }
    }
}

private data class Particle(
    val originX: Float,
    val originY: Float,
    val amplitudeX: Float,
    val amplitudeY: Float,
    val omegaX: Float,
    val omegaY: Float,
    val phaseX: Float,
    val phaseY: Float,
    val radius: Float,          // as a fraction of the box's shortest edge
    val baseAlpha: Float,
    val twinkleOmega: Float,
    val twinklePhase: Float,
) {
    companion object {
        fun random(r: Random): Particle {
            // Radius mix: mostly small dust motes, a handful of larger halo blooms
            // so the field has visual rhythm rather than uniform texture.
            val radius = if (r.nextFloat() < 0.15f)
                0.09f + r.nextFloat() * 0.07f       // large: 9-16 % of min edge
            else
                0.02f + r.nextFloat() * 0.04f       // small: 2-6 %
            val baseAlpha = 0.30f + r.nextFloat() * 0.35f
            // Motion frequencies: very slow (0.05-0.18 rad/s ≈ 35-125 s per cycle).
            val omegaX = 0.05f + r.nextFloat() * 0.13f
            val omegaY = 0.05f + r.nextFloat() * 0.13f
            return Particle(
                originX = r.nextFloat(),
                originY = r.nextFloat(),
                amplitudeX = 0.08f + r.nextFloat() * 0.20f,
                amplitudeY = 0.06f + r.nextFloat() * 0.16f,
                omegaX = omegaX,
                omegaY = omegaY,
                phaseX = r.nextFloat() * (2f * PI.toFloat()),
                phaseY = r.nextFloat() * (2f * PI.toFloat()),
                radius = radius,
                baseAlpha = baseAlpha,
                twinkleOmega = 0.4f + r.nextFloat() * 0.9f,     // twinkle every ~5-15 s
                twinklePhase = r.nextFloat() * (2f * PI.toFloat()),
            )
        }
    }
}
