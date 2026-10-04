# Chiseled Polished Blackstone

**Chiseled Polished Blackstone** (`minecraft:chiseled_polished_blackstone`) is a patterned full block for accents within Blackstone builds. [Item binding][item] · [Block registration][block]

## Obtaining

Stack **2 [Polished Blackstone Slabs](PolishedBlackstoneSlab.md) vertically → 1 Chiseled Polished Blackstone**. The recipe uses polished slabs, not raw Blackstone Slabs or Polished Blackstone Brick Slabs. Alternatively, stonecut **1 raw Blackstone or 1 Polished Blackstone → 1 chiseled block**, avoiding the slab craft. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2] · [Cut from Polished Blackstone][recipe-3]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Place it as a full-block decorative detail alongside [Polished Blackstone](PolishedBlackstone.md) or [Polished Blackstone Bricks](PolishedBlackstoneBricks.md). The [family variant table](../blocks/BlackstoneAndBasalt.md#blackstone-variants) distinguishes this finish from the ordinary shaped building pieces.

## Behavior

Placement has no selectable axis or facing. Correct-tool mining returns the same chiseled block, without Silk Touch; it does not return the two slabs used in crafting. [Registration][block] · [Exact loot][loot]

## Notes

See [finishing recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting), [stonecutting shortcuts](../blocks/BlackstoneAndBasalt.md#stonecutting) and [placed properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_polished_blackstone.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_polished_blackstone_from_blackstone_stonecutting.json
[recipe-3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_polished_blackstone_from_polished_blackstone_stonecutting.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2492
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5831-L5833
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/chiseled_polished_blackstone.json
