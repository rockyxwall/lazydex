# LazyDex — UI/UX Flow Architecture

> **Platform**: Android (Min SDK 26, Target 34)  
> **Tech**: Jetpack Compose + Material3 + MVVM  
> **Navigation**: Single-Activity, Navigation Compose 2.8 (type-safe routes)

---

## 1. Feature Inventory

| # | Feature | Description | Screens Involved |
| |---------|-------------|-----------------|
| 1 | **Add/Edit Media** | Unified screen for adding + editing. URL scrape auto-fills title, cover, alt titles, category | UnifiedAddEdit |
| 2 | **Progress Tracking** | Edit progress in UnifiedAddEdit screen. Auto-complete status when progress = total | UnifiedAddEdit |
| 3 | **Status Workflow** | 5 statuses (category-adaptive): in-progress → Completed → On Hold → Dropped → Plan to | UnifiedAddEdit |
| 4 | **Filtered List** | [Filter] + [Sort] buttons in toolbar → ModalBottomSheet. Notion-inspired | Home |
| 5 | **Read-Only List** | Cards show info only — no progress buttons. Tap card → UnifiedAddEdit | Home |
| 6 | **Rating** | User-input 1.0–5.0 stars (half-star increments), displayed as stars | UnifiedAddEdit |
| 7 | **Alternative Titles** | Dynamic list (JSON), swap-to-main UI, scraped from URL | UnifiedAddEdit |
| 8 | **Cover Images** | Downloaded to local storage (named by item ID), not URL-based | UnifiedAddEdit |
| 9 | **Backup/Restore** | SAF JSON export/import + merge/overwrite (press-and-hold 5–10s for overwrite) | Settings |
| 10 | **Auto-Backup** | WorkManager scheduled with SAF folder picker + persisted URI | Settings |
| 11 | **Theming** | Dark-first, WTR-LAB palette, Light toggle, Dynamic Color (Monet), Amoled mode | Settings → Global |
| 12 | **Settings Hub** | Data ops, Theme toggles, Auto-backup config, About | Settings |

---

## 2. Navigation Map

```
┌──────────────┐
│   HomeScreen │ ◄──────────────────────┐
│  (Read-Only  │                        │
│   List)      │                        │
└──┬───┬───┬───┘                        │
   │   │   │                            │
   │   │   └── FAB [+] ─────────────────┐
   │   │           (mode=add)           │
   │   └── Tap Card ──────────────┐     │
   │          (mode=edit)         │     │
   ▼                             ▼     │
┌───────────────────────────────────┐  │
│    UnifiedAddEditScreen           │  │
│  (Single screen: Add OR Edit)     │──┤
│  - Add mode: empty fields + scrap │  │
│  - Edit mode: pre-filled + save   │  │
│  - Delete button (edit mode only) │  │
│  - Rating, alt titles, cover path │  │
└──────────────┬────────────────────┘  │
               │ Delete confirmation   │
               ▼                       │
          HomeScreen ◄─────────────────┘
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

### 3.1 HomeScreen — Read-Only List

**Entry Point**: App launch (if empty → filter-aware EmptyState)

```
┌─────────────────────────────────────┐
│ LazyDex        [Filter][Sort] [⚙️] │  ← TopAppBar with Filter+Sort buttons
├─────────────────────────────────────┤
│ [Novel] [Manga] [Anime] ... (chips) │  ← Active filter pills (compact, below toolbar)
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐  │
│ │ 🖼️ Title of Media          ⭐ │  │
│ │    [Reading]   📖 Novel       │  │  ← MediaCard (READ-ONLY)
│ │    Ch. 42 / 100  •  5m ago    │  │
│ └───────────────────────────────┘  │
│ ┌───────────────────────────────┐  │
│ │ 🖼️ Another Title          ⭐ │  │
│ │    [Watching]  📺 Anime       │  │
│ │    Ep. 12 / 24  •  2h ago     │  │
│ └───────────────────────────────┘  │
├─────────────────────────────────────┤
│                          [+] FAB   │
└─────────────────────────────────────┘
```

**Interactions**:

| Gesture | Action | Destination |
|---------|--------|-------------|
| Tap card body | Navigate to UnifiedAddEditScreen (edit mode) with item `id` | UnifiedAddEditScreen |
| Tap [Filter] | Open FilterBottomSheet (Modal): Category + Status selection | — |
| Tap [Sort] | Open SortBottomSheet (Modal): Sort by + ASC/DESC | — |
| Tap FAB [+] | Open UnifiedAddEditScreen (add mode) | UnifiedAddEditScreen |
| Tap Settings ⚙️ | Navigate to SettingsScreen | SettingsScreen |

**State**:
```kotlin
data class HomeUiState(
    val items: List<MediaItem> = emptyList(),
    val selectedCategory: MediaCategory? = null,  // null = All
    val selectedStatus: UserStatus? = null,        // null = All
    val sortOrder: SortOrder = SortOrder.DATE_ADDED_DESC,
    val isLoading: Boolean = true
)

