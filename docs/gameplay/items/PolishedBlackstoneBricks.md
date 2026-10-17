# Polished Blackstone Bricks

**Polished Blackstone Bricks** (`minecraft:polished_blackstone_bricks`) is the full brick finish used for Blackstone brickwork, its shaped pieces and cracked bricks. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **4 [Polished Blackstone](PolishedBlackstone.md) in a 2 × 2 square → 4 Polished Blackstone Bricks**. A [Stonecutter](../blocks/Stonecutter.md) also makes **1 brick block from 1 raw Blackstone or 1 Polished Blackstone**. Cutting raw Blackstone skips the polishing craft. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2] · [Cut from Polished Blackstone][recipe-3]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Use the full blocks for brickwork or make matching [stairs](PolishedBlackstoneBrickStairs.md), [slabs](PolishedBlackstoneBrickSlab.md) and [walls](PolishedBlackstoneBrickWall.md). Smelting one brick block makes one [Cracked Polished Blackstone Bricks](CrackedPolishedBlackstoneBricks.md); the [family recipe section](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting) gives the cooking details.

## Behavior

The full block has no axis or facing selection, and ordinary correct-tool mining returns the brick block with its finish intact. Shapes keep their own item identities: a double brick slab still drops slabs. [Full-block registration][block] · [Brick loot][loot] · [Shared collection rules](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Notes

Follow [Blackstone variants](../blocks/BlackstoneAndBasalt.md#blackstone-variants), [stonecutting](../blocks/BlackstoneAndBasalt.md#stonecutting) and [placement and properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_bricks.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_bricks_from_blackstone_stonecutting.json
[recipe-3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_bricks_from_polished_blackstone_stonecutting.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2493
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5825-L5827
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_bricks.json
