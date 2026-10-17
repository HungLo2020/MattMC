# Chiseled Quartz Block

**Chiseled Quartz Block** (`minecraft:chiseled_quartz_block`) is a patterned full block for quartz construction. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **2 ordinary [Quartz Slabs](QuartzSlab.md) stacked vertically → 1 Chiseled Quartz Block**. Smooth Quartz Slabs do not substitute. [Crafting recipe][craft]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 [Block of Quartz](BlockOfQuartz.md) → 1 Chiseled Quartz Block**. [Cutting recipe][cut]

The [Quartz guide](../blocks/Quartz.md#crafting-and-smoothing) keeps the family’s finish recipes together.

Mine a placed block with an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier; explosions have a separate survival condition. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

Build with it, or use it in the [ordinary Quartz Stair and Slab recipes](../blocks/Quartz.md#crafting-stairs-and-slabs). Each occupied slot accepts a Chiseled Quartz Block, Block of Quartz or Quartz Pillar, so those inputs can be mixed. The ordinary shape stonecutting recipes accept only Block of Quartz. [Stair recipe][ordinary-stairs] · [Slab recipe][ordinary-slabs] · [Cutting inputs][cut-stairs] [cut-slabs][]

## Behavior

Use the item to place a full decorative block. It has no selectable facing or pillar axis; see [quartz placement and properties](../blocks/Quartz.md#pillar-orientation-and-placement). [Block registration][blocks]

## Notes

This item places the chiseled finish and drops as that finish when correctly mined; it does not break down into Nether Quartz crystals. See the [registered quartz variants](../blocks/Quartz.md#registered-variants). [Loot][loot]

Related: [Quartz Slab](QuartzSlab.md) · [Block of Quartz](BlockOfQuartz.md) · [Quartz Pillar](QuartzPillar.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L630-L630
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2889-L2892
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_quartz_block.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_quartz_block_from_quartz_block_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/chiseled_quartz_block.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[ordinary-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_stairs.json
[ordinary-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_slab.json
[cut-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_stairs_from_quartz_block_stonecutting.json
[cut-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_slab_from_stonecutting.json
