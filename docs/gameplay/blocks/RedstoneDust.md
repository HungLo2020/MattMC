# Redstone Dust and wire

Placing Redstone Dust creates a redstone-wire block that carries a signal. The inventory item is `minecraft:redstone`; the placed block is `minecraft:redstone_wire`.

## Placement

Wire needs a sturdy top face beneath it, with a specific exception allowing placement on a Hopper. Its visible side connections and nearby block geometry affect how it connects and supplies power. It is not a wire that can be suspended arbitrarily in air.

### Changing dot and cross shapes

Use the placed dust with your normal **Use** control, right mouse by default, to switch an isolated cross into a dot or a dot back into a cross. The interaction is available only when the current wire has **all four horizontal arms connected or none**, and the player must have building permission. A corner, T-junction or straight line does not meet that toggle condition. [Use action][shape-use] · [Shape predicates][shape-predicates] · [Default controls][controls]

The switch preserves the stored power value and recomputes nearby connections; it cannot force away a connection the surrounding blocks require. If the shape stays the same, inspect the neighboring wires/components. Use an empty main hand and release Sneak for a straightforward interaction. MattMC's default Sneak binding is **Left Ctrl**; secondary use with either hand holding an item bypasses this normal block interaction. [Connection resolution][connections] · [Server interaction][use-dispatch] · [Secondary use][secondary-use] · [Default block action][default-use]

### Steps and component connections

- Wire can connect to neighboring wire at its own level. An upward step also needs the space **above the lower wire not to conduct redstone**, an eligible neighboring support, and wire above that neighbor. A sturdy side produces the rising arm. The connection check also recognizes a Trapdoor as an eligible neighboring step; the upper wire still needs its own placement support. [Connection and support checks][steps]
- A downward step can connect to wire below the neighboring position when that neighboring block is **not a redstone conductor**. A solid conducting block at that side prevents this downward route. These are connection checks, not permission to suspend dust without support. [Steps][steps]
- Wire connects to a [Repeater](RedstoneRepeater.md) along its input/output axis, not its side faces. For an [Observer](Observer.md#watching-face-and-output), the matching wire connection is at the output face, opposite the watched face. Ordinary signal-source blocks can supply other same-level connections. [Component predicates][components] · [Observer output][observer-output] · [Receiving-block direction][signal-queries]

## Signal strength

Wire stores a power value from **0–15**. In the ordinary evaluator, incoming wire strength is the strongest eligible neighboring wire signal minus one, floored at zero. Direct block power competes with that wire input; a direct level-15 signal can set a wire to 15.

A simple unboosted line therefore loses strength as it extends. Do not confuse signal strength with light emission, and do not assume that a bright-looking line sends power equally in every direction: the output checks its connection state and queried direction.

The code can select an experimental evaluator via a feature flag. This article is not a guarantee of update-order-sensitive machines behaving identically under all flags or future versions.

### Where the signal goes

For ordinary output from powered wire, the receiver's physical position matters:

| Receiver position relative to the wire | Direct output from this wire |
| --- | --- |
| Immediately below | The wire's current strength, including a powered dot |
| Immediately above | No output from this callback |
| Same-level horizontal neighbor | Current strength only when the resolved arm toward that neighbor is connected |

A dot is therefore not an omnidirectional horizontal power source. Nearby components and conducting blocks can create additional circuit paths, so this table describes the wire's own output rather than every possible source powering a receiver. [Wire output][wire-output] · [Receiving-block queries][signal-queries]

## A useful first circuit

Use a [Lever](Lever.md) for a steady source or a [Button](Buttons.md) for a timed pulse. Connect a short, visibly joined dust path to a [Redstone Lamp](../items/RedstoneLamp.md). The lamp's code turns on when neighbor power appears and schedules a four-game-tick off check when power disappears.

If it does not respond, check the source's powered state, wire support, visible connections, signal distance, and the receiving block's input direction. Start with a short simple circuit before adding branches, vertical steps, or timing-sensitive parts.

## Obtaining the item

See [Redstone Dust](../items/RedstoneDust.md) for ore drops and block packing. A loot drop and a successfully placed circuit are separate steps.

Placed wire breaks instantly and its ordinary block loot returns **one Redstone Dust**, with no correct-tool requirement. This is separate from the pickaxe/tier rules for mining Redstone Ore. Explosions apply the loot survival condition; do not count on recovering every destroyed wire. [Wire registration][wire-registration] · [Placed-wire loot][wire-loot]

## Related pages

- [Repeater](RedstoneRepeater.md): restore a still-positive signal to strength 15 before a dust line fades out

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

The placement/control/output additions were source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. They follow the active server interaction and receiving-block queries; no live circuit, multiplayer or visual test was run. The earlier evaluator and timing qualifications remain in force.

[shape-use]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L475-L507
[shape-predicates]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L198-L209
[controls]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L559-L568
[connections]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L120-L168
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L396
[secondary-use]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L310
[default-use]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L206-L210
[steps]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L236-L269
[components]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L377-L389
[observer-output]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/ObserverBlock.java#L94-L107
[signal-queries]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/SignalGetter.java#L61-L96
[wire-output]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L354-L375
[wire-registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/Blocks.java#L1249-L1251
[wire-loot]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/loot_table/blocks/redstone_wire.json#L1-L21
