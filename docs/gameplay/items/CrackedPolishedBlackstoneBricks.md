# Cracked Polished Blackstone Bricks

**Cracked Polished Blackstone Bricks** (`minecraft:cracked_polished_blackstone_bricks`) adds a cracked full-block finish to Blackstone brickwork. [Item binding][item] · [Block registration][block]

## Obtaining

Smelt **1 [Polished Blackstone Bricks](PolishedBlackstoneBricks.md) → 1 Cracked Polished Blackstone Bricks** in a [Furnace](../blocks/Furnace.md). The recipe takes **200 game ticks** and specifies **0.1 recipe XP**. It consumes the brick finish, rather than raw or plain Polished Blackstone. [Smelting][recipe-1]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Use the cracked blocks among ordinary [Polished Blackstone Bricks](PolishedBlackstoneBricks.md) to vary a wall or floor. Keep any blocks needed for the ordinary brick [shape recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting) in that uncracked finish.

## Behavior

This is a full block with no selectable placement axis or facing. Correct-tool mining preserves the cracked finish instead of returning uncracked bricks. [Registration][block] · [Exact loot][loot]

## Notes

See [Blackstone variants](../blocks/BlackstoneAndBasalt.md#blackstone-variants), [cracking and crafting](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting) and [placed properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cracked_polished_blackstone_bricks.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2496
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5828-L5830
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cracked_polished_blackstone_bricks.json
