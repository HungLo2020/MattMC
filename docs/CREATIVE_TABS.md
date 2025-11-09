# Creative Tab System

A Minecraft-style creative inventory tab system for organizing items in creative mode.

## Overview

The creative tab system provides a way to organize items into categories (tabs) similar to Minecraft's creative inventory. Items are automatically organized into predefined tabs like "Building Blocks", "Tools & Utilities", "Ingredients", etc.

## Features

- **Predefined Tabs**: 9 built-in tabs matching Minecraft's organization
- **Automatic Organization**: Items added to tabs in the Items class initialization
- **Dynamic System**: New items can be added to tabs at runtime
- **Modding Support**: Custom tabs can be registered for mods
- **Multi-Tab Support**: Items can belong to multiple tabs if needed
- **Type-Safe**: Uses Java's type system for compile-time safety

## Quick Start

### Adding a New Item to a Tab

To add a new item to the creative inventory, simply register it in `Items.java` and add it to the appropriate tab in the static initializer:

```java
// In Items.java

// 1. Register your item
public static final Item MY_ITEM = register("my_item", new Item(64));

// 2. Add it to a tab in the static initializer
static {
    // ... existing code ...
    
    // Add to Building Blocks tab
    CreativeTabs.BUILDING_BLOCKS.addItem(MY_ITEM);
    
    // ... existing code ...
}
```

That's it! Your item will now appear in the creative inventory under the Building Blocks tab.

### Available Predefined Tabs

The following tabs are available:

| Tab                   | Identifier           | Purpose                              |
|-----------------------|----------------------|--------------------------------------|
| `BUILDING_BLOCKS`     | building_blocks      | Construction blocks                  |
| `DECORATION_BLOCKS`   | decoration_blocks    | Decorative blocks                    |
| `REDSTONE`            | redstone             | Redstone components and mechanisms   |
| `TRANSPORTATION`      | transportation       | Rails, minecarts, boats, etc.        |
| `TOOLS`               | tools                | Tools like pickaxes, axes, shovels   |
| `COMBAT`              | combat               | Weapons and armor                    |
| `FOOD`                | food                 | Food items                           |
| `INGREDIENTS`         | ingredients          | Crafting ingredients                 |
| `MISCELLANEOUS`       | miscellaneous        | Items that don't fit other categories|

### Creating a Custom Tab

For mods or custom content, you can create your own tabs:

```java
// Create a custom tab with an icon
CreativeTab myTab = CreativeTabs.registerTab(
    "my_mod_tab",           // identifier
    Items.MY_ICON_ITEM,     // icon item
    "My Mod Items"          // display name
);

// Add items to your custom tab
myTab.addItem(Items.MY_CUSTOM_ITEM);
myTab.addItem(Items.MY_OTHER_ITEM);
```

### Adding an Item to Multiple Tabs

Items can be added to multiple tabs if they fit multiple categories:

```java
static {
    // TNT could be in both Redstone and Combat tabs
    CreativeTabs.REDSTONE.addItem(Items.TNT);
    CreativeTabs.COMBAT.addItem(Items.TNT);
}
```

## How It Works

### Architecture

1. **CreativeTab**: Represents a single tab with a name, icon, and list of items
2. **CreativeTabs**: Registry of all tabs and utility methods for accessing items
3. **Items**: Static initialization assigns items to tabs
4. **InventoryScreen**: Automatically populates creative inventory from tabs

### Item Organization Flow

```
Items.java (static init)
    ↓
Register items to tabs
    ↓
CreativeTabs.getAllUniqueItems()
    ↓
InventoryScreen displays items
```

### Key Classes

#### CreativeTab

```java
CreativeTab tab = new CreativeTab("my_tab", iconItem);
tab.addItem(item1);
tab.addItem(item2);
List<Item> items = tab.getItems();
```

#### CreativeTabs

