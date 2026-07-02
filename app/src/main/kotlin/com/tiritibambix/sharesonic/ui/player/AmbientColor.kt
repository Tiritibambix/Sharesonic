package com.tiritibambix.sharesonic.ui.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Loads [imageUrl] via Coil and extracts a representative "ambient" colour from
 * the artwork (vibrant swatch preferred, then dominant, then muted) for use as a
 * soft gradient behind the Now Playing screen. Returns null while loading, if the
 * URL is null, or if no colour could be derived. Recomputes when [imageUrl] changes.
 *
 * `allowHardware(false)` forces a software bitmap so Palette can read its pixels
 * (hardware bitmaps aren't readable on the CPU).
 */
@Composable
fun rememberAmbientColor(imageUrl: String?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(initialValue = null, imageUrl) {
        value = null
        val url = imageUrl ?: return@produceState
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable
            ?.let { it as? BitmapDrawable }
            ?.bitmap
            ?: return@produceState
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
            ?: palette.darkVibrantSwatch
        value = swatch?.rgb?.let { Color(it) }
    }
    return color
}
