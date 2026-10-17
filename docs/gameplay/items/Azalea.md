# Azalea

**Azalea** is a shrub that Bone Meal can turn into a tree with **Oak Logs**, Azalea Leaves and Flowering Azalea Leaves. A planted shrub does not become a tree simply by waiting. [Tree materials][configured_feature-azalea_tree] · [Shrub behavior][azalea-block] · [Registration][blocks]

## Obtaining

- **Lush Caves moss vegetation:** the biome's floor-moss feature can select an Azalea shrub. Break an existing shrub for one item. Each patch has random vegetation and placement checks, so finding moss does not guarantee a shrub. [Biome][biome-lush_caves] · [Floor placement][placed_feature-lush_caves_vegetation] · [Patch][configured_feature-moss_patch] · [Vegetation choices][configured_feature-moss_vegetation]
- **Bone Meal on green Moss Block:** with air directly above it, the moss callback attempts a spreading patch that can select Azalea. It can also select other plants, including Flowering Azalea. Pale Moss has a different list without either shrub. Follow [Moss spreading](../blocks/MossAndPaleMoss.md#moss-blocks-and-spreading) for the ground and patch limits. [Registered callback][blocks] [moss-callback][] · [Bone Meal patch][configured_feature-moss_patch_bonemeal] · [Green choices][configured_feature-moss_vegetation] · [Pale choices][configured_feature-pale_moss_vegetation]
- **Azalea Leaves:** without Shears or Silk Touch, one shrub has a **5% chance without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, or **10% with Fortune III**. Shears or Silk Touch recover the leaf block instead. Ordinary decay uses the no-Fortune chance. Flowering Azalea Leaves yield the **flowering shrub**, rather than this item. [Ordinary loot][blocks-azalea_leaves] · [Flowering loot][blocks-flowering_azalea_leaves] · [Fortune][fortune] · [Decay][leaves] · [Tool-free drops][drops]

A placed Azalea normally drops **one Azalea**, even by hand; its loot has no tool requirement or Fortune bonus and checks explosion survival separately. No direct crafting recipe or direct Wandering Trader shrub offer was found. A selected trader offer can instead supply **2 green Moss Blocks for 1 Emerald**, with five uses, as a starter for the moss route. [Shrub loot][blocks-azalea] · [Registration][blocks] · [Recipes][recipes] · [Offers][trades] · [Trader dispatch][trader] · [Selection][trade-choice]

## Usage

Place it on **Clay, Farmland, or a dirt-tag block**, including Dirt, Grass Block, Rooted Dirt, Moss Block and Mud. It has no waterlogged state. Use Bone Meal on the **placed shrub** when you want a tree; inventory stacks do not grow. [Support and callback][azalea-block] · [Inherited support][support] · [Soil tag][soil]

Ordinary and Flowering Azalea select the **same `azalea_tree` feature**, with no combined-tree route for a 2 × 2 square. It produces Oak Logs and a mix of the two leaf types, and forces **Rooted Dirt beneath the starting trunk**. There is no separate Azalea Log in this route. Follow [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md#azalea-and-flowering-azalea) for shared tree growth and [Tree Leaves](../blocks/TreeLeaves.md#leaf-families-and-drops) for replacement shrubs. [Selection][grower] · [Tree configuration][configured_feature-azalea_tree] · [Ground call][bending] · [Ground replacement][trunk]

It can decorate a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants), where empty-hand interaction recovers the plant and tree growth is inactive. Surplus Azaleas supply **100 default furnace burn ticks** each or a **65% composting chance**, after the guaranteed first accepted layer of an empty [Composter](../blocks/Composter.md#from-ingredients-to-bone-meal). Survival composting spends the shrub even if a later roll fails. [Potted registration][blocks] · [Pot interaction][pot] · [Fuel][fuel] · [Compost][compost]

## Behavior

Azalea has **no ordinary sapling stages or natural random-tick tree growth**. Its registration does not enable random ticking, and its growth callback is Bone Meal. [Registration][blocks] · [Shrub class][azalea-block]

Bone Meal is accepted when the **fluid state directly above is empty**. This is not an air-block test: a dry solid ceiling can allow Bone Meal to be spent while blocking the tree. Each accepted use has a **45% chance to attempt the tree directly**, with no stage-0 step or brightness test. Survival use consumes one Bone Meal even on a failed roll or blocked placement. [Target and chance][azalea-block] · [Consumption][meal]

Leave open overhead and side space. The selected tree does not ignore Vines during its clearance scan. If the tree feature reports failure, the grower restores the starting shrub state; this is not a general rollback of every possible feature edit. See [space, failure and recovery](../blocks/SaplingsAndAzaleas.md#space-failure-and-recovery). [Configuration][configured_feature-azalea_tree] · [Clearance][space] · [Restoration][grower]

## Notes

- Exact block/item ID: **`minecraft:azalea`**. It is the ordinary shrub, distinct from Azalea Leaves and Flowering Azalea, and appears in the Natural Blocks Creative tab. [Block][blocks] · [Item][items] · [Creative][creative]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game moss generation, harvesting, trade or tree-growth test was run. Data packs and server settings can change outcomes.

Related: [Flowering Azalea](FloweringAzalea.md) · [Moss and Pale Moss](../blocks/MossAndPaleMoss.md) · [Lush Caves](../biomes/CaveBiomes.md#lush-caves) · [Items](Items.md)

[configured_feature-azalea_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/azalea_tree.json
[azalea-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/AzaleaBlock.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[biome-lush_caves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[placed_feature-lush_caves_vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/lush_caves_vegetation.json
[configured_feature-moss_patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch.json
[configured_feature-moss_vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_vegetation.json
[moss-callback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java
[configured_feature-moss_patch_bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch_bonemeal.json
[configured_feature-pale_moss_vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_vegetation.json
[blocks-azalea_leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/azalea_leaves.json
[blocks-flowering_azalea_leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/flowering_azalea_leaves.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java
[blocks-azalea]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/azalea.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[trader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[grower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[bending]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/BendingTrunkPlacer.java
[trunk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/trunkplacers/TrunkPlacer.java
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java
[space]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
