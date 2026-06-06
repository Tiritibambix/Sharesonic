package com.tiritibambix.sharesonic.data

import com.tiritibambix.sharesonic.data.api.SubsonicApiService
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.PlaylistDetailDto
import com.tiritibambix.sharesonic.data.api.models.PlaylistDto
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

    // ── Playlists ─────────────────────────────────────────────────────────────

    /** List all playlists visible to the authenticated user. */
    suspend fun getPlaylists(): Result<List<PlaylistDto>> = try {
        val body = api.getPlaylists().response
        if (body.status == "ok") Result.Success(body.playlists?.playlist ?: emptyList())
        else Result.Error(body.error?.message ?: "Server error")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /** Fetch a playlist's full track list by ID. */
    suspend fun getPlaylist(id: String): Result<PlaylistDetailDto> = try {
        val body = api.getPlaylist(id).response
        if (body.status == "ok") {
            val detail = body.playlist ?: return Result.Error("No playlist returned")
            Result.Success(detail)
        } else Result.Error(body.error?.message ?: "Server error")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /** Create a new empty playlist and return its metadata. */
    suspend fun createPlaylist(name: String): Result<PlaylistDto> = try {
        val body = api.createPlaylist(name).response
        if (body.status == "ok") {
            val detail = body.playlist ?: return Result.Error("No playlist returned")
            Result.Success(PlaylistDto(id = detail.id, name = detail.name, songCount = detail.songCount, duration = detail.duration))
        } else Result.Error(body.error?.message ?: "Server error")
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    /**
     * Rename a playlist. Fire-and-forget — errors are silently ignored.
     * The caller should refresh the playlist list / detail after calling this.
     */
    suspend fun renamePlaylist(playlistId: String, name: String) {
        try { api.updatePlaylist(playlistId = playlistId, name = name) } catch (_: Exception) {}
    }

    /**
     * Append songs to an existing playlist.
     * [songIds] must be Subsonic integer IDs (from search3 results).
     * Fire-and-forget — caller refreshes playlist detail.
     */
    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        try { api.updatePlaylist(playlistId = playlistId, songIdsToAdd = songIds) } catch (_: Exception) {}
    }

    /**
     * Remove a single song from a playlist by its 0-based position.
     * Fire-and-forget — caller refreshes playlist detail.
     */
    suspend fun removeSongFromPlaylist(playlistId: String, index: Int) {
        try { api.updatePlaylist(playlistId = playlistId, indicesToRemove = listOf(index)) } catch (_: Exception) {}
    }

    /** Permanently delete a playlist. Fire-and-forget. */
    suspend fun deletePlaylist(id: String) {
        try { api.deletePlaylist(id) } catch (_: Exception) {}
    }

    // ── Shares ────────────────────────────────────────────────────────────────

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
