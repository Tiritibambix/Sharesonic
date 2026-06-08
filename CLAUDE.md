# Sharesonic

## Project Overview

Sharesonic is an Android music client for **mStream Velvet** (https://mstream.io).

The goal is an app that does a handful of things well:

* Browse music by real filesystem folder structure via mStream's native API
* Shuffle play on a folder or the entire library
* Auto-DJ: continuous smart queue with BPM continuity, harmonic mixing (Camelot wheel), similar artists (Last.fm), artist cooldown, genre filter, crossfade
* Generate a public share link for any track — or for the entire current queue in one tap
* Rate tracks 0–5 stars from Now Playing, with an explicit way to clear back to "unrated"
* Manage playlists (create, rename, delete; add/remove tracks; play all / shuffle)
* Persistent mini player during navigation
* Hamburger navigation drawer (Server, Auto-DJ, Themes, Public Links) reachable from anywhere, with Search and Playlists always pinned in the top bar

## Core Requirements

### Must Have

* Folder-based browsing as the primary navigation mode using mStream's native `/api/v1/file-explorer` endpoint (not the Subsonic API — Subsonic on mStream returns tag-based artist/album views, not real folders)
* Shuffle play on any folder or on the full library
* Auto-DJ: when enabled, the queue never stops — the app continuously fetches the next track using BPM continuity, harmonic key (Camelot wheel), similar artists via Last.fm, artist cooldown, genre filter, and optional crossfade
* Share link generation: tap a track → generate a `server/shared/XXXXXXXXXX` public URL via the mStream native share API → Android share sheet opens with that URL ready to send
* Share queue: from the queue view, generate a single public link covering every track currently queued (same native share endpoint, fed the queue's filepaths); Subsonic-search-origin tracks are skipped since they aren't shareable through it
* Star ratings: rate the current track 0–5 stars from Now Playing (synced to mStream's native 0–10 half-star scale); tapping the active star — or an explicit always-visible clear button — resets it back to "unrated", mirroring the Auto-DJ "minimum rating" picker's affordance
* Settings reachable via a hamburger navigation drawer containing **Server**, **Auto-DJ**, **Themes** and **Public Links**; the hamburger glyph stays in the top-left of every drawer destination so users can tap back out; Search and Playlists icons remain pinned top-right in the Folder Browser at all times
* Playlist management: create, rename, delete playlists; add/remove tracks; play all / shuffle; add from browser (swipe right) or from Now Playing
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
* Dark theme only, deep purple color scheme (not bright purple — dark, elegant, desaturated)
* Clean and content-focused: the folder tree and the player are the stars, not the UI chrome
* No bottom navigation clutter — keep it simple

### Key Screens

1. **Navigation drawer** — hamburger menu opening a frosted-glass sheet sized to ~80% of the screen width (`Modifier.width(maxWidth * 0.8f)` + `Modifier.blur()` on the content behind it via `animateDpAsState`), so a blurred sliver of the browser stays visible and tappable on the right — making the swipe-to-dismiss gesture obvious without anyone having to discover it. Lists **Server**, **Auto-DJ**, **Themes**, **Public Links**; each destination keeps the hamburger glyph as its `navigationIcon` so tapping it again exits straight back out
2. **Server / Auto-DJ / Themes / Public Links settings** — each is its own screen reachable only via the drawer; Server holds the mStream URL, username, password and connection test
3. **Folder Browser** — real filesystem tree via native API; swipe right → add to playlist, swipe left → add to queue; long press → context menu; alphabetical letter strip; Search and Playlists icons are pinned in the top-right of the `TopAppBar` at all times (not folded into the drawer)
4. **Now Playing** — airy, single-screen player redesigned to avoid feeling cramped: cover art, title/artist, a combined format/bitrate + star-rating row, generously spaced playback controls and seek bar, and an Actions area (share, add-to-playlist, queue-share). The exact filename and full path collapse into one compact tappable line that opens an info dialog with selectable text — so nothing requires scrolling. Swipe left for queue view
5. **Queue** — scrollable queue, tap to jump, swipe left to remove; a top-bar share icon (visible only here) generates one public link for the whole queue
6. **Mini player** — persistent bottom bar on all screens except Now Playing; folds up/down without fade
7. **Playlists** — list of playlists with real track counts; create / rename / delete
8. **Playlist detail** — track list; play all / shuffle FABs; swipe to remove; add songs via search dialog
9. **Search** — pill-shaped Material You search field living in the screen body (not the `TopAppBar` title slot, which clipped it); auto-focuses on entry
10. **Share confirmation** — shows the generated link with a copy + send button

## Technical Stack

* Language: Kotlin
* UI: Jetpack Compose + Material 3
* Architecture: MVVM with ViewModel + StateFlow
* Network: Retrofit + OkHttp (mStream native API for everything; Subsonic API for search only)
* Local storage: DataStore for settings (stores URL, username, password, JWT token)
* Min SDK: 26 (Android 8.0)
* Target SDK: latest stable

## mStream Native API

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
This is how the official mStream webapp streams audio.

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

**Sharing the queue** reuses this exact endpoint — `MStreamRepository.shareQueue()` /
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

* `rating` is on mStream's **native 0–10 scale with half-star precision**. Sharesonic's UI
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

`cleanupExpiredShares()` in `MStreamRepository` fetches the list and calls DELETE for each share
whose `expires` is in the past. Called fire-and-forget in `FolderBrowserViewModel.init`.

### Native random songs

```
POST /api/v1/db/random-songs
x-access-token: <token>
Content-Type: application/json

{ "ignoreList": [3, 17, 42], "filepathPrefix": "/Music" }

→ { "songs": [{ "filepath": "Music/Artist/Album/track.flac", "metadata": { ... } }], "ignoreList": [3, 17, 42, 88] }
```

Returns **one song per call**. The app calls it 30 times sequentially, passing the updated
`ignoreList` each time, to build a shuffle queue. Songs have `filepath` as identifier —
identical to `file-explorer` `pullMetadata=true` entries. Replaces Subsonic `getRandomSongs`.

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

Returns a list of similar artist names via Last.fm, proxied through mStream.
Results are cached in memory per session. Used by `PlayerViewModel.fetchAndEnqueueAutoDjSong()`.

### On-demand art

```
GET /api/v1/files/art?fp=<filepath>
→ { "aaFile": "d41d8cd98f.jpg" }   // or { "aaFile": null }
```

Extracts embedded album art from any audio file. Returns the cache filename for use with
`GET /album-art/<aaFile>?token=<jwt>`. Implemented in `MStreamRepository.getArtFilename()`
(data layer only — not yet wired to the UI).

### Scrobbling

Sharesonic reports playback to mStream, which forwards to the user's configured **Last.fm**
and **ListenBrainz** accounts. No API keys are needed in the app — mStream uses its own.
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

For Subsonic integer-ID songs (from `search3`), scrobbling uses `scrobble.view` — see below.

## Subsonic API Endpoints Used

The Subsonic API (`/rest/`) is used **only for search**.
It is NOT used for streaming, sharing, browsing, or shuffle.

**Important:** mStream Subsonic IDs are plain integer database row IDs (e.g. `42`).
They are returned by `search3` and are valid only with other Subsonic endpoints.
Do NOT use file content hashes as Subsonic IDs.

* `search3` — full-text search across songs, albums, artists (returns integer IDs)
* `createShare` — generate a share URL for songs obtained via `search3` only
* `getCoverArt` — cover art for songs/albums obtained via Subsonic search
* `scrobble` — report playback for integer-ID songs (`submission=false` on start, `true` at 50%)

Subsonic auth: `u=<username>`, `p=<plain-text password>`, `v=1.16.1`, `c=Sharesonic`, `f=json`

Reference: https://www.subsonic.org/pages/api.jsp

### Dual-path streaming and sharing

Songs have two origins with different identifiers:

| Origin | `EntryDto.id` | Stream URL | Share | Scrobble |
|---|---|---|---|---|
| `file-explorer` (browsing) | filepath string | `/media/<id>?token=<jwt>` | `POST /api/v1/share` | native LFM + LB |
| `db/random-songs` (shuffle-all) | filepath string | `/media/<id>?token=<jwt>` | `POST /api/v1/share` | native LFM + LB |
| `search3` (search results) | numeric string (`"42"`) | `/rest/stream.view?id=42&...` | `createShare?id=42` | `scrobble.view?id=42` |

The app distinguishes Subsonic integer IDs by checking `id.all { it.isDigit() }`.
Shuffle-all songs now use the native filepath path — no integer IDs from shuffle.

## Build & Distribution

* Build system: Gradle
* CI: GitHub Actions
* On every push to `main`: build a debug APK
* On every git tag (`v*`): build a signed release APK and publish it as a GitHub Release asset
* **Releases page**: https://github.com/Tiritibambix/Sharesonic/releases
* **Obtainium** (auto-update): `obtainium://add/https://github.com/Tiritibambix/Sharesonic`

## GitHub Actions Workflow Requirements

The workflow must:

* Run on `ubuntu-latest`
* Use `actions/setup-java` with JDK 17
* Cache Gradle dependencies
* Build with `./gradlew assembleDebug` on push
* Build with `./gradlew assembleRelease` and upload APK artifact on tag
* Attach the APK to the GitHub Release automatically

## Out of Scope for v1

* Offline caching / download for offline playback
* Multiple server profiles
* Lyrics display
* Android Auto support
* Generic Subsonic server support (Navidrome, Airsonic…) — planned post-v1
