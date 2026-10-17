# Glow Squid

**Glow Squid** are the source of [Glow Ink Sacs](../items/GlowInkSac.md), used for glowing sign text and Glow Item Frames. Search dark water underground, rather than the near-surface band used by ordinary Squid. Its ID is `minecraft:glow_squid`. [Spawn predicate][GlowSquid] · [Loot][loot-glow_squid] · [Entity registration][entity-registration]

## Obtaining

Loaded biome tables include Glow Squid in the underground-water category beneath many Overworld biomes, including **Plains, Ocean, Lush Caves and Dripstone Caves**. These entries request groups of **4–6**, but the normal spawn routine stops after **4 successful mobs in that cluster call**, using the inherited Squid limit. Do not read the table maximum as a promise of six together. [Cluster limit][cluster-limit] · [Active stopping check][cluster-call] They are not exclusive to an ocean biome, and merely being in a cave does not satisfy all conditions. **Deep Dark's bundled spawn list is empty**, so it is not an equivalent biome candidate. [Plains][biome-plains] · [Ocean][biome-ocean] · [Lush Caves][biome-lush_caves] · [Dripstone Caves][biome-dripstone_caves] · [Deep Dark][biome-deep_dark]

The active spawn predicate requires a **Water block**, **raw brightness 0**, and a height at or below **sea level minus 33**: **Y 30 or lower** with the bundled Overworld sea level of 63. In-water placement additionally requires room above that is not a redstone-conducting block; collision, population and distance checks also remain. No additional stone-floor requirement appears in this registered predicate. [Registration][glow-placement] · [Predicate][GlowSquid] · [In-water placement][in-water] · [Sea level][sea-level] · [Natural checks][natural-check]

The [Glow Squid Spawn Egg](../items/GlowSquidSpawnEgg.md) is an ordinary listed item. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Creative; placing an egg is a separate route from natural spawning. [Listing][egg-list-glow] · [Egg use][egg-use] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Behavior

Glow Squid inherit Squid wandering and fleeing behavior. A recent living attacker can make one squirt glowing ink particles and flee in water. Those particles are visual effects, not dropped sacs. [Inherited behavior][GlowSquid] · [Movement][squid-movement] · [Ink trigger][squid-ink]

An accepted attacker hit starts a **100-tick dark timer**, about five seconds. The renderer suppresses its usual bright appearance, then ramps it back up during the final ten ticks. The checked renderer sets the light used to draw the Squid itself; this does not establish usable block illumination around it. [Dark timer and particles][GlowSquid] · [Renderer wiring][glow-render-wire] · [Rendering brightness][glow-visual]

Keep it underwater during transport. Its air reserve resets to **300 ticks** in water; while stranded, the active air tick deals **2 health points after about 16 seconds**, then 2 points each second. These are active drying-damage rules in this revision. [Underwater breathing][breathing] · [Air handling][ageable-water] · [Damage threshold][air-threshold] · [Server dispatch][hurt-wrapper]

You can use a [Lead](../items/Lead.md), but a Water Bucket does not capture a Glow Squid. Use a renamed [Name Tag](../items/NameTag.md) for an aquarium animal you intend to retain through ordinary distance-despawn checks. [Inherited leash support][squid-core] · [Player lead interaction][leash-use] · [Class inheritance][GlowSquid] · [Name Tag][name-tag] · [Despawn][despawn]

### Babies and spawn eggs

Glow Squid inherit the Squid group rule: after the first group member, later members have a **5% baby chance**. A baby normally matures in **24,000 ticks**, about 20 minutes. There is no feeding or mating interaction. Using the matching Glow Squid Spawn Egg on an existing Glow Squid can create a baby through the egg-specific offspring path. [Group rule][squid-baby] · [Age initialization][ageable] · [Growth][growth] · [Glow offspring][GlowSquid] · [Interaction dispatch][mob-use] · [Egg path][egg-baby]

## Drops

An **adult** drops **1–3 Glow Ink Sacs** with mob loot enabled. Looting adds a randomized zero-to-level bonus, for maximum totals of **4, 5 or 6** with Looting I, II or III. The item table does not require a player kill; **1–3 experience** does require eligible player credit. Babies do not supply these death-table items or experience. [Loot][loot-glow_squid] · [Looting calculation][ink-bonus] · [Baby gates][baby-loot] · [Experience amount][ageable-water] · [Death processing][death-loot]

## Notes

- **Health:** 10 points, or 5 hearts. [Attribute wiring][attributes-glow] · [Inherited value][squid-core]
- Use [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) for text-face, waxing and ink-application rules
- Natural spawning is based on loaded biome candidates, then the registered dark-water predicate. [World loading][world-load] · [JSON loading][registry-load] · [Biome selection][biome-choice] · [Predicate dispatch][natural-check]

Related: [Squid](Squid.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked loaded biome examples and Deep Dark exclusion, registered predicate, inherited care and baby behavior, damage response, renderer wiring and loot. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[GlowSquid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/GlowSquid.java
[loot-glow_squid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/glow_squid.json
[biome-plains]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/plains.json
[biome-ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[biome-lush_caves]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[biome-dripstone_caves]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/dripstone_caves.json
[biome-deep_dark]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json
[glow-placement]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L124-L124
[in-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L12-L20
[sea-level]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[natural-check]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list-glow]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2024-L2024
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[squid-movement]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L232-L312
[squid-ink]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L174-L203
[glow-render-wire]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L171-L175
[glow-visual]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/renderer/entity/GlowSquidRenderer.java#L13-L28
[breathing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[ageable-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AgeableWaterCreature.java#L16-L79
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[hurt-wrapper]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1776
[squid-core]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L33-L101
[name-tag]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L29
[despawn]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Mob.java#L597-L629
[squid-baby]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L223-L229
[ageable]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/AgeableMob.java#L31-L62
[growth]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L164
[mob-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1111
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L183
[ink-bonus]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L78
[baby-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes-glow]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L174-L174
[world-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[biome-choice]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[entity-registration]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L694-L696

[cluster-limit]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Mob.java#L743-L748

[cluster-call]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L216

[leash-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Entity.java#L2162-L2174
