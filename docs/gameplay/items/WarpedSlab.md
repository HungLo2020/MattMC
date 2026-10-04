# Warped Slab

## Obtaining

Place **3 [Warped Planks](WarpedPlanks.md) in one horizontal row** in a [Crafting Table](../blocks/CraftingTable.md) to make **6 Warped Slabs**. Every slot uses Warped Planks. [Recipe][recipe]

Ordinary mining returns **1 Warped Slab from a single slab or 2 from a double slab**. You can collect them by hand; an unbroken axe mines them faster. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot table][loot]

## Usage

Use Warped Slabs for half-height floors, paths, roofs, and trim.

Warped Slab items are not furnace fuel, and ordinary fire spread does not consume the placed block. See [fire and fuel differences](../blocks/WoodConstruction.md#fire-and-furnace-fuel).

## Behavior

A single Warped Slab can occupy the upper or lower half of a block space and can be waterlogged. Only another **Warped Slab** can fill its missing half to form a double slab. Combining the pair clears waterlogging, and the double slab cannot be waterlogged. See [slab placement](../blocks/WoodConstruction.md#slabs) and [waterlogging](../blocks/WoodConstruction.md#waterlogging-and-power) for the shared rules.

## Notes

* The item and placed block both use `minecraft:warped_slab`. [Registration][item]
* Available in the Building Blocks Creative tab.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/warped_slab.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/warped_slab.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L395-L395
