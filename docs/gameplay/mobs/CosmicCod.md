# Cosmic Cod

The **Cosmic Cod** is a small, passive fish that flies through the air, groups with other Cosmic Cod, and attempts to teleport away after being hurt. It has **4 health points (2 hearts)**. Keep it out of **water and rain**: its active water-sensitivity callback makes both harmful. [Flight setup and health][setup] · [Attribute wiring][attributes] · [Water sensitivity][sensitive] · [Active water damage][water-damage]

## Obtaining

The [Cosmic Cod Spawn Egg](../items/CosmicCodSpawnEgg.md) and [Bucket of Cosmic Cod](../items/BucketOfCosmicCod.md) are ordinary category-listed items available through the [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. An egg used in dry space avoids the bucket's water-placement conflict. [Registered items][items] · [Egg category][egg-category] · [Bucket category][bucket-category]

No natural spawning route was found in the checked biome lists, structure spawn overrides or other active spawning references. Bundled End biome lists and their builder do not add Cosmic Cod. Its permissive spawn check and natural-spawn positioning method only govern an attempted spawn; they do not arrange one. [Spawn-list selection][spawn-selection] · [Bundled End list][end-spawns] · [End builder][end-builder] · [Spawn callback][spawn-callback]

## Behavior

### Airborne schools

Cosmic Cod have no attack goals. They use an airborne movement controller, ignore gravity, and are not pushed by water. Nearby cod can choose a group leader and follow it; the group-building helper caps a school at **15 members**. Group leaders can start a circling pattern that nearby followers join. These social goals work independently of a natural-spawn route. [Goals][setup] · [Gravity and currents][gravity] · [School formation][school-goal] · [Group cap][school-size] · [Circling][circling]

Cosmaws actively hunt Cosmic Cod. Keeping the two together is therefore a poor way to preserve a collection. [Cosmaw target goal][predator]

### Escape after damage

When its hurt animation begins, a server-side countdown schedules a teleport attempt. It chooses a random destination within roughly 32 blocks on each axis and accepts it only when the destination block is air without water. If that attempt succeeds, other Cosmic Cod in the surrounding **16 × 16 × 16-block** search box attempt to teleport to that same destination. This is not guaranteed escape: an invalid destination fails the attempt, and damage may kill this low-health mob first. [Damage countdown and nearby cod][teleport-tick] · [Destination check][teleport]

### Catching and keeping one

Use an **empty Bucket** on a living Cosmic Cod. Its own interaction creates a Bucket of Cosmic Cod and removes the captured entity. A Water Bucket is not the item checked by this route. [Capture interaction][capture]

The registered filled item currently follows the standard water-bucket release path, even though Cosmic Cod take water damage. Read [Bucket of Cosmic Cod](../items/BucketOfCosmicCod.md#behavior) before releasing one; a safe dry release has not been verified. [Bucket registration][items] · [Release path][release] · [Water damage][water-damage]

Released cod are marked as coming from a bucket, which prevents ordinary distance-based despawning. A custom name also prevents that despawning. This persistence does not protect against water, predators or other damage. No feeding, taming or breeding interaction is installed for this Mob. [Persistence][persistence] · [Release marker][release-marker] · [Goals][setup] · [Interaction][capture]

## Drops

No bundled `entities/cosmic_cod` loot table or species-specific item drop was found. There is also no registered loose `minecraft:cosmic_cod` item in the checked registry. Do not expect killing one to supply [Cosmaw](Cosmaw.md#feeding-and-taming-missing-item-dependency) taming food. The default entity loot lookup falls back to an empty table when its resource is absent. [Registered related items][items] · [Default loot key][loot-key] · [Missing-table fallback][loot-fallback]

## Notes

- Registered as `minecraft:cosmic_cod`, category `WATER_AMBIENT`, with a **0.5 × 0.3-block** registered size. This category does not override its airborne movement or water sensitivity. [Entity factory][entity]
- Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. Spawn/drop/item-absence findings cover the checked bundle. No game, schooling, damage, teleport, capture, release or persistence test was run.

Related: [Bucket of Cosmic Cod](../items/BucketOfCosmicCod.md) · [Cosmic Cod Spawn Egg](../items/CosmicCodSpawnEgg.md) · [Cosmaw](Cosmaw.md) · [Spectre](Spectre.md) · [Mobs](Mobs.md)

[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[end-spawns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json#L35-L51
[end-builder]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/data/worldgen/biome/EndBiomes.java#L15-L35
[loot-key]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[setup]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L64-L91
[attributes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L142
[sensitive]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L166-L168
[water-damage]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2865-L2867
[items]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1831-L1836
[egg-category]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1997
[bucket-category]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1441
[spawn-callback]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L388-L399
[gravity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L252-L258
[school-goal]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/ai/CosmicCodAIFollowLeader.java#L25-L64
[school-size]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L342-L385
[circling]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L446-L491
[predator]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L126-L136
[teleport-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L190-L234
[teleport]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L292-L317
[capture]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L405-L425
[release]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/BucketItem.java#L73-L92
[persistence]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L148-L154
[release-marker]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/MobBucketItem.java#L41-L50
[entity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L394-L400
