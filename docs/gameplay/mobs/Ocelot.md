# Ocelot

An **Ocelot** is a wild jungle animal with **10 health points**, or **5 hearts**. Raw Cod or Raw Salmon can make it **trust players**, but trust does not turn it into a [Cat](Cat.md), assign an owner, or add sit/follow commands. Ocelots and Cats are separately registered mobs. [Trust behavior and attributes][ocelot] · [Entity registration][entities] · [Active attributes][attributes] · [Food tag][ocelot-food]

## Finding Ocelots

The bundled **Jungle** spawn list includes groups of **1–3 Ocelots**, and **Bamboo Jungle** includes groups of **1**. Sparse Jungle has no corresponding Ocelot entry in the checked defaults. Normal ground placement still needs valid support and clear space. The active Ocelot obstruction check requires **Grass Block or Leaves below**, a position **at or above the level's sea level**, and an unobstructed body space without liquid. [Jungle entry][jungle] · [Bamboo Jungle entry][bamboo] · [Biome helper wiring][biomes] · [Placement registration][spawn-rules] · [Ground placement][spawn-types] · [Ocelot obstruction check][ocelot]

There is an important MattMC spawn-setting detail: these entries are stored in the **monster spawning lists**, even though Ocelot's entity category is Creature. The normal spawn dispatcher can select them there, but that route depends on hostile-category spawning being enabled. In the checked server path, Peaceful difficulty or a disabled `spawnMonsters` rule prevents that category from being selected. The Ocelot-specific predicate has a two-in-three random pass and **does not impose a brightness threshold**. Do not infer that it needs darkness simply from the list name. [Spawn-list routing and category filters][natural] · [Server spawning flags][server] · [Chunk spawn dispatch][chunk-cache] · [Random predicate][ocelot]

These are spawn conditions, not a guaranteed encounter or herd size at every suitable spot. The [Ocelot Spawn Egg](../items/OcelotSpawnEgg.md) provides a Creative alternative.

## Building trust

1. Hold [Raw Cod](../items/RawCod.md) or [Raw Salmon](../items/RawSalmon.md) and wait for the Ocelot to approach
2. Remain still and avoid sudden turns while it comes close
3. Feed it while its food-attraction behavior is active and you are **less than three blocks away**
4. Repeat after smoke until a successful trust attempt shows hearts

Each eligible attempt consumes one fish in ordinary Survival and has a **one-in-three success chance**. The bundled tag accepts only those two raw fish, not Cooked Cod, Cooked Salmon, Tropical Fish, or a general fish category. Moving or turning abruptly can interrupt the untrusting Ocelot's temptation behavior and stop an attempted trust feed from taking that route. [Trust interaction and distance][ocelot] · [Accepted food][ocelot-food] · [Temptation and movement checks][tempt]

Trust removes the Ocelot's ordinary player-avoidance goal and its fear of movement during food temptation. It is a saved state for the animal, **not an ownership relationship with only the feeder**. It can still wander and hunt. There is no collar, sitting order, owner-follow teleport, riding control, or conversion into a Cat in this implementation. Use an enclosure or a [Lead](../items/Lead.md) when moving or keeping it. [Saved trust and goal changes][ocelot] · [General leash interaction][entity]

Unlike the owner's healing interaction with a Cat, the Ocelot food path does not directly restore health. After trust, accepted fish uses the ordinary animal breeding or baby-growth interaction. Hearts from a later breeding feed should not be confused with a new ownership or taming action. [Ocelot interaction][ocelot] · [Shared feeding][animal]

## Breeding and kittens

A practical sequence is to make **two adults trusting first**, then feed each another accepted raw fish when outside the breeding cooldown. Keep them close with space to approach. Trust itself is not an explicit mating requirement; it makes the feeding workflow predictable because an eligible untrusting Ocelot otherwise uses that interaction for a trust attempt first. Ocelots use the shared Animal mating checks and do not require full health. [Trust-first interaction order][ocelot] · [Love and mating rules][animal] · [Breeding approach][breed]

The baby is an **Ocelot** and starts **untrusting**. It does not inherit either parent's trust, and breeding never produces a Cat. Baby growth starts at **24,000 game ticks**, normally **20 minutes** while ticking; parents receive a **6,000-game-tick** cooldown, normally **5 minutes**. Food that reaches the shared baby-growth handler removes approximately **10% of the baby's remaining growth time**. If a feed takes the trust-attempt path instead, it does not also perform that growth action. [Default trust and offspring factory][ocelot] · [Birth, cooldown, and feeding][animal] · [Growth calculation][age]

## Other animals and Creepers

Ocelots actively target **Chickens and baby Turtles on land**. Trusting them does not disable these prey goals, so keep them away from vulnerable livestock if you want those animals alive. Their attack attribute is **3 damage points**, applied through the current close-range attack goal before the target's defenses. [Targets and attributes][ocelot] · [Attack dispatch][attack]

Creepers have a six-block avoidance goal for Ocelots as well as Cats. Trust is not required for that avoidance, but the Creeper still needs a usable escape path, and its swelling goal has higher priority. Do not treat an Ocelot as guaranteed explosion prevention. The Phantom's checked anti-swoop search is specifically for Cats, so do not assume an Ocelot supplies the same Phantom protection. [Creeper priorities][creeper] · [Avoidance conditions][avoid] · [Phantom Cat search][phantom]

Ocelots are in the bundled fall-damage-immunity tag. That does not protect them from drowning, fire, attacks, or every other environmental hazard. [Fall tag][fall-tag] · [Damage handling][living]

## Persistence and drops

A trusting Ocelot does not use ordinary distance despawning. An untrusting Ocelot becomes eligible for the standard distance checks after **2,400 ticks**, about **two minutes** of entity ticking, unless another persistence condition protects it. This is not a fixed two-minute disappearance timer. Trust is saved and restored. Naming through the ordinary Name Tag interaction also sets the generic persistence flag. [Ocelot persistence][ocelot] · [Distance checks and saved persistence][mob] · [Name Tag interaction][name]

The Ocelot's bundled death-loot table has **no item pools**, so it supplies no ordinary fur, Leather, or meat drop. Eligible adult player-attributed deaths use the inherited **1–3 XP** reward under the normal experience and mob-loot conditions; babies do not award that death experience. [Empty item loot][ocelot-loot] · [Animal experience][animal] · [Experience gates][living]

## Related pages

- [Cat](Cat.md): owned companion behavior
- [Ocelot Spawn Egg](../items/OcelotSpawnEgg.md)
- [Raw Cod](../items/RawCod.md)
- [Raw Salmon](../items/RawSalmon.md)
- [Chicken](Chicken.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Entity/attribute and spawn registration, all 68 bundled biome-definition JSON files, active natural-spawn category dispatch, trust and food ordering, breeding, prey/avoidance goals, loot, and persistence were inspected. No in-game natural-spawn, trust, breeding, livestock, or Creeper-protection test was run. Server rules and data packs can change the described defaults.

[ocelot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Ocelot.java
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[ocelot-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/ocelot_food.json
[jungle]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/jungle.json
[bamboo]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/bamboo_jungle.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/biome/OverworldBiomes.java
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[spawn-types]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[server]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java
[chunk-cache]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerChunkCache.java
[tempt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/TemptGoal.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[breed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
[attack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/OcelotAttackGoal.java
[creeper]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/monster/Creeper.java
[avoid]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/AvoidEntityGoal.java
[phantom]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/monster/Phantom.java
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
[name]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/NameTagItem.java
[ocelot-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/ocelot.json
