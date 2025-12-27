# Data Generation (Datagen) Guide

## Overview

Minecraft's **Data Generation** system allows you to programmatically generate JSON data files (recipes, loot tables, tags, etc.) from Java code instead of manually writing JSON files. This ensures consistency, reduces errors, and makes it easier to maintain large amounts of game data.

## What is Datagen?

Datagen is a Java-based code generation system that:

1. **Converts Java code into JSON files** - Write type-safe Java code that generates data files
2. **Validates data at build time** - Catch errors before runtime
3. **Ensures consistency** - Share code between related data (e.g., block tags and item tags)
4. **Reduces boilerplate** - Helper methods for common patterns
5. **Version-safe** - Updates automatically when Minecraft's data format changes

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   Your Java Code                          │
│  (Extends DataProvider: RecipeProvider, etc.)            │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────┐
│              DataGenerator Main.java                      │
│         Orchestrates all data providers                   │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────┐
│              Generated JSON Files                         │
│    Output to: generated/data/<namespace>/                │
│    - recipes/                                             │
│    - loot_table/                                          │
│    - tags/                                                │
│    - advancements/                                        │
│    - etc.                                                 │
└──────────────────────────────────────────────────────────┘
```

## Key Components

### 1. DataGenerator

**Location**: `net.minecraft.data.DataGenerator`

The main orchestrator that:
- Manages all data providers
- Handles output directory structure
- Coordinates generation process
- Validates generated data

### 2. DataProvider

**Location**: `net.minecraft.data.DataProvider`

Base interface for all data providers. Common implementations:

- **LootTableProvider** - Generates loot tables
- **RecipeProvider** - Generates recipes
- **BlockTagsProvider** - Generates block tags
- **ItemTagsProvider** - Generates item tags
- **AdvancementProvider** - Generates advancements
- **ModelProvider** - Generates block/item models

### 3. PackOutput

**Location**: `net.minecraft.data.PackOutput`

Manages output paths for generated files:
- `DATA_PACK` target → `data/` directory
- `RESOURCE_PACK` target → `assets/` directory
- `REPORTS` target → `reports/` directory

## Running Datagen

### Command Line

Run the datagen system using Minecraft's Main class:

```bash
# Run with all generators
java -cp build/libs/minecraft-1.21.10.jar net.minecraft.data.Main --all

# Run specific generators
java -cp build/libs/minecraft-1.21.10.jar net.minecraft.data.Main --server --dev --reports

# Specify custom output directory
java -cp build/libs/minecraft-1.21.10.jar net.minecraft.data.Main --output my_output_dir
```

### Gradle Task (Recommended)

Add a Gradle task to your `build.gradle`:

```groovy
tasks.register('runDatagen', JavaExec) {
    group = 'minecraft'
    description = 'Runs the Minecraft data generator'
    
    dependsOn 'classes'
    
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'net.minecraft.data.Main'
    
    // Arguments for datagen
    args = [
        '--all',                    // Generate all data
        '--output', 'generated',    // Output directory
        '--server',                 // Include server generators
        '--dev',                    // Include dev tools
        '--reports'                 // Include data reports
    ]
    
    workingDir = file('.')
}
```

Then run:
```bash
./gradlew runDatagen
```

### Available Flags

- `--all` - Generate all data (recommended)
- `--server` - Generate server-side data (recipes, loot, tags)
- `--dev` - Generate development tools
- `--reports` - Generate validation reports
- `--output <dir>` - Specify output directory (default: `generated/`)
- `--input <dir>` - Specify input directory for structure files

## Creating Custom Data Providers

### Example 1: Custom Block Loot Tables

Create a custom loot table provider for your mod's blocks:

```java
package com.yourmod.data;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Items;
import java.util.Set;

public class ModBlockLoot extends BlockLootSubProvider {
    
