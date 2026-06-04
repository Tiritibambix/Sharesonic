package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.MStreamApiService
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamFile
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest
import com.tiritibambix.sharesonic.data.api.models.MStreamShareRequest

class MStreamRepository(private val api: MStreamApiService) {

    /** Authenticate and return the JWT token. */
    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val resp = api.login(MStreamLoginRequest(username, password))
            val token = resp.token
            if (!token.isNullOrEmpty()) Result.Success(token)
            else Result.Error(resp.err ?: "Login failed")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Browse a directory.
     *
     * @param token          JWT bearer token
     * @param path           mStream directory path; empty string for root
     * @param pullMetadata   when true, file entries include filepath + audio tags
     */
    suspend fun fileExplorer(
        token: String,
        path: String,
        pullMetadata: Boolean = false
    ): Result<FileExplorerResponse> {
        return try {
            val resp = api.fileExplorer(
                token,
                FileExplorerRequest(directory = path, sort = true, pullMetadata = pullMetadata)
            )
            Result.Success(resp)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Map a FileExplorerResponse to a list of EntryDto for the browser UI.
     *
     * Directory entry.id = mStream path      (for navigation)
     * File entry.id      = mStream filepath  (for native /media/<filepath>?token= streaming)
     *
     * Only audio files with a known filepath (pullMetadata=true) are included.
     */
    fun toEntries(response: FileExplorerResponse, currentPath: String): List<EntryDto> {
        val dirs: List<EntryDto> = response.directories.map { dir ->
            val dirPath: String = dir.path
                ?: if (currentPath.isEmpty()) "/${dir.name}" else "$currentPath/${dir.name}"
            EntryDto(id = dirPath, title = dir.name, isDir = true, path = dirPath)
        }

        val files: List<EntryDto> = response.files
            .filter { it.isAudio }
            .mapNotNull { file: MStreamFile -> fileToEntryDto(file) }

        return dirs + files
    }

    /**
     * Recursively collect all playable tracks under [path] for shuffle.
     * Uses pullMetadata=true so every file has a filepath for native streaming.
     */
    suspend fun collectSongs(token: String, path: String): List<EntryDto> {
        val result = fileExplorer(token, path, pullMetadata = true)
        if (result !is Result.Success) return emptyList()

        val resp: FileExplorerResponse = result.data
        val files: List<EntryDto> = resp.files
            .filter { it.isAudio }
            .mapNotNull { file: MStreamFile -> fileToEntryDto(file) }

        val nested: List<EntryDto> = resp.directories.flatMap { dir ->
            val dirPath: String = dir.path
                ?: if (path.isEmpty()) "/${dir.name}" else "$path/${dir.name}"
            collectSongs(token, dirPath)
        }

        return files + nested
    }

    /**
     * Create a public share link for a single track.
     *
     * @param token    JWT bearer token
     * @param filepath Full mStream filepath (e.g. "library/Artist/Album/track.mp3")
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun share(token: String, filepath: String): Result<String> =
        shareFilepaths(token, listOf(filepath))

    /**
     * Create a public share link for all audio tracks under [path].
     * Recursively collects every track and creates a single shared playlist
     * with a 14-day expiry.
     *
     * @param token JWT bearer token
     * @param path  mStream directory path (e.g. "/library/Artist")
     * @return the playlistId — caller builds URL as <serverUrl>/shared/<playlistId>
     */
    suspend fun shareFolder(token: String, path: String): Result<String> {
        val songs = collectSongs(token, path)
        if (songs.isEmpty()) return Result.Error("No audio files found in folder")
        return shareFilepaths(token, songs.map { it.id }, expiryDays = 14)
    }

    private suspend fun shareFilepaths(
        token: String,
        filepaths: List<String>,
        expiryDays: Int? = null
    ): Result<String> {
        return try {
            val expirySeconds = expiryDays?.let {
                (System.currentTimeMillis() / 1000L + it * 86400L).toInt()
            }
            val resp = api.share(token, MStreamShareRequest(playlist = filepaths, time = expirySeconds))
            val shareId = resp.playlistId
            if (!shareId.isNullOrBlank()) Result.Success(shareId)
            else Result.Error("Share failed: no shareId returned")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    private fun fileToEntryDto(file: MStreamFile): EntryDto? {
        // The full mStream filepath (library + relative path) is our stable identifier.
        // It is used to build the native stream URL: /media/<filepath>?token=<jwt>
        val filepath = file.mStreamFilepath ?: return null
        val meta = file.metadata?.metadata
        return EntryDto(
            id = filepath,
            title = meta?.title?.takeIf { it.isNotBlank() } ?: file.name,
            artist = meta?.artist,
            album = meta?.album,
            coverArt = meta?.albumArt,
            isDir = false,
            path = filepath
        )
    }
}
