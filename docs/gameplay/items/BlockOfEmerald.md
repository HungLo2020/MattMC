# Block of Emerald

A **Block of Emerald** (`minecraft:emerald_block`) packs nine Emeralds into a full building block. It is also an accepted Beacon base material; unpack it when you need the individual Emerald items. [Item registration][items] · [Block registration][gem-properties]

<span id="obtaining"></span>

## Packing and unpacking

Fill a **3 × 3 Crafting Table grid with nine [Emeralds](Emerald.md)** (`minecraft:emerald`) to make **one Block of Emerald**. To reverse the conversion, put **one block into any crafting slot** and collect **nine Emeralds**. This shapeless unpacking recipe fits the personal grid and needs no extra ingredient. [Packing recipe][pack] · [Unpacking recipe][unpack]

The block is category-listed in the [inventory browser](../mechanics/InventoryBrowser.md). Creative players may request it with an empty cursor and room in their inventory, subject to the browser's feature/server checks. Catalog visibility in ordinary Survival does not grant insertion. [Category entry][category-gems]

<span id="behavior"></span>

## Placing and recovering it

The placed Emerald Block is a full solid cube with **5 hardness and 6 blast resistance**. It has no inventory, facing or waterlogged state, and removing its support leaves it in place rather than making it fall. [Properties][gem-properties] · [Shape and support][defaults] · [Fluid default][fluid] · [State defaults][states] · [State definition][no-states]

Use an **unbroken Iron, Diamond or Netherite Pickaxe** for recovery. Wooden, Golden, Stone and Copper Pickaxes are below the required drop tier; hand mining and other ordinary tools also fail the correct-tool check. [Pickaxe tag][pickaxe] · [Iron-tier requirement][iron] · [Material rules][materials] · [Wood exclusions][wood] · [Gold exclusions][gold] · [Stone exclusions][stone-exclusion] · [Copper exclusions][copper-exclusion] · [Harvest gate][harvest] · [Broken-tool check][broken]

Successful normal mining gives **one Block of Emerald**, not nine Emeralds. Silk Touch is unnecessary and Fortune does not multiply the drop. Explosion destruction is subject to the loot table's survival condition. Use crafting to unpack the block once recovered. [Complete loot][loot] · [Unpacking][unpack]

<span id="usage"></span>

## Beacon use

The Beacon accepts Emerald Blocks in its **base**, including bases mixing different accepted materials. Complete layers and the Beacon's other operating conditions still matter; see [Build the base](../blocks/Beacon.md#build-the-base). The consumer tests membership in the accepted base tag, so an Emerald Block does not supply an extra base level merely because of its material. [Base materials][base-tag] · [Active base check][base-check]

The **block item is not a Beacon payment item**. Unpack it and use one loose Emerald when [selecting and paying for effects](../blocks/Beacon.md#select-and-pay-for-effects). The same unpacking route supplies individual Emeralds for their other uses; see [Emerald](Emerald.md). [Payment items][payment-tag] · [Payment slot check][payment-slot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked packing/unpacking, registrations, complete loot, tool restrictions, placement defaults and both Beacon consumers. No gameplay test was run and natural storage-block locations were not audited. Data packs and later builds may change recipes, tags and loot.

Related: [Emerald](Emerald.md) · [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md#emerald-block) · [Beacon](../blocks/Beacon.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[gem-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2623-L2631
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/emerald_block.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/emerald.json
[category-gems]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L439-L441
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L435-L437
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[iron]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/emerald_block.json
[base-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[base-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[payment-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[payment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L158-L171
[no-states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
[gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[stone-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_stone_tool.json
[copper-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json