    public ModBlockLoot(HolderLookup.Provider provider) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
    }
    
    @Override
    public void generate() {
        // Simple drop: block drops itself
        this.dropSelf(ModBlocks.CUSTOM_BLOCK);
        
        // Drop different item
        this.add(ModBlocks.CUSTOM_ORE, 
            block -> createSingleItemTable(Items.DIAMOND));
        
        // Drop with fortune bonus
        this.add(ModBlocks.CUSTOM_ORE_ADVANCED,
            block -> createOreDrop(block, ModItems.CUSTOM_GEM));
        
        // Slab drops (handles double slabs)
        this.add(ModBlocks.CUSTOM_SLAB,
            BlockLootSubProvider::createSlabItemTable);
        
        // Door drops (handles upper/lower halves)
        this.add(ModBlocks.CUSTOM_DOOR,
            BlockLootSubProvider::createDoorTable);
        
        // Silk touch required
        this.add(ModBlocks.GRASS_BLOCK,
            block -> createSingleItemTableWithSilkTouch(block, Items.DIRT));
    }
    
    @Override
    protected Iterable<Block> getKnownBlocks() {
        // Return all blocks that should have loot tables
        return ModBlocks.getAllBlocks();
    }
}
```

### Example 2: Custom Recipe Provider

Create recipes programmatically:

```java
package com.yourmod.data;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.HolderLookup;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {
    
    public ModRecipeProvider(HolderLookup.Provider provider) {
        super(provider);
    }
    
    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Shaped recipe - specific pattern
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.CUSTOM_PICKAXE)
            .pattern("XXX")
            .pattern(" S ")
            .pattern(" S ")
            .define('X', ModItems.CUSTOM_GEM)
            .define('S', Items.STICK)
            .unlockedBy("has_gem", has(ModItems.CUSTOM_GEM))
            .save(output);
        
        // Shapeless recipe - any arrangement
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CUSTOM_DUST, 4)
            .requires(ModBlocks.CUSTOM_ORE)
            .unlockedBy("has_ore", has(ModBlocks.CUSTOM_ORE))
            .save(output);
        
        // Smelting recipe
        SimpleCookingRecipeBuilder.smelting(
                Ingredient.of(ModItems.RAW_CUSTOM),
                RecipeCategory.MISC,
                ModItems.CUSTOM_INGOT,
                0.7F,  // Experience
                200    // Cooking time in ticks (10 seconds)
            )
            .unlockedBy("has_raw_custom", has(ModItems.RAW_CUSTOM))
            .save(output);
        
        // Blasting recipe (faster than smelting)
        SimpleCookingRecipeBuilder.blasting(
                Ingredient.of(ModItems.RAW_CUSTOM),
                RecipeCategory.MISC,
                ModItems.CUSTOM_INGOT,
                0.7F,
                100    // 5 seconds
            )
            .unlockedBy("has_raw_custom", has(ModItems.RAW_CUSTOM))
            .save(output, "custom_ingot_from_blasting");
        
        // Stonecutting recipe
        SingleItemRecipeBuilder.stonecutting(
                Ingredient.of(ModBlocks.CUSTOM_BLOCK),
                RecipeCategory.BUILDING_BLOCKS,
                ModBlocks.CUSTOM_STAIRS,
                1
            )
            .unlockedBy("has_custom_block", has(ModBlocks.CUSTOM_BLOCK))
            .save(output, "custom_stairs_from_stonecutting");
    }
}
```

### Example 3: Custom Tag Provider

Create block and item tags:

```java
package com.yourmod.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends ItemTagsProvider {
    
    public ModItemTagProvider(
        HolderLookup.Provider provider,
        CompletableFuture<TagsProvider.TagLookup<Block>> blockTags
    ) {
        super(provider, blockTags);
    }
    
    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Add items to vanilla tags
        this.tag(ItemTags.PICKAXES)
            .add(ModItems.CUSTOM_PICKAXE);
        
        this.tag(ItemTags.LOGS)
            .add(ModBlocks.CUSTOM_LOG.asItem())
            .add(ModBlocks.STRIPPED_CUSTOM_LOG.asItem());
        
        // Create custom tag
        this.tag(ModTags.Items.CUSTOM_GEMS)
            .add(ModItems.CUSTOM_GEM)
            .add(ModItems.RARE_GEM)
            .add(ModItems.EPIC_GEM);
        
        // Include another tag
        this.tag(ModTags.Items.ALL_TOOLS)
            .addTag(ItemTags.PICKAXES)
            .addTag(ItemTags.AXES)
            .addTag(ItemTags.SHOVELS);
        
        // Copy from block tag
        this.copy(BlockTags.PLANKS, ItemTags.PLANKS);
    }
}
```

## Integrating Custom Providers

Update `net.minecraft.data.Main.java` to include your custom providers:

```java
public static void addServerProviders(
    DataGenerator dataGenerator,
    Collection<Path> collection,
    boolean bl,
    boolean bl2,
    boolean bl3
) {
    DataGenerator.PackGenerator packGenerator = dataGenerator.getVanillaPack(bl);
    
    // ... existing vanilla providers ...
    
    // Add your custom providers
    CompletableFuture<HolderLookup.Provider> registries = 
        CompletableFuture.supplyAsync(VanillaRegistries::createLookup);
    
    packGenerator.addProvider(bindRegistries(ModRecipeProvider::new, registries));
    packGenerator.addProvider(bindRegistries(ModBlockLoot::new, registries));
    
    TagsProvider<Block> blockTags = packGenerator.addProvider(
        bindRegistries(ModBlockTagProvider::new, registries)
    );
    packGenerator.addProvider(output -> new ModItemTagProvider(
        output,
        registries,
        blockTags.contentsGetter()
    ));
}
```

## Common Loot Table Patterns

### Simple Block Drop

```java
// Block drops itself
this.dropSelf(Blocks.STONE);
```

### Ore with Fortune

```java
// Ore drops with fortune bonus
this.add(Blocks.DIAMOND_ORE, 
    block -> createOreDrop(block, Items.DIAMOND));
