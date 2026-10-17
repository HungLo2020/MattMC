# Iron Nugget

An **Iron Nugget** is a small iron ingredient used for Lanterns and Iron Chains. Nine nuggets can be recombined into an [Iron Ingot](IronIngot.md), including nuggets recovered from spare equipment. [Registration][iron-nugget-item] · [Ingot recipe][iron-combine]

## Obtaining

Put **one Iron Ingot** in any crafting slot to make **nine Iron Nuggets**. [Recipe][iron-divide]

A **Furnace or Blast Furnace** also recycles one accepted equipment item into **one Iron Nugget**. The accepted list is Iron Pickaxe, Shovel, Axe, Hoe and Sword; Iron Helmet, Chestplate, Leggings, Boots and Horse Armor; and Chainmail Helmet, Chestplate, Leggings and Boots. The output is one nugget per piece, not its original ingot cost. [Furnace input list][iron-recycle] · [Blast Furnace input list][iron-recycle-blast]

| Recycling device | Cooking time per item | Recipe XP |
| --- | ---: | ---: |
| Furnace | 200 game ticks | 0.1 |
| Blast Furnace | 100 game ticks | 0.1 |

Those are the bundled recipe values, not measured elapsed times. The cooker accumulates XP by completed recipe and rounds fractional experience when awarding it. Ordinary [raw-iron processing](../blocks/OreResources.md#processing-raw-metal) returns ingots and uses its own recipe values; it is a separate workflow. [Furnace recipe][iron-recycle] · [Blast Furnace recipe][iron-recycle-blast] · [XP handling][xp-award]

Completed **[Piglin bartering](../mobs/Piglin.md#bartering)** has a possible **10–36 Iron Nugget** result. Pay an eligible adult with a **Gold Ingot**; Iron Nuggets are a possible reward, not an accepted payment, and no particular exchange guarantees them. [Reward entry][barter-iron] · [Currency][barter-currency-item] · [Completed response][barter-response] · [Active reward lookup][barter-loot-call]

## Usage

| Crafting layout | Output |
| --- | --- |
| Fill a 3 × 3 grid with 9 Iron Nuggets | 1 Iron Ingot · [Recipe][iron-combine] |
| Surround 1 Torch with 8 Iron Nuggets | 1 Lantern · [Recipe][lantern] |
| Surround 1 Soul Torch with 8 Iron Nuggets | 1 [Soul Lantern](SoulLantern.md) · [Recipe][soul-lantern] |
| Put 1 Iron Ingot between 2 Iron Nuggets in a vertical column | 1 [Iron Chain](IronChain.md) · [Recipe][chain] |

Breaking one ingot into nuggets covers the metal cost of one Lantern and leaves **one nugget**. A Chain still needs its separate center ingot; the two nuggets alone are insufficient.

## Behavior

An ordinary Iron Nugget stacks to **64**. Recycling is a low-return way to recover material from the listed equipment; it does not restore a whole Iron Ingot per smelted piece. [Registration][iron-nugget-item] · [Default stack][stack-default] · [Recycling result][iron-recycle]

## Notes

- This item is registered as `minecraft:iron_nugget`
- See [Ores and Ancient Debris](../blocks/OreResources.md) for mining and [resource storage](../blocks/ResourceStorageBlocks.md) for packed iron

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked active registration, ordinary conversion/recycling/use recipes, cooker XP handling and Piglin reward dispatch. No in-game cooking, crafting or barter test was run. Listed routes are selected practical sources; bundled data and server settings can be changed.

[iron-nugget-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2282-L2282
[iron-combine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/iron_ingot_from_nuggets.json#L1-L17
[iron-divide]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/iron_nugget.json#L1-L11
[iron-recycle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/iron_nugget_from_smelting.json#L1-L25
[iron-recycle-blast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/iron_nugget_from_blasting.json#L1-L25
[xp-award]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L387
[barter-iron]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L62-L77
[barter-currency-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L77-L83
[barter-response]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L399
[barter-loot-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L446
[lantern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/lantern.json#L1-L17
[soul-lantern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/soul_lantern.json#L1-L17
[chain]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/iron_chain.json#L1-L17
[stack-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
