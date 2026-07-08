# Sharesonic

## Project Overview

Sharesonic is an Android music client for **Velvet** (https://github.com/aroundmyroom/Velvet).

The goal is an app that does a handful of things well:

* Browse music by real filesystem folder structure via Velvet's native API
* Shuffle play on a folder or the entire library
* Auto-DJ: continuous smart queue with BPM continuity, harmonic mixing (Camelot wheel), similar artists (Last.fm), artist cooldown, genre filter, crossfade
* Generate a public share link for any track — or for the entire current queue in one tap
* Rate tracks 0–5 stars from Now Playing, with an explicit way to clear back to "unrated"
* Manage playlists (create, rename, delete; add/remove tracks; play all / shuffle)
* Persistent mini player during navigation
* Sleep timer (pause after a chosen duration)
* Lyrics viewer for the current track
* Native Android per-band equalizer
* Hamburger navigation drawer (Server, Auto-DJ, Equalizer, Themes, Language, Public Links) reachable from anywhere, with Search and Playlists always pinned in the top bar
* 12-language UI (en, nl, de, fr, es, it, pt, pl, ru, zh, ja, ko) with an in-app picker + a "System default" row

## Core Requirements

### Must Have

* Folder-based browsing as the primary navigation mode using Velvet's native `/api/v1/file-explorer` endpoint (not the Subsonic API — Subsonic on Velvet returns tag-based artist/album views, not real folders)
* Shuffle play on any folder or on the full library
* Auto-DJ: when enabled, the queue never stops — the app continuously fetches the next track using BPM continuity, harmonic key (Camelot wheel), similar artists via Last.fm, artist cooldown, genre filter, and optional crossfade
* Share link generation: tap a track → generate a `server/shared/XXXXXXXXXX` public URL via the Velvet native share API → Android share sheet opens with that URL ready to send
* Share queue: from the queue view, generate a single public link covering every track currently queued (same native share endpoint, fed the queue's filepaths); Subsonic-search-origin tracks are skipped since they aren't shareable through it
* Star ratings: rate the current track 0–5 stars from Now Playing (synced to Velvet's native 0–10 half-star scale); tapping the active star — or an explicit always-visible clear button — resets it back to "unrated", mirroring the Auto-DJ "minimum rating" picker's affordance
* Settings reachable via a hamburger navigation drawer containing **Server**, **Auto-DJ**, **Equalizer**, **Themes**, **Language** and **Public Links**; the hamburger glyph stays in the top-left of every drawer destination so users can tap back out; Search and Playlists icons remain pinned top-right in the Folder Browser at all times
* Language picker (drawer + Settings hub) covering 12 UI languages (matches Velvet's set: en/nl/de/fr/es/it/pt/pl/ru/zh/ja/ko) plus "System default" — the selected BCP-47 tag is persisted in DataStore and applied by `MainActivity.attachBaseContext` (Configuration.setLocale + createConfigurationContext) so every `stringResource` resolves through the wrapped context; changing the language triggers `activity.recreate()`. Playback (PlaybackService) is unaffected — its media notification follows the system locale as expected. Auto-DJ, ViewModel/data-layer error messages and other strings outside `@Composable` context are still English (a known follow-up)
* Sleep timer, lyrics and full track-info dialog reachable from the Now Playing "⋮ More" bottom sheet
* Equalizer: native Android per-band effect attached to the ExoPlayer audio session, with persistent on/off + per-band gains
* In-app crash reporter: any uncaught exception is logged and shown in a copyable dialog on the next launch — no adb needed to diagnose crashes
* Playlist management: create, rename, delete playlists; add/remove tracks; play all / shuffle; add from browser (swipe right opens a frosted-glass picker with inline "New playlist" creation) or from Now Playing
* Persistent mini player bar during navigation: thumbnail, title/artist, skip/play-pause controls, live progress strip; folds away on the Now Playing screen
* Tapping the playback media-style notification brings the app to the foreground straight into Now Playing
* Queue management: swipe left in browser to add to queue; swipe to remove from queue in the queue view
* Offline-safe: graceful error handling when the server is unreachable

### Must Not Have

* Video support
* Podcast support
* Tag-based browsing as primary mode
* Any telemetry or analytics

## Design

### Visual Direction

* Jetpack Compose UI
* Material You (Material 3)
* 5 built-in themes mirroring Velvet's CSS: **Velvet** (default deep purple), **Dark**, **Light**, **High-Contrast (AAA)** (pure black bg, white/yellow), **Colourblind-safe** (blue/orange, no red/green reliance)
* Runtime accent-color override: preset swatches + HSV picker (`AccentColorSheet`), persisted in DataStore, applied via `ColorScheme.withAccent()` (`AccentOverride.kt`)
* 4-tier text ink hierarchy (`TextInks.kt`): `textPrimary`, `textSecondary`, `textTertiary`, `textDim` + 2 border tiers (`borderSoft`, `borderStrong`) — provided per-theme via `CompositionLocal`
* OKLCH perceptual ambient halo on Now Playing: dominant/vibrant colour extracted from artwork pixels (`AmbientEngine.kt`), rendered as a `Brush.radialGradient` with WCAG contrast floor
* Floating firefly particles behind Now Playing content (`FloatingParticles.kt`): Lissajous drift, sinusoidal twinkle, tinted from the ambient seed (falls back to theme primary on grayscale art)
* Blur-based glow on the played portion of the waveform seek bar and radial glow behind the play/pause button
* Compact TopAppBar (40 dp `expandedHeight`) on all screens: `IconButton` touch targets reduced to 36 dp, icon glyphs to 20 dp, title uses `titleSmall`. FolderBrowser and settings screens use semi-transparent surface (85 %/95 % alpha); Now Playing uses translucent surface (35 %/50 % alpha) so the ambient gradient bleeds through behind the bar
* Frosted-glass effects: navigation drawer, track-info dialog, playlist picker, long-press context menu and the share-expiry prompt all blur the content behind them (`Modifier.blur`, API 31+) with a dark scrim + `Surface` card (rounded 20 dp, primary-tinted border). The reusable pieces live in `ui/components/FrostedModals.kt` (`FrostedOverlay`, `FrostedCard`, `FrostedPlaylistPicker`, `FrostedShareExpiryDialog`) and are shared by the Folder Browser **and** Now Playing so both screens' share / add-to-playlist modals behave and look identical (including inline "New playlist" creation)
* Clean and content-focused: the folder tree and the player are the stars, not the UI chrome
* No bottom navigation clutter — keep it simple

### Key Screens

1. **Navigation drawer** — hamburger menu opening a frosted-glass sheet sized to ~80% of the screen width (`Modifier.width(maxWidth * 0.8f)` + `Modifier.blur()` on the content behind it via `animateDpAsState`), so a blurred sliver of the browser stays visible and tappable on the right — making the swipe-to-dismiss gesture obvious without anyone having to discover it. Lists **Server**, **Auto-DJ**, **Equalizer**, **Themes**, **Public Links**; each destination keeps the hamburger glyph as its `navigationIcon` so tapping it again exits straight back out
2. **Server / Auto-DJ / Equalizer / Themes / Public Links settings** — each is its own screen reachable only via the drawer; Server holds the Velvet URL, username, password and connection test. The Equalizer screen exposes an on/off toggle, per-band gain sliders and a "reset to flat" action (see [Equalizer](#equalizer) below). The Themes screen (`ThemeSettingsScreen.kt`) shows 5 tappable theme rows (colour-dot previews + radio buttons) plus an accent-color row that opens `AccentColorSheet` (6 presets + HSV picker)
3. **Folder Browser** — real filesystem tree via native API; swipe right → add to playlist (frosted-glass picker with inline "New playlist" create + existing playlists), swipe left → add to queue; long press → frosted-glass context menu (Material icons, left-aligned labels: Play, Add to queue, Add to playlist, Shuffle (folders), Share); alphabetical letter strip; Search and Playlists icons are pinned in the top-right of the `TopAppBar` at all times (not folded into the drawer); Home icon in the top bar jumps back to the library root in one tap (pops the whole browser stack). Play/Shuffle FABs use explicit `containerColor = primary` to stay vivid when an accent override is active
4. **Now Playing** — non-scrolling full-screen player reached by dragging up the mini player (`PlayerPanel.kt`, `Animatable<Float>` 0..1, replaces the old `Screen.NowPlaying` route): an OKLCH radial ambient halo seeded from the artwork's vibrant pixels (`AmbientEngine.kt` + `AmbientColor.kt`, Crossfade 700 ms), floating firefly particles (`FloatingParticles.kt`, 22 dots, Lissajous drift), the cover art centred in a flexible (`weight(1f)`) area sized by `BoxWithConstraints` so the layout fits any screen without ever scrolling, title/artist/album, format/bitrate + star-rating row, generously spaced playback controls with a radial primary-colour glow behind the play/pause button (`drawBehind` + `Brush.radialGradient`), a 112-bar waveform-style seek bar (`WaveformSeekBar`, Canvas of vertical bars seeded deterministically per-track by `EntryDto.id`; tap or drag to seek) with a blur-based glow on the played portion (sibling Canvas drawing the same bars at alpha 0.85 + `Modifier.blur(8.dp)`), a Share / Playlist action row (both open the shared frosted-glass modals — `FrostedShareExpiryDialog` and `FrostedPlaylistPicker` with inline "New playlist" create — hoisted to `NowPlayingScreen` so the whole Scaffold blurs behind them), and a "⋮ More" button opening a Material 3 `ModalBottomSheet` (`MoreActionsSheet`) with entries for **Sleep timer**, **Lyrics** and **Track info** (track info opens over a frosted-glass backdrop). Translucent TopAppBar (surface @ 35 % alpha) lets the ambient gradient bleed through; on the **Queue** page it fades to a near-opaque surface (92 % alpha) once the list is scrolled, so rows passing under the bar stay readable. Swipe left for queue view
5. **Queue** — scrollable queue, tap to jump, swipe left to remove; a top-bar share icon (visible only here) generates one public link for the whole queue. The shared queue `LazyListState` is hoisted to `NowPlayingScreen` so the top bar can react to its scroll position (see item 4)
6. **Mini player / Player panel** — `PlayerPanel.kt` is a unified overlay drawn on top of the nav host: at `t=0` it shows a persistent mini bar (thumbnail, title/artist, skip/play-pause, Auto-DJ toggle, live progress strip); dragging up (or tapping) expands it to the full Now Playing screen at `t=1`. System Back collapses when expanded. The mini bar fades out by `t=0.4`, the full sheet fades in from `t=0.1`. Snap threshold 50%, velocity 300 dp/s. No grab handle pill — the drag gesture is on the whole sheet and the back arrow handles dismissal
7. **Playlists** — list of playlists with real track counts; create / rename / delete
8. **Playlist detail** — track list; two regular `FloatingActionButton`s (Play All + Shuffle, matching FolderBrowser's FABs); swipe to remove; add songs via search dialog
9. **Search** — pill-shaped Material You search field living in the screen body (not the `TopAppBar` title slot, which clipped it); auto-focuses on entry. Results (from the native `/api/v1/db/search`) are grouped into four sections, mirroring the Velvet webapp: **Folders** (real on-disk folders whose name matched — tapping navigates straight to the server-provided `browse_path`, no path guessing), **Artists**, **Albums** and **Songs**. Tapping an **Artist** opens a track list of that artist's own songs (via `artist-folder-songs`, matched on the exact tag incl. featuring/variant spellings) — like the webapp's artist profile — rather than trying to resolve the artist to a single folder. Tapping an **Album** navigates to its folder; tapping a **Song** plays it
10. **Share confirmation** — shows the generated link with a copy + send button
11. **Track info dialog** — opened from the ⋮ More sheet, rendered as a solid `Surface` (rounded 20 dp, theme `surface` colour, primary-tinted border) over a frosted-glass backdrop (`Modifier.blur(18.dp)` on the Scaffold when open, dark scrim between). Key/value metadata grid (title, artist, album, year, track, genres, BPM, key, duration, format, live bitrate, sample rate, channels, rating) with the full file path in a monospace card — selectable — at the bottom. Missing fields (typical of songs queued from search) are fetched fresh from `/api/v1/db/metadata` when the dialog opens
12. **Sleep timer sheet** — opened from the ⋮ More sheet. Presets 15/30/45/60/90 min + custom, "cancel timer" when active. Hooked into the existing 500 ms position-polling loop in `PlayerViewModel`; the remaining time is exposed in `PlayerState.sleepRemainingMs` and the ⋮ icon is tinted with the accent colour while a timer is armed
13. **Lyrics sheet** — opened from the ⋮ More sheet. Fetches lyrics for the current track via `/api/v1/lyrics` (server already parses synced/plain lines — no client-side LRC stripping). Loading / lines / "No lyrics found" / error states

## Technical Stack

* Language: Kotlin
* UI: Jetpack Compose + Material 3
* Architecture: MVVM with ViewModel + StateFlow
* Network: Retrofit + OkHttp (Velvet native API for everything, **including search** via `/api/v1/db/search`). The Subsonic compatibility layer is now only a legacy fallback for scrobbling/sharing numeric-ID songs — which native search no longer produces, so it is effectively dormant
* Local storage: DataStore for settings (stores URL, username, password, JWT token)
* Min SDK: 26 (Android 8.0)
* Target SDK: latest stable

## Velvet Native API

Used for **folder browsing, audio streaming, and sharing**. Requires JWT bearer token authentication.

### Authentication

```
POST /api/v1/auth/login
Content-Type: application/json
{ "username": "...", "password": "..." }
→ { "token": "eyJ...", "vpaths": [...] }
```

All subsequent native API calls send the JWT as: `x-access-token: <token>`

The app also sends `Authorization: Bearer <token>` on every request (added by an OkHttp
interceptor) for reverse-proxy compatibility. The server accepts either header.

The token can also be passed as a query parameter: `?token=<jwt>`

### Token refresh

```
GET /api/v1/auth/refresh
x-access-token: <token>
→ { "token": "eyJ..." }
```

Called once on app boot (in `SettingsViewModel.init`) when a stored JWT exists.
On success the new token is persisted via `SettingsRepository.saveToken()`.
Silently ignored on failure — `ensureToken()` will re-login if the token is truly expired.

### File Explorer

```
POST /api/v1/file-explorer
x-access-token: <token>
Content-Type: application/json

{ "directory": "<path>", "sort": true, "pullMetadata": false }
```

* Root level: send `{ "directory": "" }` → `{ path: "/", directories: [{name}], files: [] }`
* Subfolders: `{ "directory": "/vpath/Artist" }` → `{ path, directories: [{name, path}], files: [{name, type, metadata?}] }`
* `directories` = folders to navigate into; `files` = audio files to play
* Filter files on `type` matching known audio extensions (mp3, flac, ogg, opus, m4a, aac, wav…)

#### pullMetadata response shape

When `pullMetadata: true`, each file entry gains a `metadata` object:

```json
{
  "name": "track.mp3",
  "type": "mp3",
  "metadata": {
    "filepath": "library/Artist/Album/track.mp3",
    "metadata": {
      "hash": "<file-content-hash>",
      "title": "Track Title",
      "artist": "Artist Name",
      "album": "Album Name",
      "album-art": "abc123.jpg"
    }
  }
}
```

**Important:** `metadata.metadata.hash` is the **audio file content hash** — it is NOT a Subsonic ID
and must not be passed to any Subsonic endpoint. The correct streaming identifier is `metadata.filepath`.

### Audio Streaming

```
GET /media/<filepath>?token=<jwt>
```

Where `filepath` = `metadata.filepath` from the file-explorer response (e.g. `library/Artist/Album/track.mp3`).
This is how the official Velvet webapp streams audio.

**Each path segment must be percent-encoded** (like the webapp's `encodeFp` / `encodeURIComponent`),
keeping `/` separators intact — `PlayerViewModel.streamUrl()` does this with `Uri.encode` per
segment. Escaping only `%`/`#`/`?` (the old behaviour) left spaces, `&`, `+` and accented
characters (é, è, à, ç…) literal in the URL, which **404'd playback** for those paths — pervasive
on non-ASCII libraries. This was a real bug.

### Album Art

```
GET /album-art/<filename>?token=<jwt>
```

Where `filename` = `metadata.metadata.album-art` from the file-explorer response.

### Native Share

```
POST /api/v1/share
x-access-token: <token>
Content-Type: application/json

{ "playlist": ["library/Artist/Album/track.mp3"], "time": 14 }

→ { "playlistId": "XXXXXXXXXX", "expires": 1748000000, "playlist": [...], "user": "...", "token": "..." }
```

* `time` = number of **days** until expiry (omit for a permanent link). **Not** a Unix timestamp.
* `expires` in the response = Unix timestamp in seconds (or `null` for permanent links).

The public share URL is `<serverUrl>/shared/<playlistId>`.

**Sharing the queue** reuses this exact endpoint — `VelvetRepository.shareQueue()` /
`PlayerViewModel.shareQueue()` collect every queued song's filepath (`EntryDto.id`, skipping
any Subsonic numeric-ID entries from search results, which this endpoint can't share) and pass
them as the `playlist` array in a single request, producing one link for the whole queue.

### Native rate song

```
POST /api/v1/db/rate-song
x-access-token: <token>
Content-Type: application/json

{ "filepath": "library/Artist/Album/track.mp3", "rating": 8 }
```

* `rating` is on Velvet's **native 0–10 scale with half-star precision**. Sharesonic's UI
  shows **0–5 stars**; `PlayerViewModel.rateCurrentSong(stars)` converts both ways
  (`stars * 2` when sending, `rating / 2` when reading `EntryDto.rating` back) — comparing or
  storing the two scales directly was a real bug (stars lit up wrong / didn't toggle).
* Pass `rating: null` (UI: tap the active star again, or the explicit clear button) to reset
  a track back to "unrated" — mirrors the Auto-DJ "minimum rating" picker's always-visible
  clear affordance, which is the UX pattern the rating UI was changed to follow.

### Share list and revoke

```
GET  /api/v1/share/list          → [{ playlistId, songCount, expires }]
DELETE /api/v1/share/:playlistId → { "success": true }
```

`cleanupExpiredShares()` in `VelvetRepository` fetches the list and calls DELETE for each share
whose `expires` is in the past. Called fire-and-forget in `FolderBrowserViewModel.init`.

### Native random songs

```
POST /api/v1/db/random-songs
x-access-token: <token>
Content-Type: application/json

{ "ignoreList": [3, 17, 42], "filepathPrefix": "/Music" }

→ { "songs": [{ "filepath": "Music/Artist/Album/track.flac", "metadata": { ... } }], "ignoreList": [3, 17, 42, 88] }
```

Returns **one song per call**. For the **whole-library shuffle** (root level) the app calls it
30 times sequentially, passing the updated `ignoreList` each time, to build a shuffle queue.
Songs have `filepath` as identifier — identical to `file-explorer` `pullMetadata=true` entries.
Replaces Subsonic `getRandomSongs`. (Per-**folder** shuffle uses a different, scalable mechanism —
see "Folder shuffle" below.)

For Auto-DJ, the same endpoint is called with additional filter fields:

```json
{
  "ignoreList": [3, 17, 42],
  "ignoreVPaths": ["Podcasts"],
  "bpmRanges": [{ "min": 115, "max": 135 }],
  "bpmRangesWide": [{ "min": 105, "max": 145 }],
  "requireBpm": false,
  "musicalKeys": ["8A", "7A", "9A", "8B"],
  "requireMusicalKey": false,
  "artists": ["Similar Artist 1", "Similar Artist 2"],
  "ignoreArtists": ["Recent Artist"],
  "genres": ["Jazz"],
  "genreMode": "whitelist",
  "minRating": 3
}
```

`bpmRanges` = tight window (preferred); `bpmRangesWide` = fallback window. The app tries the
full request first, then falls back to a plain random request if no match is found.

### Auto-DJ — similar artists

```
GET /api/v1/lastfm/similar-artists?artist=<name>
x-access-token: <token>
→ { "artists": ["Artist A", "Artist B", ...] }
```

Returns a list of similar artist names via Last.fm, proxied through Velvet.
Results are cached in memory per session. Used by `PlayerViewModel.fetchAndEnqueueAutoDjSong()`.

### On-demand art

```
GET /api/v1/files/art?fp=<filepath>
→ { "aaFile": "d41d8cd98f.jpg" }   // or { "aaFile": null }
```

Extracts embedded album art from any audio file. Returns the cache filename for use with
`GET /album-art/<aaFile>?token=<jwt>`. Implemented in `VelvetRepository.getArtFilename()`
(data layer only — not yet wired to the UI).

### Fresh single-track metadata

```
POST /api/v1/db/metadata
x-access-token: <token>
Content-Type: application/json

{ "filepath": "library/Artist/Album/track.mp3" }

→ { "filepath": "...", "metadata": { bpm, "musical-key", genres, year, track, artist, album, ... }, "rg": {...} }
```

Full server-side metadata for one track. Sharesonic calls this when the Now Playing
info dialog opens (`PlayerViewModel.fetchTrackMetadata()` → `VelvetRepository.getTrackMetadata()`)
so BPM / musical-key / genres / year / track are shown **regardless of how the song was
queued** — search-origin `EntryDto`s don't carry those, so without this fetch the info
dialog would be blank for search results while showing the values for browse/shuffle songs.

**Important — JSON key names on the wire:** the server emits `"musical-key"` (hyphen),
not `"musical_key"` (underscore). The Kotlin `VelvetInnerMetadata` model uses
`@SerializedName("musical-key")` accordingly — a previous underscore version silently
deserialised to null, hiding the key everywhere and starving Auto-DJ harmonic mixing
of its anchor key. This was a real bug.

### Lyrics

```
GET /api/v1/lyrics?artist=<name>&title=<name>&filepath=<vpath>&duration=<seconds>
x-access-token: <token>

→ { "synced": true,  "lines": [{ "time": 12.3, "text": "…" }] }   // timed
| { "synced": false, "lines": [{ "time": null, "text": "…" }] }   // plain
| { "notFound": true }
```

The server matches on artist+title (falling back to parsing "Artist - Title" from a
filename-title) and prefers the DB duration for the given filepath. It fetches from
lrclib.net and **already parses LRC timing into `lines`** — no client-side stripping
needed. Sharesonic calls this from the "Lyrics" entry of the Now Playing ⋮ sheet
(`PlayerViewModel.fetchLyrics()` → `VelvetRepository.getLyrics()`), and `LyricsSheet`
renders the line texts (loading / found / "No lyrics found" / error states).

### Native full-text search

```
POST /api/v1/db/search
x-access-token: <token>
Content-Type: application/json

{ "search": "<query>", "noFolders": false }

→ {
    "folders": [{ "folder_name": "Bob Marley", "browse_path": "/Music/Reggae/Bob Marley" }],
    "artists": [{ "name": "Bob Marley", "variants": ["Bob Marley", "Bob Marley & The Wailers"] }],
    "albums":  [{ "name": "Legend", "filepath": "Music/Reggae/Bob Marley/Legend", "album_art_file": "..." }],
    "title":   [{ "name": "Bob Marley - Could You Be Loved", "filepath": "Music/.../track.mp3", "album_art_file": "..." }],
    "files":   [ ... ]
  }
```

Replaces Subsonic `search3` for all in-app search (`VelvetRepository.search()`). Notes:

* `noFolders: false` is required to get the `folders` array **and** real album filepaths (with
  the default `true`, albums come back with `filepath: false`).
* **`folders`** = real on-disk directories matched by name. `browse_path` (`/vpath/dirpath`) is a
  valid `file-explorer` `directory` — Sharesonic renders these as a "Folders" section and navigates
  to them directly, with no client-side path guessing.
* **`artists`** carry only a normalized display `name` plus `variants` — every raw
  artist/`album_artist` tag value that normalizes to that name (e.g. `01 Ben Liebrand` → `Ben
  Liebrand`). The variants are needed to query the artist's songs, since the tag match is exact.
* `title` items are songs matched by title; `name` is `"Artist - Title"`. `files` are filename-only
  matches (currently not surfaced in the UI).

### Artist folder songs

```
POST /api/v1/db/artist-folder-songs
x-access-token: <token>
Content-Type: application/json

{ "artists": ["Bob Marley", "Bob Marley & The Wailers"] }

→ [ { "filepath": "Music/.../track.mp3", "metadata": { ... } }, ... ]   // bare array
```

Every song whose `artist`/`album_artist` tag **exactly** matches one of `artists`. Pass a search
result's normalized name **plus all its `variants`** (the match is exact-string against the raw tag,
not the normalized name). Response shape = `VelvetFileMetaWrapper` (same as `db/random-songs`).
Used when an artist is tapped in search: `SearchViewModel.fetchArtistSongsRaw()` →
`ArtistResultsScreen` lists the tracks; each plays by its own server-verified filepath.

### Folder shuffle (recursive scan + batch metadata)

Per-folder shuffle must gather every track under a folder — but a genre-sized folder can hold 100k+
files across thousands of sub-directories. A client-side recursive `file-explorer` walk (one request
per sub-folder) hangs forever at that scale, so `VelvetRepository.collectSongsFast()` uses two
server-side requests instead:

```
POST /api/v1/file-explorer/recursive   { "directory": "/Music/Reggae" }
  → ["Music/Reggae/Artist/Album/track.mp3", ...]   // every filepath in the subtree, one request

POST /api/v1/db/metadata/batch          ["Music/Reggae/.../track.mp3", ...]
  → { "Music/Reggae/.../track.mp3": { "filepath": ..., "metadata": { ... } }, ... }
```

* The recursive scan can take a while server-side on huge folders, so these calls use a **longer read
  timeout** — `VelvetClient.buildLongTimeout()` (300 s) instead of the default 60 s.
* The result is **capped at `SHUFFLE_MAX` (5000) tracks, sampled randomly across the whole folder**
  (shuffle the filepaths, then take the cap) — materializing a 100k-track queue + its metadata is
  infeasible on-device. Folders under the cap are taken in full. Metadata is fetched in chunks of
  `METADATA_CHUNK` (2000) to bound each payload; unindexed files fall back to a minimal `EntryDto`
  (filepath only) so they stay playable.
* `collectSongs()` (the old recursive client walk) is **still used by `shareFolder()`** — only
  shuffle switched to the fast path.

### Scrobbling

Sharesonic reports playback to Velvet, which forwards to the user's configured **Last.fm**
and **ListenBrainz** accounts. No API keys are needed in the app — Velvet uses its own.
Calls are fire-and-forget; silently ignored if the services are not configured.

**ListenBrainz — "now playing" ping** (sent on track start):
```
POST /api/v1/listenbrainz/playing-now
x-access-token: <token>
{ "filePath": "Music/Artist/Album/track.mp3" }
```

**Last.fm scrobble** (sent after 50% of track played):
```
POST /api/v1/lastfm/scrobble-by-filepath
x-access-token: <token>
{ "filePath": "Music/Artist/Album/track.mp3" }
```

**ListenBrainz scrobble** (sent after 50% of track played):
```
POST /api/v1/listenbrainz/scrobble-by-filepath
x-access-token: <token>
{ "filePath": "Music/Artist/Album/track.mp3" }
```

Legacy: for any Subsonic integer-ID song, scrobbling would use `scrobble.view` (see below) — but
native search no longer produces such songs, so this path is dormant.

## Equalizer

Native Android per-band equalizer wired to the ExoPlayer audio session.

* [PlaybackService.kt](app/src/main/kotlin/com/tiritibambix/sharesonic/playback/PlaybackService.kt) generates a stable audio session id via `AudioManager.generateAudioSessionId()`, binds it to the ExoPlayer with `setAudioSessionId(...)`, then attaches an `android.media.audiofx.Equalizer` via [EqualizerController](app/src/main/kotlin/com/tiritibambix/sharesonic/playback/EqualizerController.kt) (a process-wide singleton — the effect is not addressable through `MediaController`, so a shared holder is the simplest bridge from the service to the settings screen).
* Persistence: [SettingsRepository.eqSettings](app/src/main/kotlin/com/tiritibambix/sharesonic/data/settings/SettingsRepository.kt) stores `enabled: Boolean` + `bandsMb: List<Short>` in DataStore; band gains are re-applied by `PlaybackService.applySavedEqualizer()` on service start.
* UI: [EqSettingsScreen.kt](app/src/main/kotlin/com/tiritibambix/sharesonic/ui/settings/EqSettingsScreen.kt) — enable/disable switch, per-band sliders (frequency label + signed dB gain), "Reset to flat". Shows a graceful "Equalizer not available" state on devices/emulators without an EQ effect (`EqualizerController.available == false`).
* Reachable from **both** the drawer (in `FolderBrowserScreen.DrawerMenuItems`) and the top-level Settings menu (in `SettingsScreen`).

## Crash reporter

An in-app uncaught-exception handler so crashes can be diagnosed without adb.

* [SharesonicApp.kt](app/src/main/kotlin/com/tiritibambix/sharesonic/SharesonicApp.kt) is the `Application` class (registered as `android:name=".SharesonicApp"` in `AndroidManifest.xml`). Its `onCreate()` installs a `Thread.setDefaultUncaughtExceptionHandler` that logs the stack trace (tag `SharesonicCrash`) **and** persists it synchronously in `SharedPreferences` (`sharesonic_crash` / `last_crash`) before delegating to the previous handler so the OS still shows its normal crash behaviour.
* On the next launch, [MainActivity](app/src/main/kotlin/com/tiritibambix/sharesonic/MainActivity.kt) reads the saved trace once and shows it in a copyable `AlertDialog` ("Previous crash"), then clears the entry when dismissed.

## Subsonic API Endpoints (legacy / dormant)

Search now uses the **native** `/api/v1/db/search` (see "Native full-text search" above), so the
Subsonic API (`/rest/`) is **no longer used for search** — and never for streaming, sharing,
browsing, or shuffle. The `SubsonicRepository` code paths below survive only as a defensive
fallback for songs carrying a Subsonic **integer** ID (`id.all { it.isDigit() }`), which the native
search no longer produces — so in practice they are dormant.

**Important:** Velvet Subsonic IDs are plain integer database row IDs (e.g. `42`), valid only with
other Subsonic endpoints. Do NOT use file content hashes as Subsonic IDs.

* `search3` — legacy full-text search (returns integer IDs); superseded by native `db/search`
* `createShare` — share a numeric-ID song (only if one somehow originates from Subsonic)
* `getCoverArt` — cover art for numeric-ID songs
* `scrobble` — report playback for integer-ID songs (`submission=false` on start, `true` at 50%)

Subsonic auth: `u=<username>`, `p=<plain-text password>`, `v=1.16.1`, `c=Sharesonic`, `f=json`

Reference: https://www.subsonic.org/pages/api.jsp

### Dual-path streaming and sharing

Songs have two origins with different identifiers:

| Origin | `EntryDto.id` | Stream URL | Share | Scrobble |
|---|---|---|---|---|
| `file-explorer` (browsing) | filepath string | `/media/<id>?token=<jwt>` | `POST /api/v1/share` | native LFM + LB |
| `db/random-songs` (shuffle-all) | filepath string | `/media/<id>?token=<jwt>` | `POST /api/v1/share` | native LFM + LB |
| `db/search` (search results) | filepath string | `/media/<id>?token=<jwt>` | `POST /api/v1/share` | native LFM + LB |
| `search3` (legacy, dormant) | numeric string (`"42"`) | `/rest/stream.view?id=42&...` | `createShare?id=42` | `scrobble.view?id=42` |

The app distinguishes Subsonic integer IDs by checking `id.all { it.isDigit() }`. Both shuffle and
native `db/search` now yield filepath IDs, so the `search3` (numeric) row is legacy only — no
in-app path currently produces integer IDs.

## Build & Distribution

* Build system: Gradle
* CI: GitHub Actions
* On every push (any branch): build a debug APK named `sharesonic-artifact+<run>.apk` (where `<run>` is the GitHub Actions run number, passed via `-PartifactRun`) and upload it as a workflow artifact. `versionCode` is also set to `<run>` so successive debug installs upgrade cleanly.
* On every git tag (`v*`): build a signed release APK named `sharesonic-v<versionName>.apk` (`versionName` derived from the tag) and publish it as a GitHub Release asset
* **Releases page**: https://github.com/Tiritibambix/Sharesonic/releases
* **Obtainium** (auto-update): `obtainium://add/https://github.com/Tiritibambix/Sharesonic`

The APK-naming split lives in [app/build.gradle.kts](app/build.gradle.kts) (`applicationVariants.all { ... outputFileName = ... }`). Non-release builds default to `sharesonic-artifact+local.apk` when built without `-PartifactRun`.

## GitHub Actions Workflow Requirements

The workflow must:

* Run on `ubuntu-latest`
* Use `actions/setup-java` with JDK 17
* Cache Gradle dependencies
* Build with `./gradlew assembleDebug -PartifactRun=<run_number> -PversionCode=<run_number>` on push
* Build with `./gradlew assembleRelease -PversionName=<tag> -PversionCode=<run_number>` on tag
* Attach the APK to the GitHub Release automatically

## Out of Scope for v1

* Offline caching / download for offline playback
* Multiple server profiles
* Android Auto support
* Chromecast / DLNA casting
* Generic Subsonic server support (Navidrome, Airsonic…) — planned post-v1
