# Observer

An Observer (`minecraft:observer`) watches the neighboring position at one face and sends a short redstone pulse from the opposite face when it receives a qualifying update there. Use it for a block-change signal; use a [Comparator](RedstoneComparator.md) when you need container fullness.

## Crafting and collecting

Craft one with six **Cobblestone**, two Redstone Dust, and one Nether Quartz:

| Left | Center | Right |
| --- | --- | --- |
| Cobblestone | Cobblestone | Cobblestone |
| Redstone Dust | Redstone Dust | Nether Quartz |
| Cobblestone | Cobblestone | Cobblestone |

These are specific ingredient items, not broad stone tags. Collect a placed Observer with a **pickaxe**. It requires the correct tool for drops and belongs to the pickaxe-mining tag; its ordinary block loot returns one Observer.

## Watching face and output

An Observer can face horizontally, upward, or downward. In the checked placement code, its watching direction matches the nearest direction you are looking. Its output is the **opposite** face. If you look horizontally east when placing it, it watches the block immediately east and sends its pulse west.

This differs from a Repeater's placement relationship: the Observer watches away from you and outputs back toward you. Check the two faces before attaching a circuit. It does not watch every adjacent block, a line of blocks, or entities walking past.

## What causes a pulse?

The active trigger is a **shape update arriving from the watched side** while the Observer is unpowered. Ordinary block placement, removal, and block-state changes can supply those updates. The name “shape update” does not limit this to a visible geometric change; a Lever's powered-state change also uses the relevant block-update path.

This is not a detector for every kind of internal change. Generic redstone neighbor notifications alone are a different callback. Moving items inside a Chest does not, by itself, supply the watched block-state update used here; read that inventory with a Comparator. Do not assume behavior advertised for every Minecraft edition or another implementation carries over.

## Delay and pulse length

Once an eligible watched-side update starts a new pulse:

1. The Observer schedules activation **two game ticks** later.
2. It powers its output at strength **15**.
3. It schedules shutoff **two game ticks** later.

The nominal delay and powered pulse are therefore each **one redstone tick**, or 0.1 seconds at the normal 20-game-tick rate. A redstone tick means two game ticks; lag or a changed tick rate changes elapsed real time.

Updates received while it is already powered do not extend the pulse. Updates while activation is already scheduled do not create another queued activation. Closely spaced changes can therefore be combined or missed as separate pulses; this is not a reliable event counter without additional circuit design.

## Small example: a block-change indicator

This layout is source-derived and has **not been tested in game**.

1. Prepare a level floor. Face east and keep your view mostly horizontal when placing an Observer, so it watches east rather than up or down. Its watched position is directly east of it.
2. Put a Redstone Lamp directly against its west face, the output side. Leave the east position accessible.
3. Place a Cobblestone block in that east position. The watched-side placement update should produce a pulse and a visible Lamp flash.
4. Wait for the Lamp to go dark, then remove the Cobblestone. That removal should produce another pulse.

The Lamp waits four game ticks before its off check, so its visible flash lasts longer than the Observer's two-game-tick powered interval. Keep the detector simple before replacing the watched block with a growing crop or another mechanism whose exact update behavior needs checking.

## Related pages

- [Observer item](../items/Observer.md)
- [Repeater](RedstoneRepeater.md), [Comparator](RedstoneComparator.md), and [Redstone Dust](RedstoneDust.md)
- [Redstone basics](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipe, mining/drop data, the current watched-side callback, and scheduled-tick dispatch were checked. No circuit simulation or in-game detection/timing test was run. In particular, this page does not claim compatibility with every imported Observer farm.

- [Registration and correct-tool requirement](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/observer.json), [pickaxe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json), and [block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/observer.json)
- [Watching direction, placement, activation, and pulse output](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ObserverBlock.java) and [placement look direction](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/context/BlockPlaceContext.java)
- [Block-state changes and shape updates](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/Level.java), [shape-update dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/redstone/NeighborUpdater.java), and [current block callback signatures](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java) and [server tick dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java)
- [Lever block-state change](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/LeverBlock.java), [container-change notification](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java), and [Lamp response](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)