```java
// Get all tabs
List<CreativeTab> tabs = CreativeTabs.getAllTabs();

// Get a specific tab
CreativeTab tools = CreativeTabs.TOOLS;
CreativeTab tools = CreativeTabs.getTab("tools");

// Get all items
List<Item> allItems = CreativeTabs.getAllUniqueItems();

// Find tabs containing an item
List<CreativeTab> tabs = CreativeTabs.getTabsForItem(Items.DIAMOND);
```

## Examples

### Example 1: Adding a Set of Related Items

```java
// In Items.java
public static final BlockItem RED_WOOL = register("red_wool", new BlockItem(Blocks.RED_WOOL));
public static final BlockItem BLUE_WOOL = register("blue_wool", new BlockItem(Blocks.BLUE_WOOL));
public static final BlockItem GREEN_WOOL = register("green_wool", new BlockItem(Blocks.GREEN_WOOL));

static {
    // Add all wool to Decoration Blocks
    CreativeTabs.DECORATION_BLOCKS.addItem(RED_WOOL);
    CreativeTabs.DECORATION_BLOCKS.addItem(BLUE_WOOL);
    CreativeTabs.DECORATION_BLOCKS.addItem(GREEN_WOOL);
}
```

### Example 2: Adding Items with Different Stack Sizes

```java
// In Items.java
public static final Item ENDER_PEARL = register("ender_pearl", new Item(16));  // Max 16
public static final Item SNOWBALL = register("snowball", new Item(16));        // Max 16
public static final Item BUCKET = register("bucket", new Item(16));            // Max 16

static {
    CreativeTabs.MISCELLANEOUS.addItem(ENDER_PEARL);
    CreativeTabs.MISCELLANEOUS.addItem(SNOWBALL);
    CreativeTabs.TOOLS.addItem(BUCKET);
}
```

### Example 3: Tools with Different Materials

```java
// In Items.java
public static final Item WOODEN_SWORD = register("wooden_sword", new Item(1));
public static final Item STONE_SWORD = register("stone_sword", new Item(1));
public static final Item IRON_SWORD = register("iron_sword", new Item(1));
public static final Item DIAMOND_SWORD = register("diamond_sword", new Item(1));

static {
    // Add all swords to Combat tab
    CreativeTabs.COMBAT.addItem(WOODEN_SWORD);
    CreativeTabs.COMBAT.addItem(STONE_SWORD);
    CreativeTabs.COMBAT.addItem(IRON_SWORD);
    CreativeTabs.COMBAT.addItem(DIAMOND_SWORD);
}
```

## Best Practices

1. **Organization**: Keep related items together in the same tab
2. **Consistency**: Follow Minecraft's organization patterns when possible
3. **Clarity**: Use descriptive tab names for custom tabs
4. **Icons**: Choose recognizable items as tab icons
5. **Order**: Add items to tabs in a logical order (e.g., wooden → stone → iron → diamond)

## Testing

The creative tab system includes comprehensive tests:

- `CreativeTabTest`: Tests for individual tab functionality
- `CreativeTabsTest`: Tests for the tab registry and item organization

Run tests with:
```bash
./gradlew test --tests "mattmc.world.item.*"
```

## Future Enhancements

Potential improvements for the future:

- Tab icons in the UI
- Multiple pages per tab with scrolling
- Search/filter functionality
- Save/load custom tab configurations
- Per-player custom tabs
- Drag-and-drop tab reordering

## Troubleshooting

### Item not appearing in creative inventory?

1. Check that the item is registered in `Items.java`
2. Verify it's added to a tab in the static initializer
3. Ensure the tab is not empty
4. Check that `CreativeTabs.getAllUniqueItems()` returns your item

### Duplicate items appearing?

- If an item is added to multiple tabs, `getAllUniqueItems()` will only include it once
- If you see true duplicates, check for duplicate `addItem()` calls

### Custom tab not showing up?

1. Make sure you call `CreativeTabs.registerTab()` before any items are added
2. Verify the tab identifier is unique
3. Check that the tab has items added to it
