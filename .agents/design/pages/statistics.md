# LazyDex Design — StatisticsScreen

> **Source:** `app.lazydex.ui.statistics` — `StatisticsScreen.kt`, `StatisticsViewModel.kt`

---

## Screen Layout

```
    ┌──────────────────────────────────────┐
    │  Statistics                          │  ← TopAppBar
    ├──────────────────────────────────────┤
    │                                       │
    │  Overview                             │  ← Section header (primary, 14sp)
    │                                       │
    │  ┌──────────────────────────────────┐│
    │  │  ┌─────────┐ ┌─────────┐ ┌─────┐ ││
    │  │  │   42    │ │   15    │ │ 1,247│ ││
    │  │  │In lib.  │ │Compltd │ │Total │ ││  ← Stat cells
    │  │  │  📚     │ │  ✓     │ │  📈  │ ││     in a row
    │  │  └─────────┘ └─────────┘ └─────┘ ││
    │  └──────────────────────────────────┘│
    │                                       │
    │  Categories Breakdown                 │  ← Section header
    │                                       │
    │  ┌──────────────────────────────────┐│
    │  │  ┌─────────┐  ┌─────────┐       ││
    │  │  │   12    │  │   18    │       ││
    │  │  │ Novels  │  │ Manga   │       ││  ← 2x2 grid
    │  │  │   📖   │  │   📖   │       ││     of categories
    │  │  └─────────┘  └─────────┘       ││
    │  │  ┌─────────┐  ┌─────────┐       ││
    │  │  │    8    │  │    4    │       ││
    │  │  │ Anime   │  │ Games   │       ││
    │  │  │   📺   │  │   🎮   │       ││
    │  │  └─────────┘  └─────────┘       ││
    │  └──────────────────────────────────┘│
    │                                       │
    │  Rating Details                       │  ← Section header
    │                                       │
    │  ┌──────────────────────────────────┐│
    │  │         ┌─────────────┐          ││
    │  │         │  3.45 ★     │          ││  ← Mean rating
    │  │         │ Mean Rating │          ││     centered
    │  │         │     ⭐     │          ││
    │  │         └─────────────┘          ││
    │  └──────────────────────────────────┘│
    │                                       │
    └──────────────────────────────────────┘
```

---

## Data Model (MediaStats)

```kotlin
data class MediaStats(
    val totalCount: Long,        // 42
    val completedCount: Long,    // 15
    val totalProgress: Long,     // 1247 (sum of currentProgress)
    val meanRating: Double?,     // 3.45
    val novelCount: Long,        // 12
    val mangaCount: Long,        // 18
    val animeCount: Long,        // 8
    val gameCount: Long,         // 4
    // (movieCount, tvCount may be added later)
)
```

---

## Card Layouts

### Overview Card

```
    ┌──────────────────────────────────────┐
    │ ┌──────────┐ ┌──────────┐ ┌────────┐│
    │ │    42    │ │    15    │ │  1,247 ││
    │ │In library│ │Completed │ │  Total ││
    │ │  📚     │ │  ✓      │ │  📈   ││
    │ └──────────┘ └──────────┘ └────────┘│
    └──────────────────────────────────────┘
    Three StatCells with equal weight, 16dp padding.
    Values: 20sp Bold. Labels: 11sp, 60% alpha. Icons: 18dp.
```

### Categories Breakdown Card

```
    ┌──────────────────────────────────────┐
    │ ┌──────────┐  ┌──────────┐          │
    │ │    12    │  │    18    │          │
    │ │  Novels  │  │  Manga   │          │
    │ │   📖    │  │   📖    │          │
    │ └──────────┘  └──────────┘          │
    │ ┌──────────┐  ┌──────────┐          │
    │ │    8     │  │    4     │          │
    │ │  Anime   │  │  Games   │          │
    │ │   📺    │  │   🎮    │          │
    │ └──────────┘  └──────────┘          │
    └──────────────────────────────────────┘
    2x2 grid, 16dp column spacing, 16dp row spacing.
```

### Rating Details Card

```
    ┌──────────────────────────────────────┐
    │           ┌──────────┐               │
    │           │  3.45 ★  │               │
    │           │  Mean    │               │
    │           │  Rating  │               │
    │           │    ⭐   │               │
    │           └──────────┘               │
    └──────────────────────────────────────┘
    Single StatCell centered, fillMaxWidth.
    "N/A" when meanRating is null.
```

---

## States

### Loading

```
    ┌──────────────────────────────────────┐
    │  Statistics                          │
    │                                       │
    │            ◎◎◎                        │  ← spinner
    │         (loading)                     │
    │                                       │
    └──────────────────────────────────────┘
```

### Loaded

Shows 3 cards as described above. Default values when `statsState` is null:

```kotlin
MediaStats(0, 0, 0, null, 0, 0, 0, 0, 0)
```

### Empty Library

```
    ┌──────────────────────────────────────┐
    │  Statistics                          │
    │                                       │
    │  Overview                             │
    │  ┌────────────────────────────┐      │
    │  │    0         0         0    │      │
    │  └────────────────────────────┘      │
    │                                       │
    │  Categories Breakdown                 │
    │  ┌────────────────────────────┐      │
    │  │    0         0             │      │
    │  │    0         0             │      │
    │  └────────────────────────────┘      │
    │                                       │
    │  Rating Details                       │
    │  ┌────────────────────────────┐      │
    │  │          N/A               │      │
    │  └────────────────────────────┘      │
    └──────────────────────────────────────┘
```

---

## User Interactions

| Action                     | Effect                              |
|----------------------------|-------------------------------------|
| Scroll                     | Vertical scroll through cards       |
| (No tap actions on stats)  | Read-only view                      |

---

## Implementation Notes

```kotlin
// Each section is a standalone Card with Surface bg
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    modifier = Modifier.fillMaxWidth()
)

// StatCell is a reusable composable:
@Composable
fun StatCell(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
)
```

No interactive elements — purely informational.
