# Data System

## Overview

Minecraft's data system provides a powerful, JSON-based framework for defining game content without modifying code. Data packs allow customization of recipes, loot tables, structures, world generation, advancements, and more through data files.

## Architecture

```
┌─────────────────────────────────────────┐
│         Resource Pack System            │
│  (Assets: textures, models, sounds)     │
└─────────────────────────────────────────┘
               ┌─────┴─────┐
               ▼           ▼
         [Client]      [Server]
               │           │
               │     ┌─────▼─────────────────┐
               │     │   Data Pack System    │
               │     │  (Data: recipes,      │
               │     │   loot, worldgen)     │
               │     └─────┬─────────────────┘
               │           │
               └───────────┴─ Merged at runtime
```

## Core Components

### 1. Data Pack Structure

**Location**: `data/` directory in pack

```
datapack/
├── pack.mcmeta              # Pack metadata
└── data/
    └── <namespace>/         # Usually "minecraft" or custom
        ├── advancement/     # Advancements
        ├── damage_type/     # Damage type definitions
        ├── dimension/       # Custom dimensions
        ├── dimension_type/  # Dimension settings
        ├── function/        # Command functions (.mcfunction)
        ├── item_modifier/   # Item modification rules
        ├── loot_table/      # Loot tables
        ├── predicate/       # Conditions/predicates
        ├── recipe/          # Crafting recipes
        ├── structure/       # Structure NBT files
        ├── tags/            # Tag definitions
        │   ├── block/       # Block tags
        │   ├── item/        # Item tags
        │   ├── entity_type/ # Entity tags
        │   ├── fluid/       # Fluid tags
        │   └── ...
        └── worldgen/        # World generation
            ├── biome/       # Biome definitions
            ├── configured_feature/ # Features
            ├── placed_feature/     # Feature placement
            ├── structure/          # Structure configs
            ├── structure_set/      # Structure sets
            ├── noise/              # Noise parameters
            ├── noise_settings/     # Generation settings
            ├── density_function/   # Custom density
            ├── flat_level_generator_preset/ # Superflat
            ├── world_preset/       # World presets
            ├── template_pool/      # Jigsaw pools
            └── processor_list/     # Structure processors
```

**pack.mcmeta Format**:
```json
{
  "pack": {
    "pack_format": 48,
    "description": "My Custom Data Pack"
  }
}
```

**Pack Formats**:
- Format 48: Minecraft 1.21.x
- Format changes with major versions
- Incompatible packs won't load

### 2. Recipe System

**Location**: `data/<namespace>/recipe/`

Defines crafting, smelting, and other recipes.

#### Recipe Types

**Shaped Crafting**:
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "###",
    "# #",
    "###"
  ],
  "key": {
    "#": {
      "item": "minecraft:stick"
    }
  },
  "result": {
    "id": "minecraft:chest",
    "count": 1
  }
}
```

**Shapeless Crafting**:
```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "minecraft:gunpowder" },
    { "item": "minecraft:paper" }
  ],
  "result": {
    "id": "minecraft:firework_rocket",
    "count": 3
  }
}
```

**Smelting**:
```json
{
  "type": "minecraft:smelting",
  "ingredient": { "item": "minecraft:iron_ore" },
  "result": {
    "id": "minecraft:iron_ingot"
  },
  "experience": 0.7,
  "cookingtime": 200
}
```

**Other Recipe Types**:
- `minecraft:blasting`: Blast furnace
- `minecraft:smoking`: Smoker
- `minecraft:campfire_cooking`: Campfire
- `minecraft:stonecutting`: Stonecutter
- `minecraft:smithing_transform`: Smithing table (netherite)
- `minecraft:smithing_trim`: Armor trims
- `minecraft:crafting_special_*`: Special recipes (fireworks, books, etc.)

**Ingredient Types**:
- Single item: `{ "item": "minecraft:stick" }`
- Tag: `{ "tag": "minecraft:logs" }`
- Multiple options: `[ { "item": "..." }, { "item": "..." } ]`

### 3. Loot Tables

**Location**: `data/<namespace>/loot_table/`

Define item drops from blocks, entities, chests, fishing, etc.

#### Loot Table Structure

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
                "min": 1,
                "max": 3
              }
            },
            {
              "function": "minecraft:apply_bonus",
              "enchantment": "minecraft:fortune",
              "formula": "minecraft:ore_drops"
            }
          ],
          "conditions": [
            {
              "condition": "minecraft:survives_explosion"
            }
          ]
        }
      ]
    }
  ]
}
```

