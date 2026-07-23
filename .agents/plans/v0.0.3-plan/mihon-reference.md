# Mihon AniList Sync — Architecture Reference

> Extracted from `mihonapp/mihon` GitHub source (main branch, 2026).
> Purpose: Reference for replicating the same patterns in LazyDex.
> Main plan: [`plan.md`](plan.md) | API reference: [`anilist-api-reference.md`](anilist-api-reference.md) | DB migration: [`db-migration.md`](db-migration.md)

---

## 1. File Structure (in Mihon)

```
data/track/
├── BaseTracker.kt              # Abstract tracker: login/logout, credentials, bind/update/refresh
├── Tracker.kt                   # Interface: all tracker operations
├── TrackerManager.kt            # Registry of all trackers (Anilist, MyAnimeList, Kitsu, etc.)
├── DeletableTracker.kt          # Interface for trackers that support delete
├── Track.kt                     # Domain model: manga_id, remote_id, library_id, score, status, dates
│
└── anilist/
    ├── Anilist.kt               # Concrete tracker: status constants, login, bind, update, refresh, delete
    ├── AnilistApi.kt            # GraphQL client: all queries + mutations
    ├── AnilistInterceptor.kt    # OkHttp interceptor: Bearer token + User-Agent
    └── dto/
        ├── ALOAuth.kt           # Token: access_token, token_type, expires, expires_in
        ├── ALManga.kt           # ALManga (search result) + ALUserManga (user's list entry)
        ├── ALSearchItem.kt      # Search result DTO → toALManga()
        ├── ALFuzzyDate.kt       # Year/month/day → epoch millis
        ├── ALUser.kt            # Viewer + mediaListOptions + scoreFormat
        ├── ALSearchResult.kt    # Search response wrapper
        ├── ALAddMangaResult.kt  # SaveMediaListEntry response
        ├── ALCurrentUserResult.kt # Viewer query response
        └── ALUserListMangaQueryResult.kt # Media list fetch response
```

---

## 2. Key Architecture Decisions

### 2a Track Model is Separate from Manga

Mihon's `Track` interface is a **join entity** linking manga to tracking service:

```kotlin
interface Track : Serializable {
    var id: Long?               // Local DB ID
    var manga_id: Long          // FK to manga
    var tracker_id: Long        // FK to tracker (Anilist = 2L)
    var remote_id: Long         // AniList media ID
    var library_id: Long?       // AniList list entry ID (cached)
    var title: String
    var last_chapter_read: Double
    var total_chapters: Long
    var score: Double           // Internally 0-100
    var status: Long            // Tracker-defined constants
    var started_reading_date: Long
    var finished_reading_date: Long
    var tracking_url: String
    var `private`: Boolean
}
```

**Why this matters**: A single manga can be tracked by multiple services (AniList AND MyAnimeList). LazyDex doesn't need this — each MediaItem maps to exactly one AniList entry. But the field design (remote_id, library_id, score/storage format, reading dates) is exactly what we need.

### 2b Tracker Interface

```kotlin
interface Tracker {
    val id: Long
    val name: String
    val client: OkHttpClient
    val supportsReadingDates: Boolean
    val supportsPrivateTracking: Boolean

    fun getStatusList(): List<Long>
    fun getStatus(status: Long): StringResource?
    fun getReadingStatus(): Long
    fun getRereadingStatus(): Long
    fun getCompletionStatus(): Long
    fun getScoreList(): List<String>
    fun indexToScore(index: Int): Double
    fun displayScore(track: DomainTrack): String

    suspend fun update(track: Track, didReadChapter: Boolean = false): Track
    suspend fun bind(track: Track, hasReadChapters: Boolean = false): Track
    suspend fun search(query: String): List<TrackSearch>
    suspend fun refresh(track: Track): Track
    suspend fun login(username: String, password: String)
    fun logout()
    val isLoggedIn: Boolean
    fun getUsername(): String
    fun getPassword(): String
    suspend fun register(item: Track, mangaId: Long)
    suspend fun setRemoteStatus(track: Track, status: Long)
    suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int)
    suspend fun setRemoteScore(track: Track, scoreString: String)
}
```

