# Bamboo Mosaic Slab

**Bamboo Mosaic Slab** (`minecraft:bamboo_mosaic_slab`) is the half-height form of Bamboo Mosaic. See [Wood construction: Bamboo Mosaic](../blocks/WoodConstruction.md#bamboo-mosaic) for its material and recipe differences. [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), put **3 full [Bamboo Mosaic](BambooMosaic.md) blocks in one horizontal row → 6 Bamboo Mosaic Slabs**. The input is full Mosaic, not Bamboo Planks or regular Bamboo Slabs. [Recipe][recipe]

Ordinary mining returns **1 Mosaic Slab from a single slab or 2 from a double slab**, including when mined by hand. An unbroken axe is efficient. Explosion drops can be reduced; see [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Use the Mosaic texture in floors, roofs, and trim. Mosaic Slabs are absent from the generic **wooden slabs** item tag; regular [Bamboo Slabs](BambooSlab.md) belong to that tag. Only regular Bamboo Slabs are the input for crafting a full Bamboo Mosaic block. [Wooden-slab tag][slab-tag] · [Mosaic recipe][mosaic-recipe]

## Behavior

The clicked face and height select the lower or upper half. Another **Bamboo Mosaic Slab** can fill the missing half to make a double slab; a regular Bamboo Slab cannot. Single slabs can be waterlogged, while combining them clears that state and double slabs reject waterlogging. See [Wood construction: slabs](../blocks/WoodConstruction.md#slabs). [Placement and water checks][slab-placement]

## Notes

Related: [Bamboo Mosaic Stairs](BambooMosaicStairs.md) · [Bamboo Slab](BambooSlab.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L393
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic_slab.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_mosaic_slab.json
[slab-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[mosaic-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic.json
[slab-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L66-L119
