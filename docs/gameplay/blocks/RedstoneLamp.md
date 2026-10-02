# Redstone Lamp

A **Redstone Lamp** (`minecraft:redstone_lamp`) emits light level **15 while lit** and **0 while unlit**. Any positive neighboring signal can light it; a stronger signal does not make it brighter. Use a Lamp as a visible on/off indicator, not as a display of exact redstone strength. [Registration][blocks] · [Active lamp behavior][lamp] · [Power query][signals]

## Crafting and collecting

Put **one Glowstone block** in the center of a Crafting Table, with **four Redstone Dust** directly above, below, left, and right. Leave the corners empty. This creates **one Redstone Lamp**. Glowstone Dust is not the center ingredient. [Recipe][recipe]

Ordinary Survival breaking returns **one Lamp**, including when it is lit. Silk Touch and a specific tool tier are not required. Its registered hardness is 0.3; explosion recovery has a survival condition rather than a guarantee. Unlike breaking a Glowstone block, breaking this Lamp does not return Glowstone Dust. [Properties][blocks] · [Loot][loot]

## Placement and power

Place it as a full block. It does not need continuing support underneath, and its initial lit state is based on power at the placed position. A Lever on the Lamp, a neighboring powered component, or suitable redstone wiring can control it. [Placement and updates][lamp] · [Default survival][default]

The Lamp is a receiver, not a signal generator merely because it emits light. Its ordinary solid-block wiring behavior can still matter in a circuit; do not infer electrical isolation from the lack of its own redstone output. [Lamp implementation][lamp] · [Default signal behavior][default] · [Signal conduction][signals]

## Switching delay

On a relevant server-side neighbor update, an unlit Lamp turns on immediately when it detects power. Losing power while lit schedules an off check **four game ticks later**, two conventional redstone ticks or 0.2 seconds at 20 TPS. The check only turns it off if power is still absent. Reapplying power before that check can keep it lit. [Neighbor and scheduled callbacks][lamp]

A Lamp can therefore visually stretch a short input, or appear continuously lit while power briefly dips. It does not by itself measure a [Repeater](RedstoneRepeater.md), [Observer](Observer.md), [Pressure Plate](PressurePlates.md), or [Tripwire](Tripwire.md) pulse exactly. Inspect the input's own timing when that matters.

## Simple control

An explicitly untested, source-derived starter layout is one Lamp with a Lever attached to its side. Turning the Lever on should illuminate it; turning the Lever off should extinguish it after the Lamp's off check. For an inverted control, use the [Redstone Torch example](RedstoneTorch.md#small-example-an-inverted-lamp).

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration/light, recipe, loot, placement, neighbor updates, scheduled-off behavior, and shared block defaults were checked. No crafting, placement, lighting, circuit, or timing gameplay test was run.

Related: [Redstone Lamp item](../items/RedstoneLamp.md) · [Lever](Lever.md) · [Redstone Torch](RedstoneTorch.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/redstone_lamp.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/redstone_lamp.json
[signals]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/SignalGetter.java
[lamp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java
[default]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
