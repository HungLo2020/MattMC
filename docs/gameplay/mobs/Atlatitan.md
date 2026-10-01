# Atlatitan

Atlatitan is a huge dinosaur that retaliates with kicks and tail sweeps. Feeding an adult [Serene Salad](../items/SereneSalad.md) enables temporary mounting through an active player interaction. This does not tame it, and **mounted stomp controls and breeding remain incomplete** in this MattMC snapshot.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:atlatitan` |
| Health | 400 points (200 hearts) |
| Base attack attribute | 8 points (4 hearts), before modifiers |
| Kick damage | 6.4 points, before modifiers |
| Tail-sweep damage | 8 points, before modifiers |
| Normal food | Pine Nuts |
| Registered main-body size | 5 blocks wide × 8 blocks tall |

It heals 2 health points every 100 ticking game ticks while injured. The registered body dimensions are not a measurement of the full neck-and-tail model.

## Obtaining

Use an [Atlatitan Spawn Egg](../items/AtlatitanSpawnEgg.md) in Creative, or summon `minecraft:atlatitan` with command permission.

**Natural spawning is not established.** No Atlatitan entry was found in the reviewed biome spawn data or spawn-placement registrations, including the [Primordial Caves](../dimensions/PrimordialCaves.md) biomes. Shared dinosaur ground predicates do not by themselves create a natural population. No registered Atlatitan species-egg item or block was found in the checked registries.

## Feeding and temporary riding

[Pine Nuts](../items/PineNuts.md) attract Atlatitan and are accepted through the ordinary animal food interaction. They can accelerate baby growth or put an eligible adult into love mode, but breeding is unsafe to rely on; see below. Nuts have a chance-based harvesting source from Pewen Branches and Pewen Pines, with natural Pewen availability still unverified.

For the implemented adult mounting path:

1. Interact with an adult while holding **Serene Salad**. The active interaction sets its mounting timer to **12,000 game ticks**, equivalent to 10 minutes at 20 ticks per second. One salad is consumed outside Creative.
2. Switch to an **empty hand** and interact again while the timer is positive and a passenger slot is available.
3. Use ordinary movement inputs to try steering. The current riding path calls its forward/backward, strafe, turning, and speed handlers. Backward and sideways input are reduced, and speed varies with its walking cycle.

These steps use the ordinary player-interaction method. A separate mixture-feeding hook contains a similar timer change but has no active caller; it is not the evidence for this mounting route.

There is no owner or saddle requirement in this mounting path. Feeding salad does **not** assign ownership, so another player can also mount during the window. Its inherited owner-command checks are disabled; salad does not unlock sit/follow commands.

More salad resets the timer to 12,000 ticks rather than adding time. Expiry prevents a new normal mounting attempt; the timer code does not include a timed ejection of an existing rider. The timer's save and load handling is disabled, so do not expect the permission to survive an entity unload and reload.

Use a roomy test area before relying on riding. Steering, collisions, multiplayer behavior, and safe dismounting have not been tested for this guide.

## Combat and leaves

Atlatitan's goals include retaliation, not indiscriminate player hunting. Its kick and tail attacks damage eligible nearby living creatures of other species, rather than only the creature that provoked it. Keep other animals away during a fight. Tail-sweep damage checks repeat during the animation, so the listed value is not a total attack-damage cap.

Its nibbling goal selects blocks in the **leaves tag**, subject to height and path checks. During a nibble, the selected block is destroyed without drops and its original state immediately restored. This is not a demonstrated leaf or Pine Nut harvesting method.

**No working mounted stomp key is established.** The client key handling is commented out and no active caller of the mount's key-packet handler was found. The underlying stomp code can destroy eligible nearby blocks when a player is aboard, but an unconnected handler is not a usable control. This page does not assign an upstream key or promise a working stomp ability.

## Breeding and drops

**Do not plan an Atlatitan breeding farm around the current implementation.** Its mating goal can mark a parent as carrying an egg, but its egg-state method returns **null**. The laying goal later passes that value into block placement without a null guard. This is an incomplete, potentially failing path, not a normal wait for an egg.

No dedicated Atlatitan death-loot table was found in the bundled entity loot directory, so specific meat, hide, or bone drops are not established.

The active base death-experience calculation comes from the ordinary animal implementation: **1–3 XP**, before modifiers, when the normal adult, player-credit, and mob-loot conditions allow it. The Atlatitan class also contains an old no-argument method returning 30, but the current death path does not call that signature; it is not evidence of a 30-XP reward.

## Related pages

- [Serene Salad](../items/SereneSalad.md)
- [Pine Nuts](../items/PineNuts.md)
- [Relicheirus](Relicheirus.md)
- [Grottoceratops](Grottoceratops.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, riding, timer persistence, combat, breeding, or drop test was run. Data packs can change the reviewed data-driven definitions.

- [Active entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L259-L265), [attribute registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L115), and [Creative spawn egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1973)
- [Stats, attacks, food, mounting timer, active interaction, controls, and null egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/AtlatitanEntity.java)
- [Active mounted-movement dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2827-L2831) and [riding-handler calls](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2422-L2442)
- [Retaliation attacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AtlatitanMeleeGoal.java), [area damage and dormant block destruction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/SauropodBaseEntity.java), and [leaf nibbling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AtlatitanNibbleTreesGoal.java)
- [Shared dinosaur interactions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java), [ordinary feeding and base XP](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L157), [mating](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java), and [unguarded egg placement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Current XP method signature](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L591) and [death-XP call and conditions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487)
- [Item registry](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java), [block registry](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java), [spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java), [biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome), and [entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
