# LazyDex Design — Settings

> **Source:** `app.lazydex.ui.settings` — `AppearanceScreen.kt`, `DataAndStorageScreen.kt`, `AboutScreen.kt`

---

## 1. Settings Tab (Main Menu)

Rendered directly in the `MainShellScreen` when Settings tab is selected.

```
    ┌──────────────────────────────────────┐
    │                                       │
    │              ┌──────┐                 │
    │              │  L/D │                 │  ← App icon (72dp)
    │              └──────┘                 │
    │                                       │
    │  🎨 Appearance                        │
    │     Theme, date & time format         │  ← Clickable row
    │ ───────────────────────────────────── │
    │  💾 Data and storage                  │
    │     Manual & automatic backups...     │  ← Clickable row
    │ ───────────────────────────────────── │
    │  ℹ️ About                             │
    │     LazyDex Stable v0.1.0             │  ← Clickable row
    │                                       │
    └──────────────────────────────────────┘
```

Each row uses `SettingsCategoryItem` composable:
- Icon (24dp, primary color)
- Title (15sp, Medium)
- Subtitle (12sp, 60% alpha)

---

## 2. AppearanceScreen

```
    ┌──────────────────────────────────────┐
    │  ← Appearance                        │  ← TopAppBar with back
    ├──────────────────────────────────────┤
    │                                       │
    │  Theme                                │
    │                                       │
    │  ┌────────────────────────────┐      │
    │  │  System  │ Light │  Dark   │      │  ← Segmented button
    │  └────────────────────────────┘      │  (rounded pill)
    │                                       │
    │  ┌────┐ ┌────┐ ┌────┐ ┌────┐        │
    │  │ ██ │ │ ██ │ │ ██ │ │ ██ │        │  ← Theme carousel
    │  │ ░░ │ │ ░░ │ │ ░░ │ │ ░░ │        │     (LazyRow)
    │  │ ░░ │ │ ░░ │ │ ░░ │ │ ░░ │        │
    │  └────┘ └────┘ └────┘ └────┘        │
    │  Def.   Dyn.  Grn   Prpl            │
    │        (Monet) (Mng) (Gme)          │
    │                                       │
    │  Pure black dark mode     [toggle]    │  ← AMOLED Switch
    │ ───────────────────────────────────── │
    │                                       │
    │  Media Info                           │
    │                                       │
    │  Theme based on cover     [toggle]    │  ← Cover theming Switch
    │                                       │
    └──────────────────────────────────────┘
```

### Theme Carousel Presets

| Preset          | Primary    | Background |
|-----------------|------------|------------|
| Default         | #5795D9    | #121318    |
| Dynamic (Monet) | #8AB4F8    | #1F1F1F    |
| Green (Manga)   | #22C55E    | #0F172A    |
| Purple (Game)   | #A855F7    | #1E1B4B    |

Each preset shown as 90×130dp mini preview card with mock content.

### Theme Mode State

```kotlin
// Stored in DataStore via SettingsViewModel
state.themeMode     // "SYSTEM" | "LIGHT" | "DARK"
state.amoledMode    // Boolean
state.coverTheming  // Boolean
```

---

## 3. DataAndStorageScreen

```
    ┌──────────────────────────────────────┐
    │  ← Data and storage                  │  ← TopAppBar
    ├──────────────────────────────────────┤
    │                                       │
    │  Backup and restore                   │
    │                                       │
    │  ┌──────────────────────────────────┐│
    │  │  Local Backups                   ││
    │  │  Export your local media tracker ││
    │  │  data and covers, or import      ││
    │  │  backups using Android SAF.      ││
    │  │                                  ││
    │  │  ┌────────────┐ ┌─────────────┐  ││
    │  │  │Create      │ │Restore      │  ││  ← OutlinedButtons
    │  │  │backup      │ │backup       │  ││
    │  │  └────────────┘ └─────────────┘  ││
    │  └──────────────────────────────────┘│
    │                                       │
    │  ℹ️ You should keep copies of        │
    │  backups in other places as well.    │
    │                                       │
    │  Show restoring progress   [toggle]   │  ← Switch
    │                                       │
    └──────────────────────────────────────┘
```

