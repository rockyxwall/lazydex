# LazyDex Design — UnifiedAddEditScreen

> **Source:** `app.lazydex.ui.addedit` — `UnifiedAddEditScreen.kt`, `UnifiedAddEditViewModel.kt`

---

## Screen Layout

### Add Mode (New Item)

```
    ┌──────────────────────────────────────┐
    │  ←                 🗑️               │  ← Transparent TopAppBar
    │ ┌────────────────────────────────────┐│
    │ │     ┌──────────┐                  ││
    │ │     │          │  New Media        ││  ← Blurred cover header
    │ │     │  Cover   │  Unknown          ││     250dp tall
    │ │     │  90x125  │  Unknown          ││     with gradient fade
    │ │     │          │                   ││     to background
    │ │     └──────────┘                  ││
    │ └────────────────────────────────────┘│
    │                                       │
    │  ┌──────────────────────────┐ ┌────┐  │
    │  │ Import URL (Auto-fill)   │ │Fetch│  │  ← URL scrape row
    │  └──────────────────────────┘ └────┘  │
    │                                       │
    │  ┌──────────────────────────────┐     │
    │  │ Cover Image URL              │     │
    │  └──────────────────────────────┘     │
    │                                       │
    │  ┌──────────────────────────────┐     │
    │  │ Title *                      │     │  ← Required field
    │  └──────────────────────────────┘     │
    │                                       │
    │  Alternative Titles                   │
    │  ┌──────────────────┐  ↕️  ✖️          │
    │  │ Alt Title 1      │                │
    │  └──────────────────┘                │
    │  ┌──────────────────────────────────┐│
    │  │ ＋ Add Alternative Title         ││
    │  └──────────────────────────────────┘│
    │                                       │
    │  Category                             │
    │  [Novel] [Manga] [Anime]              │
    │  [Game] [Movie] [TV]                  │  ← FilterChips
    │                                       │
    │  Status                               │
    │  [Reading] [Completed] [On Hold]      │
    │  [Dropped] [Plan to]                  │  ← adaptive to category
    │                                       │
    │  ┌────────────┐ ┌────────────┐        │
    │  │ Current    │ │ Total      │        │  ← Progress fields
    │  │ Progress   │ │ Items      │        │
    │  └────────────┘ └────────────┘        │
    │                                       │
    │  My Rating                            │
    │  ★★★☆☆  3.0/5.0                      │  ← Editable StarRating
    │                                       │
    │  ┌──────────────────────────────┐     │
    │  │ Notes                        │     │
    │  │                              │     │
    │  │                              │     │  ← 3 min lines
    │  └──────────────────────────────┘     │
    │                                       │
    │  ┌──────────────────────────────┐     │
    │  │        Save Tracker          │     │  ← Full-width Button
    │  └──────────────────────────────┘     │
    │                                       │
    └──────────────────────────────────────┘
```

### Edit Mode (Existing Item)

Same as Add mode, with these additions:

- No URL scrape row (visible only in add mode)
- `Source URL` field at bottom + `🔗 Open in browser` button
- Delete icon (🗑️) visible in TopAppBar
- Header shows saved cover + title + category + status
- Dominant color extraction from cover for theme accent

```
    ┌──────────────────────────────────────┐
    │  ←                    🗑️            │
    │ ┌────────────────────────────────────┐│
    │ │     ┌──────────┐                  ││
    │ │     │          │  One Piece        ││
    │ │     │  Cover   │  Manga            ││  ← extracted color
    │ │     │  90x125  │  Reading          ││     used for chip tint
    │ │     │          │                   ││
    │ │     └──────────┘                  ││
    │ └────────────────────────────────────┘│
    │                                       │
    │  ... (same form fields as above) ...  │
    │                                       │
    │  Source URL                           │
    │  ┌──────────────────────────────┐  🔗 │  ← + Open in browser
    │  │ https://anilist.co/anime/... │     │
    │  └──────────────────────────────┘     │
    │                                       │
    │  ┌──────────────────────────────┐     │
    │  │        Save Tracker          │     │
    │  └──────────────────────────────┘     │
    └──────────────────────────────────────┘
```

---

## Dialogs

### Delete Confirmation

```
    ┌──────────────────────────────────┐
    │  Delete Tracker                   │
    │                                  │
    │  Are you sure you want to delete │
    │  this media tracker? This will   │
    │  remove all progress history and │
    │  local cover art.                │
    │                                  │
    │       [Cancel]    [Delete]        │  ← Delete is red
    └──────────────────────────────────┘
```

### Discard Warning

```
    ┌──────────────────────────────────┐
    │  Discard Changes                  │
    │                                  │
    │  You have unsaved changes. Are   │
    │  you sure you want to discard    │
    │  them and go back?               │
    │                                  │
    │   [Keep Editing]   [Discard]      │  ← Discard is red
    └──────────────────────────────────┘
```

---

## State Machine

```
    ┌──────────┐     ┌────────────┐     ┌──────────────┐
    │  IDLE    │────►│ SCRAPING   │────►│ AUTO-FILLED  │
    │  (new)   │     │ (spinner)  │     │ (fields pop) │
    └──────────┘     └────────────┘     └──────┬───────┘
         │                                     │
         │  (manual edit)                      │
         ▼                                     ▼
    ┌──────────┐     ┌────────────┐     ┌──────────────┐
    │  DIRTY   │────►│ VALIDATING │────►│   SAVING     │
    │ (unsaved)│     │ (checks)   │     │  (spinner)   │
    └──────────┘     └────────────┘     └──────┬───────┘
                                               │
                                               ▼
                                          ┌──────────┐
                                          │   DONE   │────► popBack()
                                          │  (saved) │
                                          └──────────┘
```

---

## Validation Rules

| Field            | Rule                                    | UX                           |
|------------------|-----------------------------------------|------------------------------|
| Title            | Required (non-blank)                    | Red error text "Title is required" |
| Progress         | ≥ 0, ≤ total (if total set)             | Red error text + disabled Save |
| Total            | ≥ 0 or null (for ongoing)               | Placeholder "Ongoing"         |
| Source URL       | Valid URL format (if provided)          | `isUrlInvalid` flag          |
| Cover Image URL  | Optional (auto-downloaded on save)      | No error state                |

---

## Dynamic Cover Theming

When a cover image exists:

```
    Cover Image
         │
         ▼
    extractAverageColor()
         │
         ▼
    dominantColor → used for:
         │
         ├─ FilterChip selected bg (25% alpha)
         ├─ FilterChip selected label color
         ├─ Save button container color
         ├─ Fallback gradient tint
         └─ Status text color
```

## Color Accent Adaptation

```
    With Cover:                    Without Cover:
    ┌────────────────────┐        ┌────────────────────┐
    │ [Novel]  [Manga]   │        │ [Novel]  [Manga]   │
    │  ▲ tinted to       │        │  default M3 colors │
    │  dominant color    │        │                    │
    └────────────────────┘        └────────────────────┘
    ┌────────────────────┐        ┌────────────────────┐
    │   Save Tracker     │        │   Save Tracker     │
    │  ▲ colored to      │        │  default primary   │
    │  dominant color    │        │                    │
    └────────────────────┘        └────────────────────┘
```
