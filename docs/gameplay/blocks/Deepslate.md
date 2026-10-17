# Deepslate

Deepslate (`minecraft:deepslate`) provides a dark building palette with cobbled, polished, brick, tile, cracked and chiseled finishes. **Ordinary Deepslate is the axis-oriented raw block; Cobbled Deepslate (`minecraft:cobbled_deepslate`) starts the construction recipe chain.** Infested and Reinforced Deepslate have separate collection rules below. [Registrations][blocks]

## Obtaining and mining

Look underground in the normal Overworld. Its terrain settings choose Deepslate through a transition between **Y=0 and Y=8**: the Deepslate condition is fully enabled at or below 0 and disabled at or above 8. This applies to eligible base terrain after earlier surface rules, not every block at that height; caves, ores, fluids and other features still interrupt it. [Normal preset][presets] · [Overworld rule][overworld] · [Generator and surface dispatch][terrain] [surface] · [Gradient evaluation][surface-rules] [native-surface]

Use an **unbroken pickaxe of any standard material, including Wood**, for the ordinary construction forms in this guide. They require the correct tool, are pickaxe-tagged and have no higher material-tier restriction. [Registration][blocks] · [Mining tag][pickaxe] · [Tier tags][stone-tier] [iron-tier][] [diamond-tier][] · [Tool matching][tool-material] [wood-denials][] [player-tool][] [broken-tool]

- Mining **Deepslate normally drops 1 Cobbled Deepslate**; Silk Touch selects **1 Deepslate** instead. Fortune does not increase either count. [Deepslate loot][loot-deepslate]
- Ordinary **Cobbled, Polished, Brick, Tile, Chiseled and Cracked Deepslate** blocks drop one matching block with a correct tool; they do not turn back into Cobbled Deepslate when mined. Their stairs and walls also drop the matching shape. [Full-block loot][loot-cobbled-deepslate] [loot-polished-deepslate][] [loot-deepslate-bricks][] [loot-deepslate-tiles][] [loot-chiseled-deepslate][] [loot-cracked-deepslate-bricks][] [loot-cracked-deepslate-tiles][] · [Shape examples][loot-deepslate-tile-stairs] [loot-deepslate-brick-wall]
- A single slab returns **1 matching slab**; a double slab returns **2**, including with Silk Touch. These drops have no Fortune multiplier, and explosions apply their separate drop conditions. [Slab loot][loot-cobbled-deepslate-slab] [loot-polished-deepslate-slab][] [loot-deepslate-brick-slab][] [loot-deepslate-tile-slab]

Smelting **1 Cobbled Deepslate → 1 Deepslate** provides the raw oriented block without Silk Touch. Each operation takes **200 game ticks**, nominally **10 seconds**, and specifies **0.1 recipe experience**. Use the [Furnace](Furnace.md) for fuel and experience handling. [Smelting recipe][smelting-deepslate] · [Active recipe type and tick][furnace] [furnace-tick]

## Orienting ordinary deepslate

Place Deepslate on a block's **top or bottom face** to keep its axis vertical. Place against an **east/west side** for an east–west axis, or a **north/south side** for a north–south axis. The clicked face chooses the axis, rather than the player's horizontal view. Recovered items do not store the previous placed axis. [Deepslate class registration][blocks] · [Pillar placement][pillar] · [Loot][loot-deepslate]

Cobbled, Polished, Brick, Tile, Chiseled and Cracked Deepslate are ordinary full-block classes without this axis control. Their shaped forms use the stair, slab or wall rules instead. [Registered classes][blocks]

## Building variants

All four construction finishes have stairs, slabs and walls. [Blocks][blocks] · [Item forms][items]

| Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- |
| [Cobbled Deepslate](../items/CobbledDeepslate.md) | [Stairs](../items/CobbledDeepslateStairs.md) | [Slab](../items/CobbledDeepslateSlab.md) | [Wall](../items/CobbledDeepslateWall.md) |
| [Polished Deepslate](../items/PolishedDeepslate.md) | [Stairs](../items/PolishedDeepslateStairs.md) | [Slab](../items/PolishedDeepslateSlab.md) | [Wall](../items/PolishedDeepslateWall.md) |
| [Deepslate Bricks](../items/DeepslateBricks.md) | [Stairs](../items/DeepslateBrickStairs.md) | [Slab](../items/DeepslateBrickSlab.md) | [Wall](../items/DeepslateBrickWall.md) |
| [Deepslate Tiles](../items/DeepslateTiles.md) | [Stairs](../items/DeepslateTileStairs.md) | [Slab](../items/DeepslateTileSlab.md) | [Wall](../items/DeepslateTileWall.md) |

