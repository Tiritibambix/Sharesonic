package com.tiritibambix.sharesonic.playback

import android.content.Context
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.api.models.BpmRange
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.VelvetRandomSongsRequest
import com.tiritibambix.sharesonic.data.settings.AutoDjSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.utils.CamelotWheel
import com.tiritibambix.sharesonic.utils.KeywordFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared Auto-DJ next-track engine. One instance lives in the
 * [PlaybackService] (drives auto-DJ when the app UI is dead) and one lives in
 * [com.tiritibambix.sharesonic.ui.player.PlayerViewModel] (drives it while the
 * user is in-app). Both instances read the same persistent flag
 * ([SettingsRepository.autoDjEnabled]) and the same tunable settings
 * ([SettingsRepository.autoDjSettings]).
 *
 * The volatile session state (ignoreList / recentlyPlayed / similar-artists
 * cache) is kept per-instance — losing it when the app is killed is fine, the
 * server just re-explores from scratch. Not worth the complexity of sharing.
 *
 * Priority order (matches the historical algorithm from PlayerViewModel):
 *   1. Similar artists (Last.fm cache) + BPM (tight) + Camelot key
 *   2. BPM (wide) + Camelot key  (server-side fallback if tight fails)
 *   3. Plain random  (fallback — no constraints)
 *
 * Client-side Keyword Filter (Velvet has no equivalent server field) re-fetches
 * up to [MAX_KEYWORD_ATTEMPTS] times if the returned song matches a blocked
 * word — the ignoreList is advanced on every attempt so the server never
 * returns the same blocked song twice.
 */
