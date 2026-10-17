# Shoebill

Shoebills are fish-hunting birds that also catch loose fishing loot at water. They have **10 health points (5 hearts)** and a **4-point base melee attack**. Keep them away from pet fish and [Terrapins](Terrapin.md); they have no taming or owner-command system. Their food upgrades lack bundled ingredients, and their fishing does not produce treasure. [Attributes][basics] · [Goals][goals] · [Food interactions][feeding] · [Fishing rewards][fishing-loot]

## Obtaining

Use a [Shoebill Spawn Egg](../items/ShoebillSpawnEgg.md), available through the inventory item browser in **Creative**, or `/summon minecraft:shoebill` with command permission. The egg uses the normal direct-spawn route; it does not need the mob's natural spawn roll to succeed. [Egg registration][egg] · [Creative category][creative] · [Egg placement][egg-placement] · [Direct spawn][direct-spawn]

**No natural habitat is established in the reviewed implementation.** Active biome-building code, bundled biome spawn data, and spawn-placement registrations contain no Shoebill entry. Its separate spawn check uses `shoebillSpawnRolls`, which is **0**; the shared roll helper therefore rejects ordinary natural attempts. Do not plan a swamp search based on another version of Alex's Mobs. [Natural spawn selection][natural] · [Biome code][biomes] · [Spawn placements][placements] · [Bundled data][data] · [Spawn check][basics] · [Default roll][config] · [Roll helper][roll]

Using its matching spawn egg on an existing Shoebill can create a baby through the shared spawn-egg interaction. This does not establish food breeding. [Shared interaction][interaction] · [Baby creation][egg-baby] · [Offspring factory][feeding]

## Behavior

### Hunting and disturbance

Shoebills target **fish derived from the shared fish class**, including ordinary Cod, Salmon, Tropical Fish and Pufferfish, and separately target **Terrapins**. The Terrapin goal has no baby-only filter, so adults need protection too. Fishing for items does not require live prey in the water. [Prey goals][goals] · [Fish classes][fish-classes] · [Fishing conditions][fishing-search]

They have no ordinary player-hunting goal. Their retaliation goal explicitly **ignores player attackers**, while allowing retaliation against eligible other attackers and alerting nearby Shoebills. Damaging a Shoebill with a non-fish entity starts a **100–249 game-tick disturbance timer** for it and nearby Shoebills in a box extending 15 blocks horizontally and 7.5 vertically. The escape-flight goal can start from the ground while this timer is active; other goals can compete with it. Avoid hitting one to move it. [Goals][goals] · [Retaliation exclusions][retaliation] · [Damage response][damage] · [Escape flight][flight]

The current melee goal uses the shared attack method and the registered 4-point attack attribute. The imported one-argument attack method does not override that handler, so its timed beak-strike animation is not a dependable combat cue. [Melee dispatch][melee] · [Shared attack][shared-attack] · [Older animation method][old-attack] · [Attribute registration][attributes]

### Keeping one near water

A [Lead](../items/Lead.md) and a covered enclosure are useful for keeping a Shoebill nearby; food does not assign an owner, follow command or sit command. It walks and wanders normally, switches to flight movement when escaping, and has a swimming goal that helps it rise in deeper water. Provide an accessible bank and space around its fishing spot. [Goals][goals] · [Movement switching][movement] · [Swimming][swimming] · [Lead interaction][lead] · [Leash eligibility][leash]

Shoebills inherit the Animal rule that prevents ordinary distance-based despawning. Their fishing timer, luck and lure levels, flying state and disturbance timer are saved across reloads. Their landing-fall check is bypassed, but this is not general damage immunity. [Animal persistence][animal-persistence] · [Despawn checks][despawn] · [Saved state][saving] · [Fall handling][fall]

### Fishing and actual rewards

A fresh Shoebill starts with a **1,200–2,399 game-tick fishing cooldown**, roughly **60–120 seconds at 20 ticks per second**. That is a cooldown, not a guaranteed catch interval: the goal must also pass a random start check, reach water and reach the catch stage of its fishing sequence. Flying or an active disturbance timer interrupts continued fishing. [Initial timer][initial] · [Countdown][countdown] · [Fishing sequence][fishing-sequence] · [Start check][fishing-search]

A Shoebill already in water can use its current block. From land it samples nearby positions for water with a horizontally adjacent fluid-free space and fluid-free space above that neighbor, then approaches the water. The search does not check for live fish or a fishing rod. A completed cycle drops its catch beside the bird and resets the cooldown. [Water search][fishing-search] · [Fishing sequence][fishing-sequence] · [Item spawning][fishing-loot]

With the bundled tables and default luck, a completed roll selects **fish or junk in an 85:10 weight ratio**:

- **Fish:** Raw Cod, Raw Salmon, Tropical Fish or Pufferfish, weighted 60:25:2:13 within the fish table
- **Junk:** Lily Pad, damaged Leather Boots, Leather, Bone, Water Bottle, String, damaged Fishing Rod, Bowl, Stick, **10 Ink Sacs**, Tripwire Hook or Rotten Flesh

[Main fishing table][fishing-table] · [Fish contents][fish-loot] · [Junk contents][junk-loot] · [Weighted selection][weighted-selection]

**Treasure and Bamboo cannot pass this fishing route's bundled conditions.** The bird supplies luck but no fishing-hook entity or location in its loot context. Treasure requires an open-water fishing hook; Bamboo requires a jungle location. Increasing the bird's luck or moving its pool to a jungle does not supply those missing inputs. The raw loot call still evaluates ordinary fish and junk entries, so the missing inputs do not mean all fishing is disabled. [Fishing context][fishing-loot] · [Raw loot evaluation][raw-loot] · [Treasure condition][fishing-table] · [Entity condition][entity-condition] · [Missing-entity result][entity-predicate] · [Bamboo condition][junk-loot] · [Missing-location result][location-condition]