Additional full blocks are [ordinary Deepslate](../items/Deepslate.md), [Chiseled Deepslate](../items/ChiseledDeepslate.md), [Cracked Deepslate Bricks](../items/CrackedDeepslateBricks.md) and [Cracked Deepslate Tiles](../items/CrackedDeepslateTiles.md). There are no registered ordinary Deepslate stairs/slabs/walls, or chiseled/cracked shape families. Infested and Reinforced Deepslate are not extra construction finishes in this recipe chain. [Registry][blocks]

### Crafting and smelting

| Ingredients and arrangement | Result |
| --- | --- |
| 4 Cobbled Deepslate in a 2 × 2 square | 4 Polished Deepslate |
| 4 Polished Deepslate in a 2 × 2 square | 4 Deepslate Bricks |
| 4 Deepslate Bricks in a 2 × 2 square | 4 Deepslate Tiles |
| 2 Cobbled Deepslate Slabs stacked vertically | 1 Chiseled Deepslate |
| Smelt 1 Deepslate Bricks | 1 Cracked Deepslate Bricks |
| Smelt 1 Deepslate Tiles | 1 Cracked Deepslate Tiles |

[Polishing][crafting-polished-deepslate] · [Bricks][crafting-deepslate-bricks] · [Tiles][crafting-deepslate-tiles] · [Chiseled][crafting-chiseled-deepslate] · [Cracked recipes][smelting-cracked-deepslate-bricks] [smelting-cracked-deepslate-tiles]

Each cracking recipe also takes **200 game ticks** and specifies **0.1 experience**. These are Furnace recipes. Ordinary Deepslate is not the input for the polishing recipe; mine it without Silk Touch to obtain Cobbled Deepslate for the chain. [Cracking][smelting-cracked-deepslate-bricks] [smelting-cracked-deepslate-tiles] · [Polishing][crafting-polished-deepslate] · [Normal mining][loot-deepslate]

Shape recipes use the matching full-block finish: six blocks in a **1/2/3 stair pattern** give four stairs; three blocks across a row give six slabs; two full rows give six walls.

| Material | Stairs: 6 blocks → 4 | Slabs: 3 blocks → 6 | Walls: 6 blocks → 6 |
| --- | --- | --- | --- |
| Cobbled Deepslate | [Recipe][crafting-cobbled-deepslate-stairs] | [Recipe][crafting-cobbled-deepslate-slab] | [Recipe][crafting-cobbled-deepslate-wall] |
| Polished Deepslate | [Recipe][crafting-polished-deepslate-stairs] | [Recipe][crafting-polished-deepslate-slab] | [Recipe][crafting-polished-deepslate-wall] |
| Deepslate Bricks | [Recipe][crafting-deepslate-brick-stairs] | [Recipe][crafting-deepslate-brick-slab] | [Recipe][crafting-deepslate-brick-wall] |
| Deepslate Tiles | [Recipe][crafting-deepslate-tile-stairs] | [Recipe][crafting-deepslate-tile-slab] | [Recipe][crafting-deepslate-tile-wall] |

### Stonecutting shortcuts

The [Stonecutter](Stonecutter.md) consumes one input per operation. Each full block, stair or wall result below has count **1**; slab results have count **2**. Cobbled Deepslate can go directly to brick and tile products without first crafting each intermediate finish. [Active menu][stonecutter]

