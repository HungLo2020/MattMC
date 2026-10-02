# Ice

**Ice** (`minecraft:ice`) is a slippery, light-sensitive building block. [Registration][blocks] [items]

## Obtaining

Ordinary Ice has no crafting recipe. It can form through [cold-surface freezing](../blocks/Ice.md#freezing-ordinary-ice). Use **Silk Touch** to collect a placed block; without it there is no item drop. A pickaxe mines it efficiently, but the block has no correct-tool material gate. [Loot][loot-ice] · [Properties and mining tag][blocks] [pickaxe]

## Usage

Without Silk Touch, breaking it can leave water depending on its support and dimension. Bright block light can also melt it. See the [exact melting and breaking conditions](../blocks/Ice.md#ordinary-ice-melting-and-breaking). [Placed implementation][blocks] [ice]

See [Ice types and friction](../blocks/Ice.md#registered-ice-blocks) for the comparison and [recipes](../blocks/Ice.md#harvesting-and-recipes) for packing costs. Fortune does not increase this block's loot. [Loot][loot-ice]

## Related pages

- [Ice family](../blocks/Ice.md), [Snow and Powder Snow](../blocks/Snow.md), and [Transport](../mechanics/Transport.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[loot-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/ice.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/IceBlock.java
