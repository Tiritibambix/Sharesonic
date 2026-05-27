package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.SubsonicApiService
import com.tiritibambix.sharesonic.data.api.models.DirectoryBody
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.MusicFolderDto
import com.tiritibambix.sharesonic.data.api.models.ShareDto

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class SubsonicRepository(private val api: SubsonicApiService) {

    suspend fun ping(): Result<Unit> = runCatching {
        val body = api.ping().response
        if (body.status == "ok") Result.Success(Unit)
        else Result.Error(body.error?.message ?: "Server error")
    }.getOrElse { Result.Error(it.message ?: "Network error") }

    suspend fun getMusicFolders(): Result<List<MusicFolderDto>> = runCatching {
        val body = api.getMusicFolders().response
        if (body.status == "ok")
            Result.Success(body.musicFolders?.musicFolder ?: emptyList())
        else Result.Error(body.error?.message ?: "Server error")
    }.getOrElse { Result.Error(it.message ?: "Network error") }

    suspend fun getMusicDirectory(id: String): Result<DirectoryBody> = runCatching {
        val body = api.getMusicDirectory(id).response
        if (body.status == "ok" && body.directory != null)
            Result.Success(body.directory)
        else Result.Error(body.error?.message ?: "Empty directory")
    }.getOrElse { Result.Error(it.message ?: "Network error") }

    suspend fun createShare(id: String): Result<ShareDto> = runCatching {
        val body = api.createShare(id).response
        if (body.status == "ok") {
            val share = body.shares?.share?.firstOrNull()
                ?: return@runCatching Result.Error("No share returned")
            Result.Success(share)
        } else Result.Error(body.error?.message ?: "Server error")
    }.getOrElse { Result.Error(it.message ?: "Network error") }

    /** Recursively collect all songs from a directory for shuffle. */
    suspend fun collectSongs(directoryId: String): List<EntryDto> {
        val result = getMusicDirectory(directoryId)
        if (result !is Result.Success) return emptyList()
        val entries = result.data.child
        return entries.flatMap { entry ->
            if (entry.isDir) collectSongs(entry.id)
            else listOf(entry)
        }
    }
}
