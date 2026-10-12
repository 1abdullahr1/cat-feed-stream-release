# PurrFeed - Endless Cat Stream

An infinite scroll cat image and GIF stream application for Android.

## Features
- Infinite Feed: Continuous, smooth vertical scrolling stream of cat photos and animated GIFs from Cataas.
- Pager Snapping: TikTok/Shorts style card snapping with full-bleed cards and smooth physics.
- GIF & Photo Filtering: Instantly filter between All Cats, Photos, GIFs, or Liked favorites without tab navigation.
- Double-Tap to Like: Fast heart burst animation and offline persistence via Room database.
- Proactive Preloading: Coil pre-decoding and OkHttp connection pooling for zero-lag scrolling.
- Resilient Networking: Automatic fallback and retry logic ensuring uninterrupted streaming without error screens.
- First-Launch Hint: Intuitive animated swipe guide on first app launch.

## Architecture & Tech Stack
- Language: Kotlin 2.0+
- Architecture: MVVM with StateFlow & Coroutines
- UI: Native Views, ViewBinding, RecyclerView with PagerSnapHelper, Material 3
- Image Loading: Coil 2.7.0 with GifDecoder (hardware accelerated)
- Networking: OkHttp 4.12.0 + Gson
- Persistence: AndroidX Room 2.6.1 (KSP)
- CI/CD: Automated cloud compilation and APK artifact generation via GitHub Actions
