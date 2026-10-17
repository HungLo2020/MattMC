# Pale Oak Slab

## Obtaining

Place **3 [Pale Oak Planks](PaleOakPlanks.md) in one horizontal row** in a [Crafting Table](../blocks/CraftingTable.md) to make **6 Pale Oak Slabs**. Every slot uses Pale Oak Planks. [Recipe][recipe]

Ordinary mining returns **1 Pale Oak Slab from a single slab or 2 from a double slab**. You can collect them by hand; an unbroken axe mines them faster. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot table][loot]

## Usage

Use Pale Oak Slabs for half-height floors, paths, roofs, and trim.

Each Pale Oak Slab item provides **150 ticks** of default furnace fuel. A dry placed slab can be consumed by ordinary fire spread; see [fire and fuel rules](../blocks/WoodConstruction.md#fire-and-furnace-fuel).

## Behavior

A single Pale Oak Slab can occupy the upper or lower half of a block space and can be waterlogged. Only another **Pale Oak Slab** can fill its missing half to form a double slab. Combining the pair clears waterlogging, and the double slab cannot be waterlogged. See [slab placement](../blocks/WoodConstruction.md#slabs) and [waterlogging](../blocks/WoodConstruction.md#waterlogging-and-power) for the shared rules.

## Notes

* The item and placed block both use `minecraft:pale_oak_slab`. [Registration][item]
* Available in the Building Blocks Creative tab.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pale_oak_slab.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_slab.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L390-L390
