# Tuff

**Tuff** (`minecraft:tuff`) is a dark stone building material with polished, brick and two chiseled finishes. Its base, polished and brick blocks each have stairs, slabs and walls. Raw Tuff can be cut directly into the later finishes at a Stonecutter. [Block and item registrations][blocks] [items]

## Obtaining and mining

Search deep in the normal Overworld. The checked **Plains** biome includes the Tuff deposit feature, whose placement chooses candidate origins from the dimension's bottom through **Y=0**. The configured feature replaces eligible base stone with Tuff. Generated clumps can extend beyond their origin; this range does not guarantee a deposit or describe every way Tuff may appear. [Normal preset][presets] · [Biome entry][plains] · [Placement][placement-tuff] · [Tuff configuration][deposit-tuff] · [Replacement tag][base-stone] · [Active feature dispatch][features] [ore-feature] [placed-feature] [biome-generation]

Use an **unbroken pickaxe**, including Wood, to collect any of the 14 Tuff-family forms covered here. They require a correct tool for drops, are pickaxe-tagged and have no higher material-tier restriction. Hand breaking does not collect them. [Registration][blocks] · [Mining tags][pickaxe] [wood-denials] [stone-tier] [iron-tier] [diamond-tier] · [Tool and break checks][tool-material] [player-tool] [broken-tool] [break-dispatch]

Full blocks, stairs and walls drop **one matching item**. Single slabs give **one slab**, and double slabs give **two matching slabs**. No Silk Touch is needed, and Fortune adds no multiplier. Mining a polished, brick or chiseled block keeps that finish. Explosions have separate survival/decay rules. [Full-block loot][loot-tuff] [loot-polished-tuff] [loot-tuff-bricks] [loot-chiseled-tuff] [loot-chiseled-tuff-bricks] · [Shape examples][loot-tuff-stairs] [loot-polished-tuff-wall] · [Slab loot][loot-tuff-slab] [loot-polished-tuff-slab] [loot-tuff-brick-slab]

## Variants

These are the registered ordinary shape choices. [Registry][blocks] [items]

| Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- |
| [Tuff](../items/Tuff.md) | [Stairs](../items/TuffStairs.md) | [Slab](../items/TuffSlab.md) | [Wall](../items/TuffWall.md) |
| [Polished Tuff](../items/PolishedTuff.md) | [Stairs](../items/PolishedTuffStairs.md) | [Slab](../items/PolishedTuffSlab.md) | [Wall](../items/PolishedTuffWall.md) |
| [Tuff Bricks](../items/TuffBricks.md) | [Stairs](../items/TuffBrickStairs.md) | [Slab](../items/TuffBrickSlab.md) | [Wall](../items/TuffBrickWall.md) |

The two extra full blocks are **[Chiseled Tuff](../items/ChiseledTuff.md)** (`minecraft:chiseled_tuff`) and **[Chiseled Tuff Bricks](../items/ChiseledTuffBricks.md)** (`minecraft:chiseled_tuff_bricks`). They are different items and use different slab ingredients when crafted. There are no registered chiseled stairs/slabs/walls or cracked Tuff variants. [Registry][blocks] · [Chiseled recipes][crafting-chiseled-tuff] [crafting-chiseled-tuff-bricks]

## Crafting

| Ingredients and arrangement | Result |
| --- | --- |
| 4 Tuff in a 2 × 2 square | 4 Polished Tuff |
| 4 Polished Tuff in a 2 × 2 square | 4 Tuff Bricks |
| 2 Tuff Slabs stacked vertically | 1 Chiseled Tuff |
| 2 Tuff Brick Slabs stacked vertically | 1 Chiseled Tuff Bricks |

[Polishing][crafting-polished-tuff] · [Bricks][crafting-tuff-bricks] · [Chiseled Tuff][crafting-chiseled-tuff] · [Chiseled Tuff Bricks][crafting-chiseled-tuff-bricks]

