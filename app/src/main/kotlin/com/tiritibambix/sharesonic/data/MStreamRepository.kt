package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.MStreamApiService
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamFile
import com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest

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
     * @param pullMetadata   when true, file entries include their Subsonic track ID
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
     * Directory entry.id  = mStream path   (used for navigation)
     * File entry.id       = Subsonic trackId (used for playback / createShare)
     *
     * Only audio files (matched by extension) are included.
     * Files without a Subsonic ID (pullMetadata was false) are excluded.
     *
     * @param response    the raw API response
     * @param currentPath the path that was queried (used to build paths at root level)
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
     * Uses pullMetadata=true so every file has a Subsonic ID.
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

    private fun fileToEntryDto(file: MStreamFile): EntryDto? {
        val id = file.subsonicId ?: return null
        return EntryDto(id = id, title = file.name, isDir = false, path = file.path)
    }
}
