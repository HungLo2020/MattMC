# Bamboo Mosaic

**Bamboo Mosaic** (`minecraft:bamboo_mosaic`) is a decorative full block with its own slab and stair recipes. The [Bamboo Mosaic section of Wood construction](../blocks/WoodConstruction.md#bamboo-mosaic) owns the conversion and material differences. [Item registration][item]

## Obtaining

Place **2 [Bamboo Slabs](BambooSlab.md) vertically, one above the other**, in a crafting grid to make **1 Bamboo Mosaic**. This fits the inventory grid and requires regular Bamboo Slabs, not Bamboo Mosaic Slabs or full Bamboo Planks. [Recipe][recipe]

Mining the placed block normally returns **1 Bamboo Mosaic**, including by hand. An unbroken axe is the efficient tool; see [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

At a [Crafting Table](../blocks/CraftingTable.md), use **3 Bamboo Mosaic in one horizontal row → 6 [Bamboo Mosaic Slabs](BambooMosaicSlab.md)**, or **6 Bamboo Mosaic in rows of 1, 2, then 3 aligned along one side → 4 [Bamboo Mosaic Stairs](BambooMosaicStairs.md)**. [Slab recipe][slab-recipe] · [Stair recipe][stair-recipe]

Bamboo Mosaic is absent from the generic **planks** ingredient tag. Keep [Bamboo Planks](BambooPlanks.md) for recipes requiring planks, including regular Bamboo construction shapes. [Planks ingredient tag][planks-tag]

## Behavior

The full block has no facing or waterlogged state. It is explicitly accepted as furnace fuel for **300 default burn ticks per item**; its fuel entry does not make it a plank ingredient. See [Wood construction: fire and furnace fuel](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Block registration][block] · [Fuel table][fuel]

## Notes

Related: [Bamboo Slab](BambooSlab.md) · [Bamboo Planks](BambooPlanks.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L132
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_mosaic.json
[slab-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic_slab.json
[stair-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_mosaic_stairs.json
[planks-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/planks.json
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L199-L207
[fuel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L86
