## 1. Project Overview

### Purpose
Local-only Android media tracker for tracking consumption progress across Novels, Anime, Manga, and Games. No accounts, no cloud, no social. Privacy-first.

### Core Features
- Add media items via URL scraping (metadata auto-fill) or manual entry
- Track progress (current chapter/episode/volume) with optional total
- Toggle status: Reading / Completed
- View all items in a filterable list (by category)
- Tap item to open source URL in external browser
- Edit and delete items
- Backup/restore data to JSON files via Storage Access Framework (SAF)
- Merge or overwrite on import
- Dark theme with dynamic color support (Android 12+)
- Proper Settings screen (export, import, about)

### Non-Goals
- No user accounts
- No cloud sync
- No social features
- No login/authentication
- No multi-device support
- No extension/plugin system
- No push notifications
- No widget

---

## 2. Tech Stack Decisions

| Layer | Decision | Justification | Alternative Considered |
|-------|----------|---------------|----------------------|
| Language | **Kotlin** | Industry standard for Android | — |
| UI Framework | **Jetpack Compose + Material3** | Modern declarative UI, dynamic color, less boilerplate | Classic XML (rejected: more code, no dynamic theming) |
| DI | **Koin** | Lightweight, no annotation processing, fast builds | Hilt (rejected: slower builds, overkill for this size), Injekt (rejected: poorly documented) |
| Database | **Room** | Official Android SQLite wrapper, Flow support, migrations | SQLDelight (rejected: multiplatform overkill), SharedPreferences (rejected: data loss risk, no queries) |
| Networking | **OkHttp 4.x** | Industry standard, proven | Ktor (rejected: more complex for simple GET) |
| HTML Parsing | **Jsoup** | Mature, Kotlin-compatible | — |
| Image Loading | **Coil v3** | Kotlin-native, Compose support, OkHttp integration | Glide (rejected: Java-first, less Compose-native) |
| Serialization | **kotlinx.serialization** | Compile-time, no reflection, Kotlin-native | Gson (rejected: reflection-based, slower, no compile-time safety) |
| Navigation | **Navigation Compose** | Official Google solution, type-safe | Voyager (rejected: third-party, less support) |
| Architecture | **MVVM + Repository pattern** | Clean separation, testable | — |
| State Mgmt | **Kotlin Flow → StateFlow** | Reactive, lifecycle-aware | LiveData (rejected: less flexible, not Flow-compatible) |
| **Unit Testing** | **JUnit 5 (Jupiter) + MockK + Turbine** | All ViewModel, Repository, Scraper tests use Jupiter | — |
| **Compose UI Testing** | **JUnit 4 (via `ui-test-junit4`) + ComposeTestRule** | `createComposeRule()` only exists for JUnit4. Instrumented UI tests run under JUnit4 runtime via the `de.mannodermaus.android-junit5` Gradle plugin's backwards-compatible runner bridge. Pure unit tests stay on Jupiter. | — |
| Build | **Gradle version catalog** (`libs.versions.toml`) | Centralized dependency management | — |
| Min SDK | **26** (Android 8.0) | Matches old codebase, covers 95%+ devices | — |
| Target SDK | **34** | Latest stable | — |
| Package | **com.rockyxwall.lazydex** | Industry standard reverse-domain | — |

