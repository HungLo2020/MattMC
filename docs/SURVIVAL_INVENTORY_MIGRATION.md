# Major Architecture Change: JEI Panel Moved to Survival Inventory

## Summary

Based on user feedback (comment #3706388354), the JEI-like item browser panel has been moved from the creative inventory to the survival inventory, and the game now uses the survival inventory even in creative mode.

## Changes Made

### 1. InventoryScreen.java (Survival Inventory)

**Removed Creative Mode Checks:**
```java
// BEFORE: Would switch to CreativeModeInventoryScreen
@Override
public void containerTick() {
    super.containerTick();
    if (this.minecraft.player.hasInfiniteMaterials()) {
        this.minecraft.setScreen(new CreativeModeInventoryScreen(...));
    }
}

// AFTER: Stays in survival inventory
@Override
public void containerTick() {
    super.containerTick();
    // No longer switch to creative inventory
}
```

**Added Complete JEI Implementation:**

**Imports Added:**
- `com.google.common.collect.Lists`
- `java.util.List`
- `java.util.Set`
- `net.minecraft.resources.ResourceLocation`
- `net.minecraft.util.Mth`
- `net.minecraft.world.item.CreativeModeTab`
- `net.minecraft.world.item.CreativeModeTabs`
- `net.minecraft.world.item.ItemStack`
- `net.minecraft.world.item.ItemStackLinkedSet`

**Constants Added:**
```java
private static final ResourceLocation SCROLLER_SPRITE = 
    ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
private static final int JEI_SLOT_SIZE = 18;
```

**Fields Added:**
```java
// JEI-like item list fields
private final List<ItemStack> allTabItems = Lists.newArrayList();
private float jeiScrollOffs = 0.0F;
private boolean jeiScrolling = false;
private int jeiColumns = 0;
private int jeiRows = 0;
private int jeiSlotSize = JEI_SLOT_SIZE;
private int jeiPanelX = 0;
private int jeiPanelY = 0;
private int jeiPanelWidth = 0;
private int jeiPanelHeight = 0;
```

**Methods Added:**
1. `rebuildJeiItemList()` - Collects all unique items from creative mode tabs
2. `calculateJeiPanelLayout()` - Calculates panel position and size based on screen dimensions
3. `renderJeiPanel()` - Renders the item grid and scrollbar
4. `renderJeiTooltip()` - Shows tooltips when hovering over items
5. `handleJeiPanelClick()` - Handles clicking on items and scrollbar
6. `resize()` - **NEW** - Recalculates panel layout on screen resize (fixes GUI scale bug)

**Methods Modified:**
1. `init()` - Removed creative mode check, added `calculateJeiPanelLayout()` call
2. `render()` - Added `renderJeiTooltip()` call
3. `renderBg()` - Added `renderJeiPanel()` call
4. `mouseClicked()` - Added JEI panel click handling
5. `mouseScrolled()` - Added JEI panel scroll handling
6. `mouseDragged()` - Added JEI scrollbar dragging
7. `mouseReleased()` - Added JEI scroll state reset
8. Constructor - Added `rebuildJeiItemList()` call

### 2. CreativeModeInventoryScreen.java

**Status: DEAD CODE**
- No changes made to this file
- It is no longer accessed because the creative mode checks were removed from InventoryScreen
- Kept in codebase as requested by user

## Behavior Changes

### Before:
1. Player in survival mode → `InventoryScreen` (no JEI panel)
2. Player in creative mode → `CreativeModeInventoryScreen` (with JEI panel)
3. Changing GUI scale while in creative → panel disappeared until world reload

### After:
1. Player in survival mode → `InventoryScreen` (with JEI panel)
2. Player in creative mode → `InventoryScreen` (with JEI panel)  
3. Changing GUI scale → panel properly recalculates and persists ✅

## Bug Fixes

### GUI Scale Change Bug
**Problem:** When changing GUI scale in-game, the JEI panel would disappear until world reload.

**Root Cause:** The `resize()` method was not overridden in InventoryScreen to recalculate the JEI panel layout.

**Solution:** Added `resize()` method override:
```java
@Override
public void resize(Minecraft minecraft, int i, int j) {
    super.resize(minecraft, i, j);
    // Recalculate JEI panel layout when screen is resized (including GUI scale changes)
    this.calculateJeiPanelLayout();
}
```

## Technical Details

### Panel Layout Algorithm
The panel layout is calculated dynamically in `calculateJeiPanelLayout()`:

1. **Position:** Right side of inventory with 16px gap
2. **Width:** Screen width - panel X - 8px right margin - 14px scrollbar
3. **Height:** Full available screen height (top margin to bottom margin)
4. **Columns:** Available width / 18px (slot size), max 9
5. **Rows:** Available height / 18px (slot size)

### Item Collection
Items are collected in `rebuildJeiItemList()`:

1. Iterate through all `CreativeModeTabs.allTabs()`
2. Skip non-CATEGORY tabs (search, inventory, hotbar)
3. Use `ItemStackLinkedSet` for O(n) duplicate detection
4. Store unique items in `allTabItems` list

### Rendering
The panel is rendered in `renderJeiPanel()`:

1. Draw semi-transparent background (0xC0101010)
2. Draw border (0xFF8B8B8B)
3. Calculate visible items based on scroll offset
4. Render each visible item in a 16x16 slot
5. Render scrollbar if needed (when items exceed visible area)

### Mouse Interaction
- **Click item:** Picks up item with max stack size (creative mode behavior)
- **Mouse wheel:** Scrolls through items
- **Drag scrollbar:** Jumps to position in list
- **Click outside:** Drops carried item (standard behavior)

## Testing Recommendations

1. **Survival Mode:**
   - Open inventory (E key)
   - Verify JEI panel appears on right
   - Verify items can be browsed and picked up

2. **Creative Mode:**
   - Switch to creative mode (`/gamemode creative`)
   - Open inventory (E key)
   - Verify survival inventory appears (NOT creative tabs)
   - Verify JEI panel works
   - Verify item pickup with max stack size

3. **GUI Scale Changes:**
   - Open inventory
   - Go to Options → Video Settings → GUI Scale
   - Change scale
   - Return to game
   - Verify JEI panel is still visible and properly sized
   - Verify no world reload needed

4. **Different Resolutions:**
   - Test windowed mode at various sizes
   - Test fullscreen
   - Verify panel scales appropriately

## File Changes Summary

| File | Lines Added | Lines Removed | Status |
|------|-------------|---------------|--------|
| `InventoryScreen.java` | 325 | 13 | Modified |
| `CreativeModeInventoryScreen.java` | 0 | 0 | Unchanged (Dead Code) |

## Migration Notes

This is a **BREAKING CHANGE** to the game's inventory system architecture:
- Creative mode no longer has its own separate inventory screen
- All inventory interaction happens through survival inventory
- JEI panel is now a universal feature available in all game modes
- Custom code that relied on `CreativeModeInventoryScreen` being shown in creative mode will need updates

## Advantages of New Architecture

1. **Consistency:** Same inventory UI in all game modes
2. **Simplicity:** One inventory screen instead of two
3. **Maintainability:** Changes only need to be made in one place
4. **Feature Parity:** JEI panel available to all players regardless of mode
5. **Bug Prevention:** No more mode-switching edge cases
