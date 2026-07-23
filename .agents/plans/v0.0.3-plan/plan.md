# LazyDex v0.0.3 — AniList OAuth Sync & Extended Metadata Architecture

> **Tagline**: Two-way AniList sync, extended media metadata (format, duration, volumes, status, season), configurable 5-system rating architecture, and AniList-style local statistics calculation.
> **Prerequisite**: v0.0.2 codebase (Source interface, AniListSource, BackupProcessor merge logic).
> **Replaces**: The OAuth sync section from original v0.0.3 plan.

---

## 1. Executive Summary & Core Capabilities

- **Login Flow**: Mihon-style dedicated `TrackLoginActivity` capturing `lazydex://anilist-auth` deep links, parsing URI fragment `#access_token=...`, and validating CSRF `state` parameter before storing credentials in `AnilistTokenStore`.
- **Full Pull**: Paginated fetching of all user AniList entries using `$chunk` and `$perPage = 500` with 429 rate-limit backoff, deduplicating by `MediaListEntry.id`, with null-safe media DTO guards.
- **3-Tier Item Matching**: Matches local entries by:
  1. `anilistListEntryId == entry.id`
  2. `sourceUrl == "https://anilist.co/anime/$mediaId"` (or `manga/$mediaId`)
  3. Title + Local `MediaCategory` (using strict normalization and tie-breaking by `dateAdded DESC` on unlinked items).
- **Recategorization Unbind**: Automatically unbinds AniList IDs (`anilistListEntryId = null`) if an item is moved to a non-syncable category (`GAME`, live-action `TV`, live-action `MOVIE`).
- **Conflict Resolution**: Timestamp-based (**Newest Timestamp Wins**): compares local `lastUpdated` with AniList `(updatedAt.toLong() * 1000L)`. The newer modification wins automatically, defaulting to local on tie.
- **Live Push**: **Asynchronous Background Push** on progress increment / item edit. Local DB saves instantly so UI stays responsive, while a background coroutine pushes mutation to AniList with silent retry on failure.
- **5-System Configurable Rating Architecture**: Stores score internally in SQLite DB as normalized `Int?` (0–100, `null` = unrated) to prevent float precision drift. Defaults to `POINT_5` (5-Star) for local-only state, and automatically syncs `ScoreFormat` preference (5-Star, 10-Pt, 10-Decimal, 100-Pt, 3-Smiley) with AniList profile on login.
- **Local Statistics**: Computes watch days, chapter totals, and read volumes using explicit `progressVolumes` column filtered by LazyDex's local `category` enum.

---

## 2. Mihon Architecture (Replicated & Extended Patterns)

LazyDex replicates Mihon's modular tracker structure (see [`mihon-reference.md`](mihon-reference.md) for source trace):

| Layer | Mihon File | LazyDex Equivalent | Purpose |
|-------|-----------|-------------------|---------|
| Deep Link Activity | `TrackLoginActivity.kt` | `TrackLoginActivity.kt` | Dedicated transparent activity for handling OAuth callback redirect |
| Tracker Service | `Anilist.kt` | `AnilistSyncManager.kt` | High-level sync orchestrator, conflict resolver, and push/pull worker |
| API Client | `AnilistApi.kt` | `AnilistApi.kt` | GraphQL queries & mutations with scoreRaw (0–100) exchange |
| Auth Interceptor | `AnilistInterceptor.kt` | `AnilistInterceptor.kt` | Appends `Authorization: Bearer {token}` & catches 401 Unauthorized |
| Rate Limiter | `.rateLimit(85, 1.minutes)` | `AnilistRateLimiter.kt` | OkHttp `Interceptor` enforcing 85 permits/min with HTTP 429 `Retry-After` backoff |
| Token Storage | `TrackPreferences` | `AnilistTokenStore.kt` | Encrypted SharedPreferences storing token, expiry, user ID, and `ScoreFormat` |

---

## 3. Database Migration: Room v2 → v3

> Full reference: [`db-migration.md`](db-migration.md) — complete SQL DDL, entity annotations, and rollback safety.

### 13 New Columns & Entity Annotations (`MediaItemEntity.kt`)

