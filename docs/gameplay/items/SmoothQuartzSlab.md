# Smooth Quartz Slab

**Smooth Quartz Slab** (`minecraft:smooth_quartz_slab`) makes half-height floors, roofs and details in the smooth quartz finish. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **3 [Smooth Quartz Blocks](SmoothQuartzBlock.md) in one row → 6 Smooth Quartz Slabs**. [Crafting recipe][craft]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 Smooth Quartz Block → 2 Smooth Quartz Slabs**. [Cutting recipe][cut]

Both routes require the smooth full block. Ordinary Block of Quartz, chiseled blocks and pillars do not substitute; see the [shape recipes](../blocks/Quartz.md#crafting-stairs-and-slabs) and [stonecutting table](../blocks/Quartz.md#stonecutting).

Mine with an **unbroken pickaxe**, including Wood, to recover **one matching slab from a single slab or two from a double slab**. Hand breaking does not collect them. Silk Touch is unnecessary and Fortune adds no multiplier; explosions apply separate decay. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

Use it for half-height surfaces or full-height double slabs. To make a [Chiseled Quartz Block](ChiseledQuartzBlock.md), use ordinary [Quartz Slabs](QuartzSlab.md): the chiseled recipe does not accept Smooth Quartz Slabs. [Chiseled recipe][chiseled]

## Behavior

Place it in the upper or lower half of a block space. Adding **the same Smooth Quartz Slab item** to the free half makes a double slab. It remains `minecraft:smooth_quartz_slab`, rather than becoming Smooth Quartz Block, and still drops two slabs when correctly mined. Single slabs can waterlog; making a double slab clears waterlogging. Follow the [shared slab placement rules](../blocks/Stone.md#placing-shaped-blocks). [Slab behavior][shape] · [Loot][loot]

## Notes

Smooth Quartz Slab and ordinary Quartz Slab are separate items. The [Quartz guide](../blocks/Quartz.md#registered-variants) lists both forms and their full-block materials.

Related: [Smooth Quartz Block](SmoothQuartzBlock.md) · [Smooth Quartz Stairs](SmoothQuartzStairs.md) · [Quartz Slab](QuartzSlab.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L981-L981
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5263-L5263
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_quartz_slab.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/smooth_quartz_slab_from_smooth_quartz_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_quartz_slab.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[chiseled]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_quartz_block.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
