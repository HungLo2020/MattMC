# Block of Lapis Lazuli

A **Block of Lapis Lazuli** (`minecraft:lapis_block`) is a blue building block and reversible storage for nine Lapis Lazuli. Its compact form must be unpacked before using the resource at an Enchanting Table. [Item registration][items] · [Block registration][gem-properties] · [Enchanting input][lapis-slot]

<span id="obtaining"></span>

## Packing and unpacking

Place **nine [Lapis Lazuli](LapisLazuli.md)** (`minecraft:lapis_lazuli`) across the full **3 × 3 Crafting Table grid** to make **one block**. The ingredient is Lapis Lazuli, not Blue Dye. [Packing recipe][pack]

Place **one Block of Lapis Lazuli in any crafting slot** to recover **nine Lapis Lazuli items**. This shapeless recipe fits the personal crafting grid. You do not need to place or mine the block to unpack it. [Unpacking recipe][unpack]

The block is also listed for the [inventory browser](../mechanics/InventoryBrowser.md). Creative insertion needs an empty cursor and inventory room, with feature/server checks still applied. The catalog's visibility in Survival does not supply items there. [Category entry][category-gems]

<span id="behavior"></span>

## Placement and recovery

Use it as a full solid building block. It has **3 hardness and 3 blast resistance**, no inventory, and no facing or waterlogged state. It stays in place when supporting blocks are removed. [Properties][gem-properties] · [Shape and support][defaults] · [Fluid default][fluid] · [State defaults][states] · [State definition][no-states]

Recover it with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Wooden and Golden Pickaxes are insufficient despite being pickaxes. Hand mining and other ordinary tools also fail the required-tool drop check. [Pickaxe tag][pickaxe] · [Stone-tier requirement][stone] · [Wood exclusions][wood] · [Gold exclusions][gold] · [Material rules][materials] · [Harvest gate][harvest] · [Broken-tool check][broken]

Normal successful mining returns **one matching block**. Silk Touch is unnecessary and Fortune does not increase its drops. This is a storage block, not Lapis Ore: mining it does not release loose Lapis. Use the unpacking recipe for that. Explosion destruction has a survival condition and may lose the block. [Complete block loot][loot] · [Unpacking][unpack]

<span id="usage"></span>

## Using the stored Lapis

Unpack before [enchanting](../blocks/EnchantingTable.md). The Enchanting Table's resource slot accepts the exact **Lapis Lazuli item**, so the storage block and Blue Dye cannot substitute. Follow the Enchanting Table guide for level and Lapis costs. [Enchanting resource-slot check][lapis-slot]

Under the bundled Beacon rules, **neither the Lapis block nor loose Lapis is a payment item**, and the block is **not a Beacon base material**. Its blue gemstone appearance does not make it interchangeable with Diamond Blocks. [Base tag][base-tag] · [Base check][base-check] · [Payment tag][payment-tag] · [Payment slot][payment-slot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked both recipes, registration, complete block loot, tool restrictions, placement defaults and the Enchanting Table/Beacon consumers. No gameplay test was run or natural block location audited. Data packs and later builds can change these rules.

Related: [Lapis Lazuli](LapisLazuli.md) · [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md#lapis-block) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[gem-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L643-L645
[lapis-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L61-L70
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/lapis_block.json
[unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/lapis_lazuli.json
[category-gems]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L439-L441
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L435-L437
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[gold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/lapis_block.json
[base-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[base-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[payment-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[payment-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L158-L171
[no-states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
