# End Stone Brick Slab

**End Stone Brick Slab** (`minecraft:end_stone_brick_slab`) places the end stone brick slab form for half-height floors, ledges and detail work. See the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **3 End Stone Bricks in one horizontal row → 6 End Stone Brick Slabs**. Crafting requires the bricks; raw End Stone is not this recipe’s ingredient. [Crafting recipe][craft]

The [Stonecutter](../blocks/Stonecutter.md) takes **1 End Stone or End Stone Bricks → 2 End Stone Brick Slabs**. Cutting raw End Stone directly skips the separate brick-making step. [Stonecutting][cut-1] · [Cut raw End Stone][cut-2] · [One input per cut][cut-menu]

Both methods give two slabs per input block; stonecutting allows smaller batches.

Use an **unbroken pickaxe, including Wood**, to recover **one matching slab** from a single slab or **two matching slabs** from a double slab. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/EndStoneAndPurpur.md#mining-and-recovery)

## Usage

Place it in the top or bottom half of a block space. Add a second **End Stone Brick Slab** to its empty half to make a double slab; another slab material will not combine with it. The result remains `minecraft:end_stone_brick_slab`, not its full-block crafting ingredient. [Placement and matching-item check][slab] · [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

A single slab can be waterlogged. Doubling it clears its waterlogged state, and a double slab cannot accept Water through the waterlogging interface. Correct-tool mining returns slab items rather than a full block. [Slab state and Water rules][slab] · [Double-slab loot][loot]

## Notes

The exact block and item ID is `minecraft:end_stone_brick_slab`. Follow the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes), [stonecutting](../blocks/EndStoneAndPurpur.md#stonecutting) and [placed behavior](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks. No in-game crafting, harvesting or placement test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L979-L979
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5261-L5261
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/end_stone_brick_slab.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/end_stone_brick_slab.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_slab_from_end_stone_brick_stonecutting.json
[cut-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_slab_from_end_stone_stonecutting.json
