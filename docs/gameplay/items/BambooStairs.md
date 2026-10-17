# Bamboo Stairs

**Bamboo Stairs** (`minecraft:bamboo_stairs`) are regular Bamboo stair building blocks. See [Wood construction: stairs](../blocks/WoodConstruction.md#stairs) for shared orientation and corner rules. [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), arrange **6 [Bamboo Planks](BambooPlanks.md) in rows of 1, 2, then 3, aligned along one side → 4 Bamboo Stairs**. The horizontal mirror also works. [Bamboo Mosaic](BambooMosaic.md) is not an input to this recipe. [Recipe][recipe] · [Mirrored crafting match][matching]

Mining normally returns **1 matching stair item**, including by hand, regardless of its placed half or corner shape. An unbroken axe is efficient; see [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Use these stairs for stepped paths, roofs, and trim. Regular Bamboo Stairs belong to the generic **wooden stairs** item tag; [Bamboo Mosaic Stairs](BambooMosaicStairs.md) do not. [Wooden-stair tag][stair-tag]

## Behavior

Stairs face in your horizontal placement direction. The clicked face and height choose normal or upside-down placement. Nearby stairs can form inner or outer corners when their directions and halves fit; different stair materials can form corners together. These stairs can also be waterlogged. Use the [shared stair guide](../blocks/WoodConstruction.md#stairs) for the corner restrictions and [waterlogging guide](../blocks/WoodConstruction.md#waterlogging-and-power) for filling and draining. [Placement, corners, and water state][stair-placement]

## Notes

Related: [Bamboo Mosaic](BambooMosaic.md) · [Bamboo Planks](BambooPlanks.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L599
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_stairs.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_stairs.json
[matching]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[stair-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/wooden_stairs.json
[stair-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/StairBlock.java#L93-L164
