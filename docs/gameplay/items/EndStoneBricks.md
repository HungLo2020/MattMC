# End Stone Bricks

**End Stone Bricks** (`minecraft:end_stone_bricks`) turn raw [End Stone](EndStone.md) into a pale brick building block and the ingredient for brick stairs, slabs and walls. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **4 End Stone in a 2 × 2 square → 4 End Stone Bricks**, or use a [Stonecutter](../blocks/Stonecutter.md) for **1 End Stone → 1 End Stone Bricks**. Both preserve the block count. [Crafting recipe][craft] · [Stonecutting][cut-1] · [One input per cut][cut-menu]

For the existing End City material route, follow [Obtaining in the End](../blocks/EndStoneAndPurpur.md#obtaining-in-the-end).

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/EndStoneAndPurpur.md#mining-and-recovery)

## Usage

Use the full block for masonry or make [End Stone Brick Stairs](EndStoneBrickStairs.md), [Slabs](EndStoneBrickSlab.md) and [Walls](EndStoneBrickWall.md). The [family recipe guide](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes) owns the shared patterns. Raw End Stone can also be [cut directly into those brick shapes](../blocks/EndStoneAndPurpur.md#stonecutting).

## Behavior

This is an ordinary full block with no pillar axis. Correct-tool mining preserves End Stone Bricks instead of turning them back into raw End Stone. [Block registration][block] · [Exact loot][loot]

For the differences between raw End Stone and bricks in Chorus support and Ender Dragon interactions, use the [canonical placement and dragon section](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions).

## Notes

The exact block and item ID is `minecraft:end_stone_bricks`. Follow the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes), [stonecutting](../blocks/EndStoneAndPurpur.md#stonecutting) and [placed behavior](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks. No in-game crafting, harvesting or placement test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L585-L585
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4250-L4253
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/end_stone_bricks.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/end_stone_bricks.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_bricks_from_end_stone_stonecutting.json
