# Written Book

A **Written Book** holds finalized text with a title, author and copy generation. Use it for instructions, stories, shared notes, mapmaking or server documentation. It is the signed form of a [Book and Quill](BookAndQuill.md), not the plain [Book](Book.md) used in other recipes. [Finalization][server-edit] · [Stored content][written]

## Obtaining

Write in a Book and Quill, choose **Sign**, enter a title and select **Sign and Close**. This creates an **Original** Written Book with the signer's name as author. Follow the [editing and signing guide](BookAndQuill.md#sign-when-finished) for saving drafts and the normal input limits. Another player can give you a signed book, and eligible signed books can be copied as described below. [Signing screen][sign] · [Server conversion][server-edit] · [Generation labels][lang]

The registered Written Book item is **not an ordinary category entry in the current inventory browser**. That browser builds its list from category display stacks; registration or the existence of written-book content does not provide a general Creative acquisition route. Use the signed-book route above. The [inventory browser guide](../mechanics/InventoryBrowser.md#which-items-appear) explains the catalog and insertion boundaries. [Registration][items] · [Category definitions][tabs] · [List construction][browser]

## Usage

### Open and read

Hold the book and use it to open its reading screen. Turn pages with the arrows or Page Up/Page Down, and close with **Done** or Escape. This screen has no text editor or signing controls. The normal server opens it only for a held item carrying written-book content; a bare item ID without that content is not a substitute for a signed book. [Held use][use] · [Server opening][server-open] · [Client opening][client-open] · [Reading controls][view]

Put it on an empty [Lectern](../blocks/Lectern.md#placing-reading-and-taking-a-book) when you want a shared reading stand. The Lectern guide owns taking the book back, permissions, page persistence and redstone. A [Chiseled Bookshelf](../blocks/Bookshelves.md#book-types-and-front-face-slots) stores one book in each of its six slots; take a book out to read it. Ordinary Bookshelves do not store your written items. [Accepted Lectern books][lectern-tag] · [Accepted shelf books][shelf-tag] · [Shelf controls][shelf]

### Copying and generations

Put **one eligible Written Book and one or more Books and Quills** in separate crafting slots, in any arrangement. Each Book and Quill produces **one new Written Book**, and the source Written Book is returned unchanged as the recipe remainder. For example, one Original plus three Books and Quills yields **three copies, while retaining the Original**. The 2 × 2 inventory grid fits up to three target books; a 3 × 3 Crafting Table fits up to eight. [Recipe data][clone-json] · [Matching, output and remainder][clone] · [Remainder handling][result] · [Inventory grid][inventory] · [Table grid][table]

| Source label | New copies | Can the new copies be copied again? |
| --- | --- | --- |
| Original, generation 0 | Copy of original, generation 1 | Yes |
| Copy of original, generation 1 | Copy of a copy, generation 2 | No |
| Copy of a copy, generation 2 | No crafting result | No |

The source's title, author and pages carry into the copies; the copier does not become the author. A generation-2 book remains readable but cannot produce another crafting copy. The data format also recognizes generation 3, **Tattered**, which this recipe does not create or copy. [Generation checks and copied content][written] · [Displayed labels][lang]

The bundled target tag contains **Book and Quill only**. Plain Books are not blanks for this recipe. An already-written but unsigned Book and Quill still qualifies and is consumed, so protect your drafts by using empty spares. A second signed source book in another slot invalidates the recipe. [Target tag][clone-tag] · [Actual recipe checks][clone]

## Behavior

**Signing finalizes the pages.** There is no ordinary reading-screen action that restores an editable Book and Quill, changes the author, or rewrites the pages. Making a crafting copy also leaves the copy finalized. [Server conversion][server-edit] · [Reading interface][view] · [Copy output][clone]

The normal signing route inherits the Book and Quill's **100-page, 1,024-character-per-page input limits** and **15-character title field**. The written-book data format can represent richer page components and longer titles than that interface, but this is not permission to type formatted component data or extra pages in the ordinary editor. The server turns submitted pages into literal text. [Editor][edit] · [Signing field][sign] · [Packet][packet] · [Literal text conversion][server-edit] · [Written data][written]

Normal Written Books have a stack limit of **16**. Stacking also requires matching item components, so books with different titles, authors, pages or generations do not automatically combine into one stack. A normal signed book shows its title as the item name, and its tooltip includes the author and generation. [Registration][items] · [Stack comparison and naming][stack] · [Tooltip][written]

## Notes

- This item is registered as `minecraft:written_book`
- Keep the Original safe and distribute copies if you expect to make more later
- Use the [Book and Quill guide](BookAndQuill.md#behavior) for editor limits and server text filtering; use [Lectern](../blocks/Lectern.md) and [Bookshelves](../blocks/Bookshelves.md) for placed display and storage behavior

Related: [Book and Quill](BookAndQuill.md) · [Book](Book.md) · [Lectern](../blocks/Lectern.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked signing through the active edit-packet codec and server conversion, held reading through the server-to-client open-book path, the category-built browser, and loaded cloning recipe through matching, generation checks and returned inputs. No in-game signing, reading, copying, stacking or storage test was run. Bundled tags and server recipes can change. The [recipe manager][recipes] loads the [registered custom cloning serializer][serializers].

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2027-L2032
[tabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[edit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/BookEditScreen.java
[sign]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/BookSignScreen.java
[packet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/ServerboundEditBookPacket.java
[server-edit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L917-L953
[written]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/WrittenBookContent.java
[clone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/BookCloningRecipe.java
[clone-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/book_cloning_target.json
[clone-json]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/book_cloning.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L71-L90
[serializers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L10-L17
[view]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/BookViewScreen.java
[lectern-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/lectern_books.json
[shelf-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/bookshelf_books.json
[shelf]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java
[result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L113
[lang]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json#L2476-L2488
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/WrittenBookItem.java
[server-open]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1347-L1355
[client-open]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L2157-L2164
[inventory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L57
[table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L34-L47
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java
