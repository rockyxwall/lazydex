# LazyDex UI Design Hub

> Master index connecting all UI design documents.

---

## Design Pages

| # | Page | Folder | Description |
|---|------|--------|-------------|
| 1 | [Theme System](../pages/theme.md) | `ui.theme` | Color palette, typography, spacing, scheme state machine |
| 2 | [Shared Components](../pages/components.md) | `ui.components` | MediaCard, CoverImage, Badges, StarRating, AltTitleEditor, EmptyState, LibraryBottomSheet |
| 3 | [DexScreen](../pages/dex.md) | `ui.dex` | Main grid/list view with filter/sort, FAB, empty states |
| 4 | [UnifiedAddEditScreen](../pages/addedit.md) | `ui.addedit` | Add/edit form, URL scrape, validation, dialogs |
| 5 | [StatisticsScreen](../pages/statistics.md) | `ui.statistics` | Stats overview, category breakdown, rating details |
| 6 | [Navigation](../pages/navigation.md) | `ui.navigation` | Bottom nav shell, NavGraph, route flows |
| 7 | [Settings](../pages/settings.md) | `ui.settings` | Appearance, Data & Storage, About screens |

---

## App Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          LazyDex App                                     │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    MainShellScreen (Shell)                       │    │
│  │  ┌──────────────────────────────────────────────────────────┐   │    │
│  │  │  Bottom NavigationBar  [📖 Dex | 📊 Stats | ⚙️ Set.]    │   │    │
│  │  └──────────────────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│           │                    │                    │                    │
│           ▼                    ▼                    ▼                    │
│  ┌──────────────────┐ ┌──────────────┐ ┌──────────────────────┐        │
│  │    DexScreen     │ │ StatsScreen │ │  SettingsTabContent  │        │
│  │  ┌────────────┐  │ │ ┌─────────┐ │ │  ┌────────────────┐  │        │
│  │  │ MediaCard  │  │ │ │ Card 1  │ │ │  │ Appearance    │──┼────►    │
│  │  │ (grid/list)│  │ │ │ Card 2  │ │ │  │                │  │        │
│  │  │ EmptyState │  │ │ │ Card 3  │ │ │  │ Data&Storage  │──┼────►    │
│  │  │ BottomSheet│  │ │ └─────────┘ │ │  │                │  │        │
│  │  └────────────┘  │ └──────────────┘ │  │ About         │──┼────►    │
│  └────────┬─────────┘                  │  └────────────────┘  │        │
│           │                            └──────────────────────┘        │
│           ▼                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │              UnifiedAddEditScreen (itemId?)                       │  │
│  │  ┌───────────────────────────────────────────────────────────┐  │  │
│  │  │  Blurred Cover Header (250dp)                             │  │  │
│  │  │  URL Scrape Row  │  Form Fields  │  Chips  │  Save        │  │  │
│  │  │  Dialogs: Delete Confirm, Discard Warn                     │  │  │
│  │  └───────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  Settings Sub-screens                                           │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐   │    │
│  │  │ Appearance   │  │Data & Storage│  │  About             │   │    │
│  │  │ Theme picker │  │Export/Import │  │  Logo, version,    │   │    │
│  │  │ AMOLED       │  │Auto-backup   │  │  links, licenses   │   │    │
│  │  │ Cover theme  │  │Merge/Overwrite│  │                    │   │    │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Screen Relationship Map

