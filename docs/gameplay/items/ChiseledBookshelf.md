# Chiseled Bookshelf

**Chiseled Bookshelf** is the placeable item for `minecraft:chiseled_bookshelf`, a six-slot book store. Its crafting and ordinary harvested item start empty. [Registration and empty item contents][items]

## Obtaining

Follow the canonical [crafting recipe](../blocks/Bookshelves.md#chiseled-bookshelf): **6 planks and 3 wooden slabs produce 1 shelf**. **Silk Touch** is required to recover the shelf block when mining; without it there is no shelf-item drop. Stored books spill separately either way. An axe speeds mining, without a required tool tier. [Recipe][recipe] · [Loot][loot] · [Mining and separate contents](../blocks/Bookshelves.md#mining-and-block-properties)

## Uses

Place it to store one accepted book in each front-face slot. Use the [book types and slot guide](../blocks/Bookshelves.md#book-types-and-front-face-slots), [automation rules](../blocks/Bookshelves.md#hoppers-and-droppers), and [comparator output](../blocks/Bookshelves.md#comparator-output).

**Silk Touch does not package the stored books inside the shelf item.** The normal breaking path drops the books separately, and placing that recovered shelf gives empty storage. See [saving and moving books](../blocks/Bookshelves.md#saving-breaking-and-moving-books). This shelf does not replace an ordinary Bookshelf in enchanting setups or the Lectern recipe.

## Verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`; the [block guide](../blocks/Bookshelves.md) records active storage, removal and item-component evidence. No in-game test was run.

[Bookshelf item](Bookshelf.md) · [Bookshelves](../blocks/Bookshelves.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/chiseled_bookshelf.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/chiseled_bookshelf.json
