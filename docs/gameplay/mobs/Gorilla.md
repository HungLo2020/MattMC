# Gorilla

The **Gorilla** (`minecraft:gorilla`) is a social animal that can retaliate and follow a silverback. Its food-dependent features are incomplete in the bundled data: **ordinary taming, feeding, breeding, and leaf foraging have no supplied tag ingredients or targets**. Plan an enclosure around its current behavior rather than a banana-feeding routine. [Implementation][gorilla] · [Tag definitions][tags]

## Availability in this snapshot

Gorillas and their attributes are registered, and the [Gorilla Spawn Egg](../items/GorillaSpawnEgg.md) is available in Creative. No Gorilla entry was found in the 68 bundled biome spawn files or the active spawn-placement registrations. The class's ground/light helper has no registered caller, so it does not establish a natural jungle-spawning route. Use an egg or an administrator-provided mob to access the behavior described here. [Registration][registration] · [Attributes][attributes] · [Spawn placements][placements] · [Natural spawn selection][natural]

## Silverbacks and group behavior

| Adult form | Maximum health | Base attack attribute |
| --- | ---: | ---: |
| Ordinary Gorilla | 30 points / 15 hearts | 7 damage points |
| Silverback | 50 points / 25 hearts | 10 damage points |

The silverback values are applied when an adult with that variant starts ticking. Damage values are before defenses and other damage handling. A normal individual egg spawn has a **50% silverback chance**; the [egg guide](../items/GorillaSpawnEgg.md#using-on-a-gorilla) explains the different baby route. [Base attributes][base-stats] · [Silverback update][silverback] · [Spawn initialization][spawn]

Non-silverbacks can form a following chain behind a silverback. Babies can approach and climb onto an available adult Gorilla, then dismount after growing up. This is animal group behavior: the player interaction does not offer a mount, and the Gorilla has no controlling passenger. [Caravan goal][caravan] · [Baby riding goal][baby-ride] · [Growing passengers and control][gorilla]

## Approaching and combat

An untamed silverback can notice a player staring directly at it within its nearby search area, run up, and pound its chest. This particular approach ends in a display; it does not set an attack target or deal damage by itself. Looking requires line of sight. [Staring response][charge]

Hitting a Gorilla can trigger retaliation and alert nearby Gorillas. Give the group space even when only one was struck. Ordinary melee uses the shared attack handler and the attributes above. The older custom attack-animation method does not match the current melee callback, so its timed strike and extra knockback should not be treated as established ordinary melee behavior. [Goals and old attack method][goals] · [Retaliation alerts][retaliation] · [Active melee caller][melee] · [Shared damage][damage]

## Food, taming, and interaction limits

The implementation separates several food and block tags, but the corresponding bundled resources are absent:

- `bananas`, `gorilla_tameables`, and `gorilla_foodstuffs` govern taming food, attraction, and dropped-food pickup
- `gorilla_breedables` is checked only after a Gorilla is tame
- `gorilla_breakables` and `drops_bananas` govern leaf foraging and its possible extra reward

As supplied, no item qualifies for these Gorilla food checks and no block qualifies for its foraging goal. **Apples are not an established taming food**, and ordinary Leaves do not become edible merely because they are leaves. The unused extra-reward branch creates an Apple; it is not evidence of an obtainable Banana item. Server data packs can change these tags. [Food checks and pickup][gorilla] · [Tag namespace][tag-namespace] · [Tag membership][membership] · [Foraging goal][forage]

If a Gorilla already has a tame owner through custom setup, its owner can use an empty hand to toggle forced sitting. Owner-defense goals are registered, but there is no owner-follow goal in its goal list. The implemented taming and food-healing routines do not provide a working bundled food route by themselves. [Interaction][interaction] · [Goals][goals]

Gorillas inherit the animal rule that prevents ordinary distance despawning. Their silverback and sitting state are saved; ownership, when present, uses the shared tame-animal save data. They can still wander or be harmed, so distance persistence is not a substitute for an enclosure. [Animal persistence][animal] · [Gorilla state][save] · [Owner state][owner]

## Drops

No dedicated bundled Gorilla death-loot table or unique resource drop was found. Its default entity-loot key uses the empty-table fallback when the server supplies no table. An eligible adult death awards the inherited **1–3 XP** with normal player-credit and mob-loot conditions. [Loot key][loot-key] · [Missing-table fallback][fallback] · [Animal XP][animal] · [Death conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registration, bundled biome entries, spawn-helper callers, all Gorilla food/block tags, goals, interaction signatures, persistence, and loot resolution. No in-game spawn, food, taming, riding, combat, or drop test was run.

Related: [Gorilla Spawn Egg](../items/GorillaSpawnEgg.md) · [Gelada Monkey](GeladaMonkey.md) · [Mobs](Mobs.md)

[gorilla]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java
[tags]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L171-L176
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L707-L713
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L177
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[base-stats]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L99-L119
[silverback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L470-L483
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L188-L204
[caravan]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/GorillaAIFollowCaravan.java
[baby-ride]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/AnimalAIRideParent.java#L19-L86
[charge]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/GorillaAIChargeLooker.java#L26-L85
[goals]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L141-L179
[retaliation]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L60-L115
[melee]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L144
[damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1320
[tag-namespace]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L250-L255
[membership]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/Holder.java#L167-L177
[forage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/GorillaAIForageLeaves.java
[interaction]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L310-L336
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L128
[save]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L294-L307
[owner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L53-L79
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
