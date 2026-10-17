# Poppy

**Poppy** (`minecraft:poppy`) is a small flower you can collect, replant, or craft into Red Dye. The [flower guide](../blocks/Flowers.md#poppy) compares its uses with the other species. [Item registration][] · [Block registration][]

## Obtaining

Break a planted Poppy by hand to collect **one item**; no special tool is needed. [Block registration][] · [Flower loot][] · [Hand collection][] · [Mining dispatch][]

Look for Poppies in **Forest**, where ordinary flower patches can select Poppies or Dandelions. [Forest biome][] · [Forest ordinary placement][] · [Forest ordinary flowers][]

For more, use [Bone Meal](BoneMeal.md) on **Grass Block with air above it in Forest**. The eligible flower route can select Poppy or Dandelion, and a use need not produce a Poppy. **Bone Meal applied directly to the flower does not duplicate it.** See [Grass Block propagation](../blocks/Flowers.md#bone-meal-on-grass-block) for the shared limits. [Grass Bone Meal][] · [Biome flower filter][] · [Forest ordinary flowers][] · [Small-flower behavior][] · [Bone Meal dispatch][]

## Usage

Craft **one Poppy into one Red Dye** in a crafting grid. [Dye recipe][]

Use it as the flower in the [Suspicious Stew recipe](SuspiciousStew.md#obtaining) to make **one stew with Night Vision I for 100 ticks** (5 seconds at 20 TPS). The effect belongs to the stew when eaten. [Stew recipe][] · [Stew consumption][] · [Stew effects][] · [Effect level][]

## Behavior

Place this **one-block flower** on Grass Block, Dirt, or another [accepted flower soil](../blocks/Flowers.md#planting-and-support). It has no crop stages to grow through, and its survival check requires neither irrigation nor a particular light level. You can also use it on an empty [Flower Pot](../blocks/FlowerPot.md#supported-plants). The linked guides cover complete support and potting rules. [Soil support][] · [Soil tag][] · [Small-flower behavior][] · [Potted registration][] · [Pot interaction][]

## Notes

The item yields above assume block drops are enabled. Silk Touch and Fortune do not increase the flower’s normal item yield; blasts can lose the drop. Crafting consumes the input flower. [Flower loot][] · [Block-drop rule][] · [Ingredient consumption][]

[Flower uses with Bees and composters](../blocks/Flowers.md#bees-pots-and-other-uses) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked this variant’s registrations, complete loot and recipe data, the selected natural/renewal routes, and the relevant placement, harvesting, Bone Meal, and stew-consumption callers. These are checked acquisition examples; other biome, structure, trade, or loot routes remain unreviewed here. Data packs and game rules can change the results. No in-game acquisition, crafting, placement, harvesting, or propagation test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L345
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L928-L938
[Flower loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/poppy.json#L1-L21
[Hand collection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Mining dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L292
[Forest biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/forest.json#L1-L207
[Forest ordinary placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/flower_default.json#L1-L19
[Forest ordinary flowers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/flower_default.json#L1-L41
[Grass Bone Meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L32-L89
[Biome flower filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L47-L66
[Small-flower behavior]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L78
[Dye recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_poppy.json#L1-L12
[Stew recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_poppy.json#L1-L23
[Stew consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L92
[Stew effects]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L72
[Effect level]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L58
[Soil support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[Potted registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2690
[Pot interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java#L45-L74
[Block-drop rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
