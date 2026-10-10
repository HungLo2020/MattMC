# Redstone Comparator

A Redstone Comparator (`minecraft:comparator`) compares or subtracts signal strengths and reads supported blocks such as storage containers. Use it to detect stored items or preserve a signal's strength instead of restoring every positive signal to 15 as a [Repeater](RedstoneRepeater.md) does.

## Crafting and collecting

Craft one with three Redstone Torches, one **Nether Quartz**, and three **Stone**:

| Left | Center | Right |
| --- | --- | --- |
| Empty | Redstone Torch | Empty |
| Redstone Torch | Nether Quartz | Redstone Torch |
| Stone | Stone | Stone |

The recipe names ordinary Stone and the Nether Quartz item, not Cobblestone or a Quartz Block. A placed Comparator breaks immediately and returns its item in ordinary Survival mining; no correct-tool requirement is set.

## Placement and input directions

Place it on a sturdy upper support face; it breaks if that support is lost. Its output points in the horizontal direction you are looking during placement. The **rear input is toward you**, the **front output points away**, and its two side inputs lie to the left and right.

The rear is the main input. Of the two side signals, only the **stronger** value is used; they are not added together. Side dust supplies its current strength. A powered Repeater or another Comparator can also feed a side. A powered solid block is not automatically equivalent to a direct side-input component.

## Compare and subtract modes

The default mode is **compare**. Interact with the Comparator to switch between compare and subtract.

| Mode | Output |
| --- | --- |
| Compare | Rear strength if it is at least the stronger side strength; otherwise 0 |
| Subtract | Rear strength minus the stronger side strength, with a minimum of 0 |

For a rear input of 10 and side inputs of 6 and 3, compare produces 10 and subtract produces 4. If rear and stronger side are both 10, compare still produces 10 while subtract produces 0. With no side input, either mode passes the rear strength.

Ordinary input changes schedule an update **two game ticks** later: one conventional redstone tick, or 0.1 seconds at the normal 20-game-tick rate. Changing mode refreshes the output immediately in the interaction path. Placement into an already powered input schedules an initial check after one game tick. Those exceptions are why the ordinary delay is not a universal delay for every action.

## Reading containers

Put the Comparator's rear directly against a supported container such as a [Chest](Chest.md) or [Hopper](Hopper.md). Its rear input becomes the container's analog reading. This measures **fullness**, not simply the number of occupied slots or items.

- An empty readable container gives 0
- Any nonempty readable container gives at least 1
- A completely full container gives 15
- Fullness is averaged across all slots, using each stored item's allowed stack size; one non-stackable item fills its slot, while one item from a 64-stack fills only 1/64 of that slot

For a nonempty container, the calculation is 1 plus the rounded-down value of 14 times its average fullness. For example, a container with every slot half filled with ordinary 64-stack items gives 8. A normal double Chest is read as a combined container.

A [Crafter uses a different 0–9 reading](Crafter.md#comparator-signal): it counts slots that are disabled or nonempty, rather than stack fullness. Do not apply the storage-container formula to that block.

**Chest access matters in this implementation.** The Chest reading uses the container-access check: a redstone-conducting block above the lid, or a sitting Cat that blocks access, can make the reading 0 even when items remain inside. Keep the test Chest unobstructed. Container-content changes notify nearby Comparators; they do not require a block-state change of the kind an Observer watches.

A Comparator can also read a supported analog-output block through **one redstone-conducting block** directly behind it, provided the initial rear signal is below 15. This is a conditional path, not permission to read through any wall or number of blocks. Use direct adjacency for a first storage detector.

## Small example: a storage indicator

This layout is source-derived and has **not been tested in game**.

1. Put one Chest on a solid floor, with clear space above its lid.
2. Place a Comparator directly beside it while looking away from the Chest. The Comparator's rear must touch the Chest.
3. Put a Redstone Lamp directly against the Comparator's front and leave both side inputs clear. Keep the default compare mode.
4. Add an item to the Chest. A positive fullness reading should light the Lamp; emptying the Chest should turn the reading off.

The Lamp turns off only after its own four-game-tick off check. Keep it adjacent for this first test: a long dust run can lose a weak fullness signal before reaching the Lamp. Insert a Repeater after the Comparator if you want to carry a simple empty/nonempty result farther; doing so replaces the measured strength with 15.

## Related pages

- [Comparator item](../items/RedstoneComparator.md)
- [Repeater](RedstoneRepeater.md) and [Observer](Observer.md)
- [Redstone basics](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipe, loot, current callbacks, container update notifications, and scheduled-tick dispatch were checked. No in-game container, mode, or timing test was run. Container rules, active data packs, and circuit update order can affect results.

- [Block registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java) and [Comparator block-entity registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java)
- [Recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/comparator.json) and [block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/comparator.json)
- [Mode calculations, analog input, timing, and stored output](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java)
- [Placement, support, and signal directions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DiodeBlock.java) and [side-input rules](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/SignalGetter.java)
- [Container fullness](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java) and [discrete rounding](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/util/Mth.java)
- [Chest analog output, double storage, and lid blocking](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ChestBlock.java) and [Hopper analog output](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/HopperBlock.java)
- [Container change notification](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java) and [nearby Comparator updates](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/Level.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java), [server tick dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java), and [Lamp response](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)
