# Relicheirus

Relicheirus is a large dinosaur that retaliates when hurt and hunts [Trilocaris](Trilocaris.md). [Pine Nuts](../items/PineNuts.md) attract it, but it has no established player-taming or riding interaction. Its breeding and soup-assisted tree work are incomplete in this MattMC snapshot.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:relicheirus` |
| Health | 120 points (60 hearts) |
| Base attack damage | 12 points (6 hearts), before modifiers |
| Food | Pine Nuts |
| Registered adult size | 3.5 blocks wide × 3.5 blocks tall |

While injured, it heals 2 health points every 100 ticking game ticks. Its normal attacks slash and knock targets back; its Trilocaris attack instead lifts the prey and deals damage during an eating animation. Keep Trilocaris out of its enclosure.

## Obtaining

For Creative testing, use the [Relicheirus Spawn Egg](../items/RelicheirusSpawnEgg.md), or summon `minecraft:relicheirus` with command permission. The separately registered [Relicheirus Egg](../items/RelicheirusEgg.md) is explicitly listed in Creative and has a hatching path that creates a baby, but breeding does not currently produce that egg. See [placed dinosaur eggs](../blocks/DinosaurEggs.md) for conditions and safe collection.

**Natural spawning is not established.** No Relicheirus entry was found in the reviewed biome spawn data or spawn-placement registrations, including the [Primordial Caves](../dimensions/PrimordialCaves.md) biomes. A shared dinosaur spawn predicate checks dry ground in the dirt tag, but that standalone check is not proof of a natural population. No natural source for the species egg was found in the reviewed world-generation data either.

## Feeding and breeding

Hold Pine Nuts to attract a nearby Relicheirus. Feeding an eligible adult uses the ordinary animal love-mode interaction; feeding a baby accelerates growth. This is food handling, not taming. Pine Nuts can drop from Pewen Branches and Pewen Pines under their harvesting conditions; see [Pine Nuts](../items/PineNuts.md) for the source and its natural-availability limitation.

**Do not build a Relicheirus breeding farm around the current egg-laying code.** Mating marks a parent as carrying an egg, but its egg-state method returns an ordinary **Turtle Egg**, not a Relicheirus Egg. The laying goal places that returned block. The separately registered species egg therefore does not make breeding a working way to obtain more Relicheirus.

Hatching also does not tame this species, and its inherited owner-command and owner-mount checks remain disabled.

## Trees and Primordial Soup

Relicheirus has an active leaf-nibbling goal. Despite its Pewen-related name, the current goal selects ordinary blocks in the **leaves tag**, subject to height and path checks. The nibble animation destroys a selected block without drops and immediately restores its state; it is not a demonstrated Pine Nut harvesting method.

**Do not spend [Primordial Soup](../items/PrimordialSoup.md) expecting a tree-cutting helper.** The ordinary interaction consumes soup, but does not call the separate method that enables tree pushing. No active caller of that method was found. Even if enabled by code, the simplified pushing goal breaks one selected log block; it does not topple an entire tree into a falling-tree entity.

## Drops

No dedicated Relicheirus death-loot table was found in the bundled entity loot directory. This review does not establish a Dinosaur Chop, hide, or other specific death drop.

## Related pages

- [Pine Nuts](../items/PineNuts.md)
- [Pewen family](../blocks/Pewen.md)
- [Trilocaris](Trilocaris.md)
- [Tremorsaurus](Tremorsaurus.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, feeding, combat, hatching, breeding, or tree-interaction test was run. These findings describe the bundled implementation; data packs can change data-driven behavior.

- [Active registration and dimensions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1133-L1139) and [attribute registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L224)
- [Stats, goals, soup handling, healing, food, and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/RelicheirusEntity.java)
- [Slash attacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/RelicheirusMeleeGoal.java), [leaf nibbling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/RelicheirusNibblePewensGoal.java), and [simplified tree pushing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/RelicheirusPushTreesGoal.java)
- [Shared interactions, spawn predicate, and disabled owner actions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java) and [ordinary animal feeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157)
- [Mating](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java) and [egg laying](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Species egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/RelicheirusEggBlock.java) and [shared hatching](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Item and spawn-egg registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java) and [Creative listings](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Spawn-placement registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java), [biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome), and [entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
