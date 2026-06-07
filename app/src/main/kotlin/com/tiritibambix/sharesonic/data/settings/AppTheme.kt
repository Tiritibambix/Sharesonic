package com.tiritibambix.sharesonic.data.settings

/**
 * App-wide visual theme.
 *
 * - [VELVET] — the mStream-matching navy/purple palette (app default).
 * - [DARK]   — true near-black scheme (Material/Apple dark guidelines).
 * - [LIGHT]  — soft lavender-gray light scheme.
 *
 * Persisted as its [name] in DataStore via [SettingsRepository.appTheme] /
 * [SettingsRepository.saveAppTheme].
 */
enum class AppTheme {
    VELVET,
    DARK,
    LIGHT;

    companion object {
        /** Falls back to [VELVET] (the default theme) for null/unknown stored keys. */
        fun fromKey(key: String?): AppTheme = entries.find { it.name == key } ?: VELVET
    }
}
