# Lava Bucket

A **Lava Bucket** (`minecraft:lava_bucket`) carries a lava source and is a long-burning furnace fuel. It stacks to one and returns an empty Bucket through its normal placement and fuel-remainder paths. [Registration][items] · [Placement][bucket] · [Fuel values][fuel]

## Obtaining and placing

Use an empty [Bucket](Bucket.md) on an ordinary **lava source block**. Flowing lava does not satisfy the liquid pickup check. A Lava Cauldron is another confirmed source: empty-bucket interaction collects it and empties the cauldron. [Liquid pickup][liquid] · [Cauldron collection][cauldron]

Use the filled bucket against a suitable face to place lava in an available target position. Successful ordinary Survival placement leaves an empty bucket. Unlike the waterlogging-specific branch, this does not fill an arbitrary waterloggable block with lava. Lava can flow away from the source and harm entities or ignite nearby flammable material; contain the intended position before placing it near a build. [Bucket placement][bucket] · [Lava flow/fire behavior][lava]

The default `lavaSourceConversion` rule is **false**. Do not assume a two-source water-pool layout duplicates lava under the default rules. Lava's rule lookup is separate from the enabled-by-default water rule. This does not rule out other verified lava-generation systems; it describes the ordinary source-neighbor conversion route. [Rule defaults][rules] · [Lava rule lookup][lava]

## Furnace fuel

One Lava Bucket supplies **20,000 default furnace burn ticks**, enough energy for **100 uninterrupted 200-tick recipes**. The furnace consumes the lava-filled item as fuel and leaves its empty-bucket crafting remainder in the fuel slot. The number is an energy budget, not a promise of 100 outputs if processing is interrupted or recipes have different durations. [Fuel value][fuel] · [Furnace consumption/remainder][furnace]

Keep input available and output space clear: fuel keeps burning while the furnace is lit. For short jobs, a smaller fuel item can waste less of a full lava bucket's burn time. See [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) and [Hopper furnace connections](../blocks/Hopper.md) for the broader workflow.

## Water contact

Water/lava meeting behavior depends on the flow direction and source state. In the ordinary lava-block neighbor check, contact with qualifying adjacent water replaces source lava with **Obsidian**, or non-source lava with **Cobblestone**. This is not a claim that every possible arrangement produces the same block; other fluid-spread paths exist. Avoid placing your only lava source beside water if you need to collect it again. [Lava-block conversion][liquid]

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No gameplay test of placement, fluid mixing, source conversion, cauldrons, or furnace throughput was run. World rules, fluid updates, and recipe timings affect outcomes.

Related: [Bucket](Bucket.md) · [Water Bucket](WaterBucket.md) · [Furnace](../blocks/Furnace.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1511-L1517
[bucket]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BucketItem.java
[liquid]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/LiquidBlock.java
[cauldron]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L45
[furnace]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
[lava]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/material/LavaFluid.java
[rules]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/GameRules.java#L194-L199
