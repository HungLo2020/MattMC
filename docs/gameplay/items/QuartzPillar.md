# Quartz Pillar

**Quartz Pillar** (`minecraft:quartz_pillar`) is the quartz building block whose stripes can run vertically or horizontally. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **2 [Blocks of Quartz](BlockOfQuartz.md) stacked vertically → 2 Quartz Pillars**. [Crafting recipe][craft]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 Block of Quartz → 1 Quartz Pillar**. [Cutting recipe][cut]

Both routes use ordinary Blocks of Quartz; see the [family’s finish recipes](../blocks/Quartz.md#crafting-and-smoothing).

Mine a placed block with an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier; explosions have a separate survival condition. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

Use pillars as columns or horizontal beams. They also substitute for base or chiseled quartz in the [ordinary Quartz Stair and Slab crafting recipes](../blocks/Quartz.md#crafting-stairs-and-slabs), including mixed inputs in one recipe. The corresponding stonecutting recipes accept only Block of Quartz. [Stair recipe][ordinary-stairs] · [Slab recipe][ordinary-slabs] · [Cutting inputs][cut-stairs] [cut-slabs][]

## Behavior

The **clicked face sets the pillar’s axis**: a top or bottom face gives a vertical pillar, an east/west face gives an east–west pillar, and a north/south face gives a north–south pillar. Ordinary mining does not save that axis in the dropped item; choose the orientation again when placing it. See [pillar orientation and placement](../blocks/Quartz.md#pillar-orientation-and-placement). [Pillar behavior][shape] · [Loot][loot]

## Notes

Quartz Pillar is the axis-selectable full block in the [quartz family](../blocks/Quartz.md#registered-variants). Chiseled Quartz Block, Quartz Bricks and Smooth Quartz Block have no selectable pillar axis. [Registrations][family-blocks]

Related: [Block of Quartz](BlockOfQuartz.md) · [Quartz Stairs](QuartzStairs.md) · [Quartz Slab](QuartzSlab.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L633-L633
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2893-L2897
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_pillar.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_pillar_from_quartz_block_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/quartz_pillar.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[ordinary-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_stairs.json
[ordinary-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_slab.json
[cut-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_stairs_from_quartz_block_stonecutting.json
[cut-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_slab_from_stonecutting.json
[family-blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L55
