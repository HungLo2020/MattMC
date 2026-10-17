# Prismarine Wall

**Prismarine Wall** (`minecraft:prismarine_wall`) places the prismarine wall form for narrow borders, posts and barriers. See the [family variants](../blocks/Prismarine.md#prismarine-forms) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 Prismarine in two full rows → 6 Prismarine Walls**. Use the exact Prismarine full-block finish; other Prismarine finishes, Shards and Crystals are not substitutes. [Crafting recipe][craft]

The [Stonecutter](../blocks/Stonecutter.md) takes **1 Prismarine → 1 Prismarine Wall**. [Stonecutting][cut-1] · [One input per cut][cut-menu]

Both methods give one wall per input block; stonecutting allows single-item batches.

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/Prismarine.md#recovering-placed-blocks)

## Usage

It joins adjacent wall-tagged blocks, suitable sturdy block faces, bars/panes and correctly aligned Fence Gates. Bars/panes include **Iron Bars, Copper Bars and Glass Panes**, including stained panes. Ordinary fences are not a separate wall-connection category. [Connection rules][wall] · [Bars and ordinary panes][bars] · [Copper Bars inheritance][copper-bars] · [Stained panes][panes]

## Behavior

Prismarine Wall can be waterlogged. Neighboring blocks and the block above determine its arms and post; the inventory item does not store those connections. See the [shared wall controls](../blocks/Stone.md#placing-shaped-blocks). [Placement and neighbor rules][wall] · [Wall tag][wall-tag] · [Exact loot][loot]

This shape does **not** count toward a [Conduit frame](../blocks/Conduit.md#build-a-valid-frame). Use full Prismarine, Prismarine Bricks or Dark Prismarine, or Sea Lanterns. [Accepted blocks][conduit] · [Active frame check][frame] · [Ticker wiring][conduit-ticker]

## Notes

The exact block and item ID is `minecraft:prismarine_wall`. Follow the [family variants](../blocks/Prismarine.md#prismarine-forms), [stonecutting](../blocks/Prismarine.md#stonecutting) and [placed behavior](../blocks/Prismarine.md#placement-water-and-support) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks, plus the active Conduit frame consumer. No in-game crafting, harvesting, placement or Conduit test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[wall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L192
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/walls.json
[bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2307-L2331
[copper-bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java#L11-L15
[panes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StainedGlassPaneBlock.java#L8-L15
[conduit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L162
[conduit-ticker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L49-L58
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L608-L608
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5272-L5272
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/prismarine_wall.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/prismarine_wall.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/prismarine_wall_from_prismarine_stonecutting.json
