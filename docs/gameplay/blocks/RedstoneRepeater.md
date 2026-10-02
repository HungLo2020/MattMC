# Redstone Repeater

A Redstone Repeater (`minecraft:repeater`) carries a signal in one horizontal direction, restores a powered output to strength **15**, and adds an adjustable delay. Use one before a [dust line](RedstoneDust.md) fades to zero, or to delay a signal entering another component.

## Crafting and collecting

Craft one with two Redstone Torches, one Redstone Dust, and three **Stone**:

| Left | Center | Right |
| --- | --- | --- |
| Redstone Torch | Redstone Dust | Redstone Torch |
| Stone | Stone | Stone |

The two occupied rows require a Crafting Table. The recipe names ordinary Stone, not Cobblestone, Smooth Stone, or a stone-material tag. A placed Repeater breaks immediately and drops its item during ordinary Survival mining; no correct-tool requirement is set.

## Input, output, and support

Place it on a block with a sturdy upper support face. Removing that support breaks it. Placement points its output in the horizontal direction you are looking: the **rear input is toward you**, and the **front output points away**. It reads the rear signal, not the signal at either side as a normal input.

Any positive rear input can produce output 15 after the delay. This restores signal strength but does not revive a dust line that has already reached zero before the Repeater. It also prevents a signal entering its front from passing backward through the component.

## Delay and pulses

Interact with the Repeater to cycle its four settings. A newly placed Repeater starts at setting 1.

| Setting | Scheduled delay in game ticks | Conventional redstone ticks | Time at 20 game ticks/second |
| --- | --- | --- | --- |
| 1 | 2 | 1 | 0.1 seconds |
| 2 | 4 | 2 | 0.2 seconds |
| 3 | 6 | 3 | 0.3 seconds |
| 4 | 8 | 4 | 0.4 seconds |

One redstone tick means **two game ticks** here. These are ordinary input-change delays, not a guarantee about elapsed wall-clock time under lag or changed tick rates. Placing a Repeater into an already powered input schedules an initial check after one game tick.

For an unlocked Repeater, a detected short pulse can be extended to the selected delay: a queued turn-on still occurs if the rear input has already fallen, followed by a delayed turn-off. Do not assume an arbitrarily brief input will pass through with its original width.

## Locking the current state

Point a powered second Repeater or [Comparator](RedstoneComparator.md) directly into either side of the first Repeater. A positive direct side signal from that component locks the first Repeater's current powered/unpowered state. Changing its rear input while it remains locked does not change its output. Remove the side power to let it respond again.

Side dust or a Lever alone does **not** satisfy this lock check. The check accepts the diode family, which also includes MattMC's Redstone Randomizer when its selected output reaches the Repeater's side. A lock does not copy the side signal into the main output: it holds the existing state.

## Small example: a delayed lamp

This layout is source-derived and has **not been tested in game**.

1. Prepare a flat floor of solid support blocks.
2. Place a Lever, one dust, a Repeater, and a Redstone Lamp in that order along a straight row.
3. Place the Repeater while looking from the Lever end toward the Lamp, so its rear is beside the dust and its front touches the Lamp.
4. Toggle the Lever. The Repeater should pass the input after its selected delay. Try setting 4 to make the delay easier to notice.

Keep both sides of the Repeater clear during this test. The Lamp has its own four-game-tick off check, so its visible shutoff is not a precise measurement of Repeater pulse width.

## Related pages

- [Repeater item](../items/RedstoneRepeater.md)
- [Redstone basics](../redstone/Redstone.md)
- [Comparator](RedstoneComparator.md), [Observer](Observer.md), and [Buttons](Buttons.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipe, drops, current callback signatures, and scheduled-tick dispatch were checked. No circuit simulation or in-game timing/locking test was run. This describes MattMC's checked implementation, not every Minecraft edition or imported circuit.

- [Block registration and mining properties](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/repeater.json) and [block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/repeater.json)
- [Delay settings, interaction, and locking](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RepeaterBlock.java)
- [Placement, support, signal direction, and pulse handling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DiodeBlock.java)
- [Accepted side-input signals](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/SignalGetter.java) and [Randomizer's diode behavior](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java), [server tick dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java), and [block-state dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java)
- [Lamp response](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)
