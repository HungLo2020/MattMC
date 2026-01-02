# JEI-Like Item List Implementation

## Overview

This document describes the JEI (Just Enough Items) mod-like item list panel that has been added to the creative inventory screen.

## Features

### Visual Layout
- **Position**: Right side of the creative inventory screen
- **Panel**: Semi-transparent dark background with a border
- **Item Slots**: 18x18 pixels (standard Minecraft slot size)
- **Dynamic Columns/Rows**: Automatically adjusts based on screen resolution and GUI scale

### Item Display
- **All Items**: Displays all items from all creative mode tabs
- **Tab Order**: Items appear in the same order as their tabs (Building Blocks, Colored Blocks, Natural Blocks, etc.)
- **No Duplicates**: Items that appear in multiple tabs are only shown once (first occurrence)
- **Efficient Storage**: Uses `ItemStackLinkedSet` for O(n) duplicate detection

### User Interaction

#### Mouse Controls
1. **Clicking Items**
   - Left-click an item to pick it up
   - The item will be placed in your cursor with maximum stack size
   - Replaces any item currently held in cursor

2. **Scrolling**
   - Mouse wheel scrolls through the item list
   - Only works when hovering over the JEI panel
   - Scrollbar appears when there are more items than fit on screen

3. **Scrollbar Dragging**
   - Click and drag the scrollbar thumb to navigate quickly
   - Protected against division by zero errors

#### Tooltips
- Hover over items to see their tooltips
- Shows item name, properties, and which creative tabs contain them
- Same tooltip format as the main creative inventory

## Technical Implementation

### Key Classes and Methods

#### CreativeModeInventoryScreen.java

**New Fields:**
```java
private final List<ItemStack> allTabItems = Lists.newArrayList();
private float jeiScrollOffs = 0.0F;
private boolean jeiScrolling = false;
private int jeiColumns = 0;
private int jeiRows = 0;
private int jeiSlotSize = JEI_SLOT_SIZE;
private int jeiPanelX, jeiPanelY, jeiPanelWidth, jeiPanelHeight = 0;
```

**Key Methods:**
- `rebuildJeiItemList()`: Collects all unique items from creative tabs
- `calculateJeiPanelLayout()`: Calculates panel dimensions and slot layout
- `renderJeiPanel()`: Renders the item list panel and scrollbar
- `renderJeiTooltip()`: Shows tooltips when hovering over items
- `handleJeiPanelClick()`: Handles clicking on items or scrollbar

### Performance Optimizations

1. **Duplicate Detection**: Uses `ItemStackLinkedSet.createTypeAndComponentsSet()` for O(n) performance instead of O(n²)
2. **Lazy Rendering**: Only renders visible items (not the entire list)
3. **Efficient Scrolling**: Scroll calculations optimized to minimize redraws

### Safety Features

1. **Division by Zero Protection**: All division operations check for zero divisors
2. **Null Checks**: Panel only renders when data is valid
3. **Boundary Checks**: Mouse interactions verify coordinates are within panel bounds

## Layout Calculation

The panel layout is calculated dynamically:

```
Panel X = Main Inventory Right Edge + 4px margin
Panel Y = Main Inventory Top
Panel Width = (Columns × Slot Size) + 18px (for scrollbar)
Panel Height = Main Inventory Height

Columns = min(9, Available Width / Slot Size)
Rows = (Available Height - 20px) / Slot Size
```

## Integration with Main Creative Inventory

The JEI panel integrates seamlessly with the existing creative inventory:

- Does not interfere with tab switching
- Does not interfere with search functionality
- Does not count as "clicking outside" the inventory
- Shares the same item pickup behavior as creative slots
- Updates automatically when creative tabs are rebuilt

## Future Enhancements

Potential improvements that could be made:

1. **Search Integration**: Filter the JEI panel based on search box input
2. **Custom Sorting**: Allow users to sort by name, mod, or category
3. **Favorites**: Mark frequently used items as favorites
4. **GUI Scale Adaptation**: Further optimize slot size based on GUI scale setting
5. **Configuration**: Allow users to customize panel width, position, or visibility

## Testing

To test this feature:

1. Build the project: `./gradlew build`
2. Run the client: `./gradlew runClient`
3. Enter creative mode
4. Open the creative inventory (E key)
5. Look for the item list panel on the right side
6. Try scrolling and clicking items

## Known Limitations

- Panel width is fixed at up to 9 columns (like JEI)
- Currently only shows items from CATEGORY type tabs
- Does not filter based on search box (main inventory does)

## Performance Characteristics

- **Memory**: O(n) where n is the number of unique items across all tabs
- **Initial Build**: O(n) to collect and deduplicate items
- **Rendering**: O(visible items) - only renders what's on screen
- **Mouse Interaction**: O(1) for scrolling, O(visible items) for click detection
