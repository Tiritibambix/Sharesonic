package com.tiritibambix.sharesonic.data.playback

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Last playback position, persisted so the app can resume where the user left
 * off after being closed (or killed).
 *
 * Stored as a JSON file rather than in the Preferences DataStore: a folder
 * shuffle can queue up to SHUFFLE_MAX (5000) tracks, and Preferences rewrites
 * its whole file on every write — persisting a queue that large through it
 * would be pathological. A plain file lets us write the queue only when it
 * actually changes, and keep the cheap position ticks separate.
 */
data class SavedPlayback(
    val queue: List<EntryDto> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
)

class PlaybackStateStore(private val context: Context) {

    private val gson = Gson()
    private fun file() = File(context.filesDir, FILE_NAME)

    /**
     * Persist [state]. The queue is capped at [MAX_QUEUE] entries centred on the
     * current index — enough to resume meaningfully without writing a megabyte
     * of JSON for a 5000-track shuffle.
     */
    suspend fun save(state: SavedPlayback) = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = trim(state)
            file().writeText(gson.toJson(trimmed))
        }.onFailure { Log.w(TAG, "Failed to save playback state", it) }
        Unit
    }

    /** Load the saved state, or null when nothing was stored / it's unreadable. */
    suspend fun load(): SavedPlayback? = withContext(Dispatchers.IO) {
        val f = file()
        if (!f.exists()) return@withContext null
        runCatching {
            val type = object : TypeToken<SavedPlayback>() {}.type
            gson.fromJson<SavedPlayback>(f.readText(), type)
                ?.takeIf { it.queue.isNotEmpty() }
        }.getOrNull()
    }

    /** Forget the saved position (e.g. the queue was cleared). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file().delete() }
        Unit
    }

    /**
     * Keep a window of [MAX_QUEUE] tracks around [SavedPlayback.index] and
     * rebase the index onto the trimmed list, so resuming lands on the same
     * track with its neighbours intact.
     */
    private fun trim(state: SavedPlayback): SavedPlayback {
        if (state.queue.size <= MAX_QUEUE) return state
        val half = MAX_QUEUE / 2
        val start = (state.index - half).coerceIn(0, (state.queue.size - MAX_QUEUE).coerceAtLeast(0))
        val end = (start + MAX_QUEUE).coerceAtMost(state.queue.size)
        return state.copy(
            queue = state.queue.subList(start, end).toList(),
            index = (state.index - start).coerceIn(0, (end - start - 1).coerceAtLeast(0)),
        )
    }

    private companion object {
        const val TAG = "PlaybackStateStore"
        const val FILE_NAME = "playback_state.json"
        /** Cap on persisted queue length — a resume window, not the whole shuffle. */
        const val MAX_QUEUE = 300
    }
}
