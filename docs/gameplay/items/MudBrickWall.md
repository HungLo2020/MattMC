# Mud Brick Wall

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), fill **two complete rows of three [Mud Bricks](MudBricks.md)** to make **6 Mud Brick Wall**. At a [Stonecutter](../blocks/Stonecutter.md#using-the-menu), **1 Mud Bricks → 1 Mud Brick Wall**. The material yield is the same, but cutting lets you make one wall at a time. Both routes use the finished Mud Bricks block, not Mud or Packed Mud; the [family production recipes](../blocks/MudAndMudBricks.md#crafting-and-stonecutting) explain those earlier steps. [Crafting recipe] · [Cutting recipe] · [Cutter consumption]

Mine a placed block with an **unbroken pickaxe**, including a Wooden Pickaxe, to collect **1 Mud Brick Wall**. Hands, unsuitable tools and already-broken pickaxes do not collect it. Silk Touch is unnecessary and Fortune does not increase the drop. [Drop table] · [Pickaxe tag] · [Wall tag] · [Tool gate] · [Broken tools] · [Tool rules] · [Wood restrictions]

The [family mining rules](../blocks/MudAndMudBricks.md#registered-family-and-mining) cover the shared collection conditions. Normal item drops require block drops to be enabled; explosion recovery has a separate survival condition. [Block drops] · [Drop table]

## Usage

Use Mud Brick Wall for connected boundary walls, posts and building details. Combine it with [Mud Bricks](MudBricks.md), [Mud Brick Stairs](MudBrickStairs.md) and [Mud Brick Slab](MudBrickSlab.md) for the rest of the building.

## Behavior

Walls connect to neighboring walls, suitable sturdy block faces, Iron Bars and correctly aligned Fence Gates. Their collision reaches **1.5 blocks high**, and they can be waterlogged. See [Mud Brick Wall placement](../blocks/MudAndMudBricks.md#mud-brick-wall) for arm/post changes, support and source-water/bucket rules. [Wall connections and shape] · [Water handling]

Mining returns a wall item; its connections are determined again by its surroundings when placed. [Drop table] · [Wall connections and shape]

## Notes

* This item is the item form of the `minecraft:mud_brick_wall` block. [Item registration] · [Block registration]

The wall has the same mining requirements and material properties as the full Mud Bricks block. [Brick properties] · [Property copying]

Find Mud Brick Wall by displayed name in the combined [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). It has a catalog entry, but ordinary Survival browsing does not supply items: accepted item requests require the server's infinite-materials ability, normally Creative mode. [Catalog entry] · [Combined catalog] · [Creative admission]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The cited registrations, bundled recipes, loot, tool rules, placement and browser admission were checked. No in-game crafting, stonecutting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L613-L613
[Block registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Blocks.java#L5281-L5281
[Brick properties]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Blocks.java#L2265-L2273
[Property copying]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1081
[Crafting recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mud_brick_wall.json#L1-L15
[Cutting recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/stonecutting/mud_brick_wall_from_mud_bricks_stonecutting.json#L1-L8
[Cutter consumption]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L61-L68
[Drop table]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/loot_table/blocks/mud_brick_wall.json#L1-L21
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L318-L318
[Wall tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/walls.json#L24-L24
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L349-L349
[Wall connections and shape]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/WallBlock.java#L54-L132
[Water handling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L48
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Tool rules]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[Wood restrictions]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[Block drops]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
