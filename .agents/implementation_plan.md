# LazyDex Rebuild — Implementation Plan

> **Audience**: This document is for an AI agent tasked with auditing, validating, and executing a full rebuild of the LazyDex Android application. The AI must be **merciless** — find every inconsistency, every missing edge case, every race condition, every performance trap, every deviation from best practices. Do not assume anything is correct. Verify everything. Question every decision below. If you find a flaw, flag it **and propose the proper correction** before proceeding. Every flagged issue must include a concrete, actionable fix — not just a warning. All proposed solutions must strictly follow the architectural rules, conventions, and decisions documented in this plan (e.g., domain purity, normalize() enforcement, atomic SQL, epoch-0 default, etc.). Do not suggest workarounds that bypass or contradict these requirements.

---

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

### Testing Dual-Track Decision (Resolved Contradiction)

JUnit 5 (Jupiter) is the framework for **all unit tests** — ViewModels, Repository, Scraper, Backup. Compose UI tests (`createComposeRule()`) rely on JUnit4 infrastructure. These coexist via the `de.mannodermaus.android-junit5` plugin which enables Jupiter on Android while keeping JUnit4 rule support. The concrete setup:

- `test/` (unit tests): Jupiter API + engine + MockK + Turbine + Kotest assertions
- `androidTest/` (instrumented UI tests): JUnit4 runner + Compose UI test rules

Both live in the same Gradle module. The `android-junit5` plugin makes this work without conflict. Do NOT attempt to use Jupiter-only for Compose UI tests — `createComposeRule()` is hard-wired to JUnit4's `@Rule`/`@ClassRule` annotation model.

---

## 3. Architecture & Project Structure

```
com.rockyxwall.lazydex/
├── LazyDexApp.kt                          # Application class (Koin init)
├── MainActivity.kt                        # Single Activity hosting Compose (~30 lines)
│
├── data/
│   ├── local/
│   │   ├── LazyDexDatabase.kt              # Room database (exportSchema = false for v1)
│   │   ├── entity/MediaItemEntity.kt       # Room entity
│   │   ├── dao/MediaItemDao.kt             # DAO with Flow queries + atomic increment/decrement
│   │   └── converter/Converters.kt         # Type converters
│   └── repository/
│       └── MediaRepositoryImpl.kt          # Implements domain repository
│
├── domain/
│   ├── model/
│   │   ├── MediaItem.kt                    # Domain model + normalize()
│   │   ├── MediaCategory.kt                # NOVEL, ANIME, MANGA, GAME
│   │   └── UserStatus.kt                   # READING, COMPLETED
│   └── repository/
│       └── MediaRepository.kt              # Interface (contract)
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                        # Material3 theme with dynamic color
│   │   ├── Color.kt                        # Custom color palette
│   │   └── Type.kt                         # Typography
│   ├── navigation/
│   │   └── NavGraph.kt                     # NavHost + route definitions
│   ├── home/
│   │   ├── HomeScreen.kt                   # Main list + filter chips + FAB
│   │   └── HomeViewModel.kt
│   ├── add/
│   │   ├── AddItemScreen.kt                # Bottom sheet: URL scrape + manual form
│   │   └── AddItemViewModel.kt
│   ├── edit/
│   │   ├── EditItemScreen.kt               # Full edit form
│   │   └── EditItemViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt               # Export, import, about
│   │   └── SettingsViewModel.kt
│   └── components/
│       ├── MediaCard.kt                    # Single item card
│       ├── CategoryFilterChips.kt          # Filter row
│       ├── CategoryBadge.kt                # Color-coded category label
│       ├── StatusBadge.kt                  # Reading/Completed label
│       ├── ProgressControls.kt             # Increment/decrement buttons
│       └── EmptyState.kt                   # "Nothing here yet" placeholder
│
├── di/
│   └── Modules.kt                          # Koin modules (Room, Repository, ViewModels, Scraper)
│
├── scraper/
│   └── MetadataScraper.kt                  # OkHttp + Jsoup metadata extraction (NO WebView, uses UrlNormalizer)
│
├── util/
│   └── UrlNormalizer.kt                    # Single canonical URL normalizer (used by scraper + duplicate detection)
│
├── backup/
│   ├── BackupManager.kt                    # SAF read/write operations
│   └── BackupProcessor.kt                  # Serialize/deserialize/merge logic
│
└── res/
    ├── values/strings.xml                  # All user-facing strings
    ├── values/themes.xml                   # Minimal (splash screen only)
    └── drawable/                           # Vector icons, etc.
```

### Key Architectural Rules

1. **Domain layer has zero Android dependencies**. `MediaItem`, `MediaCategory`, `UserStatus`, `MediaRepository` interface — pure Kotlin. No Context, no Parcelable, no Android types.
2. **ViewModels never reference Fragment or Activity types**. No `context` in ViewModels. Use `SavedStateHandle` for nav args if needed.
3. **Repository is the single source of truth**. UI never reads from Room directly. Never caches data outside the repository.
4. **Scraper returns a domain model, not a parcelable**. `MetadataScraper` returns `ScrapedMetadata(title: String, imageUrl: String)` — pure data.
5. **Screens are stateless Composables**. All state lives in ViewModels. Screens observe `StateFlow` and emit events.
6. **The Activity does nothing but set ComposeContent**. No logic in `MainActivity`.
7. **Every write path goes through `MediaItem.normalize()`** before persisting. This is the canonical invariant enforcer (progress capping, bounds, etc.) and CANNOT be bypassed by any caller including import.

---

## 4. Detailed Feature Specifications

### 4.1 Data Model

```kotlin
// domain/model/MediaCategory.kt
enum class MediaCategory(val displayName: String) {
    NOVEL("Novel"),
    ANIME("Anime"),
    MANGA("Manga"),
    GAME("Game");

    companion object {
        /** Case-insensitive lookup. Returns null for unknown values. */
        fun fromString(value: String): MediaCategory? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

// domain/model/UserStatus.kt
enum class UserStatus(val displayName: String) {
    READING("Reading"),
    COMPLETED("Completed");

    companion object {
        /** Case-insensitive lookup. Returns null for unknown values. */
        fun fromString(value: String): UserStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

// domain/model/MediaItem.kt
data class MediaItem(
    val id: String,              // UUID string
    val category: MediaCategory,
    val title: String,
    val sourceUrl: String,
    val coverImageUrl: String,   // Can be empty
    val currentProgress: Int,    // Always >= 0, and <= totalItems when total is non-null
    val totalItems: Int?,        // null = unknown/ongoing
    val userStatus: UserStatus,
    val lastUpdated: Long        // System.currentTimeMillis()
) {
    /**
     * Canonical normalization — run before EVERY write (add, update, import, merge).
     * - Trims whitespace from title, sourceUrl, coverImageUrl
     * - Clamps currentProgress to [0, totalItems] when totalItems is non-null
     * - Clamps currentProgress >= 0 when totalItems is null
     * This is the SINGLE invariant enforcement point.
     */
    fun normalize(): MediaItem {
        val safeTotal = totalItems?.takeIf { it >= 0 }
        val safeProgress = when {
            currentProgress < 0 -> 0
            safeTotal != null && currentProgress > safeTotal -> safeTotal
            else -> currentProgress
        }
        return copy(
            title = title.trim(),
            sourceUrl = sourceUrl.trim(),
            coverImageUrl = coverImageUrl.trim(),
            totalItems = safeTotal,
            currentProgress = safeProgress
        )
    }
}

// data/local/entity/MediaItemEntity.kt
@Entity(
    tableName = "media_items",
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val category: String,        // Stored as uppercase string (e.g. "NOVEL"), no ambiguity
    val title: String,
    val sourceUrl: String,
    val coverImageUrl: String,
    val currentProgress: Int,
    val totalItems: Int?,
    val userStatus: String,      // Stored as uppercase string (e.g. "READING")
    val lastUpdated: Long
)
```

**AI Verification Checklist**:
- `currentProgress` invariant enforced in `normalize()` — never below 0, never above totalItems when total is non-null
- `normalize()` must be called by repository on every write (add, update, increment, decrement, setStatus, replaceAll). The repository is responsible, not the ViewModel
- **ID generation rule** (two distinct paths):
  - `repository.add()` (UI-entered items) generates a fresh UUID via `UUID.randomUUID()`. This is the **only** place new UUIDs are created. ViewModels never call `UUID.randomUUID()`.
  - **Import/merge** preserves the ID from the JSON file. A new UUID is generated ONLY if the imported item's `id` is missing, null, or empty (see 4.6 deserialization rules). This is critical: if import blindly generated new UUIDs, merge-by-ID would always treat imported items as new, creating duplicates.
- `totalItems` being null means "unknown" — progress can go arbitrarily high (within Int range)
- `lastUpdated` must be set on every mutation (add, edit, increment, decrement, status change)
- Entity ↔ Domain mapping uses `MediaCategory.fromString()` and `UserStatus.fromString()` — case-insensitive, defaults to null for unknown values → fail closed (reject the item or skip). The repository must wrap the mapping in try/catch inside `mapNotNull` to prevent a single corrupted row (e.g., from a future backup restore introducing "PODCAST") from crashing the entire Flow stream with a NullPointerException
- Room TypeConverters or column types? **Decision**: store enum strings directly as columns, use `@TypeConverters` only if you need reusable conversion. Since the DAO already works with raw strings, skip TypeConverters. The repository handles entity↔domain mapping.
- **`sourceUrl` has a SQLite UNIQUE index** (`@Entity(indices = [Index(value = ["sourceUrl"], unique = true)])`). This is a database-level safeguard — duplicate URL detection is NOT solely the UI's responsibility. The repository must catch `SQLiteConstraintException` and map it to a domain exception. See Section 4.4 for error handling.

### 4.2 Room Database

```kotlin
@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = false)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
}
```

