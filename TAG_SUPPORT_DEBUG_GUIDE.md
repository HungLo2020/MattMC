# Tag Support Debugging Guide

## Overview
This document explains how to test the tag support in the recipe viewing system and interpret the logging output.

## Testing Steps

### 1. Build the Project
```bash
./gradlew build
```

### 2. Run the Client
```bash
./gradlew runClient
```

### 3. Test Recipe Viewing with Tags

#### Test Case: Dyed Shulker Boxes
Dyed shulker boxes use the `#minecraft:shulker_boxes` tag as input, which should show all colored shulker boxes cycling through.

**Steps:**
1. Open your inventory (E key)
2. Find or give yourself a blue shulker box: `/give @p minecraft:blue_shulker_box`
3. Hover over the blue shulker box
4. Press 'R' to view recipes

**Expected Behavior:**
- Recipe viewer should open showing the crafting recipe
- The input slot should show ALL shulker box colors cycling through (purple, white, orange, magenta, etc.)
- The material slot should show blue dye
- The output should show blue shulker box

**Current Problem:**
- The input slot is EMPTY (no items showing up)

### 4. Check the Console Logs

When you press 'R' on a dyed shulker box, you should see detailed logging output. Here's what to look for:

#### Expected Log Flow for Working Tag Support:

```
     Creating RecipeViewerScreen...
     Creating ContextMap from level...
     ContextMap created: present
     REGISTRIES in contextMap: present
     FUEL_VALUES in contextMap: present
     RecipeViewerScreen created successfully

[RecipeViewerScreen] Rendering recipe: minecraft:blue_shulker_box
[RecipeViewerScreen]   Recipe type: RecipeType[minecraft:crafting]
[RecipeViewerScreen]   Using renderer: CraftingRecipeRenderer

[Ingredient] Creating TagSlotDisplay for tag: minecraft:shulker_boxes
[Ingredient] display() returning: TagSlotDisplay

[CraftingRecipeRenderer] Rendering SlotDisplay: TagSlotDisplay
[CraftingRecipeRenderer]   contextMap: present

[TagSlotDisplay] Resolving tag: minecraft:shulker_boxes
[TagSlotDisplay]   displayContentsFactory type: net.minecraft.world.item.crafting.display.DisplayContentsFactory$ForStacks
[TagSlotDisplay]   contextMap: present
[TagSlotDisplay]   displayContentsFactory IS ForStacks
[TagSlotDisplay]   REGISTRIES provider: present
[TagSlotDisplay]   Item registry obtained
[TagSlotDisplay]   Tag lookup result: PRESENT
[TagSlotDisplay]   Tag contains 16 items
[TagSlotDisplay]   Resolved to 16 stacks

[CraftingRecipeRenderer]   Resolved to 16 stacks
[CraftingRecipeRenderer]   Displaying item at index 0: 1 purple_shulker_box
```

#### Potential Failure Scenarios:

**Scenario 1: REGISTRIES Provider is NULL**
```
[TagSlotDisplay]   REGISTRIES provider: NULL
[TagSlotDisplay]   ERROR: REGISTRIES provider is NULL - cannot resolve tags!
```
→ **Fix**: Ensure ContextMap.fromLevel() is being called correctly and the level's registryAccess() is not null.

**Scenario 2: Tag Lookup Returns Empty**
```
[TagSlotDisplay]   Tag lookup result: EMPTY
[TagSlotDisplay]   WARNING: Tag minecraft:shulker_boxes is empty or not found!
```
→ **Fix**: Ensure the tag is properly registered in the game's tag system. Check data/minecraft/tags/items/shulker_boxes.json exists.

**Scenario 3: Tag Resolves but Renderer Gets Empty List**
```
[TagSlotDisplay]   Resolved to 16 stacks
[CraftingRecipeRenderer]   Resolved to 0 stacks
[CraftingRecipeRenderer]   WARNING: No stacks resolved - slot will be empty!
```
→ **Fix**: Issue in the stream processing between resolve() and the renderer. Check if toList() is being called correctly.

**Scenario 4: ContextMap is NULL**
```
[CraftingRecipeRenderer]   contextMap: NULL
```
→ **Fix**: Ensure contextMap is being passed correctly from RecipeViewerScreen to the renderer.

## What to Do Next

1. **Run the test** and capture the console output when viewing a dyed shulker box recipe
2. **Identify which scenario** matches your log output
3. **Share the logs** with the development team
4. **Based on the logs**, we can implement the precise fix needed

## Other Tag-Based Recipes to Test

After fixing the shulker box issue, test these recipes which also use tags:

1. **Planks from Logs** - Uses `#minecraft:logs` tag
2. **Buttons from Planks** - Uses `#minecraft:planks` tag  
3. **Smithing Templates** - May use various tags
4. **Banner Patterns** - May use dye tags

## Expected Output Format

For a properly working tag system, you should see:
- Tag name being logged
- Number of items in the tag (e.g., 16 shulker boxes)
- Items cycling through visually every 1.5 seconds (30 ticks / 20 ticks per second)

## Cleanup

Once the issue is identified and fixed, we will remove or significantly reduce this logging to avoid console spam during normal gameplay.
