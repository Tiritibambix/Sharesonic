# Sharesonic

> *Rediscover your music library. One shuffle at a time. Share what you find.*

Sharesonic is an Android client for **[mStream Velvet](https://github.com/aroundmyroom/mStream)** — the self-hosted music server. It is built around a single philosophy: **your music collection is too large to listen to linearly — let chance guide you, then share what surprises you.**

---

## Download

[![GitHub Release](https://img.shields.io/github/v/release/Tiritibambix/Sharesonic?style=for-the-badge&logo=android&color=8b5cf6&cacheSeconds=3600)](https://github.com/Tiritibambix/Sharesonic/releases/latest)

Download the latest APK directly from the [Releases](https://github.com/Tiritibambix/Sharesonic/releases/latest) page.

### Auto-updates with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs Sharesonic from GitHub Releases and keeps it up to date automatically.

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Tiritibambix/Sharesonic"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="40"></a>

---

## Why Sharesonic?

Most music apps are built around curation: playlists you already know, albums you already love, artists you already follow. That works fine for a library of a few hundred tracks. It breaks down when you self-host thousands of albums accumulated over years.

Sharesonic is built for the other scenario — the large, chaotic, lovingly disorganised self-hosted library where the best discoveries happen by accident.

- **Shuffle a whole library** — hit shuffle at the root level and let random tracks from your entire collection play back-to-back. You will hear things you forgot you had.
- **Shuffle a folder** — narrow the randomness to a genre, a decade, an artist. Still surprising, still exploratory, with a little more context.
- **Browse by folder** — the folder tree is the primary navigation mode. Your directory structure, exactly as you organised it on the server.
- **Share what you find** — when shuffle surfaces something worth passing on, one tap generates a public share link and opens the Android share sheet. Send it to anyone.

---

## Features

| Feature | Details |
|---|---|
| **Folder browsing** | Navigate your full directory tree from root to individual tracks |
| **Shuffle library** | Server-side random pick via native Velvet API — 30 tracks, no repeats |
| **Shuffle folder** | Recursive collect + shuffle on any sub-directory |
| **Auto-DJ** | Continuous smart queue: BPM continuity, harmonic mixing (Camelot wheel), similar artists, artist cooldown, genre filter, crossfade — toggle ∞ in mini player or Now Playing |
| **Share link** | Native mStream share API → public `server/shared/XXXXXXXXXX` URL → Android share sheet |
| **Now Playing** | Cover art, seek bar with elapsed/total time, artist · album info |
| **Mini player** | Persistent bottom bar with progress strip, controls and art — folds up during navigation |
| **Queue view** | Swipe left from Now Playing — full scrollable queue, tap to jump, swipe to remove |
| **Add to queue** | Swipe left on any track in the browser |
| **Playlist management** | Create, rename, delete playlists; add/remove tracks; play all or shuffle |
| **Add to playlist** | Swipe right on a track in the browser, or tap "Playlist" in Now Playing |
| **Search** | Full-text search across songs, albums, artists |
| **Settings** | Server URL, username, password, one-tap connection test |
| **Cover art** | Loaded from mStream's native `/album-art/` endpoint |
| **Scrobbling** | Playback reported to mStream → forwarded to Last.fm + ListenBrainz (no API keys needed) |
| **Dark theme only** | Deep purple Material You palette — no light mode, no compromise |

---

## Screenshots

*Coming soon.*

---

## Server compatibility

Sharesonic is built for **[mStream Velvet](https://github.com/aroundmyroom/mStream)** (7.5.x). It uses mStream Velvet's native API for browsing, streaming, sharing, shuffle, Auto-DJ, and playlist management, and the Subsonic compatibility layer for search only.

Generic Subsonic servers (Navidrome, Airsonic, etc.) are not supported yet — planned for a future release.

---

## Installation

### Direct download

1. Download the latest APK from [Releases](https://github.com/Tiritibambix/Sharesonic/releases/latest)
2. On your Android device: **Settings → Security → Install unknown apps** → allow your browser or file manager
3. Open the downloaded APK and install
4. Launch Sharesonic, enter your mStream server URL, username and password, tap **Test** then **Save**

### Obtainium (recommended — auto-updates)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium)
2. Tap the badge below or add `https://github.com/Tiritibambix/Sharesonic` manually

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Tiritibambix/Sharesonic"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="40"></a>

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

## How it works

Sharesonic uses two separate APIs from mStream:

### mStream native API (primary)

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/auth/login` | JWT authentication |
| `GET /api/v1/auth/refresh` | Refresh JWT on boot |
| `POST /api/v1/file-explorer` | Folder browsing + file metadata |
| `GET /media/<filepath>?token=<jwt>` | Audio streaming |
| `GET /album-art/<file>?token=<jwt>` | Cover art |
| `POST /api/v1/share` | Generate public share link (`time` = days) |
| `GET /api/v1/share/list` | List own share links |
| `DELETE /api/v1/share/:id` | Revoke a share link |
| `POST /api/v1/db/random-songs` | Random song for shuffle (called 30×) and Auto-DJ (called 1× with BPM/key/artist filters) |
| `GET /api/v1/lastfm/similar-artists` | Similar artists for Auto-DJ (proxied from Last.fm) |
| `GET /api/v1/playlist/getall` | List playlists |
| `POST /api/v1/playlist/load` | Load playlist tracks |
| `POST /api/v1/playlist/add-song` | Add track to playlist |
| `POST /api/v1/playlist/remove-song` | Remove track from playlist |
| `POST /api/v1/playlist/save` | Create / rename playlist |
| `DELETE /api/v1/playlist/:name` | Delete playlist |
| `POST /api/v1/lastfm/scrobble-by-filepath` | Scrobble to Last.fm at 50% |
| `POST /api/v1/listenbrainz/playing-now` | "Now playing" ping to ListenBrainz on track start |
| `POST /api/v1/listenbrainz/scrobble-by-filepath` | Scrobble to ListenBrainz at 50% |

### Subsonic API (search + scrobble for search results)

| Endpoint | Purpose |
|---|---|
| `search3` | Full-text search across songs, albums, artists |
| `scrobble` | Scrobble integer-ID songs (from search results) |

---

## Roadmap / known limitations

- No offline caching or download for offline playback
- No multiple server profiles
- No Android Auto support
- No lyrics

Contributions welcome. Open an issue before submitting a large PR.

---

## License

[GPL-3.0](LICENSE)