| One input | Available outputs and count per operation |
| --- | --- |
| Cobbled Deepslate | [1 × Chiseled Deepslate][stonecutting-chiseled-deepslate-from-cobbled-deepslate-stonecutting]; [2 × Cobbled Deepslate Slab][stonecutting-cobbled-deepslate-slab-from-cobbled-deepslate-stonecutting]; [1 × Cobbled Deepslate Stairs][stonecutting-cobbled-deepslate-stairs-from-cobbled-deepslate-stonecutting]; [1 × Cobbled Deepslate Wall][stonecutting-cobbled-deepslate-wall-from-cobbled-deepslate-stonecutting]; [2 × Deepslate Brick Slab][stonecutting-deepslate-brick-slab-from-cobbled-deepslate-stonecutting]; [1 × Deepslate Brick Stairs][stonecutting-deepslate-brick-stairs-from-cobbled-deepslate-stonecutting]; [1 × Deepslate Brick Wall][stonecutting-deepslate-brick-wall-from-cobbled-deepslate-stonecutting]; [1 × Deepslate Bricks][stonecutting-deepslate-bricks-from-cobbled-deepslate-stonecutting]; [2 × Deepslate Tile Slab][stonecutting-deepslate-tile-slab-from-cobbled-deepslate-stonecutting]; [1 × Deepslate Tile Stairs][stonecutting-deepslate-tile-stairs-from-cobbled-deepslate-stonecutting]; [1 × Deepslate Tile Wall][stonecutting-deepslate-tile-wall-from-cobbled-deepslate-stonecutting]; [1 × Deepslate Tiles][stonecutting-deepslate-tiles-from-cobbled-deepslate-stonecutting]; [1 × Polished Deepslate][stonecutting-polished-deepslate-from-cobbled-deepslate-stonecutting]; [2 × Polished Deepslate Slab][stonecutting-polished-deepslate-slab-from-cobbled-deepslate-stonecutting]; [1 × Polished Deepslate Stairs][stonecutting-polished-deepslate-stairs-from-cobbled-deepslate-stonecutting]; [1 × Polished Deepslate Wall][stonecutting-polished-deepslate-wall-from-cobbled-deepslate-stonecutting] |
| Polished Deepslate | [2 × Deepslate Brick Slab][stonecutting-deepslate-brick-slab-from-polished-deepslate-stonecutting]; [1 × Deepslate Brick Stairs][stonecutting-deepslate-brick-stairs-from-polished-deepslate-stonecutting]; [1 × Deepslate Brick Wall][stonecutting-deepslate-brick-wall-from-polished-deepslate-stonecutting]; [1 × Deepslate Bricks][stonecutting-deepslate-bricks-from-polished-deepslate-stonecutting]; [2 × Deepslate Tile Slab][stonecutting-deepslate-tile-slab-from-polished-deepslate-stonecutting]; [1 × Deepslate Tile Stairs][stonecutting-deepslate-tile-stairs-from-polished-deepslate-stonecutting]; [1 × Deepslate Tile Wall][stonecutting-deepslate-tile-wall-from-polished-deepslate-stonecutting]; [1 × Deepslate Tiles][stonecutting-deepslate-tiles-from-polished-deepslate-stonecutting]; [2 × Polished Deepslate Slab][stonecutting-polished-deepslate-slab-from-polished-deepslate-stonecutting]; [1 × Polished Deepslate Stairs][stonecutting-polished-deepslate-stairs-from-polished-deepslate-stonecutting]; [1 × Polished Deepslate Wall][stonecutting-polished-deepslate-wall-from-polished-deepslate-stonecutting] |
| Deepslate Bricks | [2 × Deepslate Brick Slab][stonecutting-deepslate-brick-slab-from-deepslate-bricks-stonecutting]; [1 × Deepslate Brick Stairs][stonecutting-deepslate-brick-stairs-from-deepslate-bricks-stonecutting]; [1 × Deepslate Brick Wall][stonecutting-deepslate-brick-wall-from-deepslate-bricks-stonecutting]; [2 × Deepslate Tile Slab][stonecutting-deepslate-tile-slab-from-deepslate-bricks-stonecutting]; [1 × Deepslate Tile Stairs][stonecutting-deepslate-tile-stairs-from-deepslate-bricks-stonecutting]; [1 × Deepslate Tile Wall][stonecutting-deepslate-tile-wall-from-deepslate-bricks-stonecutting]; [1 × Deepslate Tiles][stonecutting-deepslate-tiles-from-deepslate-bricks-stonecutting] |
| Deepslate Tiles | [2 × Deepslate Tile Slab][stonecutting-deepslate-tile-slab-from-deepslate-tiles-stonecutting]; [1 × Deepslate Tile Stairs][stonecutting-deepslate-tile-stairs-from-deepslate-tiles-stonecutting]; [1 × Deepslate Tile Wall][stonecutting-deepslate-tile-wall-from-deepslate-tiles-stonecutting] |

**Ordinary Deepslate, Chiseled Deepslate and the two cracked blocks have no stonecutting-input recipes in the checked bundled set.** There is no reverse stonecutting route from a later finish to an earlier one. Check the chosen result before processing your stock; mining a finished block normally preserves that finish. [Recipe loading][recipes] · [Menu selection][stonecutter] · [Finished-block loot][loot-polished-deepslate] [loot-deepslate-bricks][] [loot-deepslate-tiles][]

### Placing shapes and other uses

