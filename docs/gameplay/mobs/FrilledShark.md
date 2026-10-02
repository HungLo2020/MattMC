# Frilled Shark

The **Frilled Shark** (`minecraft:frilled_shark`) is a water predator with **20 health points (10 hearts)**. It does not normally seek players as prey, but can retaliate when attacked. Keep it apart from fish and squid, and provide a tall water column if you want its normal, non-depressurized appearance. Its registered [Bucket of Frilled Shark](../items/BucketOfFrilledShark.md) has an active creature-insertion path. [Registration][registry] · [Applied attributes][attributes] · [Health][shark-stats] · [Targets][shark-goals] · [Bucket release][bucket-release]

## Obtaining

The [Frilled Shark Spawn Egg](../items/FrilledSharkSpawnEgg.md) and [Bucket of Frilled Shark](../items/BucketOfFrilledShark.md) are ordinary listed items. MattMC's [inventory browser](../mechanics/InventoryBrowser.md) can supply them in Survival as well as Creative. Prepare a water enclosure before placing the creature. The egg and bucket both have active creation-and-addition callers. [Item registrations][items] · [Egg listing][egg-list] · [Bucket listing][shark-bucket-list] · [Browser entries][browser-list] · [Client request][browser-client] · [Server handling][browser-server] · [Egg caller][egg-use] · [Entity insertion][entity-create] · [Bucket caller][bucket-release]

**No bundled natural encounter route was found in the checked source/data.** The active placement registry has no Frilled Shark entry, and the checked biome/structure selection, world-generation JSON and decompressed structure templates supply no named Frilled Shark spawn. Its standalone Water predicate is not wired into the placement registry; a method which moves a naturally spawned shark toward the bottom also does not create a natural spawn entry. There is no verified MattMC deep-ocean hunting location here. [Placement registry][spawn-placements] · [Natural selection][natural-selection] · [Standalone predicate][shark-spawn] · [Natural-spawn positioning][shark-natural-pose] · [Bundled data][bundled-data]

## Behavior

### Predation and combat

The shark selects **Squid, Mimic Octopus, schooling fish, Blobfish and Drowned** as prey. The class-based targets include **Glow Squid**, and Cod, Salmon and Tropical Fish among schooling fish. There is no normal player-target goal; attacking it can activate retaliation, without the group-alert option used by some other mobs. Keep these prey species in separate enclosures. [Target goals][shark-goals] · [Target adapter][prey-adapter] · [Active target selection][prey-search] · [Glow Squid type][glow-squid-type] · [Cod type][cod-type] · [Salmon type][salmon-type] · [Tropical Fish type][fish-type] · [Retaliation][retaliation]

