# Sharesonic

> *Rediscover your music library. One shuffle at a time. Share what you find.*

Sharesonic is an Android client for **[mStream](https://mstream.io)** — the self-hosted music server. It is built around a single philosophy: **your music collection is too large to listen to linearly — let chance guide you, then share what surprises you.**

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
| **Share link** | Native mStream share API → public `server/shared/XXXXXXXXXX` URL → Android share sheet |
| **Now Playing** | Cover art, seek bar with elapsed/total time, artist · album info |
| **Queue view** | Swipe left from Now Playing — full scrollable queue, tap to jump |
| **Search** | Full-text search across songs, albums, artists |
| **Settings** | Server URL, username, password, one-tap connection test |
| **Cover art** | Loaded from mStream's native `/album-art/` endpoint |
| **Dark theme only** | Deep purple Material You palette — no light mode, no compromise |

---

## Screenshots

*Coming soon.*

---

## Server compatibility

Sharesonic is built for **[mStream Velvet](https://mstream.io)** (v7.x). It uses mStream's native API for browsing, streaming, sharing, and shuffle, and the Subsonic compatibility layer for search only.

Generic Subsonic servers (Navidrome, Airsonic, etc.) are not supported yet — planned for a future release.

---

## Installation

Sharesonic is distributed as a sideloaded APK. There is no Play Store release.

1. Download the latest APK from the [Releases](../../releases) page
2. On your Android device: **Settings → Security → Install unknown apps** → allow your browser or file manager
3. Open the downloaded APK and install
4. Launch Sharesonic, enter your mStream server URL, username and password, tap **Test** then **Save**

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
| `POST /api/v1/db/random-songs` | Random song for shuffle (called 30×) |

### Subsonic API (search only)

| Endpoint | Purpose |
|---|---|
| `search3` | Full-text search across songs, albums, artists |

---

## Roadmap / known limitations (v0.1.0)

- No offline caching or download for offline playback
- No playlist management
- No scrobbling (Last.fm / ListenBrainz)
- No multiple server profiles
- No Android Auto support
- No lyrics

Contributions welcome. Open an issue before submitting a large PR.

---

## License

[GPL-3.0](LICENSE)
