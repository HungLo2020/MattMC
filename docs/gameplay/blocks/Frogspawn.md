# Frogspawn

**Frogspawn** (`minecraft:frogspawn`) is the water-surface egg block laid after [Frog breeding](../mobs/Frog.md#breeding-and-laying-frogspawn). Protect it in place until it hatches into [Tadpoles](../mobs/Tadpole.md); breaking it does not give an item to move elsewhere. [Registration][block] · [Hatching and destruction][spawn] · [Empty loot table][loot]

## Obtaining and laying conditions

Breed two ready frogs with Slimeballs and provide a reachable shoreline. The pregnant frog lays **one Frogspawn block** while standing on land beside suitable water. The actual laying check requires a neighboring **water source** with an empty top collision face and **air in the block space above the water**, where the Frogspawn will go. Keep that surface clear. [Breeding guide](../mobs/Frog.md#breeding-and-laying-frogspawn) · [Active laying behavior][lay]

The item is also listed in Creative. Its item behavior targets source water and attempts placement above it; see the [Frogspawn item](../items/Frogspawn.md). This is separate from obtaining an egg block by breeding in Survival. [Creative entry][creative] · [Water-surface item placement][item-placement]

## Water support and protection

Frogspawn survives when the fluid directly below is **source water** and there is **no fluid in the Frogspawn block's own position**. Flowing water is not the source-fluid type required by this check. Removing its supporting water or flooding its position invalidates it; neighbor updates or its scheduled tick can destroy it. [Survival and update checks][spawn]

Keep falling blocks, such as falling Sand or Gravel, away from the eggs. The active block-contact callback destroys Frogspawn when a **falling-block entity** enters it. The egg block has no collision and is registered to break instantly. [Falling-block contact][spawn] · [Block properties][block]

## Hatching

Placement schedules a hatch after **3,600–11,999 game ticks**. At 20 ticks per second, this is approximately **3 to just under 10 minutes** of ticking game time. When the scheduled callback runs, the block checks its water support again; valid eggs hatch, while invalid eggs are destroyed. [Scheduled delay and hatch callback][spawn]

Hatching removes the Frogspawn and creates **2–5 Tadpoles** in the water below, marked persistent. Their growth timer is a separate stage; see [Tadpole growth](../mobs/Tadpole.md#growth-and-feeding). The hatch delay is not a promise about elapsed real time while the world is paused or the area is not ticking. [Tadpole creation][spawn]

### If the eggs are not hatching

- **Wait for the scheduled hatch, rather than increasing `randomTickSpeed`.** The delay is chosen when the eggs are placed. Changing the random-tick rule does not shorten that queued delay; setting it to zero does not disable the scheduled hatch. Keep the area ticking and account for pauses or lag using [ticks and chunk activity](../mechanics/TicksAndChunkActivity.md#what-kind-of-tick-does-the-task-need). [Scheduled hatch][hatch-current] · [Server dispatch][hatch-dispatch]
- **Bone Meal does not accelerate Frogspawn.** The egg block is not a Bone Meal growth target. Save Slimeballs for [feeding the hatched Tadpoles](../mobs/Tadpole.md#growth-and-feeding), which is a separate growth stage. [Block class][hatch-current] · [Bone Meal target check][hatch-bonemeal]
- **There is no nighttime or light-level requirement in the hatch check.** Adding a Torch or shading the eggs does not unlock an extra condition. Protect the [source-water support](#water-support-and-protection) instead: invalid support destroys the eggs when checked. [Hatch and support checks][hatch-current]

## Collection limits

The bundled block loot table has **no item pools**. Ordinary mining, Shears, and Silk Touch therefore do not recover a Frogspawn item. Hatching and the block's own destruction helper also remove it without drops. There is no bundled crafting recipe for the item. [Loot table][loot] · [Destruction helper][spawn]

To move the next generation, wait for hatching and collect each living Tadpole with a **Water Bucket**. An empty Bucket is not the capture item; follow [Bucket of Tadpole](../items/BucketOfTadpole.md).

Related: [Frog](../mobs/Frog.md) · [Tadpole](../mobs/Tadpole.md) · [Frogspawn item](../items/Frogspawn.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game breeding, placement, hatching, destruction, or collection test was run. Data packs can change tags, variant selection, and loot; the values above describe bundled source behavior.

The hatch-troubleshooting section was source-reviewed on **2026-10-16** at `11346d05b851dc3b2b2b3efd0bb92252c350d521`, including current registration/native tick flags, scheduled dispatch and Bone Meal eligibility. No hatch-timing, command, lighting, feeding or chunk-boundary gameplay test was run; the earlier review retains its scope. [Native definition][hatch-native]

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L6754-L6764
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FrogspawnBlock.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/frogspawn.json
[lay]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/TryLaySpawnOnWaterNearLand.java
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L948
[item-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/PlaceOnWaterBlockItem.java

[hatch-current]: https://github.com/HungLo2020/MattMC/blob/11346d05b851dc3b2b2b3efd0bb92252c350d521/src/main/java/net/minecraft/world/level/block/FrogspawnBlock.java#L28-L104
[hatch-dispatch]: https://github.com/HungLo2020/MattMC/blob/11346d05b851dc3b2b2b3efd0bb92252c350d521/src/main/java/net/minecraft/server/level/ServerLevel.java#L394-L400
[hatch-bonemeal]: https://github.com/HungLo2020/MattMC/blob/11346d05b851dc3b2b2b3efd0bb92252c350d521/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[hatch-native]: https://github.com/HungLo2020/MattMC/blob/11346d05b851dc3b2b2b3efd0bb92252c350d521/src/main/rust/content/block/definitions/catalog.rs#L1201
