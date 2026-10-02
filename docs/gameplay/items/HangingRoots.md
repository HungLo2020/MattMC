# Hanging Roots

Hanging Roots is the item for **`minecraft:hanging_roots`**. Collect the placed plant with **Shears**; Silk Touch on a different tool does not satisfy its loot condition. [Item][items] · [Loot][loot-hanging_roots]

A renewable route is Bone Meal on Rooted Dirt with air below, followed by harvesting the new roots with Shears. Hoeing Rooted Dirt also produces the item directly while converting the soil. Keep those controls in the [Rooted Dirt guide](../blocks/SoilSandAndGravel.md#rooted-dirt-and-hanging-roots). There is no crafting recipe. [Growth callback][rooted]

Place Hanging Roots under a sturdy downward ceiling face. It can be waterlogged, but does not climb, extend downward, or respond to Bone Meal itself. [Placed behavior][roots]

See [Hanging Roots and Spore Blossom](../blocks/HangingRootsAndSporeBlossom.md#hanging-roots) for natural acquisition, source-water placement, bucket recovery, and support loss.

[items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L359-L379
[loot-hanging_roots]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/hanging_roots.json
[rooted]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/RootedDirtBlock.java
[roots]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/HangingRootsBlock.java
