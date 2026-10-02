# Plains and meadows

Choose **Plains or Sunflower Plains for farm-animal and equine candidates**, **Meadow for mixed flowers and occasional bee-bearing trees**, or **Cherry Grove for Cherry timber and Pink Petals**. All four are actual selections in the bundled Normal Overworld, rather than names available only through commands. Their feature lists tell the generator what to attempt; they do not promise a village, tree, flower patch, or animal at every stop. [Normal preset][normal] · [Loaded parameter preset][parameters] · [Preset dispatch][provider] · [Biome selection][selection]

## Choose a destination

| Destination | Main reason to visit | Planning difference |
| --- | --- | --- |
| [Plains](#plains) | Farm animals, Horses, Donkeys, mixed small flowers | Very sparse tree-attempt settings; find timber before settling |
| [Sunflower Plains](#sunflower-plains) | Sunflowers alongside the Plains animal roster | Not included in the checked Plains-village or Outpost biome tags |
| [Meadow](#meadow) | Mixed flowers, Wildflowers, Sheep, Rabbits, Donkeys | Tree placement passes a 1-in-100 rarity filter before other checks |
| [Cherry Grove](#cherry-grove) | Cherry trees and Pink Petals | Pig candidates replace Meadow's Donkeys; no Plains-village eligibility |

The sections below trace each difference to its biome and selected features. For denser timber choices, see [temperate forests and Mushroom Fields](TemperateForests.md).

## Plains

**ID: `minecraft:plains`.** This is the broadest livestock choice here: its creature table lists Sheep, Pigs, Chickens, Cows, Horses, and Donkeys. Its small-flower provider selects from Dandelion, Poppy, Azure Bluet, Oxeye Daisy, Cornflower, and four tulip colors according to its noise rules. Search more than one patch when collecting colors; this is a provider palette, not a promise that all species grow together. [Biome][biome-plains] · [Flower placement][place-flower_plains] · [Flower palette][config-flower_plain]

Do not depend on a thick wood supply. The tree placement chooses **zero attempts with weight 19 or one with weight 1**, then applies surface-water, height, sapling-survival, and biome checks. The selector includes ordinary/fancy Oak and a fallen-Oak branch. The standing tree configurations can attempt occupied Bee Nests, but a successful tree and free nest space are still required. Bring saplings or visit a neighboring [Forest](TemperateForests.md#forest) if you need sustained timber. [Tree placement][place-trees_plains] · [Tree selection][config-trees_plains] · [Oak configuration][config-oak_bees_005] · [Fancy Oak configuration][config-fancy_oak_bees_005] · [Nest checks][nest-call]

The biome also lists Pumpkin, Sugar Cane, and water-edge Firefly Bush features. Their presence in the list does not bypass each plant's placement rules. Use the existing [Pumpkin/Melon](../blocks/PumpkinAndMelon.md) and [Sugar Cane](../blocks/SugarCane.md) guides for collecting and farming a successful find. [Biome features][biome-plains]

## Sunflower Plains

**ID: `minecraft:sunflower_plains`.** Choose this variant when you want **Sunflowers** without giving up the Plains livestock and equine candidates. Its biome list adds the Sunflower patch route; the placed feature has a 1-in-3 rarity filter before location and biome checks, and the patch still needs air and valid plant support. That is an attempt filter, not a one-third map coverage claim. [Biome][biome-sunflower_plains] · [Sunflower placement][place-patch_sunflower] · [Patch configuration][config-patch_sunflower] · [Plant placement checks][plant-call]

Tree and ordinary small-flower routes match Plains, so plan around sparse timber here too. A useful navigation distinction is that **Sunflower Plains is absent from both the Plains-village and Pillager Outpost allowed-biome tags** in this snapshot. A nearby structure can belong to neighboring terrain; the biome name alone does not establish its start eligibility. See [Flowers](../blocks/Flowers.md#tall-flower-variants) for Sunflower collection, duplication, and uses. [Biome][biome-sunflower_plains] · [Village eligibility][tag-village_plains] · [Outpost eligibility][tag-pillager_outpost]

## Meadow

**ID: `minecraft:meadow`.** Meadow combines grass with a noise-selected palette of Allium, Poppy, Azure Bluet, Dandelion, Cornflower, and Oxeye Daisy, plus a separate **Wildflowers** feature. These are two different flower routes; Wildflowers are the low ground-cover block described in [flowerbeds and Leaf Litter](../blocks/FlowerbedsAndLeafLitter.md). Sheep, Rabbits, and Donkeys are the entire checked passive-creature list. It does not list the Plains Cows, Chickens, Horses, or Pigs. [Biome][biome-meadow] · [Flower palette][config-flower_meadow] · [Flower placement][place-flower_meadow] · [Wildflower placement][place-wildflowers_meadow] · [Wildflower configuration][config-wildflowers_meadow]

Meadow trees are **rare attempts**, with a 1-in-100 placement filter. Their selector chooses fancy Oak or tall Birch configurations whose nest decorators have probability 1.0. Even then a nest requires a successful tree and open placement space; this is not a guaranteed Bee at every Meadow or tree. Look for an occupied nest and follow [Bee](../mobs/Bee.md#finding-bees) and [Bee housing](../blocks/BeeHousing.md) for moving or using the colony. [Tree placement][place-trees_meadow] · [Tree selection][config-meadow_trees] · [Oak nest setting][config-fancy_oak_bees] · [Birch nest setting][config-super_birch_bees] · [Actual nest checks][nest-call]

Meadow participates in the selector's plateau choices, rather than defining a fixed elevation. It also adds the Emerald Ore and infested-block features discussed [below](#mining-and-encounter-limits), and qualifies for the Plains-village and Outpost routes. [Selection][selection] · [Biome][biome-meadow] · [Village tag][tag-village_plains] · [Outpost tag][tag-pillager_outpost]

## Cherry Grove

**ID: `minecraft:cherry_grove`.** Visit for **Cherry Logs, Cherry Leaves, and Pink Petals**. The loaded biome invokes a Cherry-tree feature and a flower feature whose configured states are Pink Petals. Its tree count selects ten or eleven attempts before water-depth, height, biome, and sapling-survival checks. This makes it a more substantial configured timber destination than the sparse tree settings in Plains or Meadow, without predicting how many complete trees a particular chunk contains. [Biome][biome-cherry_grove] · [Tree placement][place-trees_cherry] · [Tree configuration][config-cherry_bees_005] · [Petal placement][place-flower_cherry] · [Petal configuration][config-flower_cherry]

Cherry trees use a **5% nest-decorator probability**, followed by the actual nest-space check. The passive list contains Pigs, Rabbits, and Sheep; Bees enter through occupied generated nests rather than a Bee entry in that creature list. Cherry Grove also has the Emerald/infested feature pair and is in the mountain tag used by Outposts, but not in the Plains-village tag. Use [tree materials](../blocks/TreeLogsAndRoots.md), [tree leaves](../blocks/TreeLeaves.md), and [flowerbeds](../blocks/FlowerbedsAndLeafLitter.md) for harvesting and propagation. [Tree configuration][config-cherry_bees_005] · [Nest decorator][nest-call] · [Biome][biome-cherry_grove] · [Mountain tag][tag-is_mountain] · [Village tag][tag-village_plains]

## Structure search choices

These are the checked biome filters **among these four destinations**, not guaranteed discoveries or a complete structure list:

| Route | Plains | Sunflower Plains | Meadow | Cherry Grove |
| --- | --- | --- | --- | --- |
| Plains village | Eligible | Absent | Eligible | Absent |
| Pillager Outpost | Eligible | Absent | Eligible | Eligible |
| Ruined Portal variant | Standard | Standard | Mountain | Mountain |

The actual Village, Outpost, and Ruined Portal definitions use these tags, and their loaded structure sets schedule placement. Outposts additionally have a placement frequency filter and an exclusion zone around the Village set. World structure settings, start placement, and terrain checks still apply; being eligible is only one step. [Village definition][structure-village_plains] · [Village set][set-villages] · [Outpost definition][structure-pillager_outpost] · [Outpost set][set-pillager_outposts] · [Village tag][tag-village_plains] · [Outpost tag][tag-pillager_outpost] · [Mountain tag][tag-is_mountain] · [Standard portal tag][tag-ruined_portal_standard] · [Mountain portal tag][tag-ruined_portal_mountain] · [Portal set][set-ruined_portals] · [Generation caller][structure-call]

Use [Structures](../structures/Structures.md#finding-a-structure) for search commands and expedition preparation. Finding a village and moving an animal are separate tasks; use [Horse](../mobs/Horse.md), [Donkey](../mobs/Donkey.md), [Sheep](../mobs/Sheep.md), and [Rabbit](../mobs/Rabbit.md) for their care and transport rules.

## Mining and encounter limits

Meadow and Cherry Grove both add **Emerald Ore** and **infested Stone/Deepslate** attempts to their ordinary ore lists. The configurations target the bundled Stone/Deepslate replacement tags; placement still depends on height and a suitable host. The infested route is a reason to consult [Silverfish](../mobs/Silverfish.md) before mining unfamiliar blocks. Neither feature is a measured ore yield or a guarantee of an encounter. [Meadow][biome-meadow] · [Cherry Grove][biome-cherry_grove] · [Emerald placement][place-ore_emerald] · [Emerald configuration][config-ore_emerald] · [Infested placement][place-ore_infested] · [Infested configuration][config-ore_infested] · [Stone hosts][tag-stone_ore_replaceables] · [Deepslate hosts][tag-deepslate_ore_replaceables]

All four have populated ordinary monster lists, including Zombies, Skeletons, Creepers, Spiders, Slimes, Endermen, and Witches. Open or flower-covered ground is not a safety rule. Their animal entries also remain conditional: ordinary farm animals and equines require the animal-support tag, which contains Grass Block, and sufficient brightness; Rabbits use their broader ground tag and the same brightness threshold of **raw brightness above 8**. Natural spawning still checks placement, space, distance, and other restrictions. [Spawn caller][spawn-call] · [Registered checks][spawn-registration] · [Animal rule][animal-rule] · [Animal ground][tag-animals_spawnable_on] · [Rabbit rule][rabbit-rule] · [Rabbit ground][tag-rabbits_spawnable_on]

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. The review traced the Normal preset through the parameter provider to loaded biome JSON, selected placed/configured features, active feature/decorator callers, creature placement checks, and the structure filters above. No in-game terrain survey, generation-rate, spawn-rate, resource collection, or structure-search test was run. Seeds, data packs, custom presets, and already-generated terrain can differ. [World registry loading][world-load] · [Loaded registry codecs][load] · [Resource-file loader][load-files] · [Biome-feature execution][feature-call] · [Placement execution][placed-call] · [Tree/decorator execution][tree-call]

Related: [Biomes](Biomes.md) · [Temperate forests and Mushroom Fields](TemperateForests.md) · [Flowers](../blocks/Flowers.md)

[animal-rule]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[biome-cherry_grove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/cherry_grove.json
[biome-meadow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/meadow.json
[biome-plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/plains.json
[biome-sunflower_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/sunflower_plains.json
[config-cherry_bees_005]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/cherry_bees_005.json
[config-fancy_oak_bees]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/fancy_oak_bees.json
[config-fancy_oak_bees_005]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/fancy_oak_bees_005.json
[config-flower_cherry]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/flower_cherry.json
[config-flower_meadow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/flower_meadow.json
[config-flower_plain]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/flower_plain.json
[config-meadow_trees]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/meadow_trees.json
[config-oak_bees_005]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/oak_bees_005.json
[config-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_emerald.json
[config-ore_infested]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/ore_infested.json
[config-patch_sunflower]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/patch_sunflower.json
[config-super_birch_bees]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/super_birch_bees.json
[config-trees_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_plains.json
[config-wildflowers_meadow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/wildflowers_meadow.json
[feature-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L386
[load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[load-files]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L333
[nest-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L39-L67
[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[parameters]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[place-flower_cherry]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/flower_cherry.json
[place-flower_meadow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/flower_meadow.json
[place-flower_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/flower_plains.json
[place-ore_emerald]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_emerald.json
[place-ore_infested]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/ore_infested.json
[place-patch_sunflower]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_sunflower.json
[place-trees_cherry]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_cherry.json
[place-trees_meadow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_meadow.json
[place-trees_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_plains.json
[place-wildflowers_meadow]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/wildflowers_meadow.json
[placed-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L37-L65
[plant-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L17-L44
[provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[rabbit-rule]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Rabbit.java#L423-L427
[selection]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[set-pillager_outposts]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/pillager_outposts.json
[set-ruined_portals]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[set-villages]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[spawn-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L250-L325
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L113-L157
[structure-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L577
[structure-pillager_outpost]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json
[structure-village_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/village_plains.json
[tag-animals_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[tag-deepslate_ore_replaceables]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/deepslate_ore_replaceables.json
[tag-is_mountain]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/is_mountain.json
[tag-pillager_outpost]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json
[tag-rabbits_spawnable_on]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/rabbits_spawnable_on.json
[tag-ruined_portal_mountain]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_mountain.json
[tag-ruined_portal_standard]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ruined_portal_standard.json
[tag-stone_ore_replaceables]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/stone_ore_replaceables.json
[tag-village_plains]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_plains.json
[tree-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L126-L172
[world-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L32-L45
