# Stripped Crimson Stem

Stripped Crimson Stem is one of the four [Crimson timber forms](../blocks/TreeLogsAndRoots.md#crimson-timber), used for building and crafting.

## Obtaining

Use an **unbroken axe** on a placed [Crimson Stem](CrimsonStem.md) to turn it into Stripped Crimson Stem, then mine it to collect the item. See [stripping](../blocks/TreeLogsAndRoots.md#stripping) for axis retention and tool wear. [Exact conversion][strip]

Ordinary mining of a placed Stripped Crimson Stem returns **one Stripped Crimson Stem**, without Silk Touch. An unbroken axe is the efficient tool, but hand mining also collects it. See [mining and placement](../blocks/TreeLogsAndRoots.md#mining-and-placement) for the shared harvest rules. [Matching drop][loot] · [Block properties][block] · [Axe tag][axe-tag] · [Harvest eligibility][harvest]

## Usage

- **4 Stripped Crimson Stem in a 2 × 2 square → 3 [Stripped Crimson Hyphae](StrippedCrimsonHyphae.md)**. [Recipe][hyphae-recipe]
- **1 Stripped Crimson Stem → 4 [Crimson Planks](CrimsonPlanks.md)**, anywhere in a crafting grid. This exact form is included in the plank recipe's accepted inputs. [Recipe][planks-recipe] · [Inputs][planks-inputs]

See [crafting choices](../blocks/TreeLogsAndRoots.md#crafting-choices) and [Wood Construction](../blocks/WoodConstruction.md#planks-and-materials) for the other family forms and building conversions.

## Behavior

Placement takes its axis from the clicked face: top or bottom gives a vertical pillar; a side gives the corresponding horizontal axis. See [mining and placement](../blocks/TreeLogsAndRoots.md#mining-and-placement). [Axis placement][axis]

This is already stripped and has **no further axe-stripping conversion**. [Stripping map][strip]

## Notes

- This item places `minecraft:stripped_crimson_stem`. [Item registration][item] · [Block registration][block]
- It is **not a default furnace fuel**. The [fire and fuel guide](../blocks/TreeLogsAndRoots.md#fire-and-fuel) explains the separate placed-block rules; **dropped stacks can still take fire or lava damage**. [Fuel table][fuel] · [Fuel exclusion][fuel-exclusion] · [Dropped-item damage][item-damage]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game test was run; data packs can change recipes, tags, and drops.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L243-L243
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5508-L5508
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/stripped_crimson_stem.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[hyphae-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/stripped_crimson_hyphae.json
[planks-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_planks.json
[planks-inputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L49-L56
[strip]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[item-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L288
