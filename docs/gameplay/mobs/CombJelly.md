# Comb Jelly

The **Comb Jelly** (`minecraft:comb_jelly`) is a small, passive water creature with blue, green or red variants and **6 health points (3 hearts)**. It can be kept in a water enclosure, but **capturing it is currently unsafe for relocation**: its bucket removes the animal and the release callback never inserts a replacement into the world. Read the existing [Bucket of Comb Jelly warning](../items/BucketOfCombJelly.md) before using a Water Bucket. [Registration][registry] · [Applied attributes][attributes] · [Health][jelly-stats] · [Textures][jelly-render] · [Release callback][jelly-release]

## Obtaining

### Spawn-egg access

Use the registered [Comb Jelly Spawn Egg](../items/CombJellySpawnEgg.md) in a prepared water enclosure. It is an ordinary listed egg, available through MattMC's [inventory browser](../mechanics/InventoryBrowser.md) in Creative. The egg's water-use path creates and adds the creature; this is separate from the broken bucket release. [Item registration][items] · [Listed egg][egg-list] · [Browser entries][browser-list] · [Client request][browser-client] · [Server handling][browser-server] · [Egg use][egg-use] · [Creation and addition][entity-create]

### Natural-spawn limit

**No bundled natural encounter route was found in the checked source and data.** There is no Comb Jelly entry in the active spawn-placement registrations or named biome/structure spawn selection, and the checked world-generation JSON and decompressed structure templates contain no Comb Jelly reference. Do not use a claimed ocean, depth or nighttime spawning location from another version as a MattMC collection route. [Placement registry][spawn-placements] · [Natural selection caller][natural-selection] · [Bundled data][bundled-data]

The class contains a predicate requiring Water at the spawn position and above, raw brightness at most **4**, and time-of-day fraction **greater than 0.27 and at most 0.8**, except for spawners. That predicate has no registered caller here. A separate instance check uses the configured spawn-roll helper, but neither check supplies a missing biome spawn entry. These are implementation limits, not instructions for a working spawn farm. [Unused position predicate][jelly-rules] · [Configured rolls][jelly-config] · [Roll helper][jelly-roll] · [Natural validation][natural-validation]

## Behavior

### Water care and movement

Keep it in Water. Its active water-animal air handler restores air to **300 ticks** while in Water and counts it down on land; from a full supply, it takes **2 damage points (1 heart)** after about **16 seconds**, then every second at the normal 20 ticks per second. Rain is not an exception in this handler. It cannot be led with a Lead. [Air and Lead rules][water-air] · [Damage threshold][air-threshold]

Comb Jellies choose nearby submerged destinations and drift toward them. They sink away from the surface when not fully submerged, lose their no-gravity state outside Water, and have no registered attack or retaliation goal. There is no active feeding, taming or breeding interaction; the species interaction handles bucket capture and otherwise falls through to the ordinary mob interaction. [Swimming][jelly-swim] · [Class and inherited empty goals][jelly-class] · [Default goals][mob-goals] · [Species interaction][jelly-capture] · [Shared interaction][mob-interact]

### Variants and keeping a specimen

Spawn initialization chooses one of **three color variants** and a visual scale of roughly **0.8–1.2**. Color and scale are saved by the current world-save callbacks. The renderer applies that scale, while the registered base dimensions remain **0.75 × 0.75 blocks**. [Initialization][jelly-variants] · [World save/load][jelly-save] · [Renderer][jelly-render] · [Dimensions][registry]

A custom-named jelly is protected from ordinary distance despawning. A `FromBucket` flag would also provide that protection, but the current bucket release cannot establish a live returned creature. Use naming to retain a specimen already in its enclosure; do not capture one merely to try to make it persistent. [Persistence signatures][jelly-rules] · [Active despawn caller][mob-despawn] · [Bucket release limitation][jelly-release]

## Bucket capture

Use a **Water Bucket**, not an empty Bucket, on a living jelly to capture it. Capture produces `minecraft:comb_jelly_bucket` and discards the original creature. The bucket owner documents the incomplete release and the issue tracking it, [#801](https://github.com/HungLo2020/MattMC/issues/801); placing water and consuming the bucket does not prove a creature was returned. [Capture interaction][jelly-capture] · [Shared capture][bucket-capture] · [Item selection][jelly-selection] · [Registry alias][jelly-alias] · [Bucket guide](../items/BucketOfCombJelly.md#usage)

## Drops

The default `minecraft:entities/comb_jelly` loot table is absent from the bundled data, and the class has no separate item-production or custom death-drop path. There is no configured species item drop or checked recipe using a Comb Jelly resource. A qualifying player-credited kill can give **1–3 base XP**, subject to the ordinary `doMobLoot` gate and experience modifiers. [Default loot naming][loot-default] · [Bundled loot directory][loot-directory] · [Missing-table fallback][loot-fallback] · [Water-animal base XP][water-xp] · [Death/XP gate][death-gate] · [Recipe loading][recipe-loader]

## Notes

The entity is registered in `MobCategory.WATER_AMBIENT` and has an active renderer. It is bundled Alex's Mobs content; the natural-spawn and bucket limits above describe this MattMC integration. [Registration][registry] · [Renderer binding][render-binding]

Related: [Bucket of Comb Jelly](../items/BucketOfCombJelly.md) · [Comb Jelly Spawn Egg](../items/CombJellySpawnEgg.md) · [Frilled Shark](FrilledShark.md) · [Mobs](Mobs.md)

Source-reviewed at `4ae465c9594b9a6507f67b4fedf81cdbafa7a490` on 2026-10-02. Active registration, callers, world-save signatures, all tracked main-source references, bundled recipes/loot, world-generation JSON and decompressed structure templates were checked. Optional nested packs were scanned separately without assuming they are enabled. No gameplay, capture/release, aquarium or multiplayer test was run.

[air-threshold]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[attributes]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L141-L143
[browser-client]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/resources/data
[death-gate]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
[egg-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1996-L1998
[egg-use]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L127
[entity-create]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776
[items]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/Items.java#L1825-L1842
[jelly-alias]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L34
[jelly-capture]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L245-L275
[jelly-class]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L41-L82
[jelly-config]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/config/AMConfig.java#L11-L12
[jelly-release]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/item/ItemModFishBucket.java#L39-L70
[jelly-render]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/client/render/RenderCombJelly.java#L11-L46
[jelly-roll]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L71
[jelly-rules]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L58-L82
[jelly-save]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L211-L223
[jelly-selection]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L145-L152
[jelly-stats]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L141-L143
[jelly-swim]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L155-L209
[jelly-variants]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L277-L282
[loot-default]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-directory]: https://github.com/HungLo2020/MattMC/tree/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[mob-despawn]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/Mob.java#L598-L628
[mob-goals]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/Mob.java#L150-L157
[mob-interact]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[natural-selection]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[natural-validation]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L251-L287
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L83
[registry]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L387-L407
[render-binding]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L122-L124
[spawn-placements]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L55-L178
[water-air]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L39-L68
[water-xp]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L34-L37
