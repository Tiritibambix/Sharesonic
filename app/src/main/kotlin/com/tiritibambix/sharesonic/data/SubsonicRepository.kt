package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.SubsonicApiService
import com.tiritibambix.sharesonic.data.api.models.DirectoryBody
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.IndexesBody
import com.tiritibambix.sharesonic.data.api.models.MusicFolderDto
import com.tiritibambix.sharesonic.data.api.models.ShareDto

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class SubsonicRepository(private val api: SubsonicApiService) {

    suspend fun ping(): Result<Unit> {
        return try {
            val body = api.ping().response
            if (body.status == "ok") Result.Success(Unit)
            else Result.Error(body.error?.message ?: "Server error")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getMusicFolders(): Result<List<MusicFolderDto>> {
        return try {
            val body = api.getMusicFolders().response
            if (body.status == "ok")
                Result.Success(body.musicFolders?.musicFolder ?: emptyList())
            else Result.Error(body.error?.message ?: "Server error")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getIndexes(musicFolderId: String): Result<IndexesBody> {
        return try {
            val body = api.getIndexes(musicFolderId).response
            if (body.status == "ok" && body.indexes != null)
                Result.Success(body.indexes)
            else Result.Error(body.error?.message ?: "Empty index")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun getMusicDirectory(id: String): Result<DirectoryBody> {
        return try {
            val body = api.getMusicDirectory(id).response
            if (body.status == "ok" && body.directory != null)
                Result.Success(body.directory)
            else Result.Error(body.error?.message ?: "Empty directory")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun createShare(id: String): Result<ShareDto> {
        return try {
            val body = api.createShare(id).response
            if (body.status == "ok") {
                val share = body.shares?.share?.firstOrNull()
                    ?: return Result.Error("No share returned")
                Result.Success(share)
            } else Result.Error(body.error?.message ?: "Server error")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Recursively collect all songs from a directory for shuffle. */
    suspend fun collectSongs(directoryId: String): List<EntryDto> {
        val result = getMusicDirectory(directoryId)
        if (result !is Result.Success) return emptyList()
        return result.data.child.flatMap { entry ->
            if (entry.isDir) collectSongs(entry.id)
            else listOf(entry)
        }
    }
}
