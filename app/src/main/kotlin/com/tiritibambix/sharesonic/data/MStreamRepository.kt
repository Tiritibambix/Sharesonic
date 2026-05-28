package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.MStreamApiService
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.FileExplorerRequest
import com.tiritibambix.sharesonic.data.api.models.FileExplorerResponse
import com.tiritibambix.sharesonic.data.api.models.MStreamFile

class MStreamRepository(private val api: MStreamApiService) {

    /** Authenticate and return the JWT token. */
    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val resp = api.login(
                com.tiritibambix.sharesonic.data.api.models.MStreamLoginRequest(username, password)
            )
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
     * @param token   JWT bearer token
     * @param path    mStream directory path; empty string for root
     * @param pullMetadata  when true, file entries include their Subsonic track ID
     */
    suspend fun fileExplorer(
        token: String,
        path: String,
        pullMetadata: Boolean = false
    ): Result<FileExplorerResponse> {
        return try {
            val resp = api.fileExplorer(
                MStreamClient.bearerHeader(token),
                FileExplorerRequest(directory = path, sort = true, pullMetadata = pullMetadata)
            )
            Result.Success(resp)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Map a FileExplorerResponse to a flat list of EntryDto suitable for the browser.
     *
     * Directory entries: id = mStream path (used for navigation).
     * File entries:      id = Subsonic track ID (used for playback/share).
     *                    Only included when pullMetadata=true produced a valid id.
     *
     * @param response   the raw API response
     * @param currentPath the path that was queried (needed to build paths for root-level entries)
     */
    fun toEntries(response: FileExplorerResponse, currentPath: String): List<EntryDto> {
        val dirs = response.directories.map { dir ->
            // At root level dir.path is null; construct it from current path + name.
            val dirPath = dir.path ?: if (currentPath.isEmpty()) "/${dir.name}"
                                      else "$currentPath/${dir.name}"
            EntryDto(id = dirPath, title = dir.name, isDir = true, path = dirPath)
        }

        val files = response.files
            .filter { it.isAudio }
            .mapNotNull { file -> file.toEntryDto() }

        // Dirs first (already sorted by server when sort=true), then files
        return dirs + files
    }

    /**
     * Recursively collect all playable tracks under [path] for shuffle.
     * Uses pullMetadata=true so every file has a Subsonic ID.
     */
    suspend fun collectSongs(token: String, path: String): List<EntryDto> {
        val result = fileExplorer(token, path, pullMetadata = true)
        if (result !is Result.Success) return emptyList()
        val resp = result.data
        val files = resp.files.filter { it.isAudio }.mapNotNull { it.toEntryDto() }
        val nested = resp.directories.flatMap { dir ->
            val dirPath = dir.path ?: if (path.isEmpty()) "/${dir.name}" else "$path/${dir.name}"
            collectSongs(token, dirPath)
        }
        return files + nested
    }

    private fun MStreamFile.toEntryDto(): EntryDto? {
        val id = subsonicId ?: return null // need Subsonic ID to play/share
        return EntryDto(id = id, title = name, isDir = false, path = path)
    }
}
