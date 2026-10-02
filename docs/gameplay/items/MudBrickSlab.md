# Mud Brick Slab

**Mud Brick Slab** (`minecraft:mud_brick_slab`) is the inventory form of the matching block. See [Mud, Packed Mud and Mud Bricks](../blocks/MudAndMudBricks.md#mud-brick-slab) for placed behavior and the shared production chain.

## Obtaining

Craft or stonecut Mud Bricks using the [canonical slab recipes](../blocks/MudAndMudBricks.md#crafting-and-stonecutting). An unbroken pickaxe, including Wood, is required to collect placed slabs. A single slab drops one slab; a double slab drops two. Silk Touch is unnecessary and Fortune adds no slabs.

## Usage

Place slabs in the upper or lower half of a block space for floors, roofs and details. The [family guide](../blocks/MudAndMudBricks.md#mud-brick-slab) covers placement and water.

## Behavior

Two matching slabs combine into a double-slab state, not a Mud Bricks block item. Single slabs can hold source water; completing a double clears waterlogging, and double slabs cannot be filled.

## Notes

- This is a registered placeable block item
- Recipes and loot can be changed by server data packs
- Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`; no in-game test was run

[Item registration][slab-item] · [Block properties][slab-reg] · [Slab loot][loot-mud_brick_slab] · [Slab states][slab-state] · [Bucket handling][waterlogging]

[slab-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L404
[slab-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L3877-L3886
[loot-mud_brick_slab]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud_brick_slab.json
[slab-state]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L115
[waterlogging]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L48
