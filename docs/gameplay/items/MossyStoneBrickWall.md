# Mossy Stone Brick Wall

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), craft **6 [Mossy Stone Bricks](MossyStoneBricks.md) → 6 Mossy Stone Brick Wall**, filling **two complete rows of three**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 [Mossy Stone Bricks](MossyStoneBricks.md) → 1 Mossy Stone Brick Wall**. [Stonecutting 1]

Make the matching mossy full block first; the shape recipe does not accept the plain block with moss added beside it. See [mossy-material recipes](../blocks/Stone.md#mossy-variants). [Recipe]

Mine with an **unbroken pickaxe**, including a Wooden Pickaxe: each block returns **1 Mossy Stone Brick Wall**. Breaking it by hand does not collect it. Silk Touch does not change the output or bypass the pickaxe requirement, and Fortune does not multiply it. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Stone.md#obtaining) for tool tiers and drop conditions. [Wood tier]

## Usage

Use Mossy Stone Brick Wall for connected boundary walls, posts and building details.

## Behavior

Walls connect to neighboring walls, suitable sturdy block faces, Iron Bars and suitably aligned Fence Gates. Their collision reaches **1.5 blocks high**, and they can be waterlogged. [Wall shape] See the [placed-shape guide](../blocks/Stone.md#placing-shaped-blocks) for connection and post behavior.

## Notes

* This item is the item form of the `minecraft:mossy_stone_brick_wall` block. [Item registration] · [Block registration]

Find Mossy Stone Brick Wall by name in the combined [inventory item browser](../mechanics/InventoryBrowser.md). Its catalog entry is available to browse, but requesting an item requires the server's infinite-materials ability, normally Creative mode; ordinary Survival browsing does not supply it. [Catalog entry] · [Combined catalog] · [Creative admission]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes, loot, registrations, tool rules and Creative-browser admission were checked; no in-game crafting, smelting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L610-L610
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5276-L5278
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/mossy_stone_brick_wall.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Wood tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L274-L274
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L88-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_brick_wall.json
[Stonecutting 1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/mossy_stone_brick_wall_from_mossy_stone_brick_stonecutting.json
[Wall shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L51-L133
