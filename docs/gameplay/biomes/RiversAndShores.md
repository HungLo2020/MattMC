# Rivers and shores

Use **Rivers** to look for Salmon, Squid and waterside plants, **Beaches** for sand and the checked Turtle spawn route, and **Stony Shores** for exposed stone/gravel terrain. Frozen River and Snowy Beach share cold-surface hazards, but their plants and spawn tables differ. These five exact IDs are selected by the bundled Normal Overworld source. [Normal preset][normal] · [Parameter preset][overworld-preset] · [Selector dispatch][preset-provider] · [River selection][river-selection] · [Shore selection][shore-selection]

## River {#river}

**`minecraft:river`** lists Salmon and Squid, with Glow Squid and Bats in their separate underground/ambient categories. Salmon have spawn weight 5 and groups of 1–5; Squid weight 2 and groups of 1–4. The Salmon and Squid bindings both use water placement and near-sea-level predicates, from **sea level minus 13 through sea level** inclusive, with Water above and water-tagged fluid below. The Normal Overworld's sea level is 63, giving **Y=50–63** for those predicates. This is a spawn condition, not a statement that every river is that deep. [Biome table][river] · [Spawn bindings][spawn-bindings] · [Salmon predicate][water-animal] · [Squid predicate][ageable-water] · [Overworld settings][overworld-settings]

The feature list includes **river Seagrass**, waterside trees, bushes, Sugar Cane and Firefly Bushes near water, along with flowers, grass, mushrooms and pumpkins. Seagrass placement ultimately checks Water at the ocean-floor position and the plant's support conditions. Follow [Seagrass](../blocks/Seagrass.md) and [Sugar Cane](../blocks/SugarCane.md) for recovery, planting and growth; the presence of water is not enough to make every bank grow these plants. [Biome features][river] · [River Seagrass placement][river-seagrass] · [Seagrass implementation][seagrass-generator]

**Drowned are a real listed hazard here**, in addition to ordinary hostile candidates. River's Drowned weight is 100 with a group size of 1. Both river IDs belong to the more-frequent-Drowned spawn tag, which uses a 1-in-15 random gate for an otherwise eligible natural attempt and does not apply the non-river depth check. Water, darkness, difficulty and the rest of natural spawning still apply; neither this gate nor the table weight is a per-minute encounter rate. [River table][river] · [River membership][river-tag] · [Drowned tag][drowned-tag] · [Active predicate][drowned]

## Frozen River {#frozen-river}

**`minecraft:frozen_river`** retains the same Salmon and Squid entries as River. Its Drowned entry has weight **1**, compared with River's 100, while still using the shared river-specific predicate. Do not interpret that lower weight as Drowned being impossible. Its ordinary hostile list includes Skeletons, not an added Stray entry. [Frozen River table][frozen-river] · [River table][river] · [Drowned predicate][drowned]

Its base temperature is **0.0**, and its features include top-layer freezing. Cold-enough surface Water can become Ice and supported surface positions can receive Snow, subject to the biome's block-light and placement checks. For collecting Ice or keeping Water available, use [Ice](../blocks/Ice.md). The freeze feature does not fill the whole river with solid ice. [Biome climate and features][frozen-river] · [Top-layer feature][freeze] · [Freeze/Snow checks][weather]

Frozen River keeps the waterside-tree, Sugar Cane and Firefly Bush entries but **does not include `seagrass_river`** in its bundled feature list. That is a generation-list distinction, not a claim that players cannot plant Seagrass there or that no vegetation can cross a biome boundary. [Frozen River features][frozen-river] · [River features][river]

## Beach {#beach}

**`minecraft:beach`** uses the Overworld's sand/sandstone surface branch. It is the only one of these three shore biomes with a **Turtle** entry: weight 5, groups of 2–5. Natural Turtle candidates need Sand-tagged support, raw brightness above 8 and a position **below sea level plus 4**. For the Normal sea level of 63, the height condition is **Y≤66**. A dark or high sand platform does not meet those checks merely because its biome is Beach. See [Turtles](../mobs/Turtle.md) for breeding and home-beach behavior. [Surface rules][sand-surface] · [Biome table][beach] · [Turtle checks][turtle] · [Sand check][turtle-sand] · [Brightness check][animal-light] · [Sand tag][sand-tag]

Beaches list Sugar Cane, Firefly Bushes near water, flowers, sparse grass, mushrooms and pumpkins, but no tree feature. Bring wood if this is your landing or building site. They still list ordinary hostile candidates, Bats and underground Glow Squid; an empty-looking coastline is not a universal safe zone. [Biome features and spawns][beach]

## Snowy Beach {#snowy-beach}

**`minecraft:snowy_beach`** shares the sand/sandstone surface branch and the ordinary Beach decoration list, with base temperature **0.05** and a top-layer freeze feature. Snow and surface Ice remain subject to actual temperature, light and support conditions. The sand underneath does not make it an ordinary Turtle destination: **its creature table is empty**, with no Turtle entry. [Biome data][snowy-beach] · [Sand surface rules][sand-surface] · [Freezing implementation][freeze] · [Weather checks][weather]

