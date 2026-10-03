# Knowledge Book

**Knowledge Book** (`minecraft:knowledge_book`) is a retained utility item whose recipe-unlocking effect is **inactive in current MattMC**. Even a book containing valid recipe IDs does not teach recipes: the server's recipe-award method returns without recording them. The ordinary item stacks to **one**, has **Epic** rarity, and starts with an **empty recipe list**. [Registration][registration] · [Use handler][use-handler] · [Inactive recipe award][recipe-award]

## Obtaining

One verified way to obtain the ordinary empty book is `/give @s minecraft:knowledge_book 1`, when run as a player with **permission level 2 or higher**. The command resolves the registered item and creates its default stack; this example does not add recipe data. For targeting and permission context, see [Commands](../commands/Commands.md). [Give permission and arguments][give] · [Registry lookup][item-parser] · [Stack creation][give-item] · [Empty default list][registration]

Knowledge Book has **no entry in the checked Creative category listings**. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) assembles those category entries rather than every registered item, so its registry ID alone does not make it a normal browser choice in either Survival or Creative. Ordinary listed items remain visible in both modes, but browser insertion requires Creative; this book is a listing exception. This review does not establish a natural drop or crafting route. [Complete category definitions][tabs] · [Browser list construction][browser]

## Usage

**Avoid using a Knowledge Book in hand in Survival if you want to keep it.** Once its ordinary use handler is reached, it consumes one book **before** checking the recipe list. An empty or invalid book can therefore disappear even though the use fails. Creative's infinite-materials handling preserves the item. These outcomes concern ordinary item use, not an interaction handled by a block first. [Handler order][use-handler] · [Consumption rule][consume] · [Player ability][infinite] · [Mode abilities][gamemode] · [Server dispatch and hand update][server-use]

A Knowledge Book can instead be **stored in a [Chiseled Bookshelf](../blocks/Bookshelves.md#chiseled-bookshelf)**: it belongs to the accepted-book tag, and using it on an empty front slot transfers the book into that slot. Follow the shelf guide for choosing a slot and taking it back. That is a storage interaction, not a recipe-unlocking use. [Accepted books][shelf-tag] · [Slot interaction][shelf-insert] · [Transfer and retrieval][shelf-transfer]

## Behavior

The book reads its `minecraft:recipes` component as a list of recipe IDs. The following are the checked **server-side outcomes after the item handler runs**:

| Recipe data on the book | Outcome |
| --- | --- |
| Empty list, including the ordinary default item | Use fails after the consumption attempt; no recipe award or item-use statistic is reached |
| Nonempty list containing an ID absent from the loaded recipe manager | The first missing ID makes use fail; no recipe award or item-use statistic is reached, and the consumed book is not refunded |
| Nonempty list whose IDs all resolve | Use succeeds and records the item-used statistic, but the called recipe-award method does nothing |

Consumption still respects Creative's infinite-materials rule in all three rows. A list is resolved completely before the award call, so a later invalid ID does not partially teach the earlier entries. [Recipe-list component][recipes-component] · [Ordered validation and statistic][use-handler] · [Loaded-recipe lookup][lookup] · [Inactive award][recipe-award] · [Statistic recording][stat-recording] · [Consumption][consume]

The normal client sends an item-use request; the server checks the held enabled item and dispatches the action. Spectator mode, cooldowns and an earlier handled interaction can prevent the book handler from running, so the table is not a promise that every right-click consumes a book. [Client use][client-dispatch] · [Server packet handling][server-packet] · [Dispatch checks][server-use]

For crafting layouts, use [Crafting](../crafting/Crafting.md) and the linked item/block guides. The current crafting-table screen has **no recipe-book interface**, and a Knowledge Book does not restore one. [Crafting screen][crafting-screen] · [Inactive recipe award][recipe-award]

## Notes

Source-reviewed on **2026-10-02** at `13ff4feddc5b7b0ce0cdbfd912a9486d817400fd`. Checked registration and default components, complete category listings, browser assembly, the permission-gated command route, client/server item-use dispatch, consumption and hand updates, recipe lookup/award behavior, and Chiseled Bookshelf storage. No command, UI, item-use, crafting or multiplayer runtime test was run. These findings describe this MattMC source; custom item components or later code changes can differ.

Related: [Crafting](../crafting/Crafting.md) · [Inventory item browser](../mechanics/InventoryBrowser.md) · [Bookshelves](../blocks/Bookshelves.md) · [Items](Items.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/item/Items.java#L2284-L2286
[use-handler]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/item/KnowledgeBookItem.java#L26-L53
[recipe-award]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1470-L1474
[stat-recording]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1457-L1462
[give]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/server/commands/GiveCommand.java#L23-L51
[give-item]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/commands/arguments/item/ItemInput.java#L39-L46
[item-parser]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/commands/arguments/item/ItemParser.java#L149-L155
[tabs]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L107
[consume]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[infinite]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[gamemode]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[client-dispatch]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L378-L407
[server-packet]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1303-L1323
[server-use]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L335
[recipes-component]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/core/component/DataComponents.java#L263-L265
[lookup]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L155-L157
[crafting-screen]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/client/gui/screens/inventory/CraftingScreen.java#L12-L32
[shelf-tag]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/resources/data/minecraft/tags/item/bookshelf_books.json#L1-L9
[shelf-insert]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java#L77-L96
[shelf-transfer]: https://github.com/HungLo2020/MattMC/blob/13ff4feddc5b7b0ce0cdbfd912a9486d817400fd/src/main/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java#L116-L137
