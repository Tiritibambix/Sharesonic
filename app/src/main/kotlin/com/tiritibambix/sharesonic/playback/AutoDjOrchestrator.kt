package com.tiritibambix.sharesonic.playback

import android.content.Context
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.VelvetRepository
import com.tiritibambix.sharesonic.data.api.VelvetClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.VelvetRandomSongsRequest
import com.tiritibambix.sharesonic.data.settings.AutoDjSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.utils.CamelotWheel
import com.tiritibambix.sharesonic.utils.KeywordFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Shared Auto-DJ next-track engine. One instance lives in the [PlaybackService]
 * (drives Auto-DJ when the app UI is dead) and one in the
 * [com.tiritibambix.sharesonic.ui.player.PlayerViewModel] (drives it in-app).
 * Both read the same persistent flag ([SettingsRepository.autoDjEnabled]) and
 * tunables ([SettingsRepository.autoDjSettings]).
 *
 * ## Soft-scoring architecture (ported from Velvet v0.4.0 / v0.3.22)
 *
 * The old design sent hard filters (BPM window, Camelot keys, genre) to the
 * server and escalated through fallback tiers. This mirrors Velvet's redesign:
 * fetch a **broad candidate batch** (`returnAll = true`) scoped only by the true
 * hard filters — collections/paths, minimum rating, the similar-artist list,
 * the artist cooldown, and the track-length window — then **score every
 * candidate client-side** and queue the single best. BPM / genre / harmonic /
 * year / diversity are all soft "nice to have" signals that only move a
 * candidate's score; nothing but the hard filters and the client-side keyword
 * filter can exclude a song.
 *
 * Scoring weights (when Last.fm similar-artist data exists):
 *   similar-artist 35% · BPM 25% · genre 13% · harmonic 7% · year 10% · diversity 10%.
 * When there's no similar-artist data the 35% is redistributed proportionally
 * into BPM/genre/harmonic. A hard artist-repeat floor (last 3 played artists)
 * sits on top of the scoring as the only artist-based exclusion.
 *
 * Volatile session state (ignore list, artist history, anchors, similar cache)
 * is per-instance — losing it on an app kill is fine; the server re-explores.
 */
