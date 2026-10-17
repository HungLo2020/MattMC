# Prismarine Stairs

**Prismarine Stairs** (`minecraft:prismarine_stairs`) places the prismarine stairs form for steps, roofs and corners. See the [family variants](../blocks/Prismarine.md#prismarine-forms) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 Prismarine in the [stair pattern](../blocks/Stone.md#crafting-yields) → 4 Prismarine Stairs**. Use the exact Prismarine full-block finish; other Prismarine finishes, Shards and Crystals are not substitutes. [Crafting recipe][craft]

The [Stonecutter](../blocks/Stonecutter.md) takes **1 Prismarine → 1 Prismarine Stairs**. [Stonecutting][cut-1] · [One input per cut][cut-menu]

Stonecutting makes four stairs from four accepted input blocks; crafting four stairs uses six. See the [family cutting choices](../blocks/Prismarine.md#stonecutting).

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/Prismarine.md#recovering-placed-blocks)

## Usage

Your horizontal direction sets the facing. The clicked face and click height choose upright or upside-down placement. Suitable neighboring stairs automatically form inner or outer corners, including other stair materials when the half and facing checks fit. [Placement and corner rules][stairs] · [Shared stair controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

Prismarine Stairs can be waterlogged. The placed half, facing and corner are block states; mining returns the ordinary Prismarine Stairs item, and placement chooses those states again. [Stair states][stairs] · [Exact loot][loot]

This shape does **not** count toward a [Conduit frame](../blocks/Conduit.md#build-a-valid-frame). Use full Prismarine, Prismarine Bricks or Dark Prismarine, or Sea Lanterns. [Accepted blocks][conduit] · [Active frame check][frame] · [Ticker wiring][conduit-ticker]

## Notes

The exact block and item ID is `minecraft:prismarine_stairs`. Follow the [family variants](../blocks/Prismarine.md#prismarine-forms), [stonecutting](../blocks/Prismarine.md#stonecutting) and [placed behavior](../blocks/Prismarine.md#placement-water-and-support) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks, plus the active Conduit frame consumer. No in-game crafting, harvesting, placement or Conduit test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L163
[conduit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L162
[conduit-ticker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L49-L58
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L753-L753
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3161-L3161
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/prismarine_stairs.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/prismarine_stairs.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_stairs_from_prismarine_stonecutting.json
