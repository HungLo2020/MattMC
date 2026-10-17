# Resin Brick Slab

A **Resin Brick Slab** (`minecraft:resin_brick_slab`) is a half-height masonry piece. Two matching slabs can fill one block space, or serve as the ingredients for [Chiseled Resin Bricks](ChiseledResinBricks.md). [Item registration][items] · [Block registration][resin-properties]

## Obtaining

Put **three [Resin Bricks blocks](ResinBricks.md) across one row** of a Crafting Table to make **six slabs**. These inputs are the plural masonry blocks, not the loose Resin Brick items. [Crafting recipe][craft]

For smaller batches, place **one Resin Bricks block** into a [Stonecutter](../blocks/Stonecutter.md), select Resin Brick Slab and take **two slabs**. Both methods give two slabs per input block; stonecutting needs only one block at a time. [Stonecutting recipe][cut] · [Output selection][cut-menu]

The slab also appears in the [inventory browser](../mechanics/InventoryBrowser.md). Creative insertion still requires an empty cursor, inventory room and the applicable feature/server checks; catalog visibility in Survival does not provide the item. [Category entry][category-resin]

<span id="usage"></span>
<span id="behavior"></span>

## Placing and using slabs

Click a top face or the lower half of a side to place a bottom slab; an underside or upper half of a side places a top slab. Place a second matching slab into the open half to form a **double slab**. This consumes another slab and remains a slab block state rather than becoming the separate Resin Bricks block. [Placement and replacement][slabs]

**Single slabs can waterlog; double slabs cannot.** Combining a waterlogged single slab into a double clears its waterlogged state. The registered hardness is **1.5** and blast resistance **6**. [Slab water rules][slabs] · [Properties][resin-properties]

For a chiseled decoration, put **two Resin Brick Slabs vertically in a crafting grid** to make **one Chiseled Resin Bricks block**. This recipe fits the personal 2 × 2 grid. It consumes inventory slabs, not an already-placed double slab. [Chiseled recipe][chisel]

## Recovery

Use an **unbroken pickaxe; Wooden is sufficient**. Ordinary successful mining returns **one slab from a single slab or two from a double slab**. Silk Touch is unnecessary and Fortune does not multiply these counts. Hand mining or another ordinary tool type fails the required-tool drop gate; explosion decay can reduce the recovered count. [Pickaxe tag][pickaxe] · [Material rules][materials] · [Wood restrictions][wood] · [Harvest gate][harvest] · [Broken-tool check][broken] · [Complete slab loot][loot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked recipes, placement/water callbacks, tool restrictions and the double-slab loot condition. No gameplay test was run; data packs and later builds may change recipes, tags or drops. See [Resin](../blocks/Resin.md) for the complete processing and building family.

Related: [Resin Bricks](ResinBricks.md) · [Chiseled Resin Bricks](ChiseledResinBricks.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[resin-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2451-L2489
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/resin_brick_slab.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/resin_brick_slab_from_resin_bricks_stonecutting.json
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L110-L157
[category-resin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L350-L354
[slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L44-L130
[chisel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_resin_bricks.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/resin_brick_slab.json
