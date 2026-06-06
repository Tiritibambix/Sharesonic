package com.tiritibambix.sharesonic.data.settings

/**
 * User-configurable settings for the Auto-DJ feature.
 * Persisted via [SettingsRepository.autoDjSettings] / [SettingsRepository.saveAutoDjSettings].
 */
data class AutoDjSettings(
    /** Use BPM continuity — prefer tracks in the same tempo range. */
    val useBpm: Boolean = true,
    /** Tight BPM range around the current track's BPM (±N). Default ±10. */
    val bpmTightRange: Int = 10,
    /** Wide BPM range fallback used when tight range yields no results (±N). Default ±20. */
    val bpmWideRange: Int = 20,
    /** If true, tracks without a BPM tag are excluded entirely. */
    val requireBpm: Boolean = false,

    /** Use harmonic mixing — prefer tracks whose key is compatible on the Camelot wheel. */
    val useHarmonicMixing: Boolean = true,
    /** If true, tracks without a musical key tag are excluded entirely. */
    val requireKey: Boolean = false,

    /** Fetch similar artists from Last.fm (via mStream) and prefer their tracks. */
    val useSimilarArtists: Boolean = true,
    /** Number of most-recently-played artists to exclude (artist cooldown). Default 5. */
    val artistCooldown: Int = 5,

    /**
     * Genre filter mode: "off" (disabled), "whitelist" (only listed genres),
     * or "blacklist" (exclude listed genres).
     */
    val genreMode: String = "off",
    /** List of genres for the whitelist / blacklist filter. */
    val genres: List<String> = emptyList(),

    /** Minimum track rating to include (0 = no minimum). */
    val minRating: Int = 0
)
