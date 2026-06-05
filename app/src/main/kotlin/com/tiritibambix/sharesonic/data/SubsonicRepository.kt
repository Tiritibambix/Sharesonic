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

    /**
     * Report playback for scrobbling (integer-ID songs from search3).
     * [submission] = false → "now playing"; true → full scrobble at 50%.
     * Forwards to Last.fm / ListenBrainz based on the user's mStream settings.
     * Fire-and-forget — silently ignored on error.
     */
    suspend fun scrobble(id: String, submission: Boolean) {
        try { api.scrobble(id, submission) } catch (_: Exception) {}
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

    /**
     * Delete every Subsonic share whose expiry date is in the past.
     * Silently ignores parse and network errors — best-effort cleanup only.
     */
    suspend fun cleanupExpiredShares() {
        try {
            val body = api.getShares().response
            if (body.status != "ok") return
            val shares = body.shares?.share ?: return
            val now = java.time.Instant.now()
            shares.forEach { share ->
                val expires = share.expires ?: return@forEach
                try {
                    if (java.time.Instant.parse(expires).isBefore(now)) {
                        api.deleteShare(share.id)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
