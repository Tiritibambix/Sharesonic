package com.tiritibambix.sharesonic.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Loads [imageUrl] via Coil, downscales it, and extracts a representative
 * "ambient" colour from the artwork using [AmbientEngine]'s dominant / vibrant
 * quantiser. Returns null while loading, if the URL is null, or if no colour
 * could be derived. Recomputes when the URL or [vibrant] changes.
 *
 * Previously used `androidx.palette` for swatch extraction, which was a whole
 * dependency for a mediocre visual (Palette's vibrant swatch is often a stray
 * highlight, not the field colour). The engine's own 12-bit bucket count on a
 * 64 px thumbnail is smaller, faster, and gives the "album halo" look we want.
 *
 * `allowHardware(false)` forces a software bitmap so the pixel scan can run
 * on the CPU (hardware bitmaps aren't readable via `getPixel`).
 */
@Composable
fun rememberAmbientColor(imageUrl: String?, vibrant: Boolean = false): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(initialValue = null, imageUrl, vibrant) {
        value = null
        val url = imageUrl ?: return@produceState
        val cacheKey = "$url|${if (vibrant) 'v' else 'm'}"
        val (hit, cached) = cachedSeed(cacheKey)
        if (hit) { value = cached; return@produceState }
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(64)
            .build()
        val bitmap: Bitmap = (context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable
            ?.let { it as? BitmapDrawable }
            ?.bitmap
            ?: return@produceState
        val seed = if (vibrant) vibrantOf(bitmap) else dominantOf(bitmap)
        putSeed(cacheKey, seed)
        value = seed
    }
    return color
}

/**
 * Loads the ambient seed from [imageUrl] and returns a ready-to-use OKLCH
 * radial brush composed over [base] via [ambientBrush]. Returns null while
 * loading, when the URL is null, or when the seed is too grayscale to tint
 * (WCAG-safe fallback).
 *
 * Callers should paint this as the outer container's background — the
 * signature "album halo" look.
 */
@Composable
fun rememberAmbientBrush(
    imageUrl: String?,
    base: Color,
    vibrant: Boolean = false,
    center: Offset = Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY,
): Brush? {
    val seed = rememberAmbientColor(imageUrl, vibrant) ?: return null
    return ambientBrush(seed, base = base, vibrant = vibrant, center = center, radius = radius)
}
