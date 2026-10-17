# Enderiophage

The **Enderiophage** is a flying parasite with **20 health points (10 hearts)**. It attaches to suitable living hosts to bite them, then seeks an Enderman after losing its eye. Keep it away from [Endergrades](Endergrade.md) and poisoned animals. MattMC currently uses ordinary **Poison** for its “Ender Flu,” and its attachment to players is blocked by a shared server rule. [Attributes and goals][basics] · [Active attributes][attributes] · [Effect alias][effect-alias] · [Player attachment](#player-attachment-limitation)

## Obtaining

Use the [Enderiophage Spawn Egg](../items/EnderiophageSpawnEgg.md) from Creative's ordinary Spawn Eggs category for deliberate placement. See the egg page and [shared spawn-egg rules](../items/SpawnEggs.md) for acquisition and placement. [Egg registration][egg] · [Creative entry][creative] · [Placement][egg-placement]

**No natural encounter or Survival acquisition route is established in this snapshot.** The checked biome and structure spawn lists, spawn-placement registrations, bundled data and structure templates contain no Enderiophage entry. Its permissive spawn-check methods do not add it to those lists. No active event, transformation or effect-based creation route was found either. Do not plan an End biome farm or expect an infected host to produce more Enderiophages. [Spawn checks][spawn-checks] · [Natural selection][natural] · [Biome and structure lists][spawn-lists] · [Spawn placements][placements] · [Bundled data][data] · [Effect behavior][poison]

Initial spawning selects an Overworld, Nether or default appearance according to the dimension; that variant is saved. A different appearance is not evidence of a separate spawn route or fire immunity. [Initial spawn][initial-spawn] · [Dimension checks][dimension-checks] · [Variant selection][variants] · [Saved state][saving] · [Registration][entity]

## Behavior

### Choosing a host

An Enderiophage with its eye seeks **Endergrades or living entities with Poison**, subject to the normal target, range and sight checks. Its base follow range is **16 blocks**. It also has a retaliation goal that ignores Enderman attackers. While missing its eye, it instead seeks Endermen and clears non-Enderman targets. Ordinary, unpoisoned players are not a standing prey category, though retaliation can select them. [Attributes and goals][basics] · [Effect alias][effect-alias] · [Target selection][target-selection] · [Target filters][target-filters] · [Retaliation][retaliation] · [Missing-eye targeting][eye-targeting]

It walks or flies toward its target and attempts to attach at close contact when its attachment cooldown has expired. This attack goal stops accepting a host already carrying **four Enderiophages**. Non-player attachment uses the shared passenger system; it is not a ride that the player controls. [Approach and attachment][attachment] · [Riding gate][riding] · [Server AI dispatch][ai-dispatch]

### Biting and recovering its eye

Once attached to a living host for more than **15 ticks**, the active server passenger update attempts a bite. The requested damage is **6 health points (3 hearts)** while the host is above 20% of maximum health, or **1 health point (½ heart)** otherwise. It keeps attempting bites while attached; the host's damage defenses and hurt cooldown still apply, so these numbers are not damage per tick. [Passenger dispatch][passenger-dispatch] · [Bite routine][bites] · [Damage handling][damage]

After an accepted damage attempt, or when the host's remaining health is below 1.5 points:

- On a **non-Enderman host**, there is a **one-in-three** chance to enter the eye-loss branch. If the Enderiophage still has its eye, it attempts to apply **Poison I for 12,000 ticks**, about **10 minutes at 20 ticks per second**. Existing Poison instead keeps its remaining duration and gains one amplifier step, capped at amplifier 4 (**Poison V**). The Enderiophage heals **5 health points (2½ hearts)**, loses its eye and detaches. The eye-loss branch does not require the host to accept the effect, so effect immunity can prevent Poison without preventing eye loss. [Bite and infection branch][bites] · [Effect alias][effect-alias] · [Effect admission][effect-admission] · [Healing cap][healing]
- On an **Enderman**, it restores its eye, heals **5 health points**, applies **Blindness for 400 ticks** and flies away for a **400-tick** fleeing period, about **20 seconds**. It clears its target and detaches. This is an internal eye-state change; the routine does not award an Eye of Ender item. [Eye recovery][bites] · [Fleeing][fleeing]

Detaching after these conditions sets a **100-tick attachment cooldown**, about **5 seconds**. The bite routine can also kill its host. Poison itself only attempts damage while health is above 1 point, but the physical bite has no such survival floor. [Detachment][detach] · [Cooldown update][cooldowns] · [Poison damage][poison]

### Player attachment limitation

A poisoned player, or a player selected for retaliation, can be pursued, but **this attack route cannot attach an Enderiophage to a player on the server**. It calls forced riding, yet the shared method rejects nonserializable vehicles before checking that force flag; players have the nonserializable registration. Consequently, this passenger-bite route does not establish player bites or Poison application. This is the source-confirmed limitation tracked in [issue #805](https://github.com/HungLo2020/MattMC/issues/805), not a claim that non-player attachment is broken. [Attachment caller][attachment] · [Shared rejection][riding] · [Player registration][player-type] · [Nonserializable flag][no-save]

### Breeding and keeping one

There is **no implemented taming or food-breeding interaction**. It accepts no food and its offspring method returns nothing. Using a matching spawn egg directly on an existing Enderiophage also cannot create an offspring through the shared baby-spawn path; place the egg against a block instead. [Food and class][basics] · [No offspring][offspring] · [Animal interaction][animal-interaction] · [Egg offspring path][egg-offspring]

“Ender Flu” does **not** hatch into Enderiophages here: its active effect is vanilla Poison. The small-scale initialization helper has no caller in the checked active source and supplies no reproduction route. [Effect alias][effect-alias] · [Poison implementation][poison] · [Unused effect-spawn helper][effect-spawn]

Use an enclosed space with a roof for containment. It can fly, cannot be attached to a normal [Lead](../items/Lead.md), and has no owner-following behavior. It inherits the Animal rule that prevents ordinary distance-based despawning, so a [Name Tag](../items/NameTag.md) is optional for that purpose. Its flying state, eye state and appearance use the current save/load callbacks. [Movement goals][basics] · [Idle flight][idle-flight] · [Enemy classification][classification] · [Lead exclusion][leash] · [Animal persistence][animal-persistence] · [Despawn dispatch][despawn] · [Saved state][saving]

Switching to Peaceful does not automatically remove it: its entity registration keeps the shared “allowed in Peaceful” default. Peaceful blocks normal player targeting, but does not guarantee that non-player hosts are safe. [Registration][entity] · [Registration defaults][entity-defaults] · [Despawn dispatch][despawn] · [Player-targeting rule][player-targeting]

## Notes

### Environment and damage

- Keep it out of prolonged submersion and away from fire or lava. It has a Float goal, but no inherent underwater breathing or fire immunity. The Nether appearance does not change those protections. [Float goal registration][basics] · [Breathing check][breathing] · [Drowning][drowning] · [Breathing tag][breathing-tag] · [Nested undead tag][undead-tag] · [Skeleton members][skeleton-tag] · [Zombie members][zombie-tag] · [Registration][entity] · [Registration defaults][entity-defaults]
- Ordinary landing fall checks are disabled by its active override. However, its old two-argument fall-damage helper is not the current damage callback: fall damage propagated from a host uses the shared three-argument method. It is absent from the bundled fall-immunity tag, so do not treat it as immune to every source of fall damage. [Fall methods][fall-methods] · [Passenger fall propagation][fall-propagation] · [Current fall damage][fall-damage] · [Fall-immunity tag][fall-tag]

### Drops

No `minecraft:entities/enderiophage` death-loot table is supplied in the checked bundled data. That ID resolves to `data/minecraft/loot_table/entities/enderiophage.json`; a missing table yields empty loot. No species-specific material drop or eye-stealing item reward is established. [Default loot-table ID][entity-defaults] · [Bundled data][data] · [Missing-table fallback][loot-fallback] · [Eye recovery][bites]

An ordinary adult has **1–3 base experience**, subject to the shared recent-player-credit and `doMobLoot` conditions. It inherits the Animal reward method; the constructor's separate value of 5 does not determine that reward. [Animal reward][animal-persistence] · [Reward dispatch][experience-dispatch] · [Experience conditions][experience-conditions] · [Constructor][basics]

### Rendering limitation

Enderiophage uses the Citadel model path tracked in [rendering issue #803](https://github.com/HungLo2020/MattMC/issues/803). When its visible ordinary body enters the active Rust whole-frame renderer with an eligible direct texture and finite transform, the inspected path produces an empty body mesh and throws an error. Its translucent body reaches this path through the shared direct-texture submission. This is a conditional source finding; no Enderiophage crash or invisibility was reproduced in game. [Renderer registration][renderer-registration] · [Body renderer][renderer] · [Model][model] · [Shared proxy][proxy] · [Body submission][body-submission] · [Translucent admission][render-admission] · [Finite-transform guard][render-transform] · [Extraction guard][render-guard]

Registered as `minecraft:enderiophage`, using `MobCategory.MONSTER`, with a normal size of **0.9 × 0.9 blocks**. Its entity class is `EntityEnderiophage` and its spawn egg is `minecraft:enderiophage_spawn_egg`. It comes from bundled Alex's Mobs content integrated into MattMC. [Registration][entity] · [Egg registration][egg]

Related: [Enderiophage Spawn Egg](../items/EnderiophageSpawnEgg.md) · [Endergrade](Endergrade.md) · [Enderman](Enderman.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked registration and attributes, active AI and passenger dispatch, damage and effects, spawn/event/offspring callers, resource filenames and decompressed structure data, optional bundled packs, nested tags, persistence, loot and the direct-texture rendering path. No in-game spawning, host attachment, feeding, damage, drop, save/reload or rendering test was run. Server data packs and later builds can change these results.

[basics]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L75-L160
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L160
[effect-alias]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/effect/AMEffectRegistry.java#L13-L17
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1867
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2014
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L84
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L91-L115
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[spawn-lists]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L78-L178
[initial-spawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L99-L110
[variants]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L301-L309
[saving]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L488-L502
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L562-L568
[target-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L83
[target-filters]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L59-L93
[retaliation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L33-L71
[eye-targeting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L429-L449
[attachment]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L720-L757
[riding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2291-L2325
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L633-L670
[passenger-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerLevel.java#L787-L802
[bites]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L203-L275
[damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1198
[effect-admission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L978-L1012
[healing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1139
[fleeing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L342-L370
[detach]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L277-L283
[cooldowns]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L326-L345
[poison]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/effect/PoisonMobEffect.java#L6-L26
[player-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1596-L1606
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L540-L544
[animal-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L131-L158
[egg-offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[effect-spawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L296-L309
[idle-flight]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L630-L704
[leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1219
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L631
[entity-defaults]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2050-L2074
[player-targeting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L917-L926
[drowning]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L440
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json#L1-L19
[undead-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[fall-methods]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L184-L189
[fall-propagation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1422-L1436
[fall-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1734
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json#L1-L22
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[experience-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L595
[experience-conditions]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L146
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderEnderiophage.java#L14-L75
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelEnderiophage.java#L13-L47
[proxy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L55
[body-submission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1044-L1054
[render-admission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L262-L281
[render-guard]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[dimension-checks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L191-L197
[classification]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityEnderiophage.java#L49
[breathing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L385
[no-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2142-L2145
[skeleton-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/skeletons.json#L1-L9
[zombie-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/zombies.json#L1-L12
[render-transform]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9513-L9532
