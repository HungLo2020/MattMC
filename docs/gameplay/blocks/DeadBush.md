# Dead Bush

A **Dead Bush** (`minecraft:dead_bush`) is dry decorative vegetation. Collect it with **Shears** if you want the [item](../items/DeadBush.md) for decoration or [Gelada Monkey breeding](../mobs/GeladaMonkey.md#feeding-and-breeding); breaking it without Shears produces possible Sticks instead. [Block registration][block] · [Loot][loot]

## Finding and collecting

The bundled **Desert** and **Badlands** biomes include Dead Bush patch features. Their placed features resolve to the configured patch that places `minecraft:dead_bush`; these are confirmed examples, not a complete biome list. [Desert feature list][desert] · [Badlands feature list][badlands] · [Desert placement][desert-placement] · [Badlands placement][badlands-placement] · [Configured patch][feature]

The block breaks instantly and does not require a tool tier. Its loot table has two alternatives:

- With Shears: **one Dead Bush**
- Otherwise: **0–2 Sticks**, with no Dead Bush item

There is no Silk Touch alternative or Fortune multiplier in this loot table. Use Shears to keep a breeding supply; merely breaking more bushes by hand will not collect the plant item. [Block properties][block] · [Drop alternatives][loot]

[Geladas clearing plants](../mobs/GeladaMonkey.md#clearing-plants-with-wheat) use an empty-tool destruction path, so their work follows the Stick alternative. An intact bush is not a guaranteed clearing reward. [Clearing callback][gelada] · [Empty-tool destruction][destroy]

## Placement and decoration

Place the item on a block in the dry-vegetation support tag: the bundled **sand**, **terracotta**, or **dirt** groups, or **Farmland**. These groups include ordinary Sand, Red Sand, Dirt, Grass Blocks, Coarse Dirt, and more. The bush has no collision and needs valid support underneath; it does not require nearby water. [Support check][dry] · [Support tag][support] · [Sand group][sand] · [Dirt group][dirt] · [Survival check][vegetation]

A Dead Bush can also be placed in an empty **[Flower Pot](FlowerPot.md)**. Use the filled pot with an empty hand to recover the bush while leaving the pot in place. The potted form is not in the Gelada clearing tag. [Potted registration][potted] · [Pot interaction][pot] · [Clearing tag][plants]

Bone Meal does not grow or duplicate this block: its registered class has no bonemeal target implementation. It is a decorative plant, not a crop with a growth cycle. [Plant class][dry] · [Bone Meal dispatch][bonemeal]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registration, two active biome-to-patch chains, support tags, mining loot, pot interaction, Bone Meal dispatch, and Gelada clearing. No in-game generation, harvesting, placement, or clearing test was run.

Related: [Dead Bush item](../items/DeadBush.md) · [Gelada Monkey](../mobs/GeladaMonkey.md) · [Shears](../items/Shears.md) · [Blocks](Blocks.md)

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L727-L738
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dead_bush.json
[desert]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/desert.json#L83-L96
[badlands]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/badlands.json#L83-L96
[desert-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_dead_bush_2.json
[badlands-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_dead_bush_badlands.json
[feature]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/patch_dead_bush.json
[gelada]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L421-L429
[destroy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[dry]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DryVegetationBlock.java
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/dry_vegetation_may_place_on.json
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/sand.json
[dirt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/dirt.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L28-L47
[potted]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2712
[pot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L56-L90
[plants]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/gelada_monkey_grass.json
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
