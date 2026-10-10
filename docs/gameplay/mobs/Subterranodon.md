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

A [Trilocaris Tail](../items/TrilocarisTail.md) or [Cooked Trilocaris Tail](../items/CookedTrilocarisTail.md) can feed a Subterranodon, but **not every feeding attempts taming**. Growth and love-mode feeding happen first; an untamed animal only reaches the **one-in-three** taming roll if that earlier interaction allows it and a tail remains in the interacting hand.

With ordinary tails, the source-reviewed server feeding behavior for an **untamed** Subterranodon in Survival is:

| Animal and held stack | Tails consumed by one interaction | Result |
| --- | --- | --- |
| Baby, at least one tail | 1 | Growth feeding; no taming roll |
| Adult ready to enter love mode, exactly one tail | 1 | Enters love mode; the stack empties before any taming roll |
| Adult ready to enter love mode, at least two tails in the same stack | 2 | Enters love mode, then makes one taming roll |
| Adult already in love mode or still on breeding cooldown, at least one tail | 1 | Makes one taming roll; skips the initial love-mode feeding |

An adult is ready to enter love mode when its breeding cooldown has ended and it is not already in love mode. In Creative, infinite materials keep the held stack from shrinking, so even a single held tail can reach an adult's taming roll; feeding a baby still does not roll.

If testing this source behavior, keep one animal apart from potential mates, wait until it is adult, and hold a stack of at least two raw tails or at least two cooked tails. If a click releases its lead instead, interact again to feed it. If the first interaction only used a lone tail to start love mode, replenish the hand: another tail can attempt taming while that adult remains in love. Each reached roll has its own one-in-three chance; three attempts do not guarantee success.

**Hearts alone do not prove ownership:** ordinary love-mode feeding also produces them. This port also predicts the interaction locally, which can differ from the server result; particles or an immediate command response do not confirm server ownership. The full interaction remains untested in a running world. Keep potential mates apart while testing because love mode can lead into the [incomplete egg-laying route](#breeding-and-drops).

A nearby player can instead receive ownership when one [hatches](../blocks/SubterranodonEgg.md#hatching); see the egg article for distance and sitting behavior. An already-tamed animal has no further taming roll, though tails can still feed growth or start love mode when eligible.

### Owner controls

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

Baseline article source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Feeding, taming, local feedback, and breeding cooldown re-reviewed at [snapshot 1b9b103398fd](https://github.com/HungLo2020/MattMC/commit/1b9b103398fd70d5b5152b93a1d0abc581fffc19) on 2026-10-10. No running-world test was performed.

- [Growth and love feeding before species interaction](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L173), [dinosaur routing](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java#L286-L330), and [distinct interaction results](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/InteractionResult.java#L11-L16)
- [Conditional taming roll](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java#L507-L523) and [accepted food and owner controls](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java#L620-L630)
- [Local interaction prediction](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L428-L431), [server dispatch](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1735), and [player-to-mob interaction](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L870)
- [Lead interactions before feeding](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Entity.java#L2108-L2159) and [mob interaction ordering](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1073)
- [Single-item use](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120), [empty-stack matching](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/ItemStack.java#L297-L335), and [infinite-material consumption exception](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/ItemStack.java#L1072-L1079)
- [Love-mode hearts](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Animal.java#L230-L242) and [taming hearts/smoke](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L86-L109)
- [Egg-breeding cooldown and love reset](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java#L61-L65) and [mating eligibility](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L202)

- [Active entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L1245-L1252)
- [Stats, behavior, interaction and flight TODO](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java)
- [Owner interaction routing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java#L286-L330)
- [Following condition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/ai/SubterranodonFollowOwnerGoal.java)
- [Breeding goal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/ai/AnimalBreedEggsGoal.java)
- [Laying goal](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/ai/AnimalLayEggGoal.java)
- [Spawn registration review](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
