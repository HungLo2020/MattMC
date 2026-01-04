# JEI-Style Recipe Viewer Implementation Plan

## Overview

This document outlines the comprehensive implementation plan for adding a JEI-style recipe viewer that replaces the vanilla recipe book. The system will allow pressing 'R' on any item to view its recipes, with ESC closing the recipe viewer before closing the inventory.

## Core Requirements

1. **Remove Recipe Book Button** - Eliminate the vanilla recipe book button from all inventory screens
2. **Dynamic Recipe Discovery** - Find all recipes that produce a given item using the recipe JSON system
3. **Authentic GUI Rendering** - Display recipes using actual Minecraft GUI textures for each recipe type
4. **Multi-Recipe Support** - Show tabs for items that can be crafted multiple ways (e.g., steak in furnace/smoker)
5. **'R' Key Binding** - Allow recipe viewing from any inventory context (player inventory, containers, JEI panel)
6. **Smart Input Handling** - ESC key closes recipe viewer first, then inventory screen on second press

## Recipe Types to Support

Based on Minecraft's recipe system, the following recipe types will be supported:

### Recipe Types with GUIs (Will be displayed)
1. **Crafting** (`RecipeType.CRAFTING`)
   - Uses crafting table GUI texture
   - Shows 3x3 grid with recipe pattern
   - Supports shaped and shapeless recipes
   - Texture: `textures/gui/container/crafting_table.png`

2. **Smelting** (`RecipeType.SMELTING`)
   - Uses furnace GUI texture
   - Shows input slot, fuel slot, output slot
   - Texture: `textures/gui/container/furnace.png`

3. **Blasting** (`RecipeType.BLASTING`)
   - Uses blast furnace GUI texture
   - Shows input slot, fuel slot, output slot
   - Texture: `textures/gui/container/blast_furnace.png`

4. **Smoking** (`RecipeType.SMOKING`)
   - Uses smoker GUI texture
   - Shows input slot, fuel slot, output slot
   - Texture: `textures/gui/container/smoker.png`

5. **Stonecutting** (`RecipeType.STONECUTTING`)
   - Uses stonecutter GUI texture
   - Shows single input and output
   - Texture: `textures/gui/container/stonecutter.png`

6. **Smithing** (`RecipeType.SMITHING`)
   - Uses smithing table GUI texture
   - Shows template, base, addition, and output slots
   - Texture: `textures/gui/container/smithing.png`

### Recipe Types without GUIs (Will be excluded)
- **Campfire Cooking** (`RecipeType.CAMPFIRE_COOKING`) - No dedicated GUI in vanilla
- **Special Recipes** - Custom recipes like armor dyeing, banner duplication, etc.

## Implementation Phases

### Phase 1: Remove Recipe Book Button

**Objective:** Disable the vanilla recipe book button from all inventory screens.

**Files to Modify:**
- `src/main/java/net/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen.java`

**Implementation:**
1. Comment out or remove the `initButton()` method call in `init()`
2. Alternatively, add a flag to disable recipe book button rendering
3. Ensure the layout still works correctly without the button

**Affected Screens:**
- InventoryScreen
- CraftingScreen  
- FurnaceScreen
- BlastFurnaceScreen
- SmokerScreen

**Testing:**
- Open each inventory screen and verify button is not present
- Verify layout still functions correctly
- Check that JEI panel still renders properly

---

### Phase 2: Recipe Lookup System

**Objective:** Create a system to dynamically find all recipes that produce a given item.

**New File:**
- `src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java`

**Implementation:**

