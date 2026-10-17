# Resin Bricks

**Resin Bricks** (`minecraft:resin_bricks`) are the full orange masonry block used to build the Resin Brick family. The plural name matters: a loose [Resin Brick](ResinBrick.md) is an ingredient, while a [Block of Resin](BlockOfResin.md) stores Resin Clumps. Each has a different recipe and role. [Block-item registrations][items] · [Masonry properties][resin-properties]

## Obtaining

Arrange **four Resin Brick items** (`minecraft:resin_brick`) in a **2 × 2 square** to make **one Resin Bricks block**. This fits the personal crafting grid. To make the ingredients, smelt Resin Clumps individually in a Furnace: one clump produces one brick item. Follow [Resin's starting-supply guide](../blocks/Resin.md#getting-a-starting-supply) to obtain the clumps. Neither clumps nor a Block of Resin substitutes for the four fired brick ingredients. [Masonry recipe][craft] · [Smelting recipe][smelt]

The block is also listed for the [inventory item browser](../mechanics/InventoryBrowser.md). Creative players can request it with room in their inventory and an empty cursor, subject to the browser's feature and server checks; seeing it in Survival does not grant inventory insertion. [Category entry][category-resin]

<span id="usage"></span>
<span id="behavior"></span>

## Building and shaping

Place it as a full solid building block. It has no facing or waterlogged state and stays in place when its support is removed. Its registered hardness is **1.5**, with **6 blast resistance**. [Properties][resin-properties] · [Shape and survival][defaults] · [Fluid default][fluid] · [State defaults][states] · [State definition][no-states]

For shaped construction, use these exact conversions:

| Result | Crafting Table | Stonecutter |
| --- | --- | --- |
| [Resin Brick Slab](ResinBrickSlab.md) | 3 blocks across a row → **6 slabs** | 1 block → **2 slabs** |
| [Resin Brick Stairs](ResinBrickStairs.md) | 6 blocks in a 1 / 2 / 3 stair pattern → **4 stairs** | 1 block → **1 stair** |
| [Resin Brick Wall](ResinBrickWall.md) | 6 blocks in two full rows → **6 walls** | 1 block → **1 wall** |
| [Chiseled Resin Bricks](ChiseledResinBricks.md) | First make slabs; 2 slabs in a vertical column → **1 block** | 1 block → **1 chiseled block** |

The linked item pages give each recipe and placement details. Stonecutting saves material for stairs; it accepts the **Resin Bricks block**, not the loose Resin Brick ingredient. Select the desired result before taking it. The two-slab chiseled recipe also fits the personal grid. [Stonecutter output selection][cut-menu] · [Full recipe comparison](../blocks/Resin.md#slabs-stairs-walls-and-chiseled-bricks)

These bundled masonry recipes do not return loose Resin Brick items or Resin Clumps. For armor trimming, use the separate **Resin Brick ingredient**, which is the listed trim material. [Processing limits](../blocks/Resin.md#slabs-stairs-walls-and-chiseled-bricks) · [Trim ingredients][trim]

## Recovering a placed block

Use an **unbroken pickaxe; Wooden is sufficient**. Hand mining and other ordinary tool types fail the required-tool drop check. Successful normal mining gives **one Resin Bricks block**. Silk Touch is unnecessary, Fortune does not add blocks, and explosion destruction does not guarantee recovery. [Pickaxe tag][pickaxe] · [Tool rules][materials] · [Wood restrictions][wood] · [Harvest gate][harvest] · [Broken-tool check][broken] · [Complete loot][loot]

<span id="notes"></span>

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the registrations, bundled recipes, tool restrictions, loot and placement defaults; no gameplay test was run. Data packs and later builds can change recipes, tags and drops. The [Resin guide](../blocks/Resin.md) owns the full acquisition, processing and placed-block family.

Related: [Resin Brick](ResinBrick.md) · [Block of Resin](BlockOfResin.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[resin-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2451-L2489
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/resin_bricks.json
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/resin_brick.json
[category-resin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L350-L354
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L435-L437
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L110-L157
[trim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/trim_materials.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/resin_bricks.json
[no-states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
