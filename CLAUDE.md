# Sharesonic

## Project Overview

Sharesonic is an Android music client for Navidrome (and any Subsonic-compatible server).
It is built by combining the best features of two open source projects:

* **DSub2000** (https://github.com/trigsoft/DSub2000): excellent folder browsing and shuffle
* **Ultrasonic** (https://gitlab.com/ultrasonic/ultrasonic): Subsonic share link creation via API

The goal is a single app that does three things well:

* Browse music by folder structure
* Shuffle play on a folder or the entire library
* Generate a public share link for any track, album or folder via the Subsonic API

## Core Requirements

### Must Have

* Folder-based browsing as the primary navigation mode (not artist/album/tag-based)
* Shuffle play on any folder or on the full library
* Share link generation: tap a track or folder -> generate a `server/share/XXXXXXXXXX` public URL via the Subsonic `createShare` endpoint -> Android share sheet opens with that URL ready to send
* Settings screen: server URL, username, password, connection test button
* Offline-safe: graceful error handling when the server is unreachable

### Must Not Have

* Video support
* Podcast support
* Tag-based browsing as primary mode (can exist as secondary)
* Any telemetry or analytics

## Design

### Visual Direction

* Jetpack Compose UI
* Material You (Material 3)
* Dark theme only, deep purple color scheme (not bright purple — dark, elegant, desaturated)
* Clean and content-focused: the folder tree and the player are the stars, not the UI chrome
* No bottom navigation clutter — keep it simple

### Key Screens

1. **Settings** — server URL, username, password, test connection
2. **Folder Browser** — recursive folder tree, swipe or long press on item for actions
3. **Now Playing** — minimal player with share button prominently accessible
4. **Share confirmation** — shows the generated link with a copy + send button

## Technical Stack

* Language: Kotlin
* UI: Jetpack Compose + Material 3
* Architecture: MVVM with ViewModel + StateFlow
* Network: Retrofit or Ktor for Subsonic API calls
* Local storage: DataStore for settings
* Min SDK: 26 (Android 8.0)
* Target SDK: latest stable

## Subsonic API Endpoints Used

* `ping` — connection test
* `getMusicFolders` — list root folders
* `getMusicDirectory` — browse folder contents
* `stream` — audio playback
* `createShare` — generate a public share link (key feature)
* `getShares` — list existing shares (optional)
* `deleteShare` — delete a share (optional)

Reference: https://www.subsonic.org/pages/api.jsp
Navidrome Subsonic compatibility: https://www.navidrome.org/docs/developers/subsonic-api/

## Build \& Distribution

* Build system: Gradle
* CI: GitHub Actions
* On every push to `main`: build a debug APK
* On every git tag (`v\*`): build a release APK and publish it as a GitHub Release asset
* No signing required beyond debug for now (sideload install only, no Play Store)

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
* Playlist management
* Scrobbling (Last.fm, ListenBrainz)
* Multiple server profiles
* Lyrics display
* Android Auto support

