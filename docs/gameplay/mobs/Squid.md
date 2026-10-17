# Squid

**Squid** supply [Ink Sacs](../items/InkSac.md) for Black Dye, Book and Quill, and removing glow from sign text. They swim without attacking players, and you can move them with a [Lead](../items/Lead.md). Ordinary Water Buckets do not capture them. Its ID is `minecraft:squid`. [Behavior and leash support][squid-core] · [Entity registration][entity-registration]

## Obtaining

Natural candidates occur in **all nine normal Overworld ocean variants**, plus **River and Frozen River**. Most of these tables request groups of **1–4**; Lukewarm Ocean requests **1–2**, and Warm Ocean requests **4**. See [Ocean biomes](../biomes/Oceans.md#ocean-families) for the ocean families. [Ocean][biome-ocean] · [Deep Ocean][biome-deep_ocean] · [Cold][biome-cold_ocean] · [Deep Cold][biome-deep_cold_ocean] · [Lukewarm][biome-lukewarm_ocean] · [Deep Lukewarm][biome-deep_lukewarm_ocean] · [Warm][biome-warm_ocean] · [Frozen][biome-frozen_ocean] · [Deep Frozen][biome-deep_frozen_ocean] · [River][biome-river] · [Frozen River][biome-frozen_river]

The registered natural-spawn predicate uses the **surface water band**, from sea level minus 13 through sea level, inclusive: **Y 50–63** with the bundled Overworld sea level of 63. It requires water at and below the spawn position and a Water block above. Collision, population and distance checks still apply. [Registration][placements] · [Water placement][in-water] · [Surface rule][ageable-water] · [Sea level][sea-level] · [Natural checks][natural-check]

The [Squid Spawn Egg](../items/SquidSpawnEgg.md) is an ordinary listed item. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Creative; placing an egg is a separate route from natural spawning. [Listing][egg-list-squid] · [Egg use][egg-use] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Behavior

Squid swim with alternating strokes and choose wandering directions. A recent attacker within **10 blocks** can activate fleeing while the Squid is in water. Accepted damage with an associated living attacker makes it squirt ink particles; those particles are not collectible Ink Sac items. [Movement][squid-movement] · [Ink response][squid-ink]

Keep it underwater during transport. Its air reserve resets to **300 ticks** in water; while stranded, the active air tick deals **2 health points after about 16 seconds**, then 2 points each second. These are active drying-damage rules in this revision. [Underwater breathing][breathing] · [Air handling][ageable-water] · [Damage threshold][air-threshold] · [Server dispatch][hurt-wrapper]

For an aquarium, use a renamed [Name Tag](../items/NameTag.md) if you want to prevent ordinary distance despawning. A lead moves the Squid but does not make dry ground safe. [Name Tag persistence][name-tag] · [Despawn checks][despawn] · [Leash support][squid-core] · [Player lead interaction][leash-use]

### Babies are not a breeding recipe

Squid can spawn as babies: after the first member of a spawn group, initialization uses a **5% baby chance** for later members. Babies mature over **24,000 ticks**, about 20 minutes. There is **no player feeding or mating route** in this implementation. Its offspring method is used by the matching spawn egg interaction; use a Squid Spawn Egg on a Squid to create a baby, separately from natural spawning. [Group initialization][squid-baby] · [Ageable initialization and breeding flag][ageable] · [Growth][growth] · [Interaction dispatch][mob-use] · [Egg offspring][egg-baby]

## Drops

An **adult** Squid drops **1–3 Ink Sacs** with mob loot enabled. Looting adds a randomized bonus from zero up to its level, giving maximum totals of **4, 5 or 6** with Looting I, II or III. The normal item table does not require a player kill, but **1–3 experience** requires eligible player credit. Babies give neither the ordinary death-table items nor death experience. [Ink table][loot-squid] · [Looting calculation][ink-bonus] · [Baby and mob-loot gates][baby-loot] · [Experience amount][ageable-water] · [Death processing][death-loot]

## Notes

- **Health:** 10 points, or 5 hearts. [Attribute wiring][attributes-squid] · [Health][squid-core]
- **Primordial Ocean also has Squid candidates.** The loaded Primordial Caves dimension includes that biome and takes precedence over the matching preset definition. Actual encounters still need the dimension-relative water band and other spawn checks; no occurrence rate was surveyed. See [Ocean biomes](../biomes/Oceans.md#integrated-ocean-content-is-separate). [Biome entry][biome-primordial_ocean] · [Loaded dimension][primordial-dimension] · [Dimension precedence][dimension-bake]
- The checked natural routes use loaded biome JSON and the active biome/structure chooser. [World loading][world-load] · [JSON loading][registry-load] · [Selection][biome-choice]

Related: [Glow Squid](GlowSquid.md) · [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked active biome selection and placement, movement, drying damage dispatch, baby initialization versus egg offspring, leash support, attributes and loot. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[squid-core]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L33-L101
[biome-ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[biome-deep_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_ocean.json
[biome-cold_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/cold_ocean.json
[biome-deep_cold_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_cold_ocean.json
[biome-lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/lukewarm_ocean.json
[biome-deep_lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_lukewarm_ocean.json
[biome-warm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json
[biome-frozen_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/frozen_ocean.json
[biome-deep_frozen_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_frozen_ocean.json
[biome-river]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/river.json
[biome-frozen_river]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/frozen_river.json
[placements]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L88-L105
[in-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L12-L20
[ageable-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AgeableWaterCreature.java#L16-L79
[sea-level]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[natural-check]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list-squid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2096-L2096
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[squid-movement]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L232-L312
[squid-ink]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L174-L203
[breathing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[hurt-wrapper]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1776
[name-tag]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L29
[despawn]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Mob.java#L597-L629
[squid-baby]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Squid.java#L223-L229
[ageable]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/AgeableMob.java#L31-L62
[growth]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L164
[mob-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1111
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L183
[loot-squid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/squid.json
[ink-bonus]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L78
[baby-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes-squid]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L255-L255
[biome-primordial_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[world-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[biome-choice]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[entity-registration]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1337-L1339

[leash-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/Entity.java#L2162-L2174

[primordial-dimension]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/dimension/primordial_caves.json#L1087-L1236
[dimension-bake]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183
