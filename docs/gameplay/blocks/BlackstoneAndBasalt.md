# Blackstone and Basalt

**Blackstone** (`minecraft:blackstone`) supplies a dark masonry family with polished, brick, chiseled and cracked finishes. **Basalt** (`minecraft:basalt`) is a separate pillar-like building block with polished and smooth alternatives. Gilded Blackstone has its own drop rules and is covered separately below. [Registrations][blocks] [items]

## Mining and ordinary drops

Use an **unbroken pickaxe**, including Wood, to collect the blocks and shapes covered here. All require the correct tool, are pickaxe-tagged and have no higher material-tier restriction. Hand breaking does not collect them. [Registry][blocks] · [Tags][pickaxe] [wood-denials][] [stone-tier][] [iron-tier][] [diamond-tier][] · [Tool and break checks][tool-material] [player-tool][] [broken-tool][] [break-dispatch]

Except for [Gilded Blackstone](#gilded-blackstone), ordinary full blocks, stairs and walls drop **one matching item**. Single slabs give **one slab**; double slabs give **two matching slabs**. Silk Touch is unnecessary, Fortune adds no multiplier, and mining a polished or brick block preserves its finish. Explosions have separate survival/decay rules. [Ordinary full-block loot][loot-blackstone] [loot-polished-blackstone][] [loot-polished-blackstone-bricks][] [loot-cracked-polished-blackstone-bricks][] [loot-chiseled-polished-blackstone][] [loot-basalt][] [loot-polished-basalt][] [loot-smooth-basalt] · [Shape examples][loot-blackstone-stairs] [loot-polished-blackstone-brick-wall] · [Slab tables][loot-blackstone-slab] [loot-polished-blackstone-slab][] [loot-polished-blackstone-brick-slab][]

## Finding blackstone and basalt

**Basalt Deltas** are a checked natural route for both materials: the normal Nether's surface settings select Basalt and Blackstone in this biome. This is a source-backed terrain example, not a best-height or per-chunk abundance claim. [Nether preset][nether-preset] · [Surface settings][nether-settings] · [Surface execution][terrain] [surface][] [native-surface][]

**Adult Piglin bartering** can also yield **8–16 Blackstone** when that entry is selected. Interacting with an eligible adult Piglin using a Gold Ingot starts its admiration/barter flow; the returned item is random, so an ingot does not guarantee Blackstone. [Barter loot][barter-loot] · [Active Piglin interaction and brain][piglin] [piglin-ai] · [Completion behavior][barter-completion]

## Blackstone variants

All three construction finishes have stairs, slabs and walls. [Registered forms][blocks] [items]

| Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- |
| [Blackstone](../items/Blackstone.md) | [Stairs](../items/BlackstoneStairs.md) | [Slab](../items/BlackstoneSlab.md) | [Wall](../items/BlackstoneWall.md) |
| [Polished Blackstone](../items/PolishedBlackstone.md) | [Stairs](../items/PolishedBlackstoneStairs.md) | [Slab](../items/PolishedBlackstoneSlab.md) | [Wall](../items/PolishedBlackstoneWall.md) |
| [Polished Blackstone Bricks](../items/PolishedBlackstoneBricks.md) | [Stairs](../items/PolishedBlackstoneBrickStairs.md) | [Slab](../items/PolishedBlackstoneBrickSlab.md) | [Wall](../items/PolishedBlackstoneBrickWall.md) |

Two additional full blocks are [Chiseled Polished Blackstone](../items/ChiseledPolishedBlackstone.md) and [Cracked Polished Blackstone Bricks](../items/CrackedPolishedBlackstoneBricks.md). There are no chiseled or cracked shaped subfamilies. Gilded Blackstone is not an ingredient for the ordinary finishing chain. [Registry][blocks]

### Crafting and smelting

| Ingredients and arrangement | Result |
| --- | --- |
| 4 Blackstone in a 2 × 2 square | 4 Polished Blackstone |
| 4 Polished Blackstone in a 2 × 2 square | 4 Polished Blackstone Bricks |
| 2 Polished Blackstone Slabs stacked vertically | 1 Chiseled Polished Blackstone |
| Smelt 1 Polished Blackstone Bricks | 1 Cracked Polished Blackstone Bricks |

[Polishing][crafting-polished-blackstone] · [Bricks][crafting-polished-blackstone-bricks] · [Chiseled][crafting-chiseled-polished-blackstone] · [Cracked][smelting-cracked-polished-blackstone-bricks]

The cracking recipe uses a [Furnace](Furnace.md), takes **200 game ticks**, nominally **10 seconds at 20 ticks per second**, and specifies **0.1 recipe experience**. [Recipe][smelting-cracked-polished-blackstone-bricks] · [Active Furnace processing][furnace] [furnace-tick]

Shape recipes use the matching full-block finish and the [shared stair, slab and wall patterns](Stone.md#crafting-yields).

| Matching full-block material | 6 → 4 stairs | 3 → 6 slabs | 6 → 6 walls |
| --- | --- | --- | --- |
| Blackstone | [Recipe][crafting-blackstone-stairs] | [Recipe][crafting-blackstone-slab] | [Recipe][crafting-blackstone-wall] |
| Polished Blackstone | [Recipe][crafting-polished-blackstone-stairs] | [Recipe][crafting-polished-blackstone-slab] | [Recipe][crafting-polished-blackstone-wall] |
| Polished Blackstone Bricks | [Recipe][crafting-polished-blackstone-brick-stairs] | [Recipe][crafting-polished-blackstone-brick-slab] | [Recipe][crafting-polished-blackstone-brick-wall] |

Ordinary Blackstone is accepted by the material tags used for a [Furnace](Furnace.md#crafting-and-mining) and [Stone Pickaxe](../items/StonePickaxe.md). Polished Blackstone, Gilded Blackstone and Basalt are not in those tags. The registered Polished Blackstone button and pressure plate are separate redstone controls; they are not part of the masonry shape table. [Material tags][materials] [tool-materials] · [Control registrations][blocks]

### Stonecutting

The [Stonecutter](Stonecutter.md) consumes one input per operation. Full blocks, stairs and walls below have output count **1**, and slabs have count **2**. Cutting saves material for stairs compared with six blocks for four crafted stairs, and can skip intermediate Blackstone finishes. [Menu consumption][stonecutter]

| One input block | Available outputs and count per operation |
| --- | --- |
| Blackstone | [2 × Blackstone Slab][stonecutting-blackstone-slab-from-blackstone-stonecutting]; [1 × Blackstone Stairs][stonecutting-blackstone-stairs-from-blackstone-stonecutting]; [1 × Blackstone Wall][stonecutting-blackstone-wall-from-blackstone-stonecutting]; [1 × Chiseled Polished Blackstone][stonecutting-chiseled-polished-blackstone-from-blackstone-stonecutting]; [1 × Polished Blackstone][stonecutting-polished-blackstone-from-blackstone-stonecutting]; [2 × Polished Blackstone Brick Slab][stonecutting-polished-blackstone-brick-slab-from-blackstone-stonecutting]; [1 × Polished Blackstone Brick Stairs][stonecutting-polished-blackstone-brick-stairs-from-blackstone-stonecutting]; [1 × Polished Blackstone Brick Wall][stonecutting-polished-blackstone-brick-wall-from-blackstone-stonecutting]; [1 × Polished Blackstone Bricks][stonecutting-polished-blackstone-bricks-from-blackstone-stonecutting]; [2 × Polished Blackstone Slab][stonecutting-polished-blackstone-slab-from-blackstone-stonecutting]; [1 × Polished Blackstone Stairs][stonecutting-polished-blackstone-stairs-from-blackstone-stonecutting]; [1 × Polished Blackstone Wall][stonecutting-polished-blackstone-wall-from-blackstone-stonecutting] |
| Polished Blackstone | [1 × Chiseled Polished Blackstone][stonecutting-chiseled-polished-blackstone-from-polished-blackstone-stonecutting]; [2 × Polished Blackstone Brick Slab][stonecutting-polished-blackstone-brick-slab-from-polished-blackstone-stonecutting]; [1 × Polished Blackstone Brick Stairs][stonecutting-polished-blackstone-brick-stairs-from-polished-blackstone-stonecutting]; [1 × Polished Blackstone Brick Wall][stonecutting-polished-blackstone-brick-wall-from-polished-blackstone-stonecutting]; [1 × Polished Blackstone Bricks][stonecutting-polished-blackstone-bricks-from-polished-blackstone-stonecutting]; [2 × Polished Blackstone Slab][stonecutting-polished-blackstone-slab-from-polished-blackstone-stonecutting]; [1 × Polished Blackstone Stairs][stonecutting-polished-blackstone-stairs-from-polished-blackstone-stonecutting]; [1 × Polished Blackstone Wall][stonecutting-polished-blackstone-wall-from-polished-blackstone-stonecutting] |
| Polished Blackstone Bricks | [2 × Polished Blackstone Brick Slab][stonecutting-polished-blackstone-brick-slab-from-polished-blackstone-bricks-stonecutting]; [1 × Polished Blackstone Brick Stairs][stonecutting-polished-blackstone-brick-stairs-from-polished-blackstone-bricks-stonecutting]; [1 × Polished Blackstone Brick Wall][stonecutting-polished-blackstone-brick-wall-from-polished-blackstone-bricks-stonecutting] |

The checked recipes provide no reverse cutting route from bricks to polished or raw Blackstone. Cracked and Chiseled Polished Blackstone are not stonecutting inputs. [Recipe loading and menu selection][recipes] [stonecutter]

## Basalt variants and orientation

| Block | Collection or conversion | Placement |
| --- | --- | --- |
| [Basalt](../items/Basalt.md) | Mine existing Basalt with a pickaxe | Clicked face chooses its axis |
| [Polished Basalt](../items/PolishedBasalt.md) | 4 Basalt in a 2 × 2 square → 4, or stonecut 1 Basalt → 1 | Clicked face chooses its axis |
| [Smooth Basalt](../items/SmoothBasalt.md) | Smelt 1 Basalt → 1; also found in the checked geode shell route | Ordinary full block, without an axis property |

[Registry][blocks] · [Polishing][crafting-polished-basalt] · [Stonecutting][stonecutting-polished-basalt-from-basalt-stonecutting] · [Smoothing][smelting-smooth-basalt] · [Axis placement][pillar]

For Basalt and Polished Basalt, clicking a **top/bottom face** sets a vertical axis; an **east/west side** sets an east–west axis; a **north/south side** sets a north–south axis. The item does not preserve its former orientation when mined. Smooth Basalt is registered as a plain Block despite sharing the base material properties. No Basalt stairs, slabs or walls are registered in these three finishes. [Placement][pillar] · [Block classes][blocks] · [Loot][loot-basalt] [loot-polished-basalt][] [loot-smooth-basalt][]

Smoothing Basalt takes **200 game ticks**, nominally **10 seconds**, and specifies **0.1 recipe experience**. The input is ordinary Basalt, not Polished Basalt. Smooth Basalt also forms the **outer shell of Amethyst Geodes**: the checked Plains biome includes the placed geode feature, whose active generator uses that outer-layer provider. [Smelting][smelting-smooth-basalt] · [Biome and feature wiring][plains] [geode-placement][] [geode-config][] · [Active geode placement][features] [geode-feature]

### Making basalt with lava

The active liquid callback can convert lava to Basalt when **Soul Soil is directly underneath the lava block** and **Blue Ice is adjacent on a side or above it**. Soul Sand and ordinary Ice are not the named blocks. The conversion replaces the lava position with Basalt; it can consume a lava source as well as flowing lava. [Placement/neighbor callbacks and exact block checks][liquid]

For a small, **untested** collection setup, put Soul Soil under the intended conversion cell, Blue Ice beside that cell, and let lava flow into it from a separate source. Keep water away from the lava contact area, because water/lava checks can instead produce Stone, Cobblestone or Obsidian. Mine the resulting Basalt with a pickaxe. The callback verifies the conversion rule; this guide does not claim a tested layout or production rate. [Basalt conversion][liquid] · [Related water/lava rules](Stone.md#water-and-lava)

## Gilded blackstone

`minecraft:gilded_blackstone` is a separate decorative block with a Gold Nugget drop branch. The checked [Bastion Remnant](../structures/BastionRemnant.md) route can produce it: a registered Bastion start template contains Blackstone, and its active degradation processor can convert Blackstone to Gilded Blackstone. Bastion placement is limited by its biome tag and is not a guarantee in every Nether area. [Structure set][nether-structures] · [Bastion configuration and biomes][bastion] [bastion-biomes] · [Start pool][bastion-starts] · [Template][bastion-template] · [Processor][bastion-processor] · [Jigsaw/template/processor dispatch][jigsaw] [single-pool][] [template][] [rule-processor]

With a correct pickaxe, the normal mining outcomes are:

| Tool enchantment | Chance of 2–5 Gold Nuggets | Otherwise |
| --- | ---: | --- |
| No Fortune, no Silk Touch | 10% | 1 Gilded Blackstone |
| Fortune I, no Silk Touch | About 14.29% | 1 Gilded Blackstone |
| Fortune II, no Silk Touch | 25% | 1 Gilded Blackstone |
| Fortune III, no Silk Touch | 100% | No block-item outcome |
| Silk Touch | 0% | 1 Gilded Blackstone |

Fortune changes the chance of the nugget branch, not its **2–5** count. Silk Touch is checked first and is the reliable way to preserve the decorative block. The nuggets replace the block drop; they are not an extra reward alongside it. No crafting, smelting or stonecutting recipe for Gilded Blackstone was found in the bundled recipe scan. [Exact loot alternatives][loot-gilded-blackstone] · [Fortune chance lookup][fortune-table]

**Mining Gilded Blackstone can anger nearby Piglins, including when using Silk Touch.** It is in the guarded-block tag, and the player-break callback calls the Piglin anger routine independently of the loot choice. [Guarded tag][guarded] · [Break callback][break-anger] · [Piglin anger handling][piglin-ai]

## Placement and properties

Blackstone full blocks have no axis or facing control. Their stairs, slabs and walls use the [shared shape, corner and waterlogging rules](Stone.md#placing-shaped-blocks). All these construction blocks remain placed when the support beneath them is removed. [Classes][blocks] · [Base behavior][properties] · [Shapes][stairs] [slabs][] [walls][]

| Full-block material | Hardness | Blast resistance |
| --- | ---: | ---: |
| Blackstone and Gilded Blackstone | 1.5 | 6 |
| Polished Blackstone | 2 | 6 |
| Polished Blackstone Bricks, cracked bricks and Chiseled Polished Blackstone | 1.5 | 6 |
| Basalt, Polished Basalt and Smooth Basalt | 1.25 | 4.2 |

The Blackstone Slab and Polished Blackstone Brick Slab registrations specifically use **hardness 2**, rather than their full blocks' 1.5. The other masonry shapes inherit their base finish's properties. These materials use the bass-drum note-block instrument. Hardness is not a timed mining result. [Registrations and inherited properties][blocks] [properties]

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. Checked the 18 covered block/item registrations and loot tables, recipes, tool gates, Basalt placement and liquid callbacks, surface/geode/Bastion wiring, barter dispatch, and Gilded Blackstone loot and Piglin provocation. No in-game mining, crafting, placement, bartering, world-generation or Basalt-generator test was run.

Related: [Blackstone item](../items/Blackstone.md) · [Basalt item](../items/Basalt.md) · [Gilded Blackstone item](../items/GildedBlackstone.md) · [Nether Bricks](NetherBricks.md) · [Stone](Stone.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/Items.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/blackstone.json
[loot-polished-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone.json
[loot-polished-blackstone-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_bricks.json
[loot-cracked-polished-blackstone-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/cracked_polished_blackstone_bricks.json
[loot-chiseled-polished-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/chiseled_polished_blackstone.json
[loot-basalt]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/basalt.json
[loot-polished-basalt]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/polished_basalt.json
[loot-smooth-basalt]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_basalt.json
[loot-blackstone-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/blackstone_stairs.json
[loot-polished-blackstone-brick-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_brick_wall.json
[loot-blackstone-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/blackstone_slab.json
[loot-polished-blackstone-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_slab.json
[loot-polished-blackstone-brick-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_brick_slab.json
[nether-preset]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java#L94-L114
[nether-settings]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json
[terrain]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java
[surface]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java
[native-surface]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java
[barter-loot]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json
[piglin]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java
[barter-completion]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/StopHoldingItemIfNoLongerAdmiring.java
[crafting-polished-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone.json
[crafting-polished-blackstone-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_bricks.json
[crafting-chiseled-polished-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/chiseled_polished_blackstone.json
[smelting-cracked-polished-blackstone-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/cracked_polished_blackstone_bricks.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L204
[crafting-blackstone-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/blackstone_stairs.json
[crafting-blackstone-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/blackstone_slab.json
[crafting-blackstone-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/blackstone_wall.json
[crafting-polished-blackstone-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_stairs.json
[crafting-polished-blackstone-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_slab.json
[crafting-polished-blackstone-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_wall.json
[crafting-polished-blackstone-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_brick_stairs.json
[crafting-polished-blackstone-brick-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_brick_slab.json
[crafting-polished-blackstone-brick-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_brick_wall.json
[materials]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json
[tool-materials]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-blackstone-slab-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/blackstone_slab_from_blackstone_stonecutting.json
[stonecutting-blackstone-stairs-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/blackstone_stairs_from_blackstone_stonecutting.json
[stonecutting-blackstone-wall-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/blackstone_wall_from_blackstone_stonecutting.json
[stonecutting-chiseled-polished-blackstone-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_polished_blackstone_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-slab-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_slab_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-stairs-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_stairs_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-wall-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_wall_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-bricks-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_bricks_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-slab-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_slab_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-stairs-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_stairs_from_blackstone_stonecutting.json
[stonecutting-polished-blackstone-wall-from-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_wall_from_blackstone_stonecutting.json
[stonecutting-chiseled-polished-blackstone-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_polished_blackstone_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-slab-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_slab_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-stairs-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_stairs_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-wall-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_wall_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-bricks-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_bricks_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-slab-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_slab_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-stairs-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_stairs_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-wall-from-polished-blackstone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_wall_from_polished_blackstone_stonecutting.json
[stonecutting-polished-blackstone-brick-slab-from-polished-blackstone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_slab_from_polished_blackstone_bricks_stonecutting.json
[stonecutting-polished-blackstone-brick-stairs-from-polished-blackstone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_stairs_from_polished_blackstone_bricks_stonecutting.json
[stonecutting-polished-blackstone-brick-wall-from-polished-blackstone-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_brick_wall_from_polished_blackstone_bricks_stonecutting.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[crafting-polished-basalt]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/polished_basalt.json
[stonecutting-polished-basalt-from-basalt-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/polished_basalt_from_basalt_stonecutting.json
[smelting-smooth-basalt]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/smooth_basalt.json
[pillar]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[plains]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/biome/plains.json#L22-L45
[geode-placement]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/placed_feature/amethyst_geode.json
[geode-config]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/configured_feature/amethyst_geode.json
[features]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[geode-feature]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java
[liquid]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/LiquidBlock.java
[nether-structures]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/structure_set/nether_complexes.json
[bastion]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/structure/bastion_remnant.json
[bastion-biomes]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/bastion_remnant.json
[bastion-starts]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/template_pool/bastion/starts.json
[bastion-template]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/structure/bastion/bridge/starting_pieces/entrance_base.nbt
[bastion-processor]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/processor_list/bastion_generic_degradation.json
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java
[single-pool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java
[template]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java
[rule-processor]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/RuleProcessor.java
[loot-gilded-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/gilded_blackstone.json
[fortune-table]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[guarded]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[break-anger]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Block.java#L480-L489
[properties]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/StairBlock.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WallBlock.java