enum class SortOrder {
    DATE_ADDED_DESC,    // Default — no teleporting
    LAST_UPDATED_DESC,
    TITLE_ASC,
    PROGRESS_ASC
}
```

**FilterBottomSheet** (ModalBottomSheet):
- Category section: [All] [Novels] [Manga] [Anime] [Games] [Movies] [TV]
- Status section: [All] [Reading] [Watching] [Playing] [Completed] [On Hold] [Dropped] [Plan to]
- [Clear Filters] button at bottom
- Active filters shown as compact chips below TopAppBar

**SortBottomSheet** (ModalBottomSheet):
- Sort by: [Date Added] [Last Active] [Title] [Progress %]
- Order: [↑ Ascending] [↓ Descending]
- Current selection highlighted

**Edge Cases**:
- Empty list + no filters → EmptyState: "Nothing here yet. Tap + to add your first item."
- Empty list + filters active → EmptyState: "No items match your filters. [Clear Filters]"
- Cards are READ-ONLY — no progress controls on cards. All editing via UnifiedAddEditScreen.
- **Teleporting prevention**: Default sort by `dateAdded DESC` — no item jumps position on edit.

---

### 3.2 UnifiedAddEditScreen — Full-Screen Form (Add + Edit modes)

**Trigger**: FAB [+] (add mode) or Tap Card (edit mode) from HomeScreen

Single screen handles both adding new items (empty fields, scrape available) and editing existing items (pre-filled from DB, delete button visible).

```
┌─────────────────────────────────────┐
│ [←] Add Media / Edit Media [🗑️]    │  ← Delete only in edit mode
├─────────────────────────────────────┤
│                                     │
│       ┌───────────────────┐         │
│       │  🖼️ Cover Image   │         │  ← Local file (downloaded)
│       │  (local path,     │         │    Gradient+initials fallback
│       │   Coil async)     │         │
│       └───────────────────┘         │
│                                     │
│  URL (scrape trigger)              │
│  ┌─────────────────────────────┐    │
│  │ https://novel.site.com/...  │    │  ← Add mode only
│  └─────────────────────────────┘    │
│  [🔍 Auto-fill]  ← triggers scrape │
│  (blocks: title, cover, alt titles, │
│   category during scrape)           │
│                                     │
│  Title *                           │
│  ┌─────────────────────────────┐    │
│  │ Editable text field         │    │  ← Scrape fills this (blocked)
│  └─────────────────────────────┘    │
│                                     │
│  Alternative Titles                │
│  ┌─────────────────────────────┐    │  ← Dynamic list (0+ items)
│  │ Alt Title 1           [↕]   │    │    [↕] = swap with main title
│  │ Alt Title 2           [↕]   │    │    [×] = remove
│  │ Alt Title 3           [↕]   │    │
│  │ [+ Add Alternative Title]   │    │
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
│  │Reading │Completed│On Hold │       │  ← Category-adaptive chips
│  ├────────┼────────┼────────┤       │    (editable, defaults to smart value)
│  │ Dropped│Plan to │        │       │
│  └────────┴────────┴────────┘       │
│                                     │
│  Rating                             │
│  ┌─────────────────────────────┐    │
│  │ ⭐ ⭐ ⭐ ⭐ ⭐  4.5/5.0      │    │  ← Tappable stars (half-star)
│  └─────────────────────────────┘    │
│                                     │
│  Progress         Total            │
│  ┌──────┐        ┌──────┐          │
│  │  42  │        │ 100  │          │  ← Number keyboard
│  └──────┘        └──────┘          │
│  ⚠ Progress cannot exceed total    │  ← Red error text if progress > total
│                                     │
│  Notes                             │
│  ┌─────────────────────────────┐    │
│  │ Editable text field         │    │
│  └─────────────────────────────┘    │
│                                     │
│  Source URL                        │
│  ┌─────────────────────────────┐    │
│  │ https://novel.site.com/...  │    │  ← Editable
│  └─────────────────────────────┘    │
│  [🌐 Open URL]  ← launches browser │
│                                     │
│  ┌─────────────────────────────┐    │
│  │          SAVE               │    │  ← Disabled if validation fails
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Flow States**:
```
IDLE ──► SCRAPING ──► AUTO-FILLED ──► VALIDATING ──► SAVING ──► DONE (pop back)
            │                              │
            ▼                              ▼
         ERROR                         VALIDATION ERROR
      (inline message)              (inline field errors)
```

