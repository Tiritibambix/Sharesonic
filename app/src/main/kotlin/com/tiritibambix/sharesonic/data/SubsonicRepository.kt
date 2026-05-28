package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.SubsonicApiService
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.SearchResult3
import com.tiritibambix.sharesonic.data.api.models.ShareDto

/**
 * Subsonic API repository — playback, sharing, random shuffle, search.
 * Folder browsing is handled by MStreamRepository.
 */
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

    suspend fun getRandomSongs(size: Int = 200): Result<List<EntryDto>> {
        return try {
            val body = api.getRandomSongs(size).response
            if (body.status == "ok")
                Result.Success(body.randomSongs?.song ?: emptyList())
            else Result.Error(body.error?.message ?: "Server error")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun search(query: String): Result<SearchResult3> {
        return try {
            val body = api.search3(query).response
            if (body.status == "ok")
                Result.Success(body.searchResult3 ?: SearchResult3())
            else Result.Error(body.error?.message ?: "Server error")
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
}
