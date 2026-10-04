# Smooth Quartz Block

**Smooth Quartz Block** (`minecraft:smooth_quartz`) is the smelted quartz finish used for smooth full blocks, stairs and slabs. [Item registration][items] · [Block registration][blocks]

## Obtaining

Smelt **1 ordinary [Block of Quartz](BlockOfQuartz.md) in a [Furnace](../blocks/Furnace.md) → 1 Smooth Quartz Block**. The recipe takes **200 game ticks** (nominally 10 seconds at 20 ticks per second) and specifies **0.1 recipe experience**. Nether Quartz items, chiseled blocks, pillars and Quartz Bricks are not substitutes. See [crafting and smoothing](../blocks/Quartz.md#crafting-and-smoothing). [Smelting recipe][smelt]

Mine a placed block with an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier; explosions have a separate survival condition. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

Use the full block in a build, or make [Smooth Quartz Stairs](SmoothQuartzStairs.md) and [Smooth Quartz Slabs](SmoothQuartzSlab.md). Their [crafting recipes](../blocks/Quartz.md#crafting-stairs-and-slabs) and [stonecutting routes](../blocks/Quartz.md#stonecutting) require this smooth finish. Ordinary Quartz Stairs and Slabs use a different ingredient list. [Smooth shapes][smooth-stairs] [smooth-slabs][] · [Ordinary shapes][ordinary-stairs] [ordinary-slabs][]

## Behavior

Use the item to place a full decorative block. It has no selectable facing or pillar axis; see [quartz placement and properties](../blocks/Quartz.md#pillar-orientation-and-placement). [Block registration][blocks]

## Notes

The displayed name is Smooth Quartz Block, while its registry ID is `minecraft:smooth_quartz`. Correctly mining it keeps the smooth finish. See the [registered quartz variants](../blocks/Quartz.md#registered-variants). [Loot][loot]

Related: [Block of Quartz](BlockOfQuartz.md) · [Smooth Quartz Stairs](SmoothQuartzStairs.md) · [Smooth Quartz Slab](SmoothQuartzSlab.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L413-L413
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3925-L3928
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/smooth_quartz.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_quartz.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[smooth-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_quartz_stairs.json
[smooth-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_quartz_slab.json
[ordinary-stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_stairs.json
[ordinary-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_slab.json
