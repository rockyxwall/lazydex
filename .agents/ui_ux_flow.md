# LazyDex — UI/UX Flow Architecture

> **Platform**: Android (Min SDK 26, Target 34)  
> **Tech**: Jetpack Compose + Material3 + MVVM  
> **Navigation**: Single-Activity, Navigation Compose 2.8 (type-safe routes)

---

## 1. Feature Inventory

| # | Feature | Description | Screens Involved |
|---|---------|-------------|-----------------|
| 1 | **Add Media** | Manual entry + URL scrape (novels) auto-fill | AddItemSheet → Home |
| 2 | **Progress Tracking** | Increment/decrement via atomic SQL buttons | Home (cards), Detail |
| 3 | **Status Workflow** | 7 statuses: Reading/Watching/Playing → Completed → On Hold → Dropped → Plan to | Detail, AddItemSheet |
| 4 | **Filtered List** | Category chips + status chips, SQL-backed filtering | Home |
| 5 | **Detail View** | Full detail + inline editing + source URL launch | Detail |
| 6 | **Edit & Delete** | Inline edit on Detail, delete with confirmation | Detail |
| 7 | **Backup/Restore** | SAF JSON export/import + merge/overwrite | Settings |
| 8 | **Auto-Backup** | WorkManager scheduled daily/weekly | Background |
| 9 | **Theming** | Dark default, Light toggle, Dynamic Color (Monet), Amoled mode | Settings → Global |
| 10 | **Settings Hub** | Data ops, Theme toggles, Auto-backup config, About | Settings |

---

## 2. Navigation Map

```
┌──────────────┐
│   HomeScreen │ ◄──────────────────┐
│  (Main List) │                    │
└──┬───┬───┬───┘                    │
   │   │   │                        │
   │   │   └── FAB [+] ─────────────┐
   │   │                            │
   │   └── Tap Card ─────────┐      │
   │                         │      │
   ▼                         ▼      │
┌──────────────┐    ┌──────────────┐ │
│ AddItemSheet │    │ DetailScreen │─┤
│ (BottomSheet)│    │ (Inline Edit)│ │
└──────────────┘    └──────┬───────┘ │
                           │         │
                           │ Delete  │
                           ▼         │
                      HomeScreen ◄───┘
                           ▲
┌──────────────┐           │
│SettingsScreen│── Back ───┘
│  (Full Page) │
└──────────────┘
     │
     │ Export/Import via SAF
     ▼
(SAF System Document Picker)
```

---

## 3. Screen-by-Screen UX Flow

### 3.1 HomeScreen — Main List

**Entry Point**: App launch (if empty → EmptyState)

```
┌─────────────────────────────────────┐
│ [←] LazyDex                  [⚙️]  │  ← TopAppBar
├─────────────────────────────────────┤
│ [All] [Novels] [Manga] [Anime] ... │  ← CategoryFilterChips
│ [All] [Reading] [Completed] ...    │  ← StatusFilterChips
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐  │
│ │ 🖼️ Title of Media             │  │
│ │    [Novel] [Reading]          │  │  ← MediaCard
│ │    Ch. 42 / 100  •  5m ago    │  │
│ │              [-] 42 [+]       │  │  ← ProgressControls
│ └───────────────────────────────┘  │
│ ┌───────────────────────────────┐  │
│ │ 🖼️ Another Title              │  │
│ │    [Anime] [Watching]         │  │
│ │    Ep. 12 / 24  •  2h ago     │  │
│ │              [-] 12 [+]       │  │
│ └───────────────────────────────┘  │
├─────────────────────────────────────┤
│                          [+] FAB   │
└─────────────────────────────────────┘
```

**Interactions**:

