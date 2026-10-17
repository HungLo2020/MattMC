# Mud Brick Stairs

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), arrange **6 [Mud Bricks](MudBricks.md)** in three rows containing **one, two, then three blocks aligned along one side** to make **4 Mud Brick Stairs**. At a [Stonecutter](../blocks/Stonecutter.md#using-the-menu), **1 Mud Bricks → 1 Mud Brick Stairs**. Cutting therefore makes six stairs from the same six blocks that craft four. Both routes need the finished Mud Bricks block; follow the [family production recipes](../blocks/MudAndMudBricks.md#crafting-and-stonecutting) to turn Mud and Wheat into it. [Crafting recipe] · [Cutting recipe] · [Cutter consumption]

Mine a placed block with an **unbroken pickaxe**, including a Wooden Pickaxe, to collect **1 Mud Brick Stairs**. Hands, unsuitable tools and already-broken pickaxes do not collect it. Silk Touch is unnecessary and Fortune does not increase the drop. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] · [Tool rules] · [Wood restrictions]

The [family mining rules](../blocks/MudAndMudBricks.md#registered-family-and-mining) cover the shared collection conditions. Normal item drops require block drops to be enabled; explosion recovery has a separate survival condition. [Block drops] · [Drop table]

## Usage

Mud Brick Stairs is used for stairways, rooflines, seating, trim, and angled details. Pair it with [Mud Bricks](MudBricks.md), [Mud Brick Slab](MudBrickSlab.md) and [Mud Brick Wall](MudBrickWall.md) for matching construction.

## Behavior

Stairs connect into corner shapes depending on nearby stair blocks and placement direction. Suitable stairs at the same half-height can form inner or outer corners even when they use another material. The clicked face and height choose upright or upside-down placement. [Stair placement and corners]

Mud Brick Stairs can be waterlogged. See [Mud Brick Stairs placement](../blocks/MudAndMudBricks.md#mud-brick-stairs) for direction, source-water, bucket and support rules. Mining returns a stair item, so choose its orientation again when placing it. [Stair placement and corners] · [Water handling] · [Drop table]

## Notes

* This item is the item form of the `minecraft:mud_brick_stairs` block. [Item registration] · [Block registration]

The stair has the same mining requirements and material properties as the full Mud Bricks block. [Brick properties] · [Stair inheritance] · [Property copying]

Find Mud Brick Stairs by displayed name in the combined [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). It has a catalog entry, but ordinary Survival browsing does not supply items: accepted item requests require the server's infinite-materials ability, normally Creative mode. [Catalog entry] · [Combined catalog] · [Creative admission]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. The cited registrations, bundled recipes, loot, tool rules, placement and browser admission were checked. No in-game crafting, stonecutting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L570-L570
[Block registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Blocks.java#L2439-L2439
[Brick properties]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Blocks.java#L2265-L2273
[Stair inheritance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Blocks.java#L7256-L7258
[Property copying]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1081
[Crafting recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/crafting/mud_brick_stairs.json#L1-L16
[Cutting recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipe/stonecutting/mud_brick_stairs_from_mud_bricks_stonecutting.json#L1-L8
[Cutter consumption]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L61-L68
[Drop table]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/loot_table/blocks/mud_brick_stairs.json#L1-L21
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L324-L326
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L347-L347
[Stair placement and corners]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
[Water handling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L48
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Tool rules]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[Wood restrictions]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[Block drops]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
