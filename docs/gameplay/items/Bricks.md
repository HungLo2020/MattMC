# Bricks

**Bricks** (`minecraft:bricks`) is a full building block made from loose [Brick](Brick.md) ingredients. Use its block item for walls and floors, or convert it into masonry shapes. [Block registration][bricks-block] · [Item registration][bricks-item]

## Obtaining and use

Use the [Bricks crafting recipe](../blocks/ClayAndBricks.md#crafting-and-smelting), then place the result as an ordinary full block. The shared guide also lists exact [Brick Slab, Stair, and Wall recipes](../blocks/ClayAndBricks.md#brick-slabs-stairs-and-walls), including the better stair yield from a Stonecutter. [Bricks recipe][bricks-recipe]

Collect it with an **unbroken pickaxe**; a Wooden Pickaxe is sufficient under the bundled tags. Correct-tool mining returns **one Bricks block**, with no Silk Touch requirement or Fortune bonus. Hand breaking does not satisfy its required tool gate. See [building and collection](../blocks/ClayAndBricks.md#building-and-collecting-bricks) for the checked tool rules and shape drops. [Bricks properties][bricks-block] · [Pickaxe tag][pickaxe] · [Bricks loot][bricks-loot]

Related: [Brick](Brick.md) · [Brick Slab](BrickSlab.md) · [Brick Stairs](BrickStairs.md) · [Brick Wall](BrickWall.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, recipe, mining tags, and loot checked; shared placement evidence is in the block guide. No in-game test was run.

[bricks-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1103-L1106
[bricks-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L417
[bricks-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/bricks.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[bricks-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/bricks.json
