# End Stone Brick Stairs

**End Stone Brick Stairs** (`minecraft:end_stone_brick_stairs`) places the end stone brick stairs form for steps, roofs and corners. See the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 End Stone Bricks in the [stair pattern](../blocks/Stone.md#crafting-yields) → 4 End Stone Brick Stairs**. Crafting requires the bricks; raw End Stone is not this recipe’s ingredient. [Crafting recipe][craft]

The [Stonecutter](../blocks/Stonecutter.md) takes **1 End Stone or End Stone Bricks → 1 End Stone Brick Stairs**. Cutting raw End Stone directly skips the separate brick-making step. [Stonecutting][cut-1] · [Cut raw End Stone][cut-2] · [One input per cut][cut-menu]

Stonecutting makes four stairs from four accepted input blocks; crafting four stairs uses six. See the [family cutting choices](../blocks/EndStoneAndPurpur.md#stonecutting).

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/EndStoneAndPurpur.md#mining-and-recovery)

## Usage

Your horizontal direction sets the facing. The clicked face and click height choose upright or upside-down placement. Suitable neighboring stairs automatically form inner or outer corners, including other stair materials when the half and facing checks fit. [Placement and corner rules][stairs] · [Shared stair controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

End Stone Brick Stairs can be waterlogged. The placed half, facing and corner are block states; mining returns the ordinary End Stone Brick Stairs item, and placement chooses those states again. [Stair states][stairs] · [Exact loot][loot]

## Notes

The exact block and item ID is `minecraft:end_stone_brick_stairs`. Follow the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes), [stonecutting](../blocks/EndStoneAndPurpur.md#stonecutting) and [placed behavior](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks. No in-game crafting, harvesting or placement test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L163
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L961-L961
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5241-L5241
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/end_stone_brick_stairs.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/end_stone_brick_stairs.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_stairs_from_end_stone_brick_stonecutting.json
[cut-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_stairs_from_end_stone_stonecutting.json