**`exportSchema = false` Decision**: For v1 there are zero migrations. Schema JSON output is unnecessary until a v2 migration is written. When that happens, flip to `true` and configure `room.schemaLocation` in `build.gradle.kts`.

**AI Verification Checklist**:
- Database name: `"lazydex_db"` (constant)
- `fallbackToDestructiveMigration()` — acceptable for v1 since there are no migrations. Add a `Log.w()` on destructive migration so developers know data was wiped
- Room `.build()` is lazy — corruption surfaces on first query, not at construction. Wrap first DAO access in try/catch, not the builder call
- Never use `allowMainThreadQueries()` — all DB ops go through coroutines
- Enable WAL mode via `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` for better concurrent read/write

### 4.3 DAO

```kotlin
@Dao
interface MediaItemDao {
    // Reactive queries
    @Query("SELECT * FROM media_items ORDER BY lastUpdated DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY lastUpdated DESC")
    fun observeByCategory(category: String): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items")
    fun observeCount(): Flow<Int>

    // Reactive single-item observation (used by EditItemViewModel)
    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: String): Flow<MediaItemEntity?>

    // Lightweight health check — SELECT 1 evaluates instantly without table scans
    @Query("SELECT 1")
    suspend fun healthCheck(): Int?

    // One-shot reads
    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM media_items WHERE sourceUrl = :url LIMIT 1)")
    suspend fun existsByUrl(url: String): Boolean

    // Writes — using @Upsert (Room 2.5+) instead of @Insert(onConflict = REPLACE)
    // NOTE: @Upsert preserves the SQLite rowid vs INSERT(REPLACE) which deletes+reinserts.
    // HOWEVER: Room's invalidation tracker fires at the TABLE level, not the row level.
    // ANY write to media_items (UPDATE, INSERT, DELETE) triggers re-evaluation of ALL
    // active Flow queries on that table regardless of rowid changes. @Upsert alone does
    // NOT prevent false-positive Flow re-emissions. The Repository layer MUST apply
    // .distinctUntilChanged() after mapping to domain to suppress table-level noise.
    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    /**
     * Atomic progress increment — uses single SQL UPDATE with MIN() capping.
     * No read-modify-write race condition. No debounce needed.
     */
    @Query("""
        UPDATE media_items
        SET currentProgress = MIN(currentProgress + 1, COALESCE(totalItems, currentProgress + 1)),
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun atomicIncrement(id: String, now: Long)

    /**
     * Atomic progress decrement — uses single SQL UPDATE with MAX() floor.
     * Mirrors increment but in reverse: floor at 0, never below.
     * Cannot be a copy-paste of increment (MIN vs MAX, +1 vs -1, no COALESCE needed).
     */
    @Query("""
        UPDATE media_items
        SET currentProgress = MAX(currentProgress - 1, 0),
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun atomicDecrement(id: String, now: Long)

    /**
     * Atomic status update.
     */
    @Query("""
        UPDATE media_items
        SET userStatus = :status,
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: String, now: Long)

    // Deletes
    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    /**
     * Atomic replaceAll — runs in a single transaction.
     * Prevents data loss if process dies between deleteAll and upsertAll.
     */
    @Transaction
    @Query("DELETE FROM media_items")
    suspend fun clearAndInsert(items: List<MediaItemEntity>)
}
```

The `clearAndInsert` method above is wrong — `@Transaction` + `@Query` works for a single DELETE, not DELETE+INSERT. The correct approach is a default method in the DAO interface:

```kotlin
@Transaction
suspend fun replaceAll(items: List<MediaItemEntity>) {
    deleteAll()
    // Chunk the upsert to avoid building one giant in-memory entity list.
    // 500 items per chunk balances memory vs number of SQL statements.
    // For a 10k-item import, this runs 20 batch inserts inside the same
    // atomic transaction — still atomic, not one monolithic SQL blob.
    // Note: chunking does NOT shorten the SQLite write lock in rollback
    // journal mode — a single @Transaction holds the write lock for the
    // entire duration regardless of chunking. The memory win alone makes
    // chunking worthwhile (avoids OOM on 10k-item upsertAll). The real
    // benefit for read concurrency comes from WAL mode (setJournalMode),
    // where readers aren't blocked during the write transaction at all.
    // Chunk size is 100 because MediaItemEntity has 9 columns.
    // 100 * 9 = 900 bound variables — safely under Android's SQLite limit of 999.
    // Using 500 would produce 4,500 bound variables, crashing on API ≤ 30.
    items.chunked(100).forEach { chunk -> upsertAll(chunk) }
}
```

Note: `@Transaction` on an interface default method works because Room generates the wrapper class. The function does NOT need to be `open`. The entire `deleteAll() + chunked upserts` is one atomic unit — if any chunk fails, SQLite rolls back the entire transaction including all prior chunks and the delete. No data loss. The chunk size of 100 keeps bound variables under the SQLite limit of 999 (9 columns × 100 = 900). It does NOT shorten the write lock — the lock is held for the full transaction regardless. For read concurrency during a write, configure WAL mode via `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` on the Room builder.

**AI Verification Checklist**:
- `observeAll()` returns `Flow` — Room runs this on a background thread automatically
- `observeByCategory()` filters by category string — must match uppercase enum names ("NOVEL", "ANIME", etc.)
- `observeById()` returns `Flow<MediaItemEntity?>` — used by EditItemViewModel to reactively observe the latest data from Room without passing the full domain model through navigation arguments
- `atomicIncrement` and `atomicDecrement` use single SQL UPDATE statements — no read-modify-write race condition possible. This makes the "rapid tap" race condition a non-issue. The user can tap [+] 100 times and every increment is individually atomic
- `updateStatus` is also a single atomic SQL UPDATE — no race condition
- `replaceAll` is annotated with `@Transaction` and calls `deleteAll()` + `upsertAll()` in the right order. If the process dies mid-transaction, Room rolls back — no data loss
- DAO methods must never be called from the main thread (Room enforces this by default with `suspend` and `Flow`)
- `@Upsert` (Room 2.5+) is used instead of `@Insert(onConflict = REPLACE)` to avoid deleting+reinserting the row (which changes the SQLite rowid). **However, this does NOT prevent false-positive Flow re-emissions** — Room's invalidation tracker fires at the *table* level, not the *row* level. ANY write to `media_items` causes ALL active Flow queries on that table to re-evaluate. The Repository layer applies `.distinctUntilChanged()` after domain mapping to suppress table-level noise (see Section 4.9).

### 4.4 Repository

```kotlin
interface MediaRepository {
    /** Reactive observation — UI bindings */
    fun observeAll(): Flow<List<MediaItem>>
    fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>>
    fun observeById(id: String): Flow<MediaItem?>  // Single-item observation for EditItemViewModel

    /** One-shot reads */
    suspend fun getById(id: String): MediaItem?
    suspend fun getAll(): List<MediaItem>   // For backup export
    suspend fun existsByUrl(url: String): Boolean  // SQLite-bound, near-zero memory

    /** Writes — ALL go through MediaItem.normalize() before persisting */
    suspend fun add(item: MediaItem): MediaItem           // generates id + sets lastUpdated
    suspend fun update(item: MediaItem)                    // caller provides full item
    suspend fun delete(id: String)

    /** Atomic operations (delegated to DAO-level SQL) */
    suspend fun incrementProgress(id: String)
    suspend fun decrementProgress(id: String)
    suspend fun setStatus(id: String, status: UserStatus)

    /** Bulk operations — atomic via @Transaction */
    suspend fun replaceAll(items: List<MediaItem>)         // clears and inserts atomically
}
```

**Implementation Contract** (every write path explicitly listed — no exceptions):
- `add()` → `item.copy(id = UUID.randomUUID().toString(), lastUpdated = System.currentTimeMillis()).normalize()` → `dao.upsert(entity)`
- `update()` → `item.copy(lastUpdated = System.currentTimeMillis()).normalize()` → `dao.upsert(entity)`. **This includes the Edit screen**: user manually types progress=500, total=100 → `normalize()` clamps progress to 100 before DB write. The repository does NOT trust that ViewModel data is already valid.
- `incrementProgress()` → `dao.atomicIncrement(id, System.currentTimeMillis())` — no read needed, SQL handles capping
- `decrementProgress()` → `dao.atomicDecrement(id, System.currentTimeMillis())` — no read needed, SQL handles flooring
- `setStatus()` → `dao.updateStatus(id, status.name, System.currentTimeMillis())`
- `replaceAll()` → normalizes every item, then calls `dao.replaceAll(normalizedEntities)`

**Single Canonical URL Normalizer** — used by ALL code paths (scraper, duplicate detection, validation). There is exactly one normalizer. If it changes, every consumer changes with it. No drift permitted.

```kotlin
// util/UrlNormalizer.kt
import java.net.URI

object UrlNormalizer {
    /**
     * Canonical URL normalization for duplicate detection + scrape validation.
     * Uses java.net.URI (pure Java/Kotlin, no Android dependency) to safely
     * parse and normalize without destroying query parameters or path segments
     * via naive string manipulation.
     * - Lowercase scheme + host
     * - Trim whitespace
     * - Remove trailing slash
     * - Strip fragment (#...) via URI builder
     * Does NOT validate the URL (use UrlValidator for that).
     */
    fun normalize(url: String): String {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()

            // Reconstruct without fragment
            val normalized = URI(
                scheme,
                uri.userInfo,
                host,
                uri.port,
                uri.path,
                uri.query,
                null // Strip fragment
            ).toString()

            normalized.removeSuffix("/")
        } catch (e: Exception) {
            url.trim()
        }
    }
}
```

The scraper in 4.5 calls `UrlNormalizer.normalize(url)` for its normalization step. The duplicate detection in the repository calls the same `UrlNormalizer.normalize(url)` on both the new URL and all existing `sourceUrl` values. Two different implementations would drift — this must be one file.

