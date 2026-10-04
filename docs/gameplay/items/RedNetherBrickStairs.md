# Red Nether Brick Stairs

**Red Nether Brick Stairs** (`minecraft:red_nether_brick_stairs`) is the stepped form of [Red Nether Bricks](RedNetherBricks.md). Use it with the other [family shapes](../blocks/NetherBricks.md#variants) when planning a matching build. [Item binding][item] · [Registration helper][factory]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), arrange **6 Red Nether Bricks blocks** as three rows containing **1, 2, then 3 blocks aligned along one side → 4 Red Nether Brick Stairs**. A [Stonecutter](../blocks/Stonecutter.md) gives **1 stair per Red Nether Bricks block**, so six blocks can make six stairs instead of four. Both recipes require full **Red Nether Bricks blocks**; the regular finish and small [Nether Brick](NetherBrick.md) items are not substitutes. [Crafting][craft] · [Stonecutting][cut]

Use an **unbroken pickaxe**, including Wood, to collect the placed block. Ordinary mining returns **1 Red Nether Brick Stairs item**. Hand breaking or an unsuitable tool does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. Explosion recovery has a separate survival condition. [Exact loot][loot] · [Tool and collection rules](../blocks/NetherBricks.md#collect-placed-masonry)

## Usage

Use them for stairways, rooflines, seating and angled trim. Placement chooses a horizontal facing and an upper or lower half from your direction and where you click. See the [shared stair placement controls](../blocks/Stone.md#placing-shaped-blocks). [Placement][shape]

## Behavior

Compatible neighboring stairs in the same half can automatically form inner or outer corners; they do not have to be made from the same material. The shape updates when horizontal neighbors change. Stairs can be waterlogged where Water can exist. [Corner and Water rules][shape]

## Notes

This item places the `minecraft:red_nether_brick_stairs` block. The [family recipe guide](../blocks/NetherBricks.md#crafting-and-cracking) compares the recipes; use the [shared masonry guide](../blocks/Stone.md#placing-shaped-blocks) for detailed placement. [Block registration][block] · [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/block bindings, exact production recipes, complete loot, mining tags and tool gates, and the relevant placement or interaction rules. No in-game crafting, smelting, mining, placement or interaction test was run. Data packs can change recipes, tags and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L967
[factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2750-L2782
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5247
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/red_nether_brick_stairs.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/red_nether_brick_stairs.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/red_nether_brick_stairs_from_red_nether_bricks_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L161
