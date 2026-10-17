# Warped Wart Block

Warped Wart Block is a solid building material used in huge Warped fungus caps. Its item and block ID is `minecraft:warped_wart_block`. [Block registration][blocks] · [Planted cap material][warped-planted]

## Obtaining

Break a Warped Wart Block by hand or with a tool to collect **one matching block**. A **hoe is the efficient tool**, but there is no correct-tool requirement for this block's ordinary drop. Neither Silk Touch nor Fortune changes the one-block result. [Full block loot][loot-warped_wart_block] · [Hoe tag][hoe-tag] · [Block registration][blocks] · [Harvest gate][harvest]

For renewable cap material, grow **Warped Fungus on Warped Nylium** with Bone Meal, then harvest the generated cap. The active planted feature selects Warped Wart Block as its cap material. The fungus has a **40% growth-attempt chance per valid application**, and ordinary Survival consumes one Bone Meal even on a failed chance. Clear space first: placement and obstructions affect the cap, so there is no fixed number of cap blocks per use. Follow [Nether Fungi](../blocks/NetherFungi.md#bone-meal-and-huge-growth) for the complete cultivation route. [Fungus checks][fungus] · [Consumption][bonemeal] · [Planted configuration][warped-planted] · [Cap placement][huge]

## Usage

Use it as a full building block or decorative fungus-cap material. Mining it returns the **Warped Wart Block itself**, not loose [Nether Wart](NetherWart.md). The plantable brewing crop and [Nether Wart Block](NetherWartBlock.md) are separate items. [Registration][blocks] · [Block loot][loot-warped_wart_block]

## Behavior

This is a plain solid block, with no leaf-distance or leaf-decay behavior. It does not need a fungus stem beneath it, and removing the stem does not trigger leaf-style decay. The [Nether and Warped Wart Blocks guide](../blocks/NetherGroundAndVegetation.md#nether-and-warped-wart-blocks) owns the placed behavior and related cap-material rules. [Block registration][blocks]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game mining or fungus-growth test was run. The fungus callback resolves the registered configured feature; data packs can change its materials and loot. [Growth callback][fungus] · [Configured dispatch][feature-dispatch] · [Feature registration][feature-registry]

Related: [Warped Wart Block guide](../blocks/NetherGroundAndVegetation.md#warped-wart-block) · [Warped Fungus](WarpedFungus.md) · [Warped Nylium](WarpedNylium.md) · [Nether Fungi](../blocks/NetherFungi.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5581
[warped-planted]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/warped_fungus_planted.json
[loot-warped_wart_block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/warped_wart_block.json
[hoe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[fungus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FungusBlock.java#L53-L80
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[huge]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L23-L155
[feature-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L18-L27
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L122-L127
