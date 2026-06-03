package com.tiritibambix.sharesonic.data.api.models

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
    /** Present when pullMetadata=true. */
    val metadata: MStreamMetadata? = null
) {
    /** The Subsonic-compatible ID to pass to /rest/stream and createShare. */
    val subsonicId: String? get() = metadata?.hash

    val isAudio: Boolean
        get() = type?.lowercase() in AUDIO_EXTENSIONS

    companion object {
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "opus", "m4a", "aac",
            "wav", "aiff", "wv", "ape", "mp4", "alac"
        )
    }
}

data class MStreamMetadata(
    val hash: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)
