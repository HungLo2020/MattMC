# Iron Bars

Iron Bars (`minecraft:iron_bars`) form thin connected barriers. The placed-block guide covers [connections, collision and curing use](../blocks/IronFixtures.md#iron-bars). [Registration][reg-bars-chain] · [English name][names]

## Obtaining

Craft **6 Iron Ingots in two rows of three → 16 Iron Bars** at a Crafting Table. Mine placed bars with an **unbroken pickaxe of any material**, including Wooden or Golden, to recover one item. Hand mining does not satisfy their normal drop requirement. Stronghold prison halls are one [verified generated source](../blocks/IronFixtures.md#finding-generated-examples). [Recipe][recipe-iron_bars] · [Loot][loot-iron_bars] · [Collection rules](../blocks/IronFixtures.md#mining-and-collection)

## Usage

Use the block for railings, window grilles, or enclosures. Bars connect horizontally to compatible panes/bars, walls, and suitable sturdy faces. They can also contribute to the nearby-block bonus for a Zombie Villager cure already in progress. [Bar behavior](../blocks/IronFixtures.md#iron-bars) · [Curing bonus](../blocks/IronFixtures.md#bars-beside-a-curing-zombie-villager)

## Behavior

The connected collision shape is **one block high**, with narrow posts/strips. Bars can be waterlogged, do not require continuing support, and do not open with redstone or act as a Ladder. Silk Touch and Fortune do not change their one-item loot. [Shapes and connections][bars] · [Collision][cross] · [Water and power](../blocks/IronFixtures.md#power-and-water) · [Loot][loot-iron_bars]

## Notes

This is the item form of `minecraft:iron_bars`. Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02; no in-game test was run. The canonical [Iron fixtures guide](../blocks/IronFixtures.md) owns detailed placed behavior and verification.

[reg-bars-chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L2307-L2331
[names]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1739-L1744
[recipe-iron_bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_bars.json#L1-L15
[loot-iron_bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_bars.json#L1-L21
[bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/IronBarsBlock.java#L30-L110
[cross]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java#L37-L85
