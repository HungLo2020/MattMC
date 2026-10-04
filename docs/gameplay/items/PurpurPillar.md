# Purpur Pillar

**Purpur Pillar** (`minecraft:purpur_pillar`) is the striped Purpur building block. Its axis follows the face you click, making it useful for both upright columns and horizontal beams. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **2 [Purpur Slabs](PurpurSlab.md) stacked vertically → 1 Purpur Pillar**, or stonecut **1 [Purpur Block](PurpurBlock.md) → 1 Purpur Pillar**. The crafting recipe takes slab items, not two full Purpur Blocks. [Crafting recipe][craft] · [Stonecutting][cut-1] · [One input per cut][cut-menu]

The family guide covers the [Chorus Fruit production route](../blocks/EndStoneAndPurpur.md#chorus-fruit-to-purpur) and [End City materials](../blocks/EndStoneAndPurpur.md#obtaining-in-the-end).

Use an **unbroken pickaxe, including Wood**, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary, and Fortune does not multiply the ordinary mining drop. [Exact loot][loot] · [Durability gate][broken-tool] · [Harvest dispatch][harvest] · [Family mining rules](../blocks/EndStoneAndPurpur.md#mining-and-recovery)

## Usage

Click a top or bottom face for a vertical pillar, an east/west face for an east–west axis, or a north/south face for a north–south axis. Ordinary Purpur Block has no pillar axis. [Pillar placement][pillar] · [Family placement guide](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions)

Purpur Pillars also work alongside Purpur Blocks, independently per occupied slot, in [Purpur stair and slab crafting](../blocks/EndStoneAndPurpur.md#purpur-variants-and-recipes). Those shapes’ [stonecutting recipes](../blocks/EndStoneAndPurpur.md#stonecutting) accept only Purpur Block.

## Behavior

Mining preserves the pillar item, but not its previous placed axis. The next placement chooses an axis from the newly clicked face. [Ordinary loot][loot] · [Axis selection][pillar]

## Notes

The exact block and item ID is `minecraft:purpur_pillar`. Follow the [family variants](../blocks/EndStoneAndPurpur.md#purpur-variants-and-recipes), [stonecutting](../blocks/EndStoneAndPurpur.md#stonecutting) and [placed behavior](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) for the shared details. [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item/block binding, exact recipes and loot, mining tags and tool gates, and relevant placement callbacks. No in-game crafting, harvesting or placement test was run. Recipes describe the bundled data; data packs can change them.

[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[pillar]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L55
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L62-L77
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L472-L472
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4244-L4248
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/purpur_pillar.json
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/purpur_pillar.json
[cut-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/purpur_pillar_from_purpur_block_stonecutting.json
