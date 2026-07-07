package com.tiritibambix.sharesonic.utils

import com.tiritibambix.sharesonic.data.api.models.EntryDto

/**
 * Client-side keyword blocker for Auto-DJ, mirroring Velvet's autoplay filter
 * (server has no equivalent field — the check runs entirely in the app).
 *
 * Velvet's normalization: lowercase + collapse runs of the same character via
 * `(.)\1+ → $1`, so "acappella" and "acapella" both normalize to "acapela" and
 * match. Substring match runs against title+artist+album+filepath joined with
 * spaces — one hit blocks the song.
 */
object KeywordFilter {
    private val repeatedChars = Regex("(.)\\1+")

    fun normalize(s: String): String =
        s.lowercase().replace(repeatedChars) { it.groupValues[1] }

    fun isBlocked(song: EntryDto, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        val haystack = normalize(
            listOfNotNull(song.title, song.artist, song.album, song.path)
                .joinToString(" ")
        )
        return words.any { w ->
            val n = normalize(w.trim())
            n.isNotEmpty() && haystack.contains(n)
        }
    }
}
