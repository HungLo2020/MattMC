# Book and Quill

A **Book and Quill** is an editable notebook. Write and save pages while it is unsigned, then sign it only when you are ready to make a finalized [Written Book](WrittenBook.md). [Item and content][items] · [Editing][edit] · [Signing][server-edit]

## Obtaining

Craft **1 plain [Book](Book.md) + 1 Ink Sac + 1 Feather → 1 Book and Quill**, in any arrangement with one ingredient in each of three slots. The recipe fits the inventory's 2 × 2 grid or a Crafting Table. A Glow Ink Sac is not the Ink Sac ingredient. [Recipe][recipe] · [Ingredient matching][shapeless] · [Inventory grid][inventory]

Book and Quill is a category-listed item in the [inventory browser](../mechanics/InventoryBrowser.md). Its ordinary insertion route requires Creative's infinite-materials ability; seeing it in Survival does not provide a book. See the browser's [mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Category entry][tabs] · [List construction][browser]

## Usage

### Write and save

1. Hold the Book and Quill and use it to open the editor. Ordinary held use supports either hand
2. Type on the current page. The text field supports selection, copy, cut, paste and newlines
3. Use the page arrows, or Page Up/Page Down, to move between pages. Going forward from the last page adds another until the 100-page limit
4. Choose **Done** to save your writing and close the editor. The book remains editable when you open it again

The editor's **Done** button is the save action. **Escape closes the screen without sending that save**, so use Done to keep new edits. Saving removes completely empty pages at the end; empty pages between written pages remain. [Held editor][use] · [Client opening][local-open] · [Editor controls and save][edit] · [Text controls][text-field] · [Escape behavior][screen]

### Sign when finished

Choose **Sign**, enter a title of up to **15 characters** in the normal title field, then choose **Sign and Close**. **Cancel** returns to the editor so you can keep working. Leading and trailing title spaces are trimmed on submission. The author is the signing player's server-side name, not a separate editable author field. [Signing screen][sign] · [Button labels][lang] · [Finalization][server-edit]

**Signing is irreversible through the ordinary book interface.** It replaces this item with a Written Book and removes the editable content. The signed book can be read and copied, but cannot be reopened as this notebook for corrections. Keep an unsigned draft separately if you need one; the crafting copy recipe copies signed books, not an editable draft. [Finalization][server-edit] · [Copy recipe][clone]

### Display, store or spend a spare

Use a book on an empty [Lectern](../blocks/Lectern.md#placing-reading-and-taking-a-book) to display it for reading. The Lectern's screen does not edit it: take it back and open it in hand to continue writing. A [Chiseled Bookshelf](../blocks/Bookshelves.md#book-types-and-front-face-slots) stores one book per slot; remove it to read or edit. [Accepted Lectern books][lectern-tag] · [Lectern screen][lectern-screen] · [Shelf interaction][shelf]

A spare Book and Quill is consumed when [copying a Written Book](WrittenBook.md#copying-and-generations). The recipe accepts this item even if it already contains notes, so use an empty spare. An expert Librarian can also offer to buy it for an Emerald; that offer does not require blank pages either. Check the [Trading guide](../trading/Trading.md) for offer selection and restocking. [Copy targets][clone] · [Librarian pools][trades] · [Trade item matching][trade-cost]

## Behavior

The normal editor allows **up to 100 pages**, with **1,024 characters and 14 wrapped display lines per page**. The line limit often fills a page before its character cap; move to a new page yourself. A paste that would exceed the line limit is rejected rather than flowing onto another page. [Editor limits][edit] · [Input handling][text-field]

The active edit packet independently bounds each page to 1,024 characters and the page list to 100. It allows a title up to 32 characters, but that does **not** enlarge the normal signing field's 15-character limit. These code string limits should not be read as a promise of that many visible symbols for every Unicode input. [Packet limits][packet] · [Writable content][writable] · [Title field][sign]

Saving and signing send the selected hotbar slot or offhand slot to the server. The handler accepts those slot classes, runs text through the server's filtering path, then checks that the item still has editable-book content before updating it. A title changes the action from saving a draft to finalizing a Written Book. Server filtering can affect the stored or displayed text. [Active packet registration][protocol] · [Save/sign sender][edit] · [Server checks][server-edit]

## Notes

- This item is registered as `minecraft:writable_book` and normally has a stack limit of **1**. [Registration][items]
- The checked Librarian listing declares a base input of two, but the current merchant price calculation clamps the actual first input to this item's stack limit of one. Use the displayed offer; do not assume the listing's raw count is the payment. [Offer][trades] · [Price clamp][trade-price]
- Plain Books, Book and Quill, Written Books and Enchanted Books have different roles. Start with the [Book guide](Book.md) for the plain crafting ingredient

Related: [Written Book](WrittenBook.md) · [Paper](Paper.md) · [Lectern](../blocks/Lectern.md) · [Bookshelves](../blocks/Bookshelves.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registration, the loaded shapeless recipe, held-use dispatch, current editor and signing screens, the registered packet codec and final server handler, copy targets, and Librarian item matching and price calculation. No in-game crafting, editing, saving, signing, copying or trading test was run. The [recipe manager][recipes] reads the recipe through its current [serializer][serializers]; changed server data or code can change these results.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2027-L2032
[tabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[edit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/BookEditScreen.java
[sign]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/BookSignScreen.java
[packet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/ServerboundEditBookPacket.java
[protocol]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L60-L85
[server-edit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L917-L953
[writable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/WritableBookContent.java
[clone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/BookCloningRecipe.java
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L71-L90
[serializers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L10-L17
[lectern-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/lectern_books.json
[lectern-screen]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/LecternScreen.java
[shelf]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ChiseledBookShelfBlock.java
[lang]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json#L2476-L2488
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/writable_book.json
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java
[inventory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L57
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/WritableBookItem.java
[local-open]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/player/LocalPlayer.java#L548-L553
[text-field]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/components/MultilineTextField.java
[screen]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/Screen.java#L111-L186
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[trade-cost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/ItemCost.java
[trade-price]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L92-L104