```java
public class RecipeLookupHelper {
    // Cache for performance - maps Item to list of recipes
    private static final Map<Item, List<RecipeHolder<?>>> recipeCache = new HashMap<>();
    private static boolean cacheDirty = true;
    
    /**
     * Find all recipes that produce the given item.
     * Returns a map of RecipeType to list of recipes.
     */
    public static Map<RecipeType<?>, List<RecipeHolder<?>>> findRecipesFor(Item item, Level level) {
        if (cacheDirty) {
            rebuildCache(level);
            cacheDirty = false;
        }
        
        List<RecipeHolder<?>> allRecipes = recipeCache.getOrDefault(item, List.of());
        Map<RecipeType<?>, List<RecipeHolder<?>>> byType = new HashMap<>();
        
        for (RecipeHolder<?> recipe : allRecipes) {
            RecipeType<?> type = recipe.value().getType();
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(recipe);
        }
        
        return byType;
    }
    
    /**
     * Rebuild the recipe cache from the recipe manager.
     */
    private static void rebuildCache(Level level) {
        recipeCache.clear();
        RecipeManager recipeManager = level.getRecipeManager();
        
        // Query all recipe types
        for (RecipeType<?> type : getRecipeTypesToQuery()) {
            Collection<RecipeHolder<?>> recipes = recipeManager.getAllRecipesFor(type);
            
            for (RecipeHolder<?> recipe : recipes) {
                ItemStack result = getRecipeResult(recipe.value());
                if (!result.isEmpty()) {
                    Item item = result.getItem();
                    recipeCache.computeIfAbsent(item, k -> new ArrayList<>()).add(recipe);
                }
            }
        }
    }
    
    /**
     * Get the list of recipe types to query (excludes campfire cooking).
     */
    private static List<RecipeType<?>> getRecipeTypesToQuery() {
        return List.of(
            RecipeType.CRAFTING,
            RecipeType.SMELTING,
            RecipeType.BLASTING,
            RecipeType.SMOKING,
            RecipeType.STONECUTTING,
            RecipeType.SMITHING
        );
    }
    
    /**
     * Extract the result ItemStack from a recipe.
     */
    private static ItemStack getRecipeResult(Recipe<?> recipe) {
        // Handle different recipe types to get the output
        // This may require type-specific logic
        return recipe.getResultItem(/* registry access */);
    }
    
    /**
     * Mark cache as dirty when recipes are updated.
     */
    public static void invalidateCache() {
        cacheDirty = true;
    }
}
```

**Key Features:**
- Caches recipes for performance
- Filters by output item
- Groups recipes by type
- Invalidates cache when recipes reload

**Integration Points:**
- Hook into recipe reload to invalidate cache
- Call from recipe viewer to get recipes
- Handle empty results gracefully

---

### Phase 3: Recipe Viewer GUI Component

**Objective:** Create an overlay screen that displays recipes with authentic GUI textures.

**New Files:**
- `src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java`
- `src/main/java/net/minecraft/client/gui/components/recipes/RecipeRenderer.java`
- `src/main/java/net/minecraft/client/gui/components/recipes/CraftingRecipeRenderer.java`
- `src/main/java/net/minecraft/client/gui/components/recipes/FurnaceRecipeRenderer.java`
- `src/main/java/net/minecraft/client/gui/components/recipes/SmithingRecipeRenderer.java`
- `src/main/java/net/minecraft/client/gui/components/recipes/StonecutterRecipeRenderer.java`

**RecipeViewerScreen Design:**

