# Finding ores

Choose your destination, the rock you search and your height together. **A listed attempt is not a deposit, and an origin range is not a boundary around every ore block.** This guide compares the bundled generation routes for Coal, Iron, Copper, Gold, Redstone, Lapis, Diamond, Emerald, Nether Quartz and Ancient Debris.

Bring the [right pickaxe](../blocks/OreResources.md#bring-a-suitable-pickaxe); it also covers harvesting, Fortune, drops and processing. [Mining](Mining.md) explains shared tool rules.

## Plan a mining trip

These are choices suggested by the source distributions, not measured best-height or yield rankings:

- **Coal and Iron:** search high exposed stone for upper placements; Iron also has a separate underground concentration centered on Y=16. Coal's ordinary Overworld origins start at Y=0, so a deep Diamond trip is a poor choice for replenishing Coal.
- **Copper:** the ordinary triangle centers on Y=48. Dripstone Caves use the larger configuration at the same attempt count; Granite containing Copper or Raw Copper may lead into a separate large vein.
- **Gold:** Badlands, Eroded Badlands and Wooded Badlands add 50 attempts over Y=32–256. Search eligible stone, including inside the hills; the extra feature does not replace Terracotta.
- **Diamond and Redstone:** their lower distributions favor descending within the available rock. Diamond has several overlapping placements, and its air-exposure rejection makes searching only open cave walls incomplete. Bedrock, lava, travel and mining speed still affect a practical route.
- **Lapis:** ordinary origins cluster around Y=0, with a separate buried spread from −64 to 64. **Emerald:** choose one of the mountain/windswept biomes listed below; a tall hill in another biome does not add the Emerald feature.
- **Nether Quartz and Ancient Debris:** use the Nether chart. Debris is generated with full air-exposure rejection; the concentrated placement samples Y=8–24, centered on 16. This is a reason to investigate that band, not a proven optimum.

The tables and linked definitions below supply the evidence for these choices.

## Read the ranges correctly

**Uniform** samples each listed origin height equally. **Triangle** weights the middle more heavily; these definitions use a trapezoid provider with its default zero plateau. Some triangles extend beyond the generated terrain. Their out-of-height samples are not moved to the nearest valid level. [Height sampling][uniform] · [Triangle sampling][triangle]

| Bundled generator | Bottom anchor | Top anchor used by these placements | Why the dimension height alone is insufficient |
| --- | ---: | ---: | --- |
| Normal Overworld | −64 | 319 | Generator and dimension are both 384 blocks high |
| Nether | 0 | 127 | Generator is 128 blocks high; dimension type is 256 |
| Primordial Caves | −64 | 319 | Generator is 384 blocks high; dimension type is 400 |

An `above_bottom` offset adds to the generator's effective bottom; `below_top: 0` means its top anchor, not Y=0. For example, ordinary Diamond's offsets −80 and 80 resolve to **origin samples −144 through 16**, while Nether `below_top: 10` resolves to **117**. The context uses the greater bottom and smaller height from the generator and level. [Context][height-context] · [Generator heights][generator-height] · [Anchor calculation][anchors] · [Overworld settings][overworld-noise] / [type][overworld-type] · [Nether settings][nether-noise] / [type][the_nether-type] · [Primordial settings][primordial_caves-noise] / [type][primordial_caves-type]

**Attempts** means the configured origins before biome, host and placement checks in a chunk's decoration pass. A rarity of 1/9 is a chance for one origin, not a promise that every ninth chunk contains a deposit. **Size** is a shape input, not a block count or guaranteed yield. Ordinary ore shapes extend around their origins; Ancient Debris instead scatters candidate positions near its origin and can choose zero candidates. [Decoration][decoration] · [Placement modifiers][placed] · [Count][count] · [Rarity][rarity] · [Ore shapes and checks][ore] · [Debris scatter][scattered]

The **air rejection** column is the chance to reject an otherwise eligible candidate that touches Air on a face when generation runs. At 100%, every such candidate fails; at 0%, this air test imposes no restriction. Water is not Air for this test. These are per-candidate generation checks, not a chance that an entire vein disappears, nor a restriction on exposing ore later by mining. [Host and air checks][ore] · [Adjacent-block test][air-neighbors]

## Overworld placements

The ordinary Coal, Iron, Gold, Redstone, Diamond and Lapis set is shared by the bundled Overworld biome definitions, including the cave biomes. Copper normally uses the small configuration; **Dripstone Caves substitutes the large one**. Emerald and extra Gold require the specific biome membership below. The biome at the sampled position matters, including underground biome changes. [Overworld selection][biome-presets] · [Biome builder][overworld-biomes] · [Forest example][b-forest] · [Dripstone Caves][b-dripstone_caves] · [Position's biome check][biome-filter]

Every ordinary row below uses stone/deepslate host tags. Regular ore replaces **Stone, Granite, Diorite or Andesite**; its Deepslate form replaces **Deepslate or Tuff**. Air, Dirt, Terracotta and Sandstone are not hosts just because the height is suitable. [Stone hosts][stone_ore_replaceables] · [Deepslate hosts][deepslate_ore_replaceables]

| Mineral and placement | Sampled origin Y; distribution | Attempts | Size input | Air rejection |
| --- | --- | ---: | ---: | ---: |
| [Coal upper][p-ore_coal_upper] | 136–319; uniform | 30 | [17][c-ore_coal] | 0% |
| [Coal lower][p-ore_coal_lower] | 0–192; triangle | 20 | [17][c-ore_coal_buried] | 50% |
| [Iron upper][p-ore_iron_upper] | 80–384; triangle | 90 | [9][c-ore_iron] | 0% |
| [Iron middle][p-ore_iron_middle] | −24–56; triangle | 10 | [9][c-ore_iron] | 0% |
| [Iron small][p-ore_iron_small] | −64–72; uniform | 10 | [4][c-ore_iron_small] | 0% |
| [Copper ordinary][p-ore_copper] | −16–112; triangle | 16 | [10][c-ore_copper_small] | 0% |
| [Copper, Dripstone Caves][p-ore_copper_large] | −16–112; triangle | 16 | [20][c-ore_copper_large] | 0% |
| [Gold ordinary][p-ore_gold] | −64–32; triangle | 4 | [9][c-ore_gold_buried] | 50% |
| [Gold lower][p-ore_gold_lower] | −64–−48; uniform | 0–1 | [9][c-ore_gold_buried] | 50% |
| [Gold, Badlands extra][p-ore_gold_extra] | 32–256; uniform | 50 | [9][c-ore_gold] | 0% |
| [Redstone ordinary][p-ore_redstone] | −64–15; uniform | 4 | [8][c-ore_redstone] | 0% |
| [Redstone lower][p-ore_redstone_lower] | −96–−32; triangle | 8 | [8][c-ore_redstone] | 0% |
| [Lapis ordinary][p-ore_lapis] | −32–32; triangle | 2 | [7][c-ore_lapis] | 0% |
| [Lapis buried][p-ore_lapis_buried] | −64–64; uniform | 4 | [7][c-ore_lapis_buried] | 100% |
| [Diamond small][p-ore_diamond] | −144–16; triangle | 7 | [4][c-ore_diamond_small] | 50% |
| [Diamond medium][p-ore_diamond_medium] | −64–−4; uniform | 2 | [8][c-ore_diamond_medium] | 50% |
| [Diamond rare large][p-ore_diamond_large] | −144–16; triangle | 1 with 1/9 chance | [12][c-ore_diamond_large] | 70% |
| [Diamond buried][p-ore_diamond_buried] | −144–16; triangle | 4 | [8][c-ore_diamond_buried] | 100% |
| [Emerald, listed biomes][p-ore_emerald] | −16–480; triangle | 100 | [3][c-ore_emerald] | 0% |

### Biome bonuses

- **Extra Gold:** Badlands, Eroded Badlands and Wooded Badlands. The ordinary and lower Gold placements remain alongside it. [Badlands][b-badlands] · [Eroded][b-eroded_badlands] · [Wooded][b-wooded_badlands]
- **Larger ordinary Copper:** Dripstone Caves uses size 20 instead of 10, with the same 16 attempts and height triangle. It is a size substitution, not 16 extra ordinary Copper attempts. [Biome][b-dripstone_caves] · [Cave biomes](../biomes/CaveBiomes.md)
- **Emerald:** Cherry Grove, Meadow, Grove, Snowy Slopes, Frozen Peaks, Jagged Peaks, Stony Peaks, Windswept Hills, Windswept Gravelly Hills and Windswept Forest. **Windswept Savanna is not on this list.** The −16–480 origin triangle extends above the normal terrain height; it does not create ore up to Y=480. [Cherry Grove][b-cherry_grove] · [Meadow][b-meadow] · [Grove][b-grove] · [Slopes][b-snowy_slopes] · [Frozen Peaks][b-frozen_peaks] · [Jagged Peaks][b-jagged_peaks] · [Stony Peaks][b-stony_peaks] · [Hills][b-windswept_hills] · [Gravelly Hills][b-windswept_gravelly_hills] · [Forest][b-windswept_forest] · [Savanna comparison][b-windswept_savanna]

Use [mountain and windswept biomes](../biomes/MountainsAndWindsweptBiomes.md) and [Badlands](../biomes/DesertsBadlandsAndSavannas.md) for the wider expedition conditions.

## Follow large Copper and Iron veins

The Normal Overworld also enables a **separate noise-based vein route** during terrain filling. It is independent of the attempt counts above and is not a Primordial geode. [Overworld vein settings][overworld-noise] · [Activation][noise-chunk] · [Terrain writes][noise-fill]

| Vein kind | Candidate block Y | Ore and raw block | Associated filler |
| --- | --- | --- | --- |
| Copper | 0–50 | Copper Ore and Raw Copper Blocks | Granite |
| Iron | −60–−8 | Deepslate Iron Ore and Raw Iron Blocks | Tuff |

These are the veinifier's own inclusive height gates, unlike a placed feature's origin range. Noise shape, edge attenuation and random checks decide which candidates become ore, a raw block or filler. Follow a promising mixed deposit, but ordinary Granite or Tuff alone does not prove a vein. The earlier terrain material rule can also supply air/fluid instead of passing a position to the veinifier. [Vein selection and gates][veinifier] · [Material-rule order][noise-chunk] [material-rules][]

**The bundled Nether and Primordial Caves noise settings disable this route.** Finding ordinary Iron/Copper or a custom geode there does not mean a large Overworld-style noise vein is nearby. [Nether][nether-noise] · [Primordial Caves][primordial_caves-noise]

## Nether Quartz, Gold and Ancient Debris

All five ordinary Nether biomes list the Debris pair. **Nether Wastes, Soul Sand Valley, Crimson Forest and Warped Forest** use the standard Gold/Quartz counts; **Basalt Deltas** uses the higher-count variants. Gold and Quartz still replace **Netherrack only**, so more configured attempts in Basalt Deltas do not establish a better yield through Basalt or Blackstone. Debris can replace **Netherrack, Basalt or Blackstone**. [Nether selector][biome-presets] · [Wastes][b-nether_wastes] · [Valley][b-soul_sand_valley] · [Crimson][b-crimson_forest] · [Warped][b-warped_forest] · [Deltas][b-basalt_deltas] · [Debris hosts][base_stone_nether]

| Mineral and placement | Sampled origin Y; distribution | Attempts | Size input | Air rejection |
| --- | --- | ---: | ---: | ---: |
| [Nether Gold, standard][p-ore_gold_nether] | 10–117; uniform | 10 | [10][c-ore_nether_gold] | 0% |
| [Nether Gold, Basalt Deltas][p-ore_gold_deltas] | 10–117; uniform | 20 | [10][c-ore_nether_gold] | 0% |
| [Quartz, standard][p-ore_quartz_nether] | 10–117; uniform | 16 | [14][c-ore_quartz] | 0% |
| [Quartz, Basalt Deltas][p-ore_quartz_deltas] | 10–117; uniform | 32 | [14][c-ore_quartz] | 0% |
| [Ancient Debris, concentrated][p-ore_ancient_debris_large] | 8–24; triangle | 1 | [3][c-ore_ancient_debris_large] | 100% |
| [Ancient Debris, broad][p-ore_debris_small] | 8–119; uniform | 1 | [2][c-ore_ancient_debris_small] | 100% |

The Debris size inputs 3 and 2 limit their scatter candidate draws, not guaranteed recoverable blocks. Both can produce nothing after random, host and air checks. Use [Nether biomes](../biomes/NetherBiomes.md) for travel conditions and [Ancient Debris processing](../blocks/OreResources.md#ancient-debris-to-netherite) for what to do with a find.

## Primordial Caves: check the actual biome

With bundled resources loaded unchanged, the effective dimension includes **Primordial Plains, Dry Midlands and Primordial Ocean**. The loaded dimension entry takes precedence over the literal Normal preset's two-biome entry. See the [dimension guide](../dimensions/PrimordialCaves.md#what-currently-generates) for entry, fallback and saved-world details. [Loaded resource][primordial-dimension] · [Normal preset][normal] · [Loading][world-load] · [Dimension precedence][dimension-bake]

**Primordial Plains reuses the ordinary Overworld placements**, including ordinary Copper and all four Diamond placements, without the Badlands Gold or mountain Emerald additions. Its matching −64…319 generator anchors give those placements the same numeric origin ranges as above. Terrain and the disabled large-vein route still differ. [Plains features][b-primordial_plains] · [Primordial settings][primordial_caves-noise]

**Dry Midlands and Primordial Ocean share the custom ordinary placements below; only Primordial Ocean adds the two Emerald rows.** These corrected configurations use the same stone/deepslate hosts described above. For a trip there, high rock has both Copper placements; the middle bands overlap Iron, Gold, Lapis and Redstone; Diamond has buried/lower routes plus two middle-band routes. These are configured opportunities, not a comparison of measured yield against the Overworld. [Dry Midlands][b-dry_midlands] · [Primordial Ocean][b-primordial_ocean]

| Mineral and custom placement | Sampled origin Y; distribution | Attempts | Size input | Air rejection |
| --- | --- | ---: | ---: | ---: |
| [Coal middle][p-ore-coal_middle] | 1–184; uniform | 12 | [12][c-ore-coal] | 0% |
| [Coal high][p-ore-coal_high] | 184–319; uniform | 30 | [12][c-ore-coal] | 0% |
| [Coal buried][p-ore-coal_buried] | −32–287; uniform | 10 | [17][c-ore-coal_buried] | 85% |
| [Iron low, small][p-ore-iron_low] | −48–120; uniform | 65 | [4][c-ore-iron_small] | 0% |
| [Iron middle][p-ore-iron_middle] | 1–200; uniform | 32 | [9][c-ore-iron] | 30% |
| [Iron high][p-ore-iron_high] | 200–367; triangle | 5 | [9][c-ore-iron] | 30% |
| [Iron high, small][p-ore-iron_small_high] | 200–271; uniform | 20 | [4][c-ore-iron_small] | 0% |
| [Copper high, small][p-ore-copper_high] | 120–303; uniform | 16 | [10][c-ore-copper_small] | 0% |
| [Copper high, large][p-ore-copper_large_high] | 120–303; uniform | 16 | [20][c-ore-copper_large] | 0% |
| [Gold lower][p-ore-gold_lower] | −32–120; uniform | 15 | [9][c-ore-gold] | 0% |
| [Gold middle][p-ore-gold_middle] | 1–183; uniform | 4 | [9][c-ore-gold] | 0% |
| [Gold buried][p-ore-gold_buried] | −64–120; uniform | 10 | [9][c-ore-gold_buried] | 50% |
| [Redstone low][p-ore-redstone_low] | −80–−16; triangle | 15 | [8][c-ore-redstone] | 0% |
| [Redstone middle][p-ore-redstone_middle] | −32–120; uniform | 32 | [8][c-ore-redstone] | 0% |
| [Lapis middle][p-ore-lapis_middle] | −32–199; uniform | 8 | [7][c-ore-lapis] | 0% |
| [Lapis buried][p-ore-lapis_buried] | −64–120; uniform | 20 | [7][c-ore-lapis_buried] | 100% |
| [Diamond lower, medium][p-ore-diamond_lower] | −96–0; triangle | 8 | [8][c-ore-diamond_medium] | 50% |
| [Diamond lower, large][p-ore-diamond_large] | −96–0; triangle | 8 | [12][c-ore-diamond_large] | 70% |
| [Diamond buried][p-ore-diamond_buried] | −64–0; uniform | 8 | [8][c-ore-diamond_buried] | 100% |
| [Diamond middle, medium][p-ore-diamond_medium_middle] | −32–120; uniform | 4 | [8][c-ore-diamond_medium] | 50% |
| [Diamond middle, small][p-ore-diamond_small_middle] | −32–120; uniform | 8 | [4][c-ore-diamond_small] | 50% |
| [Emerald, Ocean only][p-ore-emerald] | −48–303; uniform | 8 | [3][c-ore-emerald] | 0% |
| [Emerald extra, Ocean only][p-ore-emerald_extra] | −16–116; uniform | 4 | [3][c-ore-emerald] | 0% |

The high Iron triangle samples to **367**: its `below_top: -48` offset adds 48 to the generator top of 319. That does not add rock above the terrain. Use the rock actually present when choosing a route; the sampling limit is not an ore-block boundary.

Ore-bearing geodes are another route with their own shape, layer and replacement checks. Keep the [Dry Midlands geode guide](../biomes/DryMidlands.md#mining-geodes-versus-ordinary-veins) and [Primordial Ocean resource guide](../biomes/SpecialBiomes.md#resources-and-terrain-conditions) alongside this chart. Their separate **Magma/Gravel vein definitions still name the missing `base_stone_dwarfhollow` host tag**; the ordinary ore-target correction does not fix those entries. Geodes use a different replacement path. [Magma configuration][custom-magma] · [Gravel configuration][custom-gravel] · [Geode example][geode-example]

The three bundled Primordial biome feature lists and their referenced feature definitions supply **no Nether Gold, Nether Quartz or Ancient Debris generation route**. That is a scoped conclusion about these bundled definitions, not a claim about commands, imported terrain or data-pack overrides. Use the Nether for the checked ore routes above.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. This review joins the bundled resource filenames, biome memberships, placement/configuration references and host tags to the server loading and generation callers. No game session, runtime registry test, world scan or ore-yield measurement was run. Existing generated chunks and data-pack overrides can differ.

The active chain loads world-generation registries from server resources, selects/bakes dimensions, then calls biome decoration, placement modifiers and the configured feature. [Registry types][registry-types] · [Resource loading][registry-load] · [Dedicated fresh/saved loading][server-create] · [Fresh bake][server-bake] · [Generation stage][decoration-stage] · [Placement][placed] · [Configured dispatch][configured] · [Feature registration][feature-registration]

For ordinary ores, Java supplies the origin/endpoints, random draws, host/air tests and writes; the active Rust kernel constructs and rasterizes candidate shapes. Debris scattering and the large-vein material decision remain separate algorithms. The large-vein density/noise inputs also pass through active native evaluators; none of the tables assumes an old Java geometry implementation or a fixed shape yield. [Java/native boundary][native-ore] · [Rust entry points][rust-ore] · [Shape construction][rust-shapes] · [Rasterization][rust-raster] · [Density caller][density-noise] · [Native binding][native-density] · [Rust density dispatch][rust-density] [rust-density-eval][] · [Noise evaluator][rust-noise]

Related: [Ores and Ancient Debris](../blocks/OreResources.md) · [Mining](Mining.md) · [Biomes](../biomes/Biomes.md) · [Dimensions](../dimensions/Dimensions.md)

[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L53
[registry-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L336
[registry-types]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L136
[server-create]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/Main.java#L221-L234
[server-bake]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/Main.java#L286-L312
[dimension-bake]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L183
[decoration-stage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L142-L156
[decoration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L381
[placed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L33-L60
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java#L22-L28
[configured]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L14-L28
[feature-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L109-L136
[ore]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java#L23-L130
[air-neighbors]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L205-L220
[scattered]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ScatteredOreFeature.java#L18-L49
[rarity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/RarityFilter.java#L24-L27
[count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/placement/CountPlacement.java#L30-L33
[height-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldGenerationContext.java#L10-L21
[generator-height]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L428-L439
[anchors]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/VerticalAnchor.java#L56-L97
[triangle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/heightproviders/TrapezoidHeight.java#L14-L63
[uniform]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/heightproviders/UniformHeight.java
[veinifier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/OreVeinifier.java#L23-L78
[noise-chunk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/NoiseChunk.java#L146-L172
[noise-fill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L384-L397
[material-rules]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/material/MaterialRuleList.java#L8-L21
[native-ore]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/NativeOreGeometry.java#L90-L135
[rust-ore]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/world/level/levelgen/feature/ore/ffi.rs#L30-L112
[rust-shapes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/world/level/levelgen/feature/ore/spheres.rs
[rust-raster]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/world/level/levelgen/feature/ore/geometry.rs
[density-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/DensityFunctions.java#L810-L830
[native-density]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/NativeDensityMath.java#L87-L103
[rust-density]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/world/level/levelgen/density/ffi.rs#L21-L66
[rust-density-eval]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/world/level/levelgen/density/evaluator.rs#L44-L66
[rust-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/world/level/levelgen/synth/dispatch.rs#L86-L143
[biome-presets]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L53-L113
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[primordial-dimension]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[overworld-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[nether-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json
[primordial_caves-noise]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/noise_settings/primordial_caves.json
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/overworld.json
[the_nether-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_nether.json
[primordial_caves-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[stone_ore_replaceables]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/stone_ore_replaceables.json
[deepslate_ore_replaceables]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/deepslate_ore_replaceables.json
[base_stone_nether]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/base_stone_nether.json
[p-ore_coal_upper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_coal_upper.json
[c-ore_coal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_coal.json
[p-ore_coal_lower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_coal_lower.json
[c-ore_coal_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_coal_buried.json
[p-ore_iron_upper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_iron_upper.json
[c-ore_iron]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_iron.json
[p-ore_iron_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_iron_middle.json
[p-ore_iron_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_iron_small.json
[c-ore_iron_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_iron_small.json
[p-ore_copper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_copper.json
[c-ore_copper_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_copper_small.json
[p-ore_copper_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_copper_large.json
[c-ore_copper_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_copper_large.json
[p-ore_gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold.json
[c-ore_gold_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_gold_buried.json
[p-ore_gold_lower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_lower.json
[p-ore_gold_extra]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_extra.json
[c-ore_gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_gold.json
[p-ore_redstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_redstone.json
[c-ore_redstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_redstone.json
[p-ore_redstone_lower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_redstone_lower.json
[p-ore_lapis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_lapis.json
[c-ore_lapis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_lapis.json
[p-ore_lapis_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_lapis_buried.json
[c-ore_lapis_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_lapis_buried.json
[p-ore_diamond]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_diamond.json
[c-ore_diamond_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_diamond_small.json
[p-ore_diamond_medium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_diamond_medium.json
[c-ore_diamond_medium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_diamond_medium.json
[p-ore_diamond_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_diamond_large.json
[c-ore_diamond_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_diamond_large.json
[p-ore_diamond_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_diamond_buried.json
[c-ore_diamond_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_diamond_buried.json
[p-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_emerald.json
[c-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_emerald.json
[p-ore_gold_nether]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_nether.json
[c-ore_nether_gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_nether_gold.json
[p-ore_gold_deltas]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_deltas.json
[p-ore_quartz_nether]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_quartz_nether.json
[c-ore_quartz]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_quartz.json
[p-ore_quartz_deltas]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_quartz_deltas.json
[p-ore_ancient_debris_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_ancient_debris_large.json
[c-ore_ancient_debris_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_ancient_debris_large.json
[p-ore_debris_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore_debris_small.json
[c-ore_ancient_debris_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore_ancient_debris_small.json
[p-ore-coal_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/coal_middle.json
[c-ore-coal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/coal.json
[p-ore-coal_high]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/coal_high.json
[p-ore-coal_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/coal_buried.json
[c-ore-coal_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/coal_buried.json
[p-ore-iron_low]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/iron_low.json
[c-ore-iron_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/iron_small.json
[p-ore-iron_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/iron_middle.json
[c-ore-iron]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/iron.json
[p-ore-iron_high]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/iron_high.json
[p-ore-iron_small_high]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/iron_small_high.json
[p-ore-copper_high]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/copper_high.json
[c-ore-copper_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/copper_small.json
[p-ore-copper_large_high]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/copper_large_high.json
[c-ore-copper_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/copper_large.json
[p-ore-gold_lower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/gold_lower.json
[c-ore-gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/gold.json
[p-ore-gold_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/gold_middle.json
[p-ore-gold_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/gold_buried.json
[c-ore-gold_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/gold_buried.json
[p-ore-redstone_low]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/redstone_low.json
[c-ore-redstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/redstone.json
[p-ore-redstone_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/redstone_middle.json
[p-ore-lapis_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/lapis_middle.json
[c-ore-lapis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/lapis.json
[p-ore-lapis_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/lapis_buried.json
[c-ore-lapis_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/lapis_buried.json
[p-ore-diamond_lower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/diamond_lower.json
[c-ore-diamond_medium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/diamond_medium.json
[p-ore-diamond_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/diamond_large.json
[c-ore-diamond_large]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/diamond_large.json
[p-ore-diamond_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/diamond_buried.json
[c-ore-diamond_buried]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/diamond_buried.json
[p-ore-diamond_medium_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/diamond_medium_middle.json
[p-ore-diamond_small_middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/diamond_small_middle.json
[c-ore-diamond_small]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/diamond_small.json
[p-ore-emerald]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/emerald.json
[c-ore-emerald]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/emerald.json
[p-ore-emerald_extra]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ore/emerald_extra.json
[b-forest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/forest.json
[b-dripstone_caves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/dripstone_caves.json
[b-badlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/badlands.json
[b-eroded_badlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/eroded_badlands.json
[b-wooded_badlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/wooded_badlands.json
[b-cherry_grove]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/cherry_grove.json
[b-meadow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/meadow.json
[b-grove]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/grove.json
[b-snowy_slopes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/snowy_slopes.json
[b-frozen_peaks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/frozen_peaks.json
[b-jagged_peaks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/jagged_peaks.json
[b-stony_peaks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/stony_peaks.json
[b-windswept_hills]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/windswept_hills.json
[b-windswept_gravelly_hills]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/windswept_gravelly_hills.json
[b-windswept_forest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/windswept_forest.json
[b-windswept_savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/windswept_savanna.json
[b-nether_wastes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[b-soul_sand_valley]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json
[b-crimson_forest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json
[b-warped_forest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[b-basalt_deltas]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/basalt_deltas.json
[b-primordial_plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json
[b-dry_midlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[b-primordial_ocean]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[custom-magma]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/magma.json
[custom-gravel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ore/gravel.json
[geode-example]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/geode/diamond.json
