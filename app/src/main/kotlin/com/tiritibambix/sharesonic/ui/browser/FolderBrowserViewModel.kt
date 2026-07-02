package com.tiritibambix.sharesonic.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tiritibambix.sharesonic.data.MStreamRepository
import com.tiritibambix.sharesonic.data.Result
import com.tiritibambix.sharesonic.data.SubsonicRepository
import com.tiritibambix.sharesonic.data.api.MStreamClient
import com.tiritibambix.sharesonic.data.api.SubsonicClient
import com.tiritibambix.sharesonic.data.api.models.EntryDto
import com.tiritibambix.sharesonic.data.api.models.NativePlaylist
import com.tiritibambix.sharesonic.data.settings.ServerSettings
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.ui.navigation.Screen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface BrowserState {
    data object Loading : BrowserState
    data class Ready(val entries: List<EntryDto>) : BrowserState
    data class Error(val message: String) : BrowserState
}

sealed interface ShareState {
    data object Idle : ShareState
    data object Loading : ShareState
    data class Done(val url: String) : ShareState
    data class Error(val message: String) : ShareState
}

/**
 * Browsing model:
 *
 *   folderId == "root"  → POST /api/v1/file-explorer  { directory: "" }
 *                         shows top-level directories
 *   folderId == path    → POST /api/v1/file-explorer  { directory: path, pullMetadata: true }
 *                         shows subdirectories + audio files with native filepaths
 *
 * Directory entry.id  = mStream path     (for navigation → next file-explorer call)
 * File entry.id       = mStream filepath (for /media/<filepath>?token= streaming)
 *                     OR numeric string  (Subsonic integer ID from search3 results only)
 */
