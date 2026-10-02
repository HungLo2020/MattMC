# Redstone

Redstone components carry signals and control blocks. Start with a source, a supported transmission path, and a receiving component before attempting timing-sensitive machines.

## Core components

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

## Strength is not duration

A Lever and a pressed Button both supply signal 15, but the lever holds its state while the button schedules release. Ordinary wire loses strength as it carries a signal farther. A pulse's duration and a wire's strength are distinct properties.

The button guides use **game ticks**: at normal speed, 20 game ticks equal one second. One conventional redstone tick equals two game ticks. Do not read either as fixed elapsed time during lag; the component pages distinguish ordinary delays from placement and interaction exceptions.

## First troubleshooting steps

1. Confirm the source actually is powered.
2. Check support and wire connections, including vertical clearance.
3. Shorten the path to rule out signal loss.
4. Verify the receiver responds to the direction and type of signal supplied.
5. Check whether an experimental feature flag changes the wire evaluator before relying on update-order-sensitive designs.

A short lever–dust–lamp test is easier to diagnose than a large imported machine. This wiki does not claim universal compatibility with every vanilla machine or upstream mod circuit.

## Further reading

- [Lever item and recipe](../items/Lever.md)
- [Stone Button](../items/StoneButton.md), [Oak Button](../items/OakButton.md)
- [Redstone Dust acquisition](../items/RedstoneDust.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No circuit simulation or in-game timing test was run; feature flags can select different wire evaluators.

- [Signal source example](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/LeverBlock.java)
- [Timed input example](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ButtonBlock.java)
- [Wire evaluator selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java)
- [Container analog output](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ChestBlock.java)