class AutoDjOrchestrator(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    /** Add a fetched track to the play queue. Must be safe to call from any
     *  coroutine — implementations marshal to their own scope if needed. */
    private val addTrackToQueue: (EntryDto) -> Unit,
    /** Current track — seeds BPM/key/year/genre references and similar-artist lookup. */
    private val getCurrentTrack: () -> EntryDto?,
    /** Available library vpaths for the source-folder filter. */
    private val getCachedVpaths: () -> List<String>,
) {

    /** Positional indices already returned this session — passed back to avoid repeats. */
    @Volatile
    private var ignoreList: List<Int> = emptyList()

    /** Recently-played artist names (manual + DJ), newest last. Powers the
     *  server cooldown list, the diversity score and the hard artist floor. */
    private val artistHistory = ArrayDeque<String>()

    /** Filepaths of recently enqueued DJ picks — avoids literal repeats. */
    private val recentPaths = ArrayDeque<String>()

    /** Rolling BPM/year history of DJ picks; the anchors are their averages. */
    private val bpmHistory = ArrayDeque<Int>()
    private val yearHistory = ArrayDeque<Int>()

    /** True when the last DJ pick had no BPM — its tempo must not seed the anchor blend. */
    @Volatile
    private var lastPickFree: Boolean = false

    /** Similar-artist cache for the current anchor artist. */
    private var similarFor: String? = null
    private var similarArtists: List<String> = emptyList()
    private var variantRankMap: Map<String, Int> = emptyMap()

    /**
     * Record a track becoming current (manual play or DJ advance) so the
     * cooldown / diversity / floor reflect what actually played. [cooldownSize]
     * is retained for signature compatibility; the server cooldown slice is
     * sized from [AutoDjSettings.artistCooldown] at fetch time.
     */
    fun onTrackChanged(track: EntryDto, cooldownSize: Int) {
        val artist = track.artist?.takeIf { it.isNotBlank() } ?: return
        pushArtistHistory(artist)
    }

    /** Drop all volatile state — called when Auto-DJ is toggled off. */
    fun reset() {
        ignoreList = emptyList()
        artistHistory.clear()
        recentPaths.clear()
        bpmHistory.clear()
        yearHistory.clear()
        lastPickFree = false
        similarFor = null
        similarArtists = emptyList()
        variantRankMap = emptyMap()
    }

    /**
     * Fetch one candidate batch, score it, and enqueue the best pick. Runs in
     * [scope]. Silent no-op when unauthenticated or when the batch is empty.
     */
    fun fetchNext(scope: CoroutineScope) {
        scope.launch {
            val serverSettings = settingsRepo.settings.first()
            val token = serverSettings.jwtToken.ifEmpty { return@launch }
            val autoDj = settingsRepo.autoDjSettings.first()
            val current = getCurrentTrack()
            val velvet = VelvetRepository(VelvetClient.build(serverSettings.serverUrl))

            // ── Similar artists (cached per anchor artist) ──────────────────────
            if (autoDj.useSimilarArtists && !current?.artist.isNullOrBlank()) {
                val artist = current!!.artist!!
                if (similarFor != artist) {
                    when (val r = velvet.getSimilarArtistsRanked(token, artist)) {
                        is Result.Success -> {
                            similarArtists = r.data.artists
                            variantRankMap = r.data.variantRankMap
                        }
                        is Result.Error -> {
                            similarArtists = emptyList()
                            variantRankMap = emptyMap()
                        }
                    }
                    similarFor = artist
                }
            } else {
                similarArtists = emptyList()
                variantRankMap = emptyMap()
            }

            // ── Hard scope only — NO bpm/key/genre (those are scored client-side) ─
            val cachedVpaths = getCachedVpaths()
            val ignoreVPaths: List<String>? = if (autoDj.sourceFolders.isNotEmpty() && cachedVpaths.isNotEmpty()) {
                cachedVpaths.filter { it !in autoDj.sourceFolders }.takeIf { it.isNotEmpty() }
            } else null
            val cooldown = artistHistory.toList().takeLast(autoDj.artistCooldown).takeIf { it.isNotEmpty() }

            // Track-length window (hard scope). Guard min > max — the server 400s
            // on a backwards window, which would stall Auto-DJ; drop the max then.
            val durOn = autoDj.durationFilterEnabled
            val minDur = if (durOn) autoDj.minDurationSec.takeIf { it > 0 } else null
            val maxDur = (if (durOn) autoDj.maxDurationSec.takeIf { it > 0 } else null)
                ?.takeUnless { minDur != null && it < minDur }
            val allowUnknown = if (durOn && (minDur != null || maxDur != null)) autoDj.allowUnknownDuration else null

            val request = VelvetRandomSongsRequest(
                ignoreList    = ignoreList,
                ignoreVPaths  = ignoreVPaths,
                artists       = similarArtists.takeIf { it.isNotEmpty() },
                ignoreArtists = cooldown,
                // UI stars are 0..5; the server's minRating is the native 0..10
                // half-star scale (like rate-song), so send stars * 2.
                minRating     = autoDj.minRating.takeIf { it > 0 }?.let { it * 2 },
                minDuration   = minDur,
                maxDuration   = maxDur,
                allowUnknownDuration = allowUnknown,
            )

            val batch = when (val r = velvet.fetchAutoDjBatch(token, request)) {
                is Result.Success -> { ignoreList = r.data.second; r.data.first }
                is Result.Error   -> return@launch
            }

            val pick = pickBest(batch, current, autoDj) ?: return@launch

            // Update anchors + histories from the actual pick before enqueueing.
            lastPickFree = pick.bpm == null
            pick.bpm?.let { pushBpmHistory(it.roundToInt()) }
            pick.year?.let { pushYearHistory(it) }
            pick.id.takeIf { it.isNotBlank() }?.let { rememberPath(it) }
            pick.artist?.takeIf { it.isNotBlank() }?.let { pushArtistHistory(it) }

            addTrackToQueue(pick)
        }
    }

    // ── Candidate selection ─────────────────────────────────────────────────────

    private fun pickBest(batch: List<EntryDto>, current: EntryDto?, autoDj: AutoDjSettings): EntryDto? {
        val filterWords = autoDj.keywordFilterWords
        val keywordOn = autoDj.keywordFilterEnabled && filterWords.isNotEmpty()

        var candidates = batch.filterNot { keywordOn && KeywordFilter.isBlocked(it, filterWords) }
        if (candidates.isEmpty()) return null

        // Prefer candidates not played very recently, but never empty the pool.
        val recent = recentPaths.toSet()
        candidates.filterNot { it.id in recent }.takeIf { it.isNotEmpty() }?.let { candidates = it }

        // Hard artist-repeat floor: never the last HARD_FLOOR played artists,
        // unless honouring it would empty the pool.
        val floor = artistHistory.toList().takeLast(HARD_FLOOR).map { it.trim().lowercase() }.toSet()
        if (floor.isNotEmpty()) {
            candidates.filterNot { (it.artist ?: "").trim().lowercase() in floor }
                .takeIf { it.isNotEmpty() }?.let { candidates = it }
        }

        // Precompute the current-track references once for the whole batch.
        val ref = ScoringRef.from(current, bpmAnchor(), yearAnchor(), lastPickFree)

        var best: EntryDto? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            val s = scoreSong(c, ref, autoDj)
            if (s > bestScore || (s == bestScore && Random.nextBoolean())) {
                bestScore = s; best = c
            }
        }
        return best
    }

    /** Immutable per-fetch reference values derived from the current track. */
    private class ScoringRef(
        val bpmBlend: Int?,
        val yearBlend: Int?,
        val camelot: String?,
        val neighbours: Set<String>,
        val genreRaw: String?,
    ) {
        companion object {
            fun from(current: EntryDto?, bpmAnchor: Int?, yearAnchor: Int?, lastPickFree: Boolean): ScoringRef {
                val curBpm = current?.bpm?.roundToInt()
                val bpmBlend = when {
                    curBpm != null && bpmAnchor != null && !lastPickFree ->
                        (0.6 * curBpm + 0.4 * bpmAnchor).roundToInt()
                    else -> curBpm ?: bpmAnchor
                }
                val curYear = current?.year
                val yearBlend = when {
                    curYear != null && yearAnchor != null -> (0.6 * curYear + 0.4 * yearAnchor).roundToInt()
                    else -> curYear ?: yearAnchor
                }
                val camelot = CamelotWheel.toCamelot(current?.musicalKey)
                return ScoringRef(
                    bpmBlend = bpmBlend,
                    yearBlend = yearBlend,
                    camelot = camelot,
                    neighbours = CamelotWheel.neighbours(camelot),
                    genreRaw = current?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                )
            }
        }
    }

    /**
     * Score a candidate in 0..~1 (higher = better). Faithful port of the Velvet
     * web player's `_djScoreSong`. Every term is additive; nothing here excludes.
     */
    private fun scoreSong(song: EntryDto, ref: ScoringRef, autoDj: AutoDjSettings): Double {
        val hasSimilar = autoDj.useSimilarArtists && similarArtists.isNotEmpty()
        var wArtist = 0.35; var wBpm = 0.25; var wGenre = 0.13; var wHarmonic = 0.07
        if (!hasSimilar) {
            val otherTotal = wBpm + wGenre + wHarmonic
            wBpm += wArtist * (wBpm / otherTotal)
            wGenre += wArtist * (wGenre / otherTotal)
            wHarmonic += wArtist * (wHarmonic / otherTotal)
            wArtist = 0.0
        }

        var score = 0.0

        // ── Harmonic — current-track key, not a locked anchor ──────────────────
        if (!autoDj.useHarmonicMixing) {
            score += wHarmonic * 0.4
        } else {
            val cand = CamelotWheel.toCamelot(song.musicalKey)
            score += wHarmonic * when {
                cand == null -> 0.4
                cand == ref.camelot -> 1.0
                ref.neighbours.contains(cand) -> 0.7
                CamelotWheel.neighbours(cand).contains(ref.camelot) -> 0.3
                else -> 0.0
            }
        }

        // ── Genre — user whitelist/blacklist blended with continuity matrix ────
        val candGenreRaw = song.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val filterScore = genreFilterScore(candGenreRaw, autoDj)
        val contScore = genreCompatScore(ref.genreRaw, candGenreRaw)
        score += wGenre * (if (filterScore != null) 0.6 * filterScore + 0.4 * contScore else contScore)

        // ── Similar-artist / Last.fm rank ──────────────────────────────────────
        if (wArtist > 0.0) {
            score += wArtist * if (song.artist != null && variantRankMap.isNotEmpty()) {
                val rank = variantRankMap[song.artist]
                if (rank != null) max(0.18, 1.0 - (rank - 1) / 60.0) else 0.0
            } else 0.3
        }

        // ── BPM proximity — blended reference (60% current + 40% anchor) ───────
        if (!autoDj.useBpm) {
            score += wBpm * 0.5
        } else {
            val candBpm = song.bpm?.roundToInt()
            val blend = ref.bpmBlend
            if (candBpm != null && blend != null) {
                val tol = autoDj.bpmTightRange.coerceAtLeast(1)
                val diff = abs(candBpm - blend)
                val bpmScore = when {
                    diff <= tol -> 1.0 - (diff.toDouble() / tol) * 0.3
                    diff <= tol * 2 -> 0.3 - ((diff - tol).toDouble() / tol) * 0.3
                    else -> 0.0
                }
                score += wBpm * bpmScore
            } else {
                score += wBpm * 0.5
            }
        }

        // ── Year/era proximity (10%, fixed) — blended reference ────────────────
        val candYear = song.year
        val yearBlend = ref.yearBlend
        if (candYear != null && yearBlend != null) {
            val tol = 12
            val diff = abs(candYear - yearBlend)
            val yearScore = when {
                diff <= tol -> 1.0 - (diff.toDouble() / tol) * 0.3
                diff <= tol * 2 -> 0.3 - ((diff - tol).toDouble() / tol) * 0.3
                else -> 0.0
            }
            score += 0.10 * yearScore
        } else {
            score += 0.10 * 0.5
        }

        // ── Artist diversity (10%, fixed) ──────────────────────────────────────
        val recent = artistHistory.toList().takeLast(DIVERSITY_WINDOW)
        val aNorm = (song.artist ?: "").trim().lowercase()
        val aidx = recent.indexOfLast { it.trim().lowercase() == aNorm }
        score += 0.10 * when {
            aidx == -1 -> 1.0
            aidx >= recent.size - 5 -> 0.0
            aidx >= recent.size - 10 -> 0.4
            else -> 0.7
        }

        return score
    }

    /**
     * Explicit user genre whitelist/blacklist as a soft 0..1 score (never a hard
     * block). Null when no filter is configured — caller falls back to continuity.
     */
    private fun genreFilterScore(candGenreRaw: String?, autoDj: AutoDjSettings): Double? {
        if (autoDj.genreMode == "off" || autoDj.genres.isEmpty()) return null
        val raw = (candGenreRaw ?: "").lowercase()
        val inList = autoDj.genres.any { g ->
            val lc = g.lowercase()
            raw == lc || raw.startsWith("$lc,") || raw.endsWith(", $lc") || raw.contains(", $lc,")
        }
        return if (autoDj.genreMode == "whitelist") {
            if (raw.isNotEmpty()) (if (inList) 1.0 else 0.0) else 0.5
        } else {
            if (raw.isNotEmpty()) (if (inList) 0.0 else 1.0) else 1.0 // blacklist: untagged pass
        }
    }

    // ── Anchors / history helpers ────────────────────────────────────────────────

    private fun bpmAnchor(): Int? = bpmHistory.takeIf { it.isNotEmpty() }?.let { (it.sum().toDouble() / it.size).roundToInt() }
    private fun yearAnchor(): Int? = yearHistory.takeIf { it.isNotEmpty() }?.let { (it.sum().toDouble() / it.size).roundToInt() }

    private fun pushBpmHistory(bpm: Int) { bpmHistory.addLast(bpm); while (bpmHistory.size > ANCHOR_WINDOW) bpmHistory.removeFirst() }
    private fun pushYearHistory(year: Int) { yearHistory.addLast(year); while (yearHistory.size > ANCHOR_WINDOW) yearHistory.removeFirst() }

    private fun rememberPath(path: String) { recentPaths.addLast(path); while (recentPaths.size > RECENT_PATHS) recentPaths.removeFirst() }

    private fun pushArtistHistory(artist: String) {
        val norm = artist.trim().lowercase().replace(".", "")
        // Dedup (move-to-end): drop any existing occurrence, then append.
        artistHistory.removeAll { it.trim().lowercase().replace(".", "") == norm }
        artistHistory.addLast(artist.trim())
        while (artistHistory.size > ARTIST_HISTORY_CAP) artistHistory.removeFirst()
    }

    companion object {
        /** Rolling window for the BPM/year anchor averages (webapp: 8). */
        private const val ANCHOR_WINDOW = 8
        /** Diversity scoring window (webapp DJ_ARTIST_COOLDOWN). */
        private const val DIVERSITY_WINDOW = 15
        /** Never repeat the same artist within this many picks (webapp DJ_ARTIST_HARD_FLOOR). */
        private const val HARD_FLOOR = 3
        /** Cap on the retained artist history. */
        private const val ARTIST_HISTORY_CAP = 50
        /** Literal-repeat dedup window. */
        private const val RECENT_PATHS = 30

        /**
         * Genre-compatibility continuity score (0..1) — ported from the Velvet
         * web player's `_djGenreCompatScore`. Same genre 1.0, same category 0.9,
         * cross-category from a curated matrix, missing data neutral.
         */
        fun genreCompatScore(curGenre: String?, candGenre: String?): Double {
            if (curGenre.isNullOrBlank() || candGenre.isNullOrBlank()) return 0.6
            val lc1 = curGenre.lowercase(); val lc2 = candGenre.lowercase()
            if (lc1 == lc2) return 1.0
            val c1 = genreCategory(lc1); val c2 = genreCategory(lc2)
            if (c1 == c2) return 0.9
            return GENRE_MATRIX["$c1→$c2"] ?: 0.5
        }

        private fun genreCategory(g: String): String = when {
            g.contains("ambient") || g.contains("chillout") || g.contains("downtempo") || g.contains("new age") -> "ambient"
            g.contains("disco") || g.contains("funk") || g.contains("soul") || g.contains("motown") -> "disco"
            g.contains("electronic") || g.contains("techno") || g.contains("house") ||
                g.contains("trance") || g.contains("dance") || g.contains("edm") ||
                g.contains("synth") || g.contains("electro") -> "electronic"
            g.contains("hardcore") || g.contains("hardstyle") || g.contains("gabber") -> "hard"
            g.contains("pop") || g.contains("rock") || g.contains("indie") ||
                g.contains("alternative") || g.contains("country") || g.contains("folk") ||
                g.contains("r&b") || g.contains("rnb") || g.contains("jazz") -> "soft"
            else -> "other"
        }

        private val GENRE_MATRIX: Map<String, Double> = mapOf(
            "electronic→disco" to 0.7, "disco→electronic" to 0.7,
            "electronic→ambient" to 0.35, "ambient→electronic" to 0.35,
            "disco→ambient" to 0.15, "ambient→disco" to 0.15,
            "electronic→soft" to 0.25, "soft→electronic" to 0.25,
            "disco→soft" to 0.45, "soft→disco" to 0.45,
            "ambient→soft" to 0.25, "soft→ambient" to 0.25,
            "electronic→hard" to 0.05, "hard→electronic" to 0.05,
            "disco→hard" to 0.05, "hard→disco" to 0.05,
            "ambient→hard" to 0.0, "hard→ambient" to 0.0,
            "soft→hard" to 0.15, "hard→soft" to 0.15,
        )
    }
}
