# Smooth Basalt

**Smooth Basalt** (`minecraft:smooth_basalt`) is a smooth full building block with ordinary placement, rather than the selectable axis of the other Basalt finishes. [Item binding][item] · [Block registration][block]

## Obtaining

Smelt **1 ordinary [Basalt](Basalt.md) → 1 Smooth Basalt** in a [Furnace](../blocks/Furnace.md). The recipe takes **200 game ticks** and specifies **0.1 recipe XP**. [Polished Basalt](PolishedBasalt.md) is not an accepted input. [Smelting][recipe-1]

For natural collection, follow the family guide’s existing [Amethyst Geode outer-shell route](../blocks/BlackstoneAndBasalt.md#basalt-variants-and-orientation).

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Place Smooth Basalt as a full block for walls, floors or trim. If a build needs a visibly aligned pillar or beam, compare [ordinary and Polished Basalt placement](../blocks/BlackstoneAndBasalt.md#basalt-variants-and-orientation).

## Behavior

Smooth Basalt has no axis property, so the clicked face does not choose an orientation as it does for Basalt and Polished Basalt. Correct-tool mining returns Smooth Basalt with its finish intact. [Block registration][block] · [Exact loot][loot]

## Notes

See [Basalt variants](../blocks/BlackstoneAndBasalt.md#basalt-variants-and-orientation) and [placement and properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run. The linked geode route retains the family guide’s earlier verification scope.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/smooth_basalt.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L521
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6717
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_basalt.json
