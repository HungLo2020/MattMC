# White Tulip

**White Tulip** (`minecraft:white_tulip`) is a small flower you can collect, replant, or craft into Light Gray Dye. The [flower guide](../blocks/Flowers.md#white-tulip) compares its uses with the other species. [Item registration][] · [Block registration][]

## Obtaining

Break a planted White Tulip by hand to collect **one item**; no special tool is needed. [Block registration][] · [Flower loot][] · [Hand collection][] · [Mining dispatch][]

Look for patches of White Tulip in **Plains**. Flower choice depends on position, so explore another part of the biome if you find only other species. [Plains biome][] · [Plains placement][] · [Plains flowers][] · [Plains selection][]

Use [Bone Meal](BoneMeal.md) on **Grass Block with air above it in Plains** for another chance to generate White Tulip. The biome’s position-dependent flower choices still apply, and a use need not produce this species. **Bone Meal applied directly to the flower does not duplicate it.** See [Grass Block propagation](../blocks/Flowers.md#bone-meal-on-grass-block) for the shared limits. [Grass Bone Meal][] · [Biome flower filter][] · [Plains flowers][] · [Plains selection][] · [Small-flower behavior][] · [Bone Meal dispatch][]

## Usage

Craft **one White Tulip into one Light Gray Dye** in a crafting grid. [Dye recipe][]

Use it as the flower in the [Suspicious Stew recipe](SuspiciousStew.md#obtaining) to make **one stew with Weakness I for 140 ticks** (7 seconds at 20 TPS). The effect belongs to the stew when eaten. [Stew recipe][] · [Stew consumption][] · [Stew effects][] · [Effect level][]

## Behavior

Place this **one-block flower** on Grass Block, Dirt, or another [accepted flower soil](../blocks/Flowers.md#planting-and-support). It has no crop stages to grow through, and its survival check requires neither irrigation nor a particular light level. You can also use it on an empty [Flower Pot](../blocks/FlowerPot.md#supported-plants). The linked guides cover complete support and potting rules. [Soil support][] · [Soil tag][] · [Small-flower behavior][] · [Potted registration][] · [Pot interaction][]

## Notes

The item yields above assume block drops are enabled. Silk Touch and Fortune do not increase the flower’s normal item yield; blasts can lose the drop. Crafting consumes the input flower. [Flower loot][] · [Block-drop rule][] · [Ingredient consumption][]

[Flower uses with Bees and composters](../blocks/Flowers.md#bees-pots-and-other-uses) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked this variant’s registrations, complete loot and recipe data, the selected natural/renewal routes, and the relevant placement, harvesting, Bone Meal, and stew-consumption callers. These are checked acquisition examples; other biome, structure, trade, or loot routes remain unreviewed here. Data packs and game rules can change the results. No in-game acquisition, crafting, placement, harvesting, or propagation test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L351
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L994-L1004
[Flower loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/white_tulip.json#L1-L21
[Hand collection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Mining dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L292
[Plains biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/plains.json#L1-L202
[Plains placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/flower_plains.json#L1-L25
[Plains flowers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/flower_plain.json#L1-L68
[Plains selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/stateproviders/NoiseThresholdProvider.java#L50-L58
[Grass Bone Meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L89
[Biome flower filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L47-L66
[Small-flower behavior]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L78
[Dye recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_white_tulip.json#L1-L12
[Stew recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_white_tulip.json#L1-L23
[Stew consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L92
[Stew effects]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L72
[Effect level]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L58
[Soil support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[Potted registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2698
[Pot interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L45-L74
[Block-drop rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
