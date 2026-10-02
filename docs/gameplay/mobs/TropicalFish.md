# Tropical Fish

**Tropical Fish** are decorative schooling fish with varied patterns and colors. Capture a favorite with a Water Bucket: the [Bucket of Tropical Fish](../items/BucketOfTropicalFish.md) keeps its appearance, while the loose [Tropical Fish item](../items/TropicalFish.md) is food, not a living aquarium fish. Its ID is `minecraft:tropical_fish`. [Bucket components][tropical-components] · [Death item][loot-tropical_fish] · [Entity registration][entity-registration]

## Obtaining

The loaded water-ambient lists include Tropical Fish in **Warm Ocean, Lukewarm Ocean, Deep Lukewarm Ocean, Mangrove Swamp and Lush Caves**. Each list requests groups of **8**, but terrain checks and the fish's random-appearance branch can stop the group before eight are created. [Warm][biome-warm_ocean] · [Lukewarm][biome-lukewarm_ocean] · [Deep Lukewarm][biome-deep_lukewarm_ocean] · [Mangrove Swamp][biome-mangrove_swamp] · [Lush Caves][biome-lush_caves] · [Group termination][TropicalFish]

Natural spawning needs water at and below the fish, and a Water block above it. In these oceans and Mangrove Swamp, the height range is **sea level minus 13 through sea level**, or **Y 50–63** with the bundled Overworld settings. **Lush Caves alone** is in the bundled any-height exception tag, so suitable cave water can spawn Tropical Fish below that surface band. Population and collision checks still apply. [Registered predicate][placements] · [Water placement][in-water] · [Fish predicate][tropical-colors] · [Any-height tag][tropical-height] · [Surface band][water] · [Sea level][sea-level] · [Natural checks][natural-check]

The [Tropical Fish Spawn Egg](../items/TropicalFishSpawnEgg.md) is an ordinary listed item. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Survival as well as Creative; placing an egg is a separate route from natural spawning. [Listing][egg-list-tropical] · [Egg use][egg-use] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Behavior

Tropical Fish avoid players and can follow a same-species leader in schools of up to **8**. They have no checked feeding or breeding interaction. The colors in an aquarium need not all match: later leader-following is based on the fish's class, not its pattern. [Fish goals][fish] · [School size][school] · [Leader selection][school-goal]

### Patterns and colors

For a new spawn group, the common branch has a **90% selection chance** and chooses one of **22 predefined appearances**; later members of that group inherit it. The other branch independently chooses one of **12 patterns**, a base color and a pattern color from the **16 dye colors**, and ends that spawn group. This branch permits 3,072 pattern/color combinations, including combinations with the same two colors; this is a count of data combinations, not a claim that every combination looks distinct. [Common appearances][tropical-common] · [Selection and patterns][tropical-colors] · [Color set][dye-colors]

A captured bucket stores all three appearance components along with health and custom name. Release applies those components after spawn initialization and marks the fish as bucket-origin, preventing ordinary distance despawning. [Saved appearance][tropical-components] · [General saved data][bucket-capture] · [Creation order][spawn-order] · [Components][stack-config] · [Release][bucket-release] · [Persistence][fish]

Keep it submerged: it breathes underwater, flops when stranded on solid ground, and loses air while out of water. From a full 300-tick reserve, drying deals its first **2 health points after about 16 seconds**, then another 2 points each second; entering water refills that reserve. [Breathing tag][breathing] · [Flopping][fish] · [Active air tick][water] · [Damage threshold][air-threshold]

## Drops

With mob loot enabled, death supplies **one Tropical Fish item** and a separate **5% chance of one Bone Meal**. The table has no Looting increase or cooked-fish conversion. An eligible player-credit kill gives **1–3 experience**. Use a bucket if keeping the animal or its colors matters. [Loot][loot-tropical_fish] · [Experience amount][water] · [Death gates][death-loot]

## Notes

- **Health:** 3 points, or 1½ hearts. [Attribute wiring][attributes-tropical] · [Value][fish]
- [Fishing](../mechanics/Fishing.md) catches the loose item; it does not preserve a living fish's appearance
- [Nautilus feeding](Nautilus.md#taming-and-feeding) has its own rules for loose fish and fish buckets
- Natural candidates above come from loaded biome JSON, not inferred Java generation helpers. [World loading][world-load] · [JSON loading][registry-load] · [Active chooser][biome-choice]

Related: [Cod](Cod.md) · [Salmon](Salmon.md) · [Ocean biomes](../biomes/Oceans.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked the loaded biome candidates and active spawn placement, behavior inheritance, bucket capture/release, attributes and death loot. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[tropical-components]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java#L165-L213
[loot-tropical_fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/tropical_fish.json
[biome-warm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json
[biome-lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/lukewarm_ocean.json
[biome-deep_lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_lukewarm_ocean.json
[biome-mangrove_swamp]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json
[biome-lush_caves]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[TropicalFish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java
[placements]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L88-L105
[in-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L12-L20
[tropical-colors]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java#L236-L297
[tropical-height]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/worldgen/biome/allows_tropical_fish_spawns_at_any_height.json
[water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L16-L79
[sea-level]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[natural-check]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list-tropical]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2107-L2107
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L34-L142
[school]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractSchoolingFish.java#L14-L113
[school-goal]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/goal/FollowFlockLeaderGoal.java#L23-L64
[tropical-common]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/TropicalFish.java#L47-L74
[dye-colors]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/DyeColor.java#L24-L40
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[spawn-order]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1774
[stack-config]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[breathing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes-tropical]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L260-L260
[world-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[biome-choice]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[entity-registration]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1446-L1448
