# Redstone

Redstone components carry signals and control blocks. Start with a **source**, a supported **transmission path**, and a **receiver**: for example, a Lever, a short line of Redstone Dust, and a Redstone Lamp. Keeping those three jobs separate makes it easier to find why a machine does not respond.

Power does not mean the same action for every receiver. A Lamp checks whether any adjacent signal is positive; other components check particular faces, a new activation, or an analog value. Follow the receiver's guide before applying a familiar circuit to it. [Lamp input][lamp] · [Neighbor queries][signals]

## Core components

- [Redstone Torch](../blocks/RedstoneTorch.md) inverts its support-side input; [Redstone Lamp](../blocks/RedstoneLamp.md) shows powered/unpowered states
- [Rails](../blocks/Rails.md) provide propulsion, cart detection, and vehicle-specific activation
- [Pressure plates](../blocks/PressurePlates.md) detect occupancy or entity counts
- [Tripwire](../blocks/Tripwire.md) detects crossings between facing hooks
- [Pistons](../blocks/Pistons.md) move blocks within load and pushability limits
- [Dispenser and Dropper](../blocks/DispenserAndDropper.md) perform item actions or transfer/eject selected items
- [Repeater](../blocks/RedstoneRepeater.md): directional strength restoration, adjustable delay, and locking
- [Comparator](../blocks/RedstoneComparator.md): compare/subtract modes and analog container readings
- [Observer](../blocks/Observer.md): watched-side updates and short pulses

- [Lever](../blocks/Lever.md): toggled, sustained signal
- [Buttons](../blocks/Buttons.md): timed Stone/Oak pulses and arrow differences
- [Redstone Dust and wire](../blocks/RedstoneDust.md): connections and signal loss
- [Hopper](../blocks/Hopper.md): item movement and powered locking
- [Chest](../blocks/Chest.md): a container with analog fullness output

### Inputs and sensing

- [Daylight Detector](../blocks/DaylightDetector.md)
- [Target](../blocks/Target.md)
- [Sculk Sensors](../blocks/SculkSensors.md)
- [Lightning Rods](../blocks/LightningRods.md)

### Storage and crafting

- [Crafter](../blocks/Crafter.md)
- [Trapped Chest](../blocks/TrappedChest.md)
- [Wooden Shelves](../blocks/Shelves.md)

### Outputs and effects

- [Note Block](../blocks/NoteBlock.md)
- [Redstone Randomizer](../blocks/RedstoneRandomizer.md)
- [Copper lighting](../blocks/CopperLighting.md)
- [TNT](../blocks/TNT.md)

## Strength is not duration

Signal strength ranges from **0 to 15**. Zero is unpowered; a positive value can activate a receiver that only asks whether power is present. A Lever and a pressed Button both supply **15**, but the lever holds its state while the button schedules release. Strength measures the signal's level; duration measures how long that level lasts. [Lever][lever] · [Button][button] · [Signal queries][signals]

In the ordinary wire evaluator, a wire gets the strongest eligible incoming wire value **minus one**, floored at zero, unless stronger block power is available. A short line beginning at 15 can therefore continue as 14, 13, and so on. Adding dust spends strength; it does not turn a steady lever signal into a timed pulse. Use a correctly oriented [Repeater](../blocks/RedstoneRepeater.md) before an ordinary dust line fades out when you need strength restoration. [Wire attenuation][attenuation] · [Ordinary evaluator][evaluator] · [Repeater behavior][repeater] · [Directional output][diode]

The component guides use **game ticks**: at the normal 20 ticks per second, 20 game ticks equal one second. One conventional redstone tick equals two game ticks. Scheduled delays are measured in game ticks, so lag or a changed tick rate can alter the elapsed real time. Receivers may add their own timing: a Lamp turns on when it detects power, but schedules a **four-game-tick off check** when power disappears. [Tick rate][ticks] · [Lamp response][lamp]

