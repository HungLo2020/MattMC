# Cachalot Whale

The **Cachalot Whale** (`minecraft:cachalot_whale`) is a large, air-breathing predator. Returning a stranded whale to water can produce a **one-time reward of 2–3 Prismarine Crystals**. Its charge attacks and block destruction are active, so leave generous open water around it and keep it away from valuable structures. [Whale behavior][whale]

## Availability, size, and albinos

The whale, its attributes, its echo entity, and its [spawn egg](../items/CachalotWhaleSpawnEgg.md) are registered. No whale entry was found in the 68 bundled biome spawn files or active spawn-placement registrations. Its surface-water helper has no registered caller, and the saved beach-event fields have no active spawning caller. Neither a particular ocean biome nor a recurring stranded-whale event is established here. A Creative egg or administrator-provided whale is the verified setup route. [Registration][registration] · [Attributes][attributes] · [Spawn placements][placements] · [Natural spawn selection][natural] · [Beach-event data][world-data]

| Default form | Maximum health | Attack attribute used by a charge |
| --- | ---: | ---: |
| Ordinary | 160 points / 80 hearts | 30 damage points |
| Albino | 230 points / 115 hearts | 45 damage points |

A normal spawn initialization has a **1% albino chance**. The higher albino stats are installed when the variant is first set. Damage values are before defenses and other damage handling. While ticking, the whale also heals **2 health points every 200 ticks**, about ten seconds at 20 TPS. [Attributes][stats] · [Albino state][albino] · [Initialization][spawn] · [Healing][heal]

Its registered main body is **5 blocks wide and 4.5 blocks high**. Additional head/body/tail helpers extend around it, and baby helpers scale down. These are not tested tank dimensions: the old multipart hitbox integration was removed, so do not assume every visible part has independently working player hit detection. [Registration][registration] · [Part construction and scaling][parts] · [Multipart limit][multipart] · [Current part tracking][part-tracking] · [Hit lookup][part-lookup]

## Air and water care

Leave an open route to the surface. The whale has **4,000 ticks of maximum air**, about **3 minutes 20 seconds at 20 TPS** under ordinary conditions. It is not in the underwater-breathing tag, so normal drowning logic still applies. Its breathing goal takes priority when air falls below 140 and tries to move upward; surfacing restores its air supply. A sealed underwater enclosure is unsafe. [Air supply and breathing goal][air] · [Underwater-breathing tag][breathing-tag] · [Drowning path][drowning]

A whale on the ground and outside water becomes beached. It has a nearby-water search, but this does not guarantee it can free itself from terrain. Restoring water around it and a route to open water addresses the actual state check; no food or special rescue-item interaction is required by that check. Water currents can push it while its beached flag is set. [Beaching transition][rescue] · [Water search][water-goal] · [Fluid response][fluid]

The sleep code should not be read as a reliable nightly schedule. It compares the world's raw day-time value to **18,000–22,812**, without wrapping it into a repeating day. Its surfacing particles are also commented out, so visible spout particles are not a dependable indicator of breathing. [Sleep-time check][sleep] · [Particle limit][spout]

## Rescue reward

For a whale that has not already rewarded a player:

1. Its beached state must transition back into water
2. It selects the nearest player within **50 blocks**, unless that player is its currently remembered attacker
3. While still in water, it moves toward that selected player and emits **2–3 Prismarine Crystals** when within **10 blocks**

This is proximity-based credit: the implementation does not track which player dug a channel or placed the water. The items appear near the whale's mouth, with ordinary pickup delay. Its saved `GivenReward` flag makes the reward **once per whale**, rather than a repeatable payment for every stranding. [Selection and reward][rescue] · [Saved reward flag][save]

The actual reward is [Prismarine Crystals](../items/PrismarineCrystals.md). The source comment mentioning Ambergris does not make an Ambergris item drop available. Looting is not used in this reward calculation. [Reward item][rescue]

## Predation, retaliation, and boats

The bundled prey tag includes **Mimic Octopus, Giant Squid, Squid, and Glow Squid**. Keep these out of a shared enclosure. A hurt whale can retaliate and alert adults nearby; a hurt calf alerts adults and stops its own retaliation goal. [Target goals][goals] · [Prey tag][prey] · [Calf alert behavior][retaliation]

Its combat runs directly in the server update. For an underwater prey target it can send an echo and wait for the return before charging. Player targets, targets outside water, and its current attacker bypass that wait. A close, developed charge requests the attack damage in the table above, and charging can also push nearby creatures around the head. [Echo and charge][charge] · [Echo return][echo] · [Damage forwarding][hurt] · [Charge push][push]

