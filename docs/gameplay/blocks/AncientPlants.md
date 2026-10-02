# Ancient trees, Flytraps and Tree Stars

Grow an **Ancient Sapling** into a branching Jungle Log tree with **Ancient Leaves**, duplicate an existing **Flytrap** with Bone Meal, or attach **Tree Stars** to supported surfaces. These MattMC blocks have active placement and growth behavior, but their Creative listings do not establish a naturally available starter supply. [Registrations][blocks] [items] · [Tree growth][grower] [tree] · [Decorative plants][flytrap] [star]

## Registered forms and access

| Block | Registry ID | Verified access |
| --- | --- | --- |
| [Ancient Sapling](../items/AncientSapling.md) | `minecraft:ancient_sapling` | Natural Blocks Creative tab; chance drop from Ancient Leaves |
| [Ancient Leaves](../items/AncientLeaves.md) | `minecraft:ancient_leaves` | Natural Blocks Creative tab; registered Ancient Sapling growth |
| Potted Ancient Sapling | `minecraft:potted_ancient_sapling` | Put an Ancient Sapling into a [Flower Pot](FlowerPot.md#adding-and-removing-plants) |
| [Flytrap](../items/Flytrap.md) | `minecraft:flytrap` | Natural Blocks Creative tab; Bone Meal can duplicate an existing plant |
| [Tree Star](../items/TreeStar.md) | `minecraft:tree_star` | Natural Blocks Creative tab; harvesting an existing placed block |

The four ordinary block items are explicitly registered. The potted form uses the Flower Pot interaction and has no separate ordinary item. No crafting recipes for these plants were found in the bundled recipe inventory. [Items and Creative entries][items] [creative] · [Potting][pot] · [Recipe loading][recipes]

**A natural starter source was not established.** The bundled configured and placed Ancient Tree features exist, but none of the 68 bundled biome definitions references either Ancient Tree feature, and the checked generation paths do not place Flytraps or Tree Stars. No block from this family appeared in the 1,202 bundled structure palettes checked. Growing a supplied sapling and harvesting its leaves is a verified source path; it does not explain how an unmodified Survival world supplies the first one. [Tree feature registrations][custom-features] · [Configured/placed features][tree-config] [tree-placement][] [giant-config][] [giant-placement] · [Tree bodies][tree] [giant-tree]

For Pewen trees and the separate Fiddlehead/Cycad/Archaic Vine behaviors, use the existing [Pewen](Pewen.md) and [Primordial decorative plants](PrimordialPlants.md) guides.

## Growing an Ancient Sapling

Plant the sapling on a block in the **dirt tag**, such as Dirt, Grass Block, Podzol, Coarse Dirt, Moss Block or Mud. The inherited planting rule also accepts **Farmland**, but the actual Ancient Tree feature requires dirt-tag ground, and Farmland is not in that tag. Use dirt-tag ground for a tree you intend to grow. Removing suitable support breaks the planted sapling; ordinary harvesting returns one sapling without requiring Shears or Silk Touch. [Planting support][plant-support] [dirt-tag] · [Feature ground check][tree] · [Sapling loot][loot-ancient-sapling]

The registered sapling uses the ordinary two-stage growth system:

1. It begins at **stage 0**. A successful growth advance changes it to stage 1
2. At stage 1, another advance asks the registered tree grower to place the tree
3. Random growth checks need brightness **at least 9 above the sapling** and pass a **one-in-seven** check on the selected random tick
4. Bone Meal has a **45% success check per valid use** to advance the stage; it does not apply that random-growth light requirement

Bone Meal can be consumed without a visible tree, including when its chance fails, when it only advances the stage, or when tree placement fails. A failed feature placement restores the sapling. `randomTickSpeed` controls natural random growth, not direct Bone Meal use. [Sapling callbacks][sapling] · [Consumption][bone-meal] · [Placement/restoration][tree-grower] · [Random dispatch][chunk-tick] [random-tick]

The active ordinary feature chooses a height parameter of **3–6**, places a **4–7-block central Jungle Log trunk**, then adds branching Jungle Logs and Ancient Leaves above and around it. It requires dry replaceable trunk/crown space and rejects blocks in the feature-protection tag. This height parameter is not the finished canopy height or a guaranteed clearance box: grow it in a generous open area. There is no separate Ancient Log wood family in this registration. [Actual tree construction][tree] · [Protected blocks][protected-tag] · [Registry][blocks]

**A 2 × 2 or 3 × 3 arrangement does not select a giant tree through this sapling.** Its assigned grower has no mega-tree feature. A separate giant feature and a 3 × 3 checking helper exist in source, but that helper and the alternate grower are not called by the registered Ancient Sapling. The grower's `0.1` secondary chance has no secondary tree configured, so it is not a 10% giant-tree chance. [Assigned grower][blocks] [grower] · [Actual selection logic][tree-grower] · [Separate feature][giant-config] [giant-tree]

## Ancient Leaves: decoration, decay and drops

Ancient Leaves use the current leaves implementation with **distance**, **persistent** and **waterlogged** states. Leaves you place through their item become persistent and do not decay merely because there is no nearby log. The tree feature places non-persistent leaves. Their distance updates use six-direction connections to log-tag blocks and other blocks carrying the leaves distance property; at distance **7**, non-persistent leaves can decay on a random tick. Jungle Logs qualify as support through the logs tags. [Registered leaves class][blocks] [tinted-leaves] · [Persistence and distance][leaves] · [Tree placement][tree] · [Log tags][logs-tag] [logs-burn-tag][] [jungle-logs-tag][]

Use **Shears or Silk Touch** to recover one Ancient Leaves block. Without either, the sapling and stick pools are independent:

| Tool enchantment | Chance of 1 Ancient Sapling | Chance of 1–2 Sticks |
| --- | --- | --- |
| No Fortune | 5% | 2% |
| Fortune I | 6.25% | about 2.22% |
| Fortune II | about 8.33% | 2.5% |
| Fortune III | 10% | about 3.33% |

The Shears/Silk Touch branch excludes those resource pools. Ordinary decay uses an empty tool, so it follows the no-Fortune chances. Explosions have separate survival/decay conditions, and item drops require `doTileDrops` to be enabled. [Exact leaves loot][loot-ancient-leaves] · [Fortune lookup][fortune] · [Decay and drop dispatch][leaves] [block][] [rules][]

Ancient Leaves are missing from the bundled **leaves tag** and **hoe-mining tag**. This does not disable their inherited decay or their explicit Shears loot rule, but it means the usual tag-driven Shears/hoe speed bonuses do not apply. It also matters for Tree Star support below. [Tags][leaves-tag] [hoe] · [Shears/tool rules][shears] [tool-material]

The potted sapling is a separate decorative form: it does not run Sapling growth or accept Bone Meal as a tree. Recover the plant through the [Flower Pot interaction](FlowerPot.md#adding-and-removing-plants); ordinary pot breaking yields the pot and sapling according to its two loot pools. [Pot behavior][pot] · [Potted loot][loot-potted-ancient-sapling]

## Flytrap

Flytrap is a non-colliding decorative plant with an **open** state, initially true. It uses the usual dirt-tag/Farmland support rule, and losing that support removes it. Its ordinary block loot returns one Flytrap, subject to explosion survival; no Shears or Silk Touch requirement applies. [Plant registration][blocks] · [Support][plant-support] [bush] · [Loot][loot-flytrap]

Use **one Bone Meal to produce one extra Flytrap item**, leaving the original plant in place. Its target and success checks both return true, and it drops the item at the plant rather than placing a neighboring plant. This item spawn is suppressed if `doTileDrops` is disabled. Once you have a starter plant, the callback is a source-backed propagation route. [Flytrap Bone Meal overrides][flytrap] · [Consumption and item spawning][bone-meal] [block]

On a random tick, an open Flytrap closes and schedules reopening **100–199 game ticks** later, about **5–10 seconds at 20 ticks per second**. A random tick while closed opens it earlier; a pending scheduled tick also opens it. This is not a fixed open/closed cycle. Setting `randomTickSpeed` to zero stops new random toggles, but does not cancel a reopening already scheduled. Open Flytraps can emit fly particles; the active class has no prey-catching, feeding or entity-damage callback. [State and particles][flytrap] · [Random/scheduled dispatch][chunk-tick] [random-tick][] [scheduled-tick][]

## Tree Star

Tree Star is a non-colliding decoration that can face all **six directions**. Click the top, bottom or side of a support to orient it outward from that face. It survives when the supporting block has a sturdy face toward it **or belongs to the leaves tag**. Removing or invalidating that support breaks the Tree Star, whose ordinary loot gives one item without a special tool. [Facing and support][star] · [Placement validation][placement] · [Loot][loot-tree-star]

**Ancient Leaves cannot support it in the bundled data.** Their leaves implementation supplies an empty support shape, and their missing leaves-tag membership also fails Tree Star's alternate support condition. Use a suitable solid face such as a Jungle Log, or tagged ordinary leaves such as Oak Leaves. Ancient Tree generation does not place Tree Stars; both tree implementations leave out that decoration. [Support shape and tag][leaves] [leaves-tag][] [support][] · [Tree Star test][star] · [Tree generation][tree] [giant-tree]

Tree Star can be **waterlogged**: placement in source water records that state, and the standard waterlogging interface handles later water interactions. It does not implement Bone Meal growth, random spreading or a harvesting cycle beyond recovering the placed item. [State, fluid and implemented interfaces][star] [waterlogged] · [Registration][blocks]

## A small planting example

**Source-based example, not tested in gameplay:** with supplied starter items, plant one Ancient Sapling on Dirt in an open area and use Bone Meal until its stage advances and the tree places. Preserve decorative Ancient Leaves with Shears. Put a Tree Star on a Jungle Log face rather than on those leaves. Plant a Flytrap on nearby Grass Block and use Bone Meal to obtain another Flytrap item.

## Related pages

- [Pewen family](Pewen.md), [Primordial decorative plants](PrimordialPlants.md), and [Flower Pot](FlowerPot.md)
- [Flood Basalt and Fern Thatch](FloodBasaltAndFernThatch.md)
- [Blocks](Blocks.md) and [Items](../items/Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. Active block/feature registrations, grower selection, callbacks, loot, tags, all bundled recipes, 68 biome definitions and 1,202 structure palettes were inspected. Natural starter acquisition remains unverified. No gameplay planting, growth, harvesting or interaction test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[grower]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/block/grower/AncientTreeGrower.java
[tree]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/level/feature/AncientTreeFeature.java
[flytrap]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/custom/FlytrapBlock.java
[star]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/custom/TreeStarBlock.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[pot]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java
[recipes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[custom-features]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/ACFeatures.java
[tree-config]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/ancient_tree.json
[tree-placement]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/ancient_tree.json
[giant-config]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/giant_ancient_tree.json
[giant-placement]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/giant_ancient_tree.json
[giant-tree]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/level/feature/GiantAncientTreeFeature.java
[plant-support]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[dirt-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/dirt.json
[loot-ancient-sapling]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/ancient_sapling.json
[sapling]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BoneMealItem.java#L32-L80
[tree-grower]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L375-L405
[random-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L474-L515
[protected-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/features_cannot_replace.json
[tinted-leaves]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/TintedParticleLeavesBlock.java
[leaves]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[logs-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/logs.json
[logs-burn-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/logs_that_burn.json
[jungle-logs-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/jungle_logs.json
[loot-ancient-leaves]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/ancient_leaves.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Block.java
[rules]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/GameRules.java
[leaves-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/leaves.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[shears]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ShearsItem.java
[tool-material]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ToolMaterial.java
[loot-potted-ancient-sapling]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/potted_ancient_sapling.json
[bush]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/BushBlock.java
[loot-flytrap]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/flytrap.json
[scheduled-tick]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[placement]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BlockItem.java
[loot-tree-star]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/tree_star.json
[support]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SupportType.java
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
