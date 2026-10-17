# Purpur Slab

**Purpur Slab** (`minecraft:purpur_slab`) places the purpur slab form for half-height floors, ledges and detail work. See the [family variants](../blocks/EndStoneAndPurpur.md#purpur-variants-and-recipes) for its matching full block. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **3 Purpur Blocks and/or Purpur Pillars in one horizontal row → 6 Purpur Slabs**. Each occupied slot independently accepts either full-block form, so they may be mixed. [Crafting recipe][craft] · [Ingredient matching][ingredient] · [Per-slot matching][pattern]

The [Stonecutter](../blocks/Stonecutter.md) instead takes **1 Purpur Block → 2 Purpur Slabs**. It does not accept Purpur Pillar for this conversion. [Stonecutting][cut-1] · [One input per cut][cut-menu]

Both methods give two slabs per input block; stonecutting allows smaller batches.

Use an **unbroken pickaxe, including Wood**, to recover **one matching slab** from a single slab or **two matching slabs** from a double slab. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/EndStoneAndPurpur.md#mining-and-recovery)

## Usage

Place it in the top or bottom half of a block space. Add a second **Purpur Slab** to its empty half to make a double slab; another slab material will not combine with it. The result remains `minecraft:purpur_slab`, not its full-block crafting ingredient. [Placement and matching-item check][slab] · [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks)

Two slab items can also craft a [Purpur Pillar](PurpurPillar.md#obtaining); placing a double slab is a different operation.

## Behavior

A single slab can be waterlogged. Doubling it clears its waterlogged state, and a double slab cannot accept Water through the waterlogging interface. Correct-tool mining returns slab items rather than a full block. [Slab state and Water rules][slab] · [Double-slab loot][loot]

## Notes

The exact block and item ID is `minecraft:purpur_slab`. Follow the [family variants](../blocks/EndStoneAndPurpur.md#purpur-variants-and-recipes), [stonecutting](../blocks/EndStoneAndPurpur.md#stonecutting) and [placed behavior](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks. No in-game crafting, harvesting or placement test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[ingredient]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L60-L66
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L409-L409
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3912-L3916
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/purpur_slab.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/purpur_slab.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/purpur_slab_from_purpur_block_stonecutting.json