Room Database version increments from **2 to 3**. The `rating` column stores `Int?` (0–100 integer, `null` = unrated) to prevent float precision drift:

```kotlin
@Entity(
    tableName = "media_items",
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
data class MediaItemEntity(
    // ... existing 19 columns ...
    @ColumnInfo(defaultValue = "NULL") val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val anilistListEntryId: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val mediaFormat: String? = null,     // Stored as enum .name string
    @ColumnInfo(defaultValue = "NULL") val rawFormat: String? = null,        // Original format string from AniList/scraper
    @ColumnInfo(defaultValue = "NULL") val publishingStatus: String? = null,
    @ColumnInfo(defaultValue = "NULL") val season: String? = null,
    @ColumnInfo(defaultValue = "NULL") val totalVolumes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val progressVolumes: Int? = null,     // Completed volumes count
    @ColumnInfo(defaultValue = "NULL") val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceMaterial: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDoujin: Boolean = false,
)
```

### Migration SQL (`LazyDexDatabase.kt`)

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN lastSyncedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN anilistListEntryId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN mediaFormat TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN rawFormat TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN publishingStatus TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN season TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN totalVolumes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN progressVolumes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN durationMinutes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN sourceMaterial TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isAdult INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isDoujin INTEGER NOT NULL DEFAULT 0")
    }
}
```

---

## 4. OAuth Security & `TrackLoginActivity`

### CSRF Protection & Intent Handling

1. **`state` Generation**: On initiating login in `AnilistSyncManager`, generate a 256-bit random UUID string `authState`, save it in `AnilistTokenStore`, and construct the authorize URL:
   `https://anilist.co/api/v2/oauth/authorize?client_id={CLIENT_ID}&response_type=token&redirect_uri=lazydex://anilist-auth&state={authState}`
2. **Dedicated Activity (`TrackLoginActivity.kt`)**: Registered in `AndroidManifest.xml` with `<intent-filter>` for scheme `lazydex` and host `anilist-auth`.
3. **Fragment Parsing & State Check**:
   ```kotlin
   class TrackLoginActivity : BaseActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           val uri = intent.data ?: run { finish(); return }
           
           val fragment = uri.encodedFragment ?: uri.encodedQuery ?: ""
           val params = fragment.split("&").associate {
               val parts = it.split("=")
               parts[0] to Uri.decode(parts.getOrNull(1) ?: "")
           }
           
           val token = params["access_token"]
           val returnedState = params["state"]
           val savedState = tokenStore.getAuthState()
           
           if (token != null && returnedState == savedState) {
               lifecycleScope.launch {
                   syncManager.loginWithToken(token)
                   returnToSettings()
               }
           } else {
               tokenStore.clear()
               returnToSettings()
           }
       }
       
       private fun returnToSettings() {
           finish()
           startActivity(Intent(this, MainActivity::class.java).apply {
               addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
           })
       }
   }
   ```
4. **401 Unauthorized Recovery**: When `AnilistInterceptor` encounters HTTP 401:
   - Call `AnilistTokenStore.clear()`
   - Emit `AuthState.Unauthenticated` to UI
   - Cancel ongoing sync tasks without throwing unhandled exceptions.

---

## 5. Configurable 5-System Rating Architecture

### Rating Scale & Storage Standard
- DB Storage: Scores are stored as `scoreRaw: Int?` (0–100, where `null` = unrated).
- Default Preference: `POINT_5` (5-Star 0.5–5.0 ★) for local-only users.
- User Preference Enum `ScoreFormat`:
  ```kotlin
  enum class ScoreFormat(val displayName: String) {
      POINT_100("100-Point (1-100)"),
      POINT_10_DECIMAL("10-Point Decimal (1.0-10.0)"),
      POINT_10("10-Point Integer (1-10)"),
      POINT_5("5-Star (0.5-5.0 ★)"),
      POINT_3("3-Point Smiley (😦 😐 😊)")
  }
  ```

### Conversion & Rounding Rules

