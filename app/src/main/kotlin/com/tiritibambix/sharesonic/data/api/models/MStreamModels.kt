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
    @SerializedName("album-art") val albumArt: String? = null,
    /** Track BPM as detected by mStream Velvet's audio analysis. */
    val bpm: Float? = null,
    /** Musical key in Camelot notation (e.g. "8A", "11B"). */
    @SerializedName("musical_key") val musicalKey: String? = null,
    /** Genre tags. */
    val genres: List<String>? = null,
    /** User rating, 0–10 (mStream's native scale — half-star precision; UI shows 0–5 stars = rating / 2). */
    val rating: Int? = null
)

// ── Native share ───────────────────────────────────────────────────────────────

/**
 * POST /api/v1/share — create a public share link.
 * [time] = number of days until expiry; omit for a permanent link.
 * The resulting URL is <serverUrl>/shared/<playlistId>.
 */
data class MStreamShareRequest(
    val playlist: List<String>,
    val time: Int? = null
)

data class MStreamShareResponse(
    val playlistId: String? = null,
    /** Unix timestamp (seconds) of expiry, or null for permanent links. */
    val expires: Long? = null
)

/** One item from GET /api/v1/share/list. */
data class MStreamShareListItem(
    val playlistId: String = "",
    val songCount: Int = 0,
    /** Unix timestamp (seconds) of expiry, or null for permanent links. */
    val expires: Long? = null
)

// ── Native random songs (POST /api/v1/db/random-songs) ────────────────────────

/** BPM range for Auto-DJ filtering. */
data class BpmRange(val min: Float, val max: Float)

/**
 * Request body for POST /api/v1/db/random-songs.
 * The server returns one random song per call; pass the updated [ignoreList]
 * from each response back on the next call to avoid repeats.
 *
 * For Auto-DJ, additional fields narrow the selection to harmonically / rhythmically
 * compatible tracks.
 */
data class MStreamRandomSongsRequest(
    val ignoreList: List<Int> = emptyList(),
    val ignorePercentage: Float? = null,
    val ignoreVPaths: List<String>? = null,
    val filepathPrefix: String? = null,
    /** Tight BPM range (preferred). */
    val bpmRanges: List<BpmRange>? = null,
    /** Wide BPM range fallback (used when tight yields no results). */
    val bpmRangesWide: List<BpmRange>? = null,
    /** When true, tracks without a BPM tag are excluded. */
    val requireBpm: Boolean? = null,
    /** List of compatible Camelot keys to filter by. */
    val musicalKeys: List<String>? = null,
    /** When true, tracks without a musical_key tag are excluded. */
    val requireMusicalKey: Boolean? = null,
    /** Prefer tracks from these artists (similar artists list from Last.fm). */
    val artists: List<String>? = null,
    /** Exclude tracks from these artists (artist cooldown). */
    val ignoreArtists: List<String>? = null,
    /** Genre filter list (used together with [genreMode]). */
    val genres: List<String>? = null,
    /** Genre filter mode: "whitelist" (include only) or "blacklist" (exclude). */
    val genreMode: String? = null,
    /** Minimum track rating to include (0 = disabled). */
    val minRating: Int? = null
)

/**
 * Response from POST /api/v1/db/random-songs.
 * [songs] contains exactly one entry per call.
 * [ignoreList] is the input list with the new song's positional index appended;
 * pass it back on the next call.
 * Reuses [MStreamFileMetaWrapper] — same shape as pullMetadata=true file-explorer entries.
 */
data class MStreamRandomSongsResponse(
    val songs: List<MStreamFileMetaWrapper> = emptyList(),
    val ignoreList: List<Int> = emptyList()
)

// ── Ratings ────────────────────────────────────────────────────────────────────

/**
 * Request body for POST /api/v1/db/rate-song.
 * mStream's native rating scale is 0–10 (half-star precision); Sharesonic's UI
 * shows 0–5 stars, so the ViewModel sends `rating = stars * 2`. `null` clears it.
 */
data class MStreamRateSongRequest(
    val filepath: String,
    val rating: Int?
)