**Scrape Flow** (Add mode only):
1. User enters URL → taps "Auto-fill"
2. **Blocked during scrape**: Title, Cover Image, Alternative Titles, Category fields show spinner
3. **Active during scrape**: Progress, Total, Status, Notes, Rating remain editable
4. Success → scraped values filled into blocked fields. If user already typed in a field and scrape finds different info → replaced with scraped value. If scrape finds nothing → user's text preserved.
5. Auto-detect: if progress = total → auto-set status to Completed
6. Failure → inline error "Could not auto-fill, please enter manually", blocked fields re-enabled

**Cover Image Logic**:
- Scraper downloads cover image to `{appInternalDir}/covers/{itemId}.{ext}`
- `coverImagePath` stored in Room (no URL saved)
- If download fails → user provides direct URL → download saved locally
- Fallback display: gradient background + category icon + first letter of title

**Alternative Titles UI**:
- Stored as JSON array in single Room column: `["Alt Title 1", "Alt Title 2", ...]`
- Displayed as list with: [↕] swap button, [×] remove button, [+ Add] button
- Tap [↕] on any alt title → it becomes main title, old main title moves to alt list position 1
- Scrape fills alt titles from page metadata

**Rating UI**:
- 5 tappable stars (filled/half/empty)
- Half-star precision (0.5 increments)
- Numerical display: "4.5/5.0"
- Rating color interpolated: 1=red → 3=yellow → 5=green

**Validation Rules**:

| Field | Rule | Error Behavior |
|-------|------|---------------|
| Title | Required, non-empty after trim | Block save, show inline error |
| Category | Must be selected (default: Novel) | — |
| URL | Optional, must be HTTPS if provided | Block save if invalid |
| Progress | ≥ 0, must be ≤ total if total set | Red error text, **Save disabled** |
| Total | ≥ 0 if provided, null = unknown | — |
| Rating | 1.0–5.0, optional (null = unrated) | — |
| Similar title | Check if main/alt title matches existing item | Warning: "Similar title exists" |

**Status Derivation** (smart default, fully editable):
- Novel/Manga → **Reading**
- Anime/Movie/TV → **Watching**
- Game → **Playing**

**Safe Int Parsing**: Progress → `text.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0`  
Total → `text.takeIf { it.isNotBlank() }?.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()`

**Data Flow** (Edit mode):
```
Screen receives itemId nav arg (or none for add mode)
    │
    ▼
ViewModel
    ├── If edit mode: repository.observeById(id).take(1) → initialize draft MutableStateFlow
    ├── If add mode: empty draft MutableStateFlow
    │       ▼
    ├── UI binds to draft state (independent of Room re-emissions)
    │
    ├── on Save → item.copy(lastUpdated = now, 
    │       coverImagePath = downloadIfNeeded(...)
    │   ).normalize()
    │       → if add: repository.add(item)
    │       → if edit: repository.update(item)
    │
    └── on Delete → confirmation dialog → repository.delete(id) → pop to Home
```

**Interactions**:

| Action | Behavior |
|--------|----------|
| Edit any field | Changes reflected in local draft state |
| Change category | Status auto-remaps: READING↔WATCHING↔PLAYING based on new category (in-progress only) |
| Progress > total | Red error text: "Progress cannot exceed total (N)". Save button disabled until fixed. |
| Tap Save | `repository.update(item.copy(lastUpdated = now).normalize())` (or `repository.add(...)` for new) |
| Tap Delete (edit only) | Confirmation dialog: "Delete 'Title'? This cannot be undone." → `repository.delete(id)` → pop back |
| Tap Open URL | `Intent.ACTION_VIEW` with try/catch for `ActivityNotFoundException` |
| Back press + unsaved changes | Discard confirmation dialog: "Discard unsaved changes? [Keep Editing] [Discard]" |

**Edge Cases**:
- Item deleted externally → `observeById` emits null → navigate back
- Two screens editing same item → last save wins (no optimistic locking for v1)
- Blank title on save → ViewModel blocks with inline error
- Progress > Int.MAX_VALUE → safe parsing prevents crash
- Scrape in progress + screen dismissed → coroutine cancelled via viewModelScope
- Add mode + no URL → manual entry only, no scrape triggered

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

**Overwrite Safety**: Press-and-hold the confirmation button for 5–10 seconds to execute destructive overwrite.

