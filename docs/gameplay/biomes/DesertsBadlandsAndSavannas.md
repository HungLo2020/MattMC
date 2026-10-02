# Deserts, Badlands and Savannas

Choose **Desert** for Sand, desert structures and the checked Camel route; choose **Badlands** for terracotta terrain and additional Gold generation attempts; choose **Savanna** for Acacia-oriented tree generation and grazing-animal candidates. These seven biomes differ even when their warm, dry climate looks similar.

## Choosing a destination

All IDs below use the `minecraft:` namespace. Each exact ID has its own section; the comparison lists selected differences, not every underground feature or monster.

| Biome | Useful distinction | Selected animal candidates |
| --- | --- | --- |
| [Desert](#desert) | Sand/Sandstone surface rules; Cactus, dry grass, desert wells | Rabbit, Camel |
| [Badlands](#badlands) | Red Sand/terracotta rules; extra Gold feature | Farm animals, Armadillo |
| [Wooded Badlands](#wooded_badlands) | Badlands resources plus Oak tree feature | Farm animals, Armadillo, Wolf |
| [Eroded Badlands](#eroded_badlands) | Badlands resources plus the eroded-pillar surface extension | Farm animals, Armadillo |
| [Savanna](#savanna) | Acacia/Oak tree selection; Savanna village eligibility | Farm animals, Horse, Donkey, Armadillo |
| [Savanna Plateau](#savanna_plateau) | Same vegetation feature list as Savanna; additional animal entries | Savanna candidates plus Llama and Wolf |
| [Windswept Savanna](#windswept_savanna) | Stone/Coarse Dirt surface branches; different tree and grass placements | Farm animals, Horse, Donkey, Armadillo |

Here, **farm animals** means Sheep, Pig, Chicken and Cow entries. All seven definitions disable precipitation. The table's biome evidence is linked in each section; terrain comes from the loaded [Overworld surface rules][surface], applied by the [surface-generation caller][surface-call], [active surface adapter][surface-apply] and [rule compiler][surface-rules].

## Desert { #desert }

**`minecraft:desert`** is the dry option for Sand and Sandstone, with Cactus, dead-bush and dry-grass feature entries. Its bundled list has no tree feature, so take wood and food for an extended search. Use [Sand and soil](../blocks/SoilSandAndGravel.md), [Sandstone](../blocks/Sandstone.md) and [Cactus](../blocks/Cactus.md) for collection and handling. The biome also includes fossil and desert-well attempts. [Desert definition][desert] · [Surface rules][surface]

A desert well is a **feature**, separate from the structure routes below. Its placed feature has a rarity filter of 1 in 1,000 candidate passes, a surface heightmap and biome filter; its generator requires Sand and rejects a footprint with unsupported holes below. Those filters do not mean one well is guaranteed per thousand chunks. Successfully generated wells place Suspicious Sand with a desert-well archaeology loot assignment: follow the [brushing and fragile-block guide](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel) before digging. [Well placement][placed-desert_well] · [Rarity check][rarity-check] · [Configuration][configured-desert_well] · [Terrain checks][well-checks] · [Archaeology assignment][well-archaeology]

The creature list contains **Rabbit and Camel**, while the monster list includes **Husk**. For the natural Camel route, the support tag expands to Sand, Red Sand and Suspicious Sand, and the predicate requires brightness above 8; ordinary ground and population checks still apply. Natural Husks additionally need a view of the sky and the monster rules. The [Camel guide](../mobs/Camel.md) owns the separate village-template route, riding and feeding details. [Desert list][desert] · [Spawn registration][spawn-registration] · [Camel predicate][camel-checks] · [Camel ground][camel-ground] · [Sand tag][sand-tag] · [Brightness][animal-checks] · [Husk predicate][husk-checks]

## Badlands { #badlands }

**`minecraft:badlands`** is a terracotta and Red Sand search destination, with Cactus, dead-bush and dry-grass entries. The loaded surface rules include terracotta banding and Red Sand/Red Sandstone branches; they do not make every exposed block the same material. There is no tree feature in this biome's checked list. Bring wood, and use the [Terracotta](../blocks/Terracotta.md) and [Sand and soil](../blocks/SoilSandAndGravel.md) owners for mining, colors and falling-block rules. [Badlands definition][badlands] · [Surface rules][surface]

Badlands includes `ore_gold_extra` in addition to its ordinary Gold placements. The same extra feature is present in both variants below. This is a reason to investigate suitable rock, rather than expecting Gold to replace colored terracotta; see [the exact resource constraint](#extra-gold-and-animal-support). Armadillos and all four farm animals are listed, but the ordinary farm-animal support rule accepts Grass Block, so an animal entry is not evidence that a bare terracotta patch can spawn livestock. [Badlands list][badlands] · [Animal predicate][animal-checks] · [Animal ground tag][animal-ground]

## Wooded Badlands { #wooded_badlands }

**`minecraft:wooded_badlands`** keeps the badlands extra-Gold and dry-vegetation entries and adds `trees_badlands`. That feature selects an Oak leaf-litter tree by default, with a fallen-Oak alternative. Its outer placement checks an Oak sapling's ability to survive, uses an ocean-floor heightmap and allows no water depth. The surface rules also have a Wooded Badlands branch for high ground with Grass Block/Coarse Dirt outcomes. Expect trees only where the full placement succeeds. [Biome][wooded_badlands] · [Tree placement][placed-trees_badlands] · [Tree selector][configured-trees_badlands] · [Surface rules][surface] · [Tree space checks][tree-checks]

This is the only one of these three Badlands definitions with a **Wolf** entry. Wolves have their own support tag and brightness check; terracotta alone does not satisfy that tag. The [tree-log](../blocks/TreeLogsAndRoots.md), [leaf](../blocks/TreeLeaves.md) and [sapling](../blocks/SaplingsAndAzaleas.md) guides own harvesting and replanting, while [Wolf](../mobs/Wolf.md) covers the animal. [Biome candidates][wooded_badlands] · [Wolf predicate][wolf-checks] · [Wolf ground][wolf-ground]

## Eroded Badlands { #eroded_badlands }

**`minecraft:eroded_badlands`** shares Badlands' checked feature and spawn lists, including extra Gold, Cactus and Armadillo candidates, and likewise has no tree feature. Its distinguishing source route is the **eroded-badlands pillar extension** invoked by the surface generator before ordinary surface rules. The extension uses terrain noise and can abandon a column when it encounters Water, so the biome name does not promise a pillar at every position. [Biome][eroded_badlands] · [Extension caller][surface-apply] · [Pillar algorithm][eroded-extension]

Plan a route around the actual terrain rather than a fixed promised height or pillar density. For material collection and animal support, use the same Badlands guidance above; Eroded Badlands does not receive a separate richer Gold configuration or a Wooded Badlands tree list in the checked data. [Badlands comparison][badlands] · [Eroded comparison][eroded_badlands]

## Savanna { #savanna }

**`minecraft:savanna`** is the primary choice here for **Acacia**: `trees_savanna` selects checked Acacia, fallen Oak or ordinary checked Oak features. The living-tree routes point to configurations using the corresponding logs and leaves, with sapling-survival checks before the tree generator's space checks. Tall grass, Savanna grass and warm-flower features accompany this tree list. Use [Logs and Roots](../blocks/TreeLogsAndRoots.md), [Leaves](../blocks/TreeLeaves.md) and [Saplings](../blocks/SaplingsAndAzaleas.md) to take a continuing supply home. [Biome][savanna] · [Tree placement][placed-trees_savanna] · [Selector][configured-trees_savanna] · [Acacia support][placed-acacia_checked] · [Oak support][placed-oak_checked] · [Acacia materials][configured-acacia] · [Oak materials][configured-oak] · [Tree checks][tree-checks]

Its creature list adds **Horse, Donkey and Armadillo** to the four farm-animal entries. Of the three Savanna variants, only this exact ID appears in the bundled Savanna-village biome tag; do not expand that eligibility to the Plateau or Windswept names. [Biome candidates][savanna] · [Village tag][tag-village_savanna]

## Savanna Plateau { #savanna_plateau }

**`minecraft:savanna_plateau`** has the **same checked feature list as Savanna**, including `trees_savanna`, tall grass and the ordinary ore suite. Its useful encounter difference is the additional **Llama and Wolf** entries. The biome selector uses a separate plateau table, but this page does not infer a fixed surface elevation from the name. [Plateau definition][savanna_plateau] · [Savanna comparison][savanna] · [Selector][selector]

For a Llama or Wolf search, both the candidate list and usable terrain matter: Llamas use the ordinary animal predicate, while Wolves use their separate support tag. Reaching the Plateau does not guarantee either animal, and the Savanna village tag does not include this ID. [Spawn registration][spawn-registration] · [Animal predicate][animal-checks] · [Wolf predicate][wolf-checks] · [Wolf support][wolf-ground] · [Village tag][tag-village_savanna]

## Windswept Savanna { #windswept_savanna }

**`minecraft:windswept_savanna`** uses `trees_windswept_savanna`, which points to the same Acacia/Oak selector as ordinary Savanna but uses a different outer attempt count. Its flower and grass entries are `flower_default` and `patch_grass_normal`, rather than the Savanna/tall-grass combination. Surface rules add noise-dependent **Stone and Coarse Dirt** outcomes. These differences are useful when choosing scenery or looking for exposed rock, but they do not establish a fixed tree density or a guaranteed mining yield. [Biome][windswept_savanna] · [Tree placement][placed-trees_windswept_savanna] · [Shared selector][configured-trees_savanna] · [Surface rules][surface]

The checked creature candidates match ordinary Savanna, including Horse, Donkey and Armadillo; the Plateau's Llama and Wolf additions are absent. There is no `ore_gold_extra` entry here, and the Savanna village tag omits it. Use the [mining guide](../blocks/OreResources.md) for suitable tools before breaking exposed ore. [Windswept list][windswept_savanna] · [Plateau comparison][savanna_plateau] · [Village tag][tag-village_savanna]

## Extra Gold and animal support

All three Badlands variants reference one extra Gold placement: **50 attempted starting positions with a uniform Y range of 32–256**, followed by the biome filter. The configuration uses a vein size parameter of 9 and targets the stone/deepslate replacement tags. These are generator inputs, not fifty veins, nine guaranteed blocks per vein, a best-mining-height claim or a chunk yield. The tags include Stone/Granite/Diorite/Andesite and Deepslate/Tuff, **not Terracotta or Red Sand**; the active ore placer tests the target before writing. The [Ore Resources guide](../blocks/OreResources.md) remains the owner of pickaxe tiers, drops and processing. [Extra placement][placed-ore_gold_extra] · [Gold configuration][configured-ore_gold] · [Stone targets][ore-stone] · [Deepslate targets][ore-deepslate] · [Placement checks][ore-checks]

Armadillos differ from ordinary farm animals here: their support tag includes the ordinary animal group, badlands terracotta, Red Sand and Coarse Dirt. Their registered predicate still requires brightness above 8. This makes terrain support relevant when comparing a Badlands route to a grassy Savanna, without turning either list into an encounter guarantee. [Spawn registration][spawn-registration] · [Armadillo predicate][armadillo-checks] · [Support tag][armadillo-ground] · [Brightness][animal-checks]

## Selected structure routes

| Search target | Eligible IDs in this guide | Active definition and placement set |
| --- | --- | --- |
| [Desert Pyramid](../structures/DesertPyramid.md) | `desert` | [Definition][structure-desert_pyramid] · [Set][set-desert_pyramids] · [Biome tag][tag-desert_pyramid] |
| Desert Village | `desert` | [Definition][structure-village_desert] · [Village set][set-villages] · [Biome tag][tag-village_desert] |
| Savanna Village | `savanna` | [Definition][structure-village_savanna] · [Village set][set-villages] · [Biome tag][tag-village_savanna] |
| Mesa-type Mineshaft | `badlands`, `wooded_badlands`, `eroded_badlands` | [Definition][structure-mineshaft_mesa] · [Mineshaft set][set-mineshafts] · [Eligibility][tag-mineshaft_mesa] · [Expanded Badlands tag][badlands-tag] |

This is a selected search comparison, not a complete structure inventory or loot guide. The generator checks the structure set's placement, the structure's valid biome and its generation point; eligibility is not a promise of a building in every biome patch. Leave time to search, and use the [Camel guide](../mobs/Camel.md#finding-a-camel) for the separately verified Desert-village Camel route. [Placement caller][structure-call] · [Biome gate][structure-biome] · [Generation-point filter][generation-point]

## How these entries reach the world

The bundled **Normal** preset uses the Overworld multi-noise selection and Overworld noise settings. Its parameter provider calls the current biome selector, and the biome source samples that parameter list; the listed biomes are selected destinations, rather than merely registered names. World loading reads the biome, placed-feature, configured-feature and structure JSON registries. Data packs and other presets can replace these defaults. [Normal preset][normal] · [Parameter preset][parameters] · [Provider][selector-provider] · [Selection][selector] · [Biome sampling][biome-sampling] · [World loading][world-load] · [Registry types][registry-load] · [JSON loading][json-load]

A **feature entry is an attempt**, and a **spawn entry is a candidate**. Decoration dispatches the biome's placed features through their placement filters and configured generators. The biome filter checks the actual biome at each candidate position; support, height, water and space checks can still reject it. A listed mob also needs the active spawning system's placement, rule and obstruction checks. [Decoration][feature-call] · [Placement pipeline][placed-call] · [Configured dispatch][configured-call] · [Biome filter][biome-filter] · [Natural spawning][spawn-call]

For direct item access, see the [Inventory item browser](../mechanics/InventoryBrowser.md). Its ordinary-item insertion route works separately from exploring, harvesting or finding mobs, including when playing Survival; possession through the browser does not verify natural availability.

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. This guide checks exactly seven IDs, Normal-preset selection and loaded resources, the described surface/feature routes, selected structure eligibility and spawn constraints. No in-game generation survey, structure search, ore-yield, harvesting or natural-spawn test was run. These are source-backed defaults, not measured encounter or resource rates.

Related: [Biomes](Biomes.md) · [Jungles and Swamps](JunglesAndSwamps.md) · [Ore Resources](../blocks/OreResources.md) · [Structures](../structures/Structures.md)

[surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[surface-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L225-L263
[surface-apply]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L101-L159
[surface-rules]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java#L101-L164
[desert]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/desert.json
[placed-desert_well]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/desert_well.json
[rarity-check]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/RarityFilter.java#L20-L23
[configured-desert_well]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/desert_well.json
[well-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java#L29-L46
[well-archaeology]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java#L100-L111
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L100-L173
[camel-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/camel/Camel.java#L134-L138
[camel-ground]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/camels_spawnable_on.json
[sand-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/sand.json
[animal-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[husk-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/monster/Husk.java#L23-L28
[badlands]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/badlands.json
[animal-ground]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[wooded_badlands]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/wooded_badlands.json
[placed-trees_badlands]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_badlands.json
[configured-trees_badlands]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_badlands.json
[tree-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L113
[wolf-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L667-L671
[wolf-ground]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/wolves_spawnable_on.json
[eroded_badlands]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/eroded_badlands.json
[eroded-extension]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L211-L236
[savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/savanna.json
[placed-trees_savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_savanna.json
[configured-trees_savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_savanna.json
[placed-acacia_checked]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/acacia_checked.json
[placed-oak_checked]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/oak_checked.json
[configured-acacia]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/acacia.json
[configured-oak]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/oak.json
[tag-village_savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_savanna.json
[savanna_plateau]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/savanna_plateau.json
[selector]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[windswept_savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/windswept_savanna.json
[placed-trees_windswept_savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_windswept_savanna.json
[placed-ore_gold_extra]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_gold_extra.json
[configured-ore_gold]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_gold.json
[ore-stone]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/stone_ore_replaceables.json
[ore-deepslate]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/deepslate_ore_replaceables.json
[ore-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/OreFeature.java#L74-L121
[armadillo-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L223-L227
[armadillo-ground]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/armadillo_spawnable_on.json
[structure-desert_pyramid]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/desert_pyramid.json
[set-desert_pyramids]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/desert_pyramids.json
[tag-desert_pyramid]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/desert_pyramid.json
[structure-village_desert]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/village_desert.json
[set-villages]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[tag-village_desert]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_desert.json
[structure-village_savanna]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/village_savanna.json
[structure-mineshaft_mesa]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/mineshaft_mesa.json
[set-mineshafts]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/mineshafts.json
[tag-mineshaft_mesa]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/mineshaft_mesa.json
[badlands-tag]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/is_badlands.json
[structure-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L463-L491
[structure-biome]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L542-L577
[generation-point]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L202-L206
[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L2-L13
[parameters]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[selector-provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-sampling]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSource.java#L40-L77
[world-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L32-L46
[registry-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[json-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L282-L334
[feature-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L385
[placed-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[configured-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L24-L26
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java#L21-L27
[spawn-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L248-L325