```java
public class RecipeViewerScreen extends Screen {
    private final Screen parentScreen;
    private final ItemStack targetItem;
    private final Map<RecipeType<?>, List<RecipeHolder<?>>> recipesByType;
    
    // Tab management
    private List<RecipeType<?>> availableTabs;
    private int currentTabIndex = 0;
    
    // Recipe navigation (for multiple recipes of same type)
    private int currentRecipeIndex = 0;
    
    // Layout
    private int centerX;
    private int centerY;
    private int guiWidth;
    private int guiHeight;
    
    public RecipeViewerScreen(Screen parentScreen, ItemStack targetItem, 
                              Map<RecipeType<?>, List<RecipeHolder<?>>> recipes) {
        super(Component.literal("Recipe Viewer"));
        this.parentScreen = parentScreen;
        this.targetItem = targetItem;
        this.recipesByType = recipes;
        this.availableTabs = new ArrayList<>(recipes.keySet());
    }
    
    @Override
    protected void init() {
        // Calculate centered position
        this.guiWidth = 176; // Standard GUI width
        this.guiHeight = 166; // Standard GUI height
        this.centerX = (this.width - this.guiWidth) / 2;
        this.centerY = (this.height - this.guiHeight) / 2;
        
        // Add tab buttons if multiple recipe types
        if (availableTabs.size() > 1) {
            addTabButtons();
        }
        
        // Add navigation buttons if multiple recipes of current type
        if (getCurrentRecipes().size() > 1) {
            addNavigationButtons();
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Render semi-transparent background
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
        
        // Get current recipe and render it
        RecipeType<?> currentType = availableTabs.get(currentTabIndex);
        List<RecipeHolder<?>> recipes = getCurrentRecipes();
        RecipeHolder<?> recipe = recipes.get(currentRecipeIndex);
        
        // Get appropriate renderer and render recipe
        RecipeRenderer renderer = getRendererForType(currentType);
        renderer.render(guiGraphics, centerX, centerY, recipe, mouseX, mouseY);
        
        // Render tabs
        if (availableTabs.size() > 1) {
            renderTabs(guiGraphics, mouseX, mouseY);
        }
        
        // Render navigation arrows
        if (getCurrentRecipes().size() > 1) {
            renderNavigation(guiGraphics, mouseX, mouseY);
        }
        
        // Render tooltips
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // ESC closes the recipe viewer, not the parent screen
        if (keyEvent.key() == 256) { // ESC
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        
        // Arrow keys for navigation
        if (keyEvent.key() == 262) { // Right arrow
            nextRecipe();
            return true;
        }
        if (keyEvent.key() == 263) { // Left arrow
            previousRecipe();
            return true;
        }
        
        return super.keyPressed(keyEvent);
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        // Click outside recipe viewer closes it
        if (!isMouseOverRecipeArea(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, bl);
    }
    
    private RecipeRenderer getRendererForType(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) {
            return new CraftingRecipeRenderer();
        } else if (type == RecipeType.SMELTING) {
            return new FurnaceRecipeRenderer("textures/gui/container/furnace.png");
        } else if (type == RecipeType.BLASTING) {
            return new FurnaceRecipeRenderer("textures/gui/container/blast_furnace.png");
        } else if (type == RecipeType.SMOKING) {
            return new FurnaceRecipeRenderer("textures/gui/container/smoker.png");
        } else if (type == RecipeType.STONECUTTING) {
            return new StonecutterRecipeRenderer();
        } else if (type == RecipeType.SMITHING) {
            return new SmithingRecipeRenderer();
        }
        // Default fallback
        return new CraftingRecipeRenderer();
    }
    
    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Render tabs at top of screen
        int tabX = centerX;
        int tabY = centerY - 28;
        int tabWidth = 28;
        int tabHeight = 28;
        
        for (int i = 0; i < availableTabs.size(); i++) {
            RecipeType<?> type = availableTabs.get(i);
            boolean selected = (i == currentTabIndex);
            
            // Draw tab background
            int color = selected ? 0xFFCCCCCC : 0xFF888888;
            guiGraphics.fill(tabX + i * tabWidth, tabY, 
                           tabX + (i + 1) * tabWidth, tabY + tabHeight, color);
            
            // Draw tab icon (representative item for that recipe type)
            ItemStack icon = getIconForRecipeType(type);
            guiGraphics.renderItem(icon, tabX + i * tabWidth + 6, tabY + 6);
        }
    }
    
    private ItemStack getIconForRecipeType(RecipeType<?> type) {
        // Return appropriate icon item for each recipe type
        if (type == RecipeType.CRAFTING) return new ItemStack(Items.CRAFTING_TABLE);
        if (type == RecipeType.SMELTING) return new ItemStack(Items.FURNACE);
        if (type == RecipeType.BLASTING) return new ItemStack(Items.BLAST_FURNACE);
        if (type == RecipeType.SMOKING) return new ItemStack(Items.SMOKER);
        if (type == RecipeType.STONECUTTING) return new ItemStack(Items.STONECUTTER);
        if (type == RecipeType.SMITHING) return new ItemStack(Items.SMITHING_TABLE);
        return ItemStack.EMPTY;
    }
}
```

**RecipeRenderer Base Class:**