**AI Verification Checklist**:
- `add()` generates UUID and sets lastUpdated — this is the **only** place UUIDs are generated. ViewModels never call `UUID.randomUUID()`
- `normalize()` is called in the repository on every write path — NOT in the ViewModel, NOT in the UI
- `replaceAll()` normalizes every item individually — this handles the import-bypass gap. Even if a corrupted backup has `currentProgress = -5`, it gets clamped to 0 before hitting the DB
- Atomic increment/decrement means NO debounce in the ViewModel. "Rapid tap" is safe. Remove the debounce suggestion from 4.10.1
- Optimistic locking (stale-edit detection) is NOT implemented for v1. See 4.10.5 for rationale
- Error handling: repository wraps DB exceptions and rethrows as `DataAccessException` (a custom sealed class or RuntimeException subclass). Alternatively, use Kotlin `Result<T>` for all write operations
- **UNIQUE constraint on `sourceUrl`**: The entity has a SQLite UNIQUE index on `sourceUrl`. If the repository's `add()` or `update()` triggers a `SQLiteConstraintException` (e.g., an edge case bypasses the UI-level duplicate check), the repository must catch it and map it to a `DuplicateUrlException` domain exception. Do NOT let raw constraint violations propagate to the ViewModel.

### 4.5 Metadata Scraper

```kotlin
class MetadataScraper(private val okHttpClient: OkHttpClient) {
    suspend fun scrape(url: String): Result<ScrapedMetadata>

    data class ScrapedMetadata(
        val title: String,
        val imageUrl: String
    )
}
```

**Scraping Strategy**:
1. Validate URL format before making request
2. Normalize URL via `UrlNormalizer.normalize(url)`
3. GET the page with OkHttp (custom User-Agent, 15s read timeout)
4. Parse HTML with Jsoup
5. Extract `og:title` or `twitter:title` or `<title>` (in that priority)
6. Extract `og:image` or `twitter:image` or first `<img>` (in that priority)
7. For `og:image` with relative URL: resolve to absolute using `Jsoup`'s `absUrl()`
8. For `og:image` scheme: **keep as-is**. Do NOT upgrade http→https. Do NOT HEAD-check. Coil will handle connection errors on its own retry/fallback. Blind-upgrade breaks http-only images. HEAD-check adds an extra round-trip and many servers 405/block HEAD even when GET works.
9. Return `Result.success` or `Result.failure` with clear error message

**URL Validation Rules**:
- Must start with `https://` (reject `http://` and non-http schemes)
- Must be a valid URL (use `java.net.URL` or regex — prefer regex to avoid URL constructor side effects)
- Max length: 2048 characters (standard URL max)
- Reject IP-address-based URLs (security: no `https://192.168.x.x` or `https://10.x.x.x`)
- Reject known malicious patterns (e.g., URLs containing JavaScript schemes, `data:` URIs, `file://`)
- Normalize before duplicate check: lowercase scheme + host, trim trailing slashes, strip fragments

