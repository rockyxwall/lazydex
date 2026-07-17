# LazyDex — Design System (WTR-LAB Inspired)

> **Platform**: Android (Min SDK 26, Target 34)
> **UI Framework**: Jetpack Compose + Material3
> **Design Philosophy**: Dark-first, card-based, accent-driven

---

## 1. Theme Architecture

```
User Preference (DataStore)
    │
    ├── Theme Mode: [System / Dark / Light]
    ├── Amoled Mode: [On / Off]
    │
    ▼
LazyDexTheme(
    darkTheme = (system ? isSystemInDark() : preference),
    dynamicColor = SDK >= 31,
    amoledMode = preference
)
    │
    ├── SDK >= 31 & dynamicColor → dynamicLightColorScheme / dynamicDarkColorScheme
    │       └── Override with custom values below
    ├── else → custom LightColorScheme / DarkColorScheme
    │
    ├── amoledMode & darkTheme → override background/surface to pure black (#000000)
    │
    ▼
MaterialTheme(colorScheme = finalScheme, typography, shapes)
```

**Default**: Dark mode on first launch.

---

## 2. Color Palette

### 2.1 Core Colors (Dark Mode Default)

| Token | Hex | Role |
|-------|-----|------|
| `background` | `#1b1d23` | Main app background (slate-gray) |
| `surface` | `#1f2129` | Card surfaces, bottom sheets |
| `surfaceVariant` | `#23272c` | Popovers, dropdowns |
| `toolbar` | `#212529` | TopAppBar, nav bar |
| `primary` | `#5795d9` | Primary action buttons, active states |
| `primaryContainer` | `#1a3a5c` | Selected chip backgrounds |
| `secondary` | `#444e5b` | Secondary actions |
| `accent` | `#fd7e14` | Progress indicators, highlights (WTR orange) |
| `onBackground` | `#a9a9a9` | Primary text on background |
| `onSurface` | `#cccccc` | Text on cards |
| `muted` | `#111111` | Muted backgrounds, tab containers |
| `onMuted` | `#a9b1b7` | Muted text |
| `outline` | `rgba(255,255,255,0.1)` | Card borders, dividers |
| `inputBorder` | `rgba(255,255,255,0.15)` | Text field borders |

### 2.2 Light Mode Mapped Colors

| Token | Hex |
|-------|-----|
| `background` | `#f2f3f4` |
| `surface` | `#ffffff` |
| `primary` | `#4288c9` |
| `onSurface` | `#212529` |
| `outline` | `#d4dadb` |

### 2.3 Status Colors (Reserved for Status Badges Only)

| Status | Color | Usage |
|--------|-------|-------|
| In Progress (Reading/Watching/Playing) | `#5795d9` (primary) | Active items |
| Completed | `#22c55e` | Finished items |
| On Hold | `#eab308` | Paused items |
| Dropped | `#ef4444` | Abandoned items |
| Plan to | `#6b7280` | Future items |

### 2.4 Category Representation (Neutral Outline + Icon)

| Category | Icon | Outline Color |
|----------|------|---------------|
| Novel | `📖` (book icon) | `#5795d9` |
| Manga | `📘` (book icon) | `#22c55e` |
| Anime | `📺` (tv icon) | `#ef4444` |
| Game | `🎮` (gamepad icon) | `#a855f7` |
| Movie | `🎬` (film icon) | `#f97316` |
| TV | `📺` (tv icon) | `#14b8a6` |

Categories use **outline-style badges** (no fill) with a distinct Material icon. Color is only used for the outline/icon stroke, never as a filled background.

### 2.5 Rating Colors (WTR-LAB Rate Scale)

| Rating | Color |
|--------|-------|
| 5.0 (Highest) | `#22c55e` |
| 4.0 | `#84cc16` |
| 3.0 | `#eab308` |
| 2.0 | `#f97316` |
| 1.0 (Lowest) | `#ef4444` |

---

## 3. Typography

### 3.1 Font Stacks