```java
public abstract class RecipeRenderer {
    /**
     * Render the recipe at the given position.
     */
    public abstract void render(GuiGraphics guiGraphics, int x, int y, 
                               RecipeHolder<?> recipe, int mouseX, int mouseY);
    
    /**
     * Get the width of this renderer's GUI.
     */
    public abstract int getWidth();
    
    /**
     * Get the height of this renderer's GUI.
     */
    public abstract int getHeight();
}
```

**CraftingRecipeRenderer:**

```java
public class CraftingRecipeRenderer extends RecipeRenderer {
    private static final ResourceLocation CRAFTING_TABLE_LOCATION = 
        ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    
    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, 
                      RecipeHolder<?> recipe, int mouseX, int mouseY) {
        // Draw crafting table background
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE_LOCATION, 
                        x, y, 0, 0, 176, 166, 256, 256);
        
        // Render recipe ingredients in 3x3 grid
        if (recipe.value() instanceof CraftingRecipe craftingRecipe) {
            renderCraftingIngredients(guiGraphics, x, y, craftingRecipe);
        }
        
        // Render result item
        ItemStack result = recipe.value().getResultItem(/* registry */);
        guiGraphics.renderItem(result, x + 124, y + 35);
    }
    
    private void renderCraftingIngredients(GuiGraphics guiGraphics, int x, int y, 
                                          CraftingRecipe recipe) {
        // Get ingredients and render them in appropriate grid positions
        // Handle both shaped and shapeless recipes
    }
    
    @Override
    public int getWidth() { return 176; }
    
    @Override
    public int getHeight() { return 166; }
}
```

**FurnaceRecipeRenderer:**

```java
public class FurnaceRecipeRenderer extends RecipeRenderer {
    private final ResourceLocation texture;
    
    public FurnaceRecipeRenderer(String texturePath) {
        this.texture = ResourceLocation.withDefaultNamespace(texturePath);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, 
                      RecipeHolder<?> recipe, int mouseX, int mouseY) {
        // Draw furnace background
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, 
                        x, y, 0, 0, 176, 166, 256, 256);
        
        // Render input ingredient
        if (recipe.value() instanceof AbstractCookingRecipe cookingRecipe) {
            // Get first ingredient option and render it
            // Position at x + 56, y + 17 (input slot)
        }
        
        // Render fuel indicator (coal/lava bucket)
        ItemStack fuel = new ItemStack(Items.COAL);
        guiGraphics.renderItem(fuel, x + 56, y + 53);
        
        // Render result
        ItemStack result = recipe.value().getResultItem(/* registry */);
        guiGraphics.renderItem(result, x + 116, y + 35);
    }
    
    @Override
    public int getWidth() { return 176; }
    
    @Override
    public int getHeight() { return 166; }
}
```

**SmithingRecipeRenderer:**

```java
public class SmithingRecipeRenderer extends RecipeRenderer {
    private static final ResourceLocation SMITHING_LOCATION = 
        ResourceLocation.withDefaultNamespace("textures/gui/container/smithing.png");
    
    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, 
                      RecipeHolder<?> recipe, int mouseX, int mouseY) {
        // Draw smithing table background
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SMITHING_LOCATION, 
                        x, y, 0, 0, 176, 166, 256, 256);
        
        // Render ingredients
        if (recipe.value() instanceof SmithingRecipe smithingRecipe) {
            // Render template, base, and addition items
            // Template: x + 8, y + 45
            // Base: x + 26, y + 45  
            // Addition: x + 44, y + 45
            // Result: x + 98, y + 45
        }
    }
    
    @Override
    public int getWidth() { return 176; }
    
    @Override
    public int getHeight() { return 166; }
}
```

**StonecutterRecipeRenderer:**

