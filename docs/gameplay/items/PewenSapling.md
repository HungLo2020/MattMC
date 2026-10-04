# Pewen Sapling

**Pewen Sapling** (`minecraft:pewen_sapling`) has an active tree-growing route into Pewen Logs, Wood, Branches and Pines. Its starter supply is more limited than that of ordinary Overworld saplings: the checked bundled generation does not establish a natural first tree. [Sapling registration][blocks] · [Assigned grower][pewen-grower] · [Tree construction][pewen-tree]

## Obtaining

**Pewen Pines and Pewen Branches** both have a sapling loot branch. Harvest without Shears or Silk Touch for a **5% chance of one sapling without Fortune**, **6.25% with Fortune I**, about **8.33% with Fortune II**, or **10% with Fortune III**. Shears or Silk Touch choose the harvested decorative block instead. The Branch loot does not require a leafy tip state. The separate Stick/Pine Nuts resource pool does not guarantee a replacement sapling. [Pines loot][blocks-pewen_pines] · [Branch loot][blocks-pewen_branch] · [Fortune lookup][fortune]

Pines and Branches do not use ordinary leaf decay; follow the [Pewen family guide](../blocks/Pewen.md#branches-pines-and-resources) for their support and harvesting behavior. A planted sapling normally returns **one sapling**, including when broken by hand, with no Silk Touch requirement or Fortune bonus; its loot checks explosion survival. [Pines implementation][pewen-pines] · [Branch implementation][pewen-branch] · [Sapling loot][blocks-pewen_sapling] · [Registration][blocks]

**A natural starter remains unverified at this source snapshot.** The custom tree type is actively registered, and configured and placed Pewen features exist, but none of the 68 bundled biome definitions reaches that feature in the checked feature references. In particular, the effective [Primordial Caves dimension](../dimensions/PrimordialCaves.md#what-currently-generates) includes Primordial Plains, Dry Midlands and Primordial Ocean; checking the Normal preset alone would miss the loaded dimension override. Those three biomes still do not supply a Pewen tree route. [Feature registration][features] · [Configured feature][configured_feature-pewen_tree] · [Placed feature][placed_feature-pewen_tree] · [Loaded dimension][loaded-dimension] · [Loading][world-loader] · [Registry precedence][precedence] · [Biome lists][biome-primordial_plains] [biome-dry_midlands][] [biome-primordial_ocean][]

Creative lists the sapling. No direct producing recipe, trader offer or family block in the bundled structure palettes was found. A supplied sapling can start the tree-and-loot loop, but chance-based drops do not guarantee that every tree replaces its starter. [Item][items] · [Creative][creative] · [Recipe loader][recipes] · [Trader entries][trades]

## Growing

Plant on a **dirt-tag block**, such as Dirt, Grass Block, Podzol, Moss Block or Mud. The inherited planting rule also accepts **Farmland**, but the actual Pewen Tree feature requires the dirt tag, which excludes Farmland. Use dirt-tag ground for growth. The sapling has no waterlogged state. [Plant support][support] · [Soil tag][soil] · [Feature ground gate][pewen-tree] · [Sapling states][sapling]

The assigned grower selects the configured **`pewen_tree`** feature directly. A matching 2 × 2 square does not select a giant variant. This sapling path does not run the separate world-generation placed feature's random height and environment-scan modifiers. [Assigned grower][pewen-grower] · [Configured-feature dispatch][grower] · [Separate placement][placed_feature-pewen_tree]

Natural selected random ticks require **brightness at least 9 above the sapling**, then a **1-in-7 roll**. The first advancement changes hidden stage 0 to 1; the next attempts the tree. Bone Meal has a **45% chance to advance** without the brightness requirement. An accepted Survival use spends one even when it fails, only advances the stage, or attempts a blocked tree. Failed placement restores the starting sapling state. [Sapling callbacks][sapling] · [Consumption][meal] · [Restoration][grower]

Allow generous dry space above and around it. The tree chooses an **11–20 height parameter**, places the main log column and a Wood cap, adds Pines above the cap, and branches around the upper trunk. The height parameter is not the complete tree height or a guaranteed safe clearance box. Its checked replacement positions reject fluids and feature-protected blocks. The [Pewen family guide](../blocks/Pewen.md#growing-and-obtaining-wood) owns the detailed growing and wood-use guidance, including recipe and tool integration limits. [Tree construction and tests][pewen-tree]

For decoration, place it in a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants); the potted form does not grow, and empty-hand interaction recovers the plant. **Pewen Saplings are absent from the bundled saplings fuel tag and compostable entries**, with no separate fuel registration found. Do not assume the fuel and compost uses of ordinary saplings apply. [Potted registration][blocks] · [Recovery][pot] · [Fuel table][fuel] · [Saplings tag][sapling-tag] · [Compost table][compost]

## Related pages

- [Pewen Log](PewenLog.md)
- [Pewen Pines](PewenPines.md) and [Pewen Branch](PewenBranch.md)
- [Pewen family](../blocks/Pewen.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked active registration and growth dispatch, loot, tags, trades, 1,501 ordinary recipe resources, 68 biome definitions and their feature references, the loaded Primordial Caves override, and 1,202 structure palettes. These are source inventories, not runtime-loaded counts. No in-game generation, growth, harvesting or crafting test was run; data packs and world settings can change the result.

[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
[pewen-grower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/block/grower/PewenGrower.java
[pewen-tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/level/feature/PewenTreeFeature.java
[blocks-pewen_pines]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json
[blocks-pewen_branch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_branch.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[pewen-pines]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/block/PewenPinesBlock.java
[pewen-branch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/block/PewenBranchBlock.java
[blocks-pewen_sapling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_sapling.json
[features]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ACFeatures.java
[configured_feature-pewen_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pewen_tree.json
[placed_feature-pewen_tree]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/pewen_tree.json
[loaded-dimension]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[world-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java
[precedence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java
[biome-primordial_plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json
[biome-dry_midlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[biome-primordial_ocean]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[sapling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[grower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java
[pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[sapling-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/saplings.json
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
