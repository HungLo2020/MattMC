# Mungus

The **Mungus** is a passive mushroom-carrying animal with **15 health points (7½ hearts)** and a base movement-speed attribute of **0.25**. It normally carries up to five mushrooms or fungi, uses them to grow matching plants, and periodically drops Brown Mushrooms. Natural spawning, breeding food and biome conversion are incomplete in this MattMC snapshot. [Attribute registration][attributes] · [Health and speed][spawn-rules] · [Mushroom behavior][mungus]

## Obtaining

Use the [Mungus Spawn Egg](../items/MungusSpawnEgg.md) from Creative for deliberate placement. Both the entity factory and the egg are registered, and the egg has an ordinary Spawn Eggs category entry. Using the matching egg on an existing Mungus creates a baby through its offspring callback. [Registration][entity] · [Egg item][egg] · [Creative category][creative] · [Baby creation][baby] · [Spawn-egg interaction][egg-baby]

No Mungus entry was found in the checked biome or structure spawn lists, and no spawn-placement registration calls its `canMungusSpawn` helper. Natural spawning selects from those lists; the helper's solid-ground check and the mob's separate one-in-ten spawn-rule roll do **not** establish a natural encounter location. In particular, Mushroom Fields are not a verified source of wild Munguses. [Natural selection][natural] · [Biome and structure consumers][spawn-lists] · [Placement registrations][placements] · [Bundled data][data] · [Local spawn rules][spawn-rules]

Ordinary block placement with the egg runs spawn initialization: the Mungus starts with **zero or one** stored mushroom and a randomly selected Brown or Red Mushroom type. An egg-created baby uses the simpler offspring factory, so it starts with no stored mushrooms or selected type. [Spawn initialization][initialization] · [Egg placement factory][egg-placement] · [Baby creation][baby]

## Behavior

The Mungus has wandering, swimming, panic, parent-following and mushroom-seeking goals, but no melee or ranged attack goal. Accepted damage clears its growth target and starts a **1,200-tick** growth cooldown, about **60 seconds while ticking at normal speed**. [Goals][goals] · [Damage response][damage]

Being passive does not prevent it from alerting a nearby [Bunfungus](Bunfungus.md) when a living attacker hurts it. The alert skips Bunfungus attackers and requires an eligible Bunfungus with no existing target. Against a **player**, the defender additionally needs its special carroted state; the bundled food tag for creating that state is absent, so an ordinary Mungus is not an established way to make Bunfunguses attack players. [Alert goal][alert] · [Defender condition][defender] · [Bunfungus food and state][bunfungus] · [Bundled data][data]

### Carrying mushrooms and luring

Use one of these four items on a Mungus, or drop it nearby for the Mungus to collect:

- [Brown Mushroom](../items/BrownMushroom.md)
- [Red Mushroom](../items/RedMushroom.md)
- [Warped Fungus](../items/WarpedFungus.md)
- [Crimson Fungus](../items/CrimsonFungus.md)

Each accepted item adds **one** stored mushroom, with a normal loading limit of **five**. At zero, any of the four types can be selected; above zero, only the already stored type is accepted. Hand-feeding spends one item in Survival and preserves it in Creative. Dropped-item collection consumes one from the ground stack, including items thrown by a Creative player. These interactions add growth charges; they do not heal the Mungus. [Accepted types][types] · [Hand interaction][feeding] · [Ground-item acceptance][ground-food] · [Ground-item consumption][pickup] · [Player-item consumption][consumption]

Hold Brown or Red Mushrooms to attract a Mungus even if it carries another type or is already full. Held Warped or Crimson Fungus attracts it only when its stored type matches, or its count is zero. The lure searches for a nearby player within **10 blocks** and checks either hand. Attraction alone does not mean the held item can be added. [Held-item lure][lure]

### Growing matching plants

A Mungus with at least one stored mushroom searches for a placed plant of the **same type**. It approaches an obstructed target and beams at it when its line-of-sight test passes. Keep a matching small mushroom or fungus nearby and leave room for growth. [Target selection][targets]

After the beam accumulates **more than 200 ticks**, roughly **10 seconds at normal speed**, it attempts the target block's Bone Meal growth action. Brown and Red Mushrooms use their huge-mushroom feature; Warped and Crimson Fungi require their corresponding Nylium for the huge-fungus attempt. This path checks whether the target is valid, then calls growth directly without the usual Bone Meal item's 40% success roll. Feature placement can still fail because of ground or clearance. [Beam action][beam] · [Mushroom growth][mushroom-growth] · [Fungus growth][fungus-growth] · [Fungus block registrations][fungus-blocks]

If the target does not change, the fallback makes **15 placement attempts** near it, trying to place up to **2–4** copies in air above occluding blocks. Placement attempts can fail, and newly placed plants remain subject to their ordinary survival rules. The completed cycle removes **one stored mushroom if any remain** and starts the roughly **60-second cooldown**, even when growth fails. [Beam and fallback][beam]

### Breeding

Mushroom loading is separate from breeding. The inherited animal interaction checks the `minecraft:mungus_breedables` item tag, which is **not supplied in the checked bundled data**. No ordinary hand-fed breeding food or food-based baby growth acceleration is therefore established here. A data pack that supplies that tag can enable the existing love-mode and offspring paths; the matching spawn egg already supplies a baby-creation route. [Breeding test][breeding] · [Animal food interaction][animal-food] · [Tag loading][tags] · [Tag membership][membership] · [Bundled data][data]

### Brown Mushroom production and shearing

Every living **adult** has a timer of **24,000–47,999 ticks**, approximately **20–40 minutes while ticking at normal speed**. When it expires, the adult drops **one Brown Mushroom** and resets the timer. It does not need a mate or stored mushrooms, and the drop stays Brown even if it carries another type. The timer is saved with the mob. [Production][production] · [Saved timer][save]