```kotlin
// Google Fonts loaded at app level
// Display/Body: Nunito Sans (400, 600, 700 weights)
// Mono/Counter: JetBrains Mono (400, 500, 700 weights)
```

### 3.2 Type Scale

| Token | Font | Size | Weight | Line Height | Use Case |
|-------|------|------|--------|-------------|----------|
| `display` | Nunito Sans | 24sp | 700 | 1.25 | Screen titles |
| `titleLarge` | Nunito Sans | 20sp | 700 | 1.3 | Card headers, section titles |
| `titleMedium` | Nunito Sans | 16sp | 600 | 1.4 | Subheaders |
| `bodyLarge` | Nunito Sans | 16sp | 400 | 1.5 | Reading text |
| `bodyMedium` | Nunito Sans | 14sp | 400 | 1.55 | Default body |
| `bodySmall` | Nunito Sans | 13sp | 400 | 1.5 | Descriptions, metadata |
| `labelLarge` | Nunito Sans | 14sp | 600 | 1.4 | Buttons, active tabs |
| `labelMedium` | Nunito Sans | 12sp | 600 | 1.4 | Badges, tags, dates |
| `labelSmall` | Nunito Sans | 11sp | 600 | 1.4 | Captions |
| `counter` | JetBrains Mono | 12sp | 400 | 1.5 | Progress counters (e.g. "42/100") |
| `rating` | JetBrains Mono | 14sp | 500 | 1.5 | Star ratings |

### 3.3 Compose Type Mapping

```kotlin
val LazyDexTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
```

---

## 4. Component Styling

### 4.1 Cards

| Property | Value |
|----------|-------|
| Background | `surface` (`#1f2129`) |
| Border | `1px solid outline` (`rgba(255,255,255,0.1)`) |
| Corner radius | `8dp` (Material3 `RoundedCornerShape(8.dp)`) |
| Shadow | `0 1px 3px rgba(0,0,0,0.1)` (subtle) |
| Padding | 12dp horizontal, 12dp vertical |
| Max width (reading) | Not applicable (mobile full-width) |

### 4.2 Progress Bar (Accent-Driven)

- **Default**: Orange gradient (`#fd7e14` → `#ffaa60`)
- **Completed**: Green gradient (`#22c55e` → `#4ade80`)
- **Track**: Muted background (`#111` dark, `#f0f0f0` light)
- **Height**: 5dp
- **Corner radius**: 3dp

### 4.3 Badges

| Type | Style | Example |
|------|-------|---------|
| Status badge | Filled pill with status color, white text | `[Reading]` in blue |
| Category badge | Outline pill with category icon + outline color | `📖 Novel` in outline style |
| Rating badge | Star icon + JetBrains Mono number | `⭐ 4.5` |

### 4.4 TopAppBar / Toolbar

| Property | Value |
|----------|-------|
| Background | `toolbar` (`#212529` in dark) |
| Content color | `onBackground` (`#a9a9a9` in dark) |
| Height | 56dp |

### 4.5 Bottom Sheet

| Property | Value |
|----------|-------|
| Background | `surface` (`#1f2129`) |
| Handle | Centered thin pill, `outline` color |
| Corner radius | 12dp top corners |

### 4.6 Text Fields (Outlined)

| State | Border | Label |
|-------|--------|-------|
| Default | `inputBorder` | `onMuted` |
| Focused | `primary` | `primary` |
| Error | `destructive` (`#dc3545`) | `destructive` |
| Disabled | `inputBorder` at 0.5 alpha | `onMuted` at 0.5 alpha |

### 4.7 Star Rating Display

- Displayed as star icons (filled/empty/half)
- Rating number in JetBrains Mono next to stars
- Color interpolated on the 1→5 rate scale:
  - 1.0: `#ef4444` (red)
  - 2.0: `#f97316` (orange)
  - 3.0: `#eab308` (yellow)
  - 4.0: `#84cc16` (lime)
  - 5.0: `#22c55e` (green)

---

## 5. Spacing Scale

