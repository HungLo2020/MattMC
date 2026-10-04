# Polished Granite

**Polished Granite** (`minecraft:polished_granite`) is the polished full block in the [Granite family](../blocks/DecorativeStone.md#polished-and-shaped-variants). Use it for building or as the crafting ingredient for its polished stairs and slabs. [Item binding][item]

## Obtaining

Craft **4 [Granite](Granite.md) in a 2 × 2 square → 4 Polished Granite**, or stonecut **1 Granite → 1 Polished Granite**. Both routes preserve the block count; the [Stonecutter](../blocks/Stonecutter.md) lets you polish one block at a time. Every occupied crafting slot requires ordinary Granite. [Crafting][craft] · [Stonecutting][cut-raw]

Use an **unbroken pickaxe**, including a Wooden Pickaxe, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune does not increase the count. Explosion survival or decay is a separate condition. [Exact loot][loot] · [Tool and drop rules](../blocks/DecorativeStone.md#mining-and-drops)

## Usage

Place it as a full decorative block, or use it to make [Polished Granite Stairs](PolishedGraniteStairs.md) and [Polished Granite Slabs](PolishedGraniteSlab.md). Compare the [family crafting recipes](../blocks/DecorativeStone.md#shaped-crafting) and [stonecutting choices](../blocks/DecorativeStone.md#stonecutting-choices): raw Granite can also be cut directly into those polished shapes.

## Behavior

The placed full block has no player-selected facing or pillar axis. Mining it preserves the polished finish. See the [family placement guide](../blocks/DecorativeStone.md#placement-and-properties). [Block registration][blocks] · [Ordinary placement][placement]

## Notes

This is the ordinary item form of the `minecraft:polished_granite` block. Follow the [family guide](../blocks/DecorativeStone.md#polished-and-shaped-variants) for shared recipes and placed behavior. [Block registration][blocks] · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The exact recipes, complete loot table, item binding, tool requirements and relevant placement code were checked. No in-game crafting, mining or placement test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L71-L71
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L79-L82
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_granite.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_granite.json
[cut-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_granite_from_granite_stonecutting.json
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
