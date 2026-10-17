# Quartz Bricks

**Quartz Bricks** (`minecraft:quartz_bricks`) is the brick-patterned full-block finish of quartz construction. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **4 [Blocks of Quartz](BlockOfQuartz.md) in a 2 × 2 square → 4 Quartz Bricks**. [Crafting recipe][craft]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 Block of Quartz → 1 Quartz Bricks**. [Cutting recipe][cut]

See the [family’s finish recipes](../blocks/Quartz.md#crafting-and-smoothing) and [stonecutting table](../blocks/Quartz.md#stonecutting).

Mine a placed block with an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier; explosions have a separate survival condition. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

Use it for full-block walls, floors and decorative brickwork. Keep ordinary Blocks of Quartz for [Quartz Stairs](QuartzStairs.md) and [Quartz Slabs](QuartzSlab.md): those crafting recipes accept base, chiseled or pillar blocks, and their cutting recipes accept only the base block. **Quartz Bricks is not an accepted input for those shapes.** [Stair recipe][ordinary-stairs] · [Slab recipe][ordinary-slabs] · [Cutting inputs][cut-stairs] [cut-slabs][]

## Behavior

Use the item to place a full decorative block. It has no selectable facing or pillar axis; see [quartz placement and properties](../blocks/Quartz.md#pillar-orientation-and-placement). [Block registration][blocks]

## Notes

The item places `minecraft:quartz_bricks` and ordinary mining preserves that finish. The [Quartz guide](../blocks/Quartz.md#registered-variants) distinguishes it from the other full-block finishes. [Loot][loot]

Related: [Block of Quartz](BlockOfQuartz.md) · [Chiseled Quartz Block](ChiseledQuartzBlock.md) · [Smooth Quartz Block](SmoothQuartzBlock.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L632-L632
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5883-L5883
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_bricks.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_bricks_from_quartz_block_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/quartz_bricks.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[ordinary-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_stairs.json
[ordinary-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_slab.json
[cut-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_stairs_from_quartz_block_stonecutting.json
[cut-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_slab_from_stonecutting.json
