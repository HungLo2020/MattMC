# Frog

**Frogs** (`minecraft:frog`) breed through a water-based lifecycle: **Frogspawn → Tadpole → adult Frog**. Raise Tadpoles in different biomes to obtain the variants used for different Froglights. Frogs have **10 health points (5 hearts)** and can move on land and in water. [Entity registration][entity] · [Active attributes][attributes] · [Frog attributes and movement][frog]

## Finding frogs

The bundled **Swamp** and **Mangrove Swamp** biome tables list Frogs in groups of **2–5**. Their ordinary ground-spawn check requires raw brightness **above 8** and a block below in the frog-spawnable tag, which contains **Grass Block, Mud, Mangrove Roots, and Muddy Mangrove Roots**. The normal ground-placement and empty-space checks still apply; a listed surface alone does not guarantee a spawn. [Swamp table][swamp] · [Mangrove Swamp table][mangrove] · [Placement registration][spawn-registration] · [Frog predicate][spawn-predicate] · [Brightness][brightness] · [Ground tag][ground] · [Placement checks][ground-check]

Naturally spawned Swamp Frogs use the **Temperate** selection; Mangrove Swamp Frogs use **Warm**. The broader variant biome tags do not add Frog spawns to those other biomes. Use [Tadpoles](Tadpole.md) for the checked route to a Cold variant. Creative players also have the [Frog Spawn Egg](../items/FrogSpawnEgg.md). [Variant selectors][variants] · [Warm tag][warm] · [Cold tag][cold] · [Spawn egg][egg]

## Breeding and laying frogspawn

Use **[Slimeballs](../items/Slimeball.md)** to tempt Frogs and feed **one to each of two ready adults**. Slimeball is the only item in the bundled frog-food tag. Give the pair accessible land, room to approach one another, and nearby water. The breeding behavior runs in the land/idle activity and requires suitable, non-panicking partners. [Food tag][food] · [Feeding][animal] · [Frog activities][ai] · [Partner behavior][breeding]

Successful breeding marks one Frog **pregnant** rather than creating a baby Frog immediately. Both parents receive a **6,000-tick breeding cooldown**, about **5 minutes at 20 ticks per second** while ticking. [Pregnancy callback][pregnancy] · [Shared breeding finalization][breed-finalize]

Provide a simple shoreline with a solid place for the pregnant Frog to stand beside an open **water source**. The laying behavior checks that the Frog is on the ground and outside water, with no attack target; the adjacent water position needs an empty top collision face and **air above the water**. It places **one [Frogspawn block](../blocks/Frogspawn.md)** in that air space and clears pregnancy. A flooded pen with no accessible shore or an obstructed water surface can prevent laying. [Laying behavior][lay] · [Active laying activity][ai]

Protect the eggs and follow the [Frogspawn hatch guide](../blocks/Frogspawn.md#hatching). Its Tadpoles grow separately from the parents' breeding cooldown. Use the [Tadpole guide](Tadpole.md#growth-and-feeding) for growth time and Slimeball feeding.

## Variants and where to grow tadpoles

The adult variant is selected from the **biome where a Tadpole matures**. Transport the young with a [Bucket of Tadpole](../items/BucketOfTadpole.md), release it in a safe pool in the chosen biome, and allow it to finish growing there. Its parents' variants and the hatching biome are not inputs to this conversion selection. [Tadpole conversion][tadpole-conversion] · [Frog finalization][variant-selection] · [Biome context][context]

| Adult variant | Verified examples of maturation biomes | Selection rule |
| --- | --- | --- |
| Temperate | Swamp, Plains, Forest | Default when no higher-priority variant condition matches |
| Warm | Mangrove Swamp, Desert, Jungle | Bundled warm-variant biome tag |
| Cold | Snowy Plains, Snowy Taiga, Frozen Peaks | Bundled cold-variant biome tag |

Warm and Cold conditions have priority **1**; Temperate is an unconditional priority-**0** fallback. The current bundled warm/cold tags have no overlap. These are data-driven biome rules, so judging only by a biome's name or apparent weather can mislead. [Variant definitions][variants] · [Warm definition][warm-variant] · [Cold definition][cold-variant] · [Temperate definition][temperate-variant] · [Warm tag][warm] · [Cold tag][cold] · [Priority selection][priority]

An adult stores its chosen variant; simply moving it to another biome does not rerun Tadpole maturation. Although Nether biomes are in the Warm tag, **a normal bucket release there evaporates its water**, so use a suitable Overworld pool for raising Tadpoles. [Variant storage][frog] · [Bucket release warning](../items/BucketOfTadpole.md#releasing-it-safely)

## Slimes, Magma Cubes, and Froglights

The bundled prey tag contains **Slimes and Magma Cubes**, and the active food check permits only their **smallest size**. The tongue behavior requires a reachable target and performs the killing attack; placing a Frog near an inaccessible or larger cube does not produce the resource. [Prey tag][prey] · [Size restriction][eat] · [Tongue behavior][tongue]

Use the [Magma Cube Froglight table](MagmaCube.md#drops-and-froglights) for the exact Warm/Pearlescent, Cold/Verdant, and Temperate/Ochre mapping and drop conditions. For the Slimeball route, see [Slime drops](Slime.md#drops-and-experience). Keep the Frog safe from the cubes while arranging access; the lifecycle guide does not establish a tested farm layout. [Magma Cube loot][magma-loot] · [Slime loot][slime-loot]

## Drops and persistence

The Frog's own bundled loot table has no item pools. A qualifying player-attributed kill has a base reward of **1–3 experience**, and successful breeding gives **1–7 experience** with mob loot enabled. Ordinary Frogs do not despawn merely because a player moves far away. [Frog loot][loot] · [Animal XP and persistence][animal] · [Death-XP conditions][death-xp] · [Breeding rewards][breed-finalize]

Related: [Tadpole](Tadpole.md) · [Frogspawn](../blocks/Frogspawn.md) · [Bucket of Tadpole](../items/BucketOfTadpole.md) · [Magma Cube](MagmaCube.md) · [Slime](Slime.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Natural spawning, breeding, laying, variant selection, and prey guidance are traced through active registry, biome, tag, and callback paths. The listed variant biomes are examples, not complete tag lists. No in-game spawning, breeding, laying, growth, bucket transfer, variant, or Froglight-farm test was run. Data packs can change food, prey, biome, variant, and loot data.

[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L644-L646
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L168
[frog]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java
[swamp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[mangrove]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L120
[spawn-predicate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L377-L381
[brightness]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L112-L114
[ground]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/frogs_spawnable_on.json
[ground-check]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L24-L42
[variants]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/FrogVariants.java
[warm]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_warm_variant_frogs.json
[cold]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_cold_variant_frogs.json
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1878
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/frog_food.json
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[ai]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/FrogAi.java
[breeding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/AnimalMakeLove.java
[pregnancy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L281-L294
[breed-finalize]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L215-L228
[lay]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/TryLaySpawnOnWaterNearLand.java
[tadpole-conversion]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Tadpole.java#L224-L232
[variant-selection]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L296-L303
[context]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/variant/SpawnContext.java
[warm-variant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/frog_variant/warm.json
[cold-variant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/frog_variant/cold.json
[temperate-variant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/frog_variant/temperate.json
[priority]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/variant/PriorityProvider.java
[prey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/frog_food.json
[eat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L357-L359
[tongue]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/ShootTongue.java
[magma-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/magma_cube.json
[slime-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/slime.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/frog.json
[death-xp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
