# Crimson Fungus

Crimson Fungus is a small plant used to grow huge Crimson fungi and feed Hoglins. Its item and placed-block ID is `minecraft:crimson_fungus`. [Item registration][items] · [Block registration][blocks]

## Obtaining

Break a small Crimson Fungus by hand or with a tool to collect **one Crimson Fungus**. Neither Shears nor Silk Touch is needed, and Fortune does not increase this ordinary drop. [Full block loot][loot-crimson_fungus] · [Harvest gate][harvest]

To produce more from an existing patch, use Bone Meal **on either Nylium color with air directly above the ground**. Both vegetation providers can choose Crimson Fungus. Their placement attempts can fail or choose other plants, so an accepted Survival use consumes one Bone Meal without guaranteeing a fungus. Clear small plants to make room. See [producing more small fungi](../blocks/NetherFungi.md#producing-more-small-fungi). [Ground callback][nylium] · [Crimson provider][crimson-vegetation] · [Warped provider][warped-vegetation] · [Placement checks][vegetation] · [Consumption][bonemeal]

## Usage

Place it on **Crimson Nylium**, then apply Bone Meal to the **fungus** to attempt huge growth. Each valid use has a **40% chance to call the planted huge-fungus feature** and consumes one Bone Meal in ordinary Survival even when the chance fails. The planted configuration uses Crimson Stem and Nether Wart Block; clear space before growing because obstructions can leave an incomplete structure after the small fungus is consumed. Follow [Nether Fungi](../blocks/NetherFungi.md#bone-meal-and-huge-growth) for growth and clearance details. [Growth target and chance][fungus] · [Consumption][bonemeal] · [Planted configuration][crimson-planted] · [Feature placement][huge]

Feed it directly to ready adult [Hoglins](../mobs/Hoglin.md#feeding-and-breeding) for breeding, or to babies to speed growth. Pacified adults cannot enter love mode; a nearby Warped Fungus repellent can interfere with a breeding pen. Holding Crimson Fungus alone is not a checked Hoglin following method. [Food tag][hoglin-food] · [Food consumer][hoglin-food-check] · [Feeding dispatch][hoglin-use] · [Shared feeding][animal-feed] · [Love-mode gate][hoglin-love] · [Hoglin activities][hoglin-ai]

## Behavior

For decoration, the small fungus accepts **either Nylium, Soul Soil, Farmland, or dirt-tag blocks** such as Dirt, Grass Block, Mycelium and Mud. Soul Sand and Netherrack do not satisfy these support rules. This broader placement rule does not permit huge growth on the wrong substrate: Crimson Fungus needs **Crimson Nylium** for its Bone Meal target. [Support and growth checks][fungus] · [Inherited support][soil] · [Dirt tag][dirt] · [Nylium tag][nylium-tag]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game harvesting, growth or mob-interaction test was run. The active growth callback resolves the configured feature and dispatches its registered implementation. Data packs can change the checked loot, tags and features. [Configured dispatch][feature-dispatch] · [Feature registration][feature-registry]

Related: [Crimson Fungus block guide](../blocks/NetherFungi.md#crimson-fungus) · [Crimson Nylium](CrimsonNylium.md) · [Warped Fungus](WarpedFungus.md) · [Bone Meal](BoneMeal.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L362-L368
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5581
[loot-crimson_fungus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/crimson_fungus.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[nylium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L31-L88
[crimson-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation_bonemeal.json
[warped-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation_bonemeal.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L17-L47
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[fungus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L53-L80
[crimson-planted]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[huge]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L155
[hoglin-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/hoglin_food.json
[hoglin-food-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L271-L274
[hoglin-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L233-L239
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[hoglin-love]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L322-L336
[hoglin-ai]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L74-L105
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[dirt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[nylium-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/nylium.json
[feature-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L27
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L122-L127
