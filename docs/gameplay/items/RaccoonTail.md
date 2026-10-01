# Raccoon Tail

The **Raccoon Tail** is a registered item from the bundled Alex's Mobs content. It is available in Creative, but **no Survival source or gameplay use was found in the active source and bundled data**. Do not assume that killing a [Raccoon](../mobs/Raccoon.md) will supply one. [Registration][registration] · [Creative listing][creative] · [Entity loot data][loot]

## Obtaining

Find it in the Creative inventory, or use `/give @s minecraft:raccoon_tail` with command permission. No raccoon death-loot table, recipe output, or special tail-dropping interaction was found in the checked snapshot. [Creative listing][creative] · [Recipes][recipes] · [Raccoon implementation][raccoon] · [Loot data][loot]

## Use and limitations

It is registered as a plain item, without food, armor, or a special use action. No bundled recipe consumes it, and no active equipment recipe using raccoon tails was found. The item name and Creative availability should not be read as a working crafting or farming progression. [Plain-item registration][registration] · [Registration helper][helper] · [Recipes][recipes]

## Verification scope

Source-reviewed on **2026-10-01** against MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2`. Acquisition and use were not tested in-game. A server's added data packs can provide recipes or loot that are absent from this snapshot.

## Related pages

- [Raccoon](../mobs/Raccoon.md)
- [Items](Items.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2127
[helper]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2796-L2798
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1825
[recipes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities
[raccoon]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java
