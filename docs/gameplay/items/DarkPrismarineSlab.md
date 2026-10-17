# Dark Prismarine Slab

**Dark Prismarine Slab** (`minecraft:dark_prismarine_slab`) places the dark prismarine slab form for half-height floors, ledges and detail work. See the [family variants](../blocks/Prismarine.md#dark-prismarine-forms) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **3 Dark Prismarine in one horizontal row → 6 Dark Prismarine Slabs**. Use the exact Dark Prismarine full-block finish; other Prismarine finishes, Shards and Crystals are not substitutes. [Crafting recipe][craft]

The [Stonecutter](../blocks/Stonecutter.md) takes **1 Dark Prismarine → 2 Dark Prismarine Slabs**. [Stonecutting][cut-1] · [One input per cut][cut-menu]

Both methods give two slabs per input block; stonecutting allows smaller batches.

Use an **unbroken pickaxe, including Wood**, to recover **one matching slab** from a single slab or **two matching slabs** from a double slab. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/Prismarine.md#recovering-placed-blocks)

## Usage

Place it in the top or bottom half of a block space. Add a second **Dark Prismarine Slab** to its empty half to make a double slab; another slab material will not combine with it. The result remains `minecraft:dark_prismarine_slab`, not its full-block crafting ingredient. [Placement and matching-item check][slab] · [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

A single slab can be waterlogged. Doubling it clears its waterlogged state, and a double slab cannot accept Water through the waterlogging interface. Correct-tool mining returns slab items rather than a full block. [Slab state and Water rules][slab] · [Double-slab loot][loot]

This shape does **not** count toward a [Conduit frame](../blocks/Conduit.md#build-a-valid-frame), even as a double slab. Use full Prismarine, Prismarine Bricks or Dark Prismarine, or Sea Lanterns. [Accepted blocks][conduit] · [Active frame check][frame] · [Ticker wiring][conduit-ticker]

## Notes

The exact block and item ID is `minecraft:dark_prismarine_slab`. Follow the [family variants](../blocks/Prismarine.md#dark-prismarine-forms), [stonecutting](../blocks/Prismarine.md#stonecutting) and [placed behavior](../blocks/Prismarine.md#placement-water-and-support) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks, plus the active Conduit frame consumer. No in-game crafting, harvesting, placement or Conduit test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[conduit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L162
[conduit-ticker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L49-L58
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L412-L412
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3174-L3178
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_prismarine_slab.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/dark_prismarine_slab.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/dark_prismarine_slab_from_dark_prismarine_stonecutting.json