```kotlin
object ScoreConverter {
    // Converts 0-100 DB score to UI display values
    fun scoreToDisplay(scoreRaw: Int?, format: ScoreFormat): String = when {
        scoreRaw == null || scoreRaw == 0 -> "Unrated"
        else -> when (format) {
            ScoreFormat.POINT_100 -> "$scoreRaw / 100"
            ScoreFormat.POINT_10_DECIMAL -> String.format(Locale.US, "%.1f", scoreRaw / 10.0)
            ScoreFormat.POINT_10 -> "${(scoreRaw / 10.0).roundToInt()}"
            ScoreFormat.POINT_5 -> String.format(Locale.US, "%.1f ★", (scoreRaw / 10.0).roundToInt() / 2.0)
            ScoreFormat.POINT_3 -> when {
                scoreRaw <= 35 -> "😦"
                scoreRaw <= 60 -> "😐"
                else -> "😊"
            }
        }
    }

    // Converts user UI inputs back to 0-100 DB score for storage and AniList push
    fun uiToScoreRaw(value: Double, format: ScoreFormat): Int = when (format) {
        ScoreFormat.POINT_100 -> value.toInt().coerceIn(1, 100)
        ScoreFormat.POINT_10_DECIMAL -> (value * 10.0).roundToInt().coerceIn(10, 100)
        ScoreFormat.POINT_10 -> (value * 10.0).roundToInt().coerceIn(10, 100)
        ScoreFormat.POINT_5 -> (value * 20.0).roundToInt().coerceIn(10, 100) // Supports half stars (0.5 * 20 = 10)
        ScoreFormat.POINT_3 -> when (value.toInt()) {
            1 -> 30  // 😦
            2 -> 50  // 😐
            3 -> 90  // 😊
            else -> 0
        }
    }
}
```

---

## 6. Full Pull, Conflict Resolution & Matching Algorithm

### Conflict Resolution Strategy (Newest Timestamp Wins)

```kotlin
fun resolveSyncConflict(local: MediaItem, remote: ALMediaListEntry): SyncDecision {
    val remoteUpdatedMs = (remote.updatedAt.toLong()) * 1000L
    val localUpdatedMs = local.lastUpdated
    
    return when {
        // Local edit is strictly newer -> Push local item to AniList
        localUpdatedMs > remoteUpdatedMs -> SyncDecision.PushToRemote
        
        // Remote edit is strictly newer -> Pull remote item to local DB
        remoteUpdatedMs > localUpdatedMs -> SyncDecision.PullFromRemote
        
        // Equal timestamps or tie -> Preserve local state
        else -> SyncDecision.KeepLocal
    }
}
```

### Live Push Trigger Architecture (Asynchronous Background Push)

```kotlin
fun triggerLivePush(item: MediaItem) {
    // 1. Immediate local SQLite upsert so UI updates instantly
    viewModelScope.launch {
        mediaRepository.upsert(item)
    }
    
    // 2. Asynchronous background push if item has AniList binding
    if (item.anilistListEntryId != null || item.sourceUrl?.contains("anilist.co") == true) {
        syncScope.launch {
            try {
                anilistSyncManager.pushItem(item)
            } catch (e: Exception) {
                // Network/API failure: log silently and leave item for next Full Push retry
                Log.w("LivePush", "Failed to live push ${item.id}, queued for batch push", e)
            }
        }
    }
}
```

### 3-Tier Matching Rules

When ingesting an `ALMediaListEntry` from AniList during Full Pull:

```kotlin
suspend fun findMatchingLocalItem(
    entry: ALMediaListEntry,
    inferredCategory: MediaCategory
): MediaItem? {
    val entryId = entry.id
    val mediaId = entry.mediaId
    val expectedAniListUrl = "https://anilist.co/${if (inferredCategory == MediaCategory.ANIME) "anime" else "manga"}/$mediaId"
    
    // Tier 1: Match by explicit cached anilistListEntryId
    val byEntryId = mediaDao.getByAnilistEntryId(entryId)
    if (byEntryId != null) return byEntryId.toDomain()

    // Tier 2: Match by exact AniList sourceUrl
    val byUrl = mediaDao.getBySourceUrl(expectedAniListUrl)
    if (byUrl != null) return byUrl.toDomain()

    // Tier 3: Match by Normalized Title + Local MediaCategory
    val rawTitle = entry.media?.title?.userPreferred 
        ?: entry.media?.title?.romaji 
        ?: entry.media?.title?.english 
        ?: return null
        
    val normalizedRemoteTitle = TitleNormalizer.normalize(rawTitle)
    val candidates = mediaDao.getAllUnboundByCategory(inferredCategory.name)
        .filter { TitleNormalizer.normalize(it.title) == normalizedRemoteTitle }

    // Tie-break rule: If multiple match, pick the unlinked item with the latest dateAdded
    return candidates.maxByOrNull { it.dateAdded }?.toDomain()
}
```

