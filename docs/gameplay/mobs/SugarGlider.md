# Sugar Glider

The **Sugar Glider** is a small, passive, tamable animal with **8 health points (4 hearts)**. Apples attract it and are used for feeding, taming, and healing. An owner can select wandering, following, or sitting. Its climbing and gliding are automatic; the reviewed MattMC code has important limits around player carrying and leaf foraging, explained below. [Attributes and goals][attributes-goals] · [Attribute registration][attribute-registration]

## Obtaining

Use the [Sugar Glider Spawn Egg](../items/SugarGliderSpawnEgg.md) for Creative access through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md). The egg is registered and included in the Spawn Eggs category. Using a matching egg on an existing Sugar Glider goes through the shared baby-spawning interaction before owner commands. The new baby is not automatically given the parent's owner. [Egg registration][egg-registration] · [Category entry][egg-category] · [Interaction dispatch][mob-interaction] · [Baby creation][egg-offspring] · [Sugar Glider offspring][offspring]

**No built-in natural-spawn route was found in the reviewed gameplay snapshot.** Sugar Glider has no entry in the active spawn-placement registrations or biome/structure spawn lists. Its class contains a spawn helper and an obstruction check that accepts leaves, logs, or grass blocks beneath a clear, liquid-free space, but those checks do not add it to a biome. Do not treat a forest, leaf platform, or upstream Alex's Mobs biome description as a confirmed way to find one in MattMC. [Spawn registration and defaults][spawn-placements] · [Natural-spawn selection][natural-spawns] · [Entity spawn checks][spawn-checks]

## Behavior

### Apples, taming, and healing

Hold an [Apple](../items/Apple.md) to attract a Sugar Glider. The food and temptation checks name `Items.APPLE` directly: there is no Sugar Glider food tag to fill in, and sugar, honey, and golden apples are not substitutes in these checks. The goal list includes wandering, gliding, breeding, and panic, but no attack or owner-defense goal. Its attack-damage attribute does not make it a combat pet. [Goals and food check][attributes-goals] · [Food predicate][food]

The taming branch has a **50% success chance per attempt** and records the feeding player as owner. The healing branch restores **5 health points (2½ hearts)** to a tamed Sugar Glider below maximum health; it does not require the feeder to be the owner. These branches consume an apple, subject to Creative's normal item-consumption rules. [Apple interaction][apple-interaction] · [Owner assignment][taming] · [Item consumption][consume]

**Bring a stack of apples and account for feeding order.** The entity calls ordinary animal feeding *before* checking taming or healing. An adult ready to breed first consumes an apple to enter love mode; a baby first consumes one to speed growth. If apples remain, the same interaction can then consume a second apple for taming or healing. If that was the last apple, the stack now counts as air and the later apple branch is skipped. A single apple can therefore feed an animal without attempting to tame it or heal it. Hearts alone also do not distinguish love mode from a successful tame. [Animal feeding][animal-feeding] · [Sugar Glider interaction order][apple-interaction] · [Empty-stack checks][item-stack] · [Taming particles][tame-particles]

There are two further interaction quirks in this snapshot: feeding the last apple to your breed-ready adult can fall through into an owner command, because the superclass returns `SUCCESS_SERVER` and the subclass only excludes `SUCCESS`; a full-health tamed glider with apples remaining returns `PASS` even if the superclass already fed it. These are source-level limits, not a reliable one-apple-per-click interface. [Animal feeding][animal-feeding] · [Owner-command gate][owner-interaction] · [Top-level result handling][mob-interaction]

### Breeding and babies

Feed apples to two adults ready to breed and keep them close enough to reach one another. The inherited mating check requires two Sugar Gliders in love; it adds no requirement that they be tamed, share an owner, or be fed by their owner. Successful breeding gives the parents a **6,000-tick cooldown** (5 minutes at 20 ticks per second). A new baby starts with **24,000 ticks** to adulthood (20 minutes at that rate); apple feeding shortens its remaining growth time. [Breeding goal][attributes-goals] · [Mating and breeding cooldown][animal-breeding] · [Baby age and feeding reduction][baby-age]

