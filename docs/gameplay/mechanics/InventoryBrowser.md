# Inventory item browser

MattMC includes a **JEI-style item browser beside inventory screens**. **You can browse the catalog in Survival and Creative, but ordinary Survival insertion requests are skipped before the server inventory handler.** Item insertion requires the server player's infinite-materials ability, normally supplied by Creative mode. A visible entry is not a Survival acquisition route. [Screen integration][screen] · [Packet gate][protocol-gate] · [Server context][server-context] · [Ability][infinite-materials] · [Game-mode abilities][mode-abilities]

## Finding and requesting an item

Browse in either mode; use Creative for admitted insertion requests. The [mode limit](#mode-and-permission-limits) applies before the later slot and item checks.

1. Open your own inventory with the configured Inventory key. The normal path opens the same inventory screen in Survival and Creative. [Inventory opening][open] · [Shared screen][inventory]
2. Find the item panel on the right. If there is too little screen space beside the inventory, the panel has no visible columns; enlarge the available view or use a smaller GUI scale. [Panel layout][layout]
3. Click the search field and type part of the item's **displayed name**. Matching ignores case. This search checks names, not arbitrary registry IDs or a mod-name query language. Scroll over the panel or use its scrollbar to browse more entries. [Name matching][search] · [Search and item clicks][click] · [Scrolling][scroll]
4. For a Creative insertion request, put away anything held on the mouse cursor and leave room in your inventory. An occupied cursor prevents an item request. Click an item to request **one**, or **Shift-click** to request its maximum stack size. [Click handling][click]

The client chooses matching partial player-inventory stacks first, then empty player-inventory slots, excluding armor slots. It requests only quantities that fit its current view; the server still has to admit and handle each request. A full inventory is not a promise that a stack will appear or be dropped beside you. Use your own inventory for this basic workflow; this review does not certify every other container screen's slot layout. [Slot selection and capacity][slots] · [Packet gate][protocol-gate]

An admitted Creative request does not consume recipe ingredients or experience. The item must be enabled for the current game's feature set, and the server checks the requested slot and stack count. Those later checks do not remove the earlier mode gate. [Client feature check][client] · [Server checks][server] · [Packet gate][protocol-gate]

## Mode and permission limits

The client sends the creative-slot update even in Survival: its outgoing protocol context permits it. The server uses a different, live context bound to the actual player. That context reads `instabuild`, which ordinary Survival sets to false and Creative sets to true. When it is false, decoding skips the packet **before** the inventory-insertion handler runs. The handler's relaxed mode check cannot override this earlier gate. [Client protocol][client-protocol] · [Server binding][server-binding] · [Server context][server-context] · [Player ability][infinite-materials] · [Mode assignment][mode-abilities] · [Active codec modifier][modifier] · [Packet registration][packet-registration] · [Admission check][protocol-gate]

This applies to the inspected **dedicated multiplayer and integrated single-player** paths. The local memory connection also uses packet serialization and the same decoder; it is not a bypass. The decoder consumes the rejected frame payload, and the connection handles this exception as a skipped packet. This source review does not claim an observed disconnect, crash, or specific client display afterward. [Dedicated setup][dedicated] · [Integrated connection][integrated] · [Local server setup][local-server] · [Serialization][serialization] · [Memory connection][memory] · [Decoder skip][decoder] · [Connection handling][skip]

The gate tests the server ability, not only the displayed game-mode name. Modified abilities, changed code or a future fix may change admission. The current ordinary Survival path does not provide server-accepted items merely because the catalog is visible.

## Which items appear

The browser combines **category-tab display entries**, removing duplicate stacks with identical items and components. It does not simply enumerate every registered item. An item can therefore be registered but absent from this browser. A block without any inventory-item registration is another separate case. [Category-list assembly][list]

**Operator-category entries are permission-gated.** The tab builder receives `canUseGameMasterBlocks`, which requires both the instant-build ability and permission level **2 or higher**. Its operator list is added only when that permission flag is true. Ordinary listed building materials or ingredients do not acquire that gate merely because they lack a crafting recipe. [Permission passed to tabs][list] · [Player gate][permissions] · [Operator category][operator]

Visibility and possession also do not override a block or item's own use restrictions. Follow the relevant guide for command, structure and other operator tools. Creative mode's abilities and mode-changing permissions remain documented in [Game modes](../gamemodes/Gamemodes.md).

## Reading acquisition notes elsewhere

A guide's “no bundled recipe” or “no natural source verified” statement describes those particular resource routes. An ordinary category-listed item can have a **Creative browser route** without having an ordinary Survival acquisition route. Conversely, a registered item can be absent from the category list. Obtaining an item, using an item already supplied by another route, and merely seeing its catalog entry are separate questions. [List construction][list] · [Client request][client] · [Protocol admission][protocol-gate]

Related: [Items](../items/Items.md) · [Creative](../gamemodes/Creative.md) · [Survival](../gamemodes/Survival.md) · [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-03. This correction follows the client sender through active codec-modifier application, the real server ability context, dedicated and integrated transport, packet skipping and the later handler. Existing UI/source excerpts were rechecked and repinned only where their exact lines match. **Earlier wording that treated the permissive handler as proof of ordinary Survival insertion was incorrect.** No inventory request, game-mode change, running-client UI or multiplayer test was performed. Valid crafting, loot, item-use rules and operator-category gates are separate from this correction.

[open]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L2059-L2066
[inventory]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/InventoryScreen.java#L34-L45
[screen]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L107-L119
[list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[layout]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L142-L174
[search]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L115-L132
[click]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L317-L398
[scroll]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L423-L445
[slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L530-L633
[client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[permissions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1817
[operator]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2166

[protocol-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[packet-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L114-L118
[modifier]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/protocol/ProtocolInfoBuilder.java#L164-L176
[client-protocol]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/ClientConfigurationPacketListenerImpl.java#L188-L194
[server-binding]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/players/PlayerList.java#L160-L164
[server-context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[infinite-materials]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[mode-abilities]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[dedicated]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerConnectionListener.java#L88-L100
[integrated]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L2208-L2215
[local-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerConnectionListener.java#L106-L128
[serialization]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/Connection.java#L494-L505
[memory]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/Connection.java#L519-L530
[decoder]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/PacketDecoder.java#L22-L35
[skip]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/network/Connection.java#L129-L134
