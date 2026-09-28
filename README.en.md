# Social Media — Native Android

<!-- TODO: ekran görüntüsü eklenecek -->

## Description

Social Media — Native Android is a fully native Android client for the
`sosyal-medya` backend (Flask + Supabase). It covers the core of a social media
app in Kotlin + Jetpack Compose: a feed, discover, Reels, 24-hour stories,
one-to-one and group messaging, plus voice/video calling over WebRTC (1:1) and
LiveKit (group). On the data side it talks to the backend's versioned REST layer
over Retrofit/OkHttp, uses Supabase Realtime for live message delivery and call
signalling, and caches the feed in Room. Authentication is opaque Bearer tokens
with Google Sign-In through Credential Manager. The app is distributed outside
the Play Store as APKs on GitHub Releases, with binary delta patching for
in-app updates.

Application code lives in `native-app/` — not at the repo root.

> [Türkçe sürüm](README.md)

## Tech Stack

- **Language / UI:** Kotlin, Jetpack Compose (Material3), MVVM (ViewModel + StateFlow)
- **DI:** Manual (`ServiceLocator.kt` wires every Repository/Api/Manager from a single point — no Hilt/Dagger, a deliberate simplicity call made during the MVP phase)
- **Networking:** Retrofit2 + Gson + OkHttp (kotlinx.serialization deliberately not used)
- **Authentication:** Opaque Bearer tokens against the backend's `/api/v1/*` endpoints (`api_tokens` table, stored via `EncryptedSharedPreferences`); also Google Sign-In through Credential Manager
- **Local cache:** Room (feed cache only — `data/local/`)
- **Realtime:** Supabase Realtime (`realtime-kt`, BOM 3.1.4) — `postgres_changes` subscription for messaging (silently falls back to polling on failure), and the backend's HMAC-derived public broadcast channels for 1:1 call signalling
- **Image/GIF loading:** Coil (`coil-compose` + `coil-gif`, an animated GIF needs its own decoder)
- **Camera:** CameraX (story creation — live preview, photo by tap, video by press-and-hold)
- **Video playback:** Media3/ExoPlayer (Reels, in-feed video — a single shared player pool)
- **1:1 voice/video calls:** `stream-webrtc-android` (Android bindings for Google WebRTC) + Supabase Realtime broadcast signalling
- **Group voice/video calls:** LiveKit (`livekit-android` + `livekit-android-compose-components`, via JitPack) — a system entirely separate from the 1:1 call stack
- **Embedded YouTube playback:** `android-youtube-player` (in the link preview card, a wrapper around the official IFrame Player API)
- **Audio output selection:** `com.twilio:audioswitch` (arriving transitively via LiveKit's `AudioSwitchHandler`) — speaker/headset/phone/Bluetooth; shared UI lives in `ui/components/AudioOutputButton.kt`
- **Push notifications:** Firebase Cloud Messaging (`FcmService.kt`)
- **Crash telemetry:** Firebase Crashlytics (same Firebase project, no extra console setup)
- **`minSdk 26` / `compileSdk 36` / `targetSdk 36`**, Kotlin 2.1.20, AGP 8.9.1, Gradle 8.11.1, Compose BOM 2024.12.01

## Setup

### Requirements

- JDK 17
- Android Studio (a current release — must support Kotlin 2.1.20 / AGP 8.9.1)
- Android SDK (`compileSdk`/`targetSdk` 36, `minSdk` 26 — installed via the Android Studio SDK Manager)

`native-app/app/google-services.json` (for Firebase Cloud Messaging + Crashlytics)
is already checked into the repo — no extra setup step.

### Steps

1. Open **`native-app/`** as the project root in Android Studio (not the repo root).
2. Wait for the Gradle sync — dependencies resolve automatically from `google()`, `mavenCentral()` and, for LiveKit, JitPack.
3. Run it (from Android Studio onto an emulator/device), or build a debug APK from the command line:

```bash
cd native-app
./gradlew assembleDebug
```

The APK lands in `native-app/app/build/outputs/apk/debug/`.

The backend address is hardcoded in `network/RetrofitClient.kt`
(`https://sosyalmedyadeneme.onrender.com/api/v1/`) — edit that file to point at a
different backend.

## Project Structure

```
native-app/
├── app/build.gradle.kts            # applicationId=com.umuterayaltay.sosyal.native
├── tools/                          # release.ps1 (build -> generate patch -> verify on-device via code -> upload) + PatchVerify.java + README.md
└── app/src/main/java/com/umuterayaltay/sosyal/nativeapp/
    ├── MainActivity.kt             # single Activity, Compose Navigation + App Links / shortcut / share intents
    ├── SosyalApplication.kt        # Application.onCreate() -> ServiceLocator.init() + Coil ImageLoader (GIF)
    ├── ServiceLocator.kt           # manual DI setup for every Repository/Api/Manager
    ├── auth/                       # GoogleSignInHelper — Credential Manager wrapper
    ├── data/                       # TokenStore, AppLockPreferenceStore, theme/update preferences
    │   └── local/                  # Room DB (feed cache only)
    ├── network/                    # Retrofit interfaces — a separate *Api.kt per feature area
    │                                # + RetrofitClient, AuthInterceptor, shared DTOs
    ├── repository/                 # wraps Api and presents a sealed Result to the ViewModel
    ├── player/                     # feed video playback pool (a single shared ExoPlayer)
    ├── service/                    # FcmService (push) + ActiveConversationTracker
    ├── webrtc/                     # WebRtcCallManager (1:1 call media layer)
    ├── update/                     # delta (zstd --patch-from) in-app update — ApkPatcher/Sha256/UpdateManifest/UpdateStorage/ZstdRefPrefix
    ├── widget/                     # SosyalAppWidgetProvider — home screen widget
    ├── navigation/                 # AppNavHost — all route definitions
    ├── viewmodel/                  # one ViewModel per screen (exposes StateFlow)
    └── ui/
        ├── theme/                  # Compose ColorScheme (light + dark)
        ├── components/             # composables shared across screens (media picker, link preview card, fullscreen image/video, audio output button)
        └── screens/                # each screen in its own file
```

## Features

- Feed, post creation (up to 4 images or a video up to 25 MB), post detail, discover, followable hashtags, trending pages
- Likes, comments, reposts, shares, bookmarks — with user-defined bookmark collections — emoji reactions, mentions, reporting a post
- Editing your own post / deleting / pinning / archiving
- Profile (Posts / Media / Liked / Saved / My Stickers / Archived tabs), profile editing, follower & following lists, follow requests, close friends list, blocked users, muting, insights
- Reels — a vertical swipeable video feed
- 24-hour stories: created with the camera (photo by tap, video by press-and-hold), a fullscreen canvas editor (multiple text layers, drag/zoom/rotate, 6 background + 9 text colours, GIF/sticker/poll overlays), viewing, reactions/replies, highlights, and a **story archive** that does not delete after 24 hours
- Messaging: one-to-one and group chats, group creation/management (rename, member list), forwarding, message search, sticker/GIF sending, per-conversation mute
- Voice/video calls: 1:1 (WebRTC) and group (LiveKit), with audio output device selection
- Notifications + notification preferences + push (FCM)
- Polls (on posts and stories)
- Link preview cards (including a tweet-style card), tap-to-play embedded video for YouTube links
- Drafts: save unwritten posts and publish them later
- Sticker gallery: create/save your own stickers, manage them from the profile tab
- Sign in with Google, two-factor authentication (2FA — TOTP enrolment, verification, disable), active sessions screen, forgot-password flow, account deactivation / reactivation
- Light/dark/system theme
- Biometric/PIN **app lock** (toggled in Settings, requested on cold start)
- Home screen **widget** (single purpose: a "New post" shortcut) and launcher **App Shortcuts** (long-press for New post / Notifications)
- **App Links** (share links under `https://sosyalmedyadeneme.onrender.com` open the app directly) and being accepted as a share target
- In-app update check (via the GitHub Releases API)

## Versions / Releases

The app ships outside the Play Store — APKs are uploaded to this repo's GitHub
Releases page (e.g. `native-v0.1.0`). The release APK is permanently narrowed to
arm64-v8a (122 → 58 MB, gated behind the `-PreleaseAbi` flag; dev/emulator builds
are unaffected).

The in-app update check uses the same Releases API, but tries a **binary delta
patch** before the full APK: zstd `--patch-from` produces the difference between
the installed and the new version and only that is downloaded (measured at ~300 KB
for a single-line change). On the device, JNA binds directly to `ZSTD_DCtx_refPrefix`
inside the `.so` that zstd-jni already embeds in the APK (no NDK/C compilation
required). The downloaded file is verified with SHA-256, there is a real progress
indicator plus a cancel button, and if the delta cannot be applied the app
visibly falls back to downloading the full APK. `REQUEST_INSTALL_PACKAGES` is
required to install. `native-app/tools/release.ps1` collapses the whole release
flow (capture the old APK → build → generate the patch → verify on-device via
code → upload) into a single command.
