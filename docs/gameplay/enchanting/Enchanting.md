# Enchanting

Enchanting adds bonuses to equipment or stores them in an Enchanted Book. Start with an [Enchanting Table](../blocks/EnchantingTable.md), an eligible unenchanted item or plain [Book](../items/Book.md), [Lapis Lazuli](../items/LapisLazuli.md), and experience levels.

## Using the table

1. Place the table and arrange bookshelves if you want stronger offers. The [table's setup guide](../blocks/EnchantingTable.md#bookshelf-setup) includes a 15-shelf layout.
2. Put one target item in the first slot and lapis in the second.
3. Hover over an available offer to see one of its enchantments and its costs. The result can include additional compatible enchantments.
4. Choose an offer, then collect the result. A plain Book becomes an [Enchanted Book](../items/EnchantedBook.md).

### Required level versus levels spent

In Survival, the number displayed on an offer is the **experience level you must have before choosing it**. The row determines how much lapis and how many levels the enchantment actually consumes:

| Offer | Lapis consumed | Experience levels consumed |
| --- | ---: | ---: |
| Top | 1 | 1 |
| Middle | 2 | 2 |
| Bottom | 3 | 3 |

With **15 effective bookshelves**, the bottom offer requires **level 30**. Choosing it at level 30 consumes three lapis and leaves you at level 27. At level 29, having extra lapis does not unlock that offer. More than 15 effective shelves does not raise the table's offer strength further.

These are level costs, not fixed numbers of experience points. The displayed requirement also does not promise a particular enchantment, maximum enchantment level, or number of bonuses.

## Which enchantments can appear?

The table requires an item with enchanting enabled and no existing applied enchantments. An item fitting in the slot does not establish that it can receive an offer. Already-enchanted equipment and Enchanted Books cannot be enchanted again at this table in the bundled setup.

An offer also depends on the item's enchantability, the enchantment's allowed table items and cost range, random selection, and compatibility with the other selected enchantments. This matters for both vanilla equipment and integrated content: do not assume every weapon or tool can receive every familiar enchantment.

Practical examples from the bundled data:

- **Fortune and Silk Touch exclude each other.** A table result will not combine them, including on a Book.
- **Sharpness is offered directly on swords, not axes.** Its broader supported-item list includes axes, but its table-selection list is restricted to swords.
- **Mending, Soul Speed, and Swift Sneak are absent from the table's pool.** Repeatedly rerolling a table will not produce them.

Books bypass equipment-type restrictions during table selection, but still use the same table enchantment pool, cost ranges, and compatibility checks. They can contain more than one enchantment; the table removes one random entry when its initial book selection contains multiple entries.

## Changing the offers

Offers use a saved random seed belonging to the player. **Successfully enchanting an item changes that seed**, including using the cheapest available offer on a spare eligible item or Book. A new seed can still produce similar results.

Closing and reopening the menu, removing and reinserting the same unchanged item, or switching to another table with the same effective shelf count does not itself reroll that seed. Changing item type or effective shelf count can change the offers calculated from it, but does not guarantee a desired result. After changing the surrounding shelves or gap blocks, remove and reinsert the target item to recalculate the offers.

## Troubleshooting

- **An offer is disabled:** check both the displayed level requirement and the row's lapis cost.
- **No useful offers appear:** check that the item is enchantable, has no applied enchantments, and has eligible enchantments in the table pool.
- **The bottom offer is below level 30:** check shelf type, height, distance, and gap blocks using the [setup guide](../blocks/EnchantingTable.md#bookshelf-setup).
- **The same preview keeps returning:** reopening the menu is not a fresh roll; a successful enchant changes the player's seed.

## Related pages

- [Unbreaking and Mending](DurabilityEnchantments.md): wear chances, supported equipment, XP-repair selection, broken stacks and acquisition

- [Protection enchantments](ProtectionEnchantments.md): four armor choices, damage tags, combined cap, secondary attributes and retained broken gear

- [Efficiency, Fortune and Silk Touch](MiningEnchantments.md): tool eligibility, speed bonuses, loot choices and collection limits

- [Experience points, levels, and Mending](../mechanics/Experience.md)

- [Enchanting Table block](../blocks/EnchantingTable.md)
- [Enchanting Table item](../items/EnchantingTable.md)
- [Enchanted Book](../items/EnchantedBook.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active MattMC sources. No in-game tests were run. These costs describe Survival use; data packs can change enchantment definitions and tags, and later builds can change behavior. This page does not verify other acquisition routes for treasure enchantments.

- [Menu eligibility, previews, payment, book conversion, and seed reuse](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L85-L198)
- [Offer costs, shelf cap, item selection, and compatibility filtering](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L499-L600)
- [Level deduction and new player seed](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java#L1458-L1470)
- [Item eligibility](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971) and [Enchanted Book registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L2110-L2117)
- [Table enchantment pool](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json) and [bundled non-treasure members](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json)
- [Primary-item and compatibility rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L161)
- [Fortune definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/enchantment/fortune.json), [Silk Touch definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/enchantment/silk_touch.json), and [mining exclusion group](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/mining.json)
- [Sharpness definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/enchantment/sharpness.json), [table sword tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/enchantable/sword.json), and [supported sharp weapons](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/enchantable/sharp_weapon.json)
