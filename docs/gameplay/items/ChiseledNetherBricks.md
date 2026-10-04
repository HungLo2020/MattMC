# Chiseled Nether Bricks

**Chiseled Nether Bricks** (`minecraft:chiseled_nether_bricks`) is a decorative full-block finish in the [Nether Bricks family](../blocks/NetherBricks.md#variants). It can be made from ordinary Nether Brick Slabs or cut directly from regular Nether Bricks. [Item binding][item] · [Registration helper][factory]

## Obtaining

Stack **2 [Nether Brick Slabs](NetherBrickSlab.md) vertically → 1 Chiseled Nether Bricks**. This fits the personal crafting grid. Alternatively, use a [Stonecutter](../blocks/Stonecutter.md) for **1 regular [Nether Bricks](NetherBricks.md) block → 1 Chiseled Nether Bricks**, avoiding the intermediate slabs. Red Nether Brick Slabs are not ingredients in the chiseled recipe. [Crafting][craft] · [Stonecutting][cut]

Use an **unbroken pickaxe**, including Wood, to collect the placed block. Ordinary mining returns **1 Chiseled Nether Bricks item**. Hand breaking or an unsuitable tool does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. Explosion recovery has a separate survival condition. [Exact loot][loot] · [Tool and collection rules](../blocks/NetherBricks.md#collect-placed-masonry)

## Usage

Place it as patterned masonry for accents, columns or borders alongside the other [Nether Brick finishes](../blocks/NetherBricks.md#variants). Its crafting recipe consumes slab items in a grid; placing two slabs together instead makes a double slab.

## Behavior

It is an ordinary full block with no player-selected facing or axis. Mining it with the correct tool preserves the chiseled finish. See [full-block placement](../blocks/NetherBricks.md#placement-and-the-fence) for the family properties. [Block registration][block] · [Loot][loot]

## Notes

The exact item and block ID is `minecraft:chiseled_nether_bricks`. The [crafting and cracking guide](../blocks/NetherBricks.md#crafting-and-cracking) and [stonecutting table](../blocks/NetherBricks.md#stonecutting) compare the family conversions. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/block bindings, exact production recipes, complete loot, mining tags and tool gates, and the relevant placement or interaction rules. No in-game crafting, smelting, mining, placement or interaction test was run. Data packs can change recipes, tags and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L575
[factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2750-L2782
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5865-L5873
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/chiseled_nether_bricks.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_nether_bricks.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_nether_bricks_from_nether_bricks_stonecutting.json
