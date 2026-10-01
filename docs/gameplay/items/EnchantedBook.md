# Enchanted Book

Enchanted Book stores enchantments for equipment progression. It is registered as `minecraft:enchanted_book`, stacks to **one**, and uses a stored-enchantments component rather than behaving like already-enchanted equipment.

## Obtaining from a table

Enchant an eligible plain [Book](Book.md) at an [Enchanting Table](../blocks/EnchantingTable.md). The output can store compatible enchantments from the table's available pool. The [Enchanting guide](../enchanting/Enchanting.md) explains costs, eligibility, and enchantments absent from that pool.

This article does not claim that table enchanting is the only acquisition route, or that it can produce every enchantment obtainable elsewhere.

## Applying and combining

The Anvil menu recognizes stored enchantments and checks support, compatibility, levels, and costs while combining items. A book holding an enchantment does not guarantee that it can apply to every tool or armor piece.

Check the offered anvil result and cost before taking it. A book can store more than one enchantment, while only supported and compatible effects may be relevant to a particular piece of equipment.

Enchanted Books are not eligible for another ordinary Enchanting Table offer in the checked registration/menu setup. Use the table on a plain Book when generating new offers, not on an existing Enchanted Book.

## Related pages

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
