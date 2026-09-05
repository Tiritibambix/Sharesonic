package com.tiritibambix.sharesonic.utils

/**
 * Camelot Wheel utility for harmonic mixing.
 *
 * The Camelot system maps each musical key to a position on a 24-slot wheel:
 *   1A–12A = minor keys, 1B–12B = major keys.
 *
 * **Important — key notation on the wire.** Velvet stores and returns the
 * musical key in *long-form musical notation* (e.g. `"A minor"`, `"C major"`,
 * `"F# minor"`) in the `musical-key` field — NOT as a Camelot code. The web
 * player converts it to Camelot with the same [CODE_BY_KEY] table ported here.
 * (A previous Sharesonic assumption that the field was already Camelot meant
 * harmonic scoring silently never matched.) Always run a raw key through
 * [toCamelot] before comparing.
 */
object CamelotWheel {

    /** Long-form musical key name → Camelot code. Ported from Velvet's web app. */
    private val CODE_BY_KEY: Map<String, String> = mapOf(
        "Ab minor" to "1A", "G# minor" to "1A", "B major" to "1B",
        "Eb minor" to "2A", "D# minor" to "2A", "F# major" to "2B", "Gb major" to "2B",
        "Bb minor" to "3A", "A# minor" to "3A", "Db major" to "3B", "C# major" to "3B",
        "F minor" to "4A", "Ab major" to "4B", "G# major" to "4B",
        "C minor" to "5A", "Eb major" to "5B", "D# major" to "5B",
        "G minor" to "6A", "Bb major" to "6B", "A# major" to "6B",
        "D minor" to "7A", "F major" to "7B",
        "A minor" to "8A", "C major" to "8B",
        "E minor" to "9A", "G major" to "9B",
        "B minor" to "10A", "D major" to "10B",
        "F# minor" to "11A", "A major" to "11B",
        "C# minor" to "12A", "E major" to "12B",
    )

    private val CAMELOT_CODE = Regex("^(1[0-2]|[1-9])[AB]$")

    /**
     * Normalise a raw key value to its canonical Camelot code, or null if it
     * isn't a key we recognise. Accepts both long-form names (`"A minor"`) and
     * values that are already Camelot codes (`"8a"` → `"8A"`).
     */
    fun toCamelot(key: String?): String? {
        val raw = key?.trim() ?: return null
        if (raw.isEmpty()) return null
        val upper = raw.uppercase()
        if (CAMELOT_CODE.matches(upper)) return upper
        return CODE_BY_KEY[raw]
    }

    /**
     * The set of Camelot codes harmonically adjacent to [code] — the current
     * position, its relative major/minor, and the ±1 steps with their relatives
     * (6 codes total). Mirrors the web player's `camelotNeighbours`. Returns an
     * empty set for an invalid code.
     *
     * @param code A canonical Camelot code (run the raw key through [toCamelot] first).
     */
    fun neighbours(code: String?): Set<String> {
        if (code.isNullOrEmpty()) return emptySet()
        val num = code.dropLast(1).toIntOrNull() ?: return emptySet()
        if (num < 1 || num > 12) return emptySet()
        val letter = code.last()
        if (letter != 'A' && letter != 'B') return emptySet()
        val other = if (letter == 'A') 'B' else 'A'
        val prev = ((num - 2 + 12) % 12) + 1
        val next = (num % 12) + 1
        return setOf(
            "$num$letter", "$num$other",
            "$prev$letter", "$prev$other",
            "$next$letter", "$next$other",
        )
    }
}
