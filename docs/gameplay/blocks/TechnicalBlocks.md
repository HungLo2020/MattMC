# Air, Barriers, Light, and Structure Void

These invisible blocks do different jobs: Air is empty space, Barriers stop movement, Light adds illumination, and Structure Void leaves holes in a saved template. Choose by behavior rather than by visibility. See [Command Blocks](CommandBlocks.md), [Structure and Jigsaw Blocks](StructureAndJigsawBlocks.md), and [Test Blocks](TestBlocks.md) for tools with editors.

## Access and inventory forms

Barrier, Light, and Structure Void each have a registered inventory item. They are included in **operator-category contents** only when the player has Creative instant-build and permission level **2 or higher**. MattMC's current [inventory browser](../mechanics/InventoryBrowser.md) builds these contents from that permission check. [Items][items] · [Operator contents][op] · [Permission check][permissions] · [Active browser][jei] · [Inventory screen][inventory]

With command permission, `/give @s minecraft:barrier 1`, `/give @s minecraft:light 1`, or `/give @s minecraft:structure_void 1` supplies the corresponding item. These examples are for an in-game player, and are not actions performed by this page. These three ordinary block items do **not** use the extra placement gate of a Game Master block item: if supplied to a Survival player, their item placement is not blocked solely for lacking Creative/operator status. Light-level editing has its own stricter check below. No crafting recipe for these IDs was found in the bundled recipe tree. [Giving][give] · [Item registrations][items] · [Game Master placement gate for comparison][gm-item]

`minecraft:air` is registered as an internal **AirItem**, which extends Item rather than BlockItem; an Air stack is treated as empty. **Cave Air and Void Air have no separately registered inventory items.** Do not describe these as three obtainable or placeable inventory blocks. [Registrations][items] · [AirItem][air-item] · [Empty stacks][empty]

## Air

**`minecraft:air`** has no visible model, outline, collision, loot table, or emitted light. It is replaceable empty space, so building into it does not require mining it first. Its registration uses the air flag. [Registration][air-reg] · [Shape][air] · [Zero-light default][properties]

## Cave Air

**`minecraft:cave_air`** has the same basic empty, replaceable, noncolliding, nonluminous behavior as ordinary Air. The separate ID lets generation represent empty space with a different block identity. One verified use is the Nether carver, which writes Cave Air above its lower lava cutoff. This does not mean every cave or every empty block uses Cave Air. [Registration][air-reg2] · [Air behavior][air] · [Nether carving][nether-carver]

## Void Air

**`minecraft:void_air`** shares the Air behavior. The level's block lookup returns it outside build height, and an empty-chunk implementation also returns it. It is a technical empty-space result, not a solid floor or protection against falling beyond the world. [Registration][air-reg2] · [Height-boundary lookup][void-read] · [Empty chunk][empty-chunk]

## Barrier

**`minecraft:barrier`** is invisible but has **full-block collision**. Use it for map boundaries where a visible wall would be distracting. It emits no light and does not visually occlude neighboring blocks. A dry Barrier lets skylight pass; waterlogging changes its fluid/light behavior. It has hardness **−1**, very high explosion resistance, no loot table, and blocks piston pushing. Ordinary Survival mining makes no progress, regardless of tool or enchantment. [Registration][barrier-light-reg] · [Shape and mining defaults][defaults] · [Barrier behavior][barrier] · [Light default][properties]

In Creative, hold a Barrier in your **main hand** to show nearby Barrier marker particles. Markers depend on nearby client animation sampling; they are not a permanent visible wall. Creative removal is available through ordinary block breaking, subject to world restrictions. [Marker path][markers] · [Breaking][break]

Barrier's `waterlogged` state defaults to false and becomes true when placed into Water. Its bucket fill and pickup callbacks require a **Creative player**, even though placing the supplied Barrier item itself has no Game Master item gate. [Water behavior][barrier] · [Item class][items]

## Light

**`minecraft:light`** is invisible and noncolliding, with emitted light controlled by **`level=0` through `level=15`**. The normal item starts at **15**; the operator category supplies all 16 levels. Light level 0 remains a Light block but emits nothing. It is replaceable, has hardness −1, and has no loot table. Holding a Light item makes its selection outline available; collision still uses the empty default-context shape. [Registration][barrier-light-reg] · [Level, outline, and clone behavior][light] · [Collision dispatch][defaults] · [Item default][items] · [Operator variants][op]

Hold Light in your main hand in Creative to see nearby Light markers. With Creative instant-build and permission level 2, use the targeted block to cycle its level upward, wrapping 15 to 0. Pick Block preserves its current level on the copied item. It also supports a `waterlogged` state and the normal waterlogged bucket interface. Unlike Barrier, Light has no placement override that automatically reads existing Water; inspect/set the desired state instead of assuming placing it into Water preserves the source. [Controls][light] · [Markers][markers] · [Permissions][permissions]

A small lighting example: in a spare Creative build, choose a level-15 Light, place it in an unobstructed recess, and put the item away to hide the marker. Keep track of the coordinate so you can target it again. A luminous block affects local block light; this is not a promise of daylight or a fixed illuminated radius through solid walls.

## Structure Void

**`minecraft:structure_void`** is invisible, replaceable, noncolliding, and emits no light. It has a small centered selection outline, zero default hardness, and no loot table. It can therefore be removed without recovering an item; it is not an unbreakable Barrier. [Registration][void-reg] · [Outline][structure-void] · [Defaults][properties] · [No-collision configuration][no-collision]

Its useful purpose is **template omission**: the Structure Block save path excludes Structure Void positions. When that saved template is later placed, those omitted cells do not replace the destination. Ordinary Air cells are captured by this save path and can clear destination blocks. Use Structure Void in cells you intend a template to leave untouched, then follow [the save/load workflow](StructureAndJigsawBlocks.md#structure-block). [Save exclusions][structure-save] · [Region capture][template-capture] · [Load path][structure-load]

## Related pages

- [Bedrock](Bedrock.md) for a visible solid boundary
- [Placed blocks](Blocks.md) and [technical-block catalog](catalog/special.md)
- [Commands and permissions](../commands/Commands.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on **2026-10-02**. These are source-based instructions, not in-game verification. No game commands, server changes, saves, loads, generation, or tests were executed. Availability and results can change with data packs and server configuration.

[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[op]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2130-L2160
[permissions]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1818
[jei]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L109
[inventory]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/InventoryScreen.java#L26-L46
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java
[gm-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/GameMasterBlockItem.java
[air-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/AirItem.java
[empty]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L297-L303
[air-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L72
[air]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/AirBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1024
[air-reg2]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5222-L5223
[nether-carver]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/carver/NetherWorldCarver.java#L38-L61
[void-read]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/Level.java#L337-L345
[empty-chunk]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/EmptyLevelChunk.java#L19-L32
[barrier-light-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L3121-L3144
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L353
[barrier]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BarrierBlock.java
[markers]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L495-L527
[break]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L300
[light]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/LightBlock.java
[void-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4364-L4368
[structure-void]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/StructureVoidBlock.java
[no-collision]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1107-L1115
[structure-save]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java#L325-L363
[template-capture]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L96-L130
[structure-load]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/StructureBlockEntity.java#L367-L430
