# Salmon

**Salmon** are schooling fish found in rivers and cold or frozen oceans. Capture them with a Water Bucket to keep their **small, medium or large appearance**, or collect [Raw Salmon](../items/RawSalmon.md) for food. Its ID is `minecraft:salmon`. [Salmon][Salmon] · [Capture][bucket-capture] · [Entity registration][entity-registration]

## Obtaining

The loaded biome lists include Salmon groups of **1–5** in **River, Frozen River, Cold Ocean, Deep Cold Ocean, Frozen Ocean and Deep Frozen Ocean**. Ordinary, warm and lukewarm oceans are not in this natural Salmon list. [River][biome-river] · [Frozen River][biome-frozen_river] · [Cold][biome-cold_ocean] · [Deep Cold][biome-deep_cold_ocean] · [Frozen][biome-frozen_ocean] · [Deep Frozen][biome-deep_frozen_ocean]

The registered natural-spawn checks require water at the spawn position and below it, a Water block above, and a height from **sea level minus 13 through sea level**, inclusive. With the bundled Overworld sea level of 63, that is **Y 50–63**. The usual population, distance and collision checks still apply. [Placement registration][placements] · [Water placement][in-water] · [Surface rule][water] · [Sea level][sea-level] · [Natural checks][natural-check]

The [Salmon Spawn Egg](../items/SalmonSpawnEgg.md) is an ordinary listed item. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Survival as well as Creative; placing an egg is a separate route from natural spawning. [Listing][egg-list-salmon] · [Egg use][egg-use] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Behavior

Salmon avoid nearby players and form schools of up to **5**. Schools are not families: the checked implementation has no feeding or breeding interaction. [School limit][Salmon] · [Fish goals][fish] · [Schooling][school] · [Leader goal][school-goal]

### Three sizes

Spawn initialization chooses **small, medium and large** with relative weights **30:50:15**. These add to 95, so they are not percentages. Their body-size scales are **0.5, 1 and 1.5** respectively. A small Salmon is a size variant, not an ageable baby; the size does not change its checked health or loot count. [Variants and selection][salmon-variants] · [Health][fish] · [Loot][loot-salmon]

A [Bucket of Salmon](../items/BucketOfSalmon.md) preserves the captured size, health and custom name. On release, spawn initialization runs first, then the saved size component is applied, so the random initialization does not replace a captured Salmon's size. Released fish also gain protection from ordinary distance despawning. [Saved size][salmon-bucket] · [General saved data][bucket-capture] · [Creation order][spawn-order] · [Item components][stack-config] · [Release][bucket-release] · [Persistence][fish]

Keep it submerged: it breathes underwater, flops when stranded on solid ground, and loses air while out of water. From a full 300-tick reserve, drying deals its first **2 health points after about 16 seconds**, then another 2 points each second; entering water refills that reserve. [Breathing tag][breathing] · [Flopping][fish] · [Active air tick][water] · [Damage threshold][air-threshold]

## Drops

With mob loot enabled, every size uses the same table: **one Raw Salmon**, plus a separate **5% chance of one Bone Meal**. Looting does not increase either entry. Burning at death, or a qualifying main-hand Fire Aspect enchantment on the direct attacker, smelts the fish drop to [Cooked Salmon](../items/CookedSalmon.md). [Loot][loot-salmon] · [Enchantments][smelt-tag] · [Smelting function][smelt-function] · [Recipe result][smelt-salmon]

An eligible player-credit kill gives **1–3 experience**. Bucket capture preserves the animal instead of paying death loot. [Experience][water] · [Loot gates][death-loot] · [Capture][bucket-capture]

## Notes

- **Health:** 3 points, or 1½ hearts, for all three sizes. [Attribute wiring][attributes-salmon] · [Health][fish]
- [Fishing](../mechanics/Fishing.md) is the separate route for catching Salmon as an item
- The natural spawn chooser reads the loaded biome list; a school limit is not an extra biome-spawn entry. [World loading][world-load] · [JSON loading][registry-load] · [Selection][biome-choice]

Related: [Cod](Cod.md) · [Ocean biomes](../biomes/Oceans.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked the loaded biome candidates and active spawn placement, behavior inheritance, bucket capture/release, attributes and death loot. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[Salmon]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Salmon.java
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[biome-river]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/river.json
[biome-frozen_river]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/frozen_river.json
[biome-cold_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/cold_ocean.json
[biome-deep_cold_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_cold_ocean.json
[biome-frozen_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/frozen_ocean.json
[biome-deep_frozen_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_frozen_ocean.json
[placements]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L88-L105
[in-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L12-L20
[water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L16-L79
[sea-level]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[natural-check]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list-salmon]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2074-L2074
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L34-L142
[school]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractSchoolingFish.java#L14-L113
[school-goal]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/goal/FollowFlockLeaderGoal.java#L23-L64
[salmon-variants]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Salmon.java#L138-L163
[loot-salmon]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/salmon.json
[salmon-bucket]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Salmon.java#L98-L133
[spawn-order]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1774
[stack-config]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[breathing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[smelt-tag]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[smelt-function]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L30-L47
[smelt-salmon]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/recipe/smelting/cooked_salmon.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes-salmon]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L225-L225
[world-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[biome-choice]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[entity-registration]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1140-L1142
