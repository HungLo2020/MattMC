# Piston and Sticky Piston

A **Piston** (`minecraft:piston`) pushes movable blocks when powered. A **Sticky Piston** (`minecraft:sticky_piston`) can also pull the block in front of its extended head back when retracting. Use them for gates, movable barriers, and mechanisms whose path and load fit the movement rules.

## Crafting and collecting

Craft one ordinary Piston with three accepted Planks, four Cobblestone, one Iron Ingot, and one Redstone Dust:

| Left | Center | Right |
| --- | --- | --- |
| Planks | Planks | Planks |
| Cobblestone | Iron Ingot | Cobblestone |
| Cobblestone | Redstone Dust | Cobblestone |

Each plank must match `minecraft:planks`; Oak is accepted, but the bundled tag does not include Pewen Planks. Cobblestone and Iron Ingot are specific items.

To craft one Sticky Piston, put one [Slimeball](../items/Slimeball.md) directly above one ordinary Piston. This two-slot recipe fits the inventory crafting grid.

A pickaxe is the tagged mining tool for both Pistons, but neither sets a correct-tool requirement for its own ordinary Survival drop. Each returns its matching item. Retract the mechanism and let it settle before collecting the base; the extended head and moving-block stage have separate removal behavior.

## Facing and power

The head can point in any of the six directions. Placement points it **opposite your nearest look direction**, usually toward you for a horizontal placement. Check the head before placing the load.

A sustained input extends the head one block if the movement can succeed. Removing the input retracts it. The ordinary Piston leaves pushed blocks where they ended up; the Sticky Piston attempts to pull the eligible block directly beyond its extended head back by one block.

For a simple control, put a [Lever](Lever.md) on the floor immediately beside the base. The direct-neighbor power check excludes the head-facing side. There are also checks around the position above the base, which can make indirect-power builds depend on whether the Piston receives an update. Begin with direct side or rear power when troubleshooting.

Pistons queue **block events** and recheck power when those events run. They do not use the Dispenser's fixed four-game-tick action delay. Normal moving-block progress advances in half-block steps on game ticks before settling; exact circuit response depends on event and update ordering. A rapid Sticky Piston pulse can take a retraction path that leaves the pushed block behind, so a short pulse is not a guarantee of a complete push-and-pull cycle.

## What can move?

- A movement can include at most **12 moved blocks**. This is the total moving group, including branches attached through Slime or Honey Blocks, not 12 per branch
- Ordinary movable blocks need room at the far end. An immovable obstruction in the push path prevents the extension
- Obsidian, Crying Obsidian, Respawn Anchors, Reinforced Deepslate, and blocks with unbreakable hardness are rejected
- Extended Pistons cannot be moved. Retracted Pistons can be moved when the other rules allow it
- Ordinary block-entity containers such as Chests, Hoppers, Dispensers, and Droppers are not movable storage here
- Some blocks are destroyed instead of moved. Torch and Sugar Cane registrations use this behavior; the destruction path runs their normal resource-drop rules
- [Glazed Terracotta](Terracotta.md#pistons-and-sticky-blocks) is push-only: an ordinary push can move it, but a Sticky Piston cannot directly pull it back

World borders and vertical build limits also restrict movement. Do not use the list as a complete catalog of every integrated block's behavior.

**Slime and Honey Blocks** can attach eligible neighboring blocks to the moving group. Slime and Honey do not stick to each other. A neighboring support or wall may add an unwanted load, and all attached moved blocks count toward the same 12-block limit.

## Small example: a retractable block

This layout is source-derived and has **not been tested in game**.

1. Place a Sticky Piston horizontally on a flat floor with its head facing a clear two-block lane.
2. Put one Cobblestone immediately in front of the head. Leave the next position empty.
3. Place a floor-mounted Lever directly beside the Piston's base.
4. Turn the Lever on and allow the movement to finish. The Cobblestone should move one block farther along the lane.
5. Turn the Lever off after it settles. The Sticky Piston should return the Cobblestone to its starting position.

Repeat with an ordinary Piston to see the pushed block remain at the farther position. Keep this first test free of Slime/Honey branches and rapid clocks.

## Settling and saved state

Moved blocks recheck their support when they settle; a block that cannot survive at its destination can break. The normal completion path also clears a moved block's `waterlogged` state, so do not assume a waterlogged build retains that water after movement.

The moving-block stage saves the moved block state, direction, progress, and extension/retraction state. That is source evidence for saved motion state, not a verified crash-recovery or chunk-reload guarantee. These mechanics do not turn an immovable container into portable filled storage.

## Related pages

- [Piston item](../items/Piston.md) and [Sticky Piston item](../items/StickyPiston.md)
- [Dispenser and Dropper](DispenserAndDropper.md)
- [Redstone basics](../redstone/Redstone.md), [Repeater](RedstoneRepeater.md), and [Observer](Observer.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipes, loot, current power callbacks, block-event dispatch, and moving-block tick/save paths were checked. No in-game movement, pulse, waterlogging, or save/reload test was run. These rules describe this MattMC snapshot, not every Minecraft edition or imported machine.

- [Block registration, mining properties, and push reactions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Piston recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/piston.json)
- [Sticky Piston recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/sticky_piston.json)
- [Accepted planks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/planks.json)
- [Tagged mining tools](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Piston loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/piston.json)
- [Sticky Piston loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/sticky_piston.json)
- [Placement, power, events, pushability, and sticky retraction](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java)
- [12-block limit and sticky branches](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java)
- [Block-event queue and dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java)
- [Moving-stage ticker and removal behavior](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/MovingPistonBlock.java)
- [Motion, settling, waterlogging, and saved state](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java)
- [Active block-entity tick dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java)