```java
public class StonecutterRecipeRenderer extends RecipeRenderer {
    private static final ResourceLocation STONECUTTER_LOCATION = 
        ResourceLocation.withDefaultNamespace("textures/gui/container/stonecutter.png");
    
    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, 
                      RecipeHolder<?> recipe, int mouseX, int mouseY) {
        // Draw stonecutter background
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, STONECUTTER_LOCATION, 
                        x, y, 0, 0, 176, 166, 256, 256);
        
        // Render ingredients
        if (recipe.value() instanceof StonecutterRecipe stonecutterRecipe) {
            // Render input item at x + 20, y + 33
            // Render output item at x + 143, y + 33
        }
    }
    
    @Override
    public int getWidth() { return 176; }
    
    @Override
    public int getHeight() { return 166; }
}
```

---

### Phase 4: 'R' Key Binding for Recipe Viewing

**Objective:** Register 'R' key and implement recipe viewing logic.

**Files to Modify:**
- `src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java`
- `src/main/java/net/minecraft/client/Options.java` (if creating new keybinding)

**Implementation in AbstractContainerScreen:**

```java
@Override
public boolean keyPressed(KeyEvent keyEvent) {
    // Check for 'R' key (key code 82)
    if (keyEvent.key() == 82 && !keyEvent.hasControlDown() && !keyEvent.hasAltDown()) {
        ItemStack hoveredItem = getHoveredItemStack();
        
        if (!hoveredItem.isEmpty()) {
            return openRecipeViewer(hoveredItem);
        }
    }
    
    // Check JEI panel first
    if (this.jeiPanel != null && this.jeiPanel.keyPressed(keyEvent)) {
        return true;
    }
    
    // ... rest of existing key handling
}

/**
 * Get the ItemStack currently hovered by the mouse.
 * Checks in order: slot hover, JEI panel hover.
 */
private ItemStack getHoveredItemStack() {
    // First check if hovering over a slot
    if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
        return this.hoveredSlot.getItem();
    }
    
    // Check JEI panel
    if (this.jeiPanel != null) {
        ItemStack jeiItem = this.jeiPanel.getHoveredItem();
        if (!jeiItem.isEmpty()) {
            return jeiItem;
        }
    }
    
    return ItemStack.EMPTY;
}

/**
 * Open the recipe viewer for the given item.
 * Returns true if recipes were found and viewer opened.
 */
private boolean openRecipeViewer(ItemStack item) {
    if (this.minecraft == null || this.minecraft.level == null) {
        return false;
    }
    
    // Find recipes for this item
    Map<RecipeType<?>, List<RecipeHolder<?>>> recipes = 
        RecipeLookupHelper.findRecipesFor(item.getItem(), this.minecraft.level);
    
    if (recipes.isEmpty()) {
        // No recipes found - could show a message
        return false;
    }
    
    // Open recipe viewer screen
    RecipeViewerScreen viewer = new RecipeViewerScreen(this, item, recipes);
    this.minecraft.setScreen(viewer);
    return true;
}
```

**Alternative: Create Dedicated KeyMapping:**

If we want the key to be configurable, create a new `KeyMapping`:

```java
// In Minecraft class or Options class
public final KeyMapping keyRecipeViewer = new KeyMapping(
    "key.recipeviewer.show",
    InputConstants.KEY_R,
    KeyMapping.Category.INVENTORY
);

// Register in key mappings array
```

Then check with:
```java
if (this.minecraft.options.keyRecipeViewer.matches(keyEvent)) {
    // Open recipe viewer
}
```

---

### Phase 5: Input Handling & ESC Behavior

**Objective:** Implement proper input handling so ESC closes recipe viewer first.

**Implementation Details:**

The `RecipeViewerScreen` already handles this in Phase 3:

```java
@Override
public boolean keyPressed(KeyEvent keyEvent) {
    // ESC closes the recipe viewer, returning to parent screen
    if (keyEvent.key() == 256) { // ESC key code
        this.minecraft.setScreen(parentScreen);
        return true; // Consume the event
    }
    
    // Handle other keys...
    return super.keyPressed(keyEvent);
}
```

**How it works:**
1. Player opens inventory screen (e.g., chest)
2. Player presses 'R' on an item → Recipe viewer opens over inventory
3. Player presses ESC → Recipe viewer closes, inventory still open
4. Player presses ESC again → Inventory closes, returns to game

