# Tremorsaurus

Tremorsaurus is a large predator with biting, prey-shaking, and roaring behavior. An untamed adult can target nearby players. A tamed adult accepts its owner as a rider, but **normal golden-apple taming, species breeding, and rider combat keys are not fully connected** in this MattMC snapshot.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:tremorsaurus` |
| Health | 150 points (75 hearts) |
| Base bite damage | 14 points (7 hearts), before modifiers |
| Armor | 8 points |
| Food accepted for breeding/growth | Dinosaur Nugget, only while tame |
| Registered adult size | 3.5 blocks wide × 4.5 blocks tall |

It heals 2 health points every 100 ticking game ticks while injured. Its chase behavior raises base movement speed from 0.2 to 0.35 while running; these attribute values are not blocks per second.

## Obtaining and ownership

Use a [Tremorsaurus Spawn Egg](../items/TremorsaurusSpawnEgg.md) in Creative, or summon `minecraft:tremorsaurus` with command permission. **Natural spawning is not established:** no entry was found in the reviewed biome spawn data or spawn-placement registrations, including the [Primordial Caves](../dimensions/PrimordialCaves.md) biomes. The shared dinosaur ground checks do not by themselves put this animal into a biome.

The registered [Tremorsaurus Egg](../items/TremorsaurusEgg.md) provides a separate ownership path for testing. Its item is explicitly listed in Creative and can also be obtained by command as `minecraft:tremorsaurus_egg`. See [placed dinosaur eggs](../blocks/DinosaurEggs.md) for hatching and safe collection. When its hatching conditions succeed, the baby is tamed to the **nearest non-spectator player within 10 blocks** and ordered to sit. Being the egg's placer is not the ownership check, so keep other players farther away if testing this route. No Survival source for this species egg was established.

**Golden Apples are not a verified normal taming interaction.** A separate feeding method contains a golden-apple taming routine, but neither the ordinary dinosaur interaction nor the registered Golden Apple calls it, and no active caller was found elsewhere in the reviewed Java source. Do not approach a wild adult expecting apples to make it safe.

## Wild behavior and combat

- The nearby-player targeting goal applies to **untamed adults**, with a configured search distance of 8 blocks. This is an acquisition check, not a safe boundary for escaping an existing fight.
- It retaliates when hurt and hunts Grottoceratops and [Subterranodon](Subterranodon.md) while untamed. The current goals do not include Relicheirus as prey.
- Its bite requires line of sight and close range. It can also grab and shake prey, with repeated damage during that animation; the base bite value is not a total damage limit.
- Adult roars disrupt eligible nearby mobs' targeting and movement. The bundled roar-resistance tag contains Tremorsaurus itself. A tame animal's roar also applies Weakness I for 200 ticks to non-allied, non-resistant living targets, refreshed during the effect's active checks.

Holding a [Dinosaur Nugget](../items/DinosaurNugget.md) is an attraction cue, but combat has higher goal priority. It is not a guarantee of safe handling.

## Owner commands and riding

Once you own a Tremorsaurus, use an **empty hand** to avoid food and special-item interactions:

- Sneak-interact to cycle **wander (0) → sit (1) → follow (2)**. Following requires command 2 and can be suspended during combat.
- Interact without sneaking to mount an adult. The owner-mount path does not require a saddle; babies cannot be mounted this way.
- Ordinary forward/backward and strafe input have implemented riding handlers. Backward and sideways inputs are reduced.

**Rider attacks and roar controls remain unverified.** Their handlers exist, but the entity's client key-handling section has been removed and no active caller of its key-packet handler was found. This page does not assign upstream keys or promise working mounted combat. Test movement and dismounts in a safe enclosure before relying on the animal for transport. Screen shake is explicitly disabled in the checked implementation.

## Breeding and drops

Only a **tamed** Tremorsaurus accepts Dinosaur Nuggets through the normal food interaction. Feeding an eligible adult enters love mode; feeding a tamed baby accelerates growth. Nugget supply itself has no established Survival source in this review.

**Breeding does not currently produce Tremorsaurus Eggs.** Mating uses the egg-carrying goal, but the species' egg-state method returns an ordinary **Turtle Egg**, which the laying goal places. Do not confuse this placeholder with the separately registered Tremorsaurus Egg and its ownership behavior.

No dedicated Tremorsaurus death-loot table was found in the bundled entity loot directory. Dinosaur Chops or other specific death drops are not established by this review.

## Related pages

- [Dinosaur Nugget](../items/DinosaurNugget.md)
- [Relicheirus](Relicheirus.md)
- [Subterranodon](Subterranodon.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, egg hatching, taming, combat, riding, or breeding test was run. Data packs can change the reviewed spawn, tag, recipe, and loot data.

- [Active registration and dimensions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1425-L1431) and [attribute registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L243)
- [Stats, goals, attacks, roar, healing, owner actions, and integration limits](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java)
- [Nearby-player targeting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/MobTargetClosePlayers.java), [untamed prey targeting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/MobTargetUntamedGoal.java), and [melee attacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/TremorsaurusMeleeGoal.java)
- [Shared owner interaction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java#L286-L330) and [owner following](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalFollowOwnerGoal.java)
- [Species egg registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4815-L4819), [egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/TremorsaurusEggBlock.java), and [hatching and owner selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Ordinary animal feeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157), [mating](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java), and [egg laying](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Roar-resistance tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/resists_tremorsaurus_roar.json)
- [Item and spawn-egg registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java) and [Creative listings](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Spawn-placement registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java), [biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome), and [entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
