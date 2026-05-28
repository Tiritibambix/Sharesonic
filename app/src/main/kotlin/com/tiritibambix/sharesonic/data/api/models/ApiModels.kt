package com.tiritibambix.sharesonic.data.api.models

import com.google.gson.annotations.SerializedName

// ── Subsonic API wrapper ───────────────────────────────────────────────────────

data class SubsonicEnvelope(
    @SerializedName("subsonic-response") val response: SubsonicBody
)

data class SubsonicBody(
    val status: String = "",
    val version: String = "",
    val randomSongs: RandomSongsContainer? = null,
    val searchResult3: SearchResult3? = null,
    val shares: SharesContainer? = null,
    val error: SubsonicError? = null
)

data class SubsonicError(val code: Int = 0, val message: String = "")

// ── EntryDto — shared by mStream file entries and Subsonic random-songs ────────

/**
 * For mStream file-explorer entries:
 *   isDir=true  → id = mStream directory path (for navigation)
 *   isDir=false → id = Subsonic track ID      (for playback / createShare)
 *
 * For Subsonic getRandomSongs entries:
 *   id = Subsonic track ID
 */
data class EntryDto(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int? = null,
    val isDir: Boolean = false,
    val suffix: String? = null,
    val contentType: String? = null,
    val coverArt: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val path: String? = null
) {
    val displayName: String get() = title ?: name ?: id
}

// ── Random songs (Subsonic getRandomSongs) ─────────────────────────────────────

data class RandomSongsContainer(val song: List<EntryDto> = emptyList())

// ── Search (Subsonic search3) ──────────────────────────────────────────────────

data class SearchResult3(
    val song: List<EntryDto> = emptyList(),
    val album: List<EntryDto> = emptyList(),
    val artist: List<TopLevelDir> = emptyList()
)

data class TopLevelDir(val id: String = "", val name: String = "")

// ── Shares (Subsonic createShare / getShares) ──────────────────────────────────

data class SharesContainer(val share: List<ShareDto> = emptyList())

data class ShareDto(
    val id: String = "",
    val url: String = "",
    val description: String? = null,
    val username: String? = null,
    val created: String? = null,
    val expires: String? = null,
    val visitCount: Int = 0
)