### Food, luck and lure limits

**No specific food works through the Shoebill's food tags in bundled data.** The implementation names `minecraft:shoebill_foodstuffs`, `minecraft:shoebill_luck_foods` and `minecraft:shoebill_lure_foods`, but no bundled item memberships are supplied. Do not assume a fish item, Crocodile Egg or any upstream food is accepted. A server data pack can fill these tags. [Tag keys][tags] · [Namespace][tag-namespace] · [Bundled data][data] · [Tag loading][tag-loading]

If a server supplies those memberships, the routes differ:

- **Foodstuffs alone:** holding a matching item attracts the bird. Dropping one nearby lets it eat one item and heal up to **5 health points (2½ hearts)**. Direct use is not ordinary feeding
- **Luck food:** direct use consumes one item and raises its saved luck level by one, up to **10**. This supplies half that level as loot luck; with the bundled tables, level 10 removes the junk weight and leaves fish, without enabling treasure
- **Lure food:** direct use consumes one item and raises lure by one, up to **10**. It also subtracts 200 ticks from the current cooldown, clamped to 200–2,400 ticks, so it cannot force an immediate catch. Future cooldowns subtract 120 ticks per lure level, with a 200-tick minimum
- **Dropped upgrade foods:** these can also raise the corresponding level and heal 5 points. Dropped lure food does **not** shorten the current cooldown. Direct upgrade feeding does **not** heal. At a direct-feed cap, the interaction succeeds without consuming food and may trigger a beak shake

The pickup goal requires an empty main hand and a loose item older than 10 game ticks, consumes one item, and does not store it in the bird's hand. Luck takes precedence if an item belongs to both upgrade tags. [Temptation][goals] · [Direct feeding, caps and cooldowns][feeding] · [Pickup conditions][pickup] · [Item consumption][pickup-consumption] · [Luck weights][weights]

Shoebills cannot be tamed or bred through ordinary food interactions. Their ordinary food test always returns false, the superclass therefore cannot enter love mode or accelerate baby growth, and their goals include no breeding goal. The offspring factory used by spawn eggs does not change those limits. [Food test][food-test] · [Superclass feeding][animal-feeding] · [Goals][goals] · [Offspring factory][feeding]

## Notes

No Shoebill death-loot table is supplied in bundled data. Its default `minecraft:entities/shoebill` lookup falls back to an empty table, so no species-specific meat, feather, material count or Looting bonus is established. The fishing catches above are a separate live behavior. Adults inherit a **1–3 base experience reward** under the normal recent-player-damage and `doMobLoot` conditions; babies do not drop that experience. [Default loot path][default-loot] · [Death loot dispatch][death] · [Loot loading][loot-loader] · [Missing-table fallback][loot-fallback] · [Bundled data][data] · [Animal experience][animal-experience] · [Experience conditions][experience] · [Baby exclusion][baby-experience]

Registered as `minecraft:shoebill`, with `MobCategory.CREATURE` and an adult size of **0.7 blocks wide × 1.0 block tall**. Its spawn egg is `minecraft:shoebill_spawn_egg`. This is bundled Alex's Mobs content integrated into MattMC; the behavior above is specific to this source snapshot. [Entity registration][entity] · [Egg registration][egg]

Related: [Shoebill Spawn Egg](../items/ShoebillSpawnEgg.md) · [Terrapin](Terrapin.md) · [Lead](../items/Lead.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active registration and attributes, spawn wiring, prey and player targeting, superclass interaction results, current attack and fall signatures, food tags, fishing context and loaded loot tables, offspring reachability and saved state. No in-game spawning, combat, flight, feeding, fishing, breeding or drop test was run. Server data packs can change tags and loot.

[basics]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L74-L79
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L230
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1179-L1181
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1948
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2079
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L101
[direct-spawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1774
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L284-L325
[biomes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/biome
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[config]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/config/AMConfig.java#L35-L36
[roll]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L71
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1110
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L185
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L134-L146
[fish-classes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal
[retaliation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L27-L68
[damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L98-L114
[flight]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/ShoebillAIFlightFlee.java#L25-L64
[melee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L126-L133
[shared-attack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[old-attack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L244-L248
[movement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L192-L210
[swimming]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AnimalAIWadeSwimming.java#L18-L27
[lead]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2178
[leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L606-L631
[saving]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L220-L235
[fall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L154-L159
[initial]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L60-L62
[countdown]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L178-L187
[fishing-sequence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/ShoebillAIFish.java#L45-L79
[fishing-search]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/ShoebillAIFish.java#L98-L154
[fishing-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/ShoebillAIFish.java#L82-L95
[fishing-table]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json
[fish-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[junk-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/fishing/junk.json
[weighted-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L62-L89
[raw-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/LootTable.java#L83-L99
[entity-condition]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemEntityPropertyCondition.java#L33-L36
[entity-predicate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/advancements/critereon/EntityPredicate.java#L93-L101
[location-condition]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/predicates/LocationCheck.java#L37-L44
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L285-L357
[food-test]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityShoebill.java#L94-L96
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L95
[pickup-consumption]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L142-L145
[weights]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L127-L131
[tags]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L81-L84
[tag-namespace]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L250-L252
[tag-loading]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L142-L168
[default-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[death]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1474
[loot-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L53-L71
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[animal-experience]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L128
[experience]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1486
[baby-experience]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L566
