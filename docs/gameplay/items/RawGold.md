# Raw Gold

**Raw Gold** is the unprocessed metal obtained from ordinary Gold Ore and Deepslate Gold Ore. Cook it into [Gold Ingots](GoldIngot.md), or pack it into a Raw Gold Block for storage. Raw Gold, Gold Ingots and Gold Nuggets are different recipe ingredients. [Registration][registration] · [Ore loot][gold-loot] · [Deepslate loot][deepslate-loot]

## Obtaining

Mine Gold Ore or Deepslate Gold Ore with a suitable, unbroken **Iron, Diamond or Netherite Pickaxe**. Without Silk Touch or Fortune, a successful ordinary harvest drops **one Raw Gold**. Silk Touch selects the corresponding ore block instead; Fortune III can raise the raw-metal yield to **1–4**, without guaranteeing four. Follow [Ores and Ancient Debris](../blocks/OreResources.md#bring-a-suitable-pickaxe) for the harvesting restrictions and [Finding ores](../mechanics/FindingOres.md) for generation. [Ore loot][gold-loot] · [Deepslate loot][deepslate-loot] · [Fortune formula][fortune]

You can also unpack **one Raw Gold Block into nine Raw Gold** in a crafting-grid slot. This returns packed raw material; unpacking a **Gold Block** instead gives Gold Ingots. [Raw unpacking][unpack]

Raw Gold is listed in the **Ingredients** Creative catalog. Catalog visibility does not provide an ordinary Survival insertion route; see the [Inventory Browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Catalog][catalog]

## Usage

| Device | Input | Output | Recipe time | Recipe XP |
| --- | --- | --- | --- | --- |
| Furnace | 1 Raw Gold | 1 Gold Ingot | 200 ticks / 10 seconds | 1.0 |
| Blast Furnace | 1 Raw Gold | 1 Gold Ingot | 100 ticks / 5 seconds | 1.0 |

Times assume 20 game ticks per second and a fueled, operating device. Experience is recorded and awarded through the cooker's collection rules, not paid as an immediate orb when you insert the raw item. For the shared processing rules, see [processing raw metal](../blocks/OreResources.md#processing-raw-metal). [Smelting][smelt] · [Blasting][blast] · [Cooker experience][xp]

Fill a Crafting Table's **3 × 3 grid with nine Raw Gold** to make **one Raw Gold Block**. Unpack that block before using the individual-item cooking recipes above. See [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md) for the placed block's mining and storage details. [Packing][pack] · [Unpacking][unpack]

## Behavior

Raw Gold stacks to **64** and is a resource ingredient, with no food component in its ordinary registration. For [Piglin bartering](../mobs/Piglin.md#bartering), process it into Gold Ingots first: the checked barter currency is the ingot item. [Registration][registration] · [Default item construction][item-defaults] · [Item properties][item-properties] · [Stack size][stack] · [Barter currency][currency]

## Notes

- Item ID: `minecraft:raw_gold`
- The routes above cover ordinary ore harvesting, storage conversion and processing; this is not an exhaustive loot inventory
- Related: [Gold Ingot](GoldIngot.md), [Gold Nugget](GoldNugget.md), [Ores](../blocks/OreResources.md), [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item registration, the two ore loot tables, harvesting dependencies, recipe loading and cooking experience handling. No in-game mining, Fortune distribution, crafting or smelting test was run. Server settings and data packs can change the bundled behavior. [Recipe loading][recipes]

[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2797
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1302-L1304
[gold-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/gold_ore.json#L1-L52
[deepslate-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_gold_ore.json#L1-L52
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L132-L148
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/raw_gold.json#L1-L11
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1782-L1790
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_raw_gold.json#L1-L11
[blast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_raw_gold.json#L1-L11
[xp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L387
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/raw_gold_block.json#L1-L16
[currency]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L799-L803