class FolderBrowserViewModel(
    private val settingsRepo: SettingsRepository,
    val folderId: String   // already decoded (Base64 path decoded in AppNavGraph)
) : ViewModel() {

    private val _state = MutableStateFlow<BrowserState>(BrowserState.Loading)
    val state: StateFlow<BrowserState> = _state

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState

    private var _settings: ServerSettings? = null

    /**
     * Build a cover art URL.
     * - Subsonic IDs ("al-N", "ar-N", numeric) → Subsonic getCoverArt endpoint
     * - mStream album-art filenames → native /album-art/<file>?token=<jwt>
     */
    fun coverArtUrl(coverArtId: String, size: Int = 64): String? {
        val s = _settings ?: return null
        return if (coverArtId.startsWith("al-") || coverArtId.startsWith("ar-") || coverArtId.all { it.isDigit() }) {
            SubsonicClient.coverArtUrl(s, coverArtId, size)
        } else {
            val base = s.serverUrl.trimEnd('/')
            "$base/album-art/$coverArtId?token=${s.jwtToken}"
        }
    }

    init {
        load()
        // Fire-and-forget: delete expired native share links on each browser open
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = ensureToken(settings) ?: return@launch
            MStreamRepository(MStreamClient.build(settings.serverUrl)).cleanupExpiredShares(token)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { BrowserState.Loading }
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) {
                _state.update { BrowserState.Error("Server not configured") }
                return@launch
            }
            _settings = settings

            val token = ensureToken(settings) ?: run {
                _state.update { BrowserState.Error("Authentication failed — open Settings to reconnect") }
                return@launch
            }

            val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
            val path = if (folderId == Screen.Browser.ROOT) "" else folderId
            // pullMetadata=true only for non-root so files get filepaths for native streaming
            val pullMeta = path.isNotEmpty()

            when (val r = mStream.fileExplorer(token, path, pullMeta)) {
                is Result.Success -> {
                    val entries = mStream.toEntries(r.data, path)
                    _state.update { BrowserState.Ready(entries) }
                }
                is Result.Error -> _state.update { BrowserState.Error(r.message) }
            }
        }
    }

    fun refresh() = load()

    // ── Leaf folder cover art ────────────────────────────────────────────────

    /**
     * Album-art filename per leaf-folder path (or null if the folder isn't a leaf,
     * or has no embedded art). Populated lazily by [loadFolderArt] as rows appear,
     * and cached for the lifetime of this ViewModel to avoid repeat lookups.
     */
    private val _folderArt = MutableStateFlow<Map<String, String?>>(emptyMap())
    val folderArt: StateFlow<Map<String, String?>> = _folderArt

    private val folderArtInFlight = mutableSetOf<String>()

    /** Caps concurrent /file-explorer lookups triggered by scrolling the browser. */
    private val folderArtSemaphore = Semaphore(4)

    /**
     * Resolve and cache the leaf-folder cover art for [path], if not already known.
     * Safe to call repeatedly (e.g. from a per-row LaunchedEffect) — no-ops once
     * cached or while a lookup for [path] is already in flight.
     */
    fun loadFolderArt(path: String) {
        if (_folderArt.value.containsKey(path) || !folderArtInFlight.add(path)) return
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val token = ensureToken(settings)
            val art = if (token != null) {
                val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
                folderArtSemaphore.withPermit { mStream.leafFolderArt(token, path) }
            } else {
                null
            }
            _folderArt.update { it + (path to art) }
            folderArtInFlight.remove(path)
        }
    }

    // ── Shuffle ───────────────────────────────────────────────────────────────

    fun shuffleCurrent(
        onReady: (List<EntryDto>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (folderId == Screen.Browser.ROOT) {
                // Shuffle whole library via native Velvet random-songs endpoint
                val token = ensureToken(settings) ?: run { onError("Authentication failed"); return@launch }
                val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
                when (val r = mStream.getRandomSongs(token, count = 30)) {
                    is Result.Success -> {
                        if (r.data.isEmpty()) onError("No songs found")
                        else onReady(r.data.shuffled())
                    }
                    is Result.Error -> onError(r.message)
                }
            } else {
                // Collect audio files under this folder via server-side requests
                // (recursive scan + batch metadata) instead of a recursive client-side
                // walk — scales to very large folders that used to hang forever.
                // Long-timeout client: the server-side recursive walk of a huge folder
                // can take well over the normal 60 s read timeout.
                val token = ensureToken(settings) ?: run { onError("Authentication failed"); return@launch }
                val mStream = MStreamRepository(MStreamClient.buildLongTimeout(settings.serverUrl))
                val songs = mStream.collectSongsFast(token, folderId)
                if (songs.isEmpty()) onError("No songs found")
                else onReady(songs.shuffled())
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    /**
     * @param expiryDays Number of days until the link expires (from the share dialog's
     *                   "days until expiration" field); null → permanent link.
     */
    fun shareEntry(entry: EntryDto, expiryDays: Int? = null) {
        _shareState.update { ShareState.Loading }
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            when {
                entry.isDir -> {
                    // Folder → collect all tracks, share as a single playlist
                    val jwt = ensureToken(settings) ?: run {
                        _shareState.update { ShareState.Error("Authentication failed") }
                        return@launch
                    }
                    val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
                    when (val r = mStream.shareFolder(jwt, entry.id, expiryDays)) {
                        is Result.Success -> {
                            val url = settings.serverUrl.trimEnd('/') + "/shared/${r.data}"
                            _shareState.update { ShareState.Done(url) }
                        }
                        is Result.Error -> _shareState.update { ShareState.Error(r.message) }
                    }
                }
                entry.id.all { it.isDigit() } -> {
                    // Song from Subsonic search3 — has an integer ID, share via Subsonic createShare
                    val subsonicRepo = SubsonicRepository(
                        SubsonicClient.build(settings.serverUrl, settings.username, settings.password)
                    )
                    when (val r = subsonicRepo.createShare(entry.id, expiryDays)) {
                        is Result.Success -> _shareState.update { ShareState.Done(r.data.url) }
                        is Result.Error   -> _shareState.update { ShareState.Error(r.message) }
                    }
                }
                else -> {
                    // mStream native song — use filepath with native share endpoint
                    val jwt = ensureToken(settings) ?: run {
                        _shareState.update { ShareState.Error("Authentication failed") }
                        return@launch
                    }
                    val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
                    when (val r = mStream.share(jwt, entry.id, expiryDays)) {
                        is Result.Success -> {
                            val url = settings.serverUrl.trimEnd('/') + "/shared/${r.data}"
                            _shareState.update { ShareState.Done(url) }
                        }
                        is Result.Error -> _shareState.update { ShareState.Error(r.message) }
                    }
                }
            }
        }
    }

    fun clearShareState() = _shareState.update { ShareState.Idle }

    // ── Add to playlist ───────────────────────────────────────────────────────

    /** Cached playlist list for the swipe-to-add picker. Loaded on first swipe. */
    private val _playlists = MutableStateFlow<List<NativePlaylist>>(emptyList())
    val playlists: StateFlow<List<NativePlaylist>> = _playlists

    /**
     * Load the user's playlists into [playlists] for the picker dialog.
     * No-op if already loaded; caller can force a refresh by passing [forceRefresh].
     */
    fun loadPlaylists(forceRefresh: Boolean = false) {
        if (!forceRefresh && _playlists.value.isNotEmpty()) return
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = ensureToken(settings) ?: return@launch
            val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
            when (val r = mStream.getPlaylists(token)) {
                is Result.Error -> {}  // silently ignored — picker shows empty list
                is Result.Success -> {
                    _playlists.update { r.data }
                    // mStream's getall songCount is denormalized and not updated by add-song.
                    // Refresh each playlist's real count in parallel.
                    val refreshed = r.data.map { playlist ->
                        async {
                            val loaded = mStream.loadPlaylist(token, playlist.name)
                            if (loaded is Result.Success)
                                playlist.copy(songCount = loaded.data.size)
                            else
                                playlist
                        }
                    }.awaitAll()
                    _playlists.update { refreshed }
                }
            }
        }
    }

    /** Append [filepath] to the named playlist. Fire-and-forget. */
    fun addToPlaylist(filepath: String, playlistName: String) {
        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.isConfigured) return@launch
            val token = ensureToken(settings) ?: return@launch
            MStreamRepository(MStreamClient.build(settings.serverUrl))
                .addSongToPlaylist(token, filepath, playlistName)
        }
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    /**
     * Return the stored JWT if valid, or attempt a fresh login.
     * Persists the new token to DataStore on success.
     */
    private suspend fun ensureToken(settings: ServerSettings): String? {
        if (settings.jwtToken.isNotEmpty()) return settings.jwtToken
        val mStream = MStreamRepository(MStreamClient.build(settings.serverUrl))
        val result = mStream.login(settings.username, settings.password)
        if (result is Result.Success) {
            settingsRepo.saveToken(result.data)
            return result.data
        }
        return null
    }
}

class FolderBrowserViewModelFactory(
    private val repo: SettingsRepository,
    private val folderId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FolderBrowserViewModel(repo, folderId) as T
    }
}
