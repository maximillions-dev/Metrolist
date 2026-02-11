# Metrolist Agent Guide

## 1. Process & Rules (CRITICAL)
- **Branching**: `fix/desc`, `feature/desc`, `refactor/desc`, `chore/desc`.
- **Commits**: `type(scope): short description` (e.g., `feat(ui): add dark mode`).
- **Forbidden**: Do **NOT** edit `README.md` or upstream `strings.xml`.
- **Strings**: **ONLY** add features/strings to `app/src/main/res/values/metrolist_strings.xml`.

## 2. Build & Verify
- **Reqs**: JDK 21, Go 1.20+, protoc.
- **Setup**: `git submodule update --init --recursive` then `cd app && bash generate_proto.sh`.
- **Build**: `./gradlew :app:assembleuniversalFossDebug` (APK in `app/build/outputs/apk/universalFoss/debug/`).
- **Lint**: `./gradlew lint` (Config: `lint.xml`).
- **Test**: No existing tests. Add unit tests to `app/src/test/kotlin/`. Run: `./gradlew test`.

## 2. Architecture & Stack
- **Stack**: Kotlin, Jetpack Compose, MVVM + Repository, Hilt (KSP), Coroutines/Flow, Room, Coil, Media3 (ExoPlayer).
- **Network**: `innertube` module (custom API), Ktor/OkHttp.
- **Nav**: Use `com.metrolist.music.ui.screens.Screens` sealed class.

## 3. Key Conventions
- **Strings**: **NEVER** edit `strings.xml` (upstream). **ONLY** add features/strings to `metrolist_strings.xml`.
- **Logging**: Use `Timber.d(...)`. For non-fatal errors: `com.metrolist.music.utils.reportException(e)`.
- **UI**: No XML. Use `@Composable`. Separate UI from logic (ViewModel). Use `StateFlow`.
- **Copyright**: All new files **MUST** have this header:
  ```kotlin
  /**
   * Metrolist Project (C) 2026
   * Licensed under GPL-3.0 | See git history for contributors
   */
  ```
- **Comments**: Minimal. Focus on *why*, not *what*. No conversational comments.

## 4. Structure
- `app/src/main/kotlin/com/metrolist/music/`:
  - `ui/screens/`: Feature screens.
  - `ui/component/`: Reusable components (Check first!).
  - `viewmodels/`: Business logic.
  - `di/`: Hilt modules.
- `innertube/`: YouTube Music API client.
