# Bamboo

**Bamboo** (`minecraft:bamboo`) is a plantable resource used for Sticks, Scaffolding, Blocks of Bamboo, and Furnace fuel. Its [block guide](../blocks/Bamboo.md) covers obtaining a starter supply, support, growth, Bone Meal, and harvesting. [Item registration][item] · [Block registration][blocks]

## Obtaining and planting

Ordinary mining of a Bamboo stalk or its small shoot gives **one Bamboo item**. No tool or Silk Touch is required, and Fortune does not multiply the drop. An unbroken sword has the tagged instant-mining rule for both forms. [Stalk loot][loot] · [Shoot loot][shoot-loot] · [Sword tag][sword-tag] · [Sword rule][sword]

Use the item on [suitable, fluid-free planting space](../blocks/Bamboo.md#planting-and-support) to start a shoot or extend a stalk. There is no separate Bamboo Sapling item needed for this planting route. [Placement][plant]

## Resource uses

See [selected uses and fuel](../blocks/Bamboo.md#selected-uses-and-fuel) for the checked packing recipe, links to the canonical Stick and Scaffolding recipes, and default fuel value. Bamboo can also be displayed in a [Flower Pot](../blocks/FlowerPot.md#supported-plants). [Block recipe][block-recipe] · [Fuel registration][fuel] · [Potted registration][pot]

Related: [Stick](Stick.md) · [Scaffolding](Scaffolding.md) · [Block of Bamboo](BlockOfBamboo.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, placement, plant/shoot loot, selected recipes, and fuel checked. No in-game test was run; the broader Bamboo wood family is not covered here.

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L382
[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5189-L5221
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/bamboo.json
[shoot-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/bamboo_sapling.json
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/sword_instantly_mines.json
[sword]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[plant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java#L80-L112
[block-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/bamboo_block.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L96
[pot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5221
