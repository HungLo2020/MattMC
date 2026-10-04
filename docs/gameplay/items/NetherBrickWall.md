# Nether Brick Wall

**Nether Brick Wall** (`minecraft:nether_brick_wall`) is the connecting wall form of [Nether Bricks](NetherBricks.md). Use it with the other [family shapes](../blocks/NetherBricks.md#variants) when planning a matching build. [Item binding][item] · [Registration helper][factory]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), fill **two rows of three Nether Bricks blocks → 6 Nether Brick Walls**. A [Stonecutter](../blocks/Stonecutter.md) gives **1 wall per Nether Bricks block**, the same yield with a smaller batch. Both recipes require full **Nether Bricks blocks**; the red finish and small [Nether Brick](NetherBrick.md) items are not substitutes. [Crafting][craft] · [Stonecutting][cut]

Use an **unbroken pickaxe**, including Wood, to collect the placed block. Ordinary mining returns **1 Nether Brick Wall item**. Hand breaking or an unsuitable tool does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. Explosion recovery has a separate survival condition. [Exact loot][loot] · [Tool and collection rules](../blocks/NetherBricks.md#collect-placed-masonry)

## Usage

Use walls for narrow boundaries, posts and edging. The [family guide](../blocks/NetherBricks.md#variants) compares the available masonry forms; the [shared wall placement rules](../blocks/Stone.md#placing-shaped-blocks) explain how neighboring blocks affect the wall profile.

## Behavior

The wall connects to adjacent walls, suitable sturdy block faces, **Iron Bars, Copper Bars or glass panes**, and correctly aligned Fence Gates. Its connections update with its neighbors, and it can be waterlogged where Water can exist. Bars and panes qualify through the shared bar-block behavior. [Wall connections and placement][shape] · [Bars and pane registrations][bars] · [Copper Bars inheritance][copper-bars] · [Stained pane inheritance][stained-panes]

The [Nether Brick Fence](NetherBrickFence.md) is a separate block with different connection rules.

## Notes

This item places the `minecraft:nether_brick_wall` block. The [family recipe guide](../blocks/NetherBricks.md#crafting-and-cracking) compares the recipes; use the [shared masonry guide](../blocks/Stone.md#placing-shaped-blocks) for detailed placement. [Block registration][block] · [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/block bindings, exact production recipes, complete loot, mining tags and tool gates, and the relevant placement or interaction rules. No in-game crafting, smelting, mining, placement or interaction test was run. Data packs can change recipes, tags and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L614
[factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2750-L2782
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5282-L5284
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/nether_brick_wall.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/nether_brick_wall.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/nether_brick_wall_from_nether_bricks_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L170
[bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2307-L2331
[copper-bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java#L11-L25
[stained-panes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StainedGlassPaneBlock.java#L8-L25
