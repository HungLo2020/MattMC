# Polished Blackstone

**Polished Blackstone** (`minecraft:polished_blackstone`) is a full building block and the crafting step between raw Blackstone and Polished Blackstone Bricks. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **4 [Blackstone](Blackstone.md) in a 2 × 2 square → 4 Polished Blackstone**, or stonecut **1 Blackstone → 1 Polished Blackstone**. Both preserve the block count; the Stonecutter permits a one-block batch. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Place it for a polished wall or floor, or craft **4 Polished Blackstone in a 2 × 2 square → 4 [Polished Blackstone Bricks](PolishedBlackstoneBricks.md)**. It also supplies the polished stairs, slabs and walls. See the [canonical finishing and shape recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting).

## Behavior

This full block has no placement axis or facing to choose. Its [stonecutting options](../blocks/BlackstoneAndBasalt.md#stonecutting) include polished shapes, brick shapes, bricks and the chiseled finish; several of these can also be cut directly from raw Blackstone. Mining preserves the polished full block. [Registration][block] · [Loot][loot]

## Notes

See [Blackstone variants](../blocks/BlackstoneAndBasalt.md#blackstone-variants) and [placement and properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties). The [Polished Blackstone Button](PolishedBlackstoneButton.md) and [Pressure Plate](PolishedBlackstonePressurePlate.md) have their own control rules.

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_from_blackstone_stonecutting.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2489
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5824
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone.json
