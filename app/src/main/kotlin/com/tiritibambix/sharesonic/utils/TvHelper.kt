package com.tiritibambix.sharesonic.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that carries whether the app is running on an Android TV device.
 * Provided at the root of the Compose tree in [MainActivity] and consumed by any
 * composable that needs to adapt its interaction model (replace swipe gestures with
 * explicit buttons, switch pager pages with tab controls, etc.).
 */
val LocalIsTV = compositionLocalOf { false }

/**
 * Returns true when the app is running on an Android TV or Google TV device.
 * `FEATURE_LEANBACK` is the check the Android TV docs recommend; the UI-mode
 * test is kept as a fallback for boxes that report TV mode without it.
 */
fun Context.isTV(): Boolean {
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    val mgr = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return mgr.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
