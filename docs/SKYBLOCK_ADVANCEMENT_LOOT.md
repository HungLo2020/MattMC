# Skyblock Achievement Loot System

## Overview

This feature allows you to configure custom loot drops when players unlock achievements/advancements while playing in a Skyblock world. The system automatically detects when a player is in a Skyblock world (one using the `SkyblockChunkGenerator`) and checks for optional loot tables to drop items.

## How It Works

1. **World Detection**: When a player completes an advancement, the system checks if they are in a Skyblock world by examining the chunk generator.

2. **Loot Table Loading**: If in Skyblock, the system attempts to load a loot table from:
   ```
   data/minecraft/loot_table/skyblock_advancement/<advancement_id>
   ```
   
   For example, if a player completes `minecraft:story/mine_stone`, it looks for:
   ```
   data/minecraft/loot_table/skyblock_advancement/story/mine_stone.json
   ```

3. **Item Distribution**: If a loot table exists:
   - Items are generated using the loot table's random rolls and conditions
   - Items are added to the player's inventory if there's space
   - If the inventory is full, items are dropped on the ground near the player
   - A pickup sound is played when items are successfully added

4. **Optional Behavior**: If no loot table exists for an advancement, nothing happens - this allows you to selectively reward only certain advancements.

## Creating Loot Tables

Loot tables use Minecraft's standard loot table JSON format. Here's an example:

### Example: Stone Breaking Advancement

File: `data/minecraft/loot_table/skyblock_advancement/story/mine_stone.json`

```json
{
  "type": "minecraft:advancement_reward",
  "pools": [
    {
      "bonus_rolls": 0.0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:cobblestone",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": {
                "type": "minecraft:uniform",
                "min": 8,
                "max": 16
              }
            }
          ]
        },
        {
          "type": "minecraft:item",
          "name": "minecraft:stone_pickaxe"
        }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "minecraft:skyblock_advancement/story/mine_stone"
}
```

This gives the player:
- 8-16 cobblestone (random amount)
- 1 stone pickaxe

## Loot Table Features

You can use all standard Minecraft loot table features:

### Multiple Pools
```json
{
  "type": "minecraft:advancement_reward",
  "pools": [
    {
      "entries": [...],
      "rolls": 1.0
    },
    {
      "entries": [...],
      "rolls": 2.0
    }
  ]
}
```

### Conditional Drops
```json
{
  "entries": [...],
  "conditions": [
    {
      "condition": "minecraft:random_chance",
      "chance": 0.5
    }
  ]
}
```

### Weighted Items
```json
{
  "entries": [
    {
      "type": "minecraft:item",
      "name": "minecraft:diamond",
      "weight": 1
    },
    {
      "type": "minecraft:item",
      "name": "minecraft:iron_ingot",
      "weight": 5
    }
  ]
}
```

## Example Loot Tables Included

This implementation includes several example loot tables:

- **story/mine_stone**: Cobblestone and stone pickaxe for first stone mining
- **story/upgrade_tools**: Iron ingots for upgrading to iron tools
- **story/mine_diamond**: Diamonds and possibly a golden apple
- **story/enter_the_nether**: Obsidian and flint & steel for nether access
- **story/enter_the_end**: Ender pearls and blaze powder for end portal
- **nether/obtain_blaze_rod**: Extra blaze rods and magma cream

## Adding Your Own Loot Tables

To add loot tables for other advancements:

1. Create a JSON file in the appropriate directory:
   ```
   src/main/resources/data/minecraft/loot_table/skyblock_advancement/<category>/<advancement_name>.json
   ```

2. Use the advancement's ID path. For example:
   - `minecraft:adventure/kill_a_mob` → `skyblock_advancement/adventure/kill_a_mob.json`
   - `minecraft:husbandry/plant_seed` → `skyblock_advancement/husbandry/plant_seed.json`

3. Follow the loot table format shown in the examples above.

## Technical Details

### Implementation Location
- **Main Logic**: `net.minecraft.server.PlayerAdvancements.grantSkyblockAdvancementLoot()`
- **Invocation**: Called after regular advancement rewards are granted
- **World Check**: Uses `instanceof SkyblockChunkGenerator`

### Loot Context
The loot tables use the `ADVANCEMENT_REWARD` loot context parameter set, which provides:
- `THIS_ENTITY`: The player who earned the advancement
- `ORIGIN`: The player's current position

This allows you to use entity-based conditions if desired.

## Design Philosophy

This system is designed to be:
- **Optional**: Only works in Skyblock worlds
- **Non-intrusive**: Doesn't affect normal gameplay or other world types
- **Flexible**: Uses standard loot table format for maximum compatibility
- **Safe**: Gracefully handles missing loot tables without errors

## Future Enhancements

Possible future improvements could include:
- Configuration option to enable/disable per world
- Custom messages when loot is dropped
- Multiplayer-friendly duplicate prevention
- Alternative loot tables based on player progression
