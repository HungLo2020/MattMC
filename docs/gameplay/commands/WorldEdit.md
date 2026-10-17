# WorldEdit basics

MattMC's built-in WorldEdit can fill a selected box, replace blocks, copy a build, and undo recent edits. Start with a small, disposable area. Editing commands act immediately, without a confirmation step or automatic rollback. Before important local-world changes, follow [Local worlds, saving, and backups](../mechanics/LocalWorlds.md#back-up-a-closed-world). [Set and replace][edits] · [Paste][paste]

## Access and command spelling

Run these commands **as a player**, with WorldEdit initialized and the server recognizing you as an operator. That check includes the single-player owner when commands are allowed and the server's allow-commands-for-all setting. There is no separate Creative requirement or universal numeric level-2 gate. A plain server console is not a player. [Player gate][selection-gate] · [Permission implementation][permission] · [Operator rules][op]

Type the **two leading slashes**, for example `//size`. Chat removes one slash before reaching the registered command. The server registers these command groups during startup. [Chat handling][chat] · [Startup][startup] · [Command groups][groups]

## 1. Select a small box

Use the [World Edit Wand](../items/WorldEditWand.md#usage) for block-click controls, or set corners from where you stand:

1. Stand at the first corner position and enter `//pos1`
2. Move to the opposite corner and enter `//pos2`
3. Run `//size` to read the bounds, dimensions, and total block positions before editing

`//pos1` and `//pos2` use your current block position; they take **no coordinate arguments**. Both corners are required. The selection is a cuboid containing both corner positions and everything between them, including air. For example, corners three blocks apart along X give a width of four blocks. [Position commands][positions] · [Syntax][selection-syntax] · [Complete selection][complete] · [Inclusive bounds][bounds]

`//sel` reports the same information as `//size`; it does not choose a shape. `//desel` clears both corners in your current dimension without changing blocks, clipboard, or history. Selections are separate for each dimension. [Selection display and clearing][selection-info] · [Size alias][size] · [Corner reset][clear-selection] · [Session selections][session]

## 2. Fill and replace

With both corners selected, try one operation at a time and inspect the result:

| Command | Result within the selection |
| --- | --- |
| `//set minecraft:stone` | Fill eligible positions with Stone, including positions that were air |
| `//replace stone dirt` | Replace Stone with Dirt |
| `//replace dirt` | Replace every non-air block with Dirt |
| `//set oak_stairs[facing=east,half=top]` | Place the specified stair state |
| `//set stone,dirt` | Choose randomly between Stone and Dirt at each position |
| `//set 70%stone,30%dirt` | Choose with relative weights of 70 and 30 |

A pattern describes the output. Weights must be positive and finite; they need not total 100 and do not promise exact proportions. Unweighted entries have weight 1. A lone weighted entry such as `70%stone` is rejected. [Set/replace behavior][edits] · [Non-air matching][non-air] · [Pattern parser][patterns] · [Random choice][random]

A mask filters existing blocks. The first part of `//replace stone,dirt grass_block` matches either input type. Bare `oak_stairs` matches all stair states; a bracketed mask matches the **whole parsed state**, with unspecified properties taking defaults rather than acting as wildcards. Masks do not accept weights or upstream tag/special-mask syntax. Keep masks compact, without spaces inside lists or brackets. [Replacement split][replacement] · [Mask parser][mask-parser] · [Type matching][block-types] · [State matching][exact-states] · [State defaults][state-defaults] · [Mask token][mask-token]

`//gmask stone,dirt` adds a persistent destination filter to subsequent set, replace, and paste operations. Replace must satisfy both its input mask and this filter. **Enter `//gmask` with no argument to clear it**, especially when an edit unexpectedly changes nothing. The filter survives dimension travel and does not restrict copying. `/gmask` is also an alias. [Mask command][gmask] · [Applying the filter][edit-session] · [Filtered writes][writes] · [Copy][copy] · [Dimension travel][dimension-travel]

## 3. Copy and place a build

Select a small build made from ordinary blocks, stand at a memorable reference position, and enter `//copy`. This replaces your clipboard with every selected block state, including air. Your **position when copying is the anchor**, even if you stand outside the selection. Copying does not change the world or add an undo entry. [Copy operation][copy] · [Clipboard contents][clipboard]

Move to the intended destination. A plain `//paste` places the build relative to your new block position, without requiring or changing the current selection. For a fresh copy, moving the anchor ten blocks east moves every copied block ten blocks east. **Default paste includes air**, so empty space in the copied box can erase destination blocks. [Paste positioning][paste-options] · [Placement][paste]

| Option | What it does |
| --- | --- |
| `//paste -n` | Set the selection to the calculated destination box, without pasting or adding history; use `//size` to inspect it |
| `//paste -a` | Skip copied `minecraft:air`; cave air and void air are not covered by this particular check |
| `//paste -s` | Set that destination selection and paste |
| `//paste -o` | Use the copy-time anchor coordinates **in your current dimension** |

`-n` is a selection-only operation, not a rendered ghost preview or a reserved placement. Stay at the same anchor before the real paste. The selected box includes all clipboard entries, even if air skipping, masks, limits, or placement failures reduce the actual changes. Flags can be combined, such as `//paste -as`. Unknown flags and positional arguments are rejected. [Option handling and bounds][paste-options] · [Paste loop][paste] · [Flag parser][flags]

The accepted `-e`/`-b` copy flags and `-v`/`-e`/`-b` paste flags only warn; they add no entity or biome support, and `-v` has no extra behavior. The clipboard stores **block states only**: no container contents, sign text, other block-entity NBT, entities, biomes, or player inventory. NBT text accepted in a block pattern is discarded. This is not a world snapshot. [Copy warnings][copy] · [Paste warnings][paste-options] · [Stored data][clipboard] · [Pattern result][pattern-state]

## 4. Undo and redo carefully

`//undo` replays one remembered edit backward; `//redo` reapplies one undone edit. Neither accepts a count or another player's name. History belongs to you, with up to **15 edit entries**. Set, replace, and ordinary paste consume entries even when they change zero blocks. A new remembered edit discards the redo tail; older entries are eventually dropped. `//clearhistory` immediately empties undo and redo history without reverting blocks or clearing the clipboard. [History commands][history-commands] · [History storage][history]

History is chronological across dimensions. Undo/redo acts in the **original edit's dimension**, even after you travel elsewhere. It bypasses the edit's mask and counting limit and can overwrite later changes by another player. It restores recorded before/after block states, not inventories, item drops, entities, or collateral physics/block-update effects. [Original world and writes][writes] · [Replay][replay] · [Recorded states][recorded]

Disconnecting loses your selection, clipboard, mask, and history; reconnecting starts fresh. Restarting the server or closing and reopening a local world also loses them. Session-saving comments and configuration names do not provide implemented disk persistence. [Disconnect caller][disconnect] · [Disconnect handler][disconnect-handler] · [Session removal][sessions] · [Shutdown caller][shutdown] · [WorldEdit shutdown][shutdown-handler]

## Size and verification limits

The active limit is **1,000,000 counted changes per edit**. A large operation can stop partway through without rolling back or giving a special limit warning. Identical states and masked-out positions do not consume the count; selection volume is not a preflight limit check. Copying has no matching size cap. Reported change counts ignore whether the world's block-placement call succeeded, so they do not guarantee complete placement, particularly at world bounds. Keep selections small and inspect the result. [Active defaults][session] · [Edit creation][edit-session] · [Counted writes][writes] · [Set traversal][set-loop] · [Replace traversal][replace-loop] · [World placement][world-write] · [Copy loop][copy]

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`, following active registration, parsers, consumers, and session lifecycle. No game commands, world edits, or runtime tests were performed. This guide covers only the workflow above; syntax from other WorldEdit installations should not be assumed compatible. [Region gate][region-gate] · [Clipboard gate][clipboard-gate] · [History gate][history-gate] · [Global-mask gate][general-gate]

The title-menu [Region Editor](../mechanics/RegionEditor.md) operates on saved terrain with its own clipboard and limited undo.

Related: [Commands](Commands.md) · [World Edit Wand](../items/WorldEditWand.md) · [Local worlds and backups](../mechanics/LocalWorlds.md) · [Gameplay](../Gameplay.md)

[edits]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/RegionCommands.java#L116-L178
[paste]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/ClipboardCommands.java#L281-L319
[selection-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L271-L282
[permission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/platform/MattMCPlatform.java#L55-L58
[op]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L626-L630
[chat]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/ChatScreen.java#L257-L267
[startup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L814-L831
[groups]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/WorldEditCommands.java#L14-L28
[positions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L109-L141
[selection-syntax]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L31-L82
[complete]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/region/selector/CuboidRegionSelector.java#L49-L53
[bounds]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/region/CuboidRegion.java#L52-L105
[selection-info]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L179-L216
[size]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/SelectionCommands.java#L264-L265
[clear-selection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/region/selector/CuboidRegionSelector.java#L76-L80
[session]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/session/LocalSession.java#L18-L85
[non-air]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/mask/ExistingBlockMask.java#L19-L21
[patterns]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/pattern/BlockPatternParser.java#L25-L155
[random]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/pattern/RandomPattern.java#L50-L66
[replacement]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/argument/WorldEditReplacementArgument.java#L61-L79
[mask-parser]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/pattern/BlockPatternParser.java#L38-L84
[state-defaults]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/commands/arguments/blocks/BlockStateParser.java#L352-L365
[mask-token]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/argument/WorldEditMaskArgument.java#L37-L59
[gmask]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/GeneralCommands.java#L21-L47
[edit-session]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/session/LocalSession.java#L162-L183
[writes]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/core/EditSession.java#L19-L75
[copy]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/ClipboardCommands.java#L97-L139
[clipboard]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/clipboard/Clipboard.java#L13-L60
[paste-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/ClipboardCommands.java#L204-L279
[flags]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/ClipboardCommands.java#L431-L453
[pattern-state]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/pattern/BlockPatternParser.java#L86-L101
[history-commands]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/HistoryCommands.java#L23-L96
[history]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/session/LocalSession.java#L210-L261
[replay]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/core/EditSession.java#L305-L329
[recorded]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/history/ArrayListHistory.java#L15-L47
[disconnect]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1349-L1354
[disconnect-handler]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/platform/WorldEditIntegration.java#L159-L165
[sessions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/session/SessionManager.java#L15-L56
[shutdown]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L718-L725
[shutdown-handler]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/core/WorldEdit.java#L56-L63
[set-loop]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/core/EditSession.java#L97-L109
[replace-loop]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/core/EditSession.java#L159-L173
[world-write]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/Level.java#L202-L249
[region-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/RegionCommands.java#L352-L363
[clipboard-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/ClipboardCommands.java#L405-L416
[history-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/HistoryCommands.java#L102-L113
[general-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/command/GeneralCommands.java#L50-L61
[block-types]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/mask/BlockTypeMask.java#L19-L25
[exact-states]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/worldedit/mask/BlockSetMask.java#L19-L26
[dimension-travel]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1072-L1130
