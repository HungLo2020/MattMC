# Nether Sprouts

**Nether Sprouts** (`minecraft:nether_sprouts`) are small decorative plants. Their [family guide](../blocks/NetherGroundAndVegetation.md#nether-sprouts) distinguishes their Shears-only recovery from hand-collectible Crimson and Warped Roots. [Item nether_sprouts]

## Obtaining

Mine with **Shears** for **one Nether Sprouts item**. Bare hands and other tools, including Silk Touch on a non-Shears item, give none. Fortune does not multiply the result. Warped Forest selects a natural Sprouts feature, and Bone Meal on exposed Warped Nylium runs a separate Sprouts-placement pass. [Loot nether_sprouts] · [Warped Forest biome] · [Placed feature nether_sprouts] · [Configuration nether_sprouts] · [Nylium vegetation callbacks] · [Configuration nether_sprouts_bonemeal]

## Usage

Plant on **either Nylium, Soul Soil, Farmland, or a dirt-tag block**. Soul Sand and Netherrack do not satisfy this support test. The plant needs no brightness threshold or Nether dimension in that check. See [accepted supports](../blocks/NetherGroundAndVegetation.md#roots-and-nether-sprouts). [Sprout support and behavior] · [Shared vegetation survival] · [Nylium tag] · [Dirt support tag]

## Behavior

Sprouts have no growth age or direct Bone Meal target. Use Bone Meal on the Nylium ground to produce more, rather than expecting the existing Sprouts to mature. They are not waterlogged; water or support removal can destroy them without supplying the Shears needed for item recovery. [Sprout support and behavior] · [Registry nether_sprouts] · [Bone Meal dispatch and consumption] · [Water harvest drops] · [Loot nether_sprouts]

## Notes

No crafting or smelting recipe produces this item in the checked data. Nether Sprouts also have no registered filled Flower Pot form; the two Roots do. [Flower Pot](../blocks/FlowerPot.md#supported-plants) owns potting behavior. [Potted root registrations]

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`. Checked the registry, complete Shears-only loot, support and water callbacks, absence of a direct Bone Meal handler, natural/renewable Sprouts feature routes, and recipe absence. No in-game mining, support, water, Bone Meal, crafting, or world-generation test was run. Data packs can change tags, loot, recipes, and features.

[Registry nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5495-L5506
[Loot nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/nether_sprouts.json
[Item nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L366
[Nylium tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/nylium.json
[Dirt support tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/dirt.json
[Nylium vegetation callbacks]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L46-L81
[Bone Meal dispatch and consumption]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[Sprout support and behavior]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NetherSproutsBlock.java#L12-L33
[Shared vegetation survival]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Water harvest drops]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Configuration nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/nether_sprouts.json
[Configuration nether_sprouts_bonemeal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/nether_sprouts_bonemeal.json
[Warped Forest biome]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[Placed feature nether_sprouts]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/placed_feature/nether_sprouts.json
[Potted root registrations]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5800-L5805
