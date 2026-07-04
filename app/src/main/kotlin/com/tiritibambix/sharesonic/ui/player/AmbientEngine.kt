package com.tiritibambix.sharesonic.ui.player

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Album-art ambient colour engine, ported from mStream's `ambient_color.dart`
 * (which itself ports `color.jsx` from the Velvet webapp). Reproduces four
 * lessons baked into the original:
 *
 *  1. seed from a vibrancy-gated swatch
 *  2. grayscale fallback (reject low chroma)
 *  3. blend perceptually in OKLab
 *  4. hard contrast floor vs. white text (WCAG)
 *
 * Fully self-contained — no third-party colour library.
 */

// ── Tunables ──────────────────────────────────────────────────────────────
private const val CHROMA_FLOOR = 0.030      // below this → art is "grayscale" → no tint
private const val CHROMA_CAP   = 0.220      // saturation ceiling — vivid without going neon
private const val MIN_CONTRAST = 4.5         // white text on the glow must clear this

// ─────────────────────────────────────────────────────────────
// sRGB ⇄ linear
// ─────────────────────────────────────────────────────────────
private fun sRGBToLinear(c: Int): Double {
    val x = c / 255.0
    return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
}

private fun linearToSRGB(c: Double): Int {
    val x = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
    return (x.coerceIn(0.0, 1.0) * 255.0).toInt()
}

private fun cbrt(x: Double): Double =
    if (x < 0) -((-x).pow(1.0 / 3.0)) else x.pow(1.0 / 3.0)

// ─────────────────────────────────────────────────────────────
// OKLab / OKLCH (Björn Ottosson)
// ─────────────────────────────────────────────────────────────
private data class OkLab(val l: Double, val a: Double, val b: Double)

