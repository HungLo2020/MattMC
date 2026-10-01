# Subterranodon

Subterranodon is a tameable flying dinosaur integrated from Alex's Caves. It can follow its owner and accepts an adult rider, but **flight-key integration and egg laying are incomplete** in this snapshot. Do not plan essential transport or a breeding farm around untested upstream controls.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:subterranodon` |
| Health | 20 points (10 hearts) |
| Base attack damage | 2 points (one heart), before modifiers |
| Food | Raw or Cooked Trilocaris Tail |

Its goals include retaliation when hurt, following an owner, joining packs, flight movement, and attraction to either tail food. It is not assigned a goal to hunt nearby players indiscriminately. It heals 2 health points every 100 ticking game ticks while below maximum health.

## Obtaining

Use a [Subterranodon Spawn Egg](../items/SubterranodonSpawnEgg.md) in Creative or summon `minecraft:subterranodon` with command permission. A [placed Subterranodon Egg](../blocks/SubterranodonEgg.md) also has a defined hatching path.

Natural spawning is **not established**: a standalone spawn test checks suitable dry ground, but no Subterranodon entry was found in the checked biome spawn data or spawn-placement registrations. A spawn predicate alone does not place the mob in a biome.

## Taming and owner interactions

The current taming interaction consumes a [Trilocaris Tail](../items/TrilocarisTail.md) or [Cooked Trilocaris Tail](../items/CookedTrilocarisTail.md) and uses a **one-in-three** random success check. A nearby player can also receive ownership when one hatches; see the egg article for distance and sitting behavior.

After taming, use an empty hand to avoid food and special-item interactions:

- Sneak-interact as the owner to cycle commands: **wander (0) → sit (1) → follow (2)**.
- Interact without sneaking to mount an adult. The owner-mount condition rejects babies; no saddle condition is present in that path.
- Follow behavior requires command 2 and is suspended by sitting and some combat conditions.

## Riding limitations

The code reads ordinary forward/backward and strafe input and includes ascent/descent handlers. However, the client flight-key section is still a TODO, and no active caller of the mount's key-packet handler was found. This wiki therefore does not assign an upstream ascent/descent key or claim working altitude control.

Use a safe test enclosure before trying riding. Mounting adjusts the creature's position to fit the rider, and the rider is positioned beneath its body. Precise movement, collisions, and dismount safety remain untested here.

## Breeding and drops

**Egg laying is not a working acquisition route established by this review.** The active breeding goal can mark the animal as carrying an egg, but Subterranodon's egg-state method returns null. The laying goal later passes that result to block placement without a null guard. That is an incomplete, potentially failing path, not a normal wait for an egg.

A Creative-placed egg uses a separate hatching implementation. No dedicated Subterranodon death-loot table was found in the bundled entity loot directory; no specific death drop is promised here.

## Related pages

- [Subterranodon Egg](../blocks/SubterranodonEgg.md)
- [Trilocaris](Trilocaris.md)
- [Trilocaris Tail](../items/TrilocarisTail.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No running-world test was performed.

- [Active entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L1245-L1252)
- [Stats, behavior, interaction and flight TODO](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java)
- [Owner interaction routing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java#L286-L330)
- [Following condition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/ai/SubterranodonFollowOwnerGoal.java)
- [Breeding goal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java)
- [Laying goal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Spawn registration review](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
