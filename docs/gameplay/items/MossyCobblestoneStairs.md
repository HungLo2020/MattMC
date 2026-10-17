# Mossy Cobblestone Stairs

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), craft **6 [Mossy Cobblestone](MossyCobblestone.md) → 4 Mossy Cobblestone Stairs**, arranged as three rows of **1, 2, then 3 blocks aligned along one side**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 [Mossy Cobblestone](MossyCobblestone.md) → 1 Mossy Cobblestone Stairs**. [Stonecutting 1]

Stonecutting gives one stair per input block, so it uses less material than the six-block crafting recipe.

Make the matching mossy full block first; the shape recipe does not accept the plain block with moss added beside it. See [mossy-material recipes](../blocks/Stone.md#mossy-variants). [Recipe]

Mine with an **unbroken pickaxe**, including a Wooden Pickaxe: each block returns **1 Mossy Cobblestone Stairs**. Breaking it by hand does not collect it. Silk Touch does not change the output or bypass the pickaxe requirement, and Fortune does not multiply it. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Stone.md#obtaining) for tool tiers and drop conditions. [Wood tier]

## Usage

Use Mossy Cobblestone Stairs for stairways, stepped roofs and trim. For a larger build, use the Stonecutter route above to save full blocks.

## Behavior

Place stairs upright or upside down according to the clicked face and height. They face your horizontal direction, form inner or outer corners beside compatible stairs, and can be waterlogged. [Stair placement] See the [placed-shape guide](../blocks/Stone.md#placing-shaped-blocks) for orientation and corner details.

## Notes

* This item is the item form of the `minecraft:mossy_cobblestone_stairs` block. [Item registration] · [Block registration]

Find Mossy Cobblestone Stairs by name in the combined [inventory item browser](../mechanics/InventoryBrowser.md). Its catalog entry is available to browse, but requesting an item requires the server's infinite-materials ability, normally Creative mode; ordinary Survival browsing does not supply it. [Catalog entry] · [Combined catalog] · [Creative admission]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes, loot, registrations, tool rules and Creative-browser admission were checked; no in-game crafting, smelting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L960-L960
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5240-L5240
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/mossy_cobblestone_stairs.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Wood tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L260-L260
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L88-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_stairs.json
[Stonecutting 1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/mossy_cobblestone_stairs_from_mossy_cobblestone_stonecutting.json
[Stair placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L166
