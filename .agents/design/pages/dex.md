# LazyDex Design — DexScreen

> **Source:** `app.lazydex.ui.dex` — `DexScreen.kt`, `DexViewModel.kt`

---

## Screen Flow

```
    ┌──────────────────────────────────────┐
    │  Dex                         🔍 ⊙   │  ← TopAppBar (title + count badge)
    │                                       │
    │  [Novels ✖] [Anime ✖]                │  ← Active filter pills (InputChip)
    │                                       │
    │  ┌──────┐ ┌──────┐ ┌──────┐         │
    │  │      │ │      │ │      │         │
    │  │  OP  │ │  AOT │ │  CSM │         │  ← Grid (LazyVerticalGrid, 3 cols)
    │  │      │ │      │ │      │         │
    │  │85%   │ │42%   │ │18%   │         │
    │  │4.5★  │ │3.0★  │ │2.5★  │         │
    │  └──────┘ └──────┘ └──────┘         │
    │  ┌──────┐ ┌──────┐ ┌──────┐         │
    │  │      │ │      │ │      │         │
    │  │  JJK │ │  NM  │ │ Vag. │         │
    │  │      │ │      │ │      │         │
    │  │72%   │ │ 6%   │ │34%   │         │
    │  │3.5★  │ │2.0★  │ │4.0★  │         │
    │  └──────┘ └──────┘ └──────┘         │
    │                                       │
    │                          ┌─────┐      │
    │                          │  ＋  │      │  ← FAB (Add)
    │                          └─────┘      │
    ├──────────────────────────────────────┤
    │    📖 Dex     📊 Stats     ⚙️       │  ← Bottom NavigationBar
    └──────────────────────────────────────┘
```

### State: Loading

```
    ┌──────────────────────────────────────┐
    │  Dex                         1       │
    │                                       │
    │            ◎◎◎                        │  ← CircularProgressIndicator
    │         (spinning)                    │
    │                                       │
    └──────────────────────────────────────┘
```

### State: Empty (No Items)

```
    ┌──────────────────────────────────────┐
    │  Dex                         0       │
    │                                       │
    │                                       │
    │      Nothing here yet.               │
    │  Tap [+] to add your first           │
    │      tracking item.                  │
    │                                       │
    └──────────────────────────────────────┘
```

### State: Empty (Filtered)

```
    ┌──────────────────────────────────────┐
    │  Dex                         0       │
    │  [Manga ✖] [Completed ✖]           │
    │                                       │
    │    No items match your filters       │
    │                                       │
    │        ┌──────────────┐              │
    │        │ Clear Filters│              │
    │        └──────────────┘              │
    └──────────────────────────────────────┘
```

### State: List View (not Grid)

```
    ┌──────────────────────────────────────┐
    │  Dex                        12       │
    │                                       │
    │  ┌──────────────────────────────────┐│
    │  │ ┌────┐  One Piece        2h ago  ││
    │  │ │    │  ★★★★☆              ││
    │  │ │cover│                     ││
    │  │ │    │  📖 Manga  │ 🔵 IP  ││
    │  │ └────┘  Ch.1054/1100      ││
    │  └──────────────────────────────────┘│
    │  ┌──────────────────────────────────┐│
    │  │ ┌────┐  AOT              1d ago ││
    │  │ │    │  ★★★☆☆              ││
    │  │ │cover│                     ││
    │  │ │    │  📺 Anime   │ 🔵 IP  ││
    │  │ └────┘  Ep.22/??           ││
    │  └──────────────────────────────────┘│
    │                          ┌─────┐      │
    │                          │  ＋  │      │
    │                          └─────┘      │
    └──────────────────────────────────────┘
```

---

## Bottom Sheet (Filter/Sort/Display)

Triggered by tapping the filter icon (⊙) in the TopAppBar.

```
    ┌──────────────────────────────────────┐  ← ModalBottomSheet
    │  ┌────────┬────────┬────────┐        │
    │  │ Filter │  Sort  │ Display│        │  ← TabRow
    │  └────────┴────────┴────────┘        │
    │                                      │
    │  [Filter Tab selected]               │
    │  Category                            │
    │  [All] [Novels] [Manga] [Anime] [TV] │
    │  [Movies] [Games]                    │
    │                                      │
    │  Status                              │
    │  [All] [In Progress] [Completed]     │
    │  [On Hold] [Dropped] [Plan to]      │
    │                                      │
    │  ┌──────────────────────────────────┐│
    │  │          Clear Filters           ││
    │  └──────────────────────────────────┘│
    └──────────────────────────────────────┘
```

### Filter Tab

| Control     | Type       | Values                                      |
|-------------|------------|---------------------------------------------|
| Category    | FilterChips| All, Novel, Manga, Anime, TV, Movie, Game   |
| Status      | FilterChips| All, In Progress, Completed, On Hold, Dropped, Plan to |
| Clear All   | Button     | Resets all filters                          |

### Sort Tab

| Control     | Type       | Values                                      |
|-------------|------------|---------------------------------------------|
| Field       | RadioButton| Title, Last Active (default), Progress, Date Added |
| Direction   | FilterChips| Ascending ↑, Descending ↓                   |

### Display Tab

| Control     | Type       | Values                                      |
|-------------|------------|---------------------------------------------|
| Layout Mode | FilterChips| Grid (default), List                         |

---

## User Interactions

| Action                     | Effect                                    |
|----------------------------|-------------------------------------------|
| Tap card                   | Navigate to `UnifiedAddEditScreen(itemId)` |
| Tap FAB [+]                | Navigate to `UnifiedAddEditScreen(new)`    |
| Tap Filter icon (⊙)        | Open LibraryBottomSheet                    |
| Tap Search icon (🔍)       | (Not yet wired — placeholder)              |
| Tap filter pill close (✖)  | Remove that filter, update list            |
| Tap bottom nav tab         | Switch between Dex / Statistics / Settings |
| Grid/List toggle           | Switch LazyVerticalGrid ↔ LazyColumn      |

---

## Key Implementation Details

```kotlin
// Layout: Grid (3 cols) with padding 4dp
LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    contentPadding = PaddingValues(4.dp)
) { ... }

// Layout: List (full width)
LazyColumn { ... }

// State defaults
isGridView = true     // Grid is default
librarySheetTab = 0    // Filter tab is default
```

---

## Visual Weight Map

```
    ┌──────────────────────────────────────┐
    │  Header (TopAppBar)                  │  ← 56dp
    ├──────────────────────────────────────┤
    │  Filter pills (conditional)          │  ← ~32dp
    ├──────────────────────────────────────┤
    │                                      │
    │         Main Content Area             │  ← fills remaining
    │    (Grid: 3-col LazyVerticalGrid     │
    │     or List: LazyColumn)             │
    │                                      │
    │                                      │
    ├──────────────────────────────────────┤
    │  FAB [+]                             │  ← 56dp circle, bottom-right
    ├──────────────────────────────────────┤
    │  Bottom NavigationBar                │  ← 80dp
    └──────────────────────────────────────┘
```
