# Lectern

A **Lectern** (`minecraft:lectern`) holds one readable book, lets players turn its pages, produces redstone signals, and acts as a Librarian's job-site block. Its displayed book is separate from the ordinary [Bookshelf](Bookshelves.md#bookshelf) used to craft it. [Registration][blocks] · [Block and book behavior][lectern] [lectern-entity] · [Job-site mapping][poi] [profession]

## Crafting, placement and collection

Craft **4 wooden slabs + 1 ordinary Bookshelf → 1 Lectern**. Put three slabs across the top row, the Bookshelf in the center, and the fourth slab in the bottom-center slot. Each slab must be in `minecraft:wooden_slabs`; accepted wood types can be mixed. A Chiseled Bookshelf is not a substitute. [Recipe][recipe] · [Slab tag and matching][slabs] [ingredient][] [pattern][]

The Lectern faces the player when placed. A normal crafted or harvested item places an **empty, unpowered** Lectern. Its hardness and blast resistance are both **2.5**. Use an unbroken axe for faster mining; a bare hand can still recover **one Lectern**, because the block has no correct-tool requirement. Silk Touch is unnecessary, Fortune adds nothing, and the block loot has an explosion-survival condition. [Placement and initial state][lectern] · [Properties][blocks] [properties] · [Axe/tool rules][axe] [tool][] [stack][] [player-tool][] [harvest][] · [Loot][loot]

## Placing, reading and taking a book

Use a [Book and Quill](../items/BookAndQuill.md) or [Written Book](../items/WrittenBook.md) on an empty Lectern. These are the two members of the bundled `lectern_books` tag. Plain Books, Enchanted Books and Knowledge Books do not fill it. Insertion transfers **one book** from the held stack in Survival; infinite-material players do not lose the held item. A book already on the stand must be removed before another can be inserted. [Accepted tag][books] · [Insertion dispatch][lectern] [use-dispatch] · [Consumption][stack]

Interact with a filled Lectern to open the reading screen. Use its page controls to change the displayed page. This screen reads the book; it is not the Book and Quill editor. **Take Book** returns the book to your inventory, or drops it if the inventory cannot accept it. Taking requires the player's build permission; players without that permission can use the reading controls but do not get the normal Take Book button. Spectators cannot send the page/take actions through the server menu-button handler. [Screen and menu][screen] [menu] · [Server button handler][menu-dispatch]

The placed Lectern saves the book and selected page; putting a new book on it starts at the first page. When normally broken, it drops the book separately from the Lectern item, without requiring Silk Touch. Replacing the harvested Lectern does not restore that book or its selected page. [Book/page save and load, removal][lectern-entity] · [Active removal callback][chunk-removal] · [Block loot][loot]

## Pages and redstone

There are two outputs to distinguish:

- **Comparator output:** a continuing signal based on the displayed page
- **Page-turn pulse:** signal strength **15** when the stored page changes, scheduled to switch off after **2 game ticks** (nominally 0.1 seconds at 20 ticks per second)

Opening the reading screen without changing the stored page does not trigger that page-change pulse. The pulse provides weak power in all directions and direct power to the block beneath the Lectern. The comparator reads the page value separately. [Page change and signal callbacks][lectern] [lectern-entity] · [Comparator input][comparator] · [Direct-power direction][signals]

For a book with **N > 1 pages**, the comparator strength is **1 + floor(14 × (P − 1) / (N − 1))**, where **P** is the displayed page number starting at 1. The first page gives **1** and the last gives **15**. An empty Lectern gives **0**; a valid book with zero or one stored page gives **15**. [Page-count and signal calculation][lectern-entity] · [Empty-block gate][lectern]

For example, a three-page book gives **1, 8, 15** on pages 1, 2 and 3. Books with many pages can have neighboring pages with the same comparator strength, although changing the stored page still requests a pulse. Setting the page is clamped to the book's page range. Removing the book clears both the book-present and powered states. [Page setter, removal reset and signal formula][lectern-entity] [lectern]

## Automation limits

**Hoppers cannot insert a book into or extract the displayed book from a Lectern.** Its reading menu's private one-book access is not exposed as a world container to the Hopper lookup. A Dropper facing it therefore uses ordinary item ejection rather than placing a book on the stand. Use the normal player book interaction, or use a [Chiseled Bookshelf](Bookshelves.md#hoppers-and-droppers) when you need automated book storage. [Lectern interfaces and menu access][lectern] [lectern-entity] · [Hopper container lookup][hopper] · [Dropper fallback][dropper]

The Lectern's redstone output does not turn its own pages. The checked page changes come through the reading menu's previous/next/jump actions, rather than a redstone-input handler. [Menu actions][menu] · [Block callbacks][lectern]

## Librarian job site

An available, reachable Lectern can be claimed by an unemployed adult Villager to become a **Librarian**. It has room for **one claimant**, and **does not need a book**: all of the Lectern's facing, powered and book-present states are registered as the same Librarian job-site type. Nitwits and babies do not become Librarians through this normal job-acquisition path. [All-state POI registration][poi] · [Profession mapping][profession] · [Active brain, acquisition and assignment][villager] [goals][] [acquire][] [assign]

Placing a Lectern near a Villager does not guarantee that Villager claims it or offers a particular enchanted book. Removing the workstation only permits the ordinary profession reset when the Villager has **zero villager experience and is still level 1**; trading can prevent that reset. Preserve the full [Villager employment guidance](../mobs/Villager.md#employment-and-changing-jobs) and [Trading guide](../trading/Trading.md) when planning a Librarian setup. [Reset conditions][reset] · [Trade selection][villager]

A Lectern does not contribute to Enchanting Table bookshelf power. Use the [ordinary Bookshelf setup](EnchantingTable.md#bookshelf-setup) for that purpose. [Power-provider tag][providers]

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked the exact recipe and tags, tools and loot, item/book placement, active reading-screen/menu/server-button path, comparator and scheduled pulse callbacks, book/page persistence, removal, unavailable Hopper container access, and actual Librarian acquisition wiring. Natural generation and exceptional command-supplied book data were not surveyed. No in-game crafting, reading, redstone, save/reload, mining or Villager test was run. Server data and later source changes can change these results.

Related: [Lectern item](../items/Lectern.md) · [Bookshelves](Bookshelves.md) · [Book and Quill](../items/BookAndQuill.md) · [Written Book](../items/WrittenBook.md) · [Workstations and storage](catalog/workstations.md) · [Blocks](Blocks.md)

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
[lectern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/LecternBlock.java
[lectern-entity]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/LecternBlockEntity.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/lectern.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/lectern.json
[books]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/lectern_books.json
[screen]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/gui/screens/inventory/LecternScreen.java
[menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/LecternMenu.java
[signals]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/SignalGetter.java
[menu-dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1866-L1880
[poi]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java
[profession]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java
[villager]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/Villager.java
[goals]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java
[acquire]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java
[assign]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java
[reset]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/behavior/ResetProfession.java
[providers]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/enchantment_power_provider.json