The offspring method creates a fresh Sugar Glider without copying ownership or taming state. **Tame the baby separately** if you want it to belong to you. Babies do not use the adult's autonomous gliding goal or forage from leaves, although a tamed baby's follow routine can mark it as gliding while airborne. [Offspring and follow behavior][offspring-follow] · [Adult glide gate][glide-goal] · [Initial tame state][tame-initial]

### Owner commands and following

Use an empty hand on your tamed Sugar Glider without sneaking to cycle **Wandering → Following → Sitting → Wandering**. New gliders start in Wandering, so the first command click selects Following. The action-bar message names the selected command. Shared interactions such as naming, matching spawn eggs, or handling a lead can take priority over the species callback; use an unoccupied hand and finish those interactions first. [Initial command][initial-data] · [Command handling][owner-interaction] · [Command text][command-text] · [Shared dispatch][mob-interaction] · [Leash dispatch][leash-interaction]

- **Wandering:** normal wandering and automatic climbing/gliding goals can run
- **Following:** the owner-follow goal starts at 5 blocks and continues until within 2 blocks. Adults can move through the air toward their owner; babies and nearby adults use navigation. At 12 blocks or more, the goal attempts to find a collision-free teleport destination near the owner. Leashed gliders and passengers skip that movement/teleport step, so following is not guaranteed transport through every obstacle
- **Sitting:** sets the sit order, stops navigation during travel, and blocks the glider's climbing and autonomous gliding checks. It is useful for keeping a pet in place, but does not make it invulnerable

[Follow goal][follow-goal] · [Follow movement][offspring-follow] · [Sitting travel and climbing][travel-safety] · [Glide goal][glide-goal]

### Climbing, gliding, and carrying

Adults can search for nearby climbable surfaces, attach to their sides, climb, and glide toward another surface. This is an AI behavior rather than a player-steered flight mode. Landing or attaching to a surface stops the gliding state. Water, lava, sitting, and being a passenger clear the wall attachment; there is no saddle or steering interaction in the species callback. [Attachment movement][movement] · [Glide movement][glide-goal] · [Owner interactions][owner-interaction]

