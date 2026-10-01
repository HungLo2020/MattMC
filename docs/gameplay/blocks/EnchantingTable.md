# Enchanting Table

An Enchanting Table offers random enchantments for eligible equipment and plain Books. Surround it with correctly placed Bookshelves to unlock stronger offers. Its block and item ID is `minecraft:enchanting_table`.

## Crafting and collecting

At a [Crafting Table](CraftingTable.md), arrange **one Book, two Diamonds, and four Obsidian** like this:

| Left | Center | Right |
| --- | --- | --- |
| Empty | Book | Empty |
| Diamond | Obsidian | Diamond |
| Obsidian | Obsidian | Obsidian |

The recipe produces **one Enchanting Table**. Use a pickaxe to collect a placed table: this block requires the correct tool for drops and is in the pickaxe mining tag. Its block loot returns one table, preserves its custom name, and is subject to explosion survival.

## Bookshelf setup

Only ordinary **Bookshelves** provide enchanting power in the bundled provider tag. Chiseled Bookshelves do not count, regardless of their contents.

Valid shelves occupy the outer edge of a **5 × 5 square centered on the table**, either at the table block's height or one block higher. Keep the intervening inner ring clear. Shelves immediately beside the table, farther away, below it, or two blocks above it do not count.

This top-down arrangement uses **15 Bookshelves**, all at the table's height:

```text
BBBBB
B...B
B.T.B
B...B
BB.BB
```

`T` is the table, `B` is a Bookshelf, and `.` is an empty space. The opening at the bottom lets you walk into the clear inner ring. You can also split valid shelves across the two allowed heights. Each valid shelf block counts once; **only the first 15 affect offer strength**.

The gap check uses MattMC's **enchantment-power-transmitter tag**, which contains the replaceable-block tag. Air is a simple reliable choice. The bundled tag also permits blocks such as Water and Short Grass. **Torches and carpets in the checked gap block prevent the affected shelves from counting.** Keeping the whole inner ring empty avoids these obstructions.

After changing shelves or gap blocks, remove and reinsert the target item in the table so its offers are recalculated. With 15 effective shelves, the bottom offer's level requirement is 30; see the [enchanting guide](../enchanting/Enchanting.md#required-level-versus-levels-spent) for the separate payment costs.

## Using the table

Interact with the placed table to open its menu. The first slot holds one target item; the second accepts Lapis Lazuli. See [Enchanting](../enchanting/Enchanting.md) for item eligibility, offer previews, lapis and level costs, and changing the offers.

The table emits light level **7**. It does not need fuel or redstone power to open its enchanting menu.

## Related pages

- [Enchanting guide](../enchanting/Enchanting.md)
- [Enchanting Table item](../items/EnchantingTable.md)
- [Bookshelf item](../items/Bookshelf.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active MattMC sources. No in-game tests were run. Data packs can change crafting recipes, provider/transmitter tags, and loot; later builds can change the behavior described here. Natural generation was not reviewed for this page.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/enchanting_table.json)
- [Block registration, tool requirement, and light](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2515-L2524), [pickaxe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json), and [player tool-for-drops check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657)
- [Table block loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/enchanting_table.json)
- [Shelf positions, gap checks, and opening the menu](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/EnchantingTableBlock.java)
- [Power providers](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/enchantment_power_provider.json), [power transmitters](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/enchantment_power_transmitter.json), and [replaceable blocks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/replaceable.json)
- [Menu slots and shelf recounting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L49-L132)
- [15-shelf cap and offer-cost calculation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L499-L515)