### Export Dialog

```
    ┌──────────────────────────────────────┐
    │  Export Options                       │
    │                                      │
    │  Do you want to package local cover  │
    │  images in your backup? Including    │
    │  covers increases backup file size.  │
    │                                      │
    │       [Metadata Only]                │  ← dismissButton
    │       [Metadata + Covers]            │  ← confirmButton
    └──────────────────────────────────────┘
```

### Import Merge/Overwrite Dialog

```
    ┌──────────────────────────────────────┐
    │  Import Options                       │
    │                                      │
    │  Found 42 media items in the backup  │
    │  file.                               │
    │                                      │
    │  Merge: Adds new items and overwrites│
    │  conflicts where the import file     │
    │  contains a newer timestamp.         │
    │                                      │
    │  Overwrite: Wipes all your local     │
    │  database trackers and covers...     │
    │                                      │
    │       [Merge]                        │
    │       [Overwrite (Hold 5s)]          │  ← red, press & hold
    │                                      │
    │  (During hold: progress bar shown)   │
    │  ████████░░░░░░░░  60%              │
    │  "Restoring... Keep holding down..." │
    └──────────────────────────────────────┘
```

**Safety mechanism:** Overwrite requires 5-second press-and-hold. Shows `LinearProgressIndicator` during hold.

---

## 4. AboutScreen

```
    ┌──────────────────────────────────────┐
    │  ← About                             │  ← TopAppBar
    ├──────────────────────────────────────┤
    │                                       │
    │            ⬭⬭⬭                       │  ← Canvas logo
    │           ⬭   ⬭                      │     (glasses icon)
    │            ⬭⬭⬭                       │
    │                                       │
    │         LazyDex                       │  ← App name, 22sp Bold
    │     Stable v0.1.0                     │  ← Version, 13sp
    │                                       │
    │  ┌──────────────────────────────────┐│
    │  │  Check for updates               ││
    │  │  What's new                      ││  ← Clickable rows
    │  │  What's coming (soon)            ││     (AboutLinkItem)
    │  │  Help translate                  ││
    │  │  Open source licenses            ││  → opens dialog
    │  │  Privacy policy                  ││
    │  └──────────────────────────────────┘│
    │                                       │
    │       🌐  💬  💻                     │  ← Social icons
    │     GitHub Discord GitHub            │
    │                                       │
    └──────────────────────────────────────┘
```

### Licenses Dialog

```
    ┌──────────────────────────────────────┐
    │  Open Source Licenses                │
    │                                      │
    │  Jetpack Compose & Material3         │
    │  Apache License 2.0                  │
    │                                      │
    │  Room Database                       │  ← Scrollable list
    │  Apache License 2.0                  │
    │  ...                                 │
    │                                      │
    │            [Dismiss]                 │
    └──────────────────────────────────────┘
```

---

## Settings Data Flow

```kotlin
// ViewModel manages DataStore-backed state
class SettingsViewModel : ViewModel() {
    val uiState: StateFlow<SettingsUiState>

    fun setThemeMode(mode: String)     // "SYSTEM" | "LIGHT" | "DARK"
    fun setAmoledMode(enabled: Boolean)
    fun setCoverTheming(enabled: Boolean)
    fun exportBackup(context, uri, includeCovers)
    fun importBackup(context, uri)
    fun executeMerge()
    fun executeOverwrite()
    fun cancelImport()
}
```

---

## Key UX Rules

| Rule                          | Implementation                       |
|-------------------------------|--------------------------------------|
| Theme mode persists           | DataStore Preferences                |
| AMOLED only works in dark     | Switch disabled when mode = LIGHT    |
| Cover theming toggle          | Independent of theme mode            |
| Export uses SAF               | `CreateDocument` contract            |
| Import uses SAF               | `OpenDocument` contract              |
| Toast feedback                | `LaunchedEffect` on success/error    |
| Overwrite requires hold       | `pointerInput.detectTapGestures` + timer |
| Merge is standard             | Single tap to execute                |
