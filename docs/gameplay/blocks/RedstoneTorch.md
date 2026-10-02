# Redstone Torch

A **Redstone Torch** supplies redstone power while lit and turns off when its supporting block supplies power to it. This inversion makes it useful for a simple “input off, output on” control. It is a circuit component with light level **7 while lit**, not the brighter ordinary [Torch](Torch.md). [Torch behavior][torch] · [Registration and light][blocks]

## Crafting and collection

Place **one Redstone Dust directly above one Stick** to craft **one Redstone Torch**. The two-slot vertical recipe fits the inventory crafting grid. It is also an ingredient in devices such as [Repeaters](RedstoneRepeater.md) and [Comparators](RedstoneComparator.md). [Recipe][recipe]

The same item places a standing torch on suitable upper support or a wall torch on a sturdy horizontal face. It cannot hang below a ceiling. Standing and wall blocks have separate IDs, `minecraft:redstone_torch` and `minecraft:redstone_wall_torch`, but share the Redstone Torch item and loot. Losing valid support removes the placed torch. [Item placement mapping][item] · [Standing support][support] · [Wall support][wall-support] · [Wall state][wall] · [Shared wall properties][wall-properties]

Ordinary Survival breaking returns **one Redstone Torch**, without needing Silk Touch or a tool tier. Explosion recovery is conditional. The torch breaks instantly and has no collision. [Properties][blocks] · [Loot][loot]

## Input and output

A standing torch reads power from the block **underneath it**; a wall torch reads from the block **behind its attachment**. Powering that support can turn the torch off. A nearby powered component is not automatically an input unless its wiring causes the support-side check to receive power. [Standing input][torch] · [Wall input][wall]

While lit, it supplies signal strength **15** toward adjacent positions except its attachment/support side. It also supplies direct power to the block above it. When off, these outputs are zero. Signal direction is defined by the recipient's query, so use the physical support/output distinction when laying out a circuit. [Outputs][torch] · [Wall output][wall] · [Neighbor query directions][signals]

A relevant neighbor update schedules an ordinary state recheck after **two game ticks**, one conventional redstone tick. The recheck reads the current input; this is not a guarantee that every arbitrary rapid pulse is reproduced at the output. [State scheduling and tick][torch]

## Rapid switching and burnout

The implementation records **lit-to-unlit transitions** at the torch's position. Eight such transitions within the retained **60-game-tick window** can cause burnout. The torch stays off and schedules a restart check **160 game ticks** later, about eight seconds at 20 TPS. [Toggle record and restart][torch]

The 160-tick value is a scheduled restart check, not a separate unconditional lockout timer. An actual earlier tick can recheck the torch, but scheduled ticks for the same block and position are deduplicated: changing the input does not guarantee that a pending restart moves earlier. Relighting requires an unpowered input and fewer than eight retained off transitions; tick processing removes records more than 60 game ticks old. Slow the input or break the feedback loop if a torch circuit repeatedly burns out. This page does not prescribe a tested maximum clock frequency. [Burnout and relight paths][torch] · [Pending-tick handling][tick-queue]

## Small example: an inverted lamp

This layout is source-derived and has **not been tested in game**.

1. Place a Cobblestone support block, attach a Lever to its west face, and attach a Redstone Torch to its east face.
2. Put a [Redstone Lamp](RedstoneLamp.md) immediately east of the torch, continuing away from the support. The Lever, support, torch, and Lamp now occupy a straight row.
3. With the Lever off, the torch should light and power the Lamp.
4. Turn the Lever on to power the support. After the torch's recheck, the torch should go out; the Lamp has a separate delayed switch-off.

Keep the output separate from the input support when troubleshooting, and avoid a feedback loop for this first example.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, recipes, loot, standing/wall placement, physical signal-query direction, active neighbor/tick callbacks, and burnout bookkeeping were checked. No crafting, placement, circuit, pulse, burnout, or lighting gameplay test was run.

Related: [Redstone Torch item](../items/RedstoneTorch.md) · [Redstone Lamp](RedstoneLamp.md) · [Redstone](../redstone/Redstone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/redstone_torch.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/redstone_torch.json
[signals]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/SignalGetter.java
[torch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneTorchBlock.java
[tick-queue]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/ticks/LevelChunkTicks.java
[wall]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneWallTorchBlock.java
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L995-L998
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BaseTorchBlock.java
[wall-support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/WallTorchBlock.java
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7274
