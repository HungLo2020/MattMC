# Jungles and Swamps

Choose **Jungle** for a mixed jungle-tree route and Melon attempts, **Bamboo Jungle** for its dedicated Bamboo generation, and **Mangrove Swamp** for Mud and Mangrove trees. **Sparse Jungle** and ordinary **Swamp** have their own resource, animal and structure lists; a family name does not make those lists interchangeable.

## Choosing a destination

All IDs below use the `minecraft:` namespace. The comparison highlights selected differences; follow each exact-ID section for the constraints.

| Biome | Vegetation and material route | Distinctive listed candidates or exclusions |
| --- | --- | --- |
| [Jungle](#jungle) | Mixed jungle trees, light Bamboo, Melons, Vines | [Parrot](../mobs/Parrot.md) and [Panda](../mobs/Panda.md); Ocelot in the monster list |
| [Sparse Jungle](#sparse_jungle) | Sparse jungle-tree placement, sparse Melon placement, Vines | Wolf; no Parrot, Panda or Ocelot entry |
| [Bamboo Jungle](#bamboo_jungle) | Dedicated Bamboo, mixed vegetation and Melons | [Parrot](../mobs/Parrot.md), stronger [Panda](../mobs/Panda.md) list weight; Ocelot in the monster list |
| [Swamp](#swamp) | Swamp Oak, Water Lilies, Blue Orchid feature, Clay disks | Farm animals and Frog; Slime and Bogged in monster lists |
| [Mangrove Swamp](#mangrove_swamp) | Mud surface, Mangrove trees/roots, Water Lilies, Clay disks | Frog and Tropical Fish; no farm-animal entries |

The corresponding biome definitions are linked in every section. **Farm animals** means Sheep, Pig, Chicken and Cow. All five definitions allow precipitation. Weight comparisons describe candidate selection within a category, not real encounter rates.

## Jungle { #jungle }

**`minecraft:jungle`** combines `trees_jungle`, `bamboo_light`, Vines and Melon patches. The tree selector includes ordinary Jungle trees, Jungle bushes, mega Jungle trees, fancy Oak and a fallen-Jungle alternative. It is a mixed source of wood and vegetation, not a promise that every tree has the same size, leaves or decorations. The ordinary Jungle-tree configuration has a Cocoa decorator; the mega-tree configuration instead lists vine decorators, so a large Jungle tree does not by itself promise Cocoa. Use [Cocoa](../blocks/Cocoa.md), [Logs](../blocks/TreeLogsAndRoots.md) and [Leaves](../blocks/TreeLeaves.md) for harvesting. [Biome][jungle] · [Tree selector][configured-trees_jungle] · [Ordinary Jungle tree][configured-jungle_tree] · [Mega Jungle tree][configured-mega_jungle_tree] · [Active decorators][tree-decorators]

The light Bamboo placement selects the **no-Podzol** Bamboo configuration; Bamboo Jungle uses a different route described below. Melon generation selects replaceable, fluid-free positions above **Grass Block**, so a Melon entry does not make every forest-floor block suitable. [Bamboo placement][placed-bamboo_light] · [Bamboo configuration][configured-bamboo_no_podzol] · [Melon placement][placed-patch_melon] · [Melon support][configured-patch_melon]

Jungle lists **Parrot and Panda** creature candidates, plus **Ocelot in the monster list**. MattMC's Ocelot category routing and obstruction checks matter when searching; follow the [Ocelot guide](../mobs/Ocelot.md#finding-ocelots), which also distinguishes the animal from tameable Cats. Jungle is the only one of these five IDs in the bundled **Trail Ruins** tag, and is also eligible for Jungle Temples. [Jungle list][jungle] · [Trail Ruins tag][tag-trail_ruins] · [Temple tag][tag-jungle_temple]

## Sparse Jungle { #sparse_jungle }

**`minecraft:sparse_jungle`** uses a different tree selector and outer placement. `trees_sparse_jungle` supplies **2 or 3 outer attempts**, compared with **50 or 51** for `trees_jungle`. The sparse selector keeps ordinary Jungle trees, bushes, fancy Oak and fallen-Jungle options, but omits the mega-Jungle branch. These are placement inputs; failed support, water and space checks prevent them from being a tree count. Both placed features use an ocean-floor heightmap, a zero-water-depth limit and a biome filter. [Biome][sparse_jungle] · [Sparse placement][placed-trees_sparse_jungle] · [Jungle placement][placed-trees_jungle] · [Sparse selector][configured-trees_sparse_jungle] · [Tree checks][tree-checks]

Its Melon feature uses a **1-in-64 rarity filter**, versus the ordinary Jungle/Bamboo Jungle feature's **1-in-6**, while sharing the same Grass Block support configuration. Sparse Jungle has Vines but no Bamboo feature in the checked list, and its creature list adds Wolf rather than Parrot or Panda; it has no Ocelot entry. Neither the Jungle Temple nor Trail Ruins tag includes this ID. Choose the other Jungle variants when those specific resources, animals or structures are the goal. [Sparse biome][sparse_jungle] · [Sparse Melon placement][placed-patch_melon_sparse] · [Ordinary placement][placed-patch_melon] · [Rarity check][rarity-check] · [Melon configuration][configured-patch_melon] · [Temple tag][tag-jungle_temple] · [Trail Ruins tag][tag-trail_ruins]

## Bamboo Jungle { #bamboo_jungle }

**`minecraft:bamboo_jungle`** combines the dedicated `bamboo` feature with `bamboo_vegetation`, Vines and ordinary Melon patches. The Bamboo placement uses a noise-based count and the **some-Podzol** configuration. Its generator needs an empty starting space and support on which Bamboo survives; its Podzol branch changes suitable nearby dirt, rather than covering every floor block. The [Bamboo guide](../blocks/Bamboo.md) owns collection, planting, growth and uses. [Biome][bamboo_jungle] · [Bamboo placement][placed-bamboo] · [Configuration][configured-bamboo_some_podzol] · [Generator checks][bamboo-checks]

`bamboo_vegetation` separately selects fancy Oak, Jungle bushes and mega Jungle trees, with a Jungle-grass fallback. Bamboo itself and this vegetation mix are distinct entries; the latter is not an instruction to place only Bamboo. The Bamboo Jungle Panda entry has **weight 80**, compared with **1** in Jungle, and both list Parrot at weight 40. These numbers are candidate weights, not spawn percentages or promised animal counts. Bamboo Jungle also lists Ocelot in the monster category and is Jungle-Temple eligible, but is absent from the Trail Ruins tag. [Vegetation placement][placed-bamboo_vegetation] · [Selector][configured-bamboo_vegetation] · [Bamboo Jungle candidates][bamboo_jungle] · [Jungle comparison][jungle] · [Ocelot search constraints](../mobs/Ocelot.md#finding-ocelots) · [Temple tag][tag-jungle_temple] · [Trail Ruins tag][tag-trail_ruins]

## Swamp { #swamp }

**`minecraft:swamp`** is the Oak-based wetland in this comparison. Its tree feature points to **Swamp Oak**, whose configuration includes Oak logs/leaves and a leaf-vine decorator. The placement permits water depth up to 2 before applying the heightmap, biome and Oak-sapling-survival checks; this is not a guarantee that any flooded position grows a tree. [Biome][swamp] · [Tree placement][placed-trees_swamp] · [Swamp Oak configuration][configured-swamp_oak] · [Tree checks][tree-checks]

Its other entries include **Water Lilies, Clay disks, Seagrass, mushrooms, Sugar Cane, firefly bushes** and `flower_swamp`, whose configured block is **Blue Orchid**. Ordinary Swamp also has fossil attempts. Use [Clay and Bricks](../blocks/ClayAndBricks.md) and the relevant plant guides for recovery rather than inferring drops from generation. In contrast to Mangrove Swamp, its creature list contains the four farm animals as well as Frog. Both wetlands list Slime and Bogged in their monster data. [Swamp definition][swamp] · [Blue Orchid feature][configured-flower_swamp]

Only this exact wetland ID is eligible for **Swamp Huts** in the checked tag. Frog and surface-Slime conditions are shared below; a bright place suitable for Frog candidates is not automatically suitable for a surface-Slime attempt. [Hut tag][tag-swamp_hut] · [Frog conditions](#frogs-slimes-and-spawn-lists)

## Mangrove Swamp { #mangrove_swamp }

**`minecraft:mangrove_swamp`** is the checked natural **Mud** route: Normal's Overworld noise settings contain explicit Mangrove Swamp Mud surface branches, evaluated by the active surface generator. Use [Mud and Mud Bricks](../blocks/MudAndMudBricks.md#natural-supplies) for collection and the continuing Mud-building chain. A biome-wide name still does not imply that every ground block is Mud. [Surface rules][surface] · [Caller][surface-call] · [Active adapter][surface-apply] · [Loaded-rule compilation][surface-compile] · [Rule translation][surface-rules]

Its `trees_mangrove` feature selects ordinary and tall Mangrove configurations. Placement permits water depth up to 5 and applies biome/heightmap filters; the selected checked features test a Mangrove Propagule's survival before tree-space and root checks. Both tree configurations specify Mangrove logs/leaves and root placers, including Muddy Mangrove Roots in suitable mud positions. Use [Logs and Roots](../blocks/TreeLogsAndRoots.md), [hanging Propagules](../blocks/TreeLeaves.md#mangrove-propagules), and [planted Propagules](../blocks/SaplingsAndAzaleas.md#planted-mangrove-propagules) for exact recovery and growth behavior. [Biome][mangrove_swamp] · [Placement][placed-trees_mangrove] · [Selector][configured-mangrove_vegetation] · [Ordinary support][placed-mangrove_checked] · [Tall support][placed-tall_mangrove_checked] · [Ordinary configuration][configured-mangrove] · [Tall configuration][configured-tall_mangrove] · [Tree checks][tree-checks]

The creature list contains **Frog only**, while Tropical Fish appear in the water-ambient category. The checked list also includes Water Lilies, Seagrass, Clay/grass disks and fossil attempts; it lacks ordinary Swamp's Blue Orchid, mushroom and Sugar Cane entries. It is not Swamp-Hut eligible. Bring food or obtain it through a separately checked route rather than relying on ordinary Swamp's farm-animal list here. [Mangrove definition][mangrove_swamp] · [Swamp comparison][swamp] · [Hut tag][tag-swamp_hut]

## Frogs, Slimes and spawn lists

Both wetlands list Frog groups of **2–5**. The registered Frog predicate requires brightness above 8 and a block below from its support tag: **Grass Block, Mud, Mangrove Roots or Muddy Mangrove Roots**. Population, placement and obstruction checks remain separate. The [Frog guide](../mobs/Frog.md) owns variant selection, breeding, Tadpoles and Froglights. A Frog variant's biome tag is not an additional natural-spawn list. [Swamp list][swamp] · [Mangrove list][mangrove_swamp] · [Registration][spawn-registration] · [Frog predicate][frog-checks] · [Ground tag][frog-ground] · [Brightness][animal-checks]

Both biomes belong to the **surface-Slime** tag. That particular spawn branch requires a non-Peaceful world, **Y 51–69**, a random pass tied to moon brightness, and low enough local raw brightness for another random check, before normal mob checks. This is a conditional surface route; it is not a guarantee of Slimes after dark or on every moon phase. The Slime predicate also has a separate below-Y-40 slime-chunk branch, so the surface tag is not the complete spawning rule. [Surface tag][slime-biomes] · [Active Slime predicate][slime-checks]

The tables above are deliberately selective. Ordinary hostile entries remain present, and a friendly-looking biome or named animal candidate does not establish a safe encounter. [Natural spawn dispatcher][spawn-call]

## Selected structure routes

| Search target | Eligible IDs among these five | Active definition and placement set |
| --- | --- | --- |
| [Jungle Temple](../structures/JungleTemple.md) | `jungle`, `bamboo_jungle` | [Definition][structure-jungle_pyramid] · [Set][set-jungle_temples] · [Biome tag][tag-jungle_temple] |
| Trail Ruins | `jungle` | [Definition][structure-trail_ruins] · [Set][set-trail_ruins] · [Biome tag][tag-trail_ruins] |
| [Swamp Hut](../structures/SwampHut.md) | `swamp` | [Definition][structure-swamp_hut] · [Set][set-swamp_huts] · [Biome tag][tag-swamp_hut] |

These are selected routes, not a complete structure list. Structure-set placement, the definition's biome restriction and the structure's generation point all participate in the active generation path. A matching biome is search eligibility, not a guaranteed structure or loot result. For Trail Ruins materials and archaeology, use [Mud's natural supplies](../blocks/MudAndMudBricks.md#natural-supplies) and [Suspicious Sand and Gravel](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel). [Placement caller][structure-call] · [Biome gate][structure-biome] · [Generation-point filter][generation-point]

The Swamp Hut definition supplies **Witch monster candidates and Cat creature candidates inside the structure piece's bounds**, replacing those categories' ordinary biome lists there. The active lookup checks these overrides before falling back to the biome. That is distinct from general Swamp spawning and does not imply that all of the swamp produces Cats or only Witches. [Hut overrides][structure-swamp_hut] · [Active lookup][spawn-override] · [Natural-spawn caller][spawn-call]

## How these entries reach the world

The bundled **Normal** preset uses the Overworld multi-noise selection and Overworld noise settings. Its parameter provider calls the current biome selector, and the biome source samples that parameter list; the listed biomes are selected destinations, rather than merely registered names. World loading reads the biome, placed-feature, configured-feature and structure JSON registries. Data packs and other presets can replace these defaults. [Normal preset][normal] · [Parameter preset][parameters] · [Provider][selector-provider] · [Selection][selector] · [Biome sampling][biome-sampling] · [World loading][world-load] · [Registry types][registry-load] · [JSON loading][json-load]

A **feature entry is an attempt**, and a **spawn entry is a candidate**. Decoration dispatches the biome's placed features through their placement filters and configured generators. The biome filter checks the actual biome at each candidate position; support, height, water and space checks can still reject it. A listed mob also needs the active spawning system's placement, rule and obstruction checks. [Decoration][feature-call] · [Placement pipeline][placed-call] · [Configured dispatch][configured-call] · [Biome filter][biome-filter] · [Natural spawning][spawn-call]

For direct item access, see the [Inventory item browser](../mechanics/InventoryBrowser.md). Its ordinary-item insertion route works separately from exploring, harvesting or finding mobs, including when playing Survival; possession through the browser does not verify natural availability.

## Sources and verification

Source-reviewed on **2026-10-02** at `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`. This guide checks exactly five IDs, Normal-preset selection and loaded resources, the described vegetation and Mud routes, selected structure eligibility/overrides, and spawn constraints. No in-game biome survey, resource-collection, tree-growth, structure-search or natural-spawn-rate test was run. Seeds, terrain, data packs, world presets and spawn settings can change the result.

Related: [Biomes](Biomes.md) · [Deserts, Badlands and Savannas](DesertsBadlandsAndSavannas.md) · [Bamboo](../blocks/Bamboo.md) · [Frog](../mobs/Frog.md) · [Structures](../structures/Structures.md)

[jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/jungle.json
[configured-trees_jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_jungle.json
[configured-jungle_tree]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/jungle_tree.json
[configured-mega_jungle_tree]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/mega_jungle_tree.json
[tree-decorators]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L155-L160
[placed-bamboo_light]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/bamboo_light.json
[configured-bamboo_no_podzol]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/bamboo_no_podzol.json
[placed-patch_melon]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_melon.json
[configured-patch_melon]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/patch_melon.json
[tag-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trail_ruins.json
[tag-jungle_temple]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/jungle_temple.json
[sparse_jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/sparse_jungle.json
[placed-trees_sparse_jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_sparse_jungle.json
[placed-trees_jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_jungle.json
[configured-trees_sparse_jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/trees_sparse_jungle.json
[tree-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L113
[placed-patch_melon_sparse]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_melon_sparse.json
[rarity-check]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/RarityFilter.java#L20-L23
[bamboo_jungle]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/bamboo_jungle.json
[placed-bamboo]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/bamboo.json
[configured-bamboo_some_podzol]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/bamboo_some_podzol.json
[bamboo-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/BambooFeature.java#L30-L73
[placed-bamboo_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/bamboo_vegetation.json
[configured-bamboo_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/bamboo_vegetation.json
[swamp]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[placed-trees_swamp]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_swamp.json
[configured-swamp_oak]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/swamp_oak.json
[configured-flower_swamp]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/flower_swamp.json
[tag-swamp_hut]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/swamp_hut.json
[surface]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json
[surface-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L225-L263
[surface-apply]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java#L101-L159
[surface-compile]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java#L55-L70
[surface-rules]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java#L101-L164
[mangrove_swamp]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json
[placed-trees_mangrove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/trees_mangrove.json
[configured-mangrove_vegetation]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/mangrove_vegetation.json
[placed-mangrove_checked]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/mangrove_checked.json
[placed-tall_mangrove_checked]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/placed_feature/tall_mangrove_checked.json
[configured-mangrove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/mangrove.json
[configured-tall_mangrove]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/configured_feature/tall_mangrove.json
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L100-L173
[frog-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L377-L381
[frog-ground]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/block/frogs_spawnable_on.json
[animal-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[slime-biomes]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/tags/worldgen/biome/allows_surface_slime_spawns.json
[slime-checks]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/entity/monster/Slime.java#L272-L300
[spawn-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L248-L325
[structure-jungle_pyramid]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/jungle_pyramid.json
[set-jungle_temples]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/jungle_temples.json
[structure-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/trail_ruins.json
[set-trail_ruins]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/trail_ruins.json
[structure-swamp_hut]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure/swamp_hut.json
[set-swamp_huts]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/structure_set/swamp_huts.json
[structure-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L463-L491
[structure-biome]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L542-L577
[generation-point]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L202-L206
[spawn-override]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[normal]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L2-L13
[parameters]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[selector-provider]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[selector]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[biome-sampling]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSource.java#L40-L77
[world-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/server/WorldLoader.java#L32-L46
[registry-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[json-load]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L282-L334
[feature-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L385
[placed-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[configured-call]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L24-L26
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cfed1bb2ac5db4457ec7654a9418120f59bcf69d/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java#L21-L27
