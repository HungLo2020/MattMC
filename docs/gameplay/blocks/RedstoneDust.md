# Redstone Dust and wire

Placing Redstone Dust creates a redstone-wire block that carries a signal. The inventory item is `minecraft:redstone`; the placed block is `minecraft:redstone_wire`.

## Placement

Wire needs a sturdy top face beneath it, with a specific exception allowing placement on a Hopper. Its visible side connections and nearby block geometry affect how it connects and supplies power. It is not a wire that can be suspended arbitrarily in air.

## Signal strength

Wire stores a power value from **0–15**. In the ordinary evaluator, incoming wire strength is the strongest eligible neighboring wire signal minus one, floored at zero. Direct block power competes with that wire input; a direct level-15 signal can set a wire to 15.

A simple unboosted line therefore loses strength as it extends. Do not confuse signal strength with light emission, and do not assume that a bright-looking line sends power equally in every direction: the output checks its connection state and queried direction.

The code can select an experimental evaluator via a feature flag. This article is not a guarantee of update-order-sensitive machines behaving identically under all flags or future versions.

## A useful first circuit

Use a [Lever](Lever.md) for a steady source or a [Button](Buttons.md) for a timed pulse. Connect a short, visibly joined dust path to a [Redstone Lamp](../items/RedstoneLamp.md). The lamp's code turns on when neighbor power appears and schedules a four-game-tick off check when power disappears.

If it does not respond, check the source's powered state, wire support, visible connections, signal distance, and the receiving block's input direction. Start with a short simple circuit before adding branches, vertical steps, or timing-sensitive parts.

## Obtaining the item

See [Redstone Dust](../items/RedstoneDust.md) for ore drops and block packing. A loot drop and a successfully placed circuit are separate steps.

## Related pages

- [Redstone basics](../redstone/Redstone.md)
- [Lever](Lever.md)
- [Buttons](Buttons.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No circuit simulation or in-game timing test was run; feature flags can select different wire evaluators.

- [Wire support, connections and signals](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java)
- [Default evaluator](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/redstone/DefaultRedstoneWireEvaluator.java)
- [Neighbor wire attenuation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/redstone/RedstoneWireEvaluator.java)
- [Power property](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java)
- [Lamp response](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)
- [Item/block registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
