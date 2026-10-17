# Test Blocks and Test Instance Blocks

**`minecraft:test_block`** supplies test signals and messages. **`minecraft:test_instance_block`** selects and runs a registered GameTest. These are mapmaking/developer tools, not Survival resources or general-purpose workstations. Their editors are reachable in the checked client/server code; this page does not claim a test suite was run. [Block interactions][test] [test-instance] · [Server actions][test-server]

## Access and physical behavior

Both IDs have registered Game Master items in the permission-gated operator contents. MattMC's [inventory browser](../mechanics/InventoryBrowser.md) exposes them only with **Creative instant-build and permission level 2 or higher**. The Test Block category entries carry its four modes; a plain Test Block item defaults to Start. Placement, editing, and ordinary removal also require Game Master permission. An authorized player can use `/give @s minecraft:test_block 1` or `/give @s minecraft:test_instance_block 1`, but giving an item does not bypass these gates. [Items][items] · [Operator variants][op] · [Permissions][permissions] · [Placement][gm-item] · [Removal][break] · [Giving][give] · [Editor/server gates][test-server]

Both have full-block collision, zero emitted light, hardness **−1**, explosion resistance **3,600,000**, and no loot table. The Test Instance Block is nonoccluding and disables the view-blocking predicate, but that does not make it noncolliding. Neither has a waterlogged state or a bundled crafting recipe. They cannot be mined as Survival drops, including with Silk Touch. [Registrations][test-reg] · [Physical defaults][defaults] [properties]

## Test Block

Use a Test Block to choose its `mode` and, for non-Start modes, a message. The editor permits up to **128 characters**. Pick Block preserves the chosen mode on the copied item. Log, Fail, and Accept react to a **rising neighbor-power transition**; keeping a lever on does not continuously create new redstone edges. Start is activated by the test runner rather than this neighbor-input path. [Screen][test-ui] · [State, pick behavior, and input][test] · [Mode identifiers][test-modes]

| Mode | Effect |
| --- | --- |
| Start | The block-based runner activates it; it supplies signal strength 15 while its powered flag is set |
| Log | Logs its nonblank message with mode and position to the server log when triggered |
| Fail | Records a trigger for the block-based runner to report as failure with the configured message |
| Accept | Records a trigger for the block-based runner to recognize as success |

Fail and Accept are not ordinary chat-message blocks. Their meaning is consumed by a running block-based test. Start's reset clears its power, but the checked trigger method does not itself schedule a one-tick reset; do not rely on an undocumented one-tick pulse duration. [Signal value/input][test] · [Trigger, log, and reset][test-entity] · [Runner][test-runner]

A small standalone check is a **Log** block with message `test switch`, activated once by a nearby [Lever](Lever.md). Its useful observable is the server log, not player chat. Turn the lever off before the next activation. This checks the message input path without starting a structure-restoring test run. The example is source-based and was not executed. [Input callback][test] · [Log output][test-entity]

For a registered **block-based** test, the runner requires exactly **one Start** and at least **one Accept** inside the test structure. During its checks, any triggered Accept succeeds; otherwise triggered Fail blocks fail with their message and Log blocks are processed. These checks do not turn a random arrangement of Test Blocks into a registered test. The runner needs a test definition and its structure. [Active block-based runner][test-runner] · [Instance lookup/run][test-instance-entity]

## Test Instance Block

Its editor selects a **test ID**, shows information about that registered test, and offers size, rotation, Include Entities, Reset, Save, and Run. Changing/querying the ID requests its description and, when available, the associated template size. Start with that information before using an action. The bundled test definition `minecraft:always_pass` is a function-based test referencing `minecraft:empty`; it is not an example block-based redstone test. [Editor][test-instance-ui] · [Query handler][test-server] · [Bundled definition][test-data]

| Action | Practical effect and limit |
| --- | --- |
| Run | Requires a registered test and a loadable associated structure; restores the structure, clears the shared running-test ticker/failed-test list, then starts this test |
| Reset | Removes its barriers/error markers and attempts to restore the selected structure; it is not an undo command for an arbitrary build |
| Save | Captures the chosen region to the test's structure name, or a supplied test ID as fallback; this does not register a new executable test definition |
| Export Structure | IDE-mode screen control that saves and converts a structure to a development file; not a general client download button |
| Done / Cancel | Save edited configuration or leave the editor; these are separate from Run |

Each size field is clamped to **1…48** by the screen, and the default region begins at offset **0, 1, 1** from the block. The screen enables Save/Export only with a parseable ID and no rotation; Export is shown only when `IS_RUNNING_IN_IDE` is true. Save/export feedback should be checked rather than taken as proof of a successfully running test. [Action implementation][test-instance-entity] · [Screen gates][test-instance-ui] · [Action dispatch][test-server]

**Run and Reset can replace the selected area and remove non-player entities there.** Structure placement forces relevant chunks loaded and clears the region's scheduled block ticks and block events. Include Entities controls template contents; it is not protection for existing mobs or dropped items in the destination. Running a test also clears the shared test ticker, so avoid launching one over somebody else's active test run. [Restore and run path][test-instance-entity] · [Area clearing][test-clear]

A low-impact first inspection is to place the block in an empty Creative test world, enter `minecraft:always_pass`, inspect its description/size, and Cancel. Only use Run or Reset after accepting the bounded world changes above and verifying the box is clear. Missing-test and missing-structure messages identify different prerequisites; entering an invented ID is insufficient. The normal server tick path advances GameTests when the tick-rate manager runs normally. [Query and actions][test-server] · [Prerequisite errors][test-instance-entity] · [Server ticking][test-ticker]

## Related pages

- [Structure and Jigsaw Blocks](StructureAndJigsawBlocks.md)
- [Command Blocks](CommandBlocks.md)
- [Redstone overview](../redstone/Redstone.md)
- [Development documentation](../../development/home.md) for changing or debugging the implementation
- [Technical-block catalog](catalog/special.md) and [Placed blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on **2026-10-02**. These are source-based instructions, not in-game verification. No game commands, server changes, saves, loads, generation, or tests were executed. Availability and results can change with data packs and server configuration.

[test]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/TestBlock.java
[test-server]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L812-L868
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[op]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2160
[permissions]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1818
[gm-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/GameMasterBlockItem.java
[break]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L300
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java
[test-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5732-L5739
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L353
[test-ui]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/TestBlockEditScreen.java
[test-modes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/properties/TestBlockMode.java
[test-entity]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TestBlockEntity.java
[test-runner]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/gametest/framework/BlockBasedTestInstance.java
[test-instance-entity]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TestInstanceBlockEntity.java
[test-instance-ui]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/TestInstanceBlockEditScreen.java
[test-data]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/test_instance/always_pass.json
[test-clear]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/gametest/framework/StructureUtils.java#L82-L90
[test-ticker]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/MinecraftServer.java#L1263-L1266
[test-instance]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/TestInstanceBlock.java#L28-L43
[properties]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1024
