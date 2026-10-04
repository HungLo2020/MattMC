# Flowering Azalea

Flowering Azalea is a flowering shrub and a Bone Meal target for an Azalea tree. Its tree uses **Oak Logs**, with Azalea and Flowering Azalea Leaves; there is no separate Azalea Log produced by this route.

## Obtaining

Use these checked routes for a starting shrub:

- **Lush Caves moss vegetation:** the biome's floor-moss feature can place Flowering Azalea through its weighted vegetation choice. Find and break a shrub for one item. A moss patch need not contain one. [Lush Caves][lush] · [Floor placement][lush-placement] · [Moss patch][natural-moss] · [Vegetation choices][moss-vegetation]
- **Bone Meal on a green Moss Block:** leave air directly above the block, then apply Bone Meal. Its patch can choose Flowering Azalea among other plants, subject to ground and plant placement. This is a random, placement-dependent supply route, not one shrub per application. **Pale Moss** uses a different vegetation list with no Azalea. See [Moss spreading](../blocks/MossAndPaleMoss.md#moss-blocks-and-spreading) for the patch layout. [Registered feature][moss-registration] · [Moss callback][moss-callback] · [Patch][moss-patch] · [Green choices][moss-vegetation] · [Pale choices][pale-vegetation]
- **Flowering Azalea Leaves:** break them without Shears or Silk Touch, or collect their decay drops. The chance of one shrub is **5% without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, or **10% with Fortune III**. Shears or Silk Touch recover the leaf block instead; decay uses the no-Fortune chance. Ordinary Azalea Leaves yield **Azalea**, not Flowering Azalea. [Flowering leaf loot][leaf-loot] · [Ordinary leaf loot][ordinary-leaf] · [Fortune][fortune] · [Decay][decay] · [Tool-free drops][empty-tool]

An already placed Flowering Azalea normally drops **one Flowering Azalea**, even by hand, with no Silk Touch requirement or Fortune bonus. The shrub loot checks explosion survival separately. No direct producing recipe or direct Wandering Trader shrub offer was found in the checked data. The trader can instead offer **1 Emerald → 2 green Moss Blocks**, with five uses, as a starter for the moss route when that offer is selected. [Shrub loot][plant-loot] · [Registration][registration] · [Harvest gate][gate] · [Trader inventory][shrub-trades] · [Moss offer][moss-trade] · [Offer counts][trade-count] · [Trader selection][trader] [trade-choice]

## Usage

Place the shrub on **Clay, Farmland, or a dirt-tag block**, including Dirt, Grass Block, Rooted Dirt, Moss Block and Mud. It has no waterlogged state. Its inventory item does not grow in a carried stack: apply Bone Meal to the **placed shrub** when you want a tree. [Support and shrub callback][azalea] · [Default support][support] · [Ground tag][soil] · [Placement check][placement]

A single Flowering Azalea uses the **same `azalea_tree` feature as ordinary Azalea**. A 2 × 2 square does not select a larger tree. The tree supplies Oak Logs and a weighted mix of the two Azalea leaf types, and forces **Rooted Dirt beneath its starting trunk**. Growing the flowering shrub does not select an all-flowering canopy. Follow [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md#azalea-and-flowering-azalea) for the shared growth route, and [Tree Leaves](../blocks/TreeLeaves.md#leaf-families-and-drops) when gathering replacement shrubs. [Grower][selection] · [Tree materials and ground][tree] · [Ground call][bending] · [Forced replacement][ground]

For decoration, it can be placed in a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants). Empty-hand interaction recovers the plant for outdoor replanting. Spare shrubs supply **100 default furnace burn ticks** each, or an **85% composting chance** after the guaranteed first accepted layer in an empty [Composter](../blocks/Composter.md). Ordinary Survival composting spends the shrub even if a later roll fails. [Potted form][pot-registration] · [Recovery][pot] · [Fuel][fuel] · [Compost entry][compost] · [Consumption][compost-use] · [Layer roll][compost-roll]

## Behavior

Flowering Azalea has **no ordinary sapling stages or natural random-tick tree growth**. Its registration does not enable random ticking, and its tree-growing callback is Bone Meal. Waiting for a planted shrub alone therefore does not perform this transformation. [Registration][registration] · [Shrub class][azalea]

Bone Meal is accepted when the **fluid state directly above the shrub is empty**. This is not an air-block test: a dry solid ceiling can still allow Bone Meal to be spent while obstructing the tree. Each accepted use has a **45% chance to attempt the tree directly**, with no stage-0 step and no brightness check. Ordinary Survival use consumes one Bone Meal even when the chance fails or tree placement is blocked. [Target and chance][azalea] · [Consumption][meal]

Give the tree open space above and around it. The selected feature does not ignore Vines in its clearance scan, so they can also prevent growth. If placement reports failure, the grower restores the starting shrub state; this is not a general rollback guarantee for every feature edit. See the [family space and recovery guide](../blocks/SaplingsAndAzaleas.md#space-failure-and-recovery) for the exact scope. [Feature][tree] · [Clearance][space] · [Single-plant restoration][dispatch]

## Notes

- Exact registered block/item ID: **`minecraft:flowering_azalea`**, named **Flowering Azalea**. It is a shrub, distinct from Flowering Azalea Leaves, and is listed in the Natural Blocks Creative inventory. [Block][registration] · [Item][items] · [Creative listing][creative]
- The recipe browser searches recipe **outputs**, not leaf loot, moss vegetation or Bone Meal transformations. It can also have an empty cache when no recipe manager is available. An empty lookup is not evidence that the shrub lacks a Survival route. [Browser lookup][recipe-browser] · [Empty-result handling][browser-empty]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Registration, moss and leaf acquisition, trader entries, bundled recipes and the active growth/feature paths were checked. No in-game harvesting, moss generation, tree growth, trading, or browser test was run. Data packs and server settings can change outcomes.

Related: [Azalea](Azalea.md) · [Moss and Pale Moss](../blocks/MossAndPaleMoss.md) · [Lush Caves](../biomes/CaveBiomes.md#lush-caves) · [Items](Items.md)

[lush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json#L1-L189
[lush-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/lush_caves_vegetation.json#L1-L44
[natural-moss]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch.json#L1-L27
[moss-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_vegetation.json#L1-L43
[moss-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6610-L6614
[moss-callback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java#L36-L54
[moss-patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch_bonemeal.json#L1-L27
[pale-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_vegetation.json#L1-L38
[leaf-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/flowering_azalea_leaves.json#L1-L62
[ordinary-leaf]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/azalea_leaves.json#L1-L62
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[decay]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L56-L65
[empty-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L345-L367
[plant-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/flowering_azalea.json#L1-L21
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6574-L6589
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[shrub-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L714-L831
[moss-trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L820-L822
[trade-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1478
[trader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L140
[azalea]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/AzaleaBlock.java#L17-L54
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L136
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L27-L65
[tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json#L1-L75
[bending]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/BendingTrunkPlacer.java#L44-L58
[ground]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/TrunkPlacer.java#L60-L75
[pot-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6730-L6733
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L48-L90
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L167-L177
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L243-L255
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[space]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L56-L114
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L127-L193
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L289-L292
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L873-L885
[recipe-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java#L29-L132
[browser-empty]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L327-L337
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
