# Inventory item browser

MattMC includes a **JEI-style item browser beside inventory screens**. For ordinary listed items, the checked client and server deliberately allow item insertion in **Survival as well as Creative**. This is a separate acquisition route from crafting, mining, mob drops and world generation; a browser entry does not establish any of those routes. [Screen integration][screen] · [Client request][client] · [Server insertion][server]

## Finding and requesting an item

1. Open your own inventory with the configured Inventory key. The normal path opens the same inventory screen in Survival and Creative. [Inventory opening][open] · [Shared screen][inventory]
2. Find the item panel on the right. If there is too little screen space beside the inventory, the panel has no visible columns; enlarge the available view or use a smaller GUI scale. [Panel layout][layout]
3. Click the search field and type part of the item's **displayed name**. Matching ignores case. This search checks names, not arbitrary registry IDs or a mod-name query language. Scroll over the panel or use its scrollbar to browse more entries. [Name matching][search] · [Search and item clicks][click] · [Scrolling][scroll]
4. Put away anything held on the mouse cursor and leave room in your inventory. An occupied cursor prevents an item request. Click an item to request **one**, or **Shift-click** to request its maximum stack size. [Click handling][click]

The insertion routine fills matching partial player-inventory stacks first, then empty player-inventory slots, excluding armor slots. It sends only quantities that fit; a full inventory is not a promise that a requested stack will appear or be dropped beside you. Use your own inventory for this basic workflow; this review does not certify every other container screen's slot layout. [Slot selection and capacity][slots]

Requesting an ordinary listed item through this route does not require changing game mode, supplying a crafting ingredient, or spending experience. Normal Survival hunger, damage and movement rules still apply. The item must be enabled for the current game's feature set, and the server checks the requested slot and stack count. These are the checked MattMC rules, not a promise about another server build or a modified client. [Client feature check][client] · [Server checks and addition][server]

## Which items appear

The browser combines **category-tab display entries**, removing duplicate stacks with identical items and components. It does not simply enumerate every registered item. An item can therefore be registered but absent from this browser. A block without any inventory-item registration is another separate case. [Category-list assembly][list]

**Operator-category entries are permission-gated.** The tab builder receives `canUseGameMasterBlocks`, which requires both the instant-build ability and permission level **2 or higher**. Its operator list is added only when that permission flag is true. Ordinary listed building materials or ingredients do not acquire that gate merely because they lack a crafting recipe. [Permission passed to tabs][list] · [Player gate][permissions] · [Operator category][operator]

Visibility and possession also do not override a block or item's own use restrictions. Follow the relevant guide for command, structure and other operator tools. Creative mode's abilities and mode-changing permissions remain documented in [Game modes](../gamemodes/Gamemodes.md).

## Reading acquisition notes elsewhere

A guide's “no bundled recipe” or “no natural source verified” statement describes those particular resource routes. For an ordinary category-listed item, the browser can still provide an inventory insertion route in this implementation. Conversely, an item missing from the category list is not made searchable merely by having a registry ID. Keep these distinctions when testing partially integrated content. [List construction][list] · [Insertion path][client] · [Server implementation][server]

Related: [Items](../items/Items.md) · [Creative](../gamemodes/Creative.md) · [Survival](../gamemodes/Survival.md) · [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. Followed Inventory-key opening, screen initialization, category construction, visibility/search/click controls, cursor/capacity checks, client request and server handling, plus operator-category permission selection. No inventory request, game-mode change, running-client UI test or multiplayer test was performed. This describes the checked source, with ordinary browser insertion kept separate from recipe/loot acquisition and operator use permissions.

[open]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/Minecraft.java#L2059-L2066
[inventory]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/InventoryScreen.java#L34-L45
[screen]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L107-L119
[list]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[layout]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L142-L174
[search]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L115-L132
[click]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L317-L398
[scroll]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L423-L445
[slots]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L530-L633
[client]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[server]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[permissions]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1817
[operator]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2166