#### Loot Table Types

- `minecraft:block`: Block drops
- `minecraft:entity`: Mob drops
- `minecraft:chest`: Chest contents
- `minecraft:fishing`: Fishing loot
- `minecraft:archaeology`: Brush finds
- `minecraft:gift`: Cat/villager gifts
- `minecraft:barter`: Piglin bartering
- `minecraft:advancement_reward`: Advancement rewards
- `minecraft:generic`: Other uses

#### Entry Types

- `minecraft:item`: Single item
- `minecraft:tag`: Random item from tag
- `minecraft:loot_table`: Reference another table
- `minecraft:dynamic`: Dynamic content (block entity content)
- `minecraft:alternatives`: First matching entry
- `minecraft:sequence`: All entries in order
- `minecraft:group`: Group with shared weight

#### Functions

Modify dropped items:

- `set_count`: Set item count (fixed or range)
- `set_nbt`: Add NBT data
- `set_damage`: Set item damage
- `set_name`: Custom name
- `set_lore`: Lore text
- `enchant_randomly`: Random enchantment
- `enchant_with_levels`: Enchant with level range
- `apply_bonus`: Fortune/Looting bonus
- `furnace_smelt`: Smelt if on fire
- `explosion_decay`: Reduce count from explosion
- `copy_nbt`: Copy NBT from source
- `copy_name`: Copy block entity name
- `set_contents`: Set container contents
- `limit_count`: Clamp count
- `set_attributes`: Add attribute modifiers
- `set_potion`: Set potion type
- `fill_player_head`: Set skull owner

#### Conditions

Control when entries/functions apply:

- `survives_explosion`: Not destroyed by explosion
- `random_chance`: Probability check
- `random_chance_with_enchanted_bonus`: Enchantment-modified chance
- `killed_by_player`: Player kill
- `entity_properties`: Entity property check
- `block_state_property`: Block state check
- `match_tool`: Tool requirement
- `inverted`: Invert condition
- `any_of`, `all_of`: Combine conditions
- `weather_check`: Weather condition
- `damage_source_properties`: Damage source check

### 4. Advancements

**Location**: `data/<namespace>/advancement/`

Progression system with achievements and triggers.

#### Advancement Structure

```json
{
  "display": {
    "icon": {
      "id": "minecraft:diamond"
    },
    "title": "Diamonds!",
    "description": "Acquire diamonds",
    "frame": "task",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": false
  },
  "parent": "minecraft:story/iron_tools",
  "criteria": {
    "diamond": {
      "trigger": "minecraft:inventory_changed",
      "conditions": {
        "items": [
          {
            "items": ["minecraft:diamond"]
          }
        ]
      }
    }
  },
  "rewards": {
    "experience": 100,
    "loot": ["minecraft:chests/simple_dungeon"],
    "recipes": ["minecraft:diamond_sword"],
    "function": "namespace:reward_function"
  }
}
```

#### Trigger Types

- `impossible`: Manual grant only
- `inventory_changed`: Item in inventory
- `item_used_on_block`: Right-click block
- `player_hurt_entity`: Damage entity
- `entity_hurt_player`: Take damage
- `player_killed_entity`: Kill entity
- `killed_by_crossbow`: Crossbow piercing
- `location`: Enter location
- `slept_in_bed`: Sleep
- `bred_animals`: Breed mobs
- `placed_block`: Place block
- `consume_item`: Eat/drink
- `effects_changed`: Potion effect change
- `used_ender_eye`: Use ender eye
- `used_totem`: Use totem
- `levitation`: Levitate
- `changed_dimension`: Enter dimension
- `tick`: Every tick (expensive!)
- And many more...

