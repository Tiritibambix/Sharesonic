package com.tiritibambix.sharesonic.playback

import android.media.audiofx.Equalizer

/**
 * Process-wide holder for the Android platform [Equalizer] attached to the
 * playback audio session. [PlaybackService] creates it once the ExoPlayer audio
 * session id is known ([attach]); the EQ settings screen reads and controls it.
 *
 * [available] is false when the device/ROM exposes no equalizer effect (some
 * emulators, or when effect creation fails) — the UI shows a disabled state then.
 * All mutators are best-effort and never throw.
 */
object EqualizerController {

    @Volatile private var equalizer: Equalizer? = null

    val available: Boolean get() = equalizer != null

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0

    /** [min, max] band gain in millibels, or null when unavailable. */
    val levelRangeMb: Pair<Short, Short>?
        get() = equalizer?.bandLevelRange?.let { it[0] to it[1] }

    /** Center frequency of [band] in Hz (the effect reports milli-Hz). */
    fun centerFreqHz(band: Int): Int =
        (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun getBandLevelMb(band: Int): Short =
        runCatching { equalizer?.getBandLevel(band.toShort()) }.getOrNull() ?: 0

    fun setBandLevelMb(band: Int, levelMb: Short) {
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMb) }
    }

    var enabled: Boolean
        get() = runCatching { equalizer?.enabled }.getOrNull() ?: false
        // AudioEffect.setEnabled(boolean) returns an int status, so it is NOT a
        // Kotlin property setter — call it explicitly and ignore the result.
        set(value) { runCatching { equalizer?.setEnabled(value) } }

    /** Bind a fresh [Equalizer] to [audioSessionId]. No-op if already attached. */
    fun attach(audioSessionId: Int) {
        if (equalizer != null) return
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