## Sources, direction, and powered blocks

A source's own output and power passed through its support are separate routes. An on Lever supplies ordinary strength 15 to every adjacent position, plus direct power **into its attachment block**. If that block conducts redstone, a neighboring receiver can read the direct input through it. This is why a switch on one side of a suitable solid block can control a component on another side. [Lever output][lever] · [Physical attachment direction][attachment] · [Conductor queries][signals]

That conductor check only looks for **direct power from the block's immediate neighbors**. It does not recursively carry power along a chain of ordinary solid blocks. A sturdy support face also does not establish that a block conducts redstone; shape and component rules matter. [Direct inputs and conduction][signals] · [Conductor predicate][behavior]

Output direction depends on the source. A [Redstone Torch](../blocks/RedstoneTorch.md) excludes its support side and turns off when its support-side input is powered. A Repeater has a directional input/output axis. Dust supplies horizontal output along its resolved arms and can power the block below; its own signal callback does not power the block immediately above it. Follow the [wire placement and output guide](../blocks/RedstoneDust.md) for corners, dots, and steps. [Torch outputs][torch] · [Wall-torch direction][wall-torch] · [Repeater output][diode] · [Dust output][wire]

## A small lamp circuit

This is a **source-derived starting layout, not an in-game-tested circuit**. Use it in an otherwise empty area with no other power sources or nearby wiring.

1. Make a straight, level strip of full Cobblestone support blocks.
2. On top, place a floor Lever, then two pieces of Redstone Dust, then a Redstone Lamp, in four consecutive positions on the same level. Keep the space above the dust clear.
3. Check that the dust forms a straight connected path from the Lever toward the Lamp, then turn the Lever on.
4. Under the ordinary evaluator, the first dust should receive **15** and the second **14**. The Lamp should light because its adjacent input is positive. Turn the Lever off to remove that input; allow for the Lamp's four-game-tick off check.

The expected result follows from the Lever's adjacent output, dust's support/connection checks and one-step attenuation, and the Lamp's neighbor-power check. The line's endpoint arm continues toward the Lamp when the opposite arm connects to the preceding wire. If it fails, inspect those stages before extending the circuit. [Lever][lever] · [Wire placement and connections][wire] · [Attenuation][attenuation] · [Lamp][lamp]

## First troubleshooting steps

1. Confirm the source actually is powered.
2. Check support and wire connections, including vertical clearance.
3. Shorten the path to rule out signal loss.
4. Verify the receiver responds to the direction and type of signal supplied.
5. Check whether an experimental feature flag changes the wire evaluator before relying on update-order-sensitive designs.

The experimental redstone feature can select a different wire evaluator. This guide describes basic source-derived behavior and the ordinary evaluator's attenuation; it does not certify update-order-sensitive designs or universal compatibility with every vanilla machine or upstream mod circuit. [Evaluator selection][wire]

## Further reading

- [Lever item and recipe](../items/Lever.md)
- [Stone Button](../items/StoneButton.md), [Oak Button](../items/OakButton.md)
- [Redstone Dust acquisition](../items/RedstoneDust.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Active signal queries, interaction/output callbacks, ordinary wire attenuation, evaluator selection, conductor defaults, current Cobblestone properties, and Lamp timing were checked. The example is derived from those paths; no circuit simulation, placement, pulse, or in-game timing test was run.

[lever]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/LeverBlock.java
[button]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/ButtonBlock.java
[signals]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/SignalGetter.java
[wire]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java
[evaluator]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/redstone/DefaultRedstoneWireEvaluator.java
[attenuation]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/redstone/RedstoneWireEvaluator.java
[repeater]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/RepeaterBlock.java
[diode]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/DiodeBlock.java
[ticks]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/TickRateManager.java
[lamp]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java
[attachment]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java
[behavior]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1056
[torch]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/RedstoneTorchBlock.java
[wall-torch]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/RedstoneWallTorchBlock.java