| Gesture | Action | Destination |
|---------|--------|-------------|
| Tap [+] on card | `repository.incrementProgress(id)` (atomic SQL) | — |
| Tap [-] on card | `repository.decrementProgress(id)` (atomic SQL) | — |
| Tap card body | Navigate to DetailScreen with item `id` | DetailScreen |
| Long-press card | Context menu: Edit / Delete | DetailScreen / Delete dialog |
| Tap category chip | Filter list (reactive via `flatMapLatest`) | — |
| Tap status chip | Filter list by status | — |
| Tap FAB [+] | Open AddItemSheet | AddItemSheet |
| Tap Settings ⚙️ | Navigate to SettingsScreen | SettingsScreen |

**State**:
```kotlin
data class HomeUiState(
    val items: List<MediaItem> = emptyList(),
    val selectedCategory: MediaCategory? = null,  // null = All
    val selectedStatus: UserStatus? = null,        // null = All
    val isLoading: Boolean = true
)
```

**Edge Cases**:
- Empty list → `EmptyState` composable: "Nothing here yet. Tap + to add your first item."
- Rapid [+] taps → atomic SQL `MIN(currentProgress + 1, totalItems)` — no debounce
- Rapid card taps → 500ms debounce on `OpenItem` event channel
- **Teleporting prevention**: List sorted by `dateAdded DESC` not `lastUpdated DESC` — tapping [+] does not move the card

---

### 3.2 AddItemSheet — Bottom Sheet

**Trigger**: FAB [+] from HomeScreen

```
┌─────────────────────────────────────┐
│  Add Media Item         [Cancel]    │  ← Sheet handle + toolbar
├─────────────────────────────────────┤
│                                     │
│  URL                               │
│  ┌─────────────────────────────┐    │
│  │ https://novel.site.com/...  │    │
│  └─────────────────────────────┘    │
│  [🔍 Auto-fill]  ← triggers scrape │
│                                     │
│  ┌──────┬──────┬──────┬──────┐      │
│  │Novel │Manga │Anime │Game  │      │  ← Category chips
│  ├──────┼──────┼──────┼──────┤      │
│  │Movie │ TV   │      │      │      │
│  └──────┴──────┴──────┴──────┘      │
│                                     │
│  Title *                           │
│  ┌─────────────────────────────┐    │
│  │ Auto-filled or manual       │    │
│  └─────────────────────────────┘    │
│                                     │
│  Cover Image URL                   │
│  ┌─────────────────────────────┐    │
│  │ https://cover.site.com/...  │    │
│  └─────────────────────────────┘    │
│                                     │
│  Progress         Total            │
│  ┌──────┐        ┌──────┐          │
│  │  1   │        │ 100  │          │  ← Number keyboard
│  └──────┘        └──────┘          │
│                                     │
│  Notes (optional)                  │
│  ┌─────────────────────────────┐    │
│  │                             │    │
│  └─────────────────────────────┘    │
│                                     │
│  Status: [Reading] (auto-derived)   │  ← Read-only label
│                                     │
│  ┌─────────────────────────────┐    │
│  │          + ADD              │    │  ← Primary button
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Flow States**:
```
IDLE ──► SCRAPING ──► AUTO-FILLED ──► VALIDATING ──► SAVING ──► DONE
            │                              │
            ▼                              ▼
         ERROR                         VALIDATION ERROR
      (inline message)              (inline message)
