# Sharesonic

## Project Overview

Sharesonic is an Android music client for **mStream** (https://mstream.io).
It is built by combining the best features of two open source projects:

* **DSub2000** (https://github.com/trigsoft/DSub2000): excellent folder browsing and shuffle
* **Ultrasonic** (https://gitlab.com/ultrasonic/ultrasonic): Subsonic share link creation via API

The goal is a single app that does three things well:

* Browse music by real filesystem folder structure via mStream's native API
* Shuffle play on a folder or the entire library
* Generate a public share link for any track via the Subsonic `createShare` endpoint

## Core Requirements

### Must Have

* Folder-based browsing as the primary navigation mode using mStream's native `/api/v1/file-explorer` endpoint (not the Subsonic API — Subsonic on mStream returns tag-based artist/album views, not real folders)
* Shuffle play on any folder or on the full library
* Share link generation: tap a track → generate a `server/shared/XXXXXXXXXX` public URL via the Subsonic `createShare` endpoint → Android share sheet opens with that URL ready to send
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
* Network: Retrofit + OkHttp (mStream native API + Subsonic API)
* Local storage: DataStore for settings (stores URL, username, password, JWT token)
* Min SDK: 26 (Android 8.0)
* Target SDK: latest stable

## mStream Native API

Used for **folder browsing only**. Requires JWT bearer token authentication.

### Authentication

```
POST /api/v1/auth/login
Content-Type: application/json
{ "username": "...", "password": "..." }
→ { "token": "eyJ...", "vpaths": [...] }
```

All subsequent native API calls send the JWT as: `x-access-token: <token>`

### File Explorer

```
POST /api/v1/file-explorer
Authorization: Bearer <token>
Content-Type: application/json

{ "directory": "<path>", "sort": true, "pullMetadata": false }
```

* Root level: send `{ "directory": "" }` → `{ directories: [{name}], files: [] }`
* Subfolders: send `{ "directory": "/Genre/Artist" }` → `{ path, directories: [{name, path}], files: [{name, path, type}] }`
* `pullMetadata: true` adds a Subsonic-compatible `id` (or `track_id`) field to each file entry, usable with the Subsonic stream and createShare endpoints
* `directories` = folders to navigate into; `files` = audio files to play
* Filter files on `type` matching known audio extensions (mp3, flac, ogg, opus, m4a, aac, wav…)

## Subsonic API Endpoints Used

The Subsonic API (`/rest/`) is used **only for playback and sharing**, not for browsing.

* `stream` — audio playback (`/rest/stream?id=<subsonicId>&...`)
* `createShare` — generate a public share link (key feature)
* `getShares` — list existing shares (optional)
* `deleteShare` — delete a share (optional)
* `getRandomSongs` — server-side random pool for shuffle-all

Subsonic auth: `u=<username>`, `p=<plain-text password>`, `v=1.16.1`, `c=Sharesonic`, `f=json`

Reference: https://www.subsonic.org/pages/api.jsp

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

## Source Reference

When implementing folder browsing and shuffle, refer to DSub's implementation.
When implementing share link creation, refer to Ultrasonic's implementation.
Do not copy code blindly — understand the logic and rewrite it cleanly in the new codebase.

## Out of Scope for v1

* Offline caching / download for offline playback
* Multiple server profiles
* Lyrics display
* Android Auto support
