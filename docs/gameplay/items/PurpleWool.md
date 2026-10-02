# Purple Wool

**Purple Wool** (`minecraft:purple_wool`) is the purple member of the [Wool and Carpet family](../blocks/WoolAndCarpet.md). Use it for colored builds or make matching [carpet](PurpleCarpet.md#crafting). [Block and item registration][blocks] · [Item registration][items]

## Obtaining

- **Recolor harvested wool:** combine **one [Purple Dye](PurpleDye.md)** with **one wool of any other bundled color**, shapeless, to obtain **one purple wool**. The input list includes all 15 other colors; it excludes purple wool itself. [Recipe][dye]
- **Shear a purple [Sheep](../mobs/Sheep.md#shearing-and-regrowth):** an eligible living, adult, unsheared sheep gives **1–3 purple wool**. Dye a sheep with fleece to establish a renewable supply; the [Sheep guide](../mobs/Sheep.md#dyeing-and-offspring-color) explains dyeing and regrowth. [Color routing][shear-routing] · [Color loot][shear]
- **Death drop:** an unsheared adult sheep of this color drops **one purple wool** with mob loot enabled. Looting does not increase this wool count. [Color selection][death-routing] · [Color loot][death] · [Adult and mob-loot gate][adult]

Breaking the placed block normally returns **one purple wool**, not carpet or dye. Its loot needs no Silk Touch and has an explosion-survival condition. See the [family mining guide](../blocks/WoolAndCarpet.md#mining-and-drops) for tools and shared restrictions. [Block loot][loot]

## Building and crafting

Use the [Purple Carpet recipe](PurpleCarpet.md#crafting) for a thin floor covering. For placement, fire precautions, furnace fuel, and vibration behavior, use the [shared block guide](../blocks/WoolAndCarpet.md#placement-and-support).

Related: [Sheep](../mobs/Sheep.md) · [Shears](Shears.md) · [All wool and carpet colors](../blocks/WoolAndCarpet.md#colors-and-item-ids) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. This page covers the checked crafting and sheep routes, not every structure or merchant source. No gameplay test was run. Data packs can change recipes, tags, and loot.

[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L326-L341
[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L811-L889
[shear-routing]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/shearing/sheep.json
[death-routing]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/sheep.json
[adult]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[dye]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_purple_wool.json
[shear]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/shearing/sheep/purple.json
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/sheep/purple.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/purple_wool.json
