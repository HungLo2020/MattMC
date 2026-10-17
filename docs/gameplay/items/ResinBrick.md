# Resin Brick

## Obtaining

Smelt **one Resin Clump** in a fueled [Furnace](../blocks/Furnace.md) to obtain **one Resin Brick**. The recipe takes **200 ticks** and has **0.1 recipe XP**. It is a smelting recipe, not blasting or smoking. [Recipe][smelt]

## Usage

Craft **four Resin Brick items in a 2 × 2 square** into **one Resin Bricks block**. The loose item also supplies the Resin armor-trim material at a [Smithing Table](../blocks/SmithingTable.md). [Masonry recipe][craft-resin_bricks] · [Trim item registration][brick-item] · [Trim ingredient tag][trim-tag]

## Behavior

Resin Brick is an **ingredient item, not a placeable block**. To build, craft the block first, then use the [Resin masonry recipes](../blocks/Resin.md#slabs-stairs-walls-and-chiseled-bricks). The ingredient is distinct from Resin Clumps, Blocks of Resin, and the plural Resin Bricks block. [Ingredient registration][brick-item] · [Block-item registrations][items]

## Notes

* This item is registered as `minecraft:resin_brick`.
* Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no gameplay test was run. See [Resin](../blocks/Resin.md#resin-brick) for processing and use.

[smelt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/smelting/resin_brick.json
[craft-resin_bricks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_bricks.json
[brick-item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L2119
[trim-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/trim_materials.json
[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L561-L567
