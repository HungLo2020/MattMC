# Structure and Jigsaw Blocks

Structure Blocks copy named regions; Jigsaw Blocks connect pieces from configured template pools. They are operator/mapmaking tools that can replace world contents. Use a disposable area or a recoverable world copy before loading or generating a layout. [Active structure placement][structure-load] · [Active Jigsaw placement][jigsaw-placement]

## Access, placement, and drops

**`minecraft:structure_block`** and **`minecraft:jigsaw`** both have registered Game Master block items. Operator-category contents in MattMC's [inventory browser](../mechanics/InventoryBrowser.md) require **Creative instant-build and permission level 2 or higher**. Their player-item placement, editor access, editor updates, and ordinary removal enforce that permission; merely possessing an item in Survival does not make it usable. With command permission, an in-game player can obtain the first item with `/give @s minecraft:structure_block 1` or `/give @s minecraft:jigsaw 1`. [Items][items] · [Category][op] · [Permissions][permissions] · [Item placement][gm-item] · [Block interactions][structure] [jigsaw] · [Server updates][structure-server] [jigsaw-server] · [Removal][break] · [Giving][give]

Both are solid, nonluminous blocks with hardness **−1**, explosion resistance **3,600,000**, and no loot table. Ordinary Survival mining, Silk Touch, and Fortune do not supply them; no crafting recipe for either ID was found in the bundled recipe tree. They have no waterlogged state. These editor/action handlers use the Game Master permission check rather than the separate command-block gamerule. [Registrations][structure-reg] · [Physical defaults][defaults] [properties] · [Structure handler][structure-server] · [Jigsaw handlers][jigsaw-server]

## Structure Block

A newly placed Structure Block starts in **Load** mode. Its editor can save a selected region, load a named template, or use Corner blocks to measure the region. Names are resource IDs: use a distinct name such as `example:one_stone` for a small experiment. The offset is relative to the Structure Block; it is not an absolute world coordinate. [Default and controls][structure] · [Name and offset handling][structure-entity] · [Editor][structure-ui]

| Mode/control | Useful behavior |
| --- | --- |
| Save | Captures the offset/size region under the chosen name; the Save button requests a disk save |
| Load | Loads the named template at the selected offset, with optional mirror/rotation |
| Corner | Marks boundaries for a same-name Save block's Detect Size action |
| Data | Stores a marker string for structure-generation code that understands it; this is not a command executor |
| Include Entities | Includes entities in capture/placement when enabled; defaults off |
| Show Bounding Box / Show Invisible Blocks | Visualization aids; inspect the selected region before saving/loading |
| Integrity and Seed | Below integrity 1, randomly omits blocks during loading; 1 retains the template's blocks, and seed 0 chooses a fresh random source |
| Strict | Changes placement/update handling; it does not protect destination blocks from replacement |

The ordinary mode cycle excludes Data; the extended mode values include it. Data behavior belongs to the consuming structure generator, so typing arbitrary text does not create a new effect. [Editor modes and controls][structure-ui] · [Capture][structure-save] · [Load settings][structure-load] · [Random-seed handling][structure-entity] · [Data-marker consumer][data-markers]

### Size, corners, and files

The editor packet clamps each offset to **−48…48**, each size to **0…48**, and integrity to **0…1**. Use positive sizes for an actual region. This describes the normal block-editor path, not an unlimited world-copy tool. Detect Size searches for matching Corner blocks within **80 blocks horizontally** and the world's height range; it measures the interior between extrema, excluding the Corner boundary. With one matching Corner, it also uses the Save block as the other boundary. [Packet bounds][structure-packet] · [Corner scan and interior calculation][structure-corners]

The Save button captures and writes through the world's structure manager. Normal generated files are under the world's `generated/<namespace>/structures/<path>.nbt`; loading also uses the server's template manager, so a file on your client alone is not a shared-server template. Confirm the success/failure message rather than assuming the name exists. [Save path][structure-save] · [Template manager][structure-manager] · [Server feedback][structure-server]

### One-block save/load example

This is a source-based workflow, not a performed action:

