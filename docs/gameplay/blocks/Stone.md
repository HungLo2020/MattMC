# Stone

Stone (`minecraft:stone`), Cobblestone (`minecraft:cobblestone`) and their brick, mossy and smooth forms provide a connected set of building materials. Mine Stone for Cobblestone, smelt it back to Stone, and choose a Crafting Table or Stonecutter for the finish and shape you need. **Stone and Smooth Stone are different ingredients.** The darker construction family has its own [Deepslate guide](Deepslate.md).

## Obtaining

The normal Overworld uses Stone as its base terrain block; caves, ores, surface materials and the deep Deepslate layer interrupt it. Mine exposed Stone or reach it beneath the surface. This is the ordinary terrain route, not a promise about every custom world preset. [Normal preset][presets] · [Terrain settings][overworld] · [Generator][terrain]

Use an **unbroken pickaxe**, including a Wooden Pickaxe, to collect the ordinary construction blocks covered here. They require a correct tool for drops, are in the pickaxe mining tag, and have no Stone-, Iron- or Diamond-tier restriction. Breaking one by hand does not collect it. See [Mining](../mechanics/Mining.md) for the shared tool rules. [Registration][blocks] · [Pickaxe tag][pickaxe] · [Tier tags][stone-tier] [iron-tier][] [diamond-tier][] · [Wood rules][wood-denials] · [Tool checks][tool-material] [player-tool][] [broken-tool][] · [Server mining dispatch][break-dispatch]

### Mining and drops

| Placed material | Ordinary correct-tool drop | With Silk Touch |
| --- | --- | --- |
| Stone | 1 Cobblestone | 1 Stone |
| Cobblestone, Mossy Cobblestone or Smooth Stone | 1 of the same block | Same result |
| Stone Bricks, Mossy Stone Bricks, Cracked Stone Bricks or Chiseled Stone Bricks | 1 of the same block | Same result |
| A listed stair or wall | 1 matching stair or wall | Same result |
| A listed single slab / double slab | 1 / 2 matching slabs | Same result |

