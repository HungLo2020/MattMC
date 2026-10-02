# Rails

Rails guide minecarts along a built route. Use **ordinary Rail** for corners, **Powered Rail** for propulsion and braking, **Detector Rail** to sense a cart, and **Activator Rail** to invoke a cart-specific action.

This guide covers the placed blocks. [Transport](../mechanics/Transport.md) owns route planning, launch-and-stop layouts, and driving differences; [Minecart](../items/Minecart.md) covers the passenger vehicle and boarding.

| Rail | Block/item ID | Main role |
| --- | --- | --- |
| Ordinary Rail | `minecraft:rail` | Straight, curved, or ascending track |
| Powered Rail | `minecraft:powered_rail` | Boost when powered; brake when unpowered |
| Detector Rail | `minecraft:detector_rail` | Minecart-triggered redstone output |
| Activator Rail | `minecraft:activator_rail` | Passenger ejection and other cart-specific activation |

## Crafting and collecting

The existing [Transport recipe table](../mechanics/Transport.md#building-a-basic-rail-route) gives the layouts for **16 ordinary Rails** and **6 Powered Rails**. All four rail recipes need a Crafting Table.

For the other two recipes, fill both outer columns with **six Iron Ingots** and arrange the center column as follows:

| Result | Center top | Center middle | Center bottom |
| --- | --- | --- | --- |
| 6 Detector Rails | Empty | Stone Pressure Plate | Redstone Dust |
| 6 Activator Rails | Stick | Redstone Torch | Stick |

Detector Rails require the ordinary [Stone Pressure Plate](PressurePlates.md); a weighted or wooden plate is not the listed ingredient. Activator Rails use a Redstone Torch, not an ordinary Torch or loose dust.

All four rail blocks are in the pickaxe-mining tag, but none sets a correct-tool requirement for its own ordinary Survival drop. Each returns its matching rail item. Loss of required support also causes the rail to drop and disappear.

## Support, slopes, and junctions

Rails need **rigid upper support directly underneath**. An ascending rail also checks the supporting block at its high end. Build the supports before laying track, especially on stair-step slopes.

Placement starts with an axis based on your horizontal facing, then nearby compatible rails affect the final connection shape. All four types can form straight sections and one-block climbs. **Only ordinary Rail can curve**; use it at corners between special rails.

A suitable three-way junction of ordinary rails can change its selected curve in response to redstone. The resulting rail still connects two directions at a time, not all three simultaneously. Check the visible connection with the control both on and off before using it as a route selector.

These rails support waterlogging. That does not make water irrelevant to cart movement: the [Transport guide](../mechanics/Transport.md#why-another-railway-design-may-behave-differently) explains water and movement-model differences.

## Powered Rail and power propagation

Supply neighboring redstone power to energize a Powered Rail. The checked block also searches along connected rails of its **own type and compatible axis**, with an eight-step search limit. Slopes are handled by that search, but an ordinary Rail gap, wrong axis, or different special-rail type interrupts the relay.

Activator Rails use the same power-state machinery but propagate through **Activator Rails**, not through Powered Rails. Do not assume one switch powers a whole route, or that these rails replace general redstone wiring to other devices.

Only the block specifically registered as **Powered Rail** receives the cart's boost/brake treatment. Activator Rails share a block implementation but do not become acceleration rails because they are powered.

A powered flat section may need a push or a suitable backing block to choose a starting direction. An unpowered section brakes rather than guaranteeing an instant stop. Follow the [source-derived terminal layout](../mechanics/Transport.md#starting-and-stopping-a-cart) and test the intended load; this guide does not prescribe universal boost spacing.

## Detector Rail output

A Detector Rail checks for an **Abstract Minecart-type entity** in its sensing region. An empty passenger Minecart qualifies; a player walking across, or a loose item by itself, does not.

When occupied it emits **15**, including direct power into the block below. When empty it emits 0. While occupied, it rechecks every **20 game ticks**: ten redstone ticks, or one second at the normal 20-game-tick rate. Departure is noticed at a recheck, so this is not an immediate falling edge or a fixed one-second pulse after every cart.

A [Comparator](RedstoneComparator.md) reading the Detector Rail gets a different kind of output:

- A container cart in range supplies its inventory fullness reading
- An ordinary passenger cart has no inventory reading, even while the Detector's ordinary output is 15
- A Command Block Minecart supplies its command success count, checked before container carts

If several carts overlap, the reading uses a matching cart found by the query; it does not sum their cargo. Avoid overlapping carts when you need an unambiguous measurement. Detector rechecks also notify nearby Comparators, so a parked-cart monitor should not assume every inventory edit updates the display instantly.

## Activator Rail effects

Both default and experimental cart movement call the cart's activation handler with the Activator Rail's current power state. The result depends on the cart:

| Cart | Effect |
| --- | --- |
| Ordinary rideable Minecart | A powered Activator Rail ejects its passenger |
| Minecart with Hopper | Powered disables its own item collection; unpowered enables collection again |
| Minecart with TNT | Powered attempts to prime it, subject to the `tntExplodes` rule; removing power does not cancel an already started fuse |
| Minecart with Command Block | Powered attempts command execution with a four-game-tick activation cooldown and the command system's own checks |

The Hopper cart's enabled flag persists after leaving the Activator Rail and is saved with the cart. Ordinary track does not automatically re-enable it. Disabling its own collection is not a promise that every other container can no longer access its inventory.

Use Activator Rails only where the specific cart effect is wanted. They are not a generic substitute for Powered Rails at a passenger station.

## Small example: a cart-presence lamp

This layout is source-derived and has **not been tested in game**.

1. Build a short, straight, supported track with a Detector Rail between two ordinary Rails.
2. Put a Redstone Lamp beside the Detector Rail, off to the side of the track.
3. Place an empty Minecart onto the Detector Rail. Its presence should light the Lamp.
4. Push the cart clear onto an ordinary section. Allow the Detector's empty recheck and the Lamp's own off delay to run.

Keep this first test level and free of Powered or Activator Rails so that propulsion or cart activation does not complicate the observation.

## Default and experimental movement

The default feature set selects the older cart movement implementation. Enabling `minecart_improvements` selects a separate implementation and enables the `minecartMaxSpeed` rule. Both check the actual Powered Rail block for boost/brake behavior and call the actual cart activation handlers, but their movement and rail-sampling paths differ.

Do not transfer an exact speed, stopping distance, boost gap, or high-speed detector assumption between those models without testing. The existing [Transport](../mechanics/Transport.md) and [Minecart](../items/Minecart.md) guides cover the established movement and control details.

## Related pages

- [Rail item](../items/Rail.md), [Powered Rail item](../items/PoweredRail.md), [Detector Rail item](../items/DetectorRail.md), and [Activator Rail item](../items/ActivatorRail.md)
- [Redstone basics](../redstone/Redstone.md) and [Lever](Lever.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipes, loot, active rail tags, shape/support logic, power propagation, Detector callbacks, scheduled rechecks, vehicle activation callsites, and default/experimental movement selection were checked. No in-game track, junction, cart, circuit, timing, speed, or stopping-distance test was run. Data packs, world settings, and later source changes can affect results.

- [Block registrations and mining properties](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Active rail tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/rails.json)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Rail recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/rail.json)
- [Powered Rail recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/powered_rail.json)
- [Detector Rail recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/detector_rail.json)
- [Activator Rail recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/activator_rail.json)
- [Rail loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/rail.json)
- [Powered Rail loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/powered_rail.json)
- [Detector Rail loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/detector_rail.json)
- [Activator Rail loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/activator_rail.json)
- [Placement, support, slopes, waterlogging, and neighbor callbacks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BaseRailBlock.java)
- [Ordinary Rail junction updates](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RailBlock.java)
- [Connection and shape selection](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RailState.java)
- [Powered/Activator state and eight-step propagation](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PoweredRailBlock.java)
- [Detector contacts, output, rechecks, and analog readings](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DetectorRailBlock.java)
- [Default movement, Powered Rail effects, and Activator callsite](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java)
- [Experimental movement and Activator callsite](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/NewMinecartBehavior.java)
- [Movement selection and backing-block launch direction](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java)
- [Rideable Minecart activation](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/Minecart.java)
- [Hopper Minecart activation and saved enabled state](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/MinecartHopper.java)
- [TNT Minecart activation and game-rule restriction](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/MinecartTNT.java)
- [Command-cart activation and cooldown](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/vehicle/MinecartCommandBlock.java)
- [Command execution checks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/BaseCommandBlock.java)
- [Cart placement on rails](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/MinecartItem.java)
- [Default feature flags](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/flag/FeatureFlags.java)
- [Default world configuration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/WorldDataConfiguration.java)
- [Experimental speed rule](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/GameRules.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java)
- [Scheduled tick execution](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java)
- [Lamp response](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)
