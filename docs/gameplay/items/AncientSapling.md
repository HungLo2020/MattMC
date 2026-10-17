# Ancient Sapling

**Ancient Sapling** (`minecraft:ancient_sapling`) has an active growing route into a branching **Jungle Log tree with Ancient Leaves**. The registered sapling does not select a giant tree, and its natural starter supply remains unverified. [Registration][blocks] · [Assigned grower][ancient-grower] · [Tree construction][ancient-tree]

## Obtaining

Harvest **Ancient Leaves without Shears or Silk Touch**, or collect their ordinary decay drops. The chance of one Ancient Sapling is **5% without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, or **10% with Fortune III**. Shears or Silk Touch yield the leaf block instead. Decay uses an empty tool and the no-Fortune chance. Follow [Ancient Leaves drops](../blocks/AncientPlants.md#ancient-leaves-decoration-decay-and-drops) for the independent Stick pool, leaf persistence and missing tool-tag integrations. [Leaf loot][blocks-ancient_leaves] · [Fortune][fortune] · [Decay][leaves] · [Tool-free drops][drops]

An already planted sapling normally returns **one sapling**, even by hand, with no Fortune bonus or Silk Touch requirement; explosion survival is checked separately. Creative lists the item. No direct producing recipe or trader offer was found in the bundled definitions. [Sapling loot][blocks-ancient_sapling] · [Registration][blocks] · [Creative][creative] · [Recipe loader][recipes] · [Trade inventory][trades]

**The first natural starter is still not established.** Ancient and Giant Ancient configured/placed features exist, but none of the 68 bundled biome definitions reaches them in the checked feature references, and no Ancient family block was found in the 1,202 bundled structure palettes. The effective [Primordial Caves biome source](../dimensions/PrimordialCaves.md#what-currently-generates) also includes Primordial Ocean through a loaded override; that biome does not add an Ancient tree route. This is a bounded source finding, not proof of absence from every world or data pack. See the canonical [Ancient family access guide](../blocks/AncientPlants.md#registered-forms-and-access). [Feature registration][features] · [Configured features][configured_feature-ancient_tree] [configured_feature-giant_ancient_tree][] · [Placed features][placed_feature-ancient_tree] [placed_feature-giant_ancient_tree][] · [Loaded dimension][loaded-dimension] · [Registry precedence][precedence] · [Biome lists][biome-primordial_plains] [biome-dry_midlands][] [biome-primordial_ocean][]

## Usage

Plant on a **dirt-tag block**, including Dirt, Grass Block, Podzol, Moss Block or Mud. Although the inherited planting rule accepts **Farmland**, the actual Ancient Tree feature requires dirt-tag ground; Farmland is absent from that tag. The sapling has no waterlogged state. [Support][support] · [Soil][soil] · [Feature ground test][ancient-tree] · [Sapling state][sapling]

The registered sapling advances **stage 0 → stage 1 → tree attempt**. Selected random ticks require **brightness at least 9 above it** and a **1-in-7 roll**. Bone Meal has a **45% chance to advance**, without that brightness test. Accepted Survival use spends one Bone Meal even when the roll fails, only advances the stage, or attempts an obstructed tree. Failed feature placement restores the initiating sapling state. [Sapling callback][sapling] · [Consumption][meal] · [Dispatch and restoration][grower]

The assigned grower requests the configured **`ancient_tree`** directly. It creates Jungle Logs and Ancient Leaves, with dry replacement and dirt-ground tests. Give it generous room for spreading branches and foliage. Follow [Ancient Sapling growth](../blocks/AncientPlants.md#growing-an-ancient-sapling) for the tree's construction and clearance limits. The separate world-generation placed feature is not part of this sapling callback. [Grower][ancient-grower] · [Configured-feature dispatch][grower] · [Tree][ancient-tree] · [Separate placement][placed_feature-ancient_tree]

**Neither a 2 × 2 nor a 3 × 3 arrangement selects a giant tree.** The assigned grower has no mega or secondary tree configured. Its `0.1` secondary chance therefore is not a 10% giant-tree chance. A separate giant grower and 3 × 3 helper exist, but the registered sapling does not call them. [Assigned block][blocks] · [Growers and helper][ancient-grower] · [Active selection][grower]

It can decorate a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants), where it does not grow into a tree; empty-hand interaction recovers the plant. **Ancient Saplings are absent from the bundled saplings fuel tag and compostable entries**, with no separate fuel registration found. Save extras for replanting and decoration rather than assuming ordinary-sapling fuel or compost behavior. [Potted form][blocks] · [Pot interaction][pot] · [Fuel table][fuel] · [Saplings tag][sapling-tag] · [Compost table][compost]

## Related pages

- [Ancient trees and plants](../blocks/AncientPlants.md), [Ancient Leaves](AncientLeaves.md), and [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked active block/feature registration and dispatcher, loot, support and item tags, trade entries, 1,501 ordinary recipe resources, 68 biome definitions and their feature references, the loaded Primordial Caves override, and 1,202 structure palettes. These are source inventories, not runtime-loaded counts. No in-game planting, growth, harvesting, trading or generation test was run; data packs and server settings can change outcomes.

[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[ancient-grower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/block/grower/AncientTreeGrower.java
[ancient-tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/level/feature/AncientTreeFeature.java
[blocks-ancient_leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/ancient_leaves.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[leaves]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java
[blocks-ancient_sapling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/ancient_sapling.json
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[features]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ACFeatures.java
[configured_feature-ancient_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/ancient_tree.json
[configured_feature-giant_ancient_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/giant_ancient_tree.json
[placed_feature-ancient_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/ancient_tree.json
[placed_feature-giant_ancient_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/giant_ancient_tree.json
[loaded-dimension]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[precedence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java
[biome-primordial_plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json
[biome-dry_midlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[biome-primordial_ocean]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[sapling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java
[grower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[sapling-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/saplings.json
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