**State Flow:**
```
Game → Inventory Screen → Recipe Viewer Screen
                ↑              ↓ (ESC)
                ←──────────────┘
        ↓ (ESC)
        Game
```

---

### Phase 6: JEI Panel Integration

**Objective:** Track hovered items in JEI panel and support 'R' key.

**Files to Modify:**
- `src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java`

**Add to JeiPanel:**

```java
// Track currently hovered item
private ItemStack hoveredItem = ItemStack.EMPTY;
private int hoveredItemX = -1;
private int hoveredItemY = -1;

/**
 * Get the currently hovered item in the JEI panel.
 */
public ItemStack getHoveredItem() {
    return this.hoveredItem;
}

/**
 * Update hover tracking - call this in render method.
 */
private void updateHoverTracking(int mouseX, int mouseY) {
    this.hoveredItem = ItemStack.EMPTY;
    this.hoveredItemX = -1;
    this.hoveredItemY = -1;
    
    if (this.columns <= 0 || this.rows <= 0 || this.filteredTabItems.isEmpty()) {
        return;
    }
    
    if (mouseX < this.panelX || mouseX >= this.panelX + this.panelWidth ||
        mouseY < this.panelY || mouseY >= this.panelY + this.panelHeight) {
        return;
    }
    
    int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
    int maxScroll = Math.max(0, totalRows - this.rows);
    int scrollRow = (int)(this.scrollOffs * maxScroll);
    int startIndex = scrollRow * this.columns;
    
    for (int row = 0; row < this.rows; row++) {
        for (int col = 0; col < this.columns; col++) {
            int index = startIndex + row * this.columns + col;
            if (index >= this.filteredTabItems.size()) {
                break;
            }
            
            int x = this.panelX + 2 + col * this.slotSize;
            int y = this.panelY + 2 + row * this.slotSize;
            
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                this.hoveredItem = this.filteredTabItems.get(index);
                this.hoveredItemX = x;
                this.hoveredItemY = y;
                return;
            }
        }
    }
}

@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Update hover tracking
    updateHoverTracking(mouseX, mouseY);
    
    // ... rest of existing render code
}

// Note: keyPressed already exists in JeiPanel, it handles search bar
// The 'R' key will be handled by AbstractContainerScreen which calls
// jeiPanel.getHoveredItem() when 'R' is pressed
```

---

### Phase 7: Visual Polish and UX

**Objective:** Add visual polish, tooltips, and indicators.

**Features to Add:**

1. **Recipe Availability Tooltip:**
```java
// In AbstractContainerScreen or JeiPanel
private void renderRecipeIndicator(GuiGraphics guiGraphics, Slot slot) {
    if (slot.hasItem()) {
        ItemStack item = slot.getItem();
        Map<RecipeType<?>, List<RecipeHolder<?>>> recipes = 
            RecipeLookupHelper.findRecipesFor(item.getItem(), this.minecraft.level);
        
        if (!recipes.isEmpty()) {
            // Draw small indicator (e.g., green dot in corner)
            guiGraphics.fill(slotX + 12, slotY + 12, slotX + 16, slotY + 16, 0xFF00FF00);
        }
    }
}
```

2. **"Press R" Tooltip:**
```java
// Add to tooltip when hovering over items with recipes
if (!recipes.isEmpty()) {
    tooltip.add(Component.literal("Press R to view recipes").withStyle(ChatFormatting.GRAY));
}
```

3. **Smooth Animations:**
```java
// In RecipeViewerScreen
private float fadeInProgress = 0.0F;
private static final float FADE_DURATION = 0.15F; // seconds

@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Update fade animation
    fadeInProgress = Math.min(fadeInProgress + partialTick / FADE_DURATION, 1.0F);
    
    // Apply fade to background
    int alpha = (int)(0x80 * fadeInProgress);
    guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24));
    
    // Scale recipe GUI based on fade
    guiGraphics.pose().pushPose();
    guiGraphics.pose().translate(centerX + guiWidth / 2.0F, centerY + guiHeight / 2.0F, 0);
    guiGraphics.pose().scale(fadeInProgress, fadeInProgress, 1.0F);
    guiGraphics.pose().translate(-(centerX + guiWidth / 2.0F), -(centerY + guiHeight / 2.0F), 0);
    
    // ... render recipe
    
    guiGraphics.pose().popPose();
}
```

