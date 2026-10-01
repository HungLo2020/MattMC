# Oak

Oak is a renewable source of logs, planks, sticks, and occasional apples. A useful first loop is to collect logs, save the leaf-dropped saplings, make a [Crafting Table](CraftingTable.md), and replant away from buildings. This guide covers oak's harvesting, leaf decay, planting, growth, and stripping behavior; the linked item pages hold crafting and fuel details.

## Finding and harvesting oak

Natural oak trees are wired into the bundled **Plains** tree feature and **Forest** tree selection. These are confirmed examples, not every place oak can occur or a guarantee of a tree in each chunk. The reviewed tree configurations produce oak logs and oak leaves. [Plains biome][plains] · [Plains placement][plains-placement] · [Plains tree selection][plains-trees] · [Forest biome][forest] · [Forest placement][forest-placement] · [Forest tree selection][forest-trees] · [Oak tree material][oak-feature]

Break the trunk to collect [Oak Logs](../items/OakLog.md). Logs and planks do not require a special harvesting tier; an axe is their preferred mining tool. Their ordinary loot tables return **one matching block item**, subject to explosion survival. A stripped log drops its stripped form. Removing every log, including branches in a fancy oak, helps release the remaining leaves to decay. [Block properties][blocks] · [Axe mining tag][axe-tag] · [Log loot][log-loot] · [Plank loot][plank-loot] · [Stripped-log loot][stripped-loot] · [Harvest gate][harvest]

### Leaf drops

For leaves broken without shears or Silk Touch, the bundled oak-leaf table has separate sapling, stick, and apple rolls:

| Possible drop | No Fortune | Fortune I | Fortune II | Fortune III |
| --- | ---: | ---: | ---: | ---: |
| 1 [Oak Sapling](../items/OakSapling.md) | 5% | 6.25% | approximately 8.33% | 10% |
| 1–2 [Sticks](../items/Stick.md), if the roll succeeds | 2% | approximately 2.22% | 2.5% | approximately 3.33% |
| 1 [Apple](../items/Apple.md) | 0.5% | approximately 0.556% | 0.625% | approximately 0.833% |

A leaf can give nothing, and separate rolls can produce more than one kind of item. **Shears or Silk Touch give the leaf block instead**, bypassing these material-drop rolls. Natural decay supplies no tool, so it uses the no-Fortune column. Explosions have additional survival/decay checks and should not be treated as ordinary hand harvesting. [Leaf loot][leaf-loot] · [Fortune-level lookup][fortune] · [Decay drop dispatch][decay-drops]

Natural leaves become eligible to decay when their stored distance reaches **7** and they are not marked persistent. Distance propagates through neighboring leaves from blocks in the logs tag; it is not a simple spherical radius. A nearby remaining log can keep leaves alive. **Leaves placed by a player are persistent** and do not decay through this rule. [Leaf distance, random ticks, and placement][leaves]

## Planting and growing saplings

Place an [Oak Sapling](../items/OakSapling.md) on **farmland or a block in the dirt tag**. The bundled tag includes dirt, grass blocks, coarse dirt, rooted dirt, podzol, mycelium, moss, pale moss, mud, and muddy mangrove roots. Supporting the sapling is only the planting condition; the chosen tree must also fit. [Plant support][vegetation] · [Dirt tag][dirt]

Natural growth uses two stages:

1. On a selected random tick, the **local brightness above the sapling must be at least 9**, then a **1-in-7 roll** must succeed
2. The first advance changes the sapling's hidden stage from 0 to 1
3. A later advance attempts to place the tree. If placement fails, the sapling is restored so it can try again

There is no fixed growth time in seconds. The block must receive random ticks, and obstruction or world-height limits can prevent tree placement. Leave open space above and around it rather than planting under a low roof and waiting indefinitely. [Natural growth and stages][sapling] · [Tree placement and recovery][grower] · [Space checks][tree-feature] · [Random-tick dispatch][random-dispatch]

### Bone Meal

Using [Bone Meal](../items/BoneMeal.md) on a planted oak sapling gives a **45% chance to advance it**. Bone Meal is consumed in ordinary Survival even when the advancement roll fails, or when a stage-1 tree attempt cannot fit. A successful first application can therefore appear to do nothing because it only advances the hidden stage. [Sapling Bone Meal callbacks][sapling] · [Consumption and callback dispatch][bone-meal]

The Bone Meal path has **no brightness check**, but it still uses the same tree-placement and space checks. If repeated uses do not make a tree, check overhead/side clearance rather than assuming every consumed Bone Meal should guarantee growth. [Bone Meal implementation][bone-meal] · [Growth implementation][grower] · [Tree clearance][tree-feature]

The sapling grower selects a **fancy oak with a 10% chance**, otherwise the regular form. It has **no 2 × 2 mega-oak configuration**. A flower-tagged block within two blocks horizontally and one vertically selects a bee-capable variant; its decorator has a **5% placement roll** and still needs a valid nest location. These are sapling-growth rules, not the natural biome tree-selection percentages. [Oak grower and flower search][grower] · [Bee-capable oak][bee-oak] · [Bee-capable fancy oak][bee-fancy] · [Nest-placement conditions][bee-decorator]

## Placing, stripping, and building

Placed logs take their axis from the face you click: top/bottom faces give vertical logs, and side faces give the corresponding horizontal orientation. [Log placement][pillar]

Use an axe on a placed oak log or oak wood block to make its stripped version. The operation preserves its axis and attempts **one point of axe durability damage**. There is no bark item created by this interaction. If an offhand shield takes priority, use secondary interaction, normally sneaking, or remove the shield before trying again. [Stripping map, callback, and shield precedence][axe] · [Item-use dispatch][use-dispatch]

Oak logs, planks, and leaves are flammable. Keep a tree-growing area and timber structures clear of fire and lava. [Fire registration][fire] · [Lava-ignition properties][blocks]

For inventory conversions and fuel planning:

- [Oak Log](../items/OakLog.md): oak wood conversion, stripping links, and charcoal route
- [Oak Planks](../items/OakPlanks.md): accepted log-family inputs and crafting/fuel efficiency
- [Stick](../items/Stick.md): vertical crafting recipes and beginner uses
- [Oak Sapling](../items/OakSapling.md): collection, replanting links, and fuel caution

## Verification scope

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`, using active MattMC code and bundled data. No in-game growth, harvesting, bee-nest, or stripping test was run. Data packs can alter tags, tree features, and loot; random ticks, clearance, server rules, and item state affect results.

Related: [Blocks](Blocks.md) · [Furnace](Furnace.md) · [Crafting](../crafting/Crafting.md) · [Items](../items/Items.md)

[plains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/plains.json
[plains-placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/placed_feature/trees_plains.json
[plains-trees]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/trees_plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/forest.json
[forest-placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/placed_feature/trees_birch_and_oak_leaf_litter.json
[forest-trees]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/trees_birch_and_oak_leaf_litter.json
[oak-feature]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/oak.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/Blocks.java
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[log-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_log.json
[plank-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_planks.json
[stripped-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/stripped_oak_log.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L285-L292
[leaf-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[decay-drops]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/Block.java#L364-L368
[leaves]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[vegetation]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[dirt]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/dirt.json
[sapling]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[grower]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[tree-feature]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java
[random-dispatch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L503
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BoneMealItem.java
[bee-oak]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/oak_bees_005.json
[bee-fancy]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/fancy_oak_bees_005.json
[bee-decorator]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java
[pillar]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[axe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/AxeItem.java
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L394
[fire]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/FireBlock.java