An occupied ordinary **Boat** can be destroyed by the charge-hit routine. Do not treat a boat as protection from an angry whale. The special boat handling checks the `Boat` class; this is not a verified claim about every raft or chest-boat type. [Boat hit handling][boat]

The Giant Squid capture initiation and the call to its captured-state update are commented out. Consequently, the old squid-holding struggle is not an established active whale behavior, even though related fields and rendering code remain. [Disabled capture bridge][capture]

## Building damage

The live block-breaking callback searches the whale's main body area, including nearby blocks above and below it, with an upper checked block height of **Y 127**:

- Normally it selects blocks in the **ice tag**, including Ice, Packed Ice, Blue Ice, and Frosted Ice, and replaces broken tagged ice with Water
- When charging with a target, it instead selects the broad **pickaxe-mineable tag**, which includes Stone and Obsidian
- A selected block must have a nonempty shape and no fluid in its state

The direct destruction path has **no `mobGriefing` check**. Disabling that rule alone does not establish protection for a stone aquarium or other selected blocks. Use generous separation from builds and avoid provoking a charge beside them. The Y cap also means ice-breaking code is not a promise that the whale can clear an arbitrarily high roof. [Active block breaking][break] · [Ice tag][ice] · [Pickaxe tag][pickaxe] · [Destruction caller][destroy]

## Feeding, offspring, and persistence

No item is accepted as food: `isFood` always returns false. A breeding goal and offspring factory exist, but ordinary feeding cannot start breeding or speed baby growth. There is no ordinary taming, owner assignment, or player mounting interaction. Using a matching [egg on an existing whale](../items/CachalotWhaleSpawnEgg.md#using-on-a-whale) is a separate baby-creation route and copies that parent's albino flag. [Food and interaction][whale] · [Offspring factory][offspring]

Ordinary whales can despawn with distance. Albinos, sleeping whales, and charging whales are excluded by the whale's normal distance-removal check. A properly applied Name Tag supplies ordinary mob persistence. Merely becoming beached does not set the separate special beach-despawn flag; that flag's timer belongs to custom entity setup, not every stranded whale. [Distance removal and special timer][persistence] · [Name Tag persistence][name-tag] · [Saved flags][save]

Protect calves from [Orcas](Orca.md), whose active target goal specifically selects baby Cachalot Whales. [Orca target][orca]

## Other drops

A charge-strike event has a **one-in-ten chance to emit one Bone**. This is a living combat event, not a death-loot or Looting reward, and the current item is ordinary [Bone](../items/Bone.md), not a whale-tooth item. [Charge bonus][bonus]

No dedicated bundled Cachalot Whale death-loot table was found. The default loot key uses the empty-table fallback unless server data supplies a table. Eligible adult deaths can still award the inherited **1–3 XP** under the normal player-credit and mob-loot conditions. [Default loot key][loot-key] · [Fallback][fallback] · [Animal XP][animal] · [XP conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registrations, all bundled biome files, beach-event callers, variants, rescue attribution, actual reward items, air handling, echo and charge callbacks, block tags, disabled capture/multipart integration, persistence, and loot fallback. No in-game spawning, rescue, combat, block damage, breathing, or drop test was run. Server data and custom entity setup can alter availability and defaults.

Related: [Cachalot Whale Spawn Egg](../items/CachalotWhaleSpawnEgg.md) · [Giant Squid](GiantSquid.md) · [Prismarine Crystals](../items/PrismarineCrystals.md) · [Orca](Orca.md) · [Mobs](Mobs.md)

[whale]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L332-L337
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L134
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[world-data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/world/AMWorldData.java#L64-L87
[stats]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L125-L127
[albino]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L368-L383
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L850-L858
[heal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L479-L481
[parts]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L111-L175
[multipart]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L824-L833
[air]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L863-L976
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[drowning]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[rescue]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L433-L472
[water-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/TryFindWaterGoal.java#L15-L40
[fluid]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L869-L871
[sleep]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L768-L770
[spout]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L313-L325
[save]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L188-L204
[goals]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L237-L255
[prey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/cachalot_whale_targets.json
[retaliation]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/AnimalAIHurtByTargetNotBaby.java
[charge]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L583-L724
[echo]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotEcho.java#L89-L164
[hurt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1777
[push]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L742-L765
[boat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L684-L708
[capture]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L607-L692
[break]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L262-L300
[ice]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/ice.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[destroy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/Level.java#L262-L284
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L835-L843
[persistence]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L137-L153
[name-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L29
[orca]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityOrca.java#L143-L156
[bonus]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L709-L715
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L128
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[part-tracking]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L1907-L1911
[part-lookup]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L1316-L1321
