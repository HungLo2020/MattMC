# Orca

The **Orca** (`minecraft:orca`) can accompany a swimming player and grant **Orca's Might**, an attack-speed benefit. It also hunts other creatures and retaliates when hurt. It has **60 health points (30 hearts)**, so approach without striking it and leave room for it to surface. [Behavior and attributes][orca] · [Registration][registration]

## Availability in this snapshot

The entity, its attributes, and its [Orca Spawn Egg](../items/OrcaSpawnEgg.md) are registered. However, **no Orca spawn entry was found in the bundled biome data**, and its water-height spawn helper is not registered with the active spawn-placement system. A method describing water below sea level is not an established natural-spawning route. Do not spend a Survival expedition searching a particular ocean temperature on that evidence alone. [Spawn placements][placements] · [Natural spawn selection][natural] · [Attribute registration][attributes]

The verified setup route is the Creative spawn egg, or an administrator-provided mob. The behavior below applies when an Orca is present. Its registered adult dimensions are **1.6 blocks wide and 1.2 blocks high**; those are body dimensions, not a tested minimum enclosure size. [Entity registration][registration]

## Swimming together and Orca's Might

Swim near an Orca that is not targeting you. Its companion goal selects the nearest non-spectator player within **24 blocks**, requires that player to be swimming, and only continues while they are swimming and **less than 16 blocks away**. While running, each goal update has a **1-in-6 chance** to grant **Orca's Might I for 1,000 ticks**, about **50 seconds at 20 TPS**. Feeding is not required. [Companion selection and grant][custom-orca-goal] · [Player selection][custom-orca-player]

A later ordinary grant refreshes the remaining timer to 1,000 ticks when it is shorter; it does not add another 50 seconds or another level. Leaving the swimming encounter stops further grants but does not itself remove an existing timer. An angry Orca has a separate removal rule below. [Companion goal][custom-orca-goal] · [Same-effect refresh][custom-orca-refresh]

The effect adds **3 attack-speed attribute points per level**: +3 at the companion's level I, or +6 at level II if another source supplies it. That attribute controls how quickly a player's attack strength recharges between attacks; it does not directly add damage to each hit. The active effect is `minecraft:orcas_might`. Its modifier is installed when the effect is applied and removed when it expires or is cleared, including by [Milk](../items/MilkBucket.md). [Effect modifier][custom-orca-modifier] · [Level scaling][custom-orca-scale] · [Attack recharge][recharge] · [Apply and remove][custom-orca-lifecycle] · [Milk removal][custom-orca-milk]

An Orca targeting a player removes Orca's Might from that target. Treat the benefit as a reason to keep the encounter peaceful, not protection from the Orca itself. The companion goal can be interrupted by higher-priority behavior such as seeking air. [Effect removal and goal priorities][orca]

## Feeding and the breeding limitation

Use **Raw Salmon** directly on the Orca. The accepted food is the Salmon item itself; Cooked Salmon and Raw Cod do not match this check. The inherited animal interaction can consume it to put an eligible adult into love mode, or speed up a baby's growth. This interaction does not heal, tame, or assign an owner to the Orca. [Food check][food] · [Feeding interaction][animal]

