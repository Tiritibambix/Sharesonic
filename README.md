# Sharesonic

> *Rediscover your music library. One shuffle at a time. Share what you find.*

Sharesonic is an Android client for [Navidrome](https://www.navidrome.org/) and any [Subsonic-compatible](https://www.subsonic.org/pages/api.jsp) music server. It is built around a single philosophy: **your music collection is too large to listen to linearly — let chance guide you, then share what surprises you.**

---

## Why Sharesonic?

Most music apps are built around curation: playlists you already know, albums you already love, artists you already follow. That works fine for a library of a few hundred tracks. It breaks down when you self-host thousands of albums accumulated over years.

Sharesonic is built for the other scenario — the large, chaotic, lovingly disorganised self-hosted library where the best discoveries happen by accident.

- **Shuffle a whole library** — not just an album or a playlist. Hit shuffle at the root level and let 200 random tracks from your entire collection play back-to-back. You will hear things you forgot you had.
- **Shuffle a folder** — narrow the randomness down to a genre folder, a decade folder, an artist folder. Still surprising, still exploratory, but with a little more context.
- **Browse by folder** — the folder tree is the primary navigation mode. No tag-based views, no "Recently Added" carousels. Just your directory structure, exactly as you organised it on the server.
- **Share what you find** — when shuffle surfaces something worth passing on, one tap generates a public share link via the Subsonic `createShare` API and opens the Android share sheet. Send it to anyone. No account required on their end.

---

## Features

| Feature | Details |
|---|---|
| **Folder browsing** | Navigate your full directory tree from root to individual tracks |
| **Shuffle library** | `getRandomSongs` — server-side random pick, up to 200 tracks |
| **Shuffle folder** | Recursive local collect + shuffle on any sub-directory |
| **Share link** | `createShare` endpoint → public URL → Android share sheet |
| **Now Playing** | Full-screen cover art, seek bar with elapsed/total time, artist · album info, file path |
| **Queue view** | Swipe left from Now Playing — full scrollable queue, tap to jump |
| **Search** | `search3` — songs, albums, artists with cover art thumbnails |
| **Settings** | Server URL, username, password, one-tap connection test |
| **Cover art** | Loaded from `getCoverArt` in folder rows and full-screen player |
| **Dark theme only** | Deep purple Material You palette — no light mode, no compromise |

---

## Screenshots

*Coming soon.*

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM — ViewModel + StateFlow |
| Navigation | Navigation Compose |
| Networking | Retrofit 2 + OkHttp 4 + Gson |
| Authentication | Subsonic token auth (MD5 + random salt per request) |
| Playback | Media3 ExoPlayer + `MediaSessionService` |
| Image loading | Coil 2 |
| Settings storage | DataStore Preferences |
| Min SDK | 26 (Android 8.0) |

---

## Server compatibility

Sharesonic works with any server that implements the Subsonic REST API v1.13+:

- [Navidrome](https://www.navidrome.org/) ✅ (primary target)
- [Airsonic](https://github.com/airsonic/airsonic) ✅
- [Subsonic](https://www.subsonic.org/) ✅
- [Gonic](https://github.com/sentriz/gonic) ✅
- Other Subsonic-compatible servers — should work, not tested

---

## Installation

Sharesonic is distributed as a sideloaded APK. There is no Play Store release.

1. Download the latest APK from the [Releases](../../releases) page
2. On your Android device: **Settings → Security → Install unknown apps** → allow your browser or file manager
3. Open the downloaded APK and install
4. Launch Sharesonic, enter your server URL, username and password, tap **Test** then **Save**

---

## Building from source

### Prerequisites

- JDK 17
- Android SDK with build tools for API 35
- A `local.properties` file at the project root with your SDK path:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

### Debug build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release build

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## CI / CD

GitHub Actions runs on every push and tag:

| Trigger | Action |
|---|---|
| Push to `main` | Build debug APK, upload as workflow artifact |
| Tag `v*` | Build release APK, create GitHub Release, attach APK |

---

## Subsonic API endpoints used

| Endpoint | Purpose |
|---|---|
| `ping` | Connection test |
| `getMusicFolders` | List root music libraries |
| `getIndexes` | List top-level directories within a library |
| `getMusicDirectory` | Browse a directory's contents |
| `getRandomSongs` | Server-side random song pool for shuffle |
| `search3` | Full-text search across songs, albums, artists |
| `stream` | Audio playback |
| `getCoverArt` | Album artwork |
| `createShare` | Generate a public share URL |
| `getShares` | List existing shares |
| `deleteShare` | Delete a share |

---

## Project background

Sharesonic was assembled from the best parts of two open-source projects:

- **[DSub2000](https://github.com/trigsoft/DSub2000)** — the folder-browsing model and shuffle architecture
- **[Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic)** — the `createShare` API flow and share link handling

Neither codebase was copied directly. The logic was studied and rewritten from scratch in Kotlin + Compose.

---

## Roadmap / known limitations (v0.0.x)

- Seeking works but the position bar resets on skip — will be addressed in a future update
- No offline caching or download for offline playback
- No playlist management
- No scrobbling (Last.fm / ListenBrainz)
- No multiple server profiles
- No Android Auto support
- No lyrics

Contributions welcome. Open an issue before submitting a large PR.

---

## License

[GPL-3.0](LICENSE) — same as the upstream projects this draws from.
