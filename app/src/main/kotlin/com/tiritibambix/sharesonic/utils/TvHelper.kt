package com.tiritibambix.sharesonic.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that carries whether the app is running on an Android TV device.
 * Provided at the root of the Compose tree in [MainActivity] and consumed by any
 * composable that needs to adapt its interaction model (replace swipe gestures with
 * explicit buttons, switch pager pages with tab controls, etc.).
 */
val LocalIsTV = compositionLocalOf { false }

/** Returns true when the app is running on an Android TV or Google TV device. */
fun Context.isTV(): Boolean {
    val mgr = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return mgr.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
