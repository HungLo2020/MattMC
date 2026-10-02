# Saplings and Azalea growth

Choose the planting pattern before spending Bone Meal: **Dark Oak and Pale Oak need a matching 2 × 2 square**, while Spruce and Jungle can use either one sapling or four. Mangrove Propagules and the two Azalea shrubs follow different growth callbacks. This guide compares the eight ordinary Overworld saplings, planted Mangrove Propagules, Azalea, and Flowering Azalea. [Oak](Oak.md#planting-and-growing-saplings) keeps its detailed oak-growing instructions; [Tree Leaves](TreeLeaves.md#mangrove-propagules) keeps the hanging-propagule collection loop.

## Ordinary sapling comparison

Each ID below is in the `minecraft:` namespace. All eight are registered as `SaplingBlock` with their named grower and random ticking enabled. “2 × 2” means four copies of the **same block type**, side by side at the same height. Four different species do not count as a square. [Sapling registrations][s1] · [Grower selections][s2] · [Matching-square check][s3]

| Sapling ID | Single planting | Matching 2 × 2 planting | Family difference |
| --- | --- | --- | --- |
| <span id="oak-sapling">`oak_sapling`</span> | Supported | No combined-tree route | See [Oak](Oak.md#planting-and-growing-saplings) for its normal/fancy choice and flower interaction |
| <span id="spruce-sapling">`spruce_sapling`</span> | Spruce | Mega Spruce or Mega Pine, each 50% per large-tree selection | Both large features use Spruce wood and can alter nearby ground to Podzol |
| <span id="birch-sapling">`birch_sapling`</span> | Birch | No combined-tree route | Nearby flowers select its bee-capable configuration |
| <span id="jungle-sapling">`jungle_sapling`</span> | Single Jungle configuration without vine decorators | Mega Jungle | The large configuration includes trunk and leaf vine decorators |
| <span id="acacia-sapling">`acacia_sapling`</span> | Acacia | No combined-tree route | One configured single-tree route |
| <span id="cherry-sapling">`cherry_sapling`</span> | Cherry | No combined-tree route | Nearby flowers select its bee-capable configuration |
| <span id="dark-oak-sapling">`dark_oak_sapling`</span> | No tree route | Dark Oak | A lone sapling can advance its hidden stage but cannot produce a tree |
| <span id="pale-oak-sapling">`pale_oak_sapling`</span> | No tree route | Pale Oak planted configuration | Its selected `pale_oak_bonemeal` feature has no decorators, including no Creaking Heart decorator |

The feature name `pale_oak_bonemeal` is also selected when a Pale Oak sapling advances through **natural random ticks**; it is not restricted to Bone Meal use. For all families without a combined-tree route, adjacent saplings remain individual growing attempts. The exact selected feature files are listed in the [evidence matrix](#growth-feature-and-loot-matrix). [Natural advancement][s4] · [Single versus large feature dispatch][s5] · [pale_oak_bonemeal][s6]

### Ground, stages, and Bone Meal

Plant ordinary saplings on **Farmland or a dirt-tag block**. The bundled dirt tag includes Dirt, Grass Block, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. Clay is not in that tag. These eight saplings have no waterlogged state. Keeping a sapling planted and having enough room for its eventual tree are separate checks. [Vegetation support][s7] · [Dirt tag][s8] · [Sapling state][s9] · [State definition][s10] · [Item placement survival check][s11]

The eight ordinary saplings share the same two-step advancement:

1. A selected random tick needs **local raw brightness at least 9 above the sapling**, then a **1-in-7** roll
2. An advance at hidden stage 0 only changes it to stage 1
3. A later advance at stage 1 attempts the selected tree, subject to its pattern and space checks

Bone Meal gives a **45% chance to advance**, with **no brightness test** in that path. An accepted Survival application consumes one Bone Meal even when the 45% roll fails, the first success only changes the hidden stage, or a later tree attempt is obstructed. Neither the 45% chance nor the 1-in-7 roll is a promise of a tree on that use or tick. [Sapling advancement][s12] · [Bone Meal callback and consumption][s13] · [Selected random-tick dispatch][s14]

For a matching 2 × 2 square, advance **one** sapling to its tree attempt. The other three only need to be the same sapling block; the matching check does not require their hidden stages to be 1. If a large-tree placement is attempted and fails, that attempt does not fall back to a single tree. Spruce and Jungle use their single route when no matching square is found; Dark Oak and Pale Oak have no such fallback. [Large-tree attempt and failure return][s15] · [Type-only square test][s16]

### Flowers and bee-capable trees

Birch and Cherry join Oak in checking the **flower block tag** within two blocks horizontally and one block vertically of the sapling: a **5 × 3 × 5 box**, not just the four adjacent ground cells. The tag includes ordinary flowers, Flowering Azalea and its leaves, Cherry Leaves, and Mangrove Propagules. Birch and Cherry select their bee-capable tree configuration when this search succeeds. [Family flower routes][s17] · [Flower search][s18] · [Flower tag][s19] · [Small flowers][s20]

The Birch and Cherry configurations each give the nest decorator a **5% roll**. A successful roll still needs a suitable trunk-adjacent air cell and air in front of the nest, so flowers do not guarantee a nest. [Oak](Oak.md#bone-meal) owns the corresponding oak explanation. [birch_bees_005][s21] · [cherry_bees_005][s22] · [Nest placement checks][s23]

## Planted Mangrove Propagules

**`minecraft:mangrove_propagule`** uses the Mangrove grower once planted. Follow [Tree Leaves](TreeLeaves.md#mangrove-propagules) for obtaining a mature propagule, valid soil or Clay, and source-water placement. Its hanging age and its tree-growth stage are different properties: the collected item places at **age 4 but stage 0**. Being fully mature for collection does not skip the first tree-growth advance. [Default and placed states][s24] · [Mangrove registration][s25]

A planted propagule has a **1-in-7 advancement roll on a selected random tick, without the ordinary sapling brightness check**. It still advances stage 0 to stage 1 before attempting a tree. Bone Meal uses the inherited **45% advancement chance** while planted. The hanging form instead matures and never enters this tree-growing branch. [Planted versus hanging callbacks][s26] · [Inherited stages and Bone Meal][s27]

Mangrove uses a single propagule, with **85% Tall Mangrove and 15% Mangrove selection** per tree attempt. These are feature-selection probabilities, not success rates or final tree-height guarantees. Both routes use the Mangrove root placer, which checks the space up to the raised trunk origin and simulates roots before placing them; a valid planting support alone does not guarantee root placement succeeds. [Mangrove selection][s28] · [Secondary-feature selection][s29] · [mangrove][s30] · [tall_mangrove][s31] · [Root placement checks][s32]

Both selected Mangrove features include hanging-propagule and vine decorators, plus a **1% bee-nest decorator roll without a flower requirement**. These are possible generated additions, not guaranteed drops or fixed counts. The hanging-propagule decorator requires empty space below a leaf and can create immature propagules; use the [maturation guide](TreeLeaves.md#mangrove-propagules) before harvesting them. [Mangrove decorators][s33] · [Tall Mangrove decorators][s34] · [Leaf-attachment checks][s35] · [Nest placement][s36]

## Azalea and Flowering Azalea

<span id="azalea"></span><span id="flowering-azalea"></span>

**`minecraft:azalea`** and **`minecraft:flowering_azalea`** both use `AzaleaBlock` and the **same Azalea tree feature**. Both accept Farmland, the dirt tag, or Clay beneath them. They do not have a sapling-stage property or a natural random-tick growth callback, and their registrations do not enable random ticking. They also have no waterlogged state. [Both shrub registrations][s37] · [Shrub support and growth][s38] · [Default vegetation support][s39]

Use Bone Meal while the **fluid state directly above is empty**. This tests for fluid, not for an air block: a dry solid block overhead can still allow Bone Meal consumption even though it obstructs the tree. An accepted use has a **45% chance to attempt the tree directly**, with no hidden stage advance and no brightness test. Bone Meal is consumed on a failed probability roll or a failed placement attempt. Four shrubs together do not select a large-tree route. [Azalea Bone Meal rules][s40] · [Consumption][s41] · [Azalea grower][s42]

Both shrubs produce a tree with **Oak Logs**, a mix of Azalea and Flowering Azalea Leaves, and a Rooted Dirt provider beneath the trunk. The feature forces that ground replacement through its bending-trunk placer. There is no distinct “Azalea Log” in this selected route. Keep the [Tree Leaves drop table](TreeLeaves.md#leaf-families-and-drops) in mind when collecting the shrubs again. [azalea_tree][s43] · [Ground placement call][s44] · [Forced ground replacement][s45]

## Space, failure, and recovery

Give growing trees open overhead and side space. The active tree feature samples its configuration, checks world-height bounds, and scans a configuration-dependent square at each height. It uses the selected trunk placer's free-position rule; ordinary tree positions accept air or the replaceable-by-trees tag, while log-tag blocks can also pass the base clearance check. Passing that check is not a promise that every existing block will be overwritten. Mangrove has extra root and trunk replacement rules. [Height and free-space checks][s46] · [Trunk clearance versus placement][s47] · [Replaceable tree blocks][s48] · [Mangrove extra trunk rule][s49] · [Mangrove trunk-passable blocks][s50]

Vines can matter: the two large Spruce features, Mega Jungle, and Azalea Tree have `ignore_vines: false`, so a vine in their checked space can prevent placement. The other selected tree configurations ignore vines in that particular obstruction check. Clear obstacles before repeatedly spending Bone Meal. This guide does not turn trunk-height parameters or clearance scans into a guaranteed final canopy size; the chosen trunk, foliage, roots, and decorators all contribute. [Vine obstruction condition][s51] · [mega_spruce][s52] · [mega_pine][s53] · [mega_jungle_tree][s54] · [azalea_tree][s55]

The grower temporarily removes the initiating plant for placement and **restores its prior block state if the feature reports failure**. A waterlogged single propagule temporarily leaves its fluid in place. A failed 2 × 2 placement restores all four positions using the initiating sapling's state, so it does not preserve four separate hidden-stage values. This is restoration of the starting plants, not a general rollback of every possible feature edit. [Large-tree restoration][s56] · [Single-plant restoration][s57]

Large Spruce growth deserves space around valuable soil too: both large configurations run a Podzol ground decorator. It searches nearby columns and replaces eligible **dirt-tag blocks**, not an arbitrary solid floor. For soil collection and conversion, use [Soil, Sand, and Gravel](SoilSandAndGravel.md). [Podzol configuration][s58] · [Alternate Podzol configuration][s59] · [Ground decorator][s60] · [Ground eligibility][s61]

## Collection and decoration

Ordinary mining of each of the eight saplings and the two Azalea shrubs returns **one matching item**, with no Silk Touch or Fortune alternative and no correct-tool tier requirement. Their bundled loot checks explosion survival. The planted propagule's age-4 drop rule remains in [Tree Leaves](TreeLeaves.md#mangrove-propagules). [Leaf drops](TreeLeaves.md#leaf-families-and-drops) supply the saplings and shrubs through their checked material rolls. [Flower Pots](FlowerPot.md) covers the separate potted forms; this guide's growth callbacks belong to the unpotted plant blocks. [Plant registrations][s62] · [Azalea registrations][s63] · [Harvest gate][s64]

## Growth feature and loot matrix

These are the exact configured-feature keys referenced by the live growers, not a list of natural biome spawns. The registry loads configured features with their codec, and the callback invokes the retrieved feature's placement implementation. [Live feature keys][s65] · [Configured-feature registry][s66] · [Tree feature implementation registration][s67] · [Configured placement dispatch][s68]

??? info "Checked selected features and plant loot"

    | Plant | Referenced feature definitions | Planted-block loot |
    | --- | --- | --- |
    | Oak Sapling | [oak][s69] · [fancy_oak][s70] · [oak_bees_005][s71] · [fancy_oak_bees_005][s72] | [Loot][s73] |
    | Spruce Sapling | [spruce][s74] · [mega_spruce][s75] · [mega_pine][s76] | [Loot][s77] |
    | Birch Sapling | [birch][s78] · [birch_bees_005][s79] | [Loot][s80] |
    | Jungle Sapling | [jungle_tree_no_vine][s81] · [mega_jungle_tree][s82] | [Loot][s83] |
    | Acacia Sapling | [acacia][s84] | [Loot][s85] |
    | Cherry Sapling | [cherry][s86] · [cherry_bees_005][s87] | [Loot][s88] |
    | Dark Oak Sapling | [dark_oak][s89] | [Loot][s90] |
    | Pale Oak Sapling | [pale_oak_bonemeal][s91] | [Loot][s92] |
    | Mangrove Propagule | [mangrove][s93] · [tall_mangrove][s94] | [Loot][s95] |
    | Azalea | [azalea_tree][s96] | [Loot][s97] |
    | Flowering Azalea | [azalea_tree][s98] | [Loot][s99] |

## Sources and verification

Source-reviewed on **2026-10-02** at `e87cde38c872d30ae86139bbee181937603af769`. The eleven selected plant registrations, their complete loot tables, all nineteen selected tree configurations, and the active support, advancement, placement, and recovery paths were checked. No in-game growth, clearance, Bone Meal, nest, or ground-conversion test was run. This guide does not establish natural biome availability or final tree heights. Data packs, random-tick delivery, and server settings can change results. [Pewen](Pewen.md) remains separate; no Ancient or Pewen parity is assumed.

Related: [Oak](Oak.md) · [Tree Leaves](TreeLeaves.md) · [Tree Logs and Roots](TreeLogsAndRoots.md) · [Crimson and Warped Fungi](NetherFungi.md) · [Bone Meal](../items/BoneMeal.md) · [Blocks](Blocks.md)

[s1]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L208-L263
[s2]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L27-L65
[s3]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L187-L193
[s4]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L45-L58
[s5]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L127-L185
[s6]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_bonemeal.json
[s7]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[s8]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/dirt.json
[s9]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L34-L38
[s10]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L75-L79
[s11]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L136
[s12]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L45-L73
[s13]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[s14]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L507
[s15]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L127-L185
[s16]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L187-L193
[s17]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L57-L65
[s18]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L195-L203
[s19]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/flowers.json
[s20]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/small_flowers.json
[s21]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/birch_bees_005.json
[s22]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/cherry_bees_005.json
[s23]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L38-L66
[s24]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L43-L65
[s25]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L264-L276
[s26]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L103-L133
[s27]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L52-L73
[s28]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L47-L56
[s29]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L105-L118
[s30]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mangrove.json
[s31]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/tall_mangrove.json
[s32]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/rootplacers/MangroveRootPlacer.java#L35-L102
[s33]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mangrove.json
[s34]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/tall_mangrove.json
[s35]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/AttachedToLeavesDecorator.java#L45-L78
[s36]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L38-L66
[s37]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L6574-L6589
[s38]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/AzaleaBlock.java#L18-L60
[s39]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[s40]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/AzaleaBlock.java#L41-L54
[s41]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[s42]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L57-L65
[s43]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json
[s44]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/BendingTrunkPlacer.java#L47-L62
[s45]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/TrunkPlacer.java#L59-L77
[s46]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L52-L114
[s47]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/TrunkPlacer.java#L88-L123
[s48]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/replaceable_by_trees.json
[s49]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/UpwardsBranchingTrunkPlacer.java#L131-L136
[s50]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mangrove_logs_can_grow_through.json
[s51]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L96-L114
[s52]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_spruce.json
[s53]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_pine.json
[s54]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_jungle_tree.json
[s55]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json
[s56]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L137-L152
[s57]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L163-L183
[s58]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_spruce.json
[s59]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_pine.json
[s60]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/AlterGroundDecorator.java#L27-L71
[s61]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L197-L203
[s62]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L208-L276
[s63]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java#L6574-L6589
[s64]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[s65]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/data/worldgen/features/TreeFeatures.java#L72-L113
[s66]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L104
[s67]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L61-L62
[s68]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L26
[s69]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/oak.json
[s70]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/fancy_oak.json
[s71]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/oak_bees_005.json
[s72]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/fancy_oak_bees_005.json
[s73]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/oak_sapling.json
[s74]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/spruce.json
[s75]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_spruce.json
[s76]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_pine.json
[s77]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/spruce_sapling.json
[s78]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/birch.json
[s79]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/birch_bees_005.json
[s80]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/birch_sapling.json
[s81]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/jungle_tree_no_vine.json
[s82]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mega_jungle_tree.json
[s83]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/jungle_sapling.json
[s84]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/acacia.json
[s85]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/acacia_sapling.json
[s86]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/cherry.json
[s87]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/cherry_bees_005.json
[s88]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/cherry_sapling.json
[s89]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/dark_oak.json
[s90]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_sapling.json
[s91]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_bonemeal.json
[s92]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_sapling.json
[s93]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/mangrove.json
[s94]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/tall_mangrove.json
[s95]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/mangrove_propagule.json
[s96]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json
[s97]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/azalea.json
[s98]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json
[s99]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/flowering_azalea.json
