# Mossy Stone Bricks

## Obtaining

Combine **1 [Stone Bricks](StoneBricks.md) + 1 [Moss Block](MossBlock.md) → 1 Mossy Stone Bricks**, or **1 Stone Bricks + 1 [Vine](Vines.md) → 1 Mossy Stone Bricks**. Both recipes are shapeless. Use a Moss Block, not Moss Carpet; the vine alternative specifically names the Vine item. [Recipe 1] · [Recipe 2]

Mine with an **unbroken pickaxe**, including a Wooden Pickaxe: each block returns **1 Mossy Stone Bricks**. Breaking it by hand does not collect it. Silk Touch does not change the output or bypass the pickaxe requirement, and Fortune does not multiply it. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Stone.md#obtaining) for tool tiers and drop conditions. [Wood tier]

## Usage

Use Mossy Stone Bricks as a weathered full building block, or make [slabs](MossyStoneBrickSlab.md), [stairs](MossyStoneBrickStairs.md) and [walls](MossyStoneBrickWall.md). Make the mossy full block first, then shape it. The [family shape recipes](../blocks/Stone.md#crafting-yields) give the yields; [mossy-material routes](../blocks/Stone.md#mossy-variants) distinguish this brick family from Mossy Cobblestone.

## Behavior

This is an ordinary full masonry block. For placed-block properties and the distinction from [Infested lookalikes](../blocks/Stone.md#infested-stone-variants), see the [Stone block guide](../blocks/Stone.md#block-properties). The mining route above returns this ordinary item. [Block registration] · [Drop table]

## Notes

* This item is the item form of the `minecraft:mossy_stone_bricks` block. [Item registration] · [Block registration]

Find Mossy Stone Bricks by name in the combined [inventory item browser](../mechanics/InventoryBrowser.md). Its catalog entry is available to browse, but requesting an item requires the server's infinite-materials ability, normally Creative mode; ordinary Survival browsing does not supply it. [Catalog entry] · [Combined catalog] · [Creative admission]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes, loot, registrations, tool rules and Creative-browser admission were checked; no in-game crafting, smelting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L539-L539
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2252-L2255
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/mossy_stone_bricks.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Wood tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L271-L271
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L88-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_moss_block.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_vine.json
