# Chiseled Resin Bricks

**Chiseled Resin Bricks** (`minecraft:chiseled_resin_bricks`) are a full decorative block in the Resin masonry family. They are a finished building material, separate from the loose Resin Brick ingredient and the clump-packed Block of Resin. [Item registration][items] · [Block registration][resin-properties]

## Obtaining

Put **two [Resin Brick Slabs](ResinBrickSlab.md) in a vertical column** to make **one Chiseled Resin Bricks block**. This fits the personal 2 × 2 crafting grid. Starting from full Resin Bricks, the slab recipe makes six slabs from three blocks, enough for three chiseled blocks. [Chiseled crafting recipe][craft] · [Slab crafting recipe][make-slabs]

For a direct route, use a [Stonecutter](../blocks/Stonecutter.md): **one [Resin Bricks block](ResinBricks.md) → one Chiseled Resin Bricks block**. Select the chiseled result. The listed stonecutting input is `minecraft:resin_bricks`; the two-slab recipe is specifically a crafting route. Both methods use the same amount of masonry per finished block, but stonecutting avoids the slab batch. [Stonecutting recipe][cut] · [Output selection][cut-menu]

The item is also category-listed in the [inventory browser](../mechanics/InventoryBrowser.md). Creative players can request it subject to the browser's cursor, capacity, feature and server checks; ordinary Survival catalog visibility does not grant insertion. [Category entry][category-resin]

<span id="usage"></span>
<span id="behavior"></span>

## Building and recovery

Place it as a solid decorative cube. There is no facing or waterlogged state, and removing supporting blocks leaves it in place. Its registered hardness is **1.5**, with **6 blast resistance**. [Properties][resin-properties] · [Shape and support][defaults] · [Fluid default][fluid] · [State defaults][states] · [State definition][no-states]

Use an **unbroken pickaxe; Wooden is sufficient** to recover a placed block. Normal successful mining returns **one Chiseled Resin Bricks item**. Hand mining and other ordinary tools fail the required-tool drop check. Silk Touch is unnecessary, Fortune adds nothing, and the explosion-survival condition can prevent an explosion drop. [Pickaxe tag][pickaxe] · [Material rules][materials] · [Wood restrictions][wood] · [Harvest gate][harvest] · [Broken-tool check][broken] · [Complete loot][loot]

The checked bundled masonry recipes do not unpack this decoration into loose Resin Brick items or Resin Clumps. Keep the ingredient, clump storage and masonry routes separate; the [Resin family guide](../blocks/Resin.md#crafting-and-smelting) explains the processing chain.

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked recipes, registration, placement defaults, tool restrictions and complete loot; no gameplay test was run. Data packs and later builds can change recipes, tags and drops.

Related: [Resin Brick Slab](ResinBrickSlab.md) · [Resin Bricks](ResinBricks.md) · [Resin family](../blocks/Resin.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[resin-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2451-L2489
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_resin_bricks.json
[make-slabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/resin_brick_slab.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_resin_bricks_from_resin_bricks_stonecutting.json
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L110-L157
[category-resin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L350-L354
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L435-L437
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/chiseled_resin_bricks.json
[no-states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
