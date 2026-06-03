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

data class MStreamFile(
    val name: String = "",
    val path: String? = null,
    val type: String? = null,
    val filepath: String? = null,
    /**
     * Top-level Subsonic-compatible ID — present in some mStream versions when
     * pullMetadata=true. This is the canonical Subsonic song ID for /rest/stream
     * and createShare.
     */
    val id: String? = null,
    @SerializedName("track_id") val trackId: String? = null,
    /** Present when pullMetadata=true. */
    val metadata: MStreamFileMetaWrapper? = null
) {
    /**
     * The Subsonic-compatible ID to pass to /rest/stream and createShare.
     *
     * Priority chain — the first non-blank value wins:
     *   1. id          — top-level field (documented in CLAUDE.md, canonical)
     *   2. track_id    — top-level alternative field
     *   3. metadata.metadata.hash — double-nested hash
     *   4. metadata.hash          — single-nested hash fallback
     */
    val subsonicId: String? get() =
        id?.takeIf { it.isNotBlank() }
        ?: trackId?.takeIf { it.isNotBlank() }
        ?: metadata?.metadata?.hash?.takeIf { it.isNotBlank() }
        ?: metadata?.hash?.takeIf { it.isNotBlank() }

    val isAudio: Boolean
        get() = type?.lowercase() in AUDIO_EXTENSIONS

    companion object {
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "opus", "m4a", "aac",
            "wav", "aiff", "wv", "ape", "mp4", "alac"
        )
    }
}

data class MStreamFileMetaWrapper(
    val hash: String? = null,        // single-nested: metadata.hash
    val filepath: String? = null,
    val metadata: MStreamInnerMetadata? = null  // double-nested: metadata.metadata.hash
)

data class MStreamInnerMetadata(
    val hash: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)
