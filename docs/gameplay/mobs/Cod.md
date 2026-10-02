# Cod

**Cod** are small schooling fish that you can carry home alive or collect as [Raw Cod](../items/RawCod.md). Bring a **Water Bucket**, not an empty bucket, if you want a living fish for a pond or aquarium. Its ID is `minecraft:cod`. [Fish interaction][fish] · [Cod bucket][Cod] · [Entity registration][entity-registration]

## Obtaining

Look in **Ocean, Deep Ocean, Cold Ocean, Deep Cold Ocean, Lukewarm Ocean and Deep Lukewarm Ocean**. Their loaded water-ambient lists select Cod groups of **3–6**; this is a candidate group size, not a guarantee that every attempted fish can spawn. Warm and frozen oceans are not on this Cod route. [Ocean][biome-ocean] · [Deep Ocean][biome-deep_ocean] · [Cold][biome-cold_ocean] · [Deep Cold][biome-deep_cold_ocean] · [Lukewarm][biome-lukewarm_ocean] · [Deep Lukewarm][biome-deep_lukewarm_ocean]

The registered natural-spawn checks require water at the spawn position and below it, a Water block above, and a height from **sea level minus 13 through sea level**, inclusive. With the bundled Overworld sea level of 63, that is **Y 50–63**. The usual population, distance and collision checks still apply. [Placement registration][placements] · [Water placement][in-water] · [Surface rule][water] · [Sea level][sea-level] · [Natural checks][natural-check]

The [Cod Spawn Egg](../items/CodSpawnEgg.md) is an ordinary listed item. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Survival as well as Creative; placing an egg is a separate route from natural spawning. [Listing][egg-list-cod] · [Egg use][egg-use] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Behavior

Cod flee nearby players and panic when threatened. Nearby Cod can follow a school leader, with a school limit of **8**; schooling is movement behavior, not breeding. There is no feeding or breeding interaction in the checked fish implementation. [Fish goals][fish] · [Schooling][school] · [Finding a leader][school-goal]

Keep it submerged: it breathes underwater, flops when stranded on solid ground, and loses air while out of water. From a full 300-tick reserve, drying deals its first **2 health points after about 16 seconds**, then another 2 points each second; entering water refills that reserve. [Breathing tag][breathing] · [Flopping][fish] · [Active air tick][water] · [Damage threshold][air-threshold]

### Keeping and moving Cod

Use a Water Bucket directly on a living Cod to obtain a [Bucket of Cod](../items/BucketOfCod.md). Capture preserves its health and custom name. Releasing it marks it as bucket-origin, protecting it from ordinary distance despawning. Capturing a wild fish is therefore useful even if you immediately return it to the same pond. [Capture and saved data][bucket-capture] · [Release][bucket-release] · [Persistence][fish]

## Drops

With mob loot enabled, a Cod's table supplies **one Raw Cod** and a separate **5% chance of one Bone Meal**. Looting does not increase either entry. If it is burning at death, or its direct attacker has a qualifying main-hand Fire Aspect enchantment, the fish drop is smelted into [Cooked Cod](../items/CookedCod.md). [Loot][loot-cod] · [Smelting enchantment tag][smelt-tag] · [Loot smelting][smelt-function] · [Cooking result][smelt-cod]

An eligible player-credit kill also gives **1–3 experience**. Collecting the fish alive in a bucket does not run its death loot. [Experience amount][water] · [Death and experience gates][death-loot] · [Capture][bucket-capture]

## Notes

- **Health:** 3 points, or 1½ hearts. [Attribute wiring][attributes-fish] · [Value][fish]
- [Fishing](../mechanics/Fishing.md) catches fish items; it is separate from collecting living Cod with a bucket
- **Primordial Ocean also has Cod candidates.** The loaded Primordial Caves dimension includes that biome and takes precedence over the matching preset definition. This is a source-wired candidate route, not a surveyed encounter rate; the dimension-relative water-band checks still apply. See [Ocean biomes](../biomes/Oceans.md#integrated-ocean-content-is-separate). [Biome entry][biome-primordial_ocean] · [Loaded dimension][primordial-dimension] · [Dimension precedence][dimension-bake]
- The spawn chooser uses loaded biome data or a structure override. [World loading][world-load] · [Biome registry][registry-biome] · [Fresh registries][registry-fresh] · [JSON loading][registry-load] · [Spawn selection][biome-choice]

Related: [Salmon](Salmon.md) · [Tropical Fish](TropicalFish.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked the loaded biome candidates and active spawn placement, behavior inheritance, bucket capture/release, attributes and death loot. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L34-L142
[Cod]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Cod.java
[biome-ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[biome-deep_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_ocean.json
[biome-cold_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/cold_ocean.json
[biome-deep_cold_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_cold_ocean.json
[biome-lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/lukewarm_ocean.json
[biome-deep_lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_lukewarm_ocean.json
[placements]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L88-L105
[in-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L12-L20
[water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L16-L79
[sea-level]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[natural-check]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list-cod]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2000-L2000
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[school]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractSchoolingFish.java#L14-L113
[school-goal]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/goal/FollowFlockLeaderGoal.java#L23-L64
[breathing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[loot-cod]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/cod.json
[smelt-tag]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[smelt-function]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L30-L47
[smelt-cod]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/recipe/smelting/cooked_cod.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes-fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L145-L145
[biome-primordial_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[world-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-biome]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L108
[registry-fresh]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L401-L409
[registry-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[biome-choice]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[entity-registration]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L439-L441

[primordial-dimension]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/dimension/primordial_caves.json#L1087-L1236
[dimension-bake]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183
