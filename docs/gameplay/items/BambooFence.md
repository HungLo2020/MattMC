# Bamboo Fence

**Bamboo Fence** (`minecraft:bamboo_fence`) forms connected railings and barriers. The [Wood construction fence guide](../blocks/WoodConstruction.md#fences) owns its connection and collision rules. [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), fill **2 rows with Bamboo Planks–Stick–Bamboo Planks**. The total is **4 [Bamboo Planks](BambooPlanks.md) + 2 [Sticks](Stick.md) → 3 Bamboo Fences**. Each plank slot requires Bamboo Planks; Bamboo Mosaic and other plank materials cannot substitute. [Recipe][recipe]

Mining a fence normally returns **1 Bamboo Fence**, including by hand. An unbroken axe is efficient; see [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Build a railing or pen and add a [Bamboo Fence Gate](BambooFenceGate.md) for a passage. The gate has a different recipe: it uses more sticks and fewer planks than the fence.

## Behavior

Bamboo Fence connects horizontally to the other tagged wooden fences, suitable sturdy faces, and correctly oriented fence gates. Connection exceptions and the Nether Brick Fence distinction are covered in [Wood construction: fences](../blocks/WoodConstruction.md#fences). [Connection checks][fence] · [Wooden-fence tag][fence-tag]

Its collision reaches **1.5 blocks high**, above the visible one-block post. Fences can be waterlogged; removing a rail connection does not remove the fence. [Fence placement and updates][fence] · [Collision and water behavior][cross]

## Notes

Related: [Bamboo Fence Gate](BambooFenceGate.md) · [Bamboo Planks](BambooPlanks.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L502
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_fence.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_fence.json
[fence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FenceBlock.java#L37-L124
[fence-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/wooden_fences.json
[cross]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java