4. **Recipe Count Indicator:**
```java
// Show "Recipe 1 of 3" text
if (getCurrentRecipes().size() > 1) {
    String text = String.format("Recipe %d of %d", 
        currentRecipeIndex + 1, getCurrentRecipes().size());
    guiGraphics.drawString(this.font, text, centerX + guiWidth / 2 - 30, 
                          centerY - 15, 0xFFFFFFFF);
}
```

5. **Tab Labels:**
```java
// Add tooltip on tab hover
if (isMouseOverTab(mouseX, mouseY, tabIndex)) {
    Component tabName = getRecipeTypeName(availableTabs.get(tabIndex));
    guiGraphics.setTooltipForNextFrame(this.font, List.of(tabName), mouseX, mouseY);
}
```

---

### Phase 8: Testing and Edge Cases

**Objective:** Comprehensive testing of all scenarios.

**Test Cases:**

1. **Items with No Recipes:**
   - Test: Press 'R' on items like dirt, grass, cobblestone (basic blocks)
   - Expected: No recipe viewer opens (or message "No recipes available")

2. **Items with Single Recipe:**
   - Test: Press 'R' on wooden planks (crafted from logs)
   - Expected: Recipe viewer opens showing crafting table with log → planks

3. **Items with Multiple Recipes (Same Type):**
   - Test: Press 'R' on stone (can be crafted or smelted from multiple stones)
   - Expected: Recipe viewer with navigation arrows to cycle recipes

4. **Items with Multiple Recipe Types:**
   - Test: Press 'R' on cooked steak
   - Expected: Recipe viewer with tabs for Furnace, Smoker
   - Test: Click each tab to verify correct GUI renders

5. **All Inventory Contexts:**
   - Player inventory (E key)
   - Crafting table
   - Furnace / Blast Furnace / Smoker
   - Smithing table
   - Stonecutter
   - Chest / Shulker box / Barrel
   - Ender chest
   - Dispenser / Dropper
   - Hopper
   - Anvil
   - Enchanting table
   - Brewing stand

6. **JEI Panel Integration:**
   - Hover over JEI panel items and press 'R'
   - Verify recipe viewer opens with correct recipes
   - Test with filtered items in JEI search

7. **ESC Key Behavior:**
   - Open inventory → Press 'R' → Press ESC → Verify recipe viewer closes, inventory stays
   - Press ESC again → Verify inventory closes
   - Test with nested containers (chest inside inventory)

8. **Performance:**
   - Test with large modpacks with thousands of recipes
   - Verify recipe cache works efficiently
   - Test recipe reload scenario (resource pack change)

9. **Edge Cases:**
   - Items with recipe book categories but no actual recipes
   - Custom recipes added via data packs
   - Recipes with complex ingredients (tags, NBT)
   - Smithing recipes with different template types
   - Multiple output items (though rare in vanilla)

10. **Visual Issues:**
    - Test all recipe GUIs render correctly
    - Verify item rendering in recipe slots
    - Check tooltip positioning at screen edges
    - Test with different GUI scales
    - Verify overlay doesn't clip

**Performance Benchmarks:**
- Recipe lookup should take < 1ms for cached items
- Recipe viewer should open in < 50ms
- GUI rendering should maintain 60 FPS

---

## Technical Implementation Notes

### Recipe Result Extraction

Different recipe types store results differently:

```java
private static ItemStack getRecipeResult(Recipe<?> recipe, RegistryAccess registryAccess) {
    if (recipe instanceof CraftingRecipe) {
        return recipe.getResultItem(registryAccess);
    } else if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
        return cookingRecipe.result();
    } else if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
        return stonecutterRecipe.result();
    } else if (recipe instanceof SmithingRecipe smithingRecipe) {
        return smithingRecipe.getResultItem(registryAccess);
    }
    
    // Fallback
    return recipe.getResultItem(registryAccess);
}
```

### Ingredient Rendering

