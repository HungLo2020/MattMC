# Block of Raw Gold

A **Block of Raw Gold** (`minecraft:raw_gold_block`) combines nine Raw Gold pieces for storage or building. It remains raw metal: obtaining Gold Ingots requires unpacking and processing the pieces. [Item registration][items] · [Block registration][raw-properties]

<span id="obtaining"></span>

## Packing and unpacking

Fill a **3 × 3 Crafting Table grid with nine [Raw Gold](RawGold.md)** (`minecraft:raw_gold`) to make **one Block of Raw Gold**. This requires raw pieces; Gold Ingots are the ingredient for the separate [Block of Gold](BlockOfGold.md). [Packing recipe][pack]

Place **one Block of Raw Gold in any crafting slot** to unpack it into **nine Raw Gold items**. This shapeless recipe fits the personal crafting grid. Placing or breaking the block is not part of the unpacking recipe. [Unpacking recipe][unpack]

Creative players can also request it through the category-backed [inventory browser](../mechanics/InventoryBrowser.md), subject to empty-cursor, inventory-capacity, feature and server checks. A listing in Survival does not itself admit an insertion request. [Category entry][category-raw]

<span id="behavior"></span>

## Mining and Piglins

Recover a placed block with an **unbroken Iron, Diamond or Netherite Pickaxe**. Wooden, Golden, Stone and Copper Pickaxes do not meet its drop requirement, nor do hand mining and other ordinary tools. [Pickaxe tag][pickaxe] · [Iron-tier requirement][iron] · [Material rules][materials] · [Wood exclusions][wood] · [Gold exclusions][gold] · [Stone exclusions][stone-exclusion] · [Copper exclusions][copper-exclusion] · [Harvest gate][harvest] · [Broken-tool check][broken]

Correct-tool mining returns **one Block of Raw Gold**, not its nine ingredients. Silk Touch is unnecessary and Fortune does not multiply the result. Its loot has an explosion-survival condition, so blast recovery is not guaranteed. [Complete loot][loot]

**Breaking Raw Gold Blocks can anger eligible nearby idle [Piglins](../mobs/Piglin.md).** The guarded-block callback runs before the ordinary tool/drop check; an unsuitable pickaxe or Silk Touch does not remove that callback. Target eligibility and game rules still determine the resulting anger. [Guarded tag][guarded] · [Break callback][anger-trigger] · [Break ordering][harvest] · [Piglin search][anger] · [Target check][anger-target]

The block item is Piglin-loved, but **it is not barter currency**. The barter check requires a Gold Ingot. Unpacking produces Raw Gold, which still needs smelting or blasting before it becomes that currency. [Loved tag][loved] · [Pickup consumer][loved-use] · [Currency][currency] · [Currency check][currency-check]

<span id="usage"></span>

## Processing and placement

Unpack first, then process the individual Raw Gold items in a [Furnace](../blocks/Furnace.md) or [Blast Furnace](../blocks/Furnace.md#blast-furnace-and-smoker). Each checked recipe takes one Raw Gold and yields one Gold Ingot; the bundled recipes do not smelt or blast the entire storage block. [Smelting][gold-smelt] · [Blasting][gold-blast]

The placed block has **5 hardness and 6 blast resistance**. It is a full fixed cube with no inventory or waterlogged state, and it remains when support is removed. Under bundled rules it is **neither a Beacon base block nor a Beacon payment item**. [Properties][raw-properties] · [Shape and support][defaults] · [Fluid default][fluid] · [Base tag][base-tag] · [Base validation][base-check] · [Payment tag][payment-tag] · [Payment slot][payment-slot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked both storage recipes, raw-item processing, complete block loot, tool gates, placement and active Piglin/Beacon consumers. No gameplay test was run or natural block location established. Data packs and later builds can change these rules. See [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md#raw-gold-block) for the family comparison.

Related: [Raw Gold](RawGold.md) · [Gold and Piglins](../blocks/ResourceStorageBlocks.md#gold-and-piglins) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[raw-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6726-L6729
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/raw_gold_block.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/raw_gold.json
[category-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L829-L831
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[iron]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/raw_gold_block.json
[guarded]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[anger-trigger]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487
[anger]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533
[anger-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L676-L688
[loved]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/piglin_loved.json
[loved-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L452-L476
[currency]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L80
[gold-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/gold_ingot_from_smelting_raw_gold.json
[gold-blast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/gold_ingot_from_blasting_raw_gold.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[base-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[payment-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[stone-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[copper-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
[currency-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L801-L803
[base-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[payment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L158-L171
