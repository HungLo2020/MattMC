# Bookshelves

**Bookshelf** is an enchanting-power and building block. **Chiseled Bookshelf** is a separate six-slot book store with a comparator output. Putting books into a Chiseled Bookshelf does not turn it into an enchanting-power provider. [Registrations][blocks] · [Storage behavior][shelf] · [Power-provider tag][providers]

## Bookshelf

**ID:** `minecraft:bookshelf`. The ordinary Bookshelf has no inventory or book-reading interaction; its visible books are part of the block. It has no facing property, so placing it from a different direction does not create a different storage front. [Registration and base behavior][blocks] [properties]

Only this ordinary form belongs to the bundled **enchantment-power-provider** tag. The Enchanting Table counts it only at its checked shelf positions with an allowed intervening gap. Use the canonical [Bookshelf setup](EnchantingTable.md#bookshelf-setup) for the layout, allowed heights and effective shelf limit, and [Enchanting](../enchanting/Enchanting.md) for offers and payment. Chiseled Bookshelves and Lecterns are not substitutes. [Provider and transmitter tags][providers] [transmitters] · [Position/gap test and active menu calculation][enchanting-block] [enchanting-menu]

### Crafting and buying

Craft **6 planks + 3 Books → 1 Bookshelf**: fill the top and bottom rows with planks and put the three Books across the middle. Each plank slot accepts a member of the bundled `minecraft:planks` item tag; matching wood colors are not required. [Recipe][bookshelf-recipe] · [Planks tag and ingredient matching][planks] [ingredient][] [pattern][]

A novice Librarian can also offer **one Bookshelf for a base price of 9 Emeralds**. The offer is one choice in its level-1 trade pool, so it is not guaranteed for every Librarian; demand and other price adjustments can change the displayed cost. The offer exists in both the normal and optional rebalance pools. Follow [Trading](../trading/Trading.md) for offer selection and pricing. [Trade pools][trades] · [Active pool selection][villager]

## Chiseled bookshelf

**ID:** `minecraft:chiseled_bookshelf`. Craft **6 planks + 3 wooden slabs → 1 empty Chiseled Bookshelf**. Fill the top and bottom rows with planks and the middle row with slabs. It uses the `planks` and `wooden_slabs` item tags, not an ordinary Bookshelf or Books. The wood types can be mixed within those tags. Bamboo Mosaic and Bamboo Mosaic Slabs are not in those ingredient tags. [Recipe][chiseled-recipe] · [Exact accepted tags][planks] [slabs] · [Ingredient matching][ingredient] [pattern]

### Book types and front-face slots

The front faces the player when placed. Use that **front face** to put a book into an empty slot or remove a book from an occupied slot; the top, back and sides do not select a book slot. The six slots are two rows of three, numbered left to right as you look at the front: [Placement and interaction][shelf] · [Face-coordinate selection][slot-selection]

| Front row | Left | Middle | Right |
| --- | ---: | ---: | ---: |
| Top | 1 | 2 | 3 |
| Bottom | 4 | 5 | 6 |

Each slot holds **one item** from the bundled `bookshelf_books` tag:

- [Book](../items/Book.md) — `minecraft:book`
- [Book and Quill](../items/BookAndQuill.md) — `minecraft:writable_book`
- [Written Book](../items/WrittenBook.md) — `minecraft:written_book`
- [Enchanted Book](../items/EnchantedBook.md) — `minecraft:enchanted_book`
- [Knowledge Book](../items/KnowledgeBook.md) — `minecraft:knowledge_book`

This is an acceptance list, not a claim that every listed item has an ordinary Survival acquisition route. Other items are rejected by the shelf's storage check. [Accepted-book tag][books] · [Inventory size and filter][shelf-entity]

Inserting transfers one book from the held stack in Survival. Clicking an occupied front slot removes its existing book, even if you are holding another accepted book; it does **not** exchange the two in one click. The removed item goes into your inventory, or drops if there is no room. Clicking an empty slot with an empty hand does not change the contents. There is no inventory screen or reading screen: take out a book to read or edit it, or put a suitable book on a [Lectern](Lectern.md). [Actual interactions][shelf] · [Consumption rules][stack] · [Server dispatch][use-dispatch]

### Hoppers and droppers

An enabled [Hopper](Hopper.md) pointing into a Chiseled Bookshelf can insert accepted books, and a Hopper underneath can extract them. A [Dropper](DispenserAndDropper.md) pointing into the shelf uses the same container insertion path. These are container transfers; a Dispenser's ordinary item-ejection behavior is different. [Hopper container lookup and transfer][hopper] · [Dropper insertion][dropper] · [Dispenser default behavior][dispenser] [dispense-registrations] · [Shelf inventory interface][shelf-entity] [list-container]

Automation scans slots in order **1 through 6**, using the first slot that can accept or supply a book. The shelf has no different face-specific slot list, so pointing into another face does not target that visible column. The one-book-per-slot limit and accepted-book filter still apply. Removal also requires room in the destination container. [Slot order and transfer checks][hopper] · [Shelf limits and extraction check][shelf-entity] [list-container]

### Comparator output

A comparator reads **the last slot changed by insertion or removal**, using the 1–6 numbering above. A newly placed empty shelf starts at **0**. The signal is not the number of books: removing the last book from slot 6 can leave an empty shelf with signal **6**. Automated insertions and removals also update this stored slot value. [Stored interaction and state update][shelf-entity] · [Analog output][shelf] · [Comparator read path][comparator]

### Saving, breaking and moving books

A placed Chiseled Bookshelf saves its six item stacks and last-interacted slot. Written text and enchantments travel with the stored book item. This persistence while placed is different from harvesting the shelf. [Save/load and item storage][shelf-entity]

**Breaking the shelf spills its stored books separately, including when using Silk Touch.** Silk Touch decides whether the shelf block itself drops; it does not pack the books into that drop. An ordinary harvested shelf item has empty storage when placed again. Remove valuable books first if loose drops would be difficult to collect. [Active block-entity removal][chunk-removal] [base-entity][] [containers][] · [Shelf loot][chiseled-loot] · [Default item contents and placement][items] [block-item]

## Mining and block properties

An **unbroken axe**, including Wood, mines both shelf forms faster than an empty hand. Neither requires a particular tool or material tier to run its ordinary harvest loot; **Silk Touch is the separate condition for recovering a shelf block**. [Registry][blocks] · [Axe mining and tool speed][axe] [tool][] [stack][] · [Harvest gate][player-tool] [harvest]

| Block | With Silk Touch | Without Silk Touch | Hardness | Blast resistance |
| --- | --- | --- | ---: | ---: |
| Bookshelf | 1 Bookshelf | 3 Books; no planks returned | 1.5 | 1.5 |
| Chiseled Bookshelf | 1 empty Chiseled Bookshelf | No shelf item | 1.5 | 1.5 |

Stored Chiseled Bookshelf contents spill separately in either case. Fortune does not multiply these results. Ordinary Bookshelf's non-Silk-Touch book loot has explosion decay; the table describes normal player harvesting, not guaranteed survival of loose items in an explosion. Hardness is not a measured breaking time. [Loot][bookshelf-loot] [chiseled-loot] · [Registered and inherited properties][blocks] [properties]

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked both shelf registrations, recipes and loot, active front-slot interactions, book filters, hopper/dropper access, comparator dispatch, placed-state serialization, ordinary removal, Librarian offers and the enchanting provider/range relationship. Natural generation and exceptional command-supplied item contents were not surveyed. No in-game crafting, mining, storage, automation, save/reload, trading or enchanting test was run. Server data changes can alter tags, recipes, offers and loot.

Related: [Bookshelf item](../items/Bookshelf.md) · [Chiseled Bookshelf item](../items/ChiseledBookshelf.md) · [Lectern](Lectern.md) · [Enchanting Table](EnchantingTable.md) · [Workstations and storage](catalog/workstations.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java
[properties]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[axe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/ToolMaterial.java
[stack]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/ItemStack.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[player-tool]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L343-L378
[recipes]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[planks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/planks.json
[slabs]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/Ingredient.java
[pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[chunk-removal]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java
[base-entity]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java
[containers]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/Containers.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[dropper]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/DropperBlock.java
[comparator]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java
[shelf]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java
[shelf-entity]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/ChiseledBookShelfBlockEntity.java
[list-container]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/ListBackedContainer.java
[slot-selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/SelectableSlotContainer.java
[dispenser]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/DispenserBlock.java
[dispense-registrations]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java
[books]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/bookshelf_books.json
[bookshelf-recipe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/bookshelf.json
[chiseled-recipe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/chiseled_bookshelf.json
[bookshelf-loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/bookshelf.json
[chiseled-loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/chiseled_bookshelf.json
[providers]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/enchantment_power_provider.json
[transmitters]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/enchantment_power_transmitter.json
[enchanting-block]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/EnchantingTableBlock.java
[enchanting-menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java
[trades]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/Villager.java
[block-item]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BlockItem.java
