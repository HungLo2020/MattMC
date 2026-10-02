# Mountain and windswept biomes

Choose **Grove for snowy Spruce trees, Snowy Slopes or cold peaks for Goat searches, Frozen Peaks for exposed ice materials, Stony Peaks for Stone/Calcite terrain, and Windswept Forest for more tree attempts than the other two windswept hills**. These eight exact IDs are selected by the bundled Normal Overworld. Bring food, timber for exposed routes, and a deliberate way back down. [Normal preset][normal] · [Parameter preset][parameters] · [Preset wiring][preset-provider] · [Selection][selector]

## Compare the eight biomes

Materials below are conditional surface-rule outputs, not an uninterrupted floor. Spawn lists identify creature-category candidates, not guaranteed animals or the complete set of entities possible there. [Surface settings][surfaces] · [Generation caller][surface-caller]

| Biome | Exact ID | Terrain/resource distinction | Creature candidates |
| --- | --- | --- | --- |
| [Grove](#grove) | `minecraft:grove` | Snow Blocks/Powder Snow; Spruce-family tree attempts | Wolf, Rabbit, Fox |
| [Snowy Slopes](#snowy-slopes) | `minecraft:snowy_slopes` | Snow/Powder Snow with Stone on steep surfaces; no tree feature | Rabbit, Goat |
| [Frozen Peaks](#frozen-peaks) | `minecraft:frozen_peaks` | Packed Ice, Ice, Snow Blocks; no tree feature | Goat |
| [Jagged Peaks](#jagged-peaks) | `minecraft:jagged_peaks` | Stone and Snow Blocks; no tree feature | Goat |
| [Stony Peaks](#stony-peaks) | `minecraft:stony_peaks` | Stone with conditional Calcite; no tree feature | Empty creature list |
| [Windswept Hills](#windswept-hills) | `minecraft:windswept_hills` | Conditional exposed Stone; sparse Oak/Spruce-family attempts | Sheep, Pig, Chicken, Cow, Llama |
| [Windswept Gravelly Hills](#windswept-gravelly-hills) | `minecraft:windswept_gravelly_hills` | Gravel/Stone/grass-and-dirt surface choices; sparse trees | Sheep, Pig, Chicken, Cow, Llama |
| [Windswept Forest](#windswept-forest) | `minecraft:windswept_forest` | More attempts of the same mixed tree selector | Sheep, Pig, Chicken, Cow, Llama |

The eight definitions supply these feature and creature lists: [Grove][grove], [Slopes][snowy_slopes], [Frozen][frozen_peaks], [Jagged][jagged_peaks], [Stony][stony_peaks], [Hills][windswept_hills], [Gravelly Hills][windswept_gravelly_hills], and [Forest][windswept_forest]. Each also has Bat, Glow Squid and monster candidates in other categories; an empty creature list does not mean a safe or entirely mob-free biome.

## Finding the right mountain

The selector combines temperature, humidity, continentalness, erosion, depth and weirdness. In its colder slope choices, drier humidity slots select **Snowy Slopes** and wetter ones select **Grove**. Its colder peak branch switches **Jagged Peaks/Frozen Peaks** with the weirdness branch; a warmer peak slot selects **Stony Peaks**. The windswept family comes from a separate table. These names do not guarantee a summit height, fixed geographic direction, or that one biome always borders another. [Branch tables and selection][selector] · [Active sampled lookup][selected-biome]

## Grove

**ID: `minecraft:grove`.** This is the wooded choice among the snowy slopes and peaks on this page. Its selector chooses Pine- or Spruce-shaped trees made from **Spruce Logs/Leaves**. The snow-aware nested placements scan upward out of Powder Snow and require Snow Block or Powder Snow beneath; tree height and clearance checks still apply. Follow [tree logs](../blocks/TreeLogsAndRoots.md) and [saplings](../blocks/SaplingsAndAzaleas.md) for timber and replanting behavior. [Biome list][grove] · [Tree placement][placed-trees_grove] · [Selector][config-trees_grove] · [Pine placement][placed-pine_on_snow] · [Spruce placement][placed-spruce_on_snow] · [Pine materials][config-pine] · [Spruce materials][config-spruce] · [Tree checks][tree-checks]

Its Snow Block surface can contain **Powder Snow**, with Dirt below other portions. Wear [Leather Boots for Powder Snow walking](../blocks/Snow.md#powder-snow-buckets-and-collision) and carry an empty Bucket if you want to collect it. Tree cover is not evidence that all pale footing is solid. Wolves, Rabbits and Foxes are candidates; this biome's creature list does not include the four ordinary farm animals or Goats. [Surface rules][surfaces] · [Spawn list][grove]

## Snowy Slopes

**ID: `minecraft:snowy_slopes`.** This has the other checked **Powder Snow** surface route, alongside Snow Blocks and exposed Stone under the steep-surface condition. Its feature list has no trees, so supply timber before committing to a longer climb. Rabbits and Goats are creature candidates. Goat spawning still checks its allowed-ground tag and brightness, so a mountainside is not a guarantee of a herd. [Surface rules][surfaces] · [Biome list][snowy_slopes] · [Goat registration][spawn-registrations] · [Goat checks][goat-checks] · [Allowed ground][tag-goats_spawnable_on]

Snowy Slopes is eligible for **igloos and Pillager Outposts**. It is absent from the snowy-village start tag. Use the structure comparison below when deciding whether to keep climbing or return to [Snowy Plains](TaigaAndSnowyBiomes.md#snowy-plains). [Igloo tag][tag-igloo] · [Outpost tag][tag-pillager_outpost] · [Mountain membership][tag-is_mountain] · [Snowy-village tag][tag-village_snowy]

## Frozen Peaks

**ID: `minecraft:frozen_peaks`.** Choose this for surface-rule routes to **Packed Ice and ordinary Ice**, mixed with Snow Blocks and fallback Stone. Steepness and distinct ice-noise tests affect which material is selected. This is different from the [Ice Spikes](TaigaAndSnowyBiomes.md#ice-spikes) feature route: Frozen Peaks' checked list has no ice-spike feature. [Surface rules][surfaces] · [Biome list][frozen_peaks]

Goat is the only creature-category entry, and there is no tree feature. Bring wood and provisions; consult [Ice harvesting](../blocks/Ice.md#harvesting-and-recipes) before planning a collection trip. The surface's ice appearance does not certify the mining drop or make the route safe from falls.

## Jagged Peaks

**ID: `minecraft:jagged_peaks`.** This is the Stone-and-Snow Block counterpart to Frozen Peaks. Its dedicated surface rules use Stone on steep faces and below the snow cover, rather than Frozen Peaks' special Packed Ice/Ice branches. Goat is again the only creature-category candidate and its list has no tree feature. Choose it for a rocky snowy route, not because its name proves a particular cliff shape or peak height. [Surface rules][surfaces] · [Biome list][jagged_peaks]

## Stony Peaks

**ID: `minecraft:stony_peaks`.** Its dedicated surface rule chooses **Calcite where the Calcite noise condition matches, otherwise Stone**. This gives a surface Calcite search route separate from the underground Amethyst Geode entry. Neither a Calcite band nor a geode is promised at your stopping point. [Surface rules][surfaces] · [Biome list][stony_peaks]

The loaded creature list is empty: **Goats are not ordinary biome-list candidates here**, despite this being a peak biome. Monsters and other categories still have entries, and arriving or structure-related entities are separate. There are no tree features. Plan food and timber before exploring the exposed terrain. [Spawn and feature lists][stony_peaks]

## Windswept Hills

**ID: `minecraft:windswept_hills`.** This uses a surface-noise condition for exposed Stone and lists a mixed tree selector with Spruce, Oak, Fancy Oak and fallen-tree choices. Its tree-count distribution is **zero attempts with weight 9, one with weight 1**, before water-depth, heightmap, biome and nested ground checks. A few visible trees do not imply a continuous timber supply. [Biome list][windswept_hills] · [Surface rules][surfaces] · [Tree placement][placed-trees_windswept_hills] · [Mixed selector][config-trees_windswept_hills]

Sheep, Pigs, Chickens, Cows and Llamas are the creature candidates. For a longer timber stop, compare Windswept Forest below; for a Goat search, choose Snowy Slopes, Frozen Peaks or Jagged Peaks instead. These distinctions concern the loaded lists, not a barrier preventing mobs from walking across biome boundaries. [Hills list][windswept_hills] · [Slopes list][snowy_slopes] · [Frozen list][frozen_peaks] · [Jagged list][jagged_peaks]

## Windswept Gravelly Hills

**ID: `minecraft:windswept_gravelly_hills`.** Surface noise selects among **Gravel, Stone and grass-and-dirt rules**, so the name does not mean the entire surface is Gravel. It shares Windswept Hills' sparse tree placement and the same farm-animal/Llama creature candidates. Use [soil, sand and gravel](../blocks/SoilSandAndGravel.md) for block behavior and collection. [Loaded surfaces][surfaces] · [Biome list][windswept_gravelly_hills] · [Shared tree placement][placed-trees_windswept_hills]

## Windswept Forest

**ID: `minecraft:windswept_forest`.** The practical distinction is **more tree attempts**, not a completely different tree family. Its placed feature points to the same mixed selector as Windswept Hills but draws **three attempts with weight 9, four with weight 1**. The resulting tree count still depends on water, space, ground and biome checks. It lists the same Sheep, Pig, Chicken, Cow and Llama candidates as the other two windswept biomes. [Biome list][windswept_forest] · [Forest placement][placed-trees_windswept_forest] · [Hills placement][placed-trees_windswept_hills] · [Shared selector][config-trees_windswept_hills] · [Tree checks][tree-checks]

## Mining and snow precautions

All eight lists include **Emerald Ore and infested-stone feature entries**, in addition to their ordinary ore features. The configurations target the Stone/Deepslate ore-replaceable tags. A selected height in open air, snow or an unsuitable material does not turn into ore merely because the biome is mountainous. The Emerald placement's sampled range even extends beyond the Normal world's buildable terrain; it must not be read as a mountain height or a guaranteed yield. Use [Ore resources](../blocks/OreResources.md) for tools, drops and processing. [Emerald placement][placed-ore_emerald] · [Emerald targets][config-ore_emerald] · [Infested placement][placed-ore_infested] · [Infested targets][config-ore_infested] · [Stone targets][tag-stone_ore_replaceables] · [Deepslate targets][tag-deepslate_ore_replaceables] · [Placement, build-height and target checks][ore-checks] · [Normal terrain settings][surfaces]

Every list also includes the top-layer snow/freezing feature, including warmer Stony Peaks. It still asks the biome's temperature and block/light checks whether Snow or Ice can form; its presence is not a promise of snow cover. Keep [Snow/Powder Snow](../blocks/Snow.md) and [Ice](../blocks/Ice.md) as the owners of collision, freezing, melting and harvesting behavior. [Top-layer placement][placed-freeze_top_layer] · [Configured feature][config-freeze_top_layer] · [Top-layer caller][freeze-checks] · [Environmental checks][cold-checks]

## Structures and exploration

| Search goal | Eligible starts among these eight IDs |
| --- | --- |
| Igloo | Snowy Slopes |
| Pillager Outpost | Grove, Snowy Slopes, Frozen Peaks, Jagged Peaks, Stony Peaks |
| Mountain-style Ruined Portal | Snowy Slopes, all three peaks, all three windswept biomes |
| Standard-style Ruined Portal | Grove |

The nested mountain and hill tags have different membership: **the three windswept biomes are in the hill tag, not the mountain tag used by outposts**. Grove has its own explicit outpost entry. None of these eight is in the checked taiga- or snowy-village start tag. This is selected structure-start eligibility, not a complete structure catalog or a promise of a nearby structure. [Igloo tag][tag-igloo] · [Outpost tag][tag-pillager_outpost] · [Mountain tag][tag-is_mountain] · [Hill tag][tag-is_hill] · [Mountain portal tag][tag-ruined_portal_mountain] · [Standard portal tag][tag-ruined_portal_standard] · [Taiga-village tag][tag-village_taiga] · [Snowy-village tag][tag-village_snowy]

The definitions use those tags, while structure sets and generation-point checks decide whether a start is actually produced. An Outpost's full-bounding-box monster override supplies Pillager candidates instead of the biome monster list there; their other spawn checks still apply. [Igloo definition][structure-igloo] · [Outpost definition][structure-pillager_outpost] · [Mountain portal definition][structure-ruined_portal_mountain] · [Standard portal definition][structure-ruined_portal_standard] · [Igloo set][set-igloos] · [Outpost set][set-pillager_outposts] · [Portal set][set-ruined_portals] · [Generation caller][structure-start] · [Biome check][structure-biome] · [Active spawn override][structure-spawns] · [Spawn caller][spawn-caller] · [Natural-spawn checks][spawn-checks]

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. Checked Normal selection, all eight loaded biome definitions, conditional surfaces, selected tree/snow/ore chains, spawn candidates and selected structure tags/definitions/sets. No world-generation survey, climb, resource-yield, spawn-rate or structure-search gameplay test was run. Seeds, data packs, presets and previously generated terrain can change results.

The world loader reads the biome, feature, structure, and noise-setting registries from resources. The biome's feature list reaches the decoration caller, which applies the placed feature's filters and dispatches its configured generator. These guides describe the bundled data and checked callers; a feature name alone does not establish successful placement. [World loading][world-loader] · [Registry types][registry-loader] · [Resource loading][resource-loader] · [Biome fields][biome-codec] · [Decoration caller][decoration] · [Placed dispatch][placed-dispatch] · [Configured dispatch][configured-dispatch] · [Generator registrations][feature-registry] · [Biome filter][biome-filter]

The surface builder passes the loaded surface-rule tree through the native evaluator; the comparisons describe those rules rather than measured terrain distributions. [Surface caller][surface-caller] · [Surface system][surface-system] · [Native evaluator][native-surface]

Related: [Biomes](Biomes.md) · [Taiga and snowy plains biomes](TaigaAndSnowyBiomes.md) · [Snow/Powder Snow](../blocks/Snow.md) · [Ice](../blocks/Ice.md) · [Structures](../structures/Structures.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[parameters]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[preset-provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[selector]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[surfaces]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[surface-caller]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L225-L262
[grove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/grove.json
[snowy_slopes]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/snowy_slopes.json
[frozen_peaks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/frozen_peaks.json
[jagged_peaks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/jagged_peaks.json
[stony_peaks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/stony_peaks.json
[windswept_hills]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/windswept_hills.json
[windswept_gravelly_hills]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/windswept_gravelly_hills.json
[windswept_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/windswept_forest.json
[selected-biome]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSource.java#L40-L77
[placed-trees_grove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_grove.json
[config-trees_grove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_grove.json
[placed-pine_on_snow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/pine_on_snow.json
[placed-spruce_on_snow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/spruce_on_snow.json
[config-pine]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pine.json
[config-spruce]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/spruce.json
[tree-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L113
[spawn-registrations]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L113-L167
[goat-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/goat/Goat.java#L374-L379
[tag-goats_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/goats_spawnable_on.json
[tag-igloo]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/igloo.json
[tag-pillager_outpost]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json
[tag-is_mountain]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/is_mountain.json
[tag-village_snowy]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_snowy.json
[placed-trees_windswept_hills]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_windswept_hills.json
[config-trees_windswept_hills]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_windswept_hills.json
[placed-trees_windswept_forest]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_windswept_forest.json
[placed-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_emerald.json
[config-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_emerald.json
[placed-ore_infested]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_infested.json
[config-ore_infested]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_infested.json
[tag-stone_ore_replaceables]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/stone_ore_replaceables.json
[tag-deepslate_ore_replaceables]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/deepslate_ore_replaceables.json
[ore-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java#L23-L132
[placed-freeze_top_layer]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/freeze_top_layer.json
[config-freeze_top_layer]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/freeze_top_layer.json
[freeze-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SnowAndFreezeFeature.java#L19-L50
[cold-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/Biome.java#L151-L211
[tag-is_hill]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/is_hill.json
[tag-ruined_portal_mountain]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_mountain.json
[tag-ruined_portal_standard]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_standard.json
[tag-village_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_taiga.json
[structure-igloo]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/igloo.json
[structure-pillager_outpost]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json
[structure-ruined_portal_mountain]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/ruined_portal_mountain.json
[structure-ruined_portal_standard]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/ruined_portal.json
[set-igloos]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/igloos.json
[set-pillager_outposts]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/pillager_outposts.json
[set-ruined_portals]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[structure-start]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L456-L581
[structure-biome]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L202-L206
[structure-spawns]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-caller]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L325
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[world-loader]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-loader]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[resource-loader]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L326
[biome-codec]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/Biome.java#L38-L46
[decoration]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L353-L380
[placed-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L39-L60
[configured-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java
[surface-system]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L66-L164
[native-surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java