```

**Scrape Flow**:
1. User enters URL → taps "Auto-fill"
2. Loading spinner on button, form fields disabled
3. `viewModelScope` launches `scraper.scrape(url)`
4. **Success**: Title + Cover URL auto-filled, form re-enabled
5. **Failure**: Inline error "Could not auto-fill, please enter manually", form re-enabled

**Validation Rules**:

| Field | Rule | Error Behavior |
|-------|------|---------------|
| Title | Required, non-empty after trim | Block save, show inline error |
| Category | Must be selected (default: Novel) | — |
| URL | Optional, must be HTTPS if provided | Block save if invalid |
| Cover URL | Optional, must be valid http/https | Block save if invalid |
| Progress | ≥ 0 (default 0), must be ≤ total if total set | Block save with "Progress cannot exceed total" |
| Total | ≥ 0 if provided, null = unknown | — |
| Duplicate URL | `repository.existsByUrl(normalizedUrl)` | Warning "This URL is already tracked" |

**Status Derivation**: Status is auto-set based on category (read-only):
- Novel/Manga → **Reading**
- Anime/Movie/TV → **Watching**
- Game → **Playing**

**Safe Int Parsing**: Progress → `text.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0`  
Total → `text.takeIf { it.isNotBlank() }?.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()`

**Edge Cases**:
- Dismiss during scrape → coroutine cancelled via `viewModelScope`
- Rotation during scrape → ViewModel survives, loading state preserved via `StateFlow`
- Scrape succeeds while user manually edited → don't overwrite manual edits
- Double-tap Add → disabled after first tap, catch `DuplicateUrlException` → inline error

---

### 3.3 DetailScreen — Inline Editing

**Trigger**: Tap card body on HomeScreen, or Edit from long-press context menu

```
┌─────────────────────────────────────┐
│ [←] Details               [🗑️]     │  ← TopAppBar with delete
├─────────────────────────────────────┤
│                                     │
│       ┌───────────────────┐         │
│       │   🖼️ Cover Image  │         │  ← Larger preview
│       │   (async via Coil)│         │
│       └───────────────────┘         │
│                                     │
│  Title                             │
│  ┌─────────────────────────────┐    │
│  │ Editable text field         │    │  ← Inline editable
│  └─────────────────────────────┘    │
│                                     │
│  Category                          │
│  ┌──────┬──────┬──────┬──────┐      │
│  │Novel │Manga │Anime │Game  │      │  ← Editable chips
│  ├──────┼──────┼──────┼──────┤      │
│  │Movie │ TV   │      │      │      │
│  └──────┴──────┴──────┴──────┘      │
│                                     │
│  Status                            │
│  ┌────────┬────────┬────────┐       │
│  │Watching│Completed│On Hold │       │  ← Context-adaptive chips
│  ├────────┼────────┼────────┤       │    (category-aware)
│  │ Dropped│Plan to │        │       │
│  └────────┴────────┴────────┘       │
│                                     │
│  Progress         Total            │
│  ┌──────┐        ┌──────┐          │
│  │  42  │        │ 100  │          │  ← Editable, Number keyboard
│  └──────┘        └──────┘          │
│  (Capped to 100)                   │  ← Clamp hint on blur
│                                     │
│  Notes                             │
│  ┌─────────────────────────────┐    │
│  │ Editable notes field        │    │
│  └─────────────────────────────┘    │
│                                     │
│  Source URL                        │
│  ┌─────────────────────────────┐    │
│  │ https://novel.site.com/...  │    │  ← Editable
│  └─────────────────────────────┘    │
│  [🌐 Open URL]  ← launches browser │
│                                     │
│  ┌─────────────────────────────┐    │
│  │          SAVE               │    │  ← Primary button
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Data Flow**:
```
DetailScreen (id nav arg)
    │
    ▼
DetailViewModel
    ├── savedStateHandle.toRoute<DetailRoute>().id
    │
    ├── repository.observeById(id).take(1)   ← read once to initialize
    │       ▼
    ├── MutableStateFlow<MediaItem>(draft state)  ← independent of Room re-emissions
    │       ▼
    ├── UI binds to draft state
    │
    └── on Save → repository.update(normalized)
```

**Interactions**:

| Action | Behavior |
|--------|----------|
| Edit title, category, progress, total, notes, URL | Changes reflected in local draft state |
| Change category | Status auto-remaps: READING↔WATCHING↔PLAYING based on new category |
| Tap Save | `repository.update(item.copy(lastUpdated = now).normalize())` |
| Tap Delete | Confirmation dialog: "Delete 'Title'? This cannot be undone." → `repository.delete(id)` → pop back |
| Tap Open URL | `Intent.ACTION_VIEW` with try/catch for `ActivityNotFoundException` |
| Edit sourceUrl to duplicate | `DuplicateUrlException` caught → inline error "This URL is already tracked" |
| Back press | Discard unsaved changes (no confirmation — v1 simplicity) |