---

## 7. GraphQL API, Rate Limiting & Pagination Loop

### MediaListCollection Query DTO Guard & Deduplication

```kotlin
suspend fun fetchFullLibrary(type: String): List<ALMediaListEntry> {
    val allEntries = mutableListOf<ALMediaListEntry>()
    var chunk = 1
    var hasNextChunk = true
    
    while (hasNextChunk) {
        // Rate Limiter Interceptor delays if 85 requests/min limit is hit
        val response = api.getMediaListCollection(type = type, chunk = chunk, perPage = 500)
        
        val collection = response.data?.mediaListCollection
        if (collection == null) break
        
        collection.lists?.forEach { group ->
            group.entries?.forEach { entry ->
                // Null-guard: skip invalid/deleted media entries
                if (entry.media != null) {
                    allEntries.add(entry)
                }
            }
        }
        
        hasNextChunk = collection.hasNextChunk == true
        chunk++
    }
    
    // Deduplicate by MediaListEntry.id (distinctBy entry.id, NOT media.id)
    return allEntries.distinctBy { it.id }
}
```

---

## 8. Corrected Local Statistics Calculation (SQLite DAO)

Local statistics in `MediaItemDao.kt` filter strictly by LazyDex's local `category` enum:

```sql
@Query("""
    SELECT 
        (SELECT COUNT(*) FROM media_items) as totalCount,
        (SELECT COUNT(*) FROM media_items WHERE userStatus = 'COMPLETED') as completedCount,
        (SELECT COALESCE(SUM(currentProgress), 0) FROM media_items) as totalProgress,
        (SELECT AVG(rating) FROM media_items WHERE rating IS NOT NULL AND rating > 0) as meanRating,
        (SELECT COUNT(*) FROM media_items WHERE userStatus IN ('READING', 'WATCHING', 'PLAYING', 'REPEATING')) as inProgressCount,
        (SELECT COALESCE(SUM(currentProgress * COALESCE(durationMinutes, 24)), 0) FROM media_items WHERE category IN ('ANIME', 'MOVIE', 'TV')) as totalWatchMinutes,
        (SELECT COALESCE(SUM(currentProgress), 0) FROM media_items WHERE category IN ('MANGA', 'NOVEL')) as totalChaptersRead,
        (SELECT COALESCE(SUM(progressVolumes), 0) FROM media_items WHERE category IN ('MANGA', 'NOVEL')) as totalVolumesRead
""")
fun getExtendedStats(): Flow<ExtendedStatsQueryResult>
```

---

## 9. Implementation Order & Verification

1. **Phase P0 (Auth Foundation & Security)**: `TrackLoginActivity.kt`, CSRF `state` parameter validation, `AnilistTokenStore.kt` with `ScoreFormat` preference (defaults to `POINT_5`), and HTTP 401 handling in `AnilistInterceptor.kt`.
2. **Phase P1 (API & DTOs)**: GraphQL API queries with `$chunk`, `$perPage = 500`, null-safe media DTO guards, `distinctBy { it.id }` deduplication, and rate-limit backoff.
3. **Phase P2 (DB Migration Room v2 $\rightarrow$ v3)**: Add 13 columns (including `progressVolumes`), set Room migration `MIGRATION_2_3`, update `MediaItemDao.kt` queries.
4. **Phase P3 (Sync & Recategorization Logic)**: 3-tier item matching, `TitleNormalizer.kt`, category unbind on recategorization, score format conversions (`ScoreConverter.kt`), Newest Timestamp Wins conflict resolution, async background live push.
5. **Phase P4 (UI & Stats)**: Configurable `ScoreFormat` UI pickers in settings and entry edit screens, updated `StatisticsScreen.kt`.
