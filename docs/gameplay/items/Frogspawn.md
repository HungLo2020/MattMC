# Frogspawn

**Frogspawn** (`minecraft:frogspawn`) is the item form of the egg block that hatches into Tadpoles. Use the [Frogspawn block guide](../blocks/Frogspawn.md) for water support, hatch timing, and egg protection. [Item registration][item]

## Obtaining

The item is listed in **Creative**. In Survival, [breeding Frogs](../mobs/Frog.md#breeding-and-laying-frogspawn) places the egg block directly on suitable water; it does not hand the player a Frogspawn item. The bundled block loot is empty, including with Silk Touch, and no bundled crafting recipe produces the item. [Creative entry][creative] · [Laying behavior][lay] · [Block loot][loot]

## Placement and transport

When available as an item, use it while targeting source water. Its placement handler targets the block above the water and then applies normal block-placement and survival checks. See [water support](../blocks/Frogspawn.md#water-support-and-protection) for the exact requirements. [Water-surface placement][placement] · [Survival check][spawn]

For Survival transport, let the eggs hatch and use a [Water Bucket](WaterBucket.md) on a living [Tadpole](../mobs/Tadpole.md), creating a [Bucket of Tadpole](BucketOfTadpole.md).

Related: [Frogspawn block](../blocks/Frogspawn.md) · [Frog](../mobs/Frog.md) · [Tadpole](../mobs/Tadpole.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game item placement, breeding, hatching, or collection test was run. Data packs can change tags, variant selection, and loot; the values above describe bundled source behavior.

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2524
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L948
[lay]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/TryLaySpawnOnWaterNearLand.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/frogspawn.json
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/PlaceOnWaterBlockItem.java
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FrogspawnBlock.java
