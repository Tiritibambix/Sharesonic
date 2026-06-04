package com.tiritibambix.sharesonic.data.api.models

import com.google.gson.annotations.SerializedName

// ── Login ──────────────────────────────────────────────────────────────────────

data class MStreamLoginRequest(
    val username: String,
    val password: String
)

data class MStreamLoginResponse(
    val token: String? = null,
    val vpaths: List<String> = emptyList(),
    val err: String? = null
)

// ── File Explorer ──────────────────────────────────────────────────────────────

data class FileExplorerRequest(
    val directory: String,
    val sort: Boolean = true,
    val pullMetadata: Boolean = false
)

data class FileExplorerResponse(
    val path: String? = null,
    val directories: List<MStreamDir> = emptyList(),
    val files: List<MStreamFile> = emptyList()
)

data class MStreamDir(
    val name: String = "",
    /** Full path provided by the server on non-root responses. */
    val path: String? = null
)

/**
 * A file entry from /api/v1/file-explorer.
 *
 * When pullMetadata=true, the server adds a nested [metadata] object shaped as:
 *   { "filepath": "library/Artist/Album/track.mp3", "metadata": { ... } }
 *
 * The [metadata.filepath] is the **full mStream path** (library name + relative path)
 * and is the correct identifier for the native /media/<filepath>?token=<jwt> stream URL.
 *
 * NOTE: mStream Subsonic IDs are plain integer DB row IDs — they are NOT related
 * to the [metadata.metadata.hash] field, which is the audio-file content hash.
 * Do not pass the hash to any Subsonic endpoint.
 */
data class MStreamFile(
    val name: String = "",
    val path: String? = null,
    val type: String? = null,
    /** Present when pullMetadata=true. Contains the full filepath + inner metadata. */
    val metadata: MStreamFileMetaWrapper? = null
) {
    /**
     * The full mStream filepath (e.g. "library/Artist/Album/track.mp3").
     * Use this to build the native stream URL: /media/<filepath>?token=<jwt>
     */
    val mStreamFilepath: String? get() = metadata?.filepath

    val isAudio: Boolean
        get() = type?.lowercase() in AUDIO_EXTENSIONS

    companion object {
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "opus", "m4a", "aac",
            "wav", "aiff", "wv", "ape", "mp4", "alac"
        )
    }
}

/** Outer wrapper returned by pullMetadata=true on each file entry. */
data class MStreamFileMetaWrapper(
    val filepath: String? = null,
    val metadata: MStreamInnerMetadata? = null
)

/** Inner metadata object (audio tags + file hash). */
data class MStreamInnerMetadata(
    val hash: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("album-art") val albumArt: String? = null
)

// ── Native share ───────────────────────────────────────────────────────────────

/**
 * POST /api/v1/share — create a public share link.
 * The resulting URL is <serverUrl>/shared/<shareId>.
 */
data class MStreamShareRequest(
    val playlist: List<String>,
    val time: Int? = null
)

data class MStreamShareResponse(
    val playlistId: String? = null
)