**Normal player pickup is blocked by the reviewed engine code.** The Sugar Glider callback tries to put it on its owner when the owner sneaks and interacts with a non-food item or an empty hand while carrying no passengers. However, the active `startRiding` method rejects player vehicles on the server because the player entity type is non-serializable. The callback ignores that failure and still reports success. Do not rely on this gesture to carry a glider or obtain Slow Falling. [Pickup request][owner-interaction] · [Mount rejection][start-riding] · [Player entity registration][player-type] · [`noSave` implementation][no-save] The rejected player-carry route is tracked in [issue #805](https://github.com/HungLo2020/MattMC/issues/805).

The separate passenger routine contains the intended head-perching behavior: if the glider were already riding its owner, it would refresh **Slow Falling I for 100 ticks** (5 seconds at 20 ticks per second) on that player, sit above the player's head, and detach when the owner sneaks after a 20-tick pickup delay. That routine does not establish a working pickup route by itself. [Passenger routine][ride-tick]

### Leaf foraging and its limits

A tamed adult with zero forage cooldown checks the leaf block on its attached side, or underneath it when unattached. It builds up 100 ticks of foraging progress while that condition remains true, then attempts a reward; losing the condition resets progress. The code spawns reward items beside the glider without removing the leaves. It uses the active `minecraft:leaves` block tag, so foraging eligibility is broader than the list of leaves with special rewards. [Forage tick][forage-tick] · [Adult/tame requirement][offspring-follow] · [Bundled leaves tag][leaves-tag]

The direct reward branches use one random roll:

| Leaf type | Direct reward before the fallback |
| --- | --- |
| Oak | 10% one apple; next 15% one oak sapling |
| Jungle | 10% one cocoa bean; next 15% one jungle sapling |
| Acacia | 10% one stick; next 15% one acacia sapling |
| Birch, spruce, dark oak | 25% one matching sapling |
| Mangrove | 25% one mangrove propagule |
| Other tagged leaves | No special direct reward; proceeds to the fallback |

[Leaf reward maps][leaf-maps] · [Reward roll order][forage-loot]

**Foraging is incomplete in this snapshot; do not plan a repeatable leaf farm around it.** Two source defects matter:

- After a completed reward attempt, the code sets a cooldown of 8,000 or 16,000 ticks, but never decrements that field. The numbers do not establish a working timed repeat cycle, and the cooldown is not saved
- The remaining rolls call the bundled `minecraft:gameplay/sugar_glider_reward` table, whose JSON contains one stick. The caller supplies a block-state parameter to the piglin-barter context, which does not allow it; context validation throws before loot generation. The existing stick table is therefore not evidence that this fallback can pay out as written

[Cooldown assignment and tick tail][forage-tick] · [Save fields][save] · [Fallback call][forage-loot] · [Bundled reward table][reward-table] · [Barter context][barter-context] · [Loot-parameter construction][loot-params] · [Context validation][context-validation]

## Notes

* This mob is registered as `minecraft:sugar_glider`, with class `EntitySugarGlider` and category `MobCategory.CREATURE`. Its registered size is **0.6 blocks wide × 0.5 blocks tall**. [Entity registration][entity-registration]
* Its spawn egg is registered as `minecraft:sugar_glider_spawn_egg`. This is bundled Alex's Mobs content integrated into MattMC; upstream behavior is not a substitute for the active MattMC implementation. [Egg registration][egg-registration]
* The active attributes are 8 maximum health, 0.25 movement speed, 1-block step height, and 2 attack damage. Taming adds no species-specific health upgrade. [Attributes][attributes-goals] · [Taming side effects][taming]
* The death loot table contains **one String** for adults when mob loot is enabled; babies do not use this death-loot path, and the table has no Looting multiplier or rare Sugar Glider item. [Death table][death-loot] · [Death-loot rules][loot-rules]
* Inherited animal behavior prevents ordinary distance-based despawning. Ownership is saved by `TamableAnimal`; this entity additionally saves its attachment face, command, and Sugar Glider sitting flag. Gliding state, forage progress, and the forage/pickup cooldowns are not included in those species save fields. [Animal despawning][animal-despawn] · [Despawn caller][despawn] · [Owner persistence][owner-save] · [Species persistence][save]
* The entity explicitly resists in-wall suffocation and overrides normal movement fall tracking with an empty method. Its old two-argument `causeFallDamage` method is **not** the engine's current three-argument damage override, so this does not establish universal fall-damage immunity. Carrying it is not a verified substitute for player fall protection. [Safety methods][travel-safety] · [Current fall dispatch][fall-dispatch] · [Current living-entity fall damage][living-fall]
* These details follow gameplay source commit [`2fff1ef`](https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716). They are source-traced findings, not a claim of an in-game test.

[attributes-goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L114-L139
[attribute-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L233
[egg-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1951
[egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2082
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1107
[egg-offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L545-L549
[spawn-placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L52-L189
[natural-spawns]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L284-L325
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L180-L195
[food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L164-L166
[apple-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L474-L500
[owner-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L501-L527
[taming]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L115-L172
[consume]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1121
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L173
[item-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L297-L335
[tame-particles]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L86-L109
[animal-breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[baby-age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L156-L168
[offspring-follow]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L545-L584
[glide-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L593-L754
[tame-initial]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L46-L51
[initial-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L141-L150
[command-text]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L4195-L4198
[leash-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2108-L2179
[follow-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/FlyingAIFollowOwner.java#L31-L161
[travel-safety]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L370-L420
[movement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L226-L280
[start-riding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2291-L2329
[player-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1596-L1605
[no-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2142-L2145
[ride-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L322-L352
[forage-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L282-L320
[leaves-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/leaves.json#L1-L15
[leaf-maps]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L69-L83
[forage-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L354-L368
[save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L383-L395
[reward-table]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/sugar_glider_reward.json#L1-L14
[barter-context]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/parameters/LootContextParamSets.java#L45-L46
[loot-params]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/LootParams.java#L60-L95
[context-validation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/util/context/ContextMap.java#L74-L85
[entity-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1188-L1191
[death-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/sugar_glider.json#L1-L14
[loot-rules]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[animal-despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[owner-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L53-L79
[fall-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1398-L1437
[living-fall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1734
