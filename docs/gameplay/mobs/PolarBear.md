# Polar Bear

A Polar Bear (`minecraft:polar_bear`) is a neutral animal with **30 health points (15 hearts)**. Adults can attack to protect nearby cubs, and being neutral is not a promise that walking close is safe. Fish do not tame or breed it in the checked implementation. [Health and spawn rule][bear-health] · [Registered attributes][bear-attributes] · [Food and goals][bear-food] · [Cub protection][bear-cubs]

## Obtaining

The bundled normal Overworld lists Polar Bears in **[Snowy Plains and Ice Spikes](../biomes/TaigaAndSnowyBiomes.md)** and **[Frozen Ocean and Deep Frozen Ocean](../biomes/Oceans.md)**. Each creature entry specifies groups of **1–2**. Those are attempted group sizes, not guaranteed sightings. Group finalization can produce a cub alongside an adult. [Snowy Plains][snowy-plains] · [Ice Spikes][ice-spikes] · [Frozen Ocean][frozen-ocean] · [Deep Frozen Ocean][deep-frozen-ocean] · [Group initialization][bear-cubs] · [Shared age selection][age-group]

Ground requirements differ. In the two frozen-ocean biomes, the alternate spawn rule requires **Ice** beneath the attempt and raw brightness **above 8**. Elsewhere it uses the ordinary animal ground tag, currently **Grass Block**, and the ordinary brightness rule. Snowy scenery or Packed Ice alone does not satisfy the frozen-ocean Ice tag. Placement and collision checks still apply. [Registered predicate][bear-placement] · [Branching rule][bear-health] · [Alternate biomes][bear-alt-biomes] · [Alternate ground][bear-ground] · [Ordinary ground][animal-ground] · [Brightness helper][animal-spawn] · [Placement checks][natural-check]

The normal source selects those biomes, and the loaded creature tables reach both generation and later natural-spawn callers. [Normal preset][normal] · [Parameter source][parameters] [Biome-source mapping][biome-provider] · [Cold biome selection][biome-cold] · [Generation][worldgen-caller] [Creature population][worldgen-spawn] · [Natural selection and creation][natural-select] [Natural spawn creation][natural-finalize]

The ordinary listed [Polar Bear Spawn Egg](../items/PolarBearSpawnEgg.md) is another placement route through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival and Creative. Egg placement is separate from natural spawning or breeding. [Egg entry][bear-egg]

## Behavior

### Cubs, retaliation and warning attacks

An adult can select a nearby player when **any cub** is found in its surrounding search box, expanded 8 blocks horizontally and 4 vertically. The check does not require that cub to be its own offspring. An injured cub alerts eligible nearby adults and stops its own retaliation goal. Adults also retaliate when hurt, and their target goals include Foxes. [Installed targets][bear-food] · [Cub-dependent targeting and alerts][bear-cubs]

Standing upright and growling can be part of the melee attack's warning sequence. Do not approach to inspect it: the attack routine switches between warning posture and actual strikes according to reach and its attack cooldown. Give the family space and choose a route around it. [Attack/warning sequence][bear-attack]

The persistent-anger timer is sampled from **20–39 seconds of ticks**, but it does **not count down while a player remains the active target** in this update mode. Anger state is saved, and a nearby cub can create another targeting opportunity. Simply waiting 39 seconds beside an angry bear is not a reliable calming method. [Timer range][bear-anger] · [Server anger update][bear-tick] · [Countdown and rule conditions][anger] · [Saved anger][bear-save]

### Feeding, growth and keeping a bear

The Polar Bear's food test always returns false, and its active goals contain no ordinary breeding or food-temptation routine. **Raw Cod and Salmon are not accepted breeding food.** The presence of an offspring-creation method does not supply a food-driven breeding route. Cub growth follows the ordinary age timer; feeding does not provide the usual baby-growth shortcut because there is no accepted food. [Food test and goals][bear-food] · [Shared feeding gate][animal-interact] · [Age ticking][age]

It inherits the animal rule against ordinary distance-based despawning and can be attached to a Lead. These do not make it tame or remove cub protection and anger. Secure a route before attempting to move one, and keep Foxes away from its enclosure. [Retention][animal-persistence] · [Lead eligibility][leash] · [Targets][bear-food]

## Drops

The adult death table makes one weighted choice between **Cod at weight 3** and **Salmon at weight 1**, then gives **0–2 of the selected fish before Looting**. A zero roll can leave no fish. Looting can increase the count, and the table can smelt the fish under its fire/enchantment conditions. These are death rewards, not evidence that the bear eats those items. [Fish table][bear-loot]

Ordinary entity loot must be enabled; babies fail the shared loot gate. This source review does not establish a fish farm or a reason to approach a cub group for drops. [Loot gate][loot-rule] · [Death dispatch][death]

## Notes

Related: [Snowy biomes](../biomes/TaigaAndSnowyBiomes.md) · [Ocean biomes](../biomes/Oceans.md) · [Fox](Fox.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked loaded spawn lists, ground/light branches and active callers, cub group state, installed target/melee goals, anger ticking/save data, food refusal, age/persistence and fish loot. The registered Polar Bear uses the ordinary mob AI scheduler. [Registration][bear-type] · [AI caller][mob-ai] · [Loaded loot][loot-load]

No search, spawning, cub approach, combat, calming, transport, feeding or drop gameplay test was performed. World rules and data packs can change behavior and eligibility.

[bear-health]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L97-L112
[bear-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L214
[bear-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L68-L95
[bear-cubs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L238-L295
[snowy-plains]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/snowy_plains.json#L1-L183
[ice-spikes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/ice_spikes.json#L1-L186
[frozen-ocean]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/frozen_ocean.json#L1-L195
[deep-frozen-ocean]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/deep_frozen_ocean.json#L1-L195
[age-group]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L31-L48
[bear-placement]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L139
[bear-alt-biomes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/biome/polar_bears_spawn_on_alternate_blocks.json#L1-L6
[bear-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/polar_bears_spawnable_on_alternate.json#L1-L5
[animal-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json#L1-L5
[animal-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[natural-check]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L265
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L84
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[biome-provider]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-cold]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L71-L100
[worldgen-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L442-L450
[worldgen-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L418
[natural-select]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[natural-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L210
[bear-egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2063
[bear-attack]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L299-L330
[bear-anger]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L59-L62
[bear-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L185-L208
[anger]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/NeutralMob.java#L29-L87
[bear-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/PolarBear.java#L114-L149
[animal-interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[age]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[leash]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[bear-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/polar_bear.json#L1-L133
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[bear-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1066-L1068
[mob-ai]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L634-L661
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
