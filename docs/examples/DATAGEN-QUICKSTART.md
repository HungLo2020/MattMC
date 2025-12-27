# Datagen Quick Start Example

This guide walks you through creating your first custom block loot table using the datagen system.

## Goal

Create a custom ore block that:
- Drops diamonds with Fortune enchantment support
- Has a chance to drop emeralds
- Survives explosions

## Step 1: Create a Custom Loot Provider

Create a new Java file for your custom loot tables:

**File**: `src/main/java/net/minecraft/data/loot/packs/CustomBlockLoot.java`

```java
package net.minecraft.data.loot.packs;

import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.core.registries.Registries;

public class CustomBlockLoot extends BlockLootSubProvider {
    
    public CustomBlockLoot(HolderLookup.Provider provider) {
        // No explosion-resistant blocks for now
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
    }
    
    @Override
    public void generate() {
        // Example 1: Simple block - drops itself
        this.dropSelf(Blocks.COBBLESTONE);
        
        // Example 2: Custom ore with Fortune support
        this.add(Blocks.DIAMOND_ORE, block ->
            LootTable.lootTable()
                .withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(Items.DIAMOND)
                        // Drop 1-3 diamonds
                        .apply(SetItemCountFunction.setCount(
                            UniformGenerator.between(1.0F, 3.0F)
                        ))
                        // Apply Fortune enchantment bonus
                        .apply(ApplyBonusCount.addOreBonusCount(
                            this.registries.lookupOrThrow(Registries.ENCHANTMENT)
                        ))
                    )
                )
                // 20% chance to also drop an emerald
                .withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(Items.EMERALD))
                    .when(LootItemRandomChanceCondition.randomChance(0.2F))
                )
        );
        
        // Example 3: Ore drop helper (simpler version)
        this.add(Blocks.COAL_ORE, 
            block -> createOreDrop(block, Items.COAL));
        
        // Example 4: Silk touch required for grass block
        this.add(Blocks.GRASS_BLOCK,
            block -> createSingleItemTableWithSilkTouch(block, Items.DIRT));
    }
    
    @Override
    protected Iterable<Block> getKnownBlocks() {
        // Return all blocks that should have loot tables
        // For this example, we're just using vanilla blocks
        return Set.of(
            Blocks.COBBLESTONE,
            Blocks.DIAMOND_ORE,
            Blocks.COAL_ORE,
            Blocks.GRASS_BLOCK
        );
    }
}
```

## Step 2: Register Your Provider

Add your custom provider to the datagen system.

**File**: `src/main/java/net/minecraft/data/Main.java`

Find the `addServerProviders` method and add your provider:

```java
public static void addServerProviders(
    DataGenerator dataGenerator,
    Collection<Path> collection,
    boolean bl,
    boolean bl2,
    boolean bl3
) {
    // ... existing code ...
    
    CompletableFuture<HolderLookup.Provider> completableFuture = 
        CompletableFuture.supplyAsync(VanillaRegistries::createLookup, Util.backgroundExecutor());
    
    DataGenerator.PackGenerator packGenerator2 = dataGenerator.getVanillaPack(bl);
    
    // Add your custom loot provider
    packGenerator2.addProvider(bindRegistries(CustomBlockLoot::new, completableFuture));
    
    // ... rest of the code ...
}
```

## Step 3: Create Gradle Task for Datagen

Add this task to your `build.gradle`:

```groovy
tasks.register('runDatagen', JavaExec) {
    group = 'minecraft'
    description = 'Runs the Minecraft data generator'
    
    dependsOn 'classes'
    
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'net.minecraft.data.Main'
    
    // Arguments for datagen
    args = [
        '--all',                         // Generate all data
        '--output', 'generated',         // Output directory
        '--server'                       // Include server generators
    ]
    
    workingDir = file('.')
    
    doFirst {
        println "🔧 Running data generation..."
        println "📂 Output directory: ${file('generated').absolutePath}"
    }
    
    doLast {
        println "✅ Data generation complete!"
        println "📁 Check 'generated/data/minecraft/loot_table/blocks/' for your loot tables"
    }
}
```

## Step 4: Run the Datagen

Execute the Gradle task:

```bash
./gradlew runDatagen
```

Expected output:
```
> Task :runDatagen
🔧 Running data generation...
📂 Output directory: /path/to/MattMC/generated
Starting provider: vanilla/Loot Tables
vanilla/Loot Tables finished after 1234 ms
All providers took: 2345 ms
✅ Data generation complete!
📁 Check 'generated/data/minecraft/loot_table/blocks/' for your loot tables
```

