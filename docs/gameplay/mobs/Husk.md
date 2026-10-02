# Husk

A **Husk** is a desert Zombie variant that **does not ignite from sunlight** and can inflict **Hunger** with a successful empty-handed melee hit. Water converts it into an ordinary [Zombie](Zombie.md), which can subsequently become a [Drowned](Drowned.md). [Husk behavior][husk] · [Active entity and attribute registration][entities] [attributes]

<span id="obtaining"></span>

## Finding Husks

The **Desert** monster table lists Husks at weight 80 in configured groups of four, compared with weight 19 for ordinary Zombies. These are relative spawn-list weights, not an 80% chance that any dark location produces a Husk. Natural spawning requires non-Peaceful difficulty, valid ground, monster darkness checks, and **visible sky**. In the normal Overworld, block light must be 0, with additional sky/local-light checks. [Desert table][desert] · [Husk predicate][husk] · [Registered ground placement][placements] · [Darkness checks][monster] · [Overworld settings][overworld] · [Natural dispatch][natural]

[Dry Midlands](../biomes/DryMidlands.md) also has a Husk entry, but the normal [Primordial Caves](../dimensions/PrimordialCaves.md) dimension has **no skylight**. Its ordinary Husk spawning route fails the sky requirement despite that table entry. The source's sky test requires sky-light brightness 15. [Dry Midlands][dry_midlands] · [Dimension settings][primordial] · [Sky test][sky]

Spawner reasons have a separate exception to the sky requirement. A configured Husk spawner can therefore work underground when its other conditions pass; this does not make ordinary cave spawning possible. The bundled Trial Spawner data includes a Husk configuration. The ordinary listed [Husk Spawn Egg](../items/HuskSpawnEgg.md) provides a separate spawning route through MattMC's [inventory browser](../mechanics/InventoryBrowser.md), including in Survival. [Egg category](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2036) [Sky exception][husk] · [Spawner reason definitions][reasons] · [Husk trial configuration][trial-husk] · [Egg registration][items]

<span id="behavior"></span>

## Combat and sunlight

Husks inherit the Zombie family's melee targeting, baby variants, equipment, and conditional door-breaking behavior. See [Zombie](Zombie.md#fighting-and-protecting-villagers) for those shared rules. Their sunlight exception only disables that ignition trigger; it does not grant immunity to fire, lava, or weapons. [Shared behavior][zombie] · [Sun-sensitivity override][husk]

Hunger applies only after a **successful melee hit while the Husk's main hand is empty**. The duration is **140 game ticks multiplied by the integer part of local effective difficulty**, so it is not one fixed duration per world difficulty. The effect is Hunger I and increases food exhaustion rather than acting like Poison. Stay out of melee reach instead of waiting for sunrise to remove the threat. [Hit callback][husk] · [Local difficulty][difficulty] · [Hunger and healing](../mechanics/Hunger.md)

Husks also inherit the Zombie kill callback that can turn a Villager into a [Zombie Villager](ZombieVillager.md): the conversion roll succeeds 50% of the time on Normal and always on Hard, while Easy does not use that infection route. [Inherited infection][zombie]

## Water conversion

Keep the Husk's **eyes submerged in water** to start its water-conversion sequence. The inherited exposure threshold is **600 game ticks**, roughly **30 seconds**, followed by a **300-tick counter** that finishes when it falls below zero, roughly **15 more seconds** at normal tick speed. Leaving the water before the first threshold resets exposure. Leaving after the conversion countdown starts does **not** cancel it. The mob must remain alive, ticking, and have AI enabled for this timer. [Exposure and countdown][zombie]

The first result is an **ordinary Zombie**, not a Drowned. That new Zombie needs its own water exposure and conversion sequence to become Drowned. Equipment and its drop chances transfer through these conversions, along with baby state, custom name, and relevant persistence state; conversion is not a loot drop. [Husk conversion target][husk] · [Shared conversion setup][zombie] · [State and equipment transfer][conversion] · [Replacement dispatch][mob]

## Drops and persistence

With **doMobLoot** enabled, the Husk table contains:

- **0–2 [Rotten Flesh](../items/RottenFlesh.md)**, with a maximum of five at Looting III
- A player-credit-gated **2.5% combined chance** of one Iron Ingot, Carrot, or Potato, selected equally; the combined chance rises to **5.5% at Looting III**

The Potato uses the same burning/Fire Aspect smelting-loot condition described on [Zombie](Zombie.md#drops). This Husk table has no ordinary Zombie's baby-jockey music-disc pool. Equipment is a separate drop path; see the shared [Zombie equipment rules](Zombie.md#drops). [Husk loot][husk-loot] · [Smelting-loot tag][smelts] · [Monster loot gate][monster] · [Equipment handling][mob]

Ordinary hostile distance-despawn rules apply unless a persistence condition prevents them. Water exposure and an active conversion countdown are saved, but beginning water conversion does not itself grant persistence. Peaceful difficulty can remove the Husk. [Saved timers][zombie] · [Despawn logic][mob] · [Peaceful exclusion][entities]

## Related pages

- [Zombie](Zombie.md), [Drowned](Drowned.md), [Zombie Villager](ZombieVillager.md)
- [Rotten Flesh](../items/RottenFlesh.md), [Mobs](Mobs.md)

<span id="notes"></span>

## Sources and verification

Source-reviewed at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` on 2026-10-02 against active `src/main` code and bundled data. No in-game combat, conversion, curing, loot, or crafting test was run. Data packs, entity state, difficulty, gamerules, and later builds can change these results.

[husk]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Husk.java
[entities]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/EntityType.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[desert]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/desert.json
[placements]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[monster]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Monster.java
[overworld]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/dimension_type/overworld.json
[natural]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[dry_midlands]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[primordial]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[sky]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/BlockAndTintGetter.java
[reasons]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java
[trial-husk]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/trial_spawner/trial_chamber/melee/husk/normal.json
[items]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java
[zombie]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/monster/Zombie.java
[difficulty]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/DifficultyInstance.java
[conversion]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ConversionType.java
[mob]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/Mob.java
[husk-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/entities/husk.json
[smelts]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