**Hearts do not establish working Orca breeding here.** The class provides an Orca offspring factory, but its registered goals contain no mating/breeding goal and no replacement mating routine was found. Do not rely on feeding two adults to produce a calf in this snapshot. Using an Orca Spawn Egg on an existing Orca is a separate, active baby-creation route described on the [egg page](../items/OrcaSpawnEgg.md#using-on-an-orca). [Registered goals and offspring][orca] · [Normal breeding caller][breed]

Wild Orcas can despawn with distance because their distance-despawn check allows it when untamed. Salmon feeding does not change that check. No ordinary player taming or riding interaction was found in the checked Orca implementation. [Persistence and interactions][orca]

## Air, water, and enclosure care

An Orca needs **both water and access to air**:

- Its maximum air supply is **4,800 ticks**, about four minutes at 20 TPS from a full supply. Normal underwater breathing rules still apply, and its air-seeking goal activates when air is low
- Water or rain restores its moisture to **2,400 ticks**. Out of both, moisture counts down; once exhausted, it repeatedly requests drying damage
- A stranded Orca tries to find water and can flop, but those behaviors do not guarantee it escapes an enclosure or beach

Leave a clear route to the water surface. No bundled `orca_breakables` block tag was found, so do not rely on it breaking an ice roof or other barriers for air. The block-breaking routine needs that tag to select blocks. [Air and moisture][orca] · [Air-seeking goal][air] · [Underwater air handling][living-air] · [Underwater-breathing tag][breathing-tag] · [Block-tag definition][tags]

## Predation and combat limits

Orcas retaliate and can alert nearby Orcas. They also select baby Cachalot Whales and the bundled prey-tag members: **Moose, Polar Bears, Turtles, Drowned, Guardians, and Elder Guardians**. Keep those creatures out of a shared enclosure. The tag does not contain players; player retaliation is a separate route. [Target goals][orca] · [Prey tag][prey]

Its default attack attribute is **10 damage points before defenses and other damage handling**. Ordinary melee uses the current shared attack handler. Its separate jumping attack has a live hit callback and can trigger a bite animation. Avoid assuming a moving boat or the water's edge makes an angry Orca harmless. [Melee caller][melee] · [Shared damage][mob] · [Jump attack][jump] · [Damage forwarding][hurt]

There is a MattMC integration limit: the older custom attack method that chooses between bite and tail-swing animations does not match the current melee callback signature. Consequently, its tail-swing selection and special knockback should not be presented as verified ordinary melee behavior. This does not disable the working melee and jump damage paths. [Legacy selector][orca] · [Active caller][melee]

## Drops

No dedicated bundled Orca death-loot table or unique item drop was found. The default entity-loot key therefore resolves to the empty-table fallback unless server data supplies a table. Its inherited animal XP reward is **1–3 points** for an eligible adult death with the normal player-credit and mob-loot conditions. Orca's Might is obtained by swimming with it, not by killing it. [Default loot key][loot-key] · [Missing-table fallback][loot-fallback] · [Animal XP][animal] · [Death conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked active registrations, all 68 bundled biome JSON files, spawn-helper callers, food and inherited interactions, AI goals, effect registration, combat signatures, air handling, and loot resolution. No in-game spawning, swimming benefit, feeding, breeding, combat, enclosure, or drop test was run. Data packs, commands, and custom entity data can change availability and behavior.

Related: [Orca Spawn Egg](../items/OrcaSpawnEgg.md) · [Hammerhead Shark](HammerheadShark.md) · [Dolphin](Dolphin.md) · [Raw Salmon](../items/RawSalmon.md) · [Mobs](Mobs.md)

[orca]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityOrca.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L972-L974
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L204
[companion]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityOrca.java#L413-L456
[effects]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/effect/MobEffects.java#L129-L132
[might]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/effect/EffectOrcaMight.java
[recharge]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L1723-L1731
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityOrca.java#L338-L345
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L174
[breed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[air]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreathAirGoal.java
[living-air]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[tags]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L202-L204
[prey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/orca_targets.json
[melee]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L132
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1293-L1320
[jump]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/OrcaAIMeleeJump.java
[hurt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1777
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L121
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527

Orca's Might details additionally source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. No in-game effect or attack-timing test was run.

[custom-orca-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityOrca.java#L413-L455
[custom-orca-player]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/EntityGetter.java#L93-L100
[custom-orca-refresh]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L147
[custom-orca-modifier]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/effect/EffectOrcaMight.java#L9-L18
[custom-orca-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L201-L204
[custom-orca-lifecycle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1046-L1090
[custom-orca-milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
