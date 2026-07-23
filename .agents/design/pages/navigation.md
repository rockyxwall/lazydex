# LazyDex Design — Navigation

> **Source:** `app.lazydex.ui.navigation` — `MainShellScreen.kt`, `NavGraph.kt`

---

## Navigation Map

```
                            ┌─────────────────────────┐
                            │     MainShellScreen     │
                            │  (Bottom NavigationBar) │
                            └───────────┬──┬──┬───────┘
                                        │  │  │
                  ┌─────────────────────┘  │  └─────────────────────┐
                  ▼                        ▼                        ▼
        ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────────┐
        │   DexScreen      │   │ StatisticsScreen │   │  SettingsTabContent  │
        │  (grid/list)     │   │  (3 stat cards)  │   │  (menu list)         │
        │  [tab:📖 Dex]    │   │  [tab:📊 Stats]  │   │  [tab:⚙️ Settings]  │
        └────────┬─────────┘   └──────────────────┘   └───┬───────┬───────┬──┘
                 │                                        │       │       │
          ┌──────▼──────┐                          ┌──────▼─┐ ┌──▼────┐ ┌▼──────┐
          │ AddEditRoute│                          │Appear- │ │Data & │ │About  │
          │ (itemId?)   │                          │ance    │ │Storage│ │Screen │
          └─────────────┘                          │Screen  │ │Screen │ │       │
                                                   └────────┘ └───────┘ └───────┘
```

---

## Bottom NavigationBar

```
    ┌──────────────────────────────────────────────────────┐
    │                                                       │
    │              Main Content Area                        │
    │        (Dex / Statistics / Settings)                  │
    │                                                       │
    ├──────────────────────────────────────────────────────┤
    │  ┌────────────┐    ┌────────────┐    ┌────────────┐  │
    │  │   📖 Dex   │    │ 📊 Stats   │    │  ⚙️ Set.   │  │
    │  │ (selected) │    │            │    │            │  │
    │  └────────────┘    └────────────┘    └────────────┘  │
    └──────────────────────────────────────────────────────┘
```

3 tabs via `NavigationBar` + `NavigationBarItem`:

| Index | Tab       | Icon          | Content              |
|-------|-----------|---------------|----------------------|
| 0     | Dex       | `Icons.Book`  | `DexScreen`          |
| 1     | Statistics| `Icons.BarChart` | `StatisticsScreen` |
| 2     | Settings  | `Icons.Settings` | `SettingsTabContent` |

Tab state persisted via `rememberSaveable { mutableStateOf(ShellTab.DEX) }`.

---

## Route Definitions

```kotlin
// Type-safe routes via kotlinx.serialization
@Serializable object MainShellRoute          // /
@Serializable data class AddEditRoute(       // /add-edit?itemId=xxx
    val itemId: String? = null
)
@Serializable object AppearanceRoute         // /appearance
@Serializable object DataAndStorageRoute     // /data-and-storage
@Serializable object AboutRoute              // /about
```

---

## NavGraph Structure

```
    NavHost(startDestination = MainShellRoute)
    │
    ├── composable<MainShellRoute>
    │   └── MainShellScreen
    │       ├── DexScreen(onNavigateToAddItem, onNavigateToEditItem)
    │       ├── StatisticsScreen
    │       └── SettingsTabContent
    │           ├── onNavigateToAppearance  ──► composable<AppearanceRoute>
    │           ├── onNavigateToDataAndStorage ─► composable<DataAndStorageRoute>
    │           └── onNavigateToAbout       ──► composable<AboutRoute>
    │
    ├── composable<AddEditRoute>
    │   └── UnifiedAddEditScreen(itemId, onBack)
    │
    ├── composable<AppearanceRoute>
    │   └── AppearanceScreen(onBack)
    │
    ├── composable<DataAndStorageRoute>
    │   └── DataAndStorageScreen(onBack)
    │
    └── composable<AboutRoute>
        └── AboutScreen(onBack)
```

---

## Navigation Flows

### Adding a New Item

```
    DexScreen                    UnifiedAddEditScreen
    ┌──────────┐    tap FAB [+]    ┌────────────────────┐
    │          │ ────────────────► │   Add Mode          │
    │   list   │                   │   (itemId = null)   │
    │          │                   │                    │
    │          │ ◄─── popBack() ───│   Save → Done      │
    └──────────┘                   └────────────────────┘
```

### Editing an Existing Item

```
    DexScreen                    UnifiedAddEditScreen
    ┌──────────┐   tap card(id)    ┌────────────────────┐
    │          │ ────────────────► │   Edit Mode          │
    │   list   │                   │   (itemId = "xxx")   │
    │          │                   │                    │
    │          │ ◄─── popBack() ───│   Save → Done       │
    │          │                   │   or Back → Discard │
    └──────────┘                   └────────────────────┘
```

### Settings Sub-screens

```
    SettingsTab                     AppearanceScreen
    ┌────────────┐  tap "Appear."   ┌────────────────────┐
    │ Appearance │ ───────────────► │ Theme / AMOLED      │
    │ Data       │                  │                    │
    │ About      │ ◄── popBack() ───│                    │
    └────────────┘                  └────────────────────┘
```

### Discard Flow (Edit Screen)

```
    ┌──────────────────┐   back press    ┌──────────────────┐
    │  Editing (dirty)  │ ─────────────► │  Discard Warning  │
    │                   │                │  Dialog           │
    │                   │                │                   │
    │                   │◄─ "Keep Edit"──│                   │
    │                   │                │                   │
    │                   │── "Discard" ──►│  popBack()        │
    └──────────────────┘                └──────────────────┘
```

---

## Key UX Rules

| Rule                        | Implementation                       |
|-----------------------------|--------------------------------------|
| Tab state survives rotation | `rememberSaveable`                   |
| Back from edit              | `navController.popBackStack()`       |
| Unsaved change protection   | `BackHandler` + `checkBackPressAllowed()` |
| Delete behavior             | Navigate back after deletion         |
| Settings sub-screens        | Full routes (not dialogs)            |
