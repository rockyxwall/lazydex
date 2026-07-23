# LazyDex Design — Shared Components

> **Source:** `app.lazydex.ui.components`

---

## 1. MediaCard

Two layout variants controlled by `isGridView` flag.

### Grid Variant (180dp tall)

```
    ┌──────────────────────┐
    │  ┌────┐              │
    │  │ IP │  ← StatusBadge│
    │  └────┘              │
    │                      │
    │      Cover Image     │
    │      (fill card)     │
    │                      │
    │ ┌────────────────────┐│
    │ │ One Piece          ││  ← Title (Bold, 12sp, 2 lines max)
    │ │ Ch. 1054 / 1100 ─★││  ← Progress + Rating
    │ └────────────────────┘│
    │  ▲ Gradient overlay   │
    └──────────────────────┘
```

**States:**

```
    ┌─ Loaded ──────────────────┐    ┌─ No Cover ────────────────┐
    │  ┌────┐            ┌───┐  │    │  ┌────┐            ┌───┐  │
    │  │ IP │  cover art │★4 │  │    │  │ IP │  initials   │★4 │  │
    │  └────┘            └───┘  │    │  └────┘  fallback   └───┘  │
    │         OP                 │    │         OP                 │
    └────────────────────────────┘    └────────────────────────────┘
```

### List Variant (95dp tall)

```
    ┌──────────────────────────────────────────┐
    │ ┌──────────┐  One Piece            2h ago│
    │ │          │  ★★★★☆  4.0/5.0            │
    │ │  Cover   │                             │
    │ │  70x95   │  ┌───────┐ ┌────┐           │
    │ │          │  │ 📖 M  │ │ IP │           │
    │ └──────────┘  └───────┘ └────┘           │
    │              Ch. 1054 / 1100              │
    └──────────────────────────────────────────┘
```

**States:**

```
    ┌─ Rated ─────────────────────┐    ┌─ Unrated ─────────────────┐
    │  OP                Just now │    │  CSM                 1d ago│
    │  ★★★★☆  4.0/5.0           │    │  (no stars shown)         │
    │  Ch. 10 / ?      📖 M │ IP │    │  Ep. 6 / 24    📺 A │ IP │
    └──────────────────────────────┘    └────────────────────────────┘
```

---

## 2. CoverImage

```
    ┌─ Valid Cover ──────────────┐    ┌─ Fallback (Initials) ─────┐
    │  ┌──────────────────────┐  │    │  ┌──────────────────────┐  │
    │  │                      │  │    │  │                      │  │
    │  │    cover art via     │  │    │  │       ┌────┐         │  │
    │  │    Coil AsyncImage   │  │    │  │       │ OP │         │  │
    │  │                      │  │    │  │       └────┘         │  │
    │  │                      │  │    │  │        initials      │  │
    │  └──────────────────────┘  │    │  └──────────────────────┘  │
    │  6dp radius, 2C3E50→000   │    │  6dp radius, 2C3E50→000    │
    └────────────────────────────┘    └────────────────────────────┘
```

**Behavior:** Loads from local file via Coil. On error → initials fallback.
Gradient background: `#2C3E50 → #000000` linear.

---

## 3. StatusBadge

```
    ┌──────────────┬──────────────┬──────────────┐
    │ ┌──────────┐ │ ┌──────────┐ │ ┌──────────┐ │
    │ │ Reading  │ │ │ Watching │ │ │ Playing  │ │
    │ └──────────┘ │ └──────────┘ │ └──────────┘ │
    │   bg:blue    │   bg:blue    │   bg:blue    │
    ├──────────────┼──────────────┼──────────────┤
    │ ┌──────────┐ │ ┌──────────┐ │ ┌──────────┐ │
    │ │Completed │ │ │ On Hold  │ │ │ Dropped  │ │
    │ └──────────┘ │ └──────────┘ │ └──────────┘ │
    │  bg:green   │   bg:yellow  │   bg:red     │
    ├──────────────┼──────────────┼──────────────┤
    │ ┌──────────┐ │              │              │
    │ │ Plan to  │ │              │              │
    │ └──────────┘ │              │              │
    │   bg:gray    │              │              │
    └──────────────┴──────────────┴──────────────┘
```

All badges: 15% alpha bg fill, 4dp radius, 6px h + 2px v padding, 10sp text.

---

## 4. CategoryBadge

