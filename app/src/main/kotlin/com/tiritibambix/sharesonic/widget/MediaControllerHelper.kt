package com.tiritibambix.sharesonic.widget

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tiritibambix.sharesonic.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Connect to the app's [PlaybackService] via a [MediaController], hand the
 * controller to [block], then release the controller.
 *
 * All widget [androidx.glance.appwidget.action.ActionCallback]s that need to
 * dispatch a transport command use this helper — building the controller once
 * per action is cheap (a bound-service handshake) and avoids leaking a
 * controller when the widget's WorkManager job exits.
 *
 * If the service isn't running, buildAsync starts it (media3 handles the bind).
 *
 * The 150 ms delay before release() is deliberate: MediaController transport
 * calls are dispatched to the session asynchronously, and releasing the
 * controller right after `.play()` / `.pause()` was silently dropping the
 * command on some devices ("widget buttons do nothing" symptom). 150 ms is
 * plenty for the IPC round-trip and cheap in a widget click context.
 *
 * Any thrown exception is caught + logged so a failing tap doesn't propagate
 * into Glance's WorkManager, which would keep retrying the action.
 */
suspend fun withMediaController(context: Context, block: (Player) -> Unit) {
    try {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller: MediaController = suspendCancellableCoroutine { cont ->
            future.addListener({
                try { cont.resume(future.get()) } catch (t: Throwable) { cont.resumeWithException(t) }
            }, MoreExecutors.directExecutor())
            cont.invokeOnCancellation { future.cancel(true) }
        }
        try {
            block(controller)
            delay(150)
        } finally {
            controller.release()
        }
    } catch (t: Throwable) {
        Log.w("SharesonicWidget", "MediaController action failed", t)
    }
}