Sugar Cane and Firefly Bush attempts remain in the data, despite the cold setting. Use [Sugar Cane](../blocks/SugarCane.md) for water/support requirements rather than assuming the biome name either guarantees or forbids a farm. The checked hostile table matches Beach's ordinary set and does not add a Stray entry. [Features and spawns][snowy-beach]

## Stony Shore {#stony-shore}

**`minecraft:stony_shore`** has a dedicated **Stone and Gravel** surface branch, with gravel controlled by the configured surface-noise condition. It is the shore choice for rocky building materials rather than the Beach sand branch. Use [Soil, Sand and Gravel](../blocks/SoilSandAndGravel.md) for Gravel/Flint recovery and falling-block behavior. [Stony surface branch][stony-surface]

Its biome definition still contains the same flower, sparse-grass, mushroom, pumpkin, Sugar Cane and near-water Firefly Bush entries as the two beaches. These features need their own viable ground and water conditions; a stone surface does not ensure the attempted plants can survive. No Turtle, Salmon or Squid entry appears in its table. It has the ordinary hostile set, Bats and underground Glow Squid. [Biome data][stony-shore] · [Simple-block survival gate][simple-block]

## Coastal structures and underground resources

**Beach and Snowy Beach** are the two members of `#minecraft:is_beach`. The beached Shipwreck and Buried Treasure eligibility tags both expand that tag, and both structures have structure-set entries. **Stony Shore is not in that beach tag**; its name alone does not establish either coastal-structure route. These are eligibility conditions, not a Shipwreck or treasure chest on every beach. Placement selection and valid generation still decide individual finds. [Beach membership][beach-tag] · [Shipwreck eligibility][wreck-tag] · [Shipwreck definition][wreck] · [Shipwreck set][wreck-set] · [Treasure eligibility][treasure-tag] · [Treasure definition][treasure] · [Treasure set][treasure-set] · [Structure caller][structure-call]

All five river/shore definitions include ordinary ore placements, Amethyst geodes, monster rooms and Sand/Clay/Gravel disk features. Those underground entries are separate from the surface material choice, and the disks still require the configured placement/replacement conditions. See [Ore resources](../blocks/OreResources.md), [Clay and Bricks](../blocks/ClayAndBricks.md), and [Ocean biomes](Oceans.md) for nearby resource routes. [River][river] · [Frozen River][frozen-river] · [Beach][beach] · [Snowy Beach][snowy-beach] · [Stony Shore][stony-shore]

The selector uses cold and non-cold river ranges plus separate coast/erosion rules. Its beach helper chooses Snowy Beach in the coldest temperature band, ordinary Beach in intermediate bands and Desert in the hottest band; other coast branches can select Stony Shore or neighboring land biomes. **Not every boundary with an ocean is a sandy Beach.** [River selection][river-selection] · [Beach helper][beach-helper] · [Full selector][selector]

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. The review follows the bundled biome JSON through the registry loader and actual decoration/spawn callers. A feature list describes attempted placement; a spawn list supplies candidates subject to placement, light, population and difficulty checks. [World loader][load] · [Registry loading][json-load] · [Decoration caller][decorate] · [Placed feature dispatch][placed-dispatch] · [Natural spawning][spawner]

No in-game shoreline survey, fishing/spawn-rate test, structure search or resource-yield test was run. Data packs, world settings and existing terrain can differ.

Related: [Biomes](Biomes.md) · [Cave biomes](CaveBiomes.md) · [Ocean biomes](Oceans.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[overworld-preset]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[preset-provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[river-selection]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L701-L792
[shore-selection]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L420-L442
[river]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/river.json
[spawn-bindings]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[water-animal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L70-L78
[ageable-water]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/AgeableWaterCreature.java#L65-L79
[overworld-settings]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[river-seagrass]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/seagrass_river.json
[seagrass-generator]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SeagrassFeature.java
[river-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/is_river.json
[drowned-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/more_frequent_drowned_spawns.json
[drowned]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/monster/Drowned.java#L109-L130
[frozen-river]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/frozen_river.json
[freeze]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SnowAndFreezeFeature.java
[weather]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/Biome.java#L151-L206
[sand-surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L1400-L1435
[beach]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/beach.json
[turtle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L140-L144
[turtle-sand]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L117-L123
[animal-light]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[sand-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/sand.json
[snowy-beach]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/snowy_beach.json
[stony-surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L1318-L1374
[stony-shore]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/stony_shore.json
[simple-block]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L18-L43
[beach-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/is_beach.json
[wreck-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/shipwreck_beached.json
[wreck]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/shipwreck_beached.json
[wreck-set]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/shipwrecks.json
[treasure-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/buried_treasure.json
[treasure]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/buried_treasure.json
[treasure-set]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/buried_treasures.json
[structure-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L554-L575
[beach-helper]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L855-L865
[selector]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L35-L48
[json-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[decorate]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[placed-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L34-L60
[spawner]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
