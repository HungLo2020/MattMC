# Skyblock Achievement Loot System - Visual Examples

## What This Feature Does

When you play in a **Skyblock world** and unlock achievements/advancements, the game will optionally drop bonus loot to help you progress!

## Example Scenarios

### 🪨 Example 1: Mining Your First Stone

**Achievement**: "Stone Age" (minecraft:story/mine_stone)  
**What Happens**: After mining stone with a wooden pickaxe

```
You unlock: "Stone Age" advancement
✅ Skyblock world detected
📦 Loot table loaded: skyblock_advancement/story/mine_stone
🎁 You receive:
   - 8-16 Cobblestone (random amount)
   - 1 Stone Pickaxe
```

**Loot Table** (`story/mine_stone.json`):
```json
{
  "pools": [{
    "entries": [
      {
        "type": "minecraft:item",
        "name": "minecraft:cobblestone",
        "functions": [{
          "function": "minecraft:set_count",
          "count": { "type": "minecraft:uniform", "min": 8, "max": 16 }
        }]
      },
      {
        "type": "minecraft:item",
        "name": "minecraft:stone_pickaxe"
      }
    ],
    "rolls": 1.0
  }]
}
```

---

### ⚒️ Example 2: Getting Better Pickaxe

**Achievement**: "Getting an Upgrade" (minecraft:story/upgrade_tools)  
**What Happens**: After crafting a stone pickaxe

```
You unlock: "Getting an Upgrade" advancement  
✅ Skyblock world detected
📦 Loot table loaded: skyblock_advancement/story/upgrade_tools
🎁 You receive:
   - 3-6 Iron Ingots (random amount)
```

This helps you skip the tedious process of finding iron ore in skyblock!

---

### 💎 Example 3: Diamonds!

**Achievement**: "Diamonds!" (minecraft:story/mine_diamond)  
**What Happens**: After mining your first diamond

```
You unlock: "Diamonds!" advancement
✅ Skyblock world detected
📦 Loot table loaded: skyblock_advancement/story/mine_diamond
🎁 You receive:
   - 1-3 Diamonds OR 1 Emerald (weighted random)
   - 50% chance: 1 Golden Apple (bonus pool)
```

**Loot Table** (`story/mine_diamond.json`):
```json
{
  "pools": [
    {
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:diamond",
          "functions": [{
            "function": "minecraft:set_count",
            "count": { "type": "minecraft:uniform", "min": 1, "max": 3 }
          }]
        },
        {
          "type": "minecraft:item",
          "name": "minecraft:emerald",
          "weight": 1
        }
      ],
      "rolls": 1.0
    },
    {
      "entries": [{
        "type": "minecraft:item",
        "name": "minecraft:golden_apple"
      }],
      "rolls": 1.0,
      "conditions": [{
        "condition": "minecraft:random_chance",
        "chance": 0.5
      }]
    }
  ]
}
```

---

### 🔥 Example 4: Entering the Nether

**Achievement**: "We Need to Go Deeper" (minecraft:story/enter_the_nether)  
**What Happens**: When you enter the Nether for the first time

```
You unlock: "We Need to Go Deeper" advancement
✅ Skyblock world detected
📦 Loot table loaded: skyblock_advancement/story/enter_the_nether
🎁 You receive:
   - 4-8 Obsidian (in case you need to rebuild the portal)
   - 1 Flint and Steel (in case you lost yours)
```

Safety net for skyblock players!

---

### ⭐ Example 5: Obtaining Blaze Rods

**Achievement**: "Into Fire" (minecraft:nether/obtain_blaze_rod)  
**What Happens**: After getting your first blaze rod

```
You unlock: "Into Fire" advancement
✅ Skyblock world detected
📦 Loot table loaded: skyblock_advancement/nether/obtain_blaze_rod
🎁 You receive:
   - 2-4 Blaze Rods (extra to help with brewing)
   - 1-3 Magma Cream (bonus crafting material)
```

---

## Key Features

### ✅ Only in Skyblock Worlds
```java
// The system checks the world type:
if (chunkGenerator instanceof SkyblockChunkGenerator) {
    // Drop skyblock loot
}
```

Normal worlds are unaffected!

### ✅ Optional Loot Tables
If a loot table doesn't exist for an advancement, nothing happens. This lets you:
- Pick and choose which advancements get rewards
- Create custom progression paths
- Balance difficulty as needed

### ✅ Standard Loot Table Format
All loot tables use Minecraft's standard format, supporting:
- **Random counts**: `"min": 8, "max": 16`
- **Weighted items**: Choose between multiple items
- **Conditions**: Random chance, player stats, etc.
- **Multiple pools**: Separate guaranteed and bonus loot
- **Functions**: Enchantments, NBT data, etc.

### ✅ Smart Item Delivery
1. **First**: Try to add items to player inventory
2. **If full**: Drop items on the ground near player
3. **Sound**: Play pickup sound when successful
4. **Broadcast**: Update inventory UI

---

## Creating Your Own Loot Tables

### Step 1: Find the Advancement ID
Look in `data/minecraft/advancement/` for the advancement file.
Example: `story/mine_stone.json` → ID is `minecraft:story/mine_stone`

### Step 2: Create the Loot Table
Create a file at: `data/minecraft/loot_table/skyblock_advancement/<advancement_path>.json`

Example: For `minecraft:adventure/kill_a_mob`
```
data/minecraft/loot_table/skyblock_advancement/adventure/kill_a_mob.json
```

### Step 3: Write the Loot Table
Use the examples above as templates!

---

## Technical Details

### File Locations
```
src/main/resources/data/minecraft/
├── loot_table/
│   └── skyblock_advancement/
│       ├── story/
│       │   ├── mine_stone.json
│       │   ├── upgrade_tools.json
│       │   ├── mine_diamond.json
│       │   ├── enter_the_nether.json
│       │   └── enter_the_end.json
│       └── nether/
│           └── obtain_blaze_rod.json
```

### Code Implementation
**Location**: `net.minecraft.server.PlayerAdvancements.java`

**Method**: `grantSkyblockAdvancementLoot(AdvancementHolder)`

**Flow**:
1. Check if world is skyblock
2. Build loot table ID from advancement ID
3. Load loot table (returns empty if not found)
4. Generate items using loot context
5. Distribute items to player

---

## Example Use Cases

### Progression Acceleration
Give players resources they need to progress faster in skyblock challenges.

### Safety Nets
Provide backup items (like obsidian and flint & steel) in case players make mistakes.

### Alternative Paths
Allow players to obtain rare items through achievements instead of traditional methods.

### Balanced Rewards
Use random counts and conditions to keep rewards fair but helpful.

---

## Comparison: Normal World vs Skyblock World

### Normal World (e.g., "Default")
```
Player mines stone
→ "Stone Age" advancement unlocked
→ Regular advancement rewards only
→ No extra loot
```

### Skyblock World
```
Player mines stone
→ "Stone Age" advancement unlocked
→ Regular advancement rewards
→ 🎁 BONUS: 8-16 cobblestone + stone pickaxe
→ Sound effect plays
→ Items added to inventory
```

---

## Summary

This feature adds an **optional, configurable loot system** specifically for Skyblock worlds that:
- ✅ Helps players progress in challenging skyblock environments
- ✅ Uses familiar Minecraft loot table JSON format
- ✅ Doesn't affect normal gameplay in other world types
- ✅ Is completely optional - only works if loot tables exist
- ✅ Is extensible - add your own loot tables for any advancement!

Perfect for custom skyblock maps and modpacks!
