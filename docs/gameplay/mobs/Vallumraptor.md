# Vallumraptor

Vallumraptor is a small, fast predator that can hunt in packs, open doors, and steal eligible items from containers. A baby hatched from its named egg can become an owned follower. **The chest-nugget-and-salad taming sequence is not connected in the bundled implementation**, and breeding currently produces a Dragon Egg placeholder.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:vallumraptor` |
| Registered base health | 28 points (14 hearts) |
| Elder health | 32 points (16 hearts) |
| Elder armor | 5 points |
| Base attack damage | 3 points (1.5 hearts), before modifiers |
| Breeding and growth food | Dinosaur Nugget, only while tame |
| Registered size | 0.8 blocks wide × 1.5 blocks tall |

Elder is a distinct state selected during spawn setup, not a demonstrated aging milestone. Vallumraptor normally heals 2 points every 100 ticking game ticks while injured. A tamed animal can flee during combat below 45% health; its temporary hiding state accelerates that healing check to every 15 ticks.

## Obtaining and ownership

Both the [Vallumraptor Spawn Egg](../items/VallumraptorSpawnEgg.md) and placeable [Vallumraptor Egg](../items/VallumraptorEgg.md) (`minecraft:vallumraptor_egg`) are explicitly listed in Creative. With command permission, summon `minecraft:vallumraptor` or give the named egg item.

**Natural spawning is not established.** No Vallumraptor entry was found in the reviewed biome spawn data or spawn-placement registrations, including the [Primordial Caves](../dimensions/PrimordialCaves.md) biomes. Shared dinosaur ground checks are not proof of biome placement, and no natural generation source for its species egg was found.

When a placed Vallumraptor Egg hatches, the shared hatch path can tame each baby to the **nearest non-spectator player within 10 blocks**, ordering it to sit. The owner is selected by proximity, not who placed the egg. Without an eligible nearby player, that path does not assign ownership. The [placed dinosaur egg guide](../blocks/DinosaurEggs.md) explains the shared solid-ground, random-tick, and trampling checks; Vallumraptor's species egg additionally supports several eggs in one block.

## Why chest-and-salad taming is unreliable

The source contains a sequence for relaxing after eating a stolen Dinosaur Nugget, followed by taming through [Serene Salad](../items/SereneSalad.md). Two integration gaps prevent treating that as a normal player procedure:

1. The bundled theft and meat tags do **not** include Dinosaur Nuggets. The current item-selection test therefore does not choose them from chests or dropped items, despite a separate nugget-specific relaxation branch.
2. The salad-taming method has no active caller in the reviewed Java source. Ordinary interaction and the registered salad item do not invoke it.

Holding a [Dinosaur Nugget](../items/DinosaurNugget.md) still attracts Vallumraptor through a separate goal. That is not taming. Nuggets are available in Creative, but their Survival supply is not established by the reviewed recipes and loot.

## Protecting livestock and supplies

Untamed adults have a nearby-player target goal with a configured search distance of **12 blocks**. This is a target-acquisition check, not an escape boundary. They also retaliate, and can alert other Vallumraptors when attacked.

The bundled prey tag includes pigs, sheep, cows, horses, donkeys, mules, goats, frogs, mooshrooms, and sniffers. A separate untamed pack-target goal selects [Grottoceratops](Grottoceratops.md) when the pack has **at least five members**. Packs can form around an elder leader, with the joining goal capped at eight members. Attacks include bites, slashes, and lunges.

An untamed, empty-handed Vallumraptor can take **one eligible item at a time** from an accessible container. Selection includes ordinary, Blue, and Brown Eggs, raw/cooked Chicken, and food items in the meat tag, including Rotten Flesh. It can also collect eligible dropped items, including while tame if not ordered to sit. Consuming an eligible held item restores 5 health points.

Its door-opening goal handles doors the game classifies as **hand-openable**, so a closed door alone is not reliable protection for stored food. The tame version attempts to close a door after passing; the wild version does not. Container theft is disabled for tame animals when the goal starts.

## Owner commands and breeding

Use an **empty hand** as the owner to cycle **wander (0) → sit (1) → follow (2)**. Sneaking is not required for this species. Following requires command 2 and can be suspended during combat. The animal also has goals to defend its owner and attack its owner's targets. There is no implemented owner-mount interaction.

Only tame Vallumraptors accept Dinosaur Nuggets as normal food: eligible adults enter love mode, and babies grow faster. **Mating does not lay a Vallumraptor Egg.** Its active egg-state method returns an ordinary **Dragon Egg**, which the shared laying goal places. Do not build a species-breeding farm around that placeholder.

No dedicated Vallumraptor death-loot table was found in the bundled entity loot directory. This review establishes no specific meat or other species death drop.

## Related pages

- [Dinosaur Nugget](../items/DinosaurNugget.md)
- [Grottoceratops](Grottoceratops.md)
- [Tremorsaurus](Tremorsaurus.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, ownership, theft, doors, combat, or breeding test was run. Tags and other data-pack overrides can change the described selections.

- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1252-L1258), [active attributes](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L251), and [Creative species eggs](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L954-L955)
- [Stats, goals, elder state, theft eligibility, feeding, commands, and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java)
- [Prey tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/vallumraptor_targets.json), [theft tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/vallumraptor_steals.json), [egg tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/eggs.json), and [meat tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/meat.json)
- [Container stealing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalLootChestsGoal.java), [dropped-item selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/MobTargetItemGoal.java), and [door behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/VallumraptorOpenDoorGoal.java)
- [Door eligibility](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/DoorInteractGoal.java) and [hand-openable definition](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L271-L277)
- [Player targeting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/MobTargetClosePlayers.java), [pack targeting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalPackTargetGoal.java), [pack joining](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalJoinPackGoal.java), and [melee attacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/VallumraptorMeleeGoal.java)
- [Egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/VallumraptorEggBlock.java), [multiple-egg behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/MultipleDinosaurEggsBlock.java), and [hatch ownership](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Shared interaction routing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java), [item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java), and [egg laying](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java), [biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome), and [entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
