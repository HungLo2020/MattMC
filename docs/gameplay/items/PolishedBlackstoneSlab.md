# Polished Blackstone Slab

**Polished Blackstone Slab** (`minecraft:polished_blackstone_slab`) provides the polished half-height building piece for ledges, floors and trim. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **3 [Polished Blackstone](PolishedBlackstone.md) in one row → 6 Polished Blackstone Slabs**. A [Stonecutter](../blocks/Stonecutter.md) turns **1 Blackstone or 1 Polished Blackstone → 2 slabs**. Cutting keeps the crafting yield while allowing a one-block batch. The raw Blackstone route skips the intermediate finishing crafts. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2] · [Cut from Polished Blackstone][recipe-3]

Use an **unbroken pickaxe**, including Wood, to recover **one matching slab from a single slab or two from a double slab**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Choose the top or bottom half using the clicked face and height. Add a second **Polished Blackstone Slab** in the empty half to make a double slab; another slab material cannot fill it. [Placement and matching-item check][shape] · [Shared slab placement](../blocks/Stone.md#placing-shaped-blocks) Two of these slabs stacked vertically in the crafting grid make **1 [Chiseled Polished Blackstone](ChiseledPolishedBlackstone.md)**; see the [family recipe](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting).

## Behavior

A double slab fills the block space but remains a Polished Blackstone Slab, rather than becoming Polished Blackstone. Single slabs can hold Water; making a double slab clears its waterlogged state. Mining the double returns two slab items. [Slab states][shape] · [Slab loot][loot]

## Notes

Compare the [three Blackstone finishes](../blocks/BlackstoneAndBasalt.md#blackstone-variants), their [crafting recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting), [cutting shortcuts](../blocks/BlackstoneAndBasalt.md#stonecutting) and [placed properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_slab.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_slab_from_blackstone_stonecutting.json
[recipe-3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_slab_from_polished_blackstone_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2490
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5845-L5847
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_slab.json
