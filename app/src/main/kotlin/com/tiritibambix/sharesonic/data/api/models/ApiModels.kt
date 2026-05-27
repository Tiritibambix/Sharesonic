package com.tiritibambix.sharesonic.data.api.models

import com.google.gson.annotations.SerializedName

// ── Top-level wrapper ──────────────────────────────────────────────────────────

data class SubsonicEnvelope(
    @SerializedName("subsonic-response") val response: SubsonicBody
)

data class SubsonicBody(
    val status: String = "",
    val version: String = "",
    val musicFolders: MusicFoldersContainer? = null,
    val indexes: IndexesBody? = null,
    val directory: DirectoryBody? = null,
    val randomSongs: RandomSongsContainer? = null,
    val shares: SharesContainer? = null,
    val error: SubsonicError? = null
)

data class SubsonicError(val code: Int = 0, val message: String = "")

// ── Music folders ──────────────────────────────────────────────────────────────

data class MusicFoldersContainer(val musicFolder: List<MusicFolderDto> = emptyList())

data class MusicFolderDto(val id: String = "", val name: String = "")

// ── Indexes (top-level folders inside a music library) ────────────────────────

data class IndexesBody(
    val index: List<IndexGroup> = emptyList(),
    // loose files at library root (rare but possible)
    val child: List<EntryDto> = emptyList()
)

data class IndexGroup(
    val name: String = "",
    // In folder-browsing mode these "artists" are top-level directories
    val artist: List<TopLevelDir> = emptyList()
)

data class TopLevelDir(
    val id: String = "",
    val name: String = ""
)

// ── Directory browsing ─────────────────────────────────────────────────────────

data class DirectoryBody(
    val id: String = "",
    val name: String = "",
    val parent: String? = null,
    val child: List<EntryDto> = emptyList()
)

data class EntryDto(
    val id: String = "",
    // directories expose "title"; songs expose "title" or fall back to "name"
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
    val year: Int? = null
) {
    val displayName: String get() = title ?: name ?: id
}

// ── Random songs (shuffle all) ────────────────────────────────────────────────

data class RandomSongsContainer(val song: List<EntryDto> = emptyList())

// ── Shares ─────────────────────────────────────────────────────────────────────

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