```

### Silk Touch Required

```java
// Different drop unless silk touch
this.add(Blocks.GRASS_BLOCK,
    block -> createSingleItemTableWithSilkTouch(block, Items.DIRT));
```

### Multiple Items

```java
// Drop multiple different items
this.add(Blocks.CUSTOM_BLOCK, block ->
    LootTable.lootTable()
        .withPool(LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .add(LootItem.lootTableItem(Items.DIAMOND)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3))))
        )
        .withPool(LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .add(LootItem.lootTableItem(Items.EMERALD))
            .when(LootItemRandomChanceCondition.randomChance(0.5f))
        )
);
```

### Crop Block (Age-based)

```java
// Wheat - different drops based on age
this.add(Blocks.WHEAT, block ->
    createCropDrops(
        block,
        Items.WHEAT,        // Mature crop item
        Items.WHEAT_SEEDS,  // Seed item
        LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
            .setProperties(StatePropertiesPredicate.Builder.properties()
                .hasProperty(CropBlock.AGE, 7)  // Max age
            )
    )
);
```

### Conditional Drops

```java
// Drop only when killed by player
this.add(Blocks.CUSTOM_BLOCK, block ->
    LootTable.lootTable()
        .withPool(LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .add(LootItem.lootTableItem(Items.DIAMOND))
            .when(LootItemKilledByPlayerCondition.killedByPlayer())
        )
);
```

## Understanding the Output Structure

When you run datagen with `--output generated`, the files are created in:

```
generated/
├── data/
│   └── <namespace>/          # Usually "minecraft" or your mod ID
│       ├── recipe/           # Generated recipes
│       │   └── custom_pickaxe.json
│       ├── loot_table/       # Generated loot tables
│       │   └── blocks/
│       │       └── custom_block.json
│       ├── tags/             # Generated tags
│       │   ├── block/
│       │   │   └── mineable/
│       │   │       └── pickaxe.json
│       │   └── item/
│       │       └── custom_gems.json
│       └── advancement/      # Generated advancements
│           └── story/
│               └── custom_achievement.json
└── reports/                  # Validation reports
    ├── blocks.json
    ├── items.json
    └── commands.json
```

## Generated JSON Examples

### Recipe Example

From `ShapedRecipeBuilder`:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "tools",
  "pattern": [
    "XXX",
    " S ",
    " S "
  ],
  "key": {
    "X": {
      "item": "yourmod:custom_gem"
    },
    "S": {
      "item": "minecraft:stick"
    }
  },
  "result": {
    "id": "yourmod:custom_pickaxe",
    "count": 1
  }
}
```

### Loot Table Example

From `BlockLootSubProvider`:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:diamond"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ],
  "random_sequence": "yourmod:blocks/custom_ore"
}
```

### Tag Example

From `ItemTagsProvider`:

```json
{
  "values": [
    "yourmod:custom_gem",
    "yourmod:rare_gem",
    "yourmod:epic_gem"
  ]
}
```

## Best Practices

### 1. Use Helper Methods

Don't recreate the wheel - use existing helper methods:

```java
// ✓ Good - use helper
this.dropSelf(Blocks.STONE);

// ✗ Bad - manual creation
this.add(Blocks.STONE, LootTable.lootTable()
    .withPool(LootPool.lootPool()
        .setRolls(ConstantValue.exactly(1))
        .add(LootItem.lootTableItem(Blocks.STONE))
    )
);
```

### 2. Organize Providers by Type

Keep different data types in separate providers:
- One provider for block loot
- One for entity loot
- One for chest loot
- Separate providers for recipes, tags, etc.

### 3. Use Tags for Flexibility

```java
// ✓ Good - flexible
.requires(Ingredient.of(ItemTags.LOGS))

