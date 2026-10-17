# Debug Stick

The Debug Stick changes a block's existing state properties, such as the direction a supported block faces. It is an operator utility, and possessing one does not grant permission to use it. [Interaction rules][interaction]

## Obtaining

Search for **Debug Stick** in the [inventory item browser](../mechanics/InventoryBrowser.md). Its category entry comes only from **Operator Utilities**: the browser includes it when the player has both the instant-build ability, normally supplied by Creative mode, and permission level **2 or higher**. Creative mode alone is insufficient. [Operator entry][operator] · [Browser categories][browser] · [Permission gate][permission] · [Mode abilities][modes]

Leave inventory space and clear the mouse cursor before requesting it. Browser insertion still depends on the server's infinite-materials gate, enabled features, and valid slot and stack checks; a catalog entry is not an ordinary Survival acquisition route. [Browser instructions](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) · [Packet gate][packet] · [Server checks][server]

A player with permission to run `/give` can also use `/give @s minecraft:debug_stick`. The command requires permission level 2; obtaining the stick this way does not bypass its separate use restriction. No bundled crafting, loot, or trade source was found in the reviewed resource and provider search. [Give command][give] · [Item registration][registration]

## Usage

Use Creative mode with permission level 2 or higher for the normal workflow. The stick checks the instant-build ability and that permission level again when you try to select or change a property. [Use gate][interaction] · [Permission gate][permission]

1. Hold the stick and **left-click a block** to cycle through its available properties. The on-screen message identifies the selected property and its current value.
2. **Right-click the block** to cycle the selected property's value. If no property has been selected for that block type, the stick uses its first available property.
3. Hold **Sneak** while clicking to cycle in the opposite direction, both when choosing a property and when changing its value. [Controls][controls] · [Cycling and feedback][interaction] · [Sneak input][sneak]

If a block opens a menu or otherwise handles right-click first, Sneak can bypass that block interaction so the stick receives the click. This also reverses the stick's cycling direction. [Block interaction order][use-order]

## Behavior

The selected property is remembered **on that stick, separately for each block type**. Choosing a property does not change the block; right-clicking changes the chosen property's value on the targeted block. The stick only cycles values that the property defines. Blocks with no state properties report that there is nothing to edit. [Stored selection and updates][interaction]

The stick's block-breaking callback returns false, so it does not mine the block when that callback is reached. It edits block states rather than replacing the block with a different block type. [Block-breaking behavior][controls] · [State change][interaction]

## Notes

* Registry ID: `minecraft:debug_stick`; maximum stack size: **1**. Its built-in glint does not require an enchantment. [Registration][registration]
* A stick supplied to a Survival player does not make state editing available: ordinary Survival lacks the required instant-build ability. [Permission gate][permission] · [Mode abilities][modes]
* This is different from the [World Edit Wand](WorldEditWand.md), which marks region corners for editing commands.

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The acquisition search covered bundled data, recipe/loot providers, and villager trades. Custom data packs or server changes may add other sources. Controls and restrictions were traced in source; no running-client or multiplayer test was performed.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2287-L2295
[operator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2148
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[permission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1817
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[packet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1905
[give]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/GiveCommand.java#L23-L34
[controls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/DebugStickItem.java#L28-L49
[interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/DebugStickItem.java#L51-L100
[sneak]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L310
[use-order]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L388
