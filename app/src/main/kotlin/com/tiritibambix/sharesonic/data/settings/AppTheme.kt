package com.tiritibambix.sharesonic.data.settings

/**
 * App-wide visual theme — mirrors Velvet's five CSS themes 1-to-1.
 *
 * - [VELVET]          — navy/purple, the app default (Velvet `:root`).
 * - [DARK]            — true near-black (Velvet `:root.dark`).
 * - [LIGHT]           — soft lavender-gray (Velvet `:root.light`).
 * - [HIGH_CONTRAST]   — AAA pure black/white with a yellow accent (Velvet `:root.hc`).
 * - [COLORBLIND_SAFE] — blue primary + orange accent, no red/green reliance
 *   (Velvet `:root.cb`).
 *
 * Persisted as its [name] in DataStore via [SettingsRepository.appTheme] /
 * [SettingsRepository.saveAppTheme].
 */
enum class AppTheme {
    VELVET,
    DARK,
    LIGHT,
    HIGH_CONTRAST,
    COLORBLIND_SAFE;

    companion object {
        /** Falls back to [VELVET] (the default theme) for null/unknown stored keys. */
        fun fromKey(key: String?): AppTheme = entries.find { it.name == key } ?: VELVET
    }
}