## Step 5: View Generated Files

The generated loot tables will be in:
```
generated/
└── data/
    └── minecraft/
        └── loot_table/
            └── blocks/
                ├── diamond_ore.json
                ├── coal_ore.json
                ├── grass_block.json
                └── cobblestone.json
```

**Example Output** (`generated/data/minecraft/loot_table/blocks/diamond_ore.json`):

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:diamond",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": {
                "type": "minecraft:uniform",
                "min": 1.0,
                "max": 3.0
              }
            },
            {
              "function": "minecraft:apply_bonus",
              "enchantment": "minecraft:fortune",
              "formula": "minecraft:ore_drops"
            }
          ]
        }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:emerald"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:random_chance",
          "chance": 0.2
        }
      ]
    }
  ],
  "random_sequence": "minecraft:blocks/diamond_ore"
}
```

## Step 6: Copy to Resources

Copy the generated files to your resources directory:

```bash
# Copy all generated loot tables
cp -r generated/data/minecraft/loot_table src/main/resources/data/minecraft/

# Or copy specific files
cp generated/data/minecraft/loot_table/blocks/diamond_ore.json \
   src/main/resources/data/minecraft/loot_table/blocks/
```

## Step 7: Test In-Game

1. Build the project:
   ```bash
   ./gradlew build
   ```

2. Run the client:
   ```bash
   ./gradlew runClient
   ```

3. Test your loot table:
   - Create a new world in Creative mode
   - Place a diamond ore block
   - Switch to Survival mode
   - Mine the diamond ore with different pickaxes:
     - Stone pickaxe (no Fortune): Should drop 1-3 diamonds
     - Pickaxe with Fortune III: Should drop more diamonds
     - Check for the 20% emerald drop

## Common Helper Methods

Here are commonly used helper methods from `BlockLootSubProvider`:

```java
// Block drops itself
this.dropSelf(Blocks.STONE);

// Ore with Fortune support
this.add(Blocks.DIAMOND_ORE, 
    block -> createOreDrop(block, Items.DIAMOND));

// Silk touch required
this.add(Blocks.GRASS_BLOCK,
    block -> createSingleItemTableWithSilkTouch(block, Items.DIRT));

// Slab (handles double slabs correctly)
this.add(Blocks.OAK_SLAB,
    BlockLootSubProvider::createSlabItemTable);

// Door (handles upper/lower halves)
this.add(Blocks.OAK_DOOR,
    BlockLootSubProvider::createDoorTable);

// Leaves with sapling and stick drops
this.add(Blocks.OAK_LEAVES, block ->
    createLeavesDrops(block, Blocks.OAK_SAPLING, NORMAL_LEAVES_SAPLING_CHANCES));

// Crop with age-based drops
this.add(Blocks.WHEAT, block ->
    createCropDrops(
        block,
        Items.WHEAT,
        Items.WHEAT_SEEDS,
        LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
            .setProperties(StatePropertiesPredicate.Builder.properties()
                .hasProperty(CropBlock.AGE, 7)
            )
    ));
```

## Next Steps

Now that you've created your first loot table, try:

1. **Add recipes** - Create a `RecipeProvider` to generate crafting recipes
2. **Add tags** - Create tag providers to group blocks and items
3. **Add advancements** - Create advancement providers for achievements
4. **Explore complex loot** - Add conditions, functions, and multiple pools

See the [DATAGEN-GUIDE.md](../DATAGEN-GUIDE.md) for comprehensive documentation on all datagen features.

## Troubleshooting

### "Class not found" error
Make sure you've compiled the code first:
```bash
./gradlew classes
```

### "Missing loot table" warning
Check that `getKnownBlocks()` returns all blocks that should have loot tables.

### Generated files look wrong
Check the logs for validation errors:
```bash
./gradlew runDatagen --info
```

### Loot doesn't work in-game
Ensure you've copied the files to `src/main/resources/data/` and rebuilt:
```bash
cp -r generated/data src/main/resources/
./gradlew build
```

## Summary

You've learned how to:

✅ Create a custom `BlockLootSubProvider`  
✅ Register it with the datagen system  
✅ Run datagen to generate JSON files  
✅ Understand the generated loot table format  
✅ Test your loot tables in-game  

The datagen system is powerful and extensible - this is just the beginning!
