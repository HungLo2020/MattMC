# Warped Fungus

Warped Fungus grows huge Warped fungi, feeds Striders, and repels Hoglins when planted. Its item and placed-block ID is `minecraft:warped_fungus`. [Item registration][items] · [Block registration][blocks]

## Obtaining

Break a small Warped Fungus by hand or with a tool to collect **one Warped Fungus**. Shears and Silk Touch are unnecessary, and Fortune does not increase the ordinary drop. [Full block loot][loot-warped_fungus] · [Harvest gate][harvest]

With an existing Nylium patch, apply Bone Meal **to either Nylium color with air immediately above it**. Both vegetation providers include Warped Fungus, although their other choices and weights differ. A use consumes one Bone Meal in ordinary Survival even if placement attempts produce no useful fungus. See [renewable small fungi](../blocks/NetherFungi.md#producing-more-small-fungi). [Ground callback][nylium] · [Crimson choices][crimson-vegetation] · [Warped choices][warped-vegetation] · [Placement checks][vegetation] · [Consumption][bonemeal]

## Usage

Place it on **Warped Nylium** and use Bone Meal **on the fungus**. A valid use consumes one Bone Meal in ordinary Survival and has a **40% chance to attempt the planted huge fungus**. That configuration uses Warped Stem and Warped Wart Block. A successful chance is not a guaranteed complete structure: obstructions can leave gaps after the small fungus is cleared. Use the [huge-growth guide](../blocks/NetherFungi.md#bone-meal-and-huge-growth) for clearance and harvesting. [Growth checks][fungus] · [Consumption][bonemeal] · [Planted configuration][warped-planted] · [Placement][huge]

- **Striders:** holding Warped Fungus attracts them; feeding two ready adults breeds them, and feeding a baby speeds its growth. See [Strider breeding](../mobs/Strider.md#breeding-and-growth). The attraction tag includes the food tag, and the feeding path consumes that food through the shared animal interaction. [Tempting tag][strider-tempt] · [Tempting goal][strider-goals] · [Food tag][strider-food] · [Strider interaction][strider-use] · [Shared feeding][animal-feed]
- **Steering:** craft **one Fishing Rod plus one Warped Fungus**, with the fungus diagonally below-right of the rod, into **one [Warped Fungus on a Stick](WarpedFungusonaStick.md)**. A saddled Strider's steering check requires that separate item; loose Warped Fungus does not satisfy it. [Recipe][stick-recipe] · [Steering check][strider-control]
- **Hoglins:** place Warped Fungus, or its potted form, as a repellent. The block tag feeds an active sensor and avoidance/pacification behavior; merely holding this item is not that block check. Read [Hoglin containment](../mobs/Hoglin.md#repelling-and-containing-hoglins), especially before placing repellents beside a breeding pen. Hoglin food is Crimson Fungus. [Repellent tag][hoglin-repellent] · [Sensor][hoglin-sensor] · [Activities][hoglin-ai] · [Hoglin food][hoglin-food]

## Behavior

Decorative planting accepts **either Nylium, Soul Soil, Farmland, or dirt-tag blocks**, including Dirt, Grass Block, Mycelium and Mud. **Soul Sand and Netherrack are not valid supports** in these checks. Huge growth has the narrower requirement of **Warped Nylium**; a fungus that survives on Crimson Nylium is still the wrong growth target. [Support and growth][fungus] · [Shared support][soil] · [Dirt tag][dirt] · [Nylium tag][nylium-tag]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game growth, harvesting, breeding, riding or repellent test was run. The planted feature resolves through the active configured-feature dispatch. Data packs can change loot, tags, recipes and features. [Configured dispatch][feature-dispatch] · [Feature registration][feature-registry]

Related: [Warped Fungus block guide](../blocks/NetherFungi.md#warped-fungus) · [Warped Nylium](WarpedNylium.md) · [Crimson Fungus](CrimsonFungus.md) · [Bone Meal](BoneMeal.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L362-L368
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5581
[loot-warped_fungus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/warped_fungus.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[nylium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L31-L88
[crimson-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation_bonemeal.json
[warped-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation_bonemeal.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L17-L47
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[fungus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L53-L80
[warped-planted]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[huge]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L155
[strider-tempt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/strider_tempt_items.json
[strider-goals]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Strider.java#L139-L149
[strider-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/strider_food.json
[strider-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Strider.java#L373-L402
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[stick-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/warped_fungus_on_a_stick.json
[strider-control]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Strider.java#L190-L196
[hoglin-repellent]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/hoglin_repellents.json
[hoglin-sensor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/sensing/HoglinSpecificSensor.java#L31-L63
[hoglin-ai]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L74-L105
[hoglin-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/hoglin_food.json
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[dirt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[nylium-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/nylium.json
[feature-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L27
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L122-L127
