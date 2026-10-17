# Resin Brick Wall

A **Resin Brick Wall** (`minecraft:resin_brick_wall`) is a connecting masonry barrier made from [Resin Bricks blocks](ResinBricks.md). It uses the wall shape rather than the full cube of the source block. [Item registration][items] · [Block registration][resin-properties]

## Obtaining

Fill **two complete rows of a Crafting Table with six Resin Bricks blocks** to make **six Resin Brick Walls**. The input is `minecraft:resin_bricks`, the masonry block; loose Resin Brick items and Resin Clumps do not fill this recipe. [Crafting recipe][craft]

Alternatively, put **one Resin Bricks block** into a [Stonecutter](../blocks/Stonecutter.md), choose Resin Brick Wall and take **one wall**. The two routes have the same one-wall-per-block material yield, while stonecutting lets you make a single wall without a six-block batch. This is a Resin Bricks-to-wall recipe, not a conversion from another Resin shape. [Stonecutting recipe][cut] · [Output selection][cut-menu]

Creative players can request the category-listed wall through the [inventory browser](../mechanics/InventoryBrowser.md), with its empty-cursor, capacity, feature and server limits. A visible entry in Survival does not itself allow insertion. [Category entry][category-resin]

<span id="usage"></span>
<span id="behavior"></span>

## Placement and recovery

Use walls for borders and barriers. Their sides connect automatically to other wall-tagged blocks, suitable sturdy faces, bars and correctly aligned Fence Gates. Nearby block changes update the sides and center post; the [Resin guide](../blocks/Resin.md#placing-clumps-and-building-with-resin) covers the family's placed states. Walls can **waterlog**. [Connection and update rules][walls] · [Water state][wall-water]

The wall has **1.5 hardness and 6 blast resistance**. Recover it with an **unbroken pickaxe; Wooden is sufficient**. A successful normal harvest gives **one Resin Brick Wall item**, regardless of its connections. Hand mining and other ordinary tool types fail the correct-tool drop gate. [Properties][resin-properties] · [Pickaxe tag][pickaxe] · [Tool rules][materials] · [Wood restrictions][wood] · [Harvest gate][harvest] · [Broken-tool check][broken]

The complete loot table has no Silk Touch requirement or Fortune multiplier. Its explosion-survival condition means a destroyed wall is not guaranteed to survive as an item. [Wall loot][loot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked both recipes, the block/item registration, wall callbacks, tool restrictions and complete loot. No in-game building, water or harvesting test was run. Data packs and later builds can alter these rules.

Related: [Resin Bricks](ResinBricks.md) · [Resin family](../blocks/Resin.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[resin-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2451-L2489
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/resin_brick_wall.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/resin_brick_wall_from_resin_bricks_stonecutting.json
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L110-L157
[category-resin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L350-L354
[walls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L155
[wall-water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L241-L254
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/resin_brick_wall.json
