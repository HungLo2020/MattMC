# World Edit Wand

The World Edit Wand marks two corners for MattMC's built-in WorldEdit commands. **Right-click sets position 1; a left-click block-breaking attempt sets position 2.** Sneak-right-click does not select position 2. This item is separate from the [Building Wand](BuildingWand.md). [Wand controls][wand] · [Separate registrations][registration]

## Obtaining

There are two routes with different permission checks:

* **Inventory browser:** search for **WorldEdit Wand** in the [inventory item browser](../mechanics/InventoryBrowser.md). Its category entry comes only from **Operator Utilities**, which requires the instant-build ability, normally Creative mode, plus permission level **2 or higher**. Feature, inventory-slot, stack, and server insertion checks still apply. [Operator entry][operator] · [Browser categories][browser] · [Browser permission][permission] · [Mode abilities][modes] · [Server checks][server]
* **Command:** run `//wand` as a player while WorldEdit is initialized and the server recognizes you as an operator. This command uses the server's operator check, including the single-player owner when commands are enabled and the server's allow-commands-for-all setting. It has **no separate Creative-mode requirement or universal numeric level-2 requirement**. [Command registration][command] · [Command permission][command-permission] · [WorldEdit permission][platform] · [Operator rules][op]

**Leave an empty inventory slot before running `//wand`.** It tries to add one wand. With a full inventory in Creative, the underlying inventory code can report success without placing the new wand, so the success message alone does not prove delivery. [Giving the wand][give-wand] · [Inventory behavior][inventory-add]

No bundled crafting, loot, or trade source was found in the reviewed resource and provider search. The command route can supply an operator in Survival, but ordinary Survival browser requests do not pass the server's infinite-materials gate. [Packet gate][packet] · [Inventory browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits)

## Usage

1. Hold the World Edit Wand and **right-click a block** to set **position 1**.
2. Aim at the opposite corner and **left-click to attempt to break that block** to set **position 2**. In Creative, this reaches the selection callback immediately. In Survival, a brief tap may only start mining; selection occurs when the server reaches the block-breaking callback after enough mining progress.
3. Check the first-position and second-position messages before running an editing command. [Wand controls][wand] · [Creative and Survival break paths][break-path] · [Selection feedback][feedback]

Sneak is not a corner-switching control. If a block opens a menu instead of selecting position 1, Sneak-right-click can bypass the block interaction and reach the wand; it still selects **position 1**. An active brush bound to the held item may handle right-click before the wand runs. [Interaction order][use-order] · [Brush handling][brush]

## Behavior

WorldEdit must be initialized, and changing either corner requires its selection permission. The current permission implementation uses the same operator check as `//wand`; simply receiving or holding a wand does not grant it. The item automatically binds its selection tool when its own interaction runs. [Tool binding and initialization][wand] · [Selection permission][selection-tool] · [WorldEdit permission][platform]

The selection callbacks mark positions without placing blocks. The wand cancels normal block destruction when its block-breaking callback is reached. It does not itself fill, replace, clear, or copy the selected area; use the supported editing commands after making a selection. See [Commands](../commands/Commands.md) for more uses. [Wand behavior][wand] · [Server destruction check][destroy-check]

For the checked fill, replace, clipboard and history workflow, follow [WorldEdit basics](../commands/WorldEdit.md). Its history stores block states rather than a full-world backup.

## Selection

Selections belong to the player's WorldEdit session and are kept separately for each world. The default selector needs both corners to define a **cuboid**, including the corner blocks and every block position between them. Either corner can be above or below the other. [World-specific selection][session] · [Two-corner requirement][selector] · [Inclusive bounds][cuboid]

For example, opposite corners at `(0, 64, 0)` and `(2, 65, 3)` define a region **3 blocks wide, 2 high, and 4 long**, containing **24 block positions**. A wall selection has only one block of depth if both corners have the same coordinate on that axis. [Dimensions and volume][cuboid]

## Notes

* Registry ID: `minecraft:wand`; maximum stack size: **1**. The separate Building Wand uses `minecraft:building_wand`. [Registration][registration]
* Use the permission rules for the route you are trying. Operator-category visibility, `//wand` access, and selecting a corner are separate checks.
* If selection does not change, check operator access, the clicked block, and the feedback message. Normal interaction restrictions and the Survival mining delay can prevent the wand callback from being reached. [Selection permission][selection-tool] · [Break path][break-path] · [Use order][use-order]

## Trivia

MattMC includes its own WorldEdit integration and registers its selection commands during server startup. Its active wand control order is the one described above; do not assume the controls of another WorldEdit installation. [Startup][startup] · [Selection registration][worldedit-commands] · [Wand controls][wand]

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The item registration, active interaction and command paths, permission checks, inventory behavior, and region bounds were traced in source. The acquisition search covered bundled data, recipe/loot providers, and villager trades; custom data packs or server changes may add other sources. No running-client or multiplayer test was performed.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2681-L2686
[operator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2148
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[permission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1817
[modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1905
[command]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L31-L35
[command-permission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L271-L282
[platform]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/platform/MattMCPlatform.java#L55-L58
[op]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/players/PlayerList.java#L626-L630
[give-wand]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L89-L103
[inventory-add]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L249-L289
[packet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[wand]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/WandItem.java#L29-L98
[break-path]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L152-L228
[feedback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/region/selector/CuboidRegionSelector.java#L83-L117
[use-order]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L388
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/platform/WorldEditIntegration.java#L120-L141
[selection-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/tool/SelectionWand.java#L31-L77
[destroy-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L247-L259
[session]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/session/LocalSession.java#L13-L58
[selector]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/region/selector/CuboidRegionSelector.java#L30-L53
[cuboid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/region/CuboidRegion.java#L52-L105
[startup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L825-L830
[worldedit-commands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/command/WorldEditCommands.java#L14-L19
