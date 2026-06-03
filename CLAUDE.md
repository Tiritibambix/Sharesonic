# Sharesonic

## Project Overview

Sharesonic is an Android music client for **mStream** (https://mstream.io).

The goal is a single app that does three things well:

* Browse music by real filesystem folder structure via mStream's native API
* Shuffle play on a folder or the entire library
* Generate a public share link for any track

## Core Requirements

### Must Have

* Folder-based browsing as the primary navigation mode using mStream's native `/api/v1/file-explorer` endpoint (not the Subsonic API — Subsonic on mStream returns tag-based artist/album views, not real folders)
* Shuffle play on any folder or on the full library
* Share link generation: tap a track → generate a `server/shared/XXXXXXXXXX` public URL via the mStream native share API → Android share sheet opens with that URL ready to send
* Settings screen: mStream server URL, username, password, connection test button (tests JWT login)
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
2. **Folder Browser** — real filesystem tree via native API, swipe or long press for actions
3. **Now Playing** — minimal player with share button prominently accessible
4. **Share confirmation** — shows the generated link with a copy + send button

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

The token can also be passed as a query parameter: `?token=<jwt>`

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

{ "playlist": ["library/Artist/Album/track.mp3"] }

→ { "playlistId": "XXXXXXXXXX", "playlist": [...], "user": "...", "expires": null, "token": "..." }
```

The public share URL is `<serverUrl>/shared/<playlistId>`.

## Subsonic API Endpoints Used

The Subsonic API (`/rest/`) is used **only for search and library-wide shuffle**.
It is NOT used for streaming, sharing, or browsing.

**Important:** mStream Subsonic IDs are plain integer database row IDs (e.g. `42`).
They are returned by Subsonic endpoints (`getRandomSongs`, `search3`) and are valid
only with other Subsonic endpoints (`stream.view`, `createShare`).
Do NOT use file content hashes as Subsonic IDs.

* `getRandomSongs` — server-side random pool for shuffle-all (returns songs with integer IDs)
* `search3` — full-text search across songs, albums, artists
* `createShare` — generate a share URL for songs obtained via Subsonic (`getRandomSongs` results only)
* `getCoverArt` — cover art for songs/albums obtained via Subsonic

Subsonic auth: `u=<username>`, `p=<plain-text password>`, `v=1.16.1`, `c=Sharesonic`, `f=json`

Reference: https://www.subsonic.org/pages/api.jsp

### Dual-path streaming and sharing

Songs have two origins with different identifiers:

| Origin | `EntryDto.id` | Stream URL | Share |
|---|---|---|---|
| `file-explorer` (browsing) | filepath string | `/media/<id>?token=<jwt>` | `POST /api/v1/share` |
| `getRandomSongs` (shuffle-all) | numeric string (`"42"`) | `/rest/stream.view?id=42&...` | `createShare?id=42` |

The app distinguishes them by checking `id.all { it.isDigit() }`.

## Build & Distribution

* Build system: Gradle
* CI: GitHub Actions
* On every push to `main`: build a debug APK
* On every git tag (`v*`): build a signed release APK and publish it as a GitHub Release asset

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
