# Hammerhead Shark

The **Hammerhead Shark** (`minecraft:hammerhead_shark`) is a predator that can select **living targets at half health or below**, including vulnerable players. It has **30 health points (15 hearts)** and a default **5-point attack attribute** before defenses and damage scaling. Heal before approaching, and do not treat its circling as harmless. [Targeting and attributes][shark] · [Registration][registration]

## Availability in this snapshot

The shark, its attributes, and its [spawn egg](../items/HammerheadSharkSpawnEgg.md) are active registrations. **No Hammerhead Shark entry was found in the bundled biome spawn lists**, and its water-height helper is not registered in the current spawn-placement system. The helper alone does not establish a warm-ocean, coral-reef, or other natural-spawn location. [Spawn placements][placements] · [Natural selection][natural] · [Attribute registration][attributes]

The verified setup route is its Creative spawn egg or an administrator-provided shark. This page describes behavior when one is present, not a confirmed Survival search route. Its registered body dimensions are **1.4 blocks wide and 0.8 blocks high**; leave more space for swimming and turning than those numbers alone suggest. [Registration][registration]

## What makes it attack

The registered target goals include:

- Retaliation against an attacker
- Living creatures at **50% of maximum health or lower**
- Squid, Mimic Octopuses, and schooling-fish entities through separate prey goals

The general injured-target rule is not limited to hostile mobs or to creatures that damaged the shark. For a player with the ordinary 20-point maximum health, the threshold is **10 health points, or five hearts**. Normal target eligibility still applies; the shared combat check excludes players in Peaceful and normally invulnerable Creative players. [Target goals][shark] · [Target selection][target-selection] · [Combat eligibility][eligibility] · [Player invulnerability][player]

**Healing above half health does not automatically end a pursuit.** The injury predicate is used to acquire a target, while the shared continuation check can keep the existing target without checking that health threshold again. Create distance and reach safety rather than waiting beside it for healing to cancel the attack. [Continuation check][continuation]

The shark also has a Guardian-avoidance goal, but it has lower goal priority than its prey-circling behavior. Do not rely on a Guardian beside an aquarium to suppress all attacks. [Registered priorities][shark]

## Circling and striking

With a target selected, the shark tries to circle in water, then moves directly toward the target. During its attack phase it invokes the current melee damage method when less than **two blocks** away, then resets the circling state. Its path, other active goals, and the target's movement affect what you actually see; this page does not claim a tested attack timetable. [Circling goal][shark] · [Live damage handler][damage]

Keep food and an exit route ready before entering its enclosure. The ordinary Boat-following goal can also make a shark accompany a moving player-controlled boat. Following a boat does not tame the shark or remove its target rules. [Registered behavior][shark] · [Boat-following goal][boat]

## Keeping one and interaction limits

Keep it in water. The inherited water-animal handler resets its air while submerged and drains it out of water, eventually dealing damage. It has a water-seeking goal, but no guarantee of escaping a dry holding area. [Water-animal air handling][water]

No player feeding, taming, healing, riding, or breeding interaction is implemented in the checked Hammerhead Shark class. It inherits from the water-animal base rather than the breedable animal base. Offering fish is not a verified way to make it friendly, and attacking a fish does not establish a player feeding mechanic. [Entity behavior][shark] · [Base class][water]

This base also refuses leashing. The ordinary spawn-egg-on-mob baby path cannot produce a baby Hammerhead: its inherited baby setter does nothing, and the egg helper rejects a result that is not a baby. See the [spawn egg page](../items/HammerheadSharkSpawnEgg.md). [Leashing][water] · [Baby check][egg]

## Drops

**There is no active Shark Tooth reward in this snapshot.** The tooth-drop section in the attack goal is explicitly disabled, and no Shark Tooth item or dedicated Hammerhead death-loot table was found in the bundled registrations/data. Do not build a tooth farm or expect the commented attack reward to produce Bones. [Disabled reward][shark]

The default entity-loot key falls back to an empty table when no table is supplied. The inherited water-animal reward is **1–3 XP points** under the normal player-credit and mob-loot conditions. Server data or custom equipment can change drops beyond this ordinary setup. [Loot key][loot-key] · [Empty fallback][loot-fallback] · [Water-animal XP][water] · [Death conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registrations, all 68 bundled biome JSON files, spawn-helper callers, target acquisition/continuation, active attack signatures, inherited air/interactions, and loot resolution. No in-game spawn search, low-health targeting, healing escape, combat, enclosure, breeding, or drop test was run. Custom data packs and entity data can change availability or behavior.

Related: [Hammerhead Shark Spawn Egg](../items/HammerheadSharkSpawnEgg.md) · [Orca](Orca.md) · [Hunger and healing](../mechanics/Hunger.md) · [Mobs](Mobs.md)

[shark]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityHammerheadShark.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L728-L734
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L181
[target-selection]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L75
[eligibility]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L917-L926
[player]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L764-L765
[continuation]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/target/TargetGoal.java#L38-L70
[damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1320
[boat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/FollowBoatGoal.java
[water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L121
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
