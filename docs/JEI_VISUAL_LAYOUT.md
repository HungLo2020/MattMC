# JEI-Like Item List Visual Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Minecraft Creative Inventory                     │
├─────────────────────────────────┬───────────────────────────────────┤
│                                 │                                   │
│  ┌─────────────────────────┐   │  ┌───────────────────────────┐   │
│  │   Creative Inventory    │   │  │   JEI Item List Panel     │   │
│  │   (Main Area)           │   │  │   (New Feature)           │   │
│  │                         │   │  │                           │   │
│  │  [Tab1][Tab2][Tab3]...  │   │  │  [Item] [Item] [Item] ... │   │
│  │                         │   │  │  [Item] [Item] [Item] ... │   │
│  │  Title of Selected Tab  │   │  │  [Item] [Item] [Item] ... │   │
│  │                         │   │  │  [Item] [Item] [Item] ... │   │
│  │  ┌──────────────────┐   │   │  │  [Item] [Item] [Item] ... │   │
│  │  │ [🔍] Search Box  │   │   │  │  [Item] [Item] [Item] ... │   │
│  │  └──────────────────┘   │   │  │  [Item] [Item] [Item] ... │   │
│  │                         │   │  │                           │ ║ │
│  │  Item Grid (5x9):       │   │  │  (All items from all      │ ║ │
│  │  [Item] [Item] [Item]   │   │  │   creative tabs in        │ ║ │
│  │  [Item] [Item] [Item]   │   │  │   order, deduplicated)    │ ║ │
│  │  [Item] [Item] [Item]   │   │  │                           │ ║ │
│  │  [Item] [Item] [Item] ║ │   │  │  Scrollable list with     │ ║ │
│  │  [Item] [Item] [Item] ║ │   │  │  dynamic layout based on  │ ║ │
│  │                       ║ │   │  │  screen size & GUI scale  │ ║ │
│  │  Player Inventory:      │   │  │                           │ ║ │
│  │  [Hotbar Items 1-9]     │   │  │  Up to 9 columns wide     │ ║ │
│  │  [Inv] [Inv] [Inv]      │   │  │  Variable rows            │ ║ │
│  │  [Inv] [Inv] [Inv]      │   │  │                           │ ║ │
│  │  [Inv] [Inv] [Inv]      │   │  │                           │ ║ │
│  │                         │   │  │                           │ ║ │
│  └─────────────────────────┘   │  └───────────────────────────┘ ║ │
│                                 │                              ║ │
│  Standard Creative UI           │  New JEI-Like Panel         ║ │
│                                 │                          Scroll │
└─────────────────────────────────┴──────────────────────────────║─┘
                                                                 ║
                                                         Scrollbar
```

## Layout Details

### Main Creative Inventory (Left Side) - UNCHANGED
- Standard Minecraft creative inventory
- Tab buttons at top
- Search box (when in search tab)
- 5 rows × 9 columns of items from selected tab
- Standard scrollbar
- Player inventory at bottom

### JEI Item List Panel (Right Side) - NEW
- **Position**: Right side of main inventory, 4px gap
- **Background**: Semi-transparent dark (0xC0101010)
- **Border**: Light gray (0xFF8B8B8B)
- **Items**: All items from all category tabs
- **Deduplication**: Only shows first occurrence of each item
- **Layout**: 
  - Columns: Up to 9 (adjusts to screen width)
  - Rows: Adjusts to screen height
  - Slot Size: 18×18 pixels (standard Minecraft)
- **Scrollbar**: 
  - 12px wide
  - On right edge of panel
  - Only visible when needed
  - Draggable thumb

## Item Flow

```
Creative Tabs → JEI Item List
─────────────────────────────
Building Blocks → [Oak Log], [Oak Wood], [Oak Planks], ...
Colored Blocks → [White Wool], [Orange Wool], [Magenta Wool], ...
Natural Blocks → [Grass Block], [Dirt], [Stone], ...
Functional Blocks → [Crafting Table], [Furnace], ...
Redstone Blocks → [Redstone Dust], [Repeater], ...
Tools & Utilities → [Diamond Pickaxe], [Diamond Axe], ...
Combat → [Diamond Sword], [Diamond Helmet], ...
Food & Drinks → [Apple], [Bread], [Cooked Beef], ...
Ingredients → [Stick], [Coal], [Iron Ingot], ...
Spawn Eggs → [Pig Spawn Egg], [Cow Spawn Egg], ...

Duplicates are automatically removed!
(e.g., if Stick appears in both Tools and Ingredients,
 only the first occurrence is shown)
```

## User Interactions

### Mouse Actions

1. **Scrolling**
   ```
   Mouse Over Panel + Scroll Wheel
   ↓
   Panel scrolls up/down
   Items that were off-screen become visible
   ```

2. **Clicking Items**
   ```
   Click Item in JEI Panel
   ↓
   Item appears in cursor with max stack size
   Can then place in inventory or other slots
   ```

3. **Scrollbar Dragging**
   ```
   Click Scrollbar Thumb + Drag
   ↓
   Panel scrolls to corresponding position
   Quick navigation through many items
   ```

4. **Tooltips**
   ```
   Hover Over Item
   ↓
   Tooltip appears showing:
   - Item name
   - Item properties
   - Which creative tabs contain it
   ```

## Technical Architecture

```
CreativeModeInventoryScreen
│
├─ init()
│  └─ calculateJeiPanelLayout()
│     └─ Set jeiPanelX, jeiPanelY, jeiColumns, jeiRows
│
├─ tryRebuildTabContents()
│  └─ rebuildJeiItemList()
│     ├─ Iterate all CreativeModeTabs
│     ├─ Collect items from each tab
│     └─ Deduplicate using ItemStackLinkedSet
│
├─ render()
│  ├─ renderBg()
│  │  └─ renderJeiPanel()
│  │     ├─ Draw background & border
│  │     ├─ Calculate scroll position
│  │     ├─ Render visible items
│  │     └─ Draw scrollbar
│  └─ renderJeiTooltip()
│     └─ Show tooltip for hovered item
│
├─ mouseClicked()
│  └─ handleJeiPanelClick()
│     ├─ Check if scrollbar clicked
│     └─ Check if item clicked → pick up item
│
├─ mouseScrolled()
│  └─ If in JEI panel → scroll panel
│
└─ mouseDragged()
   └─ If scrolling JEI → drag scrollbar
```

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Build item list | O(n) | n = total items across all tabs |
| Duplicate detection | O(n) | Uses ItemStackLinkedSet hash set |
| Render frame | O(v) | v = visible items (typically 45-90) |
| Mouse click | O(v) | Check visible items only |
| Scroll wheel | O(1) | Just update offset |
| Scrollbar drag | O(1) | Just update offset |

## Memory Usage

- **Item List**: ~1-3 MB (depending on number of unique items)
- **Per Item**: ~100-200 bytes (ItemStack object)
- **Total**: Minimal impact on overall memory

## Example Item Counts

Typical Minecraft has:
- ~1,000-1,500 unique items (vanilla)
- ~2,000-5,000 items (with mods)
- JEI panel shows all of them in one scrollable list
