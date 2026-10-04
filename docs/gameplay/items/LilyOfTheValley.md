# Lily of the Valley

**Lily of the Valley** (`minecraft:lily_of_the_valley`) is a small flower you can collect, replant, or craft into White Dye. The [flower guide](../blocks/Flowers.md#lily-of-the-valley) compares its uses with the other species. [Item registration][] · [Block registration][]

## Obtaining

Break a planted Lily of the Valley by hand to collect **one item**; no special tool is needed. [Block registration][] · [Flower loot][] · [Hand collection][] · [Mining dispatch][]

Look for natural Lily of the Valley patches in **Forest**. Its mixed flower-patch feature can select lilies alongside separate tall-flower choices. [Forest biome][] · [Forest placement][] · [Forest patches][]

For a Bone Meal route, use **Grass Block with air above it in Flower Forest**, whose eligible flower feature includes lilies. The natural lily patch in ordinary Forest is excluded from Grass Block’s flower selection; finding lilies there does not make that Grass Block route produce them. **Bone Meal applied directly to the flower does not duplicate it.** See [Grass Block propagation](../blocks/Flowers.md#bone-meal-on-grass-block) for the shared limits. [Grass Bone Meal][] · [Biome flower filter][] · [Flower Forest biome][] · [Flower Forest placement][] · [Flower Forest flowers][] · [Small-flower behavior][] · [Bone Meal dispatch][]

## Usage

Craft **one Lily of the Valley into one White Dye** in a crafting grid. [Dye recipe][]

Use it as the flower in the [Suspicious Stew recipe](SuspiciousStew.md#obtaining) to make **one stew with Poison I for 220 ticks** (11 seconds at 20 TPS). The effect belongs to the stew when eaten. [Stew recipe][] · [Stew consumption][] · [Stew effects][] · [Effect level][]

## Behavior

Place this **one-block flower** on Grass Block, Dirt, or another [accepted flower soil](../blocks/Flowers.md#planting-and-support). It has no crop stages to grow through, and its survival check requires neither irrigation nor a particular light level. You can also use it on an empty [Flower Pot](../blocks/FlowerPot.md#supported-plants). The linked guides cover complete support and potting rules. [Soil support][] · [Soil tag][] · [Small-flower behavior][] · [Potted registration][] · [Pot interaction][]

## Notes

The item yields above assume block drops are enabled. Silk Touch and Fortune do not increase the flower’s normal item yield; blasts can lose the drop. Crafting consumes the input flower. [Flower loot][] · [Block-drop rule][] · [Ingredient consumption][]

[Flower uses with Bees and composters](../blocks/Flowers.md#bees-pots-and-other-uses) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked this variant’s registrations, complete loot and recipe data, the selected natural/renewal routes, and the relevant placement, harvesting, Bone Meal, and stew-consumption callers. These are checked acquisition examples; other biome, structure, trade, or loot routes remain unreviewed here. Data packs and game rules can change the results. No in-game acquisition, crafting, placement, harvesting, or propagation test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L355
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1049-L1059
[Flower loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/lily_of_the_valley.json#L1-L21
[Hand collection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Mining dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L292
[Forest biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/forest.json#L1-L207
[Forest placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/forest_flowers.json#L1-L32
[Forest patches]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/forest_flowers.json#L1-L148
[Grass Bone Meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L89
[Biome flower filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L47-L66
[Flower Forest biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/flower_forest.json#L1-L206
[Flower Forest placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/flower_flower_forest.json#L1-L23
[Flower Forest flowers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/flower_flower_forest.json#L1-L70
[Small-flower behavior]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L78
[Dye recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/white_dye_from_lily_of_the_valley.json#L1-L12
[Stew recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_lily_of_the_valley.json#L1-L23
[Stew consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L92
[Stew effects]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L72
[Effect level]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L58
[Soil support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[Potted registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2702-L2704
[Pot interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L45-L74
[Block-drop rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
