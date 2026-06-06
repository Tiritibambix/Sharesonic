# Sharesonic

## Project Overview

Sharesonic is an Android music client for **mStream** (https://mstream.io).

The goal is an app that does a handful of things well:

* Browse music by real filesystem folder structure via mStream's native API
* Shuffle play on a folder or the entire library
* Generate a public share link for any track
* Manage playlists (create, rename, delete; add/remove tracks; play all / shuffle)
* Persistent mini player during navigation

## Core Requirements

### Must Have

* Folder-based browsing as the primary navigation mode using mStream's native `/api/v1/file-explorer` endpoint (not the Subsonic API — Subsonic on mStream returns tag-based artist/album views, not real folders)
* Shuffle play on any folder or on the full library
* Share link generation: tap a track → generate a `server/shared/XXXXXXXXXX` public URL via the mStream native share API → Android share sheet opens with that URL ready to send
* Settings screen: mStream server URL, username, password, connection test button (tests JWT login)
* Playlist management: create, rename, delete playlists; add/remove tracks; play all / shuffle; add from browser (swipe right) or from Now Playing
* Persistent mini player bar during navigation: thumbnail, title/artist, skip/play-pause controls, live progress strip; folds away on the Now Playing screen
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

1. **Settings** — mStream server URL, username, password, test connection
2. **Folder Browser** — real filesystem tree via native API; swipe right → add to playlist, swipe left → add to queue; long press → context menu; alphabetical letter strip
3. **Now Playing** — full player (cover art, seek bar, share + add-to-playlist buttons); swipe left for queue view
4. **Queue** — scrollable queue, tap to jump, swipe left to remove
5. **Mini player** — persistent bottom bar on all screens except Now Playing; folds up/down without fade
6. **Playlists** — list of playlists with real track counts; create / rename / delete
7. **Playlist detail** — track list; play all / shuffle FABs; swipe to remove; add songs via search dialog
8. **Share confirmation** — shows the generated link with a copy + send button

## Technical Stack

* Language: Kotlin
* UI: Jetpack Compose + Material 3
* Architecture: MVVM with ViewModel + StateFlow
* Network: Retrofit + OkHttp (mStream native API + Subsonic API for search and shuffle-all)
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
