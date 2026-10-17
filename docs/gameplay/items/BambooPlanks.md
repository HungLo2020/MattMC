# Bamboo Planks

**Bamboo Planks** (`minecraft:bamboo_planks`) are full building blocks and the starting ingredient for regular Bamboo construction shapes. See [Wood construction: planks and materials](../blocks/WoodConstruction.md#planks-and-materials). [Item registration][item]

## Obtaining

Put **1 [Block of Bamboo](BlockOfBamboo.md) or [Block of Stripped Bamboo](BlockOfStrippedBamboo.md)** in any crafting slot to make **2 Bamboo Planks**. This shapeless recipe fits the inventory grid. Raw [Bamboo](Bamboo.md) is not a direct input; see [Bamboo processing](../blocks/Bamboo.md#selected-uses-and-fuel) for that route. [Recipe][recipe] · [Accepted input tag][input]

Mining placed Bamboo Planks normally returns **1 Bamboo Planks item**, including when mined by hand. An unbroken axe is the efficient tool; see [Wood construction: mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Use Bamboo Planks for [Bamboo Slabs](BambooSlab.md), [Stairs](BambooStairs.md), [Fences](BambooFence.md), [Fence Gates](BambooFenceGate.md), [Buttons](BambooButton.md), and [Pressure Plates](BambooPressurePlate.md). The construction layouts use Bamboo Planks specifically; see [matching-material recipes](../blocks/WoodConstruction.md#crafting-construction-shapes).

Bamboo Planks also belong to the generic **planks** ingredient tag. [Bamboo Mosaic](BambooMosaic.md) does not, so it cannot replace planks in recipes using that tag. [Planks ingredient tag][planks-tag]

## Behavior

Placed planks are full blocks without a facing or waterlogged state. For placement, burning, and the **300 default furnace burn ticks per item**, use [Wood construction](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Block registration][block] · [Fuel table][fuel]

## Notes

Related: [Bamboo](../blocks/Bamboo.md) · [Bamboo Mosaic](BambooMosaic.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L129
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_planks.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_planks.json
[input]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/bamboo_blocks.json
[planks-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/planks.json
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L190-L198
[fuel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L86