#### Frame Types

- `task`: Normal advancement (square icon)
- `goal`: Important advancement (rounded icon)
- `challenge`: Difficult advancement (fancy border)

### 5. Tags

**Location**: `data/<namespace>/tags/`

Group items, blocks, entities, etc. for flexible reference.

#### Tag Types

**Block Tags** (`tags/block/`):
```json
{
  "values": [
    "minecraft:oak_log",
    "minecraft:birch_log",
    "minecraft:spruce_log",
    "#minecraft:dark_oak_logs"
  ]
}
```

**Item Tags** (`tags/item/`):
```json
{
  "values": [
    "minecraft:diamond_sword",
    "minecraft:iron_sword",
    "minecraft:stone_sword"
  ]
}
```

**Entity Type Tags** (`tags/entity_type/`):
```json
{
  "values": [
    "minecraft:zombie",
    "minecraft:zombie_villager",
    "minecraft:husk"
  ]
}
```

**Other Tag Types**:
- `fluid`: Fluid tags
- `game_event`: Game event tags
- `biome`: Biome tags
- `damage_type`: Damage type tags
- `enchantment`: Enchantment tags
- `function`: Function tags (run multiple)

#### Tag Features

**Tag References**: `#namespace:tag_name`
- Include other tags
- Recursive resolution
- Override with `replace: true`

**Common Uses**:
- Recipe ingredients
- Loot conditions
- Block/item properties
- Mob spawning
- World generation

### 6. Predicates

**Location**: `data/<namespace>/predicate/`

Reusable condition definitions.

```json
{
  "condition": "minecraft:entity_properties",
  "entity": "this",
  "predicate": {
    "type": "minecraft:zombie",
    "flags": {
      "is_on_fire": true
    }
  }
}
```

**Uses**:
- Loot table conditions
- Advancement criteria
- Execute command conditions

### 7. Item Modifiers

**Location**: `data/<namespace>/item_modifier/`

Reusable item modification rules.

```json
{
  "function": "minecraft:set_count",
  "count": {
    "min": 1,
    "max": 5
  }
}
```

**Uses**:
- Loot table functions
- Item command modifications

### 8. Functions

**Location**: `data/<namespace>/function/`

Command scripts (.mcfunction files).

**Format**: One command per line
```mcfunction
# Give starter kit
give @s minecraft:stone_sword
give @s minecraft:bread 16
effect give @s minecraft:resistance 60 1
tellraw @s {"text":"Starter kit given!","color":"green"}
```

**Function Tags**:
- `minecraft:load`: Run on datapack load
- `minecraft:tick`: Run every tick

**Example** (`data/minecraft/tags/function/tick.json`):
```json
{
  "values": [
    "namespace:my_tick_function"
  ]
}
```

### 9. World Generation Data

**Location**: `data/<namespace>/worldgen/`

Define custom world generation elements.

#### Biome Definition

```json
{
  "temperature": 0.8,
  "downfall": 0.4,
  "effects": {
    "sky_color": 7907327,
    "fog_color": 12638463,
    "water_color": 4159204,
    "water_fog_color": 329011,
    "grass_color": 9470285,
    "foliage_color": 10387789,
    "mood_sound": {
      "sound": "minecraft:ambient.cave",
      "tick_delay": 6000,
      "block_search_extent": 8,
      "offset": 2.0
    }
  },
  "spawners": {
    "monster": [...],
    "creature": [...],
    "ambient": [...],
    "water_creature": [...],
    "water_ambient": [...],
    "misc": [...]
  },
  "spawn_costs": {},
  "carvers": {
    "air": ["minecraft:cave", "minecraft:canyon"]
  },
  "features": [
    [],
    ["minecraft:lake_lava_underground"],
    ["minecraft:ore_dirt", "minecraft:ore_gravel"],
    ...
  ]
}
```

