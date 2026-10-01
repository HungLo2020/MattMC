# Bookshelf

Bookshelf is a placeable block item registered as `minecraft:bookshelf`. Properly arranged ordinary Bookshelves increase Enchanting Table offer strength.

## Crafting

Put **three Books across the middle row**, with a full row of planks-tag items above and below, to craft **one Bookshelf**. The recipe needs six planks and three books; generic planks-tag membership matters for imported woods.

## Collecting and enchanting use

The loot table selects the Bookshelf block with **Silk Touch**. Otherwise it selects **three Books**, with explosion decay; it does not return the crafting planks.

Ordinary Bookshelf is the bundled enchanting-power provider. [Chiseled Bookshelf](ChiseledBookshelf.md) is a different block and is not in that provider tag. For valid spacing, heights, gap blocks, and the 15-effective-shelf cap, use the [Enchanting Table setup guide](../blocks/EnchantingTable.md#bookshelf-setup).

A shelf directly touching the table is not automatically useful. Placement rules, not simply the number of shelves in a room, determine the counted power.

## Related pages

- [Book](Book.md)
- [Enchanting Table](../blocks/EnchantingTable.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game enchanting, anvil, mining, or crafting test was run.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/bookshelf.json)
- [Silk Touch and normal loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/bookshelf.json)
- [Power provider tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/enchantment_power_provider.json)
- [Shelf offsets and transmitter checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/EnchantingTableBlock.java)
