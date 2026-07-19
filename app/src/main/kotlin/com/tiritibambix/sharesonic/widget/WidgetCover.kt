package com.tiritibambix.sharesonic.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File

/**
 * Widget cover-art cache — deliberately split into a slow write and a fast read
 * so the widget's render path NEVER touches the network.
 *
 * The previous design downloaded the cover with Coil inside `provideGlance`,
 * which runs on every `update()`. That blocked the ENTIRE re-render (text,
 * buttons, Auto-DJ pill, rating) behind a network fetch to the self-hosted
 * Velvet server — up to a ~2-minute timeout. Now the download happens once,
 * off the render path (in the ViewModel's collector), writing a cached file;
 * `provideGlance` only reads that file synchronously, so re-renders are instant.
 */

private fun coverFile(context: Context): File = File(context.cacheDir, "widget_cover.png")

/**
 * Download [url] via Coil and cache it to a file. Suspend + potentially slow —
 * call this OFF the widget render path (from the app's own coroutine when the
 * current track's cover URL changes). Deletes the cache when [url] is null so a
 * cover-less track doesn't keep showing the previous artwork.
 */
suspend fun cacheWidgetCover(context: Context, url: String?) {
    val file = coverFile(context)
    if (url.isNullOrBlank()) {
        runCatching { file.delete() }
        return
    }
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(256)
        .allowHardware(false)
        .build()
    val bmp = ((runCatching { context.imageLoader.execute(request) }.getOrNull() as? SuccessResult)
        ?.drawable as? BitmapDrawable)?.bitmap
    if (bmp != null) {
        runCatching {
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
    } else {
        runCatching { file.delete() }
    }
}

/**
 * Read the cached cover bitmap synchronously — no network, effectively instant.
 * Returns null when nothing is cached (⇒ the widget shows its placeholder).
 * Safe to call from `provideGlance`.
 */
fun decodeWidgetCover(context: Context): Bitmap? {
    val file = coverFile(context)
    if (!file.exists()) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
}

/** Drop the cached cover (used when the track changes before the new one loads). */
fun deleteWidgetCover(context: Context) {
    runCatching { coverFile(context).delete() }
}
