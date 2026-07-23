# LazyDex Design System — Theme

> **Source:** `app.lazydex.ui.theme` — `Color.kt`, `Theme.kt`, `Type.kt`

---

## Color Palette (WTR-LAB Inspired)

### Dark Mode (Default)

```
    ┌──────────────────────┬─────────────┬──────────┐
    │ Token                │ Hex         │ Sample   │
    ├──────────────────────┼─────────────┼──────────┤
    │ Background           │ #1B1D23     │ ████████ │
    │ Surface              │ #1F2129     │ ████████ │
    │ Surface Variant      │ #23272C     │ ████████ │
    │ Toolbar              │ #212529     │ ████████ │
    │ Primary              │ #5795D9     │ ████████ │
    │ Primary Container    │ #1A3A5C     │ ████████ │
    │ Secondary            │ #444E5B     │ ████████ │
    │ Accent (Orange)      │ #FD7E14     │ ████████ │
    │ On Background        │ #A9A9A9     │ ████████ │
    │ On Surface           │ #CCCCCC     │ ████████ │
    │ Outline              │ #1AFFFFFF   │ ░░░░░░░░ │
    │ Input Border         │ #26FFFFFF   │ ░░░░░░░░ │
    └──────────────────────┴─────────────┴──────────┘
```

### Light Mode

```
    ┌──────────────────────┬─────────────┬──────────┐
    │ Token                │ Hex         │ Sample   │
    ├──────────────────────┼─────────────┼──────────┤
    │ Background           │ #F2F3F4     │ ████████ │
    │ Surface              │ #FFFFFF     │ ████████ │
    │ Primary              │ #4288C9     │ ████████ │
    │ On Background        │ #212529     │ ████████ │
    │ On Surface           │ #212529     │ ████████ │
    │ Outline              │ #D4DADB     │ ████████ │
    └──────────────────────┴─────────────┴──────────┘
```

### AMOLED Mode (Override)

When AMOLED is enabled + Dark mode:

```
    Background  →  #000000  (pure black)
    Surface     →  #000000  (pure black)
    Variant     →  #111111  (near-black for popups)
```

---

## Status Colors (Badge Fill)

```
    ┌──────────────────────┬─────────────┬──────────┬─────────────┐
    │ Status               │ Hex         │ Sample   │ Usage       │
    ├──────────────────────┼─────────────┼──────────┼─────────────┤
    │ In Progress (R/W/P)  │ #5795D9     │ ████████ │ Blue        │
    │ Completed            │ #22C55E     │ ████████ │ Green       │
    │ On Hold              │ #EAB308     │ ████████ │ Yellow      │
    │ Dropped              │ #EF4444     │ ████████ │ Red         │
    │ Plan to              │ #6B7280     │ ████████ │ Gray        │
    └──────────────────────┴─────────────┴──────────┴─────────────┘
```

## Category Colors (Badge Outline + Icon)

```
    ┌──────────────────────┬─────────────┬──────────┬─────────────┐
    │ Category             │ Hex         │ Sample   │ Icon        │
    ├──────────────────────┼─────────────┼──────────┼─────────────┤
    │ Novel                │ #5795D9     │ ████████ │ 📖 Book     │
    │ Manga                │ #22C55E     │ ████████ │ 📖 Book     │
    │ Anime                │ #EF4444     │ ████████ │ 📺 TV       │
    │ Game                 │ #A855F7     │ ████████ │ 🎮 Gamepad  │
    │ Movie                │ #F97316     │ ████████ │ 🎬 Movie    │
    │ TV                   │ #14B8A6     │ ████████ │ 📺 TV       │
    └──────────────────────┴─────────────┴──────────┴─────────────┘
```

## Rating Colors (Star Gradient Scale)

```
    ┌──────────┬─────────────┬──────────┐
    │ Rating   │ Hex         │ Sample   │
    ├──────────┼─────────────┼──────────┤
    │ 5.0 ★   │ #22C55E     │ ████████ │  ← Green (best)
    │ 4.0 ★   │ #84CC16     │ ████████ │
    │ 3.0 ★   │ #EAB308     │ ████████ │
    │ 2.0 ★   │ #F97316     │ ████████ │
    │ 1.0 ★   │ #EF4444     │ ████████ │  ← Red (worst)
    └──────────┴─────────────┴──────────┘
```

---

## Typography

| Role             | Font Family     | Weight     | Size  | Line Ht |
|------------------|-----------------|------------|-------|---------|
| `bodyLarge`      | Default (sans)  | Normal     | 16sp  | 24sp    |
| `titleLarge`     | Default (sans)  | SemiBold   | 22sp  | 28sp    |
| `labelSmall`     | Default (sans)  | Medium     | 11sp  | 16sp    |

Custom overrides at component level:

```
    MediaCard title:     12sp (grid) / 14sp (list), Bold
    Progress label:      10sp (grid) / 11sp (list), Medium
    Badge text:          10sp
    Section headers:     14sp, SemiBold, primary color
    Form labels:         12sp (OutlinedTextField label)
    Rating stars:        20dp icon, 12sp text
    Empty state:         15sp body
    Search hint:         12sp
    Save button:         14sp
```

---

## Spacing Scale

```
    ┌──────────┬────────┐
    │ Token    │ Value  │
    ├──────────┼────────┤
    │ xxs      │ 4dp    │
    │ xs       │ 8dp    │
    │ sm       │ 12dp   │
    │ md       │ 16dp   │
    │ lg       │ 24dp   │
    │ xl       │ 32dp   │
    └──────────┴────────┘
```

---

## Theme Switching State Machine

```
                      ┌──────────┐
           ┌─────────►│  System  │◄─────────┐
           │          │ (follows │          │
           │          │  device) │          │
           │          └────┬─────┘          │
           │               │                │
      ┌────┴────┐    ┌────▼────┐     ┌─────┴────┐
      │  Light  │    │  Dark*  │     │  AMOLED  │
      │ (white  │    │ (#1B1D  │     │ (#000000 │
      │  bg)    │    │  23 bg) │     │   bg)    │
      └─────────┘    └─────────┘     └──────────┘
                          │
                    ┌─────▼──────┐
                    │  Dynamic   │
                    │  Color     │
                    │  (Monet)   │
                    │  Android   │
                    │  12+ only  │
                    └────────────┘

    * = default mode
```

---

## Component Color Rules

| Component              | Rule                                      |
|------------------------|-------------------------------------------|
| StatusBadge            | Filled background @ 15% alpha of status color |
| CategoryBadge          | Outlined border + icon in category color   |
| StarRating             | Dynamic color from rating value scale      |
| Progress Bar           | Accent orange (#FD7E14)                    |
| MediaCard (list)       | Surface bg, 2dp elevation                  |
| MediaCard (grid)       | Surface bg, cover image + gradient overlay |
| FAB                    | Primary color, white icon                  |
| TopAppBar              | Background color (transparent on edit)     |
| Section Title          | Primary color, 14sp, SemiBold              |
| Filter Chip (selected) | Primary container bg + primary text        |

---

## Key UX Constants

| Rule                          | Value            |
|-------------------------------|------------------|
| Card corner radius            | 12dp             |
| Cover image corner radius     | 6dp              |
| Badge corner radius           | 4dp              |
| Edit screen cover height      | 250dp            |
| FAB size                      | default (56dp)   |
| Bottom nav height             | 80dp             |
| Max title lines (card)        | 2                |
| Min rating increment          | 0.5 ★           |
| Rating range                  | 1.0 – 5.0 ★     |