**Auto-Backup Directory**: When user selects a backup schedule, SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`) opens → user selects destination folder → URI persisted via `takePersistableUriPermission` → displayed in Settings UI.

**Edge Cases**:
- Empty backup file → "Backup file contains no valid items" error
- Corrupted JSON → error dialog "Backup file appears corrupted"
- Old backup (no schemaVersion) → defaults to 1, loads successfully
- Schema version > 1 → "Unsupported backup schema version: X"
- File > 50MB → abort with "Backup file is too large"
- Export to full storage → toast "Could not save backup. Check storage space."
- No browser installed → Toast "No web browser found"
- Auto-backup temp file collision → unique UUID-based temp filenames
- Auto-backup retention: keep last 30 files, delete older

---

## 4. Shared Component Library

| Component | Purpose | Inputs |
|-----------|---------|--------|
| `MediaCard` | List item card (READ-ONLY) | `item: MediaItem`, `onTap` |
| `FilterBottomSheet` | Category + status filter bottom sheet | `selectedCategory`, `selectedStatus`, `onApply` |
| `SortBottomSheet` | Sort options bottom sheet | `currentSort`, `onApply` |
| `CategoryBadge` | Outline + icon badge | `category: MediaCategory` |
| `StatusBadge` | Color-filled status pill | `status: UserStatus` |
| `StarRating` | Tappable star rating (1.0–5.0) | `rating: Double?`, `onChange`, `interactive: Boolean` |
| `AltTitleEditor` | Dynamic alt title list with swap/remove/add | `titles: List<String>`, `mainTitle: String`, `onSwap`, `onAdd`, `onRemove` |
| `EmptyState` | Filter-aware placeholder | `message: String`, `actionLabel: String?`, `onAction`, `isFiltered: Boolean` |

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

**Color Strategy**: Status gets color badges. Category gets neutral outline + Material icon (e.g., 📖 Novel, 🎮 Game, 🎬 Movie).

**Status Colors** (reserved for status only):
| Status | Color |
|--------|-------|
| In Progress (Reading/Watching/Playing) | `#5795d9` (primary blue) |
| Completed | `#22c55e` (green) |
| On Hold | `#eab308` (yellow) |
| Dropped | `#ef4444` (red) |
| Plan to | `#6b7280` (gray) |

**Category Representation** (neutral outline + icon, no color fill):
| Category | Icon |
|----------|------|
| Novel | Book icon |
| Manga | Book icon |
| Anime | TV icon |
| Game | Gamepad icon |
| Movie | Film icon |
| TV | TV icon |

**Rating Colors** (1.0–5.0 scale):
| Rating | Color |
|--------|-------|
| 5.0 | `#22c55e` |
| 4.0 | `#84cc16` |
| 3.0 | `#eab308` |
| 2.0 | `#f97316` |
| 1.0 | `#ef4444` |

**Badge Backgrounds**: `color.copy(alpha = 0.15f)` for status pills.

**First Launch**: Dark theme (default) with WTR-LAB palette (`#1b1d23` background, `#1f2129` cards, `#5795d9` primary, `#fd7e14` accent). See `DESIGN.md` for full theme mapping.

---

## 6. Error Handling Map

| Layer | Error | UX Treatment |
|-------|-------|-------------|
| Add/Edit | Similar title detected | Inline warning: "Similar title already exists" |
| Add/Edit | Scrape failure | Inline error below URL field |
| Add/Edit | Validation | Inline field-level errors |
| Add/Edit | Blank title | Inline error "Title is required" |
| Add/Edit | Progress > total | Red error text, Save disabled |
| Add/Edit | DB constraint on add | Error inline "Failed to save" |
| Add/Edit | Item deleted externally | Auto-navigate back to Home |
| Add/Edit | Discard unsaved changes | Confirmation dialog: "Discard changes?" |
| Add/Edit | Delete | Confirmation dialog before deletion |
| Settings | Export failure | Error toast |
| Settings | Import failure | Error dialog |
| Settings | Corrupt/empty backup | Error dialog |
| Settings | Overwrite import | Press-and-hold 5–10s to confirm |
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
            "alternativeTitles": ["Alt Title 1", "Alt Title 2"],
            "sourceUrl": "https://novel.site.com/example",
            "currentProgress": 42,
            "totalItems": 100,
            "userStatus": "READING",
            "rating": 4.5,
            "notes": "My reading notes",
            "lastUpdated": 1700000000000,
            "dateAdded": 1700000000000
        }
    ]
}
```

**New fields vs original**:
- `alternativeTitles`: JSON array (flexible list, not limited to 2)
- `rating`: 1.0–5.0 stars (nullable = unrated)
- `dateAdded`: Timestamp for stable sort order (no teleporting)
- `coverImageUrl` removed (covers stored as local files, not URLs)

**Robustness**: Missing/invalid fields handled gracefully — missing schemaVersion defaults to 1, missing title rejects item, bad status defaults to category-appropriate status, negative progress clamped to 0, missing lastUpdated → epoch 0 (local wins in merge).
