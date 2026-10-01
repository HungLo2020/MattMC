# Water Bucket

A **Water Bucket** (`minecraft:water_bucket`) carries water for placement, waterlogging, and compatible animal collection. It stacks to one. Fill an empty [Bucket](Bucket.md) from a water source block or a full Water Cauldron. [Registration][items] · [Collection][bucket] · [Cauldron route][cauldron]

## Placing and recovering water

Use the filled bucket against a suitable block face. In an ordinary dimension, a successful placement creates water in the adjacent available position, or fills a compatible waterlogging container. The ordinary Survival result is an empty Bucket. The target must pass the block and permission checks; aiming at any solid block does not guarantee replacement. [Placement and result][bucket]

The waterlogging path first considers the clicked block when it implements the liquid-container interface. Sneak/Crouch changes the replace/container check and can redirect placement to the adjacent face instead. For a predictable waterlogged build, release Sneak/Crouch and check the target's own block guide. [Placement selection and fallback][bucket] · [Shared waterlogging behavior][waterlogging]

Use an empty bucket to recover a source. Removing a source does not instantly prove the whole stream is gone: existing flow updates and neighboring sources can sustain or refill water. Clear the actual source and allow the fluid update rules to run. [Liquid pickup][liquid] · [Fluid updates][flow]

## The Nether restriction

In an **ultrawarm dimension**, ordinary bucket water placement produces extinguishing effects and returns success **without placing water**. Survival still receives the empty bucket. The bundled Nether is ultrawarm. Do not rely on a normal Water Bucket as a Nether pool or fall-rescue plan. [Ultrawarm branch][bucket] · [Nether dimension type][nether]

A **Water Cauldron is a separate block interaction**. Its bucket-fill handler sets the cauldron to water level 3 rather than placing a water-fluid block. The ordinary bucket evaporation branch should not be generalized to mean that every container interaction behaves like open water. [Cauldron fill handler][cauldron]

## Renewable source pools

The default `waterSourceConversion` rule is **true**. During a flowing-fluid update, two horizontally adjacent matching source neighbors can create a source when the connections are open and the block below is solid or another matching source. [Rule default][rules] · [Water rule lookup][water] · [Source conversion][flow]

A practical source arrangement is a contained **2 × 2 pool over a solid floor**, with initial sources placed in opposite corners. After updates, the other two cells satisfy the two-neighbor rule; drawing from one corner can let it refill from the remaining sources. This is a source-derived construction pattern, not an in-game-tested farm. It depends on source conversion being enabled and unobstructed fluid connections.

Water Bucket collection of living bucketable creatures uses a separate entity interaction. See the relevant mob/bucket guide for what survives transfer; an ordinary empty bucket is not the required starting item. [Shared animal collection][bucketable]

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No gameplay test of waterlogging, fluid updates, source pools, Nether evaporation, or cauldron use was run. Game rules and block states can change the described results.

Related: [Bucket](Bucket.md) · [Lava Bucket](LavaBucket.md) · [Farmland](../blocks/Farmland.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1511-L1517
[bucket]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BucketItem.java
[cauldron]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[waterlogging]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[liquid]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/LiquidBlock.java#L203-L209
[flow]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/material/FlowingFluid.java
[nether]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/dimension_type/the_nether.json
[rules]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/GameRules.java#L194-L199
[water]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L83-L85
[bucketable]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L71-L88