| Token | DP | Usage |
|-------|----|-------|
| `xxs` | 4dp | Icon gaps, inner padding |
| `xs` | 8dp | Chip gaps, small margins |
| `sm` | 12dp | Card padding, list spacing |
| `md` | 16dp | Section margins, form spacing |
| `lg` | 24dp | Screen padding, card groups |
| `xl` | 32dp | Large section breaks |

---

## 6. Component Tree (Updated)

```
HomeScreen
├── TopAppBar (LazyDex title, [Filter] [Sort] buttons, Settings gear)
├── Active filter chips (compact, below toolbar — shows current selections)
├── LazyColumn
│   └── MediaCard (READ-ONLY: cover, title, category icon, status badge, progress text, rating)
├── FilterBottomSheet (Modal — Category + Status selection)
├── SortBottomSheet (Modal — Sort by + ASC/DESC)
├── EmptyState (filter-aware)
└── FAB [+] → UnifiedAddEditScreen (mode=add)

UnifiedAddEditScreen (single screen for Add + Edit/Detail)
├── TopAppBar ([←], title = "Add Media" or "Edit Media", [🗑️] only in edit mode)
├── Cover Image (scraped/downloaded, shown from local path)
├── Title (editable)
├── Alternative Titles (dynamic list: add/remove/swap-to-main)
├── Category chips (editable)
├── Status chips (editable, category-adaptive)
├── Rating (1.0–5.0 star input)
├── Progress + Total (editable number fields)
├── Notes (editable)
├── Source URL (editable + Open URL button)
└── [SAVE] button

SettingsScreen
├── Data section (Export, Import + overwrite hold-to-confirm)
├── Theme section (Dark/Light/System toggle, Amoled mode)
├── Backup section (Auto-backup schedule + folder picker)
└── About section (version, licenses)
```

---

## 7. Database Fields (Updated Room Entity)

```kotlin
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val category: String,            // "NOVEL", "MANGA", etc.
    val title: String,
    val alternativeTitles: String,   // JSON array: ["Alt 1", "Alt 2", ...]
    val sourceUrl: String?,
    val coverImagePath: String,      // Local file path (not URL)
    val currentProgress: Int,
    val totalItems: Int?,
    val userStatus: String,          // "READING", "COMPLETED", etc.
    val rating: Double?,             // 1.0–5.0, null = unrated
    val notes: String,
    val lastUpdated: Long,
    val dateAdded: Long
)
```

**New fields vs original plan**:
- `alternativeTitles`: JSON list (flexible, not limited to 2)
- `coverImagePath`: Local file path instead of URL
- `rating`: User rating 1.0–5.0 stars

**Removed vs original plan**:
- `coverImageUrl`: Replaced by `coverImagePath` (images downloaded locally)

---

## 8. Image Management

### Cover Image Lifecycle
1. **Scrape**: MetadataScraper extracts `og:image` URL
2. **Download**: ViewModel triggers Coil/OkHttp download → saves to `{appInternalDir}/covers/{itemId}.{ext}`
3. **Path stored**: `coverImagePath` in Room DB (not the URL)
4. **Display**: Coil loads from local file path
5. **Fallback**: If download fails, user provides direct URL → download triggered again
6. **On delete**: Cover image file deleted alongside database entry

### Grid Generation for Missing Covers
- First letter of title on gradient background
- Category icon overlaid
- Gradient derived from category color with dark overlay

---

## 9. Key UX Rules

- Cards are **read-only** in lists. No progress buttons.
- Tap card → UnifiedAddEditScreen (edit mode)
- Progress edited only via UnifiedAddEditScreen
- Save disabled until actual changes detected
- Delete always shows confirmation dialog
- Back navigation with unsaved changes → confirmation dialog
- Overwrite import → press-and-hold 5–10 seconds
- Scrape blocks only conflicting fields (title, cover, alt titles, category)
- Progress > total → red error, Save disabled
- Rating is user-input 1.0–5.0 stars (half-star increments)
- Alternative titles: dynamic list, tap to swap with main title
- Default sort: `dateAdded DESC` (no teleporting). User can change via Sort button.
