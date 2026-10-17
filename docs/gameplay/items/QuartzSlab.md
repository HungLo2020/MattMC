# Quartz Slab

**Quartz Slab** (`minecraft:quartz_slab`) supplies half-height surfaces for quartz floors, paths and trim. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **3 accepted quartz blocks in one row → 6 Quartz Slabs**. Each slot independently accepts [Block of Quartz](BlockOfQuartz.md), [Chiseled Quartz Block](ChiseledQuartzBlock.md) or [Quartz Pillar](QuartzPillar.md), so they can be mixed. Quartz Bricks and Smooth Quartz Block are excluded. [Crafting recipe][craft] · [Slot matching][ingredient] [pattern][]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 ordinary Block of Quartz → 2 Quartz Slabs**. Chiseled blocks and pillars are not substitutes for this cutting recipe. [Cutting recipe][cut]

See the [canonical shape recipes](../blocks/Quartz.md#crafting-stairs-and-slabs) and [cutting table](../blocks/Quartz.md#stonecutting).

Mine with an **unbroken pickaxe**, including Wood, to recover **one matching slab from a single slab or two from a double slab**. Hand breaking does not collect them. Silk Touch is unnecessary and Fortune adds no multiplier; explosions apply separate decay. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

Place half-height floors or combine identical slabs for full-height geometry. Two ordinary Quartz Slab items also craft one [Chiseled Quartz Block](ChiseledQuartzBlock.md) when stacked vertically in the crafting grid. [Chiseled recipe][chiseled]

## Behavior

Place it in the upper or lower half of a block space. Adding **the same Quartz Slab item** to the free half makes a double slab; the placed block remains `minecraft:quartz_slab`, rather than becoming a Block of Quartz. Single slabs can waterlog, and making a double slab clears waterlogging. Follow the [shared slab placement rules](../blocks/Stone.md#placing-shaped-blocks). [Slab behavior][shape]

## Notes

Ordinary Quartz Slab and [Smooth Quartz Slab](SmoothQuartzSlab.md) are separate items with different recipe inputs. Use the [Quartz guide](../blocks/Quartz.md#registered-variants) to compare the family.

Related: [Block of Quartz](BlockOfQuartz.md) · [Chiseled Quartz Block](ChiseledQuartzBlock.md) · [Quartz Stairs](QuartzStairs.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L406-L406
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3897-L3901
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_slab.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_slab_from_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/quartz_slab.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[ingredient]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L32-L66
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[chiseled]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_quartz_block.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