These shapes use the same [slab, stair and wall placement rules](Stone.md#placing-shaped-blocks) as the ordinary stone family: top/bottom and double slabs, upright/upside-down stairs and automatic corners, connected walls, and waterlogging of the single-slab/stair/wall forms. They stay placed when a support below is removed. [Shape classes][slabs] [stairs][] [walls][] · [Registration and base survival][blocks] [properties]

Cobbled Deepslate also substitutes for Cobblestone in the checked [Furnace recipe](Furnace.md#crafting-and-mining) and [Stone Pickaxe recipe](../items/StonePickaxe.md). This is recipe-tag support, not a general promise that every Cobblestone recipe accepts it. [Material tags][stone-materials] [tool-materials] · [Recipe inputs][crafting-furnace] [crafting-stone-pickaxe]

For a small build, **eight Cobbled Deepslate can make eight Deepslate Tile Stairs at the Stonecutter**. Crafting from full Deepslate Tiles instead gives four stairs per six tiles. This is a source-based planning example, not an in-game crafting test. [Direct cut][stonecutting-deepslate-tile-stairs-from-cobbled-deepslate-stonecutting] · [Crafted stairs][crafting-deepslate-tile-stairs]

## Infested deepslate

`minecraft:infested_deepslate` is a separate Silverfish-bearing block. It preserves the raw Deepslate axis-placement behavior but follows the shared [infested-block rules](Stone.md#infested-stone-variants): **no block item without Silk Touch**, and a Silverfish-spawn callback with `doTileDrops` enabled; **Silk Touch gives 1 ordinary Deepslate and suppresses the Silverfish**. The infested item itself is not a mining reward. It has hardness **1.5**, blast resistance **0.75**, and no correct-tool requirement. [Registration][blocks] · [Infested axis][infested-pillar] · [Break behavior and properties][infested] · [Suppression tag][prevent-infestation] · [Loot][loot-infested-deepslate]

The checked infested-ore feature can generate this block, and Silverfish can infest compatible ordinary Deepslate. Cobbled, polished, brick and tile Deepslate are not registered infestation hosts. [Feature targets][ore-infested] · [Host registrations][blocks] · [Silverfish AI][silverfish]

## Reinforced deepslate

`minecraft:reinforced_deepslate` is separate from both ordinary and Infested Deepslate. It is a full building block with **hardness 55** and **blast resistance 1,200**, and pistons explicitly refuse to move it. Its bundled loot table is empty: **breaking it gives no item, even with Silk Touch or Fortune**. No crafting, smelting or stonecutting recipe produces it in the checked resources. Its [item](../items/ReinforcedDeepslate.md) is listed in Creative inventory; do not spend Survival tools expecting to collect it. [Registration][blocks] · [Empty loot][loot-reinforced-deepslate] · [Piston restriction][pistons] · [Creative entry][creative]

The registered block is an ordinary Block with no axis property or activation interaction. Its appearance alone does not establish a portal mechanic. [Registered class][blocks] · [Base interaction][properties]

## Block properties

| Ordinary material | Hardness | Blast resistance |
| --- | ---: | ---: |
| Raw Deepslate | 3 | 6 |
| Cobbled, Polished, Brick, Tile, Chiseled and Cracked forms, including their registered stairs/slabs/walls | 3.5 | 6 |

These values do not apply to the two exceptional blocks above. Hardness is not a number of seconds; tool speed and conditions affect breaking time. [Registrations and copied properties][blocks] [properties]

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. Checked active block/item registration, normal terrain rule dispatch, tool tags and mining drops, all family crafting/smelting/stonecutting recipes, orientation and shaped-block classes, Infested Deepslate and Reinforced Deepslate exceptions. No in-game mining, crafting, world-generation, placement or Silverfish test was run.

Related: [Deepslate item](../items/Deepslate.md) · [Cobbled Deepslate item](../items/CobbledDeepslate.md) · [Stone family](Stone.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java
[presets]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java#L120-L135
[overworld]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[terrain]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java
[surface]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java
[surface-rules]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/SurfaceRules.java
[native-surface]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L33-L48
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[player-tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[loot-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate.json
[loot-cobbled-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cobbled_deepslate.json
[loot-polished-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/polished_deepslate.json
[loot-deepslate-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate_bricks.json
[loot-deepslate-tiles]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate_tiles.json
[loot-chiseled-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/chiseled_deepslate.json
[loot-cracked-deepslate-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cracked_deepslate_bricks.json
[loot-cracked-deepslate-tiles]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cracked_deepslate_tiles.json
[loot-deepslate-tile-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate_tile_stairs.json
[loot-deepslate-brick-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate_brick_wall.json
[loot-cobbled-deepslate-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cobbled_deepslate_slab.json
[loot-polished-deepslate-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/polished_deepslate_slab.json
[loot-deepslate-brick-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate_brick_slab.json
[loot-deepslate-tile-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/deepslate_tile_slab.json
[smelting-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/deepslate.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L204
[pillar]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[crafting-polished-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/polished_deepslate.json
[crafting-deepslate-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_bricks.json
[crafting-deepslate-tiles]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_tiles.json
[crafting-chiseled-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/chiseled_deepslate.json
[smelting-cracked-deepslate-bricks]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/cracked_deepslate_bricks.json
[smelting-cracked-deepslate-tiles]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/cracked_deepslate_tiles.json
[crafting-cobbled-deepslate-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/cobbled_deepslate_stairs.json
[crafting-cobbled-deepslate-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/cobbled_deepslate_slab.json
[crafting-cobbled-deepslate-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/cobbled_deepslate_wall.json
[crafting-polished-deepslate-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/polished_deepslate_stairs.json
[crafting-polished-deepslate-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/polished_deepslate_slab.json
[crafting-polished-deepslate-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/polished_deepslate_wall.json
[crafting-deepslate-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_brick_stairs.json
[crafting-deepslate-brick-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_brick_slab.json
[crafting-deepslate-brick-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_brick_wall.json
[crafting-deepslate-tile-stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_tile_stairs.json
[crafting-deepslate-tile-slab]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_tile_slab.json
[crafting-deepslate-tile-wall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/deepslate_tile_wall.json
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-chiseled-deepslate-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_deepslate_from_cobbled_deepslate_stonecutting.json
[stonecutting-cobbled-deepslate-slab-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/cobbled_deepslate_slab_from_cobbled_deepslate_stonecutting.json
[stonecutting-cobbled-deepslate-stairs-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/cobbled_deepslate_stairs_from_cobbled_deepslate_stonecutting.json
[stonecutting-cobbled-deepslate-wall-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/cobbled_deepslate_wall_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-brick-slab-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_slab_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-brick-stairs-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_stairs_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-brick-wall-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_wall_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-bricks-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_bricks_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-tile-slab-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_slab_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-tile-stairs-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_stairs_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-tile-wall-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_wall_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-tiles-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tiles_from_cobbled_deepslate_stonecutting.json
[stonecutting-polished-deepslate-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_from_cobbled_deepslate_stonecutting.json
[stonecutting-polished-deepslate-slab-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_slab_from_cobbled_deepslate_stonecutting.json
[stonecutting-polished-deepslate-stairs-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_stairs_from_cobbled_deepslate_stonecutting.json
[stonecutting-polished-deepslate-wall-from-cobbled-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_wall_from_cobbled_deepslate_stonecutting.json
[stonecutting-deepslate-brick-slab-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_slab_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-brick-stairs-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_stairs_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-brick-wall-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_wall_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-bricks-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_bricks_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-tile-slab-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_slab_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-tile-stairs-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_stairs_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-tile-wall-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_wall_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-tiles-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tiles_from_polished_deepslate_stonecutting.json
[stonecutting-polished-deepslate-slab-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_slab_from_polished_deepslate_stonecutting.json
[stonecutting-polished-deepslate-stairs-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_stairs_from_polished_deepslate_stonecutting.json
[stonecutting-polished-deepslate-wall-from-polished-deepslate-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_wall_from_polished_deepslate_stonecutting.json
[stonecutting-deepslate-brick-slab-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_slab_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-brick-stairs-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_stairs_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-brick-wall-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_wall_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-tile-slab-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_slab_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-tile-stairs-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_stairs_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-tile-wall-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_wall_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-tiles-from-deepslate-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tiles_from_deepslate_bricks_stonecutting.json
[stonecutting-deepslate-tile-slab-from-deepslate-tiles-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_slab_from_deepslate_tiles_stonecutting.json
[stonecutting-deepslate-tile-stairs-from-deepslate-tiles-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_stairs_from_deepslate_tiles_stonecutting.json
[stonecutting-deepslate-tile-wall-from-deepslate-tiles-stonecutting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_tile_wall_from_deepslate_tiles_stonecutting.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[slabs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/StairBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/WallBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[stone-materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json
[tool-materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[crafting-furnace]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/furnace.json
[crafting-stone-pickaxe]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/crafting/stone_pickaxe.json
[infested-pillar]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/InfestedRotatedPillarBlock.java
[infested]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/InfestedBlock.java
[prevent-infestation]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/prevents_infested_spawns.json
[loot-infested-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/infested_deepslate.json
[ore-infested]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/configured_feature/ore_infested.json
[silverfish]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Silverfish.java
[loot-reinforced-deepslate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/reinforced_deepslate.json
[pistons]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L225-L235
[creative]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
