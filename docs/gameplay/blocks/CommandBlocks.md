# Command Blocks

Command Blocks store a server command and run it from a placed block. They are mapmaking/operator tools, not crafted redstone resources. A button or lever can trigger a configured block, but editing or placing its item requires the permissions below. For command syntax beyond these controls, use [Commands](../commands/Commands.md).

## Access and server enable gate

The three registered block items are `minecraft:command_block`, `minecraft:repeating_command_block`, and `minecraft:chain_command_block`. All appear in operator-category contents when the player has **Creative instant-build and permission level 2 or higher**. MattMC's current [inventory browser](../mechanics/InventoryBrowser.md) reads that gate. Their Game Master item class refuses ordinary player placement without it; opening their editor and ordinary removal also check Game Master permission. [Items][items] · [Operator contents][op] · [Browser][jei] · [Permission predicate][permissions] · [Placement][gm-item] · [Editor][command] · [Removal][break]

An authorized in-game player can obtain one with `/give @s minecraft:command_block 1`. Giving an item does not remove its placement/editor restrictions. No recipe was found for these three IDs in the bundled recipe tree. [Give command][give] · [Placement gate][gm-item]

**MattMC uses the `commandBlocksEnabled` gamerule**, registered with default **true**. `/gamerule commandBlocksEnabled` queries the current value. The server consults it both when accepting editor updates and when dispatching ordinary stored commands. A false value can produce the disabled-command-block message when saving. Do not diagnose this build using an assumed upstream `enable-command-block` property. Changing a gamerule affects the shared server; query it before asking its owner to change it. [Server gate][command-gate] · [Default][command-rule] · [Editor update][command-save] · [Execution gate][command-run]

## Impulse command block

**`minecraft:command_block`** is the Impulse form. With **Needs Redstone**, a change from unpowered to powered schedules one execution for the next game tick. Holding power on does not repeatedly trigger it. **Always Active** bypasses the power requirement and enabling that setting can schedule an execution; it does not turn an Impulse block into a repeating clock. New plain Impulse items start nonautomatic. [Power handling][command] · [Automatic scheduling][command-entity]

## Repeating command block

**`minecraft:repeating_command_block`** runs on successive scheduled game ticks while powered or set to Always Active. It does not depend on repeatedly toggling a button. A stopped, frozen, lagging, or unloaded world does not promise a fixed number of executions per real-world second. New plain Repeating items start nonautomatic, so choose the trigger setting deliberately. [Tick scheduling][command] · [Defaults and mode changes][command-entity] · [Registration][commands-reg]

## Chain command block

**`minecraft:chain_command_block`** runs when reached by the arrow direction of an executing Impulse, Repeating, or prior Chain block. The next block must be another Chain block; its arrow then supplies the next direction. A Chain block still needs power or Always Active to execute its stored command. New plain Chain items start automatic. Powering a Chain block by itself does not start this traversal. [Traversal and power checks][command] · [Automatic registration][commands-reg]

Traversal stops at a non-Chain block, an execution guard, or the **`maxCommandChainLength`** limit (default **65,536**). This is a finite safety limit, not a recommendation to build a chain that long. Stored command execution also normally guards against running the same block twice in one game tick. [Traversal][command] · [Rule default][chain-limit] · [Execution guard][command-run]

## Facing, condition, and feedback

The placed arrow faces opposite the nearest direction the player is looking. Blocks expose `facing` and `conditional` states; the editor can switch among Impulse, Repeating, and Chain while retaining facing. Point arrows along the intended chain before connecting power. [Placement][command] · [Mode update][command-save]

**Conditional** checks the command block immediately **behind this block**, opposite its own arrow, and requires that block's success count to be positive. It is not a test that this block receives redstone power, and around a corner the block behind may differ from the preceding chain step. Unconditional removes this success check. [Condition evaluation][command-entity]

A Comparator reads the stored command's success count. The editor's output toggle controls whether the latest output is retained; `sendCommandFeedback` and `commandBlockOutput` also influence feedback/admin messages. A silent output box alone does not prove a block failed to execute. [Comparator output][command] · [Output toggle][command-ui] · [Feedback rules][command-feedback]

Commands run at the block's center with **permission level 2** and **no executing entity**. `@s` therefore does not automatically mean the player who placed or pressed it. Higher-permission commands are not unlocked simply by putting them in a block. [Command source][command-entity]

## Small read-only command example

In a spare Creative test area, place one Impulse block with **Unconditional**, **Needs Redstone**, and output tracking enabled. Enter `time query daytime`, save, and pulse it once with a [Button](Buttons.md). The command reads daytime without changing it. Reopen the editor to inspect previous output when feedback is enabled. This demonstrates the trigger and output path without spawning entities or editing terrain. [Time query][time] · [Editor][command-ui] · [Trigger][command]

If it fails, check Creative/operator permission, the gamerule, the saved command, a fresh power transition, and feedback settings. For a chain experiment, add just one Always Active Chain block in the first block's arrow direction with the same query before increasing complexity.

## Physical behavior and drops

All three forms have full-block collision, zero emitted light, hardness **−1**, explosion resistance **3,600,000**, and no loot table. Ordinary Survival mining makes no progress and neither Silk Touch nor Fortune supplies them. They have no waterlogged state. Their redstone use does not make them a Survival crafting ingredient. [Impulse registration][command-reg] · [Other forms][commands-reg] · [Physical defaults][defaults] [properties]

## Related pages

- [Redstone overview](../redstone/Redstone.md) and [Comparators](RedstoneComparator.md)
- [Structure and Jigsaw Blocks](StructureAndJigsawBlocks.md)
- [Technical-block catalog](catalog/special.md) and [Placed blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on **2026-10-02**. These are source-based instructions, not in-game verification. No game commands, server changes, saves, loads, generation, or tests were executed. Availability and results can change with data packs and server configuration.

[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[op]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2160
[jei]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L109
[permissions]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1818
[gm-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/GameMasterBlockItem.java
[command]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/CommandBlock.java
[break]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L300
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java
[command-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/MinecraftServer.java#L1521-L1523
[command-rule]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L238-L240
[command-save]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L589-L641
[command-run]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/BaseCommandBlock.java#L95-L139
[command-entity]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/CommandBlockEntity.java
[commands-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4297-L4306
[chain-limit]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L121-L126
[command-ui]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/CommandBlockEditScreen.java
[command-feedback]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/BaseCommandBlock.java#L189-L220
[time]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/TimeCommand.java#L12-L61
[command-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2635-L2639
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L353
[properties]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1024