```
    ┌────────────┬────────────┬────────────┐
    │ ┌────────┐ │ ┌────────┐ │ ┌────────┐ │
    │ │ 📖 Nvl │ │ │ 📖 Mng │ │ │ 📺 Anm │ │
    │ └────────┘ │ └────────┘ │ └────────┘ │
    │  blue      │  green     │  red       │
    ├────────────┼────────────┼────────────┤
    │ ┌────────┐ │ ┌────────┐ │ ┌────────┐ │
    │ │ 🎮 Gme │ │ │ 🎬 Mov │ │ │ 📺 TV  │ │
    │ └────────┘ │ └────────┘ │ └────────┘ │
    │  purple    │  orange    │  teal      │
    └────────────┴────────────┴────────────┘
```

All badges: 1dp border (50% alpha), 4dp radius, 6px h + 2px v padding, 10sp.

---

## 5. StarRating

```
    ┌─ Display (read-only) ──────────────────┐
    │  ★★★★★  4.0/5.0                        │
    │  ☆ = empty  ★ = full  ◐ = half        │
    └─────────────────────────────────────────┘

    ┌─ Editable ─────────────────────────────┐
    │  ★★★☆☆  (tap to rate, half-star)       │
    │  ◐◐◐☆☆  → tap left half = half star    │
    │  ★★☆☆☆  → tap right half = full star   │
    └─────────────────────────────────────────┘

    ┌─ Unrated (editable) ───────────────────┐
    │  ☆☆☆☆☆  Unrated                       │
    └─────────────────────────────────────────┘

    20dp icons. Color changes by score:
    1.0★=red  2.0★=orange  3.0★=yellow  4.0★=green  5.0★=green
```

**Interaction (editable mode):** Tap gesture detects which star and whether left/right half.

---

## 6. AltTitleEditor

```
    ┌──────────────────────────────────────────┐
    │  Alternative Titles                      │
    │                                          │
    │  ┌──────────────────┐  ↕️  ✖️            │
    │  │ Alt Title 1      │  swap  remove      │
    │  └──────────────────┘                    │
    │  ┌──────────────────┐  ↕️  ✖️            │
    │  │ Alt Title 2      │  swap  remove      │
    │  └──────────────────┘                    │
    │                                          │
    │  ┌──────────────────────────────────────┐│
    │  │  ＋ Add Alternative Title             ││
    │  └──────────────────────────────────────┘│
    └──────────────────────────────────────────┘
```

**Swap button** (↕️): swaps alt title with main title field.
**Remove button** (✖️): deletes that alt title row.
**Add button**: appends a new empty alt title field.

---

## 7. EmptyState

```
    ┌──────────────────────────────────────┐
    │                                      │
    │        Nothing here yet.             │
    │    Tap [+] to add your first         │
    │       tracking item.                 │
    │                                      │
    │        ┌──────────────────┐          │
    │        │  Clear Filters   │          │
    │        └──────────────────┘          │
    │          (shown when filtered)        │
    └──────────────────────────────────────┘
```

Two modes:
- **Empty library:** "Nothing here yet. Tap [+] to add your first tracking item."
- **Filtered empty:** "No items match your filters" + "Clear Filters" button.

15sp body text, centered, 60% alpha on `onBackground`.

---

## 8. LibraryBottomSheet

```
    ┌──────────────────────────────────────────┐  ← drag handle
    │  ┌────────┬────────┬────────┐            │
    │  │ Filter │  Sort  │ Display│            │  ← TabRow
    │  └────────┴────────┴────────┘            │
    │                                          │
    │  ┌─ Filter Tab ───────────────────────┐  │
    │  │ Category                           │  │
    │  │ [All] [Novels] [Manga] [Anime] ... │  │
    │  │ Status                             │  │
    │  │ [All] [Reading] [Completed] ...    │  │
    │  │ ┌────────────────────────────────┐ │  │
    │  │ │        Clear Filters          │ │  │
    │  │ └────────────────────────────────┘ │  │
    │  └────────────────────────────────────┘  │
    │                                          │
    │  ┌─ Sort Tab ─────────────────────────┐  │
    │  │ ○ Title                            │  │
    │  │ ● Last Active                      │  │
    │  │ ○ Progress                         │  │
    │  │ ○ Date Added                       │  │
    │  │ Direction                          │  │
    │  │ [Ascending ↑] [Descending ↓]      │  │
    │  └────────────────────────────────────┘  │
    │                                          │
    │  ┌─ Display Tab ──────────────────────┐  │
    │  │ Layout Mode                        │  │
    │  │ [● Grid] [○ List]                  │  │
    │  └────────────────────────────────────┘  │
    └──────────────────────────────────────────┘
```

ModalBottomSheet with 3 tabs. Uses `rememberModalBottomSheetState()`.
Transition between tabs is instant (no animation).
