# Data Generation Guide

## Overview

MattMC includes Minecraft's built-in data generation system that can automatically create JSON resource files for recipes, loot tables, tags, advancements, models, and more. This eliminates the need to manually write thousands of JSON files.

## Quick Start

### Generate All Data

To generate both server-side and client-side data:

```bash
./gradlew runDatagenAll
```

### Generate Server-Side Data Only

Server-side data includes recipes, loot tables, tags, advancements, and world generation data:

```bash
./gradlew runDatagen
```

### Generate Client-Side Data Only

Client-side data includes block/item models, blockstates, and atlases:

```bash
./gradlew runDatagenClient
```

## Output

Generated files are written to the `generated/` directory:

```
generated/
├── data/                     # Server-side data
│   └── minecraft/
│       ├── advancement/      # Achievement definitions
│       ├── loot_table/       # Loot tables
│       │   ├── blocks/       # Block drops
│       │   ├── entities/     # Mob drops
│       │   ├── chests/       # Chest loot
│       │   └── ...
│       ├── recipe/           # Crafting recipes (empty in vanilla, datapacks have them)
│       ├── tags/             # Tag definitions
│       │   ├── block/
│       │   ├── item/
│       │   ├── entity_type/
│       │   └── ...
│       ├── worldgen/         # World generation data
│       │   ├── biome/
│       │   ├── configured_feature/
│       │   └── ...
│       └── datapacks/        # Built-in datapacks
│           ├── trade_rebalance/
│           ├── redstone_experiments/
│           └── minecart_improvements/
├── assets/                   # Client-side data
│   └── minecraft/
│       ├── blockstates/      # Block state definitions
│       ├── models/           # Block and item models
│       │   ├── block/
│       │   └── item/
│       ├── items/            # Item model definitions
│       ├── equipment/        # Equipment asset definitions
│       ├── atlases/          # Texture atlas definitions
│       └── waypoint_style/   # Waypoint styles
└── reports/                  # Debug/validation reports
    └── biome_parameters/     # Biome parameter reports

```

**Statistics:**
- **~5,950 JSON files** generated
- **~26 MB** total size
- Includes all vanilla Minecraft data

## What Gets Generated

### Server-Side Data (`runDatagen`)

1. **Advancements** - All vanilla achievements and progression
2. **Loot Tables** - Block drops, mob drops, chest loot, fishing, archaeology
3. **Tags** - Block tags, item tags, entity tags, biome tags, etc.
4. **Recipes** - Organized in datapacks (trade_rebalance, etc.)
5. **World Generation** - Biomes, features, structures, noise parameters
6. **Registry Data** - Dimension types, damage types, chat types, etc.
7. **Built-in Datapacks** - Trade rebalance, redstone experiments, minecart improvements

### Client-Side Data (`runDatagenClient`)

1. **Block States** - Visual state definitions for all blocks
2. **Models** - 3D model definitions for blocks and items
3. **Item Definitions** - Item model mappings
4. **Equipment Assets** - Armor and equipment visual assets
5. **Atlases** - Texture atlas definitions
6. **Waypoint Styles** - Map marker styles

### Reports (`--reports` flag)

1. **Biome Parameters** - Biome spawn and generation details
2. **Item List** - Complete item registry
3. **Block List** - Complete block registry
4. **Command Syntax** - Command structure documentation
5. **Registry Dump** - All registry contents
6. **Packet Report** - Network packet definitions
7. **Datapack Structure** - Datapack organization

## Using Generated Data

### Option 1: Copy to Resources

Copy specific files from `generated/` to `src/main/resources/`:

```bash
# Copy all generated data to resources
cp -r generated/data/* src/main/resources/data/
cp -r generated/assets/* src/main/resources/assets/

# Or copy selectively
cp -r generated/data/minecraft/loot_table src/main/resources/data/minecraft/
```

### Option 2: Reference for Modding

Use the generated files as a reference when creating custom content:
- Study recipe syntax
- Learn loot table structure
- Understand tag organization
- See model definitions

### Option 3: Build Custom Datapacks

Package generated data into datapacks:

```bash
# Create a datapack
mkdir -p my_datapack/data/mymod
cp -r generated/data/minecraft/recipe my_datapack/data/mymod/
# Add pack.mcmeta and customize
```

## Advanced Usage

### Custom Output Directory

Modify the `args` in `build.gradle` to change output location:

```groovy
args = [
    '--output', file('custom_output').absolutePath,
    '--server',
    '--all'
]
```

### Selective Generation

Edit the task arguments to generate specific categories:

```bash
# Only server data, no reports
./gradlew runDatagen -Pargs="--output,generated,--server"

# Only development tools
./gradlew runDatagen -Pargs="--output,generated,--dev"
```

### Regenerating After Changes

If you modify Minecraft's data providers in `net/minecraft/data/`, regenerate:

```bash
# Clean old generated data
rm -rf generated/

# Rebuild and regenerate
./gradlew clean classes runDatagenAll
```

## Creating Custom Providers

To add your own custom data generation:

1. Create a provider class extending the appropriate base:
   - `RecipeProvider` for recipes
   - `LootTableProvider` for loot tables
   - `TagsProvider<T>` for tags
   - `AdvancementProvider` for advancements

2. Register it in `net.minecraft.data.Main.addServerProviders()` or `net.minecraft.client.data.Main.addClientProviders()`

3. Regenerate data

Example:

```java
public class MyRecipeProvider extends RecipeProvider {
    public MyRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, provider);
    }
    
    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Items.DIAMOND)
            .pattern("CCC")
            .pattern("CCC")
            .pattern("CCC")
            .define('C', Items.COAL)
            .unlockedBy("has_coal", has(Items.COAL))
            .save(output, "diamond_from_coal");
    }
}
```

## Troubleshooting

### Task Fails with ClassNotFoundException

Ensure classes are compiled first:
```bash
./gradlew classes runDatagen
```

### Out of Memory

Increase heap size in `build.gradle`:
```groovy
jvmArgs = [
    '-Xmx8G',  // Increase from 4G
    '-Xms4G',  // Increase from 2G
    '-XX:+UseG1GC'
]
```

### Generated Files Are Empty

Check the task output for errors. Ensure:
- Bootstrap completed successfully
- No provider exceptions
- Cache was written successfully

### Want to Regenerate Everything

Clean and regenerate:
```bash
rm -rf generated/
./gradlew clean runDatagenAll
```

## Performance

Data generation is optimized:
- **Server data**: ~3 seconds, 6,300+ files
- **Client data**: ~1 second, 5,900+ files
- **Total time**: ~4-5 seconds for everything
- **Incremental**: Only modified files are rewritten

## Integration with Build

To automatically generate data on build, add to `build.gradle`:

```groovy
build.dependsOn runDatagenAll
```

Or run manually before packaging:

```bash
./gradlew runDatagenAll build
```

## Related Documentation

- [Data System Overview](DATA-SYSTEM.md) - Understanding data packs and JSON formats
- Vanilla providers in `net/minecraft/data/` - Reference implementations
- Vanilla client providers in `net/minecraft/client/data/` - Model generation

## Command Reference

| Task | Description | Output |
|------|-------------|--------|
| `runDatagen` | Server-side generation | `generated/data/` |
| `runDatagenClient` | Client-side generation | `generated/assets/` |
| `runDatagenAll` | Both server + client | `generated/` |

All tasks:
- Use bundled JDK automatically
- Support `--no-daemon` for CI/CD
- Generate reports by default
- Cache unchanged files
- Run in ~5 seconds total
