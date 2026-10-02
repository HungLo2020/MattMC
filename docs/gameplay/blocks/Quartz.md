# Quartz construction

**Block of Quartz** (`minecraft:quartz_block`) is the starting block for a pale construction family: chiseled blocks, pillars, bricks, smooth blocks and selected stairs/slabs. The small [Nether Quartz item](../items/NetherQuartz.md) is a separate ingredient. Mining a quartz building block does not unpack it into quartz crystals. [Block and item registrations][blocks] [items] · [Building-block loot][loot-quartz-block]

## Obtaining and mining

Craft **4 Nether Quartz in a 2 × 2 square → 1 Block of Quartz**. Follow [Ore Resources](OreResources.md#bring-a-suitable-pickaxe) for Nether Quartz Ore mining and its tool, Silk Touch and Fortune rules; this guide covers the resulting building materials. [Construction recipe][crafting-quartz-block]

Use an **unbroken pickaxe**, including Wood, to recover all nine placed quartz forms below. They require a correct tool, are pickaxe-tagged and have no higher material-tier restriction. Hand breaking does not collect them. [Registrations][blocks] · [Mining tags][pickaxe] [wood-denials] [stone-tier] [iron-tier] [diamond-tier] · [Tool and server checks][tool-material] [player-tool] [broken-tool] [break-dispatch]

Full blocks and stairs give **one matching item**. A single slab gives **one slab**; a double slab gives **two matching slabs**. Silk Touch is unnecessary and Fortune does not multiply these building-block drops. Explosions apply separate survival/decay conditions. [Full-block loot][loot-quartz-block] [loot-chiseled-quartz-block] [loot-quartz-pillar] [loot-quartz-bricks] [loot-smooth-quartz] · [Stair loot][loot-quartz-stairs] [loot-smooth-quartz-stairs] · [Slab loot][loot-quartz-slab] [loot-smooth-quartz-slab]

## Registered variants

| Block or shape | Registry ID |
| --- | --- |
| [Block of Quartz](../items/BlockOfQuartz.md) | `minecraft:quartz_block` |
| [Chiseled Quartz Block](../items/ChiseledQuartzBlock.md) | `minecraft:chiseled_quartz_block` |
| [Quartz Pillar](../items/QuartzPillar.md) | `minecraft:quartz_pillar` |
| [Quartz Bricks](../items/QuartzBricks.md) | `minecraft:quartz_bricks` |
| [Quartz Stairs](../items/QuartzStairs.md) | `minecraft:quartz_stairs` |
| [Quartz Slab](../items/QuartzSlab.md) | `minecraft:quartz_slab` |
| [Smooth Quartz Block](../items/SmoothQuartzBlock.md) | `minecraft:smooth_quartz` |
| [Smooth Quartz Stairs](../items/SmoothQuartzStairs.md) | `minecraft:smooth_quartz_stairs` |
| [Smooth Quartz Slab](../items/SmoothQuartzSlab.md) | `minecraft:smooth_quartz_slab` |

There is no registered Quartz Wall, Quartz Brick stair/slab/wall family, cracked quartz block or smooth pillar. Quartz Bricks is a full-block finish, not the ingredient for ordinary Quartz Stairs and Slabs. [Registry][blocks] [items] · [Shape ingredients][crafting-quartz-stairs] [crafting-quartz-slab]

## Crafting and smoothing

| Ingredients and arrangement | Result |
| --- | --- |
| 4 Nether Quartz in a 2 × 2 square | 1 Block of Quartz |
| 2 Blocks of Quartz stacked vertically | 2 Quartz Pillars |
| 2 Quartz Slabs stacked vertically | 1 Chiseled Quartz Block |
| 4 Blocks of Quartz in a 2 × 2 square | 4 Quartz Bricks |
| Smelt 1 Block of Quartz | 1 Smooth Quartz Block |

[Base block][crafting-quartz-block] · [Pillars][crafting-quartz-pillar] · [Chiseled][crafting-chiseled-quartz-block] · [Bricks][crafting-quartz-bricks] · [Smoothing][smelting-smooth-quartz]

Smoothing uses a [Furnace](Furnace.md), takes **200 game ticks**, nominally **10 seconds at 20 ticks per second**, and specifies **0.1 recipe experience**. Its input is the ordinary Block of Quartz, not a pillar, chiseled block, quartz crystal or Quartz Bricks. [Recipe][smelting-smooth-quartz] · [Active Furnace processing][furnace] [furnace-tick]

### Crafting stairs and slabs

Use the [shared stair/slab patterns](Stone.md#crafting-yields): six blocks in a 1/2/3 stair pattern, or three in one row for slabs.

| Result | Accepted ingredient in each occupied recipe slot | Yield |
| --- | --- | --- |
| Quartz Stairs | Block of Quartz, Chiseled Quartz Block or Quartz Pillar | 6 blocks → 4 stairs |
| Quartz Slab | Block of Quartz, Chiseled Quartz Block or Quartz Pillar | 3 blocks → 6 slabs |
| Smooth Quartz Stairs | Smooth Quartz Block only | 6 blocks → 4 stairs |
| Smooth Quartz Slab | Smooth Quartz Block only | 3 blocks → 6 slabs |

The three accepted ordinary quartz forms may be **mixed within one crafting recipe** because each occupied slot independently accepts any listed form. Quartz Bricks and Smooth Quartz are excluded from that ordinary ingredient list. [Exact recipes][crafting-quartz-stairs] [crafting-quartz-slab] [crafting-smooth-quartz-stairs] [crafting-smooth-quartz-slab] · [Ingredient and pattern matching][ingredient] [pattern]

## Stonecutting

The [Stonecutter](Stonecutter.md) consumes one input per operation. It gives **one full decorative block or stair**, or **two slabs**, for these checked inputs. [Menu consumption][stonecutter]

| One input block | Available outputs and count per operation |
| --- | --- |
| Block of Quartz | [1 × Chiseled Quartz Block][stonecutting-chiseled-quartz-block-from-quartz-block-stonecutting]; [1 × Quartz Bricks][stonecutting-quartz-bricks-from-quartz-block-stonecutting]; [1 × Quartz Pillar][stonecutting-quartz-pillar-from-quartz-block-stonecutting]; [2 × Quartz Slab][stonecutting-quartz-slab-from-stonecutting]; [1 × Quartz Stairs][stonecutting-quartz-stairs-from-quartz-block-stonecutting] |
| Smooth Quartz Block | [2 × Smooth Quartz Slab][stonecutting-smooth-quartz-slab-from-smooth-quartz-stonecutting]; [1 × Smooth Quartz Stairs][stonecutting-smooth-quartz-stairs-from-smooth-quartz-stonecutting] |

**Quartz Pillars, Chiseled Quartz Blocks and Quartz Bricks are not stonecutting inputs** in the checked bundled set. The broader crafting ingredient list does not carry over to the Stonecutter. There is also no reverse recipe that unpacks a quartz building block into Nether Quartz items. [Recipe loading and selection][recipes] [stonecutter]

For a small build, **four ordinary Blocks of Quartz make four Quartz Stairs by stonecutting**; crafting four stairs takes six accepted blocks. Keep unprocessed blocks if you want these flexible output choices. This is a source-based, untested planning example. [Cut stairs][stonecutting-quartz-stairs-from-quartz-block-stonecutting] · [Crafted stairs][crafting-quartz-stairs]

## Pillar orientation and placement

**Quartz Pillar** uses the clicked face's axis. Click a top/bottom face for a vertical pillar, an east/west side for an east–west pillar, or a north/south side for a north–south pillar. Its item does not retain a previously placed axis. The other quartz full blocks have no selectable facing or axis. [Pillar class and placement][blocks] [pillar] · [Pillar loot][loot-quartz-pillar]

Stairs and slabs follow the [shared placement, corner, double-slab and waterlogging rules](Stone.md#placing-shaped-blocks). These full blocks and shapes remain placed when support beneath them is removed. [Classes][blocks] · [Base survival behavior][properties] · [Shapes][stairs] [slabs]

## Block properties

| Forms | Hardness | Blast resistance |
| --- | ---: | ---: |
| Ordinary Block of Quartz, Chiseled Quartz Block, Quartz Pillar, Quartz Bricks and Quartz Stairs | 0.8 | 0.8 |
| Quartz Slab; Smooth Quartz Block, Stairs and Slab | 2 | 6 |

Quartz Slab has its own stronger property settings rather than inheriting the ordinary full block's values. These materials use the bass-drum note-block instrument. Hardness is not a measured break time. [Registrations][blocks] · [Single-value strength and copied properties][properties]

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. Checked all nine block/item registrations and loot tables, tool gates and server dispatch, every family recipe, mixed crafting ingredients versus literal stonecutting inputs, pillar placement and shape properties. No in-game crafting, mining, smelting or placement test was run.

Related: [Nether Quartz](../items/NetherQuartz.md) · [Block of Quartz](../items/BlockOfQuartz.md) · [End Stone and Purpur](EndStoneAndPurpur.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java
[loot-quartz-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/quartz_block.json
[crafting-quartz-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/quartz_block.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-chiseled-quartz-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/chiseled_quartz_block.json
[loot-quartz-pillar]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/quartz_pillar.json
[loot-quartz-bricks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/quartz_bricks.json
[loot-smooth-quartz]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/smooth_quartz.json
[loot-quartz-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/quartz_stairs.json
[loot-smooth-quartz-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/smooth_quartz_stairs.json
[loot-quartz-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/quartz_slab.json
[loot-smooth-quartz-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/smooth_quartz_slab.json
[crafting-quartz-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/quartz_stairs.json
[crafting-quartz-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/quartz_slab.json
[crafting-quartz-pillar]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/quartz_pillar.json
[crafting-chiseled-quartz-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/chiseled_quartz_block.json
[crafting-quartz-bricks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/quartz_bricks.json
[smelting-smooth-quartz]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/smelting/smooth_quartz.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L204
[crafting-smooth-quartz-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/smooth_quartz_stairs.json
[crafting-smooth-quartz-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/smooth_quartz_slab.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/crafting/Ingredient.java
[pattern]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-chiseled-quartz-block-from-quartz-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_quartz_block_from_quartz_block_stonecutting.json
[stonecutting-quartz-bricks-from-quartz-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/quartz_bricks_from_quartz_block_stonecutting.json
[stonecutting-quartz-pillar-from-quartz-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/quartz_pillar_from_quartz_block_stonecutting.json
[stonecutting-quartz-slab-from-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/quartz_slab_from_stonecutting.json
[stonecutting-quartz-stairs-from-quartz-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/quartz_stairs_from_quartz_block_stonecutting.json
[stonecutting-smooth-quartz-slab-from-smooth-quartz-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/smooth_quartz_slab_from_smooth_quartz_stonecutting.json
[stonecutting-smooth-quartz-stairs-from-smooth-quartz-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/smooth_quartz_stairs_from_smooth_quartz_stonecutting.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[pillar]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/StairBlock.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/SlabBlock.java