private fun rgbToOklab(r: Int, g: Int, b: Int): OkLab {
    val lr = sRGBToLinear(r); val lg = sRGBToLinear(g); val lb = sRGBToLinear(b)
    val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
    val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
    val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
    return OkLab(
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

private fun oklabToRgb(bL: Double, a: Double, bb: Double): IntArray {
    val lp = bL + 0.3963377774 * a + 0.2158037573 * bb
    val mp = bL - 0.1055613458 * a - 0.0638541728 * bb
    val sp = bL - 0.0894841775 * a - 1.2914855480 * bb
    val l = lp * lp * lp; val m = mp * mp * mp; val s = sp * sp * sp
    return intArrayOf(
        linearToSRGB(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        linearToSRGB(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        linearToSRGB(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    )
}

private fun oklchToRgb(l: Double, c: Double, h: Double): IntArray =
    oklabToRgb(l, c * cos(h), c * sin(h))

/** Perceptual mix of two RGB triples in OKLab. */
private fun mixOk(c1: IntArray, c2: IntArray, t: Double): IntArray {
    val a = rgbToOklab(c1[0], c1[1], c1[2])
    val b = rgbToOklab(c2[0], c2[1], c2[2])
    return oklabToRgb(
        a.l + (b.l - a.l) * t,
        a.a + (b.a - a.a) * t,
        a.b + (b.b - a.b) * t,
    )
}

// WCAG relative luminance + contrast vs. white.
private fun relLum(r: Int, g: Int, b: Int): Double =
    0.2126 * sRGBToLinear(r) + 0.7152 * sRGBToLinear(g) + 0.0722 * sRGBToLinear(b)

private fun contrastWhite(r: Int, g: Int, b: Int): Double =
    1.05 / (relLum(r, g, b) + 0.05)

/**
 * Bright→mid→base radial gradient, boosted for chroma and darkened until white
 * text hits [MIN_CONTRAST] on the brightest stop. Returns null when the seed's
 * chroma is below [CHROMA_FLOOR] (art is effectively grayscale — no tint).
 *
 * @param vibrant `true` = Spotify-style punch; `false` (default) = calm muted glow.
 */
fun ambientBrush(
    seed: Color,
    base: Color,
    vibrant: Boolean = false,
    center: Offset = Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY,
): Brush? {
    val r = (seed.red * 255f).toInt()
    val g = (seed.green * 255f).toInt()
    val b = (seed.blue * 255f).toInt()
    val lab = rgbToOklab(r, g, b)
    val chroma = sqrt(lab.a * lab.a + lab.b * lab.b)
    val hue = atan2(lab.b, lab.a)

    // (2) grayscale fallback — reject near-neutral seeds.
    if (chroma < CHROMA_FLOOR) return null

    // (3) work in OKLCH: lift muted seeds into a visible glow; keep vibrant rich.
    var l = if (vibrant) max(lab.l, 0.46) else lab.l + 0.16
    // Vividness comes from chroma, not lightness — keeps the tint readable
    // as a colour while staying under the contrast floor below.
    val c = min(chroma * (if (vibrant) 1.35 else 1.15), CHROMA_CAP)

    // (4) hard contrast floor: darken in L until white text clears MIN_CONTRAST.
    var bright = oklchToRgb(l, c, hue)
    var guard = 0
    while (contrastWhite(bright[0], bright[1], bright[2]) < MIN_CONTRAST &&
        l > 0.08 && guard++ < 60) {
        l -= 0.012
        bright = oklchToRgb(l, c, hue)
    }

    val brightColor = Color(bright[0], bright[1], bright[2])
    val baseInts = intArrayOf(
        (base.red * 255f).toInt(),
        (base.green * 255f).toInt(),
        (base.blue * 255f).toInt(),
    )
    val midRgb = mixOk(bright, baseInts, 0.40)
    val midColor = Color(midRgb[0], midRgb[1], midRgb[2])

    // Top glow — the colour haloes the art (its source) and fades to the dark
    // base over the lower half so text further down stays legible.
    return Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to brightColor,
            0.40f to midColor,
            0.82f to base,
        ),
        center = center,
        radius = radius,
    )
}

// ─────────────────────────────────────────────────────────────
// Dominant-color extraction from a bitmap (no third-party pkg)
// ─────────────────────────────────────────────────────────────

/**
 * Most-populous 12-bit colour bucket in a 20×20 grid over the bitmap — the
 * "field" tone (calm ambient seed).
 */
fun dominantOf(bitmap: Bitmap): Color? {
    val w = bitmap.width; val h = bitmap.height
    if (w == 0 || h == 0) return null
    val grid = 20
    val counts = HashMap<Int, Int>()
    val sums = HashMap<Int, IntArray>()
    for (gy in 0 until grid) for (gx in 0 until grid) {
        val x = (gx * w / grid).coerceIn(0, w - 1)
        val y = (gy * h / grid).coerceIn(0, h - 1)
        val p = bitmap.getPixel(x, y)
        val alpha = (p ushr 24) and 0xFF
        if (alpha < 128) continue
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        counts[bucket] = (counts[bucket] ?: 0) + 1
        val s = sums.getOrPut(bucket) { IntArray(4) }
        s[0] += r; s[1] += g; s[2] += b; s[3] += 1
    }
    if (counts.isEmpty()) return null
    var bestKey = counts.keys.first(); var bestCount = -1
    for ((k, c) in counts) if (c > bestCount) { bestCount = c; bestKey = k }
    val s = sums[bestKey]!!
    return Color(s[0] / s[3], s[1] / s[3], s[2] / s[3])
}

/**
 * Highest-chroma swatch (max − min channel spread) in a 22×22 grid within
 * a brightness gate — Spotify-style vivid seed.
 */
fun vibrantOf(bitmap: Bitmap): Color? {
    val w = bitmap.width; val h = bitmap.height
    if (w == 0 || h == 0) return null
    val grid = 22
    var br = 0; var bg = 0; var bb = 0
    var best = -1
    for (gy in 0 until grid) for (gx in 0 until grid) {
        val x = (gx * w / grid).coerceIn(0, w - 1)
        val y = (gy * h / grid).coerceIn(0, h - 1)
        val p = bitmap.getPixel(x, y)
        val alpha = (p ushr 24) and 0xFF
        if (alpha < 128) continue
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mx < 60 || mx > 235) continue           // gate out near-black / near-white
        val chroma = mx - mn
        if (chroma > best) { best = chroma; br = r; bg = g; bb = b }
    }
    if (best < 0) return null
    return Color(br, bg, bb)
}

// ─────────────────────────────────────────────────────────────
// Seed cache — FIFO, bounded so a long session over a huge library
// can't grow it without limit. Oldest-first eviction is plenty for
// ambient seeds; exact recency doesn't matter.
// ─────────────────────────────────────────────────────────────
private const val SEED_CACHE_MAX = 128
private val seedCache = LinkedHashMap<String, Color?>(SEED_CACHE_MAX, 0.75f, false)

@Synchronized
fun cachedSeed(key: String): Pair<Boolean, Color?> =
    if (seedCache.containsKey(key)) true to seedCache[key] else false to null

@Synchronized
fun putSeed(key: String, color: Color?): Color? {
    if (seedCache.size >= SEED_CACHE_MAX && !seedCache.containsKey(key)) {
        seedCache.remove(seedCache.keys.iterator().next())
    }
    seedCache[key] = color
    return color
}
