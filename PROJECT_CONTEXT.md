# Project Context & AI Assistant Onboarding Guide

This document defines the working preferences, architectural foundation, and development lifecycle for this application codebase. Any AI assistant working on this project must read and strictly adhere to the guidelines below.

---

## 1. Core Principles & User Preferences

1. **Strictly Zero Emojis**:
   - Never use emojis anywhere: code, commit messages, comments, logs, documentation, or chat responses.
   - Use clean, professional language and native vector iconography only.
2. **Concise & Direct Responses**:
   - Provide direct summaries, actionable implementation details, and verification steps. Avoid excessive conversational filler.
3. **Clickable File Links**:
   - Always link referenced files, directories, classes, and methods using the `file://` URL scheme with forward slashes.
4. **Preserve Working Architecture**:
   - Do not perform destructive refactors without careful verification. Keep working features intact.

---

## 2. Environment & Build Strategy

- **Operating System**: Windows (PowerShell shell).
- **No Local Compilation / No Local Toolchain Installations**:
  - The local Windows environment does not have Java, Gradle, Cargo, Rust, or Node installed.
  - Never execute `./gradlew`, `gradle build`, or package managers on the host machine.
  - Never attempt to download or install JDK, Android SDK, or build tools locally.
- **Cloud CI with GitHub Actions**:
  - All builds and packaging are handled in the cloud using GitHub Actions runners.
  - Workflow resides in `.github/workflows/build.yml`.
  - Compiles the Android debug APK and uploads the artifact `catfeed-debug-apk`.

---

## 3. Application Architecture & Key Patterns

### PurrFeed
- **Language**: Kotlin 2.0+
- **Min SDK**: 24 (Android 7.0) | **Target SDK**: 35
- **Feed UI**:
  - Endless vertical card feed using `RecyclerView` and `PagerSnapHelper`.
  - Zero tabs: direct, continuous feed.
  - Top floating filter bar for switching between All, Photos, GIFs, and Liked cats.
  - First-time animated swipe indicator guiding users to swipe up.
- **Image & GIF Engine**:
  - `Coil 2.7.0` with `ImageDecoderDecoder` (Android 28+) and `GifDecoder` for smooth hardware-accelerated animated GIF rendering.
  - Proactive pre-fetching into memory and disk cache for adjacent cards.
- **Networking**:
  - `OkHttp 4.12.0` with connection pool and automatic retry.
  - Fallback resilience guaranteeing zero error screens on slow or failed network requests.
- **Local Persistence**:
  - AndroidX Room 2.6.1 with KSP for offline bookmarking and persistent like state.