Use the [shared stair, slab and wall patterns](Stone.md#crafting-yields) for the shape recipes. Use the matching full-block finish: six in a 1/2/3 stair pattern, three in one row for slabs, or two complete rows for walls.

| Matching full-block material | 6 → 4 stairs | 3 → 6 slabs | 6 → 6 walls |
| --- | --- | --- | --- |
| Tuff | [Recipe][crafting-tuff-stairs] | [Recipe][crafting-tuff-slab] | [Recipe][crafting-tuff-wall] |
| Polished Tuff | [Recipe][crafting-polished-tuff-stairs] | [Recipe][crafting-polished-tuff-slab] | [Recipe][crafting-polished-tuff-wall] |
| Tuff Bricks | [Recipe][crafting-tuff-brick-stairs] | [Recipe][crafting-tuff-brick-slab] | [Recipe][crafting-tuff-brick-wall] |

The checked recipe set has **no smelting recipe for a Tuff-family result** and no recipe that manufactures the raw Tuff block. The construction chain starts with collected Tuff. Do not put bricks into a Furnace expecting an unregistered cracked finish. [Recipe loading][recipes] · [Registered forms][blocks]

## Stonecutting

A [Stonecutter](Stonecutter.md) consumes one input block per operation. Full blocks, stairs and walls in this table have output count **1**; slabs have count **2**. Raw Tuff can become Polished Tuff, Tuff Bricks or either chiseled block directly, including the listed shapes. [Menu consumption][stonecutter]

| One input block | Available outputs and count per operation |
| --- | --- |
| Tuff | [1 × Chiseled Tuff][stonecutting-chiseled-tuff-from-tuff-stonecutting]; [1 × Chiseled Tuff Bricks][stonecutting-chiseled-tuff-bricks-from-tuff-stonecutting]; [1 × Polished Tuff][stonecutting-polished-tuff-from-tuff-stonecutting]; [2 × Polished Tuff Slab][stonecutting-polished-tuff-slab-from-tuff-stonecutting]; [1 × Polished Tuff Stairs][stonecutting-polished-tuff-stairs-from-tuff-stonecutting]; [1 × Polished Tuff Wall][stonecutting-polished-tuff-wall-from-tuff-stonecutting]; [2 × Tuff Brick Slab][stonecutting-tuff-brick-slab-from-tuff-stonecutting]; [1 × Tuff Brick Stairs][stonecutting-tuff-brick-stairs-from-tuff-stonecutting]; [1 × Tuff Brick Wall][stonecutting-tuff-brick-wall-from-tuff-stonecutting]; [1 × Tuff Bricks][stonecutting-tuff-bricks-from-tuff-stonecutting]; [2 × Tuff Slab][stonecutting-tuff-slab-from-tuff-stonecutting]; [1 × Tuff Stairs][stonecutting-tuff-stairs-from-tuff-stonecutting]; [1 × Tuff Wall][stonecutting-tuff-wall-from-tuff-stonecutting] |
| Polished Tuff | [1 × Chiseled Tuff Bricks][stonecutting-chiseled-tuff-bricks-from-polished-tuff-stonecutting]; [2 × Polished Tuff Slab][stonecutting-polished-tuff-slab-from-polished-tuff-stonecutting]; [1 × Polished Tuff Stairs][stonecutting-polished-tuff-stairs-from-polished-tuff-stonecutting]; [1 × Polished Tuff Wall][stonecutting-polished-tuff-wall-from-polished-tuff-stonecutting]; [2 × Tuff Brick Slab][stonecutting-tuff-brick-slab-from-polished-tuff-stonecutting]; [1 × Tuff Brick Stairs][stonecutting-tuff-brick-stairs-from-polished-tuff-stonecutting]; [1 × Tuff Brick Wall][stonecutting-tuff-brick-wall-from-polished-tuff-stonecutting]; [1 × Tuff Bricks][stonecutting-tuff-bricks-from-polished-tuff-stonecutting] |
| Tuff Bricks | [1 × Chiseled Tuff Bricks][stonecutting-chiseled-tuff-bricks-from-tuff-bricks-stonecutting]; [2 × Tuff Brick Slab][stonecutting-tuff-brick-slab-from-tuff-bricks-stonecutting]; [1 × Tuff Brick Stairs][stonecutting-tuff-brick-stairs-from-tuff-bricks-stonecutting]; [1 × Tuff Brick Wall][stonecutting-tuff-brick-wall-from-tuff-bricks-stonecutting] |

**Chiseled Tuff and Chiseled Tuff Bricks are not stonecutting inputs** in the checked bundled data. There is no reverse stonecutting route from Polished Tuff to raw Tuff, or from bricks to the earlier finishes. Choose the output before converting a large supply. [Recipe loading and selection][recipes] [stonecutter]

For a small build, **12 Tuff can make 12 Tuff Brick Stairs by stonecutting**. The Crafting Table stair recipe needs **18 Tuff Bricks for 12 stairs**. This comparison follows the exact recipes; it is an untested build-planning example. [Direct cut][stonecutting-tuff-brick-stairs-from-tuff-stonecutting] · [Crafted stairs][crafting-tuff-brick-stairs]

## Placement and properties

Tuff full blocks, including both chiseled finishes, use ordinary Block placement with **no selectable facing or pillar axis**. They remain placed when a support underneath is removed. The shaped forms use the [shared stair/slab/wall rules](Stone.md#placing-shaped-blocks), including automatic stair corners, wall connections, and waterlogging of stairs, walls and single slabs. Double slabs use the same two-item collection rule described above. [Registered classes][blocks] · [Base behavior][properties] · [Shape classes][slabs] [stairs] [walls]

All 14 forms have **hardness 1.5** and **blast resistance 6**, inherited from the base Tuff registration. Their note-block instrument is the bass drum. These values are block properties, not timed mining measurements. [Registration and copied properties][blocks] [properties]

Tuff is not included in the ordinary Furnace's stone-crafting-materials tag. Its decorative recipes do not make it a universal Cobblestone substitute. [Material tag][materials] · [Furnace recipe](Furnace.md#crafting-and-mining)

## Sources and verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`. Checked all 14 block/item registrations and loot tables, correct-tool rules and break dispatch, exact crafting and stonecutting recipes, negative recipe claims against the bundled set, placed classes, and the active example generation route. No in-game crafting, mining, placement or world-generation test was run.

Related: [Tuff item](../items/Tuff.md) · [Deepslate](Deepslate.md) · [Granite, Diorite, Andesite and Calcite](DecorativeStone.md) · [Stone](Stone.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/Items.java
[presets]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java#L120-L135
[plains]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/biome/plains.json#L22-L45
[placement-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/placed_feature/ore_tuff.json
[deposit-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/worldgen/configured_feature/ore_tuff.json
[base-stone]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/base_stone_overworld.json
[features]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[ore-feature]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java
[placed-feature]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java
[biome-generation]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/tuff.json
[loot-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff.json
[loot-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/tuff_bricks.json
[loot-chiseled-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/chiseled_tuff.json
[loot-chiseled-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/chiseled_tuff_bricks.json
[loot-tuff-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/tuff_stairs.json
[loot-polished-tuff-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff_wall.json
[loot-tuff-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/tuff_slab.json
[loot-polished-tuff-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff_slab.json
[loot-tuff-brick-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/tuff_brick_slab.json
[crafting-chiseled-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/chiseled_tuff.json
[crafting-chiseled-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/chiseled_tuff_bricks.json
[crafting-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_tuff.json
[crafting-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_bricks.json
[crafting-tuff-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_stairs.json
[crafting-tuff-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_slab.json
[crafting-tuff-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_wall.json
[crafting-polished-tuff-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_tuff_stairs.json
[crafting-polished-tuff-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_tuff_slab.json
[crafting-polished-tuff-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/polished_tuff_wall.json
[crafting-tuff-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_brick_stairs.json
[crafting-tuff-brick-slab]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_brick_slab.json
[crafting-tuff-brick-wall]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/crafting/tuff_brick_wall.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-chiseled-tuff-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_from_tuff_stonecutting.json
[stonecutting-chiseled-tuff-bricks-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_bricks_from_tuff_stonecutting.json
[stonecutting-polished-tuff-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_from_tuff_stonecutting.json
[stonecutting-polished-tuff-slab-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_slab_from_tuff_stonecutting.json
[stonecutting-polished-tuff-stairs-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_stairs_from_tuff_stonecutting.json
[stonecutting-polished-tuff-wall-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_wall_from_tuff_stonecutting.json
[stonecutting-tuff-brick-slab-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_slab_from_tuff_stonecutting.json
[stonecutting-tuff-brick-stairs-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_stairs_from_tuff_stonecutting.json
[stonecutting-tuff-brick-wall-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_wall_from_tuff_stonecutting.json
[stonecutting-tuff-bricks-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_bricks_from_tuff_stonecutting.json
[stonecutting-tuff-slab-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_slab_from_tuff_stonecutting.json
[stonecutting-tuff-stairs-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_stairs_from_tuff_stonecutting.json
[stonecutting-tuff-wall-from-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_wall_from_tuff_stonecutting.json
[stonecutting-chiseled-tuff-bricks-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_bricks_from_polished_tuff_stonecutting.json
[stonecutting-polished-tuff-slab-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_slab_from_polished_tuff_stonecutting.json
[stonecutting-polished-tuff-stairs-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_stairs_from_polished_tuff_stonecutting.json
[stonecutting-polished-tuff-wall-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_wall_from_polished_tuff_stonecutting.json
[stonecutting-tuff-brick-slab-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_slab_from_polished_tuff_stonecutting.json
[stonecutting-tuff-brick-stairs-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_stairs_from_polished_tuff_stonecutting.json
[stonecutting-tuff-brick-wall-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_wall_from_polished_tuff_stonecutting.json
[stonecutting-tuff-bricks-from-polished-tuff-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_bricks_from_polished_tuff_stonecutting.json
[stonecutting-chiseled-tuff-bricks-from-tuff-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_bricks_from_tuff_bricks_stonecutting.json
[stonecutting-tuff-brick-slab-from-tuff-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_slab_from_tuff_bricks_stonecutting.json
[stonecutting-tuff-brick-stairs-from-tuff-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_stairs_from_tuff_bricks_stonecutting.json
[stonecutting-tuff-brick-wall-from-tuff-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_wall_from_tuff_bricks_stonecutting.json
[properties]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/StairBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/WallBlock.java
[materials]: https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json