class AutoDjOrchestrator(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    /** Add a fetched track to the play queue. Must be safe to call from any
     *  coroutine — implementations should marshal to their own scope if needed. */
    private val addTrackToQueue: (EntryDto) -> Unit,
    /** Current track — used to seed BPM window, harmonic key, and similar-artist lookup. */
    private val getCurrentTrack: () -> EntryDto?,
    /** Available library vpaths for the source-folder filter. */
    private val getCachedVpaths: () -> List<String>,
) {

    /** Songs already returned this session — passed back to avoid repeats. */
    @Volatile
    private var ignoreList: List<Int> = emptyList()

    /** Ring buffer of recently played artist names for cooldown tracking. */
    private val recentlyPlayedArtists = ArrayDeque<String>()

    /** Cache of Last.fm similar-artist lookups (artist → list of similar artists). */
    private val similarArtistsCache = mutableMapOf<String, List<String>>()

    /** Track the artist of the last-played track for cooldown. Callers invoke on every
     *  onMediaItemTransition to keep the buffer honest across manual skips too. */
    fun onTrackChanged(track: EntryDto, cooldownSize: Int) {
        val artist = track.artist?.takeIf { it.isNotBlank() } ?: return
        recentlyPlayedArtists.remove(artist)
        recentlyPlayedArtists.addLast(artist)
        while (recentlyPlayedArtists.size > cooldownSize) recentlyPlayedArtists.removeFirst()
    }

    /** Drop all volatile state — called when the user toggles Auto-DJ off, so a
     *  later toggle-on doesn't inherit stale ignore/artist history. */
    fun reset() {
        ignoreList = emptyList()
        recentlyPlayedArtists.clear()
        similarArtistsCache.clear()
    }

    /**
     * Fire a single Auto-DJ fetch and enqueue the result. Runs in [scope]. Caller
     * decides when to invoke — typically on Player.STATE_ENDED, or on the queue
     * reaching its last item, or on the toggle-on transition if we're already
     * sitting at the last item.
     *
     * Silent no-op when the user isn't authenticated (no JWT) or when the
     * fallback random-song request itself fails.
     */
    fun fetchNext(scope: CoroutineScope) {
        scope.launch {
            val serverSettings = settingsRepo.settings.first()
            val token = serverSettings.jwtToken.ifEmpty { return@launch }
            val autoDj = settingsRepo.autoDjSettings.first()
            val cooldownArtists = recentlyPlayedArtists.toList().takeIf { it.isNotEmpty() }
            val current = getCurrentTrack()
            val velvet = VelvetRepository(VelvetClient.build(serverSettings.serverUrl))

            // 1. Similar artists (cached per artist)
            val similarArtists: List<String>? = if (autoDj.useSimilarArtists
                && !current?.artist.isNullOrBlank()
            ) {
                similarArtistsCache.getOrPut(current!!.artist!!) {
                    when (val r = velvet.getSimilarArtists(token, current.artist!!)) {
                        is Result.Success -> r.data
                        else              -> emptyList()
                    }
                }.takeIf { it.isNotEmpty() }
            } else null

            // 2. BPM ranges from current track
            val bpmTight: List<BpmRange>? = if (autoDj.useBpm && current?.bpm != null) {
                listOf(BpmRange(
                    current.bpm!! - autoDj.bpmTightRange,
                    current.bpm!! + autoDj.bpmTightRange,
                ))
            } else null
            val bpmWide: List<BpmRange>? = if (autoDj.useBpm && current?.bpm != null) {
                listOf(BpmRange(
                    current.bpm!! - autoDj.bpmWideRange,
                    current.bpm!! + autoDj.bpmWideRange,
                ))
            } else null

            // 3. Compatible Camelot keys
            val keys: List<String>? = if (autoDj.useHarmonicMixing
                && !current?.musicalKey.isNullOrBlank()
            ) {
                CamelotWheel.compatibleKeys(current!!.musicalKey!!)
            } else null

            // 4. Genre / rating filters
            val genreList: List<String>? = if (autoDj.genreMode != "off") autoDj.genres.takeIf { it.isNotEmpty() } else null
            val genreMode: String?        = if (autoDj.genreMode != "off") autoDj.genreMode else null
            val minRating: Int?           = if (autoDj.minRating > 0) autoDj.minRating else null

            // 5. Source folder filter — invert to "vpaths to exclude"
            val cachedVpaths = getCachedVpaths()
            val sourceFolders = autoDj.sourceFolders
            val ignoreVPaths: List<String>? = if (sourceFolders.isNotEmpty() && cachedVpaths.isNotEmpty()) {
                cachedVpaths.filter { it !in sourceFolders }.takeIf { it.isNotEmpty() }
            } else null

            val primaryRequest = VelvetRandomSongsRequest(
                ignoreList          = ignoreList,
                ignoreVPaths        = ignoreVPaths,
                bpmRanges           = bpmTight,
                bpmRangesWide       = bpmWide,
                requireBpm          = if (autoDj.requireBpm) true else null,
                musicalKeys         = keys,
                requireMusicalKey   = if (autoDj.requireKey) true else null,
                artists             = similarArtists,
                ignoreArtists       = cooldownArtists,
                genres              = genreList,
                genreMode           = genreMode,
                minRating           = minRating,
            )

            suspend fun fetchOnce(): EntryDto? {
                when (val r = velvet.fetchAutoDjSong(token, primaryRequest.copy(ignoreList = ignoreList))) {
                    is Result.Success -> {
                        ignoreList = r.data.second
                        return r.data.first
                    }
                    is Result.Error -> Unit
                }
                return when (val fallback = velvet.fetchAutoDjSong(
                    token,
                    VelvetRandomSongsRequest(ignoreList = ignoreList, ignoreVPaths = ignoreVPaths),
                )) {
                    is Result.Success -> {
                        ignoreList = fallback.data.second
                        fallback.data.first
                    }
                    is Result.Error -> null
                }
            }

            val filterWords = autoDj.keywordFilterWords
            val filterOn = autoDj.keywordFilterEnabled && filterWords.isNotEmpty()

            repeat(MAX_KEYWORD_ATTEMPTS) {
                val song = fetchOnce() ?: return@launch
                if (!filterOn || !KeywordFilter.isBlocked(song, filterWords)) {
                    addTrackToQueue(song)
                    return@launch
                }
                // Blocked — ignoreList already advanced, loop refetches a different song.
            }
        }
    }

    companion object {
        /** Cap Keyword Filter refetch attempts so a too-broad word can't loop forever. */
        private const val MAX_KEYWORD_ATTEMPTS = 10
    }
}