**Edge Cases & Errors the AI MUST Handle**:
- URL is empty or blank
- URL is malformed (no dots, spaces, invalid characters)
- URL is `http://` not `https://` — reject with "Only HTTPS URLs are supported"
- URL is a localhost/intranet IP — reject with "Only public URLs are supported"
- URL points to a binary file (PDF, ZIP, image) — the page returns non-HTML
- Network timeout (15s read timeout, but site takes 30s)
- DNS resolution failure
- SSL handshake failure (expired cert, wrong host)
- Server returns 4xx or 5xx
- Server returns empty body
- Server returns malformed HTML (no `<head>`, no `<title>`, unclosed tags)
- Page is behind a login wall (returns login page HTML, not real content)
- Page requires cookies/accept headers (some sites return 403 without proper headers)
- Page uses Cloudflare/DDOS protection (returns challenge page)
- Page redirects multiple times (OkHttp follows redirects by default, but set max redirects to 5)
- Page is extremely large (>5MB) — abort and fail. CRITICAL: Content-Length header may be absent or lie (chunked transfer encoding). Always enforce a hard byte limit at the Okio source level via `body.source().peek().readByteString(5 * 1024 * 1024)` BEFORE calling Jsoup.parse(). Never call `response.body.string()` on an unbounded response — that loads the entire payload into memory and causes OOM. Jsoup.parse() is CPU-bound and MUST run on Dispatchers.Default, NOT on OkHttp's IO dispatcher pool.
- `og:title` exists but is empty string
- `og:image` exists but is a relative URL — resolve to absolute using `Jsoup`'s `absUrl("content")`
- `og:image` scheme is `http://` — keep as-is. Coil handles http images. Do NOT try to upgrade to https.
- Title extracted is garbage (e.g., "404 Not Found", "Access Denied", "Error") — heuristic rejection check
- Multiple languages in title (e.g., English primary with Japanese subtitle) — keep as-is
- Character encoding issues (ISO-8859-1 vs UTF-8 pages) — Jsoup handles this
- JavaScript-rendered pages (Jsoup can't execute JS — this is intentional. If site requires JS, scraping fails gracefully. NO WebView fallback.)
- Rate limiting / 429 Too Many Requests — the scraper returns the failure message. No automatic retry.

**AI MUST verify**:
- OkHttp client is configured with proper timeouts (connect: 10s, read: 15s, write: 10s)
- `followRedirects = true`, `followSslRedirects = true`, max redirects = 5
- User-Agent is set to a modern mobile browser string (e.g., Chrome Android latest)
- No cookies are persisted between requests (`NoCookieJar` or null CookieJar)
- The request is a GET with standard headers only (no custom auth headers)
- Scraper is injectable (takes OkHttpClient as constructor parameter) — testable
- `scrape()` is a `suspend` function — MUST use `suspendCancellableCoroutine` with `call.enqueue()` to properly propagate coroutine cancellation to OkHttp. Simply wrapping `call.execute()` in `withContext(Dispatchers.IO)` is WRONG — OkHttp's blocking `execute()` ignores coroutine cancellation until the read timeout (15s). The correct pattern:
  ```kotlin
  suspend fun scrape(url: String): Result<ScrapedMetadata> = suspendCancellableCoroutine { continuation ->
      val request = Request.Builder().url(url).build()
      val call = okHttpClient.newCall(request)
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(object : Callback {
          override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body ?: throw IOException("Empty body")
                    // Check Content-Length header first — fast rejection for known-large
                    val contentLength = body.contentLength()
                    if (contentLength > 5 * 1024 * 1024) throw IOException("File too large")
                    // BoundedInputStream: streams directly to Jsoup without buffering
                    // the entire HTML as a contiguous 5MB byte array + String.
                    // Eliminates the intermediate Okio ByteString allocation.
                    val boundedStream = object : InputStream() {
                        private val delegate = body.byteStream()
                        private var bytesRead = 0L
                        override fun read(): Int {
                            if (bytesRead >= MAX_SCRAPE_BYTES) return -1
                            val result = delegate.read()
                            if (result != -1) bytesRead++
                            return result
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val remaining = MAX_SCRAPE_BYTES - bytesRead
                            if (remaining <= 0) return -1
                            val actual = delegate.read(b, off, minOf(len, remaining.toInt()))
                            if (actual != -1) bytesRead += actual
                            return actual
                        }
                    }
                    // Jsoup is CPU-bound — MUST run on Dispatchers.Default, not OkHttp's pool.
                    // Use withContext instead of launching an unstructured coroutine to
                    // avoid continuation leaks and ensure proper cancellation propagation.
                    val doc = withContext(Dispatchers.Default) {
                        Jsoup.parse(boundedStream, "UTF-8", url)
                    }
                    // ... extraction logic ...
                    continuation.resume(Result.success(...))
                } catch (e: Exception) {
                    continuation.resume(Result.failure(e))
                } finally {
                    response.close()
                }
          }
          override fun onFailure(call: Call, e: IOException) { /* resume */ }
      })
  }
  ```
- `Result.failure` includes a meaningful error message, not just the exception
- No memory leaks: OkHttp response body must be closed (`Response.use {}` or ensure body is consumed), Jsoup Document must not leak

### 4.6 Backup System

```kotlin
object BackupProcessor {
    suspend fun serialize(items: List<MediaItem>): String       // kotlinx.serialization JSON
    suspend fun deserialize(json: String): List<MediaItem>
    suspend fun merge(local: List<MediaItem>, imported: List<MediaItem>): List<MediaItem>
}

object BackupManager {
    suspend fun export(context: Context, uri: Uri, items: List<MediaItem>)
    suspend fun import(context: Context, uri: Uri): List<MediaItem>
}
```

**Backup JSON Schema** (backward-compatible with old codebase):
```json
{
    "schemaVersion": 1,
    "items": [
        {
            "id": "uuid-string",
            "category": "NOVEL",
            "title": "Example",
            "sourceUrl": "https://...",
            "coverImageUrl": "https://...",
            "currentProgress": 42,
            "totalItems": 100,
            "userStatus": "READING",
            "lastUpdated": 1700000000000
        }
    ]
}
```

**Backward Compatibility with Old (Gson) Codebase**:
- The old codebase used Gson with `@SerializedName` that matched the exact field names above
- The old codebase likely did NOT write a `schemaVersion` field (it was added in the new app)
- **Decision**: `schemaVersion` defaults to `1` when missing. This is the most important backward-compat rule — do NOT reject files missing `schemaVersion`
- The old codebase likely did NOT write `lastUpdated` (it was added later). **Decision**: `lastUpdated` defaults to **epoch 0** (`0L`) when missing, null, or 0. NOT import time. Rationale: if untimestamped imported items got `System.currentTimeMillis()`, they'd be newer than every local item, causing merge ("keep newest") to silently clobber genuinely newer local edits. Epoch 0 means local always wins conflicts against untimestamped imports — safe default. Merge tiebreak (`local wins when timestamps equal`) then also favors local for any imported items that were also stamped 0.
- `category` in old files may be stored as `NOVEL`, `Novel`, or `novel` — handle case-insensitively
- `userStatus` similarly may vary — handle case-insensitively
- The old Gson serializer used `@SerializedName(nullable = true)` for `totalItems` and `coverImageUrl` — ensure kotlinx.serialization handles nullable fields correctly

**Deserialization Rules**:
- `schemaVersion` missing → treat as `1`
- `schemaVersion` > `1` → reject with "Unsupported backup schema version: X" (future-proofing)
- `schemaVersion` is present but not an integer → reject as malformed
- `items` field missing or null → return empty list (don't crash)
- Individual item with missing `id` → generate a new UUID (don't reject the whole import)
- Individual item with missing `title` → reject that item (data is corrupted)
- `category` string doesn't match any `MediaCategory` value → reject that item
- `userStatus` string doesn't match any `UserStatus` value → default to `READING`
- `currentProgress` negative or null → clamp to 0 during deserialization
- `totalItems` negative → treat as `null`
- `lastUpdated` is 0, negative, or missing → set to epoch 0 (0L). NOT `System.currentTimeMillis()`. Rationale: stamping imported items with import time makes them newer than every local item, so merge ("keep newer") silently clobbers genuine local edits. Epoch 0 is the safe default — local always wins conflicts against untimestamped imports. Note: `merge()` will correct pure-addition items (no local counterpart) to `System.currentTimeMillis()` — see merge logic below. Deserialization itself stays conservative (epoch 0) because it has no knowledge of which items have local counterparts.
- Empty file or blank string → throw `IllegalArgumentException("Backup file is empty")`
- File > 10MB → abort with "Backup file is too large"

**Export Rules**:
- Write to temp file first: `File(context.cacheDir, "temp_backup.json")`. Prevents partial-file corruption if write fails mid-way.
- Transfer to SAF target URI via content resolver: open an output stream to the SAF URI via `context.contentResolver.openOutputStream(uri, "wt")` and copy using `tempFile.inputStream().copyTo(outputStream)`. SAF URIs use `content://` scheme — `java.io.File.renameTo()` cannot target a content URI and will throw a `SecurityException`. Use `"wt"` (write + truncate) rather than `"w"` because on some OEM Android variants, `"w"` overwrites the beginning of the file but does not truncate remaining bytes, leaving trailing garbage if the new backup is smaller than the previous one at the same URI.
- Delete the temporary file after successful transfer.
- Use `BufferedWriter` with UTF-8 encoding, no BOM
- All items are normalized before serialization (redundant but safe — they should already be normalized)

**Merge Logic**:
- Keyed by `id` (UUID string)
- For conflicts, keep the item with the newer `lastUpdated` timestamp
- If timestamps are equal, keep local (deterministic: local wins)
- Items only in local → keep as-is
- Items only in imported → preserve valid timestamps from JSON; fall back to staggered timestamps only for items that lack a valid timestamp. Rationale: if the user exported their items (which have real historical timestamps), then uninstalled/reinstalled and imported the same file, every item is a "pure addition". Blindly overwriting with `System.currentTimeMillis() - index` would destroy the user's entire chronological history. The merge must check `importedItem.lastUpdated > 0L` first. For items with a valid timestamp, keep it. For items without (e.g., from the old Gson app that had no `lastUpdated` field), stamp with staggered import time: `val finalTime = if (importedItem.lastUpdated > 0L) importedItem.lastUpdated else System.currentTimeMillis() - index`.
- Use `LinkedHashMap` to preserve insertion order deterministically
- Normalize all items in the merged result before returning
- Note on result ordering: the array order from merge is consumed only by JSON export. The UI (`observeAll()` — see 4.3) always re-sorts by `ORDER BY lastUpdated DESC`, so merge's insertion order is invisible on screen. This is correct — users expect newly imported items to appear at the top of their list, not appended at the bottom.

**AI Verification Checklist**:
- `schemaVersion` missing → defaults to 1 (NOT an error). Verify this with unit test using a JSON string without schemaVersion
- `category` case-insensitive: `"novel"`, `"Novel"`, `"NOVEL"` all map to `MediaCategory.NOVEL`
- `userStatus` case-insensitive: `"reading"`, `"Reading"`, `"READING"` all map to `UserStatus.READING`
- Export uses temp-file write + contentResolver.copyTo() to prevent partial corruption (NEVER renameTo() on a content:// URI)
- Merge preserves order deterministically (LinkedHashMap)
- Merge normalizes every item in the result
- **Merge stamps pure-additions with `System.currentTimeMillis()`**: items present only in the imported list get import time as `lastUpdated`, NOT epoch 0. Items that existed on both sides retain their conflict-resolved timestamp (epoch 0 → local wins). Verify with a test: local items A+B, imported items B+C → result has A (local lastUpdated), B (local wins), C (≈ now).
- `replaceAll` imports through repository — goes through `normalize()` (invariant enforcement)
- Serialization uses kotlinx.serialization with `@SerialName` annotations if field names differ from Kotlin property names
- Test round-trip: serialize → deserialize → serialize → compare
- Test with actual backup file from the old Gson-based app

### 4.7 UI Screens

#### HomeScreen
- TopAppBar with title "LazyDex" and settings gear icon
- Filter chips row: [All] [Novels] [Anime] [Manga] [Games] — single selection, "All" by default
- LazyColumn of MediaCards, or EmptyState when list is empty
- FAB: + icon to open AddItemSheet
- Each card shows: cover image (async via Coil), title, category badge, status badge, progress (current/total), last updated relative time, increment [+] / decrement [-] buttons
- Tap card body → open source URL in external browser
- Tap status badge → toggle Reading/Completed (with popup or inline toggle)
- Tap [+] → increment progress (atomic — goes to repository, no need to read current value)
- Tap [-] → decrement progress (atomic)
- Long press card → context menu: Edit / Delete

**State Management**:
```kotlin
data class HomeUiState(
    val items: List<MediaItem> = emptyList(),
    val selectedCategory: MediaCategory? = null,  // null = "All"
    val isLoading: Boolean = true
)

sealed interface HomeEvent {
    data class Increment(val itemId: String) : HomeEvent
    data class Decrement(val itemId: String) : HomeEvent
    data class ToggleStatus(val itemId: String) : HomeEvent
    data class SelectCategory(val category: MediaCategory?) : HomeEvent
    data class OpenItem(val url: String) : HomeEvent
    data class EditItem(val item: MediaItem) : HomeEvent
    data class DeleteItem(val item: MediaItem) : HomeEvent
}
```

**Filtering Strategy**: Uses `flatMapLatest` on a `StateFlow<MediaCategory?>` to let SQLite do the work — never pull 10k items into RAM to filter to 50:

```kotlin
class HomeViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val selectedCategoryFlow = MutableStateFlow<MediaCategory?>(null)

    val uiState: StateFlow<HomeUiState> = selectedCategoryFlow
        .flatMapLatest { category ->
            if (category == null) repository.observeAll()
            else repository.observeByCategory(category)
        }
        .map { items -> HomeUiState(items = items, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectCategory(category: MediaCategory?) {
        selectedCategoryFlow.value = category
    }

    // One-shot event channel for navigation/intents
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    // Debounce spam-clicks on card body tap — prevents multiple overlapping
    // ACTION_VIEW intents when a user rapidly taps a card. The Channel queues
    // events immediately; the 500ms window drops duplicate OpenItem for the same URL.
    private var lastOpenTime = 0L

    fun openItem(url: String) {
        val now = System.currentTimeMillis()
        if (now - lastOpenTime > 500L) {
            lastOpenTime = now
            viewModelScope.launch {
                _events.send(HomeEvent.OpenItem(url))
            }
        }
    }
}
```

**AI Verification Checklist**:
- Increment/decrement go through repository atomic SQL — no race condition. No debounce needed. Rapid taps are safe.
- `openItem` emits a one-shot event via `Channel<HomeEvent>` (not StateFlow) — handled by Activity to start an `Intent.ACTION_VIEW`. **Spam-click protection**: `openItem()` debounces by dropping events within a 500ms window of the last emission to prevent multiple overlapping browser intents
- Long press → Edit navigates to EditItemScreen. Delete shows a confirmation dialog (Composable dialog, not AlertDialog)
- Relative time display: calculate at render time inside the Composable. It's not a live clock — "5m ago" being 6m ago is acceptable.
- Cover image: `AsyncImage` from Coil with placeholder and error drawables. If `coverImageUrl` is empty, don't load anything.
- Card click: use `LocalContext.current` to start an Activity. Never pass Context to ViewModel.
- FAB click: navigate to AddItemScreen (bottom sheet route).

#### AddItemScreen (Bottom Sheet)
- URL input field with a "Scrape" button
- Category selector chips (Novel, Anime, Manga, Game)
- Title text field
- Cover image URL text field
- Progress input (number) + Total input (number, optional)
- Cancel and Add buttons

**Flow**:
1. User enters URL → taps Scrape
2. Show loading state on scrape button, disable form fields
3. ViewModel calls `scraper.scrape(url)` via ViewModelScope
4. Success: auto-fill Title and Cover URL fields, re-enable form
5. Failure: show inline error message below URL input ("Could not auto-fill, please enter manually"), keep form enabled
6. User can also ignore scrape entirely and fill manually
7. User taps Add → validate form → create MediaItem → save to repository → navigate back

**Validation Rules** (shared by Add and Edit, except where noted):
- Title is required (non-empty, trim whitespace)
- Category must be selected (default to Novel)
- URL is optional but if provided must be valid HTTPS
- Cover URL is optional but if provided must be valid URL (http/https)
- Progress >= 0 (default 0)
- Total must be >= 0 if provided (null = unknown)
- **AddItemScreen**: If progress > total and total is not null → show validation error ("Progress cannot exceed total"). Block the save. Rationale: new items have no prior state, so blocking catches the mistake early.
- **EditItemScreen**: Client-side clamp progress on blur (cap to total), show inline hint ("Capped to 100"). Do NOT block save. Rationale: the user may have other changes to save and shouldn't be forced to fix progress first. The repository's `normalize()` is still the authoritative enforcement.

**AI Verification Checklist**:
- Scrape is cancellable via ViewModelScope — if user dismisses bottom sheet during scrape, coroutine is cancelled
- If scrape is in progress and user manually edits a field, don't overwrite their edit when scrape succeeds
- Form state survives configuration changes (rotation) — use ViewModel + SavedStateHandle or `rememberSaveable`
- Tap outside sheet → dismiss without confirmation (v1 simplicity)
- Duplicate URL check: normalize the new URL via `UrlNormalizer.normalize()`, then call `repository.existsByUrl(normalizedUrl)` — a single SQLite EXISTS query using near-zero memory. Do NOT fetch the full item list into RAM for a string comparison. Show warning: "This URL is already tracked"
- Empty cover URL → store as empty string. Coil handles empty URLs by showing placeholder.
- Progress/Total text fields accept only numeric input (`KeyboardType.Number`). **CRITICAL: Safe Int parsing required**. A user leaning on the "9" key or pasting `"999999999999"` will exceed `Int.MAX_VALUE`, and `text.toInt()` will throw `NumberFormatException` and crash. Always parse as `Long` first, clamp to `Int.MAX_VALUE`, then cast down: `text.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0`.
- URL validation on both scrape tap AND submit (user might skip scraping and enter a URL manually)

#### EditItemScreen
- Full screen with toolbar "Edit Item" + back arrow
- Cover image preview (larger than card)
- Title text field (pre-filled)
- Category chip selector (pre-selected)
- Progress + Total inputs (pre-filled)
- Status: Reading / Completed toggle
- Source URL (editable — user might have entered a wrong URL initially)
- Delete button in toolbar (with confirmation dialog)
- Save button (floating or in toolbar)

**AI Verification Checklist**:
- Editing the source URL is allowed. No stale-data warning needed — if the user changes the URL, the cover/title may become stale, but that's the user's choice.
- Edit mode is identified by passing **only the `id` (String)** as the nav argument — NOT the full `MediaItem` object. Navigation Compose serializes arguments into `SavedStateHandle` bundles; passing domain models (with URLs containing special chars) can break route parsing or hit `TransactionTooLargeException`. It also creates a stale disconnected copy of the item, violating the repository-as-SSOF rule.
- The route argument is `EditItemRoute(val id: String)` (a `@Serializable` data class).
- The `EditItemViewModel` observes the `id` from `SavedStateHandle` via `getStateFlow<String>("id", "")` and `flatMapLatest`s into `repository.observeById(id)` — always working with the latest data from Room.
- On save, calls `repository.update()`.
- Must update `lastUpdated` on save (done by repository).
- Delete shows confirmation dialog: "Delete 'Title'? This cannot be undone."
- Discard changes on back press without confirmation (v1 simplicity).
- No optimistic locking for v1. The "two screens editing same item" race is accepted as a rare edge case. The last save wins. (See 4.10.5.)
- **Clamping feedback is required**: If `normalize()` clamps progress (e.g. user types 500 but total is 100), the user sees the corrected value with no explanation. On save, visually clamp the progress field value client-side on blur (before it reaches the ViewModel) by capping it to the current `total` value. Show a brief inline hint next to the progress field: "Capped to 100". This prevents the silent-correction confusion and gives immediate feedback. The ViewModel must still call `normalize()` — the client-side clamp is a UX courtesy, not a correctness substitute.

#### SettingsScreen
- Toolbar "Settings" with back arrow
- Sections:
  - **Backup**: Export button, Import button
  - **About**: App name + version string, "Built with Kotlin & Jetpack Compose"

**Export Flow**:
1. Tap Export → SAF CreateDocument launcher (suggest "lazydex_backup.json")
2. User picks location → ViewModel calls `repository.getAll()` + `BackupProcessor.serialize()` + `BackupManager.export()`
3. Show success toast or error dialog

**Import Flow**:
1. Tap Import → SAF OpenDocument launcher (filter: application/json)
2. User picks file → ViewModel calls `BackupManager.import()` + `BackupProcessor.deserialize()`
3. Show import choice dialog: Merge or Overwrite
4. Merge: call `BackupProcessor.merge(local, imported)` then `repository.replaceAll(merged)`
5. Overwrite: call `repository.replaceAll(imported)`
6. Show success toast or error dialog

**AI Verification Checklist**:
- SAF launchers are registered in the Activity/Screen (via `rememberLauncherForActivityResult`), not ViewModel — they need ActivityResultRegistry
- Export/Import I/O runs on `Dispatchers.IO`
- `BackupProcessor.serialize/deserialize` runs on `Dispatchers.Default` (CPU-bound)
- Error handling: if export fails (no space, permission denied), show user-friendly toast. Don't crash.
- If import file is corrupted (invalid JSON, wrong schema), show error dialog, don't silently fail
- Merge + replaceAll is atomic via `@Transaction` — if merge computation fails, DB is untouched
- Overwrite + replaceAll is atomic — if any step fails, DB rolls back. No data loss.
- Version string: use `BuildConfig.VERSION_NAME` — never hardcode. Alternative: `context.packageManager.getPackageInfo(context.packageName, 0).versionName`
- Write export to temp file first, then copy to target URI (prevents partial file)
- Settings screen is NOT a bottom sheet — it's a full-screen destination in NavHost

### 4.8 Theme

```kotlin
@Composable
fun LazyDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // Android 12+
    content: @Composable () -> Unit
)
```

**Requirements**:
- Dynamic color on Android 12+ (Monet / Material You)
- Fallback to custom light/dark palettes on older versions
- Dark mode follows system setting by default
- All Material3 components used consistently (MaterialCard, Material3 buttons, etc.)
- Surface colors, background colors, card colors follow Material3 spec
- Category colors (Novel=blue, Anime=red, Manga=green, Game=purple) — use from `Color.kt` not theme
- Status colors (Reading=blue, Completed=green)
- Badge backgrounds should be semi-transparent versions of the badge text color

**AI Verification Checklist**:
- `dynamicColor` must check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` before using `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`
- Fallback palettes must have sufficient contrast (WCAG AA: 4.5:1 for normal text, 3:1 for large text)
- Category and status colors must be accessible
- Use `MaterialTheme.colorScheme` for all standard colors — never hardcode values in components
- Surface colors in dark mode: use dark gray (#1E1E1E or similar), never pure black (#000000) to avoid eye strain on AMOLED. Pure black is acceptable only if the user explicitly chooses an "AMOLED dark" mode
- Badge background: use `Color.copy(alpha = 0.15f)` of the text color for a pill-shaped background

### 4.9 Error Handling Strategy

| Layer | Error Type | Handling |
|-------|-----------|----------|
| Repository | DB corruption on first query | Catch `SQLException` on first `dao` access. Show error dialog with "Reset database" option |
| Repository | Query failure | Log error, rethrow as `DataAccessException` |
| Repository | Progress invariant violation | Handled by `normalize()` — impossible to persist bad data |
| Scraper | Network timeout | Return `Result.failure("Request timed out. The site may be slow or unreachable.")` |
| Scraper | Invalid URL | Return `Result.failure("Invalid URL format. Enter a full https:// URL.")` |
| Scraper | Non-HTTP response | Return `Result.failure("Server returned an unexpected response.")` |
| Backup | File read error | Log error, show toast "Could not read backup file" |
| Backup | Parse error | Show dialog "Backup file appears to be corrupted or from a newer version" |
| Backup | File write error | Show toast "Could not save backup. Check storage space." |
| UI | Network on main thread | Should never happen — all IO is coroutine-dispatched |
| UI | Navigation with deleted item | Catch in ViewModel, navigate back if item no longer exists |

**Important: DB corruption detection + concurrency safety** — `Room.databaseBuilder().build()` is **lazy**. It does NOT open the database or verify integrity at construction time. The first actual query (DAO call) triggers the open, and that's where corruption surfaces. Therefore, use the `onStart` Flow operator to wrap the corruption check around reactive queries — this cleanly separates the suspending check from the Flow return:

```kotlin
class MediaRepositoryImpl(
    private val dao: MediaItemDao
) : MediaRepository {
    private val healthMutex = Mutex()
    private var dbHealthy = false

    /**
     * Lightweight health check using SELECT 1 instead of observeCount().first().
     * instantiating a Room Flow + awaiting first() is unnecessarily heavy —
     * it allocates coroutine channels and invalidation tracker registrations for
     * a simple ping. SELECT 1 evaluates instantly without touching table indices
     * and still throws SQLException on corruption.
     */
    private suspend fun ensureDbHealthy() {
        if (!dbHealthy) {
            healthMutex.withLock {
                if (!dbHealthy) {
                    try {
                        dao.healthCheck()
                        dbHealthy = true
                    } catch (e: SQLException) {
                        throw DatabaseCorruptionException(e)
                    }
                }
            }
        }
    }

    override fun observeAll(): Flow<List<MediaItem>> {
        return dao.observeAll()
            .onStart { ensureDbHealthy() }
            .map { entities ->
                entities.mapNotNull { entity ->
                    try {
                        entity.toDomain()
                    } catch (e: Exception) {
                        // Skip corrupted/malformed rows — prevents NPE from
                        // unknown enum values from crashing the entire Flow stream.
                        // Room's invalidation tracker fires at the TABLE level,
                        // so a single bad row could otherwise kill ALL active observers.
                        null
                    }
                }
            }
            .distinctUntilChanged() // Suppress Room table-level invalidation noise
            .flowOn(Dispatchers.Default) // CRITICAL: Force O(N) diffing off the main thread
    }

    override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> {
        return dao.observeByCategory(category.name)
            .onStart { ensureDbHealthy() }
            .map { entities ->
                entities.mapNotNull { entity ->
                    try {
                        entity.toDomain()
                    } catch (e: Exception) {
                        null // Skip corrupted row silently
                    }
                }
            }
            .distinctUntilChanged() // Suppress Room table-level invalidation noise
            .flowOn(Dispatchers.Default) // CRITICAL: Force O(N) diffing off the main thread
    }
}
```

This guarantees the suspending database check runs precisely when the Flow begins collection, isolating the corruption check perfectly without breaking the stream or requiring complex builder blocks.

**Global Error Principles**:
- Never crash with an unhandled exception. Every coroutine should have a try/catch or a `CoroutineExceptionHandler`.
- User-facing errors must be human-readable, not stack traces.
- Log technical details with `Log.e()`.
- If the app enters an unrecoverable state (DB won't open, data corrupted), surface a "Something went wrong" screen with a "Reset app" button that calls `context.deleteDatabase("lazydex_db")` and restarts.

### 4.10 Edge Cases & Race Conditions

These are **not** exhaustive. The AI must consider every scenario below and any it can think of:

1. **Rapid increment/decrement**: User taps [+] 20 times quickly. **Mitigated by atomic SQL**: each increment is `UPDATE media_items SET currentProgress = MIN(currentProgress + 1, ...)`. No read-modify-write race. No debounce needed. Do NOT add debounce — it would drop legitimate taps.

2. **Configuration change during scrape**: User rotates phone while a scrape is in progress. The ViewModel survives (ViewModelScope). The Compose UI is rebuilt. The loading state persists via `StateFlow` → `collectAsStateWithLifecycle()`.

3. **Process death during backup**: Android kills the app while exporting. On relaunch, state is restored from Room (which was never modified during export). Safe.

4. **Import overwrite + process death**: User imports, chooses "Overwrite". The DAO's `@Transaction replaceAll()` runs `deleteAll()` + `upsertAll()` atomically. If process dies mid-transaction, Room rolls back. No data loss.

5. **Two screens editing the same item**: User opens Edit screen, then goes home and edits the same item via the list. The Edit screen still has stale data. On save, it overwrites the other change. **Decision for v1**: Last save wins. No optimistic locking. This is acceptable for a single-user local app. If implementing locking later, add `expectedLastUpdated: Long` parameter to `repository.update()` and check in DAO: `UPDATE ... WHERE id = :id AND lastUpdated = :expectedLastUpdated`. If 0 rows affected, throw `StaleDataException`.

6. **Scraping the same URL twice**: User adds URL A, it scrapes successfully. User adds URL A again. The duplicate check (normalized URL comparison) prevents adding. If first scrape is still in progress, the form fields are disabled — second tap is blocked.

7. **Very long titles**: Some sites have very long `<title>` tags (100+ characters). Handle with `maxLines = 2` and `ellipsis = TextUtils.TruncateAt.END` in the card. Room has no practical string length limit.

8. **Very large lists**: 10,000 items. Room handles this fine. LazyColumn handles this fine (view recycling). Backup JSON will be large (~5-10MB). Serialization and I/O will take a few seconds. Show a progress indicator during export/import. For v1, a simple progress dialog is sufficient. Do NOT try to stream JSON for v1 — keep it simple.

9. **Malicious URL injection**: URL like `https://evil.com/"; DROP TABLE media_items; --`. Room uses parameterized queries by default — no SQL injection. URL opened via `Intent.ACTION_VIEW` is URL-encoded by Android. Safe.

10. **Cover image URL that returns a redirect to a malicious site**: Coil handles redirects. Verify that Coil doesn't follow redirects to `file://` or `content://` schemes. This is a Coil security concern — check Coil's redirect policy. If needed, add a custom `Interceptor` to OkHttp client that rejects redirects to non-http schemes.

11. **SharedPreferences migration**: Old app stored data in SharedPreferences. New app uses Room. No automatic migration. Users will lose old data. **Decision**: Clean break. Document in Settings about section: "This version uses a new database. Previous data is not migrated. Please export from the old version first."

12. **Empty database on first launch**: Show empty state with friendly message: "Nothing here yet. Tap + to add your first item."

13. **System locale / RTL**: App supports RTL layouts (`supportsRtl="true"` in manifest). Compose handles this with `LocalLayoutDirection`. All paddings use `Start`/`End` not `Left`/`Right`.

14. **Input method / keyboard**: Progress/Total fields show numeric keyboard (`KeyboardType.Number`). Forms handle IME actions (Next → Next → Done). Test on devices without hardware keyboard.

15. **Accessibility**: All interactive elements must have content descriptions (`.contentDescription()` in Compose). Buttons must be at least 48dp touch target. Color must not be the only differentiator — category and status badges use text + icon + color.

---

## 5. Implementation Order

The AI MUST follow this order. Each phase depends on the previous one.

### Phase 1: Project Scaffold
- [ ] Create `build.gradle.kts` (project-level) with version catalog
- [ ] Create `settings.gradle.kts`
- [ ] Create `gradle/libs.versions.toml` with ALL dependencies (including MockWebServer, Kotest assertions, android-junit5 plugin, Compose UI test JUnit4)
- [ ] Create `app/build.gradle.kts` with all plugins (compose, KSP, kotlinx.serialization, android-junit5) and dependencies
- [ ] Create `gradle.properties` (without hardcoded JDK path)
- [ ] Create `proguard-rules.pro` with kotlinx.serialization keep rules
- [ ] Update `gradle-wrapper.properties` to Gradle 8.10.2 (AGP 8.7.x requires Gradle 8.9+ — Gradle 8.7.3 is NOT compatible)
- [ ] Create `local.properties` template (or document requirement)
- [ ] Run `./gradlew assembleDebug` to verify build
- [ ] **AI must verify**: Build succeeds, no dependency conflicts, no missing files, KSP + Room + Kotlin 2.0 compatible

### Phase 2: Data Layer
- [ ] Create `domain/model/` — MediaItem, MediaCategory, UserStatus (with `fromString()` companions, `normalize()`)
- [ ] Create `domain/repository/MediaRepository.kt` interface
- [ ] Create `data/local/entity/MediaItemEntity.kt`
- [ ] Create `data/local/dao/MediaItemDao.kt` (with atomic increment/decrement, `@Transaction replaceAll`)
- [ ] Create `data/local/LazyDexDatabase.kt` (exportSchema = false, WAL mode)
- [ ] Create `data/local/converter/Converters.kt`
- [ ] Create `data/repository/MediaRepositoryImpl.kt` (normalize() on every write, UUID in add only)
- [ ] Create `util/UrlNormalizer.kt` (single canonical normalizer, used by repository + scraper)
- [ ] Write tests for MediaRepositoryImpl (insert, read, update, delete, atomic increment/decrement, replaceAll transaction, normalize enforcement)
- [ ] **AI must verify**: All DAO methods work, Flow emissions are correct, atomic SQL prevents race conditions, @Transaction prevents partial updates

### Phase 3: DI Setup
- [ ] Create `di/Modules.kt` — Room module, Repository module, Scraper module, ViewModel modules, Coil ImageLoader module
- [ ] **Coil disk cache hardening**: Configure Coil's `ImageLoader` with a 250MB disk cache and `respectCacheHeaders(false)`. Manga/anime aggregator sites aggressively rotate CDN URLs and use signed expiring tokens. Without aggressive caching, users opening the app offline a month later will find half their cover images broken. The disk cache prevents standard OS eviction from clearing cover images:
  ```kotlin
  single {
      ImageLoader.Builder(androidContext())
          .diskCache {
              DiskCache.Builder()
                  .directory(androidContext().cacheDir.resolve("image_cache"))
                  .maxSizeBytes(250L * 1024 * 1024) // 250MB hard limit
                  .build()
          }
          .respectCacheHeaders(false) // Force cache regardless of site headers
          .build()
  }
  ```
- [ ] Create `LazyDexApp.kt` — Application class with Koin `startKoin()`
- [ ] Register Application class in AndroidManifest.xml
- [ ] **AI must verify**: Koin starts without errors, all modules load, no circular dependencies

### Phase 4: Theme
- [ ] Create `ui/theme/Color.kt` — custom palette, category colors, status colors
- [ ] Create `ui/theme/Type.kt` — typography scale
- [ ] Create `ui/theme/Theme.kt` — LazyDexTheme with dynamic color + fallback
- [ ] **AI must verify**: Theme compiles, dynamic color works on Android 12+ emulator, fallback works on Android 8-11, WCAG contrast ratios met

### Phase 5: Core UI Components
- [ ] Create `ui/components/MediaCard.kt`
- [ ] Create `ui/components/CategoryFilterChips.kt`
- [ ] Create `ui/components/CategoryBadge.kt`
- [ ] Create `ui/components/StatusBadge.kt`
- [ ] Create `ui/components/ProgressControls.kt`
- [ ] Create `ui/components/EmptyState.kt`
- [ ] **AI must verify**: Each component is reusable, accepts proper parameters, handles edge cases (empty strings, null values, long text, RTL)

### Phase 6: Home Screen
- [ ] Create `ui/home/HomeViewModel.kt`
- [ ] Create `ui/home/HomeScreen.kt`
- [ ] Wire filter chips, list, FAB
- [ ] Handle all user interactions (atomic increment, decrement, status toggle, tap, long press)
- [ ] **AI must verify**: All interactions work, loading states are correct, filter works, empty state shows, config change survives, one-shot events use Channel

### Phase 7: Add Item Screen
- [ ] Create `scraper/MetadataScraper.kt` + tests (MockWebServer for HTTP responses)
- [ ] Create `ui/add/AddItemViewModel.kt`
- [ ] Create `ui/add/AddItemScreen.kt`
- [ ] Handle scrape flow (loading, success, failure with inline error)
- [ ] Handle manual entry
- [ ] Form validation (title required, URL format, progress bounds)
- [ ] Duplicate URL detection (using UrlNormalizer)
- [ ] **AI must verify**: Scrape works with real URLs, form validates correctly, scrape cancellation works, orientation change preserves form state, no UUID generation in ViewModel

### Phase 8: Edit Item Screen
- [ ] Create `ui/edit/EditItemViewModel.kt`
- [ ] Create `ui/edit/EditItemScreen.kt`
- [ ] Pre-populate with existing data
- [ ] Handle save (goes through normalize()), delete with confirmation
- [ ] **AI must verify**: Editing works, delete works, all fields modifiable, source URL is editable, lastUpdated updates on save

### Phase 9: Settings Screen
- [ ] Create `backup/BackupProcessor.kt` + tests (round-trip, merge, schema version default 1, case-insensitive enums)
- [ ] Create `backup/BackupManager.kt`
- [ ] Create `ui/settings/SettingsViewModel.kt`
- [ ] Create `ui/settings/SettingsScreen.kt`
- [ ] Handle SAF export/import launchers (in Composable, not ViewModel)
- [ ] Import conflict dialog (merge vs overwrite)
- [ ] About section with version info
- [ ] **AI must verify**: Export creates valid JSON, import reads old Gson format, schemaVersion missing defaults to 1, merge is deterministic, overwrite is atomic via @Transaction, error cases (corrupt file, empty file, permissions) handled

### Phase 10: Navigation
- [ ] Create `ui/navigation/NavGraph.kt`
- [ ] Wire all screens together
- [ ] Handle up/back navigation correctly
- [ ] **AI must verify**: All routes defined, navigation type-safe (Kotlin serialization plugin for nav args), back stack correct, no duplicate destinations, bottom sheet as route

### Phase 11: Polish & Testing
- [ ] Write ViewModel unit tests (MockK for repository, verify state changes, one-shot events)
- [ ] Write scraper integration tests (MockWebServer)
- [ ] Write backup processor tests (round-trip, merge edge cases, backward compat with old Gson format)
- [ ] Write Compose UI tests (createComposeRule, JUnit4 runner) — at least one per screen
- [ ] Accessibility audit
- [ ] Performance profiling (DB queries, list scrolling, image loading)
- [ ] **AI must verify**: Test coverage is meaningful, tests are deterministic, UI tests run on JUnit4 runner not Jupiter, no flaky tests, performance acceptable

---

## 6. Build Configuration Details

### Dependencies (libs.versions.toml)

```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
compose-bom = "2024.12.01"
room = "2.6.1"
koin = "4.0.0"
okhttp = "4.12.0"
jsoup = "1.18.3"
coil = "3.0.4"
kotlinx-serialization = "1.7.3"
navigation-compose = "2.8.5"
lifecycle = "2.8.7"
coroutines = "1.9.0"
ksp = "2.0.21-1.0.28"
junit5 = "5.11.4"
android-junit5 = "1.11.4.0"
mockk = "1.13.14"
turbine = "1.2.0"
kotest-assertions = "5.9.1"
mockwebserver = "4.12.0"
compose-ui-test = "1.7.6"
androidx-test-ext = "1.2.1"
androidx-test-runner = "1.6.2"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

# Compose UI Test (JUnit4 runtime — for instrumented UI tests only)
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4", version.ref = "compose-ui-test" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest", version.ref = "compose-ui-test" }

# AndroidX Instrumentation Test (required to boot AndroidJUnitRunner and createComposeRule())
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidx-test-runner" }

# Lifecycle
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Koin
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin" }

# Networking
okhttp-core = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }

# Image Loading
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Unit Testing (JUnit5 / Jupiter)
junit5-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
kotest-assertions-core = { group = "io.kotest", name = "kotest-assertions-core", version.ref = "kotest-assertions" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# Debug
leakcanary = { group = "com.squareup.leakcanary", name = "leakcanary-android", version = "2.14" }

[bundles]
compose = ["compose-ui", "compose-ui-graphics", "compose-ui-tooling-preview", "compose-material3", "compose-material-icons"]
compose-ui-test = ["compose-ui-test-junit4", "compose-ui-test-manifest"]
android-instrumentation-test = ["androidx-test-ext-junit", "androidx-test-runner", "compose-ui-test-junit4"]
room = ["room-runtime", "room-ktx"]
koin = ["koin-core", "koin-android", "koin-compose"]
unit-test = ["junit5-api", "junit5-engine", "mockk", "turbine", "kotest-assertions-core", "coroutines-test"]
```

### Gradle Plugins (app/build.gradle.kts)
```kotlin
plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"          // Kotlin 2.0+ compose compiler
    kotlin("plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("de.mannodermaus.android-junit5") version "1.11.4.0"  // JUnit5 on Android
    id("de.mannodermaus.junit5-ktx") version "1.11.4.0"      // Kotlin extensions for android-junit5
}
```

### Build Configuration Checklist
- Kotlin 2.0+: use `kotlin("plugin.compose")` plugin (not the separate Compose compiler Gradle plugin)
- Room: use KSP (`ksp(libs.room.compiler)`), not KAPT
- kotlinx.serialization: add `kotlin("plugin.serialization")`
- JUnit5 unit tests: use `@Test` from `org.junit.jupiter.api` in `src/test/`
- Compose UI tests: use `@Test` from `org.junit` (JUnit4) in `src/androidTest/` with `createComposeRule()`
- The `de.mannodermaus.android-junit5` plugin makes Jupiter work in `test/` while keeping JUnit4 rule support for `androidTest/`. Do NOT use Jupiter annotations in `androidTest/`.
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig` for instrumented UI tests
- `testOptions { unitTests.all { useJUnitPlatform() } }` for unit test module
- `proguard-rules.pro`: must contain `-keepattributes *Annotation*`, `-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }`, and keep rules for all `@Serializable` classes
- `buildFeatures.buildConfig = true` for version info in Settings
- `room.schemaLocation` not needed until v2 migration (exportSchema = false)
- LeakCanary: add `debugImplementation(libs.leakcanary)` in dependencies — never ship to release builds

---

## 7. Testing Strategy

### Dual-Track Testing Model (Resolved)

| Test Type | Runtime | Framework | Location |
|-----------|---------|-----------|----------|
| Unit tests (ViewModel, Repository, Scraper, Backup) | JUnit5 (Jupiter) | Jupiter API + MockK + Turbine + Kotest assertions | `src/test/` |
| Compose UI tests (screen rendering, user interaction) | JUnit4 | `createComposeRule()` + `ComposeTestRule` | `src/androidTest/` |
| Scraper integration | JUnit5 (Jupiter) | OkHttp MockWebServer | `src/test/` |

Both tracks coexist via the `de.mannodermaus.android-junit5` plugin. The plugin registers Jupiter as the test engine for `src/test/` while preserving JUnit4 rule infrastructure for `src/androidTest/`. The `androidTest/` source set uses JUnit4's `@RunWith(AndroidJUnit4::class)` runner.

### Unit Tests (Required)
| Class | What to Test | Framework |
|-------|-------------|-----------|
| MediaRepositoryImpl | CRUD, atomic increment/decrement bounds, @Transaction replaceAll, normalize enforcement, duplicate URL detection | JUnit5 + MockK (mock DAO) + Turbine (test Flow) |
| BackupProcessor | Serialize/deserialize round-trip, merge logic, schemaVersion defaults to 1, case-insensitive enums, invalid JSON, empty list | JUnit5 |
| MetadataScraper | URL validation, successful scrape, various error responses (timeout, 404, empty body, malformed HTML) | JUnit5 + MockWebServer |
| HomeViewModel | Filtering, increment/decrement events, state mapping, one-shot events via Channel | JUnit5 + MockK + Turbine |
| AddItemViewModel | Form validation, scrape flow states, duplicate detection, form state preservation | JUnit5 + MockK + Turbine |
| EditItemViewModel | Load existing item, save changes, delete | JUnit5 + MockK + Turbine |

### Compose UI Tests (Instrumented, Nice to Have)
- Compose UI tests for key flows: add item form validation, edit item save, filter list interaction
- Use `createComposeRule()` + `ComposeTestRule` in `src/androidTest/`
- Run with JUnit4 `@RunWith(AndroidJUnit4::class)`

### AI Verification Checklist
- Tests are deterministic (no flakiness) — use `TestCoroutineDispatcher` or `StandardTestDispatcher` for coroutine-based tests
- Turbine tests have timeout to prevent hanging (`turbineScope { ... }` has built-in timeout)
- MockK verifies interactions correctly (`verify { ... }`)
- `createComposeRule()` tests are in `src/androidTest/` (not `src/test/`) and use JUnit4 annotations
- MockWebServer is started/stopped in `@BeforeEach`/`@AfterEach` to isolate test state
- Backup tests test with actual old-format JSON strings (missing schemaVersion, different casing)

---

## 8. Common Mistakes & Anti-Patterns — THE AI MUST AVOID

1. **Don't put Android Context in ViewModel**. ViewModel should be pure. Use `Application` context via `AndroidViewModel` only if absolutely necessary (e.g., system services), but prefer injecting dependencies.

2. **Don't use `GlobalScope`**. Ever. Use `viewModelScope` in ViewModels, `lifecycleScope` in Compose, or create a scoped coroutine.

3. **Don't call Room on main thread**. Room enforces this by default. Only `Flow` queries are safe to observe on main thread (Room switches to background internally).

4. **Don't use `StateFlow` with initial value for one-shot events** (navigation, toasts). Use `Channel<UiEvent>` with `receiveAsFlow()` or `SharedFlow` with `replay = 0`.

5. **Don't add debounce/serialize rapid increment/decrement**. The DAO uses atomic SQL `UPDATE` — no race condition exists. Debounce would lose legitimate taps.

6. **Don't hardcode strings**. Every user-facing string goes in `strings.xml`. Use `stringResource()` in Compose.

7. **Don't use `remember` for state that should survive config changes**. Use `collectAsStateWithLifecycle()` with a ViewModel Flow, or `rememberSaveable` for simple UI state.

8. **Don't use `mutableStateOf` in the ViewModel outside of `StateFlow`**. ViewModels should expose `StateFlow<UiState>`, not mutable state objects.

9. **Don't ignore ProGuard/R8**. The `proguard-rules.pro` must exist with kotlinx.serialization keep rules.

10. **Don't nest `NavHost` unnecessarily**. Single-level navigation is fine for this app (5 screens + dialogs).

11. **Don't use `by lazy` or `lateinit var` in ViewModels for observable state**. Use `MutableStateFlow` initialized in `init`.

12. **Don't use `requireActivity()` or `LocalContext.current` in ViewModel code**.

13. **Don't chain Flows without cancellation handling**. If you `flatMapLatest` in a ViewModel, ensure the upstream Flow is properly scoped to `viewModelScope`.

14. **Don't assume `System.currentTimeMillis()` is monotonic**. It can go backward if the user changes system time. For ordering, this is acceptable for v1. Document the behavior.

15. **Don't use `@OptIn` for experimental APIs without understanding the risk**.

16. **Don't create memory leaks with Coil**. Image loading is scoped to the Composable lifecycle by default with `AsyncImage`.

17. **Don't ignore the Android back button**. Navigation Compose handles it automatically.

18. **Don't use `LaunchedEffect` with infinite loop** (e.g., `while(true)` without delay). Use `snapshotFlow` or periodic `delay()`.

19. **Don't use `@Composable` functions that do side effects** (network calls, DB writes) directly. Trigger from events.

20. **Don't use JUnit5 annotations in `src/androidTest/`**. Compose UI tests run under JUnit4. Jupiter annotations will be ignored.

21. **Don't use `@TypeConverters` for enums without also testing the edge case**. Store enums as uppercase strings directly. Don't over-engineer with TypeConverters until you need a second column type.

22. **Don't call `Room.databaseBuilder().build()` and assume DB is healthy**. Corruption surfaces on first query. Wrap the first DAO call.

23. **Don't blindly upgrade `og:image` from `http://` to `https://`** or add a HEAD check. Keep the URL as-scraped. Coil handles http images fine. HEAD checks add latency and many servers reject them.

24. **Don't reject backup files missing `schemaVersion`**. Default to version 1 for backward compatibility with the old Gson-based app.

---

## 9. Pre-Implementation Q&A (Verified Answers)

These questions have been audited and resolved. The answers below are definitive:

1. **Gradle 8.7.3 vs AGP 8.7.3**: NOT compatible. AGP 8.7.x requires Gradle 8.9+. Use **Gradle 8.10.2** (or latest 8.x).
2. **KSP 2.0.21-1.0.28**: Correct. KSP versions are strictly coupled to Kotlin compiler. `-1.0.28` is the exact release for Kotlin `2.0.21`.
3. **android-junit5 1.11.4.0 + AGP 8.7.3**: Yes, the `1.11.x` series works with AGP 8.x.
4. **Coil 3.x network dependency**: Yes, `coil-network-okhttp` is required separately — Coil 3.0 decoupled networking from the core library.
5. **material3 only or material2 too?**: Strictly **material3 only**. Mixing bloats APK, fragments theming, and causes conflicting typography/color systems.
6. **Type-safe nav args?**: Yes. Navigation 2.8.x makes type-safe navigation via Kotlin Serialization the official standard. Use `@Serializable` route classes.
7. **Room WAL mode?**: Yes. `JournalMode.WRITE_AHEAD_LOGGING` allows concurrent reads via Flow while writes are in progress.
8. **Minimum OkHttp version**: **4.12.0** — compatible with both Coil 3.x and Jsoup without conflict.
9. **LeakCanary in debug?**: Yes. In an offline-first app, the biggest crash risks are memory leaks from un-scoped coroutines or retained Compose contexts. Add as `debugImplementation`.
10. **App launcher icons**: Android Adaptive Icons (`<adaptive-icon>`) in `mipmap-anydpi-v26`. Pure XML vector drawables for both foreground and background. No PNGs.
11. **No browser installed**: Wrap `context.startActivity()` in try/catch for `ActivityNotFoundException`. Show Toast: "No web browser found."
12. **Merge order**: Local first, imported second. A `LinkedHashMap` keyed by UUID naturally appends new imported items after existing local ones, preserving deterministic order before DB re-sorts by `lastUpdated`.
13. **coil-network-okhttp + OkHttp 4.12.0**: Yes, compatible. Coil 3.x supports OkHttp 4.x and 5.x seamlessly.
14. **android-junit5 vs compose/KSP plugins**: No conflict. KSP generates code, Compose plugin transforms IR, JUnit5 hooks testing runtime — different build phases.
15. **@SerialName for backup JSON**: **No**. The old Gson format used `"id"`, not `"_id"`. Keep the Kotlin property as `val id: String` without renaming for seamless backward compatibility.

---

## Appendix A: Comparison with Old Codebase

| Aspect | Old (Trash) | New (Good) |
|--------|-------------|------------|
| Storage | SharedPreferences JSON blob | Room SQLite + Flow + WAL |
| UI | XML layouts + ViewBinding | Jetpack Compose + Material3 |
| Architecture | God Activity (493 lines) | MVVM + Repository |
| DI | Manual wiring | Koin |
| Serialization | Gson (reflection) | kotlinx.serialization (compile-time) |
| Scraping | OkHttp + Jsoup + WebView (memory bloat) | OkHttp + Jsoup only (no WebView) |
| Image loading | Glide (Java-first) | Coil (Kotlin-native, Compose) |
| State | MutableList in Activity | StateFlow in ViewModel |
| Increment/decrement | Read-modify-write (race condition) | Atomic SQL UPDATE (no race) |
| Bulk replace | Not atomic (data loss possible) | @Transaction (rollback on failure) |
| Invariant enforcement | None (data could become corrupt) | normalize() on every write path |
| Testing | 1 test file | Full unit suite (JUnit5) + UI tests (JUnit4) |
| Configuration | Hardcoded JDK path, missing proguard | Clean build config |
| Navigation | Implicit (dialogs everywhere) | Navigation Compose |
| Error handling | Catch Exception silently | Per-layer error handling |
| Import compat | N/A | Reads old Gson format (missing schemaVersion → defaults to 1) |

---

## Appendix B: File Manifest

The final project should contain these files (and only these):

```
app/
├── build.gradle.kts
├── proguard-rules.pro
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/rockyxwall/lazydex/
│   │   │   ├── LazyDexApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── LazyDexDatabase.kt
│   │   │   │   │   ├── entity/MediaItemEntity.kt
│   │   │   │   │   ├── dao/MediaItemDao.kt
│   │   │   │   │   └── converter/Converters.kt
│   │   │   │   └── repository/MediaRepositoryImpl.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── MediaItem.kt
│   │   │   │   │   ├── MediaCategory.kt
│   │   │   │   │   └── UserStatus.kt
│   │   │   │   └── repository/MediaRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── navigation/NavGraph.kt
│   │   │   │   ├── home/
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   ├── add/
│   │   │   │   │   ├── AddItemScreen.kt
│   │   │   │   │   └── AddItemViewModel.kt
│   │   │   │   ├── edit/
│   │   │   │   │   ├── EditItemScreen.kt
│   │   │   │   │   └── EditItemViewModel.kt
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SettingsScreen.kt
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   └── components/
│   │   │   │       ├── MediaCard.kt
│   │   │   │       ├── CategoryFilterChips.kt
│   │   │   │       ├── CategoryBadge.kt
│   │   │   │       ├── StatusBadge.kt
│   │   │   │       ├── ProgressControls.kt
│   │   │   │       └── EmptyState.kt
│   │   │   ├── di/Modules.kt
│   │   │   ├── scraper/MetadataScraper.kt
│   │   │   ├── util/UrlNormalizer.kt
│   │   │   ├── backup/
│   │   │   │   ├── BackupManager.kt
│   │   │   │   └── BackupProcessor.kt
│   │   └── res/
│   │       ├── values/strings.xml
│   │       ├── values/themes.xml
│   │       ├── drawable/ (app icon, etc.)
│   │       └── mipmap-*/ (launcher icons)
│   ├── test/kotlin/com/rockyxwall/lazydex/
│   │   ├── data/repository/MediaRepositoryImplTest.kt
│   │   ├── scraper/MetadataScraperTest.kt
│   │   ├── backup/BackupProcessorTest.kt
│   │   ├── ui/home/HomeViewModelTest.kt
│   │   ├── ui/add/AddItemViewModelTest.kt
│   │   └── ui/edit/EditItemViewModelTest.kt
│   └── androidTest/kotlin/com/rockyxwall/lazydex/
│       └── ui/ (Compose UI tests)
│           ├── HomeScreenTest.kt
│           ├── AddItemScreenTest.kt
│           └── SettingsScreenTest.kt
gradle/
├── libs.versions.toml
└── wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
.gitignore
```

**Note**: No `schemas/` directory exists in v1 because `exportSchema = false`. When migrations become necessary in the future, the `schemas/` directory will be added.

---

> **Final Instruction to AI**: Do not proceed until you have audited every section above. If you find ambiguities, contradictions, missing edge cases, or design flaws, flag them and propose corrections before writing any code. Be merciless. The old codebase was garbage. This one must not be.
