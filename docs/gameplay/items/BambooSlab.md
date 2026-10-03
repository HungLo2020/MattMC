# Bamboo Slab

**Bamboo Slab** (`minecraft:bamboo_slab`) is the regular Bamboo half-height building block and the ingredient for full Bamboo Mosaic. See [Wood construction: slabs](../blocks/WoodConstruction.md#slabs) for shared placement rules. [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), put **3 [Bamboo Planks](BambooPlanks.md) in one horizontal row → 6 Bamboo Slabs**. Bamboo Mosaic is not an input to this recipe. [Recipe][recipe]

Ordinary mining returns **1 slab from a single slab or 2 slabs from a double slab**, including when mined by hand. An unbroken axe is efficient. Explosions can reduce those quantities; see [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Use slabs for floors, roofs, and trim. In a crafting grid, **2 Bamboo Slabs vertically → 1 [Bamboo Mosaic](BambooMosaic.md)**; this recipe fits the inventory grid. This crafting conversion differs from placing two slabs together in the world. [Mosaic recipe][mosaic-recipe]

## Behavior

Place a lower or upper slab using the clicked face and height. Filling the missing half with another **Bamboo Slab** creates a full-height double slab. A [Bamboo Mosaic Slab](BambooMosaicSlab.md) cannot fill that half. [Placement and combination][slab-placement]

Single slabs can be waterlogged; combining them clears the waterlogged state, and double slabs cannot be waterlogged. A double slab still drops two slabs when mined, not Bamboo Planks or Mosaic. See [slab water rules](../blocks/WoodConstruction.md#slabs). [Placement and water checks][slab-placement] · [Loot][loot]

## Notes

Related: [Bamboo Stairs](BambooStairs.md) · [Bamboo Mosaic](BambooMosaic.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L392
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_slab.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_slab.json
[mosaic-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic.json
[slab-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L66-L119