**Progress Clamping** (Dual Strategy):
- **Client-side**: On blur, cap progress field to `totalItems`, show inline hint "Capped to N"
- **ViewModel-side**: On save, if clamp was needed, emit snackbar event "Progress capped to N"
- **Repository-side**: `normalize()` always clamps — authoritative enforcement

**Edge Cases**:
- Item deleted externally (other screen) → `observeById` emits null → `ItemDeletedExternally` event → pop back
- Two screens editing same item → last save wins (no optimistic locking for v1)
- Blank title on save → ViewModel blocks with inline error
- Progress > Int.MAX_VALUE → safe parsing prevents crash

---

### 3.4 SettingsScreen — Full Page

**Trigger**: Gear icon on HomeScreen TopAppBar

```
┌─────────────────────────────────────┐
│ [←] Settings                        │  ← TopAppBar
├─────────────────────────────────────┤
│                                     │
│  ┌─ DATA ───────────────────────┐   │
│  │                              │   │
│  │  [📤 Export Backup]          │   │  → SAF CreateDocument
│  │  [📥 Import Backup]          │   │  → SAF OpenDocument (.json)
│  │                              │   │
│  └──────────────────────────────┘   │
│                                     │
│  ┌─ THEME ──────────────────────┐   │
│  │                              │   │
│  │  Dark Theme    [🔘]          │   │  ← Toggle
│  │  Amoled Mode   [🔘]          │   │  ← Toggle (pure black)
│  │                              │   │
│  └──────────────────────────────┘   │
│                                     │
│  ┌─ BACKUP ─────────────────────┐   │
│  │                              │   │
│  │  Auto-backup                 │   │
│  │  [Never] [Daily] [Weekly]    │   │  ← Radio group
│  │                              │   │
│  └──────────────────────────────┘   │
│                                     │
│  ┌─ ABOUT ──────────────────────┐   │
│  │                              │   │
│  │  LazyDex v1.0.0             │   │
│  │  Built with Kotlin &        │   │
│  │  Jetpack Compose            │   │
│  │                              │   │
│  │  [📄 Open Source Licenses]   │   │
│  │                              │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

**Export Flow**:
```
Tap Export
    → SAF CreateDocument launcher (suggest "lazydex_backup.json")
        → User picks location
            → repository.getAll()
            → BackupProcessor.serialize(items)       ← streaming to avoid OOM
            → BackupManager.export(context, uri, json)
                → Write to temp file (unique UUID name)
                → Copy to SAF URI via contentResolver
                → Delete temp file
            → Toast "Backup exported successfully"
```

**Import Flow**:
```
Tap Import
    → SAF OpenDocument launcher (filter: application/json)
        → User picks file
            → BackupManager.import(context, uri)     ← streaming JSON
            → BackupProcessor.deserialize(json) → List<MediaItem>
            → Guard: if empty → error "No valid items found"
            → Show choice dialog:
                ┌────────────────────────┐
                │  Import Options        │
                │                        │
                │  [Merge] — dedup by    │
                │  id + sourceUrl,       │
                │  newest timestamp wins │
                │                        │
                │  [Overwrite] — replace │
                │  all local data        │
                │  (atomic @Transaction) │
                └────────────────────────┘
                    │
                    ├─ Merge → BackupProcessor.merge(local, imported)
                    │          → repository.replaceAll(merged)
                    │
                    └─ Overwrite → repository.replaceAll(imported)
            → Toast "Imported X items"