### 2c BaseTracker (Abstract Class)

Handles:
- Credential storage via `TrackPreferences` (SharedPreferences wrapper)
- `isLoggedIn` / `isLoggedInFlow`
- `setRemoteStatus`, `setRemoteLastChapterRead`, `setRemoteScore` — convenience wrappers that call `update()`
- `register()` — creates local Track entry linked to a manga
- `logout()` — clears stored credentials

### 2d Status as Long Constants (Not Enums)

Each tracker defines its own (LazyDex adds `REPEATING` to match — see [`db-migration.md`](db-migration.md#2-enum-change-userstatuskt)):

```kotlin
class Anilist(id: Long) : BaseTracker(id, "AniList"), DeletableTracker {
    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REREADING = 6L
    }
}
```

### 2e Score Format

Mihon internally stores scores as **0-100** (integer). User preference converts display:
- `POINT_100` — 0–100
- `POINT_10` — 0–10 (×10 internally)
- `POINT_5` — 0–5 stars (0→0, 1→10, 2→30, 3→50, 4→70, 5→90)
- `POINT_3` — Smiley
- `POINT_10_DECIMAL` — 0.0–10.0

API always sends/receives `scoreRaw: Int` (0–100) via `score(format: POINT_100)` query.

### 2f Token Storage

Credentials stored via `TrackPreferences`:
```kotlin
// Username = AniList user ID, Password = access_token
fun saveCredentials(username: String, password: String)
fun getUsername(): String
fun getPassword(): String
```

OAuth token serialized separately:
```kotlin
fun saveOAuth(alOAuth: ALOAuth?)
fun loadOAuth(): ALOAuth?
```

Uses regular `SharedPreferences` (not EncryptedSharedPreferences). LazyDex will use EncryptedSharedPreferences.

### 2g Rate Limiting

Applied via OkHttp `rateLimit` extension:
```kotlin
val authClient = client.newBuilder()
    .addInterceptor(interceptor)
    .rateLimit(permits = 85, period = 1.minutes)
    .build()
```

This is a built-in extension from Mihon's network library. LazyDex will implement a manual token-bucket.

---

## 3. Sync Flows (Per-Item)

### Bind Flow (linking a manga to AniList)

```
1. User opens a manga's track settings
2. Search AniList via api.search(query)
3. User selects match → bind(track, hasReadChapters)
4. bind():
   a. api.findLibManga(track, userId) → check if already in user's AniList
   b. If found:
      - Copy remote fields to local track (status, score, dates, chapters)
      - Set library_id
      - If hasReadChapters and not completed → set status=READING
      - update(track) → push changes
   c. If not found:
      - Set status = READING (if hasReadChapters) or PLAN_TO_READ
      - Set score = 0
      - api.addLibManga(track) → creates entry on AniList
```

### Update Flow (pushes local changes)

```
1. Called when user reads a chapter or changes status/score
2. update(track, didReadChapter):
   a. If library_id missing → fetch via findLibManga() (API v1 compat)
   b. Auto-status logic:
      - If last_chapter_read == total_chapters && total > 0 → COMPLETED
      - Else if status != REREADING → READING
      - If last_chapter_read == 1 → set started_reading_date
   c. api.updateLibManga(track) → sends status, progress, score, dates, private
```

### Refresh Flow (pulls remote data)

```
1. refresh(track):
   a. api.getLibManga(track, userId) → fetch from AniList
   b. Copy remote fields to local: status, score, dates, chapters
   c. Update title, total_chapters
```

### Delete Flow

```
1. delete(track):
   a. If library_id missing → findLibManga() first
   b. api.deleteLibManga(track.libraryId)
```

---

## 4. Auth Flow

```
1. OAuth URL: https://anilist.co/api/v2/oauth/authorize?client_id=16329&response_type=token
2. Redirect URI: mihon://anilist-auth
3. User authenticates in browser → redirected to mihon://anilist-auth#access_token=TOKEN&token_type=Bearer&expires_in=31535999
4. App captures intent → extracts token from URI fragment
5. login(token):
   a. api.createOAuth(token) → wraps in ALOAuth DTO
   b. interceptor.setAuth(oauth) → saves token in interceptor + persists via saveOAuth()
   c. api.getCurrentUser() → fetch user info + score format
   d. saveCredentials(userId, accessToken)
   e. Save score format to preferences
```

---

## 5. Token Expiry

```kotlin
// ALOAuth
data class ALOAuth(
    val accessToken: String,
    val tokenType: String,
    val expires: Long,       // epoch millis (adjusted: raw * 1000 - 60000)
    val expiresIn: Long,
)
fun ALOAuth.isExpired() = System.currentTimeMillis() > expires
```

Interceptor adjusts expiry: `value.expires * 1000 - 60 * 1000` (1 minute buffer).

---

## 6. GraphQL Query Details

> Full GraphQL reference with variables and responses: [`anilist-api-reference.md`](anilist-api-reference.md).

### Get Full User List

```graphql
query ($userId: Int, $type: MediaType) {
  MediaListCollection(userId: $userId, type: $type) {
    lists {
      name
      isCustomList
      status
      entries {
        id
        userId
        mediaId
        status
        score(format: POINT_100)
        progress
        progressVolumes
        repeat
        priority
        private
        notes
        hiddenFromStatusLists
        startedAt { year month day }
        completedAt { year month day }
        updatedAt
        createdAt
        media {
          id
          title { romaji english native userPreferred }
          coverImage { large medium }
          format
          status
          chapters
          volumes
          description
          averageScore
          genres
          countryOfOrigin
        }
      }
    }
    hasNextChunk
  }
}
```

### Save/Update Entry

```graphql
mutation ($mediaId: Int, $id: Int, $status: MediaListStatus, $progress: Int, $scoreRaw: Int, $private: Boolean, $startedAt: FuzzyDateInput, $completedAt: FuzzyDateInput) {
  SaveMediaListEntry(mediaId: $mediaId, id: $id, status: $status, progress: $progress, scoreRaw: $scoreRaw, private: $private, startedAt: $startedAt, completedAt: $completedAt) {
    id
    mediaId
    status
    score
    progress
    updatedAt
  }
}
```

### Find Single Entry

```graphql
query ($userId: Int!, $mediaId: Int!, $type: MediaType) {
  Page {
    mediaList(userId: $userId, type: $type, mediaId: $mediaId) {
      id
      status
      score(format: POINT_100)
      progress
      private
      startedAt { year month day }
      completedAt { year month day }
      media { id title { userPreferred } coverImage { large } format status chapters }
    }
  }
}
```

### Delete Entry

```graphql
mutation ($id: Int) {
  DeleteMediaListEntry(id: $id) {
    deleted
  }
}
```

### Get Current User

```graphql
query {
  Viewer {
    id
    name
    mediaListOptions { scoreFormat }
  }
}
```

### Search

```graphql
query ($query: String) {
  Page(perPage: 50) {
    media(search: $query, type: MANGA, format_not_in: [NOVEL]) {
      id
      title { userPreferred }
      coverImage { large }
      format
      chapters
      description
      averageScore
      startDate { year month day }
      staff { edges { role id node { name { full userPreferred native } } } }
    }
  }
}
```

---

## 7. LazyDex Differences from Mihon

> Full LazyDex implementation plan: [`plan.md`](plan.md).

| Aspect | Mihon | LazyDex |
|--------|-------|---------|
| Track model | Separate `Track` entity (1 per manga per service) | Fields live directly on `MediaItem` |
| Sync unit | Per-manga (bind + update) | Bulk pull + bulk push + live single push |
| Token storage | Plain SharedPreferences | EncryptedSharedPreferences |
| Rate limiting | OkHttp extension | Manual token-bucket |
| Score format | 0-100 internal | 1.0-5.0 stars → converted to 0-100 |
| Status | Long constants (1-6) | UserStatus enum |
| Categories | Only MANGA + ANIME | MANGA + ANIME + NOVEL (→ mapped to MANGA) |
| Initial import | None (must bind per manga) | Full pull from MediaListCollection |
