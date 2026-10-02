# Scaffolding

**Scaffolding** (`minecraft:scaffolding`) is a temporary building platform with a special placement item. It can extend a column or walkway while you use an existing piece. The [block guide](../blocks/Scaffolding.md) explains those controls, climbing, support, and collapse. [Item registration][item] · [Placement behavior][placement]

## Obtaining and use

Craft it from raw **Bamboo and String** using the canonical [Scaffolding recipe on the String page](String.md#selected-crafting-recipes). Ordinary mining returns one item, without a tool or Silk Touch requirement; Fortune does not multiply the drop. [Recipe][recipe] · [Block properties][block] · [Loot][loot]

A supported column allows **six horizontal extensions** before another support route is needed. The next piece can fall, and removing support from an existing structure can break connected pieces. Read [support and collapse](../blocks/Scaffolding.md#support-overhangs-and-collapse) before extending a work platform. [Support calculation][support] · [Support updates][tick]

Scaffolding also has a default Furnace fuel value; see [water and fire](../blocks/Scaffolding.md#water-and-fire) for the checked value and related limitations. [Fuel registration][fuel]

Related: [Bamboo](Bamboo.md) · [String](String.md) · [Scaffolding block](../blocks/Scaffolding.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Item registration, recipe, mining loot, placement, support, and fuel checked. No in-game test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L991
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ScaffoldingBlockItem.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/scaffolding.json
[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5294-L5305
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/scaffolding.json
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L157-L181
[tick]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L97-L136
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L96
