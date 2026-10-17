# Iron Chain

Iron Chain (`minecraft:iron_chain`) is a narrow decorative block with three possible placement axes. See the [Iron Chain guide](../blocks/IronFixtures.md#iron-chain) for placement and collision. [Current English name][names] · [Registration][reg-bars-chain]

## Obtaining

At a Crafting Table, arrange **Iron Nugget–Iron Ingot–Iron Nugget vertically → 1 Iron Chain**. Mine placed chains with an **unbroken pickaxe of any material**, including Wooden or Golden, to recover one item. Some Mineshaft supports are one [verified generated source](../blocks/IronFixtures.md#finding-generated-examples). [Recipe][recipe-iron_chain] · [Loot][loot-iron_chain] · [Collection rules](../blocks/IronFixtures.md#mining-and-collection)

## Usage

Use chains for straight vertical or horizontal decorative runs. The clicked face chooses the axis; adjacent chains do not automatically bend or create side connections. Chains remain when their original attachment is removed. [Axis selection][axis] · [Shape and state][chain] · [Support rules](../blocks/IronFixtures.md#iron-chain)

## Behavior

Chains have a narrow solid collision shape and can be waterlogged. They are **not climbable ropes**; use a [Ladder](../blocks/Ladder.md) for climbing. Silk Touch and Fortune do not change the one-chain loot. [Chain behavior][chain] · [Climbable set][climbable] · [Loot][loot-iron_chain]

## Notes

The current block/item ID is `minecraft:iron_chain`, and its English display name is Iron Chain. Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02; no in-game test was run. The canonical [Iron fixtures guide](../blocks/IronFixtures.md) owns detailed placed behavior and verification.

[names]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1739-L1744
[reg-bars-chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L2307-L2331
[recipe-iron_chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_chain.json#L1-L17
[loot-iron_chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_chain.json#L1-L21
[axis]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L55
[chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/ChainBlock.java#L25-L84
[climbable]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/climbable.json#L1-L13
