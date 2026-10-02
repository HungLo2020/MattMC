# Sea Pickle

**Sea Pickles** (`minecraft:sea_pickle`) form clusters of **one to four** in a block space. Waterlogged clusters provide light; Bone Meal can multiply them on living Coral Blocks. The [item page](../items/SeaPickle.md) owns their Lime Dye smelting recipe. [Registration and light][pickle-reg] · [Cluster behavior][pickle]

## Finding and collecting

The bundled **Warm Ocean** biome includes the Sea Pickle placement, which resolves to the registered Sea Pickle generator. It attempts underwater clusters of one to four on suitable supporting blocks. This is one verified natural route, not a complete acquisition list or a guarantee for any particular chunk. [Warm Ocean features][warm] · [Placement][pickle-placed] · [Configuration][pickle-config] · [Feature registration][pickle-feature-reg] · [Generator][pickle-feature]

Clusters break instantly and require **no tool**. Ordinary mining returns their current **one, two, three, or four Sea Pickle items**, whether dry or waterlogged. Silk Touch is unnecessary and Fortune has no multiplier. Explosion decay can reduce the recovered count. [Block properties][pickle-reg] · [Default hardness/tool properties][defaults] · [Drop gate][gate] · [State-count loot][pickle-loot]

## Placement, water, and light

Place a Sea Pickle on a block whose **upper collision face is nonempty**, or whose upper face is sturdy. Coral is not required merely to place one. Support must remain valid; losing it removes the cluster through its neighbor update. [Placement and support][pickle-place]

Use more Sea Pickle items on an existing cluster to increase its count, up to four. Normal use combines them; secondary use bypasses this combining behavior. Each successful placement consumes one item through the ordinary block-item path. [Combining and replacement rules][pickle-place] · [Item placement][placement]

A new placement is waterlogged only when its destination contains the **source Water fluid type**. Flowing Water does not meet that particular test. You can also place dry pickles on valid support. A dry cluster is unlit, but it is not a permanently dead item variant: a [Water Bucket](../items/WaterBucket.md) can waterlog it through the shared waterlogging interaction. An Empty Bucket can recover its water. Follow the bucket guide for dimension and use restrictions. [Placement fluid check][pickle-place] · [Shared fill/drain behavior][waterlog] · [Bucket dispatch][bucket]

| Pickles in the cluster | Light level when waterlogged | Light level when dry |
| --- | ---: | ---: |
| 1 | **6** | **0** |
| 2 | **9** | **0** |
| 3 | **12** | **0** |
| 4 | **15** | **0** |

The light rule depends on the waterlogged state and count. Drying a cluster turns off the light without changing how many items its loot returns. Unlike collision-free Kelp and Seagrass, Sea Pickles use small collision shapes: **6/16 block high** for one to three, and **7/16 high** for four. [Light registration][pickle-reg] · [Shapes and water state][pickle] · [Default shape collision][shape] · [Loot counts][pickle-loot]

## Bone Meal multiplication

The target cluster must be **waterlogged** and sit directly on a block in the **Coral Blocks tag**. The bundled tag contains the five living full blocks: **Tube, Brain, Bubble, Fire, and Horn Coral Blocks**. Dead Coral Blocks and the small coral plants/fans are not members. No biome or light-level condition appears in this multiplication callback. [Target check][pickle-bone] · [Exact coral tag][coral]

A successful Bone Meal use fills the targeted cluster to **four pickles** and attempts new clusters nearby. Eligible neighboring positions must contain a **Water block** with a qualifying Coral Block underneath. The search covers a diamond within two horizontal steps, on the target's level and one level below; individual attempts have a **one-in-six chance** and create **one to four pickles**. The spread checks the Water block rather than a source-only fluid predicate, so it differs from item placement. [Multiplication and spread][pickle-bone] · [Bone Meal use][bone-use]

An already-full qualifying cluster can still accept Bone Meal and attempt to spread. Dry pickles or pickles on ordinary stone cannot use this multiplication route. A flat bed of the five tagged Coral Blocks under water is a source-derived starting layout; new-cluster yield remains random. [Target and full-cluster behavior][pickle-bone]

Related: [Ocean biomes](../biomes/Oceans.md) · [Sea Pickle item and dye recipe](../items/SeaPickle.md) · [Seagrass](Seagrass.md) · [Kelp](Kelp.md) · [Water Bucket](../items/WaterBucket.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registration, the Warm Ocean generation chain, cluster placement, source-water and waterlogging rules, support/collision, all four light/drop states, Coral Blocks tag, and active Bone Meal multiplication. No in-game generation, placement, light, bucket, farming, or harvesting test was run. Data packs can change world generation, tags, and loot.

[pickle-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5165-L5174
[pickle]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SeaPickleBlock.java
[warm]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json#L83-L84
[pickle-placed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/sea_pickle.json
[pickle-config]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/sea_pickle.json
[pickle-feature-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L119
[pickle-feature]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/SeaPickleFeature.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1001
[gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[pickle-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/sea_pickle.json
[pickle-place]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SeaPickleBlock.java#L47-L103
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java#L111-L138
[waterlog]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bucket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BucketItem.java#L54-L85
[shape]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L326
[pickle-bone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SeaPickleBlock.java#L125-L170
[coral]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/coral_blocks.json
[bone-use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
