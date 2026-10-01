# Grottoceratops

Grottoceratops is a sturdy dinosaur that retaliates when attacked and can draw nearby members of its species into the fight. [Pine Nuts](../items/PineNuts.md) attract and feed it. Keep its enclosure away from decorative plants: its active grazing goal can destroy flowers without drops.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:grottoceratops` |
| Health | 50 points (25 hearts) |
| Armor | 8 points |
| Base ram damage | 10 points (5 hearts), before modifiers |
| Base tail-swing damage | 15 points (7.5 hearts), before modifiers |
| Food | Pine Nuts |
| Registered adult size | 2.5 blocks wide × 2 blocks tall |

While injured, it heals 2 health points every 100 ticking game ticks. Its retaliation attacks use either a ram or a tail swing, with the tail applying a 1.5× damage multiplier. Both require close range and line of sight and apply knockback. Its own target goals do not include indiscriminate player hunting.

## Obtaining

The [Grottoceratops Spawn Egg](../items/GrottoceratopsSpawnEgg.md) and placeable [Grottoceratops Egg](../items/GrottoceratopsEgg.md) (`minecraft:grottoceratops_egg`) are both explicitly listed in Creative. With command permission, summon `minecraft:grottoceratops` or give the named egg item.

**Natural spawning is not established.** No Grottoceratops entry was found in the reviewed biome spawn data or spawn-placement registrations, including the [Primordial Caves](../dimensions/PrimordialCaves.md) biomes. A shared dinosaur spawn predicate checks dry ground in the dirt tag, but a predicate alone does not make the mob spawn there. No natural generation source for its species egg was found in the reviewed data.

Its named placed egg has a source-defined path to hatch a baby using the same base hatching implementation as the single eggs in the [placed dinosaur egg guide](../blocks/DinosaurEggs.md). **Grottoceratops does not enable hatching-based taming.** A nearby player is not assigned ownership.

## Food and grazing

Hold Pine Nuts to attract a nearby Grottoceratops. Feeding an eligible adult enters love mode; feeding a baby accelerates growth. The food check names Pine Nuts directly rather than using a general food tag. Pine Nuts can drop from Pewen Branches and Pewen Pines under their harvesting conditions; see the [Pine Nuts guide](../items/PineNuts.md) for the source and its natural-availability limitation.

The grazing goal uses the vanilla **flowers** and **small flowers** block tags. This includes more than simple ground flowers: the bundled flower tag also contains plants such as Flowering Azalea, Cherry Leaves, Pink Petals, and Pitcher Plants. A reachable selected block is destroyed **without drops** during the ground-chewing animation.

Do not use a valuable garden as its pen. The checked grazing goal has no mob-griefing gamerule check, so that setting is not a protection established by this review.

## Taming, breeding, and other animals

There is no implemented player-taming interaction for Grottoceratops, and its inherited owner-command and owner-mount checks remain disabled. Feeding it Pine Nuts does not make it a rideable pet.

**Breeding does not produce Grottoceratops Eggs in this snapshot.** Mating uses the egg-carrying goal, but its egg-state method returns an ordinary **Turtle Egg**. The shared laying goal places that returned block. Do not confuse this placeholder with the separately registered species egg that can hatch a Grottoceratops.

Keep it away from [Tremorsaurus](Tremorsaurus.md), whose untamed hunting goals include Grottoceratops. Untamed [Vallumraptor](Vallumraptor.md) packs also have a Grottoceratops-targeting goal once their pack reaches five members.

## Drops

No dedicated Grottoceratops death-loot table was found in the bundled entity loot directory. This review does not establish Dinosaur Chops or another specific species death drop.

## Related pages

- [Pine Nuts](../items/PineNuts.md)
- [Relicheirus](Relicheirus.md)
- [Vallumraptor](Vallumraptor.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, feeding, grazing, combat, hatching, or breeding test was run. Data packs can change the reviewed tag, spawn, and loot definitions.

- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L721-L727), [active attributes](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L179), and [Creative species eggs](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L954-L955)
- [Stats, goals, healing, food, and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/GrottoceratopsEntity.java) and [ram/tail attacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/GrottoceratopsMeleeGoal.java)
- [Plant-eating goal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/GrottoceratopsEatPlantsGoal.java), [flower tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/flowers.json), and [small-flower tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/small_flowers.json)
- [Species egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/GrottoceratopsEggBlock.java), [shared hatch handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java), and [disabled owner interactions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java)
- [Ordinary animal feeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157), [mating](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java), and [egg laying](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Tremorsaurus hunting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java#L90-L112), [Vallumraptor hunting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java#L153-L158), and [pack-size requirement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalPackTargetGoal.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java), [spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java), [biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome), and [entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
