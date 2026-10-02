# Blue Ice

**Blue Ice** (`minecraft:blue_ice`) is a building block with greater slipperiness than ordinary or Packed Ice and no ordinary light-melting callback. [Registration][blocks] [items]

## Obtaining

Crafting and natural iceberg examples are covered in the [Ice guide](../blocks/Ice.md#harvesting-and-recipes). Use **Silk Touch** to collect a placed block; without it there is no item drop. A pickaxe mines it efficiently, but the block has no correct-tool material gate. [Loot][loot-blue-ice] · [Properties and mining tag][blocks] [pickaxe]

## Usage

It keeps its placed form under bright lighting and does not produce water when broken. It can support [Snow layers](../blocks/Snow.md#placing-layers-and-keeping-their-support), and it participates in the [basalt-making layout](../blocks/BlackstoneAndBasalt.md#making-basalt-with-lava). [Placed implementation][blocks] [ice]

See [Ice types and friction](../blocks/Ice.md#registered-ice-blocks) for the comparison and [recipes](../blocks/Ice.md#harvesting-and-recipes) for packing costs. Fortune does not increase this block's loot. [Loot][loot-blue-ice]

## Related pages

- [Ice family](../blocks/Ice.md), [Snow and Powder Snow](../blocks/Snow.md), and [Transport](../mechanics/Transport.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[loot-blue-ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/blue_ice.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[ice]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/IceBlock.java
