# Warped Nylium

Warped Nylium is renewable planting ground for Warped Fungus and small Nether vegetation. Its item and block ID is `minecraft:warped_nylium`. [Block registration][blocks]

## Obtaining

Mine it with a **pickaxe enchanted with Silk Touch** to collect **one Warped Nylium**. A qualifying pickaxe without Silk Touch returns **one Netherrack** instead; Fortune does not change either result. Hand mining, a nonqualifying tool, or a broken pickaxe fails this block's correct-tool drop gate. Silk Touch alone does not bypass that gate. [Full loot alternatives][loot-warped_nylium] · [Required-tool registration][blocks] · [Pickaxe tag][pickaxe-tag] · [Player gate][harvest] · [Broken-tool gate][broken-gate] · [Mining dispatch][mining]

To expand a starter patch, use Bone Meal **on Netherrack within the 3 × 3 × 3 neighborhood of existing Nylium**, including diagonals and one level above or below. The block above the target must propagate skylight downward; leaving air there works. One accepted Survival application consumes one Bone Meal and converts the target. Only Warped Nylium nearby gives Warped Nylium; both colors present give a **50/50 color choice**. The starter is retained. See [Nylium renewal](../blocks/NetherGroundAndVegetation.md#bone-meal-on-netherrack). [Conversion checks][netherrack] · [Nylium tag][nylium-tag] · [Consumption][bonemeal]

## Usage

Plant **Warped Fungus** on it for that fungus's huge-growth route. Crimson Fungus and both Roots can also survive here as small decorations, but Crimson Fungus needs Crimson Nylium for huge growth. See [fungus substrates](../blocks/NetherFungi.md#fungus-and-substrate-comparison). [Fungus checks][fungus] · [Root support][roots] · [Registrations][blocks]

Apply Bone Meal **to Warped Nylium with air immediately above** to attempt small vegetation: Warped Roots, occasional Crimson Roots, and either fungus species. The callback also attempts Nether Sprouts, then has a **1-in-8 chance to attempt Twisting Vines**. These are placement attempts, not promised item yields; a valid Survival use consumes one Bone Meal even if no useful plant appears. Full weights and placement limits belong to [Bone Meal on Nylium](../blocks/NetherGroundAndVegetation.md#bone-meal-on-nylium). [Ground callback][nylium] · [Vegetation choices][warped-vegetation] · [Placement checks][vegetation] · [Consumption][bonemeal]

## Behavior

Keep a starter patch uncovered. On a random tick, sufficiently light-blocking cover directly above converts Warped Nylium into **Netherrack**. This is a cover/face-occlusion check, not a need for daylight. Removing the cover does not restore Nylium, and Nylium does not spread itself onto neighboring Netherrack through that tick callback. Use the Bone Meal conversion route to restore it. [Cover and vegetation behavior][nylium] · [Netherrack renewal][netherrack]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game mining, cover-conversion or cultivation test was run. Data packs can change loot, tags and features.

Related: [Warped Nylium block guide](../blocks/NetherGroundAndVegetation.md#warped-nylium) · [Warped Fungus](WarpedFungus.md) · [Netherrack](Netherrack.md) · [Crimson Nylium](CrimsonNylium.md) · [Bone Meal](BoneMeal.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5581
[loot-warped_nylium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/warped_nylium.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[netherrack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NetherrackBlock.java#L25-L73
[nylium-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/nylium.json
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[fungus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L53-L80
[roots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RootsBlock.java#L13-L33
[nylium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L31-L88
[warped-vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/warped_forest_vegetation_bonemeal.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/NetherForestVegetationFeature.java#L17-L47
