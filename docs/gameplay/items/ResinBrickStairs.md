# Resin Brick Stairs

**Resin Brick Stairs** (`minecraft:resin_brick_stairs`) turn Resin masonry into steps and decorative corners. Make them from [Resin Bricks blocks](ResinBricks.md), not from loose Resin Brick ingredients. [Item registration][items] · [Block registration][resin-properties]

## Obtaining

At a **Crafting Table**, arrange **six Resin Bricks blocks** in a three-row stair pattern: one block in the top row, two in the middle and three across the bottom. The result is **four Resin Brick Stairs**. [Crafting recipe][craft]

Alternatively, put **one Resin Bricks block** in a [Stonecutter](../blocks/Stonecutter.md), select Resin Brick Stairs and take **one stair**. This has the better material yield: six input blocks can make six stairs instead of the crafting recipe's four. This stonecutting recipe accepts the full `minecraft:resin_bricks` block; neither the loose brick item nor another finished Resin shape is its input. [Stonecutting recipe][cut] · [Result selection][cut-menu]

Creative players can also request the listed item through the [inventory browser](../mechanics/InventoryBrowser.md), subject to its mode, empty-cursor, capacity and feature checks. The visible Survival catalog is not an inventory-insertion route. [Category entry][category-resin]

<span id="usage"></span>
<span id="behavior"></span>

## Placement and recovery

Stairs use your horizontal facing for their orientation. Placing against an underside or the upper half of a side produces top-half stairs; placement on top or low on a side produces bottom-half stairs. Neighboring stairs with compatible orientation and the same half can change the result into inner or outer corners. These are states of the same stair item. [Placement and corner calculation][stairs]

They can **waterlog**, including when placed into water. Use the [Resin building guide](../blocks/Resin.md#placing-clumps-and-building-with-resin) for the family's shape and water behavior. Their hardness is **1.5** and blast resistance **6**, inherited from Resin Bricks. [Stair water state][stair-water] · [Block registration][resin-properties] · [Stair property copying][stair-copy] · [Copied block properties][legacy-copy]

To move a stair in normal Survival play, use an **unbroken pickaxe; Wooden is sufficient**. Correct-tool mining returns **one Resin Brick Stairs item**, regardless of its corner or top/bottom state. Hand mining and other ordinary tools fail the drop gate. Silk Touch is unnecessary, Fortune does not increase the count, and explosion recovery is conditional. [Pickaxe tag][pickaxe] · [Material rules][materials] · [Wood restrictions][wood] · [Harvest gate][harvest] · [Broken-tool check][broken] · [Complete loot][loot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, registration, tool/drop gates and stair callbacks were checked; no in-game crafting, placement or mining test was run. Data packs and later builds can change these rules.

Related: [Resin Bricks](ResinBricks.md) · [Resin family](../blocks/Resin.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[resin-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2451-L2489
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/resin_brick_stairs.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/resin_brick_stairs_from_resin_bricks_stonecutting.json
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L110-L157
[category-resin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L350-L354
[stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L92-L160
[stair-water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L213-L220
[stair-copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7256-L7258
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/resin_brick_stairs.json
[legacy-copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1090
