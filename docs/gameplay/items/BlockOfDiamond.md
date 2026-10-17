# Block of Diamond

A **Block of Diamond** (`minecraft:diamond_block`) stores nine Diamonds in one block item. It can be placed for construction or used as a qualifying Beacon base block, then recovered and unpacked when the gems are needed. [Item registration][items] · [Block registration][gem-properties]

<span id="obtaining"></span>

## Packing and unpacking

Fill all nine slots of a **Crafting Table** with **Diamonds** (`minecraft:diamond`) to make **one Block of Diamond**. Put **one block in any crafting slot** to recover **nine [Diamonds](Diamond.md)**. Unpacking is shapeless and fits the personal 2 × 2 grid. Neither recipe consumes an extra resource or requires placing the block. [Packing recipe][pack] · [Unpacking recipe][unpack]

Creative players can also request the listed block through the [inventory browser](../mechanics/InventoryBrowser.md). The browser's empty-cursor, capacity, feature and server checks still apply; an entry visible in ordinary Survival does not provide inventory insertion. [Category entry][category-gems]

<span id="behavior"></span>

## Recovering and placing blocks

Use an **unbroken Iron, Diamond or Netherite Pickaxe** to collect a placed block. Wooden, Golden, Stone and Copper Pickaxes fail its required-tool drop gate; hand mining and other ordinary tools do too. [Pickaxe tag][pickaxe] · [Iron requirement][iron] · [Material rules][materials] · [Wood exclusions][wood] · [Gold exclusions][gold] · [Stone exclusions][stone-exclusion] · [Copper exclusions][copper-exclusion] · [Harvest gate][harvest] · [Broken-tool check][broken]

Successful normal harvesting returns **one Block of Diamond**, not nine Diamonds. Silk Touch is unnecessary and Fortune adds nothing. The loot table's explosion-survival condition means explosions can destroy the item instead of returning it. Unpack through crafting when you want the nine gems. [Complete loot][loot] · [Unpacking][unpack]

It is a full solid block with **5 hardness and 6 blast resistance**. There is no container inventory, facing or waterlogged state; it remains in place without continuing support and does not fall. [Properties][gem-properties] · [Shape and support][defaults] · [Fluid default][fluid] · [State defaults][states] · [State definition][no-states]

<span id="usage"></span>

## Beacon use

Diamond Blocks are accepted by the Beacon's **base-material check**. They can be mixed with other accepted base blocks, and the base still needs the proper complete layers and beam clearance. Use [Build the Beacon base](../blocks/Beacon.md#build-the-base) for the actual construction requirements. [Accepted block tag][base-tag] · [Base validation][base-check]

The **block item does not fit the Beacon payment slot**. Unpack a block to get Diamonds, then use a loose Diamond when [selecting and paying for effects](../blocks/Beacon.md#select-and-pay-for-effects). Base construction and payment are separate uses with separate accepted-item checks. [Payment items][payment-tag] · [Payment slot consumer][payment-slot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked storage recipes, registrations, complete loot, tool restrictions, placement defaults and both Beacon consumers. No gameplay crafting, mining or Beacon test was run; natural block locations were not audited. Data packs and later builds may change recipes, tags or loot.

Related: [Diamond](Diamond.md) · [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md#diamond-block) · [Beacon](../blocks/Beacon.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[gem-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1262-L1264
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/diamond_block.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/diamond.json
[category-gems]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L439-L441
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[iron]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/diamond_block.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L435-L437
[base-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[base-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[payment-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[payment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L158-L171
[no-states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
[gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[stone-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[copper-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