A [Dispenser](../blocks/DispenserAndDropper.md) using [Shears](../items/Shears.md) can remove **one stored mushroom per successful use** from a living Mungus in front of it. It drops **no mushroom item**. Removing the last stored mushroom also clears the type and beam target and starts the growth cooldown. Leash removal can take precedence over shearing, so another activation may be needed for a leashed Mungus. [Shearing callback][shearing] · [Registered Dispenser behavior][dispenser-registration] · [Dispenser target handling][dispenser-shears]

Ordinary handheld Shears do **not** invoke the Mungus's mushroom-shearing callback in this snapshot: neither its mob interaction nor the Shears item implements that route. Handheld Shears can still perform the shared leash/equipment interactions. [Hand interaction][feeding] · [Shears item][hand-shears] · [Shared entity interaction][entity-interaction]

### Death burst and biome limits

An adult dying with **at least five** stored mushrooms triggers its special burst at death tick **19**, unless its explosion-disabled flag is set. The routine plays an explosion sound and can place its stored mushroom/fungus type on nearby solid surfaces. This is a local plant-placement effect: the routine does not call the ordinary damaging explosion system. Its randomized placement can replace a non-occluding plant above a solid block and has no `mobGriefing` check. [Death trigger][death] · [Burst placement][burst]

The larger conversion is **incomplete**. Intended mappings pair Brown/Red Mushrooms with Mycelium and Mushroom Fields, Warped Fungus with Warped Nylium and Warped Forest, and Crimson Fungus with Crimson Nylium and Crimson Forest. However, both ground-replacement tags, `minecraft:mungus_replace_mushroom` and `minecraft:mungus_replace_nether`, are missing from bundled data, and the chunk-biome setter contains no active write. Supplying the tags could enable ground replacement, but would not repair the biome setter. Do not use Munguses expecting a working biome converter. [Mappings][types] · [Replacement checks][burst] · [Inactive biome write][biome-write] · [Block tag membership][block-membership] · [Bundled data][data]

Using a [Poisonous Potato](../items/PoisonousPotato.md) on an **adult** consumes it outside Creative and starts the reverting swell, reaching the burst after about **20 ticks**. That branch skips local ground/plant placement and reaches the same inactive biome setter, so it does **not restore a biome** in this snapshot. It does not remove the stored mushrooms. [Potato interaction][feeding] · [Reverting timer][reverting] · [Reverting branch][burst] · [Biome setter][biome-write]

## Notes

- Registered as `minecraft:mungus`, with spawn egg `minecraft:mungus_spawn_egg`, category `MobCategory.CREATURE`, entity class `EntityMungus` and normal adult size **0.75 × 1.45 blocks**. This is bundled Alex's Mobs content integrated into MattMC. [Entity registration][entity] · [Egg registration][egg]
- The normal death-loot key is `minecraft:entities/mungus`, but no corresponding bundled loot table or custom Mungus death-item callback was found. A missing table resolves to empty loot. Periodic Brown Mushroom production is the established item-output route; stored mushrooms are not automatically returned on death. [Default loot key][loot-key] · [Death-loot dispatch][death-loot] · [Missing-table fallback][loot-fallback] · [Bundled data][data]
- The source contains a five-Warped-Fungus readiness helper and an explosion-disable helper, but no active callers were found. They do not establish a Crimson Mosquito/Warped Mosco transformation route in MattMC. [Uncalled helpers][mungus]

Related: [Mushrooms](../blocks/Mushrooms.md) · [Bone Meal](../items/BoneMeal.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked the entity/item factories, default attributes, server tick and AI dispatch, player interaction and damage dispatch, spawn-list consumers, tag loading, growth callbacks, Dispenser shearing and loot fallback. The ordinary server paths reach the Mungus callbacks described above. No in-game spawning, feeding, growth, shearing, production, death-burst or rendering test was run. Server data packs can change the missing spawn, food, replacement-tag and loot data; they do not complete the inactive Java biome setter. [Server tick][server-tick] · [Entity tick][entity-tick] · [AI dispatch][ai-dispatch] · [Packet interaction dispatch][packet] · [Player interaction][player-interaction] · [Mob interaction][mob-interaction]

[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L202
[mungus]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1280-L1286
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1917
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2051
[baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L602-L607
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L186
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[spawn-lists]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L108-L154
[initialization]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L557-L563
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1737-L1780
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L156-L168
[damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L406-L414
[alert]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/MungusAIAlertBunfungus.java#L31-L101
[defender]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L421-L427
[bunfungus]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java
[types]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L88-L138
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L369-L404
[ground-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L617-L636
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L130-L146
[consumption]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1122
[lure]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/MungusAITemptMushroom.java#L12-L74
[targets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L691-L771
[beam]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L469-L550
[mushroom-growth]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L91-L119
[fungus-growth]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L61-L80
[fungus-blocks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L5475-L5534
[breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L553-L555
[animal-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L131-L157
[tags]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L142-L168
[membership]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/Holder.java#L166-L177
[production]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L170-L180
[save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L429-L467
[shearing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L643-L688
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[dispenser-shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L21-L67
[hand-shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ShearsItem.java
[entity-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2108-L2182
[death]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L210-L223
[burst]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L225-L291
[biome-write]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L310-L367
[block-membership]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L848-L850
[reverting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L184-L208
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[death-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[server-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/MinecraftServer.java#L1225-L1244
[entity-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerLevel.java#L396-L416
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L634-L673
[packet]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1764
[player-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L889
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1048-L1111