```
                         ┌──────────────────────┐
                         │   MainShellScreen     │
                         │  (app entry point)    │
                         └──────────┬───────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
   ┌──────────────┐        ┌──────────────┐        ┌──────────────────┐
   │  DexScreen   │        │ Statistics   │        │  SettingsTab     │
   │  tab: 📖 Dex │        │ tab: 📊 Stats│        │  tab: ⚙️ Set.   │
   └───────┬──────┘        └──────────────┘        └────────┬─────────┘
           │                                                │
    ┌──────▼──────┐                                ┌───────┼───────────┐
    │ AddEdit     │                                │       │           │
    │ Screen      │                                ▼       ▼           ▼
    │ (itemId?)   │                          ┌────────┐ ┌──────┐ ┌────────┐
    └─────────────┘                          │Appear. │ │Data  │ │ About  │
                                             │Screen  │ │Screen│ │ Screen │
          ▲  (Shared Components)             └────────┘ └──────┘ └────────┘
          │
          ├── MediaCard           ◄── used by DexScreen
          ├── CoverImage          ◄── used by MediaCard, AddEditScreen
          ├── StatusBadge         ◄── used by MediaCard
          ├── CategoryBadge       ◄── used by MediaCard
          ├── StarRating          ◄── used by MediaCard, AddEditScreen
          ├── AltTitleEditor      ◄── used by AddEditScreen
          ├── EmptyState          ◄── used by DexScreen
          └── LibraryBottomSheet  ◄── used by DexScreen

          ▲  (Theme)
          │
          ├── Color.kt            ◄── used by ALL components
          ├── Theme.kt            ◄── wraps entire app
          └── Type.kt             ◄── typography definitions
```

---

## Navigation Route Table

```
    ┌──────────────────────┬─────────────────┬──────────────────────────┐
    │ Route                │ Screen          │ Parameters               │
    ├──────────────────────┼─────────────────┼──────────────────────────┤
    │ MainShellRoute       │ MainShellScreen │ (none)                   │
    │ AddEditRoute         │ UnifiedAddEdit  │ itemId: String? = null   │
    │ AppearanceRoute      │ AppearanceScreen│ (none)                   │
    │ DataAndStorageRoute  │ DataAndStorage  │ (none)                   │
    │ AboutRoute           │ AboutScreen     │ (none)                   │
    └──────────────────────┴─────────────────┴──────────────────────────┘
```

---

## Component → Screen Usage Matrix

| Component          | Dex | AddEdit | Stats | Settings | Appearance | Data | About |
|--------------------|:---:|:-------:|:-----:|:--------:|:----------:|:----:|:-----:|
| MediaCard          |  ✓  |         |       |          |            |      |       |
| CoverImage         |  ✓  |    ✓    |       |          |            |      |       |
| StatusBadge        |  ✓  |         |       |          |            |      |       |
| CategoryBadge      |  ✓  |         |       |          |            |      |       |
| StarRating         |  ✓  |    ✓    |       |          |            |      |       |
| AltTitleEditor     |     |    ✓    |       |          |            |      |       |
| EmptyState         |  ✓  |         |       |          |            |      |       |
| LibraryBottomSheet |  ✓  |         |       |          |            |      |       |
| StatCell           |     |         |   ✓   |          |            |      |       |
| Theme Presets      |     |         |       |          |     ✓      |      |       |
| AboutLinkItem      |     |         |       |          |            |      |   ✓   |
| SettingsCategoryItem|    |         |       |    ✓     |            |      |       |

---

## Data Flow Between Screens

```
    ┌─────────────┐         ┌──────────────────┐
    │  DexScreen  │         │ UnifiedAddEdit   │
    │             │  itemId │ Screen            │
    │  (read-only)│────────►│ (edit mode)       │
    │  viewModel  │         │ viewModel         │
    │  observes   │         │ upserts item      │
    │  all items  │◄────────│ on save           │
    └─────────────┘  pop    └──────────────────┘
         │                                   
         │ Flow<List<MediaItem>>              
         ▼                                   
    ┌─────────────┐         ┌──────────────────┐
    │ Statistics  │         │  SettingsScreen   │
    │ Screen      │         │                   │
    │ viewModel   │         │ viewModel         │
    │ computes    │         │ reads/writes      │
    │ aggregates  │         │ DataStore prefs   │
    └─────────────┘         └──────────────────┘
```

---

## Quick Links

| Go to | Description |
|-------|-------------|
| [📖 Theme →](../pages/theme.md) | Colors, typography, spacing design tokens |
| [🧩 Components →](../pages/components.md) | Reusable UI component specs |
| [📱 DexScreen →](../pages/dex.md) | Main library grid/list view |
| [✏️ AddEdit →](../pages/addedit.md) | Unified add/edit form |
| [📊 Statistics →](../pages/statistics.md) | Stats dashboard |
| [🧭 Navigation →](../pages/navigation.md) | App shell and routing |
| [⚙️ Settings →](../pages/settings.md) | Appearance, Data, About |
