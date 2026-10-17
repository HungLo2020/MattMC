# Snow Leopard

The **Snow Leopard** is a predator from MattMC's bundled Alex's Mobs content. Players are absent from its normal prey list, but adults retaliate when attacked and hunt many common farm animals. Dropped food can heal it; feeding does not tame it. [Goals][goals] · [Prey][prey] · [Dropped food][food]

## Obtaining

Use the [Snow Leopard Spawn Egg](../items/SnowLeopardSpawnEgg.md), available through the [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**, or `/summon minecraft:snow_leopard` with command permission. The egg is registered and listed in the ordinary Spawn Eggs category. Using a matching spawn egg on an existing Snow Leopard can create a baby through the shared animal offspring interaction. [Egg registration][egg] · [Creative category][creative] · [Summon command][summon] · [Baby interaction][egg-interaction] · [Offspring][offspring]

**No natural biome encounter is established by the bundled defaults reviewed here.** No Snow Leopard entry was found in the built-in biome spawn lists or spawn-placement registrations. The class has a helper requiring a block in `minecraft:snow_leopard_spawns` beneath it and brightness above 8, but that helper is not registered with the spawn-placement dispatcher. Its bundled ground tag includes Overworld base stone, Dirt, Grass Blocks, Snow, Snow Blocks, and Gravel. These ingredients do not establish a mountain biome, altitude, group size, or spawn rate: supplying suitable ground alone would not add a natural spawn-list entry. [Spawn helper][spawn-helper] · [Spawn-ground tag][spawn-ground] · [Placement dispatcher][placements] · [Biome spawn data][biomes]

## Behavior

### Attributes

- **Health:** 30 points (15 hearts)
- **Base melee damage:** 6 points (3 hearts), before combat modifiers
- **Pounce damage:** 2.5 times its attack-damage attribute, or 15 points (7.5 hearts) at the default value, before combat modifiers
- **Base movement-speed attribute:** 0.35, reduced to 0.25 while stalking; these are not blocks-per-second measurements
- **Follow-range attribute:** 64 blocks
- **Step height:** 1 block
- **Registered adult dimensions:** 0.9 blocks wide × 0.9 blocks tall

The attribute builder is connected to the active default-attribute registry. [Attributes][attributes] · [Active attributes][active-attributes] · [Stalking speed][stalking-speed] · [Pounce][melee] · [Entity registration][registration]

### Prey and retaliation

The bundled prey tag contains **Cows, Chickens, Donkeys, Horses, Llamas, Mules, Parrots, Pigs, Rabbits, Sheep, Goats, Trader Llamas, Capuchin Monkeys, and Caimans**. Keep Snow Leopards separate from livestock and these pets: prey selection checks the target's entity type, with no tame/owner exemption in that predicate. Usual combat-target eligibility checks still apply. [Prey tag][prey] · [Tag predicate][predicate] · [Target selection][target-selection]

Players are absent from that prey tag. An adult can instead target an attacker through its retaliation goal. **Hurting a baby can alert nearby adults** that have no target and meet the shared ally checks; the baby then clears its own retaliation target. Adults do not automatically call the same alarm when they themselves are hit. Babies have panic and follow-adult goals and do not run the adult melee goal. [Goals][goals] · [Baby alarm][baby-alarm] · [Shared retaliation][retaliation] · [Baby panic][baby-panic] · [Melee eligibility][melee-start]

### Stalking, leaping, and resting

When a melee pursuit begins with the target more than **4 blocks** away, the Snow Leopard tries to stalk toward a vantage point, preferring higher valid positions, then leap at the target. If the target is the player that just hurt it, stalking starts only beyond **10 blocks**. Close targets are pursued directly with left- or right-paw attacks. The pounce attempts its stronger hit when the target is within 3 blocks and visible; it is not a guaranteed hit or a fixed damage-per-second rate. [Pursuit and leap][melee] · [Paw attacks][swipes]

Ordinary landing fall damage is suppressed by its active fall-check override. Its walking navigation and one-block step height help it cross uneven terrain, and its combat leap is another reason to allow room in an enclosure. [Fall check][fall] · [Navigation][navigation] · [Attributes][attributes]

An idle Snow Leopard may sit or sleep. These are automatic resting poses, not owner commands. It stops resting when it has an attack target, enters water, enters love mode, or reaches the resting timeout. Sitting and sleeping stop its voluntary travel. [Resting][resting]

### Feeding and breeding

A Snow Leopard can seek out **dropped items with a food component**, so its healing diet is not restricted to meat. On reaching the item it consumes **one item** and restores **5 health points (2.5 hearts)**, up to maximum health. The food must have been on the ground for more than 30 ticks before this goal considers it; finding and walking to it can take longer. It can consume food even when already at full health. This pickup does not set love mode or assign an owner. [Food check and healing][food] · [Item search and consumption][item-goal]

**To breed Snow Leopards, hand-feed two adults raw or cooked Mutton, Beef, Porkchop, Chicken, or Rabbit.** These ten items are the bundled contents of `minecraft:snow_leopard_breedables`. Both adults must be ready to breed, with a path to approach each other. The active breeding goal produces a baby directly; there is no egg-laying stage. After breeding, the parents receive the standard **6,000-tick cooldown** (5 minutes at 20 ticks per second). [Breeding foods][breeding-food] · [Food check][spawn-helper] · [Goals][goals] · [Mating goal][breed-goal] · [Offspring][offspring] · [Shared breeding][animal-feeding]

Hand-feeding those same meats to a baby speeds its growth. Dropping food on the ground instead uses the healing behavior above and does not begin breeding. Merely holding food does not activate a follow-player goal: the registered goals include following adults while young, but no food-temptation goal. There is no taming interaction or owner-command system in the Snow Leopard class. [Shared feeding][animal-feeding] · [Registered goals][goals] · [Entity implementation][entity]

### Drops and persistence

There is **no bundled Snow Leopard death-loot table**, and the class adds no custom death drop. Its default table key is `minecraft:entities/snow_leopard`; the shared lookup falls back to an empty table when that resource is missing. Do not expect a species-specific meat, hide, or other material drop from the bundled defaults. An adult has the inherited animal base reward of **1–3 experience**, subject to the normal player-credit, `doMobLoot`, and experience-modifier rules; babies do not give this death experience. [Loot data][loot-data] · [Default loot key][loot-key] · [Missing-table fallback][loot-fallback] · [Animal experience][animal-persistence] · [Experience eligibility][experience]

Snow Leopards inherit the animal rule that prevents distance-based despawning, so feeding or naming one is not required merely to keep it when players move away. The class does not save its sitting, sleeping, stalking, or tackling flags; these poses are transient. [Animal persistence][animal-persistence] · [Despawn dispatcher][despawn] · [Entity implementation][entity]

## Notes

- Entity ID: `minecraft:snow_leopard`
- Spawn egg ID: `minecraft:snow_leopard_spawn_egg`
- Entity category: `MobCategory.CREATURE`
- Entity class: `EntitySnowLeopard`
- Content origin: bundled Alex's Mobs integrated into MattMC

[Entity registration][registration] · [Spawn egg registration][egg]

Source-reviewed at MattMC gameplay commit `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. Spawn-list and placement wiring, tag loading, current method signatures, attributes, target selection, food, loot, and persistence were checked separately. Missing-data statements concern bundled defaults; data packs can change tag contents and loot. No in-game spawning, combat, feeding, or breeding test was run.

[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L81-L93
[prey]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/snow_leopard_targets.json
[food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L303-L311
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1965
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2094
[summon]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/commands/SummonCommand.java#L34-L108
[egg-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1105
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L144-L148
[spawn-helper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L66-L72
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L52-L179
[biomes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/biome
[spawn-ground]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/snow_leopard_spawns.json
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L95-L97
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L253
[stalking-speed]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L201-L212
[melee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/SnowLeopardAIMelee.java#L84-L177
[registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1300-L1302
[predicate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L79-L81
[target-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L83
[baby-alarm]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AnimalAIHurtByTargetNotBaby.java#L18-L31
[retaliation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L35-L117
[baby-panic]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AnimalAIPanicBaby.java#L6-L18
[melee-start]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/SnowLeopardAIMelee.java#L84-L97
[swipes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L240-L258
[fall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L74-L79
[navigation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AdvancedPathNavigateNoTeleport.java#L12-L20
[resting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java#L213-L272
[item-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L38-L151
[breeding-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/snow_leopard_breedables.json
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L82
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L131-L227
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySnowLeopard.java
[loot-data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L121
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[experience]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1479-L1488
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L607-L633