Ingredients in recipes can have multiple options (e.g., any log type):

```java
private void renderIngredient(GuiGraphics guiGraphics, Ingredient ingredient, 
                              int x, int y, long gameTime) {
    if (ingredient.isEmpty()) {
        return;
    }
    
    // Cycle through ingredient options
    ItemStack[] stacks = ingredient.getItems();
    if (stacks.length > 0) {
        int index = (int)(gameTime / 30) % stacks.length;
        guiGraphics.renderItem(stacks[index], x, y);
    }
}
```

### Recipe JSON System Integration

All recipe discovery uses Minecraft's recipe JSON system:
- Recipes are loaded from data packs: `data/minecraft/recipes/`
- Custom recipes can be added via data packs
- Recipe manager handles all recipe loading and caching
- No hardcoding of recipes required

### Texture Locations

All GUI textures are located at:
- Crafting Table: `textures/gui/container/crafting_table.png`
- Furnace: `textures/gui/container/furnace.png`
- Blast Furnace: `textures/gui/container/blast_furnace.png`
- Smoker: `textures/gui/container/smoker.png`
- Stonecutter: `textures/gui/container/stonecutter.png`
- Smithing Table: `textures/gui/container/smithing.png`

### State Management

The recipe viewer maintains its own state:
- Current tab (recipe type)
- Current recipe index (for multiple recipes of same type)
- Parent screen reference (to return to)
- Does NOT modify parent screen state

---

## File Structure Summary

### New Files
```
src/main/java/net/minecraft/client/
├── recipe/
│   └── RecipeLookupHelper.java                    [Recipe query utility]
└── gui/
    ├── screens/inventory/
    │   └── RecipeViewerScreen.java               [Main recipe viewer overlay]
    └── components/recipes/
        ├── RecipeRenderer.java                    [Base renderer class]
        ├── CraftingRecipeRenderer.java           [Crafting table renderer]
        ├── FurnaceRecipeRenderer.java            [Furnace/blast/smoker renderer]
        ├── SmithingRecipeRenderer.java           [Smithing table renderer]
        └── StonecutterRecipeRenderer.java        [Stonecutter renderer]
```

### Modified Files
```
src/main/java/net/minecraft/client/gui/screens/inventory/
├── AbstractRecipeBookScreen.java                  [Remove recipe book button]
├── AbstractContainerScreen.java                   ['R' key handling]
└── JeiPanel.java                                  [Hover tracking for recipe viewer]
```

---

## Implementation Order

1. **Phase 1** - Remove recipe book button (simple, immediate visual change)
2. **Phase 2** - Recipe lookup system (foundation for everything else)
3. **Phase 3** - Recipe viewer GUI (core functionality)
   - Start with one renderer (crafting) to test the system
   - Add remaining renderers incrementally
4. **Phase 4** - 'R' key binding (connects lookup to viewer)
5. **Phase 6** - JEI panel integration (extends to all contexts)
6. **Phase 5** - Input handling (already mostly done in Phase 3)
7. **Phase 7** - Visual polish (nice-to-have improvements)
8. **Phase 8** - Testing (continuous throughout, comprehensive at end)

---

## Known Limitations & Future Enhancements

### Current Limitations
- Campfire cooking recipes excluded (no GUI in vanilla)
- Complex custom recipes may need special handling
- Recipe book unlocking status not shown (could be added)

### Future Enhancements
- Add recipe favoriting system
- Recipe search within viewer
- Show required items in inventory vs missing
- Automatic crafting from recipe viewer
- Recipe usage lookup ('U' key to see what item is used for)
- Integration with recipe advancement system

---

## Conclusion

This implementation plan provides a complete JEI-style recipe viewer that:
- ✅ Removes vanilla recipe book button
- ✅ Dynamically discovers recipes from JSON system  
- ✅ Displays recipes with authentic GUI textures
- ✅ Supports tabs for multiple recipe types
- ✅ Works from any inventory context including JEI panel
- ✅ Handles ESC key intelligently
- ✅ Provides smooth UX with visual polish

The system is modular, extensible, and respects Minecraft's existing recipe infrastructure.
