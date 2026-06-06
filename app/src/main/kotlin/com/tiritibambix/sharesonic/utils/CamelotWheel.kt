package com.tiritibambix.sharesonic.utils

/**
 * Camelot Wheel utility for harmonic mixing.
 *
 * The Camelot system maps each musical key to a position on a wheel with 24 slots:
 *   1A–12A  = minor keys
 *   1B–12B  = major keys
 *
 * Two tracks are harmonically compatible when their Camelot keys are:
 *   - Identical (same energy)
 *   - Adjacent by ±1 on the wheel (same letter, number differs by 1 — energy increase/decrease)
 *   - Relative major/minor (same number, A ↔ B — tonal shift)
 *
 * Example: "8A" is compatible with "7A", "8A", "9A", and "8B".
 * The wheel wraps: "12A" → "1A" and "1A" → "12A".
 */
object CamelotWheel {

    /**
     * Returns all Camelot keys that are harmonically compatible with [key].
     * Returns [key] alone if the input is not a valid Camelot notation (e.g. unknown format).
     *
     * @param key A Camelot key string such as "8A", "11B", "1A", etc.
     */
    fun compatibleKeys(key: String): List<String> {
        val trimmed = key.trim()
        if (trimmed.length < 2) return listOf(key)

        val letter = trimmed.last().uppercaseChar()
        val numberStr = trimmed.dropLast(1)
        val number = numberStr.toIntOrNull() ?: return listOf(key)

        if (letter != 'A' && letter != 'B') return listOf(key)
        if (number < 1 || number > 12) return listOf(key)

        val oppositeLetter = if (letter == 'A') 'B' else 'A'
        val prevNumber = if (number == 1) 12 else number - 1
        val nextNumber = if (number == 12) 1 else number + 1

        return listOf(
            "${number}${letter}",          // same key
            "${prevNumber}${letter}",       // step down (same mode)
            "${nextNumber}${letter}",       // step up (same mode)
            "${number}${oppositeLetter}"    // relative major/minor
        ).distinct()
    }
}
