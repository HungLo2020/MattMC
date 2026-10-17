# Taiga and snowy plains biomes

Choose **Taiga for Spruce timber and village searches, old-growth taigas for larger tree forms and mixed forest-floor materials, Snowy Plains for snowy-village or igloo searches, and Ice Spikes for Packed Ice formations**. These six exact biome IDs are part of the bundled Normal Overworld selection. A nearby example, tree, structure, or resource is not guaranteed. [Normal preset][normal] · [Parameter preset][parameters] · [Preset wiring][preset-provider] · [Selection][selector]

## Compare the six biomes

| Biome | Exact ID | Main reason to explore |
| --- | --- | --- |
| [Taiga](#taiga) | `minecraft:taiga` | Spruce-tree and berry-patch attempts; eligible taiga village and trail ruins |
| [Old Growth Pine Taiga](#old-growth-pine-taiga) | `minecraft:old_growth_pine_taiga` | Mega Pine and Mega Spruce candidates; Podzol, Coarse Dirt and Mossy Cobblestone features |
| [Old Growth Spruce Taiga](#old-growth-spruce-taiga) | `minecraft:old_growth_spruce_taiga` | Mega Spruce candidates and the same old-growth ground/rock routes |
| [Snowy Taiga](#snowy-taiga) | `minecraft:snowy_taiga` | Spruce trees with cold-weather cover; eligible igloos and trail ruins |
| [Snowy Plains](#snowy-plains) | `minecraft:snowy_plains` | Snowy-village and igloo searches; sparse tree attempts |
| [Ice Spikes](#ice-spikes) | `minecraft:ice_spikes` | Snow Block surfaces with Packed Ice spike and patch attempts |

The selector uses several climate-noise coordinates, not a compass direction or a fixed altitude. Its cold middle/plateau tables distinguish snowy plains and taigas by humidity; positive-weirdness variants include Ice Spikes and Old Growth Pine Taiga. Other inland branches can select different terrain, so this is not a promise that following a particular slope leads to the next biome. [Tables and branch selection][selector] · [Active sampled lookup][selected-biome]

## Taiga

**ID: `minecraft:taiga`.** This is a useful first stop for a Spruce-wood route: its tree selector can choose Pine-shaped, Spruce-shaped, and fallen Spruce trees. The checked standing-tree configurations use **Spruce Logs and Spruce Leaves**; “pine” names a tree shape here, not a separate Pine wood resource. Placement checks still require suitable ground, available height and space. Use [tree logs](../blocks/TreeLogsAndRoots.md), [leaves](../blocks/TreeLeaves.md), and [saplings](../blocks/SaplingsAndAzaleas.md) for collection and replanting. [Biome list][taiga] · [Tree placement][placed-trees_taiga] · [Selector][config-trees_taiga] · [Pine materials][config-pine] · [Spruce materials][config-spruce] · [Ground filter][placed-spruce_checked] · [Tree checks][tree-checks] · [Fallen placement][placed-fallen_spruce_tree] · [Fallen material][config-fallen_spruce_tree] · [Fallen-log checks][fallen-checks]

The list also includes large ferns, mushrooms, and the common berry placement. That placement's first filter passes with probability **1/32**; the patch then requires air above Grass Blocks and surviving plants. It is not one successful food patch per 32 chunks. Carry food until you actually find a patch, and use [Sweet Berry Bush](../blocks/SweetBerryBush.md) for harvesting and contact behavior. [Common placement][placed-patch_berry_common] · [Rarity check][berry-rarity] · [Patch contents and ground][config-patch_berry_bush] · [Patch attempts][patch-checks] · [Plant survival][plant-checks]

## Old Growth Pine Taiga

**ID: `minecraft:old_growth_pine_taiga`.** Explore this variant when you want the larger Spruce-family tree forms. Its selector includes **both Mega Pine and Mega Spruce**, alongside smaller Pine/Spruce and fallen Spruce choices. All four inspected standing-tree configurations use Spruce wood; the two mega forms have different foliage configurations. Selection is sequential and placement may fail, so the selector weights are not a measured tree composition or a promised timber yield. [Biome list][old_growth_pine_taiga] · [Placement][placed-trees_old_growth_pine_taiga] · [Selector][config-trees_old_growth_pine_taiga] · [Mega Pine][config-mega_pine] · [Mega Spruce][config-mega_spruce] · [Sequential selection][select-tree]

Both old-growth taigas have conditional **Podzol and Coarse Dirt** surface rules and a **Mossy Cobblestone** forest-rock feature. The rock generator searches downward for suitable dirt/stone support; the entry does not promise a boulder at every stop. These are material-search routes, not village indicators. See [soil and ground materials](../blocks/SoilSandAndGravel.md) for Podzol/Coarse Dirt collection rules. [Loaded surface rules][surfaces] · [Rock placement][placed-forest_rock] · [Rock material][config-forest_rock] · [Ground checks][rock-checks]

## Old Growth Spruce Taiga

**ID: `minecraft:old_growth_spruce_taiga`.** This variant shares the Podzol/Coarse Dirt surface, forest-rock, common berry, fern, dead-bush and mushroom entries above. Its distinctive tree selector includes **Mega Spruce but no Mega Pine choice**, while retaining smaller Pine/Spruce and fallen Spruce candidates. Choose it for that large-tree form; do not assume every tree is giant. Neither old-growth variant is in the bundled taiga-village start tag. [Biome list][old_growth_spruce_taiga] · [Placement][placed-trees_old_growth_spruce_taiga] · [Selector][config-trees_old_growth_spruce_taiga] · [Village tag][tag-village_taiga]

## Snowy Taiga

**ID: `minecraft:snowy_taiga`.** This uses the same main tree feature as Taiga, but its cold climate supports Snow/Ice top-layer checks. Its berry placement uses a **1/384** initial rarity filter, versus 1/32 in the other three taigas. That is a difference in attempted patches, not a guarantee of a particular food density after ground and plant checks. Pack food rather than depending on the trees to imply abundant berries. [Biome list][snowy_taiga] · [Rare berries][placed-patch_berry_rare] · [Common berries][placed-patch_berry_common] · [Rarity check][berry-rarity]

Search this biome for **igloos or trail ruins**, subject to structure placement. A snowy Spruce forest is not the bundled snowy-village route: Snowy Taiga is absent from both the snowy- and taiga-village start tags. [Igloos][tag-igloo] · [Trail ruins][tag-trail_ruins] · [Snowy villages][tag-village_snowy] · [Taiga villages][tag-village_taiga]

## Snowy Plains

**ID: `minecraft:snowy_plains`.** This is the snowy-village route among these six biomes and is also eligible for igloos and Pillager Outposts. The vegetation list still includes a tree feature, but its count draws **zero attempts nine times out of ten, otherwise one**, before water, biome, sapling-survival and tree checks. Treat it as a place to bring timber, not a reliable forest supply. [Biome list][snowy_plains] · [Sparse tree placement][placed-trees_snowy] · [Tree selector][config-trees_snowy] · [Structure choices](#structures-and-animal-searches)

Rabbits and Polar Bears are the creature-list candidates here. **Strays join the monster list**; their natural-spawn predicate also requires sky access above the checked column, alongside ordinary monster restrictions. This does not establish a spawn rate or make sheltered areas universally safe. [Spawn list][snowy_plains] · [Spawn registration][spawn-registrations] · [Stray check][stray-checks]

## Ice Spikes

**ID: `minecraft:ice_spikes`.** Seek this variant for **Packed Ice**. Its conditional surface rules supply Snow Blocks, the spike generator requires a Snow Block starting surface, and the separate ice-patch placement also filters for Snow Blocks before applying a Packed Ice disk configuration. The spike and patch counts are attempts: suitable starting blocks, terrain and replaceable targets still matter. Use [Ice](../blocks/Ice.md#harvesting-and-recipes) before collecting; a generated block and its recoverable mining drop are separate questions. [Biome list][ice_spikes] · [Surfaces][surfaces] · [Spike placement][placed-ice_spike] · [Spike dispatch][config-ice_spike] · [Spike checks and material][spike-checks] · [Patch placement][placed-ice_patch] · [Patch material/targets][config-ice_patch] · [Disk checks][disk-checks]

It shares Snowy Plains' Rabbit, Polar Bear, and Stray candidates and still lists the sparse snowy-tree feature. That entry does not overcome its sapling-ground restriction. Bring wood and provisions. Ice Spikes is absent from the checked village, igloo, trail-ruin and outpost start tags; use Snowy Plains or Snowy Taiga when one of those is the actual goal. [Biome list][ice_spikes] · [Sparse tree filter][placed-trees_snowy] · [Structure choices](#structures-and-animal-searches)

## Snow cover and preparation

Every biome here lists `freeze_top_layer`, but the name does not mean every surface freezes. The generator asks the local biome whether Water can freeze and Snow can survive, including temperature, light, height and block checks. [Placement][placed-freeze_top_layer] · [Configured generator][config-freeze_top_layer] · [Top-layer caller][freeze-checks] · [Environmental checks][cold-checks]

The [Snow and Powder Snow guide](../blocks/Snow.md) owns harvesting, snowfall and freezing behavior. **Grove and Snowy Slopes** have the checked mountain Powder Snow surface route; see [mountain and windswept biomes](MountainsAndWindsweptBiomes.md#grove) before treating pale ground as a safe solid path. Frozen-ocean icebergs are a separate route covered by [ocean biomes](Oceans.md#vegetation-and-resources).

## Structures and animal searches

This table is restricted to the six IDs on this page. Eligibility means a structure can pass its start-biome filter, not that each eligible biome contains one or that all its pieces stay inside that biome.

| Goal | Eligible starts among these six |
| --- | --- |
| Taiga village | Taiga |
| Snowy village | Snowy Plains |
| Igloo | Snowy Plains, Snowy Taiga |
| Trail ruins | Taiga, Snowy Taiga, both old-growth taigas |
| Pillager Outpost | Taiga, Snowy Plains |

The definitions point to the checked biome tags; villages share a placement set, and igloos, trail ruins and outposts have their own placement settings. The caller must still obtain a valid generation point and start. This table is not a complete inventory of underground structures or loot. [Taiga-village tag][tag-village_taiga] · [Snowy-village tag][tag-village_snowy] · [Igloo tag][tag-igloo] · [Trail-ruin tag][tag-trail_ruins] · [Outpost tag][tag-pillager_outpost] · [Taiga definition][structure-village_taiga] · [Snowy definition][structure-village_snowy] · [Igloo definition][structure-igloo] · [Trail-ruin definition][structure-trail_ruins] · [Outpost definition][structure-pillager_outpost] · [Village set][set-villages] · [Igloo set][set-igloos] · [Trail-ruin set][set-trail_ruins] · [Outpost set][set-pillager_outposts] · [Generation caller][structure-start] · [Biome check][structure-biome]

All four taigas list **Sheep, Pigs, Chickens, Cows, Wolves, Rabbits and Foxes** in their creature tables; Snowy Plains and Ice Spikes instead list Rabbits and Polar Bears. Every biome here also has a monster list. These are candidates, not guaranteed encounters: placement, creature-specific predicates, distance, population and collision checks still apply. At an Outpost, the structure's full-bounding-box monster override supplies Pillager candidates before the biome fallback. [Taiga][taiga] · [Pine][old_growth_pine_taiga] · [Spruce][old_growth_spruce_taiga] · [Snowy Taiga][snowy_taiga] · [Snowy Plains][snowy_plains] · [Ice Spikes][ice_spikes] · [Natural-spawn checks][spawn-checks] · [Population checks][spawn-limits] · [Outpost override][structure-pillager_outpost] · [Active override lookup][structure-spawns] · [Spawn caller][spawn-caller]

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. Checked Normal selection, the six loaded biome definitions, surface dispatch, selected tree/berry/rock/ice chains, spawn candidates and selected structure-start routes. No world-generation survey, resource-yield, spawn-rate, structure-search or travel gameplay test was run. Seeds, data packs, world presets and previously generated terrain can change results.

The world loader reads the biome, feature, structure, and noise-setting registries from resources. The biome's feature list reaches the decoration caller, which applies the placed feature's filters and dispatches its configured generator. These guides describe the bundled data and checked callers; a feature name alone does not establish successful placement. [World loading][world-loader] · [Registry types][registry-loader] · [Resource loading][resource-loader] · [Biome fields][biome-codec] · [Decoration caller][decoration] · [Placed dispatch][placed-dispatch] · [Configured dispatch][configured-dispatch] · [Generator registrations][feature-registry] · [Biome filter][biome-filter]

Loaded surface settings reach the chunk surface builder and its native evaluator. Surface statements above describe conditional rules, not uniform cover or a measured distribution. [Surface caller][surface-caller] · [Surface system][surface-system] · [Native evaluator][native-surface]

Related: [Biomes](Biomes.md) · [Mountain and windswept biomes](MountainsAndWindsweptBiomes.md) · [Ore resources](../blocks/OreResources.md) · [Structures](../structures/Structures.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[parameters]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[preset-provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[selector]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[selected-biome]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSource.java#L40-L77
[taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[placed-trees_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_taiga.json
[config-trees_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_taiga.json
[config-pine]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/pine.json
[config-spruce]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/spruce.json
[placed-spruce_checked]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/spruce_checked.json
[tree-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L113
[placed-fallen_spruce_tree]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/fallen_spruce_tree.json
[config-fallen_spruce_tree]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/fallen_spruce_tree.json
[fallen-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/FallenTreeFeature.java#L31-L89
[placed-patch_berry_common]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_berry_common.json
[berry-rarity]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/RarityFilter.java#L23-L26
[config-patch_berry_bush]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/patch_berry_bush.json
[patch-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java#L17-L39
[plant-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L44
[old_growth_pine_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/old_growth_pine_taiga.json
[placed-trees_old_growth_pine_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_old_growth_pine_taiga.json
[config-trees_old_growth_pine_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_old_growth_pine_taiga.json
[config-mega_pine]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/mega_pine.json
[config-mega_spruce]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/mega_spruce.json
[select-tree]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/RandomSelectorFeature.java#L17-L31
[surfaces]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[placed-forest_rock]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/forest_rock.json
[config-forest_rock]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/forest_rock.json
[rock-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/BlockBlobFeature.java#L17-L52
[old_growth_spruce_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/old_growth_spruce_taiga.json
[placed-trees_old_growth_spruce_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_old_growth_spruce_taiga.json
[config-trees_old_growth_spruce_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_old_growth_spruce_taiga.json
[tag-village_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_taiga.json
[snowy_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/snowy_taiga.json
[placed-patch_berry_rare]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_berry_rare.json
[tag-igloo]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/igloo.json
[tag-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trail_ruins.json
[tag-village_snowy]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_snowy.json
[snowy_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/snowy_plains.json
[placed-trees_snowy]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_snowy.json
[config-trees_snowy]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_snowy.json
[spawn-registrations]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L113-L167
[stray-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/monster/Stray.java#L25-L36
[ice_spikes]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/ice_spikes.json
[placed-ice_spike]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ice_spike.json
[config-ice_spike]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ice_spike.json
[spike-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/IceSpikeFeature.java#L19-L101
[placed-ice_patch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ice_patch.json
[config-ice_patch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ice_patch.json
[disk-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/DiskFeature.java
[placed-freeze_top_layer]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/freeze_top_layer.json
[config-freeze_top_layer]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/freeze_top_layer.json
[freeze-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SnowAndFreezeFeature.java#L19-L50
[cold-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/Biome.java#L151-L211
[tag-pillager_outpost]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json
[structure-village_taiga]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/village_taiga.json
[structure-village_snowy]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/village_snowy.json
[structure-igloo]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/igloo.json
[structure-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/trail_ruins.json
[structure-pillager_outpost]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json
[set-villages]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[set-igloos]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/igloos.json
[set-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/trail_ruins.json
[set-pillager_outposts]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/pillager_outposts.json
[structure-start]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L456-L581
[structure-biome]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L202-L206
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[spawn-limits]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L102-L128
[structure-spawns]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-caller]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L325
[world-loader]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-loader]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[resource-loader]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L326
[biome-codec]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/Biome.java#L38-L46
[decoration]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L353-L380
[placed-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L39-L60
[configured-dispatch]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java
[surface-caller]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L225-L262
[surface-system]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L66-L164
[native-surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java