// ✗ Bad - rigid
.requires(Items.OAK_LOG)
.requires(Items.BIRCH_LOG)
.requires(Items.SPRUCE_LOG)
// ... etc
```

### 4. Validate Your Data

Always run with `--reports` to generate validation reports:
- Check for missing loot tables
- Verify recipe unlock criteria
- Validate tag references

### 5. Version Control

Commit generated JSON files to your repository:
- Easier to review changes
- Can diff between versions
- Backup if datagen breaks

## Debugging Datagen

### Common Issues

**1. ClassNotFoundException**
```
Solution: Ensure classpath includes all dependencies
./gradlew runDatagen --info
```

**2. Missing Loot Tables**
```
Check reports/blocks.json for blocks without loot tables
Implement getKnownBlocks() to return all your blocks
```

**3. Invalid Recipe Unlock**
```
Every recipe needs an unlock criterion:
.unlockedBy("has_item", has(Items.ITEM))
```

**4. Tag Not Found**
```
Tags are generated in order
Block tags before item tags
Ensure dependencies are correct
```

### Enable Debug Logging

Add to your datagen task:

```groovy
tasks.register('runDatagen', JavaExec) {
    // ... other config ...
    
    jvmArgs = [
        '-Dlog4j.configurationFile=log4j2.xml',
        '-Dorg.slf4j.simpleLogger.defaultLogLevel=debug'
    ]
}
```

## Advanced Features

### Custom Loot Conditions

```java
// Create reusable condition
private static LootItemCondition.Builder hasFortuneEnchantment() {
    return MatchTool.toolMatches(
        ItemPredicate.Builder.item()
            .hasEnchantment(
                new EnchantmentPredicate(
                    Enchantments.FORTUNE,
                    MinMaxBounds.Ints.atLeast(1)
                )
            )
    );
}

// Use in loot table
this.add(Blocks.CUSTOM_ORE, block ->
    LootTable.lootTable()
        .withPool(LootPool.lootPool()
            .add(LootItem.lootTableItem(Items.DIAMOND)
                .when(hasFortuneEnchantment())
            )
        )
);
```

### Dynamic Recipe Generation

```java
// Generate recipes for all wood types
for (WoodType wood : WoodType.values()) {
    ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, wood.getStairs())
        .pattern("X  ")
        .pattern("XX ")
        .pattern("XXX")
        .define('X', wood.getPlanks())
        .unlockedBy("has_planks", has(wood.getPlanks()))
        .save(output);
}
```

### Conditional Generation

```java
@Override
protected void buildRecipes(RecipeOutput output) {
    // Only generate if feature flag enabled
    if (FeatureFlags.isEnabled(ModFeatureFlags.EXPERIMENTAL_BLOCKS)) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.EXPERIMENTAL_BLOCK)
            .pattern("XXX")
            .pattern("XXX")
            .pattern("XXX")
            .define('X', Items.DIAMOND)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(output);
    }
}
```

## Testing Generated Data

### 1. Generate Data
```bash
./gradlew runDatagen
```

### 2. Copy to Resources
```bash
cp -r generated/data src/main/resources/
```

### 3. Test In-Game
- Load the world
- Test recipes in crafting table
- Break blocks to test loot
- Check logs for errors

### 4. Validate Reports
Check `generated/reports/`:
- `blocks.json` - All registered blocks
- `items.json` - All registered items
- `commands.json` - Command structure

## Related Documentation

- [Data System](DATA-SYSTEM.md) - JSON format reference
- [Command System](COMMAND-SYSTEM.md) - Function generation
- [World Generation](WORLD-GENERATION-SYSTEM.md) - Worldgen datagen
- [Testing Guide](HOWTO-TESTING.md) - Testing generated data

## Summary

Data Generation is a powerful system that:

✅ **Reduces errors** - Type-safe Java instead of error-prone JSON  
✅ **Increases productivity** - Helper methods for common patterns  
✅ **Ensures consistency** - Shared code between related data  
✅ **Validates early** - Catch issues at generation time  
✅ **Simplifies maintenance** - Change once, regenerate all  

Start with simple providers (recipes, basic loot) and gradually expand to more complex data generation as you become comfortable with the system.
