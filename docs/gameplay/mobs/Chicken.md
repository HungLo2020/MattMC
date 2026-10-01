# Chicken

**Chickens** (`minecraft:chicken`) are small passive farm animals that produce eggs without being killed. Adults also supply feathers and meat. They have **4 health points (2 hearts)**, flee danger, and follow players holding suitable food. [Chicken behavior][chicken] · [Active attributes][attributes]

## Finding chickens

The bundled **plains, forest, savanna, and taiga** biome tables list chickens in groups of four. Ordinary ground spawning requires **grass blocks below** and raw brightness **above 8**. These examples are not a complete distribution list; suitable ground and light alone do not guarantee a spawn. [Plains][plains] · [Forest][forest] · [Savanna][savanna] · [Taiga][taiga] · [Spawn registration][spawn] · [Animal spawn check][animal] · [Ground tag][ground]

Adults are registered at **0.4 blocks wide × 0.7 blocks tall**. Other confirmed routes are breeding and [thrown eggs](../items/Egg.md#throwing-and-hatching); Creative players can use a [Chicken Spawn Egg](../items/ChickenSpawnEgg.md). [Registration][registration] · [Breeding][animal]

## Food and breeding

Hold any item in the bundled chicken-food tag to attract chickens, then feed one to each of two ready adults:

- [Wheat Seeds](../items/WheatSeeds.md)
- [Melon Seeds](../items/MelonSeeds.md)
- [Pumpkin Seeds](../items/PumpkinSeeds.md)
- [Beetroot Seeds](../items/BeetrootSeeds.md)
- [Torchflower Seeds](../items/TorchflowerSeeds.md)
- [Pitcher Pod](../items/PitcherPod.md)

**Wheat and eggs are not in that food tag.** The food is consumed in Survival. Give the adults room to approach each other; their breeding cooldown is **6,000 ticking ticks**, about **5 minutes** at 20 ticks per second. A newborn takes **24,000 ticking ticks**, about **20 minutes**, to mature unless accelerated; each feeding removes approximately 10% of the remaining time with whole-second rounding. Chicks follow adults. [Food tag][food] · [Food check and goals][chicken] · [Feeding and breeding][animal] · [Partner approach][breed-goal] · [Growth timer][age]

## Egg production and variants

A living **adult chicken without the chicken-jockey flag** lays an egg when its timer runs out. The timer starts and resets to **6,000–11,999 game ticks**, roughly **5 to just under 10 minutes** at 20 ticks per second. Only eligible ticking adults count it down; a baby does not lay eggs. The laying check does not require breeding or a recent feeding, so a single adult can supply eggs. [Laying timer][laying]

The chicken's stored variant determines the egg:

| Variant | Laid item | Bundled selection at spawning |
| --- | --- | --- |
| Temperate | [Egg](../items/Egg.md) | Fallback when a higher-priority variant does not match |
| Warm | [Brown Egg](../items/BrownEgg.md) | Warm farm-animal biome tag |
| Cold | [Blue Egg](../items/BlueEgg.md) | Cold farm-animal biome tag |

For example, plains support temperate chickens, savannas warm chickens, and taigas cold chickens. Variant selection is separate from the biome's permission to spawn chickens. A bred chick randomly inherits one parent's variant rather than selecting from its birth biome. [Laying loot][lay-loot] · [Temperate variant][temperate] · [Warm variant][warm-variant] · [Cold variant][cold-variant] · [Warm biomes][warm] · [Cold biomes][cold] · [Spawn and inheritance callbacks][inheritance]

Egg laying uses a gift loot table, separate from death loot. Its callback does not check `doMobLoot`; disabling that rule is not a way to stop the normal laying timer or its bundled gift drop. Keep the area ticking when waiting for production. [Laying callback][laying] · [Gift-loot dispatch][gift]

## Movement and chicken jockeys

Chickens slow their downward motion while airborne and are included in the **fall-damage-immune** entity tag. That protects them from falling damage, not every hazard. Ordinary chickens do not despawn simply because a player moves away. [Airborne movement][chicken] · [Fall immunity tag][fall-tag] · [Fall-damage calculation][fall] · [Persistence][animal]

A chicken used by a baby [Zombie](Zombie.md) can be marked as a **chicken jockey**. That flag prevents egg laying and permits distance despawning. Removing the rider does not itself clear the flag in the chicken's implementation, so do not rely on a rescued mount as an ordinary egg-layer. [Jockey creation][zombie] · [Jockey rules and saved flag][chicken]

## Death drops and experience

With mob loot enabled, an adult's base death drops are **0–2 [Feathers](../items/Feather.md)** and **one [Raw Chicken](../items/RawChicken.md)**. Looting adds a rounded random **0–1 per level** to each item count; at Looting III the maxima are **5 feathers** and **4 chicken**. The meat becomes [Cooked Chicken](../items/CookedChicken.md) when the chicken is on fire or the direct attacker's main-hand item meets the Fire Aspect smelts-loot condition. Death loot does not supply an extra egg. [Death loot][loot] · [Looting calculation][looting] · [Smelts-loot tag][fire-aspect]

Chicks do not drop normal death loot or experience. A qualifying player-attributed adult kill gives **1–3 base experience**, or **10** for a flagged chicken jockey. Breeding gives **1–7 experience** with mob loot enabled. [Baby restrictions][babies] · [Death XP conditions][death] · [Animal rewards][animal] · [Jockey XP override][chicken]

Related: [Egg](../items/Egg.md) · [Sheep](Sheep.md) · [Cow](Cow.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game breeding, egg-timing, variant, or drop test was run. Data packs can change foods, variants, biome spawns, and loot. The timer values assume the entity is ticking; they are not wall-clock promises for unloaded or paused areas.

[animal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Animal.java
[age]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[plains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/plains.json
[savanna]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/savanna.json
[forest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/forest.json
[taiga]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[spawn]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[ground]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[attributes]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[warm]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_warm_variant_farm_animals.json
[cold]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_cold_variant_farm_animals.json
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[babies]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[fire-aspect]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[chicken]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Chicken.java
[registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L431-L439
[food]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/chicken_food.json
[laying]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Chicken.java#L69-L122
[lay-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/chicken_lay.json
[temperate]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/chicken_variant/temperate.json
[warm-variant]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/chicken_variant/warm.json
[cold-variant]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/chicken_variant/cold.json
[inheritance]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Chicken.java#L154-L170
[gift]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1544-L1581
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[fall]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1724-L1731
[zombie]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L483-L506
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/chicken.json
