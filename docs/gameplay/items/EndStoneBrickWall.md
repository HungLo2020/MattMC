# End Stone Brick Wall

**End Stone Brick Wall** (`minecraft:end_stone_brick_wall`) places the end stone brick wall form for narrow borders, posts and barriers. See the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 End Stone Bricks in two full rows → 6 End Stone Brick Walls**. Crafting requires the bricks; raw End Stone is not this recipe’s ingredient. [Crafting recipe][craft]

The [Stonecutter](../blocks/Stonecutter.md) takes **1 End Stone or End Stone Bricks → 1 End Stone Brick Wall**. Cutting raw End Stone directly skips the separate brick-making step. [Stonecutting][cut-1] · [Cut raw End Stone][cut-2] · [One input per cut][cut-menu]

Both methods give one wall per input block; stonecutting allows single-item batches.

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/EndStoneAndPurpur.md#mining-and-recovery)

## Usage

It joins adjacent wall-tagged blocks, suitable sturdy block faces, bars/panes and correctly aligned Fence Gates. Bars/panes include **Iron Bars, Copper Bars and Glass Panes**, including stained panes. Ordinary fences are not a separate wall-connection category. [Connection rules][wall] · [Bars and ordinary panes][bars] · [Copper Bars inheritance][copper-bars] · [Stained panes][panes]

## Behavior

End Stone Brick Wall can be waterlogged. Neighboring blocks and the block above determine its arms and post; the inventory item does not store those connections. See the [shared wall controls](../blocks/Stone.md#placing-shaped-blocks). [Placement and neighbor rules][wall] · [Wall tag][wall-tag] · [Exact loot][loot]

## Notes

The exact block and item ID is `minecraft:end_stone_brick_wall`. Follow the [family variants](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes), [stonecutting](../blocks/EndStoneAndPurpur.md#stonecutting) and [placed behavior](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks. No in-game crafting, harvesting or placement test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[wall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L192
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/walls.json
[bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2307-L2331
[copper-bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java#L11-L15
[panes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StainedGlassPaneBlock.java#L8-L15
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L618-L618
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5290-L5292
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/end_stone_brick_wall.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/end_stone_brick_wall.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_wall_from_end_stone_brick_stonecutting.json
[cut-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_wall_from_end_stone_stonecutting.json
