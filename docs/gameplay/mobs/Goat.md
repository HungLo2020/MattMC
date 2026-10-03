# Goat

A Goat (`minecraft:goat`) is a mountain animal with **10 health points (5 hearts)**, active long-jump behavior and a ram that can knock a player off a ledge. It can begin a ram without first being attacked. Adults can be milked and may supply [Goat Horns](../items/GoatHorn.md). [Health][goat-health] · [Registered attributes][goat-attributes] · [Ram targeting][goat-ai]

## Obtaining

In the bundled normal Overworld, search **[Snowy Slopes, Frozen Peaks and Jagged Peaks](../biomes/MountainsAndWindsweptBiomes.md)**. Each lists Goats in configured groups of **1–3**. The ground tag allows Grass Blocks, Stone, Snow layers, Snow Blocks, Packed Ice and Gravel; the predicate also requires raw brightness **above 8**, plus the placement/space checks. Nearby mountain biome names do not guarantee that Goats are candidates there. [Slopes][slopes] · [Frozen Peaks][frozen-peaks] · [Jagged Peaks][jagged-peaks] · [Spawn registration][goat-placement] · [Goat check][goat-spawn] · [Ground tag][goat-ground] [Grass-block tag][animal-ground] · [Brightness][animal-spawn] · [Other placement checks][natural-check]

The normal preset's Overworld source selects these cold slopes/peaks, and the creature lists are consumed by the active generation and natural-spawn paths. [Normal preset][normal] · [Parameter provider][parameters] [Biome-source mapping][biome-provider] · [Peak selection][biome-peaks] · [Generation][worldgen-caller] [Creature population][worldgen-spawn] · [Natural selection][natural-select]

For deliberate placement, use the ordinary listed [Goat Spawn Egg](../items/GoatSpawnEgg.md) through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. This does not establish a natural herd. [Egg listing][goat-egg]

## Behavior

### Wheat, kids and milk

Goats follow **Wheat**, use it for ordinary breeding, and accept it to speed a kid's growth. Two eligible adults need to meet to produce a kid; afterward the shared animal system gives both parents a **6,000-tick breeding cooldown**. They do not become tame pets through that food interaction. [Food and interaction][goat-food] [Goat interaction][goat-milk] · [Installed follow/breeding behavior][goat-ai] · [Breeding caller][brain-breeding] · [Feeding and cooldown][animal-interact] [Shared mating and cooldown][animal-mate]

Use an empty Bucket on an **adult** Goat for a [Milk Bucket](../items/MilkBucket.md). The interaction has no feeding prerequisite or milking cooldown; kids do not qualify. Milk's effect-clearing behavior belongs to its item page. [Milk interaction][goat-milk]

Kids have no horns. When they become adults, the age transition supplies both horns. Spawn initialization also has a **10% chance** to remove one horn from an initialized adult, so finding a one-horn Goat does not prove that another player collected its first horn. The remaining-horn state is saved. [Age transition][goat-health] · [Initialization and saved flags][goat-finalize]

### Screaming goats

Spawn initialization chooses the screaming variant with a **2% chance**. Breeding randomly chooses one parent's screaming state, then gives a non-screaming choice a further 2% chance to become screaming: two ordinary parents give 2%, a mixed pair 51%, and two screaming parents 100% under this unchanged code. Screaming and ordinary Goats share the same entity ID. [Spawn variant][goat-finalize] · [Offspring calculation][goat-breed] · [Registration][goat-type]

The distinction affects sounds, the possible horn instruments and ram cooldowns. After a finished ram, ordinary Goats receive **600–6,000 ticks** of cooldown, while screaming Goats receive **100–300 ticks**. Initial memory setup uses the ordinary cooldown range for both, and path/target requirements still apply; these are not guaranteed intervals between visible attacks. [Cooldown definitions and initialization][goat-ai] · [Variant-dependent ram setup][goat-ram] · [Cooldown reset][ram-hit]

### Ramming and horn collection

