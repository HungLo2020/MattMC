# Warped Roots

Warped Roots are decorative ground plants, with item and block ID `minecraft:warped_roots`. They are not a small fungus or a crop to mature. [Item registration][items] · [Block registration][blocks] · [Root behavior][roots]

## Obtaining

Break the plant by hand or with a tool to collect **one Warped Roots item**. Shears and Silk Touch are unnecessary, and Fortune does not increase the ordinary drop. [Full loot table][loot-warped_roots] · [Harvest gate][harvest]

For a renewable supply, apply Bone Meal **to Warped Nylium with air directly above the ground**. The Warped ground provider includes Warped Roots; the checked Crimson provider does not. Other vegetation can be selected and placement can fail, so a valid application consumes one Bone Meal in ordinary Survival without promising a particular harvest. See [Nylium vegetation](../blocks/NetherGroundAndVegetation.md#bone-meal-on-nylium) for provider weights and placement attempts. [Nylium callback][nylium] · [Crimson choices][crimson-vegetation] · [Warped choices][warped-vegetation] · [Placement checks][vegetation] · [Consumption][bonemeal]

## Usage

Plant the roots as ground cover on **either Nylium, Soul Soil, Farmland, or a dirt-tag block**. The checked dirt tag includes Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud and Muddy Mangrove Roots. **Soul Sand and Netherrack are not valid supports** under these rules. [Root support][roots] · [Shared plant support][soil] · [Dirt tag][dirt] · [Nylium tag][nylium-tag]

## Behavior

Roots have **no maturity stages or direct Bone Meal growth target** in their active class. They do not become huge fungi. To make more roots, target the Nylium ground rather than fertilizing an existing root plant. Their support check has no light-level requirement, but removing the supporting block makes them break. See [Roots and Nether Sprouts](../blocks/NetherGroundAndVegetation.md#roots-and-nether-sprouts) for placed behavior and potted forms. [Root class][roots] · [Plant survival][soil] · [Ground callback][nylium] · [Bone Meal target dispatch][bonemeal]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game collection, placement or Bone Meal test was run. Data packs can change loot, support tags and vegetation choices.

Related: [Warped Roots block guide](../blocks/NetherGroundAndVegetation.md#warped-roots) · [Nether Fungi](../blocks/NetherFungi.md) · [Warped Nylium](WarpedNylium.md) · [Bone Meal](BoneMeal.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L362-L368
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5581
[roots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RootsBlock.java#L13-L33
[loot-warped_roots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/warped_roots.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[nylium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L31-L88
[crimson-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_forest_vegetation_bonemeal.json
[warped-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation_bonemeal.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L17-L47
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[soil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[dirt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[nylium-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/nylium.json
