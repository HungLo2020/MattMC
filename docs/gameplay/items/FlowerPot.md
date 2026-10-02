# Flower Pot

A **Flower Pot** (`minecraft:flower_pot`) is a placeable display for one supported plant. The [Flower Pot block guide](../blocks/FlowerPot.md) owns its crafting recipe, full supported-plant list, interactions, and collection rules. [Block registration][pot-block] · [Item registration][pot-item]

## Obtaining and use

Craft it from loose **Bricks** using the [Flower Pot recipe](../blocks/FlowerPot.md#crafting). The ingredient is [Brick](Brick.md), not the full Bricks building block. Ordinary mining of an empty pot returns **one Flower Pot** without a tool requirement. [Recipe][pot-recipe] · [Empty-pot loot][pot-loot]

Place the pot, then use a supported plant on it. To change plants, use the filled pot with an empty hand to recover its current plant, then add the replacement. This also works for [Dead Bush](../blocks/DeadBush.md#placement-and-decoration). [Pot interaction][pot-use]

Related: [Brick](Brick.md) · [Clay and Bricks](../blocks/ClayAndBricks.md) · [Dead Bush](DeadBush.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, crafting, empty-pot loot, and active use callbacks checked. No in-game crafting, potting, or mining test was run.

[pot-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2655
[pot-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2055
[pot-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/flower_pot.json
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/flower_pot.json
[pot-use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L56-L90
