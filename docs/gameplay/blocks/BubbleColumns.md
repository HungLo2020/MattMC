# Bubble Columns

A **Bubble Column** (`minecraft:bubble_column`) is a source-water-filled block that pushes entities vertically. **Soul Sand makes an upward column; Magma Block makes a downward column.** Soul Soil and the separate imported Primal Magma block are not the named bases in this callback. [Column direction and allowed bases][column] · [Soul Sand trigger][soul] · [Magma trigger][magma]

## Building a column

Build a contained shaft and fill **every intended column cell with source Water**, then place the desired base directly underneath the lowest water cell. A single bucket poured at the top normally produces falling Water below it, which is not eligible for this conversion. Use [Water source rules](WaterAndLava.md#renewable-source-pools) and [Water Bucket controls](../items/WaterBucket.md) when filling the shaft. This is a source-based setup, not an in-game tested elevator design. [Eligible water check][column] · [Source versus falling flow][flow]

The conversion accepts only:

- An existing Bubble Column
- An actual Water block whose fluid amount is at least 8 **and is a source**

Waterlogged building blocks and planted Kelp are not Water blocks for this test. The update walks upward through consecutive eligible cells and stops at the first ineligible block. The code does not impose a separate small column-height cap; the containing world and filled shaft still bound it. [Conversion loop and eligibility][column]

Soul Sand and Magma schedule a column update **20 game ticks** after placement, and when an upper-neighbor update finds Water. Existing column support/direction changes schedule a **5-tick** recheck. Tick scheduling and loaded-world conditions matter; do not treat these as a measured elevator throughput. [Base schedules][soul] [magma] · [Column neighbor updates][column]

## Direction, motion and air

Each column block stores its direction in `drag`: **false** for the Soul Sand upward flow and **true** for the Magma downward flow. Higher cells inherit the lower column's direction. Removing the valid base or interrupting support can turn affected column cells back into ordinary Water during updates. [Direction selection, support and replacement][column]

The default entity handlers add upward or downward motion and reset fall distance while inside a column. A column ending under a collision-free, fluid-free space invokes a separate surface-motion handler, allowing a stronger upward exit. Individual entity types can override these callbacks; the guide does not promise one speed for every mob, item, boat or rider. [Column dispatch][column] · [Default movement handlers][entity]

For breathing, the check is at the **living entity's eye position**. Eyes in a Bubble Column take the air-recovery branch instead of normal underwater air depletion. Having only the feet or lower body in bubbles is not the same check. See [Water and Lava](WaterAndLava.md#light-fire-and-breathing) for the shared breathing context. [Eye-position and air supply][living]

A downward column's Magma base has its own hot-floor step callback: it requests damage for a living entity that is not stepping carefully. The column's air recovery does not disable that separate floor interaction. [Magma stepping condition][magma]

## Water pickup and block state

Every Bubble Column exposes a **source Water fluid state**. Using an empty bucket on it removes that column block and returns a **Water Bucket**; it does not give a Bubble Column inventory item. Nearby fluid/support updates can then change the remaining shaft. The column itself has no ordinary block-loot table. [Fluid state and bucket pickup][column] · [Block registration][blocks] · [Bucket interaction][bucket]

Its outline is empty and its normal block rendering is invisible; the particles and water make the effect visible. It does not provide a solid platform inside the shaft. If an elevator does not form, check the exact base, source status of each cell, and interruptions by other blocks before assuming the particle appearance alone establishes a complete column. [Shape and rendering][column]

## Verification and related pages

Source-reviewed on **2026-10-02** at `e87cde38c872d30ae86139bbee181937603af769`, including registered block properties, both active base triggers, source-water eligibility, update propagation, entity dispatch, air supply and bucket pickup. No in-game elevator, breathing, motion or floor-damage test was run. Data, world updates and later code changes can alter results.

[Blocks](Blocks.md) · [Water and Lava](WaterAndLava.md) · [Kelp](Kelp.md) · [Magma Block item](../items/MagmaBlock.md) · [Soul Sand item](../items/SoulSand.md)

[column]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/BubbleColumnBlock.java
[soul]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SoulSandBlock.java
[magma]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/MagmaBlock.java
[flow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/material/FlowingFluid.java
[entity]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/Entity.java#L2706-L2759
[living]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L5224-L5235
[bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BucketItem.java