Fortune does not add a multiplier to these building-block drops. A double slab returns slab items, not a full-block ingredient. Explosion drops have separate survival/decay conditions. **Infested lookalikes follow different rules**, described [below](#infested-stone-variants). [Stone loot][loot-stone] · [Cobblestone][loot-cobblestone] · [Mossy Cobblestone][loot-mossy-cobblestone] · [Smooth Stone][loot-smooth-stone] · [Brick loot][loot-stone-bricks] [loot-mossy-stone-bricks][] [loot-cracked-stone-bricks][] [loot-chiseled-stone-bricks] · [Stair example][loot-stone-stairs] · [Wall example][loot-cobblestone-wall] · [Slab example][loot-stone-slab]

### Water and lava

The active liquid callbacks also produce building stone. When water is beside or above a lava block, the checked lava position becomes **Obsidian if it is a source**, or **Cobblestone if it is flowing lava**. When lava spreads downward into a water block, that water block becomes **Stone**. Keep the lava source away from the intended Cobblestone contact point. These are source-verified conversion rules; no generator layout was tested for this guide. [Water contact and update callbacks][liquid] · [Downward lava flow][lava]

## Uses

These are the construction recipes for this family. The [Furnace](Furnace.md), [Crafting Table](CraftingTable.md) and [Stonecutter](Stonecutter.md) guides explain their interfaces and shared behavior. Recipes are loaded from the active resource data, so server data packs can change them. [Recipe loading][recipes]

### Stone and cobblestone

- Smelt **1 Cobblestone → 1 Stone**. [Recipe][smelting-stone]
- Arrange **4 Stone in a 2 × 2 square → 4 Stone Bricks**. [Recipe][crafting-stone-bricks]
- Craft **1 Stone, shapeless → 1 Stone Button**, or **2 Stone side by side → 1 Stone Pressure Plate**. Their placed controls belong in [Buttons](Buttons.md) and [Pressure Plates](PressurePlates.md). [Button recipe][crafting-stone-button] · [Plate recipe][crafting-stone-pressure-plate]

Cobblestone is also accepted by the checked [Furnace recipe](Furnace.md#crafting-and-mining) and [Stone Pickaxe recipe](../items/StonePickaxe.md). These recipes use material tags; ordinary Stone is not a substitute for Cobblestone in those two tags. [Furnace materials][stone-materials] · [Tool materials][tool-materials] · [Furnace recipe][crafting-furnace] · [Pickaxe recipe][crafting-stone-pickaxe]

### Smooth stone

Smelt **1 Stone → 1 Smooth Stone**. Starting with Cobblestone therefore takes two separate smelting steps. Smooth Stone has its own slab, and is used in the [Blast Furnace](../items/BlastFurnace.md) recipe. It does **not** have a registered Smooth Stone stair or wall. [Smelting][smelting-smooth-stone] · [Slab recipe][crafting-smooth-stone-slab] · [Blast Furnace recipe][crafting-blast-furnace] · [Registered forms][blocks]

### Stone bricks

The regular Stone Bricks recipe above is the starting point for brick shapes. Smelt **1 Stone Bricks → 1 Cracked Stone Bricks**. Stack **2 Stone Brick Slabs vertically → 1 Chiseled Stone Bricks**, or use the direct stonecutting choices below. Cracked and chiseled bricks are full blocks; this family has no cracked or chiseled stairs, slabs or walls. [Cracking][smelting-cracked-stone-bricks] · [Chiseling][crafting-chiseled-stone-bricks] · [Registry][blocks]

### Mossy variants

Combine **1 Cobblestone with 1 Vine or 1 Moss Block**, shapeless, for **1 Mossy Cobblestone**. The equivalent recipe with **1 Stone Bricks** makes **1 Mossy Stone Bricks**. Moss Carpet is not the named Moss Block ingredient. Make the mossy full block first, then shape it. [Cobblestone recipes][crafting-mossy-cobblestone-from-vine] [crafting-mossy-cobblestone-from-moss-block] · [Brick recipes][crafting-mossy-stone-bricks-from-vine] [crafting-mossy-stone-bricks-from-moss-block]

All three smelting conversions above take **200 game ticks per item**, nominally **10 seconds at 20 ticks per second**, and specify **0.1 recipe experience**. They are Furnace smelting recipes; experience collection and fuel belong to the [Furnace guide](Furnace.md). [Smelting recipes][smelting-stone] [smelting-smooth-stone][] [smelting-cracked-stone-bricks][] · [Active Furnace recipe type and ticking][furnace] [furnace-tick]

## Stairs, slabs and walls

These are the registered ordinary shape choices. A dash means this form is absent from this family, not a missing recipe to discover. The names link to the matching item pages. Cracked and Chiseled Stone Bricks are linked in the [brick section](#stone-bricks). [Block registry][blocks] · [Item registrations][items]

| Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- |
| [Stone](../items/Stone.md) | [Stairs](../items/StoneStairs.md) | [Slab](../items/StoneSlab.md) | — |
| [Smooth Stone](../items/SmoothStone.md) | — | [Slab](../items/SmoothStoneSlab.md) | — |
| [Cobblestone](../items/Cobblestone.md) | [Stairs](../items/CobblestoneStairs.md) | [Slab](../items/CobblestoneSlab.md) | [Wall](../items/CobblestoneWall.md) |
| [Mossy Cobblestone](../items/MossyCobblestone.md) | [Stairs](../items/MossyCobblestoneStairs.md) | [Slab](../items/MossyCobblestoneSlab.md) | [Wall](../items/MossyCobblestoneWall.md) |
| [Stone Bricks](../items/StoneBricks.md) | [Stairs](../items/StoneBrickStairs.md) | [Slab](../items/StoneBrickSlab.md) | [Wall](../items/StoneBrickWall.md) |
| [Mossy Stone Bricks](../items/MossyStoneBricks.md) | [Stairs](../items/MossyStoneBrickStairs.md) | [Slab](../items/MossyStoneBrickSlab.md) | [Wall](../items/MossyStoneBrickWall.md) |

### Crafting yields

For **stairs**, arrange six matching full blocks as three rows containing **1, 2, then 3 blocks aligned along one side**. For **slabs**, place three matching full blocks in one row. For **walls**, fill two rows of three. These exact material recipes give the counts below.

| Material | Stairs: 6 blocks → 4 | Slabs: 3 blocks → 6 | Walls: 6 blocks → 6 |
| --- | --- | --- | --- |
| Stone | [Recipe][crafting-stone-stairs] | [Recipe][crafting-stone-slab] | — |
| Smooth Stone | — | [Recipe][crafting-smooth-stone-slab] | — |
| Cobblestone | [Recipe][crafting-cobblestone-stairs] | [Recipe][crafting-cobblestone-slab] | [Recipe][crafting-cobblestone-wall] |
| Mossy Cobblestone | [Recipe][crafting-mossy-cobblestone-stairs] | [Recipe][crafting-mossy-cobblestone-slab] | [Recipe][crafting-mossy-cobblestone-wall] |
| Stone Bricks | [Recipe][crafting-stone-brick-stairs] | [Recipe][crafting-stone-brick-slab] | [Recipe][crafting-stone-brick-wall] |
| Mossy Stone Bricks | [Recipe][crafting-mossy-stone-brick-stairs] | [Recipe][crafting-mossy-stone-brick-slab] | [Recipe][crafting-mossy-stone-brick-wall] |

### Stonecutting choices

A Stonecutter consumes **one input block** per operation. Stairs cost one block each here, compared with six blocks for four crafted stairs; slabs and walls have the same material yield as crafting but permit smaller batches. The table lists the checked choices for these six inputs. [Menu consumption][stonecutter]

| One input | Available outputs and count per operation |
| --- | --- |
| Stone | [1 × Chiseled Stone Bricks][stonecutting-chiseled-stone-bricks-stone-from-stonecutting]; [2 × Stone Brick Slab][stonecutting-stone-brick-slab-from-stone-stonecutting]; [1 × Stone Brick Stairs][stonecutting-stone-brick-stairs-from-stone-stonecutting]; [1 × Stone Brick Wall][stonecutting-stone-brick-walls-from-stone-stonecutting]; [1 × Stone Bricks][stonecutting-stone-bricks-from-stone-stonecutting]; [2 × Stone Slab][stonecutting-stone-slab-from-stone-stonecutting]; [1 × Stone Stairs][stonecutting-stone-stairs-from-stone-stonecutting] |
| Smooth Stone | [2 × Smooth Stone Slab][stonecutting-smooth-stone-slab-from-smooth-stone-stonecutting] |
| Cobblestone | [2 × Cobblestone Slab][stonecutting-cobblestone-slab-from-cobblestone-stonecutting]; [1 × Cobblestone Stairs][stonecutting-cobblestone-stairs-from-cobblestone-stonecutting]; [1 × Cobblestone Wall][stonecutting-cobblestone-wall-from-cobblestone-stonecutting] |
| Mossy Cobblestone | [2 × Mossy Cobblestone Slab][stonecutting-mossy-cobblestone-slab-from-mossy-cobblestone-stonecutting]; [1 × Mossy Cobblestone Stairs][stonecutting-mossy-cobblestone-stairs-from-mossy-cobblestone-stonecutting]; [1 × Mossy Cobblestone Wall][stonecutting-mossy-cobblestone-wall-from-mossy-cobblestone-stonecutting] |
| Stone Bricks | [1 × Chiseled Stone Bricks][stonecutting-chiseled-stone-bricks-from-stone-bricks-stonecutting]; [2 × Stone Brick Slab][stonecutting-stone-brick-slab-from-stone-bricks-stonecutting]; [1 × Stone Brick Stairs][stonecutting-stone-brick-stairs-from-stone-bricks-stonecutting]; [1 × Stone Brick Wall][stonecutting-stone-brick-wall-from-stone-bricks-stonecutting] |
| Mossy Stone Bricks | [2 × Mossy Stone Brick Slab][stonecutting-mossy-stone-brick-slab-from-mossy-stone-brick-stonecutting]; [1 × Mossy Stone Brick Stairs][stonecutting-mossy-stone-brick-stairs-from-mossy-stone-brick-stonecutting]; [1 × Mossy Stone Brick Wall][stonecutting-mossy-stone-brick-wall-from-mossy-stone-brick-stonecutting] |

### Placing shaped blocks

- **Slabs:** use the top face or lower half of a side for a bottom slab; use the underside or upper half of a side for a top slab. Place a second slab of the **same item** into the empty half to make a double slab. Single slabs can be waterlogged; making a double slab clears that waterlogged state. [Slab placement and replacement][slabs]
- **Stairs:** the stair faces the player's horizontal direction, with its higher end ahead for ordinary bottom stairs. The clicked face and height choose upright or upside-down placement. Suitable neighboring stairs automatically form inner or outer corners, including stairs made from another material when their half and facing fit. Stairs can be waterlogged. [Placement and corner rules][stairs]
- **Walls:** neighboring walls, sturdy block faces, Iron Bars and suitably aligned Fence Gates determine connections; blocks above can change wall/post shape. Their collision extends **1.5 blocks high**. Walls can be waterlogged. [Connections, shape and water][walls]

These placed forms do not fall or require a supporting block beneath them. Shape and water states affect the placed block; mining returns the corresponding ordinary item, so choose the orientation again on placement. [Block classes][blocks] · [Base survival rules][properties] · [Shape callbacks][slabs] [stairs][] [walls][]

## Infested stone variants

An infested block can resemble ordinary masonry but release a **[Silverfish](../mobs/Silverfish.md)** when broken. The registered stone-family forms and their Silk Touch outputs are:

| Infested ID | Silk Touch output |
| --- | --- |
| [`minecraft:infested_stone`](../items/InfestedStone.md) | 1 ordinary Stone |
| [`minecraft:infested_cobblestone`](../items/InfestedCobblestone.md) | 1 ordinary Cobblestone |
| [`minecraft:infested_stone_bricks`](../items/InfestedStoneBricks.md) | 1 ordinary Stone Bricks |
| [`minecraft:infested_mossy_stone_bricks`](../items/InfestedMossyStoneBricks.md) | 1 ordinary Mossy Stone Bricks |
| [`minecraft:infested_cracked_stone_bricks`](../items/InfestedCrackedStoneBricks.md) | 1 ordinary Cracked Stone Bricks |
| [`minecraft:infested_chiseled_stone_bricks`](../items/InfestedChiseledStoneBricks.md) | 1 ordinary Chiseled Stone Bricks |

Without Silk Touch these loot tables give **no block item**, and the active break callback spawns a Silverfish when `doTileDrops` is enabled. **Silk Touch suppresses the Silverfish and gives the ordinary host block**, never the infested item. The spawn suppression uses an enchantment tag containing Silk Touch in the bundled data. Ordinary destructive explosions also reach that break callback with no enchanted tool. [Infested behavior][infested] · [Suppression tag][prevent-infestation] · [Exact loot][loot-infested-stone] [loot-infested-cobblestone][] [loot-infested-stone-bricks][] [loot-infested-mossy-stone-bricks][] [loot-infested-cracked-stone-bricks][] [loot-infested-chiseled-stone-bricks] · [Mining dispatch][break-dispatch] [block-destroy][] [block-drops][] · [Explosion callback][properties]

These infested registrations **do not require a correct tool for drops**, so breaking them by hand is not a way to avoid the Silverfish. Their hardness is half the ordinary host block's hardness and their blast resistance is **0.75**. Creative breaking takes a separate no-drops path. [Registrations][blocks] · [Infested properties][infested] · [Player tool check][player-tool] · [Creative/mining path][break-dispatch]

Infestation is not just a Creative decoration. For example, the Windswept Hills biome uses the placed infested-ore feature, whose targets include Infested Stone and [Infested Deepslate](Deepslate.md#infested-deepslate). A Silverfish's active AI can also hide in compatible host blocks when `mobGriefing` allows it; hurting one can wake others in nearby infested blocks. When `mobGriefing` is disabled, the wake-up action instead restores affected blocks to their ordinary hosts. [Biome wiring][hills] · [Placed feature][placed-infested] · [Targets][ore-infested] · [Registered Silverfish goals][silverfish]

No ordinary crafting or stonecutting recipe produces these infested items in the checked resource set. Their Creative entries are separate from the survival collection rules above. There is no registered Infested Mossy Cobblestone or infested stair/slab/wall family. [Registry and Creative entries][blocks] [creative]

## Block properties

The table is for **ordinary full blocks**, not all shape variants or infested blocks. Their note-block instrument is the bass drum. Hardness is a mining property, not a time in seconds. [Registration][blocks]

| Full blocks | Hardness | Blast resistance |
| --- | ---: | ---: |
| Stone; regular, mossy, cracked and chiseled Stone Bricks | 1.5 | 6 |
| Cobblestone, Mossy Cobblestone and Smooth Stone | 2 | 6 |

## Related pages

- [Stone item](../items/Stone.md), [Cobblestone item](../items/Cobblestone.md) and [Smooth Stone item](../items/SmoothStone.md)
- [Stone Bricks](../items/StoneBricks.md), [Cracked Stone Bricks](../items/CrackedStoneBricks.md) and [Chiseled Stone Bricks](../items/ChiseledStoneBricks.md)
- [Deepslate family](Deepslate.md), [Limestone family](Limestone.md) and [stone category](catalog/stone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. Checked active registrations, terrain settings and generator use, all covered block loot, tool tags and player break dispatch, exact crafting/smelting/stonecutting recipes, shape placement, and infestation callbacks. No in-game mining, placement, crafting, generation or Silverfish test was run. The guide covers the named stone/cobblestone families; it does not extend these recipes or drops to every block in the stone category.

[presets]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java#L120-L135
[overworld]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[terrain]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L33-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/stone.json
[loot-cobblestone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cobblestone.json
[loot-mossy-cobblestone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/mossy_cobblestone.json
[loot-smooth-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/smooth_stone.json
[loot-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/stone_bricks.json
[loot-mossy-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/mossy_stone_bricks.json
[loot-cracked-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cracked_stone_bricks.json
[loot-chiseled-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/chiseled_stone_bricks.json
[loot-stone-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/stone_stairs.json
[loot-cobblestone-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cobblestone_wall.json
[loot-stone-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/stone_slab.json
[liquid]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/LiquidBlock.java
[lava]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L204-L219
[recipes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[smelting-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/stone.json
[crafting-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_bricks.json
[crafting-stone-button]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_button.json
[crafting-stone-pressure-plate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_pressure_plate.json
[stone-materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json
[tool-materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[crafting-furnace]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/furnace.json
[crafting-stone-pickaxe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_pickaxe.json
[smelting-smooth-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/smooth_stone.json
[crafting-smooth-stone-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/smooth_stone_slab.json
[crafting-blast-furnace]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/blast_furnace.json
[smelting-cracked-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/cracked_stone_bricks.json
[crafting-chiseled-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/chiseled_stone_bricks.json
[crafting-mossy-cobblestone-from-vine]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_vine.json
[crafting-mossy-cobblestone-from-moss-block]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_moss_block.json
[crafting-mossy-stone-bricks-from-vine]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_vine.json
[crafting-mossy-stone-bricks-from-moss-block]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_moss_block.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L204
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[crafting-stone-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_stairs.json
[crafting-stone-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_slab.json
[crafting-cobblestone-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/cobblestone_stairs.json
[crafting-cobblestone-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/cobblestone_slab.json
[crafting-cobblestone-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/cobblestone_wall.json
[crafting-mossy-cobblestone-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_stairs.json
[crafting-mossy-cobblestone-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_slab.json
[crafting-mossy-cobblestone-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_wall.json
[crafting-stone-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_brick_stairs.json
[crafting-stone-brick-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_brick_slab.json
[crafting-stone-brick-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_brick_wall.json
[crafting-mossy-stone-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_brick_stairs.json
[crafting-mossy-stone-brick-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_brick_slab.json
[crafting-mossy-stone-brick-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_brick_wall.json
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-chiseled-stone-bricks-stone-from-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_stone_bricks_stone_from_stonecutting.json
[stonecutting-stone-brick-slab-from-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_brick_slab_from_stone_stonecutting.json
[stonecutting-stone-brick-stairs-from-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_brick_stairs_from_stone_stonecutting.json
[stonecutting-stone-brick-walls-from-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_brick_walls_from_stone_stonecutting.json
[stonecutting-stone-bricks-from-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_bricks_from_stone_stonecutting.json
[stonecutting-stone-slab-from-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_slab_from_stone_stonecutting.json
[stonecutting-stone-stairs-from-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_stairs_from_stone_stonecutting.json
[stonecutting-smooth-stone-slab-from-smooth-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/smooth_stone_slab_from_smooth_stone_stonecutting.json
[stonecutting-cobblestone-slab-from-cobblestone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/cobblestone_slab_from_cobblestone_stonecutting.json
[stonecutting-cobblestone-stairs-from-cobblestone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/cobblestone_stairs_from_cobblestone_stonecutting.json
[stonecutting-cobblestone-wall-from-cobblestone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/cobblestone_wall_from_cobblestone_stonecutting.json
[stonecutting-mossy-cobblestone-slab-from-mossy-cobblestone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_slab_from_mossy_cobblestone_stonecutting.json
[stonecutting-mossy-cobblestone-stairs-from-mossy-cobblestone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_stairs_from_mossy_cobblestone_stonecutting.json
[stonecutting-mossy-cobblestone-wall-from-mossy-cobblestone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_wall_from_mossy_cobblestone_stonecutting.json
[stonecutting-chiseled-stone-bricks-from-stone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_stone_bricks_from_stone_bricks_stonecutting.json
[stonecutting-stone-brick-slab-from-stone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_brick_slab_from_stone_bricks_stonecutting.json
[stonecutting-stone-brick-stairs-from-stone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_brick_stairs_from_stone_bricks_stonecutting.json
[stonecutting-stone-brick-wall-from-stone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/stone_brick_wall_from_stone_bricks_stonecutting.json
[stonecutting-mossy-stone-brick-slab-from-mossy-stone-brick-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/mossy_stone_brick_slab_from_mossy_stone_brick_stonecutting.json
[stonecutting-mossy-stone-brick-stairs-from-mossy-stone-brick-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/mossy_stone_brick_stairs_from_mossy_stone_brick_stonecutting.json
[stonecutting-mossy-stone-brick-wall-from-mossy-stone-brick-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/mossy_stone_brick_wall_from_mossy_stone_brick_stonecutting.json
[slabs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/StairBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/WallBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[infested]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/InfestedBlock.java
[prevent-infestation]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/prevents_infested_spawns.json
[loot-infested-stone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_stone.json
[loot-infested-cobblestone]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_cobblestone.json
[loot-infested-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_stone_bricks.json
[loot-infested-mossy-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_mossy_stone_bricks.json
[loot-infested-cracked-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_cracked_stone_bricks.json
[loot-infested-chiseled-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_chiseled_stone_bricks.json
[block-destroy]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[block-drops]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Block.java#L364-L386
[hills]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/windswept_hills.json
[placed-infested]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/placed_feature/ore_infested.json
[ore-infested]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/configured_feature/ore_infested.json
[silverfish]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Silverfish.java
[creative]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
