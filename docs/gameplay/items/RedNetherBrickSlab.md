# Red Nether Brick Slab

**Red Nether Brick Slab** (`minecraft:red_nether_brick_slab`) is the half-height form of [Red Nether Bricks](RedNetherBricks.md). Use it with the other [family shapes](../blocks/NetherBricks.md#variants) when planning a matching build. [Item binding][item] · [Registration helper][factory]

## Obtaining

Craft **3 Red Nether Bricks blocks in one horizontal row → 6 Red Nether Brick Slabs**, or use a [Stonecutter](../blocks/Stonecutter.md) for **1 Red Nether Bricks block → 2 slabs**. Both routes give two slabs per input block. Both recipes require full **Red Nether Bricks blocks**; the regular finish and small [Nether Brick](NetherBrick.md) items are not substitutes. [Crafting][craft] · [Stonecutting][cut]

Use an **unbroken pickaxe**, including Wood, to collect the placed block. A single slab returns **1 Red Nether Brick Slab** and a double slab returns **2**. Hand breaking or an unsuitable tool does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. Explosion recovery has separate decay rules. [Exact loot][loot] · [Tool and collection rules](../blocks/NetherBricks.md#collect-placed-masonry)

## Usage

Use these slabs for floors, paths, roof edges and half-height trim. Choose the upper or lower half of a block space, then add a second **Red Nether Brick Slab** to its empty half to make a double slab. Other slab types do not combine with it. See the [shared slab placement controls](../blocks/Stone.md#placing-shaped-blocks). [Matching-item and placement rules][shape]

## Behavior

A single slab can be waterlogged where Water can exist. Combining two clears that Water state, and the double slab cannot be waterlogged. The full-height result remains a **double Red Nether Brick Slab**, not a Red Nether Bricks full block; proper mining recovers two slab items. [Water and double-state rules][shape] · [Double-slab loot][loot]

## Notes

This item places the `minecraft:red_nether_brick_slab` block. The [family recipe guide](../blocks/NetherBricks.md#crafting-and-cracking) compares the recipes; use the [shared masonry guide](../blocks/Stone.md#placing-shaped-blocks) for detailed placement. [Block registration][block] · [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item/block bindings, exact production recipes, complete loot, mining tags and tool gates, and the relevant placement or interaction rules. No in-game crafting, smelting, mining, placement or interaction test was run. Data packs can change recipes, tags and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L984
[factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2750-L2782
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5266
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/red_nether_brick_slab.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/red_nether_brick_slab.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/red_nether_brick_slab_from_red_nether_bricks_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L126