The melee goal starts a **17-tick bite animation** when the target is closer than **1.9 blocks**. Its own caller invokes the species' one-argument attack method, so this is an active path. On animation tick **12**, the server applies the shark's **3-point base attack damage** (1½ hearts before the target's damage rules). That delayed hit does not repeat the initial distance check; simply stepping just beyond the bite-start distance is not a verified escape. Squid farther from the mouth but within the goal's 10-block branch are pulled toward it. [Melee caller and pull][shark-melee] · [Bite and damage][shark-attack] · [Animation clock][animation-clock] · [Base damage][shark-stats]

The attack has no implemented bleeding effect or shark-tooth production. Feeding fish items does not tame, heal or breed the shark: its species interaction handles bucket capture, with no food interaction or breeding system. [Attack result][shark-attack] · [Species interaction][shark-bucket] · [Class and goals][shark-class] · [Shared interaction][mob-interact]

### Water care and swimming

It needs Water, not access to surface air. The active water-animal handler resets its air to **300 ticks** in Water. From a full supply on land, it takes **2 damage points (1 heart)** after about **16 seconds**, then every second at the normal 20 ticks per second. Rain does not refill that supply. It cannot be attached to a Lead. [Air and Lead rules][water-air] · [Damage threshold][air-threshold]

Its goals include bottom swimming, ordinary swimming, a nearby-water search when stranded, and following a player who is moving a boat. The water search only selects a nearby destination; do not rely on it to rescue a shark from a dry enclosure. Idle underwater movement has a small downward drift. [Goals][shark-goals] · [Bottom swimming][swim-bottom] · [Nearby-water search][find-water] · [Boat following][follow-boat] · [Water travel][shark-travel]

### Pressure appearance and names

The shark checks **ten vertical block positions**, starting at the block containing its body and ending nine blocks above. Every checked position must contain Water-tagged fluid to keep the normal appearance. If any fails, it becomes depressurized; filling the column restores the normal state on a later tick. This is a water-column check, not a fixed world-height or biome rule. The checked pressure branch changes its appearance flag, without adding pressure damage or changing attack attributes. [Clearance test][shark-pressure] · [Tick updates][shark-attack] · [Texture selection][shark-render]

A name containing **`kamata kun`** or **`kamata-kun`**, ignoring case, selects its alternate named texture, with a corresponding depressurized form. This naming check does not give it new combat powers. [Name test][shark-name] · [Renderer][shark-render]

## Capture and persistence

A **Water Bucket** captures a living shark and removes the original. The resulting `minecraft:frilled_shark_bucket` uses the standard mob-bucket class, which creates the shark, loads bucket data, marks it as from a bucket and adds it to the server level. The [bucket guide](../items/BucketOfFrilledShark.md#behavior) explains the saved-data limitation and placement conditions. [Capture interaction and item][shark-bucket] · [Shared pickup][bucket-capture] · [Release order][bucket-release]

A bucket-released shark is protected from ordinary distance despawning; a custom name also makes its distance-removal check return false. These flags use the current save/load signatures and are consulted by the active despawn caller. Retention does not make it tame or protect it from damage or suffocation. [Persistence and world saves][shark-persistence] · [Despawn caller][mob-despawn]

## Drops

The default `minecraft:entities/frilled_shark` loot table is absent from the bundled data. There is no configured species item drop, no shark-tooth production in its attack, and no checked recipe using a Frilled Shark resource. A qualifying player-credited kill can give **1–3 base XP** under the ordinary `doMobLoot` gate and experience modifiers. [Default loot naming][loot-default] · [Bundled entity loot][loot-directory] · [Missing-table fallback][loot-fallback] · [Attack result][shark-attack] · [Base XP][water-xp] · [Death/XP gate][death-gate] · [Recipe loader][recipe-loader]

## Notes

The entity is registered in `MobCategory.WATER_CREATURE`, with base dimensions **0.6 × 0.6 blocks**, and an active renderer. These are registration values, not a tested minimum aquarium design. [Registration][registry] · [Renderer binding][render-binding]

Related: [Bucket of Frilled Shark](../items/BucketOfFrilledShark.md) · [Frilled Shark Spawn Egg](../items/FrilledSharkSpawnEgg.md) · [Comb Jelly](CombJelly.md) · [Mobs](Mobs.md)

Source-reviewed at `4ae465c9594b9a6507f67b4fedf81cdbafa7a490` on 2026-10-02. Registration, actual AI/attack callers, spawn selection, breathing, current save/load and bucket components, recipes and loot fallback were traced. Bundled world-generation JSON and decompressed structures were scanned; optional nested packs were considered separately. No gameplay, combat, pressure, capture/release or multiplayer test was run.

[air-threshold]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[animation-clock]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/citadel/animation/AnimationHandler.java#L41-L64
[attributes]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L141-L143
[browser-client]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L51
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/resources/data
[cod-type]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/Cod.java#L11
[death-gate]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
[egg-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1996-L1998
[egg-use]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L127
[entity-create]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776
[find-water]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/ai/goal/TryFindWaterGoal.java#L15-L40
[fish-type]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java#L47
[follow-boat]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/ai/goal/FollowBoatGoal.java#L26-L94
[glow-squid-type]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/GlowSquid.java#L22
[items]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/Items.java#L1825-L1842
[loot-default]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-directory]: https://github.com/HungLo2020/MattMC/tree/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[mob-despawn]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/Mob.java#L598-L628
[mob-interact]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[natural-selection]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[prey-adapter]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/ai/EntityAINearestTarget3D.java#L20-L26
[prey-search]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L75
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L83
[registry]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L387-L407
[render-binding]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L122-L124
[retaliation]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L27-L70
[salmon-type]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/Salmon.java#L33
[shark-attack]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L240-L275
[shark-bucket]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L180-L217
[shark-bucket-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1434-L1445
[shark-class]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L55-L102
[shark-goals]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L81-L94
[shark-melee]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L327-L361
[shark-name]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L307-L310
[shark-natural-pose]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L140-L158
[shark-persistence]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L120-L138
[shark-pressure]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L285-L295
[shark-render]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/client/render/RenderFrilledShark.java#L14-L47
[shark-spawn]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L96-L102
[shark-stats]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L70-L72
[shark-travel]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L219-L231
[spawn-placements]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L55-L178
[swim-bottom]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/ai/AnimalAISwimBottom.java#L19-L40
[water-air]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L39-L68
[water-xp]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L34-L37
