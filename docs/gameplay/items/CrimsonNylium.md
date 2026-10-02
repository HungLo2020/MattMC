# Crimson Nylium

**Crimson Nylium** (`minecraft:crimson_nylium`) is a solid ground block for Crimson fungi and renewable small Nether vegetation. The [Nylium family guide](../blocks/NetherGroundAndVegetation.md#crimson-nylium) compares it with Warped Nylium. [Item crimson_nylium]

## Obtaining

Mine with an **unbroken Silk Touch pickaxe** to recover one Crimson Nylium. A correct pickaxe without Silk Touch gives **one Netherrack** instead; a hand or incorrect/broken tool fails the drop gate. Wooden pickaxes satisfy the block's tier, and Fortune does not increase the count. Crimson Forest's checked surface rules provide a natural source. [Loot crimson_nylium] · [Pickaxe mining tag] · [Wooden-tool exclusions] · [Player correct-tool gate] · [Broken-tool drop guard] · [Nether surface materials]

## Usage

Use Bone Meal on exposed Crimson Nylium with air directly above to run its vegetation-placement feature. To make **more Nylium**, use Bone Meal on nearby **Netherrack** instead, following the [3 × 3 × 3 conversion rule](../blocks/NetherGroundAndVegetation.md#bone-meal-on-netherrack). Huge Crimson Fungus growth has a separate [fungus-targeted operation](../blocks/NetherFungi.md#bone-meal-and-huge-growth). [Nylium vegetation callbacks] · [Netherrack target and renewal]

## Behavior

A sufficiently occluding block above can turn it into Netherrack on a random tick. Ambient darkness alone is not that test. Keep an uncovered starter patch and recheck the ground after harvesting a huge fungus. [Nylium cover conversion] · [Cover and face occlusion]

## Notes

No crafting or smelting recipe produces Crimson Nylium in the checked data. The [family guide](../blocks/NetherGroundAndVegetation.md) owns support, cover, propagation, and natural-source comparisons.

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`. Checked this item/block registration, complete loot table, correct-tool and tier tags, cover conversion, Nylium/Netherrack Bone Meal callbacks, and the cited natural surface route. No in-game mining, support, water, Bone Meal, crafting, or world-generation test was run. Data packs can change tags, loot, recipes, and features.

[Loot crimson_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/crimson_nylium.json
[Item crimson_nylium]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L117
[Pickaxe mining tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Wooden-tool exclusions]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[Broken-tool drop guard]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L588
[Player correct-tool gate]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Nylium cover conversion]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L32-L43
[Cover and face occlusion]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L50-L58
[Nylium vegetation callbacks]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L46-L81
[Netherrack target and renewal]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/NetherrackBlock.java#L25-L71
[Nether surface materials]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json#L404-L560
