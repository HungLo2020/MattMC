# Block of Raw Copper

A **Block of Raw Copper** (`minecraft:raw_copper_block`) packs nine Raw Copper items into a full building block. It stores raw ore drops, not Copper Ingots; the refined [Block of Copper](BlockOfCopper.md) belongs to a separate construction family. [Item registration][items] · [Block registration][raw-properties]

<span id="obtaining"></span>

## Packing and unpacking

Fill a **3 × 3 Crafting Table grid with nine [Raw Copper](RawCopper.md)** (`minecraft:raw_copper`) to make **one Block of Raw Copper**. Ingots cannot replace the exact raw-item ingredient. [Packing recipe][pack]

Put **one Block of Raw Copper into any crafting slot** to recover **nine Raw Copper items**. This shapeless recipe fits the personal grid, so the storage conversion is reversible without placing or mining the block. [Unpacking recipe][unpack]

The item is category-listed for the [inventory browser](../mechanics/InventoryBrowser.md). Creative requests still depend on an empty cursor, inventory space and the browser's feature/server checks. Its visible Survival entry does not allow ordinary Survival insertion. [Category entry][category-raw]

<span id="behavior"></span>

## Recovering a placed block

Bring an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Wooden and Golden Pickaxes, hand mining and other ordinary tool types fail its drop requirement. The block's Stone-tier requirement is separate from the tool's speed. [Pickaxe tag][pickaxe] · [Required tier][stone] · [Wood exclusions][wood] · [Gold exclusions][gold] · [Material rules][materials] · [Harvest gate][harvest] · [Broken-tool check][broken]

Successful normal mining returns **one Block of Raw Copper**, not nine raw pieces. Silk Touch is unnecessary and Fortune does not increase the count. Explosions have a survival condition, so their drops are not guaranteed. Unpack the recovered block in a crafting grid when you need loose material. [Complete loot][loot] · [Unpacking][unpack]

<span id="usage"></span>

## Processing and building

**Unpack before smelting or blasting.** The checked Furnace and Blast Furnace recipes consume individual `minecraft:raw_copper` items and each produce one Copper Ingot. There is no bundled whole-block smelting or blasting recipe for this storage block. Follow [Raw Copper processing](RawCopper.md#processing-and-storage) for the full route. [Smelting][copper-smelt] · [Blasting][copper-blast]

As a placed building block, it has **5 hardness and 6 blast resistance**. It is a fixed full cube with no inventory or waterlogged state; removing its support does not make it fall. It **does not oxidize**, and the checked Honeycomb-waxing and axe-scraping maps have no Raw Copper Block conversion. [Properties][raw-properties] · [Shape and support][defaults] · [Fluid default][fluid] · [Oxidation][weather] · [Waxing][wax] · [Axe conversions][axe]

Under the bundled Beacon rules, it is **neither a base material nor a payment item**. Packing raw copper does not give it the uses of another metal's storage block. [Base tag][base-tag] · [Base consumer][base-check] · [Payment tag][payment-tag] · [Payment slot][payment-slot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registrations, recipes, complete loot, mining restrictions, placement defaults and the listed use/conversion consumers. No gameplay test was run and no natural storage-block location is claimed. Data packs and later builds can alter recipes, tags and loot. [Raw Copper storage](../blocks/RawCopperStorage.md) owns the detailed block behavior.

Related: [Raw Copper](RawCopper.md) · [Raw Copper storage](../blocks/RawCopperStorage.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[raw-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6722-L6725
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/raw_copper_block.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/raw_copper.json
[category-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L829-L831
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/raw_copper_block.json
[copper-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_raw_copper.json
[copper-blast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_raw_copper.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java
[base-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[base-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[payment-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[payment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L158-L171
