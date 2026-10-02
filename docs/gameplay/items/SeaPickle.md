# Sea Pickle

**Sea Pickle** (`minecraft:sea_pickle`) places the clustered underwater light described in the [Sea Pickle block guide](../blocks/SeaPickle.md). It is also a smelting ingredient for Lime Dye. [Item registration][sea-items] · [Light registration][pickle-reg]

## Obtaining and placing

Ordinary mining returns the placed cluster's **one to four Sea Pickles**, with no tool or Silk Touch requirement and no Fortune multiplier. Dry and waterlogged clusters use the same count-based loot. The block guide covers a [verified natural source](../blocks/SeaPickle.md#finding-and-collecting) and [Bone Meal multiplication](../blocks/SeaPickle.md#bone-meal-multiplication). [Loot][pickle-loot]

Use the item on valid support, or add it to an existing cluster up to four. Only waterlogged pickles emit light. See [placement, water, and light](../blocks/SeaPickle.md#placement-water-and-light) for source-water and bucket rules. [Placement and combining][pickle-place]

## Smelting into Lime Dye

Smelt **1 Sea Pickle** in a fueled [Furnace](../blocks/Furnace.md) to obtain **1 [Lime Dye](LimeDye.md)**. The recipe takes **200 ticks**, about **10 seconds at 20 ticks per second**, and declares **0.1 recipe XP**. This is a normal smelting recipe. [Exact recipe][lime-recipe] · [Furnace type][furnace]

Use [Furnace experience handling](../blocks/Furnace.md#experience-and-troubleshooting) for payout and [fuel planning](../blocks/Furnace.md#fuel-planning) for batch sizes. For the separate dye-mixing route, see [Green Dye](GreenDye.md); that page owns its color-mixing recipe.

Related: [Sea Pickle block](../blocks/SeaPickle.md) · [Lime Dye](LimeDye.md) · [Seagrass](Seagrass.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Item registration, cluster loot, placement, light, and the exact smelting route checked. No in-game collection, placement, or smelting test was run. Data packs can change recipes and loot.

[sea-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L324-L325
[pickle-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5165-L5174
[pickle-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/sea_pickle.json
[pickle-place]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SeaPickleBlock.java#L47-L103
[lime-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/lime_dye_from_smelting.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
