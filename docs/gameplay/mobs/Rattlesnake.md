# Rattlesnake

The **Rattlesnake** (`minecraft:rattlesnake`) is a small animal that warns nearby players, hunts Rabbits and Jerboas, and, as an adult, can attack a player's close approach without first being struck. It has **8 health points (4 hearts)** and a base attack attribute of **2 damage points**. Its current ordinary melee and its older venom-animation code follow different paths. [Behavior and attributes][snake]

## Availability in this snapshot

The entity, attributes, and [Rattlesnake Spawn Egg](../items/RattlesnakeSpawnEgg.md) are registered. No Rattlesnake entry was found in the 68 bundled biome spawn files, and its ground/light helper has no active spawn-placement registration. The existing sand-and-dirt ground tag does not by itself establish natural desert spawning. The verified setup route is a Creative egg or an administrator-provided mob. [Registration][registration] · [Attributes][attributes] · [Spawn placements][placements] · [Natural spawn selection][natural] · [Ground tag][ground]

Its registered body is **0.6 blocks wide and 0.3 blocks high**. These dimensions are not a tested enclosure specification. The behavior below applies when the mob is present. [Registration][registration]

## Warning, prey, and close encounters

Nearby Survival players can trigger curling and rattling. The warning goal also recognizes [Roadrunners](Roadrunner.md), and normally continues only while the warned creature remains within five blocks and the snake has no attack target. It does not require the player to hit the snake. [Warning goal][warning]

An **adult** snake can select a non-Creative, non-spectator player within its **two-block targeting range**, subject to the shared targeting checks. This close-range goal does not require a previous warning. Babies skip that particular player-selection goal, but still have retaliation and prey-targeting goals; they should not be treated as harmless. [Close-range target][close] · [Goal list][goals] · [Target selection][target]

Rabbits and [Jerboas](Jerboa.md) are explicit prey targets. Keep them in separate enclosures. Roadrunners occur in the warning predicate, but the close-range attack goal searches for **players**, so that predicate does not establish active Roadrunner hunting. Likewise, the current Roadrunner goal list does not contain a snake-hunting goal. [Snake goals][goals] · [Player target class][close] · [Roadrunner goals][bird]

## Damage, Poison, and remedies

Ordinary melee calls the current shared attack handler, using the snake's **2-point attack attribute before defenses and other damage handling**. The snake's older one-argument attack method is the only checked caller that starts its bite animation, and it does not match the active two-argument melee callback. Therefore **ordinary melee does not establish working venom delivery in this snapshot**. [Active melee caller][melee] · [Shared damage][damage] · [Older bite starter][old-bite]

The dormant animation routine contains timed Poison and special baby/Roadrunner damage rules. Those values should not be used as ordinary combat guarantees: finding them in the animation tick does not make the animation run. The snake remains capable of normal melee damage. [Animation damage][venom]

The Rattlesnake's **own Poison immunity is active**: its current effect-susceptibility callback rejects Poison. If a player has Poison from another source or altered server behavior, a [Honey Bottle](../items/HoneyBottle.md) removes Poison specifically, while a [Milk Bucket](../items/MilkBucket.md) clears current effects through the general removal path, including beneficial effects. No dedicated snake antidote item or recipe was found. [Immunity][immunity] · [Effect caller][effects] · [Honey and Milk consumption][consumables]

## Feeding and breeding

Use food directly on the snake. Its current food check accepts **any item stack carrying a food-value component**, including **Apples** and **Bread**; it is not restricted to meat. Holding food does not activate a food-following goal because no temptation goal is registered. [Food check and offspring][food] · [Food examples][apple] · [Bread registration][bread] · [Goals][goals]

An eligible adult enters love mode through the standard animal interaction. Two ready adults have a registered breeding goal and a Rattlesnake offspring factory. Feeding a baby speeds its growth. Successful breeding gives each parent a **6,000-tick cooldown**, about five minutes at 20 TPS. Combat can interrupt the higher-level breeding behavior. [Animal feeding and breeding][animal] · [Breeding caller][breed]

Feeding does not tame the snake, assign an owner, or provide a direct health-healing interaction. It also does not remove its close-range player targeting. There is no ordinary player riding interaction in the checked class. [Implementation][snake]

## Persistence and drops

Rattlesnakes inherit the animal rule that prevents ordinary distance despawning. No dedicated bundled Rattlesnake death-loot table or unique snake resource was found; the default entity-loot key resolves to the empty fallback when server data does not supply a table. Eligible adult deaths can still award **1–3 XP** with normal player-credit and mob-loot conditions. [Animal persistence and XP][animal] · [Default loot key][loot-key] · [Missing-table fallback][fallback] · [XP conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked active registrations, all 68 bundled biome files, spawn-helper callers, goals and target classes, food components, breeding dispatch, combat and immunity signatures, remedies, persistence, and loot resolution. No in-game spawning, warning, combat, Poison, feeding, breeding, or drop test was run. Data packs and custom entity data can change these results.

Related: [Rattlesnake Spawn Egg](../items/RattlesnakeSpawnEgg.md) · [Jerboa](Jerboa.md) · [Roadrunner](Roadrunner.md) · [Poison](../effects/Poison.md) · [Mobs](Mobs.md)

[snake]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1106-L1111
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L220
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[ground]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/rattlesnake_spawns.json
[warning]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L242-L283
[close]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L286-L307
[goals]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L48-L74
[target]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L75
[bird]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRoadrunner.java#L58-L69
[melee]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L144
[damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1320
[old-bite]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L89-L92
[venom]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L162-L173
[immunity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L94-L99
[effects]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L978-L1012
[consumables]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L64
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L198-L210
[apple]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1288
[bread]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1356
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L227
[breed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
