# Bush

Bush (`minecraft:bush`) is a small decorative plant that can be multiplied with Bone Meal. **Collect it with Shears or Silk Touch**, or the plant is lost. [Item registration][items] · [Loot][bush-loot]

## Obtaining

Break a Bush with **Shears or a tool carrying Silk Touch I or higher** to receive **one Bush**. Bare hands and other ordinary tools give no item; Fortune does not increase the yield. The checked **Plains** patch is a natural source, subject to space and valid ground. See [checked acquisition examples](../blocks/ShrubsAndDryGrass.md#checked-acquisition-examples) for the generation route. [Loot][bush-loot] · [Plains features][plains] · [Bush patch][bush-patch]

No bundled crafting recipe makes Bush. Its Natural Blocks category entry also supplies a [Creative inventory-browser route](../mechanics/InventoryBrowser.md); catalog visibility in Survival does not allow ordinary Survival insertion. [Category entry][bush-category]

## Usage

Plant a Bush on **Farmland or a block in the dirt support tag**. It needs neither nearby water nor a particular light level. The [support guide](../blocks/ShrubsAndDryGrass.md#support-and-water) lists the eligible ground. [Placement checks][placement] · [Plant support][vegetation] · [Dirt tag][dirt]

One accepted Survival **Bone Meal** use creates **one new Bush** in an empty cell among the four horizontal neighbors at the same height, with valid ground beneath it. The original remains. If none of those cells qualifies, direct Bone Meal use is unavailable. Harvest the new plant with Shears or Silk Touch too. [Bush growth][bush] · [Neighbor selection][spread] · [Bone Meal consumption][bone-meal]

Bush has a **30%** ordinary chance to raise a Composter by one level; the first accepted item in an empty Composter succeeds automatically. It is absent from the checked default Furnace fuel list. [Compost value][compost] · [Compost use][compost-use] · [Fuel list][fuel]

## Behavior

Bush breaks instantly, has no collision or waterlogged form, and does not spread automatically. **Incoming water or support loss does not provide the Shears/Silk Touch needed for recovery**, so collect it before flooding or removing its ground. [Registration][bush-reg] · [Plant support][vegetation] · [Water replacement][water] · [Water drops][water-loot] · [Support removal][support-loss] · [Loot][bush-loot]

## Notes

This is the item form of `minecraft:bush`, separate from [Firefly Bush](FireflyBush.md) and [Dead Bush](DeadBush.md). The [Shrubs and Dry Grass guide](../blocks/ShrubsAndDryGrass.md) compares their harvesting and growth rules.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, using the bundled recipes, loot and tags and the active behavior paths linked here. No gameplay test was run; custom data-pack changes are outside this review.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[bush-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/bush.json
[plains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/plains.json
[bush-patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/patch_bush.json
[bush-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L893-L899
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L141
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json
[bush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BushBlock.java#L33-L47
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BonemealableBlock.java#L20-L37
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L110-L114
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L273-L319
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L108
[bush-reg]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L746-L757
[water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[water-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[support-loss]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L213-L233