```

**Merge Logic**:
```kotlin
// Keyed by id (UUID)
// Timestamps decide conflicts, local wins ties
// Deduplicates by BOTH id AND normalized sourceUrl
// Pure-additions with valid timestamps keep them
// Items without timestamps (0L) get staggered current time
// All results normalized before persist
```

**Edge Cases**:
- Empty backup file → "Backup file contains no valid items" error
- Corrupted JSON → error dialog "Backup file appears corrupted"
- Old backup (no schemaVersion) → defaults to 1, loads successfully
- Schema version > 1 → "Unsupported backup schema version: X"
- File > 50MB → abort with "Backup file is too large"
- Export to full storage → toast "Could not save backup. Check storage space."
- No browser installed → Toast "No web browser found"
- Auto-backup temp file collision → unique UUID-based temp filenames

---

## 4. Shared Component Library

| Component | Purpose | Inputs |
|-----------|---------|--------|
| `MediaCard` | List item card | `item: MediaItem`, `onIncrement`, `onDecrement`, `onTap`, `onLongClick` |
| `CategoryFilterChips` | Filter row | `selected: MediaCategory?`, `onSelect: (MediaCategory?)` |
| `StatusFilterChips` | Status filter row | `selected: UserStatus?`, `onSelect: (UserStatus?)` |
| `CategoryBadge` | Color-coded category | `category: MediaCategory` |
| `StatusBadge` | Color-coded status | `status: UserStatus` |
| `ProgressControls` | [-] N [+] row | `current: Int, total: Int?, onInc, onDec` |
| `EmptyState` | Placeholder | `message: String`, `actionLabel: String?`, `onAction` |

---

## 5. Theme Architecture

```
User Preference
    │
    ├── Theme Mode: [System / Dark / Light]     ← persisted via DataStore
    ├── Amoled Mode: [On / Off]                  ← persisted via DataStore
    │
    ▼
LazyDexTheme(
    darkTheme = (system ? isSystemInDark() : preference),
    dynamicColor = SDK >= 31,
    amoledMode = preference
)
    │
    ├── SDK >= 31 & dynamicColor → dynamicLightColorScheme / dynamicDarkColorScheme
    ├── else → custom LightColorScheme / DarkColorScheme
    │
    ├── amoledMode & darkTheme → override background/surface to pure black (#000000)
    │
    ▼
MaterialTheme(colorScheme = finalScheme, typography, shapes)
```

**Category Colors**: Novel=Blue, Manga=Green, Anime=Red, Game=Purple, Movie=Orange, TV=Teal  
**Status Colors**: InProgress=Blue, Completed=Green, OnHold=Yellow, Dropped=Red, PlanTo=Gray  
**Badge Backgrounds**: `color.copy(alpha = 0.15f)`

**First Launch**: Dark theme (default). User can switch to System or Light in Settings.

---

## 6. Error Handling Map

| Layer | Error | UX Treatment |
|-------|-------|-------------|
| Add | Duplicate URL | Inline warning below URL field |
| Add | Scrape failure | Inline error below URL field |
| Add | Validation | Inline field-level errors |
| Add | DB constraint | `DuplicateUrlException` → inline error |
| Detail | Duplicate URL on save | Inline error below URL field |
| Detail | Blank title | Inline error "Title is required" |
| Detail | Progress clamped on save | Snackbar "Progress capped to N" |
| Detail | Item deleted externally | Auto-navigate back to Home |
| Settings | Export failure | Error toast |
| Settings | Import failure | Error dialog |
| Settings | Corrupt/empty backup | Error dialog |
| Global | DB corruption | Error screen with "Reset Database" button |
| Global | No browser | Toast "No web browser found" |
| Global | Strings | Via `strings.xml` + `UiText` sealed interface |

---

## 7. Backup JSON Schema

```json
{
    "schemaVersion": 1,
    "items": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "category": "NOVEL",
            "title": "Example Novel",
            "sourceUrl": "https://novel.site.com/example",
            "coverImageUrl": "https://cdn.site.com/cover.jpg",
            "currentProgress": 42,
            "totalItems": 100,
            "userStatus": "READING",
            "notes": "My reading notes",
            "lastUpdated": 1700000000000
        }
    ]
}
```

**Robustness**: Missing/invalid fields handled gracefully — missing schemaVersion defaults to 1, missing title rejects item, bad status defaults to category-appropriate status, negative progress clamped to 0, missing lastUpdated → epoch 0 (local wins in merge).