Ramming chooses a nearby visible eligible living target, excluding Goats. It needs a reachable run-up position **4–7 blocks** away along a cardinal direction, a stable route, and a **20-tick preparation** once positioned. If the target changes block position during preparation, the Goat recalculates. Temptation, breeding and cooldown memories restrict entry into the ram activity. [Ram setup][goat-ram] · [Target/preparation][ram-prep] · [Run-up checks][ram-runway]

A ram can deal damage and substantial knockback. If it reaches a horn-snapping block without first hitting a living target, it can drop one remaining horn. The [Goat Horn guide](../items/GoatHorn.md#obtaining) lists the exact supported blocks and horn types. Killing the Goat does not call this horn-drop path. [Hit, horn and finish branches][ram-hit] · [Remaining-horn drop][goat-horns] · [Death table][goat-loot]

For an attempt, choose flat safe ground, leave a clear run-up and move out of the charge toward a suitable block. Keep away from cliff edges, including when using a Shield: the ram has its own knockback calculation. This is source-based advice, not a tested horn-farm design. [Run-up][ram-runway] · [Damage and blocking-adjusted knockback][ram-hit]

### Jumps and keeping a herd

Goats have an active long-jump routine that chooses landing candidates and checks a trajectory. Plan containment with their jumps in mind; an ordinary open pen is not certified escape-proof by this review. Their fall-damage calculation subtracts **10 damage points** from the normal result, which reduces falls rather than making them universally safe. [Jump activity][goat-jump] · [Launch conditions/candidates][jump-start] · [Trajectory check][jump-trajectory] · [Fall reduction][goat-health]

Goats inherit the animal rule against ordinary distance-based despawning and can be led, but can still wander, jump or die. Breed and transport them away from hazardous drops. [Retention][animal-persistence] · [Lead eligibility][leash]

## Drops

**This build's Goat loot table gives 1–2 Mutton before Looting**, with a Looting increase and fire/enchantment smelting conditions. The ordinary entity-loot gate requires an adult and enabled mob loot. Horns are the separate ram reward above. [Actual Goat table][goat-loot] · [Loot gate][loot-rule] · [Death dispatch][death]

## Notes

Related: [Goat Horn](../items/GoatHorn.md) · [Milk Bucket](../items/MilkBucket.md) · [Mountain biomes](../biomes/MountainsAndWindsweptBiomes.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Followed loaded biome lists and spawn callers, registered brain/attributes, feeding/breeding, screaming selection, milk, age/horn flags, jump/ram activities and loot. The Goat ticks the reviewed brain on the server. [Active brain caller][goat-ai-call] · [Registered entity][goat-type] · [Loaded loot][loot-load]

No spawning, enclosure, feeding, breeding, milking, jump, ram, horn-collection or death-loot gameplay test was performed. Modified tags, data packs and ticking can change these conditions.

[goat-health]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L126-L144
[goat-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L176
[goat-ai]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/GoatAi.java#L38-L112
[slopes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/snowy_slopes.json#L1-L184
[frozen-peaks]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/frozen_peaks.json#L1-L177
[jagged-peaks]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/jagged_peaks.json#L1-L177
[goat-placement]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L125
[goat-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L374-L378
[goat-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/goats_spawnable_on.json#L1-L10
[animal-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json#L1-L5
[animal-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[natural-check]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L265
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L84
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[biome-provider]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-peaks]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L887-L904
[worldgen-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L442-L450
[worldgen-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L418
[natural-select]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[goat-egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2026
[goat-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/goat_food.json#L1-L5
[goat-milk]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L226-L247
[brain-breeding]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/AnimalMakeLove.java#L49-L83
[animal-interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[animal-mate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L228
[goat-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L249-L283
[goat-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L170-L180
[goat-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L704-L706
[goat-ram]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/GoatAi.java#L136-L177
[ram-hit]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/RamTarget.java#L74-L124
[ram-prep]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/PrepareRamNearestTarget.java#L65-L123
[ram-runway]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/PrepareRamNearestTarget.java#L132-L171
[goat-horns]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L317-L359
[goat-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/goat.json#L1-L72
[goat-jump]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/GoatAi.java#L115-L133
[jump-start]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/LongJumpToRandomPos.java#L79-L130
[jump-trajectory]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/LongJumpToRandomPos.java#L172-L192
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[leash]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[goat-ai-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L188-L197
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
