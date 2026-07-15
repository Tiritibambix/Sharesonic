package com.tiritibambix.sharesonic.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Fetch [url] via Coil and hand back a raw [Bitmap] suitable for Glance's
 * `ImageProvider(bitmap)`. Downscaled to 256 px on the shortest side — enough
 * for a widget thumbnail on any launcher and cheap to cache. Returns null if
 * [url] is null, the request fails, or Coil returned a non-bitmap drawable
 * (shouldn't happen for an image URL, but we can't assume).
 *
 * Widgets can't safely use Compose's AsyncImage — RemoteViews only accepts
 * Bitmap-backed ImageViews. Calling this from provideGlance's suspend body is
 * the pattern the Glance docs demonstrate.
 */
suspend fun loadCoverArtBitmap(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(256)
        .allowHardware(false)
        .build()
    val result = runCatching { context.imageLoader.execute(request) }.getOrNull() ?: return null
    return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
}