// ── Auth refresh ───────────────────────────────────────────────────────────────

/** Response from GET /api/v1/auth/refresh. */
data class MStreamRefreshResponse(val token: String? = null)

// ── On-demand art ─────────────────────────────────────────────────────────────

/** Response from GET /api/v1/files/art?fp=<filepath>. */
data class MStreamArtResponse(@SerializedName("aaFile") val aaFile: String? = null)

// ── Native search (POST /api/v1/db/search) ────────────────────────────────────

data class NativeSearchRequest(
    val search: String,
    val noFolders: Boolean = true   // we don't need folder results in the UI
)

/**
 * A song, album, or file result from the native search endpoint.
 * All String fields are nullable — GSON bypasses Kotlin non-nullability
 * and would set them to null if absent, causing NPE on access.
 */
data class NativeSearchItem(
    /** For songs: "Artist - Title". For albums: album name. */
    val name: String? = null,
    /** Album-art cache filename — use with GET /album-art/<file>?token=<jwt>. */
    @SerializedName("album_art_file") val albumArtFile: String? = null,
    /** Full mStream filepath — use as EntryDto.id for native streaming. */
    val filepath: String? = null
)

/**
 * An artist result from the native search endpoint.
 * The server also returns a `variants` array which we ignore.
 */
data class NativeSearchArtist(val name: String? = null)

data class NativeSearchResponse(
    /** Song results — each item has filepath and name formatted as "Artist - Title". */
    val title: List<NativeSearchItem> = emptyList(),
    val albums: List<NativeSearchItem> = emptyList(),
    val artists: List<NativeSearchArtist> = emptyList()
)

// ── Native playlists (POST /api/v1/playlist/*) ────────────────────────────────

/**
 * One playlist as returned by GET /api/v1/playlist/getall.
 * Does NOT include song entries — use POST /api/v1/playlist/load to fetch them.
 * [name] is the playlist name AND the identifier used by all mutation endpoints.
 */
data class NativePlaylist(
    val name: String = "",
    val songCount: Int = 0,
    val totalDuration: Int = 0
)

/**
 * One song entry from POST /api/v1/playlist/load.
 * [id]       = SQLite rowid — used by POST /api/v1/playlist/remove-song { id }
 * [filepath] = "vpath/relative/path.mp3" — same format as file-explorer, use for streaming
 */
data class NativePlaylistEntry(
    val id: Int = 0,
    val filepath: String = "",
    val metadata: NativePlaylistEntryMetadata? = null
)

/** Audio metadata returned alongside each entry in the playlist load response. */
data class NativePlaylistEntryMetadata(
    val filepath: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("album-art") val albumArt: String? = null,
    /** Duration in seconds (may be fractional). */
    val duration: Double? = null
)

/** Body for POST /api/v1/playlist/load — fetch the full song list for one playlist. */
data class NativePlaylistLoadRequest(val playlistname: String)

// Playlist request bodies ───────────────────────��─────────────────────────────

data class NativePlaylistNewRequest(val title: String)
data class NativePlaylistDeleteRequest(val playlistname: String)
data class NativePlaylistRenameRequest(val oldName: String, val newName: String)
data class NativePlaylistAddSongRequest(val song: String, val playlist: String)
data class NativePlaylistRemoveSongRequest(val id: Int)
/** Overwrite a playlist's entire song list (used to reorder or bulk-replace). */
data class NativePlaylistSaveRequest(
    val title: String,
    val songs: List<String>,
    val live: Boolean = false
)

// ── Scrobble ───────────────────────────────────────────────────────────────────

/** Body for Last.fm and ListenBrainz scrobble / playing-now endpoints. */
data class ScrobbleFilepathRequest(val filePath: String)

// ── Last.fm similar artists ────────────────────────────────────────────────────

/**
 * Response from GET /api/v1/lastfm/similar-artists?artist=<name>.
 * The exact field name in the JSON response should be verified against the
 * mStream Velvet server — this uses "artists" as a best-guess.
 */
data class SimilarArtistsResponse(val artists: List<String> = emptyList())