#### Configured Feature

```json
{
  "type": "minecraft:tree",
  "config": {
    "trunk_provider": {
      "type": "minecraft:simple_state_provider",
      "state": {
        "Name": "minecraft:oak_log"
      }
    },
    "foliage_provider": {
      "type": "minecraft:simple_state_provider",
      "state": {
        "Name": "minecraft:oak_leaves"
      }
    },
    "trunk_placer": {
      "type": "minecraft:straight_trunk_placer",
      "base_height": 4,
      "height_rand_a": 2,
      "height_rand_b": 0
    },
    "foliage_placer": {
      "type": "minecraft:blob_foliage_placer",
      "radius": 2,
      "offset": 0,
      "height": 3
    }
  }
}
```

#### Placed Feature

```json
{
  "feature": "minecraft:oak_tree",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 10
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:heightmap",
      "heightmap": "WORLD_SURFACE"
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
```

### 10. Data Generation

**Location**: `net.minecraft.data`

Java code to generate data files programmatically.

**Providers**:
- `RecipeProvider`: Generate recipes
- `LootTableProvider`: Generate loot tables
- `BlockTagsProvider`: Generate block tags
- `ItemTagsProvider`: Generate item tags
- `AdvancementProvider`: Generate advancements
- `ModelProvider`: Generate block/item models

**Usage**:
```java
public class MyRecipeProvider extends RecipeProvider {
    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Items.DIAMOND_PICKAXE)
            .pattern("XXX")
            .pattern(" S ")
            .pattern(" S ")
            .define('X', Items.DIAMOND)
            .define('S', Items.STICK)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(output);
    }
}
```

## Data Pack Loading

**Priority Order**:
1. Default Minecraft data (lowest)
2. World data packs
3. Global data packs
4. Built-in data packs (highest)

**Merging**:
- Same file path → override
- Tags with `replace: false` → merge
- Tags with `replace: true` → replace

**Reloading**:
- `/reload` command
- Reloads all data packs
- Applies changes without restart

## Performance Considerations

**Optimization**:
1. Minimize complex conditions
2. Use tags instead of listing items
3. Avoid tick functions when possible
4. Cache parsed data
5. Lazy load unused data

**Common Issues**:
- Circular tag references
- Missing parent advancements
- Invalid JSON syntax
- Missing recipe unlock criteria

## Key Files

- `RecipeManager.java`: Recipe loading and caching
- `LootDataManager.java`: Loot table management
- `ServerAdvancementManager.java`: Advancement tracking
- `TagManager.java`: Tag system
- `DataPackConfig.java`: Data pack configuration
- `DataProvider.java`: Data generation base

## Common Patterns

**Custom Ore Drop**:
```json
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "entries": [{
      "type": "minecraft:item",
      "name": "minecraft:diamond",
      "functions": [
        {
          "function": "minecraft:apply_bonus",
          "enchantment": "minecraft:fortune",
          "formula": "minecraft:ore_drops"
        },
        {
          "function": "minecraft:explosion_decay"
        }
      ]
    }]
  }]
}
```

**Mob Spawn Egg Recipe**:
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "###",
    "#E#",
    "###"
  ],
  "key": {
    "#": { "item": "minecraft:egg" },
    "E": { "item": "minecraft:emerald" }
  },
  "result": {
    "id": "minecraft:zombie_spawn_egg"
  }
}
```

## Related Systems

- [Command System](COMMAND-SYSTEM.md) - Function files
- [World Generation](WORLD-GENERATION-SYSTEM.md) - Worldgen data
- [Server System](SERVER-SYSTEM.md) - Data pack loading
- [Inventory System](INVENTORY-SYSTEM.md) - Recipes and loot
