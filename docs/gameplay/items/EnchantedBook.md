# Enchanted Book

Enchanted Book stores enchantments for equipment progression. It is registered as `minecraft:enchanted_book`, stacks to **one**, and uses a stored-enchantments component rather than behaving like already-enchanted equipment.

## Obtaining from a table

Enchant an eligible plain [Book](Book.md) at an [Enchanting Table](../blocks/EnchantingTable.md). The output can store compatible enchantments from the table's available pool. The [Enchanting guide](../enchanting/Enchanting.md) explains costs, eligibility, and enchantments absent from that pool.

This article does not claim that table enchanting is the only acquisition route, or that it can produce every enchantment obtainable elsewhere.

## Applying and combining

The Anvil menu recognizes stored enchantments and checks support, compatibility, levels, and costs while combining items. A book holding an enchantment does not guarantee that it can apply to every tool or armor piece.

Check the offered anvil result and cost before taking it. A book can store more than one enchantment, while only supported and compatible effects may be relevant to a particular piece of equipment.

Put the equipment to keep in the **left Anvil slot** and the Enchanted Book in the **middle donor slot**. On a successful enchantment application, **the whole donor book is consumed**, including any stored enchantments that did not apply. There is no leftover book carrying rejected enchantments to use on another item. Creative skips level payment but still consumes the donor. If every donor enchantment is rejected, there is no combination output to take. See [Anvil combining rules](../mechanics/AnvilMechanics.md#combining-enchantments) and [what is kept and consumed](../mechanics/AnvilMechanics.md#what-is-kept-and-consumed) for costs and retained data. [Donor eligibility and conflicts][current-anvil-combining] · [Input consumption][current-anvil-consumption]

Enchanted Books are not eligible for another ordinary Enchanting Table offer in the checked registration/menu setup. Use the table on a plain Book when generating new offers, not on an existing Enchanted Book.

## Recycling unwanted books

Put **one Enchanted Book** in either input of a [Grindstone](../blocks/Grindstone.md), leaving the other slot empty, then inspect the output before taking it:

- **Only non-curse enchantments:** they are all removed and the result becomes a plain [Book](Book.md)
- **Curse plus other enchantments:** the other enchantments are removed, but the curse stays and the result remains an Enchanted Book
- **Only curses:** an output is still offered, but the curses stay and the operation awards no experience

The Grindstone removes all non-curse enchantments together; it does not let you choose one to erase or transfer an enchantment from equipment onto a Book. Experience comes from the removed non-curse enchantments, as explained in the [Grindstone's refund calculation](../blocks/Grindstone.md#experience-returned). [Single-book input][current-grindstone-inputs] · [Removal and Book conversion][current-grindstone-removal] · [Experience and consumption][current-grindstone-consumption]

Process unwanted books **one at a time**. Two ordinary Enchanted Books in the Grindstone give no result even if their enchantments are identical, because they are non-damageable and stack to one. Use the Anvil for book-to-book combinations. A plain Book or an empty Enchanted Book with only a glint does not qualify for the Grindstone's enchantment check. [Slot admission][current-grindstone-slots] · [Two-input restriction][current-grindstone-merge] · [Book registration][current-book-registration] · [Actual enchantment check][current-enchantment-check]

An ordinary curse-free Book recovered this way can go back to the Enchanting Table. This does not refund the lapis or guarantee better offers: a **successful table enchantment** changes the player's offer seed. See [changing the offers](../enchanting/Enchanting.md#changing-the-offers) for the reroll workflow. [Grindstone output][current-grindstone-removal] · [Table payment and book conversion][current-table] · [New player seed][current-player-seed]

## Related pages

- [Grindstone disenchantment and repair](../blocks/Grindstone.md)
- [Anvil repair, naming, and combining](../mechanics/AnvilMechanics.md)

- [Librarian trades](../trading/LibrarianTrades.md#enchanted-book-selection-and-price): possible book offers and their base Emerald-plus-Book cost

- [Equipment curses](../enchanting/EquipmentCurses.md#vanishing-the-death-inventory-check): stored curse books versus applied equipment effects

- [Book](Book.md)
- [Enchanting](../enchanting/Enchanting.md)
- [Anvil](Anvil.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game enchanting, anvil, mining, or crafting test was run.

- [Item registration and stored component](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L2110-L2117)
- [Table book conversion](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java)
- [Anvil support and combination rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/AnvilMenu.java)
- [Enchantable item check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971)

Whole-book Anvil consumption and Grindstone recycling were additionally source-reviewed on **2026-10-10** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. No gameplay test was run. These examples describe ordinary books with the bundled item components; custom item data can change eligibility.

[current-anvil-combining]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L133-L223
[current-anvil-consumption]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L71-L104
[current-grindstone-slots]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L48-L59
[current-grindstone-inputs]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L122-L136
[current-grindstone-merge]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L139-L164
[current-grindstone-removal]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L180-L193
[current-grindstone-consumption]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L66-L104
[current-book-registration]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L2110-L2117
[current-enchantment-check]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L77-L87
[current-table]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L150-L195
[current-player-seed]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/player/Player.java#L1462-L1471