1. In an empty Creative test area, place one Stone immediately east of a Structure Block
2. Choose Save, name `example:one_stone`, offset **1, 0, 0**, size **1, 1, 1**, and leave Include Entities off; confirm the box selects only the Stone, then Save
3. Place a second Structure Block in an unused area, choose Load and the same name, and set an offset into empty space; leave rotation/mirror unchanged and integrity at 1
4. Press Load to prepare the size if necessary, inspect the resulting box, then press Load again when it is correct

If the stored size already matches, Load can place immediately. It is not always a harmless preview button. Air in an ordinary saved region can clear existing destination blocks; put [Structure Void](TechnicalBlocks.md#structure-void) in source cells you want omitted. [Size-check/placement path][structure-load] · [Save exclusions][structure-save]

A rising redstone input can trigger a configured block: Save captures **in memory without the disk-save request**, Load places, and Corner removes the cached template entry. Holding a steady signal does not continuously retrigger it. Data has no redstone action here. [Redstone actions][structure]

## Jigsaw Block

A Jigsaw Block is a connector for **existing template-pool data**. Its front points through the clicked face; its top orientation depends on that face and the player's horizontal direction. The state is `orientation`, not a simple command-block arrow state. [Placement and connector compatibility][jigsaw]

| Editor field | What it selects |
| --- | --- |
| Target Pool | Registered pool to use when generating pieces |
| Name | This connector's identifier |
| Target Name | Connector name sought in candidate pieces |
| Turns Into / final state | Block state used when a generated Jigsaw connector is replaced; default is `minecraft:air` |
| Joint | Rollable permits rotation around the connection; Aligned additionally requires matching top directions |
| Selection / Placement Priority | Orders connector selection/placement in the generator; does not guarantee a particular complete layout |
| Levels | Requested expansion depth; the screen slider runs from 0 to 20 |
| Keep Jigsaws | Retains connectors in generated pieces when enabled |

Connections need opposite fronts and matching target/name; Aligned also checks top direction. The Generate action resolves the configured pool, starts in front of the block, and invokes the pool generator. A valid-looking text ID is not proof that the server has that pool or a compatible connector. A default empty pool does not invent a building. [Fields/defaults and active action][jigsaw-entity] · [Screen controls][jigsaw-ui] · [Connection rule][jigsaw] · [Generation path][jigsaw-placement] · [Selection order][jigsaw-order] · [Placement priority][jigsaw-priority] · [Final-state replacement][jigsaw-replace]

For a first inspection, place a Jigsaw in a spare world, read its default empty pool/target, and cancel without generating. To actually build a layout, first identify a loaded pool and its template connector names; use a small depth and Keep Jigsaws in a clear area. Increasing Levels cannot repair a missing pool or mismatched target. This page covers block controls, not authoring a data pack or guaranteeing the result of an arbitrary pool. [Editor][jigsaw-ui] · [Pool lookup][jigsaw-entity]

## Related pages

- [Generated structures](../structures/Structures.md)
- [Structure Void](TechnicalBlocks.md#structure-void)
- [Test Blocks](TestBlocks.md) and [Command Blocks](CommandBlocks.md)
- [Technical-block catalog](catalog/special.md) and [Placed blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on **2026-10-02**. These are source-based instructions, not in-game verification. No game commands, server changes, saves, loads, generation, or tests were executed. Availability and results can change with data packs and server configuration.

[structure-load]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java#L367-L430
[jigsaw-placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L219-L264
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[op]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2160
[permissions]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1818
[gm-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/GameMasterBlockItem.java
[structure]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/StructureBlock.java
[structure-server]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L759-L810
[break]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L300
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java
[structure-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5722-L5731
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L353
[jigsaw-server]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L871-L901
[structure-entity]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java
[structure-ui]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/StructureBlockEditScreen.java
[structure-save]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java#L325-L363
[data-markers]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L84-L120
[structure-packet]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/network/protocol/game/ServerboundSetStructureBlockPacket.java#L75-L100
[structure-corners]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java#L265-L323
[structure-manager]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L330-L399
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/JigsawBlock.java
[jigsaw-entity]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/JigsawBlockEntity.java
[jigsaw-ui]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JigsawBlockEditScreen.java#L99-L181
[jigsaw-order]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L112-L130
[jigsaw-priority]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L344-L358
[jigsaw-replace]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/JigsawReplacementProcessor.java
[properties]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1024
