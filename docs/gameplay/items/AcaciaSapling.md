# Acacia Sapling

An **Acacia Sapling** grows individually into an Acacia Log and Acacia Leaves tree. Use it to start a wood supply, decorate a Flower Pot, or spend surplus saplings as fuel or compost. [Tree materials][configured_feature-acacia] · [Other uses][blocks] [fuel][] [compost][]

## Obtaining

- **Acacia Leaves:** break them without Shears or Silk Touch, or collect decay drops. One sapling has a **5% chance without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, or **10% with Fortune III**. Shears or Silk Touch recover the leaf block instead. Decay uses the no-Fortune chance; a tree need not return a replacement sapling. See [Tree Leaves](../blocks/TreeLeaves.md#leaf-families-and-drops) for the separate Stick pool and decay rules. [Leaf loot][blocks-acacia_leaves] · [Fortune lookup][fortune] · [Decay][leaves] · [Tool-free drops][drops]
- **Savanna trees:** Savanna, Savanna Plateau and Windswept Savanna all reach an Acacia candidate through their tree-placement and selector definitions. Gather saplings from the leaves of trees you find. Placement checks and random choices still apply; this is not a tree or sapling guarantee at a given location. [Biomes][biome-savanna] [biome-savanna_plateau][] [biome-windswept_savanna][] · [Placements][placed_feature-trees_savanna] [placed_feature-trees_windswept_savanna][] · [Selector][configured_feature-trees_savanna] · [Acacia placement][placed_feature-acacia_checked]
- **Wandering Trader:** a selected offer sells **1 sapling for 5 Emeralds**, with eight uses. Each trader chooses offers, so this stock is not guaranteed. [Offer][trades] · [Trader dispatch][trader] · [Random selection][trade-choice]
- **Savanna village house chests:** their loot table can choose **1–2 Acacia Saplings** in an entry. That is the count for a selected entry, not a promised chest total. [House-chest loot][savanna-chest] · [Chest template binding][savanna-template]

An already planted sapling normally drops **one Acacia Sapling**, even by hand, with no Fortune bonus or Silk Touch requirement. Its loot separately checks explosion survival. No producing crafting recipe was found among the bundled ordinary recipes. [Sapling loot][blocks-acacia_sapling] · [Registration][blocks] · [Recipe loader][recipes]

## Usage

Plant one on **Farmland or a dirt-tag block**, including Dirt, Grass Block, Podzol, Moss Block and Mud. Clay is not accepted by the ordinary sapling rule. Acacia has no waterlogged state. The shared [ground and growth guide](../blocks/SaplingsAndAzaleas.md#ground-stages-and-bone-meal) owns the full support list. [Support][support] · [Soil tag][soil] · [Sapling state][sapling]

The assigned grower selects the single **Acacia** feature. A 2 × 2 square does not select a giant tree, and nearby flowers do not select a bee-nest variant. The selected tree has Acacia Logs, Acacia Leaves, and no decorators. Give its branching trunk and canopy open overhead and side space; its height parameters are not a complete clearance box. [Selection][grower] · [Tree configuration][configured_feature-acacia] · [Clearance][space]

For decoration, put it in a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants) and use an empty hand to recover it. Potted saplings do not grow. A spare sapling supplies **100 default furnace burn ticks**, or a **30% composting chance** after the guaranteed first accepted layer of an empty [Composter](../blocks/Composter.md#from-ingredients-to-bone-meal). Survival composting consumes the item even when a later roll fails. [Pot registration][blocks] · [Interaction][pot] · [Fuel and membership][fuel] [sapling-tag][] · [Composting][compost]

## Behavior

A planted Acacia starts at **stage 0**. An advancement first changes it to stage 1; another advancement attempts the tree. Selected natural random ticks need **raw brightness at least 9 above the sapling** and a **1-in-7 roll**. Bone Meal has a **45% chance to advance**, without that brightness requirement. [Sapling callbacks][sapling]

An accepted Survival Bone Meal use consumes one even if the roll fails, only changes the hidden stage, or attempts a blocked tree. Clear the growing space before repeatedly applying it. A reported placement failure restores the initiating sapling state; see the [shared failure and recovery guide](../blocks/SaplingsAndAzaleas.md#space-failure-and-recovery) for the limits of that restoration. [Consumption][meal] · [Feature dispatch][grower]

## Notes

- Exact block/item ID: **`minecraft:acacia_sapling`**. It appears in the Natural Blocks Creative tab. [Block][blocks] · [Item][items] · [Creative][creative]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game tree search, chest opening, trade, growth or harvesting test was run. Data packs and server settings can change outcomes.

Related: [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md#acacia-sapling) · [Tree Leaves](../blocks/TreeLeaves.md) · [Items](Items.md)

[configured_feature-acacia]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/acacia.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
[blocks-acacia_leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/acacia_leaves.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java
[biome-savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/savanna.json
[biome-savanna_plateau]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/savanna_plateau.json
[biome-windswept_savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/windswept_savanna.json
[placed_feature-trees_savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/trees_savanna.json
[placed_feature-trees_windswept_savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/trees_windswept_savanna.json
[configured_feature-trees_savanna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/trees_savanna.json
[placed_feature-acacia_checked]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/acacia_checked.json
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[trader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java
[savanna-chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_savanna_house.json
[savanna-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/savanna/houses/savanna_small_house_2.nbt
[blocks-acacia_sapling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/acacia_sapling.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[sapling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[grower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[space]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java
[sapling-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/saplings.json
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
